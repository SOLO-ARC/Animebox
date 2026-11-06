package com.lagradost.cloudstream3.ui.animebox.extractors
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider
import com.lagradost.cloudstream3.ui.animebox.api.ShinChanSupabaseManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class ShinChanStreamResult(
    val hlsUrl: String,
    val mp4Url: String? = null,
    val referer: String = "https://ok.ru/",
    val provider: String = "Shinchan OTAKU (OK.ru)",
    val title: String = "Japanese (HardSub)",
    val quality: String = "720p",
    val isHardSub: Boolean = true
)

object ShinChanOkRuExtractor {

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun extractShinChanStream(
        anilistId: Int,
        episode: Int = 1,
        animeTitle: String? = null,
        explicitVideoUrl: String? = null,
        preferDirectMp4: Boolean = false
    ): ShinChanStreamResult? = withContext(Dispatchers.IO) {
        try {
            val videoUrl = explicitVideoUrl ?: ShinChanSupabaseManager.getVideoUrl(episode) ?: ShinChanEpisodeProvider.getShinChanVideoUrl(episode)
            val videoId = if (!videoUrl.isNullOrEmpty()) {
                extractVideoId(videoUrl)
            } else {
                null
            }

            if (videoId.isNullOrEmpty()) return@withContext null

            // 1. Method 1: Direct API Metadata request (Fastest & most reliable)
            try {
                val apiReq = Request.Builder()
                    .url("https://www.ok.ru/dk?cmd=videoPlayerMetadata")
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", "https://ok.ru/")
                    .addHeader("Origin", "https://ok.ru")
                    .post(FormBody.Builder().add("mid", videoId).build())
                    .build()

                client.newCall(apiReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()
                        if (!body.isNullOrEmpty()) {
                            val json = JSONObject(body)
                            val hls = json.optString("hlsManifestUrl", "")
                                .ifEmpty { json.optString("ondemandHls", "") }
                                .ifEmpty { json.optString("hlsMasterPlaylistUrl", "") }

                            var directMp4Url: String? = null
                            var bestQuality = "720p"
                            val videosArr = json.optJSONArray("videos")
                            if (videosArr != null && videosArr.length() > 0) {
                                val qualityOrder = listOf("ultra", "quad", "full", "hd", "sd", "low", "lowest", "mobile")
                                for (q in qualityOrder) {
                                    for (j in 0 until videosArr.length()) {
                                        val vObj = videosArr.optJSONObject(j) ?: continue
                                        val name = vObj.optString("name", "").lowercase()
                                        if (name == q) {
                                            val streamUrl = vObj.optString("url", "")
                                            if (streamUrl.isNotEmpty()) {
                                                directMp4Url = if (streamUrl.startsWith("//")) "https:$streamUrl" else streamUrl
                                                bestQuality = name.uppercase()
                                                break
                                            }
                                        }
                                    }
                                    if (directMp4Url != null) break
                                }
                            }

                            if (preferDirectMp4 && directMp4Url != null) {
                                return@withContext ShinChanStreamResult(
                                    hlsUrl = directMp4Url,
                                    mp4Url = directMp4Url,
                                    quality = bestQuality
                                )
                            }

                            if (hls.isNotEmpty()) {
                                return@withContext ShinChanStreamResult(
                                    hlsUrl = hls,
                                    mp4Url = directMp4Url,
                                    quality = bestQuality
                                )
                            }

                            if (directMp4Url != null) {
                                return@withContext ShinChanStreamResult(
                                    hlsUrl = directMp4Url,
                                    mp4Url = directMp4Url,
                                    quality = bestQuality
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            // 2. Method 2: HTML videoembed data-options parser
            val embedUrl = "https://ok.ru/videoembed/$videoId"
            val html = fetchHtml(embedUrl, referer = "https://ok.ru/")
            if (html.isNotEmpty()) {
                val optionsPattern = Pattern.compile("data-options=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                val matcher = optionsPattern.matcher(html)
                if (matcher.find()) {
                    val rawJson = matcher.group(1)
                        ?.replace("&quot;", "\"")
                        ?.replace("&amp;", "&")
                        ?.replace("&#39;", "'") ?: ""
                    try {
                        val root = JSONObject(rawJson)
                        val flashvars = root.optJSONObject("flashvars")
                        if (flashvars != null) {
                            val hlsDirect = flashvars.optString("hlsManifestUrl", "")
                            val metaStr = flashvars.optString("metadata", "")
                            var mp4Direct: String? = null
                            var bestQual = "720p"

                            if (metaStr.isNotEmpty()) {
                                val meta = JSONObject(metaStr)
                                val videosArr = meta.optJSONArray("videos")
                                if (videosArr != null && videosArr.length() > 0) {
                                    val qualityOrder = listOf("ultra", "quad", "full", "hd", "sd", "low", "lowest", "mobile")
                                    for (q in qualityOrder) {
                                        for (j in 0 until videosArr.length()) {
                                            val vObj = videosArr.optJSONObject(j) ?: continue
                                            val name = vObj.optString("name", "").lowercase()
                                            if (name == q) {
                                                val streamUrl = vObj.optString("url", "")
                                                if (streamUrl.isNotEmpty()) {
                                                    mp4Direct = if (streamUrl.startsWith("//")) "https:$streamUrl" else streamUrl
                                                    bestQual = name.uppercase()
                                                    break
                                                }
                                            }
                                        }
                                        if (mp4Direct != null) break
                                    }
                                }
                            }

                            if (preferDirectMp4 && mp4Direct != null) {
                                return@withContext ShinChanStreamResult(
                                    hlsUrl = mp4Direct,
                                    mp4Url = mp4Direct,
                                    quality = bestQual
                                )
                            }

                            if (hlsDirect.isNotEmpty()) {
                                return@withContext ShinChanStreamResult(
                                    hlsUrl = hlsDirect,
                                    mp4Url = mp4Direct,
                                    quality = bestQual
                                )
                            }

                            if (mp4Direct != null) {
                                return@withContext ShinChanStreamResult(
                                    hlsUrl = mp4Direct,
                                    mp4Url = mp4Direct,
                                    quality = bestQual
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }

                // 3. Fallback regex extraction for m3u8 or vd okcdn streams
                val m3u8Pattern = Pattern.compile("https?://[^\\s\"'<>]+?\\.m3u8[^\\s\"'<>]*", Pattern.CASE_INSENSITIVE)
                val m3u8Matcher = m3u8Pattern.matcher(html)
                if (m3u8Matcher.find()) {
                    return@withContext ShinChanStreamResult(hlsUrl = m3u8Matcher.group(0) ?: "")
                }

                val vdPattern = Pattern.compile("https?://(?:vd\\d+\\.okcdn\\.ru|[^\"'<>]*okcdn\\.ru)/[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE)
                val vdMatcher = vdPattern.matcher(html)
                if (vdMatcher.find()) {
                    return@withContext ShinChanStreamResult(hlsUrl = vdMatcher.group(0) ?: "")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private fun extractVideoId(url: String): String? {
        val pattern = Pattern.compile("/video(?:embed)?/(\\d+)", Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(url)
        if (matcher.find()) {
            return matcher.group(1)
        }
        if (url.all { it.isDigit() }) {
            return url
        }
        return null
    }

    private suspend fun fetchHtml(url: String, referer: String? = null): String = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .addHeader("Accept-Language", "en-US,en;q=0.9")
            if (referer != null) {
                builder.addHeader("Referer", referer)
            }
            client.newCall(builder.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    return@withContext resp.body?.string() ?: ""
                }
            }
        } catch (_: Exception) {}
        return@withContext ""
    }
}
