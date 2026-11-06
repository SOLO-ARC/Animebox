package com.lagradost.cloudstream3.ui.animebox.api
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import android.util.Log
import com.lagradost.cloudstream3.ui.animebox.extractors.ByseExtractor
import com.lagradost.cloudstream3.ui.animebox.extractors.ShinChanOkRuExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class CustomStreamEntry(
    val id: String,
    val anilistId: Int,
    val episodeNumber: Int,
    val language: String,
    val serverName: String,
    val videoUrl: String,
    val title: String?,
    val quality: String,
    val isActive: Boolean
)

object CustomStreamManager {

    private const val TAG = "CustomStreamManager"
    private val SUPABASE_URL get() = AnimeBoxConfig.SUPABASE_URL
    private val SUPABASE_KEY get() = AnimeBoxConfig.SUPABASE_ANON_KEY

    private val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    // In-memory cache: "anilistId_episodeNum" -> List<CustomStreamEntry>
    private val streamCache = ConcurrentHashMap<String, List<CustomStreamEntry>>()

    fun hasCustomStreamCached(anilistId: Int, episodeNum: Int): Boolean {
        if (anilistId <= 0) return false
        val targetEp = if (episodeNum <= 0) 1 else episodeNum
        return streamCache["${anilistId}_${targetEp}"]?.isNotEmpty() == true ||
            streamCache["${anilistId}_1"]?.isNotEmpty() == true ||
            streamCache["${anilistId}_0"]?.isNotEmpty() == true
    }

    /**
     * Checks if a custom stream exists in Supabase for the given anime and episode.
     */
    suspend fun hasCustomStream(
        anilistId: Int,
        episodeNum: Int
    ): Boolean = withContext(Dispatchers.IO) {
        if (anilistId <= 0) return@withContext false
        try {
            val targetEp = if (episodeNum <= 0) 1 else episodeNum
            var candidateStreams = getCustomStreamsForEpisode(anilistId, targetEp)
            if (candidateStreams.isEmpty() && targetEp != 1) {
                candidateStreams = getCustomStreamsForEpisode(anilistId, 1)
            }
            if (candidateStreams.isEmpty() && targetEp != 0) {
                candidateStreams = getCustomStreamsForEpisode(anilistId, 0)
            }
            return@withContext candidateStreams.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Resolves an extracted stream map if a matching manual stream exists in Supabase.
     */
    suspend fun resolveCustomStream(
        anilistId: Int,
        episodeNum: Int,
        streamType: String,
        animeTitle: String? = null
    ): Map<String, Any>? = withContext(Dispatchers.IO) {
        try {
            val targetEp = if (episodeNum <= 0) 1 else episodeNum
            var candidateStreams = getCustomStreamsForEpisode(anilistId, targetEp)
            // For movies or if not found on targetEp, also query episode 1 as standard fallback
            if (candidateStreams.isEmpty() && targetEp != 1) {
                candidateStreams = getCustomStreamsForEpisode(anilistId, 1)
            }
            if (candidateStreams.isEmpty() && targetEp != 0) {
                candidateStreams = getCustomStreamsForEpisode(anilistId, 0)
            }
            if (candidateStreams.isEmpty()) return@withContext null

            // Find best matching stream for the requested language/streamType
            val selectedStream = matchStreamByLanguage(candidateStreams, streamType) ?: candidateStreams.firstOrNull()
            if (selectedStream == null || selectedStream.videoUrl.isBlank()) return@withContext null

            Log.i(TAG, "Found custom stream for Anime $anilistId Ep $targetEp (Lang: ${selectedStream.language}) -> ${selectedStream.videoUrl}")

            // Auto-detect and extract based on URL
            val extracted = extractStream(selectedStream, anilistId, targetEp, animeTitle)
            if (extracted != null) {
                return@withContext extracted
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving custom stream", e)
        }
        null
    }

    /**
     * Fetches active custom stream entries for an episode from Supabase (with caching).
     */
    private suspend fun getCustomStreamsForEpisode(
        anilistId: Int,
        episodeNum: Int
    ): List<CustomStreamEntry> = withContext(Dispatchers.IO) {
        val cacheKey = "${anilistId}_$episodeNum"
        streamCache[cacheKey]?.let { return@withContext it }

        val entries = mutableListOf<CustomStreamEntry>()
        try {
            val url = "$SUPABASE_URL/rest/v1/custom_streams?anilist_id=eq.$anilistId&episode_number=eq.$episodeNum&is_active=eq.true&order=created_at.desc"
            val req = Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_KEY")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    if (body.isNotBlank()) {
                        val arr = JSONArray(body)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            entries.add(
                                CustomStreamEntry(
                                    id = obj.optString("id", ""),
                                    anilistId = obj.optInt("anilist_id", anilistId),
                                    episodeNumber = obj.optInt("episode_number", episodeNum),
                                    language = obj.optString("language", "Hindi"),
                                    serverName = obj.optString("server_name", "OkRu"),
                                    videoUrl = obj.optString("video_url", "").trim(),
                                    title = if (obj.has("title") && !obj.isNull("title")) obj.getString("title") else null,
                                    quality = obj.optString("quality", "720p"),
                                    isActive = obj.optBoolean("is_active", true)
                                )
                            )
                        }
                    }
                }
            }
            if (entries.isNotEmpty()) {
                streamCache[cacheKey] = entries
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query custom streams from Supabase", e)
        }
        entries
    }

