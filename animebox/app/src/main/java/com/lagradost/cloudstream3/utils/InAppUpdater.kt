package com.lagradost.cloudstream3.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.NameNotFoundException
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.MainActivity.Companion.deleteFileOnExit
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxThemeHelper
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.io.FileOutputStream

object InAppUpdater {
    private const val GITHUB_USER_NAME = "SOLO-ARC"
    private const val GITHUB_REPO = "Animebox"

    private const val PRERELEASE_PACKAGE_NAME = "com.lagradost.cloudstream3.prerelease"
    private const val LOG_TAG = "InAppUpdater"

    @Serializable
    private data class GithubAsset(
        @JsonProperty("name") @SerialName("name") val name: String,
        @JsonProperty("size") @SerialName("size") val size: Int, // Size in bytes
        @JsonProperty("browser_download_url") @SerialName("browser_download_url") val browserDownloadUrl: String,
        @JsonProperty("content_type") @SerialName("content_type") val contentType: String, // application/vnd.android.package-archive
    )

    @Serializable
    private data class GithubRelease(
        @JsonProperty("tag_name") @SerialName("tag_name") val tagName: String, // Version code
        @JsonProperty("name") @SerialName("name") val name: String? = null,
        @JsonProperty("body") @SerialName("body") val body: String? = null, // Description
        @JsonProperty("assets") @SerialName("assets") val assets: List<GithubAsset>,
        @JsonProperty("target_commitish") @SerialName("target_commitish") val targetCommitish: String? = null, // Branch
        @JsonProperty("prerelease") @SerialName("prerelease") val prerelease: Boolean = false,
        @JsonProperty("node_id") @SerialName("node_id") val nodeId: String? = null,
    )

    @Serializable
    private data class Update(
        @JsonProperty("shouldUpdate") @SerialName("shouldUpdate") val shouldUpdate: Boolean,
        @JsonProperty("updateURL") @SerialName("updateURL") val updateURL: String?,
        @JsonProperty("updateVersion") @SerialName("updateVersion") val updateVersion: String?,
        @JsonProperty("changelog") @SerialName("changelog") val changelog: String?,
        @JsonProperty("updateNodeId") @SerialName("updateNodeId") val updateNodeId: String?,
    )

    private fun parseSemVer(versionStr: String?): Long? {
        if (versionStr.isNullOrBlank()) return null
        val semverRegex = Regex("""(\d+)\.(\d+)(?:\.(\d+))?""")
        val match = semverRegex.find(versionStr) ?: return null
        val major = match.groupValues[1].toLongOrNull() ?: 0L
        val minor = match.groupValues[2].toLongOrNull() ?: 0L
        val patch = match.groupValues.getOrNull(3)?.toLongOrNull() ?: 0L
        return major * 1_000_000L + minor * 1_000L + patch
    }

