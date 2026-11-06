package com.lagradost.cloudstream3.ui.animebox.extractors

import android.util.Base64
import com.lagradost.cloudstream3.ui.animebox.api.AniListClient
import com.lagradost.cloudstream3.ui.animebox.api.AniZipClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

data class RareAnimeResult(
    val hlsUrl: String,
    val referer: String = "https://codedew.com/",
    val provider: String = "RareAnime",
    val title: String = ""
)

/**
 * 1:1 port of Anilili's RareAnimes provider:
 * - F4.Ua (RareAnimesProvider: search RSS, multi-post episode resolution, season stitching, zipper -> argon -> juicycodes)
 * - F4.Oa (RareAnimesParser: RSS item parser, multi-episode block parser, single/movie parser, candidate ranking)
 * - F4.D9 (Title normalization, season number extraction, Dice bigram similarity)
 * - F4.AbstractC0405l6 (JuicyCodes blob extractor & decoder)
 */
object RareAnimeHindiExtractor {

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val BASE_URL = "https://www.rareanimes.mov"

    private val JUICY_SYMBOLS = listOf('`', '%', '-', '+', '*', '$', '!', '_', '^', '=')

    // Regexes ported 1:1 from F4.Oa, F4.D9, and F4.AbstractC0405l6
    private val EPISODE_HEADER_REGEX = Pattern.compile(""">\s*(?:Episode|EP|Ep\.?)\s+0*(\d+)""", Pattern.CASE_INSENSITIVE)
    private val LANG_TAG_REGEX = Pattern.compile("""\b(Hindi|Tamil|Telugu)\b""", Pattern.CASE_INSENSITIVE)
    private val FINAL_SEASON_REGEX = Pattern.compile("""\bfinal\s+season\b""", Pattern.CASE_INSENSITIVE)
    private val STRIP_POST_SUFFIX_WITH_SEASON_REGEX = Pattern.compile(
        """\s*(?:[-–—:|]\s*)?\b(?:Season|Episodes?|Hindi|Tamil|Telugu|Bengali|Malayalam|Dubbed|Dual\s+Audio|Download|Watch|Complete|Uncut|Movie|Film|Series)\b""",
        Pattern.CASE_INSENSITIVE
    )
    private val STRIP_POST_SUFFIX_KEEP_SEASON_REGEX = Pattern.compile(
        """\s*(?:[-–—:|]\s*)?\b(?:Episodes?|Hindi|Tamil|Telugu|Bengali|Malayalam|Dubbed|Dual\s+Audio|Download|Watch|Complete|Uncut|Film|Series)\b""",
        Pattern.CASE_INSENSITIVE
    )
    private val STRIP_QUERY_SEASON_REGEX = Pattern.compile(
        """\s*[-–—:|(\[]?\s*\b(?:\d+(?:st|nd|rd|th)\s+)?(?:the\s+)?(?:final\s+)?season\b.*$""",
        Pattern.CASE_INSENSITIVE
    )
    private val ZIPPER_ANCHOR_REGEX = Pattern.compile(
        """<a\b[^>]*?href=["'](https?://(?:www\.)?codedew\.com/zipper/\?url=[^"']+)["'][^>]*>([\s\S]*?)</a>""",
        Pattern.CASE_INSENSITIVE
    )
    private val ENTRY_CONTENT_REGEX = Pattern.compile("""<div\b[^>]*\bentry-content\b[^>]*>""", Pattern.CASE_INSENSITIVE)
    private val ENTRY_END_REGEX = Pattern.compile("""herald-related|herald-single-tags|<footer\b""", Pattern.CASE_INSENSITIVE)
    private val ARGON_IFRAME_REGEX = Pattern.compile(
        """<iframe\b[^>]*?\bsrc=["'](https?://[a-z0-9.-]+/(?:embed|e)/[A-Za-z0-9_-]+)["']""",
        Pattern.CASE_INSENSITIVE
    )
    private val ARGON_DIRECT_REGEX = Pattern.compile(
        """https?://[a-z0-9.-]+/(?:embed|e)/[A-Za-z0-9_-]+""",
        Pattern.CASE_INSENSITIVE
    )
    private val RSS_ITEM_REGEX = Pattern.compile("""<item>([\s\S]*?)</item>""", Pattern.CASE_INSENSITIVE)
    private val RSS_LINK_REGEX = Pattern.compile("""<link>([\s\S]*?)</link>""", Pattern.CASE_INSENSITIVE)
    private val RSS_TITLE_REGEX = Pattern.compile("""<title>([\s\S]*?)</title>""", Pattern.CASE_INSENSITIVE)
    private val CDATA_REGEX = Regex("""<!\[CDATA\[|]]>""")

