package com.lagradost.cloudstream3.ui.animebox.comments

import android.content.Context
import android.net.Uri
import com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager
import com.lagradost.cloudstream3.ui.animebox.sync.AnimeBoxAccountSyncManager
import com.lagradost.cloudstream3.ui.animebox.tickets.SupabaseTicketManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class DantotsuComment(
    val commentId: Int,
    val userId: String,
    val mediaId: Int,
    val parentCommentId: Int?,
    val content: String,
    val timestamp: String,
    val deleted: Boolean,
    val tag: Int?, // Episode number or null
    val upvotes: Int,
    val downvotes: Int,
    val userVoteType: Int, // 1 = upvote, -1 = downvote, 0 = neutral
    val username: String,
    val profilePictureUrl: String?,
    val isMod: Boolean,
    val isAdmin: Boolean,
    val replyCount: Int,
    val totalVotes: Int,
    val cleanContent: String,
    val sharedCollectionJson: String?,
    val imageUrls: List<String>
) {
    fun formattedDateTime(): String {
        if (timestamp.isBlank()) return ""
        return try {
            val clean = if (timestamp.contains(".")) timestamp.substringBefore(".") else timestamp.removeSuffix("Z")
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = inputFormat.parse(clean) ?: return ""
            val diffMs = Date().time - date.time
            val diffMins = diffMs / (60 * 1000)
            val diffHours = diffMs / (60 * 60 * 1000)
            val diffDays = diffMs / (24 * 60 * 60 * 1000)

            when {
                diffMins < 1 -> "Just now"
                diffMins < 60 -> "${diffMins}m ago"
                diffHours < 24 -> "${diffHours}h ago"
                diffDays < 7 -> "${diffDays}d ago"
                else -> SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(date)
            }
        } catch (_: Exception) {
            ""
        }
    }
}

data class DantotsuCommentsPage(
    val comments: List<DantotsuComment>,
    val totalPages: Int,
    val currentPage: Int
)

data class DantotsuUser(
    val userId: String,
    val username: String,
    val profilePictureUrl: String?,
    val isBanned: Boolean,
    val isMod: Boolean,
    val isAdmin: Boolean,
    val totalVotes: Int,
    val warnings: Int
)

