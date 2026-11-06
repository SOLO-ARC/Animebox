package com.lagradost.cloudstream3.ui.animebox.news

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

data class AnimeCornerNewsItem(
    val id: String,
    val title: String,
    val link: String,
    val imageUrl: String,
    val category: String,
    val date: String,
    val author: String,
    val excerpt: String
)

object AnimeCornerNewsClient {
    private const val TAG = "AnimeCornerNewsClient"
    private val cache = mutableMapOf<String, List<AnimeCornerNewsItem>>()

    suspend fun getNews(categoryKey: String = "All"): List<AnimeCornerNewsItem> = withContext(Dispatchers.IO) {
        val cached = cache[categoryKey]
        if (!cached.isNullOrEmpty()) return@withContext cached

        val feedUrl = when (categoryKey.lowercase()) {
            "editors-picks", "editors picks", "editor's picks" -> "https://animecorner.me/category/editors-picks/feed/"
            "news" -> "https://animecorner.me/category/news/feed/"
            else -> "https://animecorner.me/feed/"
        }

        val items = mutableListOf<AnimeCornerNewsItem>()
        try {
            val conn = URL(feedUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            
            if (conn.responseCode == 200) {
                val xml = conn.inputStream.bufferedReader().use { it.readText() }
                val itemRegex = Pattern.compile("<item>(.*?)</item>", Pattern.DOTALL)
                val matcher = itemRegex.matcher(xml)

                var index = 0
                while (matcher.find()) {
                    val itemStr = matcher.group(1) ?: continue
                    index++

                    val title = extractTagContent(itemStr, "title")
                        .replace("<![CDATA[", "").replace("]]>", "").trim()
                    val link = extractTagContent(itemStr, "link").trim()
                    val pubDate = extractTagContent(itemStr, "pubDate").trim()
                    val creator = extractTagContent(itemStr, "dc:creator")
                        .replace("<![CDATA[", "").replace("]]>", "").trim()
                    val description = extractTagContent(itemStr, "description")
                        .replace("<![CDATA[", "").replace("]]>", "").trim()

                    if (title.isBlank() || link.isBlank()) continue

                    val cleanTitle = cleanHtmlText(title)
                    val cleanDate = formatDateString(pubDate)
                    val cleanExcerpt = cleanHtmlText(description).take(140)

                    val itemCategory = when {
                        categoryKey.contains("editor", ignoreCase = true) || itemStr.contains("editors-picks", ignoreCase = true) -> "Editor's Pick"
                        else -> "News"
                    }

                    // Fetch high-resolution og:image for featured thumbnail
                    var imageUrl = extractImageFromHtml(itemStr)
                    if (imageUrl.isBlank()) {
                        imageUrl = fetchOgImage(link)
                    }

                    items.add(
                        AnimeCornerNewsItem(
                            id = "ac_$index",
                            title = cleanTitle,
                            link = link,
                            imageUrl = imageUrl,
                            category = itemCategory,
                            date = cleanDate,
                            author = creator.ifEmpty { "Anime Corner" },
                            excerpt = cleanExcerpt
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse Anime Corner news feed: ${e.message}")
        }

        if (items.isNotEmpty()) {
            cache[categoryKey] = items
        }
        items
    }

    private fun extractTagContent(xml: String, tag: String): String {
        val pattern = Pattern.compile("<$tag[^>]*>(.*?)</$tag>", Pattern.DOTALL)
        val matcher = pattern.matcher(xml)
        return if (matcher.find()) matcher.group(1) ?: "" else ""
    }

    private fun extractImageFromHtml(html: String): String {
        val imgRegex = Pattern.compile("<img[^>]+src=[\"'](https?://[^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
        val matcher = imgRegex.matcher(html)
        return if (matcher.find()) matcher.group(1) ?: "" else ""
    }

    private suspend fun fetchOgImage(articleUrl: String): String = withContext(Dispatchers.IO) {
        if (articleUrl.isBlank()) return@withContext ""
        try {
            val conn = URL(articleUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            if (conn.responseCode == 200) {
                val html = conn.inputStream.bufferedReader().use { it.readText() }
                val ogPattern = Pattern.compile("<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"'](https?://[^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                val matcher = ogPattern.matcher(html)
                if (matcher.find()) return@withContext matcher.group(1) ?: ""

                val ogPattern2 = Pattern.compile("<meta[^>]+content=[\"'](https?://[^\"']+)[\"'][^>]+property=[\"']og:image[\"']", Pattern.CASE_INSENSITIVE)
                val matcher2 = ogPattern2.matcher(html)
                if (matcher2.find()) return@withContext matcher2.group(1) ?: ""
            }
        } catch (_: Exception) {}
        ""
    }

    private fun cleanHtmlText(raw: String): String {
        return raw.replace(Regex("<[^>]*>"), "")
            .replace("&#8217;", "'")
            .replace("&#8216;", "'")
            .replace("&#8220;", "\"")
            .replace("&#8221;", "\"")
            .replace("&#8211;", "-")
            .replace("&#8212;", "--")
            .replace("&#039;", "'")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .trim()
    }

    private fun formatDateString(rawDate: String): String {
        if (rawDate.isBlank()) return "Recently"
        return try {
            val parts = rawDate.split(" ")
            if (parts.size >= 4) {
                "${parts[1]} ${parts[2]} ${parts[3]}"
            } else {
                rawDate.take(16)
            }
        } catch (_: Exception) {
            rawDate.take(16)
        }
    }
}