    /**
     * Matches candidate streams against the user's requested streamType / audio.
     */
    private fun matchStreamByLanguage(
        candidates: List<CustomStreamEntry>,
        streamType: String
    ): CustomStreamEntry? {
        val target = streamType.lowercase().trim()

        return when {
            target.contains("hindi") -> {
                candidates.firstOrNull { it.language.equals("Hindi", ignoreCase = true) }
            }
            target.contains("japanese") || target == "sub" || target == "hardsub" -> {
                candidates.firstOrNull { 
                    it.language.equals("Japanese", ignoreCase = true) || 
                    it.language.equals("Sub", ignoreCase = true) 
                }
            }
            target.contains("english") || target == "dub" -> {
                candidates.firstOrNull { 
                    it.language.equals("English", ignoreCase = true) || 
                    it.language.equals("Dub", ignoreCase = true) 
                }
            }
            target.contains("tamil") -> {
                candidates.firstOrNull { it.language.equals("Tamil", ignoreCase = true) }
            }
            target.contains("telugu") -> {
                candidates.firstOrNull { it.language.equals("Telugu", ignoreCase = true) }
            }
            else -> {
                candidates.firstOrNull { it.language.equals(target, ignoreCase = true) }
            }
        } ?: candidates.firstOrNull { it.language.equals("Multi", ignoreCase = true) }
    }

