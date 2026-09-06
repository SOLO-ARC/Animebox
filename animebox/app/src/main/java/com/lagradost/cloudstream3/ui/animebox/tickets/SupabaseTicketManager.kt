package com.lagradost.cloudstream3.ui.animebox.tickets

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class SupportTicket(
    val id: String,
    val ticketNumber: Int,
    val anilistId: String,
    val anilistUsername: String,
    val anilistAvatarUrl: String?,
    val profileId: String?,
    val title: String,
    val category: String,
    val description: String,
    val initialImageUrl: String?,
    val status: String, // 'open', 'in_progress', 'resolved', 'closed'
    val priority: String,
    val adminNotes: String?,
    val lastMessageAt: String,
    val lastMessagePreview: String?,
    val createdAt: String,
    val updatedAt: String
) {
    fun formattedLastMessageTime(): String {
        return formatIsoTime(lastMessageAt.ifEmpty { createdAt })
    }

    fun formattedCreatedAt(): String {
        return formatIsoTime(createdAt)
    }

    private fun formatIsoTime(isoStr: String): String {
        return try {
            val clean = if (isoStr.contains(".")) isoStr.substringBefore(".") else isoStr.removeSuffix("Z")
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = inputFormat.parse(clean) ?: Date()
            val now = Date()
            val diffMs = now.time - date.time
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
            isoStr
        }
    }
}

data class TicketMessage(
    val id: String,
    val ticketId: String,
    val senderType: String, // 'user' or 'admin'
    val senderName: String,
    val senderAvatarUrl: String?,
    val senderAnilistId: String?,
    val message: String,
    val imageUrl: String?,
    val isRead: Boolean,
    val createdAt: String
) {
    fun formattedTime(): String {
        return try {
            val clean = if (createdAt.contains(".")) createdAt.substringBefore(".") else createdAt.removeSuffix("Z")
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = inputFormat.parse(clean) ?: Date()
            val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            outputFormat.format(date)
        } catch (_: Exception) {
            createdAt
        }
    }
}

