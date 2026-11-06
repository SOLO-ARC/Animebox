package com.lagradost.cloudstream3.ui.animebox.extractors
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object AnikageExtractor {

    private val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val ANIKAGE_BASE = "https://anikage.cc"
    private const val BAKAYARO_PROXY = "https://og.bakayaro.live/m3u8/"

    // Cache AniList ID -> Anikage anime slug
    private val slugCache = ConcurrentHashMap<Int, String>()

    suspend fun extractKiwi(
        anilistId: Int,
        episodeNum: Int,
        animeTitle: String? = null,
        mode: String = "sub"
    ): HardSubResult? = withContext(Dispatchers.IO) {
        val slug = slugCache[anilistId] ?: resolveSlug(anilistId, animeTitle)
        if (slug == null) return@withContext null
        slugCache[anilistId] = slug

        // Fetch sources for Kiwi provider
        val langParam = if (mode == "dub") "dub" else "sub"
        val sourcesUrl = "$ANIKAGE_BASE/api/media/anime/$slug/episodes/$episodeNum/sources?provider=kiwi&lang=$langParam&server=kiwi"

        try {
            val req = Request.Builder()
                .url(sourcesUrl)
                .addHeader("User-Agent", UA)
                .addHeader("Referer", "$ANIKAGE_BASE/")
                .addHeader("Origin", ANIKAGE_BASE)
                .addHeader("Accept", "application/json, text/plain, */*")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                if (body.isEmpty() || body.contains("\"error\"")) return@withContext null

                val json = JSONObject(body)
                val rawUrl = json.optString("raw_url", "")
                if (rawUrl.isEmpty()) return@withContext null

                val streamUrl = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
                    rawUrl
                } else {
                    "$BAKAYARO_PROXY$rawUrl"
                }

                var introStart = 0L
                var introEnd = 0L
                var outroStart = 0L
                var outroEnd = 0L

                val introObj = json.optJSONObject("intro")
                if (introObj != null) {
                    introStart = introObj.optLong("start", 0L)
                    introEnd = introObj.optLong("end", 0L)
                }

                val outroObj = json.optJSONObject("outro")
                if (outroObj != null) {
                    outroStart = outroObj.optLong("start", 0L)
                    outroEnd = outroObj.optLong("end", 0L)
                }

                return@withContext HardSubResult(
                    hlsUrl = streamUrl,
                    referer = "$ANIKAGE_BASE/",
                    provider = "kiwi (hardsub)",
                    introStart = introStart,
                    introEnd = introEnd,
                    outroStart = outroStart,
                    outroEnd = outroEnd
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        null
    }

    private suspend fun resolveSlug(anilistId: Int, animeTitle: String?): String? = withContext(Dispatchers.IO) {
        val searchQueries = mutableListOf<String>()
        if (!animeTitle.isNullOrBlank()) {
            searchQueries.add(cleanTitle(animeTitle))
        }

        // Try getting romaji/english title from AniListClient if title is missing
        try {
            val detailsStr = com.lagradost.cloudstream3.ui.animebox.api.AniListClient.getAnimeDetails(anilistId)
            if (!detailsStr.isNullOrBlank()) {
                val media = JSONObject(detailsStr).optJSONObject("data")?.optJSONObject("Media")
                val titleObj = media?.optJSONObject("title")
                val romaji = titleObj?.optString("romaji")?.takeIf { it.isNotBlank() }
                val english = titleObj?.optString("english")?.takeIf { it.isNotBlank() }
                if (english != null) {
                    val cleaned = cleanTitle(english)
                    if (cleaned.isNotBlank() && !searchQueries.contains(cleaned)) {
                        searchQueries.add(cleaned)
                    }
                }
                if (romaji != null) {
                    val cleaned = cleanTitle(romaji)
                    if (cleaned.isNotBlank() && !searchQueries.contains(cleaned)) {
                        searchQueries.add(cleaned)
                    }
                }
            }
        } catch (_: Exception) {}

        for (q in searchQueries) {
            if (q.isBlank()) continue
            try {
                val encodedQ = URLEncoder.encode(q, "UTF-8")
                val browseUrl = "$ANIKAGE_BASE/api/media/anime/browse?q=$encodedQ&sort=popularity&page=1&limit=25&adult=true"
                val req = Request.Builder()
                    .url(browseUrl)
                    .addHeader("User-Agent", UA)
                    .addHeader("Referer", "$ANIKAGE_BASE/")
                    .addHeader("Origin", ANIKAGE_BASE)
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    val json = JSONObject(body)
                    val items = json.optJSONArray("items") ?: return@use

                    // 1. Exact match on anilistId
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val itemAnilistId = item.optInt("anilistId", -1)
                        if (itemAnilistId == anilistId) {
                            val slug = item.optString("slug", "")
                            if (slug.isNotEmpty()) return@withContext slug
                        }
                    }

                    // 2. Fallback to first item if single result or close match
                    if (items.length() > 0) {
                        val firstItem = items.getJSONObject(0)
                        val slug = firstItem.optString("slug", "")
                        if (slug.isNotEmpty()) return@withContext slug
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        null
    }

    private fun cleanTitle(title: String): String {
        return title.replace(Regex("\\([^)]*\\)"), "")
            .replace(Regex("\\[[^\\]]*\\]"), "")
            .replace(Regex("(?i)(season|cour|part)\\s*\\d+"), "")
            .replace(Regex("[:\\-–—_]"), " ")
            .trim()
    }
}
