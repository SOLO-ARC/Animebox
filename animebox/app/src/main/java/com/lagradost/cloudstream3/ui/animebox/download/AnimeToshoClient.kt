package com.lagradost.cloudstream3.ui.animebox.download
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class TorrentRelease(
    val title: String,
    val quality: String,
    val size: String,
    val seeders: Int,
    val magnetUri: String,
    val torrentUrl: String
)

object AnimeToshoClient {
    private val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val BASE_URL = "https://feed.animetosho.org/json"

    suspend fun searchTorrents(animeTitle: String, episodeNum: Int): List<TorrentRelease> = withContext(Dispatchers.IO) {
        val list = mutableListOf<TorrentRelease>()
        try {
            val formattedEp = String.format("%02d", episodeNum)
            val cleanTitle = animeTitle
                .replace(Regex("(?i)\\b(season|part|cour)\\s*\\d+\\b"), "")
                .replace(Regex("[^a-zA-Z0-9 ]"), " ")
                .trim()
                .replace(Regex("\\s+"), " ")

            val query = "$cleanTitle $formattedEp"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val targetUrl = "$BASE_URL?q=$encodedQuery"

            val req = Request.Builder()
                .url(targetUrl)
                .header("User-Agent", "FireFly/2.0 (Android; Torrent Client)")
                .build()

            val resStr = client.newCall(req).execute().use { res ->
                if (res.isSuccessful) res.body?.string() ?: "" else ""
            }

            if (resStr.isNotBlank() && resStr.startsWith("[")) {
                val arr = JSONArray(resStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val rawTitle = obj.optString("title")
                    val magnetUri = obj.optString("magnet_uri")
                    val torrentUrl = obj.optString("torrent_url")
                    val totalSize = obj.optLong("total_size", 0L)
                    val seeders = obj.optInt("seeders", 0)

                    val quality = when {
                        rawTitle.contains("1080p", ignoreCase = true) -> "1080p"
                        rawTitle.contains("720p", ignoreCase = true) -> "720p"
                        rawTitle.contains("480p", ignoreCase = true) -> "480p"
                        rawTitle.contains("2160p", ignoreCase = true) || rawTitle.contains("4K", ignoreCase = true) -> "4K"
                        else -> "HD"
                    }

                    val sizeMb = totalSize / (1024.0 * 1024.0)
                    val sizeStr = if (sizeMb >= 1024.0) {
                        String.format(java.util.Locale.US, "%.1f GB", sizeMb / 1024.0)
                    } else if (sizeMb > 0) {
                        String.format(java.util.Locale.US, "%.0f MB", sizeMb)
                    } else {
                        "Torrent"
                    }

                    if (magnetUri.isNotBlank() || torrentUrl.isNotBlank()) {
                        list.add(
                            TorrentRelease(
                                title = rawTitle,
                                quality = quality,
                                size = sizeStr,
                                seeders = seeders,
                                magnetUri = magnetUri,
                                torrentUrl = torrentUrl
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        list
    }
}
