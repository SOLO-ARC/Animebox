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
    private val aniZipJsonCache = android.util.LruCache<Int, JSONObject>(100)
    private val kitsuAniZipJsonCache = android.util.LruCache<Int, JSONObject>(100)
    private val kitsuToAnilistCache = android.util.LruCache<Int, Int>(100)

    suspend fun getAniZipJson(anilistId: Int): JSONObject? = withContext(Dispatchers.IO) {
        val cached = aniZipJsonCache.get(anilistId)
        if (cached != null) return@withContext cached

        val url = "https://api.ani.zip/mappings?anilist_id=$anilistId"
        val request = Request.Builder().url(url).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: return@use null
                    val json = JSONObject(jsonStr)
                    aniZipJsonCache.put(anilistId, json)
                    return@withContext json
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    suspend fun getAniZipJsonByKitsuId(kitsuId: Int): JSONObject? = withContext(Dispatchers.IO) {
        val cached = kitsuAniZipJsonCache.get(kitsuId)
        if (cached != null) return@withContext cached

        val url = "https://api.ani.zip/mappings?kitsu_id=$kitsuId"
        val request = Request.Builder().url(url).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: return@use null
                    val json = JSONObject(jsonStr)
                    kitsuAniZipJsonCache.put(kitsuId, json)
                    val anilistId = json.optJSONObject("mappings")?.optInt("anilist_id", -1) ?: -1
                    if (anilistId > 0) {
                        kitsuToAnilistCache.put(kitsuId, anilistId)
                        aniZipJsonCache.put(anilistId, json)
                    }
                    return@withContext json
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    suspend fun resolveAnilistIdFromKitsu(kitsuId: Int): Int? = withContext(Dispatchers.IO) {
        val cachedId = kitsuToAnilistCache.get(kitsuId)
        if (cachedId != null && cachedId > 0) return@withContext cachedId

        val json = getAniZipJsonByKitsuId(kitsuId)
        val anilistId = json?.optJSONObject("mappings")?.optInt("anilist_id", -1) ?: -1
        if (anilistId > 0) {
            kitsuToAnilistCache.put(kitsuId, anilistId)
            return@withContext anilistId
        }
        null
    }

    suspend fun getKitsuIdFromAnilist(anilistId: Int): Int? = withContext(Dispatchers.IO) {
        val json = getAniZipJson(anilistId)
        val kitsuId = json?.optJSONObject("mappings")?.optInt("kitsu_id", -1) ?: -1
        if (kitsuId > 0) kitsuId else null
    }

    private val kitsuEpisodeImagesCache = java.util.concurrent.ConcurrentHashMap<Int, Map<Int, String>>()
    private val tmdbSeasonEpisodesCache = java.util.concurrent.ConcurrentHashMap<String, Map<Int, String>>()
    private val tmdbAirdateMapCache = java.util.concurrent.ConcurrentHashMap<Int, Map<String, String>>()

    suspend fun getKitsuEpisodeImages(kitsuId: Int): Map<Int, String> = withContext(Dispatchers.IO) {
        if (kitsuId <= 0) return@withContext emptyMap()
        val cached = kitsuEpisodeImagesCache[kitsuId]
        if (cached != null && cached.isNotEmpty()) return@withContext cached

        val resultMap = mutableMapOf<Int, String>()
        var offset = 0
        try {
            while (offset <= 100) {
                val url = "https://kitsu.io/api/edge/episodes?filter%5BmediaId%5D=$kitsuId&page%5Blimit%5D=20&page%5Boffset%5D=$offset"
                val req = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                var hasMore = false
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use
                    val body = resp.body?.string() ?: return@use
                    val json = JSONObject(body)
                    val data = json.optJSONArray("data") ?: return@use
                    for (i in 0 until data.length()) {
                        val item = data.optJSONObject(i) ?: continue
                        val attrs = item.optJSONObject("attributes") ?: continue
                        val epNum = attrs.optInt("number", -1)
                        val thumb = attrs.optJSONObject("thumbnail")
                        if (epNum > 0 && thumb != null) {
                            val img = thumb.optString("original",
                                thumb.optString("large",
                                    thumb.optString("medium",
                                        thumb.optString("small",
                                            thumb.optString("tiny", "")))))
                            if (img.isNotEmpty() && !img.equals("null", ignoreCase = true)) {
                                resultMap[epNum] = img
                            }
                        }
                    }
                    hasMore = data.length() >= 20
                }
                if (!hasMore) break
                offset += 20
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (resultMap.isNotEmpty()) {
            kitsuEpisodeImagesCache[kitsuId] = resultMap
        }
        resultMap
    }

    suspend fun getTmdbEpisodeImage(tmdbId: Int, season: Int, episode: Int): String = withContext(Dispatchers.IO) {
        val key = "${season}_${episode}"
        val cache = tmdbImagesCache.getOrPut(tmdbId) { mutableMapOf() }
        if (cache.containsKey(key)) {
            return@withContext cache[key] ?: ""
        }
        
        val url = "https://api.themoviedb.org/3/tv/$tmdbId/season/$season?api_key=5a7f00b2528e0278ae94cd386deb6116"
        val request = Request.Builder()
            .url(url)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            .build()
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

    private suspend fun getTmdbAirdateStills(tmdbId: Int): Map<String, String> = withContext(Dispatchers.IO) {
        if (tmdbId <= 0) return@withContext emptyMap()
        val cached = tmdbAirdateMapCache[tmdbId]
        if (cached != null) return@withContext cached

        val map = mutableMapOf<String, String>()
        try {
            val url = "https://api.themoviedb.org/3/tv/$tmdbId/season/1?api_key=5a7f00b2528e0278ae94cd386deb6116"
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return@use
                    val json = JSONObject(body)
                    val eps = json.optJSONArray("episodes") ?: return@use
                    for (i in 0 until eps.length()) {
                        val epObj = eps.optJSONObject(i) ?: continue
                        val airDate = epObj.optString("air_date", "")
                        val stillPath = epObj.optString("still_path", "")
                        if (airDate.isNotEmpty() && stillPath.isNotEmpty() && stillPath != "null") {
                            map[airDate] = "https://image.tmdb.org/t/p/w500$stillPath"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (map.isNotEmpty()) {
            tmdbAirdateMapCache[tmdbId] = map
        }
        map
    }

    suspend fun getEpisodeMetadata(anilistId: Int): Map<Int, EpisodeMeta> = withContext(Dispatchers.IO) {
        val metadataMap = mutableMapOf<Int, EpisodeMeta>()
        if (ShinChanEpisodeProvider.isShinChan(anilistId)) {
            val covers = ShinChanSupabaseManager.getAllEpisodeCovers()
            val maxEp = ShinChanSupabaseManager.getLatestEpisodeNumber()
            for (ep in 1..maxEp) {
                val cover = covers[ep] ?: ""
                metadataMap[ep] = EpisodeMeta(
                    title = "Episode $ep",
                    overview = "",
                    imageUrl = cover,
                    airdate = ""
                )
            }
            return@withContext metadataMap
        }
        try {
            val json = getAniZipJson(anilistId) ?: return@withContext metadataMap
            val mappings = json.optJSONObject("mappings")
            val kitsuId = mappings?.optInt("kitsu_id", -1) ?: -1
            val tmdbId = mappings?.optInt("themoviedb_id", 0) ?: 0

            // Pre-fetch Kitsu episode images (exact 1:1 match with AniList seasons)
            val kitsuImages = if (kitsuId > 0) {
                getKitsuEpisodeImages(kitsuId)
            } else {
                emptyMap()
            }

            var tmdbAirdateMap: Map<String, String>? = null

            if (json.has("episodes")) {
                val episodesObj = json.getJSONObject("episodes")
                val keys = episodesObj.keys()
                while (keys.hasNext()) {
                    val epNumStr = keys.next()
                    val epNum = epNumStr.toIntOrNull() ?: continue
                    val epObj = episodesObj.getJSONObject(epNumStr)
                    
                    val title = if (epObj.has("title") && !epObj.isNull("title")) {
                        val titleObj = epObj.getJSONObject("title")
                        val enStr = if (titleObj.has("en") && !titleObj.isNull("en")) titleObj.optString("en", "") else ""
                        val jaStr = if (titleObj.has("ja") && !titleObj.isNull("ja")) titleObj.optString("ja", "") else ""
                        val cleanEn = if (enStr.equals("null", ignoreCase = true)) "" else enStr
                        val cleanJa = if (jaStr.equals("null", ignoreCase = true)) "" else jaStr
                        if (cleanEn.isNotEmpty()) cleanEn else if (cleanJa.isNotEmpty()) cleanJa else "Episode $epNum"
                    } else "Episode $epNum"
                    
                    var imageUrl = if (epObj.has("image")) epObj.getString("image") else ""
                    val season = epObj.optInt("seasonNumber", 1)
                    val episode = epObj.optInt("episodeNumber", epNum)

                    val airdate = when {
                        epObj.has("airdate") -> epObj.getString("airdate")
                        epObj.has("airDate") -> epObj.getString("airDate")
                        else -> ""
                    }
                    val overview = epObj.optString("overview", epObj.optString("summary", ""))
                    val runtime = epObj.optInt("runtime", 24)
                    val isFiller = epObj.optBoolean("isFiller", epObj.optBoolean("filler", false))

                    // 1. Resolve episode cover from Kitsu (exact season match, unblocked)
                    if (imageUrl.isEmpty() && kitsuImages.containsKey(epNum)) {
                        imageUrl = kitsuImages[epNum] ?: ""
                    }

                    // 2. Resolve episode cover from TMDB if Kitsu had no image
                    if (imageUrl.isEmpty() && tmdbId > 0) {
                        // First try airdate matching (handles shows where TMDB combines seasons into Season 1 e.g. JJK, My Dress-Up Darling)
                        if (airdate.isNotEmpty()) {
                            if (tmdbAirdateMap == null) {
                                tmdbAirdateMap = getTmdbAirdateStills(tmdbId)
                            }
                            val airMatch = tmdbAirdateMap?.get(airdate)
                            if (!airMatch.isNullOrEmpty()) {
                                imageUrl = airMatch
                            }
                        }
                        // Second try standard TMDB season/episode
                        if (imageUrl.isEmpty()) {
                            val tmdbStill = getTmdbEpisodeImage(tmdbId, season, episode)
                            if (tmdbStill.isNotEmpty()) {
                                imageUrl = tmdbStill
                            }
                        }
                    }

                    metadataMap[epNum] = EpisodeMeta(title, imageUrl, airdate, season, episode, overview, runtime, isFiller)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AniZipClient", "getEpisodeMetadata error: ${e.message}", e)
        }
        return@withContext metadataMap
    }

    suspend fun getAnimeLogoUrl(anilistId: Int, isMovie: Boolean = false): String = withContext(Dispatchers.IO) {
        if (ShinChanEpisodeProvider.isShinChan(anilistId)) {
            return@withContext ShinChanEpisodeProvider.SHINCHAN_LOGO_URL
        }
        // 0. Check Supabase dynamic custom logo first!
        val dynamicLogo = AnimeMovieTmdbMapping.getDynamicLogo(anilistId)
        if (!dynamicLogo.isNullOrBlank()) {
            return@withContext dynamicLogo
        }

        val dynamicType = AnimeMovieTmdbMapping.getDynamicTmdbType(anilistId)
        val staticMovie = AnimeMovieTmdbMapping.isStaticMovie(anilistId)
        var targetIsMovie = when {
            dynamicType != null -> dynamicType.equals("movie", ignoreCase = true)
            staticMovie -> true
            else -> isMovie
        }
        var targetType = if (targetIsMovie) "movie" else "tv"

        var tmdbId: Int? = AnimeMovieTmdbMapping.getTmdbMapping(anilistId)
        if (tmdbId == null || tmdbId <= 0) {
            if (targetIsMovie) {
                tmdbId = AnimeMovieTmdbMapping.getMovieTmdbId(anilistId)
            } else {
                tmdbId = getLongRunningTmdbId(anilistId)
            }
        }
        if (tmdbId == null || tmdbId <= 0) {
            tmdbId = getTmdbId(anilistId)
        }
        if (tmdbId == null || tmdbId <= 0) {
            val aniJson = getAniZipJson(anilistId)
            if (aniJson != null && aniJson.has("mappings")) {
                val mappings = aniJson.getJSONObject("mappings")
                if (mappings.optString("type", "").equals("MOVIE", ignoreCase = true)) {
                    targetIsMovie = true
                    targetType = "movie"
                    AnimeMovieTmdbMapping.setDynamicType(anilistId, "movie")
                }
                val tId = mappings.optInt("themoviedb_id", 0)
                if (tId > 0) {
                    tmdbId = tId
                    if (targetIsMovie) {
                        AnimeMovieTmdbMapping.setDynamicMapping(anilistId, tId, "movie")
                    }
                } else {
                    val sId = mappings.optInt("themoviedb_season_id", 0)
                    if (sId > 0) tmdbId = sId
                }
            }
        }
        if (tmdbId == null || tmdbId <= 0) {
            tmdbId = getTmdbIdFromFribb(anilistId, isMovie = targetIsMovie)
        }
        
        // Helper to query TMDB logos for a given media type
        fun queryTmdbLogo(type: String, id: Int): String {
            try {
                val tmdbUrl = "https://api.themoviedb.org/3/$type/$id/images?api_key=5a7f00b2528e0278ae94cd386deb6116&include_image_language=en,null"
                val tmdbRequest = Request.Builder().url(tmdbUrl).build()
                client.newCall(tmdbRequest).execute().use { tmdbResponse ->
                    if (tmdbResponse.isSuccessful) {
                        val tmdbJsonStr = tmdbResponse.body?.string() ?: return@use
                        val tmdbJson = JSONObject(tmdbJsonStr)
                        if (tmdbJson.has("logos")) {
                            val logosArray = tmdbJson.getJSONArray("logos")
                            var englishLogo = ""
                            var fallbackLogo = ""
                            for (i in 0 until logosArray.length()) {
                                val obj = logosArray.getJSONObject(i)
                                val lang = obj.optString("iso_639_1", "")
                                val filePath = obj.optString("file_path", "")
                                if (filePath.isNotEmpty()) {
                                    if (lang == "en" && englishLogo.isEmpty()) {
                                        englishLogo = "https://image.tmdb.org/t/p/original$filePath"
                                    } else if (fallbackLogo.isEmpty()) {
                                        fallbackLogo = "https://image.tmdb.org/t/p/original$filePath"
                                    }
                                }
                            }
                            val chosenLogo = englishLogo.ifEmpty { fallbackLogo }
                            if (chosenLogo.isNotEmpty()) return chosenLogo
                        }
                    }
                }
            } catch (_: Exception) {}

            try {
                val allLangUrl = "https://api.themoviedb.org/3/$type/$id/images?api_key=5a7f00b2528e0278ae94cd386deb6116"
                val allLangRequest = Request.Builder().url(allLangUrl).build()
                client.newCall(allLangRequest).execute().use { tmdbResponse ->
                    if (tmdbResponse.isSuccessful) {
                        val tmdbJsonStr = tmdbResponse.body?.string() ?: return@use
                        val tmdbJson = JSONObject(tmdbJsonStr)
                        if (tmdbJson.has("logos")) {
                            val logosArray = tmdbJson.getJSONArray("logos")
                            if (logosArray.length() > 0) {
                                val filePath = logosArray.getJSONObject(0).optString("file_path", "")
                                if (filePath.isNotEmpty()) {
                                    return "https://image.tmdb.org/t/p/original$filePath"
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
            return ""
        }

        // 1. Try TMDB logo for targetType
        if (tmdbId != null && tmdbId > 0) {
            val logo = queryTmdbLogo(targetType, tmdbId)
            if (logo.isNotEmpty()) return@withContext logo

            // Fallback: try alternate media type (movie <-> tv)
            val altType = if (targetType == "movie") "tv" else "movie"
            val altLogo = queryTmdbLogo(altType, tmdbId)
            if (altLogo.isNotEmpty()) {
                if (altType == "movie") {
                    AnimeMovieTmdbMapping.setDynamicType(anilistId, "movie")
                    AnimeMovieTmdbMapping.setDynamicMapping(anilistId, tmdbId, "movie")
                }
                return@withContext altLogo
            }
        }

        // 2. Try AniZip Clearlogo fallback
        val mappingUrl = "https://api.ani.zip/mappings?anilist_id=$anilistId"
        val request = Request.Builder().url(mappingUrl).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: return@use ""
                    val json = JSONObject(jsonStr)
                    
                    if (json.has("images")) {
                        val imagesArray = json.getJSONArray("images")
                        for (i in 0 until imagesArray.length()) {
                            val imgObj = imagesArray.getJSONObject(i)
                            val coverType = imgObj.optString("coverType", "")
                            if (coverType.equals("Clearlogo", ignoreCase = true) || coverType.equals("Logo", ignoreCase = true)) {
                                val url = imgObj.optString("url", "")
                                if (url.isNotEmpty()) return@use url
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {}
        return@withContext ""
    }

    private val animeCoverCache = android.util.LruCache<Int, String>(100)

    suspend fun getAnimeCover(anilistId: Int): String = withContext(Dispatchers.IO) {
        if (ShinChanEpisodeProvider.isShinChan(anilistId)) {
            return@withContext ShinChanEpisodeProvider.SHINCHAN_POSTER_URL
        }
        val cached = animeCoverCache.get(anilistId)
        if (cached != null && cached.isNotEmpty()) return@withContext cached

        try {
            val aniJson = getAniZipJson(anilistId)
            if (aniJson != null && aniJson.has("images")) {
                val images = aniJson.getJSONArray("images")
                for (i in 0 until images.length()) {
                    val img = images.getJSONObject(i)
                    val imgType = img.optString("image_type", img.optString("type", ""))
                    if (imgType.equals("poster", ignoreCase = true) || imgType.equals("cover", ignoreCase = true)) {
                        val url = img.optString("image_url", img.optString("url", ""))
                        if (url.isNotBlank()) {
                            animeCoverCache.put(anilistId, url)
                            return@withContext url
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback to AniList
        try {
            val aniCover = AniListClient.getAnimeCover(anilistId)
            if (aniCover.isNotBlank()) {
                animeCoverCache.put(anilistId, aniCover)
                return@withContext aniCover
            }
        } catch (_: Exception) {}

        // Fallback to Kitsu
        try {
            val detail = KitsuClient.getAnimeDetail(anilistId)
            if (detail != null && detail.coverUrl.isNotBlank()) {
                animeCoverCache.put(anilistId, detail.coverUrl)
                return@withContext detail.coverUrl
            }
        } catch (_: Exception) {}

        return@withContext ""
    }

    private val animeBackdropCache = android.util.LruCache<Int, String>(100)

    suspend fun getAnimeBackdropUrl(anilistId: Int): String = withContext(Dispatchers.IO) {
        val cached = animeBackdropCache.get(anilistId)
        if (cached != null && cached.isNotEmpty()) return@withContext cached

        var tmdbId = getLongRunningTmdbId(anilistId)
        if (tmdbId == null || tmdbId <= 0) {
            tmdbId = getTmdbId(anilistId)
        }
        if (tmdbId == null || tmdbId <= 0) {
            val aniJson = getAniZipJson(anilistId)
            if (aniJson != null && aniJson.has("mappings")) {
                val mappings = aniJson.getJSONObject("mappings")
                val tId = mappings.optInt("themoviedb_id", 0)
                if (tId > 0) tmdbId = tId else {
                    val sId = mappings.optInt("themoviedb_season_id", 0)
                    if (sId > 0) tmdbId = sId
                }
            }
        }
        if (tmdbId == null || tmdbId <= 0) {
            tmdbId = getTmdbIdFromFribb(anilistId)
        }

        val dynamicType = AnimeMovieTmdbMapping.getDynamicTmdbType(anilistId)
        val targetType = when {
            dynamicType != null -> dynamicType.lowercase()
            AnimeMovieTmdbMapping.isStaticMovie(anilistId) -> "movie"
            else -> "tv"
        }

        // 1. Check TMDB backdrops
        if (tmdbId != null && tmdbId > 0) {
            // Check direct show details first
            try {
                val detailsUrl = "https://api.themoviedb.org/3/$targetType/$tmdbId?api_key=5a7f00b2528e0278ae94cd386deb6116"
                val req = Request.Builder().url(detailsUrl).build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val json = JSONObject(resp.body?.string() ?: "")
                        val backdrop = json.optString("backdrop_path", "")
                        if (backdrop.isNotEmpty() && backdrop != "null") {
                            val url = "https://image.tmdb.org/t/p/w780$backdrop"
                            animeBackdropCache.put(anilistId, url)
                            return@withContext url
                        }
                    }
                }
            } catch (_: Exception) {}

            val tmdbUrl = "https://api.themoviedb.org/3/$targetType/$tmdbId/images?api_key=5a7f00b2528e0278ae94cd386deb6116"
            val tmdbRequest = Request.Builder().url(tmdbUrl).build()
            try {
                client.newCall(tmdbRequest).execute().use { tmdbResponse ->
                    if (tmdbResponse.isSuccessful) {
                        val tmdbJsonStr = tmdbResponse.body?.string() ?: return@use
                        val tmdbJson = JSONObject(tmdbJsonStr)
                        if (tmdbJson.has("backdrops")) {
                            val backdrops = tmdbJson.getJSONArray("backdrops")
                            if (backdrops.length() > 0) {
                                val filePath = backdrops.getJSONObject(0).optString("file_path", "")
                                if (filePath.isNotEmpty()) {
                                    val url = "https://image.tmdb.org/t/p/w780$filePath"
                                    animeBackdropCache.put(anilistId, url)
                                    return@withContext url
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Check AniZip / TVDB Fanart or Banner images
        try {
            val aniJson = getAniZipJson(anilistId)
            if (aniJson != null && aniJson.has("images")) {
                val imagesArray = aniJson.getJSONArray("images")
                for (i in 0 until imagesArray.length()) {
                    val imgObj = imagesArray.getJSONObject(i)
                    val coverType = imgObj.optString("coverType", imgObj.optString("image_type", ""))
                    if (coverType.equals("Fanart", ignoreCase = true) || coverType.equals("Banner", ignoreCase = true) || coverType.equals("Background", ignoreCase = true)) {
                        val url = imgObj.optString("url", "")
                        if (url.isNotEmpty()) {
                            animeBackdropCache.put(anilistId, url)
                            return@withContext url
                        }
                    }
                }
            }
        } catch (e: Exception) {}

        // 3. Check AniList bannerImage
        try {
            val detailsStr = AniListClient.getAnimeDetails(anilistId)
            if (detailsStr != null && detailsStr.isNotEmpty()) {
                val json = JSONObject(detailsStr)
                val media = json.optJSONObject("data")?.optJSONObject("Media")
                val banner = media?.optString("bannerImage", "") ?: ""
                if (banner.isNotEmpty()) {
                    animeBackdropCache.put(anilistId, banner)
                    return@withContext banner
                }
            }
        } catch (_: Exception) {}

        ""
    }

    suspend fun getTvdbBackdropFromAniZip(anilistId: Int): String = withContext(Dispatchers.IO) {
        // Priority 1: Direct TVDB Fanart from AniZip images mapping
        try {
            val aniJson = getAniZipJson(anilistId)
            if (aniJson != null && aniJson.has("images")) {
                val imagesArray = aniJson.getJSONArray("images")
                for (i in 0 until imagesArray.length()) {
                    val imgObj = imagesArray.getJSONObject(i)
                    val coverType = imgObj.optString("coverType", imgObj.optString("image_type", ""))
                    if (coverType.equals("Fanart", ignoreCase = true)) {
                        val url = imgObj.optString("url", "")
                        if (url.isNotEmpty()) return@withContext url
                    }
                }
                for (i in 0 until imagesArray.length()) {
                    val imgObj = imagesArray.getJSONObject(i)
                    val coverType = imgObj.optString("coverType", imgObj.optString("image_type", ""))
                    if (coverType.equals("Banner", ignoreCase = true) || coverType.equals("Background", ignoreCase = true)) {
                        val url = imgObj.optString("url", "")
                        if (url.isNotEmpty()) return@withContext url
                    }
                }
            }
        } catch (e: Exception) {}

        // Priority 2: Fallback to TMDB backdrop
        return@withContext getAnimeBackdropUrl(anilistId)
    }

    suspend fun getClearLogoUrl(anilistId: Int): String = withContext(Dispatchers.IO) {
        return@withContext getAnimeLogoUrl(anilistId)
    }


    private var fribbAnilistToTmdbMovieMap: Map<Int, Int>? = null
    private var fribbAnilistToTmdbTvMap: Map<Int, Int>? = null
    private var fribbAnilistToTmdbMap: Map<Int, Int>? = null
    private var isFribbLoading = false

    suspend fun getTmdbIdFromFribb(anilistId: Int, isMovie: Boolean = false): Int? = withContext(Dispatchers.IO) {
        try {
            if (fribbAnilistToTmdbMap == null && !isFribbLoading) {
                isFribbLoading = true
                try {
                    val url = "https://raw.githubusercontent.com/Fribb/anime-lists/master/anime-list-full.json"
                    val request = Request.Builder().url(url).build()
                    client.newCall(request).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string() ?: ""
                            if (body.isNotEmpty()) {
                                val arr = org.json.JSONArray(body)
                                val allMap = mutableMapOf<Int, Int>()
                                val movieMap = mutableMapOf<Int, Int>()
                                val tvMap = mutableMapOf<Int, Int>()
                                for (i in 0 until arr.length()) {
                                    val obj = arr.getJSONObject(i)
                                    val aId = obj.optInt("anilist_id", 0)
                                    val tId = obj.optInt("themoviedb_id", 0)
                                    val type = obj.optString("type", "").uppercase()
                                    if (aId > 0 && tId > 0) {
                                        allMap[aId] = tId
                                        if (type.contains("MOVIE")) {
                                            movieMap[aId] = tId
                                        } else {
                                            tvMap[aId] = tId
                                        }
                                    }
                                }
                                fribbAnilistToTmdbMap = allMap
                                fribbAnilistToTmdbMovieMap = movieMap
                                fribbAnilistToTmdbTvMap = tvMap
                            }
                        }
                    }
                } finally {
                    isFribbLoading = false
                }
            }
            return@withContext if (isMovie) {
                fribbAnilistToTmdbMovieMap?.get(anilistId) ?: fribbAnilistToTmdbMap?.get(anilistId)
            } else {
                fribbAnilistToTmdbTvMap?.get(anilistId) ?: fribbAnilistToTmdbMap?.get(anilistId)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun verifyTmdbIsAnime(tmdbId: Int, mediaType: String): String? {
        if (tmdbId <= 0) return null
        try {
            val tmdbUrl = "https://api.themoviedb.org/3/$mediaType/$tmdbId?api_key=5a7f00b2528e0278ae94cd386deb6116"
            val request = Request.Builder().url(tmdbUrl).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: ""
                    if (jsonStr.isNotEmpty()) {
                        val tmdbJson = JSONObject(jsonStr)
                        if (tmdbJson.has("status_code") && tmdbJson.optInt("status_code", 0) != 0) {
                            return null
                        }

                        // Strict type verification: Movie must have 'title', TV must have 'name'
                        if (mediaType == "movie") {
                            if (!tmdbJson.has("title") || tmdbJson.has("number_of_seasons")) {
                                return null
                            }
                        } else if (mediaType == "tv") {
                            if (!tmdbJson.has("name")) {
                                return null
                            }
                        }
                        
                        // Check if anime:
                        // 1. Genres contain Animation (id: 16)
                        var isAnimation = false
                        if (tmdbJson.has("genres")) {
                            val genresArr = tmdbJson.optJSONArray("genres")
                            if (genresArr != null) {
                                for (i in 0 until genresArr.length()) {
                                    val g = genresArr.optJSONObject(i)
                                    if (g != null) {
                                        val gid = g.optInt("id", 0)
                                        val gname = g.optString("name", "")
                                        if (gid == 16 || gname.contains("Animation", ignoreCase = true) || gname.contains("Anime", ignoreCase = true)) {
                                            isAnimation = true
                                            break
                                        }
                                    }
                                }
                            }
                        }
                        
                        // 2. Country is Japan (JP) or original language is 'ja'
                        val origLang = tmdbJson.optString("original_language", "")
                        var isJapan = origLang.equals("ja", ignoreCase = true)
                        
                        if (!isJapan && tmdbJson.has("origin_country")) {
                            val originArr = tmdbJson.optJSONArray("origin_country")
                            if (originArr != null) {
                                for (i in 0 until originArr.length()) {
                                    if (originArr.optString(i, "").equals("JP", ignoreCase = true)) {
                                        isJapan = true
                                        break
                                    }
                                }
                            }
                        }
                        if (!isJapan && tmdbJson.has("production_countries")) {
                            val prodArr = tmdbJson.optJSONArray("production_countries")
                            if (prodArr != null) {
                                for (i in 0 until prodArr.length()) {
                                    val code = prodArr.optJSONObject(i)?.optString("iso_3166_1", "") ?: ""
                                    if (code.equals("JP", ignoreCase = true)) {
                                        isJapan = true
                                        break
                                    }
                                }
                            }
                        }
                        
                        // If it is verified Anime (Animation OR from Japan)
                        if (isAnimation || isJapan) {
                            val backdropPath = tmdbJson.optString("backdrop_path", "")
                            if (backdropPath.isNotEmpty() && backdropPath != "null") {
                                return "https://image.tmdb.org/t/p/original$backdropPath"
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AniZipClient", "verifyTmdbIsAnime error: ${e.message}")
        }
        return null
    }

    suspend fun getTmdbBackdropUrl(anilistId: Int, isMovie: Boolean = false): String = withContext(Dispatchers.IO) {
        if (anilistId == 129201) {
            return@withContext "https://image.tmdb.org/t/p/original/1czz0r7urqCPP0CZTAEkCk4TZY1.jpg"
        }
        val staticMovieTmdb = AnimeMovieTmdbMapping.getMovieTmdbId(anilistId)
        if (staticMovieTmdb != null && staticMovieTmdb > 0) {
            val verified = verifyTmdbIsAnime(staticMovieTmdb, "movie")
            if (!verified.isNullOrEmpty()) {
                return@withContext verified
            }
        }
        val targetMediaType = if (isMovie || staticMovieTmdb != null) "movie" else "tv"
        try {
            // Step 1: Check AniZip mapping with Anime Verification (Animation genre / Japan country & strict media type)
            val json = getAniZipJson(anilistId)
            var anizipTmdbId = 0
            var anizipIsMovie = false
            if (json != null && json.has("mappings")) {
                val mappings = json.getJSONObject("mappings")
                if (mappings.optString("type", "").equals("MOVIE", ignoreCase = true)) {
                    anizipIsMovie = true
                    AnimeMovieTmdbMapping.setDynamicType(anilistId, "movie")
                }
                anizipTmdbId = try {
                    mappings.getString("themoviedb_id").toIntOrNull() ?: 0
                } catch (e: Exception) {
                    mappings.optInt("themoviedb_id", 0)
                }
            }

            val effectiveMediaType = if (targetMediaType == "movie" || anizipIsMovie) "movie" else "tv"

            if (anizipTmdbId > 0) {
                val verifiedBackdrop = verifyTmdbIsAnime(anizipTmdbId, effectiveMediaType)
                if (!verifiedBackdrop.isNullOrEmpty()) {
                    if (effectiveMediaType == "movie") {
                        AnimeMovieTmdbMapping.setDynamicMapping(anilistId, anizipTmdbId, "movie")
                    }
                    return@withContext verifiedBackdrop
                }
                // Fallback: try alternate media type (movie <-> tv)
                val altMediaType = if (effectiveMediaType == "movie") "tv" else "movie"
                val altBackdrop = verifyTmdbIsAnime(anizipTmdbId, altMediaType)
                if (!altBackdrop.isNullOrEmpty()) {
                    if (altMediaType == "movie") {
                        AnimeMovieTmdbMapping.setDynamicType(anilistId, "movie")
                        AnimeMovieTmdbMapping.setDynamicMapping(anilistId, anizipTmdbId, "movie")
                    }
                    return@withContext altBackdrop
                }
            }

            // Step 2: Fallback to Fribb's list if AniZip TMDB was wrong or not anime
            val fribbTmdbId = getTmdbIdFromFribb(anilistId, isMovie = isMovie || anizipIsMovie) ?: 0
            if (fribbTmdbId > 0 && fribbTmdbId != anizipTmdbId) {
                val fribbBackdrop = verifyTmdbIsAnime(fribbTmdbId, effectiveMediaType)
                if (!fribbBackdrop.isNullOrEmpty()) {
                    return@withContext fribbBackdrop
                }
                val altMediaType = if (effectiveMediaType == "movie") "tv" else "movie"
                val altBackdrop = verifyTmdbIsAnime(fribbTmdbId, altMediaType)
                if (!altBackdrop.isNullOrEmpty()) {
                    return@withContext altBackdrop
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AniZipClient", "getTmdbBackdropUrl error for $anilistId: ${e.message}", e)
        }
        return@withContext ""
    }

    suspend fun getTvdbBackdropUrl(anilistId: Int): String = withContext(Dispatchers.IO) {
        try {
            val json = getAniZipJson(anilistId) ?: return@withContext ""
            if (json.has("images")) {
                val imagesArray = json.getJSONArray("images")
                var bannerUrl = ""
                for (i in 0 until imagesArray.length()) {
                    val imgObj = imagesArray.getJSONObject(i)
                    val coverType = imgObj.optString("coverType")
                    if (coverType.equals("Fanart", ignoreCase = true)) {
                        val url = imgObj.optString("url", "")
                        if (url.isNotEmpty()) return@withContext url
                    } else if (coverType.equals("Banner", ignoreCase = true)) {
                        val url = imgObj.optString("url", "")
                        if (url.isNotEmpty()) {
                            bannerUrl = url
                        }
                    }
                }
                if (bannerUrl.isNotEmpty()) return@withContext bannerUrl
            }
        } catch (e: Exception) {
            android.util.Log.e("AniZipClient", "getTvdbBackdropUrl error: ${e.message}", e)
        }
        return@withContext ""
    }

    suspend fun getAniZipBackdropUrl(anilistId: Int): String = withContext(Dispatchers.IO) {
        return@withContext getTvdbBackdropUrl(anilistId)
    }

    suspend fun getBestBackdropUrl(anilistId: Int, anilistBanner: String = "", isMovie: Boolean = false): String = withContext(Dispatchers.IO) {
        if (ShinChanEpisodeProvider.isShinChan(anilistId)) {
            return@withContext ShinChanEpisodeProvider.SHINCHAN_BACKDROP_URL
        }
        // Priority 1: TMDB backdrop with strict Movie vs TV anime verification
        val tmdbBackdrop = getTmdbBackdropUrl(anilistId, isMovie = isMovie)
        if (tmdbBackdrop.isNotEmpty()) {
            return@withContext tmdbBackdrop
        }

        // Priority 2: TVDB backdrop from AniZip (ONLY for TV series! For movies, TVDB fanart is often the TV show)
        if (!isMovie) {
            val tvdbBackdrop = getTvdbBackdropUrl(anilistId)
            if (tvdbBackdrop.isNotEmpty()) {
                return@withContext tvdbBackdrop
            }
        }

        // Priority 3: AniList banner
        if (anilistBanner.isNotEmpty()) {
            return@withContext anilistBanner
        }

        return@withContext ""
    }

    suspend fun getTmdbId(anilistId: Int): Int? = withContext(Dispatchers.IO) {
        val staticMovieTmdb = AnimeMovieTmdbMapping.getMovieTmdbId(anilistId)
        if (staticMovieTmdb != null && staticMovieTmdb > 0) return@withContext staticMovieTmdb
        try {
            val json = getAniZipJson(anilistId)
            if (json != null && json.has("mappings")) {
                val mappings = json.getJSONObject("mappings")
                val tmdbId = mappings.optInt("themoviedb_id", 0)
                if (tmdbId > 0) return@withContext tmdbId
            }
            val fribbId = getTmdbIdFromFribb(anilistId)
            if (fribbId != null && fribbId > 0) return@withContext fribbId
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
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
                                                        val rawName = epObj.optString("name", "")
                                                        val name = if (rawName.isNotEmpty() && !rawName.equals("null", ignoreCase = true)) rawName else "Episode ${startAbs + j}"
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

    fun getLongRunningTmdbId(anilistId: Int): Int? {
        if (ShinChanEpisodeProvider.isShinChan(anilistId)) return null
        return AnimeMovieTmdbMapping.getMovieTmdbId(anilistId) ?: when (anilistId) {
        21 -> 37854    // One Piece
        235 -> 30983   // Detective Conan / Case Closed
        20 -> 46260    // Naruto
        1735 -> 31910  // Naruto Shippuden
        269 -> 30984   // Bleach
        918 -> 57243   // Gintama
        527 -> 60572   // Pokemon
        158871 -> 220150 // Pokemon Horizons: The Series (2023)
        12031 -> 61663 // Fairy Tail
        223 -> 34747   // Dragon Ball
        813 -> 12697   // Dragon Ball Z
        6702 -> 34748  // Fairy Tail (2009)
        1316 -> 44872  // Idaten Jump
        1319 -> 44872  // Idaten Jump (alias)
        // Kiteretsu Daihyakka (331 eps)
        6509 -> 80609
        2021 -> 80609

        // Chinpui / Chimpui (56 eps / 112 segments)
        5908 -> 65719
        2300 -> 65719

        // Monster Kid / Kaibutsu-kun (1980: 94/188 eps | 1968: 49 eps)
        3130 -> 66251   // 1980 color series (Latest near version)
        4094 -> 66251
        3129 -> 204045  // 1968 B&W series
        5898 -> 204045

        // Ultra B (119 eps)
        10250 -> 207937
        4216 -> 207937

        // Tsurupika Hagemaru-kun (59 eps)
        5830 -> 209246
        3272 -> 209246

        // Ninja Hattori-kun (1981: 694 eps | 2012: 52/104 eps)
        4936 -> 158198   // Ninja Hattori-kun Returns (mapped to TMDB 158198)
        3949 -> 158198
        18845 -> 158198
        158198 -> 158198
        83307 -> 158198
        80885 -> 80885

        // Perman (1983: 526 eps | 1967: 54 eps)
        11595 -> 132791 // 1983 color series (Latest near version)
        2022 -> 132791
        132791 -> 132791
        6303 -> 65739   // 1967 B&W series
        6566 -> 65739
        else -> null
    }
}
}

data class EpisodeMeta(
    val title: String,
    val imageUrl: String,
    val airdate: String = "",
    val seasonNumber: Int = 1,
    val episodeNumber: Int = 1,
    val overview: String = "",
    val runtime: Int = 24,
    val isFiller: Boolean = false
)
