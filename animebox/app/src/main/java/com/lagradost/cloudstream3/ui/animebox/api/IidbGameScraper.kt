package com.lagradost.cloudstream3.ui.animebox.api
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object IidbGameScraper {

    private val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val cache = ConcurrentHashMap<String, IidbGameMetadata>()

    suspend fun searchAndGetDetails(gameQuery: String): IidbGameMetadata? = withContext(Dispatchers.IO) {
        val cleanQuery = gameQuery
            .replace(Regex("""\s*\(\d{4}(?:/\d{4})?\)"""), "")
            .substringBefore(" or ")
            .substringBefore(" / ")
            .substringBefore(" and ")
            .substringBefore(" & ")
            .replace(Regex("""^[\*\-•\+\d\.\)\s]+"""), "")
            .replace(Regex("""^(?:Consider|Try|Play|Main story\s*->|Complete experience\s*->|Enhanced version\s*->)\s*""", RegexOption.IGNORE_CASE), "")
            .trim(' ', '*', '-', '•', '"', '\'')

        if (cleanQuery.isBlank()) return@withContext null

        val cacheKey = cleanQuery.lowercase()
        cache[cacheKey]?.let { return@withContext it }

        try {
            // 1. Query the search endpoint on iidb-api
            fun executeSearch(q: String): JSONArray? {
                return try {
                    val searchUrl = "https://iidb-api.iisu.network/api/v1/parents/search?q=${URLEncoder.encode(q, "UTF-8")}"
                    val searchReq = Request.Builder()
                        .url(searchUrl)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .header("Accept", "application/json")
                        .build()

                    val searchBody = client.newCall(searchReq).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string() else null
                    } ?: return null

                    val arr = JSONArray(searchBody)
                    if (arr.length() > 0) arr else null
                } catch (_: Exception) {
                    null
                }
            }

            var resultsArray = executeSearch(cleanQuery)
            if (resultsArray == null && (cleanQuery.contains("é") || cleanQuery.contains("É"))) {
                resultsArray = executeSearch(cleanQuery.replace("é", "e").replace("É", "E"))
            }
            if (resultsArray == null) return@withContext null

            // 2. Match the best game candidate by title
            var bestId = 0
            var bestTitle = ""
            val queryLower = cleanQuery.lowercase()

            for (i in 0 until resultsArray.length()) {
                val item = resultsArray.optJSONObject(i) ?: continue
                val id = item.optInt("id", 0)
                val name = item.optString("name", "")
                if (id <= 0) continue

                val nameLower = name.lowercase()
                if (nameLower == queryLower) {
                    bestId = id
                    bestTitle = name
                    break
                } else if (nameLower.contains(queryLower) || queryLower.contains(nameLower)) {
                    if (bestId == 0) {
                        bestId = id
                        bestTitle = name
                    }
                }
            }

            // Fallback to the first search result if no match was found
            if (bestId == 0) {
                val first = resultsArray.optJSONObject(0) ?: return@withContext null
                bestId = first.optInt("id", 0)
                bestTitle = first.optString("name", cleanQuery)
            }

            if (bestId == 0) return@withContext null

            // 3. Query the game detail endpoint
            val detailUrl = "https://iidb-api.iisu.network/api/v1/parents/$bestId/detail"
            val detailReq = Request.Builder()
                .url(detailUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept", "application/json")
                .build()

            val detailBody = client.newCall(detailReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }

            var bannerUrl = ""
            var heroUrl = ""
            var boxartUrl = ""

            if (!detailBody.isNullOrBlank()) {
                val detailObj = JSONObject(detailBody)
                val serverName = detailObj.optString("name", bestTitle)
                if (serverName.isNotBlank()) bestTitle = serverName

                val assetsArr = detailObj.optJSONArray("assets")
                if (assetsArr != null) {
                    for (j in 0 until assetsArr.length()) {
                        val asset = assetsArr.optJSONObject(j) ?: continue
                        val type = asset.optString("type", "").lowercase()
                        val filename = asset.optString("filename", "")
                        if (filename.isBlank()) continue

                        val fullUrl = if (filename.startsWith("http://") || filename.startsWith("https://")) {
                            filename
                        } else {
                            "https://assets.iisu.network/$filename"
                        }

                        when {
                            type.contains("hero") || type.contains("heroes") || type.contains("background") -> {
                                if (heroUrl.isBlank()) heroUrl = fullUrl
                            }
                            type.contains("banner") || type.contains("header") -> {
                                if (bannerUrl.isBlank()) bannerUrl = fullUrl
                            }
                            type.contains("boxart") || type.contains("cover") || type.contains("capsule") || type.contains("poster") || type.contains("icon") -> {
                                if (boxartUrl.isBlank()) boxartUrl = fullUrl
                            }
                        }
                    }
                }
            }

            // Fallback: If banner is missing, use heroes; if both missing, use boxart
            val effectiveBanner = when {
                heroUrl.isNotBlank() -> heroUrl
                bannerUrl.isNotBlank() -> bannerUrl
                else -> boxartUrl
            }

            val effectiveCover = when {
                boxartUrl.isNotBlank() -> boxartUrl
                heroUrl.isNotBlank() -> heroUrl
                bannerUrl.isNotBlank() -> bannerUrl
                else -> effectiveBanner
            }

            val result = IidbGameMetadata(
                id = bestId,
                title = bestTitle,
                bannerUrl = effectiveBanner,
                coverUrl = effectiveCover,
                detailUrl = "https://iidb.iisu.network/games/$bestId"
            )

            cache[cacheKey] = result
            result
        } catch (_: Exception) {
            null
        }
    }
}
