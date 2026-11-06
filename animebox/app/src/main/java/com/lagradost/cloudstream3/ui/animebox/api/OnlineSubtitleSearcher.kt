package com.lagradost.cloudstream3.ui.animebox.api
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream

data class OnlineSubtitleItem(
    val title: String,
    val langName: String,
    val langCode: String,
    val source: String,
    val downloadUrl: String? = null,
    val directContent: String? = null
)

object OnlineSubtitleSearcher {
    private const val TAG = "OnlineSubSearcher"
    private val SUPABASE_URL get() = AnimeBoxConfig.SUPABASE_URL
    private val SUPABASE_ANON_KEY get() = AnimeBoxConfig.SUPABASE_ANON_KEY
    private const val USER_AGENT = "Cloudstream3 v0.2"

    private val client: OkHttpClient by lazy {
        AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor { chain ->
                var req = chain.request()
                var resp = chain.proceed(req)
                var redirectCount = 0
                // Handle OpenSubtitles broken relative redirects (e.g. https://_/) and relative paths
                while (resp.isRedirect && redirectCount < 5) {
                    val loc = resp.header("Location")
                    if (loc.isNullOrBlank()) break
                    val fixedLoc = when {
                        loc.startsWith("https://_/") -> loc.replace("https://_/", "https://rest.opensubtitles.org/")
                        loc.startsWith("http://_/") -> loc.replace("http://_/", "https://rest.opensubtitles.org/")
                        loc.startsWith("/") -> {
                            val host = req.url.scheme + "://" + req.url.host
                            "$host$loc"
                        }
                        else -> loc
                    }
                    resp.close()
                    req = req.newBuilder().url(fixedLoc).build()
                    resp = chain.proceed(req)
                    redirectCount++
                }
                resp
            }
            .build()
    }

    /**
     * Clean query string by removing bracketed tags like (Dub), [Dual Audio], (Hindi), etc.,
     * punctuation and special characters that cause online subtitle endpoints to fail.
     */
    fun cleanSearchQuery(raw: String): String {
        var q = raw
        // 1. Strip tags in parens / brackets like (Dub), (Sub), [Dual Audio], (Hindi), [1080p], etc.
        q = q.replace(Regex("""(?i)\s*[\(\[]\s*(?:dub|sub|hindi|dual\s*audio|multi\s*audio|uncensored|censored|raw|batch|complete|1080p|720p|4k|eng(?:lish)?)[^\)\]]*[\)\]]"""), " ")
        // 2. Strip format tags
        q = q.replace(Regex("""(?i)\s*[\(\[]\s*(?:tv|movie|ona|ova|special|re-edit)[^\)\]]*[\)\]]"""), " ")
        // 3. Remove "Episode XX" / "Ep XX" if present in the raw query
        q = q.replace(Regex("""(?i)\b(?:episode|ep\.?)\s*\d+.*"""), " ")
        // 4. Remove any remaining parens and brackets
        q = q.replace(Regex("""[\[\]\(\)\{\}]"""), " ")
        // 5. Replace special characters that break URL paths or API filters with spaces
        q = q.replace(Regex("""[:;!?_\\/*~#$%=+\-]"""), " ")
        // 6. Condense spaces
        return q.replace(Regex("""\s+"""), " ").trim()
    }

    fun cleanBaseQuery(raw: String): String {
        val q = cleanSearchQuery(raw)
        // Strip season / part / cour / arc suffixes to get the root anime title
        val base = q.replace(Regex("""(?i)\b(?:the\s+)?final\s+season.*|\bseason\s*\d+.*|\bpart\s*\d+.*|\bcour\s*\d+.*|\barc.*"""), " ").trim()
        return if (base.length >= 3) base else q
    }

    /**
     * Search multiple internet subtitle sources (Supabase Community, OpenSubtitles, SubDL, SubSource, Jimaku)
     */
    suspend fun searchOnlineSubtitles(
        query: String,
        episodeNum: Int = 0,
        isMovie: Boolean = false,
        anilistId: Int = 0
    ): List<OnlineSubtitleItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<OnlineSubtitleItem>()
        val cleanFull = cleanSearchQuery(query)
        val cleanBase = cleanBaseQuery(query)
        if (cleanFull.isEmpty()) return@withContext results

