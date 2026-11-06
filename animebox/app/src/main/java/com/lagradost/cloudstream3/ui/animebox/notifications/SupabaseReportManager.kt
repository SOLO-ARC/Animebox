package com.lagradost.cloudstream3.ui.animebox.notifications

import com.lagradost.cloudstream3.ui.animebox.api.AnimeBoxConfig

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SupabaseReportManager {
    private val SUPABASE_URL get() = AnimeBoxConfig.SUPABASE_URL
    private val SUPABASE_ANON_KEY get() = AnimeBoxConfig.SUPABASE_ANON_KEY

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Submit an issue report directly to Supabase reports table along with full diagnostics log.
     */
    suspend fun submitReport(
        userName: String,
        email: String?,
        animeTitle: String,
        anilistId: Int?,
        episodeNumber: Int,
        issueType: String,
        description: String = "",
        userId: String? = null,
        diagnostics: JSONObject? = null,
        adminNotes: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val isValidUuid = try {
                if (!userId.isNullOrBlank()) {
                    java.util.UUID.fromString(userId.trim())
                    true
                } else false
            } catch (_: Exception) { false }

            val effectiveName = if (!userId.isNullOrBlank() && !isValidUuid && !userName.contains(userId)) {
                "$userName ($userId)"
            } else userName

            val finalAdminNotes = when {
                !adminNotes.isNullOrBlank() -> adminNotes
                diagnostics != null -> diagnostics.toString()
                else -> null
            }

            val json = JSONObject().apply {
                put("target_id", java.util.UUID.randomUUID().toString())
                put("type", "anime")
                put("user_name", effectiveName.ifBlank { "FireFly User" })
                if (isValidUuid) {
                    put("user_id", userId!!.trim())
                }
                if (!email.isNullOrBlank()) {
                    put("email", email.trim())
                }
                put("anime_title", animeTitle)
                if (anilistId != null && anilistId > 0) {
                    put("anilist_id", anilistId)
                }
                put("episode_number", episodeNumber)
                put("issue_type", issueType)
                put("description", description.trim())
                put("status", "pending")
                if (!finalAdminNotes.isNullOrBlank()) {
                    put("admin_notes", finalAdminNotes)
                }
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = json.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/reports")
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .header("Content-Type", "application/json")
                .header("Prefer", "return=minimal")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                android.util.Log.d("SupabaseReport", "Supabase report response code: ${response.code}")
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    android.util.Log.e("SupabaseReport", "Supabase report error: code=${response.code}, body=$errBody")
                }
                response.isSuccessful
            }
        } catch (e: Exception) {
            android.util.Log.e("SupabaseReport", "Exception in submitReport: ${e.message}", e)
            e.printStackTrace()
            false
        }
    }
}
