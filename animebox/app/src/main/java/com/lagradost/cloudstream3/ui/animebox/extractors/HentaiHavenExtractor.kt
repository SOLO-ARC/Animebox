package com.lagradost.cloudstream3.ui.animebox.extractors

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.regex.Pattern

data class HentaiHavenSearchResult(
    val title: String,
    val url: String,
    val poster: String
)

data class HentaiHavenEpisode(
    val episodeNumber: Int,
    val title: String,
    val url: String,
    val thumbnail: String
)

data class HentaiHavenStreamResult(
    val streamUrl: String,
    val provider: String = "Hentai Haven",
    val referer: String = "https://hentaihaven.xxx/",
    val subtitleUrl: String = "",
    val subtitlesJson: String = "[]",
    val thumbnail: String = ""
)

object HentaiHavenExtractor {

    private const val BASE_URL = "https://hentaihaven.red"
    private const val FALLBACK_URL = "https://hentaihaven.xxx"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    private val searchCache = LruCache<String, List<HentaiHavenSearchResult>>(50)
    private val episodeCache = LruCache<String, List<HentaiHavenEpisode>>(50)
    private val streamCache = LruCache<String, HentaiHavenStreamResult>(100)

    private fun getHtml(url: String, referer: String? = null): String {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .apply {
                if (!referer.isNullOrEmpty()) header("Referer", referer)
            }
            .get()
            .build()
        return AnililiHttpClient.client.newCall(req).execute().use { resp ->
            resp.body?.string().orEmpty()
        }
    }

