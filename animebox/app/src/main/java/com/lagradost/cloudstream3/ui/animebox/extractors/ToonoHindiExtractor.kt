package com.lagradost.cloudstream3.ui.animebox.extractors

import com.lagradost.cloudstream3.ui.animebox.api.AnimeBoxConfig

import android.util.Base64
import com.lagradost.cloudstream3.ui.animebox.api.AniListClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ToonoStreamResult(
    val hlsUrl: String,
    val referer: String,
    val provider: String = "RareAnime (Hindi)",
    val title: String = ""
)

object ToonoHindiExtractor {

    private val cookieStore = mutableMapOf<String, List<Cookie>>()

    private val client: OkHttpClient by lazy {
        val builder = try {
            com.lagradost.cloudstream3.app.baseClient.newBuilder()
        } catch (e: Throwable) {
            OkHttpClient.Builder()
        }
        builder
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
    }

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val BASE_URL = "https://www.rareanimes.mov"

    private val postCache = ConcurrentHashMap<String, String>()

    private val NON_ANIME_CARTOON_BLACKLIST = setOf(
        "superman", "batman", "justice league", "justice-league", "oggy", "ben 10", "ben-10",
        "teen titans", "teen-titans", "jackie chan", "jackie-chan", "supa strikas", "supa-strikas",
        "tom and jerry", "tom-and-jerry", "powerpuff girls", "powerpuff-girls", "looney tunes",
        "scooby-doo", "scooby doo", "spider-man", "spiderman", "avengers", "iron man", "hulk",
        "thor", "star wars", "transformers", "ninja turtles", "tmnt", "mr bean", "mr-bean",
        "sheep and wolves", "sheep-wolves", "official-android-app", "grand-theft-auto", "gta"
    )

    private val KNOWN_ANIME_ALIASES = mapOf(
        "chinpui" to listOf("chimpui", "chinpui"),
        "chimpui" to listOf("chimpui", "chinpui"),
        "perman" to listOf("perman"),
        "ninja hattori" to listOf("ninja hattori", "ninja hattori returns", "ninja hattori-kun"),
        "hattori" to listOf("ninja hattori", "ninja hattori-kun"),
        "monster kid" to listOf("monster kid", "the monster kid", "kaibutsu-kun"),
        "kaibutsu" to listOf("monster kid", "kaibutsu-kun"),
        "kiteretsu" to listOf("kiteretsu", "kiteretsu daihyakka"),
        "gutsy frog" to listOf("the gutsy frog", "dokonjou gaeru", "gutsy frog"),
        "dokonjou gaeru" to listOf("the gutsy frog", "dokonjou gaeru"),
        "gon the stone age boy" to listOf("gon the stone age boy", "gon", "hajime ningen gon"),
        "gon" to listOf("gon the stone age boy", "hajime ningen gon"),
        "hajime ningen gon" to listOf("gon the stone age boy", "hajime ningen gon"),
        "luckyman" to listOf("luckyman", "tottemo! luckyman"),
        "tottemo luckyman" to listOf("luckyman", "tottemo! luckyman"),
        "ultra b" to listOf("ultra b"),
        "dinosaur king" to listOf("dinosaur king", "kodai ouja kyouryuu king"),
        "idaten jump" to listOf("idaten jump"),
        "beyblade" to listOf("beyblade"),
        "yokai watch" to listOf("yokai watch", "youkai watch", "yo-kai watch"),
        "youkai watch" to listOf("yokai watch", "youkai watch", "yo-kai watch"),
        "kochikame" to listOf("kochikame", "kochira katsushika-ku kameari kouenmae hashutsujo"),
        "mighty cat masked niyandar" to listOf("mighty cat masked niyandar", "masked niyandar", "nyani ga nyandaa nyandaa kamen", "niyandar"),
        "niyandar" to listOf("mighty cat masked niyandar", "masked niyandar", "nyani ga nyandaa nyandaa kamen", "niyandar"),
        "pokemon" to listOf("pokemon", "pocket monsters", "pokemon horizons")
    )

