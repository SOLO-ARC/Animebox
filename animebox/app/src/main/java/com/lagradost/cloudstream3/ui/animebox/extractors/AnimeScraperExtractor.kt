package com.lagradost.cloudstream3.ui.animebox.extractors
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

data class ScraperStreamResult(
    val provider: String,
    val hlsUrl: String,
    val referer: String = "",
    val subtitles: List<Map<String, String>> = emptyList(),
    val introStart: Long = 0L,
    val introEnd: Long = 0L,
    val outroStart: Long = 0L,
    val outroEnd: Long = 0L
)

object AnimeScraperExtractor {
    private val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private val MIRURO_DOMAINS = listOf(
        "https://www.miruro.tv",
        "https://www.miruro.to",
        "https://www.miruro.bz",
        "https://www.miruro.ru",
        "https://www.miruro.online"
    )

    private fun encodePipePayload(payloadJson: String): String {
        return Base64.encodeToString(payloadJson.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.NO_PADDING)
            .replace("=", "")
    }

    private fun decodeGzip(bytes: ByteArray): String {
        return try {
            GZIPInputStream(bytes.inputStream()).bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: Exception) {
            String(bytes, Charsets.UTF_8)
        }
    }

    private fun decodePipeResponse(encoded: String): JSONObject? {
        return try {
            val padLen = (4 - (encoded.length % 4)) % 4
            val padded = encoded + "=".repeat(padLen)
            val bytes = Base64.decode(padded, Base64.DEFAULT)
            val jsonStr = decodeGzip(bytes)
            JSONObject(jsonStr)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private suspend fun tryMiruroPipe(payloadJson: String): JSONObject? = withContext(Dispatchers.IO) {
        val encodedReq = encodePipePayload(payloadJson)
        for (domain in MIRURO_DOMAINS) {
            try {
                val url = "$domain/api/secure/pipe?e=$encodedReq"
                val req = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", UA)
                    .addHeader("Referer", "$domain/")
                    .addHeader("Accept", "*/*")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val text = resp.body?.string()?.trim() ?: ""
                        if (text.isNotEmpty()) {
                            val decoded = decodePipeResponse(text)
                            if (decoded != null) return@withContext decoded
                        }
                    }
                }
            } catch (e: Exception) {
                // try next domain
            }
        }
        return@withContext null
    }

    private suspend fun fetchMiruroEpisodes(anilistId: Int): JSONObject? {
        val payload = JSONObject().apply {
            put("path", "episodes")
            put("method", "GET")
            put("query", JSONObject().apply { put("anilistId", anilistId) })
            put("body", JSONObject.NULL)
            put("version", "0.1.0")
        }
        return tryMiruroPipe(payload.toString())
    }

    private suspend fun fetchMiruroSources(episodeId: String, provider: String, category: String, anilistId: Int): JSONObject? {
        val encId = Base64.encodeToString(episodeId.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.NO_PADDING)
            .replace("=", "")
        val payload = JSONObject().apply {
            put("path", "sources")
            put("method", "GET")
            put("query", JSONObject().apply {
                put("episodeId", encId)
                put("provider", provider)
                put("category", category)
                put("anilistId", anilistId)
            })
            put("body", JSONObject.NULL)
            put("version", "0.1.0")
        }
        return tryMiruroPipe(payload.toString())
    }

    private suspend fun extractMiruro(anilistId: Int, episodeNum: Int, audio: String): ScraperStreamResult? = withContext(Dispatchers.IO) {
        return@withContext null
    }

    suspend fun extractAllSources(anilistId: Int, episodeNum: Int, audio: String = "sub"): ScraperStreamResult? = withContext(Dispatchers.IO) {
        // 1. Miruro provider pipeline
        val miruroRes = extractMiruro(anilistId, episodeNum, audio)
        if (miruroRes != null) return@withContext miruroRes

        // 2. AniKoto direct HLS provider pipeline
        val anikotoRes = AniKotoExtractor.extractAniKoto(anilistId, episodeNum, audio)
        if (anikotoRes != null) {
            return@withContext ScraperStreamResult(
                provider = anikotoRes.provider,
                hlsUrl = anikotoRes.hlsUrl,
                referer = anikotoRes.referer,
                subtitles = anikotoRes.subtitles,
                introStart = anikotoRes.introStart,
                introEnd = anikotoRes.introEnd,
                outroStart = anikotoRes.outroStart,
                outroEnd = anikotoRes.outroEnd
            )
        }

        return@withContext null
    }

