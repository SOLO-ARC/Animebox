package com.lagradost.cloudstream3.ui.animebox.notifications

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

data class SupabaseNotification(
    val id: String,
    val userId: String?,
    val title: String,
    val message: String,
    val imageUrl: String?,
    val link: String?,
    val isRead: Boolean,
    val createdAt: String
) {
    fun formattedDateTime(): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val cleanStr = if (createdAt.contains(".")) {
                createdAt.substringBefore(".")
            } else if (createdAt.contains("+")) {
                createdAt.substringBefore("+")
            } else if (createdAt.endsWith("Z")) {
                createdAt.removeSuffix("Z")
            } else {
                createdAt
            }
            val date = inputFormat.parse(cleanStr) ?: Date()
            val outputFormat = SimpleDateFormat("dd/MM/yyyy, HH:mm:ss", Locale.getDefault())
            outputFormat.format(date)
        } catch (_: Exception) {
            createdAt
        }
    }
}

object SupabaseNotificationManager {
    private const val SUPABASE_URL = "https://okiuzwsldqjrtuyqylhx.supabase.co"
    private const val SUPABASE_ANON_KEY = "sb_publishable_O6CrSYrDu-GilobI41fSMg_DG7IdSbf"
    private const val PREFS_NAME = "firefly_notifications_prefs"
    private const val KEY_NOTIFICATIONS_CACHE = "cached_notifications_json"
    private const val KEY_READ_IDS = "read_notification_ids"

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getReadIds(context: Context): MutableSet<String> {
        val set = getPrefs(context).getStringSet(KEY_READ_IDS, emptySet()) ?: emptySet()
        return set.toMutableSet()
    }

    private fun saveReadIds(context: Context, ids: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_READ_IDS, ids).apply()
    }

    /**
     * Fetch latest notifications from Supabase REST API and sync with local read status.
     */
    suspend fun fetchNotifications(context: Context): List<SupabaseNotification> = withContext(Dispatchers.IO) {
        val readIds = getReadIds(context)
        try {
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/notifications?select=*&order=created_at.desc")
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .header("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val responseBody = response.body?.string() ?: "[]"
                    val jsonArray = JSONArray(responseBody)
                    val resultList = mutableListOf<SupabaseNotification>()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val id = obj.optString("id", "")
                        if (id.isEmpty()) continue

                        val isReadLocally = readIds.contains(id) || obj.optBoolean("is_read", false)
                        val notif = SupabaseNotification(
                            id = id,
                            userId = if (obj.isNull("user_id")) null else obj.optString("user_id"),
                            title = obj.optString("title", "Notification"),
                            message = obj.optString("message", ""),
                            imageUrl = if (obj.isNull("image_url")) null else obj.optString("image_url").ifEmpty { null },
                            link = if (obj.isNull("link")) null else obj.optString("link").ifEmpty { null },
                            isRead = isReadLocally,
                            createdAt = obj.optString("created_at", "")
                        )
                        resultList.add(notif)
                    }

                    // Cache to SharedPreferences
                    saveNotificationsToCache(context, resultList)
                    return@withContext resultList
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Return cached notifications on network failure
        return@withContext getCachedNotifications(context)
    }

    /**
     * Read cached notifications from local storage.
     */
    fun getCachedNotifications(context: Context): List<SupabaseNotification> {
        val readIds = getReadIds(context)
        val raw = getPrefs(context).getString(KEY_NOTIFICATIONS_CACHE, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<SupabaseNotification>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id")
                list.add(
                    SupabaseNotification(
                        id = id,
                        userId = if (obj.isNull("userId")) null else obj.optString("userId"),
                        title = obj.optString("title", "Notification"),
                        message = obj.optString("message", ""),
                        imageUrl = if (obj.isNull("imageUrl")) null else obj.optString("imageUrl").ifEmpty { null },
                        link = if (obj.isNull("link")) null else obj.optString("link").ifEmpty { null },
                        isRead = readIds.contains(id) || obj.optBoolean("isRead", false),
                        createdAt = obj.optString("createdAt", "")
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getUnreadCount(context: Context): Int {
        val list = getCachedNotifications(context)
        return list.count { !it.isRead }
    }

    private fun saveNotificationsToCache(context: Context, list: List<SupabaseNotification>) {
        val array = JSONArray()
        list.forEach { notif ->
            val obj = JSONObject().apply {
                put("id", notif.id)
                put("userId", notif.userId)
                put("title", notif.title)
                put("message", notif.message)
                put("imageUrl", notif.imageUrl)
                put("link", notif.link)
                put("isRead", notif.isRead)
                put("createdAt", notif.createdAt)
            }
            array.put(obj)
        }
        getPrefs(context).edit().putString(KEY_NOTIFICATIONS_CACHE, array.toString()).apply()
    }

    /**
     * Mark a single notification as read.
     */
    suspend fun markAsRead(context: Context, id: String) = withContext(Dispatchers.IO) {
        val readIds = getReadIds(context)
        readIds.add(id)
        saveReadIds(context, readIds)

        val cached = getCachedNotifications(context).map {
            if (it.id == id) it.copy(isRead = true) else it
        }
        saveNotificationsToCache(context, cached)

        try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = "{\"is_read\": true}".toRequestBody(mediaType)
            val request = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/notifications?id=eq.$id")
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .patch(requestBody)
                .build()
            client.newCall(request).execute().close()
        } catch (_: Exception) {}
    }

    /**
     * Mark all notifications as read.
     */
    suspend fun markAllAsRead(context: Context) = withContext(Dispatchers.IO) {
        val cached = getCachedNotifications(context)
        val readIds = getReadIds(context)
        readIds.addAll(cached.map { it.id })
        saveReadIds(context, readIds)

        val updated = cached.map { it.copy(isRead = true) }
        saveNotificationsToCache(context, updated)
    }

    /**
     * Clear all notifications locally.
     */
    fun clearAllNotifications(context: Context) {
        getPrefs(context).edit().remove(KEY_NOTIFICATIONS_CACHE).apply()
    }
}
