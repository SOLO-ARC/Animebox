package com.lagradost.cloudstream3.ui.animebox.extractors
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ExtractedStream(
    val url: String,
    val referer: String,
    val quality: String = "auto",
    val provider: String = "yuki",
    val subtitles: List<Map<String, String>> = emptyList(),
    val introStart: Long = 0L,
    val introEnd: Long = 0L,
    val outroStart: Long = 0L,
    val outroEnd: Long = 0L,
    val backupUrl: String = "",
    val backupProvider: String = ""
)

object AnimexExtractor {
    private val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val slugCache = java.util.concurrent.ConcurrentHashMap<Int, String>()

    private suspend fun getOrResolveSlug(anilistId: Int): String? {
        slugCache[anilistId]?.let { return it }
        val s = resolveSlug(anilistId)
        if (s != null) slugCache[anilistId] = s
        return s
    }

    private const val ANIMEX_WATCH = "https://animex.one/watch"
    private const val ANIMEX_API = "https://pp.animex.one/rest/api"
    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val BROWSER_HEADERS = mapOf(
        "User-Agent" to UA,
        "Referer" to "https://animex.one/",
        "Origin" to "https://animex.one",
        "Accept" to "application/json, text/plain, */*"
    )

    private suspend fun resolveSlug(anilistId: Int): String? = withContext(Dispatchers.IO) {
        val pathCandidates = listOf(
            "$ANIMEX_WATCH/-$anilistId-episode-1/__data.json",
            "$ANIMEX_WATCH/-$anilistId/__data.json",
            "$ANIMEX_WATCH/-$anilistId-episode-0/__data.json"
        )

        for (url in pathCandidates) {
            try {
                val reqBuilder = Request.Builder().url(url)
                BROWSER_HEADERS.forEach { (k, v) -> reqBuilder.addHeader(k, v) }
                
                client.newCall(reqBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bodyStr = response.body?.string() ?: return@use
                    val firstLine = bodyStr.split("\n")[0]
                    val json = JSONObject(firstLine)
                    val nodes = json.optJSONArray("nodes") ?: return@use

                    for (i in 0 until nodes.length()) {
                        val node = nodes.optJSONObject(i) ?: continue
                        if (node.optString("type") == "data" && node.has("data")) {
                            val flat = node.getJSONArray("data")
                            if (flat.length() == 0) continue
                            
                            for (j in 0 until flat.length()) {
                                val obj = flat.optJSONObject(j) ?: continue
                                val slugVal = obj.opt("slug")
                                if (slugVal is String && slugVal.isNotEmpty()) {
                                    return@withContext slugVal
                                } else if (slugVal is Number) {
                                    val sIdx = slugVal.toInt()
                                    if (sIdx in 0 until flat.length()) {
                                        val s = flat.optString(sIdx, "")
                                        if (s.isNotEmpty()) return@withContext s
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // try next candidate
            }
        }
        return@withContext null
    }

    private suspend fun fetchSource(slug: String, epNum: Int, type: String, providerId: String): JSONObject? = withContext(Dispatchers.IO) {
        val epCandidates = if (epNum == 1) listOf(1, 0) else listOf(epNum)
        for (ep in epCandidates) {
            try {
                val url = "$ANIMEX_API/sources?id=${java.net.URLEncoder.encode(slug, "UTF-8")}&epNum=$ep&type=$type&providerId=$providerId"
                val reqBuilder = Request.Builder().url(url)
                BROWSER_HEADERS.forEach { (k, v) -> reqBuilder.addHeader(k, v) }

                client.newCall(reqBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val bodyStr = response.body?.string() ?: return@use
                    val json = JSONObject(bodyStr)
                    if (json.optBoolean("error", false)) return@use
                    if (json.has("sources") && json.getJSONArray("sources").length() > 0) {
                        return@withContext json
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return@withContext null
    }

    suspend fun extractServer(anilistId: Int, episode: Int, type: String = "sub", providerId: String): ExtractedStream? = withContext(Dispatchers.IO) {
        val slug = getOrResolveSlug(anilistId) ?: return@withContext null
        var data = fetchSource(slug, episode, type, providerId)
        if (data == null || !data.has("sources") || data.getJSONArray("sources").length() == 0) {
            if (type == "sub") {
                data = fetchSource(slug, episode, "raw", providerId) ?: fetchSource(slug, episode, "dub", providerId)
            }
        }
        if (data == null || !data.has("sources")) return@withContext null
        val sources = data.getJSONArray("sources")
        if (sources.length() == 0) return@withContext null

        var selectedHls = ""
        var quality = "auto"
        for (i in 0 until sources.length()) {
            val s = sources.getJSONObject(i)
            val u = s.optString("url", s.optString("file", s.optString("m3u8", s.optString("stream", ""))))
            val t = s.optString("type", "")
            if (u.contains(".m3u8") || t.contains("mpegurl") || u.startsWith("http")) {
                selectedHls = u
                quality = s.optString("quality", "auto")
                break
            }
        }
        if (selectedHls.isEmpty() && sources.length() > 0) {
            selectedHls = sources.getJSONObject(0).optString("url", sources.getJSONObject(0).optString("file", ""))
        }
        if (selectedHls.isEmpty()) return@withContext null

        val headers = data.optJSONObject("headers")
        var referer = headers?.optString("Referer", headers.optString("referer", "")) ?: ""
        if (referer.isEmpty()) {
            referer = headers?.optString("Origin", headers.optString("origin", "")) ?: ""
        }
        if (referer.isEmpty()) {
            if (selectedHls.contains("mewstream") || selectedHls.contains("megaplay") || selectedHls.contains("yuki")) {
                referer = "https://megaplay.buzz/"
            } else if (selectedHls.contains("vivibebe")) {
                referer = "https://vivibebe.site/"
            } else if (selectedHls.contains("anidb") || selectedHls.contains("animex")) {
                referer = "https://animex.one/"
            } else {
                try {
                    val uri = java.net.URI.create(selectedHls)
                    referer = "${uri.scheme}://${uri.host}/"
                } catch (e: Exception) {
                    referer = "https://animex.one/"
                }
            }
        }

        val subtitleList = mutableListOf<Map<String, String>>()
        if (data.has("tracks") && !data.isNull("tracks")) {
            val tracks = data.getJSONArray("tracks")
            for (i in 0 until tracks.length()) {
                val track = tracks.getJSONObject(i)
                val lang = track.optString("lang", track.optString("label", ""))
                val url = track.optString("url", track.optString("file", ""))
                if (url.isNotEmpty()) {
                    subtitleList.add(mapOf("lang" to lang, "url" to url))
                }
            }
        }

        var introStart = 0L; var introEnd = 0L
        var outroStart = 0L; var outroEnd = 0L
        if (data.has("chapters") && !data.isNull("chapters")) {
            val chapters = data.getJSONArray("chapters")
            for (i in 0 until chapters.length()) {
                val ch = chapters.getJSONObject(i)
                val title = ch.optString("title", "").lowercase()
                if (title == "intro") {
                    introStart = ch.optLong("start", 0L)
                    introEnd = ch.optLong("end", 0L)
                } else if (title == "outro") {
                    outroStart = ch.optLong("start", 0L)
                    outroEnd = ch.optLong("end", 0L)
                }
            }
        }

        return@withContext ExtractedStream(
            url = selectedHls,
            referer = referer,
            quality = quality,
            provider = providerId,
            subtitles = subtitleList,
            introStart = introStart,
            introEnd = introEnd,
            outroStart = outroStart,
            outroEnd = outroEnd
        )
    }

    suspend fun extractKiwiServer(anilistId: Int, episode: Int, type: String = "dub"): ExtractedStream? {
        return extractServer(anilistId, episode, type, "kiwi")
    }

    suspend fun extractPeweServer(anilistId: Int, episode: Int, type: String = "dub"): ExtractedStream? {
        return extractServer(anilistId, episode, type, "pewe")
    }

    suspend fun extractBeepServer(anilistId: Int, episode: Int, type: String = "sub"): ExtractedStream? {
        return extractServer(anilistId, episode, type, "beep")
    }

    suspend fun extract(anilistId: Int, episode: Int, type: String = "sub"): ExtractedStream? {
        val slug = getOrResolveSlug(anilistId) ?: return null

        val primaryProviders = if (type == "sub") {
            listOf("yuki", "mimi", "beep", "kiwi", "pewe", "zoro")
        } else {
            listOf("kiwi", "pewe", "beep", "yuki", "mimi", "zoro", "bee", "gogoanime", "animepahe", "hianime")
        }
        var primaryData: JSONObject? = null
        var primaryProviderName = ""

        for (p in primaryProviders) {
            val data = fetchSource(slug, episode, type, p)
            if (data != null && data.has("sources") && data.getJSONArray("sources").length() > 0) {
                primaryData = data
                primaryProviderName = p
                break
            }
        }

        var backupData: JSONObject? = null
        var backupProviderName = ""

        if (primaryData == null) {
            val backupProviders = if (type == "sub") {
                listOf("zoro", "bee", "gogoanime", "animepahe", "hianime", "kiwi", "pewe")
            } else {
                listOf("pewe", "kiwi", "beep", "zoro", "bee", "gogoanime", "animepahe", "hianime", "yuki", "mimi")
            }
            for (p in backupProviders) {
                val data = fetchSource(slug, episode, type, p)
                if (data != null && data.has("sources") && data.getJSONArray("sources").length() > 0) {
                    backupData = data
                    backupProviderName = p
                    break
                }
            }
        }

        val primaryJson = primaryData ?: backupData ?: return null
        val primaryProvider = if (primaryData != null) primaryProviderName else backupProviderName

        val sources = primaryJson.getJSONArray("sources")
        var selectedHls = ""
        var quality = "auto"

        for (i in 0 until sources.length()) {
            val s = sources.getJSONObject(i)
            val u = s.optString("url", s.optString("file", s.optString("m3u8", s.optString("stream", ""))))
            val t = s.optString("type", "")
            if (u.contains(".m3u8") || t.contains("mpegurl") || u.startsWith("http")) {
                selectedHls = u
                quality = s.optString("quality", "auto")
                break
            }
        }

        if (selectedHls.isEmpty() && sources.length() > 0) {
            val s0 = sources.getJSONObject(0)
            selectedHls = s0.optString("url", s0.optString("file", s0.optString("m3u8", s0.optString("stream", ""))))
        }

        if (selectedHls.isEmpty()) return null

        val headers = primaryJson.optJSONObject("headers")
        var referer = headers?.optString("Referer", headers.optString("referer", "")) ?: ""
        if (referer.isEmpty()) {
            referer = headers?.optString("Origin", headers.optString("origin", "")) ?: ""
        }
        if (referer.isEmpty()) {
            if (selectedHls.contains("mewstream") || selectedHls.contains("megaplay") || selectedHls.contains("yuki")) {
                referer = "https://megaplay.buzz/"
            } else if (selectedHls.contains("vivibebe")) {
                referer = "https://vivibebe.site/"
            } else if (selectedHls.contains("anidb") || selectedHls.contains("animex")) {
                referer = "https://animex.one/"
            } else {
                try {
                    val uri = java.net.URI.create(selectedHls)
                    referer = "${uri.scheme}://${uri.host}/"
                } catch (e: Exception) {
                    referer = "https://animex.one/"
                }
            }
        }

        val subtitleList = mutableListOf<Map<String, String>>()
        if (primaryJson.has("tracks") && !primaryJson.isNull("tracks")) {
            val tracks = primaryJson.getJSONArray("tracks")
            for (i in 0 until tracks.length()) {
                val track = tracks.getJSONObject(i)
                val lang = track.optString("lang", track.optString("label", ""))
                val url = track.optString("url", "")
                if (url.isNotEmpty()) {
                    subtitleList.add(mapOf("lang" to lang, "url" to url))
                }
            }
        }

        var introStart = 0L; var introEnd = 0L
        var outroStart = 0L; var outroEnd = 0L

        if (primaryJson.has("chapters") && !primaryJson.isNull("chapters")) {
            val chapters = primaryJson.getJSONArray("chapters")
            for (i in 0 until chapters.length()) {
                val ch = chapters.getJSONObject(i)
                val title = ch.optString("title", "").lowercase()
                if (title == "intro") {
                    introStart = ch.optLong("start", 0L)
                    introEnd = ch.optLong("end", 0L)
                } else if (title == "outro") {
                    outroStart = ch.optLong("start", 0L)
                    outroEnd = ch.optLong("end", 0L)
                }
            }
        }

        var backupHls = ""
        if (primaryData != null && backupData == null) {
            val remainingBackup = (primaryProviders + listOf("zoro", "bee", "gogoanime", "animepahe", "hianime")).filter { it != primaryProviderName }
            for (p in remainingBackup) {
                val bJson = fetchSource(slug, episode, type, p)
                if (bJson != null && bJson.has("sources")) {
                    val bSrcs = bJson.getJSONArray("sources")
                    if (bSrcs.length() > 0) {
                        val bUrl = bSrcs.getJSONObject(0).optString("url", "")
                        if (bUrl.isNotEmpty()) {
                            backupHls = bUrl
                            backupProviderName = p
                            break
                        }
                    }
                }
            }
        }

        return ExtractedStream(
            url = selectedHls,
            referer = referer,
            quality = quality,
            provider = primaryProvider,
            subtitles = subtitleList,
            introStart = introStart,
            introEnd = introEnd,
            outroStart = outroStart,
            outroEnd = outroEnd,
            backupUrl = backupHls,
            backupProvider = backupProviderName
        )
    }

    suspend fun extractAllDubStreams(anilistId: Int, episode: Int): List<ExtractedStream> {
        val slug = getOrResolveSlug(anilistId) ?: return emptyList()
        val providers = listOf("kiwi", "pewe", "beep", "yuki", "mimi", "zoro", "bee", "gogoanime", "animepahe", "hianime")
        val results = mutableListOf<ExtractedStream>()

        for (p in providers) {
            val data = fetchSource(slug, episode, "dub", p) ?: continue
            if (!data.has("sources")) continue
            val sources = data.getJSONArray("sources")
            if (sources.length() == 0) continue

            var selectedHls = ""
            var quality = "auto"
            for (i in 0 until sources.length()) {
                val s = sources.getJSONObject(i)
                val u = s.optString("url", "")
                val t = s.optString("type", "")
                if (u.contains(".m3u8") || t.contains("mpegurl")) {
                    selectedHls = u
                    quality = s.optString("quality", "auto")
                    break
                }
            }
            if (selectedHls.isEmpty() && sources.length() > 0) {
                selectedHls = sources.getJSONObject(0).optString("url", "")
            }
            if (selectedHls.isEmpty()) continue

            val headers = data.optJSONObject("headers")
            var referer = headers?.optString("Referer", headers.optString("referer", "")) ?: ""
            if (referer.isEmpty()) {
                if (selectedHls.contains("mewstream") || selectedHls.contains("megaplay") || selectedHls.contains("yuki")) {
                    referer = "https://megaplay.buzz/"
                } else if (selectedHls.contains("vivibebe")) {
                    referer = "https://vivibebe.site/"
                } else {
                    try {
                        val uri = java.net.URI.create(selectedHls)
                        referer = "${uri.scheme}://${uri.host}/"
                    } catch (e: Exception) {
                        referer = "https://megaplay.buzz/"
                    }
                }
            }

            val subtitleList = mutableListOf<Map<String, String>>()
            if (data.has("tracks") && !data.isNull("tracks")) {
                val tracks = data.getJSONArray("tracks")
                for (i in 0 until tracks.length()) {
                    val track = tracks.getJSONObject(i)
                    val lang = track.optString("lang", track.optString("label", ""))
                    val url = track.optString("url", "")
                    if (url.isNotEmpty()) {
                        subtitleList.add(mapOf("lang" to lang, "url" to url))
                    }
                }
            }

            var introStart = 0L; var introEnd = 0L
            var outroStart = 0L; var outroEnd = 0L
            if (data.has("chapters") && !data.isNull("chapters")) {
                val chapters = data.getJSONArray("chapters")
                for (i in 0 until chapters.length()) {
                    val ch = chapters.getJSONObject(i)
                    val title = ch.optString("title", "").lowercase()
                    if (title == "intro") {
                        introStart = ch.optLong("start", 0L)
                        introEnd = ch.optLong("end", 0L)
                    } else if (title == "outro") {
                        outroStart = ch.optLong("start", 0L)
                        outroEnd = ch.optLong("end", 0L)
                    }
                }
            }

            results.add(
                ExtractedStream(
                    url = selectedHls,
                    referer = referer,
                    quality = quality,
                    provider = "animex ($p)",
                    subtitles = subtitleList,
                    introStart = introStart,
                    introEnd = introEnd,
                    outroStart = outroStart,
                    outroEnd = outroEnd
                )
            )
        }
        return results
    }
}
