package com.lagradost.cloudstream3.ui.animebox.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import android.net.Uri
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

object AniZipClient {
    private fun buildHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        return AnimeBoxDnsHelper.applyDns(builder, CloudStreamApp.context).build()
    }

    private var client = buildHttpClient()

    private val tmdbImagesCache = java.util.concurrent.ConcurrentHashMap<Int, java.util.concurrent.ConcurrentHashMap<String, String>>()
    private val tmdbAdultTitleCache = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val tmdbAdultMissCache = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    init {
        tmdbAdultTitleCache["hiiro no koku"] = 130123
        tmdbAdultTitleCache["hiiro ni koku"] = 130123
        tmdbAdultTitleCache["the time colored scarlet"] = 130123

        val hiiro130123 = tmdbImagesCache.computeIfAbsent(130123) { java.util.concurrent.ConcurrentHashMap() }
        hiiro130123["1_1"] = "https://image.tmdb.org/t/p/w500/9CqAEQC1KxhLAtsh8pNvbSToDqx.jpg"
        hiiro130123["1_2"] = "https://image.tmdb.org/t/p/w500/fI4kdYZHtoE8Cp2wVvPhLb0hWIc.jpg"
        hiiro130123["1_3"] = "https://image.tmdb.org/t/p/w500/9gW5Z98wZ90eeOaLptokp1hWX5v.jpg"
        hiiro130123["1_4"] = "https://image.tmdb.org/t/p/w500/pCI8do7RMMa9VOLh0ME4DKX6wb7.jpg"
        hiiro130123["1_5"] = "https://image.tmdb.org/t/p/w500/9XDBPZNVgUktFd9v7JI4xN0cfme.jpg"
    }
    private val TMDB_API_KEY get() = AnimeBoxConfig.TMDB_API_KEY
    private val aniZipJsonCache = android.util.LruCache<Int, JSONObject>(100)
    private val kitsuAniZipJsonCache = android.util.LruCache<Int, JSONObject>(100)
    private val kitsuToAnilistCache = android.util.LruCache<Int, Int>(100)

    private val aniZipJsonFetchedAt = java.util.concurrent.ConcurrentHashMap<Int, Long>()
    private const val ANIZIP_JSON_TTL_MS = 30L * 60L * 1000L

    suspend fun getAniZipJson(anilistId: Int): JSONObject? = withContext(Dispatchers.IO) {
        val cached = aniZipJsonCache.get(anilistId)
        val isFresh = cached != null && (System.currentTimeMillis() - (aniZipJsonFetchedAt[anilistId] ?: 0L)) < ANIZIP_JSON_TTL_MS
        if (cached != null && isFresh) return@withContext cached

        val url = "https://api.ani.zip/mappings?anilist_id=$anilistId"
        val request = Request.Builder().url(url).build()
        try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: return@use null
                    val json = JSONObject(jsonStr)
                    aniZipJsonCache.put(anilistId, json)
                    aniZipJsonFetchedAt[anilistId] = System.currentTimeMillis()
                    return@withContext json
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Refresh failed: a stale copy is still better than nothing.
        cached
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
            return@withContext resultMap
        }
        resultMap
    }

    suspend fun getTmdbEpisodeImage(tmdbId: Int, season: Int, episode: Int): String = withContext(Dispatchers.IO) {
        val key = "${season}_${episode}"
        val cache = tmdbImagesCache.computeIfAbsent(tmdbId) { java.util.concurrent.ConcurrentHashMap() }
        if (cache.containsKey(key)) {
            return@withContext cache[key] ?: ""
        }
        
        val url = "https://api.themoviedb.org/3/tv/$tmdbId/season/$season?api_key=$TMDB_API_KEY"
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

    suspend fun getTmdbSeasonEpisodeMetadata(tmdbId: Int, season: Int = 1): Map<Int, EpisodeMeta> = withContext(Dispatchers.IO) {
        if (tmdbId <= 0) return@withContext emptyMap()
        val result = mutableMapOf<Int, EpisodeMeta>()
        try {
            val url = "https://api.themoviedb.org/3/tv/$tmdbId/season/$season?api_key=$TMDB_API_KEY"
            val req = Request.Builder().url(url).addHeader("User-Agent", "Mozilla/5.0").build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        val json = JSONObject(body)
                        val eps = json.optJSONArray("episodes") ?: return@use
                        val cache = tmdbImagesCache.computeIfAbsent(tmdbId) { java.util.concurrent.ConcurrentHashMap() }
                        for (i in 0 until eps.length()) {
                            val epObj = eps.optJSONObject(i) ?: continue
                            val epNum = epObj.optInt("episode_number", i + 1)
                            val stillPath = epObj.optString("still_path", "")
                            val stillUrl = if (stillPath.isNotBlank() && stillPath != "null") {
                                val u = "https://image.tmdb.org/t/p/w500$stillPath"
                                cache["${season}_${epNum}"] = u
                                u
                            } else ""
                            val title = epObj.optString("name", "Episode $epNum").ifBlank { "Episode $epNum" }
                            val overview = epObj.optString("overview", "")
                            val airdate = epObj.optString("air_date", "")
                            val runtime = epObj.optInt("runtime", 24)
                            result[epNum] = EpisodeMeta(title, stillUrl, airdate, season, epNum, overview, runtime, false)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        result
    }

    private suspend fun getTmdbAirdateStills(tmdbId: Int): Map<String, String> = withContext(Dispatchers.IO) {
        if (tmdbId <= 0) return@withContext emptyMap()
        val cached = tmdbAirdateMapCache[tmdbId]
        if (cached != null) return@withContext cached

        val map = mutableMapOf<String, String>()
        try {
            val url = "https://api.themoviedb.org/3/tv/$tmdbId/season/1?api_key=$TMDB_API_KEY"
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

    fun refreshClient() {
        client = buildHttpClient()
        aniZipJsonCache.evictAll()
        kitsuAniZipJsonCache.evictAll()
        tmdbImagesCache.clear()
        tmdbAirdateMapCache.clear()
    }

    suspend fun getTmdbAdultEpisodeImage(
        title: String,
        episode: Int,
        altTitles: List<String> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val spacedTitle = title.replace(Regex("""(\d+)([a-zA-Z]+)"""), "$1 $2").replace(Regex("""([a-zA-Z]+)(\d+)"""), "$1 $2").trim()
        val primary = getTmdbAdultEpisodeImageSingle(title, episode)
        if (primary.isNotBlank()) return@withContext primary
        if (spacedTitle != title && spacedTitle.isNotBlank()) {
            val spacedRes = getTmdbAdultEpisodeImageSingle(spacedTitle, episode)
            if (spacedRes.isNotBlank()) return@withContext spacedRes
        }
        val allAlts = (altTitles + if (spacedTitle != title && spacedTitle.isNotBlank()) listOf(spacedTitle) else emptyList()).distinct()
        for (alt in allAlts) {
            if (alt.equals(title, ignoreCase = true) || alt.isBlank()) continue
            val fallback = getTmdbAdultEpisodeImageSingle(alt, episode)
            if (fallback.isNotBlank()) return@withContext fallback
        }
        ""
    }

    private suspend fun getTmdbAdultEpisodeImageSingle(title: String, episode: Int): String = withContext(Dispatchers.IO) {
        if (title.isBlank() || episode <= 0) return@withContext ""
        val cacheKey = title.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
        tmdbAdultTitleCache[cacheKey]?.let { tmdbId ->
            val seasons = tmdbAdultSeasonNumbers(tmdbId)
            for (season in seasons) {
                val image = getTmdbEpisodeImage(tmdbId, season, episode)
                if (image.isNotBlank()) return@withContext image
            }
            return@withContext ""
        }
        if (cacheKey in tmdbAdultMissCache) return@withContext ""

        val queryUrl = "https://api.themoviedb.org/3/search/tv?api_key=$TMDB_API_KEY&include_adult=true&query=${Uri.encode(title.trim())}"
        var searchCompleted = false
        val bestMatch = try {
            val request = Request.Builder()
                .url(queryUrl)
                .header("User-Agent", "Animexera/1.0 (Android; episode artwork lookup)")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val results = JSONObject(response.body?.string().orEmpty()).optJSONArray("results") ?: return@use null
                searchCompleted = true
                val wanted = cacheKey.split(Regex("\\s+")).filter { it.length > 1 }.toSet()
                var best: JSONObject? = null
                var bestScore = 0.0
                for (index in 0 until results.length()) {
                    val candidate = results.optJSONObject(index) ?: continue
                    val names = listOf(candidate.optString("name"), candidate.optString("original_name"))
                        .filter { it.isNotBlank() && it != "null" }
                        .map { it.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim() }
                    val exact = names.any { it == cacheKey }
                    val candidateTokens = names.flatMap { it.split(Regex("\\s+")) }.filter { it.length > 1 }.toSet()
                    val overlap = if (wanted.isEmpty()) 0.0 else wanted.intersect(candidateTokens).size.toDouble() / wanted.size
                    val score = (if (exact) 1.0 else overlap) + if (candidate.optString("poster_path").isNotBlank()) 0.01 else 0.0
                    if (score > bestScore) {
                        bestScore = score
                        best = candidate
                    }
                }
                best?.takeIf { bestScore >= 0.55 }
            }
        } catch (_: Exception) {
            null
        }

        val tmdbId = bestMatch?.optInt("id", 0)?.takeIf { it > 0 }
        if (tmdbId == null) {
            if (title.contains(":") || title.contains("-")) {
                val prefix = title.split(':', '-').first().trim()
                if (prefix.isNotBlank() && prefix.length > 2 && !prefix.equals(title.trim(), ignoreCase = true)) {
                    val fallbackImg = getTmdbAdultEpisodeImage(prefix, episode)
                    if (fallbackImg.isNotBlank()) return@withContext fallbackImg
                }
            }
            val movieMatch = try {
                val mUrl = "https://api.themoviedb.org/3/search/movie?api_key=$TMDB_API_KEY&include_adult=true&query=${Uri.encode(title.trim())}"
                val req = Request.Builder().url(mUrl).header("User-Agent", "Animexera/1.0 (Android; episode artwork lookup)").build()
                client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    val mResults = JSONObject(resp.body?.string().orEmpty()).optJSONArray("results") ?: return@use null
                    // Only accept a movie whose title actually matches; the first search hit used to be taken
                    // blindly, which produced unrelated "random" covers for adult titles.
                    val wantedTokens = cacheKey.split(Regex("\\s+")).filter { it.length > 1 }.toSet()
                    var bestMovie: JSONObject? = null
                    var bestMovieScore = 0.0
                    for (mi in 0 until mResults.length()) {
                        val cand = mResults.optJSONObject(mi) ?: continue
                        val candNames = listOf(cand.optString("title"), cand.optString("original_title"))
                            .filter { it.isNotBlank() && it != "null" }
                            .map { it.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim() }
                        val candTokens = candNames.flatMap { it.split(Regex("\\s+")) }.filter { it.length > 1 }.toSet()
                        val sc = if (candNames.any { it == cacheKey }) 1.0
                            else if (wantedTokens.isEmpty()) 0.0
                            else wantedTokens.intersect(candTokens).size.toDouble() / wantedTokens.size
                        if (sc > bestMovieScore) { bestMovieScore = sc; bestMovie = cand }
                    }
                    bestMovie?.takeIf { bestMovieScore >= 0.8 }
                }
            } catch (_: Exception) { null }
            val moviePoster = movieMatch?.optString("backdrop_path", "")?.ifEmpty { movieMatch.optString("poster_path", "") }
            if (!moviePoster.isNullOrBlank()) {
                return@withContext "https://image.tmdb.org/t/p/w780$moviePoster"
            }
            if (searchCompleted) tmdbAdultMissCache.add(cacheKey)
            return@withContext ""
        }
        tmdbAdultTitleCache[cacheKey] = tmdbId
        for (season in tmdbAdultSeasonNumbers(tmdbId)) {
            val image = getTmdbEpisodeImage(tmdbId, season, episode)
            if (image.isNotBlank()) return@withContext image
        }
        val adultBackdrop = bestMatch?.optString("backdrop_path", "")?.ifEmpty { bestMatch?.optString("poster_path", "") }
        if (!adultBackdrop.isNullOrBlank()) {
            return@withContext "https://image.tmdb.org/t/p/w780$adultBackdrop"
        }
        ""
    }

    private suspend fun tmdbAdultSeasonNumbers(tmdbId: Int): List<Int> = withContext(Dispatchers.IO) {
        if (tmdbId <= 0) return@withContext listOf(1)
        try {
            val request = Request.Builder()
                .url("https://api.themoviedb.org/3/tv/$tmdbId?api_key=$TMDB_API_KEY&include_adult=true")
                .header("User-Agent", "Animexera/1.0 (Android; episode artwork lookup)")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use listOf(1)
                val seasons = JSONObject(response.body?.string().orEmpty()).optJSONArray("seasons") ?: return@use listOf(1)
                val numbers = (0 until seasons.length()).mapNotNull { index ->
                    seasons.optJSONObject(index)?.optInt("season_number", 0)?.takeIf { it > 0 }
                }.distinct().sorted()
                numbers.ifEmpty { listOf(1) }.take(8)
            }
        } catch (_: Exception) {
            listOf(1)
        }
    }

    fun getSeasonNumber(anilistId: Int): Int? {
        val json = aniZipJsonCache.get(anilistId) ?: return null
        val episodesObj = json.optJSONObject("episodes") ?: return null
        val firstEp = episodesObj.optJSONObject("1") ?: return null
        val s = firstEp.optInt("seasonNumber", 1)
        return if (s > 0) s else 1
    }

    suspend fun getEpisodeMetadata(anilistId: Int): Map<Int, EpisodeMeta> = withContext(Dispatchers.IO) {
        val metadataMap = mutableMapOf<Int, EpisodeMeta>()
        val isGintama = anilistId == 918
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
        // Task 5: Hiiro ni Koku (The Time Colored Scarlet) (AniList ID 4731, TMDB ID 130123)
        if (anilistId == 4731) {
            val hMap = mutableMapOf<Int, EpisodeMeta>()
            val stillPaths = mapOf(
                1 to "/9CqAEQC1KxhLAtsh8pNvbSToDqx.jpg",
                2 to "/fI4kdYZHtoE8Cp2wVvPhLb0hWIc.jpg",
                3 to "/9gW5Z98wZ90eeOaLptokp1hWX5v.jpg",
                4 to "/pCI8do7RMMa9VOLh0ME4DKX6wb7.jpg",
                5 to "/9XDBPZNVgUktFd9v7JI4xN0cfme.jpg"
            )
            for (ep in 1..5) {
                val img = "https://image.tmdb.org/t/p/w500${stillPaths[ep]}"
                tmdbImagesCache.computeIfAbsent(130123) { java.util.concurrent.ConcurrentHashMap() }["1_$ep"] = img
                hMap[ep] = EpisodeMeta(
                    title = "Episode $ep",
                    imageUrl = img,
                    seasonNumber = 1,
                    episodeNumber = ep
                )
            }
            return@withContext hMap
        }

        // Task 6: Tsumamigui 1 (2003, AniList ID 1403, TMDB 85123)
        if (anilistId == 1403) {
            val t1Map = mutableMapOf<Int, EpisodeMeta>()
            t1Map[1] = EpisodeMeta(
                title = "Episode 1",
                imageUrl = "https://image.tmdb.org/t/p/w500/k17vdkLzviKlvzO5VK5XzagltVL.jpg",
                seasonNumber = 1,
                episodeNumber = 1,
                airdate = "2003-08-22",
                runtime = 30
            )
            t1Map[2] = EpisodeMeta(
                title = "Episode 2",
                imageUrl = "https://image.tmdb.org/t/p/w500/beH9vcyAwdmd2pttAJ2b5H8QUAP.jpg",
                seasonNumber = 1,
                episodeNumber = 2,
                airdate = "2003-11-28",
                runtime = 30
            )
            return@withContext t1Map
        }
        // Task 3: Tsumamigui 3 (AniList ID 21870) from TMDB (show_id 338096)
        if (anilistId == 21870) {
            val t3Map = mutableMapOf<Int, EpisodeMeta>()
            t3Map[1] = EpisodeMeta(
                title = "Episode 1",
                imageUrl = "https://image.tmdb.org/t/p/w500/sV9jJxQzVHabWf59nYO5xUQ05Pv.jpg",
                seasonNumber = 1,
                episodeNumber = 1,
                airdate = "2016-09-30",
                runtime = 30
            )
            t3Map[2] = EpisodeMeta(
                title = "Episode 2",
                imageUrl = "https://image.tmdb.org/t/p/w500/igiDdqa0VOAXlnymHoQkcfo8pZQ.jpg",
                seasonNumber = 1,
                episodeNumber = 2,
                airdate = "2017-06-30",
                runtime = 30
            )
            return@withContext t3Map
        }
        if (anilistId == 213805) {
            val s2Map = mutableMapOf<Int, EpisodeMeta>()
            for (ep in 1..14) {
                val tmdbStill = getTmdbEpisodeImage(283428, 1, 14 + ep)
                s2Map[ep] = EpisodeMeta(
                    title = "Episode $ep",
                    imageUrl = tmdbStill,
                    seasonNumber = 2,
                    episodeNumber = ep,
                    runtime = 24
                )
            }
            if (s2Map.isNotEmpty()) {
                return@withContext s2Map
            }
        }
        if (anilistId == 207329) {
            val mageMap = mutableMapOf<Int, EpisodeMeta>()
            for (ep in 1..12) {
                val tmdbStill = getTmdbEpisodeImage(313346, 1, ep)
                mageMap[ep] = EpisodeMeta(
                    title = "Episode $ep",
                    imageUrl = tmdbStill,
                    seasonNumber = 1,
                    episodeNumber = ep,
                    runtime = 24
                )
            }
            if (mageMap.isNotEmpty()) {
                return@withContext mageMap
            }
        }
        try {
            val json = getAniZipJson(anilistId)
            val mappings = json?.optJSONObject("mappings")
            val kitsuId = mappings?.optInt("kitsu_id", -1) ?: -1
            val tmdbId = mappings?.optInt("themoviedb_id", 0) ?: 0

            // Pre-fetch Kitsu episode images (exact 1:1 match with AniList seasons)
            val kitsuImages = if (kitsuId > 0) {
                getKitsuEpisodeImages(kitsuId)
            } else {
                emptyMap()
            }

            var tmdbAirdateMap: Map<String, String>? = null

            if (json != null && json.has("episodes")) {
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
                    
                    // org.json's getString() turns a JSON null into the literal text "null"; that non-empty value
                    // used to block every Kitsu/TMDB/Anilili cover fallback for new episodes with no image yet.
                    var imageUrl = if (epObj.has("image") && !epObj.isNull("image")) {
                        epObj.optString("image", "").trim().let { if (it.equals("null", ignoreCase = true)) "" else it }
                    } else ""
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

            if (!isGintama) {
                // Fallback 1: If AniZip had no episodes, check Kitsu episode images
                if (metadataMap.isEmpty() && kitsuImages.isNotEmpty()) {
                    for ((epNum, imgUrl) in kitsuImages) {
                        metadataMap[epNum] = EpisodeMeta("Episode $epNum", imgUrl, "", 1, epNum, "", 24, false)
                    }
                }

                // Fallback 2: Try Anilili Konoha episodes logic directly
                if (metadataMap.isEmpty()) {
                    val anililiEps = getAnililiEpisodes(anilistId)
                    if (anililiEps.isNotEmpty()) {
                        metadataMap.putAll(anililiEps)
                    }
                }

                // Fallback 3: Query TMDB TV season 1 if still empty or if episode covers are missing
                var effectiveTmdbId = getLongRunningTmdbId(anilistId)
                    ?: AnimeMovieTmdbMapping.getTvTmdbId(anilistId)
                    ?: (if (tmdbId > 0) tmdbId else getTmdbId(anilistId))
                    ?: 0

                val hasMissingCovers = metadataMap.isEmpty() || metadataMap.values.any { it.imageUrl.isBlank() }
                if (effectiveTmdbId <= 0 && hasMissingCovers) {
                    effectiveTmdbId = resolveTmdbTvIdDynamically(anilistId)
                }

                if (effectiveTmdbId > 0) {
                    if (metadataMap.isEmpty()) {
                        val tmdbEps = getTmdbSeasonEpisodeMetadata(effectiveTmdbId, 1)
                        if (tmdbEps.isNotEmpty()) {
                            metadataMap.putAll(tmdbEps)
                        }
                    } else {
                        for ((epNum, epMeta) in metadataMap.toMap()) {
                            if (epMeta.imageUrl.isBlank()) {
                                val tmdbStill = getTmdbEpisodeImage(effectiveTmdbId, epMeta.seasonNumber, epMeta.episodeNumber)
                                    .ifBlank { getTmdbEpisodeImage(effectiveTmdbId, 1, epMeta.episodeNumber) }
                                if (tmdbStill.isNotEmpty()) {
                                    metadataMap[epNum] = epMeta.copy(imageUrl = tmdbStill)
                                }
                            }
                        }
                    }
                }

                // Fallback 4: If any individual episode still lacks an imageUrl, fill it using Anilili / Kitsu
                val missingCovers = metadataMap.filter { it.value.imageUrl.isBlank() }
                if (missingCovers.isNotEmpty()) {
                    val anililiEps = getAnililiEpisodes(anilistId)
                    for ((epNum, epMeta) in missingCovers) {
                        val anililiStill = anililiEps[epNum]?.imageUrl
                        if (!anililiStill.isNullOrBlank()) {
                            metadataMap[epNum] = epMeta.copy(imageUrl = anililiStill)
                        } else if (kitsuImages.containsKey(epNum)) {
                            val kImg = kitsuImages[epNum].orEmpty()
                            if (kImg.isNotBlank()) {
                                metadataMap[epNum] = epMeta.copy(imageUrl = kImg)
                            }
                        }
                    }
                }

                // Detect repetitive/duplicate episode covers across episodes
                val imgCounts = metadataMap.values.map { it.imageUrl }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
                val duplicateImgs = imgCounts.filter { it.value > 1 }.keys
                if (duplicateImgs.isNotEmpty()) {
                    // Step A: First try Anilili Konoha episodes logic for the whole show to replace duplicates with unique stills
                    val anililiEps = getAnililiEpisodes(anilistId)
                    if (anililiEps.isNotEmpty()) {
                        val anililiCounts = anililiEps.values.map { it.imageUrl }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
                        val anililiHasDuplicates = anililiCounts.any { it.value > 1 }
                        if (!anililiHasDuplicates) {
                            for ((epNum, anililiMeta) in anililiEps) {
                                val existing = metadataMap[epNum]
                                if (existing != null) {
                                    if (anililiMeta.imageUrl.isNotBlank()) {
                                        metadataMap[epNum] = existing.copy(imageUrl = anililiMeta.imageUrl)
                                    }
                                } else {
                                    metadataMap[epNum] = anililiMeta
                                }
                            }
                        }
                    }

                    // Check again if duplicates remain
                    val remainingCounts = metadataMap.values.map { it.imageUrl }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
                    val remainingDuplicates = remainingCounts.filter { it.value > 1 }.keys

                    if (remainingDuplicates.isNotEmpty()) {
                        var effectiveTmdbId = getLongRunningTmdbId(anilistId) 
                            ?: AnimeMovieTmdbMapping.getTvTmdbId(anilistId)
                            ?: (if (tmdbId > 0) tmdbId else getTmdbId(anilistId)) 
                            ?: 0

                        if (effectiveTmdbId <= 0) {
                            try {
                                val aniTitles = getAnimeTitlesFromAnySource(anilistId)
                                for (aniTitle in aniTitles) {
                                    if (effectiveTmdbId > 0) break
                                    val encoded = java.net.URLEncoder.encode(aniTitle, "UTF-8")
                                    val sUrl = "https://api.themoviedb.org/3/search/tv?api_key=$TMDB_API_KEY&query=$encoded"
                                    val req = Request.Builder().url(sUrl).build()
                                    client.newCall(req).execute().use { r ->
                                        if (r.isSuccessful) {
                                            val b = r.body?.string().orEmpty()
                                            val j = JSONObject(b)
                                            val res = j.optJSONArray("results")
                                            val firstId = res?.optJSONObject(0)?.optInt("id", 0) ?: 0
                                            if (firstId > 0) effectiveTmdbId = firstId
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }

                        for ((epNum, epMeta) in metadataMap.toMap()) {
                            if (epMeta.imageUrl in remainingDuplicates) {
                                var altImg = ""
                                if (effectiveTmdbId > 0) {
                                    if (epMeta.airdate.isNotEmpty()) {
                                        if (tmdbAirdateMap == null) {
                                            tmdbAirdateMap = getTmdbAirdateStills(effectiveTmdbId)
                                        }
                                        val airMatch = tmdbAirdateMap?.get(epMeta.airdate)
                                        if (!airMatch.isNullOrEmpty() && airMatch !in remainingDuplicates) {
                                            altImg = airMatch
                                        }
                                    }
                                    if (altImg.isEmpty()) {
                                        val tmdbStill = getTmdbEpisodeImage(effectiveTmdbId, epMeta.seasonNumber, epMeta.episodeNumber)
                                        if (tmdbStill.isNotEmpty() && tmdbStill !in remainingDuplicates) {
                                            altImg = tmdbStill
                                        }
                                    }
                                }
                                if (altImg.isEmpty()) {
                                    val anililiStill = anililiEps[epNum]?.imageUrl
                                    if (!anililiStill.isNullOrBlank() && anililiStill !in remainingDuplicates) {
                                        altImg = anililiStill
                                    }
                                }
                                if (altImg.isEmpty()) {
                                    val customCover = EpisodeCoverUploader.getCustomEpisodeCover(null, anilistId, epNum)
                                    if (!customCover.isNullOrEmpty() && customCover !in remainingDuplicates) {
                                        altImg = customCover
                                    }
                                }
                                if (altImg.isNotEmpty()) {
                                    metadataMap[epNum] = epMeta.copy(imageUrl = altImg)
                                } else if (epNum > 1) {
                                    metadataMap[epNum] = epMeta.copy(imageUrl = "")
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AniZipClient", "getEpisodeMetadata error: ${e.message}", e)
        }
        if (com.lagradost.cloudstream3.ui.animebox.extractors.PokemonHindiExtractor.isPokemon(anilistId)) {
            val tmdbShowId = if (anilistId == 158871 || anilistId == 171616) 220150 else 60572
            for ((epNum, epMeta) in metadataMap.toMap()) {
                val (seasonNum, relEpNum) = com.lagradost.cloudstream3.ui.animebox.extractors.PokemonHindiExtractor.getSeasonAndRelativeEpisode(anilistId, epNum)
                var img = epMeta.imageUrl
                if (img.isBlank()) {
                    img = getTmdbEpisodeImage(tmdbShowId, seasonNum, relEpNum)
                }
                metadataMap[epNum] = epMeta.copy(
                    seasonNumber = seasonNum,
                    episodeNumber = relEpNum,
                    imageUrl = img
                )
            }
            if (metadataMap.isEmpty()) {
                val count = when (anilistId) {
                    527 -> 276
                    1564 -> 192
                    1565 -> 191
                    9107 -> 84
                    14093 -> 24
                    17115 -> 14
                    17873 -> 20
                    19291 -> 93
                    21356 -> 47
                    97634 -> 146
                    112151, 112153 -> 136
                    158870, 160488 -> 11
                    158871 -> 45
                    171616 -> 25
                    else -> 0
                }
                if (count > 0) {
                    for (epNum in 1..count) {
                        val (sNum, relEp) = com.lagradost.cloudstream3.ui.animebox.extractors.PokemonHindiExtractor.getSeasonAndRelativeEpisode(anilistId, epNum)
                        val still = getTmdbEpisodeImage(tmdbShowId, sNum, relEp)
                        metadataMap[epNum] = EpisodeMeta(
                            title = "Episode $epNum",
                            imageUrl = still,
                            airdate = "",
                            seasonNumber = sNum,
                            episodeNumber = relEp,
                            overview = "",
                            runtime = 24,
                            isFiller = false
                        )
                    }
                }
            }
        }
        return@withContext metadataMap
    }

    suspend fun getAnimeLogoUrl(anilistId: Int, isMovie: Boolean = false, title: String = ""): String = withContext(Dispatchers.IO) {
        if (anilistId <= 0 || AniListClient.isAdultAnimeCached(anilistId)) return@withContext ""
        if (ShinChanEpisodeProvider.isShinChan(anilistId)) {
            return@withContext ShinChanEpisodeProvider.SHINCHAN_LOGO_URL
        }
        val staticLogo = AnimeMovieTmdbMapping.getStaticLogo(anilistId)
        if (!staticLogo.isNullOrBlank()) {
            return@withContext staticLogo
        }

        // 0. Check Supabase dynamic custom logo first!
        val dynamicLogo = AnimeMovieTmdbMapping.getDynamicLogo(anilistId)
        if (!dynamicLogo.isNullOrBlank()) {
            return@withContext dynamicLogo
        }

        val dynamicType = AnimeMovieTmdbMapping.getDynamicTmdbType(anilistId)
        val staticMovie = AnimeMovieTmdbMapping.isStaticMovie(anilistId)
        val isExplicitTv = AnimeMovieTmdbMapping.isStaticTv(anilistId) ||
                (getLongRunningTmdbId(anilistId) != null && !isMovie && !staticMovie) ||
                dynamicType?.equals("tv", ignoreCase = true) == true
        val isExplicitMovie = staticMovie || dynamicType?.equals("movie", ignoreCase = true) == true

        var targetIsMovie = when {
            isExplicitMovie -> true
            isExplicitTv -> false
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
        


        // 1. Try TMDB logo for targetType
        if (tmdbId != null && tmdbId > 0) {
            val logo = queryTmdbLogo(targetType, tmdbId)
            if (logo.isNotEmpty()) return@withContext logo

            // Fallback: ONLY try alternate media type if neither TV nor Movie was explicitly fixed/mapped
            // AND only if TMDB confirms that the alternate media type ID actually represents an anime!
            if (!isExplicitTv && !isExplicitMovie) {
                val altType = if (targetType == "movie") "tv" else "movie"
                if (verifyTmdbIsAnime(tmdbId, altType) != null) {
                    val altLogo = queryTmdbLogo(altType, tmdbId)
                    if (altLogo.isNotEmpty()) {
                        if (altType == "movie") {
                            AnimeMovieTmdbMapping.setDynamicType(anilistId, "movie")
                            AnimeMovieTmdbMapping.setDynamicMapping(anilistId, tmdbId, "movie")
                        }
                        return@withContext altLogo
                    }
                }
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

        // 3. Try Anilili Konoha media-index logo (exact logic investigated from Anilili pommieyewear index.json)
        val anililiLogo = getAnililiLogoUrl(anilistId)
        if (!anililiLogo.isNullOrEmpty()) {
            return@withContext anililiLogo
        }

        // 4. Anilili TMDB title search & franchise logo resolution (e.g. for movies like Made in Abyss: Mezameru Shinpi, Dragon Ball Super: Beerus, etc.)
        val titleSearchLogo = searchTmdbLogoByTitles(anilistId, targetType)
        if (!titleSearchLogo.isNullOrEmpty()) {
            return@withContext titleSearchLogo
        }

        return@withContext ""
    }

    private val anililiLogoCache = android.util.LruCache<Int, String>(100)
    private val anililiEpisodesCache = android.util.LruCache<Int, Map<Int, EpisodeMeta>>(50)

    /**
     * Retrieves anime title clear logo using Anilili Konoha media-index repository logic
     */
    suspend fun getAnililiLogoUrl(anilistId: Int): String? = withContext(Dispatchers.IO) {
        if (anilistId <= 0) return@withContext null
        val cached = anililiLogoCache.get(anilistId)
        if (cached != null) return@withContext cached

        val bucket = anilistId / 1000
        val url = "https://cdn.jsdelivr.net/gh/pommieyewear/media-index@main/data/anime/$bucket/$anilistId/index.json"
        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return@use
                    val json = JSONObject(body)
                    val imagesObj = json.optJSONObject("images")
                    val logo = imagesObj?.optString("logo", "")?.trim() ?: ""
                    if (logo.isNotEmpty() && !logo.equals("null", ignoreCase = true)) {
                        anililiLogoCache.put(anilistId, logo)
                        return@withContext logo
                    }

                    // Anilili franchise inheritance: check series list
                    val seriesArr = json.optJSONArray("series")
                    if (seriesArr != null && seriesArr.length() > 0) {
                        for (i in 0 until seriesArr.length()) {
                            val relatedId = seriesArr.optInt(i, 0)
                            if (relatedId > 0 && relatedId != anilistId) {
                                val cachedRelated = anililiLogoCache.get(relatedId)
                                if (!cachedRelated.isNullOrEmpty()) {
                                    anililiLogoCache.put(anilistId, cachedRelated)
                                    return@withContext cachedRelated
                                }
                                val relatedLogo = getAnimeLogoUrl(relatedId)
                                if (relatedLogo.isNotEmpty()) {
                                    anililiLogoCache.put(anilistId, relatedLogo)
                                    return@withContext relatedLogo
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback to lili.json in media-index
        try {
            val liliUrl = "https://cdn.jsdelivr.net/gh/pommieyewear/media-index@main/data/anime/$bucket/$anilistId/lili.json"
            val req = Request.Builder()
                .url(liliUrl)
                .addHeader("User-Agent", "Mozilla/5.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return@use
                    val json = JSONObject(body)
                    val imagesObj = json.optJSONObject("images")
                    val logo = imagesObj?.optString("logo", "")?.trim() ?: ""
                    if (logo.isNotEmpty() && !logo.equals("null", ignoreCase = true)) {
                        anililiLogoCache.put(anilistId, logo)
                        return@withContext logo
                    }

                    // Anilili franchise inheritance: check series list
                    val seriesArr = json.optJSONArray("series")
                    if (seriesArr != null && seriesArr.length() > 0) {
                        for (i in 0 until seriesArr.length()) {
                            val relatedId = seriesArr.optInt(i, 0)
                            if (relatedId > 0 && relatedId != anilistId) {
                                val cachedRelated = anililiLogoCache.get(relatedId)
                                if (!cachedRelated.isNullOrEmpty()) {
                                    anililiLogoCache.put(anilistId, cachedRelated)
                                    return@withContext cachedRelated
                                }
                                val relatedLogo = getAnimeLogoUrl(relatedId)
                                if (relatedLogo.isNotEmpty()) {
                                    anililiLogoCache.put(anilistId, relatedLogo)
                                    return@withContext relatedLogo
                                }
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        null
    }

    /**
     * Retrieves complete episode list metadata & stills using Anilili Konoha media-index repository logic
     */
    /**
     * Resolves clear logo by searching TMDB using title variations and franchise inheritance
     * (Anilili's exact logic for Made in Abyss: Mezameru Shinpi, Dragon Ball Super, etc.)
     */
    // Helper to query TMDB logos for a given media type.
    // Language priority: English -> Japanese -> Chinese -> any other available language.
    // A lower-priority language is only used when every higher-priority language has no logo at all.
    private fun queryTmdbLogo(type: String, id: Int): String {
        try {
            // Request ALL languages in one call. Filtering to "en,null" server-side hid Japanese/Chinese
            // logos and let language-less entries win before ja/zh were ever considered.
            val allLangUrl = "https://api.themoviedb.org/3/$type/$id/images?api_key=$TMDB_API_KEY"
            val allLangRequest = Request.Builder().url(allLangUrl).build()
            client.newCall(allLangRequest).execute().use { tmdbResponse ->
                if (tmdbResponse.isSuccessful) {
                    val tmdbJsonStr = tmdbResponse.body?.string() ?: return@use
                    val tmdbJson = JSONObject(tmdbJsonStr)
                    val logosArray = tmdbJson.optJSONArray("logos")
                    if (logosArray != null && logosArray.length() > 0) {
                        val priority = listOf("en", "ja", "zh")
                        var bestRank = Int.MAX_VALUE
                        var bestScore = -1.0
                        var bestPath = ""
                        for (i in 0 until logosArray.length()) {
                            val obj = logosArray.getJSONObject(i)
                            val filePath = obj.optString("file_path", "")
                            if (filePath.isEmpty()) continue
                            val lang = if (obj.isNull("iso_639_1")) "" else obj.optString("iso_639_1", "")
                            val idx = priority.indexOf(lang.lowercase())
                            // en=0, ja=1, zh=2, any other language=3, language-less/textless=4
                            val rank = when {
                                idx >= 0 -> idx
                                lang.isNotEmpty() -> priority.size
                                else -> priority.size + 1
                            }
                            val score = obj.optDouble("vote_average", 0.0) * 1000 + obj.optDouble("vote_count", 0.0)
                            if (rank < bestRank || (rank == bestRank && score > bestScore)) {
                                bestRank = rank
                                bestScore = score
                                bestPath = filePath
                            }
                        }
                        if (bestPath.isNotEmpty()) return "https://image.tmdb.org/t/p/original$bestPath"
                    }
                }
            }
        } catch (_: Exception) {}
        return ""
    }

    private val dynamicTvSearchCache = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    fun cleanAnimeTitleForSearch(title: String): String {
        var str = title
        val seasonPatterns = listOf(
            Regex("(?i)\\b\\d+(?:st|nd|rd|th)\\s+season\\b"),
            Regex("(?i)\\bseason\\s*\\d+\\b"),
            Regex("(?i)\\bpart\\s*\\d+\\b"),
            Regex("(?i)\\bcour\\s*\\d+\\b"),
            Regex("(?i)\\bfinal\\s+season\\b"),
            Regex("(?i)\\s+(?:ii|iii|iv|v|vi|vii|viii|ix|x)\\s*$"),
            Regex("(?i)\\b(?:the\\s+movie|movies?|gekijou-?ban|ovas?|oads?|onas?|specials?)\\b")
        )
        for (pattern in seasonPatterns) {
            str = pattern.replace(str, " ")
        }
        return str.replace(Regex("\\s+"), " ").trim()
    }

    suspend fun resolveTmdbTvIdDynamically(anilistId: Int): Int = withContext(Dispatchers.IO) {
        if (anilistId <= 0) return@withContext 0
        dynamicTvSearchCache[anilistId]?.let { return@withContext it }

        val candidateQueries = mutableListOf<String>()
        val baseTitles = getAnimeTitlesFromAnySource(anilistId)
        candidateQueries.addAll(baseTitles)

        // Add Anilili-style cleaned titles and arc/sub-series prefixes (e.g. 'Dragon Ball Super: Beerus' -> 'Dragon Ball Super')
        for (t in baseTitles) {
            val cleaned = cleanAnimeTitleForSearch(t)
            if (cleaned.isNotBlank() && !candidateQueries.contains(cleaned)) {
                candidateQueries.add(cleaned)
            }
            if (t.contains(":") || t.contains("-") || t.contains("–") || t.contains("—")) {
                val prefix = t.split(':', '-', '–', '—').first().trim()
                if (prefix.length >= 3 && !candidateQueries.contains(prefix)) {
                    candidateQueries.add(prefix)
                }
                val cleanedPrefix = cleanAnimeTitleForSearch(prefix)
                if (cleanedPrefix.length >= 3 && !candidateQueries.contains(cleanedPrefix)) {
                    candidateQueries.add(cleanedPrefix)
                }
            }
        }

        var foundId = 0
        for (query in candidateQueries.distinct()) {
            if (foundId > 0) break
            try {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val sUrl = "https://api.themoviedb.org/3/search/tv?api_key=$TMDB_API_KEY&query=$encoded"
                val req = Request.Builder().url(sUrl).build()
                client.newCall(req).execute().use { r ->
                    if (r.isSuccessful) {
                        val b = r.body?.string().orEmpty()
                        val j = JSONObject(b)
                        val res = j.optJSONArray("results") ?: return@use
                        val qClean = query.lowercase().filter { it.isLetterOrDigit() }
                        for (idx in 0 until res.length()) {
                            val cand = res.optJSONObject(idx) ?: continue
                            val candId = cand.optInt("id", 0)
                            if (candId <= 0) continue
                            val name = cand.optString("name", "")
                            val origName = cand.optString("original_name", "")
                            val nameClean = name.lowercase().filter { it.isLetterOrDigit() }
                            val origClean = origName.lowercase().filter { it.isLetterOrDigit() }

                            val isMatch = (qClean.length >= 4 && (nameClean.contains(qClean) || qClean.contains(nameClean))) ||
                                          (origClean.isNotEmpty() && qClean.length >= 4 && (origClean.contains(qClean) || qClean.contains(origClean))) ||
                                          idx == 0
                            if (isMatch) {
                                foundId = candId
                                break
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (foundId > 0) {
            dynamicTvSearchCache[anilistId] = foundId
            AnimeMovieTmdbMapping.setDynamicMapping(anilistId, foundId, "tv")
        }
        foundId
    }

    private suspend fun getAnimeTitlesFromAnySource(anilistId: Int): List<String> {
        if (anilistId <= 0 || AniListClient.isAdultAnimeCached(anilistId)) return emptyList()
        val list = mutableListOf<String>()
        try {
            val aniJson = getAniZipJson(anilistId)
            val titlesObj = aniJson?.optJSONObject("titles")
            if (titlesObj != null) {
                titlesObj.optString("en", "").takeIf { it.isNotBlank() }?.let { list.add(it) }
                titlesObj.optString("rj", "").takeIf { it.isNotBlank() }?.let { list.add(it) }
                titlesObj.optString("en_jp", "").takeIf { it.isNotBlank() }?.let { list.add(it) }
            }
        } catch (_: Exception) {}

        if (list.isEmpty()) {
            try {
                val detailsJsonStr = AniListClient.getAnimeDetails(anilistId)
                if (!detailsJsonStr.isNullOrEmpty()) {
                    val media = JSONObject(detailsJsonStr).optJSONObject("data")?.optJSONObject("Media")
                    val tObj = media?.optJSONObject("title")
                    if (tObj != null) {
                        tObj.optString("english", "").takeIf { it.isNotBlank() }?.let { list.add(it) }
                        tObj.optString("romaji", "").takeIf { it.isNotBlank() }?.let { list.add(it) }
                    }
                }
            } catch (_: Exception) {}
        }
        return list.filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) && !it.contains("stjerner", ignoreCase = true) }.distinct()
    }

    suspend fun searchTmdbLogoByTitles(anilistId: Int, preferredType: String): String? = withContext(Dispatchers.IO) {
        if (anilistId <= 0) return@withContext null
        val cached = anililiLogoCache.get(anilistId)
        if (!cached.isNullOrEmpty()) return@withContext cached

        // Gather titles from AniZip JSON or AniList
        val titleList = getAnimeTitlesFromAnySource(anilistId).toMutableList()

        val candidates = mutableListOf<String>()
        val prefixes = mutableListOf<String>()
        for (t in titleList) {
            candidates.add(t)
            if (t.contains(":")) {
                prefixes.add(t.substringBefore(":").trim())
            }
            if (t.contains(" - ")) {
                prefixes.add(t.substringBefore(" - ").trim())
            }
        }
        val distinctQueries = (candidates + prefixes).filter {
            it.length >= 3 &&
            !it.equals("null", ignoreCase = true) &&
            !it.equals("nul", ignoreCase = true) &&
            !it.contains("stjerner", ignoreCase = true)
        }.distinct()

        for (q in distinctQueries) {
            val encoded = java.net.URLEncoder.encode(q, "UTF-8")
            val typesToTry = if (preferredType == "movie") listOf("movie", "tv") else listOf("tv", "movie")
            for (type in typesToTry) {
                try {
                    val sUrl = "https://api.themoviedb.org/3/search/$type?api_key=$TMDB_API_KEY&query=$encoded"
                    val req = Request.Builder().url(sUrl).build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use
                        val body = resp.body?.string() ?: return@use
                        val j = JSONObject(body)
                        val results = j.optJSONArray("results") ?: return@use
                        for (i in 0 until minOf(results.length(), 2)) {
                            val item = results.optJSONObject(i) ?: continue
                            val resName = item.optString("name", item.optString("title", "")).trim()
                            if (resName.contains("stjerner", ignoreCase = true) || resName.equals("null", ignoreCase = true)) continue
                            val normQ = q.lowercase().filter { it.isLetterOrDigit() }
                            val normRes = resName.lowercase().filter { it.isLetterOrDigit() }
                            if (!normRes.contains(normQ) && !normQ.contains(normRes)) continue
                            val tmdbId = item.optInt("id", 0)
                            if (tmdbId > 0) {
                                val logo = queryTmdbLogo(type, tmdbId)
                                if (logo.isNotEmpty() && !logo.contains("stjerner", ignoreCase = true) && !logo.contains("null", ignoreCase = true)) {
                                    anililiLogoCache.put(anilistId, logo)
                                    return@withContext logo
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        null
    }

    suspend fun getAnililiEpisodes(anilistId: Int): Map<Int, EpisodeMeta> = withContext(Dispatchers.IO) {
        if (anilistId <= 0) return@withContext emptyMap()
        val cached = anililiEpisodesCache.get(anilistId)
        if (cached != null) return@withContext cached

        val bucket = anilistId / 1000
        val url = "https://cdn.jsdelivr.net/gh/pommieyewear/media-index@main/data/anime/$bucket/$anilistId/episodes.json"
        val resultMap = mutableMapOf<Int, EpisodeMeta>()
        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .addHeader("Accept", "application/json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return@use
                    val arr = org.json.JSONArray(body)
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val num = obj.optDouble("number", (i + 1).toDouble()).toInt()
                        val still = obj.optString("still", "").trim()
                        val title = obj.optString("title", "Episode $num").trim()
                        val overview = obj.optString("overview", "").trim()
                        val airDate = obj.optString("air_date", "").trim()
                        val runtime = obj.optInt("runtime", 24)
                        if (num > 0) {
                            resultMap[num] = EpisodeMeta(
                                title = title.ifEmpty { "Episode $num" },
                                imageUrl = if (still.isNotEmpty() && !still.equals("null", ignoreCase = true)) still else "",
                                airdate = airDate,
                                seasonNumber = 1,
                                episodeNumber = num,
                                overview = overview,
                                runtime = runtime,
                                isFiller = false
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        if (resultMap.isNotEmpty()) {
            anililiEpisodesCache.put(anilistId, resultMap)
        }
        resultMap
    }

    private val animeCoverCache = android.util.LruCache<Int, String>(100)

    suspend fun getAnimeCover(anilistId: Int): String = withContext(Dispatchers.IO) {
        if (anilistId == 21870) return@withContext "https://image.tmdb.org/t/p/w500/6Csk5iIk4q9hfPPrQLl1dox9EVr.jpg"
        if (anilistId == 1403) return@withContext "https://s4.anilist.co/file/anilistcdn/media/anime/cover/medium/1403.jpg"
        if (anilistId == 196750) return@withContext "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx196750-qwkwqdNMXIwD.png"
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
                val detailsUrl = "https://api.themoviedb.org/3/$targetType/$tmdbId?api_key=$TMDB_API_KEY"
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

            val tmdbUrl = "https://api.themoviedb.org/3/$targetType/$tmdbId/images?api_key=$TMDB_API_KEY"
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
            val tmdbUrl = "https://api.themoviedb.org/3/$mediaType/$tmdbId?api_key=$TMDB_API_KEY"
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
        val staticTvTmdb = AnimeMovieTmdbMapping.getTvTmdbId(anilistId)
        if (staticTvTmdb != null && staticTvTmdb > 0) {
            val verified = verifyTmdbIsAnime(staticTvTmdb, "tv")
            if (!verified.isNullOrEmpty()) {
                return@withContext verified
            }
        }
        val longRunningTmdb = getLongRunningTmdbId(anilistId)
        if (longRunningTmdb != null && longRunningTmdb > 0) {
            val verified = verifyTmdbIsAnime(longRunningTmdb, "tv")
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

    suspend fun getBestBackdropUrl(anilistId: Int, anilistBanner: String = "", isMovie: Boolean = false, title: String = ""): String = withContext(Dispatchers.IO) {
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
        val mainUrl = "https://api.themoviedb.org/3/tv/$tmdbId?api_key=$TMDB_API_KEY"
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
                                    val seasonUrl = "https://api.themoviedb.org/3/tv/$tmdbId/season/$seasonNum?api_key=$TMDB_API_KEY"
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
        if (com.lagradost.cloudstream3.ui.animebox.extractors.PokemonHindiExtractor.isPokemon(anilistId)) return null
        return AnimeMovieTmdbMapping.getTvTmdbId(anilistId) ?: when (anilistId) {
        21 -> 37854    // One Piece
        235 -> 30983   // Detective Conan / Case Closed
        20 -> 46260    // Naruto
        1735 -> 31910  // Naruto Shippuden
        269 -> 30984   // Bleach
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

    fun isLongRunningSeries(anilistId: Int): Boolean = getLongRunningTmdbId(anilistId) != null

    data class TmdbSearchResult(val tmdbId: Int)

    suspend fun searchTmdbAnime(title: String, isMovie: Boolean = false): TmdbSearchResult? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        try {
            val type = if (isMovie) "movie" else "tv"
            val enc = java.net.URLEncoder.encode(title, "UTF-8")
            val url = "https://api.themoviedb.org/3/search/$type?api_key=$TMDB_API_KEY&query=$enc"
            val req = Request.Builder().url(url).build()
            val res = client.newCall(req).execute()
            val body = res.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return@withContext null
            if (results.length() > 0) {
                val id = results.getJSONObject(0).optInt("id", 0)
                if (id > 0) TmdbSearchResult(id) else null
            } else null
        } catch (_: Exception) { null }
    }

    suspend fun getTmdbOverview(tmdbId: Int, isMovie: Boolean = false): String = withContext(Dispatchers.IO) {
        if (tmdbId <= 0) return@withContext ""
        try {
            val type = if (isMovie) "movie" else "tv"
            val url = "https://api.themoviedb.org/3/$type/$tmdbId?api_key=$TMDB_API_KEY"
            val req = Request.Builder().url(url).build()
            val res = client.newCall(req).execute()
            val body = res.body?.string() ?: return@withContext ""
            val json = JSONObject(body)
            json.optString("overview", "")
        } catch (_: Exception) { "" }
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
