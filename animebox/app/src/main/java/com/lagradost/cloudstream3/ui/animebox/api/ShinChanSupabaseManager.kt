package com.lagradost.cloudstream3.ui.animebox.api

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class ShinChanEpisodeEntry(
    val episodeNumber: Int,
    val videoId: String,
    val coverImageUrl: String?,
    val mobileUrl: String?,
    val webUrl: String?,
    val type: String = "episode",
    val source: String? = null
)

object ShinChanSupabaseManager {

    private const val SUPABASE_URL = "https://okiuzwsldqjrtuyqylhx.supabase.co"
    private const val SUPABASE_KEY = "sb_publishable_O6CrSYrDu-GilobI41fSMg_DG7IdSbf"
    private const val STATS_API_URL = "https://episode-finder-api.lovable.app/api/public/anime/stats"
    private const val FULL_EPISODES_API_URL = "https://episode-finder-api.lovable.app/api/public/anime/episodes?limit=all"

    private const val DEFAULT_LATEST_EP = 1349

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    // In-memory caches
    private val cachedCovers: MutableMap<Int, String> = mutableMapOf()
    private val cachedVideoUrls: MutableMap<Int, String> = mutableMapOf()
    private var cachedLatestEpNum: Int = DEFAULT_LATEST_EP
    private var lastFetchTimeMs: Long = 0
    private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes in-memory TTL

    private var isAssetLoaded = false
    private var isSyncingInProgress = false

