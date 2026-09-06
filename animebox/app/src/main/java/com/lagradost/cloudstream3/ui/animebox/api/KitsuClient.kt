package com.lagradost.cloudstream3.ui.animebox.api

import com.lagradost.cloudstream3.ui.animebox.AnimeBrief
import com.lagradost.cloudstream3.ui.animebox.AnimeCharacter
import com.lagradost.cloudstream3.ui.animebox.AnimeDetail
import com.lagradost.cloudstream3.ui.animebox.AnimeRelation
import com.lagradost.cloudstream3.ui.animebox.AnimeRecommendation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object KitsuClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    private const val BASE_URL = "https://kitsu.io/api/edge"

    // Global flag indicating whether the app is currently running in Kitsu fallback mode
    var isKitsuModeActive: Boolean = false

    val KITSU_GENRES = listOf(
        "Action",
        "Adventure",
        "Comedy",
        "Drama",
        "Fantasy",
        "Romance",
        "Sci-Fi",
        "Slice of Life",
        "Sports",
        "Supernatural",
        "Mystery",
        "Psychological",
        "Thriller",
        "Horror",
        "Mecha",
        "Magic",
        "Music",
        "School",
        "Shounen",
        "Isekai"
    )

    private suspend fun executeGet(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .addHeader("Accept", "application/vnd.api+json")
                .addHeader("Content-Type", "application/vnd.api+json")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    return@withContext response.body?.string()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        null
    }

    suspend fun getTrendingAnime(): List<AnimeBrief> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/trending/anime?limit=15"
        val jsonStr = executeGet(url) ?: return@withContext emptyList()
        parseKitsuAnimeList(jsonStr)
    }

    suspend fun getPopularAnime(): List<AnimeBrief> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/anime?sort=popularityRank&page[limit]=20"
        val jsonStr = executeGet(url) ?: return@withContext emptyList()
        parseKitsuAnimeList(jsonStr)
    }

    suspend fun getRecentlyAddedAnime(): List<AnimeBrief> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/anime?sort=-createdAt&page[limit]=20"
        val jsonStr = executeGet(url) ?: return@withContext emptyList()
        parseKitsuAnimeList(jsonStr)
    }

    suspend fun getPopularThisSeasonAnime(): List<AnimeBrief> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/anime?sort=popularityRank&page[limit]=20"
        val jsonStr = executeGet(url) ?: return@withContext emptyList()
        parseKitsuAnimeList(jsonStr)
    }

    suspend fun getPopularMovies(): List<AnimeBrief> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/anime?filter[subtype]=movie&sort=popularityRank&page[limit]=20"
        val jsonStr = executeGet(url) ?: return@withContext emptyList()
        parseKitsuAnimeList(jsonStr)
    }

    suspend fun getComingSoonAnime(): List<AnimeBrief> = withContext(Dispatchers.IO) {
        val url = "$BASE_URL/anime?filter[status]=unreleased&sort=popularityRank&page[limit]=20"
        val jsonStr = executeGet(url) ?: return@withContext emptyList()
        val list = parseKitsuAnimeList(jsonStr, allowUnreleased = true)
        list.map { it.copy(status = "NOT_YET_RELEASED") }
    }

    suspend fun searchAnime(query: String): List<AnimeBrief> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "$BASE_URL/anime?filter[text]=$encoded&page[limit]=20"
        val jsonStr = executeGet(url) ?: return@withContext emptyList()
        parseKitsuAnimeList(jsonStr)
    }

    suspend fun getAnimeByGenre(genre: String, page: Int = 1): List<AnimeBrief> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(genre.lowercase(), "UTF-8")
        val offset = (page - 1) * 20
        val url = "$BASE_URL/anime?filter[categories]=$encoded&page[limit]=20&page[offset]=$offset"
        val jsonStr = executeGet(url) ?: return@withContext emptyList()
        parseKitsuAnimeList(jsonStr)
    }

    private suspend fun parseKitsuAnimeList(jsonStr: String, allowUnreleased: Boolean = false): List<AnimeBrief> = withContext(Dispatchers.IO) {
        val result = mutableListOf<AnimeBrief>()
        try {
            val root = JSONObject(jsonStr)
            val dataArr = root.optJSONArray("data") ?: return@withContext emptyList()
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

            val defs = (0 until dataArr.length()).mapNotNull { i ->
                val obj = dataArr.optJSONObject(i) ?: return@mapNotNull null
                val kitsuId = obj.optInt("id", -1)
                val attr = obj.optJSONObject("attributes") ?: return@mapNotNull null

                val status = attr.optString("status", "")
                val startDate = attr.optString("startDate", "")
                val isFuture = if (startDate.isNotEmpty() && startDate.length >= 10) startDate > todayStr else false
                val isUnreleased = status.equals("unreleased", ignoreCase = true) ||
                        status.equals("not_yet_released", ignoreCase = true) ||
                        status.equals("tbd", ignoreCase = true)

                // Unless specifically requesting coming soon, filter out future releases
                if (!allowUnreleased && (isFuture || isUnreleased)) {
                    return@mapNotNull null
                }

                val titleObj = attr.optJSONObject("titles")
                val canonicalTitle = attr.optString("canonicalTitle", "")
                val enTitle = titleObj?.optString("en", "") ?: ""
                val enJpTitle = titleObj?.optString("en_jp", "") ?: ""
                val title = when {
                    enTitle.isNotBlank() -> enTitle
                    canonicalTitle.isNotBlank() -> canonicalTitle
                    enJpTitle.isNotBlank() -> enJpTitle
                    else -> "Unknown Anime"
                }

                val posterObj = attr.optJSONObject("posterImage")
                val coverUrl = posterObj?.optString("large", posterObj.optString("original", "")) ?: ""

                val coverObj = attr.optJSONObject("coverImage")
                val bannerUrl = coverObj?.optString("large", coverObj.optString("original", "")) ?: ""

                val synopsis = attr.optString("synopsis", attr.optString("description", ""))
                val averageScore = attr.optDouble("averageRating", 0.0).toInt().coerceIn(0, 100)
                val episodes = attr.optInt("episodeCount", 0)

                async {
                    val resolvedAnilistId = if (kitsuId > 0) {
                        AniZipClient.resolveAnilistIdFromKitsu(kitsuId) ?: kitsuId
                    } else kitsuId

                    val logo = if (resolvedAnilistId > 0) {
                        AniZipClient.getAnimeLogoUrl(resolvedAnilistId)
                    } else ""

                    val tmdbBackdrop = if (resolvedAnilistId > 0) {
                        AniZipClient.getTmdbBackdropUrl(resolvedAnilistId)
                            .ifEmpty { bannerUrl.ifEmpty { coverUrl } }
                    } else {
                        bannerUrl.ifEmpty { coverUrl }
                    }

                    AnimeBrief(
                        id = resolvedAnilistId,
                        title = title,
                        coverUrl = coverUrl,
                        bannerUrl = tmdbBackdrop,
                        description = synopsis,
                        genres = emptyList(),
                        averageScore = averageScore,
                        logoUrl = logo,
                        episodes = episodes,
                        status = status
                    )
                }
            }

            result.addAll(defs.awaitAll())
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }

    /**
     * Fetches detailed anime metadata from Kitsu when AniList is offline.
     */
    suspend fun getAnimeDetail(anilistId: Int, fallbackTitle: String = ""): AnimeDetail? = withContext(Dispatchers.IO) {
        try {
            var kitsuId = AniZipClient.getKitsuIdFromAnilist(anilistId)
            var detailJsonStr: String? = null

            if (kitsuId != null && kitsuId > 0) {
                detailJsonStr = executeGet("$BASE_URL/anime/$kitsuId")
            }

            // If direct kitsuId fetch failed or was missing, search Kitsu by title
            if (detailJsonStr == null && fallbackTitle.isNotBlank()) {
                val searchUrl = "$BASE_URL/anime?filter[text]=${URLEncoder.encode(fallbackTitle, "UTF-8")}&page[limit]=1"
                val searchResp = executeGet(searchUrl)
                if (searchResp != null) {
                    val sRoot = JSONObject(searchResp)
                    val sData = sRoot.optJSONArray("data")
                    if (sData != null && sData.length() > 0) {
                        val firstObj = sData.getJSONObject(0)
                        kitsuId = firstObj.optInt("id", kitsuId ?: anilistId)
                        detailJsonStr = searchResp // Contains the matched anime data
                    }
                }
            }

            if (detailJsonStr == null) {
                detailJsonStr = executeGet("$BASE_URL/anime/$anilistId")
            }

            if (detailJsonStr == null) return@withContext null
            val root = JSONObject(detailJsonStr)
            val dataObj = if (root.has("data") && root.get("data") is JSONArray) {
                root.getJSONArray("data").optJSONObject(0) ?: return@withContext null
            } else {
                root.optJSONObject("data") ?: return@withContext null
            }
            val attr = dataObj.optJSONObject("attributes") ?: return@withContext null
            val resolvedKitsuId = dataObj.optInt("id", kitsuId ?: anilistId)

            val titleObj = attr.optJSONObject("titles")
            val canonicalTitle = attr.optString("canonicalTitle", "")
            val enTitle = titleObj?.optString("en", "") ?: ""
            val enJpTitle = titleObj?.optString("en_jp", "") ?: ""
            val title = when {
                enTitle.isNotBlank() -> enTitle
                canonicalTitle.isNotBlank() -> canonicalTitle
                enJpTitle.isNotBlank() -> enJpTitle
                fallbackTitle.isNotBlank() -> fallbackTitle
                else -> "Unknown Anime"
            }

            val posterObj = attr.optJSONObject("posterImage")
            val coverUrl = posterObj?.optString("large",
                posterObj.optString("original",
                    posterObj.optString("medium",
                        posterObj.optString("small", "")))) ?: ""

            val coverObj = attr.optJSONObject("coverImage")
            val bannerUrl = coverObj?.optString("large",
                coverObj.optString("original",
                    coverObj.optString("tiny", ""))) ?: ""

            val synopsis = attr.optString("synopsis", attr.optString("description", "")).replace(Regex("<[^>]*>"), "")
            val score = attr.optDouble("averageRating", 0.0).toInt().coerceIn(0, 100)
            val isLongRunning = AniZipClient.getLongRunningTmdbId(anilistId) != null
            val rawEpCount = attr.optInt("episodeCount", 0)
            val episodesCount = if (rawEpCount > 0) rawEpCount else if (isLongRunning) 0 else 12
            val status = attr.optString("status", "").uppercase()
            val format = attr.optString("showType", "TV").uppercase()
            val startDate = attr.optString("startDate", "")
            val releaseYear = if (startDate.length >= 4) startDate.substring(0, 4).toIntOrNull() ?: 2024 else 2024
            val ageRating = attr.optString("ageRating", "")
            val isAdult = ageRating.equals("R18", ignoreCase = true)
            val youtubeVideoId = attr.optString("youtubeVideoId", "")

            // Parallel fetch of characters, relations, and recommendations from Kitsu
            val charactersDeferred = async { getAnimeCharacters(resolvedKitsuId) }
            val relationsDeferred = async { getAnimeRelations(resolvedKitsuId) }
            val recsDeferred = async { getAnimeRecommendations(resolvedKitsuId) }

            val characters = charactersDeferred.await()
            val relations = relationsDeferred.await()
            val recommendations = recsDeferred.await()

            AnimeDetail(
                id = anilistId,
                title = title,
                description = synopsis,
                coverUrl = coverUrl,
                bannerUrl = bannerUrl,
                episodesCount = episodesCount,
                score = score,
                genres = emptyList(),
                status = status,
                format = format,
                releaseYear = releaseYear,
                isAdult = isAdult,
                idMal = 0,
                trailerId = youtubeVideoId,
                characters = characters,
                relations = relations,
                recommendations = recommendations
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun getAnimeCharacters(kitsuId: Int): List<AnimeCharacter> = withContext(Dispatchers.IO) {
        val result = mutableListOf<AnimeCharacter>()
        try {
            // Fetch characters via Kitsu castings with included character and person data
            val url = "$BASE_URL/castings?filter[media_id]=$kitsuId&filter[media_type]=Anime&include=character,person&page[limit]=50"
            val jsonStr = executeGet(url) ?: return@withContext emptyList()
            val root = JSONObject(jsonStr)

            val includedArr = root.optJSONArray("included")
            val charMap = mutableMapOf<String, JSONObject>()
            val personMap = mutableMapOf<String, JSONObject>()

            if (includedArr != null) {
                for (i in 0 until includedArr.length()) {
                    val item = includedArr.optJSONObject(i) ?: continue
                    val type = item.optString("type")
                    val id = item.optString("id")
                    if (type == "characters") {
                        charMap[id] = item
                    } else if (type == "people") {
                        personMap[id] = item
                    }
                }
            }

            val dataArr = root.optJSONArray("data")
            // Map characterId -> best casting (preferring Japanese)
            val charBestCasting = mutableMapOf<String, Triple<String, String, Boolean>>() // charId -> (role, personId, isJp)
            val charNames = mutableMapOf<String, String>()
            val charImgs = mutableMapOf<String, String>()

            if (dataArr != null) {
                for (i in 0 until dataArr.length()) {
                    val casting = dataArr.optJSONObject(i) ?: continue
                    val attr = casting.optJSONObject("attributes")
                    val role = attr?.optString("role", "Main") ?: "Main"
                    val language = attr?.optString("language", "") ?: ""
                    val rels = casting.optJSONObject("relationships")

                    val charId = rels?.optJSONObject("character")?.optJSONObject("data")?.optString("id") ?: ""
                    val personId = rels?.optJSONObject("person")?.optJSONObject("data")?.optString("id") ?: ""

                    if (charId.isEmpty()) continue

                    val charObj = charMap[charId]
                    val charAttr = charObj?.optJSONObject("attributes")
                    val charName = charAttr?.optString("canonicalName", charAttr.optString("name", "")) ?: ""
                    val charImg = charAttr?.optJSONObject("image")?.optString("original", "") ?: ""

                    if (charName.isNotBlank()) {
                        charNames[charId] = charName
                        charImgs[charId] = charImg

                        val isJp = language.equals("Japanese", ignoreCase = true) || language.equals("JAPANESE", ignoreCase = true)
                        val existing = charBestCasting[charId]
                        if (existing == null || (!existing.third && isJp)) {
                            charBestCasting[charId] = Triple(role, personId, isJp)
                        }
                    }
                }

                // Construct unique character list (one entry per unique character name)
                val seenNames = mutableSetOf<String>()
                for ((charId, triple) in charBestCasting) {
                    val charName = charNames[charId] ?: continue
                    val lowerName = charName.lowercase().trim()
                    if (seenNames.contains(lowerName)) continue
                    seenNames.add(lowerName)

                    val charImg = charImgs[charId] ?: ""
                    val role = triple.first
                    val personId = triple.second

                    val personObj = personMap[personId]
                    val personAttr = personObj?.optJSONObject("attributes")
                    val actorName = personAttr?.optString("name", "") ?: ""
                    val actorImg = personAttr?.optJSONObject("image")?.optString("original", "") ?: ""

                    result.add(AnimeCharacter(charName, charImg, role, actorName, actorImg))
                }
            }

            // Fallback to /characters endpoint if castings was empty
            if (result.isEmpty()) {
                val charUrl = "$BASE_URL/anime/$kitsuId/characters?include=character&page[limit]=20"
                val charJsonStr = executeGet(charUrl)
                if (charJsonStr != null) {
                    val cRoot = JSONObject(charJsonStr)
                    val cIncluded = cRoot.optJSONArray("included")
                    val seenFallback = mutableSetOf<String>()
                    if (cIncluded != null) {
                        for (i in 0 until cIncluded.length()) {
                            val cObj = cIncluded.optJSONObject(i) ?: continue
                            val cAttr = cObj.optJSONObject("attributes") ?: continue
                            val name = cAttr.optString("canonicalName", cAttr.optString("name", ""))
                            val img = cAttr.optJSONObject("image")?.optString("original", "") ?: ""
                            val lower = name.lowercase().trim()
                            if (name.isNotBlank() && !seenFallback.contains(lower)) {
                                seenFallback.add(lower)
                                result.add(AnimeCharacter(name, img, "Main", "", ""))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }

    suspend fun getAnimeRelations(kitsuId: Int): List<AnimeRelation> = withContext(Dispatchers.IO) {
        val result = mutableListOf<AnimeRelation>()
        try {
            val url = "$BASE_URL/anime/$kitsuId/media-relationships?include=destination&page[limit]=10"
            val jsonStr = executeGet(url) ?: return@withContext emptyList()
            val root = JSONObject(jsonStr)

            val includedMap = mutableMapOf<String, JSONObject>()
            val includedArr = root.optJSONArray("included")
            if (includedArr != null) {
                for (i in 0 until includedArr.length()) {
                    val item = includedArr.optJSONObject(i) ?: continue
                    if (item.optString("type") == "anime") {
                        includedMap[item.optString("id")] = item
                    }
                }
            }

            val dataArr = root.optJSONArray("data") ?: return@withContext emptyList()
            for (i in 0 until dataArr.length()) {
                val relObj = dataArr.optJSONObject(i) ?: continue
                val role = relObj.optJSONObject("attributes")?.optString("role", "Relation") ?: "Relation"
                val destId = relObj.optJSONObject("relationships")?.optJSONObject("destination")?.optJSONObject("data")?.optString("id") ?: ""

                val animeObj = includedMap[destId] ?: continue
                val targetKitsuId = animeObj.optInt("id", -1)
                val targetAttr = animeObj.optJSONObject("attributes") ?: continue
                val title = targetAttr.optString("canonicalTitle", "Anime")
                val cover = targetAttr.optJSONObject("posterImage")?.optString("large", "") ?: ""
                val format = targetAttr.optString("showType", "TV")

                val resolvedAnilistId = if (targetKitsuId > 0) {
                    AniZipClient.resolveAnilistIdFromKitsu(targetKitsuId) ?: targetKitsuId
                } else targetKitsuId

                result.add(AnimeRelation(resolvedAnilistId, title, cover, role, format))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }

    suspend fun getAnimeRecommendations(kitsuId: Int): List<AnimeRecommendation> = withContext(Dispatchers.IO) {
        val result = mutableListOf<AnimeRecommendation>()
        try {
            val trending = getTrendingAnime()
            for (anime in trending.take(8)) {
                result.add(AnimeRecommendation(anime.id, anime.title, anime.coverUrl, "TV"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        result
    }
}
