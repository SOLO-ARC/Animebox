package com.lagradost.cloudstream3.ui.animebox.download
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import com.lagradost.cloudstream3.ui.animebox.extractors.MegaPlayExtractor
import com.lagradost.cloudstream3.ui.animebox.extractors.AnimexExtractor

enum class DownloadStatus {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    PAUSED
}

data class DownloadedEpisode(
    val id: String,
    val anilistId: Int,
    val animeTitle: String,
    val episodeNumber: Int,
    val episodeTitle: String,
    val coverUrl: String,
    val showCoverUrl: String = "",
    val localFilePath: String,
    val quality: String,
    val fileSizeBytes: Long,
    val totalBytes: Long,
    val downloadProgress: Int,
    val status: DownloadStatus,
    val downloadSpeedText: String = "",
    val downloadTimestamp: Long = System.currentTimeMillis(),
    val localSubtitlePath: String = "",
    val subtitlesJson: String = "[]",
    val localBackdropPath: String = "",
    val localLogoPath: String = "",
    val introStart: Long = 0L,
    val introEnd: Long = 0L,
    val outroStart: Long = 0L,
    val outroEnd: Long = 0L,
    val streamType: String = "sub"
)

data class DownloadedAnimeGroup(
    val anilistId: Int,
    val animeTitle: String,
    val coverUrl: String,
    val backdropUrl: String = "",
    val logoUrl: String = "",
    val episodes: List<DownloadedEpisode>,
    val totalSizeBytes: Long
)

data class StorageStats(
    val totalGb: Double,
    val usedGb: Double,
    val appGb: Double,
    val freeGb: Double
)

class AnimeDownloadManager(private val context: Context) {

    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val downloadSemaphore = kotlinx.coroutines.sync.Semaphore(10)
    private val hlsSegmentSemaphore = kotlinx.coroutines.sync.Semaphore(12)

    val episodesState: StateFlow<List<DownloadedEpisode>> = _episodesState.asStateFlow()

    val baseDir: File by lazy {
        // Keep all data inside the app only so it doesn't pollute the gallery, and gets deleted with app uninstall
        val appInternalDir = context.getExternalFilesDir(null) ?: context.filesDir
        val animeBoxDir = File(appInternalDir, "AnimeBox").apply {
            if (!exists()) mkdirs()
        }
        try {
            val nomedia = File(animeBoxDir, ".nomedia")
            if (!nomedia.exists()) nomedia.createNewFile()
        } catch (_: Throwable) {}
        animeBoxDir
    }

    private val metadataFile: File by lazy {
        File(baseDir, "downloads.json")
    }

    init {
        refreshState()
        migrateLegacyPublicDownloadsAsync()
    }

