package com.lagradost.cloudstream3.ui.animebox.settings

import android.content.Context
import android.content.SharedPreferences
import com.lagradost.cloudstream3.ui.animebox.AnimeBrief
import com.lagradost.cloudstream3.ui.animebox.history.WatchHistoryItem
import com.lagradost.cloudstream3.ui.animebox.history.WatchHistoryManager
import com.lagradost.cloudstream3.ui.animebox.library.LibraryManager
import com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager
import com.lagradost.cloudstream3.ui.animebox.profiles.UserProfile
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.json.JSONArray
import org.json.JSONObject

object AnimeBoxSettings {
    private const val PREFS_NAME = "animebox_settings_prefs"

    // Key Constants
    const val KEY_TRAILER_ENABLED = "setting_trailer_enabled"
    const val KEY_LOW_PERF_MODE = "setting_low_perf_mode"
    const val KEY_APP_THEME = "setting_app_theme"
    const val KEY_PLAYER_TIMELINE_THEME = "setting_player_timeline_theme"
    const val KEY_CUSTOM_TIMELINE_COLOR = "setting_custom_timeline_color"
    const val KEY_SKIP_INTRO_THEME = "setting_skip_intro_theme"
    const val KEY_SKIP_INTRO_ENABLED = "setting_skip_intro_enabled"
    const val KEY_BRIGHTNESS_MODE = "setting_brightness_mode"
    const val KEY_VOLUME_MODE = "setting_volume_mode"
    const val KEY_EPISODE_VIEW_MODE = "setting_episode_view_mode"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // --- Trailers ---
    fun isTrailerEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_TRAILER_ENABLED, true)
    }

    fun setTrailerEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_TRAILER_ENABLED, enabled).apply()
    }

    // --- Low Performance Mode ---
    fun isLowPerformanceMode(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_LOW_PERF_MODE, false)
    }

    fun setLowPerformanceMode(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_LOW_PERF_MODE, enabled).apply()
    }

    // --- App Theme ---
    // Options: "lavender" (default), "cyan", "crimson", "emerald", "amber", "white"
    fun getAppTheme(context: Context): String {
        return getPrefs(context).getString(KEY_APP_THEME, "lavender") ?: "lavender"
    }

    fun setAppTheme(context: Context, theme: String) {
        getPrefs(context).edit().putString(KEY_APP_THEME, theme).apply()
    }

    // --- Player Timeline Accent Color Theme ---
    // Options: "lavender" (default), "red", "cyan", "gold", "green", "white", "custom"
    fun getPlayerTimelineTheme(context: Context): String {
        return getPrefs(context).getString(KEY_PLAYER_TIMELINE_THEME, "lavender") ?: "lavender"
    }

    fun setPlayerTimelineTheme(context: Context, theme: String) {
        getPrefs(context).edit().putString(KEY_PLAYER_TIMELINE_THEME, theme).apply()
    }

    fun getCustomTimelineColor(context: Context): String {
        return getPrefs(context).getString(KEY_CUSTOM_TIMELINE_COLOR, "#D0BCFF") ?: "#D0BCFF"
    }

    fun setCustomTimelineColor(context: Context, hexColor: String) {
        getPrefs(context).edit().putString(KEY_CUSTOM_TIMELINE_COLOR, hexColor).apply()
    }

    // --- Skip Intro / Outro Button Theme ---
    // Options: "lavender" (Default), "gold", "cyan", "white", "red"
    fun getSkipIntroTheme(context: Context): String {
        return getPrefs(context).getString(KEY_SKIP_INTRO_THEME, "lavender") ?: "lavender"
    }

    fun setSkipIntroTheme(context: Context, theme: String) {
        getPrefs(context).edit().putString(KEY_SKIP_INTRO_THEME, theme).apply()
    }

    // --- Skip Intro Enabled ---
    fun isSkipIntroEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SKIP_INTRO_ENABLED, true)
    }

    fun setSkipIntroEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SKIP_INTRO_ENABLED, enabled).apply()
    }

    // --- Brightness Control Mode ---
    // Options: "hidden" (default), "gesture"
    fun getBrightnessMode(context: Context): String {
        return getPrefs(context).getString(KEY_BRIGHTNESS_MODE, "hidden") ?: "hidden"
    }

    fun setBrightnessMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_BRIGHTNESS_MODE, mode).apply()
    }

    // --- Volume Control Mode ---
    // Options: "hidden" (default), "gesture"
    fun getVolumeMode(context: Context): String {
        return getPrefs(context).getString(KEY_VOLUME_MODE, "hidden") ?: "hidden"
    }

    fun setVolumeMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_VOLUME_MODE, mode).apply()
    }

    // --- Detail Page Default Episode View Mode ---
    // Options: "image" (default), "number"
    fun getDefaultEpisodeViewMode(context: Context): String {
        return getPrefs(context).getString(KEY_EPISODE_VIEW_MODE, "image") ?: "image"
    }

    fun setDefaultEpisodeViewMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_EPISODE_VIEW_MODE, mode).apply()
    }

    // ------------------------------------------------------------------------
    // DATA EXPORT & IMPORT UTILITIES
    // ------------------------------------------------------------------------

    /**
     * Export all app data (profiles, watchlist/library, watch history, settings) as a JSON string
     */
    fun exportDataToJson(context: Context): String {
        val root = JSONObject()
        root.put("appName", "AnimeBox")
        root.put("schemaVersion", 1)
        root.put("exportTimestamp", System.currentTimeMillis())

        // 1. Profiles
        val profiles = ProfileManager.getProfiles(context)
        val activeProfileId = ProfileManager.getActiveProfile(context)
        val profilesArr = JSONArray()
        for (p in profiles) {
            val pObj = JSONObject()
            pObj.put("id", p.id)
            pObj.put("name", p.name)
            pObj.put("avatarUrl", p.avatarUrl)
            profilesArr.put(pObj)
        }
        root.put("activeProfileId", activeProfileId)
        root.put("profiles", profilesArr)

        // 2. Per-profile Library & History
        val libraryData = JSONObject()
        val historyData = JSONObject()
        for (p in profiles) {
            val libPrefs = context.getSharedPreferences("AnimeBoxLibrary_${p.id}", Context.MODE_PRIVATE)
            val libJsonStr = libPrefs.getString("library_list", null)
            if (libJsonStr != null) {
                libraryData.put(p.id, libJsonStr)
            }

            val histPrefs = context.getSharedPreferences("AnimeBoxHistory_${p.id}", Context.MODE_PRIVATE)
            val histJsonStr = histPrefs.getString("history_list", null)
            if (histJsonStr != null) {
                historyData.put(p.id, histJsonStr)
            }
        }
        root.put("libraryPerProfile", libraryData)
        root.put("historyPerProfile", historyData)

        // 3. Global App Settings
        val settingsObj = JSONObject()
        settingsObj.put(KEY_TRAILER_ENABLED, isTrailerEnabled(context))
        settingsObj.put(KEY_LOW_PERF_MODE, isLowPerformanceMode(context))
        settingsObj.put(KEY_APP_THEME, getAppTheme(context))
        settingsObj.put(KEY_PLAYER_TIMELINE_THEME, getPlayerTimelineTheme(context))
        settingsObj.put(KEY_CUSTOM_TIMELINE_COLOR, getCustomTimelineColor(context))
        settingsObj.put(KEY_SKIP_INTRO_THEME, getSkipIntroTheme(context))
        settingsObj.put(KEY_SKIP_INTRO_ENABLED, isSkipIntroEnabled(context))
        settingsObj.put(KEY_BRIGHTNESS_MODE, getBrightnessMode(context))
        settingsObj.put(KEY_VOLUME_MODE, getVolumeMode(context))
        settingsObj.put(KEY_EPISODE_VIEW_MODE, getDefaultEpisodeViewMode(context))
        root.put("settings", settingsObj)

        return root.toString(2)
    }

    /**
     * Import app data from a JSON string. Validates schema and returns Result.
     */
    fun importDataFromJson(context: Context, jsonStr: String): Result<Boolean> {
        return try {
            val root = JSONObject(jsonStr)

            // Strict format validation
            val appName = root.optString("appName")
            if (!root.has("appName") || (appName != "AnimeBox" && appName != "Animexera") || !root.has("profiles")) {
                return Result.failure(IllegalArgumentException("Invalid file format: Not a valid AnimeBox backup file."))
            }

            // 1. Restore Profiles
            val profilesArr = root.getJSONArray("profiles")
            val importedProfiles = mutableListOf<UserProfile>()
            for (i in 0 until profilesArr.length()) {
                val pObj = profilesArr.getJSONObject(i)
                importedProfiles.add(
                    UserProfile(
                        pObj.getString("id"),
                        pObj.getString("name"),
                        pObj.optString("avatarUrl", "")
                    )
                )
            }
            if (importedProfiles.isNotEmpty()) {
                ProfileManager.saveProfiles(context, importedProfiles)
            }
            if (root.has("activeProfileId")) {
                ProfileManager.setActiveProfile(context, root.getString("activeProfileId"))
            }

            // 2. Restore Library
            if (root.has("libraryPerProfile")) {
                val libObj = root.getJSONObject("libraryPerProfile")
                val keys = libObj.keys()
                while (keys.hasNext()) {
                    val pId = keys.next()
                    val libStr = libObj.getString(pId)
                    val libPrefs = context.getSharedPreferences("AnimeBoxLibrary_$pId", Context.MODE_PRIVATE)
                    libPrefs.edit().putString("library_list", libStr).apply()
                }
            }

            // 3. Restore History
            if (root.has("historyPerProfile")) {
                val histObj = root.getJSONObject("historyPerProfile")
                val keys = histObj.keys()
                while (keys.hasNext()) {
                    val pId = keys.next()
                    val histStr = histObj.getString(pId)
                    val histPrefs = context.getSharedPreferences("AnimeBoxHistory_$pId", Context.MODE_PRIVATE)
                    histPrefs.edit().putString("history_list", histStr).apply()
                }
            }

            // 4. Restore Global Settings
            if (root.has("settings")) {
                val settingsObj = root.getJSONObject("settings")
                setTrailerEnabled(context, settingsObj.optBoolean(KEY_TRAILER_ENABLED, true))
                setLowPerformanceMode(context, settingsObj.optBoolean(KEY_LOW_PERF_MODE, false))
                setAppTheme(context, settingsObj.optString(KEY_APP_THEME, "lavender"))
                setPlayerTimelineTheme(context, settingsObj.optString(KEY_PLAYER_TIMELINE_THEME, "lavender"))
                setCustomTimelineColor(context, settingsObj.optString(KEY_CUSTOM_TIMELINE_COLOR, "#D0BCFF"))
                setSkipIntroTheme(context, settingsObj.optString(KEY_SKIP_INTRO_THEME, "lavender"))
                setSkipIntroEnabled(context, settingsObj.optBoolean(KEY_SKIP_INTRO_ENABLED, true))
                setBrightnessMode(context, settingsObj.optString(KEY_BRIGHTNESS_MODE, "hidden"))
                setVolumeMode(context, settingsObj.optString(KEY_VOLUME_MODE, "hidden"))
                setDefaultEpisodeViewMode(context, settingsObj.optString(KEY_EPISODE_VIEW_MODE, "image"))
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
