package com.lagradost.cloudstream3.ui.animebox.extractors

import android.util.Base64
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import java.util.regex.Pattern

data class HentaiMamaSearchResult(
    val title: String,
    val url: String,
    val poster: String
)

data class HentaiMamaEpisode(
    val episodeNumber: Int,
    val title: String,
    val url: String,
    val thumbnail: String
)

data class HentaiMamaStreamResult(
    val streamUrl: String,
    val provider: String = "Hentai Mama",
    val referer: String = "https://hentaimama.io/",
    val thumbnail: String = ""
)

object HentaiMamaExtractor {

    private const val BASE_URL = "https://hentaimama.io"
    private const val AJAX_URL = "https://hentaimama.io/wp-admin/admin-ajax.php"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    private val searchCache = LruCache<String, List<HentaiMamaSearchResult>>(50)
    private val episodeCache = LruCache<String, List<HentaiMamaEpisode>>(50)
    private val streamCache = LruCache<String, HentaiMamaStreamResult>(100)
    private val seriesIdentifierCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun getCachedSeriesIdentifier(anilistId: Int, title: String): String? {
        val key = if (anilistId > 0) "id:$anilistId" else "title:${cleanTitle(title).lowercase()}"
        return seriesIdentifierCache[key]
    }

    fun setCachedSeriesIdentifier(anilistId: Int, title: String, seriesUrl: String) {
        val key = if (anilistId > 0) "id:$anilistId" else "title:${cleanTitle(title).lowercase()}"
        seriesIdentifierCache[key] = seriesUrl
    }

    private val EXPLICIT_SLUG_MAPPINGS = mapOf(
        "sister breeder" to "sister-breeder-ova",
        "sister breeder ova" to "sister-breeder-ova",
        "sister-breeder" to "sister-breeder-ova",
        "sister-breeder-ova" to "sister-breeder-ova",
        "sister breeder the animation" to "sister-breeder-ova",
        "tsumamigui 3" to "tsumamigui-3-the-animation",
        "tsumamigui 3 the animation" to "tsumamigui-3-the-animation",
        "wife eater 3" to "tsumamigui-3-the-animation",
        "wife-eater-3" to "tsumamigui-3-the-animation",
        "tsumamigui" to "wife-eater",
        "tsumamigui the animation" to "wife-eater",
        "wife eater" to "wife-eater",
        "wife-eater" to "wife-eater"
    )

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

    /**
     * Sequel numbers of the SERIES an episode URL belongs to. The trailing "-episode-N" part is the
     * episode counter, not a sequel number, so it must be removed first - otherwise
     * ".../tsumamigui-3-the-animation-episode-1/" yields {3, 1} and never equals the wanted {3}
     * (and every un-numbered series yields {1}), which rejected the correct series.
     */
    private fun seriesNumbersFromEpisodeUrl(episodeUrl: String): Set<String> {
        val slug = episodeUrl.trimEnd('/').substringAfterLast('/').lowercase()
            .replace(Regex("-?episode-?\\d+.*$"), "")
        return extractSequenceNumbers(slug.replace("-", " "))
    }

    private fun isPlaceholderImage(url: String): Boolean {
        val lower = url.lowercase()
        return lower.startsWith("data:") || lower.contains("placeholder") || lower.contains("blank.") ||
            lower.contains("/logo") || lower.contains("loading.") || lower.contains("lazy.") ||
            lower.substringAfterLast('/').substringBefore('.') == "null"
    }

