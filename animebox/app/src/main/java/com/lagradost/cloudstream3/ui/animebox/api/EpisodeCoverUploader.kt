package com.lagradost.cloudstream3.ui.animebox.api
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object EpisodeCoverUploader {

    private val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    // ImgBB API key & Supabase config
    private val IMGBB_API_KEY get() = AnimeBoxConfig.IMGBB_API_KEY
    private val SUPABASE_URL get() = AnimeBoxConfig.SUPABASE_URL
    private val SUPABASE_KEY get() = AnimeBoxConfig.SUPABASE_ANON_KEY

    // In-memory cache: "anilistId_episodeNum" -> coverUrl
    private val customCoversCache = ConcurrentHashMap<String, String>()

    /**
     * Auto-adjust bitmap to standard 16:9 aspect ratio.
     * If the captured frame is square or non-16:9, center-crops to 16:9.
     */
    fun adjustTo16By9(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        if (w <= 0 || h <= 0) return source

        val targetRatio = 16f / 9f
        val currentRatio = w.toFloat() / h.toFloat()

        return when {
            kotlin.math.abs(currentRatio - targetRatio) < 0.03f -> source
            currentRatio > targetRatio -> {
                // Too wide -> crop horizontally
                val newWidth = (h * targetRatio).toInt().coerceAtMost(w)
                val xOffset = ((w - newWidth) / 2).coerceAtLeast(0)
                Bitmap.createBitmap(source, xOffset, 0, newWidth, h)
            }
            else -> {
                // Too tall / square -> crop vertically
                val newHeight = (w / targetRatio).toInt().coerceAtMost(h)
                val yOffset = ((h - newHeight) / 2).coerceAtLeast(0)
                Bitmap.createBitmap(source, 0, yOffset, w, newHeight)
            }
        }
    }

    /**
     * Upload screenshot to ImgBB (with Supabase storage fallback),
     * and persist to Supabase `anime_custom_episode_covers` table.
     */
    suspend fun uploadScreenshotAsEpisodeCover(
        context: Context,
        rawBitmap: Bitmap,
        anilistId: Int,
        episodeNum: Int,
        animeTitle: String
    ): String? = withContext(Dispatchers.IO) {
        if (anilistId <= 0 || episodeNum <= 0) return@withContext null

        try {
            val adjustedBitmap = adjustTo16By9(rawBitmap)
            val stream = ByteArrayOutputStream()
            adjustedBitmap.compress(Bitmap.CompressFormat.JPEG, 88, stream)
            val imageBytes = stream.toByteArray()

            var uploadedUrl: String? = null

            // 1. Upload to ImgBB
            try {
                val mediaType = "image/jpeg".toMediaTypeOrNull()
                val imageReqBody = imageBytes.toRequestBody(mediaType)
                val formBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("image", "ep_${anilistId}_${episodeNum}.jpg", imageReqBody)
                    .addFormDataPart("name", "ep_${anilistId}_${episodeNum}")
                    .build()

                val request = Request.Builder()
                    .url("https://api.imgbb.com/1/upload?key=$IMGBB_API_KEY")
                    .post(formBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string() ?: ""
                        val json = JSONObject(bodyStr)
                        if (json.optBoolean("success", false)) {
                            val data = json.optJSONObject("data")
                            uploadedUrl = data?.optString("display_url")
                                ?.ifEmpty { data.optString("url") }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Fallback: Upload to Supabase Storage if ImgBB failed
            if (uploadedUrl.isNullOrEmpty()) {
                try {
                    val fileName = "cover_${anilistId}_${episodeNum}_${System.currentTimeMillis()}.jpg"
                    val storageUrl = "$SUPABASE_URL/storage/v1/object/covers/$fileName"
                    val mediaType = "image/jpeg".toMediaTypeOrNull()
                    val reqBody = imageBytes.toRequestBody(mediaType)

                    val storageReq = Request.Builder()
                        .url(storageUrl)
                        .addHeader("apikey", SUPABASE_KEY)
                        .addHeader("Authorization", "Bearer $SUPABASE_KEY")
                        .addHeader("Content-Type", "image/jpeg")
                        .post(reqBody)
                        .build()

                    client.newCall(storageReq).execute().use { resp ->
                        if (resp.isSuccessful) {
                            uploadedUrl = "$SUPABASE_URL/storage/v1/object/public/covers/$fileName"
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val finalUrl = uploadedUrl ?: return@withContext null

            // 3. Save to Supabase `anime_custom_episode_covers` table
            try {
                val payload = JSONObject().apply {
                    put("anilist_id", anilistId)
                    put("episode_num", episodeNum)
                    put("cover_url", finalUrl)
                    put("anime_title", animeTitle)
                }

                val upsertUrl = "$SUPABASE_URL/rest/v1/anime_custom_episode_covers"
                val upsertBody = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())

                val upsertReq = Request.Builder()
                    .url(upsertUrl)
                    .addHeader("apikey", SUPABASE_KEY)
                    .addHeader("Authorization", "Bearer $SUPABASE_KEY")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Prefer", "resolution=merge-duplicates")
                    .post(upsertBody)
                    .build()

                client.newCall(upsertReq).execute().close()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 4. Cache locally
            val cacheKey = "${anilistId}_$episodeNum"
            customCoversCache[cacheKey] = finalUrl

            try {
                val prefs = context.getSharedPreferences("AnimeCustomCovers", Context.MODE_PRIVATE)
                prefs.edit().putString(cacheKey, finalUrl).apply()
            } catch (_: Exception) {}

            try {
                com.lagradost.cloudstream3.ui.animebox.AnimeScreenshotEvents.notifyScreenshotCaptured()
            } catch (_: Exception) {}

            return@withContext finalUrl
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Instantly check if a custom episode cover exists locally (in memory cache,
     * SharedPreferences, or screenshot image files on disk).
     */
    fun getLocalOrUploadedEpisodeCover(
        context: Context?,
        anilistId: Int,
        episodeNum: Int,
        animeTitle: String? = null
    ): String? {
        if (anilistId <= 0 && animeTitle.isNullOrBlank()) return null
        val cacheKey = "${anilistId}_$episodeNum"

        // 1. In-memory cache
        customCoversCache[cacheKey]?.let { return it }

        context?.let { ctx ->
            // 2. SharedPreferences
            try {
                val prefs = ctx.getSharedPreferences("AnimeCustomCovers", Context.MODE_PRIVATE)
                val local = prefs.getString(cacheKey, null)
                if (!local.isNullOrEmpty()) {
                    customCoversCache[cacheKey] = local
                    return local
                }
            } catch (_: Exception) {}

            // 3. Local disk screenshots
            try {
                val baseScreenshots = ctx.getExternalFilesDir("screenshots")
                if (baseScreenshots != null && baseScreenshots.exists()) {
                    // Check specific animeTitle folder first
                    if (!animeTitle.isNullOrBlank()) {
                        val safeAnime = animeTitle.replace(Regex("[^a-zA-Z0-9.-]"), "_")
                        val sDir = java.io.File(baseScreenshots, safeAnime)
                        if (sDir.exists() && sDir.isDirectory) {
                            val match = sDir.listFiles { f ->
                                f.name.startsWith("ep_${episodeNum}_") && (f.name.endsWith(".jpg") || f.name.endsWith(".png") || f.name.endsWith(".jpeg"))
                            }?.maxByOrNull { it.lastModified() }
                            if (match != null && match.exists()) {
                                val path = match.absolutePath
                                customCoversCache[cacheKey] = path
                                return path
                            }
                        }
                    }

                    // Fallback: search folders by meta.json matching anilistId
                    if (anilistId > 0) {
                        val dirs = baseScreenshots.listFiles { f -> f.isDirectory } ?: emptyArray()
                        for (dir in dirs) {
                            val metaFile = java.io.File(dir, "meta.json")
                            if (metaFile.exists()) {
                                try {
                                    val json = JSONObject(metaFile.readText())
                                    if (json.optInt("anilistId", -1) == anilistId) {
                                        val match = dir.listFiles { f ->
                                            f.name.startsWith("ep_${episodeNum}_") && (f.name.endsWith(".jpg") || f.name.endsWith(".png") || f.name.endsWith(".jpeg"))
                                        }?.maxByOrNull { it.lastModified() }
                                        if (match != null && match.exists()) {
                                            val path = match.absolutePath
                                            customCoversCache[cacheKey] = path
                                            return path
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    /**
     * Check if a custom episode cover exists in cache, local files, or Supabase.
     */
    suspend fun getCustomEpisodeCover(context: Context?, anilistId: Int, episodeNum: Int, animeTitle: String? = null): String? = withContext(Dispatchers.IO) {
        val localCover = getLocalOrUploadedEpisodeCover(context, anilistId, episodeNum, animeTitle)
        if (!localCover.isNullOrEmpty()) return@withContext localCover

        val cacheKey = "${anilistId}_$episodeNum"
        try {
            val url = "$SUPABASE_URL/rest/v1/anime_custom_episode_covers?anilist_id=eq.$anilistId&episode_num=eq.$episodeNum&select=cover_url"
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
                        if (arr.length() > 0) {
                            val cUrl = arr.getJSONObject(0).optString("cover_url", "")
                            if (cUrl.isNotEmpty()) {
                                customCoversCache[cacheKey] = cUrl
                                try {
                                    context?.getSharedPreferences("AnimeCustomCovers", Context.MODE_PRIVATE)
                                        ?.edit()?.putString(cacheKey, cUrl)?.apply()
                                } catch (_: Exception) {}
                                return@withContext cUrl
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return@withContext null
    }
}
