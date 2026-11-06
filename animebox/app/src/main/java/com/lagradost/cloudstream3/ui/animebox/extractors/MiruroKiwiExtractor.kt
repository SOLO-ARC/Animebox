package com.lagradost.cloudstream3.ui.animebox.extractors

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream

/**
 * 1:1 port of Anilili's Miruro Catalog Pipe for "kiwi" (and other Miruro pipe providers:
 * bee, sun, pewe, hop, ally, kiwi, moo, bonk, etc.)
 *
 * Classes ported from Anilili-project:
 * - F4.AbstractC0571x9 (Miruro mirrors)
 * - F4.C0558w9 (MiruroCatalogApi HTTP client + "miruro/catalog" XOR + GZIP decoder)
 * - F4.Ia (MiruroCatalogRepository: config, v1/anime, v1/anime/{id}/episodes/{ep}/play)
 * - F4.C0519t9 (MiruroCatalogParser)
 * - F4.A9 & D7.o.Z (Embed resolver for kiwi / kwik / packed JS embeds)
 */
object MiruroKiwiExtractor {

    private val MIRRORS = listOf(
        "https://www.miruro.cx",
        "https://www.miruro.tv",
        "https://www.miruro.to",
        "https://www.miruro.bz",
        "https://www.miruro.ru"
    )

    private val XOR_KEY = "miruro/catalog".toByteArray(Charsets.UTF_8)
    private const val MIRURO_UA = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    private const val EMBED_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    @Volatile
    private var preferredMirror: String? = null

    data class ProviderConfig(
        val key: String,
        val label: String,
        val visible: Boolean,
        val parent: String?,
        val isEmbed: Boolean,
        val downloadWorker: String?
    )

    data class SkipWindow(
        val introStart: Long = 0L,
        val introEnd: Long = 0L,
        val outroStart: Long = 0L,
        val outroEnd: Long = 0L
    )

    @Volatile
    private var cachedConfig: Pair<Long, List<ProviderConfig>>? = null
    private val titleIdCache = ConcurrentHashMap<Int, String>()
    private val episodeSkipCache = ConcurrentHashMap<String, Map<Int, SkipWindow>>()
    private val playCache = ConcurrentHashMap<String, Pair<Long, JSONObject>>()

    private fun decodeOctetStream(bytes: ByteArray): String {
        val xored = ByteArray(bytes.size)
        for (i in bytes.indices) {
            xored[i] = (bytes[i].toInt() xor XOR_KEY[i % XOR_KEY.size].toInt()).toByte()
        }
        return GZIPInputStream(ByteArrayInputStream(xored)).use { gzip ->
            gzip.readBytes().toString(Charsets.UTF_8)
        }
    }

    private fun requestMirror(
        baseUrl: String,
        path: String,
        query: Map<String, String>
    ): String {
        val rawUrl = "$baseUrl/api/$path"
        val urlBuilder = rawUrl.toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalArgumentException("Invalid URL: $rawUrl")
        for ((k, v) in query) {
            urlBuilder.addQueryParameter(k, v)
        }
        val req = Request.Builder()
            .url(urlBuilder.build())
            .header("User-Agent", MIRURO_UA)
            .header("Referer", "$baseUrl/")
            .header("Sec-Fetch-Site", "same-origin")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Dest", "empty")
            .header("Accept", "application/json, application/octet-stream;q=0.9, */*;q=0.8")
            .get()
            .build()

        AnililiHttpClient.client.newCall(req).execute().use { resp ->
            val contentType = resp.header("Content-Type").orEmpty().substringBefore(';').trim()
            val bytes = resp.body?.bytes() ?: ByteArray(0)
            if (resp.isSuccessful) {
                return if (contentType.equals("application/octet-stream", ignoreCase = true)) {
                    decodeOctetStream(bytes)
                } else {
                    bytes.toString(Charsets.UTF_8)
                }
            }
            throw RuntimeException("Miruro HTTP ${resp.code} on $path")
        }
    }

