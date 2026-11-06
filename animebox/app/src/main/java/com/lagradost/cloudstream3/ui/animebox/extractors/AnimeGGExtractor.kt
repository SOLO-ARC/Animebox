package com.lagradost.cloudstream3.ui.animebox.extractors
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import com.lagradost.cloudstream3.ui.animebox.api.AniListClient
import com.lagradost.cloudstream3.ui.animebox.api.AniZipClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class AnimeGGResult(
    val streamUrl: String,
    val referer: String,
    val isDub: Boolean = false,
    val provider: String = "AnimeGG"
)

object AnimeGGExtractor {

    private val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val MAIN_HOST = "https://www.animegg.org"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val slugCache = ConcurrentHashMap<String, String>()

    suspend fun extract(
        anilistId: Int,
        episodeNum: Int,
        animeTitle: String? = null,
        mode: String = "sub"
    ): AnimeGGResult? = withContext(Dispatchers.IO) {
        if (episodeNum <= 0) return@withContext null

        val isDub = mode.lowercase() == "dub"
        val cacheKey = "${anilistId}_${if (isDub) "dub" else "sub"}"
        val slug = slugCache[cacheKey] ?: slugCache["${anilistId}_all"] ?: resolveAnimeGGSlug(anilistId, animeTitle, isDub)
        if (slug.isNullOrEmpty()) return@withContext null

        slugCache[cacheKey] = slug

        val seriesSlug = slug.replace("/series/", "").trim('/')
        
        // 1. Try direct episode URL candidates
        val epCandidates = mutableListOf(
            "$MAIN_HOST/$seriesSlug-episode-$episodeNum"
        )
        if (seriesSlug.endsWith("-dub")) {
            val baseSlug = seriesSlug.removeSuffix("-dub")
            epCandidates.add("$MAIN_HOST/$baseSlug-episode-$episodeNum")
        }

        var epHtml = ""
        var workingEpUrl = ""

        for (candUrl in epCandidates) {
            val epReq = Request.Builder()
                .url(candUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", "$MAIN_HOST/series/$seriesSlug")
                .build()

            try {
                client.newCall(epReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        if (body.contains("iframe") || body.contains("embed")) {
                            epHtml = body
                            workingEpUrl = candUrl
                        }
                    }
                }
            } catch (_: Exception) {}
            if (epHtml.isNotEmpty()) break
        }

        // If direct candidate gave 404, query series page to find the exact episode link
        if (epHtml.isEmpty()) {
            try {
                val seriesReq = Request.Builder()
                    .url("$MAIN_HOST/series/$seriesSlug")
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", "$MAIN_HOST/")
                    .build()

                var seriesHtml = ""
                client.newCall(seriesReq).execute().use { resp ->
                    if (resp.isSuccessful) seriesHtml = resp.body?.string() ?: ""
                }

                if (seriesHtml.isNotEmpty()) {
                    val epRegex = Pattern.compile("""<a\b[^>]*href=["'](/[^"']*-episode-$episodeNum(?:#[^"']*)?)["']""", Pattern.CASE_INSENSITIVE)
                    val epMatcher = epRegex.matcher(seriesHtml)
                    if (epMatcher.find()) {
                        val relEpPath = epMatcher.group(1)?.substringBefore("#") ?: ""
                        if (relEpPath.isNotEmpty()) {
                            workingEpUrl = "$MAIN_HOST$relEpPath"
                            val req = Request.Builder()
                                .url(workingEpUrl)
                                .addHeader("User-Agent", USER_AGENT)
                                .addHeader("Referer", "$MAIN_HOST/series/$seriesSlug")
                                .build()
                            client.newCall(req).execute().use { resp ->
                                if (resp.isSuccessful) epHtml = resp.body?.string() ?: ""
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        if (epHtml.isEmpty() || workingEpUrl.isEmpty()) return@withContext null

        // 2. Extract Embed iframe based on sub vs dub mode
        var embedPath: String? = null
        if (isDub) {
            val dubMatcher = Pattern.compile("""id=["']dubbed-[^"']*["'][^>]*>.*?<iframe\b[^>]*src=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(epHtml)
            if (dubMatcher.find()) {
                embedPath = dubMatcher.group(1)
            }
            if (embedPath == null) {
                val tabMatcher = Pattern.compile("""<iframe\b[^>]*src=["'](/embed/[^"']+)["'][^>]*id=["'][^"']*dub[^"']*["']""", Pattern.CASE_INSENSITIVE).matcher(epHtml)
                if (tabMatcher.find()) {
                    embedPath = tabMatcher.group(1)
                }
            }
            if (embedPath == null) {
                // If there are multiple iframes, 2nd is usually Dubbed
                val ifrMatcher = Pattern.compile("""<iframe\b[^>]*src=["'](/embed/[^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(epHtml)
                val allIfrs = mutableListOf<String>()
                while (ifrMatcher.find()) {
                    ifrMatcher.group(1)?.let { allIfrs.add(it) }
                }
                if (allIfrs.size > 1) {
                    embedPath = allIfrs[1]
                } else if (allIfrs.size == 1 && seriesSlug.contains("dub")) {
                    embedPath = allIfrs[0]
                }
            }
        } else {
            val subMatcher = Pattern.compile("""id=["']subbed-[^"']*["'][^>]*>.*?<iframe\b[^>]*src=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(epHtml)
            if (subMatcher.find()) {
                embedPath = subMatcher.group(1)
            }
            if (embedPath == null) {
                val anyMatcher = Pattern.compile("""<iframe\b[^>]*src=["'](/embed/[^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(epHtml)
                if (anyMatcher.find()) {
                    embedPath = anyMatcher.group(1)
                }
            }
        }

        if (embedPath.isNullOrEmpty()) return@withContext null

        val fullEmbedUrl = if (embedPath.startsWith("http")) embedPath else "$MAIN_HOST$embedPath"

        // 3. Request Embed page to extract videoSources
        val embedReq = Request.Builder()
            .url(fullEmbedUrl)
            .addHeader("User-Agent", USER_AGENT)
            .addHeader("Referer", workingEpUrl)
            .build()

        var embHtml = ""
        try {
            client.newCall(embedReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    embHtml = resp.body?.string() ?: ""
                }
            }
        } catch (_: Exception) {}

        if (embHtml.isEmpty()) return@withContext null

        // 4. Extract direct stream from videoSources
        val vsMatcher = Pattern.compile("""videoSources\s*=\s*(\[[^\]]+\])""", Pattern.CASE_INSENSITIVE).matcher(embHtml)
        if (vsMatcher.find()) {
            val rawVs = vsMatcher.group(1) ?: ""
            val fileMatches = Pattern.compile("""file\s*:\s*["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(rawVs)
            var chosenFile = ""
            while (fileMatches.find()) {
                val f = fileMatches.group(1) ?: ""
                if (f.isNotEmpty()) {
                    chosenFile = f
                    if (f.contains("720") || f.contains("1080") || f.contains(".mp4")) break
                }
            }

            if (chosenFile.isNotEmpty()) {
                val rawStreamUrl = if (chosenFile.startsWith("http")) chosenFile else "$MAIN_HOST$chosenFile"
                var finalStreamUrl = rawStreamUrl
                var resolvedReferer = fullEmbedUrl

                // AnimeGG /play/... URLs return HTTP 302 redirecting to sXXX.vidcache.net:8166
                // Resolve the redirect URL upfront with OkHttp so ExoPlayer connects directly with proper Referer
                try {
                    val headReq = Request.Builder()
                        .url(rawStreamUrl)
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("Referer", fullEmbedUrl)
                        .build()
                    client.newCall(headReq).execute().use { resp ->
                        val red = resp.request.url.toString()
                        if (red.isNotEmpty()) {
                            finalStreamUrl = red
                            if (red.contains("vidcache", ignoreCase = true) || red.contains("animegg", ignoreCase = true)) {
                                resolvedReferer = "https://www.animegg.org/"
                            }
                        }
                    }
                } catch (_: Exception) {}

                return@withContext AnimeGGResult(
                    streamUrl = finalStreamUrl,
                    referer = resolvedReferer,
                    isDub = isDub,
                    provider = if (isDub) "AnimeGG (Dub)" else "AnimeGG (HardSub)"
                )
            }

            // Fallback: check base64 'bk' (e.g. mp4upload embed)
            val bkMatches = Pattern.compile("""bk\s*:\s*["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(rawVs)
            if (bkMatches.find()) {
                val rawBk = bkMatches.group(1) ?: ""
                if (rawBk.isNotEmpty()) {
                    try {
                        val decodedBytes = android.util.Base64.decode(rawBk, android.util.Base64.DEFAULT)
                        val decodedUrl = URLDecoder.decode(String(decodedBytes, Charsets.UTF_8), "UTF-8")
                        if (decodedUrl.startsWith("http")) {
                            return@withContext AnimeGGResult(
                                streamUrl = decodedUrl,
                                referer = fullEmbedUrl,
                                isDub = isDub,
                                provider = if (isDub) "AnimeGG (Dub Backup)" else "AnimeGG (HardSub Backup)"
                            )
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        // Direct mp4/m3u8 check in embed page
        val directMediaMatcher = Pattern.compile("""src=["'](https?://[^"']+\.(?:mp4|m3u8)[^"']*)["']""", Pattern.CASE_INSENSITIVE).matcher(embHtml)
        if (directMediaMatcher.find()) {
            val u = directMediaMatcher.group(1) ?: ""
            if (u.isNotEmpty()) {
                return@withContext AnimeGGResult(
                    streamUrl = u,
                    referer = fullEmbedUrl,
                    isDub = isDub,
                    provider = if (isDub) "AnimeGG (Dub)" else "AnimeGG (HardSub)"
                )
            }
        }

        null
    }

    private suspend fun resolveAnimeGGSlug(anilistId: Int, providedTitle: String?, isDub: Boolean = false): String? = withContext(Dispatchers.IO) {
        val rawTitles = mutableListOf<String>()

        if (anilistId > 0) {
            try {
                val details = AniListClient.getAnimeDetails(anilistId)
                if (details != null) {
                    val media = JSONObject(details).optJSONObject("data")?.optJSONObject("Media")
                    val t = media?.optJSONObject("title")
                    // Prioritize English title first since AnimeGG indexes titles primarily in English
                    t?.optString("english", "")?.takeIf { it.isNotEmpty() }?.let { rawTitles.add(it) }
                    t?.optString("romaji", "")?.takeIf { it.isNotEmpty() }?.let { rawTitles.add(it) }
                    val syns = media?.optJSONArray("synonyms")
                    if (syns != null) {
                        for (i in 0 until syns.length()) {
                            syns.optString(i, "").takeIf { it.isNotEmpty() }?.let { rawTitles.add(it) }
                        }
                    }
                }
            } catch (_: Exception) {}

            try {
                val aniZip = AniZipClient.getAniZipJson(anilistId)
                val titlesObj = aniZip?.optJSONObject("titles")
                if (titlesObj != null) {
                    titlesObj.optString("en", "").takeIf { it.isNotEmpty() }?.let { rawTitles.add(it) }
                    titlesObj.optString("ro", "").takeIf { it.isNotEmpty() }?.let { rawTitles.add(it) }
                    titlesObj.optString("rj", "").takeIf { it.isNotEmpty() }?.let { rawTitles.add(it) }
                }
            } catch (_: Exception) {}
        }

        if (!providedTitle.isNullOrBlank()) {
            rawTitles.add(providedTitle)
        }

        val searchCandidates = mutableListOf<String>()
        for (rt in rawTitles) {
            // Strip year in parentheses e.g. "(2007)", "(TV)", "(Dub)"
            val stripped = rt.replace(Regex("""\([^\)]*\)"""), " ").replace(Regex("""[^a-zA-Z0-9\s]"""), " ").trim()
            if (stripped.isNotEmpty()) {
                if (isDub) searchCandidates.add("$stripped dub")
                searchCandidates.add(stripped)
            }
        }
        val distinctCandidates = searchCandidates.distinct()

        for (q in distinctCandidates) {
            try {
                val searchUrl = "$MAIN_HOST/search/?q=${URLEncoder.encode(q, "UTF-8")}"
                val req = Request.Builder()
                    .url(searchUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", "$MAIN_HOST/")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val html = resp.body?.string() ?: ""
                        val blockMatcher = Pattern.compile("""<a\b[^>]*href=["'](/series/[^"']+)["'][^>]*>(.*?)</a>""", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)

                        var bestSlug: String? = null
                        var bestScore = 0

                        while (blockMatcher.find()) {
                            val href = blockMatcher.group(1) ?: continue
                            val inner = blockMatcher.group(2) ?: ""
                            val cleanInner = inner.replace(Regex("""<[^>]+>"""), " ").replace(Regex("""\s+"""), " ").trim().lowercase()

                            val mainTitle = cleanInner.substringBefore("episodes:").trim()
                            val altTitles = if (cleanInner.contains("alt titles")) cleanInner.substringAfter("alt titles").substringBefore("status").trim(' ', ':') else ""

                            val score = calculateMatchScore(distinctCandidates, mainTitle, altTitles, isDub, href)
                            if (score > bestScore) {
                                bestScore = score
                                bestSlug = href
                            }
                        }

                        if (bestSlug != null && bestScore >= 35) {
                            return@withContext bestSlug
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        null
    }

    private fun calculateMatchScore(candidateQueries: List<String>, title: String, altTitles: String, isDub: Boolean, href: String): Int {
        var maxScore = 0
        val combinedText = "$title $altTitles".lowercase()

        for (cand in candidateQueries) {
            val c = cand.lowercase().removeSuffix(" dub").trim()
            if (title.equals(c, ignoreCase = true)) {
                var score = 100
                if (isDub && (href.contains("-dub") || combinedText.contains("dub"))) score += 20
                return score
            }
            if (combinedText.contains(c)) {
                var score = 80 + ((c.length.toDouble() / title.length.coerceAtLeast(1).toDouble()) * 15).toInt().coerceAtMost(15)
                if (isDub && (href.contains("-dub") || combinedText.contains("dub"))) score += 15
                maxScore = maxOf(maxScore, score)
            }
            val candWords = c.split(" ").filter { it.length > 2 }
            if (candWords.isNotEmpty()) {
                val matchedWords = candWords.count { combinedText.contains(it) }
                var wordScore = (matchedWords * 70) / candWords.size
                if (isDub && (href.contains("-dub") || combinedText.contains("dub"))) wordScore += 10
                maxScore = maxOf(maxScore, wordScore)
            }
        }
        return maxScore
    }
}