    private suspend fun Activity.getAppUpdate(installPrerelease: Boolean = false): Update {
        // Method 1: Try GitHub REST API
        try {
            val url = "https://api.github.com/repos/$GITHUB_USER_NAME/$GITHUB_REPO/releases"
            val headers = mapOf(
                "Accept" to "application/vnd.github.v3+json",
                "User-Agent" to "FireFly-Android-App/1.0"
            )
            val res = app.get(url, headers = headers)
            val jsonText = res.text
            if (res.isSuccessful && !jsonText.contains("API rate limit exceeded")) {
                val response = parseJson<Array<GithubRelease>>(jsonText).toList()
                if (response.isNotEmpty()) {
                    val validReleases = response.mapNotNull { rel ->
                        val apkAsset = rel.assets.firstOrNull { asset ->
                            asset.contentType == "application/vnd.android.package-archive" ||
                            asset.name.endsWith(".apk", ignoreCase = true)
                        } ?: rel.assets.firstOrNull()

                        val semver = parseSemVer(rel.tagName) ?: parseSemVer(rel.name) ?: parseSemVer(apkAsset?.name)
                        if (apkAsset != null && semver != null && apkAsset.browserDownloadUrl.isNotBlank()) {
                            Triple(rel, apkAsset, semver)
                        } else null
                    }.sortedBy { it.third }

                    val latestRelease = validReleases.lastOrNull()
                    if (latestRelease != null) {
                        val rel = latestRelease.first
                        val asset = latestRelease.second
                        val remoteVersionCode = latestRelease.third

                        val currentVersionStr = packageName?.let {
                            try { packageManager.getPackageInfo(it, 0).versionName } catch (_: Exception) { BuildConfig.VERSION_NAME }
                        } ?: BuildConfig.VERSION_NAME
                        val currentVersionCode = parseSemVer(currentVersionStr) ?: 0L

                        val shouldUpdate = remoteVersionCode > currentVersionCode
                        val updateVersionName = rel.tagName.ifBlank { rel.name ?: "Latest" }

                        Log.d(LOG_TAG, "API Update check: Current=$currentVersionStr ($currentVersionCode), Remote=${rel.tagName} ($remoteVersionCode), ShouldUpdate=$shouldUpdate")

                        return Update(
                            shouldUpdate = shouldUpdate,
                            updateURL = asset.browserDownloadUrl,
                            updateVersion = updateVersionName,
                            changelog = rel.body,
                            updateNodeId = rel.nodeId
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(LOG_TAG, "GitHub API fetch failed or rate-limited, trying fallback: ${e.message}")
        }

        // Method 2: Fallback to GitHub /releases/latest endpoint (100% Rate-Limit Free!)
        try {
            val latestWebUrl = "https://github.com/$GITHUB_USER_NAME/$GITHUB_REPO/releases/latest"
            val headers = mapOf("User-Agent" to "Mozilla/5.0 (Android; Mobile)")
            val res = app.get(latestWebUrl, headers = headers, allowRedirects = true)
            val finalUrl = res.url
            Log.d(LOG_TAG, "Fallback latest redirected URL: $finalUrl")

            val tag = if (finalUrl.contains("/tag/")) {
                finalUrl.substringAfterLast("/tag/").substringBefore("/")
            } else if (res.text.contains("/releases/tag/")) {
                val tagMatch = Regex("""/releases/tag/([vV]?\d+\.\d+(?:\.\d+)?)""").find(res.text)
                tagMatch?.groupValues?.getOrNull(1) ?: ""
            } else ""

            if (tag.isNotBlank()) {
                val remoteVersionCode = parseSemVer(tag)
                val currentVersionStr = packageName?.let {
                    try { packageManager.getPackageInfo(it, 0).versionName } catch (_: Exception) { BuildConfig.VERSION_NAME }
                } ?: BuildConfig.VERSION_NAME
                val currentVersionCode = parseSemVer(currentVersionStr) ?: 0L

                if (remoteVersionCode != null) {
                    val shouldUpdate = remoteVersionCode > currentVersionCode
                    val downloadUrl = "https://github.com/$GITHUB_USER_NAME/$GITHUB_REPO/releases/download/$tag/firefly.apk"

                    Log.d(LOG_TAG, "Web Fallback Update check: Current=$currentVersionStr ($currentVersionCode), Remote=$tag ($remoteVersionCode), ShouldUpdate=$shouldUpdate")

                    return Update(
                        shouldUpdate = shouldUpdate,
                        updateURL = downloadUrl,
                        updateVersion = tag,
                        changelog = "• Latest FireFly $tag update available.",
                        updateNodeId = tag
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Fallback release check failed", e)
        }

        return Update(false, null, null, null, null)
    }

    private val updateLock = Mutex()

    private suspend fun Activity.downloadUpdateWithProgress(
        url: String,
        onProgress: (bytesDownloaded: Long, totalBytes: Long, percentage: Int, speedBps: Long) -> Unit
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(LOG_TAG, "Downloading update: $url")
                val appUpdateName = "FireFly_Update"
                val appUpdateSuffix = "apk"

                // Delete old downloaded apk files
                cacheDir.listFiles()?.filter {
                    it.name.startsWith("FireFly_Update") || it.name.startsWith("CloudStream") && it.extension == appUpdateSuffix
                }?.forEach { deleteFileOnExit(it) }

                val downloadedFile = File.createTempFile(appUpdateName, ".$appUpdateSuffix", cacheDir)

                val response = app.get(url)
                val body = response.body
                val contentLength = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(downloadedFile)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytesRead = 0L
                var lastProgressTime = System.currentTimeMillis()
                var lastProgressBytes = 0L
                var currentSpeed = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead

                    val now = System.currentTimeMillis()
                    val timeDiff = now - lastProgressTime
                    if (timeDiff >= 150) {
                        currentSpeed = if (timeDiff > 0) ((totalBytesRead - lastProgressBytes) * 1000) / timeDiff else 0L
                        lastProgressTime = now
                        lastProgressBytes = totalBytesRead

                        val percent = if (contentLength > 0) {
                            ((totalBytesRead * 100) / contentLength).toInt().coerceIn(0, 100)
                        } else -1
                        withContext(Dispatchers.Main) {
                            onProgress(totalBytesRead, contentLength, percent, currentSpeed)
                        }
                    }
                }
                outputStream.flush()
                outputStream.close()
                inputStream.close()

                withContext(Dispatchers.Main) {
                    onProgress(totalBytesRead, contentLength, 100, currentSpeed)
                    openApk(this@downloadUpdateWithProgress, Uri.fromFile(downloadedFile))
                }
                true
            } catch (e: Exception) {
                logError(e)
                false
            }
        }
    }

    private fun openApk(context: Context, uri: Uri) = safe {
        val path = uri.path ?: return@safe
        val contentUri = FileProvider.getUriForFile(
            context, BuildConfig.APPLICATION_ID + ".provider", File(path)
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
            data = contentUri
        }
        context.startActivity(installIntent)
    }

    fun Activity.installPreReleaseIfNeeded() = ioSafe {
        val isInstalled = try {
            packageManager.getPackageInfo(PRERELEASE_PACKAGE_NAME, 0)
            true
        } catch (_: NameNotFoundException) {
            false
        }

        if (isInstalled) {
            showToast(R.string.prerelease_already_installed)
        } else if (!runAutoUpdate(checkAutoUpdate = false, installPrerelease = true)) {
            showToast(R.string.prerelease_install_failed)
        }
    }

    private fun Activity.showDownloadProgressDialog(update: Update) {
        if (isFinishing || isDestroyed) return

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(60, 40, 60, 30)
        }

        val tvStatus = android.widget.TextView(this).apply {
            text = "Downloading FireFly v${update.updateVersion}..."
            setTextColor(android.graphics.Color.WHITE)
            textSize = 15f
            setPadding(0, 0, 0, 20)
        }

        val progressBar = android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            progress = 0
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val tvDetails = android.widget.TextView(this).apply {
            text = "0% (0.0 MB / -- MB)"
            setTextColor(android.graphics.Color.LTGRAY)
            textSize = 12f
            setPadding(0, 15, 0, 0)
        }

        layout.addView(tvStatus)
        layout.addView(progressBar)
        layout.addView(tvDetails)

        val progressDialog = androidx.appcompat.app.AlertDialog.Builder(this, R.style.AlertDialogCustom)
            .setTitle("Downloading Update")
            .setView(layout)
            .setCancelable(false)
            .setNegativeButton(R.string.cancel) { d, _ ->
                d.dismiss()
            }
            .create()

        progressDialog.show()

        ioSafe {
            val success = downloadUpdateWithProgress(update.updateURL!!) { dlBytes, totBytes, percent, speed ->
                runOnUiThread {
                    if (progressDialog.isShowing && !isFinishing && !isDestroyed) {
                        progressBar.progress = percent.coerceIn(0, 100)
                        val dlMb = String.format(java.util.Locale.US, "%.1f", dlBytes / (1024f * 1024f))
                        val totMb = if (totBytes > 0) String.format(java.util.Locale.US, "%.1f MB", totBytes / (1024f * 1024f)) else "-- MB"
                        val speedMb = String.format(java.util.Locale.US, "%.1f MB/s", speed / (1024f * 1024f))
                        tvDetails.text = "$percent% • $dlMb MB / $totMb ($speedMb)"
                    }
                }
            }

            runOnUiThread {
                if (progressDialog.isShowing && !isFinishing && !isDestroyed) {
                    progressDialog.dismiss()
                }
                if (!success) {
                    showToast(R.string.download_failed, Toast.LENGTH_LONG)
                }
            }
        }
    }

    private var hasAutoCheckedThisSession = false

    /**
     * @param checkAutoUpdate if the update check was launched automatically
     * @param installPrerelease if we want to install the pre-release version
     */
    suspend fun Activity.runAutoUpdate(
        checkAutoUpdate: Boolean = true, installPrerelease: Boolean = false
    ): Boolean {
        val settingsManager = PreferenceManager.getDefaultSharedPreferences(this)
        val autoUpdateEnabled =
            settingsManager.getBoolean(getString(R.string.auto_update_key), true)
        if (checkAutoUpdate) {
            if (!autoUpdateEnabled || hasAutoCheckedThisSession) {
                return false
            }
            hasAutoCheckedThisSession = true
        }

        val update = getAppUpdate(installPrerelease)
        if (!update.shouldUpdate || update.updateURL == null) {
            return false
        }

        // Check if update should be skipped
        val updateNodeId = settingsManager.getString(
            getString(R.string.skip_update_key), ""
        )

        // Skips the update if its an automatic update and the update is skipped
        if (update.updateNodeId.equals(updateNodeId) && checkAutoUpdate) {
            return false
        }

        runOnUiThread {
            try {
                if (isFinishing || isDestroyed) return@runOnUiThread

                val currentVersion = packageName?.let {
                    try { packageManager.getPackageInfo(it, 0) } catch (_: Exception) { null }
                }

                val builder = androidx.appcompat.app.AlertDialog.Builder(this, R.style.AlertDialogCustom)
                builder.setTitle(
                    getString(R.string.new_update_format).format(
                        currentVersion?.versionName ?: BuildConfig.VERSION_NAME, update.updateVersion
                    )
                )

                val logRegex = Regex("\\[(.*?)]\\((.*?)\\)")
                val sanitizedChangelog = update.changelog?.replace(logRegex) { matchResult ->
                    matchResult.groupValues[1]
                } // Sanitized because it looks cluttered

                builder.setMessage(sanitizedChangelog)
                builder.apply {
                    setPositiveButton(R.string.update) { _, _ ->
                        showDownloadProgressDialog(update)
                    }

                    setNegativeButton(R.string.cancel) { _, _ -> }

                    if (checkAutoUpdate) {
                        setNeutralButton(R.string.skip_update) { _, _ ->
                            settingsManager.edit {
                                putString(
                                    getString(R.string.skip_update_key), update.updateNodeId ?: ""
                                )
                            }
                        }
                    }
                }
                builder.show()
            } catch (e: Exception) {
                logError(e)
            }
        }
        return true
    }
}
