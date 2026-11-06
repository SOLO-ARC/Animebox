package com.lagradost.cloudstream3.ui.animebox.extractors

import com.lagradost.cloudstream3.ui.animebox.api.AniListClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class AniwavesResult(
    val hlsUrl: String,
    val referer: String = "https://aniwaves.ru/",
    val provider: String = "AniWave",
    val isHardSub: Boolean = false,
    val subtitleUrl: String = "",
    val subtitles: List<Map<String, String>> = emptyList(),
    val introStart: Long = 0L,
    val introEnd: Long = 0L,
    val outroStart: Long = 0L,
    val outroEnd: Long = 0L
)

object AniwavesExtractor {

    private val client: OkHttpClient by lazy {
        val builder = try {
            com.lagradost.cloudstream3.app.baseClient.newBuilder()
        } catch (_: Throwable) {
            OkHttpClient.Builder()
        }
        builder
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private const val BASE_URL = "https://aniwaves.ru"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val API_CANDIDATE_HOSTS = listOf(
        "http://10.0.2.2:4000",
        "http://localhost:4000",
        "http://127.0.0.1:4000"
    )

    private val idCache = ConcurrentHashMap<Int, String>()

    private fun cleanTitle(raw: String): String {
        var s = raw.replace(Regex("""[\(\[\{].*?[\)\]\}]"""), " ")
        s = s.replace(Regex(""":\s*(?:The\s+Series|Season\s*\d+|Part\s*\d+|2nd\s*Season).*""", RegexOption.IGNORE_CASE), " ")
        s = s.replace('é', 'e').replace('É', 'E')
        s = s.replace(Regex("""[^a-zA-Z0-9\s]"""), " ")
        return s.trim().split(Regex("""\s+""")).joinToString(" ")
    }

    private suspend fun getSearchTitles(anilistId: Int, animeTitle: String?): List<String> {
        val list = mutableListOf<String>()
        if (!animeTitle.isNullOrBlank()) {
            val c = cleanTitle(animeTitle)
            if (c.isNotEmpty()) list.add(c)
        }

        if (anilistId > 0) {
            try {
                val details = AniListClient.getAnimeDetails(anilistId)
                if (details != null) {
                    val media = JSONObject(details).optJSONObject("data")?.optJSONObject("Media")
                    val t = media?.optJSONObject("title")
                    t?.optString("english", "")?.takeIf { it.isNotBlank() }?.let {
                        val c = cleanTitle(it)
                        if (c.isNotEmpty() && !list.contains(c)) list.add(0, c)
                    }
                    t?.optString("romaji", "")?.takeIf { it.isNotBlank() }?.let {
                        val c = cleanTitle(it)
                        if (c.isNotEmpty() && !list.contains(c)) list.add(c)
                    }
                }
            } catch (_: Exception) {}
        }
        return list.distinct()
    }

    /**
     * Extracts Japanese HardSub stream from AniWave.
     * Keeps Vidplay and BYFMS under one "AniWave" name, trying Vidplay first, then BYFMS.
     */
    suspend fun extractHardSub(
        anilistId: Int,
        episodeNum: Int,
        animeTitle: String? = null
    ): HardSubResult? = withContext(Dispatchers.IO) {
        val res = extractInternal(anilistId, episodeNum, "sub", animeTitle) ?: return@withContext null
        HardSubResult(
            hlsUrl = res.hlsUrl,
            referer = res.referer,
            provider = "AniWave",
            introStart = res.introStart,
            introEnd = res.introEnd,
            outroStart = res.outroStart,
            outroEnd = res.outroEnd,
            subtitleUrl = res.subtitleUrl,
            subtitles = res.subtitles
        )
    }

    /**
     * Extracts English Dub stream from AniWave with soft sub AI Dubtitle track if available.
     * Keeps Vidplay and BYFMS under one "AniWave" name, trying Vidplay first, then BYFMS.
     */
    suspend fun extractDub(
        anilistId: Int,
        episodeNum: Int,
        animeTitle: String? = null
    ): AniwavesResult? = withContext(Dispatchers.IO) {
        extractInternal(anilistId, episodeNum, "dub", animeTitle)
    }

    private suspend fun extractInternal(
        anilistId: Int,
        episodeNum: Int,
        audio: String,
        animeTitle: String?
    ): AniwavesResult? = withContext(Dispatchers.IO) {
        if (anilistId <= 0 && episodeNum <= 0) return@withContext null

        // 1. Try querying Anivexa API watch endpoint first
        for (host in API_CANDIDATE_HOSTS) {
            try {
                val watchUrl = "$host/watch/aniwaves/$anilistId/$audio/aniwaves-$episodeNum"
                val req = Request.Builder()
                    .url(watchUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Accept", "application/json")
                    .build()

                val res = client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                }

                if (res.isNotEmpty() && !res.contains("\"error\"")) {
                    val json = JSONObject(res)
                    val streamsArr = json.optJSONArray("streams")
                    val subsArr = json.optJSONArray("subtitles")
                    val intro = json.optJSONObject("intro")
                    val outro = json.optJSONObject("outro")
                    val iStart = intro?.optLong("start", 0L) ?: 0L
                    val iEnd = intro?.optLong("end", 0L) ?: 0L
                    val oStart = outro?.optLong("start", 0L) ?: 0L
                    val oEnd = outro?.optLong("end", 0L) ?: 0L

                    if (streamsArr != null && streamsArr.length() > 0) {
                        for (i in 0 until streamsArr.length()) {
                            val streamObj = streamsArr.optJSONObject(i) ?: continue
                            val url = streamObj.optString("url", "")
                            val sType = streamObj.optString("type", "")
                            if (url.isNotEmpty() && (sType == "hls" || url.contains(".m3u8"))) {
                                val subsList = mutableListOf<Map<String, String>>()
                                var aiDubtitleUrl = ""
                                var fallbackSubUrl = ""

                                if (subsArr != null) {
                                    for (j in 0 until subsArr.length()) {
                                        val sObj = subsArr.optJSONObject(j) ?: continue
                                        val sUrl = sObj.optString("url", "")
                                        val rawLabel = sObj.optString("label", sObj.optString("lang", "English"))
                                        val isDubtitle = sObj.optBoolean("isDubtitle", false) || rawLabel.contains("Dubtitle", true) || rawLabel.contains("AI", true)
                                        val label = if (audio == "dub" && (isDubtitle || rawLabel.contains("eng", true))) "English (AI Dubtitle)" else rawLabel

                                        if (sUrl.isNotEmpty()) {
                                            subsList.add(mapOf("url" to sUrl, "lang" to label, "label" to label))
                                            if (isDubtitle && aiDubtitleUrl.isEmpty()) {
                                                aiDubtitleUrl = sUrl
                                            } else if (rawLabel.contains("eng", true) && fallbackSubUrl.isEmpty()) {
                                                fallbackSubUrl = sUrl
                                            }
                                        }
                                    }
                                }

                                val chosenSubUrl = if (audio == "dub") (if (aiDubtitleUrl.isNotEmpty()) aiDubtitleUrl else fallbackSubUrl) else ""

                                return@withContext AniwavesResult(
                                    hlsUrl = url,
                                    referer = streamObj.optString("referer", "$BASE_URL/"),
                                    provider = "AniWave",
                                    isHardSub = (audio == "sub"),
                                    subtitleUrl = chosenSubUrl,
                                    subtitles = subsList,
                                    introStart = iStart,
                                    introEnd = iEnd,
                                    outroStart = oStart,
                                    outroEnd = oEnd
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Direct Standalone Extraction from aniwaves.ru
        try {
            var animeId = if (anilistId > 0) idCache[anilistId] else null

            // A. Search if not cached
            if (animeId.isNullOrEmpty()) {
                val titles = getSearchTitles(anilistId, animeTitle)
                if (titles.isEmpty()) return@withContext null

                for (query in titles) {
                    val searchUrl = "$BASE_URL/filter?keyword=${URLEncoder.encode(query, "UTF-8")}"
                    val searchReq = Request.Builder()
                        .url(searchUrl)
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("Referer", "$BASE_URL/")
                        .build()

                    val searchHtml = try {
                        client.newCall(searchReq).execute().use { resp ->
                            if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                        }
                    } catch (_: Exception) { "" }

                    if (searchHtml.isNotEmpty()) {
                        val cardRegex = Pattern.compile("""<a\b[^>]*?href=["'](/watch/[^"']*-(\d+))["'][^>]*>([\s\S]*?)</a>""", Pattern.CASE_INSENSITIVE)
                        val matcher = cardRegex.matcher(searchHtml)

                        while (matcher.find()) {
                            val candidateId = matcher.group(2) ?: ""
                            val linkText = matcher.group(3) ?: ""
                            val cleanText = linkText.replace(Regex("""<[^>]+>"""), " ").trim()

                            if (candidateId.isNotEmpty()) {
                                animeId = candidateId
                                if (cleanText.contains(query, ignoreCase = true) || query.contains(cleanText, ignoreCase = true)) {
                                    break
                                }
                            }
                        }

                        if (animeId.isNullOrEmpty()) {
                            val fallbackRegex = Pattern.compile("""/watch/[a-z0-9-]+-(\d+)""", Pattern.CASE_INSENSITIVE)
                            val fbMatcher = fallbackRegex.matcher(searchHtml)
                            if (fbMatcher.find()) {
                                animeId = fbMatcher.group(1)
                            }
                        }
                    }

                    if (!animeId.isNullOrEmpty()) {
                        if (anilistId > 0) idCache[anilistId] = animeId
                        break
                    }
                }
            }

            if (animeId.isNullOrEmpty()) return@withContext null

            // B. Fetch episode list: /ajax/episode/list/{anime_id}
            val epListUrl = "$BASE_URL/ajax/episode/list/$animeId"
            val epListReq = Request.Builder()
                .url(epListUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", "$BASE_URL/watch/$animeId")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .build()

            val epListJson = try {
                client.newCall(epListReq).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                }
            } catch (_: Exception) { "" }

            if (epListJson.isEmpty()) return@withContext null

            val epResultHtml = JSONObject(epListJson).optString("result", "")
            if (epResultHtml.isEmpty()) return@withContext null

            // Match episode data-ids
            val epTagRegex = Pattern.compile("""<a\b([^>]*?data-ids=["']([^"']+)["'][^>]*?>)""", Pattern.CASE_INSENSITIVE)
            val epMatcher = epTagRegex.matcher(epResultHtml)
            var matchedDataIds: String? = null
            var firstDataIds: String? = null

            while (epMatcher.find()) {
                val fullTag = epMatcher.group(1) ?: ""
                val rawIds = epMatcher.group(2) ?: continue
                val cleanIds = rawIds.replace("&amp;", "&")

                if (firstDataIds == null) firstDataIds = cleanIds

                val numMatcher = Pattern.compile("""data-num=["']?(\d+)["']?""", Pattern.CASE_INSENSITIVE).matcher(fullTag)
                val slugMatcher = Pattern.compile("""data-slug=["']?(\d+)["']?""", Pattern.CASE_INSENSITIVE).matcher(fullTag)

                val num = if (numMatcher.find()) numMatcher.group(1)?.toIntOrNull() else null
                val slug = if (slugMatcher.find()) slugMatcher.group(1)?.toIntOrNull() else null

                if (num == episodeNum || slug == episodeNum) {
                    matchedDataIds = cleanIds
                    break
                }
            }

            val targetDataIds = matchedDataIds ?: (if (episodeNum == 1) firstDataIds else null) ?: return@withContext null

            // C. Fetch server list: /ajax/server/list?servers={data_ids}
            val serverUrl = "$BASE_URL/ajax/server/list?servers=$targetDataIds"
            val serverReq = Request.Builder()
                .url(serverUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", "$BASE_URL/watch/$animeId")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .build()

            val serverJson = try {
                client.newCall(serverReq).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                }
            } catch (_: Exception) { "" }

            if (serverJson.isEmpty()) return@withContext null

            val serverHtml = JSONObject(serverJson).optString("result", "")
            if (serverHtml.isEmpty()) return@withContext null

            // D. Filter servers by audio section (sub or dub)
            val targetSection = if (serverHtml.contains("""data-type="$audio"""")) {
                serverHtml.substringAfter("""data-type="$audio"""").substringBefore("""<div class="type"""")
            } else serverHtml

            val linkIdRegex = Pattern.compile("""<li\b[^>]*?data-link-id=["']([^"']+)["'][^>]*>([\s\S]*?)</li>""", Pattern.CASE_INSENSITIVE)
            val linkMatcher = linkIdRegex.matcher(targetSection)

            var vidplayLinkId: String? = null
            var byfmsLinkId: String? = null

            while (linkMatcher.find()) {
                val fullLi = linkMatcher.group(0) ?: ""
                val lId = linkMatcher.group(1) ?: continue
                val sName = linkMatcher.group(2)?.trim() ?: ""

                if (sName.contains("Vidplay", ignoreCase = true) || fullLi.contains("""data-sv-id="4"""")) {
                    vidplayLinkId = lId
                } else if (sName.contains("BYFMS", ignoreCase = true) || fullLi.contains("""data-sv-id="1"""")) {
                    byfmsLinkId = lId
                }
            }

            // Fallback order: try BYFMS (Byse) first as it is AniWave's dedicated server, then Vidplay
            val candidateLinkIds = listOfNotNull(byfmsLinkId, vidplayLinkId)
            if (candidateLinkIds.isEmpty()) return@withContext null

            for (linkId in candidateLinkIds) {
                try {
                    val sourcesUrl = "$BASE_URL/ajax/sources?id=${URLEncoder.encode(linkId, "UTF-8")}&asi=0&autoPlay=0"
                    val sourcesReq = Request.Builder()
                        .url(sourcesUrl)
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("Referer", "$BASE_URL/watch/$animeId")
                        .addHeader("X-Requested-With", "XMLHttpRequest")
                        .build()

                    val sourcesJson = client.newCall(sourcesReq).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                    }

                    if (sourcesJson.isEmpty()) continue

                    val sourcesObj = JSONObject(sourcesJson).optJSONObject("result") ?: continue
                    val embedUrl = sourcesObj.optString("url", "")
                    if (embedUrl.isEmpty()) continue

                    val skipData = sourcesObj.optJSONObject("skip_data")
                    val introArr = skipData?.optJSONArray("intro")
                    val outroArr = skipData?.optJSONArray("outro")
                    val introStart = introArr?.optLong(0, 0L) ?: 0L
                    val introEnd = introArr?.optLong(1, 0L) ?: 0L
                    val outroStart = outroArr?.optLong(0, 0L) ?: 0L
                    val outroEnd = outroArr?.optLong(1, 0L) ?: 0L

                    // Resolve embedUrl to actual HLS stream
                    var streamHls: String? = null
                    var streamReferer: String = "$BASE_URL/"
                    val subsList = mutableListOf<Map<String, String>>()
                    var aiDubtitleUrl = ""
                    var fallbackSubUrl = ""

                    // 1. If embedUrl is Byse / BYFMS (mfw*.org, byse.sx, bysesayeveum.com, gn1r5n.org)
                    if (ByseExtractor.canExtract(embedUrl)) {
                        try {
                            val byseStreams = ByseExtractor.extract(embedUrl, referer = "$BASE_URL/watch/$animeId")
                            if (byseStreams.isNotEmpty()) {
                                streamHls = byseStreams[0]
                                streamReferer = "https://aniwaves.ru/"
                            }
                        } catch (_: Exception) {}
                    }

                    // 2. If embedUrl is Vidplay (play.echovideo.ru)
                    if (streamHls == null && embedUrl.contains("echovideo.ru")) {
                        try {
                            val id = embedUrl.substringAfterLast("/")
                            val type = if (embedUrl.contains("embed-1")) "embed-1" else "embed-0"
                            val getSourcesUrl = "https://play.echovideo.ru/$type/getSources?id=$id"
                            val vsReq = Request.Builder()
                                .url(getSourcesUrl)
                                .addHeader("User-Agent", USER_AGENT)
                                .addHeader("Referer", embedUrl)
                                .addHeader("X-Requested-With", "XMLHttpRequest")
                                .build()

                            val vsResp = client.newCall(vsReq).execute().use { resp ->
                                if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                            }
                            if (vsResp.isNotEmpty()) {
                                val vsJson = JSONObject(vsResp)
                                val sourcesArr = vsJson.optJSONArray("sources")
                                if (sourcesArr != null && sourcesArr.length() > 0) {
                                    val firstSrc = sourcesArr.opt(0)
                                    val srcUrl = if (firstSrc is JSONObject) firstSrc.optString("file", firstSrc.optString("url", "")) else firstSrc.toString()
                                    if (srcUrl.isNotEmpty()) {
                                        streamHls = srcUrl
                                        streamReferer = "https://play.echovideo.ru/"
                                    }
                                }
                                val tracksArr = vsJson.optJSONArray("tracks")
                                if (tracksArr != null) {
                                    for (tIdx in 0 until tracksArr.length()) {
                                        val tObj = tracksArr.optJSONObject(tIdx) ?: continue
                                        val tFile = tObj.optString("file", "")
                                        val rawLabel = tObj.optString("label", tObj.optString("name", "English"))
                                        val isDubtitle = rawLabel.contains("Dubtitle", true) || rawLabel.contains("AI", true)
                                        val label = if (audio == "dub" && (isDubtitle || rawLabel.contains("eng", true))) "English (AI Dubtitle)" else rawLabel
                                        if (tFile.isNotEmpty()) {
                                            subsList.add(mapOf("url" to tFile, "lang" to label, "label" to label))
                                            if (isDubtitle && aiDubtitleUrl.isEmpty()) aiDubtitleUrl = tFile
                                            else if (rawLabel.contains("eng", true) && fallbackSubUrl.isEmpty()) fallbackSubUrl = tFile
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    // 3. If stream is direct m3u8 in embedUrl
                    if (streamHls == null && embedUrl.contains(".m3u8")) {
                        streamHls = embedUrl
                    }

                    if (streamHls != null && streamHls.isNotEmpty()) {
                        val chosenSubUrl = if (audio == "dub") (if (aiDubtitleUrl.isNotEmpty()) aiDubtitleUrl else fallbackSubUrl) else ""
                        return@withContext AniwavesResult(
                            hlsUrl = streamHls,
                            referer = streamReferer,
                            provider = "AniWave",
                            isHardSub = (audio == "sub"),
                            subtitleUrl = chosenSubUrl,
                            subtitles = subsList,
                            introStart = introStart,
                            introEnd = introEnd,
                            outroStart = outroStart,
                            outroEnd = outroEnd
                        )
                    }
                } catch (_: Exception) {}
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