object SupabaseTicketManager {
    private const val SUPABASE_URL = "https://okiuzwsldqjrtuyqylhx.supabase.co"
    private const val SUPABASE_ANON_KEY = "sb_publishable_O6CrSYrDu-GilobI41fSMg_DG7IdSbf"
    private const val IMGBB_API_KEY = "ce2a79ca83dc35909064133f7c9c2677"
    const val ADMIN_AVATAR_URL = "https://i.ibb.co/LWs7Fpm/464405f5a311.jpg"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Upload an image from URI or bytes to ImgBB and return the direct image URL.
     */
    suspend fun uploadImageToImgBB(context: Context, imageUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bytes = context.contentResolver.openInputStream(imageUri)?.use { it.readBytes() } ?: return@withContext null
            val base64Str = Base64.encodeToString(bytes, Base64.NO_WRAP)

            val formBody = FormBody.Builder()
                .add("key", IMGBB_API_KEY)
                .add("image", base64Str)
                .build()

            val request = Request.Builder()
                .url("https://api.imgbb.com/1/upload")
                .post(formBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val respBody = response.body?.string() ?: return@withContext null
                    val json = JSONObject(respBody)
                    val data = json.optJSONObject("data")
                    return@withContext data?.optString("url") ?: data?.optString("display_url")
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Fetch all tickets created by a specific AniList user ID.
     */
    suspend fun fetchUserTickets(anilistId: String): List<SupportTicket> = withContext(Dispatchers.IO) {
        try {
            val encodedId = java.net.URLEncoder.encode(anilistId.trim(), "UTF-8")
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/tickets?anilist_id=eq.$encodedId&order=last_message_at.desc")
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: "[]"
                    val jsonArray = JSONArray(body)
                    val list = mutableListOf<SupportTicket>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        list.add(parseTicketJson(obj))
                    }
                    return@withContext list
                }
            }
            emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Create a new support ticket and send the initial message in ticket_messages.
     */
    suspend fun createTicket(
        anilistId: String,
        anilistUsername: String,
        anilistAvatarUrl: String?,
        profileId: String?,
        title: String,
        category: String,
        description: String,
        initialImageUrl: String? = null
    ): SupportTicket? = withContext(Dispatchers.IO) {
        try {
            val ticketJson = JSONObject().apply {
                put("anilist_id", anilistId.trim())
                put("anilist_username", anilistUsername.trim())
                if (!anilistAvatarUrl.isNullOrBlank()) put("anilist_avatar_url", anilistAvatarUrl.trim())
                if (!profileId.isNullOrBlank()) put("profile_id", profileId.trim())
                put("title", title.trim())
                put("category", category.trim())
                put("description", description.trim())
                if (!initialImageUrl.isNullOrBlank()) put("initial_image_url", initialImageUrl.trim())
                put("status", "open")
                put("priority", "normal")
                put("last_message_preview", description.trim().take(100))
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/tickets")
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .header("Content-Type", "application/json")
                .header("Prefer", "return=representation")
                .post(ticketJson.toString().toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val respBody = response.body?.string() ?: return@withContext null
                    val jsonArray = JSONArray(respBody)
                    if (jsonArray.length() > 0) {
                        val ticket = parseTicketJson(jsonArray.getJSONObject(0))
                        // Also add initial message to ticket_messages
                        sendMessage(
                            ticketId = ticket.id,
                            senderType = "user",
                            senderName = anilistUsername,
                            senderAvatarUrl = anilistAvatarUrl,
                            senderAnilistId = anilistId,
                            message = description,
                            imageUrl = initialImageUrl
                        )
                        return@withContext ticket
                    }
                }
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Fetch all messages for a specific ticket.
     */
    suspend fun fetchTicketMessages(ticketId: String): List<TicketMessage> = withContext(Dispatchers.IO) {
        try {
            val encodedId = java.net.URLEncoder.encode(ticketId.trim(), "UTF-8")
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/ticket_messages?ticket_id=eq.$encodedId&order=created_at.asc")
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: "[]"
                    val jsonArray = JSONArray(body)
                    val list = mutableListOf<TicketMessage>()
                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        list.add(
                            TicketMessage(
                                id = obj.optString("id"),
                                ticketId = obj.optString("ticket_id"),
                                senderType = obj.optString("sender_type", "user"),
                                senderName = obj.optString("sender_name", "User"),
                                senderAvatarUrl = if (obj.isNull("sender_avatar_url")) null else obj.optString("sender_avatar_url").ifEmpty { null },
                                senderAnilistId = if (obj.isNull("sender_anilist_id")) null else obj.optString("sender_anilist_id").ifEmpty { null },
                                message = obj.optString("message", ""),
                                imageUrl = if (obj.isNull("image_url")) null else obj.optString("image_url").ifEmpty { null },
                                isRead = obj.optBoolean("is_read", false),
                                createdAt = obj.optString("created_at", "")
                            )
                        )
                    }
                    return@withContext list
                }
            }
            emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Send a message in a ticket thread (user or admin).
     */
    suspend fun sendMessage(
        ticketId: String,
        senderType: String,
        senderName: String,
        senderAvatarUrl: String?,
        senderAnilistId: String?,
        message: String,
        imageUrl: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("ticket_id", ticketId.trim())
                put("sender_type", senderType)
                put("sender_name", senderName.trim())
                if (!senderAvatarUrl.isNullOrBlank()) put("sender_avatar_url", senderAvatarUrl.trim())
                if (!senderAnilistId.isNullOrBlank()) put("sender_anilist_id", senderAnilistId.trim())
                put("message", message.trim())
                if (!imageUrl.isNullOrBlank()) put("image_url", imageUrl.trim())
                put("is_read", false)
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/ticket_messages")
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .header("Content-Type", "application/json")
                .header("Prefer", "return=minimal")
                .post(json.toString().toRequestBody(mediaType))
                .build()

            val success = client.newCall(request).execute().use { response ->
                response.isSuccessful
            }

            if (success) {
                // Update tickets.last_message_at and last_message_preview
                val preview = if (message.isNotBlank()) message.take(100) else "[Image Attached]"
                updateTicketLastMessage(ticketId, preview)
            }
            success
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun updateTicketLastMessage(ticketId: String, preview: String) = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("last_message_preview", preview)
                put("last_message_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date()))
                put("updated_at", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date()))
            }
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/tickets?id=eq.$ticketId")
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .header("Content-Type", "application/json")
                .patch(json.toString().toRequestBody(mediaType))
                .build()

            client.newCall(request).execute().close()
        } catch (_: Exception) {}
    }

    private fun parseTicketJson(obj: JSONObject): SupportTicket {
        return SupportTicket(
            id = obj.optString("id"),
            ticketNumber = obj.optInt("ticket_number", 100),
            anilistId = obj.optString("anilist_id"),
            anilistUsername = obj.optString("anilist_username", "AniList User"),
            anilistAvatarUrl = if (obj.isNull("anilist_avatar_url")) null else obj.optString("anilist_avatar_url").ifEmpty { null },
            profileId = if (obj.isNull("profile_id")) null else obj.optString("profile_id").ifEmpty { null },
            title = obj.optString("title", "Support Request"),
            category = obj.optString("category", "Issue / Bug"),
            description = obj.optString("description", ""),
            initialImageUrl = if (obj.isNull("initial_image_url")) null else obj.optString("initial_image_url").ifEmpty { null },
            status = obj.optString("status", "open"),
            priority = obj.optString("priority", "normal"),
            adminNotes = if (obj.isNull("admin_notes")) null else obj.optString("admin_notes").ifEmpty { null },
            lastMessageAt = obj.optString("last_message_at", ""),
            lastMessagePreview = if (obj.isNull("last_message_preview")) null else obj.optString("last_message_preview").ifEmpty { null },
            createdAt = obj.optString("created_at", ""),
            updatedAt = obj.optString("updated_at", "")
        )
    }
}
