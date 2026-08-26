package com.lagradost.cloudstream3.ui.animebox.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager
import com.lagradost.cloudstream3.ui.animebox.profiles.UserProfile
import org.json.JSONArray
import org.json.JSONObject

object AnimeBoxThemeHelper {
    val COLOR_CRIMSON = Color(0xFFE50914)   // Crimson Red
    val COLOR_LIGHT_RED = Color(0xFFFF5252) // Light Red
    val COLOR_LAVENDER = Color(0xFFD0BCFF)  // Lavender Purple
    val COLOR_CYAN = Color(0xFF48CAE4)      // Ocean Cyan
    val COLOR_EMERALD = Color(0xFF57CC99)   // Emerald Green
    val COLOR_AMBER = Color(0xFFFFB703)     // Golden Amber

    fun getPrimaryColor(context: Context): Color {
        return when (AnimeBoxSettings.getAppTheme(context)) {
            "crimson" -> COLOR_CRIMSON
            "light_red", "red" -> COLOR_LIGHT_RED
            "cyan" -> COLOR_CYAN
            "emerald" -> COLOR_EMERALD
            "amber" -> COLOR_AMBER
            "lavender" -> COLOR_LAVENDER
            else -> COLOR_LAVENDER
        }
    }
}

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
    const val KEY_DNS_MODE = "setting_dns_mode"
    const val KEY_CUSTOM_DNS_URL = "setting_custom_dns_url"
    const val KEY_SHOW_FALLBACK_DIALOGS = "setting_show_fallback_dialogs"
    const val KEY_REMEMBER_PLAYBACK_PREFS = "setting_remember_playback_prefs"
    const val KEY_LAST_USED_AUDIO = "setting_last_used_audio"
    const val KEY_LAST_USED_SUB_MODE = "setting_last_used_sub_mode"
    const val KEY_SEEK_DURATION = "setting_seek_duration"
    const val KEY_AUTOPLAY_NEXT_EPISODE = "setting_autoplay_next_episode"
    const val KEY_AUTOPLAY_PREVIEWS = "setting_autoplay_previews"
    const val KEY_MATURITY_RATING = "setting_maturity_rating"
    const val KEY_DISPLAY_LANGUAGE = "setting_display_language"

    private fun getPrefs(context: Context): SharedPreferences {
        val activeProfileId = ProfileManager.getActiveProfile(context)
        return context.getSharedPreferences("${PREFS_NAME}_$activeProfileId", Context.MODE_PRIVATE)
    }

    // --- Stream & Audio Fallback Dialogs ---
    fun isShowFallbackDialogsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SHOW_FALLBACK_DIALOGS, true)
    }

    fun setShowFallbackDialogsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SHOW_FALLBACK_DIALOGS, enabled).apply()
    }

    // --- Remember Playback Audio & Subtitle Preferences ---
    fun isRememberPlaybackPrefsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_REMEMBER_PLAYBACK_PREFS, true)
    }

    fun setRememberPlaybackPrefsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_REMEMBER_PLAYBACK_PREFS, enabled).apply()
    }

    fun getLastUsedAudio(context: Context): String {
        return getPrefs(context).getString(KEY_LAST_USED_AUDIO, "Japanese (Original)") ?: "Japanese (Original)"
    }

    fun setLastUsedAudio(context: Context, audio: String) {
        getPrefs(context).edit().putString(KEY_LAST_USED_AUDIO, audio).apply()
    }

    fun getLastUsedSubMode(context: Context): String {
        return getPrefs(context).getString(KEY_LAST_USED_SUB_MODE, "English (VTT)") ?: "English (VTT)"
    }

    fun setLastUsedSubMode(context: Context, subMode: String) {
        getPrefs(context).edit().putString(KEY_LAST_USED_SUB_MODE, subMode).apply()
    }

    // --- DNS Mode ---
    fun getDnsMode(context: Context): String {
        return getPrefs(context).getString(KEY_DNS_MODE, "default") ?: "default"
    }

    fun setDnsMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_DNS_MODE, mode).apply()
    }

    fun getCustomDnsUrl(context: Context): String {
        return getPrefs(context).getString(KEY_CUSTOM_DNS_URL, "") ?: ""
    }

    fun setCustomDnsUrl(context: Context, url: String) {
        getPrefs(context).edit().putString(KEY_CUSTOM_DNS_URL, url).apply()
    }

    // --- Trailers ---
    fun isTrailerEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_TRAILER_ENABLED, false)
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
    fun getAppTheme(context: Context): String {
        return getPrefs(context).getString(KEY_APP_THEME, "lavender") ?: "lavender"
    }

    fun setAppTheme(context: Context, theme: String) {
        getPrefs(context).edit().putString(KEY_APP_THEME, theme).apply()
        // Automatically sync player timeline theme with the selected app theme
        setPlayerTimelineTheme(context, theme)
    }

    // --- Player Timeline Accent Color Theme ---
    fun getPlayerTimelineTheme(context: Context): String {
        val currentAppTheme = getAppTheme(context)
        return getPrefs(context).getString(KEY_PLAYER_TIMELINE_THEME, currentAppTheme) ?: currentAppTheme
    }

    fun setPlayerTimelineTheme(context: Context, theme: String) {
        getPrefs(context).edit().putString(KEY_PLAYER_TIMELINE_THEME, theme).apply()
    }

    fun getCustomTimelineColor(context: Context): String {
        return getPrefs(context).getString(KEY_CUSTOM_TIMELINE_COLOR, "#D0BCFF") ?: "#D0BCFF"
    }

    fun setCustomTimelineColor(context: Context, hex: String) {
        getPrefs(context).edit().putString(KEY_CUSTOM_TIMELINE_COLOR, hex).apply()
    }

    // --- Skip Intro & Outro Theme ---
    fun getSkipIntroTheme(context: Context): String {
        return getPrefs(context).getString(KEY_SKIP_INTRO_THEME, "lavender") ?: "lavender"
    }

    fun setSkipIntroTheme(context: Context, theme: String) {
        getPrefs(context).edit().putString(KEY_SKIP_INTRO_THEME, theme).apply()
    }

    // --- Skip Intro Toggle ---
    fun isSkipIntroEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SKIP_INTRO_ENABLED, true)
    }

    fun setSkipIntroEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SKIP_INTRO_ENABLED, enabled).apply()
    }

    // --- Brightness Control Mode ---
    fun getBrightnessMode(context: Context): String {
        return getPrefs(context).getString(KEY_BRIGHTNESS_MODE, "gesture") ?: "gesture"
    }

    fun setBrightnessMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_BRIGHTNESS_MODE, mode).apply()
    }

    // --- Volume Control Mode ---
    fun getVolumeMode(context: Context): String {
        return getPrefs(context).getString(KEY_VOLUME_MODE, "gesture") ?: "gesture"
    }

    fun setVolumeMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_VOLUME_MODE, mode).apply()
    }

    // --- Default Episode View Mode ---
    fun getDefaultEpisodeViewMode(context: Context): String {
        return getPrefs(context).getString(KEY_EPISODE_VIEW_MODE, "image") ?: "image"
    }

    fun setDefaultEpisodeViewMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_EPISODE_VIEW_MODE, mode).apply()
    }

    // --- Double Tap Seek Duration ---
    fun getSeekDuration(context: Context): Int {
        return getPrefs(context).getInt(KEY_SEEK_DURATION, 10)
    }

    fun setSeekDuration(context: Context, seconds: Int) {
        getPrefs(context).edit().putInt(KEY_SEEK_DURATION, seconds).apply()
    }

    // --- Autoplay Next Episode ---
    fun isAutoplayNextEpisodeEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTOPLAY_NEXT_EPISODE, true)
    }

    fun setAutoplayNextEpisodeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTOPLAY_NEXT_EPISODE, enabled).apply()
    }

    // --- Autoplay Previews & Trailers ---
    fun isAutoplayPreviewsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTOPLAY_PREVIEWS, false)
    }

    fun setAutoplayPreviewsEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTOPLAY_PREVIEWS, enabled).apply()
    }

    // --- Maturity Rating ---
    fun getMaturityRating(context: Context): String {
        return getPrefs(context).getString(KEY_MATURITY_RATING, "With restrictions") ?: "With restrictions"
    }

    fun setMaturityRating(context: Context, rating: String) {
        getPrefs(context).edit().putString(KEY_MATURITY_RATING, rating).apply()
    }

    // --- Display Language ---
    fun getDisplayLanguage(context: Context): String {
        return getPrefs(context).getString(KEY_DISPLAY_LANGUAGE, "English") ?: "English"
    }

    fun setDisplayLanguage(context: Context, lang: String) {
        getPrefs(context).edit().putString(KEY_DISPLAY_LANGUAGE, lang).apply()
    }

    // --- Dynamic Accent Palette Helpers ---
    fun getAppThemeColor(context: Context): Color {
        return AnimeBoxThemeHelper.getPrimaryColor(context)
    }

    /**
     * Export all app data (Profiles, Complete Library per profile, Complete History per profile with progress, and Settings) into JSON.
     */
    fun exportDataToJson(context: Context): String {
        val root = JSONObject()
        root.put("appName", "FireFly")
        root.put("version", 2)
        root.put("exportTimestamp", System.currentTimeMillis())

        // 1. Profiles
        val profiles = ProfileManager.getProfiles(context)
        val profilesArr = JSONArray()
        profiles.forEach { p ->
            val pObj = JSONObject()
            pObj.put("id", p.id)
            pObj.put("name", p.name)
            pObj.put("avatarUrl", p.avatarUrl)
            profilesArr.put(pObj)
        }
        root.put("profiles", profilesArr)
        root.put("activeProfileId", ProfileManager.getActiveProfile(context))

        // 2. Library & History per profile (All categories & continue watching records)
        val libObj = JSONObject()
        val histObj = JSONObject()
        val profSettingsObj = JSONObject()
        profiles.forEach { p ->
            val libPrefs = context.getSharedPreferences("AnimeBoxLibrary_${p.id}", Context.MODE_PRIVATE)
            val histPrefs = context.getSharedPreferences("AnimeBoxHistory_${p.id}", Context.MODE_PRIVATE)
            val pPrefs = context.getSharedPreferences("AnimeBoxPrefs_${p.id}", Context.MODE_PRIVATE)
            libObj.put(p.id, libPrefs.getString("library_list", "[]"))
            histObj.put(p.id, histPrefs.getString("history_list", "[]"))

            // Profile specific preferences
            val ps = JSONObject()
            pPrefs.all.forEach { (k, v) ->
                ps.put(k, v)
            }
            profSettingsObj.put(p.id, ps)
        }
        root.put("libraryPerProfile", libObj)
        root.put("historyPerProfile", histObj)
        root.put("profileSettings", profSettingsObj)

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
        settingsObj.put(KEY_AUTOPLAY_NEXT_EPISODE, isAutoplayNextEpisodeEnabled(context))
        settingsObj.put(KEY_AUTOPLAY_PREVIEWS, isAutoplayPreviewsEnabled(context))
        settingsObj.put(KEY_MATURITY_RATING, getMaturityRating(context))
        settingsObj.put(KEY_DISPLAY_LANGUAGE, getDisplayLanguage(context))
        settingsObj.put("dns_mode", getDnsMode(context))

        val globalPrefs = context.getSharedPreferences("AnimeBoxPrefs", Context.MODE_PRIVATE)
        settingsObj.put("wifi_only_downloads", globalPrefs.getBoolean("wifi_only_downloads", false))
        settingsObj.put("smart_downloads", globalPrefs.getBoolean("smart_downloads", true))
        settingsObj.put("download_quality", globalPrefs.getString("download_quality", "1080p (Standard)"))
        settingsObj.put("download_location", globalPrefs.getString("download_location", "Internal Storage"))
        settingsObj.put("sub_font_size", globalPrefs.getFloat("sub_font_size", 18f))
        settingsObj.put("sub_color", globalPrefs.getString("sub_color", "White"))
        settingsObj.put("sub_bg_opacity", globalPrefs.getInt("sub_bg_opacity", 50))
        settingsObj.put("sub_edge_style", globalPrefs.getString("sub_edge_style", "Drop Shadow"))
        root.put("settings", settingsObj)

        // 4. Tag Orders
        val tagPrefs = context.getSharedPreferences("AnimeBox_Tag_Orders", Context.MODE_PRIVATE)
        val tagObj = JSONObject()
        tagPrefs.all.forEach { (k, v) ->
            tagObj.put(k, v)
        }
        root.put("tagOrders", tagObj)

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
            if (!root.has("appName") || (appName != "FireFly" && appName != "AnimeBox" && appName != "Animexera") || !root.has("profiles")) {
                return Result.failure(IllegalArgumentException("Invalid file format: Not a valid FireFly backup file."))
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

            // 2. Restore Library per profile
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

            // 3. Restore History / Continue Watching per profile
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

            // 4. Restore Profile-Specific Settings
            if (root.has("profileSettings")) {
                val profObj = root.getJSONObject("profileSettings")
                val pKeys = profObj.keys()
                while (pKeys.hasNext()) {
                    val pId = pKeys.next()
                    val pData = profObj.getJSONObject(pId)
                    val pPrefs = context.getSharedPreferences("AnimeBoxPrefs_$pId", Context.MODE_PRIVATE).edit()
                    val itemKeys = pData.keys()
                    while (itemKeys.hasNext()) {
                        val ik = itemKeys.next()
                        val v = pData.get(ik)
                        when (v) {
                            is Boolean -> pPrefs.putBoolean(ik, v)
                            is Int -> pPrefs.putInt(ik, v)
                            is Long -> pPrefs.putLong(ik, v)
                            is Float -> pPrefs.putFloat(ik, v)
                            is Double -> pPrefs.putFloat(ik, v.toFloat())
                            else -> pPrefs.putString(ik, v.toString())
                        }
                    }
                    pPrefs.apply()
                }
            }

            // 5. Restore Global Settings
            if (root.has("settings")) {
                val settingsObj = root.getJSONObject("settings")
                setTrailerEnabled(context, settingsObj.optBoolean(KEY_TRAILER_ENABLED, false))
                setLowPerformanceMode(context, settingsObj.optBoolean(KEY_LOW_PERF_MODE, false))
                val theme = settingsObj.optString(KEY_APP_THEME, "lavender")
                setAppTheme(context, theme)
                setPlayerTimelineTheme(context, settingsObj.optString(KEY_PLAYER_TIMELINE_THEME, theme))
                setCustomTimelineColor(context, settingsObj.optString(KEY_CUSTOM_TIMELINE_COLOR, "#D0BCFF"))
                setSkipIntroTheme(context, settingsObj.optString(KEY_SKIP_INTRO_THEME, "lavender"))
                setSkipIntroEnabled(context, settingsObj.optBoolean(KEY_SKIP_INTRO_ENABLED, true))
                setBrightnessMode(context, settingsObj.optString(KEY_BRIGHTNESS_MODE, "gesture"))
                setVolumeMode(context, settingsObj.optString(KEY_VOLUME_MODE, "gesture"))
                setDefaultEpisodeViewMode(context, settingsObj.optString(KEY_EPISODE_VIEW_MODE, "image"))
                setAutoplayNextEpisodeEnabled(context, settingsObj.optBoolean(KEY_AUTOPLAY_NEXT_EPISODE, true))
                setAutoplayPreviewsEnabled(context, settingsObj.optBoolean(KEY_AUTOPLAY_PREVIEWS, false))
                setMaturityRating(context, settingsObj.optString(KEY_MATURITY_RATING, "With restrictions"))
                setDisplayLanguage(context, settingsObj.optString(KEY_DISPLAY_LANGUAGE, "English"))
                if (settingsObj.has("dns_mode")) {
                    setDnsMode(context, settingsObj.optString("dns_mode", "system"))
                }

                val globalPrefs = context.getSharedPreferences("AnimeBoxPrefs", Context.MODE_PRIVATE).edit()
                if (settingsObj.has("wifi_only_downloads")) {
                    globalPrefs.putBoolean("wifi_only_downloads", settingsObj.getBoolean("wifi_only_downloads"))
                }
                if (settingsObj.has("smart_downloads")) {
                    globalPrefs.putBoolean("smart_downloads", settingsObj.getBoolean("smart_downloads"))
                }
                if (settingsObj.has("download_quality")) {
                    globalPrefs.putString("download_quality", settingsObj.getString("download_quality"))
                }
                if (settingsObj.has("download_location")) {
                    globalPrefs.putString("download_location", settingsObj.getString("download_location"))
                }
                if (settingsObj.has("sub_font_size")) {
                    globalPrefs.putFloat("sub_font_size", settingsObj.getDouble("sub_font_size").toFloat())
                }
                if (settingsObj.has("sub_color")) {
                    globalPrefs.putString("sub_color", settingsObj.getString("sub_color"))
                }
                if (settingsObj.has("sub_bg_opacity")) {
                    globalPrefs.putInt("sub_bg_opacity", settingsObj.getInt("sub_bg_opacity"))
                }
                if (settingsObj.has("sub_edge_style")) {
                    globalPrefs.putString("sub_edge_style", settingsObj.getString("sub_edge_style"))
                }
                globalPrefs.apply()
            }

            // 6. Restore Tag Orders
            if (root.has("tagOrders")) {
                val tagObj = root.getJSONObject("tagOrders")
                val tagPrefs = context.getSharedPreferences("AnimeBox_Tag_Orders", Context.MODE_PRIVATE).edit()
                val keys = tagObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    tagPrefs.putString(k, tagObj.getString(k))
                }
                tagPrefs.apply()
            }

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
