package com.lagradost.cloudstream3.ui.animebox.extractors
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class FourAnimoResult(
    val hlsUrl: String,
    val referer: String = "https://cdn.4animo.xyz/",
    val subtitleUrl: String = "",
    val subtitles: List<Map<String, String>> = emptyList(),
    val introStart: Long = 0L,
    val introEnd: Long = 0L,
    val outroStart: Long = 0L,
    val outroEnd: Long = 0L,
    val provider: String = "4animo"
)

object FourAnimoExtractor {

    private val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    const val MAIN_HOST = "https://cdn.4animo.xyz"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private val SOURCES_URL_REGEX = Pattern.compile("""var\s+sourcesUrl\s*=\s*['"]([^'"]+)['"]""", Pattern.CASE_INSENSITIVE)

    private val ENDPOINTS = listOf("hd-2", "hd-1", "hd-3", "hd-4")

    suspend fun extract(
        anilistId: Int,
        episodeNum: Int,
        streamType: String = "sub"
    ): FourAnimoResult? = withContext(Dispatchers.IO) {
        if (anilistId <= 0 || episodeNum <= 0) return@withContext null

        val mode = when (streamType.lowercase()) {
            "dub" -> "dub"
            "raw" -> "raw"
            else -> "sub"
        }

        val malId = MalIdResolver.getMalId(anilistId)
        val idCandidatesForEmbed = mutableListOf("ani" to anilistId)
        if (malId > 0) {
            idCandidatesForEmbed.add("mal" to malId)
        }

        for (hdEndpoint in ENDPOINTS) {
            for ((idType, targetId) in idCandidatesForEmbed) {
                val embedUrl = "$MAIN_HOST/embed/$hdEndpoint/$idType/$targetId/$episodeNum/$mode?k=1"

                try {
                    val embedReq = Request.Builder()
                        .url(embedUrl)
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("Referer", "$MAIN_HOST/")
                        .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .build()

                var html = ""
                client.newCall(embedReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        html = resp.body?.string() ?: ""
                    }
                }

                if (html.isEmpty()) continue

                // 1. Extract sourcesUrl (e.g. /stream/getSources?t=...)
                val matcher = SOURCES_URL_REGEX.matcher(html)
                if (!matcher.find()) continue
                val sourcesPath = matcher.group(1) ?: continue
                val fullSourcesUrl = if (sourcesPath.startsWith("http")) sourcesPath else "$MAIN_HOST$sourcesPath"

                // 2. Fetch stream metadata from getSources
                val srcReq = Request.Builder()
                    .url(fullSourcesUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", embedUrl)
                    .addHeader("X-Requested-With", "XMLHttpRequest")
                    .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
                    .build()

                var sourcesBody = ""
                client.newCall(srcReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        sourcesBody = resp.body?.string() ?: ""
                    }
                }

                if (sourcesBody.isEmpty()) continue
                val json = JSONObject(sourcesBody)

                // 3. Extract HLS playlist URL
                var hlsUrl = ""
                val sourcesArr = json.optJSONArray("sources")
                if (sourcesArr != null && sourcesArr.length() > 0) {
                    val firstSrc = sourcesArr.getJSONObject(0)
                    val file = firstSrc.optString("file", firstSrc.optString("url", ""))
                    if (file.isNotEmpty()) {
                        hlsUrl = if (file.startsWith("http")) file else "$MAIN_HOST$file"
                    }
                }

                if (hlsUrl.isEmpty()) continue

                // 4. Extract Subtitle Tracks
                val subtitlesList = mutableListOf<Map<String, String>>()
                val tracksArr = json.optJSONArray("tracks")
                if (tracksArr != null) {
                    for (i in 0 until tracksArr.length()) {
                        val tObj = tracksArr.optJSONObject(i) ?: continue
                        val file = tObj.optString("file", tObj.optString("url", ""))
                        val label = tObj.optString("label", tObj.optString("lang", "English"))
                        val isDefault = tObj.optBoolean("default", false)
                        if (file.isNotEmpty()) {
                            val fullSubUrl = if (file.startsWith("http")) file else "$MAIN_HOST$file"
                            subtitlesList.add(
                                mapOf(
                                    "url" to fullSubUrl,
                                    "lang" to label,
                                    "default" to isDefault.toString()
                                )
                            )
                        }
                    }
                }

                var defaultSubtitle = ""
                if (subtitlesList.isNotEmpty()) {
                    val chosenSub = subtitlesList.firstOrNull { it["default"] == "true" && (it["lang"] ?: "").contains("eng", true) }
                        ?: subtitlesList.firstOrNull { (it["lang"] ?: "").contains("english", true) || (it["lang"] ?: "").contains("en", true) }
                        ?: subtitlesList.first()
                    defaultSubtitle = chosenSub["url"] ?: ""
                }

                // 5. Extract Intro & Outro Timestamps (convert to ms)
                fun parseSecToMs(sec: Double): Long {
                    return if (sec in 0.001..9999.0) (sec * 1000.0).toLong() else sec.toLong()
                }

                var introStart = 0L
                var introEnd = 0L
                val introObj = json.optJSONObject("intro")
                if (introObj != null) {
                    introStart = parseSecToMs(introObj.optDouble("start", 0.0))
                    introEnd = parseSecToMs(introObj.optDouble("end", 0.0))
                }

                var outroStart = 0L
                var outroEnd = 0L
                val outroObj = json.optJSONObject("outro")
                if (outroObj != null) {
                    outroStart = parseSecToMs(outroObj.optDouble("start", 0.0))
                    outroEnd = parseSecToMs(outroObj.optDouble("end", 0.0))
                }

                val serverTag = json.optString("server", hdEndpoint.uppercase())

                return@withContext FourAnimoResult(
                    hlsUrl = hlsUrl,
                    referer = embedUrl,
                    subtitleUrl = defaultSubtitle,
                    subtitles = subtitlesList,
                    introStart = introStart,
                    introEnd = introEnd,
                    outroStart = outroStart,
                    outroEnd = outroEnd,
                    provider = "4animo ($serverTag)"
                )
            } catch (e: Exception) {
                // Try next endpoint
            }
        }
    }
    null
}
}