object DantotsuCommentsManager {
    private const val BASE_URL = "https://api.dantotsu.app"
    private const val APP_AUTH_KEY = "6*45Qp%W2RS@t38jkXoSKY588Ynj%n"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) Dantotsu/1.0"
    private const val PREFS_NAME = "dantotsu_comments_auth_prefs"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    @Volatile
    var lastError: String? = null
        private set

    /**
     * Resolves an active AniList access token from AnimeBox Sync or CloudStream native AccountManager.
     */
    fun getEffectiveAniListToken(context: Context, profileId: String? = null): String? {
        val targetProfileId = profileId ?: ProfileManager.getActiveProfile(context)
        val tokenFromAnimeBox = AnimeBoxAccountSyncManager.getAniListToken(context, targetProfileId)
        if (!tokenFromAnimeBox.isNullOrBlank()) {
            return tokenFromAnimeBox
        }

        // Fallback: check CloudStream native AniList sync account
        try {
            val csAccount = com.lagradost.cloudstream3.syncproviders.AccountManager.accounts("anilist").firstOrNull()
            val csToken = csAccount?.token?.accessToken
            if (!csToken.isNullOrBlank()) {
                val uName = csAccount.user.name ?: "AniList User"
                val uId = csAccount.user.id.toString()
                val uAvatar = csAccount.user.profilePicture ?: ""
                val syncUser = com.lagradost.cloudstream3.ui.animebox.sync.SyncAccountUser(
                    id = uId,
                    username = uName,
                    avatarUrl = uAvatar,
                    provider = "anilist"
                )
                AnimeBoxAccountSyncManager.saveAniListAuth(context, targetProfileId, csToken, syncUser)
                return csToken
            }
        } catch (_: Throwable) {}

        return null
    }

    /**
     * Retrieves or refreshes the Dantotsu JWT auth token by exchanging the AniList OAuth token.
     */
    suspend fun getOrAuthenticateToken(context: Context, profileId: String? = null): String? = withContext(Dispatchers.IO) {
        val targetProfileId = profileId ?: ProfileManager.getActiveProfile(context)
        val anilistToken = getEffectiveAniListToken(context, targetProfileId)
        if (anilistToken.isNullOrBlank()) {
            lastError = "AniList account required to access Dantotsu comments"
            return@withContext null
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedToken = prefs.getString("auth_token_$targetProfileId", null)
        val cachedTime = prefs.getLong("auth_time_$targetProfileId", 0L)
        val fiveDaysMs = 5L * 24 * 60 * 60 * 1000

        if (!cachedToken.isNullOrBlank() && (System.currentTimeMillis() - cachedTime < fiveDaysMs)) {
            return@withContext cachedToken
        }

        try {
            val formBody = FormBody.Builder()
                .add("token", anilistToken)
                .build()

            val request = Request.Builder()
                .url("$BASE_URL/authenticate")
                .header("appauth", APP_AUTH_KEY)
                .header("User-Agent", USER_AGENT)
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    val json = JSONObject(bodyText)
                    val authToken = json.optString("authToken")
                    if (authToken.isNotBlank()) {
                        val userObj = json.optJSONObject("user")
                        prefs.edit()
                            .putString("auth_token_$targetProfileId", authToken)
                            .putLong("auth_time_$targetProfileId", System.currentTimeMillis())
                            .putString("user_id_$targetProfileId", userObj?.optString("user_id"))
                            .putString("username_$targetProfileId", userObj?.optString("username"))
                            .putString("avatar_$targetProfileId", userObj?.optString("profile_picture_url"))
                            .apply()
                        return@withContext authToken
                    }
                } else {
                    lastError = "AniList authentication failed (${response.code})"
                }
            }
        } catch (e: Exception) {
            lastError = e.localizedMessage ?: "Network error during authentication"
        }
        null
    }

    /**
     * Retrieves current Dantotsu user information.
     */
    fun getCurrentUser(context: Context, profileId: String? = null): DantotsuUser? {
        val targetProfileId = profileId ?: ProfileManager.getActiveProfile(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val userId = prefs.getString("user_id_$targetProfileId", null) ?: return null
        val username = prefs.getString("username_$targetProfileId", "User") ?: "User"
        val avatar = prefs.getString("avatar_$targetProfileId", null)
        return DantotsuUser(
            userId = userId,
            username = username,
            profilePictureUrl = avatar,
            isBanned = false,
            isMod = false,
            isAdmin = false,
            totalVotes = 0,
            warnings = 0
        )
    }

    /**
     * Checks if user has an active AniList session and can comment/vote.
     */
    fun hasAniListLogin(context: Context, profileId: String? = null): Boolean {
        return !getEffectiveAniListToken(context, profileId).isNullOrBlank()
    }

    /**
     * Fetch media comments with optional episode tag filtering, sorting, and pagination.
     * @param tag episode index (e.g. 1 for Episode 1), or null for all anime comments
     * @param sort "newest", "highest_rated", "oldest"
     */
    suspend fun fetchComments(
        context: Context,
        mediaId: Int,
        page: Int = 1,
        tag: Int? = null,
        sort: String = "newest",
        profileId: String? = null
    ): DantotsuCommentsPage = withContext(Dispatchers.IO) {
        if (mediaId <= 0) return@withContext DantotsuCommentsPage(emptyList(), 0, page)

        val authToken = getOrAuthenticateToken(context, profileId)

        val queryParams = buildString {
            if (tag != null && tag > 0) append("tag=$tag&")
            append("sort=$sort")
        }
        val url = "$BASE_URL/comments/$mediaId/$page?$queryParams"

        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("appauth", APP_AUTH_KEY)
                .header("User-Agent", USER_AGENT)

            if (!authToken.isNullOrBlank()) {
                requestBuilder.header("Authorization", authToken)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (response.code == 404 || response.code == 204) {
                    lastError = null
                    return@withContext DantotsuCommentsPage(emptyList(), 0, page)
                }
                if (!response.isSuccessful) {
                    lastError = "Comments unavailable (${response.code})"
                    return@withContext DantotsuCommentsPage(emptyList(), 0, page)
                }
                lastError = null
                val json = JSONObject(bodyText)
                val totalPages = json.optInt("totalPages", 1)
                val commentsArray = json.optJSONArray("comments") ?: JSONArray()
                val list = mutableListOf<DantotsuComment>()
                for (i in 0 until commentsArray.length()) {
                    commentsArray.optJSONObject(i)?.let { list.add(parseComment(it)) }
                }
                DantotsuCommentsPage(list, totalPages, page)
            }
        } catch (e: Exception) {
            lastError = e.localizedMessage ?: "Failed to load comments"
            DantotsuCommentsPage(emptyList(), 0, page)
        }
    }

    /**
     * Fetch thread replies for a specific parent comment.
     */
    suspend fun fetchReplies(
        context: Context,
        parentCommentId: Int,
        page: Int = 1,
        profileId: String? = null
    ): List<DantotsuComment> = withContext(Dispatchers.IO) {
        val authToken = getOrAuthenticateToken(context, profileId)
        val url = "$BASE_URL/comments/parent/$parentCommentId/$page"

        try {
            val requestBuilder = Request.Builder()
                .url(url)
                .header("appauth", APP_AUTH_KEY)
                .header("User-Agent", USER_AGENT)

            if (!authToken.isNullOrBlank()) {
                requestBuilder.header("Authorization", authToken)
            }

            client.newCall(requestBuilder.build()).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@withContext emptyList()
                val json = JSONObject(bodyText)
                val commentsArray = json.optJSONArray("comments") ?: JSONArray()
                val list = mutableListOf<DantotsuComment>()
                for (i in 0 until commentsArray.length()) {
                    commentsArray.optJSONObject(i)?.let { list.add(parseComment(it)) }
                }
                list
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Submit a new top-level comment or threaded reply.
     * Requires AniList login.
     */
    suspend fun postComment(
        context: Context,
        mediaId: Int,
        content: String,
        tag: Int? = null,
        parentCommentId: Int? = null,
        sharedCollectionJson: String? = null,
        sharedCollectionName: String? = null,
        imageUrls: List<String> = emptyList(),
        profileId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val targetProfileId = profileId ?: ProfileManager.getActiveProfile(context)
        val authToken = getOrAuthenticateToken(context, targetProfileId)
        if (authToken.isNullOrBlank()) {
            lastError = "Please log in with AniList to comment."
            return@withContext false
        }
        val user = getCurrentUser(context, targetProfileId)
        if (user == null) {
            lastError = "User profile not found. Please re-login with AniList."
            return@withContext false
        }

        // Format body with clean collection and image attachment support for all clients
        val formattedBody = buildString {
            append(content.trim())
            if (!sharedCollectionJson.isNullOrBlank()) {
                val name = sharedCollectionName ?: "My Picks"
                if (isNotBlank()) append("\n\n")
                append("📦 Collection: **$name**\n`[animebox-collection:$sharedCollectionJson]`")
            }
            if (imageUrls.isNotEmpty()) {
                if (isNotBlank()) append("\n\n")
                imageUrls.forEach { url ->
                    append("![image]($url)\n")
                }
            }
        }.trim()

        if (formattedBody.isBlank()) {
            lastError = "Comment content cannot be empty."
            return@withContext false
        }

        try {
            val formBuilder = FormBody.Builder()
                .add("user_id", user.userId)
                .add("media_id", mediaId.toString())
                .add("content", formattedBody)

            if (tag != null && tag > 0) {
                formBuilder.add("tag", tag.toString())
            }
            if (parentCommentId != null && parentCommentId > 0) {
                formBuilder.add("parent_comment_id", parentCommentId.toString())
            }

            val request = Request.Builder()
                .url("$BASE_URL/comments")
                .header("appauth", APP_AUTH_KEY)
                .header("Authorization", authToken)
                .header("User-Agent", USER_AGENT)
                .post(formBuilder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val bodyText = response.body?.string().orEmpty()
                if (response.isSuccessful) {
                    lastError = null
                    true
                } else {
                    val msg = runCatching { JSONObject(bodyText).optString("message") }.getOrNull()
                    lastError = msg?.ifBlank { null } ?: "Posting failed (${response.code})"
                    false
                }
            }
        } catch (e: Exception) {
            lastError = e.localizedMessage ?: "Failed to post comment"
            false
        }
    }

    /**
     * Cast or clear an upvote/downvote.
     * @param voteType 1 = upvote, -1 = downvote, 0 = reset/neutral
     */
    suspend fun vote(
        context: Context,
        commentId: Int,
        voteType: Int,
        profileId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val authToken = getOrAuthenticateToken(context, profileId) ?: return@withContext false
        try {
            val request = Request.Builder()
                .url("$BASE_URL/comments/vote/$commentId/$voteType")
                .header("appauth", APP_AUTH_KEY)
                .header("Authorization", authToken)
                .header("User-Agent", USER_AGENT)
                .post(FormBody.Builder().build())
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Delete comment by commentId (author or moderator).
     */
    suspend fun deleteComment(
        context: Context,
        commentId: Int,
        profileId: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val authToken = getOrAuthenticateToken(context, profileId) ?: return@withContext false
        try {
            val request = Request.Builder()
                .url("$BASE_URL/comments/$commentId")
                .header("appauth", APP_AUTH_KEY)
                .header("Authorization", authToken)
                .header("User-Agent", USER_AGENT)
                .delete()
                .build()

            client.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Upload image attachment via ImgBB.
     */
    suspend fun uploadImage(context: Context, uri: Uri): String? =
        SupabaseTicketManager.uploadImageToImgBB(context, uri)

    private fun parseComment(json: JSONObject): DantotsuComment {
        val rawContent = json.optString("content", "")
        val (cleanedContent, collectionJson, imgs) = extractCollectionAndImages(rawContent)

        return DantotsuComment(
            commentId = json.optInt("comment_id"),
            userId = json.optString("user_id"),
            mediaId = json.optInt("media_id"),
            parentCommentId = json.optInt("parent_comment_id").takeIf { it > 0 },
            content = rawContent,
            timestamp = json.optString("timestamp"),
            deleted = json.optInt("deleted", 0) == 1,
            tag = json.optInt("tag").takeIf { it > 0 },
            upvotes = json.optInt("upvotes", 0),
            downvotes = json.optInt("downvotes", 0),
            userVoteType = json.optInt("user_vote_type", 0),
            username = json.optString("username").ifBlank {
                json.optJSONObject("user")?.optString("username") ?: "AniList User"
            },
            profilePictureUrl = json.optString("profile_picture_url").ifBlank {
                json.optJSONObject("user")?.optString("profile_picture_url")
            },
            isMod = json.optInt("is_mod", 0) == 1,
            isAdmin = json.optInt("is_admin", 0) == 1,
            replyCount = json.optInt("reply_count", 0),
            totalVotes = json.optInt("total_votes", 0),
            cleanContent = cleanedContent,
            sharedCollectionJson = collectionJson,
            imageUrls = imgs
        )
    }

    /**
     * Parses embedded collection payloads and markdown image URLs from the comment body text.
     */
    private fun extractCollectionAndImages(content: String): Triple<String, String?, List<String>> {
        var text = content

        // Extract collection
        var collectionJson: String? = null
        val collectionRegex = Regex("""`?\[animebox-collection:(.+?)\]`?""", RegexOption.DOT_MATCHES_ALL)
        val colMatch = collectionRegex.find(text)
        if (colMatch != null) {
            collectionJson = colMatch.groupValues[1]
            text = text.replace(collectionRegex, "")
        }

        // Clean up markdown collection header if present
        text = text.replace(Regex("""📦\s*Collection:[^\n]*\n?"""), "").trim()

        // Extract markdown images ![alt](url)
        val imageList = mutableListOf<String>()
        val imgRegex = Regex("""!\[.*?\]\((https?://[^\s)]+)\)""")
        imgRegex.findAll(text).forEach { match ->
            imageList.add(match.groupValues[1])
        }
        text = text.replace(imgRegex, "").trim()

        return Triple(text, collectionJson, imageList)
    }
}
