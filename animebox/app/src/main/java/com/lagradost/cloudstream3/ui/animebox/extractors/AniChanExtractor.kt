package com.lagradost.cloudstream3.ui.animebox.extractors
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object AniChanExtractor {

    private val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val API_BASE = "https://anichan.to"
    private const val MAIN_REFERER = "https://anichan.net/"
    private const val ORIGIN = "https://anichan.net"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private val DATA_ID_REGEX = Pattern.compile("""data-id=["']?([^"'\s>]+)""", Pattern.CASE_INSENSITIVE)

    @Volatile
    private var cachedSessionCookie: String? = null
    @Volatile
    private var sessionCookieExpiresAt: Long = 0L

    private suspend fun ensureSession(): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!cachedSessionCookie.isNullOrEmpty() && now < sessionCookieExpiresAt) {
            return@withContext cachedSessionCookie
        }

        try {
            val sessionReq = Request.Builder()
                .url("$API_BASE/api/watch/session")
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", MAIN_REFERER)
                .addHeader("Origin", ORIGIN)
                .addHeader("Content-Type", "application/json")
                .post(okhttp3.RequestBody.create(null, """{"token":""}"""))
                .build()

            client.newCall(sessionReq).execute().use { resp ->
                val setCookieHeaders = resp.headers("Set-Cookie")
                for (sc in setCookieHeaders) {
                    if (sc.contains("anichan_ws=")) {
                        val cookieVal = sc.substringAfter("anichan_ws=").substringBefore(";")
                        cachedSessionCookie = cookieVal
                        sessionCookieExpiresAt = now + (2 * 3600 * 1000L) // 2 hours
                        return@withContext cookieVal
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        cachedSessionCookie
    }

    suspend fun extractHardSub(anilistId: Int, episodeNum: Int): HardSubResult? = withContext(Dispatchers.IO) {
        if (anilistId <= 0 || episodeNum <= 0) return@withContext null

        try {
            val sessionCookie = ensureSession()
            val url = "$API_BASE/api/watch/servers?anilistId=$anilistId&ep=$episodeNum&category=sub"
            val reqBuilder = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", MAIN_REFERER)
                .addHeader("Origin", ORIGIN)
                .addHeader("Accept", "application/json, text/plain, */*")

            if (!sessionCookie.isNullOrEmpty()) {
                reqBuilder.addHeader("Cookie", "anichan_ws=$sessionCookie")
            }

            val serversJson = client.newCall(reqBuilder.build()).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string() ?: return@withContext null
                if (body.isEmpty()) return@withContext null
                JSONObject(body)
            }

            val serversArr = serversJson.optJSONArray("servers") ?: return@withContext null

            // First pass: look specifically for hardsub servers
            for (pass in 0..1) {
                for (i in 0 until serversArr.length()) {
                    val serverObj = serversArr.optJSONObject(i) ?: continue
                    val subType = serverObj.optString("subType", "").lowercase()
                    val category = serverObj.optString("category", "").lowercase()
                    val embedUrl = serverObj.optString("embed", "")
                    val streamUrl = serverObj.optString("stream", serverObj.optString("url", ""))
                    val label = serverObj.optString("label", serverObj.optString("name", "AniChan"))

                    val isHard = subType == "hard" || category == "hsub" || embedUrl.contains("/hsub")
                    if (pass == 0 && !isHard) continue

                    // 1. Direct stream provided in server object
                    var directStream = if (streamUrl.isNotEmpty()) streamUrl else embedUrl
                    if (directStream.isNotEmpty()) {
                        if (directStream.startsWith("/")) {
                            directStream = "$API_BASE$directStream"
                        }
                        if (directStream.contains(".m3u8") || directStream.contains("/api/watch/m3u8")) {
                            return@withContext HardSubResult(
                                hlsUrl = directStream,
                                referer = MAIN_REFERER,
                                provider = "AniChan ($label)"
                            )
                        }
                    }

                    // 2. Embed URL provided
                    if (embedUrl.isNotEmpty()) {
                        val embedResult = resolveEmbed(embedUrl, label)
                        if (embedResult != null && embedResult.hlsUrl.isNotEmpty()) {
                            return@withContext embedResult
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        null
    }

    private suspend fun resolveEmbed(embedUrl: String, label: String): HardSubResult? = withContext(Dispatchers.IO) {
        try {
            val embedHost = try {
                val u = URI.create(embedUrl)
                "${u.scheme}://${u.host}/"
            } catch (_: Exception) { "https://anichan.net/" }

            val req = Request.Builder()
                .url(embedUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", MAIN_REFERER)
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            var html = ""
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    html = resp.body?.string() ?: ""
                }
            }

            if (html.isEmpty()) return@withContext null

            // Look for data-id
            var dataId = ""
            val matcher = DATA_ID_REGEX.matcher(html)
            if (matcher.find()) {
                dataId = matcher.group(1) ?: ""
            }

            if (dataId.isNotEmpty()) {
                val sourcesCandidates = listOf(
                    "${embedHost}stream/getSources?id=$dataId",
                    "https://vidtube.site/stream/getSources?id=$dataId",
                    "https://megaplay.buzz/stream/getSources?id=$dataId"
                )

                for (srcUrl in sourcesCandidates) {
                    try {
                        val srcReq = Request.Builder()
                            .url(srcUrl)
                            .addHeader("User-Agent", USER_AGENT)
                            .addHeader("Referer", embedUrl)
                            .addHeader("X-Requested-With", "XMLHttpRequest")
                            .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
                            .build()

                        client.newCall(srcReq).execute().use { srcResp ->
                            if (!srcResp.isSuccessful) return@use
                            val srcBody = srcResp.body?.string() ?: return@use
                            if (srcBody.isEmpty()) return@use

                            val json = JSONObject(srcBody)
                            var hlsUrl = ""
                            if (json.has("sources")) {
                                val sourcesObj = json.optJSONObject("sources")
                                if (sourcesObj != null) {
                                    hlsUrl = sourcesObj.optString("file", sourcesObj.optString("url", ""))
                                } else {
                                    val sourcesArr = json.optJSONArray("sources")
                                    if (sourcesArr != null && sourcesArr.length() > 0) {
                                        for (k in 0 until sourcesArr.length()) {
                                            val s = sourcesArr.optJSONObject(k) ?: continue
                                            val file = s.optString("file", s.optString("url", s.optString("src", "")))
                                            if (file.isNotEmpty()) {
                                                hlsUrl = file
                                                if (file.contains(".m3u8")) break
                                            }
                                        }
                                    }
                                }
                            }

                            if (hlsUrl.isEmpty()) {
                                hlsUrl = json.optString("file", json.optString("url", json.optString("hls", "")))
                            }

                            if (hlsUrl.isNotEmpty()) {
                                var introStart = 0L
                                var introEnd = 0L
                                val introObj = json.optJSONObject("intro")
                                if (introObj != null) {
                                    val s = introObj.optDouble("start", 0.0)
                                    val e = introObj.optDouble("end", 0.0)
                                    introStart = if (s in 0.001..9999.0) (s * 1000.0).toLong() else s.toLong()
                                    introEnd = if (e in 0.001..9999.0) (e * 1000.0).toLong() else e.toLong()
                                }

                                var outroStart = 0L
                                var outroEnd = 0L
                                val outroObj = json.optJSONObject("outro")
                                if (outroObj != null) {
                                    val s = outroObj.optDouble("start", 0.0)
                                    val e = outroObj.optDouble("end", 0.0)
                                    outroStart = if (s in 0.001..9999.0) (s * 1000.0).toLong() else s.toLong()
                                    outroEnd = if (e in 0.001..9999.0) (e * 1000.0).toLong() else e.toLong()
                                }

                                return@withContext HardSubResult(
                                    hlsUrl = hlsUrl,
                                    referer = embedHost,
                                    provider = "AniChan ($label)",
                                    introStart = introStart,
                                    introEnd = introEnd,
                                    outroStart = outroStart,
                                    outroEnd = outroEnd
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            // Fallback regex search for direct m3u8 in embed HTML
            val patterns = listOf(
                Pattern.compile("""const\s+src\s*=\s*["'](https?://[^"']+\.m3u8[^"']*)["']""", Pattern.CASE_INSENSITIVE),
                Pattern.compile("""file\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""", Pattern.CASE_INSENSITIVE),
                Pattern.compile("""["'](https?://[^"']+/master\.m3u8[^"']*)["']""", Pattern.CASE_INSENSITIVE),
                Pattern.compile("""["'](https?://[^"']+\.m3u8[^"']*)["']""", Pattern.CASE_INSENSITIVE)
            )
            for (p in patterns) {
                val m = p.matcher(html)
                if (m.find()) {
                    val u = m.group(1)
                    if (!u.isNullOrEmpty()) {
                        return@withContext HardSubResult(
                            hlsUrl = u,
                            referer = embedHost,
                            provider = "AniChan ($label)"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        null
    }
}
