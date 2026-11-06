package com.lagradost.cloudstream3.ui.animebox.extractors

import com.lagradost.cloudstream3.ui.animebox.api.AniListClient
import com.lagradost.cloudstream3.ui.animebox.api.AniZipClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream

data class HardSubResult(
    val hlsUrl: String,
    val referer: String,
    val provider: String = "pewe",
    val introStart: Long = 0L,
    val introEnd: Long = 0L,
    val outroStart: Long = 0L,
    val outroEnd: Long = 0L,
    val subtitleUrl: String = "",
    val subtitles: List<Map<String, String>> = emptyList()
)

object HardSubExtractor {

    private val client get() = AnililiHttpClient.client

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val ANINEKO_BASE = "https://anineko.to"
    private const val ANIMEX_API = "https://pp.animex.one/rest/api"

    private val aniNekoSlugCache = ConcurrentHashMap<Int, String>()
    private val animexSlugCache = ConcurrentHashMap<Int, String>()

    suspend fun extractHardSub(
        anilistId: Int,
        episodeNum: Int,
        animeTitle: String? = null,
        context: android.content.Context? = null
    ): HardSubResult? = withContext(Dispatchers.IO) {
        // Priority 0: Shin Chan dedicated OK.ru stream
        if (com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(anilistId, animeTitle)) {
            try {
                val okRuRes = ShinChanOkRuExtractor.extractShinChanStream(anilistId, episodeNum, animeTitle)
                if (okRuRes != null && okRuRes.hlsUrl.isNotEmpty()) {
                    return@withContext HardSubResult(
                        hlsUrl = okRuRes.hlsUrl,
                        referer = okRuRes.referer,
                        provider = okRuRes.provider
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Priority 1: ReAnime HardSub / SoftSub with English subtitles (Top Priority)
        try {
            val reAnimeRes = ReAnimeExtractor.extract(anilistId, episodeNum, "sub")
            if (reAnimeRes != null && reAnimeRes.hlsUrl.isNotEmpty()) {
                return@withContext HardSubResult(
                    hlsUrl = reAnimeRes.hlsUrl,
                    referer = reAnimeRes.referer,
                    provider = "ReAnime",
                    introStart = reAnimeRes.introStart,
                    introEnd = reAnimeRes.introEnd,
                    outroStart = reAnimeRes.outroStart,
                    outroEnd = reAnimeRes.outroEnd,
                    subtitleUrl = reAnimeRes.subtitleUrl,
                    subtitles = reAnimeRes.subtitles
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Priority 2: AnimePahe HardSub (Direct Kwik stream with hardcoded English subs)
        try {
            val paheRes = AnimePaheExtractor.extract(anilistId, episodeNum, animeTitle, mode = "sub", context = context)
            if (paheRes != null && paheRes.hlsUrl.isNotEmpty()) {
                return@withContext HardSubResult(
                    hlsUrl = paheRes.hlsUrl,
                    referer = paheRes.referer,
                    provider = "AnimePahe"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Priority 3: Miruro Kiwi Pipe (Kiwi / Kwik stream)
        try {
            val kiwiRes = MiruroKiwiExtractor.extract(context, anilistId, episodeNum, "sub", "kiwi")
            if (kiwiRes != null && kiwiRes.hlsUrl.isNotEmpty()) {
                return@withContext HardSubResult(
                    hlsUrl = kiwiRes.hlsUrl,
                    referer = kiwiRes.referer,
                    provider = kiwiRes.provider,
                    introStart = kiwiRes.introStart,
                    introEnd = kiwiRes.introEnd,
                    outroStart = kiwiRes.outroStart,
                    outroEnd = kiwiRes.outroEnd,
                    subtitleUrl = kiwiRes.subtitleUrl,
                    subtitles = kiwiRes.subtitles
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Priority 4: AniNeko
        try {
            val nekoRes = extractAniNeko(anilistId, episodeNum)
            if (nekoRes != null && nekoRes.hlsUrl.isNotEmpty()) {
                return@withContext nekoRes
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Priority 5: AnimeGG Hard Sub source (immediate direct CDN video stream)
        try {
            val animeGgRes = AnimeGGExtractor.extract(anilistId, episodeNum, animeTitle, mode = "sub")
            if (animeGgRes != null && animeGgRes.streamUrl.isNotEmpty()) {
                return@withContext HardSubResult(
                    hlsUrl = animeGgRes.streamUrl,
                    referer = animeGgRes.referer,
                    provider = animeGgRes.provider
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Priority 6: Animex (kiwi / pewe / beep)
        try {
            val animexRes = extractAnimex(anilistId, episodeNum, "sub")
            if (animexRes != null && animexRes.hlsUrl.isNotEmpty()) {
                return@withContext animexRes
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Priority 7: AniBD (With subtitles preserved if available)
        try {
            val aniBdRes = AniBdExtractor.extract(anilistId, episodeNum)
            if (aniBdRes != null && aniBdRes.hlsUrl.isNotEmpty()) {
                return@withContext HardSubResult(
                    hlsUrl = aniBdRes.hlsUrl,
                    referer = aniBdRes.referer,
                    provider = "AniBD",
                    subtitleUrl = aniBdRes.subtitleUrl,
                    subtitles = aniBdRes.subtitles
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Priority 8: AniWave HardSub (bottom of hardsub category)
        try {
            val aniWaveRes = AniwavesExtractor.extractHardSub(anilistId, episodeNum, animeTitle)
            if (aniWaveRes != null && aniWaveRes.hlsUrl.isNotEmpty()) {
                return@withContext aniWaveRes
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        null
    }

    suspend fun extractMiruroDub(anilistId: Int, episodeNum: Int): HardSubResult? {
        val kiwiRes = MiruroKiwiExtractor.extract(null, anilistId, episodeNum, "dub", "kiwi") ?: return null
        return HardSubResult(
            hlsUrl = kiwiRes.hlsUrl,
            referer = kiwiRes.referer,
            provider = kiwiRes.provider,
            introStart = kiwiRes.introStart,
            introEnd = kiwiRes.introEnd,
            outroStart = kiwiRes.outroStart,
            outroEnd = kiwiRes.outroEnd,
            subtitleUrl = kiwiRes.subtitleUrl,
            subtitles = kiwiRes.subtitles
        )
    }

    suspend fun extractAnimexDub(anilistId: Int, episodeNum: Int): HardSubResult? {
        return extractAnimex(anilistId, episodeNum, "dub")
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. Animex.one Extractor (kiwi / pewe server)
    // ══════════════════════════════════════════════════════════════════════════

    suspend fun extractAnimex(anilistId: Int, episodeNum: Int, type: String = "sub"): HardSubResult? = withContext(Dispatchers.IO) {
        val slug = animexSlugCache[anilistId] ?: resolveAnimexSlug(anilistId)
        if (slug == null) return@withContext null
        animexSlugCache[anilistId] = slug

        val providersToTry = if (type == "dub") listOf("kiwi", "pewe", "beep", "zoro") else listOf("kiwi", "pewe", "beep", "zoro")
        for (prov in providersToTry) {
            try {
                val url = "$ANIMEX_API/sources?id=${URLEncoder.encode(slug, "UTF-8")}&epNum=$episodeNum&type=$type&providerId=$prov"
                val req = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", UA)
                    .addHeader("Referer", "https://animex.one/")
                    .addHeader("Origin", "https://animex.one")
                    .addHeader("Accept", "application/json, text/plain, */*")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        if (body.isNotEmpty() && !body.contains("\"error\"")) {
                            val json = JSONObject(body)
                            val sourcesArr = json.optJSONArray("sources")
                            if (sourcesArr != null && sourcesArr.length() > 0) {
                                var selectedHls = ""
                                for (i in 0 until sourcesArr.length()) {
                                    val s = sourcesArr.optJSONObject(i) ?: continue
                                    val u = s.optString("url", s.optString("file", s.optString("m3u8", "")))
                                    if (u.contains(".m3u8") || u.startsWith("http")) {
                                        selectedHls = u
                                        break
                                    }
                                }
                                if (selectedHls.isNotEmpty()) {
                                    val headers = json.optJSONObject("headers")
                                    val ref = headers?.optString("Referer", headers.optString("Origin", "https://animex.one/")) ?: "https://animex.one/"
                                    return@withContext HardSubResult(
                                        hlsUrl = selectedHls,
                                        referer = ref,
                                        provider = prov
                                    )
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        null
    }

    private suspend fun resolveAnimexSlug(anilistId: Int): String? = withContext(Dispatchers.IO) {
        val candidates = listOf(
            "https://animex.one/watch/-$anilistId-episode-1/__data.json",
            "https://animex.one/watch/-$anilistId/__data.json"
        )
        for (url in candidates) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", UA)
                    .addHeader("Referer", "https://animex.one/")
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val firstLine = body.split("\n")[0]
                        val json = JSONObject(firstLine)
                        val nodes = json.optJSONArray("nodes") ?: return@use
                        for (i in 0 until nodes.length()) {
                            val node = nodes.optJSONObject(i) ?: continue
                            if (node.optString("type") == "data" && node.has("data")) {
                                val flat = node.getJSONArray("data")
                                if (flat.length() == 0) continue
                                val pageMap = flat.optJSONObject(0) ?: continue
                                val animeIdx = pageMap.optInt("anime", -1)
                                if (animeIdx in 0 until flat.length()) {
                                    val animeMap = flat.optJSONObject(animeIdx) ?: continue
                                    val slugIdx = animeMap.optInt("slug", -1)
                                    if (slugIdx in 0 until flat.length()) {
                                        val slug = flat.optString(slugIdx, "")
                                        if (slug.isNotEmpty()) return@withContext slug
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        null
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. AniNeko Extractor (neko server fallback)
    // ══════════════════════════════════════════════════════════════════════════

    suspend fun extractAniNeko(anilistId: Int, episodeNum: Int): HardSubResult? = withContext(Dispatchers.IO) {
        var slug = aniNekoSlugCache[anilistId]
        if (slug == null) {
            slug = resolveAniNekoSlug(anilistId)
            if (slug != null) aniNekoSlugCache[anilistId] = slug
        }
        if (slug == null) return@withContext null

        val epUrl = "$ANINEKO_BASE/watch/$slug/ep-$episodeNum"
        val req = Request.Builder()
            .url(epUrl)
            .addHeader("User-Agent", UA)
            .addHeader("Referer", "$ANINEKO_BASE/watch/$slug")
            .build()

        var epHtml = ""
        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) {
                epHtml = resp.body?.string() ?: ""
            }
        }
        if (epHtml.isEmpty()) return@withContext null

        val videoEmbeds = mutableListOf<String>()
        val videoMatcher = Pattern.compile("""data-video=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(epHtml)
        while (videoMatcher.find()) {
            val v = videoMatcher.group(1) ?: continue
            if (v.isNotEmpty() && !videoEmbeds.contains(v)) {
                videoEmbeds.add(v)
            }
        }

        if (videoEmbeds.isEmpty()) {
            val iframeMatcher = Pattern.compile("""<iframe\b[^>]*src=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(epHtml)
            while (iframeMatcher.find()) {
                val src = iframeMatcher.group(1) ?: continue
                if (src.isNotEmpty() && !videoEmbeds.contains(src)) {
                    videoEmbeds.add(src)
                }
            }
        }

        for (embed in videoEmbeds) {
            val directHls = extractHlsFromEmbed(embed)
            if (directHls != null && directHls.isNotEmpty()) {
                val referer = try {
                    val u = java.net.URI.create(embed)
                    "${u.scheme}://${u.host}/"
                } catch (_: Exception) { "$ANINEKO_BASE/" }

                return@withContext HardSubResult(
                    hlsUrl = directHls,
                    referer = referer,
                    provider = "AniNeko"
                )
            }
        }

        null
    }

    private suspend fun resolveAniNekoSlug(anilistId: Int): String? = withContext(Dispatchers.IO) {
        val titles = mutableListOf<String>()
        try {
            val aniZip = AniZipClient.getAniZipJson(anilistId)
            val titlesObj = aniZip?.optJSONObject("titles")
            if (titlesObj != null) {
                titlesObj.optString("en", "").takeIf { it.isNotEmpty() }?.let { titles.add(it) }
                titlesObj.optString("ro", "").takeIf { it.isNotEmpty() }?.let { titles.add(it) }
                titlesObj.optString("rj", "").takeIf { it.isNotEmpty() }?.let { titles.add(it) }
            }
        } catch (_: Exception) {}

        if (titles.isEmpty()) {
            try {
                val details = AniListClient.getAnimeDetails(anilistId)
                if (details != null) {
                    val media = JSONObject(details).optJSONObject("data")?.optJSONObject("Media")
                    val t = media?.optJSONObject("title")
                    t?.optString("english", "")?.takeIf { it.isNotEmpty() }?.let { titles.add(it) }
                    t?.optString("romaji", "")?.takeIf { it.isNotEmpty() }?.let { titles.add(it) }
                }
            } catch (_: Exception) {}
        }

        for (title in titles.distinct()) {
            val clean = title.replace(Regex("""[^a-zA-Z0-9\s]"""), " ").trim()
            val searchUrl = "$ANINEKO_BASE/browser?keyword=${URLEncoder.encode(clean, "UTF-8")}"
            val req = Request.Builder()
                .url(searchUrl)
                .addHeader("User-Agent", UA)
                .addHeader("Referer", "$ANINEKO_BASE/")
                .build()

            try {
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val html = resp.body?.string() ?: ""
                        val itemMatcher = Pattern.compile("""<a\b[^>]*href=["']/watch/([^/?#"'\s]+)["'][^>]*title=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(html)
                        val cleanTarget = clean.lowercase().replace(Regex("""[^a-z0-9]"""), "")
                        while (itemMatcher.find()) {
                            val slug = itemMatcher.group(1) ?: continue
                            val itemTitle = itemMatcher.group(2) ?: ""
                            val cleanItem = itemTitle.lowercase().replace(Regex("""[^a-z0-9]"""), "")
                            val cleanSlug = slug.replace("-", "").lowercase()
                            if (cleanItem.contains(cleanTarget) || cleanTarget.contains(cleanItem) || cleanSlug.contains(cleanTarget)) {
                                return@withContext slug
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        null
    }

    private suspend fun extractHlsFromEmbed(embedUrl: String): String? = withContext(Dispatchers.IO) {
        if (embedUrl.contains(".m3u8")) return@withContext embedUrl

        val req = Request.Builder()
            .url(embedUrl)
            .addHeader("User-Agent", UA)
            .addHeader("Referer", "$ANINEKO_BASE/")
            .build()

        try {
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val patterns = listOf(
                        Pattern.compile("""const\s+src\s*=\s*["'](https?://[^"']+\.m3u8[^"']*)["']""", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("""file\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("""["'](https?://[^"']+/master\.m3u8[^"']*)["']""", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("""["'](https?://[^"']+\.m3u8[^"']*)["']""", Pattern.CASE_INSENSITIVE),
                        Pattern.compile("""src\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""", Pattern.CASE_INSENSITIVE)
                    )
                    for (p in patterns) {
                        val m = p.matcher(html)
                        if (m.find()) {
                            val u = m.group(1)
                            if (!u.isNullOrEmpty()) return@withContext u
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        null
    }
}
