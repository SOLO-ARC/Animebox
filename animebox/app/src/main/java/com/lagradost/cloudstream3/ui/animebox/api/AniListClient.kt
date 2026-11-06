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
            .connectTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(6, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
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

    private val adultAnimeIds = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()

    init {
        adultAnimeIds.add(4731) // Hiiro ni Koku (The Time Colored Scarlet)
    }

    fun registerAdultAnimeId(id: Int) {
        if (id > 0) adultAnimeIds.add(id)
    }

    var lastErrorMessage: String? = null
        private set

    /**
     * Helper to perform raw GraphQL Query post request with retry handling, headers, and content filtering
     */
    private suspend fun query(
        graphqlQuery: String,
        variables: JSONObject = JSONObject(),
        applyMediaFilters: Boolean = true
    ): String? = withContext(Dispatchers.IO) {
        val adultEnabled = CloudStreamApp.context?.let { com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isAdultContentEnabled(it) } ?: false
        val cacheKey = "${graphqlQuery.hashCode()}_${variables}_${applyMediaFilters}_${adultEnabled}"
        queryCache.get(cacheKey)?.let { return@withContext it }

        val bodyJson = JSONObject().apply {
            put("query", graphqlQuery)
            put("variables", variables)
        }
        val requestBody = bodyJson.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(ANILIST_URL)
            .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .addHeader("Origin", "https://anilist.co")
            .addHeader("Referer", "https://anilist.co/")
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
                        lastErrorMessage = if (response.code == 403) {
                            "AniList denied the request (HTTP 403). Check API availability and network access."
                        } else {
                            "AniList is temporarily unavailable (HTTP 503). Try again later."
                        }
                        retries = 2
                    } else if (response.code == 429) {
                        lastErrorMessage = "AniList rate limit reached. Please wait a moment."
                        kotlinx.coroutines.delay(500L)
                    } else if (response.code == 400) {
                        val detail = runCatching {
                            JSONObject(body.orEmpty()).optJSONArray("errors")?.optJSONObject(0)?.optString("message")
                                ?.takeIf { it.isNotBlank() }
                        }.getOrNull()
                        lastErrorMessage = detail?.let { "AniList rejected the query: $it" }
                            ?: "AniList rejected the request (HTTP 400)."
                        retries = 2
                    } else {
                        lastErrorMessage = "AniList server error: HTTP ${response.code}"
                        retries = 2
                    }
                }
            } catch (e: Exception) {
                lastErrorMessage = "Network connection failed (${e.localizedMessage ?: "timeout"}). Try changing DNS in settings."
            }
            retries++
        }

        if (result != null) {
            val filtered = if (applyMediaFilters) filterAniListJson(result!!) else result!!
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

            if (data.has("Studio") && !data.isNull("Studio")) {
                val studio = data.getJSONObject("Studio")
                val media = studio.optJSONObject("media")
                if (media != null && media.has("nodes") && !media.isNull("nodes")) {
                    val nodesArray = media.getJSONArray("nodes")
                    val filteredArray = org.json.JSONArray()
                    val seenMediaIds = mutableSetOf<Int>()
                    for (i in 0 until nodesArray.length()) {
                        val m = nodesArray.getJSONObject(i)
                        val mId = m.optInt("id", -1)
                        if (mId != -1 && !seenMediaIds.add(mId)) continue
                        if (!isBlockedMedia(m)) {
                            filteredArray.put(m)
                        }
                    }
                    media.put("nodes", filteredArray)
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

    fun isBlockedMedia(media: JSONObject, context: android.content.Context? = null): Boolean {
        // 0. Check 18+ Adult Content Setting (Default: Off -> Block 18+ / isAdult == true / Hentai)
        var isAdultAnime = false
        try {
            val ctx = context ?: CloudStreamApp.context
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
            isAdultAnime = isAdult || isAdultGenre
            if (isAdultAnime) {
                val mediaId = media.optInt("id", 0)
                if (mediaId > 0) adultAnimeIds.add(mediaId)
            }

            if (ctx != null) {
                val adultEnabled = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isAdultContentEnabled(ctx)
                if (!adultEnabled && isAdultAnime) {
                    return true
                }
                if (adultEnabled && isAdultAnime) {
                    // When adult content is enabled, never block adult anime due to rating or scores
                    return false
                }
            } else if (isAdultAnime) {
                val adultFlowEnabled = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.adultContentEnabledFlow.value
                return !adultFlowEnabled
            }
        } catch (_: Exception) {}

        if (isAdultAnime) {
            return false
        }

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
          native
        }
        synonyms
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
        popularity
        favourites
        episodes
        duration
        status
        isAdult
        startDate {
            year
            month
            day
        }
        nextAiringEpisode {
            episode
        }
    """

    private const val STUDIO_MEDIA_FIELDS = """
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
          native
        }
        synonyms
        description
        coverImage {
          large
          extraLarge
        }
        bannerImage
        genres
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

    private val SEARCH_ALIAS_MAP = mapOf(
        "rezero" to "Re:Zero",
        "re zero" to "Re:Zero",
        "danmachi" to "Is It Wrong to Try to Pick Up Girls in a Dungeon?",
        "konosuba" to "KonoSuba",
        "oregairu" to "My Teen Romantic Comedy SNAFU",
        "tensura" to "That Time I Got Reincarnated as a Slime",
        "fmab" to "Fullmetal Alchemist: Brotherhood",
        "fma" to "Fullmetal Alchemist",
        "sao" to "Sword Art Online",
        "aot" to "Attack on Titan",
        "mha" to "My Hero Academia",
        "jjk" to "Jujutsu Kaisen",
        "csm" to "Chainsaw Man",
        "ds" to "Demon Slayer",
        "op" to "One Piece",
        "hxh" to "Hunter x Hunter",
        "steinsgate" to "Steins;Gate",
        "steins gate" to "Steins;Gate",
        "fatestaynight" to "Fate/stay night",
        "fate stay night" to "Fate/stay night",
        "saikik" to "The Disastrous Life of Saiki K.",
        "saiki k" to "The Disastrous Life of Saiki K."
    )

    private suspend fun executeSearchQuery(queryStr: String): String? {
        val variables = JSONObject().apply {
            put("search", queryStr)
        }
        val appContext = CloudStreamApp.context
        val adultEnabled = if (appContext != null) com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isAdultContentEnabled(appContext) else false
        val adultArg = if (!adultEnabled) ", isAdult: false" else ""
        val graphqlQuery = """
            query (${'$'}search: String) {
              Page(page: 1, perPage: 25) {
                media(type: ANIME, search: ${'$'}search$adultArg) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()
        return query(graphqlQuery, variables)
    }

    /**
     * Search anime by title keyword with intelligent alias normalization
     */
    suspend fun searchAnime(searchQuery: String): String? {
        val trimmed = searchQuery.trim()
        if (trimmed.isEmpty()) return null

        val cleanLower = trimmed.lowercase()
        var normalizedQuery: String? = SEARCH_ALIAS_MAP[cleanLower]
        if (normalizedQuery == null) {
            for ((alias, replacement) in SEARCH_ALIAS_MAP) {
                if (cleanLower.contains(alias)) {
                    normalizedQuery = cleanLower.replace(alias, replacement)
                    break
                }
            }
        }

        val primaryQuery = normalizedQuery ?: trimmed
        val primaryRes = executeSearchQuery(primaryQuery)

        if (normalizedQuery != null && primaryRes != null) {
            return primaryRes
        }

        if (primaryRes != null && !primaryRes.contains("\"media\":[]")) {
            return primaryRes
        }

        // Fallback: if search starts with 're' and has no punctuation/space (e.g. 'rezero')
        if (cleanLower.startsWith("re") && cleanLower.length > 2 && !cleanLower.contains(" ")) {
            val candidate = "re: " + cleanLower.substring(2)
            val fallbackRes = executeSearchQuery(candidate)
            if (fallbackRes != null && !fallbackRes.contains("\"media\":[]")) {
                return fallbackRes
            }
        }

        return primaryRes
    }

    /**
     * Fetch popular anime by animation studio name (via AniList GraphQL Studio relation / search)
     */
    suspend fun getAnimeByStudio(studioName: String, page: Int = 1): String? {
        val resolvedStudio = com.lagradost.cloudstream3.ui.animebox.api.AnimeStudioRepository.getQueryForStudio(studioName)
        val variables = JSONObject().apply {
            put("studioName", resolvedStudio)
            put("page", page)
        }
        val graphqlQuery = """
            query (${'$'}studioName: String, ${'$'}page: Int) {
              Studio(search: ${'$'}studioName) {
                id
                name
                media(page: ${'$'}page, perPage: 30, sort: POPULARITY_DESC) {
                  pageInfo {
                    hasNextPage
                  }
                  nodes {
                    $STUDIO_MEDIA_FIELDS
                  }
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
                  native
                }
                synonyms
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
                meanScore
                popularity
                favourites
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

    /** Fetch relation graphs for a group of media IDs in one GraphQL request. */
    suspend fun getAnimeDetailsBatch(anilistIds: List<Int>): List<JSONObject> = withContext(Dispatchers.IO) {
        val ids = anilistIds.filter { it > 0 }.distinct().take(50)
        if (ids.isEmpty()) return@withContext emptyList()
        val query = """
            query (${"$"}ids: [Int]) {
              Page(perPage: 50) {
                media(id_in: ${"$"}ids, type: ANIME) {
                  id
                  idMal
                  title { english romaji }
                  description
                  coverImage { large extraLarge }
                  bannerImage
                  format
                  episodes
                  status
                  isAdult
                  averageScore
                  startDate { year month day }
                  relations {
                    edges {
                      relationType
                      node {
                        id
                        idMal
                        title { english romaji }
                        description
                        coverImage { large extraLarge }
                        bannerImage
                        format
                        episodes
                        status
                        isAdult
                        averageScore
                        startDate { year month day }
                      }
                    }
                  }
                }
              }
            }
        """.trimIndent()
        val variables = JSONObject().put("ids", org.json.JSONArray(ids))
        val response = query(query, variables) ?: return@withContext emptyList()
        try {
            val media = JSONObject(response).optJSONObject("data")
                ?.optJSONObject("Page")?.optJSONArray("media") ?: return@withContext emptyList()
            (0 until media.length()).mapNotNull { media.optJSONObject(it) }
        } catch (_: Exception) { emptyList() }
    }

    suspend fun getAnimeReviews(anilistId: Int): String? {
        if (anilistId <= 0) return null
        val variables = JSONObject().put("id", anilistId)
        val graphql = """
            query (${"$"}id: Int) {
              Media(id: ${"$"}id, type: ANIME) {
                reviews(sort: RATING_DESC, perPage: 20) {
                  nodes {
                    id
                    summary
                    body
                    score
                    user { name avatar { large } }
                  }
                }
              }
            }
        """.trimIndent()
        // Reviews are user-generated text, not media recommendations. Do not run the catalog
        // score/status filter over this Media object: it has no averageScore/status fields and
        // would otherwise discard the entire response before the review dialog can parse it.
        return query(graphql, variables, applyMediaFilters = false)
    }

    /**
     * Extracts official anime title (English/Romaji) and main/supporting character names
     * to enhance subtitle transcription, spelling, and grammar without errors.
     */
    suspend fun fetchAnimeCharacterAndTitleContext(anilistId: Int): List<String> = withContext(Dispatchers.IO) {
        if (anilistId <= 0) return@withContext emptyList()
        val entities = mutableListOf<String>()
        try {
            val detailsJson = getAnimeDetails(anilistId) ?: return@withContext emptyList()
            val mediaObj = JSONObject(detailsJson).optJSONObject("data")?.optJSONObject("Media") ?: return@withContext emptyList()

            // 1. Anime titles
            mediaObj.optJSONObject("title")?.let { tObj ->
                val eng = tObj.optString("english", "").trim()
                if (eng.isNotBlank() && eng != "null") entities.add(eng)
                val rom = tObj.optString("romaji", "").trim()
                if (rom.isNotBlank() && rom != "null") entities.add(rom)
            }

            // 2. Character names
            val charEdges = mediaObj.optJSONObject("characters")?.optJSONArray("edges")
            if (charEdges != null) {
                for (i in 0 until charEdges.length()) {
                    val node = charEdges.optJSONObject(i)?.optJSONObject("node")
                    val nameObj = node?.optJSONObject("name")
                    val full = nameObj?.optString("full", "")?.trim() ?: ""
                    if (full.isNotBlank() && full != "null") {
                        entities.add(full)
                        // Split full names (e.g. "Satoru Gojo" -> "Satoru", "Gojo")
                        val parts = full.split(" ").map { it.trim() }.filter { it.length >= 3 }
                        entities.addAll(parts)
                    }
                }
            }
        } catch (_: Throwable) {}
        entities.distinct()
    }

    /**
     * Fetch popular anime filtered by genre
     */
    fun isAdultAnimeCached(anilistId: Int): Boolean {
        if (anilistId <= 0) return false
        if (adultAnimeIds.contains(anilistId)) return true
        val cached = detailsCache.get(anilistId) ?: return false
        return try {
            val root = JSONObject(cached)
            val media = root.optJSONObject("data")?.optJSONObject("Media") ?: return false
            if (media.optBoolean("isAdult", false)) {
                adultAnimeIds.add(anilistId)
                return true
            }
            val genres = media.optJSONArray("genres")
            if (genres != null) {
                for (i in 0 until genres.length()) {
                    val g = genres.optString(i, "")
                    if (g.equals("Hentai", ignoreCase = true) || g.equals("Ecchi 18+", ignoreCase = true)) {
                        adultAnimeIds.add(anilistId)
                        return true
                    }
                }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    suspend fun isAdultAnime(anilistId: Int, title: String? = null): Boolean = withContext(Dispatchers.IO) {
        if (anilistId <= 0) return@withContext false
        if (adultAnimeIds.contains(anilistId)) return@withContext true
        try {
            val jsonStr = getAnimeDetails(anilistId) ?: return@withContext false
            val root = JSONObject(jsonStr)
            val media = root.optJSONObject("data")?.optJSONObject("Media") ?: return@withContext false
            if (media.optBoolean("isAdult", false)) {
                adultAnimeIds.add(anilistId)
                return@withContext true
            }
            val genres = media.optJSONArray("genres")
            if (genres != null) {
                for (i in 0 until genres.length()) {
                    val g = genres.optString(i, "")
                    if (g.equals("Hentai", ignoreCase = true) || g.equals("Ecchi 18+", ignoreCase = true)) {
                        adultAnimeIds.add(anilistId)
                        return@withContext true
                    }
                }
            }
        } catch (_: Exception) {}
        return@withContext false
    }

    suspend fun getAnimeByGenre(genre: String, page: Int = 1): String? {
        val variables = JSONObject().apply {
            put("genre", genre)
            put("page", page)
        }
        val appContext = CloudStreamApp.context
        val adultEnabled = if (appContext != null) com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isAdultContentEnabled(appContext) else false
        val adultArg = if (!adultEnabled) ", isAdult: false" else if (genre.equals("Hentai", ignoreCase = true)) ", isAdult: true" else ""
        val graphqlQuery = """
            query (${'$'}genre: String, ${'$'}page: Int) {
              Page(page: ${'$'}page, perPage: 50) {
                pageInfo {
                  hasNextPage
                }
                media(type: ANIME, genre: ${'$'}genre$adultArg, sort: POPULARITY_DESC) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()
        return query(graphqlQuery, variables)
    }

    /**
     * Fetch popular anime filtered by keyword tag (via AniList GraphQL tag_in)
     */
    suspend fun getAnimeByTag(tag: String, page: Int = 1): String? {
        val variables = JSONObject().apply {
            put("tag", tag)
            put("page", page)
        }
        val appContext = CloudStreamApp.context
        val adultEnabled = if (appContext != null) com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isAdultContentEnabled(appContext) else false
        val adultArg = if (!adultEnabled) ", isAdult: false" else ""
        val graphqlQuery = """
            query (${'$'}tag: String, ${'$'}page: Int) {
              Page(page: ${'$'}page, perPage: 50) {
                pageInfo {
                  hasNextPage
                }
                media(type: ANIME, tag: ${'$'}tag$adultArg, sort: POPULARITY_DESC) {
                  $MEDIA_FIELDS
                }
              }
            }
        """.trimIndent()
        return query(graphqlQuery, variables)
    }

    /**
     * Advanced multi-filter search on AniList with caching
     */
    suspend fun advancedSearch(
        queryText: String? = null,
        genres: List<String> = emptyList(),
        tags: List<String> = emptyList(),
        seasonYear: Int? = null,
        season: String? = null,
        format: String? = null,
        status: String? = null,
        sort: String? = null,
        page: Int = 1
    ): String? {
        val variables = JSONObject().apply {
            put("page", page)
            if (!queryText.isNullOrBlank()) put("search", queryText.trim())
            if (genres.isNotEmpty()) {
                val gArr = org.json.JSONArray()
                genres.forEach { gArr.put(it) }
                put("genres", gArr)
            }
            if (tags.isNotEmpty()) {
                val tArr = org.json.JSONArray()
                tags.forEach { tArr.put(it) }
                put("tags", tArr)
            }
            if (seasonYear != null && seasonYear > 1960) put("seasonYear", seasonYear)
            if (!season.isNullOrBlank()) put("season", season.uppercase())
            if (!format.isNullOrBlank()) put("format", format.uppercase())
            if (!status.isNullOrBlank()) put("status", status.uppercase())
            if (!sort.isNullOrBlank()) {
                val sArr = org.json.JSONArray()
                sArr.put(sort)
                put("sort", sArr)
            }
        }

        val paramDefs = mutableListOf("\$page: Int")
        val mediaArgs = mutableListOf("type: ANIME")

        val appContext = CloudStreamApp.context
        val adultEnabled = if (appContext != null) com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isAdultContentEnabled(appContext) else false
        if (!adultEnabled) {
            mediaArgs.add("isAdult: false")
        }

        if (!queryText.isNullOrBlank()) {
            paramDefs.add("\$search: String")
            mediaArgs.add("search: \$search")
        }
        if (genres.isNotEmpty()) {
            paramDefs.add("\$genres: [String]")
            mediaArgs.add("genre_in: \$genres")
        }
        if (tags.isNotEmpty()) {
            paramDefs.add("\$tags: [String]")
            mediaArgs.add("tag_in: \$tags")
        }
        if (seasonYear != null && seasonYear > 1960) {
            paramDefs.add("\$seasonYear: Int")
            mediaArgs.add("seasonYear: \$seasonYear")
        }
        if (!season.isNullOrBlank()) {
            paramDefs.add("\$season: MediaSeason")
            mediaArgs.add("season: \$season")
        }
        if (!format.isNullOrBlank()) {
            paramDefs.add("\$format: MediaFormat")
            mediaArgs.add("format: \$format")
        }
        if (!status.isNullOrBlank()) {
            paramDefs.add("\$status: MediaStatus")
            mediaArgs.add("status: \$status")
        }
        if (!sort.isNullOrBlank()) {
            paramDefs.add("\$sort: [MediaSort]")
            mediaArgs.add("sort: \$sort")
        } else {
            mediaArgs.add("sort: POPULARITY_DESC")
        }

        val paramStr = paramDefs.joinToString(", ")
        val mediaArgsStr = mediaArgs.joinToString(", ")

        val graphqlQuery = """
            query ($paramStr) {
              Page(page: ${'$'}page, perPage: 40) {
                pageInfo {
                  hasNextPage
                }
                media($mediaArgsStr) {
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

    /**
     * Retrieve high quality portrait cover image directly from AniList
     */
    suspend fun getAnimeCover(anilistId: Int): String = withContext(Dispatchers.IO) {
        if (anilistId <= 0) return@withContext ""
        try {
            val graphqlQuery = """
                query (${'$'}id: Int) {
                    Media(id: ${'$'}id, type: ANIME) {
                        coverImage {
                            extraLarge
                            large
                            medium
                        }
                    }
                }
            """.trimIndent()
            val vars = JSONObject().apply { put("id", anilistId) }
            val raw = query(graphqlQuery, vars)
            if (raw != null) {
                val mediaObj = JSONObject(raw).optJSONObject("data")?.optJSONObject("Media")
                val coverObj = mediaObj?.optJSONObject("coverImage")
                val cover = coverObj?.optString("extraLarge", "")?.ifEmpty {
                    coverObj.optString("large", "")
                } ?: ""
                if (cover.isNotBlank()) return@withContext cover
            }
        } catch (_: Exception) {}
        ""
    }

    /**
     * Search an anime character by name and return their verified image from AniList
     */
    suspend fun searchCharacter(characterName: String): AniListCharacterData? = withContext(Dispatchers.IO) {
        val trimmed = characterName.trim()
        if (trimmed.isEmpty()) return@withContext null
        try {
            val graphqlQuery = """
                query (${'$'}search: String) {
                    Character(search: ${'$'}search) {
                        id
                        name {
                            full
                            native
                        }
                        image {
                            large
                            medium
                        }
                    }
                }
            """.trimIndent()
            val vars = JSONObject().apply { put("search", trimmed) }
            val raw = query(graphqlQuery, vars)
            if (raw != null) {
                val charObj = JSONObject(raw).optJSONObject("data")?.optJSONObject("Character")
                if (charObj != null) {
                    val id = charObj.optInt("id", 0)
                    val nameObj = charObj.optJSONObject("name")
                    val fullName = nameObj?.optString("full") ?: nameObj?.optString("native") ?: trimmed
                    val imageObj = charObj.optJSONObject("image")
                    val img = imageObj?.optString("large")?.takeIf { it.isNotBlank() }
                        ?: imageObj?.optString("medium")?.takeIf { it.isNotBlank() }
                        ?: ""
                    // Only return if verified character image is available
                    if (img.isNotBlank() && id > 0) {
                        return@withContext AniListCharacterData(id = id, name = fullName, imageUrl = img)
                    }
                }
            }
        } catch (_: Exception) {}
        null
    }
}

data class AniListCharacterData(
    val id: Int,
    val name: String,
    val imageUrl: String
)
