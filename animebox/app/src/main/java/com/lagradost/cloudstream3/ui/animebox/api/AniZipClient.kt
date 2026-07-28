package com.lagradost.cloudstream3.ui.animebox.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AniZipClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private val tmdbImagesCache = mutableMapOf<Int, MutableMap<String, String>>()

    suspend fun getTmdbEpisodeImage(tmdbId: Int, season: Int, episode: Int): String = withContext(Dispatchers.IO) {
        val key = "${season}_${episode}"
        val cache = tmdbImagesCache.getOrPut(tmdbId) { mutableMapOf() }
        if (cache.containsKey(key)) {
            return@withContext cache[key] ?: ""
        }
        
        val url = "https://api.themoviedb.org/3/tv/$tmdbId/season/$season?api_key=5a7f00b2528e0278ae94cd386deb6116"
        val request = Request.Builder().url(url).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: ""
                    if (jsonStr.isNotEmpty()) {
                        val json = JSONObject(jsonStr)
                        if (json.has("episodes")) {
                            val eps = json.getJSONArray("episodes")
                            for (i in 0 until eps.length()) {
                                val epObj = eps.getJSONObject(i)
                                val epNum = epObj.optInt("episode_number", -1)
                                val stillPath = epObj.optString("still_path", "")
                                if (epNum != -1 && stillPath.isNotEmpty() && stillPath != "null") {
                                    cache["${season}_${epNum}"] = "https://image.tmdb.org/t/p/w500$stillPath"
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext cache[key] ?: ""
    }

    suspend fun getEpisodeMetadata(anilistId: Int): Map<Int, EpisodeMeta> = withContext(Dispatchers.IO) {
        val url = "https://api.ani.zip/mappings?anilist_id=$anilistId"
        val request = Request.Builder().url(url).build()
        val metadataMap = mutableMapOf<Int, EpisodeMeta>()

        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: return@use
                    val json = JSONObject(jsonStr)
                    val mappingsObj = if (json.has("mappings")) json.getJSONObject("mappings") else null
                    val tmdbId = mappingsObj?.optInt("themoviedb_id", 0) ?: 0

                    if (json.has("episodes")) {
                        val episodesObj = json.getJSONObject("episodes")
                        val keys = episodesObj.keys()
                        while (keys.hasNext()) {
                            val epNumStr = keys.next()
                            val epNum = epNumStr.toIntOrNull() ?: continue
                            val epObj = episodesObj.getJSONObject(epNumStr)
                            
                            val title = if (epObj.has("title")) {
                                val titleObj = epObj.getJSONObject("title")
                                if (titleObj.has("en")) titleObj.getString("en") else "Episode $epNum"
                            } else "Episode $epNum"
                            
                            val imageUrl = if (epObj.has("image")) epObj.getString("image") else ""
                            val season = epObj.optInt("seasonNumber", 1)
                            val episode = epObj.optInt("episodeNumber", epNum)

                            val airdate = when {
                                epObj.has("airdate") -> epObj.getString("airdate")
                                epObj.has("airDate") -> epObj.getString("airDate")
                                else -> ""
                            }
                            val overview = epObj.optString("overview", epObj.optString("summary", ""))
                            val runtime = epObj.optInt("runtime", 24)
                            metadataMap[epNum] = EpisodeMeta(title, imageUrl, airdate, season, episode, overview, runtime)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AniZipClient", "getEpisodeMetadata error: ${e.message}", e)
        }
        return@withContext metadataMap
    }

    suspend fun getAnimeLogoUrl(anilistId: Int): String = withContext(Dispatchers.IO) {
        val mappingUrl = "https://api.ani.zip/mappings?anilist_id=$anilistId"
        val request = Request.Builder().url(mappingUrl).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: return@use ""
                    val json = JSONObject(jsonStr)
                    
                    // 1. Try to fetch from images list in ani.zip mappings first (TVDB/Clearlogo)
                    if (json.has("images")) {
                        val imagesArray = json.getJSONArray("images")
                        for (i in 0 until imagesArray.length()) {
                            val imgObj = imagesArray.getJSONObject(i)
                            if (imgObj.optString("coverType") == "Clearlogo") {
                                val url = imgObj.optString("url", "")
                                if (url.isNotEmpty()) return@use url
                            }
                        }
                    }

                    // Extract mappings
                    if (json.has("mappings")) {
                        val mappings = json.getJSONObject("mappings")
                        val tmdbId = mappings.optInt("themoviedb_id", 0)

                        if (tmdbId > 0) {
                            val tmdbUrl = "https://api.themoviedb.org/3/tv/$tmdbId/images?api_key=5a7f00b2528e0278ae94cd386deb6116&include_image_language=en,null"
                            val tmdbRequest = Request.Builder().url(tmdbUrl).build()
                            try {
                                client.newCall(tmdbRequest).execute().use { tmdbResponse ->
                                    if (tmdbResponse.isSuccessful) {
                                        val tmdbJsonStr = tmdbResponse.body?.string() ?: return@use ""
                                        val tmdbJson = JSONObject(tmdbJsonStr)
                                        if (tmdbJson.has("logos")) {
                                            val logosArray = tmdbJson.getJSONArray("logos")
                                            if (logosArray.length() > 0) {
                                                val filePath = logosArray.getJSONObject(0).optString("file_path", "")
                                                if (filePath.isNotEmpty()) {
                                                    return@use "https://image.tmdb.org/t/p/original$filePath"
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("AniZipClient", "TMDB logo fetch failed: ${e.message}")
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AniZipClient", "getAnimeLogoUrl error: ${e.message}", e)
        }
        return@withContext ""
    }

    suspend fun getClearLogoUrl(anilistId: Int): String = withContext(Dispatchers.IO) {
        return@withContext getAnimeLogoUrl(anilistId)
    }


    suspend fun getTmdbBackdropUrl(anilistId: Int): String = withContext(Dispatchers.IO) {
        if (anilistId == 129201) {
            return@withContext "https://image.tmdb.org/t/p/original/1czz0r7urqCPP0CZTAEkCk4TZY1.jpg"
        }
        var result = ""
        try {
            // Step 1: Get TMDB ID and type from ani.zip
            val mappingUrl = "https://api.ani.zip/mappings?anilist_id=$anilistId"
            val mappingRequest = Request.Builder().url(mappingUrl).build()
            var tmdbId = 0
            var mediaType = "tv"

            client.newCall(mappingRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: ""
                    if (jsonStr.isNotEmpty()) {
                        val json = JSONObject(jsonStr)
                        if (json.has("mappings")) {
                            val mappings = json.getJSONObject("mappings")
                            // Handle themoviedb_id as either string or int
                            tmdbId = try {
                                mappings.getString("themoviedb_id").toIntOrNull() ?: 0
                            } catch (e: Exception) {
                                mappings.optInt("themoviedb_id", 0)
                            }
                            val rawType = mappings.optString("type", "")
                            mediaType = if (rawType.equals("movie", ignoreCase = true)) "movie" else "tv"
                            android.util.Log.d("AniZipClient", "AniList $anilistId -> TMDB ID: $tmdbId, type: $mediaType")
                        }
                    }
                }
            }

            // Step 2: Fetch backdrop from TMDB if we have a valid ID
            if (tmdbId > 0) {
                val tmdbUrl = "https://api.themoviedb.org/3/$mediaType/$tmdbId?api_key=5a7f00b2528e0278ae94cd386deb6116"
                val tmdbRequest = Request.Builder().url(tmdbUrl).build()
                client.newCall(tmdbRequest).execute().use { tmdbResponse ->
                    if (tmdbResponse.isSuccessful) {
                        val tmdbJsonStr = tmdbResponse.body?.string() ?: ""
                        if (tmdbJsonStr.isNotEmpty()) {
                            val tmdbJson = JSONObject(tmdbJsonStr)
                            val backdropPath = tmdbJson.optString("backdrop_path", "")
                            if (backdropPath.isNotEmpty() && backdropPath != "null") {
                                result = "https://image.tmdb.org/t/p/original$backdropPath"
                                android.util.Log.d("AniZipClient", "Got TMDB backdrop for $anilistId: $result")
                            }
                        }
                    } else {
                        android.util.Log.e("AniZipClient", "TMDB API error ${tmdbResponse.code} for id $tmdbId")
                    }
                }
            } else {
                android.util.Log.w("AniZipClient", "No TMDB ID found for AniList $anilistId")
            }
        } catch (e: Exception) {
            android.util.Log.e("AniZipClient", "getTmdbBackdropUrl error for $anilistId: ${e.message}", e)
        }
        return@withContext result
    }


    suspend fun getAniZipBackdropUrl(anilistId: Int): String = withContext(Dispatchers.IO) {
        val mappingUrl = "https://api.ani.zip/mappings?anilist_id=$anilistId"
        val request = Request.Builder().url(mappingUrl).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: return@use ""
                    val json = JSONObject(jsonStr)
                    if (json.has("images")) {
                        val imagesArray = json.getJSONArray("images")
                        var bannerUrl = ""
                        for (i in 0 until imagesArray.length()) {
                            val imgObj = imagesArray.getJSONObject(i)
                            val coverType = imgObj.optString("coverType")
                            if (coverType == "Fanart") {
                                val url = imgObj.optString("url", "")
                                if (url.isNotEmpty()) return@use url
                            } else if (coverType == "Banner") {
                                val url = imgObj.optString("url", "")
                                if (url.isNotEmpty()) {
                                    bannerUrl = url
                                }
                            }
                        }
                        if (bannerUrl.isNotEmpty()) return@use bannerUrl
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AniZipClient", "getAniZipBackdropUrl error: ${e.message}", e)
        }
        return@withContext ""
    }

    suspend fun getTmdbId(anilistId: Int): Int? = withContext(Dispatchers.IO) {
        val mappingUrl = "https://api.ani.zip/mappings?anilist_id=$anilistId"
        val request = Request.Builder().url(mappingUrl).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: return@use null
                    val json = JSONObject(jsonStr)
                    if (json.has("mappings")) {
                        val mappings = json.getJSONObject("mappings")
                        if (mappings.has("themoviedb_id")) {
                            return@use mappings.getInt("themoviedb_id")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    suspend fun getTmdbAllEpisodes(tmdbId: Int): Map<Int, EpisodeMeta> = withContext(Dispatchers.IO) {
        val resultMap = java.util.concurrent.ConcurrentHashMap<Int, EpisodeMeta>()
        val mainUrl = "https://api.themoviedb.org/3/tv/$tmdbId?api_key=5a7f00b2528e0278ae94cd386deb6116"
        val mainRequest = Request.Builder().url(mainUrl).build()
        try {
            client.newCall(mainRequest).execute().use { mainResponse ->
                if (mainResponse.isSuccessful) {
                    val mainJson = JSONObject(mainResponse.body?.string() ?: "")
                    if (mainJson.has("seasons")) {
                        val seasonsArray = mainJson.getJSONArray("seasons")
                        val deferredList = mutableListOf<kotlinx.coroutines.Deferred<Unit>>()
                        val seasonList = mutableListOf<Pair<Int, Int>>()
                        
                        for (i in 0 until seasonsArray.length()) {
                            val seasonObj = seasonsArray.getJSONObject(i)
                            val seasonNum = seasonObj.getInt("season_number")
                            if (seasonNum <= 0) continue
                            val epCount = seasonObj.optInt("episode_count", 0)
                            if (epCount > 0) {
                                seasonList.add(seasonNum to epCount)
                            }
                        }
                        
                        seasonList.sortBy { it.first }
                        
                        var absoluteStart = 1
                        val seasonRanges = mutableListOf<Triple<Int, Int, Int>>()
                        for (pair in seasonList) {
                            seasonRanges.add(Triple(pair.first, absoluteStart, pair.second))
                            absoluteStart += pair.second
                        }
                        
                        kotlinx.coroutines.coroutineScope {
                            for (triple in seasonRanges) {
                                val seasonNum = triple.first
                                val startAbs = triple.second
                                
                                val job = async {
                                    val seasonUrl = "https://api.themoviedb.org/3/tv/$tmdbId/season/$seasonNum?api_key=5a7f00b2528e0278ae94cd386deb6116"
                                    val seasonRequest = Request.Builder().url(seasonUrl).build()
                                    try {
                                        client.newCall(seasonRequest).execute().use { seasonResponse ->
                                            if (seasonResponse.isSuccessful) {
                                                val seasonJson = JSONObject(seasonResponse.body?.string() ?: "")
                                                if (seasonJson.has("episodes")) {
                                                    val eps = seasonJson.getJSONArray("episodes")
                                                    for (j in 0 until eps.length()) {
                                                        val epObj = eps.getJSONObject(j)
                                                        val epNumInSeason = epObj.optInt("episode_number", 1)
                                                        val name = epObj.optString("name", "Episode ${startAbs + j}")
                                                        val airdate = epObj.optString("air_date", "")
                                                        
                                                        val stillPath = epObj.optString("still_path", "")
                                                        val imageUrl = if (stillPath.isNotEmpty() && stillPath != "null") {
                                                            "https://image.tmdb.org/t/p/w500$stillPath"
                                                        } else ""
                                                        
                                                        val overview = epObj.optString("overview", "")
                                                        val runtime = epObj.optInt("runtime", 24)
                                                        
                                                        resultMap[startAbs + j] = EpisodeMeta(
                                                            title = name,
                                                            imageUrl = imageUrl,
                                                            episodeNumber = epNumInSeason,
                                                            seasonNumber = seasonNum,
                                                            airdate = airdate,
                                                            overview = overview,
                                                            runtime = runtime
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                deferredList.add(job)
                            }
                            deferredList.forEach { it.await() }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        resultMap
    }
}

data class EpisodeMeta(
    val title: String,
    val imageUrl: String,
    val airdate: String = "",
    val seasonNumber: Int = 1,
    val episodeNumber: Int = 1,
    val overview: String = "",
    val runtime: Int = 24
)
