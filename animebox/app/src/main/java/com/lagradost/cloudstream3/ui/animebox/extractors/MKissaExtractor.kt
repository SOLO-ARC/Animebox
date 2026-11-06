package com.lagradost.cloudstream3.ui.animebox.extractors
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class MKissaResult(
    val hlsUrl: String,
    val referer: String = "https://mkissa.to/",
    val provider: String = "MKissa",
    val introStart: Long = 0L,
    val introEnd: Long = 0L,
    val outroStart: Long = 0L,
    val outroEnd: Long = 0L
)

object MKissaExtractor {

    private val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val REFERER = "https://mkissa.to/"

    private val API_CANDIDATE_HOSTS = listOf(
        "http://10.0.2.2:4000",
        "http://localhost:4000",
        "http://127.0.0.1:4000"
    )

    private val HEX_TABLE = mapOf(
        "79" to "A", "7a" to "B", "7b" to "C", "7c" to "D", "7d" to "E", "7e" to "F", "7f" to "G",
        "70" to "H", "71" to "I", "72" to "J", "73" to "K", "74" to "L", "75" to "M", "76" to "N", "77" to "O",
        "68" to "P", "69" to "Q", "6a" to "R", "6b" to "S", "6c" to "T", "6d" to "U", "6e" to "V", "6f" to "W",
        "60" to "X", "61" to "Y", "62" to "Z", "59" to "a", "5a" to "b", "5b" to "c", "5c" to "d", "5d" to "e",
        "5e" to "f", "5f" to "g", "50" to "h", "51" to "i", "52" to "j", "53" to "k", "54" to "l", "55" to "m",
        "56" to "n", "57" to "o", "48" to "p", "49" to "q", "4a" to "r", "4b" to "s", "4c" to "t", "4d" to "u",
        "4e" to "v", "4f" to "w", "40" to "x", "41" to "y", "42" to "z", "08" to "0", "09" to "1", "0a" to "2",
        "0b" to "3", "0c" to "4", "0d" to "5", "0e" to "6", "0f" to "7", "00" to "8", "01" to "9", "15" to "-",
        "16" to ".", "67" to "_", "46" to "~", "02" to ":", "17" to "/", "07" to "?", "1b" to "#", "63" to "[",
        "65" to "]", "78" to "@", "19" to "!", "1c" to "$", "1e" to "&", "10" to "(", "11" to ")", "12" to "*",
        "13" to "+", "14" to ",", "03" to ";", "05" to "=", "1d" to "%"
    )

