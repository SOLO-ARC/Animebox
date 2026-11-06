package com.lagradost.cloudstream3.ui.animebox.extractors

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.regex.Pattern

data class WatchHentaiSearchResult(
    val title: String,
    val url: String,
    val poster: String
)

data class WatchHentaiEpisode(
    val episodeNumber: Int,
    val title: String,
    val url: String,
    val thumbnail: String
)

data class WatchHentaiStreamResult(
    val streamUrl: String,
    val provider: String = "WatchHentai",
    val referer: String = "https://watchhentai.net/",
    val thumbnail: String = ""
)

object WatchHentaiExtractor {

    private const val BASE_URL = "https://watchhentai.net"
    private const val AJAX_URL = "https://watchhentai.net/wp-admin/admin-ajax.php"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    private val searchCache = LruCache<String, List<WatchHentaiSearchResult>>(50)
    private val episodeCache = LruCache<String, List<WatchHentaiEpisode>>(50)
    private val streamCache = LruCache<String, WatchHentaiStreamResult>(100)

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
        if (title.contains(wanted) || wanted.contains(title)) return 0.85
        val tokens = wanted.split(Regex("\\s+")).filter { it.length > 1 }.toSet()
        if (tokens.isEmpty()) return 0.0
        val titleTokens = title.split(Regex("\\s+")).toSet()
        return tokens.intersect(titleTokens).size.toDouble() / tokens.size
    }

    suspend fun search(query: String): List<WatchHentaiSearchResult> = withContext(Dispatchers.IO) {
        val cleaned = cleanTitle(query)
        if (cleaned.isBlank()) return@withContext emptyList()

        searchCache.get(cleaned.lowercase())?.let { return@withContext it }

        val searchUrl = "$BASE_URL/?s=" + URLEncoder.encode(cleaned, "UTF-8")
        val html = runCatching { getHtml(searchUrl, referer = "$BASE_URL/") }.getOrDefault("")
        if (html.isBlank()) return@withContext emptyList()

        val results = mutableListOf<WatchHentaiSearchResult>()

        // DooPlay search items: <article id="post-..." class="item tvshows"> ... <a href="(url)"> ... <img data-src="(poster)" ... alt="(title)"
        val articlePattern = Pattern.compile("""<article[^>]*id="post-(\d+)"[^>]*>(.*?)</article>""", Pattern.DOTALL)
        val matcher = articlePattern.matcher(html)

        while (matcher.find()) {
            val block = matcher.group(2) ?: continue
            val linkMatch = Regex("""<a\s+href=["'](https?://watchhentai\.net/series/[^"']+)["']""").find(block)
                ?: Regex("""<a\s+href=["'](https?://watchhentai\.net/[^"']+)["']""").find(block)
            val titleMatch = Regex("""title=["']([^"']+)["']""").find(block)
                ?: Regex("""alt=["']([^"']+)["']""").find(block)
            val posterMatch = Regex("""data-src=["']([^"']+)["']""").find(block)
                ?: Regex("""src=["'](https?://[^"']+)["']""").find(block)

            val url = linkMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val title = titleMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
            val poster = posterMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()

            if (url.isNotEmpty() && title.isNotEmpty()) {
                results.add(WatchHentaiSearchResult(title = title, url = url, poster = poster))
            }
        }

        if (results.isNotEmpty()) {
            searchCache.put(cleaned.lowercase(), results)
        }
        results
    }

    suspend fun getEpisodes(seriesUrl: String): List<WatchHentaiEpisode> = withContext(Dispatchers.IO) {
        if (seriesUrl.isBlank()) return@withContext emptyList()
        episodeCache.get(seriesUrl)?.let { return@withContext it }

        val html = runCatching { getHtml(seriesUrl, referer = "$BASE_URL/") }.getOrDefault("")
        if (html.isBlank()) return@withContext emptyList()

        val episodes = mutableListOf<WatchHentaiEpisode>()

        // 1. Direct card links: <a class='tv-ep-card-link' href='https://watchhentai.net/videos/...' title='...'>...<img data-src='...' ...>
        val cardLinkPattern = Pattern.compile("""<a[^>]+class=['"][^'"]*tv-ep-card-link[^'"]*['"][^>]+href=['"](https?://watchhentai\.net/videos/[^'"]+)['"][^>]*>(.*?)</a>""", Pattern.DOTALL)
        val cardMatcher = cardLinkPattern.matcher(html)
        var fallbackIndex = 1

        while (cardMatcher.find()) {
            val epUrl = cardMatcher.group(1)?.trim().orEmpty()
            val inner = cardMatcher.group(2).orEmpty()
            if (epUrl.isEmpty()) continue

            val numMatch = Regex("""episode-(\d+)""", RegexOption.IGNORE_CASE).find(epUrl)
                ?: Regex("""(?:episode|ep)\s*(\d+)""", RegexOption.IGNORE_CASE).find(inner)
            val epNum = numMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: fallbackIndex

            val imgMatch = Regex("""data-src=['"](https?://[^'"]+)['"]""").find(inner)
                ?: Regex("""data-lazy-src=['"](https?://[^'"]+)['"]""").find(inner)
                ?: Regex("""src=['"](https?://[^'"]+)['"]""").find(inner)
            val thumb = imgMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()

            episodes.add(
                WatchHentaiEpisode(
                    episodeNumber = epNum,
                    title = "Episode $epNum",
                    url = epUrl,
                    thumbnail = thumb
                )
            )
            fallbackIndex++
        }

        // 2. Fallback to <li> items if cardLinkPattern matched nothing
        if (episodes.isEmpty()) {
            val liPattern = Pattern.compile("""<li[^>]*>(.*?)</li>""", Pattern.DOTALL)
            val liMatcher = liPattern.matcher(html)
            fallbackIndex = 1

            while (liMatcher.find()) {
                val li = liMatcher.group(1) ?: continue
                val aMatch = Regex("""href=["'](https?://watchhentai\.net/videos/[^"']+)["']""").find(li) ?: continue
                val epUrl = aMatch.groupValues[1]

                val numMatch = Regex("""episode-(\d+)""", RegexOption.IGNORE_CASE).find(epUrl)
                    ?: Regex("""(?:episode|ep)\s*(\d+)""", RegexOption.IGNORE_CASE).find(li)
                val epNum = numMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: fallbackIndex

                val imgMatch = Regex("""data-src=['"](https?://[^'"]+)['"]""").find(li)
                    ?: Regex("""src=['"](https?://[^'"]+)['"]""").find(li)
                val thumb = imgMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()

                episodes.add(
                    WatchHentaiEpisode(
                        episodeNumber = epNum,
                        title = "Episode $epNum",
                        url = epUrl,
                        thumbnail = thumb
                    )
                )
                fallbackIndex++
            }
        }

        val sorted = episodes.distinctBy { it.episodeNumber }.sortedBy { it.episodeNumber }
        if (sorted.isNotEmpty()) {
            episodeCache.put(seriesUrl, sorted)
        }
        sorted
    }

    suspend fun extractStreamFromEpisodeUrl(episodeUrl: String): WatchHentaiStreamResult? = withContext(Dispatchers.IO) {
        if (episodeUrl.isBlank()) return@withContext null
        streamCache.get(episodeUrl)?.let { return@withContext it }

        val html = runCatching { getHtml(episodeUrl, referer = "$BASE_URL/") }.getOrDefault("")
        if (html.isBlank()) return@withContext null

        var streamUrl = ""

        // 1. Primary & Alternate player iframe URLs
        val primaryPlayerUrl = Regex("""data-primary-player-url=["']([^"']+)["']""").find(html)?.groupValues?.getOrNull(1)
        val alternatePlayerUrl = Regex("""data-alternate-player-url=["']([^"']+)["']""").find(html)?.groupValues?.getOrNull(1)
        val iframePlayerUrls = listOfNotNull(primaryPlayerUrl, alternatePlayerUrl)

        for (pUrl in iframePlayerUrls) {
            val pHtml = runCatching { getHtml(pUrl, referer = episodeUrl) }.getOrDefault("")
            if (pHtml.isNotBlank()) {
                val direct = Regex("""https?://[^\s"'<>]+\.(?:mp4|m3u8)[^\s"'<>]*""").find(pHtml)?.value
                    ?: Regex("""<source[^>]+src=["']([^"']+)["']""").find(pHtml)?.groupValues?.getOrNull(1)
                    ?: Regex("""<video[^>]+src=["']([^"']+)["']""").find(pHtml)?.groupValues?.getOrNull(1)
                if (!direct.isNullOrBlank()) {
                    streamUrl = direct.replace("\\/", "/")
                    break
                }
            }
        }

        // 2. Direct hstorage or mp4 in page
        if (streamUrl.isBlank()) {
            val directMatch = Regex("""(https?://hstorage\.xyz/files/[^"']+\.mp4)""").find(html)
                ?: Regex("""<source[^>]+src=["']([^"']+)["']""").find(html)
                ?: Regex("""https?://[^\s"'<>]+\.(?:mp4|m3u8)[^\s"'<>]*""").find(html)?.value
            streamUrl = directMatch?.let { if (it is kotlin.text.MatchResult) it.groupValues.getOrNull(1) ?: it.value else it.toString() }?.replace("\\/", "/").orEmpty()
        }

        // 3. DooPlay AJAX fallback
        if (streamUrl.isBlank()) {
            val typeMatch = Regex("""data-type=["']([^"']+)["']""").find(html)
            val postMatch = Regex("""data-post=["'](\d+)["']""").find(html)
            val numeMatch = Regex("""data-nume=["'](\d+)["']""").find(html)

            if (postMatch != null) {
                val typeVal = typeMatch?.groupValues?.getOrNull(1) ?: "tv"
                val postVal = postMatch.groupValues[1]
                val numeVal = numeMatch?.groupValues?.getOrNull(1) ?: "1"

                val form = FormBody.Builder()
                    .add("action", "doo_player_ajax")
                    .add("post", postVal)
                    .add("nume", numeVal)
                    .add("type", typeVal)
                    .build()

                val req = Request.Builder()
                    .url(AJAX_URL)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", episodeUrl)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .post(form)
                    .build()

                val respStr = runCatching {
                    AnililiHttpClient.client.newCall(req).execute().use { it.body?.string().orEmpty() }
                }.getOrDefault("")

                if (respStr.isNotBlank()) {
                    val json = runCatching { JSONObject(respStr) }.getOrNull()
                    val rawEmbed = json?.optString("embed_url").orEmpty().replace("\\/", "/")
                    if (rawEmbed.isNotBlank()) {
                        if (rawEmbed.contains(".mp4") || rawEmbed.contains(".m3u8")) {
                            streamUrl = rawEmbed
                        } else if (rawEmbed.startsWith("http")) {
                            val embedHtml = runCatching { getHtml(rawEmbed, referer = episodeUrl) }.getOrDefault("")
                            val vidMatch = Regex("""<source[^>]+src=["']([^"']+)["']""").find(embedHtml)
                                ?: Regex("""<video[^>]+src=["']([^"']+)["']""").find(embedHtml)
                                ?: Regex("""(https?://[^\s"'<>]+\.(?:mp4|m3u8)[^\s"'<>]*)""").find(embedHtml)
                            streamUrl = vidMatch?.groupValues?.getOrNull(1)?.replace("\\/", "/") ?: rawEmbed
                        }
                    }
                }
            }
        }

        if (streamUrl.isNotBlank()) {
            val res = WatchHentaiStreamResult(
                streamUrl = streamUrl,
                provider = "WatchHentai",
                referer = "https://watchhentai.net/"
            )
            streamCache.put(episodeUrl, res)
            return@withContext res
        }
        null
    }

    suspend fun getStreamForAnime(
        animeTitle: String,
        episodeNum: Int,
        altTitles: List<String> = emptyList()
    ): WatchHentaiStreamResult? = withContext(Dispatchers.IO) {
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
                val targetEp = eps.firstOrNull { it.episodeNumber == episodeNum }
                    ?: eps.getOrNull(episodeNum - 1)
                    ?: eps.firstOrNull() ?: continue
                val stream = extractStreamFromEpisodeUrl(targetEp.url)
                if (stream != null && stream.streamUrl.isNotEmpty()) return@withContext stream
            }
        }
        null
    }

    suspend fun getEpisodeListForAnime(
        animeTitle: String,
        altTitles: List<String> = emptyList()
    ): List<WatchHentaiEpisode> = withContext(Dispatchers.IO) {
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
            if (eps.isNotEmpty()) return@withContext eps
        }
        emptyList()
    }
}

