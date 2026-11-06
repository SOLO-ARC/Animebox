package com.lagradost.cloudstream3.ui.animebox.api
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Manages atomic anime view counts synced to Supabase database.
 * Combined view count in app = Base AniList Views + Supabase Anime Views.
 */
object AnimeViewCountManager {
    private val SUPABASE_URL get() = AnimeBoxConfig.SUPABASE_URL
    private val SUPABASE_KEY get() = AnimeBoxConfig.SUPABASE_ANON_KEY

    private val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // In-memory cache of views from database + local session increments per anilistId
    private val extraViewsCache = ConcurrentHashMap<Int, Long>()

    fun getExtraViews(anilistId: Int): Long {
        return extraViewsCache[anilistId] ?: 0L
    }

    /**
     * Increment view count for the specified anime.
     * 1. Instantly increments the local in-memory view count so UI is updated immediately.
     * 2. Fires an asynchronous RPC request to Supabase to increment in database.
     * 3. Syncs final count from server to local cache.
     */
    fun incrementView(anilistId: Int, onUpdated: ((Long) -> Unit)? = null) {
        if (anilistId <= 0) return
        val current = extraViewsCache[anilistId] ?: 0L
        val incremented = current + 1
        extraViewsCache[anilistId] = incremented
        onUpdated?.invoke(incremented)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Call Supabase RPC increment_anime_views
                val jsonBody = JSONObject().apply {
                    put("p_anilist_id", anilistId)
                }.toString()

                val request = Request.Builder()
                    .url("$SUPABASE_URL/rest/v1/rpc/increment_anime_views")
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer $SUPABASE_KEY")
                    .header("Content-Type", "application/json")
                    .post(jsonBody.toRequestBody("application/json".toMediaType()))
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body.string().trim()
                        val serverCount = bodyStr.toLongOrNull()
                        if (serverCount != null && serverCount > 0) {
                            val finalCount = maxOf(serverCount, extraViewsCache[anilistId] ?: serverCount)
                            extraViewsCache[anilistId] = finalCount
                            withContext(Dispatchers.Main) {
                                onUpdated?.invoke(finalCount)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore network errors; local increment already applied
            }
        }
    }

    /**
     * Fetch existing view count from Supabase database for an anime.
     */
    suspend fun fetchCustomViews(anilistId: Int): Long = withContext(Dispatchers.IO) {
        if (anilistId <= 0) return@withContext 0L
        try {
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/anime_views?anilist_id=eq.$anilistId&select=views")
                .header("apikey", SUPABASE_KEY)
                .header("Authorization", "Bearer $SUPABASE_KEY")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body.string()
                    if (bodyStr.isNotEmpty()) {
                        val arr = JSONArray(bodyStr)
                        if (arr.length() > 0) {
                            val v = arr.getJSONObject(0).optLong("views", 0L)
                            val finalCount = maxOf(v, extraViewsCache[anilistId] ?: v)
                            extraViewsCache[anilistId] = finalCount
                            return@withContext finalCount
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return@withContext extraViewsCache[anilistId] ?: 0L
    }
}