    /** Picks the real image of an <img> block: lazy-load attributes first, never a placeholder, always absolute. */
    private fun extractImageUrl(html: String): String {
        for (tag in Regex("<img\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html).map { it.value }) {
            for (attr in listOf("data-src", "data-lazy-src", "data-original", "src")) {
                val value = Regex("(?<![\\w-])" + attr + "=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                    .find(tag)?.groupValues?.getOrNull(1)?.replace("&amp;", "&")?.trim().orEmpty()
                if (value.isBlank() || isPlaceholderImage(value)) continue
                return when {
                    value.startsWith("//") -> "https:$value"
                    value.startsWith("/") -> BASE_URL + value
                    else -> value
                }
            }
        }
        return ""
    }

    private fun titleMatchScore(query: String, candidate: String): Double {
        fun normalize(value: String) = cleanTitle(value).lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
        val wanted = normalize(query)
        val title = normalize(candidate)
        if (wanted.isBlank() || title.isBlank()) return 0.0

        // Strict sequel number matching (e.g. Tsumamigi (none) must never match Tsumamigi 3 ("3"), and vice-versa)
        val wantedNums = extractSequenceNumbers(wanted)
        val titleNums = extractSequenceNumbers(title)
        if (wantedNums != titleNums) {
            return 0.0
        }

        if (wanted == title) return 1.0
        if (title.contains(wanted) || wanted.contains(title)) return 0.82
        val tokens = wanted.split(Regex("\\s+")).filter { it.length > 1 }.toSet()
        if (tokens.isEmpty()) return 0.0
        val titleTokens = title.split(Regex("\\s+")).toSet()
        return tokens.intersect(titleTokens).size.toDouble() / tokens.size
    }

    suspend fun search(query: String): List<HentaiMamaSearchResult> = withContext(Dispatchers.IO) {
        val cleaned = cleanTitle(query)
        if (cleaned.isBlank()) return@withContext emptyList()

        searchCache.get(cleaned.lowercase())?.let { return@withContext it }

        val searchUrl = "$BASE_URL/?s=" + URLEncoder.encode(cleaned, "UTF-8")
        val html = runCatching { getHtml(searchUrl, referer = "$BASE_URL/") }.getOrDefault("")
        if (html.isBlank()) return@withContext emptyList()

        val results = mutableListOf<HentaiMamaSearchResult>()
        // Match series cards: <article ... class="...series-card...">
        val cardRegex = Pattern.compile("""<article[^>]*class="[^"]*series-card[^"]*"[^>]*>(.*?)</article>""", Pattern.DOTALL)
        val matcher = cardRegex.matcher(html)

        val titlePattern = Pattern.compile("""<h3 class="sc-title">\s*<a href="([^"]+)">([^<]+)</a>""")
        val posterPattern = Pattern.compile("""<img[^>]+src="([^"]+)"""")

        while (matcher.find()) {
            val block = matcher.group(1) ?: continue
            val tMatcher = titlePattern.matcher(block)
            val pMatcher = posterPattern.matcher(block)

            if (tMatcher.find()) {
                val epUrl = tMatcher.group(1)?.trim().orEmpty()
                val title = tMatcher.group(2)?.trim().orEmpty()
                val poster = if (pMatcher.find()) pMatcher.group(1)?.trim().orEmpty() else ""
                if (epUrl.isNotEmpty() && title.isNotEmpty()) {
                    results.add(HentaiMamaSearchResult(title = title, url = epUrl, poster = poster))
                }
            }
        }

        // Keep working when the site's card markup changes or lazy-loads the title outside its old h3 class.
        if (results.isEmpty()) {
            val anchorPattern = Pattern.compile(
                """<a\b(?=[^>]*href=["'](https?://(?:www\.)?hentaimama\.io/[^"'#?]+)["'])[^>]*>(.*?)</a>""",
                Pattern.CASE_INSENSITIVE or Pattern.DOTALL
            )
            val anchorMatcher = anchorPattern.matcher(html)
            val seen = mutableSetOf<String>()
            while (anchorMatcher.find()) {
                val url = anchorMatcher.group(1)?.replace("&amp;", "&")?.trim().orEmpty()
                val inner = anchorMatcher.group(2).orEmpty()
                val path = runCatching { android.net.Uri.parse(url).path.orEmpty().lowercase() }.getOrDefault("")
                if (path.isBlank() || path in setOf("/", "/about/", "/contact/") ||
                    listOf("/category/", "/tag/", "/genre/", "/page/", "/wp-content/", "/wp-admin/", "episode-").any(path::contains) ||
                    !seen.add(url)
                ) continue
                val title = inner.replace(Regex("<[^>]*>"), " ").replace("&amp;", "&")
                    .replace("&#8217;", "'").replace("&quot;", "\"")
                    .replace(Regex("\\s+"), " ").trim()
                if (title.length < 3) continue
                val image = Regex("""(?:data-src|data-lazy-src|src)=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
                    .find(inner)?.groupValues?.getOrNull(1)?.replace("&amp;", "&").orEmpty()
                results.add(HentaiMamaSearchResult(title, url, image))
            }
        }

        if (results.isNotEmpty()) {
            searchCache.put(cleaned.lowercase(), results)
        }
        results
    }

    suspend fun getEpisodes(seriesUrl: String): List<HentaiMamaEpisode> = withContext(Dispatchers.IO) {
        if (seriesUrl.isBlank()) return@withContext emptyList()
        episodeCache.get(seriesUrl)?.let { return@withContext it }

        val html = runCatching { getHtml(seriesUrl, referer = "$BASE_URL/") }.getOrDefault("")
        if (html.isBlank()) return@withContext emptyList()

        val episodes = mutableListOf<HentaiMamaEpisode>()

        // Episode item pattern: <a class="dt-se-item" href="([^"]+)">...
        val itemPattern = Pattern.compile(
            """<a\b(?=[^>]*class=["'][^"']*dt-se-item[^"']*["'])(?=[^>]*href=["']([^"']+)["'])[^>]*>(.*?)</a>""",
            Pattern.DOTALL or Pattern.CASE_INSENSITIVE
        )
        val numPattern = Pattern.compile("""<b class="dt-se-num">EP\s*(\d+)</b>""", Pattern.CASE_INSENSITIVE)
        val thumbPattern = Pattern.compile("""<img[^>]+(?:data-src|data-lazy-src|src)=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE)
        val titlePattern = Pattern.compile("""<b class="dt-se-title">([^<]+)</b>""")

        val matcher = itemPattern.matcher(html)
        var fallbackIndex = 1
        while (matcher.find()) {
            val epUrl = matcher.group(1)?.replace("&amp;", "&")?.trim().orEmpty()
            if (epUrl.isEmpty()) continue
            val inner = matcher.group(2) ?: ""

            val numMatcher = numPattern.matcher(inner)
            val epNum = if (numMatcher.find()) {
                numMatcher.group(1)?.toIntOrNull() ?: fallbackIndex
            } else {
                // Try from URL: /overflow-episode-1/
                val urlMatch = Regex("""episode-(\d+)""").find(epUrl)
                urlMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: fallbackIndex
            }

            val thumbnail = extractImageUrl(inner)

            val titleMatcher = titlePattern.matcher(inner)
            val epTitle = if (titleMatcher.find()) titleMatcher.group(1)?.trim().orEmpty() else "Episode $epNum"

            episodes.add(
                HentaiMamaEpisode(
                    episodeNumber = epNum,
                    title = epTitle,
                    url = epUrl,
                    thumbnail = thumbnail
                )
            )
            fallbackIndex++
        }

        // Single video page / movie fallback
        if (episodes.isEmpty()) {
            val postMatch = Pattern.compile("""data-post="(\d+)"""").matcher(html)
            if (postMatch.find()) {
                val posterMatch = Pattern.compile("""<img[^>]+src="([^"]+)"""").matcher(html)
                val thumb = if (posterMatch.find()) posterMatch.group(1)?.trim().orEmpty() else ""
                episodes.add(
                    HentaiMamaEpisode(
                        episodeNumber = 1,
                        title = "Full Episode",
                        url = seriesUrl,
                        thumbnail = thumb
                    )
                )
            }
        }

        val sorted = episodes.sortedBy { it.episodeNumber }
        if (sorted.isNotEmpty()) {
            episodeCache.put(seriesUrl, sorted)
        }
        sorted
    }

    suspend fun extractStreamFromEpisodeUrl(episodeUrl: String): HentaiMamaStreamResult? = withContext(Dispatchers.IO) {
        if (episodeUrl.isBlank()) return@withContext null
        streamCache.get(episodeUrl)?.let { return@withContext it }

        val html = runCatching { getHtml(episodeUrl, referer = "$BASE_URL/") }.getOrDefault("")
        if (html.isBlank()) return@withContext null

        val postMatch = Pattern.compile("""data-post="(\d+)"""").matcher(html)
        val altPostMatch = Pattern.compile("""data-post-id="(\d+)"""").matcher(html)
        val postId = when {
            postMatch.find() -> postMatch.group(1)
            altPostMatch.find() -> altPostMatch.group(1)
            else -> Regex("""postid-(\d+)""").find(html)?.groupValues?.getOrNull(1)
        } ?: return@withContext null

        // Call admin-ajax.php to get player iframe
        val formBody = FormBody.Builder()
            .add("action", "get_player_contents")
            .add("a", postId)
            .add("i", "1")
            .build()

        val ajaxReq = Request.Builder()
            .url(AJAX_URL)
            .header("User-Agent", USER_AGENT)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", episodeUrl)
            .post(formBody)
            .build()

        val ajaxResp = runCatching {
            AnililiHttpClient.client.newCall(ajaxReq).execute().use { it.body?.string().orEmpty() }
        }.getOrDefault("")

        var iframeSrc = ""
        if (ajaxResp.isNotEmpty()) {
            val srcMatcher = Pattern.compile("""src=\\?["'](.*?[^\\])\\?["']""").matcher(ajaxResp)
            if (srcMatcher.find()) {
                iframeSrc = srcMatcher.group(1)
                    ?.replace("\\/", "/")
                    ?.replace("\\\"", "")
                    ?.replace("&amp;", "&")
                    ?.replace("&#038;", "&")
                    .orEmpty()
            }
        }

        var directStreamUrl = ""
        var thumbnail = ""

        // Try direct base64 decode from p parameter in iframe URL
        if (iframeSrc.isNotEmpty()) {
            val pMatch = Regex("""[?&]p=([^&#]+)""").find(iframeSrc)
            if (pMatch != null) {
                val rawP = pMatch.groupValues[1]
                val paddedP = rawP + "=".repeat((-rawP.length).mod(4))
                val decoded = runCatching {
                    String(Base64.decode(paddedP, Base64.DEFAULT), Charsets.UTF_8).trim()
                }.getOrNull()
                if (!decoded.isNullOrEmpty() && (decoded.endsWith(".mp4") || decoded.endsWith(".m3u8") || decoded.contains("/"))) {
                    directStreamUrl = if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
                        decoded
                    } else {
                        "https://gdvid.info/" + decoded.removePrefix("/")
                    }
                }
            }

            // Fetch iframe HTML to parse jwplayer sources
            val fullIframeUrl = if (iframeSrc.startsWith("//")) "https:$iframeSrc" else iframeSrc
            val iframeHtml = runCatching { getHtml(fullIframeUrl, referer = episodeUrl) }.getOrDefault("")
            if (iframeHtml.isNotEmpty()) {
                val sourceMatch = Regex(""""file"\s*:\s*"([^"]+)"""").find(iframeHtml)
                    ?: Regex("""file\s*:\s*["']([^"']+)["']""").find(iframeHtml)
                if (sourceMatch != null) {
                    val parsedUrl = sourceMatch.groupValues[1].replace("\\/", "/")
                    if (parsedUrl.startsWith("http", ignoreCase = true)) {
                        directStreamUrl = parsedUrl
                    }
                }
                val imgMatch = Regex(""""image"\s*:\s*"([^"]+)"""").find(iframeHtml)
                    ?: Regex("""image\s*:\s*["']([^"']+)["']""").find(iframeHtml)
                if (imgMatch != null) {
                    thumbnail = imgMatch.groupValues[1].replace("\\/", "/")
                }
            }
        }

        if (directStreamUrl.isNotBlank()) {
            val result = HentaiMamaStreamResult(
                streamUrl = directStreamUrl,
                provider = "Hentai Mama",
                referer = "https://hentaimama.io/",
                thumbnail = thumbnail
            )
            streamCache.put(episodeUrl, result)
            return@withContext result
        }

        null
    }

    private fun slugify(value: String): String {
        return value.lowercase()
            .replace("&", " and ")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
    }

    /** True only when the page really exists (the site answers 404 for unknown series slugs). */
    private fun pageExists(url: String): Boolean {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Referer", "$BASE_URL/")
                .get()
                .build()
            AnililiHttpClient.client.newCall(req).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /**
     * Some series (e.g. Tsumamigui) exist on the site but are not returned by its search.
     * Probe their direct /tvshows/<slug>/ page. A probe is only accepted when the slug carries the same
     * sequel numbers as the requested title, so "Tsumamigui" never resolves to "Tsumamigui 3".
     */
    private suspend fun probeSeriesBySlug(titles: List<String>): List<HentaiMamaEpisode> {
        val tried = mutableSetOf<String>()
        for (original in titles) {
            val wantedNums = extractSequenceNumbers(original)
            val rawBases = listOf(slugify(original), slugify(cleanTitle(original))).filter { it.isNotBlank() }.distinct()
            val bases = rawBases.flatMap { base ->
                listOf(
                    base,
                    base.replace("migi", "migui"),
                    base.replace("migui", "migi"),
                    base.replace("ou", "o"),
                    base.replace("uu", "u"),
                    base.replace("-the-animation", ""),
                    base.replace("-ova", "")
                )
            }.filter { it.isNotBlank() }.distinct()

            val candidates = bases.flatMap {
                listOf(
                    it,
                    "$it-the-animation",
                    "$it-the-animation-ova",
                    "$it-ova"
                )
            }.distinct()

            for (slug in candidates) {
                if (!tried.add(slug)) continue
                if (extractSequenceNumbers(slug.replace("-", " ")) != wantedNums) continue
                val url = "$BASE_URL/tvshows/$slug/"
                if (!pageExists(url)) continue
                val eps = getEpisodes(url)
                if (eps.isNotEmpty()) {
                    // Double check episode URLs: some pages on the site like /tvshows/tsumamigui/
                    // actually contain episodes for "tsumamigui-3". Never allow sequel mismatch!
                    val sampleNums = seriesNumbersFromEpisodeUrl(eps.firstOrNull()?.url.orEmpty())
                    if (wantedNums.isEmpty() && sampleNums.isNotEmpty()) {
                        continue
                    }
                    if (wantedNums.isNotEmpty() && sampleNums.isNotEmpty() && wantedNums != sampleNums) {
                        continue
                    }
                    return eps
                }
            }
        }
        return emptyList()
    }

    suspend fun resolveSeries(
        animeTitle: String,
        altTitles: List<String> = emptyList(),
        releaseYear: Int = 0,
        totalEpisodes: Int = 0,
        anilistId: Int = 0
    ): String? = withContext(Dispatchers.IO) {
        if (anilistId == 1403) return@withContext "$BASE_URL/tvshows/wife-eater/"
        if (anilistId == 21870) return@withContext "$BASE_URL/tvshows/tsumamigui-3-the-animation/"

        val cached = getCachedSeriesIdentifier(anilistId, animeTitle)
        if (!cached.isNullOrEmpty()) return@withContext cached

        val normQuery = cleanTitle(animeTitle).lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
        if (normQuery.isBlank()) return@withContext null
        if (normQuery == "tsumamigui" || normQuery == "wife eater") {
            return@withContext "$BASE_URL/tvshows/wife-eater/"
        }
        if (normQuery == "tsumamigui 3" || normQuery == "wife eater 3") {
            return@withContext "$BASE_URL/tvshows/tsumamigui-3-the-animation/"
        }

        val spacedQuery = normQuery.replace(Regex("""(\d+)([a-zA-Z]+)"""), "$1 $2").replace(Regex("""([a-zA-Z]+)(\d+)"""), "$1 $2").trim()
        val searchUrl = "$BASE_URL/?s=" + URLEncoder.encode(if (spacedQuery != normQuery && spacedQuery.isNotBlank()) spacedQuery else normQuery, "UTF-8")
        val html = runCatching { getHtml(searchUrl, referer = "$BASE_URL/") }.getOrDefault("")
        val candidates = mutableListOf<Pair<String, String>>()
        if (html.isNotBlank()) {
            val cardRegex = Pattern.compile("""<article[^>]*class="[^"]*series-card[^"]*"[^>]*>(.*?)</article>""", Pattern.DOTALL)
            val matcher = cardRegex.matcher(html)
            val titlePattern = Pattern.compile("""<h3 class="sc-title">\s*<a href="([^"]+)">([^<]+)</a>""")
            while (matcher.find()) {
                val block = matcher.group(1) ?: continue
                val tMatcher = titlePattern.matcher(block)
                if (tMatcher.find()) {
                    val sUrl = tMatcher.group(1)?.trim().orEmpty()
                    val sTitle = tMatcher.group(2)?.trim().orEmpty()
                    if (sUrl.isNotEmpty() && sTitle.isNotEmpty()) {
                        candidates.add(sTitle to sUrl)
                    }
                }
            }
        }

        if (candidates.isEmpty() && altTitles.isNotEmpty()) {
            for (alt in altTitles) {
                if (alt.equals(animeTitle, ignoreCase = true) || alt.isBlank()) continue
                val altNorm = cleanTitle(alt).lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
                if (altNorm.isBlank() || altNorm == normQuery) continue
                val altUrl = "$BASE_URL/?s=" + URLEncoder.encode(alt, "UTF-8")
                val altHtml = runCatching { getHtml(altUrl, referer = "$BASE_URL/") }.getOrDefault("")
                if (altHtml.isNotBlank()) {
                    val cardRegex = Pattern.compile("""<article[^>]*class="[^"]*series-card[^"]*"[^>]*>(.*?)</article>""", Pattern.DOTALL)
                    val matcher = cardRegex.matcher(altHtml)
                    val titlePattern = Pattern.compile("""<h3 class="sc-title">\s*<a href="([^"]+)">([^<]+)</a>""")
                    while (matcher.find()) {
                        val block = matcher.group(1) ?: continue
                        val tMatcher = titlePattern.matcher(block)
                        if (tMatcher.find()) {
                            val sUrl = tMatcher.group(1)?.trim().orEmpty()
                            val sTitle = tMatcher.group(2)?.trim().orEmpty()
                            if (sUrl.isNotEmpty() && sTitle.isNotEmpty()) {
                                candidates.add(sTitle to sUrl)
                            }
                        }
                    }
                }
                if (candidates.isNotEmpty()) break
            }
        }

        if (candidates.isEmpty()) {
            val baseSlug = slugify(normQuery)
            for (probeSlug in listOf(baseSlug, "$baseSlug-ova", "$baseSlug-the-animation")) {
                val probeUrl = "$BASE_URL/tvshows/$probeSlug/"
                if (pageExists(probeUrl)) {
                    candidates.add(animeTitle to probeUrl)
                    break
                }
            }
        }

        if (candidates.isEmpty()) return@withContext null

        val targets = (listOf(normQuery, spacedQuery) + altTitles.map { cleanTitle(it).lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim() }).filter { it.isNotBlank() }.distinct()
        val scored = candidates.map { (candTitle, candUrl) ->
            val normCand = cleanTitle(candTitle).lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
            var score = targets.maxOfOrNull { tq ->
                if (normCand == tq) 100
                else if (normCand.contains(tq) || tq.contains(normCand)) 60
                else {
                    val qW = tq.split(Regex("""\s+""")).filter { it.isNotBlank() }.toSet()
                    val cW = normCand.split(Regex("""\s+""")).filter { it.isNotBlank() }.toSet()
                    if (qW.isNotEmpty()) (40 * qW.intersect(cW).size / qW.size) else 0
                }
            } ?: 0

            val pageHtml = runCatching { getHtml(candUrl, referer = "$BASE_URL/") }.getOrDefault("")
            var candYear = 0
            var candEps = 0
            if (pageHtml.isNotBlank()) {
                val yearMatch = Regex("""(?:release|aired|year|date)[^<]*?(\b(?:19|20)\d{2}\b)""", RegexOption.IGNORE_CASE).find(pageHtml)
                candYear = yearMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                val epItemPattern = Pattern.compile("""class="[^"]*dt-se-item[^"]*"""")
                val epMatcher = epItemPattern.matcher(pageHtml)
                while (epMatcher.find()) candEps++
            }

            if (releaseYear > 0 && candYear > 0) {
                if (candYear == releaseYear) {
                    score += 50
                } else if (Math.abs(candYear - releaseYear) <= 1) {
                    score += 25
                } else {
                    score -= 40
                }
            }

            if (totalEpisodes > 0 && candEps > 0) {
                if (candEps == totalEpisodes) {
                    score += 30
                } else if (Math.abs(candEps - totalEpisodes) <= 1) {
                    score += 15
                }
            }

            Triple(score, candUrl, candEps)
        }.sortedByDescending { it.first }

        val best = scored.firstOrNull()?.second
        if (best != null) {
            setCachedSeriesIdentifier(anilistId, animeTitle, best)
        }
        best
    }

    private suspend fun findEpisodesForTitles(titlesToTry: List<String>, rawTitles: List<String>): List<HentaiMamaEpisode> {
        val allTitles = (rawTitles + titlesToTry).map { it.trim() }.filter { it.isNotBlank() }
        for (candidateTitle in allTitles) {
            val lower = candidateTitle.lowercase()
            val mappedSlug = when {
                lower.contains("sister breeder") -> "sister-breeder-ova"
                lower.contains("tsumamigui 3") || lower.contains("wife eater 3") -> "tsumamigui-3-the-animation"
                lower == "tsumamigui" || lower == "tsumamigui the animation" || lower == "wife eater" || lower == "wife-eater" -> "wife-eater"
                EXPLICIT_SLUG_MAPPINGS.containsKey(lower) -> EXPLICIT_SLUG_MAPPINGS[lower]
                else -> null
            }
            if (mappedSlug != null) {
                val url = "$BASE_URL/tvshows/$mappedSlug/"
                val eps = getEpisodes(url)
                if (eps.isNotEmpty()) {
                    return eps
                }
            }
        }
        for (t in titlesToTry) {
            val results = search(t)
            if (results.isEmpty()) continue

            // Pick exact or best fuzzy match
            val bestMatch = results.maxByOrNull { titleMatchScore(t, it.title) }
                ?.takeIf { titleMatchScore(t, it.title) >= 0.5 } ?: continue

            val eps = getEpisodes(bestMatch.url)
            if (eps.isNotEmpty()) {
                val sampleNums = seriesNumbersFromEpisodeUrl(eps.firstOrNull()?.url.orEmpty())
                val wantedNums = extractSequenceNumbers(t)
                if (wantedNums.isEmpty() && sampleNums.isNotEmpty()) {
                    continue
                }
                if (wantedNums.isNotEmpty() && sampleNums.isNotEmpty() && wantedNums != sampleNums) {
                    continue
                }
                return eps
            }
        }
        return probeSeriesBySlug(rawTitles)
    }

    private fun isHiiroNiKoku(animeTitle: String, altTitles: List<String>, anilistId: Int): Boolean {
        if (anilistId == 4731) return true
        val check = (listOf(animeTitle) + altTitles).joinToString(" ").lowercase()
        return check.contains("hiiro ni koku") || check.contains("hiiro no koku") || check.contains("time colored scarlet")
    }

    suspend fun getStreamForAnime(
        animeTitle: String,
        episodeNum: Int,
        altTitles: List<String> = emptyList(),
        releaseYear: Int = 0,
        totalEpisodes: Int = 0,
        anilistId: Int = 0
    ): HentaiMamaStreamResult? = withContext(Dispatchers.IO) {
        if (isHiiroNiKoku(animeTitle, altTitles, anilistId)) return@withContext null
        val seriesUrl = resolveSeries(animeTitle, altTitles, releaseYear, totalEpisodes, anilistId)
            ?: run {
                val baseTitles = (listOf(animeTitle) + altTitles).map { cleanTitle(it) }.filter { it.isNotBlank() }.distinct()
                val titlesToTry = baseTitles.flatMap { t -> listOf(t, t.replace("migi", "migui", ignoreCase = true), t.replace("migui", "migi", ignoreCase = true)) }.distinct()
                val eps = findEpisodesForTitles(titlesToTry, (listOf(animeTitle) + altTitles).filter { it.isNotBlank() }.distinct())
                if (eps.isNotEmpty()) {
                    val ep = eps.firstOrNull { it.episodeNumber == episodeNum } ?: eps.getOrNull(episodeNum - 1) ?: eps.firstOrNull()
                    return@withContext ep?.let { extractStreamFromEpisodeUrl(it.url) }
                }
                null
            } ?: return@withContext null

        val episodes = getEpisodes(seriesUrl)
        if (episodes.isEmpty()) return@withContext null

        val targetEp = episodes.firstOrNull { it.episodeNumber == episodeNum }
            ?: episodes.getOrNull(episodeNum - 1)
            ?: episodes.firstOrNull()
            ?: return@withContext null

        extractStreamFromEpisodeUrl(targetEp.url)
    }

    suspend fun getEpisodeListForAnime(
        animeTitle: String,
        altTitles: List<String> = emptyList(),
        releaseYear: Int = 0,
        totalEpisodes: Int = 0,
        anilistId: Int = 0
    ): List<HentaiMamaEpisode> = withContext(Dispatchers.IO) {
        if (isHiiroNiKoku(animeTitle, altTitles, anilistId)) return@withContext emptyList()
        val seriesUrl = resolveSeries(animeTitle, altTitles, releaseYear, totalEpisodes, anilistId)
        if (!seriesUrl.isNullOrEmpty()) {
            val eps = getEpisodes(seriesUrl)
            if (eps.isNotEmpty()) return@withContext eps
        }

        val baseTitles = (listOf(animeTitle) + altTitles).map { cleanTitle(it) }.filter { it.isNotBlank() }.distinct()
        val titlesToTry = baseTitles.flatMap { t -> listOf(t, t.replace("migi", "migui", ignoreCase = true), t.replace("migui", "migi", ignoreCase = true)) }.distinct()
        findEpisodesForTitles(titlesToTry, (listOf(animeTitle) + altTitles).filter { it.isNotBlank() }.distinct())
    }

    suspend fun getDescription(
        animeTitle: String,
        altTitles: List<String> = emptyList()
    ): String = withContext(Dispatchers.IO) {
        if (isHiiroNiKoku(animeTitle, altTitles, 0)) return@withContext ""
        val allTitles = (listOf(animeTitle) + altTitles).map { it.trim() }.filter { it.isNotBlank() }
        for (candidateTitle in allTitles) {
            val lower = candidateTitle.lowercase()
            val mappedSlug = when {
                lower.contains("sister breeder") -> "sister-breeder-ova"
                lower.contains("tsumamigui 3") || lower.contains("wife eater 3") -> "tsumamigui-3-the-animation"
                lower == "tsumamigui" || lower == "tsumamigui the animation" || lower == "wife eater" || lower == "wife-eater" -> "wife-eater"
                EXPLICIT_SLUG_MAPPINGS.containsKey(lower) -> EXPLICIT_SLUG_MAPPINGS[lower]
                else -> null
            }
            if (mappedSlug != null) {
                val url = "$BASE_URL/tvshows/$mappedSlug/"
                val html = runCatching { getHtml(url, referer = "$BASE_URL/") }.getOrDefault("")
                if (html.isNotBlank()) {
                    val descPattern = Pattern.compile("""<div[^>]*class="[^"]*(?:desc|entry-content|synopsis)[^"]*"[^>]*>(.*?)</div>""", Pattern.DOTALL)
                    val m = descPattern.matcher(html)
                    if (m.find()) {
                        val raw = m.group(1).orEmpty()
                        val cleaned = raw.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").replace(Regex("""\s+"""), " ").trim()
                        if (cleaned.isNotBlank()) return@withContext cleaned
                    }
                }
            }
        }


        val baseTitles = (listOf(animeTitle) + altTitles)
            .map { cleanTitle(it) }
            .filter { it.isNotBlank() }
            .distinct()
        val titlesToTry = baseTitles.flatMap { t ->
            listOf(
                t,
                t.replace("migi", "migui", ignoreCase = true),
                t.replace("migui", "migi", ignoreCase = true)
            )
        }.distinct()

        for (t in titlesToTry) {
            val results = search(t)
            if (results.isEmpty()) continue

            val bestMatch = results.maxByOrNull { titleMatchScore(t, it.title) }
                ?.takeIf { titleMatchScore(t, it.title) >= 0.5 } ?: continue

            val html = runCatching { getHtml(bestMatch.url, referer = "$BASE_URL/") }.getOrDefault("")
            if (html.isNotBlank()) {
                val descPattern = Pattern.compile("""<div[^>]*class="[^"]*(?:desc|entry-content|synopsis)[^"]*"[^>]*>(.*?)</div>""", Pattern.DOTALL)
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
