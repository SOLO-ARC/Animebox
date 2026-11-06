package com.lagradost.cloudstream3.ui.animebox.reviews

import com.lagradost.cloudstream3.ui.animebox.api.AnimeBoxConfig

import android.content.Context
import android.net.Uri
import com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager
import com.lagradost.cloudstream3.ui.animebox.sync.AnimeBoxAccountSyncManager
import com.lagradost.cloudstream3.ui.animebox.tickets.SupabaseTicketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

data class AnimeReviewComment(
    val id: String,
    val mediaId: Int,
    val parentId: String?,
    val rootId: String?,
    val depth: Int,
    val authorId: String,
    val authorType: String,
    val authorName: String,
    val avatarUrl: String,
    val body: String,
    val imageUrls: List<String>,
    val createdAt: String,
    val sharedCollectionJson: String? = null
) {
    fun formattedDateTime(): String {
        if (createdAt.isBlank()) return ""
        return try {
            val clean = if (createdAt.contains(".")) createdAt.substringBefore(".") else createdAt.removeSuffix("Z")
            val inputFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }
            val date = inputFormat.parse(clean) ?: return ""
            val now = java.util.Date()
            val diffMs = now.time - date.time
            val diffMins = diffMs / (60 * 1000)
            val diffHours = diffMs / (60 * 60 * 1000)
            val diffDays = diffMs / (24 * 60 * 60 * 1000)

            when {
                diffMins < 1 -> "Just now"
                diffMins < 60 -> "${diffMins}m ago"
                diffHours < 24 -> "${diffHours}h ago"
                diffDays < 7 -> "${diffDays}d ago"
                else -> java.text.SimpleDateFormat("dd MMM, HH:mm", java.util.Locale.getDefault()).format(date)
            }
        } catch (_: Exception) {
            ""
        }
    }
}

data class ReviewCommentIdentity(
    val id: String,
    val type: String,
    val name: String,
    val avatarUrl: String,
    val shareableAvatarUrl: String = avatarUrl
)