    suspend fun extractAllDubSources(anilistId: Int, episodeNum: Int): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<ScraperStreamResult>()

        // 1. AniKoto all dub streams
        val anikotoDubList = AniKotoExtractor.extractAniKotoAll(anilistId, episodeNum, "dub")
        results.addAll(anikotoDubList)

        // 2. Miruro dub streams
        val epData = fetchMiruroEpisodes(anilistId)
        if (epData != null) {
            val providers = epData.optJSONObject("providers")
            if (providers != null) {
                val ranking = listOf("yuki", "mimi", "beep", "zoro", "bee", "telli", "arc", "yugen", "jet", "neo", "kiwi", "hop", "ally", "pewe", "moo", "bonk")
                for (prov in ranking) {
                    val pObj = providers.optJSONObject(prov) ?: continue
                    val epsObj = pObj.optJSONObject("episodes") ?: continue
                    val epList = epsObj.optJSONArray("dub") ?: continue

                    var targetEpId: String? = null
                    for (i in 0 until epList.length()) {
                        val ep = epList.optJSONObject(i) ?: continue
                        val epNum = ep.optInt("number", -1)
                        if (epNum == episodeNum || (episodeNum == 1 && (epNum <= 1 || epList.length() == 1))) {
                            targetEpId = ep.optString("id", "")
                            break
                        }
                    }

                    if (targetEpId != null && targetEpId.isNotEmpty()) {
                        val sourceData = fetchMiruroSources(targetEpId, prov, "dub", anilistId)
                        if (sourceData != null) {
                            var hlsUrl = ""
                            val streams = sourceData.optJSONArray("streams")
                            if (streams != null) {
                                for (i in 0 until streams.length()) {
                                    val s = streams.optJSONObject(i) ?: continue
                                    val u = s.optString("url", s.optString("file", s.optString("m3u8", s.optString("stream", ""))))
                                    val t = s.optString("type", "")
                                    if (t == "hls" || u.contains(".m3u8") || u.startsWith("http")) {
                                        hlsUrl = u
                                        break
                                    }
                                }
                                if (hlsUrl.isEmpty() && streams.length() > 0) {
                                    val s0 = streams.optJSONObject(0)
                                    hlsUrl = s0?.optString("url", s0.optString("file", s0.optString("m3u8", s0.optString("stream", "")))) ?: ""
                                }
                            }

                            if (hlsUrl.isNotEmpty()) {
                                val subs = mutableListOf<Map<String, String>>()
                                val subtitlesArr = sourceData.optJSONArray("subtitles")
                                if (subtitlesArr != null) {
                                    for (i in 0 until subtitlesArr.length()) {
                                        val sub = subtitlesArr.optJSONObject(i) ?: continue
                                        val file = sub.optString("url", sub.optString("file", ""))
                                        val lang = sub.optString("lang", sub.optString("label", ""))
                                        if (file.isNotEmpty()) {
                                            subs.add(mapOf("lang" to lang, "url" to file))
                                        }
                                    }
                                }

                                var introStart = 0L; var introEnd = 0L
                                var outroStart = 0L; var outroEnd = 0L
                                val introObj = sourceData.optJSONObject("intro")
                                if (introObj != null) {
                                    introStart = introObj.optLong("start", 0L)
                                    introEnd = introObj.optLong("end", 0L)
                                }
                                val outroObj = sourceData.optJSONObject("outro")
                                if (outroObj != null) {
                                    outroStart = outroObj.optLong("start", 0L)
                                    outroEnd = outroObj.optLong("end", 0L)
                                }

                                results.add(
                                    ScraperStreamResult(
                                        provider = "miruro ($prov)",
                                        hlsUrl = hlsUrl,
                                        referer = "https://www.miruro.tv/",
                                        subtitles = subs,
                                        introStart = introStart,
                                        introEnd = introEnd,
                                        outroStart = outroStart,
                                        outroEnd = outroEnd
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
        return@withContext results
    }
}
