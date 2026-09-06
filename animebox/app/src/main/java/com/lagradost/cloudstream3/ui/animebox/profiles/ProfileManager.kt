package com.lagradost.cloudstream3.ui.animebox.profiles

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class UserProfile(
    val id: String,
    val name: String,
    val avatarUrl: String
)

object ProfileManager {
    private const val PREFS_NAME = "animebox_profiles"
    private const val KEY_ACTIVE_PROFILE = "active_profile_id"
    private const val KEY_PROFILES_LIST = "profiles_list"

    val profileRevisionFlow = kotlinx.coroutines.flow.MutableStateFlow(0L)

    fun notifyProfilesChanged() {
        profileRevisionFlow.value = System.currentTimeMillis()
    }

    // Switch profile context
    fun setActiveProfile(context: Context, profileId: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_ACTIVE_PROFILE, profileId).apply()
        notifyProfilesChanged()
    }

    fun getActiveProfile(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_PROFILE, "guest") ?: "guest"
    }

    fun getProfiles(context: Context): List<UserProfile> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val guestAvatar = "android.resource://" + context.packageName + "/drawable/images"
        val guestProfile = UserProfile("guest", "Guest", guestAvatar)
        
        val jsonStr = prefs.getString(KEY_PROFILES_LIST, null)
        if (jsonStr.isNullOrEmpty()) {
            val defaultList = listOf(guestProfile)
            saveProfiles(context, defaultList)
            return defaultList
        }
        val list = mutableListOf<UserProfile>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(UserProfile(
                    obj.getString("id"),
                    obj.getString("name"),
                    obj.getString("avatarUrl")
                ))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (list.isEmpty()) {
            return listOf(guestProfile)
        }
        return list
    }

    fun saveProfiles(context: Context, profiles: List<UserProfile>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val arr = JSONArray()
        for (profile in profiles) {
            val obj = JSONObject().apply {
                put("id", profile.id)
                put("name", profile.name)
                put("avatarUrl", profile.avatarUrl)
            }
            arr.put(obj)
        }
        prefs.edit().putString(KEY_PROFILES_LIST, arr.toString()).apply()
        notifyProfilesChanged()
    }

    fun addProfile(context: Context, name: String, avatarUrl: String) {
        val current = getProfiles(context).toMutableList()
        val newId = "profile_" + System.currentTimeMillis()
        current.add(UserProfile(newId, name, avatarUrl))
        saveProfiles(context, current)
    }

    fun deleteProfile(context: Context, profileId: String) {
        val current = getProfiles(context).filterNot { it.id == profileId }
        if (current.isNotEmpty()) {
            saveProfiles(context, current)
            if (getActiveProfile(context) == profileId) {
                setActiveProfile(context, current.first().id)
            }
        }
    }
}