    /**
     * Loads the bundled shinchan_episodes.json asset instantly into cache.
     */
    fun loadBundledAssetIfNeeded(context: Context?) {
        if (isAssetLoaded && cachedCovers.isNotEmpty()) return
        if (context == null) return

        try {
            val jsonString = context.assets.open("shinchan_episodes.json").bufferedReader().use { it.readText() }
            if (jsonString.isNotBlank()) {
                val root = JSONObject(jsonString)
                val episodesArr = root.optJSONArray("episodes")
                if (episodesArr != null) {
                    var maxEp = DEFAULT_LATEST_EP
                    for (i in 0 until episodesArr.length()) {
                        val obj = episodesArr.optJSONObject(i) ?: continue
                        val epNum = obj.optInt("episodeNumber", obj.optInt("episode_number", -1))
                        val vId = obj.optString("videoId", obj.optString("video_id", ""))
                        val cover = obj.optString("coverImageUrl", obj.optString("cover_image_url", ""))
                        val mobile = obj.optString("mobileUrl", obj.optString("mobile_url", ""))
                        val web = obj.optString("webUrl", obj.optString("web_url", ""))

                        if (epNum > 0) {
                            if (epNum > maxEp) maxEp = epNum
                            if (cover.isNotBlank() && !cachedCovers.containsKey(epNum)) {
                                cachedCovers[epNum] = cover
                            }
                            if (!cachedVideoUrls.containsKey(epNum)) {
                                val effUrl = when {
                                    mobile.isNotBlank() -> mobile
                                    vId.isNotBlank() -> "https://m.ok.ru/video/$vId"
                                    web.isNotBlank() -> web
                                    else -> ""
                                }
                                if (effUrl.isNotBlank()) {
                                    cachedVideoUrls[epNum] = effUrl
                                }
                            }
                        }
                    }
                    cachedLatestEpNum = maxOf(cachedLatestEpNum, maxEp)
                    isAssetLoaded = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Loads all Shinchan episodes from Supabase database with limit=3000.
     */
    suspend fun loadShinChanEpisodes(context: Context? = null, forceRefresh: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (context != null) {
            loadBundledAssetIfNeeded(context)
        }

        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedCovers.isNotEmpty() && (now - lastFetchTimeMs < CACHE_TTL_MS)) {
            return@withContext true
        }

        try {
            // Note: limit=3000 prevents PostgREST default 1000 cutoff
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/shinchan_episodes?is_active=eq.true&order=episode_number.asc&limit=3000")
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_KEY")
                .addHeader("Content-Type", "application/json")
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: return@withContext cachedCovers.isNotEmpty()
                    val jsonArray = JSONArray(body)

                    val newCovers = mutableMapOf<Int, String>()
                    val newVideoUrls = mutableMapOf<Int, String>()
                    var maxEp = DEFAULT_LATEST_EP

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.optJSONObject(i) ?: continue
                        val epNum = obj.optInt("episode_number", -1)
                        val vId = obj.optString("video_id", "")
                        val cover = obj.optString("cover_image_url", "")
                        val mobile = obj.optString("mobile_url", "")
                        val web = obj.optString("web_url", "")

                        if (epNum > 0) {
                            if (epNum > maxEp) {
                                maxEp = epNum
                            }
                            if (cover.isNotBlank() && !newCovers.containsKey(epNum)) {
                                newCovers[epNum] = cover
                            }
                            if (!newVideoUrls.containsKey(epNum)) {
                                val effectiveUrl = when {
                                    mobile.isNotBlank() -> mobile
                                    vId.isNotBlank() -> "https://m.ok.ru/video/$vId"
                                    web.isNotBlank() -> web
                                    else -> ""
                                }
                                if (effectiveUrl.isNotBlank()) {
                                    newVideoUrls[epNum] = effectiveUrl
                                }
                            }
                        }
                    }

                    if (newCovers.isNotEmpty() || newVideoUrls.isNotEmpty()) {
                        cachedCovers.putAll(newCovers)
                        cachedVideoUrls.putAll(newVideoUrls)
                        cachedLatestEpNum = maxOf(cachedLatestEpNum, maxEp)
                        lastFetchTimeMs = now
                        return@withContext true
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext cachedCovers.isNotEmpty()
    }

    /**
     * Returns cover image URL for a specific Shinchan episode.
     */
    suspend fun getEpisodeCoverUrl(episodeNum: Int, context: Context? = null): String? = withContext(Dispatchers.IO) {
        if (cachedCovers.isEmpty()) {
            loadShinChanEpisodes(context)
        }
        return@withContext cachedCovers[episodeNum]
    }

    /**
     * Returns video URL for a specific Shinchan episode.
     */
    suspend fun getVideoUrl(episodeNum: Int, context: Context? = null): String? = withContext(Dispatchers.IO) {
        if (cachedVideoUrls.isEmpty()) {
            loadShinChanEpisodes(context)
        }
        return@withContext cachedVideoUrls[episodeNum]
    }

    /**
     * Returns the latest episode number.
     */
    fun getLatestEpisodeNumber(): Int {
        return cachedLatestEpNum
    }

    /**
     * Returns the full map of episode covers.
     */
    fun getAllEpisodeCovers(context: Context? = null): Map<Int, String> {
        if (cachedCovers.isEmpty() && context != null) {
            loadBundledAssetIfNeeded(context)
        }
        return cachedCovers
    }

    /**
     * Background Sync: checks stats API when Shinchan is played/opened.
     * If new episodes are found, inserts them to Supabase in real-time.
     */
    fun triggerBackgroundSync(context: Context? = null) {
        if (isSyncingInProgress) return
        isSyncingInProgress = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Fetch remote stats (Fast)
                val statsReq = Request.Builder().url(STATS_API_URL).build()
                val statsBody = client.newCall(statsReq).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string() else null
                } ?: run {
                    isSyncingInProgress = false
                    return@launch
                }

                val statsJson = JSONObject(statsBody)
                val latestRemoteEp = statsJson.optJSONObject("latestEpisode")?.optInt("episodeNumber", 0) ?: 0
                val currentMax = cachedLatestEpNum

                if (latestRemoteEp > currentMax) {
                    // New episodes detected! Fetch full list to sync
                    val fullReq = Request.Builder().url(FULL_EPISODES_API_URL).build()
                    val fullBody = client.newCall(fullReq).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string() else null
                    }

                    if (fullBody != null) {
                        val fullTrimmed = fullBody.trim()
                        val epArray = if (fullTrimmed.startsWith("[")) {
                            JSONArray(fullTrimmed)
                        } else {
                            val root = JSONObject(fullTrimmed)
                            root.optJSONArray("episodes") ?: root.optJSONArray("data")
                        }

                        if (epArray != null && epArray.length() > 0) {
                            val payloadArray = JSONArray()
                            for (i in 0 until epArray.length()) {
                                val epObj = epArray.optJSONObject(i) ?: continue
                                val epNum = epObj.optInt("episodeNumber", 0)
                                val vId = epObj.optString("videoId", "")
                                if (epNum > currentMax && vId.isNotEmpty()) {
                                    val item = JSONObject().apply {
                                        put("episode_number", epNum)
                                        put("video_id", vId)
                                        put("cover_image_url", epObj.optString("coverImageUrl", null))
                                        put("mobile_url", epObj.optString("mobileUrl", "https://m.ok.ru/video/$vId"))
                                        put("web_url", epObj.optString("webUrl", "https://ok.ru/video/$vId"))
                                        put("type", epObj.optString("type", "episode"))
                                        put("views", epObj.optInt("views", 0))
                                        put("source", epObj.optString("source", "999-present"))
                                        put("is_active", true)
                                    }
                                    payloadArray.put(item)
                                    
                                    // Update memory immediately
                                    val cImg = epObj.optString("coverImageUrl", "")
                                    if (cImg.isNotEmpty()) cachedCovers[epNum] = cImg
                                    cachedVideoUrls[epNum] = "https://m.ok.ru/video/$vId"
                                }
                            }

                            if (payloadArray.length() > 0) {
                                val mediaType = "application/json; charset=utf-8".toMediaType()
                                val upsertReq = Request.Builder()
                                    .url("$SUPABASE_URL/rest/v1/shinchan_episodes")
                                    .addHeader("apikey", SUPABASE_KEY)
                                    .addHeader("Authorization", "Bearer $SUPABASE_KEY")
                                    .addHeader("Content-Type", "application/json")
                                    .addHeader("Prefer", "resolution=merge-duplicates")
                                    .post(payloadArray.toString().toRequestBody(mediaType))
                                    .build()

                                client.newCall(upsertReq).execute().close()
                                cachedLatestEpNum = maxOf(cachedLatestEpNum, latestRemoteEp)
                                loadShinChanEpisodes(context, forceRefresh = true)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isSyncingInProgress = false
            }
        }
    }
}
