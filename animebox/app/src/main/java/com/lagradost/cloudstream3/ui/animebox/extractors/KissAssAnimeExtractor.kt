package com.lagradost.cloudstream3.ui.animebox.extractors

import com.lagradost.cloudstream3.ui.animebox.api.AniListClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class KissAssAnimeResult(
    val hlsUrl: String,
    val referer: String = "https://krussdomi.com/",
    val provider: String = "KissAssAnime",
    val subtitleUrl: String = "",
    val subtitles: List<Map<String, String>> = emptyList(),
    val introStart: Long = 0L,
    val introEnd: Long = 0L,
    val outroStart: Long = 0L,
    val outroEnd: Long = 0L
)

/**
 * 1:1 implementation of Anilili's KissAssAnime (KickAssAnime / kaa.lt) mapping & stream resolver:
 * - F4.C0489r7 (POST https://kaa.lt/api/fsearch, show scoring with year & format weighting, threshold 0.28)
 * - F4.D9 (Dice bigram coefficient D9.k & length penalty D9.p)
 * - F4.C0415m2 (show episodes, episode details, VidStreaming HLS server src, krussdomi master manifest & subtitles)
 */
object KissAssAnimeExtractor {

    private const val BASE_URL = "https://kaa.lt"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private data class KaaShow(
        val slug: String,
        val title: String,
        val locales: Set<String>,
        val year: Int?,
        val type: String?
    )

    private val showCache = ConcurrentHashMap<Int, KaaShow>()

    // ─── Anilili D9.j, D9.k, D9.p similarity functions ───

    private fun normalizeTitle(value: String): String {
        return value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
    }

    private fun diceSimilarity(str1: String, str2: String): Double {
        if (str1.isEmpty() || str2.isEmpty()) return 0.0
        if (str1 == str2) return 1.0
        if (str1.contains(str2) || str2.contains(str1)) return 0.86
        if (str1.length < 2 || str2.length < 2) return 0.0

        val map = HashMap<String, Int>()
        for (i in 0 until str1.length - 1) {
            val sub = str1.substring(i, i + 2)
            map[sub] = (map[sub] ?: 0) + 1
        }

        var matches = 0
        for (i in 0 until str2.length - 1) {
            val sub = str2.substring(i, i + 2)
            val count = map[sub] ?: 0
            if (count > 0) {
                matches++
                map[sub] = count - 1
            }
        }

        return (matches * 2.0) / ((str1.length - 1) + (str2.length - 1))
    }

    private fun computeTitleScore(query: String, candidate: String): Double {
        val q = normalizeTitle(query)
        val c = normalizeTitle(candidate)
        val dK = diceSimilarity(q, c)
        if (q.isEmpty() || c.isEmpty() || q == c) return dK
        val minL = Math.min(q.length, c.length).toDouble()
        val maxL = Math.max(q.length, c.length).toDouble()
        return (((minL / maxL) * 0.4) + 0.6) * dK
    }

    /**
     * Anilili F4.C0489r7.a scoring algorithm:
     * - Best title match from English, Romaji, Native
     * - Year match: * 1.15 if match, * 0.55 if mismatch
     * - Format check: * 0.7 if format mismatch
     * - Capped at 1.0
     */
    private fun scoreShow(
        titles: List<String>,
        seasonYear: Int?,
        format: String?,
        show: KaaShow
    ): Double {
        var bestTitleScore = 0.0
        for (t in titles) {
            val sc = computeTitleScore(t, show.title)
            if (sc > bestTitleScore) bestTitleScore = sc
        }
        var score = bestTitleScore

        if (seasonYear != null && show.year != null) {
            score *= if (seasonYear == show.year) 1.15 else 0.55
        }

        val mediaFmt = format?.lowercase(Locale.ROOT)
        val showFmt = show.type?.lowercase(Locale.ROOT)
        if (mediaFmt != null && showFmt != null && mediaFmt != showFmt) {
            val bothTv = mediaFmt.contains("tv") && showFmt.contains("tv")
            if (!bothTv) {
                score *= 0.7
            }
        }

        return if (score > 1.0) 1.0 else score
    }

