package com.lagradost.cloudstream3.ui.animebox.extractors
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class AnimeAV1Embed(
    val server: String,
    val url: String
)

data class AnimeAV1SearchResult(
    val slug: String,
    val title: String,
    val malId: Int? = null
)

object AnimeAV1Extractor {

    private val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val BASE_URL = "https://animeav1.com"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val slugCache = ConcurrentHashMap<Int, String>()
    private val mediaDataCache = ConcurrentHashMap<String, Pair<Int?, List<Int>>>()

    /**
     * SvelteKit deval deserializer: Resolves graph of references from SvelteKit node data.
     */
    private fun deval(dataArray: JSONArray, index: Int, depth: Int = 0): Any? {
        if (index < 0 || index >= dataArray.length() || depth > 64) return null
        val item = dataArray.opt(index)
        if (item is JSONObject) {
            val result = mutableMapOf<String, Any?>()
            val keys = item.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val targetIndex = item.optInt(key, -1)
                if (targetIndex in 0 until dataArray.length()) {
                    result[key] = deval(dataArray, targetIndex, depth + 1)
                } else {
                    result[key] = item.opt(key)
                }
            }
            return result
        }
        if (item is JSONArray) {
            val result = mutableListOf<Any?>()
            for (i in 0 until item.length()) {
                val targetIndex = item.optInt(i, -1)
                if (targetIndex in 0 until dataArray.length()) {
                    result.add(deval(dataArray, targetIndex, depth + 1))
                } else {
                    result.add(item.opt(i))
                }
            }
            return result
        }
        return item
    }

    /**
     * Extracts the primary page data object from SvelteKit __data.json response.
     * In SvelteKit responses, the last node of type "data" contains the route data.
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseSvelteKitData(jsonStr: String): Map<String, Any?>? {
        return try {
            val root = JSONObject(jsonStr)
            val nodes = root.optJSONArray("nodes") ?: return null
            for (i in nodes.length() - 1 downTo 0) {
                val node = nodes.optJSONObject(i) ?: continue
                if (node.optString("type") == "data") {
                    val dataArr = node.optJSONArray("data") ?: continue
                    val unpacked = deval(dataArr, 0)
                    if (unpacked is Map<*, *>) {
                        return unpacked as Map<String, Any?>
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Searches AnimeAV1 for anime catalog entries matching the title.
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun search(title: String): List<AnimeAV1SearchResult> = withContext(Dispatchers.IO) {
        val cleanQuery = title
            .replace(Regex("""(?i)\b(season\s*\d+|2nd\s*season|3rd\s*season|4th\s*season|\d+(?:st|nd|rd|th)\s+season|part\s*\d+|s\d+|temporada\s*\d+|saison\s*\d+|staffel\s*\d+|cour\s*\d+)\b"""), "")
            .replace(Regex("[:!?,._-]+"), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (cleanQuery.isEmpty()) return@withContext emptyList()

        try {
            val url = "$BASE_URL/catalogo/__data.json?search=${URLEncoder.encode(cleanQuery, "UTF-8")}"
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "application/json")
                .addHeader("Referer", "$BASE_URL/catalogo")
                .build()

            val jsonStr = client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext emptyList()

            val data = parseSvelteKitData(jsonStr) ?: return@withContext emptyList()
            val rawResults = data["results"] as? List<*> ?: return@withContext emptyList()

            val results = mutableListOf<AnimeAV1SearchResult>()
            for (item in rawResults) {
                if (item is Map<*, *>) {
                    val slug = item["slug"]?.toString() ?: continue
                    val resTitle = item["title"]?.toString() ?: ""
                    val malId = (item["malId"] as? Number)?.toInt()
                    results.add(AnimeAV1SearchResult(slug, resTitle, malId))
                }
            }
            results
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Fetches media details from AnimeAV1 media/{slug}/__data.json.
     * Returns Pair(malId, episodeNumbersList).
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun fetchMediaData(slug: String): Pair<Int?, List<Int>>? = withContext(Dispatchers.IO) {
        mediaDataCache[slug]?.let { return@withContext it }
        try {
            val url = "$BASE_URL/media/$slug/__data.json"
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "application/json")
                .addHeader("Referer", "$BASE_URL/media/$slug")
                .build()

            val jsonStr = client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext null

            val data = parseSvelteKitData(jsonStr) ?: return@withContext null
            val media = data["media"] as? Map<*, *> ?: return@withContext null
            val malId = (media["malId"] as? Number)?.toInt()

            val epsList = mutableListOf<Int>()
            val rawEps = media["episodes"] as? List<*>
            if (rawEps != null) {
                for (epItem in rawEps) {
                    if (epItem is Map<*, *>) {
                        val num = (epItem["number"] as? Number)?.toInt()
                        if (num != null && num > 0) {
                            epsList.add(num)
                        }
                    }
                }
            }
            val res = Pair(malId, epsList)
            mediaDataCache[slug] = res
            res
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Normalizes title strings matching Anilili's P5.j algorithm:
     * lowercase, replaces punctuation/non-alphanumeric with spaces, collapses whitespace.
     */
    private fun normalize(str: String): String {
        return str.lowercase(java.util.Locale.ROOT)
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(Regex("""\s+"""))
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .trim()
    }

    /**
     * Resolves the MyAnimeList ID for the given AniList ID via AniZip or AniList Client.
     */
    private suspend fun resolveMalId(anilistId: Int): Int? {
        if (anilistId <= 0) return null
        try {
            val aniZip = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getAniZipJson(anilistId)
            val malId = aniZip?.optJSONObject("mappings")?.optInt("mal_id", -1)?.takeIf { it > 0 }
            if (malId != null) return malId

            val details = com.lagradost.cloudstream3.ui.animebox.api.AniListClient.getAnimeDetails(anilistId)
            if (!details.isNullOrEmpty()) {
                val media = JSONObject(details).optJSONObject("data")?.optJSONObject("Media")
                val alMalId = media?.optInt("idMal", -1)?.takeIf { it > 0 }
                if (alMalId != null) return alMalId
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Resolves the best slug for the given AniList ID and title.
     * Aligned with Anilili's AnimeAV1 provider logic (F4/D1.java):
     * 1. Query AnimeAV1 catalog with normalized title variants.
     * 2. Candidate MAL ID matching (media/{slug}/__data.json malId == idMal).
     * 3. Candidate normalized title matching (P5.j style).
     * 4. Fuzzy fallback match.
     */
    private suspend fun resolveSlug(anilistId: Int, animeTitle: String?): String? {
        slugCache[anilistId]?.let { return it }
        val targetMalId = resolveMalId(anilistId)
        val titles = MultiLanguageAudioExtractor.resolveTitleVariants(anilistId, animeTitle)
        val searchTitles = titles.distinct().take(3)

        // Collect search candidates across title variants
        val candidates = mutableListOf<AnimeAV1SearchResult>()
        val seenSlugs = mutableSetOf<String>()
        for (t in searchTitles) {
            val list = search(t)
            for (res in list) {
                if (seenSlugs.add(res.slug)) {
                    candidates.add(res)
                }
            }
        }
        if (candidates.isEmpty()) return null

        // 1. Anilili MAL ID exact match
        if (targetMalId != null) {
            for (cand in candidates.take(5)) {
                val mediaData = fetchMediaData(cand.slug)
                val candMalId = cand.malId ?: mediaData?.first
                if (candMalId != null && candMalId == targetMalId) {
                    slugCache[anilistId] = cand.slug
                    return cand.slug
                }
            }
        }

        // 2. Anilili normalized title match (P5.j style)
        val normalizedTitles = titles.map { normalize(it) }.filter { it.isNotEmpty() }.toSet()
        for (cand in candidates) {
            val candNorm = normalize(cand.title)
            if (candNorm in normalizedTitles) {
                val mediaData = fetchMediaData(cand.slug)
                if (mediaData != null) {
                    slugCache[anilistId] = cand.slug
                    return cand.slug
                }
            }
        }

        // 3. Fallback: Substring or high similarity
        for (cand in candidates) {
            val candNorm = normalize(cand.title)
            if (normalizedTitles.any { candNorm.contains(it) || it.contains(candNorm) }) {
                slugCache[anilistId] = cand.slug
                return cand.slug
            }
        }

        // 4. Default to first candidate
        val firstSlug = candidates.first().slug
        slugCache[anilistId] = firstSlug
        return firstSlug
    }

    /**
     * Fetches episode embeds for a given slug and episode number.
     * Returns Pair(DUB_embeds, SUB_embeds).
     */
    @Suppress("UNCHECKED_CAST")
    private suspend fun getEpisodeEmbeds(
        slug: String,
        episodeNum: Int
    ): Pair<List<AnimeAV1Embed>, List<AnimeAV1Embed>> = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE_URL/media/$slug/$episodeNum/__data.json"
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "application/json")
                .addHeader("Referer", "$BASE_URL/media/$slug/$episodeNum")
                .build()

            val jsonStr = client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return@withContext Pair(emptyList(), emptyList())

            val data = parseSvelteKitData(jsonStr) ?: return@withContext Pair(emptyList(), emptyList())
            val rawEmbeds = data["embeds"] as? Map<*, *> ?: return@withContext Pair(emptyList(), emptyList())

            fun parseList(key: String): List<AnimeAV1Embed> {
                val list = rawEmbeds[key] as? List<*> ?: return emptyList()
                val embeds = mutableListOf<AnimeAV1Embed>()
                for (item in list) {
                    if (item is Map<*, *>) {
                        val server = item["server"]?.toString() ?: ""
                        val embedUrl = item["url"]?.toString() ?: ""
                        if (embedUrl.isNotEmpty()) {
                            embeds.add(AnimeAV1Embed(server, embedUrl))
                        }
                    }
                }
                return embeds
            }

            Pair(parseList("DUB"), parseList("SUB"))
        } catch (_: Exception) {
            Pair(emptyList(), emptyList())
        }
    }

    /**
     * Resolves a video stream (HLS or MP4) from an AnimeAV1 embed item.
     */
    private suspend fun extractStreamFromEmbed(embed: AnimeAV1Embed): Pair<String, String>? {
        val url = embed.url
        val lower = url.lowercase()

        // 1. Byse resolution (High quality 1080p HLS)
        if (lower.contains("byse") || lower.contains("gn1r5n") || lower.contains("mfw") || ByseExtractor.canExtract(url)) {
            val byseRes = ByseExtractor.extract(url, BASE_URL)
            if (byseRes.isNotEmpty()) {
                return Pair(byseRes.first(), "AnimeAV1 HLS")
            }
        }

        // 2. VOE resolution
        if (lower.contains("voe.sx") || lower.contains("voe") || lower.contains("shanelocaldog")) {
            val voeRes = MultiLanguageAudioExtractor.resolveVoeStream(url)
            if (voeRes != null && voeRes.first.isNotEmpty()) {
                return Pair(voeRes.first, "AnimeAV1 HLS")
            }
        }

        // 3. YourUpload -> direct MP4
        if (lower.contains("yourupload")) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", BASE_URL)
                    .build()
                val html = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
                if (html != null) {
                    val m = Pattern.compile("""og:video"\s*content="([^"]+)"""").matcher(html)
                    val mAlt = if (!m.find()) Pattern.compile("""property="og:video"\s*content="([^"]+)"""").matcher(html) else null
                    val videoUrl = if (m.find()) m.group(1) else if (mAlt != null && mAlt.find()) mAlt.group(1) else null
                    if (!videoUrl.isNullOrEmpty() && !videoUrl.contains("novideo")) {
                        return Pair(videoUrl, "AnimeAV1 MP4")
                    }
                }
            } catch (_: Exception) {}
        }

        // 4. Mp4Upload -> direct MP4
        if (lower.contains("mp4upload")) {
            try {
                val req = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", BASE_URL)
                    .build()
                val html = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
                if (html != null) {
                    val m = Pattern.compile("""(?:file|src)\s*[:=]\s*["'](https?://[^"']+\.mp4[^"']*)["']""").matcher(html)
                    if (m.find()) {
                        return Pair(m.group(1)!!, "AnimeAV1 MP4")
                    }
                }
            } catch (_: Exception) {}
        }

        // 5. Direct .m3u8 or .mp4
        if (lower.contains(".m3u8")) {
            return Pair(url, "AnimeAV1 HLS")
        }
        if (lower.contains(".mp4")) {
            return Pair(url, "AnimeAV1 MP4")
        }

        return null
    }

    /**
     * Primary extractor for Spanish Latino:
     * Plays Spanish Latino DUB first if available; otherwise plays Spanish SUB under the same server name ("AnimeAV1").
     */
    suspend fun extractLatino(
        anilistId: Int,
        episodeNum: Int,
        animeTitle: String?
    ): MultiLanguageStreamResult? = withContext(Dispatchers.IO) {
        val slug = resolveSlug(anilistId, animeTitle) ?: return@withContext null
        val (dubEmbeds, subEmbeds) = getEpisodeEmbeds(slug, episodeNum)

        fun sortEmbeds(embeds: List<AnimeAV1Embed>): List<AnimeAV1Embed> {
            return embeds.sortedBy { e ->
                val s = e.server.lowercase()
                val u = e.url.lowercase()
                when {
                    s.contains("byse") || u.contains("byse") -> 0
                    s.contains("voe") || u.contains("voe") -> 1
                    s.contains("yourupload") || u.contains("yourupload") -> 2
                    s.contains("mp4upload") || u.contains("mp4upload") -> 3
                    u.contains(".m3u8") -> 4
                    else -> 5
                }
            }
        }

        // 1. Try Spanish Latino DUB first
        val sortedDub = sortEmbeds(dubEmbeds)
        for (embed in sortedDub) {
            val res = extractStreamFromEmbed(embed)
            if (res != null && res.first.isNotEmpty()) {
                val backup = sortedDub.filter { it != embed }.firstNotNullOfOrNull { extractStreamFromEmbed(it) }
                return@withContext MultiLanguageStreamResult(
                    hlsUrl = res.first,
                    referer = BASE_URL,
                    provider = "AnimeAV1",
                    language = "latino",
                    dubName = "Spanish (Latino)",
                    backupHlsUrl = backup?.first ?: "",
                    backupProvider = if (backup != null) "AnimeAV1" else ""
                )
            }
        }

        // 2. If no DUB available, fallback to Spanish SUB under the same server name ("AnimeAV1")
        val sortedSub = sortEmbeds(subEmbeds)
        for (embed in sortedSub) {
            val res = extractStreamFromEmbed(embed)
            if (res != null && res.first.isNotEmpty()) {
                val backup = sortedSub.filter { it != embed }.firstNotNullOfOrNull { extractStreamFromEmbed(it) }
                return@withContext MultiLanguageStreamResult(
                    hlsUrl = res.first,
                    referer = BASE_URL,
                    provider = "AnimeAV1",
                    language = "latino",
                    dubName = "Spanish (Latino)",
                    backupHlsUrl = backup?.first ?: "",
                    backupProvider = if (backup != null) "AnimeAV1" else ""
                )
            }
        }

        null
    }

    /**
     * Dedicated extractor for Spanish Sub:
     * Plays Spanish SUB from AnimeAV1.
     */
    suspend fun extractSub(
        anilistId: Int,
        episodeNum: Int,
        animeTitle: String?
    ): MultiLanguageStreamResult? = withContext(Dispatchers.IO) {
        val slug = resolveSlug(anilistId, animeTitle) ?: return@withContext null
        val (_, subEmbeds) = getEpisodeEmbeds(slug, episodeNum)

        val sortedSub = subEmbeds.sortedBy { e ->
            val s = e.server.lowercase()
            val u = e.url.lowercase()
            when {
                s.contains("byse") || u.contains("byse") -> 0
                s.contains("voe") || u.contains("voe") -> 1
                s.contains("yourupload") || u.contains("yourupload") -> 2
                s.contains("mp4upload") || u.contains("mp4upload") -> 3
                u.contains(".m3u8") -> 4
                else -> 5
            }
        }

        for (embed in sortedSub) {
            val res = extractStreamFromEmbed(embed)
            if (res != null && res.first.isNotEmpty()) {
                val backup = sortedSub.filter { it != embed }.firstNotNullOfOrNull { extractStreamFromEmbed(it) }
                return@withContext MultiLanguageStreamResult(
                    hlsUrl = res.first,
                    referer = BASE_URL,
                    provider = "AnimeAV1",
                    language = "sub",
                    dubName = "Spanish Sub",
                    backupHlsUrl = backup?.first ?: "",
                    backupProvider = if (backup != null) "AnimeAV1" else ""
                )
            }
        }

        null
    }
}