        // 1. Search Supabase Community Public Subtitles first
        try {
            val supaUrl = if (anilistId > 0 && episodeNum > 0) {
                "$SUPABASE_URL/rest/v1/public_subtitles?anilist_id=eq.$anilistId&episode_num=eq.$episodeNum&select=*&order=created_at.desc"
            } else {
                val encoded = URLEncoder.encode(cleanBase.ifBlank { cleanFull }, "UTF-8")
                "$SUPABASE_URL/rest/v1/public_subtitles?anime_title=ilike.*$encoded*&select=*&limit=20&order=created_at.desc"
            }

            val supaReq = Request.Builder()
                .url(supaUrl)
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .get()
                .build()

            client.newCall(supaReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    if (body.startsWith("[")) {
                        val arr = JSONArray(body)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val vtt = obj.optString("vtt_content", "")
                            val ep = obj.optInt("episode_num", 0)
                            val title = obj.optString("anime_title", cleanFull)
                            val lang = obj.optString("lang_name", "English")
                            val langCode = obj.optString("lang_code", "en")
                            val uploader = obj.optString("uploader_name", "Community")
                            if (vtt.isNotBlank()) {
                                results.add(
                                    OnlineSubtitleItem(
                                        title = "$title - Ep $ep [By $uploader]",
                                        langName = lang,
                                        langCode = langCode,
                                        source = "Community",
                                        directContent = vtt
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Supabase community sub search error", e)
        }

        // 2. Search OpenSubtitles REST API
        try {
            val urlsToTry = mutableListOf<String>()

            // Episode-specific URL
            if (!isMovie && episodeNum > 0 && cleanBase.isNotBlank()) {
                val plusBase = cleanBase.lowercase().replace(Regex("""\s+"""), "+")
                urlsToTry.add("https://rest.opensubtitles.org/search/episode-$episodeNum/query-$plusBase")
            }

            // General query URLs
            val plusFull = cleanFull.lowercase().replace(Regex("""\s+"""), "+")
            urlsToTry.add("https://rest.opensubtitles.org/search/query-$plusFull")
            if (cleanBase != cleanFull && cleanBase.isNotBlank()) {
                val plusBase = cleanBase.lowercase().replace(Regex("""\s+"""), "+")
                urlsToTry.add("https://rest.opensubtitles.org/search/query-$plusBase")
            }

            for (url in urlsToTry) {
                try {
                    val req = Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "application/json")
                        .get()
                        .build()

                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string() ?: ""
                            if (body.startsWith("[")) {
                                val arr = JSONArray(body)
                                for (i in 0 until minOf(arr.length(), 40)) {
                                    val obj = arr.getJSONObject(i)
                                    val fName = obj.optString("SubFileName", "").ifBlank { obj.optString("MovieName", "Subtitle") }
                                    val lang = obj.optString("LanguageName", "English")
                                    val langCode = obj.optString("ISO639", "en")
                                    val dl = obj.optString("SubDownloadLink", "").ifBlank { obj.optString("ZipDownloadLink", "") }

                                    if (dl.isNotEmpty() && results.none { it.title.equals(fName, ignoreCase = true) }) {
                                        results.add(
                                            OnlineSubtitleItem(
                                                title = fName,
                                                langName = lang,
                                                langCode = langCode,
                                                source = "OpenSubtitles",
                                                downloadUrl = dl
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "OpenSubtitles attempt failed for $url", e)
                }
                if (results.count { it.source == "OpenSubtitles" } >= 15) break
            }
        } catch (e: Exception) {
            Log.w(TAG, "OpenSubtitles search error", e)
        }

        // 3. Search SubDL API
        try {
            val subdlKey = try {
                com.lagradost.cloudstream3.syncproviders.AccountManager.accounts("subdl").firstOrNull()?.token?.accessToken
            } catch (_: Throwable) { null }

            val encodedTitle = URLEncoder.encode(cleanBase.ifBlank { cleanFull }, "UTF-8")
            val epParam = if (!isMovie && episodeNum > 0) "&type=episode&episode_number=$episodeNum" else if (isMovie) "&type=movie" else ""
            val authParam = if (!subdlKey.isNullOrBlank()) "&api_key=$subdlKey" else ""
            val subdlUrl = "https://api.subdl.com/api/v1/subtitles?film_name=$encodedTitle$epParam$authParam"

            val subdlReq = Request.Builder()
                .url(subdlUrl)
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(subdlReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    if (body.contains("\"subtitles\"")) {
                        val json = JSONObject(body)
                        val subs = json.optJSONArray("subtitles")
                        if (subs != null) {
                            for (i in 0 until minOf(subs.length(), 20)) {
                                val sObj = subs.getJSONObject(i)
                                val release = sObj.optString("release_name", "").ifBlank { sObj.optString("name", "SubDL Subtitle") }
                                val lang = sObj.optString("lang", "en")
                                val subUrl = sObj.optString("url", "")
                                if (subUrl.isNotEmpty() && results.none { it.title.equals(release, ignoreCase = true) }) {
                                    val fullUrl = if (subUrl.startsWith("http")) subUrl else "https://dl.subdl.com$subUrl"
                                    results.add(
                                        OnlineSubtitleItem(
                                            title = release,
                                            langName = lang.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                                            langCode = lang.take(2).lowercase(),
                                            source = "SubDL",
                                            downloadUrl = fullUrl
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SubDL search error", e)
        }

        // 4. Search SubSource API
        try {
            val searchTerm = cleanBase.ifBlank { cleanFull }
            val postBody = JSONObject().apply {
                put("query", searchTerm)
                put("signal", "{}")
                put("limit", 10)
            }.toString().toRequestBody("application/json".toMediaType())

            val subSourceReq = Request.Builder()
                .url("https://api.subsource.net/v1/movie/search")
                .header("User-Agent", "Mozilla/5.0")
                .post(postBody)
                .build()

            client.newCall(subSourceReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val root = JSONObject(body)
                    val resArr = root.optJSONArray("results")
                    if (resArr != null && resArr.length() > 0) {
                        val firstLink = resArr.getJSONObject(0).optString("link", "")
                        if (firstLink.isNotEmpty()) {
                            val subUrl = "https://api.subsource.net/v1${firstLink.replace("series", "subtitles")}"
                            val subReq = Request.Builder()
                                .url(subUrl)
                                .header("User-Agent", "Mozilla/5.0")
                                .get()
                                .build()
                            client.newCall(subReq).execute().use { subResp ->
                                if (subResp.isSuccessful) {
                                    val subBody = subResp.body?.string() ?: ""
                                    val itemRoot = JSONObject(subBody)
                                    val subs = itemRoot.optJSONArray("subtitles")
                                    if (subs != null) {
                                        for (j in 0 until minOf(subs.length(), 20)) {
                                            val sObj = subs.getJSONObject(j)
                                            val release = sObj.optString("release_info", "").ifBlank {
                                                sObj.optString("releaseName", "").ifBlank { sObj.optString("name", "SubSource Subtitle") }
                                            }
                                            val lang = sObj.optString("language", "English")
                                            val link = sObj.optString("link", "")
                                            if (link.isNotEmpty() && results.none { it.title.equals(release, ignoreCase = true) }) {
                                                results.add(
                                                    OnlineSubtitleItem(
                                                        title = release,
                                                        langName = lang.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                                                        langCode = lang.take(2).lowercase(),
                                                        source = "SubSource",
                                                        downloadUrl = "subsource:$link"
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "SubSource search error", e)
        }

        // 5. Search Jimaku (Japanese/Anime subtitle database)
        try {
            val encoded = URLEncoder.encode(cleanBase.ifBlank { cleanFull }, "UTF-8")
            val jReq = Request.Builder()
                .url("https://jimaku.cc/api/entries/search?name=$encoded")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(jReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    if (body.startsWith("[")) {
                        val arr = JSONArray(body)
                        for (i in 0 until minOf(arr.length(), 6)) {
                            val obj = arr.getJSONObject(i)
                            val title = obj.optString("japanese_name", "").ifBlank { obj.optString("english_name", cleanFull) }
                            val id = obj.optInt("id", 0)
                            if (id > 0) {
                                results.add(
                                    OnlineSubtitleItem(
                                        title = "$title (Episode $episodeNum)",
                                        langName = "Japanese / English",
                                        langCode = "ja",
                                        source = "Jimaku",
                                        downloadUrl = "https://jimaku.cc/api/entries/$id/files"
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Jimaku search error", e)
        }

        results
    }

    /**
     * Download subtitle text from url or direct content, resolve tokens / archives (ZIP, GZIP),
     * and normalize to WebVTT format.
     */
    suspend fun downloadAndNormalizeToVtt(downloadUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            val resolvedUrl = if (downloadUrl.startsWith("subsource:")) {
                val subLink = downloadUrl.removePrefix("subsource:")
                val tokenUrl = "https://api.subsource.net/v1/subtitle/$subLink"
                val tokenReq = Request.Builder().url(tokenUrl).header("User-Agent", "Mozilla/5.0").get().build()
                var downloadToken = ""
                client.newCall(tokenReq).execute().use { tResp ->
                    if (tResp.isSuccessful) {
                        val tBody = tResp.body?.string() ?: ""
                        val tJson = JSONObject(tBody)
                        val subObj = tJson.optJSONObject("subtitle")
                        downloadToken = subObj?.optString("downloadToken", "")?.ifBlank { subObj.optString("download_token", "") }
                            ?: tJson.optString("downloadToken", "")
                    }
                }
                if (downloadToken.isNotBlank()) {
                    "https://api.subsource.net/v1/subtitle/download/$downloadToken"
                } else null
            } else {
                downloadUrl
            }

            if (resolvedUrl.isNullOrBlank()) return@withContext null

            val req = Request.Builder()
                .url(resolvedUrl)
                .header("User-Agent", USER_AGENT)
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val bytes = resp.body?.bytes() ?: return@withContext null

                // 1. Handle GZIP compression (OpenSubtitles .gz)
                val rawText = if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
                    try {
                        GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use { it.readText() }
                    } catch (_: Exception) {
                        String(bytes, Charsets.UTF_8)
                    }
                }
                // 2. Handle PKZIP archives (SubSource, SubDL, OpenSubtitles zip)
                else if (bytes.size >= 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte()) {
                    try {
                        val zis = ZipInputStream(ByteArrayInputStream(bytes))
                        var entry = zis.nextEntry
                        var extractedText: String? = null
                        while (entry != null) {
                            val name = entry.name.lowercase()
                            if (name.endsWith(".srt") || name.endsWith(".vtt") || name.endsWith(".ass") || name.endsWith(".ssa")) {
                                extractedText = zis.bufferedReader(Charsets.UTF_8).readText()
                                break
                            }
                            entry = zis.nextEntry
                        }
                        extractedText ?: String(bytes, Charsets.UTF_8)
                    } catch (_: Exception) {
                        String(bytes, Charsets.UTF_8)
                    }
                } else {
                    String(bytes, Charsets.UTF_8)
                }

                convertSrtOrTextToVtt(rawText)
            }
        } catch (e: Exception) {
            Log.e(TAG, "downloadAndNormalizeToVtt error for $downloadUrl", e)
            null
        }
    }

    /**
     * Converts raw SRT / SubRip / ASS / SSA format text into clean WebVTT format.
     */
    fun convertSrtOrTextToVtt(raw: String): String {
        val trimmed = raw.trimStart()
        if (trimmed.startsWith("WEBVTT")) return raw

        // 1. Convert ASS / SSA to standard WebVTT cues
        if (raw.contains("[Events]") || raw.contains("Dialogue:")) {
            val sb = StringBuilder("WEBVTT\n\n")
            val lines = raw.lines()
            for (line in lines) {
                val l = line.trim()
                if (l.startsWith("Dialogue:", ignoreCase = true)) {
                    val parts = l.substringAfter(":").split(",", limit = 10)
                    if (parts.size >= 10) {
                        val start = formatAssTimeToVtt(parts[1].trim())
                        val end = formatAssTimeToVtt(parts[2].trim())
                        val text = parts[9].trim()
                            .replace(Regex("""\{[^}]*\}"""), "")
                            .replace("\\N", "\n")
                            .replace("\\n", "\n")
                            .trim()
                        if (text.isNotBlank()) {
                            sb.append(start).append(" --> ").append(end).append("\n")
                            sb.append(text).append("\n\n")
                        }
                    }
                }
            }
            val assResult = sb.toString()
            if (assResult.length > 10) return assResult
        }

        // 2. Convert standard SRT to WebVTT
        val sb = StringBuilder("WEBVTT\n\n")
        val lines = raw.lines()
        val srtTimePattern = Pattern.compile("""(\d{2}:\d{2}:\d{2}),(\d{3})\s*-->\s*(\d{2}:\d{2}:\d{2}),(\d{3})""")

        for (line in lines) {
            val matcher = srtTimePattern.matcher(line)
            if (matcher.find()) {
                val start = "${matcher.group(1)}.${matcher.group(2)}"
                val end = "${matcher.group(3)}.${matcher.group(4)}"
                sb.append(start).append(" --> ").append(end).append("\n")
            } else {
                sb.append(line).append("\n")
            }
        }

        return sb.toString()
    }

    private fun formatAssTimeToVtt(timeStr: String): String {
        val parts = timeStr.trim().split(":")
        if (parts.size != 3) return "00:00:00.000"
        val h = parts[0].padStart(2, '0')
        val m = parts[1].padStart(2, '0')
        val secParts = parts[2].split(".")
        val s = secParts[0].padStart(2, '0')
        val ms = if (secParts.size > 1) secParts[1].padEnd(3, '0').take(3) else "000"
        return "$h:$m:$s.$ms"
    }
}