    /**
     * Automatically extracts playable HLS / MP4 streams based on the host URL.
     */
    private suspend fun extractStream(
        entry: CustomStreamEntry,
        anilistId: Int,
        episodeNum: Int,
        animeTitle: String?
    ): Map<String, Any>? = withContext(Dispatchers.IO) {
        val rawUrl = entry.videoUrl
        val lowerUrl = rawUrl.lowercase()

        // 1. OK.ru (m.ok.ru / ok.ru / ok.ru/video/...)
        if (lowerUrl.contains("ok.ru")) {
            val okRes = ShinChanOkRuExtractor.extractShinChanStream(
                anilistId = anilistId,
                episode = episodeNum,
                animeTitle = animeTitle,
                explicitVideoUrl = rawUrl
            )
            if (okRes != null && okRes.hlsUrl.isNotEmpty()) {
                return@withContext mapOf(
                    "hls" to okRes.hlsUrl,
                    "referer" to okRes.referer,
                    "provider" to "dorara",
                    "serverKey" to "dorara",
                    "displayProvider" to "Dorara",
                    "title" to "${entry.language} • Dorara",
                    "quality" to (okRes.quality.ifEmpty { entry.quality }),
                    "isHardSub" to okRes.isHardSub
                )
            }
        }

        // 2. Byse (bysefujedu.com, gn1r5n, mfw, etc.)
        if (ByseExtractor.canExtract(rawUrl) || lowerUrl.contains("byse") || lowerUrl.contains("gn1r5n") || lowerUrl.contains("mfw")) {
            try {
                val byseRes = ByseExtractor.extractFull(rawUrl, referer = rawUrl)
                if (byseRes.hlsUrls.isNotEmpty()) {
                    val validSubs = byseRes.subtitles.filter { sub ->
                        val u = (sub["url"] ?: "").lowercase()
                        val l = (sub["lang"] ?: "").lowercase()
                        !u.contains("thumbnail") && !u.contains("sprite") && !u.contains("xywh=") && !l.contains("thumbnail") && !l.contains("sprite")
                    }
                    val subsJson = if (validSubs.isNotEmpty()) org.json.JSONArray(validSubs).toString() else "[]"
                    val primarySub = validSubs.firstOrNull { it["lang"]?.contains("Eng", true) == true }?.get("url")
                        ?: validSubs.firstOrNull()?.get("url") ?: ""
                    return@withContext mapOf(
                        "hls" to byseRes.hlsUrls[0],
                        "referer" to byseRes.referer,
                        "subtitle" to primarySub,
                        "subtitlesJson" to subsJson,
                        "provider" to "dorara",
                        "serverKey" to "dorara",
                        "displayProvider" to "Dorara",
                        "title" to "${entry.language} • Dorara",
                        "quality" to entry.quality,
                        "isHardSub" to false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract stream from Byse: $rawUrl", e)
            }
        }

        // 3. VidMoly (vidmoly.to / vidmoly.me)
        if (lowerUrl.contains("vidmoly")) {
            val vidmolyHls = extractVidMoly(rawUrl)
            if (!vidmolyHls.isNullOrEmpty()) {
                return@withContext mapOf(
                    "hls" to vidmolyHls,
                    "referer" to "https://vidmoly.to/",
                    "provider" to "dorara",
                    "serverKey" to "dorara",
                    "displayProvider" to "Dorara",
                    "title" to "${entry.language} • Dorara",
                    "quality" to entry.quality
                )
            }
        }

        // 3. Upnshare / Rpmshare
        if (lowerUrl.contains("upnshare") || lowerUrl.contains("rpmshare")) {
            val shareHls = extractGenericShare(rawUrl)
            if (!shareHls.isNullOrEmpty()) {
                return@withContext mapOf(
                    "hls" to shareHls,
                    "referer" to rawUrl,
                    "provider" to "dorara",
                    "serverKey" to "dorara",
                    "displayProvider" to "Dorara",
                    "title" to "${entry.language} • Dorara",
                    "quality" to entry.quality
                )
            }
        }

        // 4. Direct HLS (.m3u8) or MP4
        if (lowerUrl.contains(".m3u8") || lowerUrl.contains(".mp4")) {
            return@withContext mapOf(
                "hls" to rawUrl,
                "referer" to "",
                "provider" to "dorara",
                "serverKey" to "dorara",
                "displayProvider" to "Dorara",
                "title" to "${entry.language} • Dorara",
                "quality" to entry.quality
            )
        }

        // 5. Fallback: Generic HTML video source scan
        val genericFallback = extractGenericShare(rawUrl)
        if (!genericFallback.isNullOrEmpty()) {
            return@withContext mapOf(
                "hls" to genericFallback,
                "referer" to rawUrl,
                "provider" to "dorara",
                "serverKey" to "dorara",
                "displayProvider" to "Dorara",
                "title" to "${entry.language} • Dorara",
                "quality" to entry.quality
            )
        }

        null
    }

    private suspend fun extractVidMoly(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0.0.0 Safari/537.36")
                .addHeader("Referer", "https://vidmoly.to/")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val pattern = Pattern.compile("sources:\\s*\\[\\s*\\{\\s*file:\\s*[\"']([^\"']+\\.m3u8[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
                    val matcher = pattern.matcher(html)
                    if (matcher.find()) {
                        return@withContext matcher.group(1)
                    }
                    val m3u8Pattern = Pattern.compile("https?://[^\\s\"'<>]+?\\.m3u8[^\\s\"'<>]*", Pattern.CASE_INSENSITIVE)
                    val m3u8Matcher = m3u8Pattern.matcher(html)
                    if (m3u8Matcher.find()) {
                        return@withContext m3u8Matcher.group(0)
                    }
                }
            }
        } catch (_: Exception) {}
        null
    }

    private suspend fun extractGenericShare(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/124.0.0.0 Safari/537.36")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val html = resp.body?.string() ?: ""
                    val m3u8Pattern = Pattern.compile("https?://[^\\s\"'<>]+?\\.m3u8[^\\s\"'<>]*", Pattern.CASE_INSENSITIVE)
                    val m3u8Matcher = m3u8Pattern.matcher(html)
                    if (m3u8Matcher.find()) {
                        return@withContext m3u8Matcher.group(0)
                    }
                    val mp4Pattern = Pattern.compile("https?://[^\\s\"'<>]+?\\.mp4[^\\s\"'<>]*", Pattern.CASE_INSENSITIVE)
                    val mp4Matcher = mp4Pattern.matcher(html)
                    if (mp4Matcher.find()) {
                        return@withContext mp4Matcher.group(0)
                    }
                }
            }
        } catch (_: Exception) {}
        null
    }
}
