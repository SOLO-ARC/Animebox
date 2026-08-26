package com.lagradost.cloudstream3.utils.downloader

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import coil3.Extras
import coil3.SingletonImageLoader
import coil3.asDrawable
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.lagradost.cloudstream3.R
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.mvvm.safe
import com.lagradost.cloudstream3.ui.player.SubtitleData
import com.lagradost.cloudstream3.ui.result.ExtractorSubtitleLink
import com.lagradost.cloudstream3.utils.Coroutines.ioSafe
import com.lagradost.cloudstream3.utils.UiText
import com.lagradost.cloudstream3.utils.downloader.DownloadFileManagement.getBasePath
import com.lagradost.cloudstream3.utils.downloader.DownloadFileManagement.getDefaultDir
import com.lagradost.cloudstream3.utils.downloader.DownloadFileManagement.getFileName
import com.lagradost.cloudstream3.utils.downloader.DownloadFileManagement.getFolder
import com.lagradost.cloudstream3.utils.txt
import com.lagradost.safefile.closeQuietly
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/** Separate object with helper functions for the downloader */
object DownloadUtils {
    private val cachedBitmaps = ConcurrentHashMap<String, Bitmap>()
    internal fun Context.getImageBitmapFromUrl(
        url: String,
        headers: Map<String, String>? = null
    ): Bitmap? = safe {
        cachedBitmaps[url]?.let {
            return@safe it
        }

        val imageLoader = SingletonImageLoader.get(this)

        val request = ImageRequest.Builder(this)
            .data(url)
            .apply {
                headers?.forEach { (key, value) ->
                    extras[Extras.Key<String>(key)] = value
                }
            }
            .build()

        val bitmap = runBlocking {
            val result = imageLoader.execute(request)
            (result as? SuccessResult)?.image?.asDrawable(applicationContext.resources)
                ?.toBitmap()
        }

        bitmap?.let {
            cachedBitmaps.putIfAbsent(url, it)
        }

        return@safe bitmap
    }

    //calculate the time
    internal fun getEstimatedTimeLeft(
        context: Context,
        bytesPerSecond: Long,
        progress: Long,
        total: Long
    ): String {
        if (bytesPerSecond <= 0) return ""
        val timeInSec = (total - progress) / bytesPerSecond
        val hrs = timeInSec / 3600
        val mins = (timeInSec % 3600) / 60
        val secs = timeInSec % 60
        val timeFormated: UiText? = when {
            hrs > 0 -> txt(
                R.string.download_time_left_hour_min_sec_format,
                hrs,
                mins,
                secs
            )

            mins > 0 -> txt(
                R.string.download_time_left_min_sec_format,
                mins,
                secs
            )

            secs > 0 -> txt(
                R.string.download_time_left_sec_format,
                secs
            )

            else -> null
        }
        return timeFormated?.asString(context) ?: ""
    }

    internal suspend fun downloadSubtitle(
        context: Context?,
        link: ExtractorSubtitleLink,
        fileName: String,
        folder: String
    ): DownloadObjects.DownloadStatus? = withContext(Dispatchers.IO) {
        val ctx = context ?: return@withContext null
        val ext = when {
            link.url.contains(".ass", ignoreCase = true) || link.url.contains(".ssa", ignoreCase = true) -> "ass"
            link.url.contains(".srt", ignoreCase = true) -> "srt"
            link.url.contains(".ttml", ignoreCase = true) || link.url.contains(".xml", ignoreCase = true) -> "ttml"
            link.url.contains(".vtt", ignoreCase = true) -> "vtt"
            else -> "vtt"
        }
        val cleanName = link.name.trim()
        val subDisplayName = if (cleanName.isBlank()) fileName else "$fileName $cleanName"

        try {
            val (baseFile, _) = ctx.getBasePath()
            val targetBase = baseFile ?: getDefaultDir(ctx) ?: return@withContext null
            val subStream = VideoDownloadManager.setupStream(
                targetBase,
                subDisplayName,
                folder,
                ext,
                false
            )

            val headers = link.headers.toMutableMap()
            if (!headers.containsKey("user-agent") && !headers.containsKey("User-Agent")) {
                headers["user-agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Safari/537.36"
            }
            if (link.referer.isNotBlank() && !headers.containsKey("referer") && !headers.containsKey("Referer")) {
                headers["referer"] = link.referer
            }

            val response = app.get(
                url = link.url.replace(" ", "%20"),
                headers = headers,
                referer = link.referer,
                verify = false
            )

            if (!response.isSuccessful) {
                subStream.delete()
                return@withContext DownloadObjects.DownloadStatus(retrySame = false, tryNext = true, success = false)
            }

            val bytes = response.body.bytes()
            if (bytes.isEmpty()) {
                subStream.delete()
                return@withContext DownloadObjects.DownloadStatus(retrySame = false, tryNext = true, success = false)
            }

            val outputStream = subStream.open()
            outputStream.write(bytes)
            outputStream.flush()
            outputStream.closeQuietly()

            return@withContext DownloadObjects.DownloadStatus(retrySame = false, tryNext = false, success = true)
        } catch (t: Throwable) {
            logError(t)
            // Fallback to downloadThing if direct fetch fails
            return@withContext try {
                VideoDownloadManager.downloadThing(
                    ctx,
                    link,
                    subDisplayName,
                    folder,
                    ext,
                    false,
                    null,
                    createNotificationCallback = {}
                )
            } catch (t2: Throwable) {
                logError(t2)
                null
            }
        }
    }

    fun downloadSubtitle(
        context: Context?,
        link: SubtitleData,
        meta: DownloadObjects.DownloadEpisodeMetadata,
    ) {
        context?.let { ctx ->
            val fileName = getFileName(ctx, meta)
            val folder = getFolder(meta.type ?: return, meta.mainName)
            ioSafe {
                downloadSubtitle(
                    ctx,
                    ExtractorSubtitleLink(
                        link.name,
                        link.url,
                        link.headers["referer"] ?: link.headers["Referer"] ?: "",
                        link.headers
                    ),
                    fileName,
                    folder
                )
            }
        }
    }


    /** Helper function to make sure duplicate attributes don't get overridden or inserted without lowercase cmp
     * example: map("a" to 1) appendAndDontOverride map("A" to 2, "a" to 3, "c" to 4) = map("a" to 1, "c" to 4)
     * */
    internal fun <V> Map<String, V>.appendAndDontOverride(rhs: Map<String, V>): Map<String, V> {
        val out = this.toMutableMap()
        val current = this.keys.map { it.lowercase() }
        for ((key, value) in rhs) {
            if (current.contains(key.lowercase())) continue
            out[key] = value
        }
        return out
    }

    internal fun List<Job>.cancel() {
        forEach { job ->
            try {
                job.cancel()
            } catch (t: Throwable) {
                logError(t)
            }
        }
    }

    internal suspend fun List<Job>.join() {
        forEach { job ->
            try {
                job.join()
            } catch (t: Throwable) {
                logError(t)
            }
        }
    }
}