    /**
     * Clean and prepare title candidates for searching on RareAnimes.mov
     */
    private suspend fun getSearchQueries(anilistId: Int, animeTitle: String): List<String> {
        val queries = mutableListOf<String>()

        fun sanitize(t: String): String {
            var s = t.replace(Regex("""[\(\[\{].*?[\)\]\}]"""), " ")
            s = s.replace(Regex(""":\s*(?:The\s+Series|Season\s*\d+|Part\s*\d+|2nd\s*Season|\d+(?:st|nd|rd|th)\s*Season).*""", RegexOption.IGNORE_CASE), " ")
            s = s.replace('é', 'e').replace('É', 'E')
            s = s.replace(Regex("""[^a-zA-Z0-9\s]"""), " ")
            return s.trim().split(Regex("""\s+""")).joinToString(" ")
        }

        val cleaned = sanitize(animeTitle)
        if (cleaned.isNotEmpty()) {
            queries.add(cleaned)
        }

        // Check if the title matches known special/retro aliases (e.g. Chinpui -> Chimpui, Perman, Ultra B)
        val lowerRaw = animeTitle.lowercase()
        for ((key, aliases) in KNOWN_ANIME_ALIASES) {
            if (lowerRaw.contains(key) || aliases.any { lowerRaw.contains(it) }) {
                aliases.forEach { alias ->
                    val sAlias = sanitize(alias)
                    if (sAlias.isNotEmpty() && !queries.contains(sAlias)) {
                        queries.add(0, sAlias)
                    }
                }
            }
        }

        if (anilistId > 0) {
            try {
                val details = AniListClient.getAnimeDetails(anilistId)
                if (details != null) {
                    val media = JSONObject(details).optJSONObject("data")?.optJSONObject("Media")
                    val t = media?.optJSONObject("title")
                    t?.optString("english", "")?.takeIf { it.isNotEmpty() }?.let {
                        val s = sanitize(it)
                        if (s.isNotEmpty() && !queries.contains(s)) queries.add(0, s)
                    }
                    t?.optString("romaji", "")?.takeIf { it.isNotEmpty() }?.let {
                        val s = sanitize(it)
                        if (s.isNotEmpty() && !queries.contains(s)) queries.add(s)
                    }
                    val synonyms = media?.optJSONArray("synonyms")
                    if (synonyms != null) {
                        for (i in 0 until synonyms.length()) {
                            val syn = synonyms.optString(i, "")
                            if (syn.isNotEmpty()) {
                                val s = sanitize(syn)
                                if (s.isNotEmpty() && !queries.contains(s)) queries.add(s)
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            // TMDB & AniZip enrichment
            try {
                val req = Request.Builder()
                    .url("https://api.ani.zip/mappings?anilist_id=$anilistId")
                    .addHeader("User-Agent", USER_AGENT)
                    .build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val jsonStr = resp.body?.string() ?: ""
                        if (jsonStr.isNotEmpty()) {
                            val json = JSONObject(jsonStr)
                            val titles = json.optJSONObject("titles")
                            if (titles != null) {
                                titles.optString("en", "").takeIf { it.isNotEmpty() }?.let { queries.add(sanitize(it)) }
                                titles.optString("ja", "").takeIf { it.isNotEmpty() }?.let { queries.add(sanitize(it)) }
                                titles.optString("x-jat", "").takeIf { it.isNotEmpty() }?.let { queries.add(sanitize(it)) }
                            }
                            val tmdbId = json.optJSONObject("mappings")?.optString("tmdb_id", "")
                            if (!tmdbId.isNullOrEmpty()) {
                                val tmdbReq = Request.Builder()
                                    .url("https://api.themoviedb.org/3/tv/$tmdbId?api_key=${AnimeBoxConfig.TMDB_API_KEY}")
                                    .addHeader("User-Agent", USER_AGENT)
                                    .build()
                                client.newCall(tmdbReq).execute().use { tmdbResp ->
                                    if (tmdbResp.isSuccessful) {
                                        val tmdbJson = JSONObject(tmdbResp.body?.string() ?: "")
                                        tmdbJson.optString("name", "").takeIf { it.isNotEmpty() }?.let { queries.add(sanitize(it)) }
                                        tmdbJson.optString("original_name", "").takeIf { it.isNotEmpty() }?.let { queries.add(sanitize(it)) }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return queries.filter { it.length >= 2 }.distinct()
    }

    private fun unescapeHtml(text: String): String {
        return text.replace("&#8211;", "-")
            .replace("&#8217;", "'")
            .replace("&#038;", "&")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
    }

    /**
     * Strict verification that a candidate post on rareanimes.mov belongs to the requested anime.
     * Prevents running a completely wrong anime or non-anime cartoon!
     */
    private fun isTitleMatch(postTitle: String, targetQueries: List<String>): Boolean {
        val cleanPost = unescapeHtml(postTitle).lowercase().replace(Regex("""[^a-z0-9\s]"""), " ")
        val postWords = cleanPost.split(Regex("""\s+""")).filter { it.length > 1 }.toSet()

        // Strict boundary: Reject non-anime Western cartoons
        for (b in NON_ANIME_CARTOON_BLACKLIST) {
            val bPattern = Pattern.compile("""\b""" + Pattern.quote(b) + """\b""", Pattern.CASE_INSENSITIVE)
            if (bPattern.matcher(cleanPost).find()) {
                val isTargetActuallyBlacklistedItem = targetQueries.any {
                    bPattern.matcher(it.lowercase()).find()
                }
                if (!isTargetActuallyBlacklistedItem) {
                    return false
                }
            }
        }

        // Special pass for known aliases (Chimpui/Chinpui, Ultra B, Dokonjou Gaeru, Kochikame, etc.)
        for (q in targetQueries) {
            val qLower = q.lowercase().trim()
            for ((key, aliases) in KNOWN_ANIME_ALIASES) {
                if (qLower.contains(key) || aliases.any { qLower.contains(it) }) {
                    if (aliases.any { alias ->
                        val cleanA = alias.lowercase()
                        cleanPost.contains(cleanA) || cleanA.split(" ").all { w -> postWords.contains(w) || cleanPost.contains(w) }
                    }) {
                        return true
                    }
                }
            }
        }

        return targetQueries.any { query ->
            val cleanQ = query.lowercase().replace(Regex("""[^a-z0-9\s]"""), " ")
            val qWords = cleanQ.split(Regex("""\s+""")).filter {
                it.length > 1 && it !in setOf("the", "season", "part", "movie", "series", "hindi", "episodes", "dubbed", "uncut", "hd")
            }
            if (qWords.isEmpty()) false
            else qWords.all { word -> postWords.contains(word) || cleanPost.contains(word) }
        }
    }

    private fun parseSeasonNumber(text: String): Int {
        val clean = text.lowercase()
        val ordinalMatch = Regex("""\b(\d+)(?:st|nd|rd|th)\s*season\b""").find(clean)
        if (ordinalMatch != null) return ordinalMatch.groupValues[1].toIntOrNull() ?: 1

        val stdMatch = Regex("""\b(?:season|s)\s*0*(\d+)\b""").find(clean)
        if (stdMatch != null) return stdMatch.groupValues[1].toIntOrNull() ?: 1

        val partMatch = Regex("""\bpart\s*0*(\d+)\b""").find(clean)
        if (partMatch != null) return partMatch.groupValues[1].toIntOrNull() ?: 1

        if (clean.contains("season iv") || clean.contains("season 4")) return 4
        if (clean.contains("season iii") || clean.contains("season 3")) return 3
        if (clean.contains("season ii") || clean.contains("season 2")) return 2
        if (clean.contains("season i") || clean.contains("season 1")) return 1

        return 1
    }

    /**
     * Decode JuicyCodes packer found on argon.razorshell.space
     */
    fun decodeJuicyCodes(encoded: String): String {
        return RareAnimeHindiExtractor.decodeJuicyCodes(encoded) ?: ""
    }

    /**
     * Main extraction method for RareAnime:
     * Searches rareanimes.mov, parses posts and codedew multiquality zipper links,
     * extracts argon.razorshell.space embed, decodes JuicyCodes, and returns master HLS stream.
     */
    suspend fun extract(
        anilistId: Int,
        episode: Int,
        animeTitle: String
    ): ToonoStreamResult? = withContext(Dispatchers.IO) {
        try {
            // Priority 1: Anilili's verified RareAnimes RSS + Argon/JuicyCodes extraction engine
            val verifiedRes = try {
                RareAnimeHindiExtractor.extract(anilistId, episode, animeTitle)
            } catch (_: Exception) { null }

            if (verifiedRes != null && verifiedRes.hlsUrl.isNotEmpty()) {
                return@withContext ToonoStreamResult(
                    hlsUrl = verifiedRes.hlsUrl,
                    referer = verifiedRes.referer,
                    provider = verifiedRes.provider,
                    title = verifiedRes.title
                )
            }

            val queries = getSearchQueries(anilistId, animeTitle)
            if (queries.isEmpty()) return@withContext null

            // Detect target season and movie type from anime title
            val targetSeason = parseSeasonNumber(animeTitle)
            val isMovie = animeTitle.contains("movie", ignoreCase = true) || animeTitle.contains("film", ignoreCase = true)

            var postUrl = postCache["${anilistId}_${targetSeason}_post"]

            if (postUrl.isNullOrEmpty()) {
                for (q in queries) {
                    val searchUrl = "$BASE_URL/?s=${URLEncoder.encode(q, "UTF-8")}"
                    val searchReq = Request.Builder()
                        .url(searchUrl)
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("Referer", "$BASE_URL/")
                        .build()

                    val searchHtml = try {
                        client.newCall(searchReq).execute().use { resp ->
                            if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                        }
                    } catch (_: Exception) { "" }

                    if (searchHtml.isNotEmpty()) {
                        val articleRegex = Pattern.compile("""<article\b[^>]*>([\s\S]*?)</article>""", Pattern.CASE_INSENSITIVE)
                        val articleMatcher = articleRegex.matcher(searchHtml)

                        data class CandidatePost(val href: String, val title: String, val season: Int, val isMovie: Boolean)
                        val candidates = mutableListOf<CandidatePost>()

                        while (articleMatcher.find()) {
                            val art = articleMatcher.group(1) ?: continue

                            // 1. Try entry-title <h2><a href="...">Title</a></h2>
                            var href = ""
                            var title = ""
                            val h2Matcher = Pattern.compile("""<h2\b[^>]*class=["\'][^"\']*entry-title[^"\']*["\'][^>]*>[\s\S]*?<a\b[^>]*href=["\']([^"\']+)["\'][^>]*>([\s\S]*?)</a>""", Pattern.CASE_INSENSITIVE).matcher(art)
                            if (h2Matcher.find()) {
                                href = h2Matcher.group(1) ?: ""
                                title = unescapeHtml(h2Matcher.group(2)?.replace(Regex("""<[^>]+>"""), "") ?: "")
                            } else {
                                // 2. Fallback to thumbnail link
                                val linkMatcher = Pattern.compile("""<a\b[^>]*?href="(https?://[^"]+)"[^>]*?(?:title="([^"]+)")?[^>]*>""", Pattern.CASE_INSENSITIVE).matcher(art)
                                if (linkMatcher.find()) {
                                    href = linkMatcher.group(1) ?: ""
                                    title = unescapeHtml(linkMatcher.group(2) ?: "")
                                }
                            }

                            if (href.isNotEmpty() && (href.contains("rareanimes.") || href.contains("raretoons"))) {
                                if (isTitleMatch(title, queries) || isTitleMatch(href, queries)) {
                                    val postSeason = parseSeasonNumber("$title $href")
                                    val postMovie = title.contains("movie", ignoreCase = true) || href.contains("movie", ignoreCase = true)
                                    candidates.add(CandidatePost(href, title, postSeason, postMovie))
                                }
                            }
                        }

                        // Select post matching target season and movie type
                        val exactMatch = candidates.firstOrNull { it.isMovie == isMovie && it.season == targetSeason }
                            ?: candidates.firstOrNull { it.season == targetSeason }
                            ?: candidates.firstOrNull { it.isMovie == isMovie }
                            ?: candidates.firstOrNull()

                        if (exactMatch != null) {
                            postUrl = exactMatch.href
                            break
                        }
                    }
                }
            }

            if (postUrl.isNullOrEmpty()) return@withContext null
            postCache["${anilistId}_${targetSeason}_post"] = postUrl

            // 2. Fetch post HTML
            val postReq = Request.Builder()
                .url(postUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", "$BASE_URL/")
                .build()

            val postHtml = try {
                client.newCall(postReq).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                }
            } catch (_: Exception) { "" }

            if (postHtml.isEmpty()) return@withContext null

            // 3. Extract episode-specific block (or full content if movie / single episode)
            val epPattern = Pattern.compile(
                """(?:\b(?:Episode|Ep)\.?\s*0*${episode}\b)([\s\S]*?)(?=(?:\b(?:Episode|Ep)\.?\s*0*\d+\b|\bIndex\.Of\b|\bTHANKS FOR WATCHING\b|\bBatch\b|${'$'}))""",
                Pattern.CASE_INSENSITIVE
            )
            val epMatcher = epPattern.matcher(postHtml)
            val blockHtml = if (epMatcher.find()) {
                epMatcher.group(1) ?: ""
            } else if (isMovie || episode == 1 || postHtml.contains("movie", ignoreCase = true)) {
                // If it's a Movie or Single Episode post without "Episode 01" header, use main entry content
                val entryMatch = Pattern.compile("""<div\b[^>]*class=["\'][^"\']*entry-content[^"\']*["\'][^>]*>([\s\S]*?)</div>""", Pattern.CASE_INSENSITIVE).matcher(postHtml)
                if (entryMatch.find()) entryMatch.group(1) ?: postHtml else postHtml
            } else {
                return@withContext null
            }

            // 4. Extract codedew links specifically for Hindi dub
            val cdRegex = Pattern.compile("""<a\b[^>]*?href="(https?://codedew\.com/[^"]+)"[^>]*>([\s\S]*?)</a>""", Pattern.CASE_INSENSITIVE)
            val cdMatcher = cdRegex.matcher(blockHtml)
            val betaLinks = mutableListOf<String>()
            val mqLinks = mutableListOf<String>()

            while (cdMatcher.find()) {
                val href = cdMatcher.group(1) ?: continue
                val text = cdMatcher.group(2) ?: ""
                val cleanText = text.replace(Regex("""<[^>]+>"""), " ").trim().lowercase()

                // Filter out non-Hindi languages
                val startIdx = (cdMatcher.start() - 120).coerceAtLeast(0)
                val endIdx = (cdMatcher.end() + 120).coerceAtMost(blockHtml.length)
                val surrounding = blockHtml.substring(startIdx, endIdx).lowercase()
                if (surrounding.contains("tamil") || surrounding.contains("telugu") || surrounding.contains("malayalam") || surrounding.contains("kannada") || surrounding.contains("bengali")) {
                    continue
                }

                if (cleanText.contains("streambeta") || cleanText.contains("beta") || surrounding.contains("beta")) {
                    betaLinks.add(href)
                } else {
                    mqLinks.add(href)
                }
            }

            // Tier 1: Try StreamBeta direct worker stream
            for (bUrl in betaLinks) {
                val betaReq = Request.Builder()
                    .url(bUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", postUrl)
                    .build()

                val betaHtml = try {
                    client.newCall(betaReq).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                    }
                } catch (_: Exception) { "" }

                if (betaHtml.isNotEmpty()) {
                    val psRegex = Pattern.compile("""playerSources\s*=\s*(\[[\s\S]*?\]);""", Pattern.CASE_INSENSITIVE)
                    val psMatcher = psRegex.matcher(betaHtml)
                    if (psMatcher.find()) {
                        try {
                            val jsonArray = org.json.JSONArray(psMatcher.group(1))
                            for (i in 0 until jsonArray.length()) {
                                val obj = jsonArray.getJSONObject(i)
                                val streamUrl = obj.optString("stream_url", "").ifEmpty {
                                    obj.optString("url", "")
                                }
                                if (streamUrl.isNotEmpty() && (streamUrl.startsWith("http://") || streamUrl.startsWith("https://"))) {
                                    return@withContext ToonoStreamResult(
                                        hlsUrl = streamUrl,
                                        referer = "https://codedew.com/",
                                        provider = "RareAnime (Hindi)",
                                        title = "$animeTitle Ep $episode"
                                    )
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            // Tier 2: Try MultiQuality / Argon Embed
            for (mqUrl in mqLinks) {
                val cdReq = Request.Builder()
                    .url(mqUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", postUrl)
                    .build()

                val cdHtml = try {
                    client.newCall(cdReq).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                    }
                } catch (_: Exception) { "" }

                if (cdHtml.isEmpty()) continue

                val embedRegex = Pattern.compile("""<iframe\b[^>]*?\bsrc=["'](https?://[a-z0-9.-]+/embed/[A-Za-z0-9]+)["']""", Pattern.CASE_INSENSITIVE)
                val embedMatcher = embedRegex.matcher(cdHtml)
                val argonEmbedUrl = if (embedMatcher.find()) embedMatcher.group(1) else null ?: continue

                val arReq = Request.Builder()
                    .url(argonEmbedUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", "https://codedew.com/")
                    .build()

                val arHtml = try {
                    client.newCall(arReq).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                    }
                } catch (_: Exception) { "" }

                if (arHtml.isEmpty()) continue

                val matchJuicy = Pattern.compile("""_juicycodes\(([\s\S]*?)\);""", Pattern.CASE_INSENSITIVE).matcher(arHtml)
                if (!matchJuicy.find()) continue

                val insideJuicy = matchJuicy.group(1) ?: continue
                val quoteMatcher = Pattern.compile("""["']([^"']+)["']""").matcher(insideJuicy)
                val fullBlob = StringBuilder()
                while (quoteMatcher.find()) {
                    fullBlob.append(quoteMatcher.group(1) ?: "")
                }

                if (fullBlob.isEmpty()) continue

                val decodedJs = decodeJuicyCodes(fullBlob.toString())
                if (decodedJs.isEmpty()) continue

                val m3u8Matcher = Pattern.compile(""""file"\s*:\s*"(https?:[^"]+?\.m3u8[^"]*)"""", Pattern.CASE_INSENSITIVE).matcher(decodedJs)
                if (m3u8Matcher.find()) {
                    val m3u8Url = m3u8Matcher.group(1) ?: continue
                    return@withContext ToonoStreamResult(
                        hlsUrl = m3u8Url,
                        referer = argonEmbedUrl,
                        provider = "RareAnime (Hindi)",
                        title = "$animeTitle Ep $episode"
                    )
                }

                val mp4Matcher = Pattern.compile(""""file"\s*:\s*"(https?:[^"]+?\.mp4[^"]*)"""", Pattern.CASE_INSENSITIVE).matcher(decodedJs)
                if (mp4Matcher.find()) {
                    val mp4Url = mp4Matcher.group(1) ?: continue
                    return@withContext ToonoStreamResult(
                        hlsUrl = mp4Url,
                        referer = argonEmbedUrl,
                        provider = "RareAnime (Hindi)",
                        title = "$animeTitle Ep $episode"
                    )
                }
            }

            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

}
