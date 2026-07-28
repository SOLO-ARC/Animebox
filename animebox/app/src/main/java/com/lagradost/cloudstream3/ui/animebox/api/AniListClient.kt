package com.lagradost.cloudstream3.ui.animebox.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Helper client to fetch data directly from AniList GraphQL API
 */
object AniListClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()
    private const val ANILIST_URL = "https://graphql.anilist.co"

    /**
     * Helper to perform raw GraphQL Query post request with retry handling, headers, and content filtering
     */
    private suspend fun query(graphqlQuery: String, variables: JSONObject = JSONObject()): String? = withContext(Dispatchers.IO) {
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
        while (retries < 3 && result == null) {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrEmpty() && !body.contains("\"errors\":[{")) {
                            result = body
                        }
                    } else if (response.code == 429) {
                        kotlinx.coroutines.delay(1000L)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            retries++
            if (result == null && retries < 3) {
                kotlinx.coroutines.delay(500L * retries)
            }
        }
        result?.let { filterAniListJson(it) }
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
                    // Return empty data if single requested media is blocked
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

    /**
     * Checks whether a media item should be blocked based on user filtering criteria.
     * Rules:
     * 1. Rating < 40% (averageScore in 1..39) -> Block.
     * 2. Zero % Rating / Unrated (averageScore == 0) -> Block unless status is NOT_YET_RELEASED.
     * 3. Character Count Rule: If characters < 3 and rating <= 60% -> Block.
     * 4. 3D Anime / Series Rule:
     *    - For 3D non-movie shows: if episodes == 1 or 3 or > 6, require rating >= 70% (block if < 70%).
     *      Otherwise (e.g. 2, 4, 5, 6 eps), require rating >= 60% (block if < 60%).
     *    - For 3D movies: require rating >= 60% (block if < 60%).
     */
    fun isBlockedMedia(media: JSONObject): Boolean {
        val averageScore = if (media.has("averageScore") && !media.isNull("averageScore")) {
            media.optInt("averageScore", 0)
        } else if (media.has("meanScore") && !media.isNull("meanScore")) {
            media.optInt("meanScore", 0)
        } else 0

        val mediaStatus = if (media.has("status") && !media.isNull("status")) media.optString("status") else ""
        val format = if (media.has("format") && !media.isNull("format")) media.optString("format", "").uppercase() else ""
        val episodes = if (media.has("episodes") && !media.isNull("episodes")) media.optInt("episodes", 0) else 0

        // Extract character count if present
        var charCount = -1
        if (media.has("characters") && !media.isNull("characters")) {
            val charObj = media.optJSONObject("characters")
            if (charObj != null && charObj.has("edges") && !charObj.isNull("edges")) {
                val edgesArr = charObj.optJSONArray("edges")
                if (edgesArr != null) {
                    charCount = edgesArr.length()
                }
            }
        }

        // 1. Rating < 40% filter
        if (averageScore in 1..39) {
            return true
        }

        // 2. Zero % Rating / Unrated (averageScore == 0) filter:
        // Block unless status is NOT_YET_RELEASED (upcoming release)
        if (averageScore == 0) {
            if (mediaStatus != "NOT_YET_RELEASED") {
                return true
            }
        }

        // 3. Character Count Rule: If characters < 3 and rating <= 60% -> Block
        if (charCount in 0..2 && averageScore <= 60) {
            return true
        }

        // 4. 3D Anime / Series Filter
        if (is3DAnimation(media)) {
            val isMovie = format == "MOVIE"
            if (!isMovie) {
                // TV / Series / Short / OVA / ONA / Special 3D show
                if (episodes == 1 || episodes == 3 || episodes > 6) {
                    if (averageScore < 70) {
                        return true
                    }
                } else {
                    if (averageScore < 60) {
                        return true
                    }
                }
            } else {
                // Movie 3D show
                if (averageScore < 60) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Logic to identify 3D Animation / CGI show using AniList tags, genres, titles, and description.
     */
    fun is3DAnimation(media: JSONObject): Boolean {
        // Check tags array
        if (media.has("tags") && !media.isNull("tags")) {
            val tagsArr = media.optJSONArray("tags")
            if (tagsArr != null) {
                for (i in 0 until tagsArr.length()) {
                    val tagObj = tagsArr.optJSONObject(i)
                    val tagName = tagObj?.optString("name") ?: tagsArr.optString(i)
                    if (is3DKeyword(tagName)) return true
                }
            }
        }

        // Check genres array
        if (media.has("genres") && !media.isNull("genres")) {
            val genresArr = media.optJSONArray("genres")
            if (genresArr != null) {
                for (i in 0 until genresArr.length()) {
                    val genre = genresArr.optString(i)
                    if (is3DKeyword(genre)) return true
                }
            }
        }

        // Check title
        var titleText = ""
        if (media.has("title") && !media.isNull("title")) {
            val titleObj = media.optJSONObject("title")
            if (titleObj != null) {
                titleText = "${titleObj.optString("english")} ${titleObj.optString("romaji")}"
            } else {
                titleText = media.optString("title")
            }
        }

        // Check description
        val descriptionText = if (media.has("description") && !media.isNull("description")) {
            media.optString("description")
        } else ""

        val combinedText = "$titleText $descriptionText".lowercase()

        val threeDPatterns = listOf(
            "3d cg", "3d-cg", "3dcg", "full cgi", "3d animation", "3d anime",
            "3d animated", "3d graphics", "3d model", "3d cgi", "cgi animation",
            "stop-motion", "stop motion", "claymation", "puppetry", "3d short", "3d show", "gallery tour"
        )

        for (pattern in threeDPatterns) {
            if (combinedText.contains(pattern)) return true
        }

        if (Regex("""\b3d\b""").containsMatchIn(combinedText) &&
            (combinedText.contains("animation") || combinedText.contains("anime") || combinedText.contains("cg") || combinedText.contains("short"))
        ) {
            return true
        }

        return false
    }

    private fun is3DKeyword(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        val t = text.lowercase().trim()
        return t == "3d cg" || t == "3d-cg" || t == "3dcg" || t == "cgi" ||
               t == "full cgi" || t == "3d" || t == "3d anime" || t == "3d animation" ||
               t == "stop motion" || t == "puppetry" || t == "claymation" || t == "3d shorts"
    }

    /**
     * Common GraphQL media selection query fragment
     */
    private const val MEDIA_FIELDS = """
        id
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
        val variables = JSONObject().apply {
            put("id", anilistId)
        }
        val graphqlQuery = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                id
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
                trailer {
                  id
                  site
                }
                relations {
                  edges {
                    relationType
                    node {
                      $MEDIA_FIELDS
                    }
                  }
                }
                recommendations(sort: [RATING_DESC, ID], perPage: 12) {
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