object AnimeReviewCommentsManager {
    private val SUPABASE_URL get() = AnimeBoxConfig.SUPABASE_URL
    private val SUPABASE_ANON_KEY get() = AnimeBoxConfig.SUPABASE_ANON_KEY
    private const val PREFS = "anime_review_comment_identity"
    private val client by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
    }
    @Volatile var lastError: String? = null
        private set

    fun identity(context: Context, requestedProfileId: String? = null): ReviewCommentIdentity {
        val profileId = requestedProfileId ?: ProfileManager.getActiveProfile(context)
        val user = AnimeBoxAccountSyncManager.getAniListUser(context, profileId)
        val profile = ProfileManager.getProfiles(context).firstOrNull { it.id == profileId }
        val selectedAvatar = profile?.avatarUrl.orEmpty()
        val defaultAvatar = "android.resource://${context.packageName}/drawable/default_profile_picture"
        if (user != null && user.id.isNotBlank()) {
            val selectedCustomAvatar = selectedAvatar.isNotBlank() &&
                !selectedAvatar.equals(user.avatarUrl, ignoreCase = true) &&
                !selectedAvatar.contains("default_profile_picture", ignoreCase = true)
            val localAvatar = if (selectedCustomAvatar) selectedAvatar else user.avatarUrl.ifBlank { defaultAvatar }
            val shareableAvatar = when {
                localAvatar.startsWith("https://", ignoreCase = true) || localAvatar.startsWith("http://", ignoreCase = true) -> localAvatar
                localAvatar.startsWith("android.resource://", ignoreCase = true) -> localAvatar
                else -> defaultAvatar
            }
            return ReviewCommentIdentity("anilist_${user.id}", "anilist", user.username, localAvatar, shareableAvatar)
        }
        val guestAvatar = selectedAvatar.ifBlank { defaultAvatar }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = "guest_id_$profileId"
        val id = prefs.getString(key, null) ?: "guest_${UUID.randomUUID()}".also {
            prefs.edit().putString(key, it).apply()
        }
        val shareableAvatar = if (guestAvatar.startsWith("https://", true) || guestAvatar.startsWith("http://", true) || guestAvatar.startsWith("android.resource://", true)) guestAvatar else defaultAvatar
        return ReviewCommentIdentity(id, "guest", "Guest ${id.takeLast(6).uppercase()}", guestAvatar, shareableAvatar)
    }

    suspend fun fetch(mediaId: Int): List<AnimeReviewComment> = withContext(Dispatchers.IO) {
        if (mediaId <= 0) return@withContext emptyList()
        try {
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/anime_review_comments?anilist_media_id=eq.$mediaId&select=*&order=created_at.asc&limit=500")
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .header("Accept", "application/json")
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    lastError = "Comments unavailable (${response.code}). ${runCatching { JSONObject(body).optString("message") }.getOrNull().orEmpty()}".trim()
                    return@withContext emptyList()
                }
                lastError = null
                val rows = JSONArray(body)
                buildList {
                    for (i in 0 until rows.length()) rows.optJSONObject(i)?.let { add(parse(it)) }
                }
            }
        } catch (e: Exception) {
            lastError = e.localizedMessage ?: "Network error loading comments"
            emptyList()
        }
    }

    private val ADMIN_EMAIL get() = AnimeBoxConfig.ADMIN_EMAIL
    private val ADMIN_DEFAULT_PASS get() = AnimeBoxConfig.ADMIN_DEFAULT_PASS
    @Volatile private var adminToken: String? = null
    @Volatile private var adminUserId: String? = null

    suspend fun getOrLoginAdminToken(password: String = AnimeBoxConfig.ADMIN_DEFAULT_PASS): String? = withContext(Dispatchers.IO) {
        if (!adminToken.isNullOrBlank()) return@withContext adminToken
        try {
            val payload = JSONObject().apply {
                put("email", ADMIN_EMAIL)
                put("password", password)
            }
            val request = Request.Builder()
                .url("$SUPABASE_URL/auth/v1/token?grant_type=password")
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(body)
                    adminToken = json.optString("access_token")
                    adminUserId = json.optJSONObject("user")?.optString("id")
                    adminToken
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun delete(commentId: String): Boolean = withContext(Dispatchers.IO) {
        val token = getOrLoginAdminToken() ?: return@withContext false
        try {
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/anime_review_comments?id=eq.$commentId")
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $token")
                .delete()
                .build()
            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    suspend fun post(
        mediaId: Int,
        animeTitle: String,
        identity: ReviewCommentIdentity,
        body: String,
        imageUrls: List<String>,
        parentId: String? = null,
        sharedCollection: JSONObject? = null
    ): AnimeReviewComment? = withContext(Dispatchers.IO) {
        if (mediaId <= 0 || (body.isBlank() && imageUrls.isEmpty())) return@withContext null
        try {
            val isAdmin = identity.type == "admin"
            val authToken = if (isAdmin) (getOrLoginAdminToken() ?: SUPABASE_ANON_KEY) else SUPABASE_ANON_KEY
            val payload = JSONObject().apply {
                put("anilist_media_id", mediaId)
                put("anime_title", animeTitle.take(180))
                if (!parentId.isNullOrBlank()) put("parent_id", parentId)
                put("author_id", if (isAdmin) (adminUserId ?: identity.id) else identity.id)
                put("author_type", if (isAdmin) "admin" else identity.type)
                put("author_name", if (isAdmin) "Firefly" else identity.name.take(50))
                if (identity.shareableAvatarUrl.isNotBlank()) put("author_avatar_url", identity.shareableAvatarUrl)
                put("body", body.trim().take(4000))
                put("image_urls", JSONArray(imageUrls.take(4)))
                sharedCollection?.let { put("shared_collection", it) }
            }
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/anime_review_comments?select=*")
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $authToken")
                .header("Content-Type", "application/json")
                .header("Prefer", "return=representation")
                .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val msg = runCatching { JSONObject(bodyText).optString("message") }.getOrNull()
                        ?.takeIf { it.isNotBlank() } ?: "Could not post comment (${response.code})."
                    lastError = msg
                    android.util.Log.e("ReviewComments", "Post failed (${response.code}): $bodyText")
                    return@withContext null
                }
                lastError = null
                JSONArray(bodyText).optJSONObject(0)?.let(::parse)
            }
        } catch (e: Exception) {
            lastError = e.localizedMessage ?: "Could not post comment"
            null
        }
    }

    suspend fun uploadImage(context: Context, uri: Uri): String? =
        SupabaseTicketManager.uploadImageToImgBB(context, uri)

    private fun parse(json: JSONObject) = AnimeReviewComment(
        id = json.optString("id"),
        mediaId = json.optInt("anilist_media_id"),
        parentId = json.optString("parent_id").takeIf { it.isNotBlank() && it != "null" },
        rootId = json.optString("root_comment_id").takeIf { it.isNotBlank() && it != "null" },
        depth = json.optInt("depth", 0).coerceIn(0, 6),
        authorId = json.optString("author_id"),
        authorType = json.optString("author_type", "guest"),
        authorName = json.optString("author_name", "Guest"),
        avatarUrl = json.optString("author_avatar_url"),
        body = json.optString("body"),
        imageUrls = json.optJSONArray("image_urls")?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).takeIf { url -> url.startsWith("https://") || url.startsWith("http://") } }
        }.orEmpty(),
        createdAt = json.optString("created_at"),
        sharedCollectionJson = json.optJSONObject("shared_collection")?.toString()
    )
}
