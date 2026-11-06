package com.lagradost.cloudstream3.ui.animebox.extractors

import android.content.Context
import com.lagradost.cloudstream3.ui.animebox.api.AniListClient
import com.lagradost.cloudstream3.ui.animebox.api.AniZipClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

data class AnimePaheResult(
    val hlsUrl: String,
    val referer: String = "https://kwik.cx/",
    val provider: String = "AnimePahe",
    val isHardSub: Boolean = true
)

/**
 * 1:1 port of Anilili's AnimePahe provider:
 * - F4.C0572xa (AnimePahe stream resolution)
 * - F4.C0506sa (AnimePahe parser + page-1 episode offset normalization)
 * - X3.l (AnimePahe session & release pagination)
 * - F4.C0437na (PaheBridge WebView clearance + in-page XHR + Kwik embed loader)
 */
object AnimePaheExtractor {

    private const val USER_AGENT = PaheWebViewBridge.USER_AGENT
    private const val BASE_URL = "https://animepahe.pw"

    private val animeSessionCache = ConcurrentHashMap<Int, String>()

    private fun getAppContext(context: Context?): Context? {
        if (context != null) return context.applicationContext
        return try {
            val atClass = Class.forName("android.app.ActivityThread")
            val app = atClass.getMethod("currentApplication").invoke(null) as? android.app.Application
            app?.applicationContext
        } catch (_: Throwable) {
            null
        }
    }

    private fun cleanTitle(raw: String): String {
        var s = raw.replace(Regex("""[\(\[\{].*?[\)\]\}]"""), " ")
        s = s.replace(
            Regex(
                """:\s*(?:The\s+Series|Season\s*\d+|Part\s*\d+|2nd\s*Season|\d+(?:st|nd|rd|th)\s*Season).*""",
                RegexOption.IGNORE_CASE
            ),
            " "
        )
        s = s.replace('é', 'e').replace('É', 'E')
        s = s.replace(Regex("""[^a-zA-Z0-9\s]"""), " ")
        return s.trim().split(Regex("""\s+""")).joinToString(" ")
    }

