package com.lagradost.cloudstream3.ui.animebox.extractors
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class HahoMoeStream(
    val streamUrl: String,
    val referer: String = "https://haho.moe/",
    val title: String = "",
    val quality: String = "360p",
    val isDub: Boolean = false
)

object HahoMoeExtractor {

    private const val BASE_URL = "https://haho.moe"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val client by lazy {
        AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Extracts video stream from Haho.moe for a given anime title and episode number.
     * Supports both Japanese original (sub) and English dub versions.
     */
    suspend fun getStream(
        title: String,
        episodeNum: Int,
        preferDub: Boolean = false,
        searchTitles: List<String> = emptyList()
    ): HahoMoeStream? = withContext(Dispatchers.IO) {
        val slugs = resolveAnimeSlugs(title, searchTitles, preferDub)
        if (slugs.isEmpty()) return@withContext null

        var fallbackStream: HahoMoeStream? = null
        for (slug in slugs) {
            val stream = getStreamForSlug(slug, episodeNum, preferDub)
            if (stream != null && stream.streamUrl.isNotBlank()) {
                if (!preferDub || stream.isDub) {
                    return@withContext stream
                }
                if (fallbackStream == null) {
                    fallbackStream = stream
                }
            }
        }
        fallbackStream
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&#039;", "'")
            .replace("&#39;", "'")
            .replace("&quot;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
    }

    private fun normalizeTitleForMatch(input: String): String {
        return decodeHtmlEntities(input)
            .lowercase()
            .replace(Regex("""\b(the animation|ova|special|uncensored|hentai)\b"""), " ")
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    /**
     * Searches Haho.moe and finds the matching anime slugs/IDs.
     * Accurately distinguishes between sequels (e.g. Tsumamigui vs Tsumamigui 3) and matches
     * both primary Romaji titles and <small> English/alternative titles on Haho.moe cards.
     */
    private fun resolveAnimeSlugs(
        title: String,
        searchTitles: List<String>,
        preferDub: Boolean = false
    ): List<String> {
        val rawPool = mutableListOf<String>()
        for (t in (listOf(title) + searchTitles)) {
            val trimmed = t.trim()
            if (trimmed.isNotEmpty()) {
                rawPool.add(trimmed)
                // Add base title before ':' or ' - ' if it has a subtitle
                if (trimmed.contains(":")) {
                    val beforeColon = trimmed.substringBefore(":").trim()
                    if (beforeColon.length >= 3) rawPool.add(beforeColon)
                }
                if (trimmed.contains(" - ")) {
                    val beforeDash = trimmed.substringBefore(" - ").trim()
                    if (beforeDash.length >= 3) rawPool.add(beforeDash)
                }
            }
        }
        val allTitles = rawPool.distinct()
        val matchedSlugs = LinkedHashSet<String>()

        for (rawQuery in allTitles) {
            val cleanQuery = normalizeTitleForMatch(rawQuery)
            if (cleanQuery.length < 2) continue

            val hasSequelNumber = Regex("""\b(\d+)\b""").find(cleanQuery)?.groupValues?.get(1)
            val queriesToTry = if (preferDub) {
                listOf("$cleanQuery audio:english", cleanQuery)
            } else {
                listOf(cleanQuery)
            }

            for (qTerm in queriesToTry) {
                val searchUrl = "$BASE_URL/anime?q=${java.net.URLEncoder.encode(qTerm, "UTF-8")}"
                try {
                    val req = Request.Builder()
                        .url(searchUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", "$BASE_URL/")
                        .build()

                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use
                        val html = resp.body?.string().orEmpty()

                        // Match each <li class="anime-..."> card to capture slug, main title, and <small> alt title
                        val liPattern = Pattern.compile(
                            """<li[^>]*class=["'][^"']*anime-[^"']*["'][^>]*>(.*?)</li>""",
                            Pattern.CASE_INSENSITIVE or Pattern.DOTALL
                        )
                        val liMatcher = liPattern.matcher(html)
                        val candidates = mutableListOf<Pair<String, List<String>>>()

                        while (liMatcher.find()) {
                            val liHtml = liMatcher.group(1).orEmpty()
                            val slugMatch = Pattern.compile(
                                """href=["'](?:https?://haho\.moe)?/anime/([a-zA-Z0-9]+)["']""",
                                Pattern.CASE_INSENSITIVE
                            ).matcher(liHtml)
                            if (!slugMatch.find()) continue
                            val slug = slugMatch.group(1).orEmpty()
                            if (slug.isEmpty()) continue

                            val cardTitles = mutableListOf<String>()
                            val titleAttrMatch = Pattern.compile("""title=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(liHtml)
                            while (titleAttrMatch.find()) {
                                val tVal = decodeHtmlEntities(titleAttrMatch.group(1).orEmpty()).trim()
                                if (tVal.isNotEmpty()) cardTitles.add(tVal)
                            }
                            val spanMatch = Pattern.compile("""<span class=["']text-primary["']>([^<]+)</span>""", Pattern.CASE_INSENSITIVE).matcher(liHtml)
                            while (spanMatch.find()) {
                                val sVal = decodeHtmlEntities(spanMatch.group(1).orEmpty()).trim()
                                if (sVal.isNotEmpty()) cardTitles.add(sVal)
                            }
                            val smallMatch = Pattern.compile("""<small[^>]*>([^<]+)</small>""", Pattern.CASE_INSENSITIVE).matcher(liHtml)
                            while (smallMatch.find()) {
                                val smVal = decodeHtmlEntities(smallMatch.group(1).orEmpty()).trim()
                                if (smVal.isNotEmpty()) cardTitles.add(smVal)
                            }
                            if (cardTitles.isNotEmpty()) {
                                candidates.add(slug to cardTitles.distinct())
                            }
                        }

                        if (candidates.isEmpty()) {
                            val entryPattern = Pattern.compile(
                                """href=["'](?:https?://haho\.moe)?/anime/([a-zA-Z0-9]+)["'][^>]*title=["']([^"']+)["']""",
                                Pattern.CASE_INSENSITIVE
                            )
                            val matcher = entryPattern.matcher(html)
                            while (matcher.find()) {
                                val slug = matcher.group(1).orEmpty()
                                val cardTitle = decodeHtmlEntities(matcher.group(2).orEmpty()).trim()
                                if (slug.isNotEmpty() && cardTitle.isNotEmpty()) {
                                    candidates.add(slug to listOf(cardTitle))
                                }
                            }
                        }

                        // 1. Exact normalized match on any card title (Romaji or <small> English title)
                        for ((slug, cTitles) in candidates) {
                            val normalizedCards = cTitles.map { normalizeTitleForMatch(it) }.filter { it.isNotEmpty() }
                            if (normalizedCards.any { it == cleanQuery }) {
                                matchedSlugs.add(slug)
                            }
                        }

                        // 2. Substring + sequel-number aware match
                        for ((slug, cTitles) in candidates) {
                            val normalizedCards = cTitles.map { normalizeTitleForMatch(it) }.filter { it.isNotEmpty() }
                            for (cardClean in normalizedCards) {
                                val cardSequelNumber = Regex("""\b(\d+)\b""").find(cardClean)?.groupValues?.get(1)
                                if (hasSequelNumber != null) {
                                    if (cardSequelNumber == hasSequelNumber && (cardClean.contains(cleanQuery) || cleanQuery.contains(cardClean))) {
                                        matchedSlugs.add(slug)
                                    }
                                } else {
                                    if (cardSequelNumber == null && (cardClean.contains(cleanQuery) || cleanQuery.contains(cardClean))) {
                                        matchedSlugs.add(slug)
                                    }
                                }
                            }
                        }

                        // 3. Relaxed fallback if single candidate or non-sequel candidate when searching specific title
                        if (matchedSlugs.isEmpty() && candidates.isNotEmpty() && hasSequelNumber == null) {
                            val nonSequel = candidates.firstOrNull { (_, cTitles) ->
                                cTitles.none { Regex("""\b(\d+)\b""").containsMatchIn(it) }
                            }
                            if (nonSequel != null) {
                                matchedSlugs.add(nonSequel.first)
                            }
                        }
                    }
                } catch (_: Exception) {}
                if (matchedSlugs.isNotEmpty() && !preferDub) break
            }
            if (matchedSlugs.isNotEmpty() && !preferDub) break
        }
        return matchedSlugs.toList()
    }

    private fun isEnglishDubLabel(labelHtml: String): Boolean {
        val audioPart = labelHtml.substringBefore("/")
        return audioPart.contains("Audio: English", ignoreCase = true) ||
            audioPart.contains("flag-icon-gb", ignoreCase = true) ||
            audioPart.contains("flag-icon-us", ignoreCase = true) ||
            labelHtml.contains("[Dub]", ignoreCase = true) ||
            labelHtml.contains("(Dub)", ignoreCase = true)
    }

    private fun extractStreamFromEmbed(embedUrl: String, epUrl: String, isDub: Boolean): HahoMoeStream? {
        try {
            val embedReq = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", epUrl)
                .build()

            client.newCall(embedReq).execute().use { embedResp ->
                if (!embedResp.isSuccessful) return null
                val embedHtml = embedResp.body?.string().orEmpty()

                // 1. Collect all <source> tags and pick the highest resolution (1080p > 720p > 480p > 360p)
                val sourceTagPattern = Pattern.compile("""<source\s+([^>]+)>""", Pattern.CASE_INSENSITIVE)
                val tagMatcher = sourceTagPattern.matcher(embedHtml)
                val sources = mutableListOf<Pair<String, String>>() // (url, qualityTitle)
                while (tagMatcher.find()) {
                    val attrs = tagMatcher.group(1).orEmpty()
                    val srcMatch = Pattern.compile("""src=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(attrs)
                    val titleMatch = Pattern.compile("""(?:title|label|res|size)=["']([^"']*)["']""", Pattern.CASE_INSENSITIVE).matcher(attrs)
                    if (srcMatch.find()) {
                        val sUrl = decodeHtmlEntities(srcMatch.group(1).orEmpty()).trim()
                        val sQual = if (titleMatch.find()) titleMatch.group(1).orEmpty().ifBlank { "360p" } else "360p"
                        if (sUrl.isNotEmpty()) {
                            sources.add(sUrl to sQual)
                        }
                    }
                }

                if (sources.isNotEmpty()) {
                    val bestSource = sources.maxByOrNull { (_, q) ->
                        Regex("""(\d+)""").find(q)?.groupValues?.get(1)?.toIntOrNull() ?: 360
                    } ?: sources.first()
                    return HahoMoeStream(
                        streamUrl = bestSource.first,
                        referer = embedUrl,
                        quality = bestSource.second,
                        isDub = isDub
                    )
                }

                // 2. Fallback: direct .mp4 or .m3u8 URLs in script/html
                val directUrlMatcher = Pattern.compile("""https?://[^\s"'<>]+\.(?:mp4|m3u8)[^\s"'<>]*""").matcher(embedHtml)
                if (directUrlMatcher.find()) {
                    return HahoMoeStream(
                        streamUrl = decodeHtmlEntities(directUrlMatcher.group(0).orEmpty()),
                        referer = embedUrl,
                        isDub = isDub
                    )
                }
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Fetches stream from episode page on Haho.moe.
     */
    suspend fun getStreamForSlug(
        slug: String,
        episodeNum: Int,
        preferDub: Boolean = false
    ): HahoMoeStream? = withContext(Dispatchers.IO) {
        val epUrl = "$BASE_URL/anime/$slug/$episodeNum"
        try {
            val req = Request.Builder()
                .url(epUrl)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_URL/anime/$slug")
                .build()

            val candidateEmbeds = mutableListOf<Pair<String, Boolean>>() // (embedUrl, isDub)

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val html = resp.body?.string().orEmpty()

                // Find video variations in the "Videos" dropdown regardless of attribute order
                val variantPattern = Pattern.compile(
                    """<a\s+[^>]*href=["']([^"']*\?v=([a-zA-Z0-9_-]+))["'][^>]*>(.*?)</a>""",
                    Pattern.CASE_INSENSITIVE or Pattern.DOTALL
                )
                val vMatcher = variantPattern.matcher(html)
                val variants = mutableListOf<Triple<String, String, String>>() // (fullUrl, vCode, labelHtml)
                while (vMatcher.find()) {
                    variants.add(Triple(vMatcher.group(1).orEmpty(), vMatcher.group(2).orEmpty(), vMatcher.group(3).orEmpty()))
                }

                val dubVariants = variants.filter { (_, _, label) -> isEnglishDubLabel(label) }
                val subVariants = variants.filter { (_, _, label) ->
                    !isEnglishDubLabel(label) && (
                        label.substringBefore("/").contains("Audio: Japanese", ignoreCase = true) ||
                        label.substringBefore("/").contains("flag-icon-jp", ignoreCase = true)
                    )
                }

                // Check active mirror metadata on page in case there's only 1 video without dropdown items
                val mirrorMetaMatcher = Pattern.compile(
                    """<div\s+class=["'][^"']*mirror-meta[^"']*["'][^>]*>(.*?)</div>""",
                    Pattern.CASE_INSENSITIVE or Pattern.DOTALL
                ).matcher(html)
                val activePageHasDub = if (mirrorMetaMatcher.find()) {
                    isEnglishDubLabel(mirrorMetaMatcher.group(1).orEmpty())
                } else {
                    html.contains("title=\"Audio: English\"", ignoreCase = true) && variants.isEmpty()
                }

                if (preferDub) {
                    for (dv in dubVariants) {
                        candidateEmbeds.add("$BASE_URL/embed?v=${dv.second}" to true)
                    }
                }

                for (sv in subVariants) {
                    candidateEmbeds.add("$BASE_URL/embed?v=${sv.second}" to false)
                }
                for (v in variants) {
                    val url = "$BASE_URL/embed?v=${v.second}"
                    if (candidateEmbeds.none { it.first == url }) {
                        candidateEmbeds.add(url to isEnglishDubLabel(v.third))
                    }
                }

                // Fallback to active iframe on page
                val iframeMatcher = Pattern.compile("""<iframe\s+[^>]*src=["']([^"']*embed[^"']*)["']""", Pattern.CASE_INSENSITIVE).matcher(html)
                if (iframeMatcher.find()) {
                    var iframeUrl = iframeMatcher.group(1).orEmpty()
                    if (iframeUrl.isNotEmpty()) {
                        if (!iframeUrl.startsWith("http")) iframeUrl = "$BASE_URL$iframeUrl"
                        if (candidateEmbeds.none { it.first == iframeUrl }) {
                            candidateEmbeds.add(iframeUrl to activePageHasDub)
                        }
                    }
                }
            }

            for ((rawEmbedUrl, isDub) in candidateEmbeds) {
                val fullEmbedUrl = if (rawEmbedUrl.startsWith("http")) rawEmbedUrl else "$BASE_URL$rawEmbedUrl"
                val stream = extractStreamFromEmbed(fullEmbedUrl, epUrl, isDub)
                if (stream != null && stream.streamUrl.isNotBlank()) {
                    return@withContext stream
                }
            }
        } catch (_: Exception) {}
        null
    }
}
