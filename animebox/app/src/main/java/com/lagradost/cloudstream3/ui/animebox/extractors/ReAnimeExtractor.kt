package com.lagradost.cloudstream3.ui.animebox.extractors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ReAnimeResult(
    val hlsUrl: String,
    val referer: String = "https://cdn.4animo.xyz/",
    val subtitleUrl: String = "",
    val subtitles: List<Map<String, String>> = emptyList(),
    val provider: String = "ReAnime",
    val introStart: Long = 0L,
    val introEnd: Long = 0L,
    val outroStart: Long = 0L,
    val outroEnd: Long = 0L
)

object ReAnimeExtractor {

    private fun buildHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
        return com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper.applyDns(builder, com.lagradost.cloudstream3.CloudStreamApp.context).build()
    }

    private val client by lazy { buildHttpClient() }

    private const val BASE_URL = "https://reanime.to"
    private const val CDN_HOST = "https://cdn.4animo.xyz"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val EMBED_PROXY_REGEX = Pattern.compile("""__EMBED_PROXY__\s*=\s*['"]([^'"]+)['"]""", Pattern.CASE_INSENSITIVE)
    private val EMBED_TOKEN_REGEX = Pattern.compile("""__EMBED_TOKEN__\s*=\s*['"]([^'"]+)['"]""", Pattern.CASE_INSENSITIVE)
    private val LEGACY_SOURCES_URL_REGEX = Pattern.compile("""sourcesUrl\s*=\s*['"]([^'"]+)['"]""", Pattern.CASE_INSENSITIVE)
    private val STREAM_PATH_REGEX = Pattern.compile("""['"](/stream/getSources\?t=[^'"]+)['"]""", Pattern.CASE_INSENSITIVE)

    private fun parseSecondsToMs(secondsVal: Double): Long {
        return if (secondsVal in 0.001..9999.0) (secondsVal * 1000.0).toLong() else secondsVal.toLong()
    }

    /**
     * Extracts stream from ReAnime.
     * Supports soft sub, dub audio streams, and hardsub presets with full subtitle tracks.
     */
    suspend fun extract(
        anilistId: Int,
        episodeNum: Int,
        mode: String = "sub" // "sub" for Japanese soft sub/hardsub, "dub" for English dub
    ): ReAnimeResult? = withContext(Dispatchers.IO) {
        if (anilistId <= 0 || episodeNum <= 0) return@withContext null

        val targetMode = if (mode.lowercase() == "dub") "dub" else "sub"

        // 1. Query Re:ANIME API for server list (HD-1, HD-2, etc.)
        val serversToTry = mutableListOf<String>()
        val malId = MalIdResolver.getMalId(anilistId)
        val idCandidates = mutableListOf(anilistId)
        if (malId > 0 && malId != anilistId) {
            idCandidates.add(malId)
        }

        for (candId in idCandidates) {
            try {
                val apiUrl = "$BASE_URL/api/flix/$candId/$episodeNum"
                val apiReq = Request.Builder()
                    .url(apiUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", "$BASE_URL/")
                    .addHeader("Accept", "application/json, text/plain, */*")
                    .build()

                client.newCall(apiReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)
                        if (json.optBoolean("success", false)) {
                            val servers = json.optJSONArray("servers")
                            if (servers != null && servers.length() > 0) {
                                for (i in 0 until servers.length()) {
                                    val s = servers.getJSONObject(i)
                                    val dType = s.optString("dataType", "").lowercase()
                                    val sName = s.optString("serverName", "").lowercase()
                                    if (dType == targetMode && sName.isNotEmpty()) {
                                        serversToTry.add(sName)
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            if (serversToTry.isNotEmpty()) break
        }

        // Fallback server candidates if API query failed or returned no specific servers
        if (serversToTry.isEmpty()) {
            serversToTry.addAll(listOf("hd-1", "hd-2"))
        }

        // 2. Try each server candidate (HD-1, HD-2) via CDN backend
        for (candId in idCandidates) {
            for (serverName in serversToTry.distinct()) {
                val embedUrl = "$CDN_HOST/embed/$serverName/ani/$candId/$episodeNum/$targetMode?k=1"
                try {
                    val embedReq = Request.Builder()
                        .url(embedUrl)
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("Referer", "$CDN_HOST/")
                        .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .build()

                    var html = ""
                    client.newCall(embedReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            html = resp.body?.string() ?: ""
                        }
                    }

                    if (html.isEmpty()) continue

                    // Parse sources proxy path or token
                    var sourcesPath: String? = null
                    val proxyMatcher = EMBED_PROXY_REGEX.matcher(html)
                    if (proxyMatcher.find()) {
                        sourcesPath = proxyMatcher.group(1)
                    }

                    if (sourcesPath.isNullOrEmpty()) {
                        val tokenMatcher = EMBED_TOKEN_REGEX.matcher(html)
                        if (tokenMatcher.find()) {
                            val token = tokenMatcher.group(1)
                            if (!token.isNullOrEmpty()) {
                                sourcesPath = "/stream/getSources?t=$token"
                            }
                        }
                    }

                    if (sourcesPath.isNullOrEmpty()) {
                        val legacyMatcher = LEGACY_SOURCES_URL_REGEX.matcher(html)
                        if (legacyMatcher.find()) {
                            sourcesPath = legacyMatcher.group(1)
                        }
                    }

                    if (sourcesPath.isNullOrEmpty()) {
                        val streamMatcher = STREAM_PATH_REGEX.matcher(html)
                        if (streamMatcher.find()) {
                            sourcesPath = streamMatcher.group(1)
                        }
                    }

                    if (sourcesPath.isNullOrEmpty()) continue

                    val fullSourcesUrl = if (sourcesPath.startsWith("http")) sourcesPath else "$CDN_HOST$sourcesPath"

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
                    val sJson = JSONObject(sourcesBody)

                    val sourcesArr = sJson.optJSONArray("sources")
                    if (sourcesArr != null && sourcesArr.length() > 0) {
                        val firstSrc = sourcesArr.getJSONObject(0)
                        val file = firstSrc.optString("file", firstSrc.optString("url", ""))
                        if (file.isNotEmpty()) {
                            val fullHls = if (file.startsWith("http")) file else "$CDN_HOST$file"
                            val introObj = sJson.optJSONObject("intro")
                            val outroObj = sJson.optJSONObject("outro")
                            val introStart = parseSecondsToMs(introObj?.optDouble("start", 0.0) ?: 0.0)
                            val introEnd = parseSecondsToMs(introObj?.optDouble("end", 0.0) ?: 0.0)
                            val outroStart = parseSecondsToMs(outroObj?.optDouble("start", 0.0) ?: 0.0)
                            val outroEnd = parseSecondsToMs(outroObj?.optDouble("end", 0.0) ?: 0.0)

                            // Extract Subtitle Tracks
                            val subtitlesList = mutableListOf<Map<String, String>>()
                            val tracksArr = sJson.optJSONArray("tracks")
                            if (tracksArr != null) {
                                for (i in 0 until tracksArr.length()) {
                                    val tObj = tracksArr.optJSONObject(i) ?: continue
                                    val f = tObj.optString("file", tObj.optString("url", ""))
                                    val label = tObj.optString("label", tObj.optString("lang", "English"))
                                    val isDefault = tObj.optBoolean("default", false)
                                    if (f.isNotEmpty()) {
                                        val fullSubUrl = if (f.startsWith("http")) f else "$CDN_HOST$f"
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
                                if (targetMode == "dub") {
                                    val engIdx = subtitlesList.indexOfFirst { (it["lang"] ?: "").contains("dubtitle", ignoreCase = true) }
                                        .takeIf { it >= 0 }
                                        ?: subtitlesList.indexOfFirst { (it["lang"] ?: "").contains("eng", ignoreCase = true) }
                                    if (engIdx >= 0) {
                                        val cur = subtitlesList[engIdx]
                                        subtitlesList[engIdx] = mapOf(
                                            "url" to (cur["url"] ?: ""),
                                            "lang" to "English (AI Dubtitle)",
                                            "format" to "vtt",
                                            "default" to "true"
                                        )
                                    }
                                }

                                val chosenSub = if (targetMode == "dub") {
                                    // For English Dub: prioritize subtitle tracks mentioning "AI Dubtitle", "Dubtitle", "AI", or English
                                    subtitlesList.firstOrNull { (it["lang"] ?: "").contains("AI Dubtitle", ignoreCase = true) }
                                        ?: subtitlesList.firstOrNull { (it["lang"] ?: "").contains("dubtitle", ignoreCase = true) }
                                        ?: subtitlesList.firstOrNull { (it["lang"] ?: "").contains("dub", ignoreCase = true) && (it["lang"] ?: "").contains("eng", ignoreCase = true) }
                                        ?: subtitlesList.firstOrNull { (it["lang"] ?: "").contains("AI", ignoreCase = true) && (it["lang"] ?: "").contains("eng", ignoreCase = true) }
                                        ?: subtitlesList.firstOrNull { it["default"] == "true" && (it["lang"] ?: "").contains("eng", true) }
                                        ?: subtitlesList.firstOrNull { (it["lang"] ?: "").contains("english", true) || (it["lang"] ?: "").contains("en", true) }
                                        ?: subtitlesList.first()
                                } else {
                                    subtitlesList.firstOrNull { it["default"] == "true" && (it["lang"] ?: "").contains("eng", true) }
                                        ?: subtitlesList.firstOrNull { (it["lang"] ?: "").contains("english", true) || (it["lang"] ?: "").contains("en", true) }
                                        ?: subtitlesList.first()
                                }
                                defaultSubtitle = chosenSub["url"] ?: ""
                            }
                            if (defaultSubtitle.isEmpty() || (targetMode == "dub" && !subtitlesList.any { (it["lang"] ?: "").contains("AI Dubtitle", ignoreCase = true) })) {
                                if (targetMode == "dub") {
                                    val subFallback = try { extract(anilistId, episodeNum, "sub") } catch (_: Exception) { null }
                                    if (subFallback != null && subFallback.subtitles.isNotEmpty()) {
                                        val engSub = subFallback.subtitles.firstOrNull { (it["lang"] ?: "").contains("eng", true) } ?: subFallback.subtitles.first()
                                        val subUrl = engSub["url"] ?: subFallback.subtitleUrl
                                        if (subUrl.isNotEmpty()) {
                                            val dubtitleMap = mapOf(
                                                "url" to subUrl,
                                                "lang" to "English (AI Dubtitle)",
                                                "format" to "vtt",
                                                "default" to "true"
                                            )
                                            subtitlesList.add(0, dubtitleMap)
                                            defaultSubtitle = subUrl
                                        }
                                    }
                                }
                                if (defaultSubtitle.isEmpty()) {
                                    val anikotoFallback = try { AniKotoExtractor.extractAniKoto(anilistId, episodeNum, audio = targetMode) } catch (_: Exception) { null }
                                    if (anikotoFallback != null && anikotoFallback.subtitles.isNotEmpty()) {
                                        val chosenFallback = anikotoFallback.subtitles.firstOrNull { (it["lang"] ?: "").contains("eng", true) }
                                            ?: anikotoFallback.subtitles.first()
                                        defaultSubtitle = chosenFallback["url"] ?: ""
                                        subtitlesList.addAll(anikotoFallback.subtitles)
                                    } else {
                                        val anibdFallback = try { AniBdExtractor.extract(anilistId, episodeNum) } catch (_: Exception) { null }
                                        if (anibdFallback != null && anibdFallback.subtitleUrl.isNotEmpty()) {
                                            defaultSubtitle = anibdFallback.subtitleUrl
                                            subtitlesList.addAll(anibdFallback.subtitles)
                                        }
                                    }
                                }
                            }
                            if (targetMode == "dub" && defaultSubtitle.isNotEmpty()) {
                                if (!subtitlesList.any { (it["lang"] ?: "").contains("AI Dubtitle", ignoreCase = true) }) {
                                    subtitlesList.add(0, mapOf(
                                        "url" to defaultSubtitle,
                                        "lang" to "English (AI Dubtitle)",
                                        "format" to "vtt",
                                        "default" to "true"
                                    ))
                                }
                            }

                            val providerName = when {
                                targetMode == "dub" -> "ReAnime Dub"
                                else -> "ReAnime (${serverName.uppercase()})"
                            }

                            return@withContext ReAnimeResult(
                                hlsUrl = fullHls,
                                referer = "$CDN_HOST/",
                                subtitleUrl = defaultSubtitle,
                                subtitles = subtitlesList,
                                provider = providerName,
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

        null
    }
}
