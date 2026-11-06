package com.lagradost.cloudstream3.ui.animebox.extractors
import com.lagradost.cloudstream3.ui.animebox.api.AnimeBoxConfig
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class AnimeDekhoStreamResult(
    val hlsUrl: String,
    val referer: String,
    val subtitleUrl: String = "",
    val subtitles: List<Map<String, String>> = emptyList(),
    val provider: String = "animedekho",
    val title: String = "",
    val mp4Url: String? = null
)

object AnimeDekhoHindiExtractor {

    private val cookieStore = mutableMapOf<String, List<Cookie>>()

    private val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .cookieJar(object : CookieJar {
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val host = url.host
                val existing = cookieStore[host]?.toMutableList() ?: mutableListOf()
                cookies.forEach { newCookie ->
                    existing.removeAll { it.name == newCookie.name }
                    existing.add(newCookie)
                }
                cookieStore[host] = existing
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: emptyList()
            }
        })
        .build()

    private const val DEKHO_BASE = "https://animedekho.tv"
    private const val FRIBB_URL = "https://raw.githubusercontent.com/Fribb/anime-lists/master/anime-list-full.json"
    private const val ANIZIP_URL = "https://api.ani.zip/mappings"
    private val TMDB_API_KEY get() = AnimeBoxConfig.TMDB_API_KEY
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private var fribbCache: JSONArray? = null
    private var fribbCacheTime: Long = 0L
    private val tmdbSearchCache = android.util.LruCache<Int, Pair<String, Boolean>>(100)

    // Dedicated TMDB ID to AnimeDekho URL slug mappings
    private val TMDB_SLUG_MAP = mapOf(
        80609 to "kiteretsu",                     // Kiteretsu Daihyakka
        65719 to "chimpui",                      // Chimpui (Chinpui)
        66251 to "monster-kid",                  // Kaibutsu-kun / Monster Kid (1980)
        204045 to "monster-kid",                 // Kaibutsu-kun (1968)
        207937 to "ultra-b",                     // Ultra B
        209246 to "hagemaru",                    // Tsurupika Hagemaru-kun
        80885 to "ninja-hattori",                // Ninja Hattori-kun (1981)
        158198 to "ninja-hattori-kun-returns",   // Ninja Hattori-kun Returns (2012)
        132791 to "perman",                      // Perman (1983)
        65739 to "perman",                       // Perman (1967)
        37854 to "one-piece",
        30983 to "detective-conan",
        46260 to "naruto",
        31910 to "naruto-shippuden",
        30984 to "bleach",
        60572 to "pokemon",
        34747 to "dragon-ball",
        12697 to "dragon-ball-z",
        44872 to "idaten-jump",
        85937 to "demon-slayer-kimetsu-no-yaiba"
    )

    private fun toSlug(text: String): String {
        return text.lowercase()
            .replace("[^a-z0-9]+".toRegex(), "-")
            .trim('-')
    }

    private suspend fun fetchHtml(url: String, referer: String = "$DEKHO_BASE/"): String = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", referer)
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() ?: "" else ""
            }
        } catch (_: Exception) {
            ""
        }
    }

    private suspend fun bypassVerifyIfNeeded(html: String, pageUrl: String): String = withContext(Dispatchers.IO) {
        try {
            val pattern1 = Pattern.compile("name=[\"']shortlink[^\"']*[\"'][^>]*value=[\"'](https?://[^\"']*(?:verify\\.php)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
            val matcher1 = pattern1.matcher(html)
            var verifyUrl = if (matcher1.find()) matcher1.group(1) else null

            if (verifyUrl == null) {
                val pattern2 = Pattern.compile("value=[\"'](https?://[^\"']*(?:verify\\.php)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
                val matcher2 = pattern2.matcher(html)
                if (matcher2.find()) {
                    verifyUrl = matcher2.group(1)
                }
            }

            if (!verifyUrl.isNullOrEmpty()) {
                val req = Request.Builder()
                    .url(verifyUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", pageUrl)
                    .build()
                client.newCall(req).execute().close()

                // Re-fetch the unlocked page
                return@withContext fetchHtml(pageUrl, referer = "$DEKHO_BASE/")
            }
        } catch (_: Exception) {}
        return@withContext html
    }

    private suspend fun getFribbList(): JSONArray? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (fribbCache != null && (now - fribbCacheTime < 6 * 3600 * 1000L)) {
            return@withContext fribbCache
        }
        try {
            val req = Request.Builder()
                .url(FRIBB_URL)
                .addHeader("User-Agent", USER_AGENT)
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val jsonStr = resp.body?.string() ?: ""
                    if (jsonStr.isNotEmpty()) {
                        val arr = JSONArray(jsonStr)
                        fribbCache = arr
                        fribbCacheTime = now
                        return@withContext arr
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext fribbCache
    }

    private suspend fun getAniZip(anilistId: Int): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$ANIZIP_URL?anilist_id=$anilistId")
                .addHeader("User-Agent", USER_AGENT)
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val jsonStr = resp.body?.string() ?: ""
                    if (jsonStr.isNotEmpty()) return@withContext JSONObject(jsonStr)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private fun normalizeTmdbId(v: Any?, isMovie: Boolean): String? {
        if (v == null || v == JSONObject.NULL) return null
        if (v is JSONObject) {
            val first = if (isMovie) {
                if (v.has("movie") && !v.isNull("movie")) v.get("movie") else v.opt("tv")
            } else {
                if (v.has("tv") && !v.isNull("tv")) v.get("tv") else v.opt("movie")
            }
            if (first == null || first == JSONObject.NULL) return null
            val s = first.toString().trim()
            return if (s.isNotEmpty() && s != "0") s else null
        }
        val s = v.toString().split(",")[0].trim()
        return if (s.isNotEmpty() && s != "0") s else null
    }

    private suspend fun searchTmdbByTitle(title: String, isMovie: Boolean): Pair<String, String>? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        try {
            val endpoint = if (isMovie) "movie" else "tv"
            val encoded = java.net.URLEncoder.encode(title.trim(), "UTF-8")
            val url = "https://api.themoviedb.org/3/search/$endpoint?api_key=$TMDB_API_KEY&query=$encoded"
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val jsonStr = resp.body?.string() ?: ""
                    if (jsonStr.isNotEmpty()) {
                        val json = JSONObject(jsonStr)
                        val results = json.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            val first = results.getJSONObject(0)
                            val id = first.optInt("id", -1)
                            val tmdbName = if (isMovie) first.optString("title", "") else first.optString("name", "")
                            if (id > 0) return@withContext Pair(id.toString(), tmdbName)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private suspend fun fetchAniListTitlesAndFormat(anilistId: Int): Triple<String, String, Boolean> = withContext(Dispatchers.IO) {
        try {
            val query = "query (\$id: Int) { Media (id: \$id, type: ANIME) { id format title { english romaji } } }"
            val body = JSONObject().apply {
                put("query", query)
                put("variables", JSONObject().put("id", anilistId))
            }
            val reqBody = body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val req = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(reqBody)
                .addHeader("User-Agent", USER_AGENT)
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val jsonStr = resp.body?.string() ?: ""
                    if (jsonStr.isNotEmpty()) {
                        val json = JSONObject(jsonStr)
                        val media = json.optJSONObject("data")?.optJSONObject("Media")
                        if (media != null) {
                            val format = media.optString("format", "TV").uppercase()
                            val isMovie = format == "MOVIE"
                            val titleObj = media.optJSONObject("title")
                            val english = titleObj?.optString("english", "") ?: ""
                            val romaji = titleObj?.optString("romaji", "") ?: ""
                            return@withContext Triple(english, romaji, isMovie)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext Triple("", "", false)
    }

    suspend fun extractAllDekhoStreams(
        anilistId: Int,
        episode: Int,
        animeTitle: String? = null
    ): List<AnimeDekhoStreamResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<AnimeDekhoStreamResult>()
        try {
            val aniZip = getAniZip(anilistId)
            val mappings = aniZip?.optJSONObject("mappings")
            val malId = mappings?.optInt("mal_id", -1)
            val imdbId = mappings?.optString("imdb_id", "")
            val aniZipType = mappings?.optString("type", "")?.lowercase() ?: ""
            var isMovie = aniZipType == "movie"

            var tmdbId: String? = null
            var targetSeason = 1
            var targetEpisode = episode
            var slug = ""

            var isDemonSlayer = false
            when (anilistId) {
                101922 -> { // S1 (Tanjiro Kamado, Unwavering Resolve Arc)
                    tmdbId = "85937"
                    targetSeason = 1
                    targetEpisode = episode
                    isMovie = false
                    isDemonSlayer = true
                    slug = "demon-slayer-kimetsu-no-yaiba"
                }
                129874 -> { // Mugen Train Arc TV
                    tmdbId = "85937"
                    targetSeason = 2
                    targetEpisode = episode
                    isMovie = false
                    isDemonSlayer = true
                    slug = "demon-slayer-kimetsu-no-yaiba"
                }
                112151 -> { // Mugen Train Movie (Dekho hosts Mugen Train under S2 TV)
                    tmdbId = "85937"
                    targetSeason = 2
                    targetEpisode = episode.coerceIn(1, 7)
                    isMovie = false
                    isDemonSlayer = true
                    slug = "demon-slayer-kimetsu-no-yaiba"
                }
                142329 -> { // Entertainment District Arc
                    tmdbId = "85937"
                    targetSeason = 3
                    targetEpisode = episode
                    isMovie = false
                    isDemonSlayer = true
                    slug = "demon-slayer-kimetsu-no-yaiba"
                }
                145139 -> { // Swordsmith Village Arc
                    tmdbId = "85937"
                    targetSeason = 4
                    targetEpisode = episode
                    isMovie = false
                    isDemonSlayer = true
                    slug = "demon-slayer-kimetsu-no-yaiba"
                }
                166240 -> { // Hashira Training Arc
                    tmdbId = "85937"
                    targetSeason = 5
                    targetEpisode = episode
                    isMovie = false
                    isDemonSlayer = true
                    slug = "demon-slayer-kimetsu-no-yaiba"
                }
            }

            // 0. Static Verified TMDB & Long Running Mappings
            if (!isDemonSlayer) {
                val staticMovieId = com.lagradost.cloudstream3.ui.animebox.api.AnimeMovieTmdbMapping.getMovieTmdbId(anilistId)
                if (staticMovieId != null && staticMovieId > 0) {
                    tmdbId = staticMovieId.toString()
                    isMovie = true
                    slug = TMDB_SLUG_MAP[staticMovieId] ?: ""
                }

                val longRunningId = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getLongRunningTmdbId(anilistId)
                if (longRunningId != null && longRunningId > 0) {
                    tmdbId = longRunningId.toString()
                    slug = TMDB_SLUG_MAP[longRunningId] ?: ""
                }

                // 1. Try AniZip mappings
                if (tmdbId == null && mappings != null && mappings.has("themoviedb_id")) {
                    tmdbId = normalizeTmdbId(mappings.get("themoviedb_id"), isMovie)
                }
            }

            // 2. Check AniZip episodes data for season & episode mapping
            if (aniZip != null && aniZip.has("episodes")) {
                val episodesObj = aniZip.optJSONObject("episodes")
                if (episodesObj != null) {
                    val epEntry = episodesObj.optJSONObject(episode.toString())
                    if (epEntry != null) {
                        val sn = epEntry.optInt("seasonNumber", 1)
                        val en = epEntry.optInt("episodeNumber", episode)
                        if (sn > 0) targetSeason = sn
                        if (en > 0) targetEpisode = en
                    }
                }
            }

            // 3. Fallback to Fribb Anime List
            if (tmdbId == null) {
                val fribbList = getFribbList()
                if (fribbList != null) {
                    for (i in 0 until fribbList.length()) {
                        val entry = fribbList.optJSONObject(i) ?: continue
                        val entryAnilistId = entry.optInt("anilist_id", -1)
                        val entryMalId = entry.optInt("mal_id", -2)
                        val entryImdbId = entry.optString("imdb_id", "")

                        val matchAnilist = entryAnilistId == anilistId
                        val matchMal = malId != null && malId > 0 && entryMalId == malId
                        val matchImdb = imdbId != null && imdbId.isNotEmpty() && entryImdbId == imdbId

                        if (matchAnilist || matchMal || matchImdb) {
                            if (entry.optString("type", "").lowercase() == "movie") {
                                isMovie = true
                            }
                            val tmdbVal = entry.opt("themoviedb_id")
                            val norm = normalizeTmdbId(tmdbVal, isMovie)
                            if (norm != null) {
                                tmdbId = norm
                                val sObj = entry.optJSONObject("season")
                                if (sObj != null && sObj.has("tmdb")) {
                                    targetSeason = sObj.optInt("tmdb", targetSeason)
                                }
                                break
                            }
                        }
                    }
                }
            }

            // 4. Fallback: Search TMDB by title
            if (tmdbId == null) {
                val cached = tmdbSearchCache.get(anilistId)
                if (cached != null) {
                    tmdbId = cached.first
                    if (cached.second) isMovie = true
                } else {
                    val (englishTitle, romajiTitle, aniIsMovie) = fetchAniListTitlesAndFormat(anilistId)
                    if (aniIsMovie) isMovie = true

                    val titleToTry = if (!animeTitle.isNullOrBlank()) animeTitle else englishTitle

                    var searchRes: Pair<String, String>? = null
                    if (titleToTry.isNotEmpty()) {
                        searchRes = searchTmdbByTitle(titleToTry, isMovie)
                    }
                    if (searchRes == null && englishTitle.isNotEmpty() && englishTitle != titleToTry) {
                        searchRes = searchTmdbByTitle(englishTitle, isMovie)
                    }
                    if (searchRes == null && romajiTitle.isNotEmpty()) {
                        searchRes = searchTmdbByTitle(romajiTitle, isMovie)
                    }
                    if (searchRes == null && titleToTry.isNotEmpty()) {
                        searchRes = searchTmdbByTitle(titleToTry, !isMovie)
                        if (searchRes != null) isMovie = !isMovie
                    }

                    if (searchRes != null) {
                        tmdbId = searchRes.first
                        if (slug.isEmpty()) slug = toSlug(searchRes.second)
                        tmdbSearchCache.put(anilistId, Pair(tmdbId, isMovie))
                    }
                }
            }

            val tmdbInt = tmdbId?.toIntOrNull()
            if (slug.isEmpty() && tmdbInt != null) {
                slug = TMDB_SLUG_MAP[tmdbInt] ?: ""
            }
            if (slug.isEmpty()) {
                val rawTitle = animeTitle ?: ""
                if (rawTitle.isNotEmpty()) slug = toSlug(rawTitle)
            }

            // =========================================================================
            // METHOD 1: AnimeDekho Embed URL (Primary Method)
            // =========================================================================
            val embedUrls = mutableListOf<String>()
            if (isDemonSlayer) {
                embedUrls.add("$DEKHO_BASE/embed/85937/$targetSeason-$targetEpisode")
                when (anilistId) {
                    142329 -> {
                        embedUrls.add("$DEKHO_BASE/embed/85937/2-${targetEpisode + 7}")
                        embedUrls.add("$DEKHO_BASE/embed/85937/2-$targetEpisode")
                    }
                    145139 -> {
                        embedUrls.add("$DEKHO_BASE/embed/85937/3-$targetEpisode")
                    }
                    166240 -> {
                        embedUrls.add("$DEKHO_BASE/embed/85937/4-$targetEpisode")
                    }
                }
            } else if (tmdbId != null) {
                if (isMovie) {
                    embedUrls.add("$DEKHO_BASE/embed/$tmdbId")
                    embedUrls.add("$DEKHO_BASE/embed-movie/$tmdbId")
                } else {
                    embedUrls.add("$DEKHO_BASE/embed/$tmdbId/$targetSeason-$targetEpisode")
                    embedUrls.add("$DEKHO_BASE/embed/$tmdbId/$targetEpisode")
                }
            }

            for (embedUrl in embedUrls) {
                try {
                    val embedHtml = fetchHtml(embedUrl, referer = "$DEKHO_BASE/")
                    if (embedHtml.isNotEmpty() && !embedHtml.contains("Server Error", ignoreCase = true) && !embedHtml.contains("Report to Admin", ignoreCase = true)) {
                        val iframePattern = Pattern.compile("<iframe[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                        val matcher = iframePattern.matcher(embedHtml)
                        while (matcher.find()) {
                            val iframeSrc = matcher.group(1) ?: continue
                            if (iframeSrc.contains("vidmoly", ignoreCase = true)) {
                                extractVidmolyHls(iframeSrc)?.let { results.add(it) }
                            } else if (iframeSrc.contains("as-cdn", ignoreCase = true) || iframeSrc.contains("neocdn", ignoreCase = true) || iframeSrc.contains("zephyr", ignoreCase = true) || iframeSrc.contains("ravok", ignoreCase = true)) {
                                extractNeoCdnHls(iframeSrc)?.let { results.add(it) }
                            }
                        }
                        if (results.isNotEmpty()) {
                            return@withContext results.distinctBy { it.hlsUrl }
                        }
                    }
                } catch (_: Exception) {}
            }

            // =========================================================================
            // METHOD 2: Slug / Episode / Movie Page (Fallback Method if Embed Fails)
            // =========================================================================
            val slugUrls = mutableListOf<String>()
            if (slug.isNotEmpty()) {
                if (isMovie) {
                    slugUrls.add("$DEKHO_BASE/movie-hindi/$slug/")
                    slugUrls.add("$DEKHO_BASE/movie-hindi/$slug")
                } else {
                    slugUrls.add("$DEKHO_BASE/epi/$slug-${targetSeason}x$targetEpisode/")
                    slugUrls.add("$DEKHO_BASE/epi/$slug-1x$targetEpisode/")
                }
            }

            for (slugUrl in slugUrls) {
                try {
                    var pageHtml = fetchHtml(slugUrl, referer = "$DEKHO_BASE/")
                    if (pageHtml.isEmpty()) continue

                    // Unlock adblock / skip protection if present
                    pageHtml = bypassVerifyIfNeeded(pageHtml, slugUrl)

                    // Find all server options / data-src encoded attributes
                    val dataPattern = Pattern.compile("(?:data-src|data-option)=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                    val dataMatcher = dataPattern.matcher(pageHtml)
                    val serverUrls = mutableListOf<String>()

                    while (dataMatcher.find()) {
                        val b64 = dataMatcher.group(1) ?: continue
                        try {
                            val decoded = String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT), Charsets.UTF_8).trim()
                            if (decoded.isNotEmpty() && !decoded.contains("play.php")) {
                                serverUrls.add(decoded)
                            }
                        } catch (_: Exception) {
                            if (b64.startsWith("http") && !b64.contains("play.php")) {
                                serverUrls.add(b64)
                            }
                        }
                    }

                    for (serverUrl in serverUrls.distinct()) {
                        try {
                            val serverHtml = fetchHtml(serverUrl, referer = slugUrl)
                            if (serverHtml.isEmpty()) continue

                            val iframePattern = Pattern.compile("<iframe[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                            val iframeMatcher = iframePattern.matcher(serverHtml)
                            while (iframeMatcher.find()) {
                                val ifr = iframeMatcher.group(1) ?: continue
                                if (ifr.contains("vidmoly", ignoreCase = true)) {
                                    extractVidmolyHls(ifr)?.let { results.add(it) }
                                } else if (ifr.contains("as-cdn", ignoreCase = true) || ifr.contains("neocdn", ignoreCase = true) || ifr.contains("zephyr", ignoreCase = true) || ifr.contains("ravok", ignoreCase = true)) {
                                    extractNeoCdnHls(ifr)?.let { results.add(it) }
                                }
                            }
                            if (results.isNotEmpty()) break
                        } catch (_: Exception) {}
                    }

                    if (results.isNotEmpty()) {
                        val sorted = results.distinctBy { it.hlsUrl }.sortedByDescending { if (it.provider.contains("Vidmoly", ignoreCase = true)) 1 else 0 }
                        return@withContext sorted
                    }
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext results.distinctBy { it.hlsUrl }
    }

    private suspend fun extractNeoCdnHls(iframeSrc: String): AnimeDekhoStreamResult? = withContext(Dispatchers.IO) {
        try {
            if (iframeSrc.contains(".m3u8", ignoreCase = true)) {
                val host = try {
                    val u = Uri.parse(iframeSrc)
                    "${u.scheme}://${u.host}/"
                } catch (_: Exception) { "$DEKHO_BASE/" }
                return@withContext AnimeDekhoStreamResult(
                    hlsUrl = iframeSrc,
                    referer = host,
                    provider = "animedekho (NeoCDN)",
                    title = "Hindi (NeoCDN)"
                )
            }

            val iframeUri = Uri.parse(iframeSrc)
            val scheme = iframeUri.scheme ?: "https"
            val hostName = iframeUri.host ?: "as-cdn26.top"
            val host = "$scheme://$hostName"

            val queryData = iframeUri.getQueryParameter("data")
                ?: iframeUri.getQueryParameter("id")
                ?: iframeUri.getQueryParameter("hash")
                ?: iframeUri.getQueryParameter("v")
                ?: ""

            val pathLast = iframeUri.path?.trimEnd('/')?.substringAfterLast('/') ?: ""
            val videoId = if (queryData.isNotEmpty()) {
                queryData
            } else if (pathLast != "index.php" && pathLast != "player.php") {
                pathLast
            } else ""

            if (videoId.isNotEmpty()) {
                val getVideoUrl = "$host/player/index.php?data=$videoId&do=getVideo"
                val formBody = FormBody.Builder()
                    .add("hash", videoId)
                    .add("r", "$DEKHO_BASE/")
                    .build()

                val postReq = Request.Builder()
                    .url(getVideoUrl)
                    .post(formBody)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", iframeSrc)
                    .addHeader("Origin", host)
                    .addHeader("X-Requested-With", "XMLHttpRequest")
                    .addHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .build()

                client.newCall(postReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val data = resp.body?.string() ?: ""
                        if (data.isNotEmpty() && data.startsWith("{")) {
                            val json = JSONObject(data)
                            val videoSource = when {
                                json.has("videoSource") && json.optString("videoSource").isNotEmpty() -> json.getString("videoSource")
                                json.has("securedLink") && json.optString("securedLink").isNotEmpty() -> json.getString("securedLink")
                                json.has("file") && json.optString("file").isNotEmpty() -> json.getString("file")
                                json.has("url") && json.optString("url").isNotEmpty() -> json.getString("url")
                                else -> ""
                            }

                            if (videoSource.isNotEmpty()) {
                                val isRavok = hostName.contains("ravok", ignoreCase = true)
                                return@withContext AnimeDekhoStreamResult(
                                    hlsUrl = videoSource,
                                    referer = "$host/",
                                    provider = if (isRavok) "animedekho" else "animedekho (NeoCDN)",
                                    title = if (isRavok) "Hindi (Ravok)" else "Hindi (NeoCDN)"
                                )
                            }
                        }
                    }
                }
            }

            // Fallback: Fetch iframe HTML directly and regex parse m3u8
            val pageHtml = fetchHtml(iframeSrc, referer = "$DEKHO_BASE/")
            if (pageHtml.isNotEmpty()) {
                val m3u8Pattern = Pattern.compile("[\"'](https?://[^\"']+\\.m3u8[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
                val m3u8Matcher = m3u8Pattern.matcher(pageHtml)
                if (m3u8Matcher.find()) {
                    val directM3u8 = m3u8Matcher.group(1) ?: ""
                    if (directM3u8.isNotEmpty()) {
                        return@withContext AnimeDekhoStreamResult(
                            hlsUrl = directM3u8,
                            referer = "$host/",
                            provider = "animedekho (NeoCDN)",
                            title = "Hindi (NeoCDN)"
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        return@withContext null
    }

    private suspend fun extractVidmolyHls(vidmolyUrl: String): AnimeDekhoStreamResult? = withContext(Dispatchers.IO) {
        try {
            val normalizedDomain = vidmolyUrl.replace("vidmoly.biz", "vidmoly.net").replace("vidmoly.to", "vidmoly.net").replace("vidmoly.me", "vidmoly.net")
            val cleanUrl = if (normalizedDomain.contains("/w/")) {
                normalizedDomain.replaceFirst("/w/", "/embed-") + ".html"
            } else if (!normalizedDomain.contains("embed-") && !normalizedDomain.endsWith(".html")) {
                val id = normalizedDomain.trimEnd('/').substringAfterLast('/')
                "https://vidmoly.net/embed-$id.html"
            } else normalizedDomain

            val req = Request.Builder()
                .url(cleanUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", "$DEKHO_BASE/")
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val html = resp.body?.string() ?: ""
                if (html.isEmpty()) return@withContext null

                // Search for sources: [{file: "https://...master.m3u8"}] or file: "https://...master.m3u8"
                val hlsPattern = Pattern.compile("[\"']?file[\"']?\\s*:\\s*[\"'](https?://[^\"']+\\.m3u8[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
                val matcher = hlsPattern.matcher(html)
                if (matcher.find()) {
                    val m3u8Url = matcher.group(1) ?: return@withContext null
                    return@withContext AnimeDekhoStreamResult(
                        hlsUrl = m3u8Url,
                        referer = "https://vidmoly.net/",
                        provider = "animedekho (Vidmoly)",
                        title = "Hindi (Vidmoly)"
                    )
                }
            }
        } catch (_: Exception) {}
        return@withContext null
    }

    suspend fun extractDekhoStream(
        anilistId: Int,
        episode: Int,
        animeTitle: String? = null
    ): AnimeDekhoStreamResult? = withContext(Dispatchers.IO) {
        val all = extractAllDekhoStreams(anilistId, episode, animeTitle)
        return@withContext all.firstOrNull()
    }
}