    private fun callMiruroApi(path: String, query: Map<String, String> = emptyMap()): String? {
        val cur = preferredMirror
        val orderedMirrors = if (cur != null) {
            listOf(cur) + MIRRORS.filter { it != cur }
        } else {
            MIRRORS
        }
        for (mirror in orderedMirrors) {
            try {
                val res = requestMirror(mirror, path, query)
                if (preferredMirror != mirror) {
                    preferredMirror = mirror
                }
                return res
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun getConfig(): List<ProviderConfig> {
        cachedConfig?.let { (ts, list) ->
            if (SystemClock.elapsedRealtime() - ts < 1_800_000L && list.isNotEmpty()) {
                return list
            }
        }
        val text = callMiruroApi("config") ?: return emptyList()
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return emptyList()
        val streaming = root.optJSONObject("streaming") ?: return emptyList()

        val result = mutableListOf<ProviderConfig>()
        val keys = streaming.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val obj = streaming.optJSONObject(key) ?: continue
            val label = obj.optString("label", "").trim().ifEmpty { key }
            val visible = if (obj.has("visible")) obj.optBoolean("visible", true) else true
            val parent = obj.optString("parent", "").trim().takeIf { it.isNotEmpty() }
            val isEmbed = obj.optString("relationship", "").equals("embed", ignoreCase = true)
            val dlWorker = obj.optString("downloadWorker", "").trim().takeIf { it.isNotEmpty() }
            result.add(ProviderConfig(key, label, visible, parent, isEmbed, dlWorker))
        }
        if (result.isNotEmpty()) {
            cachedConfig = SystemClock.elapsedRealtime() to result
        }
        return result
    }

    private fun getMiruroTitleId(anilistId: Int): String? {
        titleIdCache[anilistId]?.let { return it }
        val text = callMiruroApi(
            "v1/anime",
            mapOf("anilist_id_in" to anilistId.toString(), "limit" to "100")
        ) ?: return null
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val data = root.optJSONArray("data") ?: return null
        val first = data.optJSONObject(0) ?: return null
        val id = first.optString("id", "").trim().takeIf { it.isNotEmpty() } ?: return null
        titleIdCache[anilistId] = id
        return id
    }

    private fun getEpisodeSkipTimes(titleId: String, episode: Int): SkipWindow {
        episodeSkipCache[titleId]?.get(episode)?.let { return it }
        try {
            val text = callMiruroApi("v1/anime/$titleId/episodes", mapOf("limit" to "10000")) ?: return SkipWindow()
            val root = runCatching { JSONObject(text) }.getOrNull() ?: return SkipWindow()
            val data = root.optJSONArray("data") ?: return SkipWindow()
            val map = HashMap<Int, SkipWindow>()
            for (i in 0 until data.length()) {
                val epObj = data.optJSONObject(i) ?: continue
                val kind = epObj.optString("kind", "regular")
                if (!kind.equals("regular", ignoreCase = true)) continue
                val epNum = epObj.optDouble("episode_number", -1.0).toInt()
                if (epNum <= 0) continue
                val skips = epObj.optJSONArray("skip_times") ?: continue
                var opStart = 0L
                var opEnd = 0L
                var edStart = 0L
                var edEnd = 0L
                for (j in 0 until skips.length()) {
                    val sObj = skips.optJSONObject(j) ?: continue
                    val sKind = sObj.optString("kind", "").lowercase()
                    val start = sObj.optDouble("start_seconds", 0.0).toLong()
                    val end = sObj.optDouble("end_seconds", 0.0).toLong()
                    if (end > start) {
                        if ((sKind == "op" || sKind == "mixed_op") && opEnd == 0L) {
                            opStart = start
                            opEnd = end
                        } else if ((sKind == "ed" || sKind == "mixed_ed") && edEnd == 0L) {
                            edStart = start
                            edEnd = end
                        }
                    }
                }
                map[epNum] = SkipWindow(opStart, opEnd, edStart, edEnd)
            }
            episodeSkipCache[titleId] = map
            return map[episode] ?: SkipWindow()
        } catch (_: Exception) {
            return SkipWindow()
        }
    }

    private fun getPlayJson(titleId: String, episode: Int): JSONObject? {
        val key = "$titleId:$episode"
        playCache[key]?.let { (ts, json) ->
            if (SystemClock.elapsedRealtime() - ts < 120_000L) {
                return json
            }
        }
        val text = callMiruroApi("v1/anime/$titleId/episodes/$episode/play") ?: return null
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        playCache[key] = SystemClock.elapsedRealtime() to json
        return json
    }

    private fun extractEmbedUrl(obj: JSONObject): String? {
        val embed = obj.opt("embed") ?: return null
        val url = when (embed) {
            is JSONObject -> embed.optString("url", "")
            is String -> embed
            else -> ""
        }.trim()
        return url.takeIf { it.isNotEmpty() }
    }

    private fun scoreStreamUrl(url: String): Int {
        val hasQuery = if (url.substringAfter('?', "").isNotEmpty()) 2 else 0
        val isM3u8 = if (url.substringBefore('?').endsWith(".m3u8", ignoreCase = true)) 1 else 0
        return hasQuery + isM3u8
    }

    /**
     * 1:1 port of F4.A9.b + D7.o.Z:
     * Resolves an embed URL (e.g. Kwik / Kiwi embed) into an HLS .m3u8 URL + optional subtitle.
     */
    private suspend fun resolveEmbedStream(
        context: Context?,
        embedUrl: String,
        referer: String?
    ): Pair<String, Pair<String, String>?>? {
        val html = try {
            val reqBuilder = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", EMBED_UA)
                .header("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
            if (!referer.isNullOrBlank()) {
                reqBuilder.header("Referer", referer)
            }
            AnililiHttpClient.client.newCall(reqBuilder.get().build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) {
            null
        } ?: if (context != null && embedUrl.contains("kwik.", ignoreCase = true)) {
            PaheWebViewBridge.fetchKwikHtml(context, embedUrl, referer ?: "https://animepahe.pw/")
        } else null

        if (html.isNullOrBlank()) return null

        var streamUrl: String? = null
        if (AnililiHttpClient.isPackedJs(html)) {
            val unpacked = AnililiHttpClient.unpackJs(html)
            if (unpacked != null) {
                val linksMatch = Regex("""\blinks\s*=\s*(\{[^{}]*\})""").find(unpacked)
                val linksJsonStr = linksMatch?.groupValues?.getOrNull(1)
                if (linksJsonStr != null) {
                    val linksObj = runCatching { JSONObject(linksJsonStr) }.getOrNull()
                    if (linksObj != null) {
                        val candidates = mutableListOf<String>()
                        val keys = linksObj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val v = linksObj.optString(k, "").trim()
                            if (v.startsWith("http", ignoreCase = true)) {
                                candidates.add(v)
                            }
                        }
                        streamUrl = candidates.maxByOrNull { scoreStreamUrl(it) }
                    }
                }
                if (streamUrl == null) {
                    streamUrl = Regex("""["'](https?://[^"'\s]+\.m3u8[^"'\s]*)["']""")
                        .find(unpacked)?.groupValues?.getOrNull(1)
                }
            }
        }

        if (streamUrl == null) {
            streamUrl = Regex("""["'](https?://[^"'\s]+\.m3u8[^"'\s]*)["']""")
                .find(html)?.groupValues?.getOrNull(1)
        }

        // If direct HTTP got blocked by Kwik Cloudflare, try PaheWebViewBridge fallback
        if (streamUrl == null && context != null && embedUrl.contains("kwik.", ignoreCase = true)) {
            val wvHtml = PaheWebViewBridge.fetchKwikHtml(context, embedUrl, referer ?: "https://animepahe.pw/")
            if (!wvHtml.isNullOrBlank()) {
                val unpacked = if (AnililiHttpClient.isPackedJs(wvHtml)) AnililiHttpClient.unpackJs(wvHtml) else wvHtml
                if (unpacked != null) {
                    streamUrl = Regex("""["'](https?://[^"'\s]+\.m3u8[^"'\s]*)["']""")
                        .find(unpacked)?.groupValues?.getOrNull(1)
                }
            }
        }

        val finalStreamUrl = streamUrl?.takeIf { it.isNotBlank() } ?: return null

        // Subtitle extraction from ?sub= or subtitle = "..." (D7.o.Z lines 662-706)
        var rawSub = Regex("""[?&]sub=([^&]+)""").find(embedUrl)?.groupValues?.getOrNull(1)
        if (rawSub.isNullOrBlank()) {
            rawSub = Regex("""subtitle\s*=\s*["']([^"']+)["']""").find(html)?.groupValues?.getOrNull(1)
        }
        val decodedSub = rawSub?.takeIf { it.isNotBlank() }?.let { s ->
            if (s.contains('%')) runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s) else s
        }
        val subPair = decodedSub?.let { sUrl ->
            val fileName = sUrl.substringBefore('?').substringAfterLast('/')
            val langTag = Regex("""_(?:sub|dub)_([a-z]{2,3})(?:[-_.]|$)""", RegexOption.IGNORE_CASE)
                .find(fileName)?.groupValues?.getOrNull(1)?.lowercase() ?: "en"
            sUrl to langTag
        }

        return finalStreamUrl to subPair
    }

    private fun parseSkipArray(skips: JSONArray?): SkipWindow? {
        if (skips == null || skips.length() == 0) return null
        var opStart = 0L
        var opEnd = 0L
        var edStart = 0L
        var edEnd = 0L
        for (j in 0 until skips.length()) {
            val sObj = skips.optJSONObject(j) ?: continue
            val sKind = sObj.optString("type", "").ifEmpty { sObj.optString("kind", "") }.lowercase()
            val start = sObj.optDouble("start", sObj.optDouble("start_seconds", 0.0)).toLong()
            val end = sObj.optDouble("end", sObj.optDouble("end_seconds", 0.0)).toLong()
            if (end > start) {
                if ((sKind == "intro" || sKind == "op" || sKind == "mixed_op") && opEnd == 0L) {
                    opStart = start
                    opEnd = end
                } else if ((sKind == "outro" || sKind == "ed" || sKind == "mixed_ed") && edEnd == 0L) {
                    edStart = start
                    edEnd = end
                }
            }
        }
        return if (opEnd > opStart || edEnd > edStart) SkipWindow(opStart, opEnd, edStart, edEnd) else null
    }

    /**
     * Extracts a stream from Miruro's catalog pipe (defaults to "kiwi", also supports "bee", "sun", "pewe", "hop", "ally", "moo", "bonk").
     */
    suspend fun extract(
        context: Context?,
        anilistId: Int,
        episode: Int,
        lang: String = "sub",
        providerLabel: String = "kiwi"
    ): AniPmStreamResult? = withContext(Dispatchers.IO) {
        try {
            val titleId = getMiruroTitleId(anilistId) ?: return@withContext null
            val configList = getConfig()
            val playJson = getPlayJson(titleId, episode) ?: return@withContext null
            val skipWindow = parseSkipArray(playJson.optJSONArray("skip_times"))
                ?: getEpisodeSkipTimes(titleId, episode)

            val cfg = configList.firstOrNull {
                it.label.equals(providerLabel, ignoreCase = true) ||
                    it.key.equals(providerLabel, ignoreCase = true)
            }
            val targetProviderKey = when {
                cfg != null && cfg.isEmbed && !cfg.parent.isNullOrBlank() -> cfg.parent
                cfg != null -> cfg.key
                providerLabel.equals("kiwi", ignoreCase = true) -> "animepahe"
                else -> providerLabel.lowercase()
            }

            val isDub = lang.equals("dub", ignoreCase = true)
            val trackNames = if (isDub) listOf("dub") else listOf("sub", "ssub")

            val tracksArr = playJson.optJSONArray("tracks") ?: JSONArray()
            var matchedProviderObj: JSONObject? = null

            for (trackName in trackNames) {
                for (i in 0 until tracksArr.length()) {
                    val tObj = tracksArr.optJSONObject(i) ?: continue
                    if (!tObj.optString("track", "").equals(trackName, ignoreCase = true)) continue
                    val providersArr = tObj.optJSONArray("providers") ?: continue
                    for (j in 0 until providersArr.length()) {
                        val pObj = providersArr.optJSONObject(j) ?: continue
                        val pName = pObj.optString("provider", "")
                        val matches = pName.equals(targetProviderKey, ignoreCase = true) ||
                            pName.equals(providerLabel, ignoreCase = true) ||
                            (providerLabel.equals("knob", ignoreCase = true) && pName.equals("bonk", ignoreCase = true)) ||
                            (providerLabel.equals("bonk", ignoreCase = true) && pName.equals("knob", ignoreCase = true)) ||
                            (providerLabel.equals("bun", ignoreCase = true) && pName.equals("bee", ignoreCase = true)) ||
                            (providerLabel.equals("bee", ignoreCase = true) && (pName.equals("animekai", ignoreCase = true) || pName.equals("hianime", ignoreCase = true))) ||
                            (providerLabel.equals("kiwi", ignoreCase = true) && (pName.equals("animepahe", ignoreCase = true) || pName.equals("kwik", ignoreCase = true)))
                        if (matches) {
                            matchedProviderObj = pObj
                            break
                        }
                    }
                    if (matchedProviderObj != null) break
                }
                if (matchedProviderObj != null) break
            }

            // If exact provider wasn't found under "kiwi", check any provider that has servers
            if (matchedProviderObj == null && providerLabel.equals("kiwi", ignoreCase = true)) {
                for (trackName in trackNames) {
                    for (i in 0 until tracksArr.length()) {
                        val tObj = tracksArr.optJSONObject(i) ?: continue
                        if (!tObj.optString("track", "").equals(trackName, ignoreCase = true)) continue
                        val providersArr = tObj.optJSONArray("providers") ?: continue
                        for (j in 0 until providersArr.length()) {
                            val pObj = providersArr.optJSONObject(j) ?: continue
                            val servers = pObj.optJSONArray("servers")
                            if (servers != null && servers.length() > 0) {
                                matchedProviderObj = pObj
                                break
                            }
                        }
                        if (matchedProviderObj != null) break
                    }
                    if (matchedProviderObj != null) break
                }
            }

            val providerObj = matchedProviderObj ?: return@withContext null
            val serversArr = providerObj.optJSONArray("servers") ?: return@withContext null

            val subtitles = mutableListOf<Map<String, String>>()
            val subsArr = providerObj.optJSONArray("subtitles")
            var defaultSubUrl = ""
            if (subsArr != null) {
                for (i in 0 until subsArr.length()) {
                    val sObj = subsArr.optJSONObject(i) ?: continue
                    val sUrl = sObj.optString("file", "").ifEmpty { sObj.optString("url", "") }.trim()
                    if (sUrl.isEmpty()) continue
                    val sLabel = sObj.optString("label", "").ifEmpty { sObj.optString("language", "Subtitle") }.trim()
                    val sLang = sObj.optString("language", "en").trim()
                    subtitles.add(mapOf("url" to sUrl, "lang" to sLabel, "language" to sLang))
                    if (defaultSubUrl.isEmpty() && (sLabel.contains("English", true) || sLang.equals("en", true) || sLang.equals("eng", true))) {
                        defaultSubUrl = sUrl
                    }
                }
            }

            for (i in 0 until serversArr.length()) {
                val serverObj = serversArr.optJSONObject(i) ?: continue
                val headersObj = serverObj.optJSONObject("headers")
                var serverReferer = ""
                if (headersObj != null) {
                    val hKeys = headersObj.keys()
                    while (hKeys.hasNext()) {
                        val hk = hKeys.next()
                        if (hk.equals("Referer", ignoreCase = true)) {
                            serverReferer = headersObj.optString(hk, "")
                            break
                        }
                    }
                }

                val streamsArr = serverObj.optJSONArray("streams") ?: JSONArray()

                // 1. Always check direct HLS/MP4 streams first (Miruro's animepahe/kiwi pipe provides direct uwucdn.top .m3u8 URLs here)
                var bestDirectUrl = ""
                var bestDirectScore = -1
                for (j in 0 until streamsArr.length()) {
                    val stObj = streamsArr.optJSONObject(j) ?: continue
                    val url = stObj.optString("url", "").trim()
                    if (!url.startsWith("http", ignoreCase = true)) continue
                    val resStr = stObj.optString("resolution", "")
                    val resScore = Regex("""(\d{3,4})""").find(resStr)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 500
                    val hlsBonus = if (url.contains(".m3u8", ignoreCase = true) || stObj.optString("format", "").equals("hls", ignoreCase = true)) 2000 else 0
                    val totalScore = hlsBonus + resScore
                    if (totalScore > bestDirectScore) {
                        bestDirectScore = totalScore
                        bestDirectUrl = url
                    }
                }

                if (bestDirectUrl.isNotEmpty()) {
                    if (defaultSubUrl.isEmpty() && subtitles.isNotEmpty()) {
                        defaultSubUrl = subtitles.first()["url"] ?: ""
                    }
                    val effectiveReferer = when {
                        serverReferer.isNotEmpty() -> serverReferer
                        bestDirectUrl.contains("uwucdn.", ignoreCase = true) || bestDirectUrl.contains("owocdn.", ignoreCase = true) || bestDirectUrl.contains("kwik.", ignoreCase = true) -> "https://kwik.cx/"
                        else -> ""
                    }
                    val displayProvider = providerLabel.replaceFirstChar { it.uppercase() } + (if (isDub) " Dub" else "")
                    return@withContext AniPmStreamResult(
                        hlsUrl = bestDirectUrl,
                        subtitleUrl = defaultSubUrl,
                        subtitles = subtitles,
                        provider = displayProvider,
                        referer = effectiveReferer,
                        introStart = skipWindow.introStart,
                        introEnd = skipWindow.introEnd,
                        outroStart = skipWindow.outroStart,
                        outroEnd = skipWindow.outroEnd
                    )
                }

                // 2. Check embed URLs (server.embed and streams[].embed) if no direct stream URL was present
                val embedUrls = mutableListOf<String>()
                extractEmbedUrl(serverObj)?.let { embedUrls.add(it) }
                for (j in 0 until streamsArr.length()) {
                    val stObj = streamsArr.optJSONObject(j) ?: continue
                    extractEmbedUrl(stObj)?.let { if (!embedUrls.contains(it)) embedUrls.add(it) }
                }

                for (embedUrl in embedUrls) {
                    val resolved = resolveEmbedStream(context, embedUrl, serverReferer.ifEmpty { null })
                    if (resolved != null) {
                        val (hlsUrl, embedSub) = resolved
                        if (embedSub != null) {
                            val (subUrl, subLang) = embedSub
                            if (subtitles.none { it["url"] == subUrl }) {
                                subtitles.add(0, mapOf("url" to subUrl, "lang" to "English", "language" to subLang))
                            }
                            if (defaultSubUrl.isEmpty()) {
                                defaultSubUrl = subUrl
                            }
                        }
                        if (defaultSubUrl.isEmpty() && subtitles.isNotEmpty()) {
                            defaultSubUrl = subtitles.first()["url"] ?: ""
                        }
                        val effectiveReferer = when {
                            embedUrl.contains("kwik.", ignoreCase = true) || hlsUrl.contains("kwik.", ignoreCase = true) || hlsUrl.contains("owocdn.", ignoreCase = true) || hlsUrl.contains("uwucdn.", ignoreCase = true) -> {
                                val host = runCatching { java.net.URI(embedUrl).host }.getOrNull() ?: "kwik.cx"
                                "https://$host/"
                            }
                            serverReferer.isNotEmpty() -> serverReferer
                            else -> embedUrl
                        }
                        val displayProvider = providerLabel.replaceFirstChar { it.uppercase() } + (if (isDub) " Dub" else "")
                        return@withContext AniPmStreamResult(
                            hlsUrl = hlsUrl,
                            subtitleUrl = defaultSubUrl,
                            subtitles = subtitles,
                            provider = displayProvider,
                            referer = effectiveReferer,
                            introStart = skipWindow.introStart,
                            introEnd = skipWindow.introEnd,
                            outroStart = skipWindow.outroStart,
                            outroEnd = skipWindow.outroEnd
                        )
                    }
                }
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