    private fun cleanTitle(title: String): String {
        return title
            .replace(Regex("""\(TV\)|\(Movie\)|\(OVA\)|\(ONA\)|Season \d+|Part \d+|The Animation|Animation|OVA""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[:\-_!?,~]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun extractSequenceNumbers(text: String): Set<String> {
        val romanMap = mapOf("i" to "1", "ii" to "2", "iii" to "3", "iv" to "4", "v" to "5", "vi" to "6", "vii" to "7")
        val tokens = text.lowercase().split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
        val nums = mutableSetOf<String>()
        for (tok in tokens) {
            if (tok.length == 4 && (tok.startsWith("19") || tok.startsWith("20"))) {
                continue
            }
            if (tok.all { it.isDigit() }) {
                nums.add(tok.toInt().toString())
            } else if (romanMap.containsKey(tok)) {
                nums.add(romanMap[tok]!!)
            }
        }
        return nums
    }

    private fun titleMatchScore(query: String, candidate: String): Double {
        fun normalize(value: String) = cleanTitle(value).lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
        val wanted = normalize(query)
        val title = normalize(candidate)
        if (wanted.isBlank() || title.isBlank()) return 0.0

        val wantedNums = extractSequenceNumbers(wanted)
        val titleNums = extractSequenceNumbers(title)
        if (wantedNums != titleNums) {
            return 0.0
        }

        if (wanted == title) return 1.0
        if (title.contains(wanted) || wanted.contains(title)) return 0.82
        val tokens = wanted.split(Regex("\\s+")).filter { it.length > 1 }.toSet()
        if (tokens.isEmpty()) return 0.0
        return tokens.intersect(title.split(Regex("\\s+")).toSet()).size.toDouble() / tokens.size
    }

    suspend fun search(query: String): List<HentaiHavenSearchResult> = withContext(Dispatchers.IO) {
        val cleaned = cleanTitle(query)
        if (cleaned.isBlank()) return@withContext emptyList()

        searchCache.get(cleaned.lowercase())?.let { return@withContext it }

        val searchUrl = "$BASE_URL/?s=" + URLEncoder.encode(cleaned, "UTF-8")
        val html = runCatching { getHtml(searchUrl, referer = "$BASE_URL/") }.getOrDefault("")
        if (html.isBlank()) return@withContext emptyList()

        val results = mutableListOf<HentaiHavenSearchResult>()

        // Match search card blocks: <div class="c-tabs-item__content"> or <div class="tab-thumb">
        val cardRegex = Pattern.compile("""<div[^>]*class="[^"]*(?:c-tabs-item__content|search-wrap|row c-tabs-item__content)[^"]*"[^>]*>(.*?)</div>\s*</div>\s*</div>""", Pattern.DOTALL)
        val matcher = cardRegex.matcher(html)

        val titlePattern = Pattern.compile("""<h3[^>]*class="[^"]*h4[^"]*"[^>]*>\s*<a[^>]*href="([^"]+)"[^>]*>([^<]+)</a>""")
        val altTitlePattern = Pattern.compile("""<div class="post-title">\s*<h3[^>]*>\s*<a href="([^"]+)">([^<]+)</a>""")
        val posterPattern = Pattern.compile("""<img[^>]+(?:data-src|src)="([^"]+)"""")

        while (matcher.find()) {
            val block = matcher.group(1) ?: continue
            val tMatcher = titlePattern.matcher(block)
            val pMatcher = posterPattern.matcher(block)

            var url = ""
            var title = ""
            if (tMatcher.find()) {
                url = tMatcher.group(1)?.trim().orEmpty()
                title = tMatcher.group(2)?.trim().orEmpty()
            } else {
                val altM = altTitlePattern.matcher(block)
                if (altM.find()) {
                    url = altM.group(1)?.trim().orEmpty()
                    title = altM.group(2)?.trim().orEmpty()
                }
            }

            val poster = if (pMatcher.find()) pMatcher.group(1)?.trim().orEmpty() else ""
            if (url.isNotEmpty() && title.isNotEmpty()) {
                results.add(HentaiHavenSearchResult(title = title, url = url, poster = poster))
            }
        }

        // Generic fallback scan for any watch links if card block regex was too strict
        if (results.isEmpty()) {
            val linkPattern = Pattern.compile(
                """<a\b(?=[^>]*href=["'](https?://(?:www\.)?hentaihaven\.(?:red|xxx)/watch/[^"'#?]+)["'])[^>]*>(.*?)</a>""",
                Pattern.CASE_INSENSITIVE or Pattern.DOTALL
            )
            val lMatcher = linkPattern.matcher(html)
            val seen = mutableSetOf<String>()
            while (lMatcher.find()) {
                val u = lMatcher.group(1)?.replace("&amp;", "&")?.trim().orEmpty()
                val inner = lMatcher.group(2).orEmpty()
                val t = inner.replace(Regex("<[^>]*>"), " ").replace("&amp;", "&").replace(Regex("\\s+"), " ").trim()
                if (u.isNotEmpty() && t.length > 2 && seen.add(u)) {
                    val poster = Regex("""(?:data-src|data-lazy-src|src)=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                        .find(inner)?.groupValues?.getOrNull(1).orEmpty()
                    results.add(HentaiHavenSearchResult(title = t, url = u, poster = poster))
                }
            }
        }

        if (results.isNotEmpty()) {
            searchCache.put(cleaned.lowercase(), results)
        }
        results
    }

    suspend fun getEpisodes(seriesUrl: String): List<HentaiHavenEpisode> = withContext(Dispatchers.IO) {
        if (seriesUrl.isBlank()) return@withContext emptyList()
        episodeCache.get(seriesUrl)?.let { return@withContext it }

        val html = runCatching { getHtml(seriesUrl, referer = "$BASE_URL/") }.getOrDefault("")
        if (html.isBlank()) return@withContext emptyList()

        val episodes = mutableListOf<HentaiHavenEpisode>()

        // Episode links: <li class="wp-manga-chapter ..."><a href="https://hentaihaven.xxx/watch/.../episode-1/">Episode 1</a></li>
        val epPattern = Pattern.compile("""<li[^>]*class="[^"]*wp-manga-chapter[^"]*"[^>]*>\s*<a[^>]*href="([^"]+)"[^>]*>(.*?)</a>""", Pattern.DOTALL)
        val numPattern = Pattern.compile("""Episode\s*(\d+)""", Pattern.CASE_INSENSITIVE)

        val matcher = epPattern.matcher(html)
        var fallbackIndex = 1
        while (matcher.find()) {
            val epUrl = matcher.group(1)?.trim().orEmpty()
            val text = matcher.group(2)?.trim().orEmpty()
            if (epUrl.isEmpty()) continue

            val numM = numPattern.matcher(text)
            val epNum = if (numM.find()) {
                numM.group(1)?.toIntOrNull() ?: fallbackIndex
            } else {
                val urlNum = Regex("""episode-(\d+)""").find(epUrl)?.groupValues?.getOrNull(1)?.toIntOrNull()
                urlNum ?: fallbackIndex
            }

            episodes.add(
                HentaiHavenEpisode(
                    episodeNumber = epNum,
                    title = "Episode $epNum",
                    url = epUrl,
                    thumbnail = ""
                )
            )
            fallbackIndex++
        }

        if (episodes.isEmpty()) {
            val genericEpisodeLink = Pattern.compile(
                """<a\b(?=[^>]*href=["']([^"']*(?:episode[-_/]?\d+)[^"']*)["'])[^>]*>(.*?)</a>""",
                Pattern.CASE_INSENSITIVE or Pattern.DOTALL
            )
            val links = genericEpisodeLink.matcher(html)
            while (links.find()) {
                val rawUrl = links.group(1)?.replace("&amp;", "&")?.trim().orEmpty()
                val match = Regex("(?i)episode[-_/]?(\\d+)").find(rawUrl) ?: continue
                val number = match.groupValues.getOrNull(1)?.toIntOrNull() ?: continue
                val href = if (rawUrl.startsWith("http", true)) rawUrl else "$BASE_URL/${rawUrl.trimStart('/')}"
                val label = links.group(2).orEmpty().replace(Regex("<[^>]*>"), " ")
                    .replace("&amp;", "&").replace(Regex("\\s+"), " ").trim()
                episodes.add(HentaiHavenEpisode(number, label.ifBlank { "Episode $number" }, href, ""))
            }
        }

        // If it's directly a watch page or single episode
        if (episodes.isEmpty()) {
            if (seriesUrl.contains("/episode-")) {
                val epNum = Regex("""episode-(\d+)""").find(seriesUrl)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
                episodes.add(
                    HentaiHavenEpisode(
                        episodeNumber = epNum,
                        title = "Episode $epNum",
                        url = seriesUrl,
                        thumbnail = ""
                    )
                )
            }
        }

        val sorted = episodes.distinctBy { it.episodeNumber }.sortedBy { it.episodeNumber }
        if (sorted.isNotEmpty()) {
            episodeCache.put(seriesUrl, sorted)
        }
        sorted
    }

    suspend fun extractStream(watchUrl: String): HentaiHavenStreamResult? = withContext(Dispatchers.IO) {
        if (watchUrl.isBlank()) return@withContext null
        streamCache.get(watchUrl)?.let { return@withContext it }

        val html = runCatching { getHtml(watchUrl, referer = "$BASE_URL/") }.getOrDefault("")
        if (html.isBlank()) return@withContext null

        // 1. Direct source regex: <source src="https://octopusmanifest.org/.../playlist.m3u8"
        val sourceMatch = Regex("""<source[^>]+src=["'](https?://[^"']+\.m3u8[^"']*)["']""").find(html)
            ?: Regex("""(?:file|src)\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""").find(html)
            ?: Regex("""["'](https?://octopusmanifest\.org/[^"']+/playlist\.m3u8)""").find(html)

        val streamUrl = sourceMatch?.groupValues?.getOrNull(1)?.replace("\\/", "/").orEmpty()
        if (streamUrl.isBlank()) return@withContext null

        // 2. Subtitle: track tag or octopusmanifest English vtt
        val trackMatch = Regex("""<track[^>]+kind=["']subtitles["'][^>]+src=["']([^"']+)["']""").find(html)
            ?: Regex("""["'](https?://octopusmanifest\.org/[^"']+/s/en\.vtt)""").find(html)
        val subUrl = trackMatch?.groupValues?.getOrNull(1)?.replace("\\/", "/").orEmpty()

        val subtitlesJson = if (subUrl.isNotEmpty()) {
            JSONArray().apply {
                put(JSONObject().apply {
                    put("label", "English")
                    put("lang", "en")
                    put("file", subUrl)
                })
            }.toString()
        } else "[]"

        val posterMatch = Regex("""<video[^>]+poster=["']([^"']+)["']""").find(html)
        val thumb = posterMatch?.groupValues?.getOrNull(1).orEmpty()

        val result = HentaiHavenStreamResult(
            streamUrl = streamUrl,
            provider = "Hentai Haven",
            referer = "https://hentaihaven.xxx/",
            subtitleUrl = subUrl,
            subtitlesJson = subtitlesJson,
            thumbnail = thumb
        )
        streamCache.put(watchUrl, result)
        result
    }

    suspend fun getStreamForAnime(
        animeTitle: String,
        episodeNum: Int,
        altTitles: List<String> = emptyList()
    ): HentaiHavenStreamResult? = withContext(Dispatchers.IO) {
        val titlesToTry = (listOf(animeTitle) + altTitles)
            .map { cleanTitle(it) }
            .filter { it.isNotBlank() }
            .distinct()

        var matchingEpisodes: List<HentaiHavenEpisode> = emptyList()

        for (t in titlesToTry) {
            val results = search(t)
            if (results.isEmpty()) continue

            val bestMatch = results.maxByOrNull { titleMatchScore(t, it.title) }
                ?.takeIf { titleMatchScore(t, it.title) >= 0.5 } ?: continue

            val eps = getEpisodes(bestMatch.url)
            if (eps.isNotEmpty()) {
                matchingEpisodes = eps
                break
            }
        }

        if (matchingEpisodes.isEmpty()) return@withContext null

        val targetEp = matchingEpisodes.firstOrNull { it.episodeNumber == episodeNum }
            ?: matchingEpisodes.getOrNull(episodeNum - 1)
            ?: matchingEpisodes.firstOrNull()
            ?: return@withContext null

        extractStream(targetEp.url)
    }

    suspend fun getEpisodeListForAnime(
        animeTitle: String,
        altTitles: List<String> = emptyList()
    ): List<HentaiHavenEpisode> = withContext(Dispatchers.IO) {
        val titlesToTry = (listOf(animeTitle) + altTitles)
            .map { cleanTitle(it) }
            .filter { it.isNotBlank() }
            .distinct()

        for (t in titlesToTry) {
            val results = search(t)
            if (results.isEmpty()) continue

            val bestMatch = results.maxByOrNull { titleMatchScore(t, it.title) }
                ?.takeIf { titleMatchScore(t, it.title) >= 0.5 } ?: continue

            val eps = getEpisodes(bestMatch.url)
            if (eps.isNotEmpty()) {
                return@withContext eps
            }
        }
        emptyList()
    }

    suspend fun getDescription(
        animeTitle: String,
        altTitles: List<String> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        val titlesToTry = (listOf(animeTitle) + altTitles)
            .map { cleanTitle(it) }
            .filter { it.isNotBlank() }
            .distinct()

        for (t in titlesToTry) {
            val results = search(t)
            if (results.isEmpty()) continue

            val bestMatch = results.maxByOrNull { titleMatchScore(t, it.title) }
                ?.takeIf { titleMatchScore(t, it.title) >= 0.5 } ?: continue

            val html = runCatching { getHtml(bestMatch.url, referer = "$BASE_URL/") }.getOrDefault("")
            if (html.isNotBlank()) {
                val descPattern = Pattern.compile("""<div[^>]*class="[^"]*(?:summary__content|description-summary|post-content)[^"]*"[^>]*>(.*?)</div>""", Pattern.DOTALL)
                val m = descPattern.matcher(html)
                if (m.find()) {
                    val raw = m.group(1).orEmpty()
                    val cleaned = raw.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").replace(Regex("""\s+"""), " ").trim()
                    if (cleaned.isNotBlank()) return@withContext cleaned
                }
            }
        }
        ""
    }
}