    private suspend fun searchKaa(query: String): List<KaaShow> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val shows = mutableListOf<KaaShow>()
        try {
            val payload = JSONObject().apply {
                put("query", query)
            }.toString()

            val req = Request.Builder()
                .url("$BASE_URL/api/fsearch")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json,text/html,*/*")
                .header("Referer", "$BASE_URL/")
                .header("Content-Type", "application/json; charset=utf-8")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val respStr = AnililiHttpClient.client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }

            if (respStr.isNotEmpty()) {
                val root = JSONObject(respStr)
                val resultArr = root.optJSONArray("result")
                if (resultArr != null) {
                    for (i in 0 until resultArr.length()) {
                        val item = resultArr.optJSONObject(i) ?: continue
                        val slug = item.optString("slug", "")
                        if (slug.isEmpty()) continue

                        val titleEn = item.optString("title_en", "")
                        val titleRaw = item.optString("title", "")
                        val displayTitle = when {
                            titleEn.isNotBlank() -> titleEn
                            titleRaw.isNotBlank() -> titleRaw
                            else -> slug
                        }

                        val localesSet = mutableSetOf<String>()
                        val locArr = item.optJSONArray("locales")
                        if (locArr != null) {
                            for (j in 0 until locArr.length()) {
                                val loc = locArr.optString(j, "")
                                if (loc.isNotBlank()) localesSet.add(loc)
                            }
                        }

                        val yearVal = if (item.has("year") && !item.isNull("year")) item.optInt("year", 0) else null
                        val effectiveYear = if (yearVal != null && yearVal > 0) yearVal else null
                        val typeStr = item.optString("type", "").takeIf { it.isNotBlank() }

                        shows.add(KaaShow(slug, displayTitle, localesSet, effectiveYear, typeStr))
                    }
                }
            }
        } catch (_: Exception) {}
        shows
    }

    private suspend fun resolveBestShow(
        anilistId: Int,
        animeTitle: String?
    ): KaaShow? = withContext(Dispatchers.IO) {
        if (anilistId > 0) {
            showCache[anilistId]?.let { return@withContext it }
        }

        val titles = mutableListOf<String>()
        var seasonYear: Int? = null
        var format: String? = null

        if (anilistId > 0) {
            try {
                val details = AniListClient.getAnimeDetails(anilistId)
                if (details != null) {
                    val media = JSONObject(details).optJSONObject("data")?.optJSONObject("Media")
                    if (media != null) {
                        val t = media.optJSONObject("title")
                        t?.optString("english", "")?.takeIf { it.isNotBlank() }?.let { titles.add(it) }
                        t?.optString("romaji", "")?.takeIf { it.isNotBlank() }?.let { if (!titles.contains(it)) titles.add(it) }
                        t?.optString("native", "")?.takeIf { it.isNotBlank() }?.let { if (!titles.contains(it)) titles.add(it) }

                        val sy = media.optInt("seasonYear", 0)
                        if (sy > 0) seasonYear = sy
                        format = media.optString("format", "").takeIf { it.isNotBlank() }
                    }
                }
            } catch (_: Exception) {}
        }

        if (titles.isEmpty() && !animeTitle.isNullOrBlank()) {
            titles.add(animeTitle)
        }

        if (titles.isEmpty()) return@withContext null

        val candidateShows = mutableListOf<KaaShow>()
        val seenSlugs = HashSet<String>()

        for (t in titles) {
            val list = searchKaa(t)
            for (s in list) {
                if (seenSlugs.add(s.slug)) {
                    candidateShows.add(s)
                }
            }
            if (candidateShows.isNotEmpty()) break
        }

        if (candidateShows.isEmpty()) {
            // Try cleaning titles
            for (t in titles) {
                val cleaned = t.replace(Regex("""[\(\[\{].*?[\)\]\}]"""), " ")
                    .replace(Regex("""[^a-zA-Z0-9\s]"""), " ")
                    .trim()
                if (cleaned.isNotBlank() && cleaned != t) {
                    val list = searchKaa(cleaned)
                    for (s in list) {
                        if (seenSlugs.add(s.slug)) {
                            candidateShows.add(s)
                        }
                    }
                }
            }
        }

        if (candidateShows.isEmpty()) return@withContext null

        var bestShow: KaaShow? = null
        var bestScore = -1.0

        for (show in candidateShows) {
            val sc = scoreShow(titles, seasonYear, format, show)
            if (sc > bestScore) {
                bestScore = sc
                bestShow = show
            }
        }

        if (bestShow == null || bestScore < 0.28) {
            return@withContext null
        }

        if (anilistId > 0) {
            showCache[anilistId] = bestShow
        }
        bestShow
    }

    /**
     * Extracts stream from KissAssAnime using Anilili 1:1 logic.
     */
    suspend fun extract(
        anilistId: Int,
        episodeNum: Int,
        animeTitle: String? = null,
        mode: String = "sub"
    ): KissAssAnimeResult? = withContext(Dispatchers.IO) {
        val show = resolveBestShow(anilistId, animeTitle) ?: return@withContext null

        val isDub = mode.equals("dub", ignoreCase = true)
        val lang = if (isDub) "en-US" else "ja-JP"

        if (show.locales.isNotEmpty() && !show.locales.contains(lang)) {
            // Requested audio language not available in this show
            if (!isDub && show.locales.contains("en-US")) {
                // Keep looking
            } else if (isDub && !show.locales.contains("en-US")) {
                return@withContext null
            }
        }

        val epUrl = "$BASE_URL/api/show/${show.slug}/episodes?ep=$episodeNum&lang=$lang"
        val epReq = Request.Builder()
            .url(epUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json,text/html,*/*")
            .header("Referer", "$BASE_URL/")
            .build()

        val epResp = try {
            AnililiHttpClient.client.newCall(epReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }
        } catch (_: Exception) { "" }

        if (epResp.isEmpty()) return@withContext null

        var episodeId: String? = null
        try {
            val epJson = if (epResp.trim().startsWith("[")) {
                JSONArray(epResp)
            } else {
                JSONObject(epResp).optJSONArray("result") ?: JSONArray()
            }

            for (i in 0 until epJson.length()) {
                val obj = epJson.optJSONObject(i) ?: continue
                val epNum = obj.optInt("episode_number", obj.optInt("number", 0))
                val epString = obj.optString("episode_string", "")
                if (epNum == episodeNum || epString == episodeNum.toString()) {
                    episodeId = obj.optString("episode_id", obj.optString("id", ""))
                    break
                }
            }
        } catch (_: Exception) {}

        if (episodeId.isNullOrEmpty()) return@withContext null

        val detailUrl = "$BASE_URL/api/show/${show.slug}/episode/ep-$episodeNum-$episodeId"
        val detailReq = Request.Builder()
            .url(detailUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json,text/html,*/*")
            .header("Referer", "$BASE_URL/")
            .build()

        val detailResp = try {
            AnililiHttpClient.client.newCall(detailReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }
        } catch (_: Exception) { "" }

        if (detailResp.isEmpty()) return@withContext null

        var vidStreamSrc: String? = null
        try {
            val detailJson = JSONObject(detailResp)
            val servers = detailJson.optJSONArray("servers")
            if (servers != null) {
                for (i in 0 until servers.length()) {
                    val sObj = servers.optJSONObject(i) ?: continue
                    val name = sObj.optString("name", "")
                    val src = sObj.optString("src", "")
                    if (name.equals("VidStreaming", ignoreCase = true) || src.contains("source=vidstream", ignoreCase = true)) {
                        vidStreamSrc = src
                        break
                    }
                }
            }
        } catch (_: Exception) {}

        if (vidStreamSrc.isNullOrEmpty()) return@withContext null

        var mediaId: String? = null
        try {
            val uri = URI(vidStreamSrc)
            val q = uri.rawQuery.orEmpty()
            for (param in q.split("&")) {
                val parts = param.split("=")
                if (parts.size >= 2 && parts[0] == "id" && parts[1].isNotEmpty()) {
                    mediaId = parts[1]
                    break
                }
            }
        } catch (_: Exception) {}

        if (mediaId.isNullOrEmpty()) return@withContext null

        val masterHls = "https://hls.krussdomi.com/manifest/$mediaId/master.m3u8"

        // Subtitles extraction from VidStreaming page
        val subtitlesList = mutableListOf<Map<String, String>>()
        var primarySub = ""
        try {
            val playerReq = Request.Builder()
                .url(vidStreamSrc)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,*/*")
                .header("Referer", "$BASE_URL/")
                .build()

            val playerHtml = AnililiHttpClient.client.newCall(playerReq).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }

            if (playerHtml.isNotEmpty()) {
                val subPattern = Pattern.compile(""""language":\[0,"([^"]*)"\],"name":\[0,"([^"]*)"\],"src":\[0,"([^"]+\.vtt)"\]""")
                val m = subPattern.matcher(playerHtml)
                while (m.find()) {
                    val langCode = m.group(1).orEmpty()
                    val langName = m.group(2).orEmpty()
                    val subUrl = m.group(3).orEmpty()
                    if (subUrl.isNotEmpty()) {
                        val label = when {
                            langName.isNotBlank() -> langName
                            langCode.isNotBlank() -> langCode
                            else -> "English"
                        }
                        subtitlesList.add(mapOf("url" to subUrl, "lang" to label))
                    }
                }
            }
        } catch (_: Exception) {}

        if (subtitlesList.isNotEmpty()) {
            val eng = subtitlesList.find { (it["lang"] ?: "").contains("eng", true) }
            primarySub = eng?.get("url") ?: subtitlesList.first()["url"] ?: ""
        }

        KissAssAnimeResult(
            hlsUrl = masterHls,
            referer = "https://krussdomi.com/",
            provider = "KissAssAnime",
            subtitleUrl = primarySub,
            subtitles = subtitlesList
        )
    }
}