    fun decodeHexUrl(hex: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < hex.length) {
            val pair = if (i + 2 <= hex.length) hex.substring(i, i + 2).lowercase() else hex.substring(i)
            out.append(HEX_TABLE[pair] ?: pair)
            i += 2
        }
        return out.toString()
    }

    /**
     * Extracts stream from MKissa provider.
     * @param anilistId AniList anime ID
     * @param episodeNum Episode number
     * @param serverFilter Optional server name filter: "Default", "Ok", "Sak", "Mp4Upload", etc.
     */
    suspend fun extract(
        anilistId: Int,
        episodeNum: Int,
        serverFilter: String? = null
    ): MKissaResult? = withContext(Dispatchers.IO) {
        if (anilistId <= 0 || episodeNum <= 0) return@withContext null

        // 1. Try querying Anivexa API watch endpoint
        for (host in API_CANDIDATE_HOSTS) {
            try {
                val watchUrl = "$host/watch/mkissa/$anilistId/sub/mkissa-$episodeNum"
                val req = Request.Builder()
                    .url(watchUrl)
                    .addHeader("User-Agent", UA)
                    .addHeader("Accept", "application/json")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        if (body.isNotEmpty() && !body.contains("\"error\"")) {
                            val json = JSONObject(body)
                            val sourcesArr = json.optJSONArray("sources")
                            val intro = json.optJSONObject("intro")
                            val outro = json.optJSONObject("outro")
                            val iStart = intro?.optLong("start", 0L) ?: 0L
                            val iEnd = intro?.optLong("end", 0L) ?: 0L
                            val oStart = outro?.optLong("start", 0L) ?: 0L
                            val oEnd = outro?.optLong("end", 0L) ?: 0L

                            if (sourcesArr != null && sourcesArr.length() > 0) {
                                val result = processSources(sourcesArr, serverFilter, iStart, iEnd, oStart, oEnd)
                                if (result != null) return@withContext result
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        // 2. Graceful fallback if backend API is offline or returned no sources:
        // Query AniChan or AniNeko so user doesn't encounter a playback error
        try {
            val aniChanRes = AniChanExtractor.extractHardSub(anilistId, episodeNum)
            if (aniChanRes != null && aniChanRes.hlsUrl.isNotEmpty()) {
                val pName = if (serverFilter != null) "MKissa ($serverFilter)" else "MKissa"
                return@withContext MKissaResult(
                    hlsUrl = aniChanRes.hlsUrl,
                    referer = aniChanRes.referer,
                    provider = pName,
                    introStart = aniChanRes.introStart,
                    introEnd = aniChanRes.introEnd,
                    outroStart = aniChanRes.outroStart,
                    outroEnd = aniChanRes.outroEnd
                )
            }
        } catch (_: Exception) {}

        try {
            val nekoRes = HardSubExtractor.extractAniNeko(anilistId, episodeNum)
            if (nekoRes != null && nekoRes.hlsUrl.isNotEmpty()) {
                val pName = if (serverFilter != null) "MKissa ($serverFilter)" else "MKissa"
                return@withContext MKissaResult(
                    hlsUrl = nekoRes.hlsUrl,
                    referer = nekoRes.referer,
                    provider = pName
                )
            }
        } catch (_: Exception) {}

        null
    }

    private suspend fun processSources(
        sourcesArr: JSONArray,
        serverFilter: String?,
        introStart: Long,
        introEnd: Long,
        outroStart: Long,
        outroEnd: Long
    ): MKissaResult? {
        val targetFilter = serverFilter?.lowercase()?.trim()

        // Filter or prioritize sources matching requested serverFilter
        val matchedSources = mutableListOf<JSONObject>()
        val otherSources = mutableListOf<JSONObject>()

        for (i in 0 until sourcesArr.length()) {
            val s = sourcesArr.optJSONObject(i) ?: continue
            val name = s.optString("name", "").lowercase()
            if (targetFilter != null) {
                if (targetFilter == "ok" && (name.contains("ok") || s.optString("url", "").contains("ok.ru"))) {
                    matchedSources.add(s)
                } else if (targetFilter == "sak" && name.contains("sak")) {
                    matchedSources.add(s)
                } else if ((targetFilter == "mp4" || targetFilter == "mp4upload") && (name.contains("mp4") || s.optString("url", "").contains("mp4upload"))) {
                    matchedSources.add(s)
                } else if (targetFilter == "default" && (name.contains("default") || name.contains("luf") || name.contains("s-mp4") || s.optString("url", "").contains("allanime"))) {
                    matchedSources.add(s)
                } else if (name.contains(targetFilter)) {
                    matchedSources.add(s)
                } else {
                    otherSources.add(s)
                }
            } else {
                matchedSources.add(s)
            }
        }

        val candidates = if (targetFilter != null && matchedSources.isNotEmpty()) matchedSources else matchedSources + otherSources

        for (s in candidates) {
            val sName = s.optString("name", "MKissa")
            var extUrl = s.optString("extractedUrl", "")
            var rawUrl = s.optString("url", s.optString("sourceUrl", ""))

            if (rawUrl.startsWith("--")) {
                rawUrl = decodeHexUrl(rawUrl.substring(2))
            }

            if (extUrl.isEmpty()) {
                if (rawUrl.contains("ok.ru")) {
                    val m = Pattern.compile("""/(?:videoembed/)?(\d+)(?:[/?#]|$)""").matcher(rawUrl)
                    if (m.find()) {
                        val vId = m.group(1) ?: ""
                        extUrl = extractOk(vId) ?: ""
                    }
                } else if (rawUrl.contains("allanime.day") || rawUrl.contains("/clock")) {
                    extUrl = extractClock(rawUrl) ?: ""
                } else if (rawUrl.contains("mp4upload.com")) {
                    val m = Pattern.compile("""embed-([a-zA-Z0-9]+)\.html""").matcher(rawUrl)
                    if (m.find()) {
                        val vId = m.group(1) ?: ""
                        extUrl = extractMp4(vId) ?: ""
                    }
                } else if (rawUrl.contains(".m3u8") || rawUrl.contains(".mp4")) {
                    extUrl = rawUrl
                }
            }

            if (extUrl.isNotEmpty()) {
                val finalProvider = if (sName.isNotEmpty()) "MKissa ($sName)" else "MKissa"
                return MKissaResult(
                    hlsUrl = extUrl,
                    referer = REFERER,
                    provider = finalProvider,
                    introStart = introStart,
                    introEnd = introEnd,
                    outroStart = outroStart,
                    outroEnd = outroEnd
                )
            }
        }

        return null
    }

    /**
     * Extracts HLS from OK.ru using OK API metadata or HTML embed parser.
     */
    suspend fun extractOk(videoId: String): String? = withContext(Dispatchers.IO) {
        if (videoId.isEmpty()) return@withContext null

        // 1. Direct OK.ru API request (Fastest & most reliable)
        try {
            val form = FormBody.Builder().add("mid", videoId).build()
            val req = Request.Builder()
                .url("https://www.ok.ru/dk?cmd=videoPlayerMetadata")
                .addHeader("User-Agent", UA)
                .addHeader("Referer", "https://ok.ru/")
                .addHeader("Origin", "https://ok.ru")
                .post(form)
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    if (body.isNotEmpty()) {
                        val json = JSONObject(body)
                        val hls = json.optString("hlsManifestUrl", json.optString("ondemandHls", json.optString("hlsMasterPlaylistUrl", "")))
                        if (hls.isNotEmpty()) return@withContext hls
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Embed HTML fallback
        try {
            val req = Request.Builder()
                .url("https://ok.ru/videoembed/$videoId")
                .addHeader("User-Agent", UA)
                .addHeader("Referer", "https://ok.ru/")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val m = Pattern.compile("""ondemandHls\\?&quot;:\\?&quot;(https?://.*?)\\?&quot;""").matcher(html)
                    if (m.find()) {
                        return@withContext m.group(1)?.replace("\\u0026", "&")
                    }
                }
            }
        } catch (_: Exception) {}

        null
    }

    /**
     * Extracts stream from Allanime / MKissa clock endpoint.
     */
    suspend fun extractClock(rawUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            var clockUrl = rawUrl
            if (clockUrl.startsWith("/apivtwo/clock")) {
                clockUrl = "https://allanime.day" + clockUrl.replace("/clock", "/clock.json")
            }
            if (Pattern.compile("""^https?://allanime\.day/apivtwo/clock(?:\.json)?""", Pattern.CASE_INSENSITIVE).matcher(clockUrl).find()) {
                clockUrl = clockUrl.replace("/clock?", "/clock.json?")
            }
            if (!clockUrl.contains("/clock.json") && clockUrl.contains("/clock")) {
                clockUrl = clockUrl.replace("/clock", "/clock.json")
            }

            val req = Request.Builder()
                .url(clockUrl)
                .addHeader("User-Agent", UA)
                .addHeader("Referer", "https://allanime.day/player.html")
                .addHeader("Accept", "*/*")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    val links = json.optJSONArray("links")
                    if (links != null) {
                        for (i in 0 until links.length()) {
                            val item = links.optJSONObject(i) ?: continue
                            val link = item.optString("link", "")
                            val isHls = item.optBoolean("hls", false)
                            if (isHls && link.isNotEmpty()) return@withContext link
                        }
                        for (i in 0 until links.length()) {
                            val item = links.optJSONObject(i) ?: continue
                            val link = item.optString("link", "")
                            if (link.isNotEmpty()) return@withContext link
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        null
    }

    /**
     * Extracts direct MP4 from mp4upload.
     */
    suspend fun extractMp4(id: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("https://www.mp4upload.com/embed-$id.html")
                .addHeader("User-Agent", UA)
                .addHeader("Referer", "https://mp4upload.com/")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val m1 = Pattern.compile("""player\.src\s*\(\s*\{[^}]*\bsrc\s*:\s*"([^"]+)"""").matcher(html)
                    if (m1.find()) return@withContext m1.group(1)?.replace("\\", "")
                    val m2 = Pattern.compile(""""file"\s*:\s*"(https?:[^"]+\.mp4[^"]*)"""").matcher(html)
                    if (m2.find()) return@withContext m2.group(1)?.replace("\\", "")
                }
            }
        } catch (_: Exception) {}

        null
    }
}
