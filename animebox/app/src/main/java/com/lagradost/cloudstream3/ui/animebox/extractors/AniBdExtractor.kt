package com.lagradost.cloudstream3.ui.animebox.extractors
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class AniBdResult(
    val hlsUrl: String,
    val referer: String = "https://playeng.animeapps.top/",
    val subtitleUrl: String = "",
    val subtitles: List<Map<String, String>> = emptyList(),
    val provider: String = "SR"
)

object AniBdExtractor {
    private val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private const val API2_HOST = "https://epeng.animeapps.top"
    private const val PLAYENG_HOST = "https://playeng.animeapps.top"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    suspend fun extract(anilistId: Int, episodeNum: Int): AniBdResult? = withContext(Dispatchers.IO) {
        if (anilistId <= 0 || episodeNum <= 0) return@withContext null

        try {
            // 1. Fetch server list from api2.php
            val api2Url = "$API2_HOST/api2.php?epid=$anilistId"
            val api2Req = Request.Builder()
                .url(api2Url)
                .addHeader("User-Agent", UA)
                .addHeader("Referer", "https://anibd.app/")
                .addHeader("Origin", "https://anibd.app")
                .build()

            var api2Body = ""
            client.newCall(api2Req).execute().use { resp ->
                if (resp.isSuccessful) {
                    api2Body = resp.body?.string() ?: ""
                }
            }

            if (api2Body.isEmpty()) return@withContext null
            val serversArray = JSONArray(api2Body)
            if (serversArray.length() == 0) return@withContext null

            // Search across servers for matching episode
            var dataKey = ""
            for (sIdx in 0 until serversArray.length()) {
                val sObj = serversArray.optJSONObject(sIdx) ?: continue
                val sData = sObj.optJSONArray("server_data") ?: continue

                for (eIdx in 0 until sData.length()) {
                    val epObj = sData.optJSONObject(eIdx) ?: continue
                    val name = epObj.optString("name", "").trim()
                    val slug = epObj.optString("slug", "").trim()
                    val link = epObj.optString("link", "").trim()

                    val nameInt = name.toIntOrNull()
                    val slugInt = slug.toIntOrNull()
                    if (nameInt == episodeNum || slugInt == episodeNum ||
                        name == episodeNum.toString() || slug == episodeNum.toString() ||
                        name == String.format("%02d", episodeNum) || slug == String.format("%02d", episodeNum)
                    ) {
                        dataKey = link
                        break
                    }
                }
                if (dataKey.isEmpty() && episodeNum - 1 in 0 until sData.length()) {
                    val epObj = sData.optJSONObject(episodeNum - 1)
                    dataKey = epObj?.optString("link", "") ?: ""
                }
                if (dataKey.isNotEmpty()) break
            }

            if (dataKey.isEmpty()) return@withContext null

            // 2. Fetch server links from apilink.php
            val linkUrl = "$API2_HOST/apilink.php?data=$dataKey"
            val linkReq = Request.Builder()
                .url(linkUrl)
                .addHeader("User-Agent", UA)
                .addHeader("Referer", "https://anibd.app/")
                .addHeader("Origin", "https://anibd.app")
                .build()

            var linkBody = ""
            client.newCall(linkReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    linkBody = resp.body?.string() ?: ""
                }
            }

            if (linkBody.isEmpty()) return@withContext null
            val linksArray = JSONArray(linkBody)
            if (linksArray.length() == 0) return@withContext null

            // Select "SR" server link (preferred) or fallback to "SB" or first item
            var embedUrl = ""
            var serverName = "SR"
            for (i in 0 until linksArray.length()) {
                val lObj = linksArray.optJSONObject(i) ?: continue
                val sType = lObj.optString("server", "")
                val lUrl = lObj.optString("link", "")
                if (sType.equals("SR", ignoreCase = true) && lUrl.isNotEmpty()) {
                    embedUrl = lUrl
                    serverName = sType
                    break
                }
            }
            if (embedUrl.isEmpty() && linksArray.length() > 0) {
                val firstObj = linksArray.optJSONObject(0)
                embedUrl = firstObj?.optString("link", "") ?: ""
                serverName = firstObj?.optString("server", "SR") ?: "SR"
            }

            if (embedUrl.isEmpty()) return@withContext null

            // 3. Fetch embed page HTML
            val embedReq = Request.Builder()
                .url(embedUrl)
                .addHeader("User-Agent", UA)
                .addHeader("Referer", "https://anibd.app/")
                .build()

            var embedHtml = ""
            client.newCall(embedReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    embedHtml = resp.body?.string() ?: ""
                }
            }

            if (embedHtml.isEmpty()) return@withContext null

            // 4. Extract videoUrl (.m3u8)
            var videoUrl = ""
            val videoMatch = Pattern.compile("""videoUrl:\s*["']([^"']+)["']""").matcher(embedHtml)
            if (videoMatch.find()) {
                videoUrl = videoMatch.group(1) ?: ""
            }
            if (videoUrl.isEmpty()) {
                val m3u8Match = Pattern.compile("""["']([^"']+\.m3u8[^"']*)["']""").matcher(embedHtml)
                if (m3u8Match.find()) {
                    videoUrl = m3u8Match.group(1) ?: ""
                }
            }

            if (videoUrl.isEmpty()) return@withContext null
            val fullHlsUrl = if (videoUrl.startsWith("http")) videoUrl else "$PLAYENG_HOST$videoUrl"

            // 5. Extract subtitle tracks
            val subtitlesList = mutableListOf<Map<String, String>>()
            val tracksMatch = Pattern.compile("""tracks:\s*(\[[^\]]+\])""").matcher(embedHtml)
            if (tracksMatch.find()) {
                try {
                    val tracksArr = JSONArray(tracksMatch.group(1) ?: "[]")
                    for (t in 0 until tracksArr.length()) {
                        val tObj = tracksArr.optJSONObject(t) ?: continue
                        val file = tObj.optString("file", tObj.optString("url", ""))
                        val label = tObj.optString("label", "English")
                        val isDefault = tObj.optBoolean("default", true)
                        if (file.isNotEmpty()) {
                            val fullSub = if (file.startsWith("http")) file else "$PLAYENG_HOST$file"
                            subtitlesList.add(
                                mapOf(
                                    "url" to fullSub,
                                    "lang" to label,
                                    "default" to isDefault.toString()
                                )
                            )
                        }
                    }
                } catch (_: Exception) {}
            }

            if (subtitlesList.isEmpty()) {
                val vttMatch = Pattern.compile("""file:\s*["']([^"']+\.vtt[^"']*)["']""").matcher(embedHtml)
                if (vttMatch.find()) {
                    val rawVtt = vttMatch.group(1) ?: ""
                    if (rawVtt.isNotEmpty()) {
                        val fullSub = if (rawVtt.startsWith("http")) rawVtt else "$PLAYENG_HOST$rawVtt"
                        subtitlesList.add(
                            mapOf(
                                "url" to fullSub,
                                "lang" to "English",
                                "default" to "true"
                            )
                        )
                    }
                }
            }

            val defaultSubtitle = subtitlesList.firstOrNull()?.get("url") ?: ""

            return@withContext AniBdResult(
                hlsUrl = fullHlsUrl,
                referer = "$PLAYENG_HOST/",
                subtitleUrl = defaultSubtitle,
                subtitles = subtitlesList,
                provider = serverName
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
