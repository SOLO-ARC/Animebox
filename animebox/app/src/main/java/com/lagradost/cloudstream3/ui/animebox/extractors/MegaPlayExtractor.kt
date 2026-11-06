package com.lagradost.cloudstream3.ui.animebox.extractors
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import com.lagradost.cloudstream3.ui.animebox.api.AniZipClient
import com.lagradost.cloudstream3.ui.animebox.api.AniListClient
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

object MalIdResolver {
    private val malCache = ConcurrentHashMap<Int, Int>()

    suspend fun getMalId(anilistId: Int): Int = withContext(Dispatchers.IO) {
        if (anilistId <= 0) return@withContext 0
        malCache[anilistId]?.let { if (it > 0) return@withContext it }

        // 1. Check AniZip mappings (Fastest & most reliable: ~30-50ms)
        try {
            val aniZip = AniZipClient.getAniZipJson(anilistId)
            if (aniZip != null) {
                val mappings = aniZip.optJSONObject("mappings")
                if (mappings != null) {
                    val malId = mappings.optInt("mal_id", mappings.optInt("malId", mappings.optInt("myanimelist_id", 0)))
                    if (malId > 0) {
                        malCache[anilistId] = malId
                        return@withContext malId
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Check AniList details
        try {
            val details = AniListClient.getAnimeDetails(anilistId)
            if (details != null) {
                val media = JSONObject(details).optJSONObject("data")?.optJSONObject("Media")
                val malId = media?.optInt("idMal", 0) ?: 0
                if (malId > 0) {
                    malCache[anilistId] = malId
                    return@withContext malId
                }
            }
        } catch (_: Exception) {}

        // 3. Fallback to anilistId directly
        malCache[anilistId] = anilistId
        return@withContext anilistId
    }
}

object MegaPlayExtractor {

    private val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    const val MAIN_REFERER = "https://megaplay.buzz/"
    const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val DATA_ID_REGEX = Pattern.compile("""data-id=["']?([^"'\s>]+)""", Pattern.CASE_INSENSITIVE)

    data class MegaPlayResult(
        val hlsUrl: String,
        val referer: String = MAIN_REFERER,
        val subtitleUrl: String = "",
        val subtitles: List<Map<String, String>> = emptyList(),
        val introStart: Long = 0L,
        val introEnd: Long = 0L,
        val outroStart: Long = 0L,
        val outroEnd: Long = 0L
    )

    suspend fun extract(
        anilistId: Int,
        episodeNum: Int,
        streamType: String = "sub"
    ): MegaPlayResult? = withContext(Dispatchers.IO) {
        if (anilistId <= 0 && episodeNum <= 0) return@withContext null
        try {
            val language = when (streamType.lowercase()) {
                "dub" -> "dub"
                "raw" -> "raw"
                else -> "sub"
            }

            val malId = MalIdResolver.getMalId(anilistId)

            // ─── Step 1: Candidate Embed Endpoints (AniList ID + MAL ID) ───
            // Airing anime and new seasons are often indexed under either AniList ID or MAL ID first.
            // Try both so if one fails or 410s, the other succeeds.
            val candidateEndpoints = mutableListOf<Pair<String, String>>()
            if (anilistId > 0) {
                candidateEndpoints.add("ani" to "https://megaplay.buzz/stream/ani/$anilistId/$episodeNum/$language")
            }
            if (malId > 0) {
                candidateEndpoints.add("mal" to "https://megaplay.buzz/stream/mal/$malId/$episodeNum/$language")
            }

            var validJson: JSONObject? = null
            var workingReferer = MAIN_REFERER
            var hlsUrl = ""

            for ((_, embedUrl) in candidateEndpoints) {
                var fileId = ""
                val embedReq = Request.Builder()
                    .url(embedUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", MAIN_REFERER)
                    .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                    .build()

                try {
                    client.newCall(embedReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val html = resp.body?.string() ?: ""
                            val matcher = DATA_ID_REGEX.matcher(html)
                            if (matcher.find()) {
                                val foundId = matcher.group(1) ?: ""
                                if (foundId.isNotEmpty() && !foundId.equals("no", ignoreCase = true)) {
                                    fileId = foundId
                                    workingReferer = embedUrl
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}

                if (fileId.isEmpty()) continue

                // Request getSources JSON using fileId
                var json: JSONObject? = null
                for (sParam in listOf("tcdn", "", "bcdn")) {
                    val sourcesUrl = if (sParam.isNotEmpty()) {
                        "https://megaplay.buzz/stream/getSources?id=$fileId&s=$sParam"
                    } else {
                        "https://megaplay.buzz/stream/getSources?id=$fileId"
                    }

                    val sourcesReq = Request.Builder()
                        .url(sourcesUrl)
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("Referer", workingReferer)
                        .addHeader("X-Requested-With", "XMLHttpRequest")
                        .addHeader("Accept", "application/json, text/javascript, */*; q=0.01")
                        .build()

                    try {
                        client.newCall(sourcesReq).execute().use { resp ->
                            if (resp.isSuccessful) {
                                val jsonStr = resp.body?.string() ?: ""
                                if (jsonStr.isNotEmpty()) {
                                    val parsed = JSONObject(jsonStr)
                                    if (parsed.has("sources") || parsed.has("enc") || parsed.has("file")) {
                                        json = parsed
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}

                    if (json != null) break
                }

                val currentJson = json ?: continue

                // Try extracting HLS from this candidate
                var candidateHls = ""
                if (currentJson.has("sources")) {
                    val sourcesObj = currentJson.optJSONObject("sources")
                    if (sourcesObj != null) {
                        candidateHls = sourcesObj.optString("file", sourcesObj.optString("url", ""))
                    } else {
                        val sourcesArr = currentJson.optJSONArray("sources")
                        if (sourcesArr != null && sourcesArr.length() > 0) {
                            for (i in 0 until sourcesArr.length()) {
                                val s = sourcesArr.optJSONObject(i) ?: continue
                                val file = s.optString("file", s.optString("url", s.optString("src", "")))
                                if (file.isNotEmpty()) {
                                    candidateHls = file
                                    if (file.contains(".m3u8")) break
                                }
                            }
                        }
                    }
                }

                if (candidateHls.isEmpty()) {
                    candidateHls = currentJson.optString("file", currentJson.optString("url", currentJson.optString("hls", "")))
                }

                if (candidateHls.isEmpty() && currentJson.has("enc")) {
                    candidateHls = decryptEncSources(currentJson.optString("enc")) ?: ""
                }

                if (candidateHls.contains("nexabloom.top")) {
                    candidateHls = candidateHls.replace("fetch.nexabloom.top", "ncdn.imgnex.top")
                }

                // Check for dummy TikTok ad streams or invalid placeholder streams (e.g. Esper Mami dummy file 155808)
                if (candidateHls.contains("tiktokcdn.com", ignoreCase = true) ||
                    candidateHls.contains("ad-site", ignoreCase = true) ||
                    candidateHls.contains("/155808/", ignoreCase = true) ||
                    fileId == "155808") {
                    continue
                }

                if (candidateHls.isNotEmpty()) {
                    hlsUrl = candidateHls
                    validJson = currentJson
                    break
                }
            }

            if (hlsUrl.isEmpty() || validJson == null) return@withContext null

            // ─── 4. Extract Subtitles & Tracks ───
            val subtitlesList = mutableListOf<Map<String, String>>()
            val tracks = validJson.optJSONArray("tracks") ?: validJson.optJSONArray("subtitles")

            if (tracks != null) {
                for (i in 0 until tracks.length()) {
                    val t = tracks.optJSONObject(i) ?: continue
                    val file = t.optString("file", t.optString("url", t.optString("src", "")))
                    val label = t.optString("label", t.optString("lang", t.optString("language", "English")))
                    val kind = t.optString("kind", "")
                    val lowerFile = file.lowercase()
                    val lowerLabel = label.lowercase()
                    val lowerKind = kind.lowercase()
                    if (lowerKind.contains("thumb") || lowerKind.contains("sprite") ||
                        lowerLabel.contains("thumb") || lowerLabel.contains("sprite") ||
                        lowerFile.contains("thumb") || lowerFile.contains("sprite") || lowerFile.contains("xywh=") ||
                        lowerFile.endsWith(".webp") || lowerFile.endsWith(".jpg") || lowerFile.endsWith(".jpeg") || lowerFile.endsWith(".png")
                    ) {
                        continue
                    }
                    if (file.isNotEmpty() && (kind == "captions" || kind == "subtitles" || kind.isEmpty() || file.endsWith(".vtt") || file.endsWith(".srt"))) {
                        subtitlesList.add(mapOf("url" to file, "lang" to label))
                    }
                }
            }

            // Note: If subtitlesList is empty, do NOT reject the stream as long as it's a genuine video (not an ad/placeholder).
            // Many anime (raw, embedded subtitles, or hardsub) have valid video without separate subtitle tracks.

            var defaultSubtitle = ""
            if (subtitlesList.isNotEmpty()) {
                val engSub = subtitlesList.find {
                    val l = (it["lang"] ?: "").lowercase()
                    l.contains("english") || l.contains("en")
                } ?: subtitlesList.first()
                defaultSubtitle = engSub["url"] ?: ""
            }

            // ─── 5. Extract Intro & Outro Timestamps ───
            fun parseSecondsToMs(secondsVal: Double): Long {
                return if (secondsVal in 0.001..9999.0) (secondsVal * 1000.0).toLong() else secondsVal.toLong()
            }

            var introStart = 0L
            var introEnd = 0L
            val introObj = validJson.optJSONObject("intro")
            if (introObj != null) {
                introStart = parseSecondsToMs(introObj.optDouble("start", 0.0))
                introEnd = parseSecondsToMs(introObj.optDouble("end", 0.0))
            }

            var outroStart = 0L
            var outroEnd = 0L
            val outroObj = validJson.optJSONObject("outro")
            if (outroObj != null) {
                outroStart = parseSecondsToMs(outroObj.optDouble("start", 0.0))
                outroEnd = parseSecondsToMs(outroObj.optDouble("end", 0.0))
            }

            return@withContext MegaPlayResult(
                hlsUrl = hlsUrl,
                referer = MAIN_REFERER,
                subtitleUrl = defaultSubtitle,
                subtitles = subtitlesList,
                introStart = introStart,
                introEnd = introEnd,
                outroStart = outroStart,
                outroEnd = outroEnd
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun decryptEncSources(encStr: String): String? {
        if (encStr.isBlank()) return null
        return try {
            var base64 = encStr.replace('-', '+').replace('_', '/')
            val mod = base64.length % 4
            if (mod > 0) base64 += "=".repeat(4 - mod)
            val rawBytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)

            val keyBytes = "i?LMTAx0Q6,:}50U".toByteArray(Charsets.UTF_8).copyOf(32)
            val ivBytes = "W0;27ToaUpl_P%'c".toByteArray(Charsets.UTF_8).copyOf(16)

            val secretKey = javax.crypto.spec.SecretKeySpec(keyBytes, "AES")
            val ivSpec = javax.crypto.spec.IvParameterSpec(ivBytes)
            val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, secretKey, ivSpec)

            val decryptedBytes = cipher.doFinal(rawBytes)
            val decryptedJson = String(decryptedBytes, Charsets.UTF_8)
            val decObj = JSONObject(decryptedJson)
            var file = decObj.optString("file", decObj.optString("url", ""))
            if (file.isEmpty() && decObj.has("sources")) {
                val arr = decObj.optJSONArray("sources")
                if (arr != null && arr.length() > 0) {
                    file = arr.getJSONObject(0).optString("file", arr.getJSONObject(0).optString("url", ""))
                }
            }
            file.ifEmpty { null }
        } catch (e: Exception) {
            null
        }
    }
}