    private val SEASON_NUM_REGEX_1 = Pattern.compile("""\bSeason\s*0*(\d+)\b""", Pattern.CASE_INSENSITIVE)
    private val SEASON_NUM_REGEX_2 = Pattern.compile("""\b0*(\d+)(?:st|nd|rd|th)\s+Season\b""", Pattern.CASE_INSENSITIVE)
    private val SEASON_ROMAN_REGEX = Pattern.compile("""\b(II|III|IV|V|VI)\s*$""", Pattern.CASE_INSENSITIVE)
    private val SEASON_TRAILING_DIGIT_REGEX = Pattern.compile("""(?<![.#])\s([2-9])\s*$""")

    private val JUICY_CALL_REGEX = Pattern.compile("""_juicycodes\(([\s\S]*?)\);?""", Pattern.CASE_INSENSITIVE)
    private val JUICY_QUOTED_REGEX = Pattern.compile("""["']([^"']*)["']""")
    private val HLS_FILE_REGEX = Pattern.compile(""""file"\s*:\s*"((?:\\.|[^"])*?\.(?:m3u8|mp4)[^"]*)"""", Pattern.CASE_INSENSITIVE)
    private val JUICY_TOKEN_REGEX = Pattern.compile(""""token"\s*:\s*"([^"]+)"""")
    private val JUICY_PING_REGEX = Pattern.compile(""""ping"\s*:\s*"([^"]+)"""")

    data class ZipperLink(val url: String, val label: String)
    data class ParsedEpisode(val number: Int, val langLinks: Map<String, List<ZipperLink>>)
    data class RssCandidate(val url: String, val title: String)
    data class CachedAnime(val postTitle: String, val episodes: List<ParsedEpisode>, val timestamp: Long)

    private val animeEpisodesCache = ConcurrentHashMap<Int, CachedAnime>()
    private val pageHtmlCache = ConcurrentHashMap<String, String>()

    private fun fetchUrl(url: String, referer: String? = "$BASE_URL/"): String? {
        return try {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,*/*")
            if (!referer.isNullOrBlank()) {
                reqBuilder.header("Referer", referer)
            }
            AnililiHttpClient.client.newCall(reqBuilder.get().build()).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getPostHtml(url: String): String {
        pageHtmlCache[url]?.let { return it }
        val html = fetchUrl(url, "$BASE_URL/") ?: ""
        if (html.isNotEmpty()) {
            pageHtmlCache[url] = html
        }
        return html
    }

    // -------------------------------------------------------------------------
    // 1:1 port of F4.D9 string normalization & Dice similarity
    // -------------------------------------------------------------------------
    private fun unescapeHtml(raw: String): String {
        if (!raw.contains('&') && !raw.contains('\\')) return raw
        var s = raw
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
        s = Regex("""&#(\d+);""").replace(s) { m ->
            val code = m.groupValues[1].toIntOrNull()
            if (code != null) code.toChar().toString() else m.value
        }
        s = Regex("""&#x([0-9a-fA-F]+);""").replace(s) { m ->
            val code = m.groupValues[1].toIntOrNull(16)
            if (code != null) code.toChar().toString() else m.value
        }
        return s.replace("\\/", "/")
    }

    private fun stripHtmlTags(html: String): String {
        return unescapeHtml(html.replace(Regex("""<[^>]*>"""), " ").replace(Regex("""\s+"""), " ").trim())
    }

    private fun trimPunctuation(s: String): String {
        val trimChars = charArrayOf('-', '\u2013', '\u2014', ':', '|', '(', '[', ' ')
        return s.trim().trimEnd(*trimChars).trim()
    }

    private fun stripQuerySeason(title: String): String {
        val m = STRIP_QUERY_SEASON_REGEX.matcher(title)
        val replaced = if (m.find()) title.substring(0, m.start()) else title
        return trimPunctuation(replaced)
    }

    private fun stripPostSuffixWithSeason(postTitle: String): String {
        val m = STRIP_POST_SUFFIX_WITH_SEASON_REGEX.matcher(postTitle)
        val sub = if (m.find()) postTitle.substring(0, m.start()) else postTitle
        return trimPunctuation(sub)
    }

    private fun stripPostSuffixKeepSeason(postTitle: String): String {
        val m = STRIP_POST_SUFFIX_KEEP_SEASON_REGEX.matcher(postTitle)
        val sub = if (m.find()) postTitle.substring(0, m.start()) else postTitle
        return trimPunctuation(sub)
    }

    private fun normalizeAlphaNum(s: String): String {
        return s.lowercase(Locale.ROOT).replace(Regex("""[^a-z0-9]"""), "")
    }

    private fun diceRaw(a: String, b: String): Double {
        if (a.isBlank() || b.isBlank()) return 0.0
        if (a == b) return 1.0
        if (b.contains(a) || a.contains(b)) return 0.86
        if (a.length < 2 || b.length < 2) return 0.0
        val counts = HashMap<String, Int>()
        for (i in 0 until a.length - 1) {
            val bg = a.substring(i, i + 2)
            counts[bg] = (counts[bg] ?: 0) + 1
        }
        var matches = 0
        for (i in 0 until b.length - 1) {
            val bg = b.substring(i, i + 2)
            val c = counts[bg] ?: 0
            if (c > 0) {
                matches++
                counts[bg] = c - 1
            }
        }
        return (matches * 2.0) / ((a.length - 1) + (b.length - 1)).toDouble()
    }

    /**
     * 1:1 port of F4.D9.p(query, candidate)
     */
    private fun similarityScore(query: String, candidate: String): Double {
        val q = normalizeAlphaNum(query)
        val c = normalizeAlphaNum(candidate)
        val raw = diceRaw(q, c)
        if (q.isEmpty() || c.isEmpty() || q == c) return raw
        val lenRatio = minOf(q.length, c.length).toDouble() / maxOf(q.length, c.length).toDouble()
        return (lenRatio * 0.4 + 0.6) * raw
    }

    /**
     * 1:1 port of F4.D9.m(title)
     */
    private fun extractSeasonNumber(title: String): Int? {
        val m1 = SEASON_NUM_REGEX_1.matcher(title)
        if (m1.find()) return m1.group(1)?.toIntOrNull()
        val m2 = SEASON_NUM_REGEX_2.matcher(title)
        if (m2.find()) return m2.group(1)?.toIntOrNull()
        val trimmed = title.trim()
        val m3 = SEASON_ROMAN_REGEX.matcher(trimmed)
        if (m3.find()) {
            return when (m3.group(1)?.uppercase(Locale.ROOT)) {
                "II" -> 2
                "III" -> 3
                "IV" -> 4
                "V" -> 5
                "VI" -> 6
                else -> null
            }
        }
        val m4 = SEASON_TRAILING_DIGIT_REGEX.matcher(trimmed)
        if (m4.find()) return m4.group(1)?.toIntOrNull()
        return null
    }

    /**
     * 1:1 port of F4.Oa.i(queries, candidate)
     */
    private fun maxCandidateSimilarity(queries: List<String>, candidate: RssCandidate): Double {
        var maxSim = 0.0
        val strippedWithSeason = stripPostSuffixWithSeason(candidate.title)
        val strippedKeepSeason = stripPostSuffixKeepSeason(candidate.title)
        for (q in queries) {
            val s1 = similarityScore(q, strippedWithSeason)
            val s2 = similarityScore(q, strippedKeepSeason)
            val best = maxOf(s1, s2)
            if (best > maxSim) maxSim = best
        }
        return maxSim
    }

    /**
     * 1:1 port of F4.Oa.h(queries, candidates, expectSeries)
     */
    private fun filterAndOrderCandidates(
        queries: List<String>,
        candidates: List<RssCandidate>,
        expectSeries: Boolean
    ): List<RssCandidate> {
        if (queries.isEmpty() || candidates.isEmpty()) return emptyList()
        val unique = candidates.distinctBy { it.url }
        val passing = unique.filter { maxCandidateSimilarity(queries, it) >= 0.5 }
        if (passing.isEmpty()) return emptyList()

        val explicitSeason = if (expectSeries) {
            queries.firstNotNullOfOrNull { extractSeasonNumber(it) }
        } else null

        val finalSeasonMax = if (expectSeries && explicitSeason == null && queries.any { FINAL_SEASON_REGEX.matcher(it).find() }) {
            passing.mapNotNull { extractSeasonNumber(it.title) }.maxOrNull()
        } else null

        val hasExactBaseMatch = if (expectSeries) {
            queries.any { q ->
                val qNorm = normalizeAlphaNum(q)
                qNorm.isNotEmpty() && passing.any { c ->
                    qNorm == normalizeAlphaNum(stripPostSuffixWithSeason(c.title))
                }
            }
        } else false

        val targetSeason: Int? = when {
            explicitSeason != null -> explicitSeason
            finalSeasonMax != null -> finalSeasonMax
            hasExactBaseMatch && passing.any { extractSeasonNumber(it.title) == 1 } -> 1
            else -> null
        }

        // Sort 1:1 with F4.C0482r0 + F4.C0469q0:
        // 1. seasonRank ascending (0 = exact season match, 1 = no season on post when targetSeason == 1, 2 = mismatch)
        // 2. maxCandidateSimilarity descending
        return passing.sortedWith(
            Comparator { a, b ->
                val rankA = if (targetSeason == null) {
                    0
                } else {
                    val sA = extractSeasonNumber(a.title)
                    when {
                        sA == targetSeason -> 0
                        sA == null && targetSeason == 1 -> 1
                        else -> 2
                    }
                }
                val rankB = if (targetSeason == null) {
                    0
                } else {
                    val sB = extractSeasonNumber(b.title)
                    when {
                        sB == targetSeason -> 0
                        sB == null && targetSeason == 1 -> 1
                        else -> 2
                    }
                }
                if (rankA != rankB) {
                    rankA.compareTo(rankB)
                } else {
                    maxCandidateSimilarity(queries, b).compareTo(maxCandidateSimilarity(queries, a))
                }
            }
        )
    }

    // -------------------------------------------------------------------------
    // 1:1 port of F4.Oa.e, F4.Oa.b, F4.Oa.f, F4.Oa.g (Post HTML episode parsers)
    // -------------------------------------------------------------------------
    private fun lettersOnlyLower(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s.lowercase(Locale.ROOT)) {
            if (c.isLetter()) sb.append(c)
        }
        return sb.toString()
    }

    private fun extractAllZipperLinks(html: String): List<ZipperLink> {
        val list = mutableListOf<ZipperLink>()
        val m = ZIPPER_ANCHOR_REGEX.matcher(html)
        while (m.find()) {
            val url = m.group(1)?.trim() ?: continue
            val label = stripHtmlTags(m.group(2) ?: "")
            if (url.isNotEmpty()) {
                list.add(ZipperLink(url, label))
            }
        }
        return list
    }

    private fun extractMultiQualityZipperLinks(html: String): List<ZipperLink> {
        return extractAllZipperLinks(html).filter { link ->
            val norm = lettersOnlyLower(link.label)
            norm.contains("multiquality") || norm.contains("multquality")
        }
    }

    /**
     * 1:1 port of F4.Oa.f(postHtml) — parses multi-episode blocks in a post.
     */
    private fun parseMultiEpisodeBlocks(postHtml: String): List<ParsedEpisode> {
        data class EpHeader(val epNum: Int, val startIndex: Int)
        val headers = mutableListOf<EpHeader>()
        val m = EPISODE_HEADER_REGEX.matcher(postHtml)
        while (m.find()) {
            val num = m.group(1)?.toIntOrNull() ?: continue
            headers.add(EpHeader(num, m.start()))
        }
        if (headers.isEmpty()) return emptyList()

        val result = mutableListOf<ParsedEpisode>()
        val seenEps = HashSet<Int>()

        for (i in headers.indices) {
            val curr = headers[i]
            val nextStart = if (i + 1 < headers.size) headers[i + 1].startIndex else postHtml.length
            val chunk = postHtml.substring(curr.startIndex, nextStart)

            data class LangPos(val lang: String, val startIndex: Int)
            val langMatches = mutableListOf<LangPos>()
            val lm = LANG_TAG_REGEX.matcher(chunk)
            while (lm.find()) {
                val lang = lm.group(1)?.lowercase(Locale.ROOT) ?: continue
                langMatches.add(LangPos(lang, lm.start()))
            }

            val langMap = LinkedHashMap<String, List<ZipperLink>>()
            if (langMatches.isEmpty()) {
                val links = extractAllZipperLinks(chunk)
                if (links.isNotEmpty()) {
                    langMap["hindi"] = links
                }
            } else {
                for (j in langMatches.indices) {
                    val lCurr = langMatches[j]
                    val lNext = if (j + 1 < langMatches.size) langMatches[j + 1].startIndex else chunk.length
                    val lChunk = chunk.substring(lCurr.startIndex, lNext)
                    val links = extractAllZipperLinks(lChunk)
                    if (links.isNotEmpty()) {
                        val existing = langMap[lCurr.lang]
                        langMap[lCurr.lang] = if (existing != null) existing + links else links
                    }
                }
            }

            if (langMap.isNotEmpty() && seenEps.add(curr.epNum)) {
                result.add(ParsedEpisode(curr.epNum, langMap))
            }
        }
        return result
    }

    /**
     * 1:1 port of F4.Oa.g(postHtml) — parses a single-episode / movie post.
     */
    private fun parseSingleMoviePost(postHtml: String): ParsedEpisode? {
        var content = postHtml
        val startM = ENTRY_CONTENT_REGEX.matcher(postHtml)
        if (startM.find()) {
            val contentStart = startM.end()
            val endM = ENTRY_END_REGEX.matcher(postHtml)
            val contentEnd = if (endM.find(contentStart)) endM.start() else postHtml.length
            content = postHtml.substring(contentStart, contentEnd)
        }

        data class LangPos(val lang: String, val startIndex: Int)
        val langMatches = mutableListOf<LangPos>()
        val lm = LANG_TAG_REGEX.matcher(content)
        while (lm.find()) {
            val lang = lm.group(1)?.lowercase(Locale.ROOT) ?: continue
            langMatches.add(LangPos(lang, lm.start()))
        }

        val langMap = LinkedHashMap<String, List<ZipperLink>>()
        if (langMatches.isEmpty()) {
            val links = extractMultiQualityZipperLinks(content).ifEmpty { extractAllZipperLinks(content) }
            if (links.isNotEmpty()) {
                langMap["hindi"] = links
            }
        } else {
            for (j in langMatches.indices) {
                val lCurr = langMatches[j]
                val lNext = if (j + 1 < langMatches.size) langMatches[j + 1].startIndex else content.length
                val lChunk = content.substring(lCurr.startIndex, lNext)
                val links = extractMultiQualityZipperLinks(lChunk).ifEmpty { extractAllZipperLinks(lChunk) }
                if (links.isNotEmpty()) {
                    val existing = langMap[lCurr.lang]
                    langMap[lCurr.lang] = if (existing != null) existing + links else links
                }
            }
        }

        return if (langMap.isNotEmpty()) ParsedEpisode(1, langMap) else null
    }

    private fun parseRssItems(rssXml: String): List<RssCandidate> {
        val list = mutableListOf<RssCandidate>()
        val im = RSS_ITEM_REGEX.matcher(rssXml)
        while (im.find()) {
            val block = im.group(1) ?: continue
            val tm = RSS_TITLE_REGEX.matcher(block)
            val lm = RSS_LINK_REGEX.matcher(block)
            if (!tm.find() || !lm.find()) continue
            val rawTitle = tm.group(1)?.replace(CDATA_REGEX, "") ?: ""
            val title = stripHtmlTags(rawTitle)
            val link = lm.group(1)?.replace(CDATA_REGEX, "")?.trim() ?: ""
            if (title.isNotEmpty() && link.startsWith("http")) {
                list.add(RssCandidate(link, title))
            }
        }
        return list
    }

    /**
     * 1:1 port of F4.AbstractC0405l6.b(blob)
     * Decodes JuicyCodes obfuscation as used on codedew / argon embed player.
     */
    fun decodeJuicyCodes(blob: String): String? {
        if (blob.length <= 3) return null
        try {
            val offsetStr = blob.takeLast(3).map { (it - 'd').toString() }.joinToString("")
            val offset = offsetStr.toIntOrNull() ?: return null

            var b64 = blob.dropLast(3).replace('_', '+').replace('-', '/')
            val padCount = (b64.length + 3) % 4
            val padding = "===".substring(padCount)
            b64 += padding

            val rawBytes = Base64.decode(b64, Base64.DEFAULT)
            val rawStr = String(rawBytes, Charsets.ISO_8859_1)

            val digits = StringBuilder(rawStr.length)
            for (c in rawStr) {
                val idx = JUICY_SYMBOLS.indexOf(c)
                if (idx < 0) return null
                digits.append(idx)
            }
            val digitStream = digits.toString()
            if (digitStream.length % 4 != 0) return null

            val decodedChars = StringBuilder(digitStream.length / 4)
            for (i in 0 until digitStream.length step 4) {
                val chunk = digitStream.substring(i, i + 4)
                val code = (chunk.toInt() % 1000) - offset
                decodedChars.append(code.toChar())
            }
            return decodedChars.toString()
        } catch (_: Exception) {
            return null
        }
    }

    private data class MediaMeta(
        val titles: List<String>,
        val isMovieOrSingleEp: Boolean,
        val expectedEpisodes: Int?
    )

    private suspend fun getMediaMeta(anilistId: Int, animeTitle: String?): MediaMeta {
        val rawTitles = mutableListOf<String>()
        var format = ""
        var episodesCount: Int? = null

        if (anilistId > 0) {
            try {
                val details = AniListClient.getAnimeDetails(anilistId)
                if (details != null) {
                    val media = JSONObject(details).optJSONObject("data")?.optJSONObject("Media")
                    if (media != null) {
                        format = media.optString("format", "")
                        val eps = media.optInt("episodes", 0)
                        val nextEp = media.optJSONObject("nextAiringEpisode")?.optInt("episode", 0) ?: 0
                        episodesCount = when {
                            eps > 0 -> eps
                            nextEp > 1 -> nextEp - 1
                            else -> null
                        }
                        val t = media.optJSONObject("title")
                        for (k in listOf("english", "romaji", "native")) {
                            t?.optString(k, "")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                                if (!rawTitles.contains(it)) rawTitles.add(it)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }

            try {
                val aniZip = AniZipClient.getAniZipJson(anilistId)
                val titlesObj = aniZip?.optJSONObject("titles")
                if (titlesObj != null) {
                    for (k in listOf("en", "x-jat")) {
                        titlesObj.optString(k, "")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                            if (!rawTitles.contains(it)) rawTitles.add(it)
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        if (!animeTitle.isNullOrBlank() && !rawTitles.contains(animeTitle.trim())) {
            rawTitles.add(0, animeTitle.trim())
        }

        val isMovieOrSingle = format.equals("MOVIE", ignoreCase = true) || episodesCount == 1
        return MediaMeta(rawTitles, isMovieOrSingle, episodesCount)
    }

    /**
     * 1:1 port of F4.Ua.h(Media, maxPages) — builds search query variants and fetches RSS candidates.
     */
    private fun searchCandidates(meta: MediaMeta, maxPages: Int): List<RssCandidate> {
        if (meta.titles.isEmpty()) return emptyList()

        val queryVariants = mutableListOf<String>()
        val seenLower = HashSet<String>()
        for (title in meta.titles) {
            val variants = listOf(
                stripQuerySeason(title),
                title.substringBefore(':').trim(),
                title
            )
            for (v in variants) {
                if (v.length >= 3 && seenLower.add(v.lowercase(Locale.ROOT))) {
                    queryVariants.add(v)
                }
            }
        }

        val allItems = mutableListOf<RssCandidate>()
        for (q in queryVariants.take(4)) {
            val encoded = URLEncoder.encode(q, "UTF-8")
            for (page in 1..maxPages) {
                val rssUrl = if (page <= 1) {
                    "$BASE_URL/search/$encoded/feed/rss2/"
                } else {
                    "$BASE_URL/search/$encoded/feed/rss2/?paged=$page"
                }
                val xml = fetchUrl(rssUrl, "$BASE_URL/") ?: break
                val items = parseRssItems(xml)
                if (items.isEmpty()) break
                allItems.addAll(items)
            }
        }

        return filterAndOrderCandidates(
            queries = meta.titles,
            candidates = allItems,
            expectSeries = !meta.isMovieOrSingleEp
        )
    }

    /**
     * 1:1 port of F4.Ua.c(Media) — resolves the episode list for an anime,
     * including single/movie fallback and multi-season post stitching.
     */
    private suspend fun resolveAnimeEpisodes(anilistId: Int, animeTitle: String?): CachedAnime? {
        if (anilistId > 0) {
            animeEpisodesCache[anilistId]?.let { cached ->
                if (System.currentTimeMillis() - cached.timestamp < 30 * 60 * 1000L) {
                    return cached
                }
            }
        }

        val meta = getMediaMeta(anilistId, animeTitle)
        val candidates = searchCandidates(meta, maxPages = 1)
        if (candidates.isEmpty()) return null

        var bestSinglePost: CachedAnime? = null
        for (cand in candidates.take(6)) {
            val html = getPostHtml(cand.url)
            if (html.isEmpty()) continue
            val eps = parseMultiEpisodeBlocks(html)
            if (eps.isNotEmpty()) {
                bestSinglePost = CachedAnime(cand.title, eps, System.currentTimeMillis())
                break
            }
        }

        val expectedEps = meta.expectedEpisodes
        val needsMoreOrMovieFallback = bestSinglePost == null ||
            (expectedEps != null && bestSinglePost.episodes.size < expectedEps - 2)

        if (needsMoreOrMovieFallback) {
            if (bestSinglePost == null && meta.isMovieOrSingleEp) {
                for (cand in candidates.take(6)) {
                    val html = getPostHtml(cand.url)
                    if (html.isEmpty()) continue
                    val singleEp = parseSingleMoviePost(html)
                    if (singleEp != null) {
                        bestSinglePost = CachedAnime(cand.title, listOf(singleEp), System.currentTimeMillis())
                        break
                    }
                }
            } else {
                // Multi-season post stitching (F4.Ua.c lines 235-315)
                val baseTitle = stripPostSuffixWithSeason(bestSinglePost?.postTitle ?: candidates.first().title)
                val moreCandidates = (candidates + searchCandidates(meta, maxPages = 3)).distinctBy { it.url }
                val matchingBase = moreCandidates.filter {
                    stripPostSuffixWithSeason(it.title).equals(baseTitle, ignoreCase = true)
                }
                val seasonPosts = matchingBase
                    .mapNotNull { c -> extractSeasonNumber(c.title)?.let { sNum -> sNum to c } }
                    .distinctBy { it.first }
                    .sortedBy { it.first }

                val contiguousSeasons = mutableListOf<Pair<Int, RssCandidate>>()
                for (pair in seasonPosts) {
                    if (pair.first == contiguousSeasons.size + 1) {
                        contiguousSeasons.add(pair)
                    }
                }

                if (contiguousSeasons.size >= 2) {
                    val stitched = mutableListOf<ParsedEpisode>()
                    var runningEpNum = 1
                    for ((_, sCand) in contiguousSeasons) {
                        val html = getPostHtml(sCand.url)
                        val eps = parseMultiEpisodeBlocks(html).sortedBy { it.number }
                        for (ep in eps) {
                            stitched.add(ParsedEpisode(runningEpNum, ep.langLinks))
                            runningEpNum++
                        }
                    }
                    val prevSize = bestSinglePost?.episodes?.size ?: 0
                    if (stitched.size > prevSize) {
                        val trimmed = if (expectedEps != null) stitched.take(expectedEps) else stitched
                        bestSinglePost = CachedAnime(
                            "${contiguousSeasons.size} season posts",
                            trimmed,
                            System.currentTimeMillis()
                        )
                    }
                }
            }
        }

        // If multi-episode parsing didn't find Episode headers, also check single/movie parser for Episode 1
        if (bestSinglePost == null) {
            for (cand in candidates.take(4)) {
                val html = getPostHtml(cand.url)
                if (html.isEmpty()) continue
                val singleEp = parseSingleMoviePost(html)
                if (singleEp != null) {
                    bestSinglePost = CachedAnime(cand.title, listOf(singleEp), System.currentTimeMillis())
                    break
                }
            }
        }

        if (bestSinglePost != null && anilistId > 0) {
            animeEpisodesCache[anilistId] = bestSinglePost
        }
        return bestSinglePost
    }

    /**
     * Resolves a codedew zipper URL -> Argon embed (with iframe Sec-Fetch-* headers + JuicyCodes ping activation) -> HLS .m3u8 URL.
     */
    private fun resolveZipperToHls(zipperUrl: String, animeTitle: String?): RareAnimeResult? {
        val zipperHtml = fetchUrl(zipperUrl, "$BASE_URL/") ?: return null
        if (zipperHtml.isEmpty()) return null

        val iframeM = ARGON_IFRAME_REGEX.matcher(zipperHtml)
        val argonEmbedUrl = if (iframeM.find()) {
            iframeM.group(1) ?: ""
        } else {
            val directM = ARGON_DIRECT_REGEX.matcher(zipperHtml)
            if (directM.find()) directM.group() ?: "" else ""
        }
        if (argonEmbedUrl.isEmpty()) return null

        val argonOrigin = try {
            val uri = URI(argonEmbedUrl)
            "${uri.scheme}://${uri.authority}"
        } catch (_: Exception) {
            "https://argon.razorshell.space"
        }

        // Fetch Argon embed with iframe Sec-Fetch-* headers so JuicyCodes session is not flagged as direct scrape
        var embedHtml = ""
        val cookiePairs = mutableListOf<String>()
        var xsrfToken = ""
        try {
            val embedReq = Request.Builder()
                .url(argonEmbedUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Referer", "https://codedew.com/")
                .header("Sec-Fetch-Dest", "iframe")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "cross-site")
                .header("Upgrade-Insecure-Requests", "1")
                .get()
                .build()

            AnililiHttpClient.client.newCall(embedReq).execute().use { resp ->
                if (!resp.isSuccessful) return null
                for (sc in resp.headers("Set-Cookie")) {
                    val pair = sc.substringBefore(';').trim()
                    if (pair.isNotEmpty()) {
                        cookiePairs.add(pair)
                        if (pair.startsWith("XSRF-TOKEN=")) {
                            xsrfToken = pair.removePrefix("XSRF-TOKEN=")
                        }
                    }
                }
                embedHtml = resp.body?.string().orEmpty()
            }
        } catch (_: Exception) {
            return null
        }
        if (embedHtml.isEmpty()) return null

        // Activate Argon/JuicyCodes session via POST /api/videos/.../ping (returns HTTP 204 and unlocks groovy.monster .m3u8)
        try {
            val tokenM = JUICY_TOKEN_REGEX.matcher(embedHtml)
            val pingM = JUICY_PING_REGEX.matcher(embedHtml)
            if (tokenM.find() && pingM.find()) {
                val token = tokenM.group(1) ?: ""
                val rawPing = (pingM.group(1) ?: "").replace("""\/""", "/")
                if (token.isNotEmpty() && rawPing.isNotEmpty()) {
                    val pingUrl = if (rawPing.startsWith("http")) rawPing else "$argonOrigin$rawPing"
                    val jsonBody = """{"_token":"$token","__type":"dawn","pingID":"4a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d"}"""
                    val pingReqBuilder = Request.Builder()
                        .url(pingUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "*/*")
                        .header("Accept-Language", "en-US,en;q=0.9")
                        .header("Referer", argonEmbedUrl)
                        .header("Origin", argonOrigin)
                        .header("Sec-Fetch-Site", "same-origin")
                        .header("Sec-Fetch-Mode", "cors")
                        .header("Sec-Fetch-Dest", "empty")
                    if (cookiePairs.isNotEmpty()) {
                        pingReqBuilder.header("Cookie", cookiePairs.joinToString("; "))
                    }
                    if (xsrfToken.isNotEmpty()) {
                        val decodedXsrf = runCatching { URLDecoder.decode(xsrfToken, "UTF-8") }.getOrDefault(xsrfToken)
                        pingReqBuilder.header("X-XSRF-TOKEN", decodedXsrf)
                    }
                    val pingReq = pingReqBuilder
                        .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()
                    AnililiHttpClient.client.newCall(pingReq).execute().close()
                }
            }
        } catch (_: Exception) {
        }

        val jcM = JUICY_CALL_REGEX.matcher(embedHtml)
        if (!jcM.find()) return null
        val jcInner = jcM.group(1) ?: return null

        val fullBlob = StringBuilder()
        val qM = JUICY_QUOTED_REGEX.matcher(jcInner)
        while (qM.find()) {
            fullBlob.append(qM.group(1) ?: "")
        }
        if (fullBlob.isEmpty()) return null

        val decodedJuicy = decodeJuicyCodes(fullBlob.toString()) ?: return null
        // _juicycodes often decodes to a Dean Edwards eval(function(p,a,c,k,e,d)...) script
        val unpackedJs = if (AnililiHttpClient.isPackedJs(decodedJuicy)) {
            AnililiHttpClient.unpackJs(decodedJuicy) ?: decodedJuicy
        } else {
            decodedJuicy
        }

        val fileM = HLS_FILE_REGEX.matcher(unpackedJs)
        val m3u8Url = if (fileM.find()) {
            (fileM.group(1) ?: "").replace("""\/""", "/")
        } else {
            val fileM2 = HLS_FILE_REGEX.matcher(decodedJuicy)
            if (fileM2.find()) (fileM2.group(1) ?: "").replace("""\/""", "/") else ""
        }

        if (m3u8Url.isEmpty() || !m3u8Url.startsWith("http")) return null



        return RareAnimeResult(
            hlsUrl = m3u8Url,
            referer = "$argonOrigin/",
            provider = "RareAnime",
            title = animeTitle ?: ""
        )
    }

    suspend fun extract(
        anilistId: Int,
        episodeNum: Int,
        animeTitle: String? = null
    ): RareAnimeResult? = withContext(Dispatchers.IO) {
        if (anilistId <= 0 && animeTitle.isNullOrBlank()) return@withContext null

        val cachedAnime = resolveAnimeEpisodes(anilistId, animeTitle) ?: return@withContext null
        val epObj = cachedAnime.episodes.firstOrNull { it.number == episodeNum }
            ?: if (episodeNum == 1 && cachedAnime.episodes.size == 1) cachedAnime.episodes.first() else null
            ?: return@withContext null

        // Language priority 1:1 with F4.Ua.f3623e: hindi -> tamil -> telugu -> any
        val orderedLangs = listOf("hindi", "tamil", "telugu") +
            epObj.langLinks.keys.filter { it !in listOf("hindi", "tamil", "telugu") }

        for (lang in orderedLangs) {
            val links = epObj.langLinks[lang] ?: continue
            if (links.isEmpty()) continue

            // Prefer MultiQuality links first, then Watch/Stream links, then any zipper link
            val sortedLinks = links.sortedWith(
                compareByDescending<ZipperLink> {
                    val norm = lettersOnlyLower(it.label)
                    norm.contains("multiquality") || norm.contains("multquality")
                }.thenByDescending {
                    val norm = lettersOnlyLower(it.label)
                    norm.contains("watch") || norm.contains("stream")
                }
            )

            for (link in sortedLinks) {
                val resolved = resolveZipperToHls(link.url, animeTitle)
                if (resolved != null && resolved.hlsUrl.isNotEmpty()) {
                    return@withContext resolved
                }
            }
        }

        null
    }
}
