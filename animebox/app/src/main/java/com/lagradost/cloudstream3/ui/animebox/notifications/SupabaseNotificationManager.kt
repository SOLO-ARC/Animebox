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
    private const val KEY_CLEARED_IDS = "cleared_notification_ids"

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

    private fun getClearedIds(context: Context): MutableSet<String> {
        val set = getPrefs(context).getStringSet(KEY_CLEARED_IDS, emptySet()) ?: emptySet()
        return set.toMutableSet()
    }

    private fun saveClearedIds(context: Context, ids: Set<String>) {
        getPrefs(context).edit().putStringSet(KEY_CLEARED_IDS, ids).apply()
    }

    /**
     * Fetch latest notifications from Supabase REST API and sync with local read status and filter for active profile.
     */
    suspend fun fetchNotifications(context: Context, profileId: String? = null): List<SupabaseNotification> = withContext(Dispatchers.IO) {
        val activeProf = profileId ?: com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager.getActiveProfile(context)
        val readIds = getReadIds(context)
        val clearedIds = getClearedIds(context)
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
                    val rawList = mutableListOf<SupabaseNotification>()

                    for (i in 0 until jsonArray.length()) {
                        val obj = jsonArray.getJSONObject(i)
                        val id = obj.optString("id", "")
                        if (id.isEmpty() || clearedIds.contains(id)) continue

                        val isReadLocally = readIds.contains(id) || obj.optBoolean("is_read", false)
                        val notif = SupabaseNotification(
                            id = id,
                            userId = if (obj.isNull("user_id")) null else obj.optString("user_id").ifEmpty { null },
                            title = obj.optString("title", "Notification"),
                            message = obj.optString("message", ""),
                            imageUrl = if (obj.isNull("image_url")) null else obj.optString("image_url").ifEmpty { null },
                            link = if (obj.isNull("link")) null else obj.optString("link").ifEmpty { null },
                            isRead = isReadLocally,
                            createdAt = obj.optString("created_at", "")
                        )
                        rawList.add(notif)
                    }

                    // Preserve any local notifications (e.g. schedule alerts) already in cache
                    val existingLocal = getAllCachedNotifications(context).filter { it.id.startsWith("schedule_alert_") && !clearedIds.contains(it.id) }
                    val merged = mutableListOf<SupabaseNotification>()
                    merged.addAll(existingLocal)
                    for (item in rawList) {
                        if (merged.none { it.id == item.id }) {
                            merged.add(item)
                        }
                    }

                    // Cache to SharedPreferences
                    saveNotificationsToCache(context, merged)
                    return@withContext filterForProfile(context, activeProf, merged)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Return cached notifications on network failure
        return@withContext getCachedNotifications(context, activeProf)
    }

    /**
     * Read all cached notifications from local storage (without profile filter).
     */
    fun getAllCachedNotifications(context: Context): List<SupabaseNotification> {
        val readIds = getReadIds(context)
        val clearedIds = getClearedIds(context)
        val raw = getPrefs(context).getString(KEY_NOTIFICATIONS_CACHE, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<SupabaseNotification>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.optString("id")
                if (id.isEmpty() || clearedIds.contains(id)) continue
                list.add(
                    SupabaseNotification(
                        id = id,
                        userId = if (obj.isNull("userId")) null else obj.optString("userId").ifEmpty { null },
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

    /**
     * Read cached notifications from local storage filtered for active profile.
     */
    fun getCachedNotifications(context: Context, profileId: String? = null): List<SupabaseNotification> {
        val activeProf = profileId ?: com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager.getActiveProfile(context)
        val all = getAllCachedNotifications(context)
        return filterForProfile(context, activeProf, all)
    }

    fun filterForProfile(context: Context, profileId: String, list: List<SupabaseNotification>): List<SupabaseNotification> {
        val anilistUser = com.lagradost.cloudstream3.ui.animebox.sync.AnimeBoxAccountSyncManager.getAniListUser(context, profileId)

        return list.filter { notif ->
            val target = notif.userId?.trim()
            if (target.isNullOrEmpty() || target.equals("all", ignoreCase = true) || target.equals("broadcast", ignoreCase = true)) {
                true
            } else if (target.equals(profileId, ignoreCase = true)) {
                true
            } else if (anilistUser != null && (
                target.equals(anilistUser.id, ignoreCase = true) ||
                target.equals(anilistUser.username, ignoreCase = true) ||
                target.equals("anilist_${anilistUser.id}", ignoreCase = true)
            )) {
                true
            } else {
                false
            }
        }
    }

    fun getUnreadCount(context: Context, profileId: String? = null): Int {
        val list = getCachedNotifications(context, profileId)
        return list.count { !it.isRead }
    }

    /**
     * Inject a local notification (e.g. Schedule episode release alert) into the cache.
     */
    fun addLocalNotification(context: Context, notification: SupabaseNotification) {
        val clearedIds = getClearedIds(context)
        if (clearedIds.contains(notification.id)) return

        val cached = getAllCachedNotifications(context).toMutableList()
        if (cached.any { it.id == notification.id }) return

        cached.add(0, notification)
        saveNotificationsToCache(context, cached)
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

        val cached = getAllCachedNotifications(context).map {
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
        val cached = getAllCachedNotifications(context)
        val readIds = getReadIds(context)
        readIds.addAll(cached.map { it.id })
        saveReadIds(context, readIds)

        val updated = cached.map { it.copy(isRead = true) }
        saveNotificationsToCache(context, updated)
    }

    /**
     * Clear all notifications locally and persist cleared IDs to prevent them from reappearing.
     */
    fun clearAllNotifications(context: Context) {
        val cached = getAllCachedNotifications(context)
        val clearedIds = getClearedIds(context)
        clearedIds.addAll(cached.map { it.id })
        saveClearedIds(context, clearedIds)
        getPrefs(context).edit().remove(KEY_NOTIFICATIONS_CACHE).apply()
    }
}
