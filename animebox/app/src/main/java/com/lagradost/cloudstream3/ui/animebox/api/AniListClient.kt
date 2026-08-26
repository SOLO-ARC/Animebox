package com.lagradost.cloudstream3.ui.animebox.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.lagradost.cloudstream3.CloudStreamApp
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

/**
 * Helper client to fetch data directly from AniList GraphQL API
 */
object AniListClient {

    private fun buildHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
        return AnimeBoxDnsHelper.applyDns(builder, CloudStreamApp.context).build()
    }

    private var client = buildHttpClient()

    fun refreshClient() {
        client = buildHttpClient()
        queryCache.evictAll()
    }

    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private const val ANILIST_URL = "https://graphql.anilist.co"

    // In-memory cache for ultra-fast (0ms) repeat page openings
    private val detailsCache = android.util.LruCache<Int, String>(100)
    private val queryCache = android.util.LruCache<String, String>(50)

    var lastErrorMessage: String? = null
        private set

    /**
     * Helper to perform raw GraphQL Query post request with retry handling, headers, and content filtering
     */
    private suspend fun query(graphqlQuery: String, variables: JSONObject = JSONObject()): String? = withContext(Dispatchers.IO) {
        val cacheKey = "${graphqlQuery.hashCode()}_${variables}"
        queryCache.get(cacheKey)?.let { return@withContext it }

        val bodyJson = JSONObject().apply {
            put("query", graphqlQuery)
            put("variables", variables)
        }
        val requestBody = bodyJson.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(ANILIST_URL)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .post(requestBody)
            .build()

        var retries = 0
        var result: String? = null
        while (retries < 2 && result == null) {
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    if (response.isSuccessful) {
                        if (!body.isNullOrEmpty() && !body.contains("\"errors\":[{")) {
                            result = body
                            lastErrorMessage = null
                        } else if (!body.isNullOrEmpty() && body.contains("\"message\":")) {
                            try {
                                val errObj = JSONObject(body).optJSONArray("errors")?.optJSONObject(0)
                                lastErrorMessage = errObj?.optString("message", "AniList API Error")
                            } catch (_: Exception) {
                                lastErrorMessage = "AniList API Error"
                            }
                        }
                    } else if (response.code == 403 || response.code == 503) {
                        lastErrorMessage = "The AniList API has been temporarily disabled due to severe stability issues (HTTP ${response.code})."
                    } else if (response.code == 429) {
                        lastErrorMessage = "AniList rate limit reached. Please wait a moment."
                        kotlinx.coroutines.delay(500L)
                    } else {
                        lastErrorMessage = "AniList server error: HTTP ${response.code}"
                    }
                }
            } catch (e: Exception) {
                lastErrorMessage = "Network connection failed (${e.localizedMessage ?: "timeout"}). Try changing DNS in settings."
            }
            retries++
        }

        if (result != null) {
            val filtered = filterAniListJson(result!!)
            if (filtered.isNotEmpty()) {
                queryCache.put(cacheKey, filtered)
                return@withContext filtered
            }
        }
        null
    }

    /**
     * Central Filter Logic for AniList JSON responses
     */
    fun filterAniListJson(jsonString: String): String {
        return try {
            val root = JSONObject(jsonString)
            if (!root.has("data") || root.isNull("data")) return jsonString
            val data = root.getJSONObject("data")

            if (data.has("Page") && !data.isNull("Page")) {
                val page = data.getJSONObject("Page")
                if (page.has("media") && !page.isNull("media")) {
                    val mediaArray = page.getJSONArray("media")
                    val filteredArray = org.json.JSONArray()
                    for (i in 0 until mediaArray.length()) {
                        val media = mediaArray.getJSONObject(i)
                        if (!isBlockedMedia(media)) {
                            filteredArray.put(media)
                        }
                    }
                    page.put("media", filteredArray)
                }
            }

            if (data.has("Media") && !data.isNull("Media")) {
                val media = data.getJSONObject("Media")
                if (isBlockedMedia(media)) {
                    return JSONObject().apply { put("data", JSONObject()) }.toString()
                }
                if (media.has("recommendations") && !media.isNull("recommendations")) {
                    val recs = media.getJSONObject("recommendations")
                    if (recs.has("nodes") && !recs.isNull("nodes")) {
                        val nodes = recs.getJSONArray("nodes")
                        val filteredNodes = org.json.JSONArray()
                        for (i in 0 until nodes.length()) {
                            val node = nodes.getJSONObject(i)
                            if (node.has("mediaRecommendation") && !node.isNull("mediaRecommendation")) {
                                val recMedia = node.getJSONObject("mediaRecommendation")
                                if (!isBlockedMedia(recMedia)) {
                                    filteredNodes.put(node)
                                }
                            }
                        }
                        recs.put("nodes", filteredNodes)
                    }
                }
                if (media.has("relations") && !media.isNull("relations")) {
                    val rels = media.getJSONObject("relations")
                    if (rels.has("edges") && !rels.isNull("edges")) {
                        val edges = rels.getJSONArray("edges")
                        val filteredEdges = org.json.JSONArray()
                        for (i in 0 until edges.length()) {
                            val edge = edges.getJSONObject(i)
                            if (edge.has("node") && !edge.isNull("node")) {
                                val relMedia = edge.getJSONObject("node")
                                if (!isBlockedMedia(relMedia)) {
                                    filteredEdges.put(edge)
                                }
                            }
                        }
                        rels.put("edges", filteredEdges)
                    }
                }
            }

            root.toString()
        } catch (e: Exception) {
            jsonString
        }
    }

    fun isBlockedMedia(media: JSONObject): Boolean {
        // 0. Check Maturity Rating (Default: With restrictions -> Block 18+ / isAdult == true / Hentai)
        try {
            val context = CloudStreamApp.context
            if (context != null) {
                val rating = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.getMaturityRating(context)
                val isAdult = media.optBoolean("isAdult", false)
                val genres = media.optJSONArray("genres")
                var isAdultGenre = false
                if (genres != null) {
                    for (i in 0 until genres.length()) {
                        val g = genres.optString(i, "")
                        if (g.equals("Hentai", ignoreCase = true) || g.equals("Ecchi 18+", ignoreCase = true)) {
                            isAdultGenre = true
                            break
                        }
                    }
                }
                if (rating != "No restrictions" && (isAdult || isAdultGenre)) {
                    return true
                }
            }
        } catch (_: Exception) {}

        val averageScore = if (media.has("averageScore") && !media.isNull("averageScore")) {
            media.optInt("averageScore", 0)
        } else if (media.has("meanScore") && !media.isNull("meanScore")) {
            media.optInt("meanScore", 0)
        } else 0

        val mediaStatus = if (media.has("status") && !media.isNull("status")) media.optString("status") else ""

        // 1. Rating < 40% filter
        if (averageScore in 1..39) {
            return true
        }

        // 2. Zero % Rating / Unrated filter (allow upcoming)
        if (averageScore == 0) {
            if (mediaStatus != "NOT_YET_RELEASED") {
                return true
            }
        }

        return false
    }

    /**
     * Common GraphQL media selection query fragment
     */
    private const val MEDIA_FIELDS = """
        id
        idMal
        trailer {
          id
          site
          thumbnail
        }
        title {
          english
          romaji
        }
        description
        coverImage {
          large
          extraLarge
        }
        bannerImage
        genres
        tags {
          name
        }
        characters(perPage: 5) {
          edges {
            node {
              id
            }
          }
        }
        format
        averageScore
        episodes
        status
        isAdult
        startDate {
          year
          month
          day
        }
    """

    /**
     * Fetch trending/popular anime for the Homepage Spotlight & Carousel lists
     */
    suspend fun getTrendingAnime(): String? {
        val graphqlQuery = """
            query {
              Page(page: 1, perPage: 15) {
                media(type: ANIME, sort: TRENDING_DESC) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()
        return query(graphqlQuery)
    }

    /**
     * Fetch popular anime for search page recommendations
     */
    suspend fun getPopularAnime(): String? {
        val graphqlQuery = """
            query {
              Page(page: 1, perPage: 30) {
                media(type: ANIME, sort: POPULARITY_DESC) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()
        return query(graphqlQuery)
    }

    /**
     * Search anime by title keyword
     */
    suspend fun searchAnime(searchQuery: String): String? {
        val variables = JSONObject().apply {
            put("search", searchQuery)
        }
        val graphqlQuery = """
            query (${'$'}search: String) {
              Page(page: 1, perPage: 25) {
                media(type: ANIME, search: ${'$'}search) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()
        return query(graphqlQuery, variables)
    }

    /**
     * Fetch full anime details (description, poster, banner, total episodes, relations)
     */
    suspend fun getAnimeDetails(anilistId: Int): String? {
        val cached = detailsCache.get(anilistId)
        if (cached != null) return cached

        val variables = JSONObject().apply {
            put("id", anilistId)
        }
        val graphqlQuery = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                id
                idMal
                trailer {
                  id
                  site
                  thumbnail
                }
                title {
                  english
                  romaji
                }
                description
                coverImage {
                  extraLarge
                  large
                }
                bannerImage
                genres
                tags {
                  name
                }
                characters(sort: [ROLE, RELEVANCE], perPage: 12) {
                  edges {
                    role
                    node {
                      id
                      name {
                        full
                      }
                      image {
                        large
                      }
                    }
                    voiceActors(language: JAPANESE) {
                      name {
                        full
                      }
                      image {
                        large
                      }
                    }
                  }
                }
                format
                episodes
                averageScore
                status
                isAdult
                startDate {
                  year
                  month
                  day
                }
                nextAiringEpisode {
                  airingAt
                  timeUntilAiring
                  episode
                }
                relations {
                  edges {
                    relationType
                    node {
                      $MEDIA_FIELDS
                    }
                  }
                }
                recommendations(sort: RATING_DESC, perPage: 15) {
                  nodes {
                    mediaRecommendation {
                      $MEDIA_FIELDS
                    }
                  }
                }
              }
            }
        """.trimIndent()
        var res = query(graphqlQuery, variables)
        if (res == null) {
            val simpleQuery = """
                query (${'$'}id: Int) {
                  Media(id: ${'$'}id, type: ANIME) {
                    $MEDIA_FIELDS
                  }
                }
            """.trimIndent()
            res = query(simpleQuery, variables)
        }
        if (res != null) {
            detailsCache.put(anilistId, res)
        }
        return res
    }

    /**
     * Fetch popular anime filtered by genre
     */
    suspend fun getAnimeByGenre(genre: String, page: Int = 1): String? {
        val variables = JSONObject().apply {
            put("genre", genre)
            put("page", page)
        }
        val graphqlQuery = """
            query (${'$'}genre: String, ${'$'}page: Int) {
              Page(page: ${'$'}page, perPage: 50) {
                pageInfo {
                  hasNextPage
                }
                media(type: ANIME, genre: ${'$'}genre, sort: POPULARITY_DESC) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()
        return query(graphqlQuery, variables)
    }

    /**
     * Batch-fetch anime by a list of AniList IDs (for fixed spotlight items)
     */
    suspend fun getAnimesByIds(ids: List<Int>): String? {
        val idsStr = ids.joinToString(",")
        val graphqlQuery = """
            query {
              Page(page: 1, perPage: ${ids.size}) {
                media(type: ANIME, id_in: [$idsStr]) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()
        return query(graphqlQuery)
    }

    /**
     * Recently released/added anime (sorted by newest ID)
     */
    suspend fun getRecentlyAdded(): String? {
        val graphqlQuery = """
            query {
              Page(page: 1, perPage: 25) {
                media(type: ANIME, sort: START_DATE_DESC, status_in: [RELEASING, FINISHED], format_in: [TV, MOVIE]) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()
        return query(graphqlQuery)
    }

    /**
     * Popular anime airing this season (dynamically detects current season/year)
     */
    suspend fun getPopularThisSeason(): String? {
        val cal = java.util.Calendar.getInstance()
        val month = cal.get(java.util.Calendar.MONTH) + 1
        val year = cal.get(java.util.Calendar.YEAR)
        val season = when (month) {
            1, 2, 3 -> "WINTER"
            4, 5, 6 -> "SPRING"
            7, 8, 9 -> "SUMMER"
            else -> "FALL"
        }
        val graphqlQuery = """
            query {
              Page(page: 1, perPage: 15) {
                media(type: ANIME, season: $season, seasonYear: $year, sort: POPULARITY_DESC) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()
        return query(graphqlQuery)
    }

    /**
     * Popular anime movies
     */
    suspend fun getPopularMovies(): String? {
        val graphqlQuery = """
            query {
              Page(page: 1, perPage: 15) {
                media(type: ANIME, format: MOVIE, sort: POPULARITY_DESC) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()
        return query(graphqlQuery)
    }

    /**
     * Anime not yet released (Coming Soon)
     */
    suspend fun getComingSoon(): String? {
        val graphqlQuery = """
            query {
              Page(page: 1, perPage: 15) {
                media(type: ANIME, status: NOT_YET_RELEASED, sort: POPULARITY_DESC) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()
        return query(graphqlQuery)
    }

    /**
     * Fetch random anime released in or after 2000
     */
    suspend fun getRandomAnimeAfter2000(page: Int = 1): String? {
        val variables = JSONObject().apply {
            put("page", page)
        }
        val graphqlQuery = """
            query (${'$'}page: Int) {
              Page(page: ${'$'}page, perPage: 50) {
                media(type: ANIME, format_in: [TV, MOVIE], startDate_greater: 19991231) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()
        return query(graphqlQuery, variables)
    }
}
