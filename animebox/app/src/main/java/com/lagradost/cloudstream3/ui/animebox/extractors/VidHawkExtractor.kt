package com.lagradost.cloudstream3.ui.animebox.extractors
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class VidHawkResult(
    val hlsUrl: String,
    val referer: String = "https://vidhawk.buzz/",
    val subtitleUrl: String = "",
    val subtitles: List<Map<String, String>> = emptyList(),
    val introStart: Long = 0L,
    val introEnd: Long = 0L,
    val outroStart: Long = 0L,
    val outroEnd: Long = 0L,
    val provider: String = "vidhawk"
)

object VidHawkExtractor {

    private val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    const val MAIN_REFERER = "https://vidhawk.buzz/"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val SERVERS = listOf("kari", "flow", "core", "gojo", "zuri")

    suspend fun extract(
        anilistId: Int,
        episodeNum: Int,
        streamType: String = "sub",
        preferredServer: String = "kari"
    ): VidHawkResult? = withContext(Dispatchers.IO) {
        if (anilistId <= 0 || episodeNum <= 0) return@withContext null

        val audio = if (streamType.lowercase() == "dub") "dub" else "sub"
        val serverOrder = mutableListOf(preferredServer)
        for (s in SERVERS) {
            if (s != preferredServer) serverOrder.add(s)
        }

        for (server in serverOrder) {
            try {
                val resolveUrl = "https://vidhawk.buzz/api/stream/resolve?episode=$episodeNum&server=$server&audio=$audio&anilistId=$anilistId"
                val embedReferer = "https://vidhawk.buzz/embed/ani/$anilistId/$episodeNum/$audio?server=$server"

                val resolveReq = Request.Builder()
                    .url(resolveUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", embedReferer)
                    .addHeader("Accept", "application/json, text/plain, */*")
                    .build()

                var ticket = ""
                var returnedServer = server
                client.newCall(resolveReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        if (body.isNotEmpty()) {
                            val json = JSONObject(body)
                            ticket = json.optString("ticket", "")
                            returnedServer = json.optString("server", server)
                        }
                    }
                }

                if (ticket.isEmpty()) continue

                val playUrl = "https://vidhawk.buzz/api/play?t=${URLEncoder.encode(ticket, "UTF-8")}"
                val playReq = Request.Builder()
                    .url(playUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", embedReferer)
                    .addHeader("Accept", "application/json, text/plain, */*")
                    .build()

                client.newCall(playReq).execute().use { playResp ->
                    if (!playResp.isSuccessful) return@use
                    val playBody = playResp.body?.string() ?: return@use
                    if (playBody.isEmpty()) return@use

                    val playJson = JSONObject(playBody)

                    // 1. Extract HLS video URL
                    var hlsUrl = ""
                    val tracksArr = playJson.optJSONArray("tracks")
                    if (tracksArr != null && tracksArr.length() > 0) {
                        for (i in 0 until tracksArr.length()) {
                            val tObj = tracksArr.optJSONObject(i) ?: continue
                            val tId = tObj.optString("id", "").lowercase()
                            val src = tObj.optString("src", tObj.optString("url", tObj.optString("file", "")))
                            if (tId == audio && src.isNotEmpty()) {
                                hlsUrl = src
                                break
                            }
                        }
                        if (hlsUrl.isEmpty()) {
                            val first = tracksArr.optJSONObject(0)
                            hlsUrl = first?.optString("src", first.optString("url", first.optString("file", ""))) ?: ""
                        }
                    }

                    if (hlsUrl.isEmpty()) {
                        hlsUrl = playJson.optString("src", playJson.optString("url", playJson.optString("file", "")))
                    }

                    if (hlsUrl.isEmpty()) return@use

                    // 2. Extract Captions (Soft Subtitles)
                    val subtitlesList = mutableListOf<Map<String, String>>()
                    val captionsObj = playJson.optJSONObject("captions")
                    val activeCaptionsArr = if (captionsObj != null) {
                        if (audio == "dub") {
                            captionsObj.optJSONArray("dub") ?: captionsObj.optJSONArray("sub")
                        } else {
                            captionsObj.optJSONArray("sub") ?: captionsObj.optJSONArray("dub")
                        }
                    } else {
                        playJson.optJSONArray("subtitles") ?: playJson.optJSONArray("tracks")
                    }

                    if (activeCaptionsArr != null) {
                        for (i in 0 until activeCaptionsArr.length()) {
                            val cObj = activeCaptionsArr.optJSONObject(i) ?: continue
                            val src = cObj.optString("src", cObj.optString("url", cObj.optString("file", "")))
                            val label = cObj.optString("label", cObj.optString("lang", cObj.optString("language", "English")))
                            if (src.isNotEmpty()) {
                                subtitlesList.add(mapOf("url" to src, "lang" to label))
                            }
                        }
                    }

                    var defaultSubtitle = ""
                    if (subtitlesList.isNotEmpty()) {
                        val engSub = subtitlesList.find {
                            val l = (it["lang"] ?: "").lowercase()
                            l.contains("english") || l.contains("en")
                        } ?: subtitlesList.first()
                        defaultSubtitle = engSub["url"] ?: ""
                    }

                    // 3. Extract Intro & Outro Timestamps (seconds -> milliseconds)
                    fun parseSecToMs(sec: Double): Long {
                        return if (sec in 0.001..9999.0) (sec * 1000.0).toLong() else sec.toLong()
                    }

                    var introStart = 0L
                    var introEnd = 0L
                    val introObj = playJson.optJSONObject("intro")
                    if (introObj != null) {
                        introStart = parseSecToMs(introObj.optDouble("start", 0.0))
                        introEnd = parseSecToMs(introObj.optDouble("end", 0.0))
                    }

                    var outroStart = 0L
                    var outroEnd = 0L
                    val outroObj = playJson.optJSONObject("outro")
                    if (outroObj != null) {
                        outroStart = parseSecToMs(outroObj.optDouble("start", 0.0))
                        outroEnd = parseSecToMs(outroObj.optDouble("end", 0.0))
                    }

                    return@withContext VidHawkResult(
                        hlsUrl = hlsUrl,
                        referer = MAIN_REFERER,
                        subtitleUrl = defaultSubtitle,
                        subtitles = subtitlesList,
                        introStart = introStart,
                        introEnd = introEnd,
                        outroStart = outroStart,
                        outroEnd = outroEnd,
                        provider = "vidhawk ($returnedServer)"
                    )
                }
            } catch (e: Exception) {
                // Try next server
            }
        }

        null
    }
}
