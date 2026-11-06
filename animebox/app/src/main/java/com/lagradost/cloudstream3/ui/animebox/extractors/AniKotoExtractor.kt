package com.lagradost.cloudstream3.ui.animebox.extractors
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object AniKotoExtractor {
    private val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private val showIdCache = java.util.concurrent.ConcurrentHashMap<Int, Pair<String, String>>()

    private const val ANIKOTO = "https://anikototv.to"
    private const val ANIZIP = "https://api.ani.zip/mappings"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    private const val SPOOF_REF = "https://hianimes.re/"

    private suspend fun getAniZipTitles(anilistId: Int): List<String> = withContext(Dispatchers.IO) {
        val titles = mutableListOf<String>()
        try {
            val req = Request.Builder().url("$ANIZIP?anilist_id=$anilistId").build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val str = resp.body?.string() ?: ""
                    if (str.isNotEmpty()) {
                        val json = JSONObject(str)
                        if (json.has("titles")) {
                            val tObj = json.getJSONObject("titles")
                            tObj.keys().forEach { k ->
                                val v = tObj.optString(k, "")
                                if (v.isNotEmpty() && !titles.contains(v)) {
                                    titles.add(v)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext titles
    }

    private suspend fun searchAnikotoSlug(query: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "$ANIKOTO/ajax/anime/search?keyword=${URLEncoder.encode(query, "UTF-8")}"
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", UA)
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("Referer", "$ANIKOTO/")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val str = resp.body?.string() ?: ""
                    val json = JSONObject(str)
                    val html = json.optJSONObject("result")?.optString("html", "") ?: json.optString("result", "")
                    val match = Regex("""href="https://anikototv\.to/watch/([^"]+)"""").find(html)
                    if (match != null) {
                        return@withContext match.groupValues[1]
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private suspend fun getShowId(slug: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "$ANIKOTO/watch/$slug"
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", UA)
                .addHeader("Referer", "$ANIKOTO/")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val match = Regex("""data-id="(\d+)"""").find(html)
                    if (match != null) {
                        return@withContext match.groupValues[1]
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private suspend fun getEpisodeIds(showId: String, slug: String, epNum: Int): String? = withContext(Dispatchers.IO) {
        try {
            val url = "$ANIKOTO/ajax/episode/list/$showId"
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", UA)
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("Referer", "$ANIKOTO/watch/$slug")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val str = resp.body?.string() ?: ""
                    val json = JSONObject(str)
                    val html = json.optJSONObject("result")?.optString("html", "") ?: json.optString("result", "")
                    
                    val tags = Regex("""<a\s[^>]*data-id="[^"]*"[^>]*>""").findAll(html)
                    for (t in tags) {
                        val tag = t.value
                        val idMatch = Regex("""data-id="([^"]*)"""").find(tag)?.groupValues?.get(1) ?: ""
                        val numMatch = Regex("""data-num="([^"]*)"""").find(tag)?.groupValues?.get(1) ?: ""
                        val idsMatch = Regex("""data-ids="([^"]*)"""").find(tag)?.groupValues?.get(1) ?: ""
                        
                        val num = numMatch.toIntOrNull() ?: -1
                        if (num == epNum || (epNum == 1 && (num <= 1 || tags.count() == 1))) {
                            return@withContext if (idsMatch.isNotEmpty()) idsMatch else idMatch
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private suspend fun getServerLinkIds(epIds: String, category: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val linkIds = mutableListOf<Pair<String, String>>()
        try {
            val url = "$ANIKOTO/ajax/server/list?servers=${URLEncoder.encode(epIds, "UTF-8")}"
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", UA)
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("Referer", "$ANIKOTO/")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val str = resp.body?.string() ?: ""
                    val json = JSONObject(str)
                    val html = json.optJSONObject("result")?.optString("html", "") ?: json.optString("result", "")
                    
                    val typeBlocks = Regex("""<div class="type" data-type="(sub|dub)">([\s\S]*?)</ul>\s*</div>""").findAll(html)
                    for (block in typeBlocks) {
                        val typeCat = block.groupValues[1]
                        if (typeCat != category) continue
                        val content = block.groupValues[2]
                        
                        val liMatches = Regex("""<li\s+([^>]*data-link-id[^>]*)>([\s\S]*?)</li>""").findAll(content)
                        for (li in liMatches) {
                            val linkId = Regex("""data-link-id="([^"]+)"""").find(li.groupValues[1])?.groupValues?.get(1) ?: ""
                            val name = li.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
                            if (linkId.isNotEmpty()) {
                                linkIds.add(linkId to name)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext linkIds
    }

    private suspend fun getEmbedUrl(linkId: String): String? = withContext(Dispatchers.IO) {
        try {
            val url = "$ANIKOTO/ajax/server?get=${URLEncoder.encode(linkId, "UTF-8")}"
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", UA)
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .addHeader("Referer", "$ANIKOTO/")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val str = resp.body?.string() ?: ""
                    val json = JSONObject(str)
                    val resultObj = json.optJSONObject("result")
                    return@withContext resultObj?.optString("url", "")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private suspend fun extractSourcesFromEmbed(embedUrl: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val origin = try {
                val uri = java.net.URI.create(embedUrl)
                "${uri.scheme}://${uri.host}"
            } catch (e: Exception) { "https://megaplay.buzz" }

            val embedReq = Request.Builder()
                .url(embedUrl)
                .addHeader("User-Agent", UA)
                .addHeader("Referer", SPOOF_REF)
                .addHeader("Accept-Language", "en-US,en;q=0.9")
                .build()

            var fileId = ""
            client.newCall(embedReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val match = Regex("""data-id="([^"]*)"""").find(html)
                    if (match != null) {
                        fileId = match.groupValues[1]
                    }
                }
            }

            if (fileId.isEmpty()) return@withContext null

            val sourcesUrl = "$origin/stream/getSources?id=$fileId&id=$fileId"
            val sourcesReq = Request.Builder()
                .url(sourcesUrl)
                .addHeader("User-Agent", UA)
                .addHeader("Referer", "$origin/")
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .build()

            client.newCall(sourcesReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val str = resp.body?.string() ?: ""
                    if (str.isNotEmpty()) return@withContext JSONObject(str)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    suspend fun extractAniKoto(anilistId: Int, episodeNum: Int, audio: String = "sub"): ScraperStreamResult? = withContext(Dispatchers.IO) {
        val list = extractAniKotoAll(anilistId, episodeNum, audio)
        return@withContext list.firstOrNull()
    }

    suspend fun extractAniKotoAll(anilistId: Int, episodeNum: Int, audio: String = "sub"): List<ScraperStreamResult> = withContext(Dispatchers.IO) {
        var cached = showIdCache[anilistId]
        var foundSlug: String? = cached?.first
        var showId: String? = cached?.second

        if (foundSlug == null || showId == null) {
            val titles = getAniZipTitles(anilistId)
            if (titles.isEmpty()) return@withContext emptyList()

            for (t in titles) {
                val s = searchAnikotoSlug(t)
                if (s != null) {
                    foundSlug = s
                    break
                }
            }

            if (foundSlug == null) return@withContext emptyList()
            showId = getShowId(foundSlug) ?: return@withContext emptyList()
            showIdCache[anilistId] = Pair(foundSlug, showId)
        }

        val epIds = getEpisodeIds(showId, foundSlug, episodeNum) ?: return@withContext emptyList()
        val serverLinks = getServerLinkIds(epIds, audio)

        val results = mutableListOf<ScraperStreamResult>()

        for (linkPair in serverLinks) {
            val linkId = linkPair.first
            val serverName = linkPair.second
            val embedUrl = getEmbedUrl(linkId) ?: continue

            val sourcesData = extractSourcesFromEmbed(embedUrl)
            if (sourcesData != null) {
                var hlsUrl = ""
                val sourcesArr = sourcesData.optJSONArray("sources")
                if (sourcesArr != null && sourcesArr.length() > 0) {
                    for (i in 0 until sourcesArr.length()) {
                        val s = sourcesArr.getJSONObject(i)
                        val file = s.optString("file", s.optString("url", ""))
                        if (file.contains(".m3u8")) {
                            hlsUrl = file
                            break
                        }
                    }
                    if (hlsUrl.isEmpty()) {
                        hlsUrl = sourcesArr.getJSONObject(0).optString("file", sourcesArr.getJSONObject(0).optString("url", ""))
                    }
                } else if (sourcesData.has("sources")) {
                    val sObj = sourcesData.optJSONObject("sources")
                    hlsUrl = sObj?.optString("file", sObj.optString("url", "")) ?: ""
                }

                if (hlsUrl.isNotEmpty()) {
                    val origin = try {
                        val uri = java.net.URI.create(embedUrl)
                        "${uri.scheme}://${uri.host}/"
                    } catch (e: Exception) { "https://megaplay.buzz/" }

                    val subsList = mutableListOf<Map<String, String>>()
                    val tracks = sourcesData.optJSONArray("tracks")
                    if (tracks != null) {
                        for (i in 0 until tracks.length()) {
                            val tr = tracks.getJSONObject(i)
                            val f = tr.optString("file", "")
                            val l = tr.optString("label", "")
                            if (f.isNotEmpty()) {
                                subsList.add(mapOf("lang" to l, "url" to f))
                            }
                        }
                    }

                    var introStart = 0L; var introEnd = 0L
                    var outroStart = 0L; var outroEnd = 0L
                    val introObj = sourcesData.optJSONObject("intro")
                    if (introObj != null) {
                        introStart = introObj.optLong("start", 0L)
                        introEnd = introObj.optLong("end", 0L)
                    }
                    val outroObj = sourcesData.optJSONObject("outro")
                    if (outroObj != null) {
                        outroStart = outroObj.optLong("start", 0L)
                        outroEnd = outroObj.optLong("end", 0L)
                    }

                    results.add(
                        ScraperStreamResult(
                            provider = "anikoto ($serverName)",
                            hlsUrl = hlsUrl,
                            referer = origin,
                            subtitles = subsList,
                            introStart = introStart,
                            introEnd = introEnd,
                            outroStart = outroStart,
                            outroEnd = outroEnd
                        )
                    )
                }
            }
        }
        return@withContext results
    }
}
