package com.lagradost.cloudstream3.ui.animebox.api

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * High-performance 1:1 port of Anilili's DubIndex & DubEpisodes overlay architecture.
 * Loads and caches dub episode counts and availability from the media-index CDN.
 */
object AnililiDubManager {

    private const val DUB_EPISODES_CDN_URL = "https://cdn.jsdelivr.net/gh/pommieyewear/media-index@main/data/dub-episodes.json"
    private const val DUB_INDEX_CDN_URL = "https://cdn.jsdelivr.net/gh/pommieyewear/media-index@main/data/dub-index.json"
    private const val OVERLAY_FILE_NAME = "dub-episodes-overlay.json"
    private const val CACHE_EXPIRY_MS = 24 * 60 * 60 * 1000L // 24 hours

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    // In-memory cache for instant O(1) synchronous lookups on poster rendering
    private val dubEpisodeCounts = ConcurrentHashMap<Int, Int>()
    private val dubIndexSet = ConcurrentHashMap.newKeySet<Int>()
    @Volatile private var isInitialized = false
    @Volatile private var isLoading = false

    /**
     * Initializes the Dub Manager using local cached file first, then asynchronously refreshes from CDN.
     */
    fun init(context: Context) {
        if (isInitialized) return
        isInitialized = true

        val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                loadLocalCache(context)
                refreshFromCdnIfStale(context)
            } catch (_: Exception) {}
        }
    }

    private fun loadLocalCache(context: Context) {
        // Load bundled dub-index from assets for instant zero-latency availability
        try {
            context.assets.open("dub-index.json").bufferedReader(Charsets.UTF_8).use { reader ->
                val arr = JSONArray(reader.readText())
                for (i in 0 until arr.length()) {
                    dubIndexSet.add(arr.optInt(i))
                }
            }
        } catch (_: Exception) {}

        try {
            val file = File(context.filesDir, OVERLAY_FILE_NAME)
            if (file.exists()) {
                val atomicFile = AtomicFile(file)
                val jsonStr = String(atomicFile.readFully(), Charsets.UTF_8)
                parseDubJson(jsonStr)
            }
        } catch (_: Exception) {}
    }

    private suspend fun refreshFromCdnIfStale(context: Context) = withContext(Dispatchers.IO) {
        if (isLoading) return@withContext
        isLoading = true
        try {
            val file = File(context.filesDir, OVERLAY_FILE_NAME)
            val lastModified = if (file.exists()) file.lastModified() else 0L
            val isStale = (System.currentTimeMillis() - lastModified) > CACHE_EXPIRY_MS

            if (!file.exists() || isStale || dubEpisodeCounts.isEmpty()) {
                val req = Request.Builder()
                    .url(DUB_EPISODES_CDN_URL)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()

                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            parseDubJson(body)
                            // Persist to local atomic file
                            val atomicFile = AtomicFile(file)
                            val out = atomicFile.startWrite()
                            try {
                                out.write(body.toByteArray(Charsets.UTF_8))
                                atomicFile.finishWrite(out)
                            } catch (e: Exception) {
                                atomicFile.failWrite(out)
                            }
                        }
                    }
                }

                // Also fetch dub-index set for fallback
                val idxReq = Request.Builder()
                    .url(DUB_INDEX_CDN_URL)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()

                client.newCall(idxReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val idxBody = resp.body?.string().orEmpty()
                        if (idxBody.isNotBlank()) {
                            val arr = JSONArray(idxBody)
                            for (i in 0 until arr.length()) {
                                dubIndexSet.add(arr.optInt(i))
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            isLoading = false
        }
    }

    private fun parseDubJson(jsonString: String) {
        try {
            val root = JSONObject(jsonString)
            val titlesObj = root.optJSONObject("titles")
            if (titlesObj != null) {
                val keys = titlesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val id = key.toIntOrNull() ?: continue
                    val count = titlesObj.optInt(key, 0)
                    if (count > 0) {
                        dubEpisodeCounts[id] = count
                        dubIndexSet.add(id)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Synchronously returns the verified dub episode count for an anime, or null if no dub exists.
     */
    fun getDubEpisodeCount(anilistId: Int, airedSubCount: Int = 0, status: String? = null): Int? {
        if (anilistId <= 0) return null
        return dubEpisodeCounts[anilistId]
    }

    /**
     * Returns true if an anime is confirmed to have an English Dub.
     */
    fun hasDub(anilistId: Int): Boolean {
        if (anilistId <= 0) return false
        return dubIndexSet.contains(anilistId) || dubEpisodeCounts.containsKey(anilistId)
    }

    /**
     * Returns true only when dub availability is confirmed by the available data.
     */
    fun isDubConfirmed(anilistId: Int): Boolean = hasDub(anilistId)

    /**
     * Resolves the best released subtitle (CC) episode count using AniList data.
     * Total episodes when known, or released episodes where applicable. Never returns negative.
     */
    fun getAiredSubCount(anilistId: Int, plannedEpisodes: Int, status: String? = null, nextEpisode: Int = 0): Int {
        if (status?.equals("NOT_YET_RELEASED", ignoreCase = true) == true) return 0
        if (plannedEpisodes > 0) return plannedEpisodes
        if (nextEpisode > 1) return nextEpisode - 1
        return 0
    }
}
