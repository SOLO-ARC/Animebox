package com.lagradost.cloudstream3.ui.animebox.api
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object AnimeFillerManager {

    private val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    // In-memory cache for fast UI access: anilistId -> Set of filler episode numbers
    private val fillerEpisodesCache = ConcurrentHashMap<Int, Set<Int>>()

    /**
     * Get set of filler episode numbers for an anime.
     * Uses AniList ID, MAL ID (if available), and Title (for slug lookup).
     */
    suspend fun getFillerEpisodes(
        context: Context?,
        anilistId: Int,
        malId: Int = 0,
        animeTitle: String = ""
    ): Set<Int> = withContext(Dispatchers.IO) {
        if (anilistId <= 0) return@withContext emptySet()

        // 1. Check in-memory cache
        fillerEpisodesCache[anilistId]?.let { return@withContext it }

        // 2. Check SharedPreferences cache
        context?.let { ctx ->
            try {
                val prefs = ctx.getSharedPreferences("AnimeFillerPrefs", Context.MODE_PRIVATE)
                val cachedJson = prefs.getString("fillers_$anilistId", null)
                if (!cachedJson.isNullOrEmpty()) {
                    val set = cachedJson.split(",")
                        .mapNotNull { it.trim().toIntOrNull() }
                        .toSet()
                    fillerEpisodesCache[anilistId] = set
                    return@withContext set
                }
            } catch (_: Exception) {}
        }

        val resultFillers = mutableSetOf<Int>()

        // 3. Resolve MAL ID if missing
        var effectiveMalId = malId
        if (effectiveMalId <= 0) {
            val aniZip = AniZipClient.getAniZipJson(anilistId)
            effectiveMalId = aniZip?.optJSONObject("mappings")?.optInt("mal_id", 0) ?: 0
        }

        // 4. Source A: Jikan API (MyAnimeList episodes)
        if (effectiveMalId > 0) {
            try {
                var page = 1
                var hasNextPage = true
                while (hasNextPage && page <= 5) {
                    val url = "https://api.jikan.moe/v4/anime/$effectiveMalId/episodes?page=$page"
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val bodyStr = response.body?.string() ?: ""
                            if (bodyStr.isNotEmpty()) {
                                val json = JSONObject(bodyStr)
                                val dataArr = json.optJSONArray("data")
                                if (dataArr != null && dataArr.length() > 0) {
                                    for (i in 0 until dataArr.length()) {
                                        val epObj = dataArr.optJSONObject(i) ?: continue
                                        val epNum = epObj.optInt("mal_id", -1)
                                        val isFiller = epObj.optBoolean("filler", false)
                                        if (epNum > 0 && isFiller) {
                                            resultFillers.add(epNum)
                                        }
                                    }
                                }
                                val pagination = json.optJSONObject("pagination")
                                hasNextPage = pagination?.optBoolean("has_next_page", false) ?: false
                            } else {
                                hasNextPage = false
                            }
                        } else {
                            hasNextPage = false
                        }
                    }
                    if (hasNextPage) {
                        page++
                    }
                }
            } catch (e: Exception) {
                // Ignore and try fallback
            }
        }

        // 5. Source B: AnimeFillerList fallback if result is empty and title is known
        if (resultFillers.isEmpty() && animeTitle.isNotBlank()) {
            val slug = slugify(animeTitle)
            if (slug.isNotEmpty()) {
                try {
                    val url = "https://www.animefillerlist.com/shows/$slug"
                    val request = Request.Builder()
                        .url(url)
                        .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val html = response.body?.string() ?: ""
                            // Pattern: <tr class="...filler..." id="eps-XX">
                            val pattern = Pattern.compile("<tr class=\"[^\"]*filler[^\"]*\" id=\"eps-(\\d+)\">")
                            val matcher = pattern.matcher(html)
                            while (matcher.find()) {
                                val epNum = matcher.group(1)?.toIntOrNull()
                                if (epNum != null && epNum > 0) {
                                    resultFillers.add(epNum)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        val finalSet = resultFillers.toSet()
        fillerEpisodesCache[anilistId] = finalSet

        // Save to SharedPreferences cache
        context?.let { ctx ->
            try {
                val prefs = ctx.getSharedPreferences("AnimeFillerPrefs", Context.MODE_PRIVATE)
                prefs.edit().putString("fillers_$anilistId", finalSet.joinToString(",")).apply()
            } catch (_: Exception) {}
        }

        return@withContext finalSet
    }

    fun isFillerSync(anilistId: Int, episodeNum: Int): Boolean {
        return fillerEpisodesCache[anilistId]?.contains(episodeNum) == true
    }

    private fun slugify(title: String): String {
        return title.lowercase()
            .replace(Regex("""(?i)\b(?:season\s*\d+|\d+(?:st|nd|rd|th)\s+season|part\s*\d+|cour\s*\d+)\b"""), "")
            .replace(":", "")
            .replace("'", "")
            .replace("!", "")
            .replace("?", "")
            .replace(".", "")
            .replace("/", " ")
            .replace(Regex("""\s+"""), "-")
            .trim('-')
    }
}