    private fun migrateLegacyPublicDownloadsAsync() {
        downloadScope.launch(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("animebox_download_prefs", Context.MODE_PRIVATE)
                if (prefs.getBoolean("legacy_storage_migrated_v2", false)) return@launch

                val targetDir = baseDir
                val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val oldPublicAnimeBox = File(publicDownloads, "AnimeBox")
                if (oldPublicAnimeBox.exists() && oldPublicAnimeBox.isDirectory) {
                    oldPublicAnimeBox.listFiles()?.forEach { file ->
                        val dest = File(targetDir, file.name)
                        if (!dest.exists()) {
                            val moved = try { file.renameTo(dest) } catch (_: Throwable) { false }
                            if (!moved) {
                                try { file.copyRecursively(dest, overwrite = true) } catch (_: Throwable) {}
                            }
                        }
                    }
                    try { oldPublicAnimeBox.deleteRecursively() } catch (_: Throwable) {}
                }

                val publicMovies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                val oldMoviesAnimeBox = File(publicMovies, "AnimeBox")
                if (oldMoviesAnimeBox.exists() && oldMoviesAnimeBox.isDirectory) {
                    oldMoviesAnimeBox.listFiles()?.forEach { file ->
                        val dest = File(targetDir, file.name)
                        if (!dest.exists()) {
                            val moved = try { file.renameTo(dest) } catch (_: Throwable) { false }
                            if (!moved) {
                                try { file.copyRecursively(dest, overwrite = true) } catch (_: Throwable) {}
                            }
                        }
                    }
                    try { oldMoviesAnimeBox.deleteRecursively() } catch (_: Throwable) {}
                }

                prefs.edit().putBoolean("legacy_storage_migrated_v2", true).apply()
            } catch (_: Throwable) {}
        }
    }

    fun refreshState() {
        val loaded = getAllDownloadedEpisodes()
        _episodesState.value = loaded
    }

    @Volatile
    private var cachedStorageStats: StorageStats? = null

    @Volatile
    private var hasRunRecoveryScan: Boolean = false

    fun getCachedStorageStats(): StorageStats {
        cachedStorageStats?.let { return it }
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBytes = stat.blockCountLong * blockSize
            val freeBytes = stat.availableBlocksLong * blockSize
            val usedBytes = totalBytes - freeBytes
            val completedBytes = _episodesState.value.filter { it.status == DownloadStatus.COMPLETED }.sumOf { it.fileSizeBytes }
            val bytesInGb = 1024.0 * 1024.0 * 1024.0
            StorageStats(
                totalGb = (totalBytes / bytesInGb),
                usedGb = ((usedBytes - completedBytes).coerceAtLeast(0L) / bytesInGb),
                appGb = (completedBytes / bytesInGb),
                freeGb = (freeBytes / bytesInGb)
            )
        } catch (_: Exception) {
            StorageStats(128.0, 45.0, 1.0, 82.0)
        }
    }

    fun getStorageStats(): StorageStats {
        return try {
            val path = Environment.getDataDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            val totalBytes = totalBlocks * blockSize
            val freeBytes = availableBlocks * blockSize
            val usedBytes = totalBytes - freeBytes

            // Accurate download size from completed downloads (or baseDir if episodes not yet loaded) and installed AI models
            val modelsBytes = try {
                com.lagradost.cloudstream3.ui.animebox.api.AiSubtitleModelManager.getInstalledModels(context).sumOf { it.sizeBytes }
            } catch (_: Exception) { 0L }
            val completedBytes = _episodesState.value.filter { it.status == DownloadStatus.COMPLETED }.sumOf { it.fileSizeBytes }
            val appBytes = (if (completedBytes > 100_000L) completedBytes else maxOf(getFolderSize(baseDir), completedBytes)) + modelsBytes

            val bytesInGb = 1024.0 * 1024.0 * 1024.0

            val stats = StorageStats(
                totalGb = (totalBytes / bytesInGb),
                usedGb = ((usedBytes - appBytes).coerceAtLeast(0L) / bytesInGb),
                appGb = (appBytes / bytesInGb),
                freeGb = (freeBytes / bytesInGb)
            )
            cachedStorageStats = stats
            stats
        } catch (e: Exception) {
            cachedStorageStats ?: StorageStats(128.0, 45.0, 1.0, 82.0)
        }
    }

    private fun getFolderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        var size = 0L
        file.listFiles()?.forEach { size += getFolderSize(it) }
        return size
    }

    fun getAllDownloadedEpisodes(): List<DownloadedEpisode> {
        val list = mutableListOf<DownloadedEpisode>()
        var sizeUpdated = false
        try {
            if (metadataFile.exists()) {
                val jsonStr = metadataFile.readText()
                if (jsonStr.isNotBlank()) {
                    val arr = JSONArray(jsonStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val epId = obj.optString("id")
                        var effectivePath = obj.optString("localFilePath")
                        var file = if (effectivePath.isNotEmpty()) File(effectivePath) else null
                        var fileExists = file?.exists() == true && file.length() > 0
                        if (!fileExists && file != null) {
                            val bundleM3u8 = File(file.parentFile, "${file.nameWithoutExtension}_bundle/local.m3u8")
                            if (bundleM3u8.exists() && bundleM3u8.length() > 0) {
                                effectivePath = bundleM3u8.absolutePath
                                file = bundleM3u8
                                fileExists = true
                            }
                        }

                        val rawStatusStr = obj.optString("status", "COMPLETED")
                        val parsedStatus = try { DownloadStatus.valueOf(rawStatusStr) } catch (_: Exception) { DownloadStatus.COMPLETED }

                        val isCurrentlyActive = activeJobs.containsKey(epId) || _episodesState.value.any { it.id == epId && (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.PENDING) }

                        val actualStatus = if (fileExists) {
                            if (parsedStatus == DownloadStatus.DOWNLOADING && isCurrentlyActive) {
                                DownloadStatus.DOWNLOADING
                            } else {
                                DownloadStatus.COMPLETED
                            }
                        } else {
                            if (parsedStatus == DownloadStatus.DOWNLOADING || parsedStatus == DownloadStatus.PENDING) {
                                DownloadStatus.DOWNLOADING
                            } else {
                                parsedStatus
                            }
                        }

                        val savedSize = maxOf(obj.optLong("fileSizeBytes", 0L), obj.optLong("totalBytes", 0L))
                        val actualSize = when {
                            savedSize > 50_000L && actualStatus == DownloadStatus.COMPLETED -> savedSize
                            file == null || !fileExists -> savedSize
                            file.isDirectory -> {
                                val s = file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                                if (s > 0) { sizeUpdated = true; s } else savedSize
                            }
                            file.name.endsWith(".m3u8", ignoreCase = true) -> {
                                val parent = file.parentFile
                                if (parent != null && parent.exists()) {
                                    val s = parent.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                                    if (s > 1000) { sizeUpdated = true; s } else savedSize
                                } else savedSize
                            }
                            else -> {
                                val bundle = File(file.parentFile, "${file.nameWithoutExtension}_bundle")
                                if (bundle.exists() && bundle.isDirectory) {
                                    val s = bundle.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                                    if (s > 1000) { sizeUpdated = true; s } else maxOf(file.length(), savedSize)
                                } else {
                                    val fLen = file.length()
                                    if (fLen > 1000) fLen else savedSize
                                }
                            }
                        }

                        val ep = DownloadedEpisode(
                            id = epId,
                            anilistId = obj.optInt("anilistId"),
                            animeTitle = obj.optString("animeTitle"),
                            episodeNumber = obj.optInt("episodeNumber"),
                            episodeTitle = obj.optString("episodeTitle"),
                            coverUrl = obj.optString("coverUrl"),
                            showCoverUrl = obj.optString("showCoverUrl", obj.optString("coverUrl")),
                            localFilePath = effectivePath,
                            quality = obj.optString("quality", "720p"),
                            fileSizeBytes = actualSize,
                            totalBytes = if (actualSize > 0) actualSize else obj.optLong("totalBytes", actualSize),
                            downloadProgress = if (actualStatus == DownloadStatus.COMPLETED) 100 else obj.optInt("downloadProgress", 0),
                            status = actualStatus,
                            downloadTimestamp = obj.optLong("downloadTimestamp", System.currentTimeMillis()),
                            localSubtitlePath = obj.optString("localSubtitlePath", ""),
                            subtitlesJson = obj.optString("subtitlesJson", "[]"),
                            localBackdropPath = obj.optString("localBackdropPath", ""),
                            localLogoPath = obj.optString("localLogoPath", ""),
                            introStart = obj.optLong("introStart", 0L),
                            introEnd = obj.optLong("introEnd", 0L),
                            outroStart = obj.optLong("outroStart", 0L),
                            outroEnd = obj.optLong("outroEnd", 0L),
                            streamType = obj.optString("streamType", "sub")
                        )
                        list.add(ep)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        try {
            val recovered = if (!hasRunRecoveryScan) {
                hasRunRecoveryScan = true
                scanAndRecoverDownloads(list)
            } else false
            if (recovered || sizeUpdated) {
                saveAll(list)
            }
        } catch (_: Throwable) {}

        return list
    }

    private fun scanAndRecoverDownloads(existingList: MutableList<DownloadedEpisode>): Boolean {
        var modified = false
        try {
            val candidateDirs = mutableListOf(baseDir)
            try {
                val oldDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "AnimeBox")
                if (oldDir.exists() && oldDir.absolutePath != baseDir.absolutePath) {
                    candidateDirs.add(oldDir)
                }
            } catch (_: Throwable) {}

            val regex = Regex("""^(.*?)_EP(\d+)(?:_(.*?))?\.mp4$""", RegexOption.IGNORE_CASE)

            for (dir in candidateDirs) {
                val files = dir.listFiles() ?: continue
                for (f in files) {
                    if (f.isFile && (f.name.endsWith(".mp4", ignoreCase = true) || f.name.endsWith(".mkv", ignoreCase = true)) && f.length() > 1000) {
                        val match = regex.find(f.name)
                        val rawTitle = match?.groupValues?.getOrNull(1)?.replace("_", " ")?.trim() ?: f.nameWithoutExtension.replace("_", " ").trim()
                        val epNum = match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 1
                        val qual = match?.groupValues?.getOrNull(3)?.replace("_", " ") ?: "720p"

                        val alreadyExists = existingList.any {
                            it.localFilePath == f.absolutePath ||
                            (it.animeTitle.equals(rawTitle, ignoreCase = true) && it.episodeNumber == epNum)
                        }
                        if (!alreadyExists) {
                            val subsDir = File(dir, "subtitles")
                            val candidateSub = File(subsDir, "sub_${f.nameWithoutExtension}.vtt")
                            val subPath = if (candidateSub.exists()) candidateSub.absolutePath else ""

                            val coversDir = File(dir, "covers")
                            val candidateCover = File(coversDir, "cover_${f.nameWithoutExtension}.jpg")
                            val coverPath = if (candidateCover.exists()) candidateCover.absolutePath else ""

                            val recovered = DownloadedEpisode(
                                id = "recovered_${rawTitle.hashCode()}_${epNum}_${System.currentTimeMillis() % 10000}",
                                anilistId = 0,
                                animeTitle = rawTitle,
                                episodeNumber = epNum,
                                episodeTitle = "Episode $epNum",
                                coverUrl = coverPath,
                                showCoverUrl = coverPath,
                                localFilePath = f.absolutePath,
                                quality = qual,
                                fileSizeBytes = f.length(),
                                totalBytes = f.length(),
                                downloadProgress = 100,
                                status = DownloadStatus.COMPLETED,
                                downloadTimestamp = f.lastModified(),
                                localSubtitlePath = subPath
                            )
                            existingList.add(recovered)
                            modified = true
                        }
                    } else if (f.isDirectory && f.name.endsWith("_bundle", ignoreCase = true)) {
                        val m3u8 = File(f, "local.m3u8")
                        if (m3u8.exists() && m3u8.length() > 0) {
                            val baseName = f.name.removeSuffix("_bundle")
                            val match = regex.find("${baseName}.mp4")
                            val rawTitle = match?.groupValues?.getOrNull(1)?.replace("_", " ")?.trim() ?: baseName.replace("_", " ").trim()
                            val epNum = match?.groupValues?.getOrNull(2)?.toIntOrNull() ?: 1
                            val qual = match?.groupValues?.getOrNull(3)?.replace("_", " ") ?: "720p"

                            val alreadyExists = existingList.any {
                                it.localFilePath == m3u8.absolutePath ||
                                (it.animeTitle.equals(rawTitle, ignoreCase = true) && it.episodeNumber == epNum)
                            }
                            if (!alreadyExists) {
                                val s = f.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                                val recovered = DownloadedEpisode(
                                    id = "recovered_${rawTitle.hashCode()}_${epNum}_${System.currentTimeMillis() % 10000}",
                                    anilistId = 0,
                                    animeTitle = rawTitle,
                                    episodeNumber = epNum,
                                    episodeTitle = "Episode $epNum",
                                    coverUrl = "",
                                    showCoverUrl = "",
                                    localFilePath = m3u8.absolutePath,
                                    quality = qual,
                                    fileSizeBytes = s,
                                    totalBytes = s,
                                    downloadProgress = 100,
                                    status = DownloadStatus.COMPLETED,
                                    downloadTimestamp = f.lastModified()
                                )
                                existingList.add(recovered)
                                modified = true
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {}
        return modified
    }

    fun getDownloadedAnimeGroups(): List<DownloadedAnimeGroup> {
        val eps = _episodesState.value.ifEmpty { getAllDownloadedEpisodes() }
        val imagesDir = File(baseDir, "images")
        val coversDir = File(baseDir, "covers")
        return eps.groupBy { it.anilistId }.map { (id, epList) ->
            val first = epList.first()
            val totalSize = epList.sumOf { it.fileSizeBytes }
            val isShinChan = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(id)
            val localBg = File(imagesDir, "backdrop_${id}.jpg")
            val localLogo = File(imagesDir, "logo_${id}.png")
            val localShowCover = File(coversDir, "show_cover_${id}.jpg")
            val localEpCover = File(coversDir, "cover_${id}_${first.episodeNumber}.jpg")

            val bgPath = when {
                localBg.exists() && localBg.length() > 0 -> localBg.absolutePath
                first.localBackdropPath.isNotEmpty() -> first.localBackdropPath
                isShinChan -> com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_BACKDROP_URL
                else -> ""
            }
            val logoPath = when {
                localLogo.exists() && localLogo.length() > 0 -> localLogo.absolutePath
                first.localLogoPath.isNotEmpty() -> first.localLogoPath
                isShinChan -> com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_LOGO_URL
                else -> ""
            }
            val coverPath = when {
                localShowCover.exists() && localShowCover.length() > 0 -> localShowCover.absolutePath
                localEpCover.exists() && localEpCover.length() > 0 -> localEpCover.absolutePath
                first.showCoverUrl.isNotEmpty() -> first.showCoverUrl
                isShinChan -> com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_POSTER_URL
                else -> first.coverUrl
            }
            DownloadedAnimeGroup(
                anilistId = id,
                animeTitle = first.animeTitle,
                coverUrl = coverPath,
                backdropUrl = bgPath,
                logoUrl = logoPath,
                episodes = epList.sortedBy { it.episodeNumber },
                totalSizeBytes = totalSize
            )
        }
    }

    fun isEpisodeDownloaded(anilistId: Int, episodeNum: Int): Boolean {
        val eps = _episodesState.value.ifEmpty { getAllDownloadedEpisodes() }
        return eps.any { it.anilistId == anilistId && it.episodeNumber == episodeNum && it.status == DownloadStatus.COMPLETED }
    }

    fun getEpisodeDownload(anilistId: Int, episodeNum: Int): DownloadedEpisode? {
        val eps = _episodesState.value.ifEmpty { getAllDownloadedEpisodes() }
        return eps.find { it.anilistId == anilistId && it.episodeNumber == episodeNum }
    }

    fun getDownloadedEpisode(anilistId: Int, episodeNum: Int): DownloadedEpisode? {
        val eps = _episodesState.value.ifEmpty { getAllDownloadedEpisodes() }
        return eps.find { it.anilistId == anilistId && it.episodeNumber == episodeNum && it.status == DownloadStatus.COMPLETED }
    }

    fun getLocalEpisodeUri(anilistId: Int, episodeNum: Int): Uri? {
        val ep = getEpisodeDownload(anilistId, episodeNum)
        if (ep != null && ep.localFilePath.isNotEmpty()) {
            val file = File(ep.localFilePath)
            if (file.exists() && file.length() > 0) {
                return Uri.fromFile(file)
            }
        }
        return null
    }

    suspend fun saveEpisodeDownload(episode: DownloadedEpisode) = withContext(Dispatchers.IO) {
        val currentList = getAllDownloadedEpisodes().toMutableList()
        val index = currentList.indexOfFirst { it.id == episode.id }
        if (index >= 0) {
            currentList[index] = episode
        } else {
            currentList.add(episode)
        }
        saveAll(currentList)
        _episodesState.value = currentList
    }

    suspend fun cancelDownload(anilistId: Int, episodeNum: Int) = withContext(Dispatchers.IO) {
        val id = "${anilistId}_${episodeNum}"
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        deleteEpisode(anilistId, episodeNum)
    }

    suspend fun deleteEpisode(anilistId: Int, episodeNum: Int) = withContext(Dispatchers.IO) {
        val currentList = getAllDownloadedEpisodes().toMutableList()
        val target = currentList.find { it.anilistId == anilistId && it.episodeNumber == episodeNum }
        if (target != null) {
            try {
                val f = File(target.localFilePath)
                if (f.exists()) f.delete()
                val coversDir = File(baseDir, "covers")
                val coverFile = File(coversDir, "cover_${anilistId}_${episodeNum}.jpg")
                if (coverFile.exists()) coverFile.delete()
                val subsDir = File(baseDir, "subtitles")
                val subFileVtt = File(subsDir, "sub_${anilistId}_${episodeNum}.vtt")
                if (subFileVtt.exists()) subFileVtt.delete()
                val subFileSrt = File(subsDir, "sub_${anilistId}_${episodeNum}.srt")
                if (subFileSrt.exists()) subFileSrt.delete()
            } catch (_: Exception) {}
            currentList.remove(target)
            saveAll(currentList)
            _episodesState.value = currentList
        }
    }

    private suspend fun downloadCoverImage(client: OkHttpClient, coverUrl: String, anilistId: Int, episodeNum: Int): String = withContext(Dispatchers.IO) {
        if (coverUrl.isBlank()) return@withContext ""
        if (coverUrl.startsWith("/") || coverUrl.startsWith("file://")) return@withContext coverUrl
        try {
            val coversDir = File(baseDir, "covers").apply { if (!exists()) mkdirs() }
            val coverFile = File(coversDir, "cover_${anilistId}_${episodeNum}.jpg")
            if (coverFile.exists() && coverFile.length() > 0) {
                return@withContext coverFile.absolutePath
            }
            val req = Request.Builder()
                .url(coverUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bytes = resp.body?.bytes()
                    if (bytes != null && bytes.isNotEmpty()) {
                        coverFile.writeBytes(bytes)
                        return@withContext coverFile.absolutePath
                    }
                }
            }
        } catch (_: Exception) {}
        return@withContext coverUrl
    }

    suspend fun downloadShowCoverImage(client: OkHttpClient, showCoverUrl: String, anilistId: Int): String = withContext(Dispatchers.IO) {
        val coversDir = File(baseDir, "covers").apply { if (!exists()) mkdirs() }
        val coverFile = File(coversDir, "show_cover_${anilistId}.jpg")
        if (coverFile.exists() && coverFile.length() > 0) {
            return@withContext coverFile.absolutePath
        }
        val isShinChan = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(anilistId)
        var rawUrl = if (isShinChan) com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_POSTER_URL else showCoverUrl
        if (rawUrl.isBlank() || !rawUrl.startsWith("http")) {
            try {
                val jsonStr = com.lagradost.cloudstream3.ui.animebox.api.AniListClient.getAnimeDetails(anilistId)
                if (!jsonStr.isNullOrEmpty()) {
                    val root = JSONObject(jsonStr)
                    val data = root.optJSONObject("data")
                    val media = data?.optJSONObject("Media")
                    val coverImg = media?.optJSONObject("coverImage")
                    val extraLarge = coverImg?.optString("extraLarge", "") ?: ""
                    val large = coverImg?.optString("large", "") ?: ""
                    val resolvedCover = extraLarge.ifEmpty { large }
                    if (resolvedCover.isNotEmpty()) {
                        rawUrl = resolvedCover
                    }
                }
            } catch (_: Exception) {}
        }
        if (rawUrl.startsWith("http")) {
            try {
                val req = Request.Builder()
                    .url(rawUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bytes = resp.body?.bytes()
                        if (bytes != null && bytes.isNotEmpty()) {
                            coverFile.writeBytes(bytes)
                            return@withContext coverFile.absolutePath
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return@withContext if (coverFile.exists() && coverFile.length() > 0) coverFile.absolutePath else showCoverUrl
    }

    suspend fun downloadBackdropAndLogoImages(
        client: OkHttpClient,
        anilistId: Int,
        providedBackdrop: String = "",
        providedLogo: String = ""
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val imagesDir = File(baseDir, "images").apply { if (!exists()) mkdirs() }
        val backdropFile = File(imagesDir, "backdrop_${anilistId}.jpg")
        val logoFile = File(imagesDir, "logo_${anilistId}.png")

        var localBackdrop = if (backdropFile.exists() && backdropFile.length() > 0) backdropFile.absolutePath else ""
        var localLogo = if (logoFile.exists() && logoFile.length() > 0) logoFile.absolutePath else ""
        val isShinChan = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(anilistId)

        if (localBackdrop.isEmpty()) {
            try {
                val isCoverLike = providedBackdrop.contains("/cover/", ignoreCase = true) ||
                                  providedBackdrop.contains("coverImage", ignoreCase = true) ||
                                  (providedBackdrop.contains("large", ignoreCase = true) && !providedBackdrop.contains("banner", ignoreCase = true))
                var bgUrl = when {
                    isShinChan -> com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_BACKDROP_URL
                    providedBackdrop.isNotBlank() && providedBackdrop.startsWith("http") && !isCoverLike -> providedBackdrop
                    else -> ""
                }
                if (bgUrl.isBlank()) {
                    bgUrl = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getBestBackdropUrl(anilistId)
                }
                if (bgUrl.isBlank()) {
                    bgUrl = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getAnimeBackdropUrl(anilistId)
                }
                if (bgUrl.isBlank()) {
                    bgUrl = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getTvdbBackdropFromAniZip(anilistId)
                }
                if (bgUrl.isBlank()) {
                    try {
                        val detailsJson = com.lagradost.cloudstream3.ui.animebox.api.AniListClient.getAnimeDetails(anilistId)
                        if (!detailsJson.isNullOrEmpty()) {
                            val root = JSONObject(detailsJson)
                            val media = root.optJSONObject("data")?.optJSONObject("Media")
                            bgUrl = media?.optString("bannerImage", "") ?: ""
                        }
                    } catch (_: Exception) {}
                }
                if (bgUrl.isNotEmpty() && bgUrl.startsWith("http")) {
                    val req = Request.Builder()
                        .url(bgUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            resp.body?.bytes()?.let { bytes ->
                                if (bytes.isNotEmpty()) {
                                    backdropFile.writeBytes(bytes)
                                    localBackdrop = backdropFile.absolutePath
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (localLogo.isEmpty()) {
            try {
                var logoUrl = when {
                    isShinChan -> com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_LOGO_URL
                    providedLogo.isNotBlank() && providedLogo.startsWith("http") -> providedLogo
                    else -> ""
                }
                if (logoUrl.isBlank()) {
                    logoUrl = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getAnimeLogoUrl(anilistId)
                }
                if (logoUrl.isNotEmpty() && logoUrl.startsWith("http")) {
                    val req = Request.Builder()
                        .url(logoUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            resp.body?.bytes()?.let { bytes ->
                                if (bytes.isNotEmpty()) {
                                    logoFile.writeBytes(bytes)
                                    localLogo = logoFile.absolutePath
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        Pair(localBackdrop, localLogo)
    }

    suspend fun ensureAnimeBackdropAndLogo(
        anilistId: Int,
        providedBackdrop: String = "",
        providedLogo: String = ""
    ): Pair<String, String> {
        if (anilistId <= 0) return Pair("", "")
        val imagesDir = File(baseDir, "images")
        val backdropFile = File(imagesDir, "backdrop_${anilistId}.jpg")
        val logoFile = File(imagesDir, "logo_${anilistId}.png")
        if (backdropFile.exists() && backdropFile.length() > 0 && logoFile.exists() && logoFile.length() > 0) {
            return Pair(backdropFile.absolutePath, logoFile.absolutePath)
        }
        val client = createDownloadHttpClient()
        return downloadBackdropAndLogoImages(client, anilistId, providedBackdrop, providedLogo)
    }

    private fun vttToSrt(vttText: String): String {
        if (vttText.isBlank()) return ""
        val lines = vttText.lines()
        val srtBuilder = StringBuilder()
        var cueIndex = 1
        var inCue = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("WEBVTT") || trimmed.startsWith("X-TIMESTAMP-MAP") || trimmed.startsWith("NOTE") || trimmed.startsWith("STYLE") || trimmed.startsWith("REGION")) {
                continue
            }
            if (trimmed.contains("-->")) {
                val srtTimestampLine = trimmed
                    .replace(Regex("(\\d{2}:\\d{2}:\\d{2})\\.(\\d{3})"), "$1,$2")
                    .replace(Regex("(\\d{2}:\\d{2})\\.(\\d{3})"), "00:$1,$2")
                    .replace(Regex("\\s+[a-zA-Z0-9:%\\.\\-]+"), " ")
                    .trim()

                if (inCue) srtBuilder.append("\n")
                srtBuilder.append(cueIndex++).append("\n")
                srtBuilder.append(srtTimestampLine).append("\n")
                inCue = true
            } else if (trimmed.isEmpty()) {
                if (inCue) {
                    srtBuilder.append("\n")
                    inCue = false
                }
            } else {
                if (inCue) {
                    val cleanLine = trimmed.replace(Regex("<[^>]*>"), "")
                    srtBuilder.append(cleanLine).append("\n")
                }
            }
        }
        return srtBuilder.toString().trim() + "\n"
    }

    private fun srtToVtt(srtText: String): String {
        if (srtText.isBlank()) return ""
        val lines = srtText.lines()
        val vttBuilder = StringBuilder("WEBVTT\n\n")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.contains("-->")) {
                val vttTimestampLine = trimmed
                    .replace(Regex("(\\d{2}:\\d{2}:\\d{2}),(\\d{3})"), "$1.$2")
                    .replace(Regex("(\\d{2}:\\d{2}),(\\d{3})"), "00:$1.$2")
                vttBuilder.append(vttTimestampLine).append("\n")
            } else {
                vttBuilder.append(line).append("\n")
            }
        }
        return vttBuilder.toString().trim() + "\n"
    }

    private fun extractBestSubtitleUrl(subtitleUrl: String, subtitlesJson: String): String {
        if (subtitlesJson.isNotBlank() && subtitlesJson != "[]") {
            try {
                val arr = JSONArray(subtitlesJson)
                val tracks = mutableListOf<Pair<String, String>>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val u = obj.optString("url", obj.optString("file", obj.optString("src", obj.optString("link", "")))).trim()
                    val lang = obj.optString("lang", obj.optString("label", obj.optString("language", obj.optString("name", "")))).trim()
                    if (u.isNotBlank()) tracks += lang to u
                }
                val aliases = listOf("english", "eng", "en")
                val preferredTrack = if (aliases.isEmpty()) null else tracks.firstOrNull { (label, _) ->
                    val normalized = label.lowercase().trim()
                    aliases.any { alias -> normalized == alias || normalized.startsWith("$alias-") || normalized.startsWith("$alias ") || normalized.contains("$alias ") }
                }
                preferredTrack?.second?.let { return it }
                tracks.firstOrNull()?.second?.let { return it }
            } catch (_: Exception) {}
        }
        if (subtitleUrl.isNotBlank() && (subtitleUrl.startsWith("http") || subtitleUrl.startsWith("//") || subtitleUrl.startsWith("/") || subtitleUrl.startsWith("file://"))) {
            return subtitleUrl.trim()
        }
        return subtitleUrl.trim()
    }

    private fun localSubtitleTrackJson(sourceJson: String, localPath: String): String {
        if (localPath.isBlank()) return sourceJson.ifBlank { "[]" }
        return try {
            val tracks = JSONArray(sourceJson.ifBlank { "[]" })
            val preferredUrl = extractBestSubtitleUrl("", sourceJson)
            val selected = (0 until tracks.length()).mapNotNull { tracks.optJSONObject(it) }
                .firstOrNull { track ->
                    track.optString("url", track.optString("file", track.optString("src", ""))).trim() == preferredUrl
                }?.let { JSONObject(it.toString()).put("url", localPath) }
                ?: JSONObject().put("lang", "English").put("url", localPath)
            JSONArray().put(selected).toString()
        } catch (_: Exception) {
            JSONArray().put(JSONObject().put("lang", "English").put("url", localPath)).toString()
        }
    }

    private suspend fun downloadSubtitleFile(
        client: OkHttpClient,
        subtitleUrl: String,
        subtitlesJson: String,
        anilistId: Int,
        episodeNum: Int,
        targetVideoFile: File? = null
    ): String = withContext(Dispatchers.IO) {
        var rawSubUrl = extractBestSubtitleUrl(subtitleUrl, subtitlesJson)

        // 1. If not provided in parameters, fetch from Extractor Engine directly in background
        if (rawSubUrl.isBlank()) {
            try {
                val sInfo = com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.getStreamInfo(context, anilistId, episodeNum, "sub")
                if (sInfo != null) {
                    val sUrl = (sInfo["subtitle"] as? String) ?: ""
                    val sJson = (sInfo["subtitlesJson"] as? String) ?: "[]"
                    rawSubUrl = extractBestSubtitleUrl(sUrl, sJson)
                }
            } catch (_: Exception) {}
        }

        if (rawSubUrl.isBlank()) {
            try {
                val aniBdResult = com.lagradost.cloudstream3.ui.animebox.extractors.AniBdExtractor.extract(anilistId, episodeNum)
                if (aniBdResult != null && aniBdResult.subtitleUrl.isNotBlank()) {
                    rawSubUrl = aniBdResult.subtitleUrl
                }
            } catch (_: Exception) {}
        }

        if (rawSubUrl.isBlank()) {
            try {
                val fourAnimoResult = com.lagradost.cloudstream3.ui.animebox.extractors.FourAnimoExtractor.extract(anilistId, episodeNum, "sub")
                if (fourAnimoResult != null) {
                    rawSubUrl = fourAnimoResult.subtitleUrl.ifBlank {
                        fourAnimoResult.subtitles.firstOrNull { (it["lang"] ?: "").contains("en", true) || (it["lang"] ?: "").contains("English", true) }?.get("url")
                            ?: fourAnimoResult.subtitles.firstOrNull()?.get("url") ?: ""
                    }
                }
            } catch (_: Exception) {}
        }

        if (rawSubUrl.isBlank()) {
            try {
                val vidHawkResult = com.lagradost.cloudstream3.ui.animebox.extractors.VidHawkExtractor.extract(anilistId, episodeNum, "sub")
                if (vidHawkResult != null) {
                    rawSubUrl = vidHawkResult.subtitleUrl.ifBlank {
                        vidHawkResult.subtitles.firstOrNull { (it["lang"] ?: "").contains("en", true) || (it["lang"] ?: "").contains("English", true) }?.get("url")
                            ?: vidHawkResult.subtitles.firstOrNull()?.get("url") ?: ""
                    }
                }
            } catch (_: Exception) {}
        }

        if (rawSubUrl.isBlank()) {
            try {
                val megaResult = MegaPlayExtractor.extract(anilistId, episodeNum, "sub")
                if (megaResult != null) {
                    rawSubUrl = megaResult.subtitleUrl.ifBlank {
                        megaResult.subtitles.firstOrNull { (it["lang"] ?: "").contains("en", true) || (it["lang"] ?: "").contains("English", true) }?.get("url")
                            ?: megaResult.subtitles.firstOrNull()?.get("url") ?: ""
                    }
                }
            } catch (_: Exception) {}
        }

        if (rawSubUrl.isBlank()) {
            try {
                val beepResult = AnimexExtractor.extractBeepServer(anilistId, episodeNum, "sub")
                if (beepResult != null && beepResult.subtitles.isNotEmpty()) {
                    rawSubUrl = beepResult.subtitles.firstOrNull()?.get("url") ?: ""
                }
            } catch (_: Exception) {}
        }

        val subsDir = File(baseDir, "subtitles").apply { if (!exists()) mkdirs() }
        val vttFile = File(subsDir, "sub_${anilistId}_${episodeNum}.vtt")
        val srtFile = File(subsDir, "sub_${anilistId}_${episodeNum}.srt")

        fun saveToAllLocations(vttContent: String, srtContent: String) {
            try {
                vttFile.writeText(vttContent)
                srtFile.writeText(srtContent)
                if (targetVideoFile != null) {
                    val p = targetVideoFile.parentFile
                    if (p != null) {
                        p.mkdirs()
                        File(p, "${targetVideoFile.nameWithoutExtension}.vtt").writeText(vttContent)
                        File(p, "${targetVideoFile.nameWithoutExtension}.srt").writeText(srtContent)
                        File(p, "sub.vtt").writeText(vttContent)
                        File(p, "local.vtt").writeText(vttContent)
                        File(p, "sub_${anilistId}_${episodeNum}.vtt").writeText(vttContent)
                        val bundleDir = File(p, "${targetVideoFile.nameWithoutExtension}_bundle")
                        bundleDir.mkdirs()
                        File(bundleDir, "sub.vtt").writeText(vttContent)
                        File(bundleDir, "local.vtt").writeText(vttContent)
                        File(bundleDir, "sub_${anilistId}_${episodeNum}.vtt").writeText(vttContent)
                    }
                }
            } catch (_: Exception) {}
        }

        // If local file path was given directly
        if (rawSubUrl.startsWith("/") && (rawSubUrl.startsWith("/data") || rawSubUrl.startsWith("/storage") || File(rawSubUrl).exists())) {
            val localF = File(rawSubUrl)
            if (localF.exists() && localF.length() > 0) {
                try {
                    val text = localF.readText()
                    saveToAllLocations(if (text.contains("WEBVTT")) text else srtToVtt(text), if (text.contains("WEBVTT")) vttToSrt(text) else text)
                } catch (_: Exception) {}
                return@withContext vttFile.absolutePath
            }
        }

        if (rawSubUrl.isBlank()) {
            if (vttFile.exists() && vttFile.length() > 20) {
                if (targetVideoFile != null) {
                    try {
                        val text = vttFile.readText()
                        saveToAllLocations(text, vttToSrt(text))
                    } catch (_: Exception) {}
                }
                return@withContext vttFile.absolutePath
            }
            if (srtFile.exists() && srtFile.length() > 20) {
                return@withContext srtFile.absolutePath
            }
            return@withContext ""
        }

        try {
            val effectiveUrl = when {
                rawSubUrl.startsWith("http://") || rawSubUrl.startsWith("https://") -> rawSubUrl
                rawSubUrl.startsWith("//") -> "https:$rawSubUrl"
                rawSubUrl.startsWith("/p?") || rawSubUrl.contains("4animo", true) -> "https://cdn.4animo.xyz$rawSubUrl"
                rawSubUrl.startsWith("/api") || rawSubUrl.contains("vidhawk", true) -> "https://vidhawk.buzz$rawSubUrl"
                rawSubUrl.startsWith("/stream") || rawSubUrl.contains("megaplay", true) -> "https://megaplay.buzz$rawSubUrl"
                rawSubUrl.contains("playeng", true) || rawSubUrl.contains("nukitashi", true) -> if (rawSubUrl.startsWith("/")) "https://playeng.animeapps.top$rawSubUrl" else rawSubUrl
                rawSubUrl.startsWith("/") -> "https://cdn.4animo.xyz$rawSubUrl"
                else -> rawSubUrl
            }

            val isMegaPlay = effectiveUrl.contains("megaplay", true) || effectiveUrl.contains("megacloud", true) || effectiveUrl.contains("kryntal", true) || effectiveUrl.contains("rapid-cloud", true)
            val is4Animo = effectiveUrl.contains("4animo", true) || effectiveUrl.contains("/p?t=", true)
            val isPlayEng = effectiveUrl.contains("playeng", true) || effectiveUrl.contains("nukitashi", true) || effectiveUrl.contains("animeapps", true)
            val isAnimex = effectiveUrl.contains("animex", true) || effectiveUrl.contains("anidb", true)
            val isVidHawk = effectiveUrl.contains("vidhawk", true)

            val primaryReferer = when {
                isMegaPlay -> "https://megaplay.buzz/"
                is4Animo -> "https://cdn.4animo.xyz/"
                isPlayEng -> "https://playeng.animeapps.top/"
                isAnimex -> "https://animex.one/"
                isVidHawk -> "https://vidhawk.buzz/"
                else -> try {
                    val u = Uri.parse(effectiveUrl)
                    "${u.scheme ?: "https"}://${u.host ?: "megaplay.buzz"}/"
                } catch (_: Exception) { "https://megaplay.buzz/" }
            }

            val primaryOrigin = when {
                isMegaPlay -> "https://megaplay.buzz"
                is4Animo -> "https://cdn.4animo.xyz"
                isPlayEng -> "https://playeng.animeapps.top"
                isAnimex -> "https://animex.one"
                isVidHawk -> "https://vidhawk.buzz"
                else -> ""
            }

            val candidateReferers = mutableListOf(
                primaryReferer to primaryOrigin,
                "https://megaplay.buzz/" to "https://megaplay.buzz",
                "https://cdn.4animo.xyz/" to "https://cdn.4animo.xyz",
                "https://playeng.animeapps.top/" to "https://playeng.animeapps.top",
                "https://animex.one/" to "https://animex.one",
                "" to ""
            ).distinctBy { it.first }

            var subBodyText = ""
            for ((candRef, candOrigin) in candidateReferers) {
                try {
                    val reqBuilder = Request.Builder()
                        .url(effectiveUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                        .header("Accept", "*/*")
                    if (candRef.isNotEmpty()) {
                        reqBuilder.header("Referer", candRef)
                    }
                    if (candOrigin.isNotEmpty()) {
                        reqBuilder.header("Origin", candOrigin)
                    }
                    client.newCall(reqBuilder.build()).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val text = resp.body?.string() ?: ""
                            if (text.isNotBlank() && (text.contains("WEBVTT") || text.contains("-->") || text.contains("#EXTM3U") || text.length > 20)) {
                                subBodyText = text
                            }
                        }
                    }
                } catch (_: Exception) {}
                if (subBodyText.isNotBlank()) break
            }

            if (subBodyText.isNotBlank()) {
                val finalVtt: String
                val finalSrt: String

                if (subBodyText.contains("#EXTM3U")) {
                    // Subtitle playlist (.m3u8) - parse segment playlist
                    val subLines = subBodyText.lines()
                    val subSegUrls = mutableListOf<String>()
                    for (sLine in subLines) {
                        val st = sLine.trim()
                        if (st.isNotEmpty() && !st.startsWith("#")) {
                            subSegUrls.add(resolveHlsUrl(effectiveUrl, st))
                        }
                    }
                    val combinedSub = StringBuilder()
                    var hasHeader = false
                    for (segUrl in subSegUrls) {
                        val segReq = Request.Builder()
                            .url(segUrl)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .build()
                        try {
                            client.newCall(segReq).execute().use { segResp ->
                                if (segResp.isSuccessful) {
                                    val segText = segResp.body?.string() ?: ""
                                    if (segText.isNotBlank()) {
                                        if (!hasHeader) {
                                            combinedSub.append(segText).append("\n")
                                            hasHeader = true
                                        } else {
                                            val filteredLines = segText.lines().filterNot {
                                                it.trim().startsWith("WEBVTT") || it.trim().startsWith("X-TIMESTAMP-MAP")
                                            }
                                            combinedSub.append(filteredLines.joinToString("\n")).append("\n")
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                    val stitched = combinedSub.toString()
                    finalVtt = if (stitched.contains("WEBVTT")) stitched else srtToVtt(stitched)
                    finalSrt = vttToSrt(finalVtt)
                } else if (subBodyText.contains("WEBVTT") || subBodyText.contains("-->")) {
                    if (subBodyText.contains("WEBVTT")) {
                        finalVtt = subBodyText
                        finalSrt = vttToSrt(subBodyText)
                    } else {
                        finalSrt = subBodyText
                        finalVtt = srtToVtt(subBodyText)
                    }
                } else {
                    finalVtt = subBodyText
                    finalSrt = subBodyText
                }

                if (finalVtt.isNotBlank()) {
                    saveToAllLocations(finalVtt, finalSrt)
                    return@withContext vttFile.absolutePath
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (vttFile.exists() && vttFile.length() > 20) return@withContext vttFile.absolutePath
        if (srtFile.exists() && srtFile.length() > 20) return@withContext srtFile.absolutePath
        return@withContext ""
    }

    suspend fun deleteAnimeGroup(anilistId: Int) = withContext(Dispatchers.IO) {
        val currentList = getAllDownloadedEpisodes().toMutableList()
        val targets = currentList.filter { it.anilistId == anilistId }
        targets.forEach { ep ->
            val id = "${ep.anilistId}_${ep.episodeNumber}"
            activeJobs[id]?.cancel()
            activeJobs.remove(id)
            try {
                val f = File(ep.localFilePath)
                if (f.exists()) f.delete()
                val subsDir = File(baseDir, "subtitles")
                File(subsDir, "sub_${ep.anilistId}_${ep.episodeNumber}.vtt").delete()
                File(subsDir, "sub_${ep.anilistId}_${ep.episodeNumber}.srt").delete()
            } catch (_: Exception) {}
        }
        currentList.removeAll { it.anilistId == anilistId }
        saveAll(currentList)
        _episodesState.value = currentList
    }

    suspend fun deleteAllDownloads() = withContext(Dispatchers.IO) {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        try {
            baseDir.listFiles()?.forEach { file ->
                if (file.name != "downloads.json") {
                    file.deleteRecursively()
                }
            }
        } catch (_: Exception) {}
        saveAll(emptyList())
        _episodesState.value = emptyList()
    }

    private fun saveAll(list: List<DownloadedEpisode>) {
        try {
            val arr = JSONArray()
            list.forEach { ep ->
                val obj = JSONObject().apply {
                    put("id", ep.id)
                    put("anilistId", ep.anilistId)
                    put("animeTitle", ep.animeTitle)
                    put("episodeNumber", ep.episodeNumber)
                    put("episodeTitle", ep.episodeTitle)
                    put("coverUrl", ep.coverUrl)
                    put("showCoverUrl", ep.showCoverUrl)
                    put("localFilePath", ep.localFilePath)
                    put("quality", ep.quality)
                    put("fileSizeBytes", ep.fileSizeBytes)
                    put("totalBytes", ep.totalBytes)
                    put("downloadProgress", ep.downloadProgress)
                    put("status", ep.status.name)
                    put("downloadTimestamp", ep.downloadTimestamp)
                    put("localSubtitlePath", ep.localSubtitlePath)
                    put("subtitlesJson", ep.subtitlesJson)
                    put("localBackdropPath", ep.localBackdropPath)
                    put("localLogoPath", ep.localLogoPath)
                    put("introStart", ep.introStart)
                    put("introEnd", ep.introEnd)
                    put("outroStart", ep.outroStart)
                    put("outroEnd", ep.outroEnd)
                    put("streamType", ep.streamType)
                }
                arr.put(obj)
            }
            metadataFile.writeText(arr.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createDownloadHttpClient(): OkHttpClient {
        val sslContext = try {
            javax.net.ssl.SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(object : javax.net.ssl.X509TrustManager {
                    override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
                }), java.security.SecureRandom())
            }
        } catch (_: Exception) { null }

        val builder = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)

        if (sslContext != null) {
            builder.sslSocketFactory(sslContext.socketFactory, object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
            })
            builder.hostnameVerifier { _, _ -> true }
        }
        return builder.build()
    }

    /**
     * Enqueues a direct MP4 or HLS (.m3u8) streaming download with live progress tracking, subtitles & skip intro/outro.
     */
    fun enqueueDownload(
        anilistId: Int,
        animeTitle: String,
        episodeNum: Int,
        episodeTitle: String,
        coverUrl: String,
        showCoverUrl: String = "",
        quality: String,
        resolvedUrl: String,
        referer: String = "",
        subtitleUrl: String = "",
        subtitlesJson: String = "[]",
        introStart: Long = 0L,
        introEnd: Long = 0L,
        outroStart: Long = 0L,
        outroEnd: Long = 0L,
        streamType: String = "sub",
        providedBackdrop: String = "",
        providedLogo: String = ""
    ): Long {
        try {
            val sanitizedTitle = animeTitle.replace(Regex("[^a-zA-Z0-9_.-]"), "_")
            val isHls = resolvedUrl.contains(".m3u8", ignoreCase = true) ||
                resolvedUrl.contains("m3u8", ignoreCase = true) ||
                resolvedUrl.contains("4animo", ignoreCase = true) ||
                resolvedUrl.contains("/p?t=", ignoreCase = true) ||
                resolvedUrl.contains("megaplay", ignoreCase = true) ||
                resolvedUrl.contains("megacloud", ignoreCase = true) ||
                resolvedUrl.contains("kryntal", ignoreCase = true) ||
                resolvedUrl.contains("playeng", ignoreCase = true) ||
                resolvedUrl.contains("nukitashi", ignoreCase = true) ||
                resolvedUrl.contains("animex", ignoreCase = true) ||
                resolvedUrl.contains("anidb", ignoreCase = true)
            val fileName = "${sanitizedTitle}_EP${episodeNum}_${quality.replace(" ", "_")}.mp4"
            val targetFile = File(baseDir, fileName)

            val downloadId = "${anilistId}_${episodeNum}"

            // Cancel any prior job for same episode
            activeJobs[downloadId]?.cancel()

            val effectiveShowCover = showCoverUrl.ifEmpty { coverUrl }

            val initialEpisode = DownloadedEpisode(
                id = downloadId,
                anilistId = anilistId,
                animeTitle = animeTitle,
                episodeNumber = episodeNum,
                episodeTitle = episodeTitle.ifEmpty { "Episode $episodeNum" },
                coverUrl = coverUrl,
                showCoverUrl = effectiveShowCover,
                localFilePath = targetFile.absolutePath,
                quality = quality,
                fileSizeBytes = 0L,
                totalBytes = 0L,
                downloadProgress = 0,
                status = DownloadStatus.DOWNLOADING,
                downloadSpeedText = "Starting...",
                localSubtitlePath = subtitleUrl,
                subtitlesJson = subtitlesJson,
                introStart = introStart,
                introEnd = introEnd,
                outroStart = outroStart,
                outroEnd = outroEnd,
                streamType = streamType
            )

            val currentList = getAllDownloadedEpisodes().toMutableList()
            currentList.removeAll { it.id == initialEpisode.id }
            currentList.add(initialEpisode)
            saveAll(currentList)
            _episodesState.value = currentList

            // Start background foreground service so downloads never get paused or killed on backgrounding
            AnimeDownloadService.startService(context)

            val client = createDownloadHttpClient()

            // Launch streaming download (HLS or Direct MP4) with offline cover and subtitle caching
            // Maximum 10 concurrent active downloads; queued downloads wait automatically
            val job = downloadScope.launch {
                try {
                    if (downloadSemaphore.availablePermits == 0) {
                        updateLiveEpisodeState(downloadId, 0, 0, 0, "Queued", DownloadStatus.PENDING, persist = true)
                    }
                    downloadSemaphore.withPermit {
                        updateLiveEpisodeState(downloadId, 0, 0, 0, "Starting...", DownloadStatus.DOWNLOADING, persist = true)
                        val localCoverDeferred = async { downloadCoverImage(client, coverUrl, anilistId, episodeNum) }
                        val localShowCoverDeferred = async { downloadShowCoverImage(client, effectiveShowCover, anilistId) }
                        val localImagesDeferred = async { downloadBackdropAndLogoImages(client, anilistId, providedBackdrop, providedLogo) }
                        val localSubDeferred = async { downloadSubtitleFile(client, subtitleUrl, subtitlesJson, anilistId, episodeNum, targetFile) }

                        val localCoverPath = localCoverDeferred.await()
                        val localShowCoverPath = localShowCoverDeferred.await()
                        val (localBg, localLogo) = localImagesDeferred.await()
                        val localSubPath = localSubDeferred.await()

                        val updatedInitial = initialEpisode.copy(
                            coverUrl = if (localCoverPath.isNotEmpty()) localCoverPath else coverUrl,
                            showCoverUrl = if (localShowCoverPath.isNotEmpty()) localShowCoverPath else effectiveShowCover,
                            localBackdropPath = if (localBg.isNotEmpty()) localBg else initialEpisode.localBackdropPath,
                            localLogoPath = if (localLogo.isNotEmpty()) localLogo else initialEpisode.localLogoPath,
                            localSubtitlePath = if (localSubPath.isNotEmpty()) localSubPath else subtitleUrl,
                            subtitlesJson = if (localSubPath.isNotEmpty()) localSubtitleTrackJson(subtitlesJson, localSubPath) else subtitlesJson
                        )

                        val list = getAllDownloadedEpisodes().toMutableList()
                        val idx = list.indexOfFirst { it.id == downloadId }
                        if (idx >= 0) {
                            list[idx] = updatedInitial
                            saveAll(list)
                            _episodesState.value = list
                        }

                        if (isHls) {
                            downloadHlsStream(client, resolvedUrl, referer, targetFile, downloadId, updatedInitial)
                        } else {
                            downloadDirectStream(client, resolvedUrl, referer, targetFile, downloadId, updatedInitial, animeTitle, episodeNum, quality)
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) {
                        try { if (targetFile.exists()) targetFile.delete() } catch (_: Exception) {}
                        return@launch
                    }
                    e.printStackTrace()
                    updateLiveEpisodeState(downloadId, 0, 0, 0, "Failed", DownloadStatus.FAILED, persist = true)
                } finally {
                    activeJobs.remove(downloadId)
                }
            }

            activeJobs[downloadId] = job
            return 1L
        } catch (e: Exception) {
            e.printStackTrace()
            return -1L
        }
    }

    private suspend fun downloadDirectStream(
        client: OkHttpClient,
        resolvedUrl: String,
        initialReferer: String,
        targetFile: File,
        downloadId: String,
        initialEpisode: DownloadedEpisode,
        animeTitle: String,
        episodeNum: Int,
        quality: String
    ) {
        val effectiveReferer = getEffectiveReferer(resolvedUrl, initialReferer)
        val origin = try {
            val uri = Uri.parse(effectiveReferer)
            "${uri.scheme}://${uri.host}"
        } catch (_: Exception) { "" }

        val req = Request.Builder()
            .url(resolvedUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Referer", effectiveReferer)
            .apply { if (origin.isNotEmpty()) header("Origin", origin) }
            .build()

        client.newCall(req).execute().use { response ->
            if (response.isSuccessful) {
                val contentType = response.header("Content-Type", "").orEmpty()
                val isM3u8Header = contentType.contains("mpegurl", ignoreCase = true) || contentType.contains("m3u8", ignoreCase = true)
                if (isM3u8Header || resolvedUrl.contains("4animo", true) || resolvedUrl.contains("/p?t=", true)) {
                    downloadHlsStream(client, resolvedUrl, effectiveReferer, targetFile, downloadId, initialEpisode)
                    return
                }

                val body = response.body
                if (body != null) {
                    val contentLength = body.contentLength()
                    var downloadedBytes = 0L
                    val buffer = ByteArray(64 * 1024)

                    var lastUpdateTime = System.currentTimeMillis()
                    var lastUpdatedBytes = 0L
                    var currentSpeed = ""

                    targetFile.outputStream().use { output ->
                        body.byteStream().use { input ->
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                downloadedBytes += read

                                val now = System.currentTimeMillis()
                                val timeDiff = now - lastUpdateTime

                                if (timeDiff >= 350L || downloadedBytes == contentLength) {
                                    val bytesDiff = downloadedBytes - lastUpdatedBytes
                                    val speedBytesPerSec = if (timeDiff > 0) (bytesDiff * 1000L) / timeDiff else 0L
                                    currentSpeed = formatSpeed(speedBytesPerSec)

                                    val progress = if (contentLength > 0) {
                                        ((downloadedBytes * 100) / contentLength).toInt().coerceIn(0, 99)
                                    } else {
                                        0
                                    }

                                    lastUpdateTime = now
                                    lastUpdatedBytes = downloadedBytes

                                    updateLiveEpisodeState(
                                        downloadId,
                                        downloadedBytes,
                                        contentLength,
                                        progress,
                                        currentSpeed,
                                        DownloadStatus.DOWNLOADING,
                                        persist = false
                                    )
                                }
                            }
                        }
                    }

                    val finalSize = targetFile.length()
                    if (finalSize == 0L) throw Exception("Downloaded file is 0 bytes")

                    val (localBg, localLogo) = try { downloadBackdropAndLogoImages(client, initialEpisode.anilistId) } catch (_: Exception) { Pair("", "") }
                    val downloadedSubPath = try { downloadSubtitleFile(client, initialEpisode.localSubtitlePath, initialEpisode.subtitlesJson, initialEpisode.anilistId, initialEpisode.episodeNumber, targetFile) } catch (_: Exception) { "" }

                    val subsDir = File(baseDir, "subtitles")
                    val srtFile = File(subsDir, "sub_${initialEpisode.anilistId}_${initialEpisode.episodeNumber}.srt")
                    val vttFile = File(subsDir, "sub_${initialEpisode.anilistId}_${initialEpisode.episodeNumber}.vtt")
                    val resolvedSub = when {
                        downloadedSubPath.isNotEmpty() -> downloadedSubPath
                        srtFile.exists() && srtFile.length() > 0 -> srtFile.absolutePath
                        vttFile.exists() && vttFile.length() > 0 -> vttFile.absolutePath
                        else -> initialEpisode.localSubtitlePath
                    }

                    val completedEpisode = initialEpisode.copy(
                        fileSizeBytes = finalSize,
                        totalBytes = if (contentLength > 0) contentLength else finalSize,
                        downloadProgress = 100,
                        status = DownloadStatus.COMPLETED,
                        downloadSpeedText = "",
                        localSubtitlePath = resolvedSub,
                        localBackdropPath = localBg.ifEmpty { initialEpisode.localBackdropPath },
                        localLogoPath = localLogo.ifEmpty { initialEpisode.localLogoPath }
                    )
                    saveEpisodeDownload(completedEpisode)
                }
            } else {
                throw Exception("HTTP error code: ${response.code}")
            }
        }
    }

    private fun getEffectiveReferer(url: String, customReferer: String): String {
        if (customReferer.isNotBlank()) return customReferer
        return try {
            val uri = Uri.parse(url)
            val host = uri.host ?: ""
            when {
                host.contains("ok.ru", true) || host.contains("okcdn", true) || host.contains("odnoklassniki", true) -> "https://ok.ru/"
                host.contains("4animo", true) -> "https://cdn.4animo.xyz/"
                host.contains("megaplay", true) -> "https://megaplay.buzz/"
                host.contains("kryntal", true) -> "https://megaplay.buzz/"
                host.contains("playeng", true) || host.contains("nukitashi", true) || host.contains("animeapps", true) -> "https://playeng.animeapps.top/"
                host.contains("animex", true) || host.contains("anidb", true) -> "https://animex.one/"
                host.contains("videas", true) -> "https://app.videas.fr/"
                host.contains("blisseyhusband", true) -> "https://blisseyhusband.net/"
                host.contains("animedekho", true) || host.contains("as-cdn", true) || host.contains("neocdn", true) -> "https://animedekho.app/"
                host.contains("megacloud", true) || host.contains("rabbit", true) || host.contains("dokicloud", true) -> "https://megacloud.tv/"
                host.contains("streamhg", true) || host.contains("multimovies", true) -> "https://multimovies.click/"
                host.contains("kwik", true) || host.contains("animepahe", true) -> "https://kwik.cx/"
                host.contains("anifast", true) || host.contains("miruro", true) -> "https://miruro.tv/"
                host.contains("vidhawk", true) -> "https://vidhawk.buzz/"
                host.contains("vidmoly", true) -> "https://vidmoly.net/"
                host.isNotEmpty() -> "${uri.scheme ?: "https"}://$host/"
                else -> "https://kwik.cx/"
            }
        } catch (_: Exception) {
            "https://kwik.cx/"
        }
    }

    private fun resolveHlsUrl(baseUrl: String, pathOrUrl: String): String {
        val trimmed = pathOrUrl.trim().removeSurrounding("\"")
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }
        return try {
            val base = java.net.URL(baseUrl)
            java.net.URL(base, trimmed).toString()
        } catch (_: Exception) {
            if (trimmed.startsWith("/")) {
                val protocol = if (baseUrl.startsWith("https")) "https://" else "http://"
                val host = baseUrl.substringAfter("://").substringBefore("/")
                "$protocol$host$trimmed"
            } else {
                val dir = baseUrl.substringBeforeLast("/")
                "$dir/$trimmed"
            }
        }
    }

    private data class HlsVariant(
        val url: String,
        val width: Int,
        val height: Int,
        val bandwidth: Long
    )

    private fun parseMasterPlaylistVariants(baseUrl: String, content: String): List<HlsVariant> {
        val variants = mutableListOf<HlsVariant>()
        val lines = content.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                val resMatch = Regex("RESOLUTION=(\\d+)x(\\d+)").find(line)
                val width = resMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                val height = resMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0
                val bwMatch = Regex("BANDWIDTH=(\\d+)").find(line)
                val bw = bwMatch?.groupValues?.get(1)?.toLongOrNull() ?: 0L

                var nextIdx = i + 1
                while (nextIdx < lines.size) {
                    val nextLine = lines[nextIdx].trim()
                    if (nextLine.isNotEmpty() && !nextLine.startsWith("#")) {
                        val resolved = resolveHlsUrl(baseUrl, nextLine)
                        variants.add(HlsVariant(resolved, width, height, bw))
                        break
                    }
                    nextIdx++
                }
            }
            i++
        }
        return variants
    }

    private suspend fun downloadHlsStream(
        client: OkHttpClient,
        m3u8Url: String,
        initialReferer: String,
        targetFile: File,
        downloadId: String,
        initialEpisode: DownloadedEpisode
    ) = withContext(Dispatchers.IO) {
        val referer = getEffectiveReferer(m3u8Url, initialReferer)
        val origin = try {
            val uri = Uri.parse(referer)
            "${uri.scheme}://${uri.host}"
        } catch (_: Exception) { "" }

        // 1. Fetch Master Playlist
        val req = Request.Builder()
            .url(m3u8Url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
            .header("Referer", referer)
            .apply { if (origin.isNotEmpty()) header("Origin", origin) }
            .build()

        val playlistContent = client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code} fetching playlist: $m3u8Url")
            resp.body?.string() ?: ""
        }
        if (playlistContent.isBlank()) throw Exception("Empty HLS playlist")

        // 2. Parse Video & Audio streams from Master Playlist
        var videoMediaPlaylistUrl = m3u8Url
        var actualVideoMediaContent = playlistContent
        var audioMediaPlaylistUrl: String? = null
        var actualAudioMediaContent: String? = null

        if (playlistContent.contains("#EXT-X-STREAM-INF")) {
            val variants = parseMasterPlaylistVariants(m3u8Url, playlistContent)
            if (variants.isNotEmpty()) {
                val targetHeight = Regex("(\\d{3,4})p", RegexOption.IGNORE_CASE)
                    .find(initialEpisode.quality)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                val matchedVariant = if (targetHeight > 0) {
                    variants.firstOrNull { it.height == targetHeight }
                        ?: variants.minByOrNull { kotlin.math.abs(it.height - targetHeight) }
                } else null
                val bestVariant = matchedVariant ?: variants.maxByOrNull { (it.height * 1000L) + (it.bandwidth / 1000L) } ?: variants.first()
                videoMediaPlaylistUrl = bestVariant.url

                val mediaReq = Request.Builder()
                    .url(videoMediaPlaylistUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Referer", referer)
                    .apply { if (origin.isNotEmpty()) header("Origin", origin) }
                    .build()

                actualVideoMediaContent = client.newCall(mediaReq).execute().use { resp ->
                    if (!resp.isSuccessful) throw Exception("HTTP ${resp.code} fetching variant playlist: $videoMediaPlaylistUrl")
                    resp.body?.string() ?: ""
                }
            }

            // Check for separate audio track (#EXT-X-MEDIA:TYPE=AUDIO)
            val audioLines = playlistContent.lines().filter { it.trim().startsWith("#EXT-X-MEDIA:TYPE=AUDIO") }
            if (audioLines.isNotEmpty()) {
                val reqType = initialEpisode.streamType.lowercase()
                val qLower = initialEpisode.quality.lowercase()
                val chosenAudioLine = when {
                    reqType == "hindi" || qLower.contains("hindi") -> {
                        audioLines.firstOrNull {
                            it.contains("hin", ignoreCase = true) ||
                            it.contains("hi\"", ignoreCase = true) ||
                            it.contains("hi,", ignoreCase = true) ||
                            it.contains("Hindi", ignoreCase = true)
                        } ?: audioLines.firstOrNull { it.contains("DEFAULT=YES", ignoreCase = true) }
                          ?: audioLines.first()
                    }
                    reqType in listOf("french", "vf", "fr") || qLower.contains("french") || qLower.contains("vf") -> {
                        audioLines.firstOrNull {
                            it.contains("fra", ignoreCase = true) ||
                            it.contains("fre", ignoreCase = true) ||
                            it.contains("fr\"", ignoreCase = true) ||
                            it.contains("fr,", ignoreCase = true) ||
                            it.contains("French", ignoreCase = true) ||
                            it.contains("Français", ignoreCase = true)
                        } ?: audioLines.firstOrNull { it.contains("DEFAULT=YES", ignoreCase = true) }
                          ?: audioLines.first()
                    }
                    reqType in listOf("italian", "ita", "it") || qLower.contains("italian") || qLower.contains("ita") -> {
                        audioLines.firstOrNull {
                            it.contains("ita", ignoreCase = true) ||
                            it.contains("it\"", ignoreCase = true) ||
                            it.contains("it,", ignoreCase = true) ||
                            it.contains("Italian", ignoreCase = true) ||
                            it.contains("Italiano", ignoreCase = true)
                        } ?: audioLines.firstOrNull { it.contains("DEFAULT=YES", ignoreCase = true) }
                          ?: audioLines.first()
                    }
                    reqType in listOf("spanish", "es", "spanish_latino", "latino", "spanish_castellano", "castellano") || qLower.contains("spanish") || qLower.contains("latino") || qLower.contains("castellano") -> {
                        audioLines.firstOrNull {
                            it.contains("spa", ignoreCase = true) ||
                            it.contains("es\"", ignoreCase = true) ||
                            it.contains("es,", ignoreCase = true) ||
                            it.contains("Spanish", ignoreCase = true) ||
                            it.contains("Español", ignoreCase = true) ||
                            it.contains("Latino", ignoreCase = true) ||
                            it.contains("Castellano", ignoreCase = true)
                        } ?: audioLines.firstOrNull { it.contains("DEFAULT=YES", ignoreCase = true) }
                          ?: audioLines.first()
                    }
                    reqType in listOf("german", "deutsch", "de", "ger") || qLower.contains("german") || qLower.contains("deutsch") -> {
                        audioLines.firstOrNull {
                            it.contains("ger", ignoreCase = true) ||
                            it.contains("deu", ignoreCase = true) ||
                            it.contains("de\"", ignoreCase = true) ||
                            it.contains("de,", ignoreCase = true) ||
                            it.contains("German", ignoreCase = true) ||
                            it.contains("Deutsch", ignoreCase = true)
                        } ?: audioLines.firstOrNull { it.contains("DEFAULT=YES", ignoreCase = true) }
                          ?: audioLines.first()
                    }
                    reqType == "dub" || qLower.contains("dub") -> {
                        audioLines.firstOrNull {
                            it.contains("eng", ignoreCase = true) ||
                            it.contains("en\"", ignoreCase = true) ||
                            it.contains("en,", ignoreCase = true) ||
                            it.contains("English", ignoreCase = true)
                        } ?: audioLines.firstOrNull { it.contains("DEFAULT=YES", ignoreCase = true) }
                          ?: audioLines.first()
                    }
                    else -> {
                        audioLines.firstOrNull {
                            it.contains("jpn", ignoreCase = true) ||
                            it.contains("ja\"", ignoreCase = true) ||
                            it.contains("ja,", ignoreCase = true) ||
                            it.contains("Japanese", ignoreCase = true)
                        } ?: audioLines.firstOrNull { it.contains("DEFAULT=YES", ignoreCase = true) }
                          ?: audioLines.first()
                    }
                }

                val uriMatch = Regex("URI=\"([^\"]+)\"").find(chosenAudioLine)
                if (uriMatch != null) {
                    val rawAudioUri = uriMatch.groupValues[1]
                    audioMediaPlaylistUrl = resolveHlsUrl(m3u8Url, rawAudioUri)

                    val audioReq = Request.Builder()
                        .url(audioMediaPlaylistUrl!!)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Referer", referer)
                        .apply { if (origin.isNotEmpty()) header("Origin", origin) }
                        .build()

                    actualAudioMediaContent = try {
                        client.newCall(audioReq).execute().use { resp ->
                            if (resp.isSuccessful) resp.body?.string() else null
                        }
                    } catch (_: Exception) { null }
                }
            }
        }

        // Helper segment parser with durations, ranges, and init segments
        data class DetailedSegment(val url: String, val durationSec: Double, val byteRange: String? = null)
        data class ParsedMediaPlaylist(
            val segments: List<DetailedSegment>,
            val initSegUrl: String?,
            val keyUrl: String?,
            val keyIv: ByteArray?,
            val mediaSequence: Long
        )

        fun getSequenceIv(seq: Long): ByteArray {
            val iv = ByteArray(16)
            var num = seq
            for (i in 15 downTo 8) {
                iv[i] = (num and 0xFF).toByte()
                num = num shr 8
            }
            return iv
        }

        fun parseMediaPlaylist(mediaContent: String, mediaUrl: String): ParsedMediaPlaylist {
            val segments = mutableListOf<DetailedSegment>()
            var initSegUrl: String? = null
            var keyUrlFound: String? = null
            var keyIvFound: ByteArray? = null
            var currentRange: String? = null
            var currentRangeOffset = 0L
            var currentDuration = 6.0
            var mediaSequence = 0L

            val lines = mediaContent.lines()
            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                        mediaSequence = trimmed.substringAfter("#EXT-X-MEDIA-SEQUENCE:").trim().toLongOrNull() ?: 0L
                    }
                    trimmed.startsWith("#EXTINF:") -> {
                        val durStr = trimmed.substringAfter("#EXTINF:").substringBefore(",").trim()
                        currentDuration = durStr.toDoubleOrNull() ?: 6.0
                    }
                    trimmed.startsWith("#EXT-X-BYTERANGE:") -> {
                        val rangeSpec = trimmed.substringAfter("#EXT-X-BYTERANGE:").trim()
                        if (rangeSpec.contains("@")) {
                            val parts = rangeSpec.split("@")
                            val length = parts[0].toLongOrNull() ?: 0L
                            val offset = parts[1].toLongOrNull() ?: 0L
                            currentRangeOffset = offset + length
                            currentRange = "bytes=$offset-${offset + length - 1}"
                        } else {
                            val length = rangeSpec.toLongOrNull() ?: 0L
                            val offset = currentRangeOffset
                            currentRangeOffset += length
                            currentRange = "bytes=$offset-${offset + length - 1}"
                        }
                    }
                    trimmed.startsWith("#EXT-X-MAP:") -> {
                        val uriMatch = Regex("URI=\"([^\"]+)\"").find(trimmed)
                        if (uriMatch != null) {
                            initSegUrl = resolveHlsUrl(mediaUrl, uriMatch.groupValues[1])
                        }
                    }
                    trimmed.startsWith("#EXT-X-KEY:") -> {
                        if (trimmed.contains("METHOD=AES-128")) {
                            val uriMatch = Regex("URI=\"([^\"]+)\"").find(trimmed)
                            if (uriMatch != null) {
                                keyUrlFound = resolveHlsUrl(mediaUrl, uriMatch.groupValues[1])
                            }
                            val ivMatch = Regex("IV=0x([0-9a-fA-F]+)").find(trimmed)
                            if (ivMatch != null) {
                                val hex = ivMatch.groupValues[1].padStart(32, '0')
                                keyIvFound = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                            }
                        }
                    }
                    trimmed.isNotEmpty() && !trimmed.startsWith("#") -> {
                        val fullSegUrl = resolveHlsUrl(mediaUrl, trimmed)
                        segments.add(DetailedSegment(fullSegUrl, currentDuration, currentRange))
                        currentRange = null
                    }
                }
            }
            return ParsedMediaPlaylist(segments, initSegUrl, keyUrlFound, keyIvFound, mediaSequence)
        }

        val videoParsed = parseMediaPlaylist(actualVideoMediaContent, videoMediaPlaylistUrl)
        val videoSegments = videoParsed.segments
        val videoInitUrl = videoParsed.initSegUrl
        val videoKeyUrl = videoParsed.keyUrl
        val videoKeyIv = videoParsed.keyIv
        val videoMediaSeq = videoParsed.mediaSequence

        if (videoSegments.isEmpty()) throw Exception("No media segments found in HLS playlist")

        val audioParsed = if (!actualAudioMediaContent.isNullOrBlank() && audioMediaPlaylistUrl != null) {
            parseMediaPlaylist(actualAudioMediaContent!!, audioMediaPlaylistUrl!!)
        } else null

        val audioSegments = audioParsed?.segments ?: emptyList()
        val audioInitUrl = audioParsed?.initSegUrl
        val audioKeyUrl = audioParsed?.keyUrl
        val audioKeyIv = audioParsed?.keyIv
        val audioMediaSeq = audioParsed?.mediaSequence ?: 0L

        // Episode folder for bundle
        val epDir = File(targetFile.parentFile, "${targetFile.nameWithoutExtension}_bundle")
        epDir.mkdirs()

        val totalTasks = videoSegments.size + audioSegments.size + (if (videoInitUrl != null) 1 else 0) + (if (audioInitUrl != null) 1 else 0)
        val completedTasks = java.util.concurrent.atomic.AtomicInteger(0)
        val downloadedBytes = java.util.concurrent.atomic.AtomicLong(0L)
        val progressLock = Any()
        var lastUpdateTime = System.currentTimeMillis()
        var lastUpdatedBytes = 0L
        var currentSpeed = ""

        fun notifyProgress() {
            synchronized(progressLock) {
                val now = System.currentTimeMillis()
                val timeDiff = now - lastUpdateTime
                val completed = completedTasks.get()
                val bytes = downloadedBytes.get()
                if (timeDiff >= 350L || completed == totalTasks) {
                    val bytesDiff = bytes - lastUpdatedBytes
                    val speedBytesPerSec = if (timeDiff > 0) (bytesDiff * 1000L) / timeDiff else 0L
                    currentSpeed = formatSpeed(speedBytesPerSec)
                    val progress = ((completed * 100) / totalTasks.coerceAtLeast(1)).coerceIn(0, 99)
                    lastUpdateTime = now
                    lastUpdatedBytes = bytes

                    updateLiveEpisodeState(
                        downloadId,
                        bytes,
                        bytes,
                        progress,
                        currentSpeed,
                        DownloadStatus.DOWNLOADING,
                        persist = false
                    )
                }
            }
        }

        // 3. Download Init Segments
        var localVideoInitFile: File? = null
        if (videoInitUrl != null) {
            localVideoInitFile = File(epDir, "video_init.mp4")
            val initReq = Request.Builder().url(videoInitUrl).header("User-Agent", "Mozilla/5.0").header("Referer", referer).apply { if (origin.isNotEmpty()) header("Origin", origin) }.build()
            client.newCall(initReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val b = resp.body?.bytes()
                    if (b != null) {
                        localVideoInitFile.writeBytes(b)
                        downloadedBytes.addAndGet(b.size.toLong())
                    }
                }
            }
            completedTasks.incrementAndGet()
            notifyProgress()
        }

        var localAudioInitFile: File? = null
        if (audioInitUrl != null) {
            localAudioInitFile = File(epDir, "audio_init.mp4")
            val initReq = Request.Builder().url(audioInitUrl).header("User-Agent", "Mozilla/5.0").header("Referer", referer).apply { if (origin.isNotEmpty()) header("Origin", origin) }.build()
            client.newCall(initReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val b = resp.body?.bytes()
                    if (b != null) {
                        localAudioInitFile.writeBytes(b)
                        downloadedBytes.addAndGet(b.size.toLong())
                    }
                }
            }
            completedTasks.incrementAndGet()
            notifyProgress()
        }

        // 4. Download Video Segments (with AES-128 key handling & decryption)
        var videoKeyBytes: ByteArray? = null
        if (videoKeyUrl != null) {
            val keyReq = Request.Builder()
                .url(videoKeyUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", referer)
                .apply { if (origin.isNotEmpty()) header("Origin", origin) }
                .build()
            for (attempt in 1..3) {
                try {
                    client.newCall(keyReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            videoKeyBytes = resp.body?.bytes()
                        }
                    }
                    if (videoKeyBytes != null && videoKeyBytes!!.size == 16) break
                } catch (_: Exception) {
                    kotlinx.coroutines.delay(500)
                }
            }
        }

        val isFmp4 = videoInitUrl != null || videoSegments.firstOrNull()?.url?.contains(".m4s") == true
        val videoExt = if (isFmp4) "m4s" else "ts"
        val localVideoPlaylist = StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:12\n#EXT-X-MEDIA-SEQUENCE:0\n#EXT-X-PLAYLIST-TYPE:VOD\n")
        if (localVideoInitFile != null) {
            localVideoPlaylist.append("#EXT-X-MAP:URI=\"${localVideoInitFile.name}\"\n")
        }

        suspend fun downloadSegments(
            segments: List<DetailedSegment>,
            prefix: String,
            extension: String,
            keyBytes: ByteArray?,
            keyIv: ByteArray?,
            mediaSequence: Long
        ): List<File?> = coroutineScope {
            segments.indices.toList().chunked(24).flatMap { batch ->
                batch.map { index ->
                    async(Dispatchers.IO) {
                        hlsSegmentSemaphore.withPermit {
                            val segment = segments[index]
                            val segmentFile = File(epDir, "${prefix}_${index}.$extension")
                            var bytes: ByteArray? = null
                            for (attempt in 1..3) {
                                try {
                                    val request = Request.Builder()
                                        .url(segment.url)
                                        .header("User-Agent", "Mozilla/5.0")
                                        .header("Referer", referer)
                                        .apply {
                                            if (origin.isNotEmpty()) header("Origin", origin)
                                            segment.byteRange?.let { header("Range", it) }
                                        }
                                        .build()
                                    client.newCall(request).execute().use { response ->
                                        if (response.isSuccessful) bytes = response.body?.bytes()
                                    }
                                    if (bytes != null) break
                                } catch (_: Exception) {
                                    if (attempt < 3) kotlinx.coroutines.delay(350L * attempt)
                                }
                            }
                            val payload = bytes
                            if (payload != null) {
                                val output = if (keyBytes?.size == 16) {
                                    try { decryptAes128(payload, keyBytes, keyIv ?: getSequenceIv(mediaSequence + index)) }
                                    catch (_: Exception) { payload }
                                } else payload
                                segmentFile.writeBytes(output)
                                downloadedBytes.addAndGet(output.size.toLong())
                            }
                            completedTasks.incrementAndGet()
                            notifyProgress()
                            segmentFile.takeIf { it.exists() && it.length() > 0L }
                        }
                    }
                }.awaitAll()
            }
        }

        val videoSegmentFiles = downloadSegments(
            videoSegments, "v", videoExt, videoKeyBytes, videoKeyIv, videoMediaSeq
        )

        targetFile.outputStream().use { concatOut ->
            if (localVideoInitFile != null && localVideoInitFile.exists()) {
                concatOut.write(localVideoInitFile.readBytes())
            }

            videoSegments.forEachIndexed { index, seg ->
                val segFile = videoSegmentFiles[index]
                if (segFile != null) {
                    segFile.inputStream().use { it.copyTo(concatOut) }
                    localVideoPlaylist.append("#EXTINF:${seg.durationSec},\n${segFile.name}\n")
                }
            }
        }
        localVideoPlaylist.append("#EXT-X-ENDLIST\n")
        val localVideoPlaylistFile = File(epDir, "video.m3u8")
        localVideoPlaylistFile.writeText(localVideoPlaylist.toString())

        // 5. Download Audio Segments if separate (with AES-128 key handling & decryption)
        var localAudioPlaylistFile: File? = null
        if (audioSegments.isNotEmpty()) {
            val audioExt = if (audioInitUrl != null || audioSegments.firstOrNull()?.url?.contains(".m4s") == true) "m4s" else "ts"
            val localAudioPlaylist = StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:12\n#EXT-X-MEDIA-SEQUENCE:0\n#EXT-X-PLAYLIST-TYPE:VOD\n")
            if (localAudioInitFile != null) {
                localAudioPlaylist.append("#EXT-X-MAP:URI=\"${localAudioInitFile.name}\"\n")
            }

            var audioKeyBytes: ByteArray? = null
            if (audioKeyUrl != null) {
                val aKeyReq = Request.Builder().url(audioKeyUrl).header("User-Agent", "Mozilla/5.0").header("Referer", referer).apply { if (origin.isNotEmpty()) header("Origin", origin) }.build()
                for (attempt in 1..3) {
                    try {
                        client.newCall(aKeyReq).execute().use { resp ->
                            if (resp.isSuccessful) {
                                audioKeyBytes = resp.body?.bytes()
                            }
                        }
                        if (audioKeyBytes != null && audioKeyBytes!!.size == 16) break
                    } catch (_: Exception) {
                        kotlinx.coroutines.delay(500)
                    }
                }
            }

            val audioSegmentFiles = downloadSegments(
                audioSegments, "a", audioExt, audioKeyBytes, audioKeyIv, audioMediaSeq
            )
            audioSegments.forEachIndexed { index, seg ->
                val segFile = audioSegmentFiles[index]
                if (segFile != null) {
                    localAudioPlaylist.append("#EXTINF:${seg.durationSec},\n${segFile.name}\n")
                }
            }
            localAudioPlaylist.append("#EXT-X-ENDLIST\n")
            localAudioPlaylistFile = File(epDir, "audio.m3u8")
            localAudioPlaylistFile.writeText(localAudioPlaylist.toString())
        }


        // 6. Generate Master local.m3u8 playlist file
        val masterPlaylistFile = File(epDir, "local.m3u8")
        if (localAudioPlaylistFile != null && localAudioPlaylistFile.exists() && localAudioPlaylistFile.length() > 0) {
            val audioLang = when (initialEpisode.streamType.lowercase()) {
                "hindi" -> "hi"
                "french", "vf", "fr" -> "fr"
                "italian", "ita", "it" -> "it"
                "spanish_latino", "latino" -> "es-MX"
                "spanish_castellano", "castellano" -> "es-ES"
                "german", "deutsch", "de" -> "de"
                "dub" -> "en"
                else -> "ja"
            }
            val audioName = when (initialEpisode.streamType.lowercase()) {
                "hindi" -> "Hindi"
                "french", "vf", "fr" -> "French (VF)"
                "italian", "ita", "it" -> "Italian (ITA)"
                "spanish_latino", "latino" -> "Spanish (Latino)"
                "spanish_castellano", "castellano" -> "Spanish (Castellano)"
                "german", "deutsch", "de" -> "German (Deutsch)"
                "dub" -> "English"
                else -> "Japanese"
            }
            val masterContent = """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",LANGUAGE="$audioLang",NAME="$audioName",DEFAULT=YES,AUTOSELECT=YES,URI="${localAudioPlaylistFile.name}"
                #EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=2500000,AUDIO="audio"
                ${localVideoPlaylistFile.name}
            """.trimIndent()
            masterPlaylistFile.writeText(masterContent)
        } else {
            val masterContent = """
                #EXTM3U
                #EXT-X-VERSION:3
                #EXT-X-STREAM-INF:PROGRAM-ID=1,BANDWIDTH=2500000
                ${localVideoPlaylistFile.name}
            """.trimIndent()
            masterPlaylistFile.writeText(masterContent)
        }

        val finalLocalPath = masterPlaylistFile.absolutePath
        val finalSize = downloadedBytes.get()

        val (localBg, localLogo) = try { downloadBackdropAndLogoImages(client, initialEpisode.anilistId) } catch (_: Exception) { Pair("", "") }
        val downloadedSubPath = try { downloadSubtitleFile(client, initialEpisode.localSubtitlePath, initialEpisode.subtitlesJson, initialEpisode.anilistId, initialEpisode.episodeNumber, targetFile) } catch (_: Exception) { "" }

        val subsDir = File(baseDir, "subtitles")
        val srtFile = File(subsDir, "sub_${initialEpisode.anilistId}_${initialEpisode.episodeNumber}.srt")
        val vttFile = File(subsDir, "sub_${initialEpisode.anilistId}_${initialEpisode.episodeNumber}.vtt")
        val resolvedSub = when {
            downloadedSubPath.isNotEmpty() -> downloadedSubPath
            srtFile.exists() && srtFile.length() > 0 -> srtFile.absolutePath
            vttFile.exists() && vttFile.length() > 0 -> vttFile.absolutePath
            else -> initialEpisode.localSubtitlePath
        }

        val completedEpisode = initialEpisode.copy(
            localFilePath = finalLocalPath,
            fileSizeBytes = finalSize,
            totalBytes = finalSize,
            downloadProgress = 100,
            status = DownloadStatus.COMPLETED,
            downloadSpeedText = "",
            localSubtitlePath = resolvedSub,
            localBackdropPath = localBg.ifEmpty { initialEpisode.localBackdropPath },
            localLogoPath = localLogo.ifEmpty { initialEpisode.localLogoPath }
        )
        saveEpisodeDownload(completedEpisode)
    }

    private fun decryptAes128(cipherData: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val keySpec = javax.crypto.spec.SecretKeySpec(key, "AES")
        val ivSpec = javax.crypto.spec.IvParameterSpec(iv)
        return try {
            val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS7Padding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, ivSpec)
            cipher.doFinal(cipherData)
        } catch (_: Exception) {
            try {
                val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
                cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, ivSpec)
                cipher.doFinal(cipherData)
            } catch (_: Exception) {
                val cipher = javax.crypto.Cipher.getInstance("AES/CBC/NoPadding")
                cipher.init(javax.crypto.Cipher.DECRYPT_MODE, keySpec, ivSpec)
                cipher.doFinal(cipherData)
            }
        }
    }

    private fun updateLiveEpisodeState(
        id: String,
        downloaded: Long,
        total: Long,
        progress: Int,
        speed: String,
        status: DownloadStatus,
        persist: Boolean = false
    ) {
        val current = _episodesState.value.toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            val item = current[index].copy(
                fileSizeBytes = downloaded,
                totalBytes = if (total > 0) total else downloaded,
                downloadProgress = progress,
                downloadSpeedText = speed,
                status = status
            )
            current[index] = item
            _episodesState.value = current
            if (persist) {
                val list = getAllDownloadedEpisodes().toMutableList()
                val fileIdx = list.indexOfFirst { it.id == id }
                if (fileIdx >= 0) {
                    list[fileIdx] = item
                } else {
                    list.add(item)
                }
                saveAll(list)
            }
        }
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSec / (1024.0 * 1024.0))
            bytesPerSec >= 1024 -> "${bytesPerSec / 1024} KB/s"
            else -> "$bytesPerSec B/s"
        }
    }

    companion object {
        private val _episodesState = MutableStateFlow<List<DownloadedEpisode>>(emptyList())
        val episodesState: StateFlow<List<DownloadedEpisode>> = _episodesState.asStateFlow()

        @Volatile
        private var INSTANCE: AnimeDownloadManager? = null

        fun getInstance(context: Context): AnimeDownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AnimeDownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        suspend fun resolveStreamQualities(
            streamUrl: String,
            referer: String = ""
        ): List<String> = withContext(Dispatchers.IO) {
            if (streamUrl.contains(".mp4", ignoreCase = true)) {
                return@withContext listOf("Source")
            }
            if (!streamUrl.contains(".m3u8", ignoreCase = true)) {
                return@withContext listOf("Source")
            }
            val qualities = linkedSetOf<String>()
            try {
                val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()
                val req = Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .apply { if (referer.isNotEmpty()) header("Referer", referer) }
                    .build()
                val content = client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                }
                val lines = content.lineSequence().map(String::trim).toList()
                for (line in lines) {
                    if (!line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) continue
                    val height = Regex("RESOLUTION=\\d+x(\\d+)", RegexOption.IGNORE_CASE)
                        .find(line)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                    val namedQuality = Regex("NAME=\"([^\"]+)\"", RegexOption.IGNORE_CASE)
                        .find(line)?.groupValues?.getOrNull(1)
                    val quality = when {
                        height > 0 -> "${height}p"
                        !namedQuality.isNullOrBlank() -> namedQuality
                        else -> null
                    }
                    if (quality != null) qualities.add(quality)
                }
            } catch (_: Exception) {}
            qualities.sortedWith(
                compareByDescending<String> { Regex("\\d{3,4}").find(it)?.value?.toIntOrNull() ?: -1 }
                    .thenBy { it.lowercase() }
            ).ifEmpty { listOf("Source") }
        }
    }
}
