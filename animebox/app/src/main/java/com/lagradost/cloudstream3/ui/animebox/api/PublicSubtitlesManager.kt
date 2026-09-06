package com.lagradost.cloudstream3.ui.animebox.api

import android.content.Context
import android.util.Log
import com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager
import com.lagradost.cloudstream3.ui.animebox.sync.AnimeBoxAccountSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class PublicSubtitleItem(
    val id: String,
    val anilistId: Int,
    val episodeNum: Int,
    val animeTitle: String,
    val langCode: String,
    val langName: String,
    val uploaderName: String,
    val vttContent: String,
    val createdAt: Long
)

object PublicSubtitlesManager {

    private const val TAG = "PublicSubsManager"
    private const val SUPABASE_URL = "https://okiuzwsldqjrtuyqylhx.supabase.co"
    private const val SUPABASE_ANON_KEY = "sb_publishable_O6CrSYrDu-GilobI41fSMg_DG7IdSbf"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Get the display username for subtitle contribution:
     * - AniList username if logged in
     * - Active profile name if guest
     * - Default to "Firefly User"
     */
    fun getEffectiveUploaderName(context: Context): String {
        return try {
            val profileId = ProfileManager.getActiveProfile(context)
            val profile = ProfileManager.getProfiles(context).firstOrNull { it.id == profileId }
            val anilistUser = AnimeBoxAccountSyncManager.getAniListUser(context, profileId)
            if (anilistUser != null && anilistUser.username.isNotBlank()) {
                "@${anilistUser.username}"
            } else {
                val profileName = profile?.name
                if (!profileName.isNullOrBlank() && !profileName.equals("Guest", ignoreCase = true)) {
                    profileName
                } else {
                    "Firefly User"
                }
            }
        } catch (_: Exception) {
            "Firefly User"
        }
    }

    /**
     * Upload a newly transcribed subtitle file to the community Public Subtitles database.
     */
    suspend fun uploadPublicSubtitle(
        context: Context,
        anilistId: Int,
        episodeNum: Int,
        animeTitle: String,
        langCode: String,
        langName: String,
        vttContent: String,
        customUploaderName: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (anilistId <= 0 || episodeNum <= 0 || vttContent.isBlank() || vttContent.length < 100) {
            return@withContext false
        }

        try {
            val uploader = customUploaderName ?: getEffectiveUploaderName(context)

            val jsonBody = JSONObject().apply {
                put("anilist_id", anilistId)
                put("episode_num", episodeNum)
                put("anime_title", animeTitle.ifBlank { "Anime #$anilistId" })
                put("lang_code", langCode.lowercase())
                put("lang_name", langName)
                put("uploader_name", uploader)
                put("vtt_content", vttContent)
            }

            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/public_subtitles")
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .header("Content-Type", "application/json")
                .header("Prefer", "return=representation")
                .post(jsonBody.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    Log.d(TAG, "Public subtitle uploaded successfully for $animeTitle Ep $episodeNum by $uploader")
                    markSavedToPublic(context, anilistId, episodeNum, langCode)
                    true
                } else {
                    Log.e(TAG, "Failed to upload public subtitle. Code=${resp.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadPublicSubtitle error", e)
            false
        }
    }

    /**
     * Fetch public subtitles for a specific anime episode from Supabase.
     */
    suspend fun fetchPublicSubtitles(
        anilistId: Int,
        episodeNum: Int
    ): List<PublicSubtitleItem> = withContext(Dispatchers.IO) {
        val results = mutableListOf<PublicSubtitleItem>()
        if (anilistId <= 0 || episodeNum <= 0) return@withContext results

        try {
            val url = "$SUPABASE_URL/rest/v1/public_subtitles?anilist_id=eq.$anilistId&episode_num=eq.$episodeNum&select=*&order=created_at.desc"
            val req = Request.Builder()
                .url(url)
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val bodyStr = resp.body?.string() ?: ""
                    if (bodyStr.startsWith("[")) {
                        val arr = JSONArray(bodyStr)
                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            val vtt = obj.optString("vtt_content", "")
                            if (vtt.isNotBlank() && (vtt.contains("-->") || vtt.contains("Dialogue:") || vtt.length > 50)) {
                                results.add(
                                    PublicSubtitleItem(
                                        id = obj.optString("id", ""),
                                        anilistId = obj.optInt("anilist_id", 0),
                                        episodeNum = obj.optInt("episode_num", 0),
                                        animeTitle = obj.optString("anime_title", ""),
                                        langCode = obj.optString("lang_code", "en"),
                                        langName = obj.optString("lang_name", "English"),
                                        uploaderName = obj.optString("uploader_name", "Firefly User").ifBlank { "Firefly User" },
                                        vttContent = vtt,
                                        createdAt = System.currentTimeMillis()
                                    )
                                )
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchPublicSubtitles error", e)
        }

        results
    }

    private const val PREFS_PUBLIC_SUBS = "PublicSubtitlesSavedPrefs"
    private const val KEY_SAVED_PUBLIC_SET = "saved_public_sub_keys"

    fun hasSavedToPublic(context: Context, anilistId: Int, episodeNum: Int, langCode: String): Boolean {
        if (anilistId <= 0 || episodeNum <= 0) return false
        val prefs = context.getSharedPreferences(PREFS_PUBLIC_SUBS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_SAVED_PUBLIC_SET, emptySet()) ?: emptySet()
        val key = "${anilistId}_${episodeNum}_${langCode.lowercase()}"
        return set.contains(key)
    }

    fun markSavedToPublic(context: Context, anilistId: Int, episodeNum: Int, langCode: String) {
        if (anilistId <= 0 || episodeNum <= 0) return
        val prefs = context.getSharedPreferences(PREFS_PUBLIC_SUBS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(KEY_SAVED_PUBLIC_SET, emptySet())?.toMutableSet() ?: mutableSetOf()
        val key = "${anilistId}_${episodeNum}_${langCode.lowercase()}"
        set.add(key)
        prefs.edit().putStringSet(KEY_SAVED_PUBLIC_SET, set).apply()
    }
}