    private suspend fun getSearchTitles(anilistId: Int, animeTitle: String?): List<String> {
        val list = mutableListOf<String>()
        if (anilistId > 0) {
            try {
                val details = AniListClient.getAnimeDetails(anilistId)
                if (details != null) {
                    val media = JSONObject(details).optJSONObject("data")?.optJSONObject("Media")
                    val t = media?.optJSONObject("title")
                    t?.optString("romaji", "")?.takeIf { it.isNotBlank() }?.let {
                        list.add(it.trim())
                        val c = cleanTitle(it)
                        if (c.length >= 2 && !list.contains(c)) list.add(c)
                    }
                    t?.optString("english", "")?.takeIf { it.isNotBlank() }?.let {
                        if (!list.contains(it.trim())) list.add(it.trim())
                        val c = cleanTitle(it)
                        if (c.length >= 2 && !list.contains(c)) list.add(c)
                    }
                    t?.optString("native", "")?.takeIf { it.isNotBlank() }?.let {
                        if (!list.contains(it.trim())) list.add(it.trim())
                    }
                }
            } catch (_: Exception) {
            }

            try {
                val aniZip = AniZipClient.getAniZipJson(anilistId)
                val titlesObj = aniZip?.optJSONObject("titles")
                if (titlesObj != null) {
                    for (k in listOf("x-jat", "en", "ja")) {
                        titlesObj.optString(k, "").takeIf { it.isNotBlank() }?.let {
                            if (!list.contains(it.trim())) list.add(it.trim())
                            val c = cleanTitle(it)
                            if (c.length >= 2 && !list.contains(c)) list.add(c)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        if (!animeTitle.isNullOrBlank()) {
            if (!list.contains(animeTitle.trim())) list.add(animeTitle.trim())
            val c = cleanTitle(animeTitle)
            if (c.length >= 2 && !list.contains(c)) list.add(c)
        }
        return list.distinct()
    }

    fun unpackJs(script: String): String? = AnililiHttpClient.unpackJs(script)

    /**
     * Fetches a relative path from https://animepahe.pw.
     * Tries direct HTTP with AppDns + CookieManager cookies first, and falls back to PaheWebViewBridge
     * (1:1 with X3.l.q -> F4.C0437na.g) when DDoS-Guard / Cloudflare challenges direct HTTP.
     */
    private suspend fun fetchPahePath(context: Context?, path: String): String? {
        val isApi = path.startsWith("/api")
        val fullUrl = "$BASE_URL$path"

        try {
            val cookieHeader = runCatching {
                android.webkit.CookieManager.getInstance().getCookie(BASE_URL)
            }.getOrNull()

            val reqBuilder = Request.Builder()
                .url(fullUrl)
                .header("User-Agent", USER_AGENT)
                .header(
                    "Accept",
                    if (isApi) "application/json, text/javascript, */*; q=0.01"
                    else "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
                )
                .header("Referer", "$BASE_URL/")
            if (isApi) {
                reqBuilder.header("X-Requested-With", "XMLHttpRequest")
            }
            if (!cookieHeader.isNullOrBlank()) {
                reqBuilder.header("Cookie", cookieHeader)
            }

            val directBody = AnililiHttpClient.client.newCall(reqBuilder.get().build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
            if (!directBody.isNullOrBlank()) {
                val trimmed = directBody.trimStart()
                val isChallenge = trimmed.contains("DDoS-Guard", ignoreCase = true) ||
                    trimmed.contains("Just a moment", ignoreCase = true)
                if (!isChallenge && (!isApi || trimmed.startsWith("{"))) {
                    return directBody
                }
            }
        } catch (_: Exception) {
        }

        val appCtx = getAppContext(context) ?: return null
        val bridgeResp = PaheWebViewBridge.get(appCtx, path)
        return bridgeResp?.body?.takeIf { it.isNotBlank() }
    }

    private suspend fun fetchKwikPage(context: Context?, kwikUrl: String): String? {
        try {
            val cookieHeader = runCatching {
                android.webkit.CookieManager.getInstance().getCookie(kwikUrl)
            }.getOrNull()
            val reqBuilder = Request.Builder()
                .url(kwikUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            if (!cookieHeader.isNullOrBlank()) {
                reqBuilder.header("Cookie", cookieHeader)
            }
            val directHtml = AnililiHttpClient.client.newCall(reqBuilder.get().build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
            if (!directHtml.isNullOrBlank() &&
                (AnililiHttpClient.isPackedJs(directHtml) || directHtml.contains(".m3u8"))
            ) {
                return directHtml
            }
        } catch (_: Exception) {
        }

        val appCtx = getAppContext(context) ?: return null
        return PaheWebViewBridge.fetchKwikHtml(appCtx, kwikUrl, "$BASE_URL/")
    }

    private fun extractIdsFromHtml(pageHtml: String): Pair<Int, Int> {
        var candidateAnilistId = 0
        var candidateMalId = 0

        val anilistLinkM = Pattern.compile("""anilist\.co/anime/(\d+)""", Pattern.CASE_INSENSITIVE).matcher(pageHtml)
        if (anilistLinkM.find()) {
            candidateAnilistId = anilistLinkM.group(1)?.toIntOrNull() ?: 0
        } else {
            val anilistM = Pattern.compile(
                """<meta\b[^>]*\bname=["']anilist["'][^>]*\bcontent=["'](\d+)["']""",
                Pattern.CASE_INSENSITIVE
            ).matcher(pageHtml)
            if (anilistM.find()) candidateAnilistId = anilistM.group(1)?.toIntOrNull() ?: 0
        }

        val malLinkM = Pattern.compile("""myanimelist\.net/anime/(\d+)""", Pattern.CASE_INSENSITIVE).matcher(pageHtml)
        if (malLinkM.find()) {
            candidateMalId = malLinkM.group(1)?.toIntOrNull() ?: 0
        } else {
            val malM = Pattern.compile(
                """<meta\b[^>]*\bname=["']mal["'][^>]*\bcontent=["'](\d+)["']""",
                Pattern.CASE_INSENSITIVE
            ).matcher(pageHtml)
            if (malM.find()) candidateMalId = malM.group(1)?.toIntOrNull() ?: 0
        }
        return candidateAnilistId to candidateMalId
    }

    /**
     * 1:1 port of X3.l.k(Media) — resolves the AnimePahe session UUID for an AniList ID.
     */
    private suspend fun resolveAnimeSession(
        context: Context?,
        anilistId: Int,
        animeTitle: String?
    ): String? = withContext(Dispatchers.IO) {
        if (anilistId > 0) {
            animeSessionCache[anilistId]?.let { return@withContext it }
        }

        val searchTitles = getSearchTitles(anilistId, animeTitle)
        val malId = if (anilistId > 0) MalIdResolver.getMalId(anilistId) else 0

        data class Candidate(val session: String, val title: String)
        val seenSessions = HashSet<String>()
        val allCandidates = mutableListOf<Candidate>()

        for (q in searchTitles) {
            try {
                val encodedQ = URLEncoder.encode(q, "UTF-8")
                val searchBody = fetchPahePath(context, "/api?m=search&l=8&q=$encodedQ") ?: continue
                if (!searchBody.trimStart().startsWith("{")) continue

                val json = JSONObject(searchBody)
                val dataArr = json.optJSONArray("data") ?: continue
                for (i in 0 until dataArr.length()) {
                    val candidate = dataArr.optJSONObject(i) ?: continue
                    val session = candidate.optString("session", "").trim()
                    if (session.isEmpty() || !seenSessions.add(session)) continue
                    val title = candidate.optString("title", "").trim()
                    allCandidates.add(Candidate(session, title))

                    val pageHtml = fetchPahePath(context, "/anime/$session") ?: continue
                    val (candAnilist, candMal) = extractIdsFromHtml(pageHtml)
                    if ((anilistId > 0 && candAnilist == anilistId) ||
                        (malId > 0 && candMal == malId)
                    ) {
                        if (anilistId > 0) animeSessionCache[anilistId] = session
                        return@withContext session
                    }
                }
            } catch (_: Exception) {
            }
        }

        // Title-similarity fallback if anime page meta tags didn't match
        if (allCandidates.isNotEmpty() && searchTitles.isNotEmpty()) {
            val targetClean = cleanTitle(searchTitles.first()).lowercase()
            val best = allCandidates.firstOrNull {
                cleanTitle(it.title).lowercase() == targetClean
            } ?: allCandidates.first()
            if (anilistId > 0) animeSessionCache[anilistId] = best.session
            return@withContext best.session
        }

        null
    }

    private data class ReleasePage(
        val total: Int,
        val perPage: Int,
        val lastPage: Int,
        val episodes: Map<Int, String>,
        val episodeOffset: Int
    )

    /**
     * 1:1 port of F4.C0506sa.d(element, offset):
     * Normalizes continuous episode numbers on Season 2+ releases by subtracting (minEpOnPage1 - 1).
     */
    private fun parseReleasePage(body: String, knownOffset: Int?): ReleasePage? {
        if (!body.trimStart().startsWith("{")) return null
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val dataArr = json.optJSONArray("data")
        val rawMap = LinkedHashMap<Int, String>()
        if (dataArr != null) {
            for (i in 0 until dataArr.length()) {
                val item = dataArr.optJSONObject(i) ?: continue
                val ep = item.optInt("episode", -1)
                val session = item.optString("session", "").trim()
                if (ep >= 0 && session.isNotEmpty()) {
                    rawMap[ep] = session
                }
            }
        }
        val offset = knownOffset ?: run {
            val minEp = rawMap.keys.minOrNull() ?: 1
            (minEp - 1).coerceAtLeast(0)
        }
        val normalizedMap = LinkedHashMap<Int, String>(rawMap.size)
        for ((ep, session) in rawMap) {
            normalizedMap[ep - offset] = session
        }
        val total = json.optInt("total", normalizedMap.size)
        val perPage = json.optInt("per_page", normalizedMap.size.coerceAtLeast(30)).coerceAtLeast(1)
        val lastPage = json.optInt("last_page", 1).coerceAtLeast(1)
        return ReleasePage(total, perPage, lastPage, normalizedMap, offset)
    }

    /**
     * 1:1 port of X3.l.a(session, episode):
     * Finds the episode session across paginated release responses.
     */
    private suspend fun resolveEpisodeSession(
        context: Context?,
        animeSession: String,
        episodeNum: Int
    ): String? {
        val page1Body = fetchPahePath(
            context,
            "/api?m=release&id=$animeSession&sort=episode_asc&page=1"
        ) ?: return null
        val page1 = parseReleasePage(page1Body, knownOffset = null) ?: return null
        page1.episodes[episodeNum]?.let { return it }

        if (page1.lastPage <= 1) return null

        val guessedPage = (((episodeNum - 1).coerceAtLeast(0)) / page1.perPage + 1)
            .coerceIn(1, page1.lastPage)
        val visited = HashSet<Int>().apply { add(1) }

        if (visited.add(guessedPage)) {
            val guessedBody = fetchPahePath(
                context,
                "/api?m=release&id=$animeSession&sort=episode_asc&page=$guessedPage"
            )
            if (guessedBody != null) {
                val parsed = parseReleasePage(guessedBody, page1.episodeOffset)
                parsed?.episodes?.get(episodeNum)?.let { return it }
            }
        }

        for (p in 2..page1.lastPage) {
            if (!visited.add(p)) continue
            val body = fetchPahePath(
                context,
                "/api?m=release&id=$animeSession&sort=episode_asc&page=$p"
            ) ?: continue
            val parsed = parseReleasePage(body, page1.episodeOffset) ?: continue
            parsed.episodes[episodeNum]?.let { return it }
        }
        return null
    }

    suspend fun extract(
        anilistId: Int,
        episodeNum: Int,
        animeTitle: String? = null,
        mode: String = "sub",
        context: Context? = null
    ): AnimePaheResult? = withContext(Dispatchers.IO) {
        if (anilistId <= 0 && animeTitle.isNullOrBlank()) return@withContext null
        val wantDub = mode.equals("dub", ignoreCase = true)

        // Fast-path: Miruro's "kiwi" pipe IS provider="animepahe" (direct uwucdn.top / kwik.cx HLS without DDoS-Guard challenges)
        if (anilistId > 0) {
            try {
                val kiwiRes = MiruroKiwiExtractor.extract(context, anilistId, episodeNum, if (wantDub) "dub" else "sub", "kiwi")
                if (kiwiRes != null && kiwiRes.hlsUrl.isNotEmpty()) {
                    return@withContext AnimePaheResult(
                        hlsUrl = kiwiRes.hlsUrl,
                        referer = kiwiRes.referer.ifEmpty { "https://kwik.cx/" },
                        provider = if (wantDub) "AnimePahe (Dub)" else "AnimePahe",
                        isHardSub = !wantDub
                    )
                }
            } catch (_: Exception) {
            }
        }

        val animeSession = resolveAnimeSession(context, anilistId, animeTitle) ?: return@withContext null
        val epSession = resolveEpisodeSession(context, animeSession, episodeNum) ?: return@withContext null

        // Step 2: Fetch play page and extract Kwik embed buttons (F4.C0572xa + F4.C0506sa)
        val playHtml = fetchPahePath(context, "/play/$animeSession/$epSession") ?: return@withContext null

        data class KwikButton(
            val src: String,
            val audio: String,
            val resolution: Int,
            val isAv1: Boolean
        )

        val buttonRegex = Pattern.compile("""<button\b([^>]*\bdata-src\s*=[^>]*)>""", Pattern.CASE_INSENSITIVE)
        val buttonMatcher = buttonRegex.matcher(playHtml)
        val parsedButtons = mutableListOf<KwikButton>()

        while (buttonMatcher.find()) {
            val btnTag = buttonMatcher.group(1) ?: continue
            val srcM = Pattern.compile("""\bdata-src\s*=\s*["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(btnTag)
            if (!srcM.find()) continue
            val src = (srcM.group(1) ?: "").trim()
            if (!src.contains("kwik", ignoreCase = true)) continue

            val audioM = Pattern.compile("""\bdata-audio\s*=\s*["']([^"']*)["']""", Pattern.CASE_INSENSITIVE).matcher(btnTag)
            val audioVal = if (audioM.find()) (audioM.group(1) ?: "").trim().lowercase() else "jpn"

            val resM = Pattern.compile("""\bdata-resolution\s*=\s*["'](\d+)["']""", Pattern.CASE_INSENSITIVE).matcher(btnTag)
            val resolution = if (resM.find()) (resM.group(1)?.toIntOrNull() ?: 720) else 720

            val av1M = Pattern.compile("""\bdata-av1\s*=\s*["']([^"']*)["']""", Pattern.CASE_INSENSITIVE).matcher(btnTag)
            val av1Str = if (av1M.find()) (av1M.group(1) ?: "").trim() else "0"
            val isAv1 = av1Str == "1" || av1Str.equals("true", ignoreCase = true)

            parsedButtons.add(KwikButton(src, audioVal, resolution, isAv1))
        }

        // Exact filter from F4.C0572xa line 170: (!audio.equals("jpn")) == wantDub
        val matchingAudioButtons = parsedButtons.filter { (!it.audio.equals("jpn", ignoreCase = true)) == wantDub }
        if (matchingAudioButtons.isEmpty() && wantDub) {
            return@withContext null
        }
        val effectiveButtons = if (matchingAudioButtons.isNotEmpty()) matchingAudioButtons else parsedButtons

        // Sort: prefer eng when dub, prefer non-AV1, then highest resolution (F4.C0572xa lines 285-322)
        val kwikCandidates = effectiveButtons
            .sortedWith(
                compareByDescending<KwikButton> { wantDub && it.audio == "eng" }
                    .thenBy { it.isAv1 }
                    .thenByDescending { it.resolution }
            )
            .map { it.src }
            .distinct()
            .toMutableList()

        if (kwikCandidates.isEmpty() && !wantDub) {
            val urlM = Pattern.compile("""let\s+url\s*=\s*["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(playHtml)
            if (urlM.find()) {
                val u = urlM.group(1) ?: ""
                if (u.contains("kwik", ignoreCase = true)) {
                    kwikCandidates.add(u)
                }
            }
        }

        if (kwikCandidates.isEmpty()) return@withContext null

        // Step 3: Fetch Kwik embed and unpack stream URL (F4.C0572xa lines 369-459)
        val streamUrlPattern = Pattern.compile("""https?://[^\s'"\\]+\.m3u8[^\s'"\\]*""", Pattern.CASE_INSENSITIVE)
        for (kwikUrl in kwikCandidates) {
            try {
                val kwikHtml = fetchKwikPage(context, kwikUrl) ?: continue
                val unpacked = if (AnililiHttpClient.isPackedJs(kwikHtml)) {
                    AnililiHttpClient.unpackJs(kwikHtml) ?: kwikHtml
                } else {
                    kwikHtml
                }

                val m = streamUrlPattern.matcher(unpacked)
                val streamUrl = if (m.find()) {
                    m.group()
                } else {
                    val m2 = streamUrlPattern.matcher(kwikHtml.replace("""\/""", "/"))
                    if (m2.find()) m2.group() else null
                }

                if (!streamUrl.isNullOrEmpty() && streamUrl.startsWith("http")) {
                    val refererHost = try {
                        val uri = URI(kwikUrl)
                        "${uri.scheme}://${uri.authority}/"
                    } catch (_: Exception) {
                        "https://kwik.cx/"
                    }

                    return@withContext AnimePaheResult(
                        hlsUrl = streamUrl,
                        referer = refererHost,
                        provider = if (wantDub) "AnimePahe (Dub)" else "AnimePahe",
                        isHardSub = !wantDub
                    )
                }
            } catch (_: Exception) {
            }
        }

        null
    }
}
