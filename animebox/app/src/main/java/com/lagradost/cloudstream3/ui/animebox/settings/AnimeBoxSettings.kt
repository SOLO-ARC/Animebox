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
    val COLOR_LIGHT_RED = Color(0xFFF27A7D) // AniDash Soft Light Red / Coral
    val COLOR_LAVENDER = Color(0xFFD0BCFF)  // Lavender Purple
    val COLOR_CYAN = Color(0xFF48CAE4)      // Ocean Cyan
    val COLOR_EMERALD = Color(0xFF57CC99)   // Emerald Green
    val COLOR_AMBER = Color(0xFFFFB703)     // Golden Amber
    val COLOR_LIGHT_GREEN = Color(0xFF00D26A) // Vivid Light Green (Stream Player 2 Theme)

    val themeRevisionFlow = kotlinx.coroutines.flow.MutableStateFlow(0L)

    fun notifyThemeChanged() {
        themeRevisionFlow.value = System.currentTimeMillis()
    }

    val MotoGoogleSansFontFamily = androidx.compose.ui.text.font.FontFamily(
        androidx.compose.ui.text.font.Font(com.lagradost.cloudstream3.R.font.productsans_regular, androidx.compose.ui.text.font.FontWeight.Normal),
        androidx.compose.ui.text.font.Font(com.lagradost.cloudstream3.R.font.productsans_medium, androidx.compose.ui.text.font.FontWeight.Medium),
        androidx.compose.ui.text.font.Font(com.lagradost.cloudstream3.R.font.productsans_bold, androidx.compose.ui.text.font.FontWeight.Bold)
    )

    fun getPrimaryColor(context: Context): Color {
        return when (AnimeBoxSettings.getAppTheme(context)) {
            "crimson" -> COLOR_CRIMSON
            "light_red", "red" -> COLOR_LIGHT_RED
            "cyan" -> COLOR_CYAN
            "emerald" -> COLOR_EMERALD
            "amber" -> COLOR_AMBER
            "lavender" -> COLOR_LAVENDER
            "light_green", "green" -> COLOR_LIGHT_GREEN
            else -> COLOR_LIGHT_RED
        }
    }

    fun getNavbarBgColor(context: Context): Color {
        return when (AnimeBoxSettings.getNavbarBgTheme(context)) {
            "dark_black" -> Color(0xFF131417)
            else -> Color(0xFF1E2026) // "greyish" / classic slate as before (Default)
        }
    }

    fun getNavbarOutlineColor(context: Context): Color {
        return when (AnimeBoxSettings.getNavbarOutlineTheme(context)) {
            "yellow" -> Color(0xFFF0B836)
            else -> Color.White // "white" (Default)
        }
    }
}

object AnimeBoxSettings {
    private const val PREFS_NAME = "animebox_settings_prefs"
    val adultContentEnabledFlow = kotlinx.coroutines.flow.MutableStateFlow(false)

    val SUPPORTED_DISPLAY_LANGUAGES = listOf(
        "English", "Japanese", "Hindi", "Spanish", "French", "German",
        "Portuguese", "Indonesian", "Arabic", "Russian"
    )

    // Key Constants
    const val KEY_NAVBAR_BG_THEME = "setting_navbar_bg_theme" // "greyish" or "dark_black", default "greyish"
    const val KEY_NAVBAR_OUTLINE_THEME = "setting_navbar_outline_theme" // "white" or "yellow", default "white"

    const val KEY_TRAILER_ENABLED = "setting_trailer_enabled"
    const val KEY_LOW_PERF_MODE = "setting_low_perf_mode"
    const val KEY_APP_THEME = "setting_app_theme"
    const val KEY_PLAYER_STYLE = "setting_player_style"
    const val KEY_PLAYER_TIMELINE_THEME = "setting_player_timeline_theme"
    const val KEY_PLAYER2_TIMELINE_THEME = "setting_player2_timeline_theme"
    const val KEY_CUSTOM_TIMELINE_COLOR = "setting_custom_timeline_color"
    const val KEY_YOUTUBE_PLAYER_TIMELINE_THEME = "setting_yt_player_timeline_theme"
    const val KEY_YOUTUBE_PLAYER_CUSTOM_TIMELINE_COLOR = "setting_yt_player_custom_timeline_color"
    const val KEY_SKIP_INTRO_THEME = "setting_skip_intro_theme"
    const val KEY_SKIP_INTRO_ENABLED = "setting_skip_intro_enabled"
    const val KEY_BRIGHTNESS_MODE = "setting_brightness_mode"
    const val KEY_VOLUME_MODE = "setting_volume_mode"
    const val KEY_EPISODE_VIEW_MODE = "setting_episode_view_mode"
    const val KEY_DNS_MODE = "setting_dns_mode"
    const val KEY_CUSTOM_DNS_URL = "setting_custom_dns_url"
    const val KEY_FAST_DETAIL_LOADING = "setting_fast_detail_loading"
    const val KEY_SHOW_FALLBACK_DIALOGS = "setting_show_fallback_dialogs"
    const val KEY_REMEMBER_PLAYBACK_PREFS = "setting_remember_playback_prefs"
    const val KEY_EXTERNAL_PLAYER_ENABLED = "setting_external_player_enabled"
    const val KEY_LAST_USED_AUDIO = "setting_last_used_audio"
    const val KEY_LAST_USED_SUB_MODE = "setting_last_used_sub_mode"
    const val KEY_SEEK_DURATION = "setting_seek_duration"
    const val KEY_PLAYER_LAUNCH_MODE = "setting_player_launch_mode"
    const val LAUNCH_MODE_DIRECT = "direct"
    const val LAUNCH_MODE_WATCH_SCREEN = "watch_screen"
    const val KEY_AUTOPLAY_NEXT_EPISODE = "setting_autoplay_next_episode"
    const val KEY_AUTOPLAY_PREVIEWS = "setting_autoplay_previews"
    const val KEY_MATURITY_RATING = "setting_maturity_rating"
    const val KEY_DISPLAY_LANGUAGE = "setting_display_language"
    const val KEY_BLUR_EPISODE_SPOILERS = "setting_blur_episode_spoilers"
    const val KEY_CLUSTERED_WATCH_HISTORY = "setting_clustered_watch_history"
    const val KEY_SUB_OUTLINE_WIDTH = "setting_sub_outline_width"
    const val KEY_SUB_FONT_FAMILY = "setting_sub_font_family"
    const val KEY_CUSTOM_SUB_FONT_PATH = "setting_custom_sub_font_path"
    const val KEY_PLAYER_SCREENSHOT_CAMERA_ENABLED = "player_screenshot_camera_enabled"
    const val KEY_AUTO_RESUME_PLAYBACK = "setting_auto_resume_playback"
    const val KEY_NON_JAPANESE_SUB_OFF = "setting_non_japanese_sub_off"
    const val KEY_GROQ_API_KEY = "groq_cloud_api_key"
    const val KEY_DOWNLOAD_QUALITY = "download_quality"
    const val DEFAULT_DOWNLOAD_QUALITY = "ask"
    const val KEY_AUTO_MLKIT_TRANSLATION_LANGUAGE = "auto_mlkit_translation_language"
    const val KEY_ADULT_CONTENT_ENABLED = "setting_adult_content_enabled"
    const val KEY_HENTAI_FEATURE_UNLOCKED = "setting_hentai_feature_unlocked"

    fun getGroqApiKey(context: Context): String {
        val globalPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return globalPrefs.getString(KEY_GROQ_API_KEY, "")?.trim() ?: ""
    }

    fun setGroqApiKey(context: Context, key: String) {
        val globalPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        globalPrefs.edit().putString(KEY_GROQ_API_KEY, key.trim()).apply()
    }

    const val KEY_OPENROUTER_API_KEY = "openrouter_api_key"
    const val KEY_OPENROUTER_MODEL = "openrouter_model"
    const val DEFAULT_OPENROUTER_MODEL = "google/gemini-3.8-flash"

    fun getOpenRouterApiKey(context: Context): String {
        val globalPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return globalPrefs.getString(KEY_OPENROUTER_API_KEY, "")?.trim() ?: ""
    }

    fun setOpenRouterApiKey(context: Context, key: String) {
        val globalPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        globalPrefs.edit().putString(KEY_OPENROUTER_API_KEY, key.trim()).apply()
    }

    const val KEY_OPENROUTER_FREE_MODE = "openrouter_free_mode"

    fun isOpenRouterFreeMode(context: Context): Boolean {
        val globalPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return globalPrefs.getBoolean(KEY_OPENROUTER_FREE_MODE, false)
    }

    fun setOpenRouterFreeMode(context: Context, isFree: Boolean) {
        val globalPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = globalPrefs.edit().putBoolean(KEY_OPENROUTER_FREE_MODE, isFree)
        if (!isFree) {
            val saved = globalPrefs.getString(KEY_OPENROUTER_MODEL, "") ?: ""
            if (saved.contains(":free") || saved.isBlank() || saved == "google/gemini-2.0-flash-001") {
                editor.putString(KEY_OPENROUTER_MODEL, DEFAULT_OPENROUTER_MODEL)
            }
        } else {
            editor.putString(KEY_OPENROUTER_MODEL, DEFAULT_FREE_MODEL)
        }
        editor.apply()
    }

    const val DEFAULT_FREE_MODEL = "inclusionai/ling-3.0-flash-vl:free"

    fun getProModel(context: Context): String {
        val globalPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = globalPrefs.getString(KEY_OPENROUTER_MODEL, DEFAULT_OPENROUTER_MODEL)?.trim()
        if (saved.isNullOrBlank() || saved == "google/gemini-2.0-flash-001" || saved.contains(":free") || saved.contains("gemini-2.0-flash-exp")) {
            return DEFAULT_OPENROUTER_MODEL
        }
        return saved
    }

    fun getOpenRouterModel(context: Context): String {
        if (isOpenRouterFreeMode(context)) {
            return DEFAULT_FREE_MODEL
        }
        return getProModel(context)
    }

    fun setOpenRouterModel(context: Context, model: String) {
        val globalPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        globalPrefs.edit().putString(KEY_OPENROUTER_MODEL, model.trim()).apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        val activeProfileId = ProfileManager.getActiveProfile(context)
        return context.getSharedPreferences("${PREFS_NAME}_$activeProfileId", Context.MODE_PRIVATE)
    }

    fun isPlayerScreenshotEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_PLAYER_SCREENSHOT_CAMERA_ENABLED, true)
    }

    fun setPlayerScreenshotEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_PLAYER_SCREENSHOT_CAMERA_ENABLED, enabled).apply()
    }

    fun isBlurEpisodeSpoilersEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BLUR_EPISODE_SPOILERS, false)
    }

    fun setBlurEpisodeSpoilersEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BLUR_EPISODE_SPOILERS, enabled).apply()
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

    fun isExternalPlayerEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_EXTERNAL_PLAYER_ENABLED, false)

    fun setExternalPlayerEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_EXTERNAL_PLAYER_ENABLED, enabled).apply()
    }

    val DEFAULT_AUDIO_TRACKS = listOf(
        "Japanese (Original)",
        "English",
        "Hindi",
        "French (VF)",
        "Italian (ITA)",
        "Spanish (Latino)",
        "Spanish (Castellano)",
        "German (Deutsch)"
    )

    fun getLastUsedAudio(context: Context): String {
        return getPrefs(context).getString(KEY_LAST_USED_AUDIO, "Japanese (Original)") ?: "Japanese (Original)"
    }

    fun setLastUsedAudio(context: Context, audio: String) {
        getPrefs(context).edit().putString(KEY_LAST_USED_AUDIO, audio).apply()
    }

    fun getLastUsedSubMode(context: Context): String {
        val mode = getPrefs(context).getString(KEY_LAST_USED_SUB_MODE, "English (VTT)")
        return if (mode.isNullOrBlank() || mode.equals("Off", ignoreCase = true)) "English (VTT)" else mode
    }

    fun setLastUsedSubMode(context: Context, subMode: String) {
        if (!subMode.equals("Off", ignoreCase = true) && subMode.isNotBlank()) {
            getPrefs(context).edit().putString(KEY_LAST_USED_SUB_MODE, subMode).apply()
        }
    }

    // --- Auto Resume Playback ---
    fun isAutoResumeEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_RESUME_PLAYBACK, false)
    }

    fun setAutoResumeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_RESUME_PLAYBACK, enabled).apply()
    }

    // --- Non-Japanese Audio Subtitle Off Preference (Global setting from My Space) ---
    fun isNonJapaneseSubOff(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_NON_JAPANESE_SUB_OFF, false)
    }

    fun setNonJapaneseSubOff(context: Context, off: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_NON_JAPANESE_SUB_OFF, off).apply()
    }

    // --- 18+ Adult Anime Content (Default: Off, Locked by Redeem Code) ---
    fun isHentaiFeatureUnlocked(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_HENTAI_FEATURE_UNLOCKED, false)
    }

    fun setHentaiFeatureUnlocked(context: Context, unlocked: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_HENTAI_FEATURE_UNLOCKED, unlocked).apply()
        if (!unlocked) adultContentEnabledFlow.value = false
    }

    fun isAdultContentEnabled(context: Context): Boolean {
        val enabled = isHentaiFeatureUnlocked(context) && getPrefs(context).getBoolean(KEY_ADULT_CONTENT_ENABLED, false)
        adultContentEnabledFlow.value = enabled
        return enabled
    }

    fun setAdultContentEnabled(context: Context, enabled: Boolean) {
        val effectiveEnabled = enabled && isHentaiFeatureUnlocked(context)
        getPrefs(context).edit().putBoolean(KEY_ADULT_CONTENT_ENABLED, effectiveEnabled).apply()
        adultContentEnabledFlow.value = effectiveEnabled
    }

    // --- DNS Mode ---
    fun getDnsMode(context: Context): String {
        return getPrefs(context).getString(KEY_DNS_MODE, "cloudflare") ?: "cloudflare"
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

    fun isFastDetailLoadingEnabled(context: Context): Boolean =
        getPrefs(context).getBoolean(KEY_FAST_DETAIL_LOADING, true)

    fun setFastDetailLoadingEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_FAST_DETAIL_LOADING, enabled).apply()
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
        return getPrefs(context).getBoolean(KEY_LOW_PERF_MODE, true)
    }

    fun setLowPerformanceMode(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_LOW_PERF_MODE, enabled).apply()
    }

    // --- App Theme ---
    fun getAppTheme(context: Context): String {
        return getPrefs(context).getString(KEY_APP_THEME, "light_red") ?: "light_red"
    }

    fun setAppTheme(context: Context, theme: String) {
        getPrefs(context).edit().putString(KEY_APP_THEME, theme).apply()
        // Automatically sync player timeline theme with the selected app theme
        setPlayerTimelineTheme(context, theme)
        AnimeBoxThemeHelper.notifyThemeChanged()
    }

    // --- Navbar Background & Outline Themes ---
    fun getNavbarBgTheme(context: Context): String {
        return getPrefs(context).getString(KEY_NAVBAR_BG_THEME, "greyish") ?: "greyish"
    }

    fun setNavbarBgTheme(context: Context, theme: String) {
        getPrefs(context).edit().putString(KEY_NAVBAR_BG_THEME, theme).apply()
        AnimeBoxThemeHelper.notifyThemeChanged()
    }

    fun getNavbarOutlineTheme(context: Context): String {
        return getPrefs(context).getString(KEY_NAVBAR_OUTLINE_THEME, "white") ?: "white"
    }

    fun setNavbarOutlineTheme(context: Context, theme: String) {
        getPrefs(context).edit().putString(KEY_NAVBAR_OUTLINE_THEME, theme).apply()
        AnimeBoxThemeHelper.notifyThemeChanged()
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

    // --- Player 2 Dedicated Timeline Accent Color Theme (Default: Vivid Light Green #00D26A) ---
    fun getPlayer2TimelineTheme(context: Context): String {
        return getPrefs(context).getString(KEY_PLAYER2_TIMELINE_THEME, "light_green") ?: "light_green"
    }

    fun setPlayer2TimelineTheme(context: Context, theme: String) {
        getPrefs(context).edit().putString(KEY_PLAYER2_TIMELINE_THEME, theme).apply()
    }

    // --- YouTube Style Player Dedicated Timeline Accent Color Theme (Default: Red #E50914) ---
    fun getYouTubePlayerTimelineTheme(context: Context): String {
        return getPrefs(context).getString(KEY_YOUTUBE_PLAYER_TIMELINE_THEME, "red") ?: "red"
    }

    fun setYouTubePlayerTimelineTheme(context: Context, theme: String) {
        getPrefs(context).edit().putString(KEY_YOUTUBE_PLAYER_TIMELINE_THEME, theme).apply()
    }

    fun getYouTubePlayerCustomTimelineColor(context: Context): String {
        return getPrefs(context).getString(KEY_YOUTUBE_PLAYER_CUSTOM_TIMELINE_COLOR, "#E50914") ?: "#E50914"
    }

    fun setYouTubePlayerCustomTimelineColor(context: Context, hex: String) {
        getPrefs(context).edit().putString(KEY_YOUTUBE_PLAYER_CUSTOM_TIMELINE_COLOR, hex).apply()
    }

    fun getYouTubePlayerTimelineColor(context: Context): Color {
        val theme = getYouTubePlayerTimelineTheme(context)
        val customHex = getYouTubePlayerCustomTimelineColor(context)
        return try {
            when (theme) {
                "red", "crimson_red" -> Color(0xFFE50914)
                "cyan" -> Color(0xFF00E5FF)
                "gold" -> Color(0xFFFFD700)
                "green" -> Color(0xFF00E676)
                "white" -> Color.White
                "lavender" -> Color(0xFFD0BCFF)
                "custom" -> Color(android.graphics.Color.parseColor(if (customHex.startsWith("#")) customHex else "#$customHex"))
                else -> Color(0xFFE50914)
            }
        } catch (_: Exception) {
            Color(0xFFE50914)
        }
    }

    fun getDefaultPlayerTimelineColor(context: Context): Color {
        val theme = getPlayerTimelineTheme(context)
        val customHex = getCustomTimelineColor(context)
        return try {
            when (theme) {
                "red" -> Color(0xFFE50914)
                "cyan" -> Color(0xFF00E5FF)
                "gold" -> Color(0xFFFFD700)
                "green" -> Color(0xFF00E676)
                "white" -> Color.White
                "custom" -> Color(android.graphics.Color.parseColor(if (customHex.startsWith("#")) customHex else "#$customHex"))
                else -> Color(0xFFD0BCFF)
            }
        } catch (_: Exception) {
            Color(0xFFD0BCFF)
        }
    }

    const val PLAYER_STYLE_DEFAULT = "default"
    const val PLAYER_STYLE_YOUTUBE = "youtube_style"

    // --- Video Player UI Style (Default Player vs YouTube Style) ---
    fun getPlayerStyle(context: Context): String {
        val style = getPrefs(context).getString(KEY_PLAYER_STYLE, PLAYER_STYLE_DEFAULT) ?: PLAYER_STYLE_DEFAULT
        return if (style == PLAYER_STYLE_YOUTUBE) PLAYER_STYLE_YOUTUBE else PLAYER_STYLE_DEFAULT
    }

    fun setPlayerStyle(context: Context, style: String) {
        val normalized = if (style == PLAYER_STYLE_YOUTUBE) PLAYER_STYLE_YOUTUBE else PLAYER_STYLE_DEFAULT
        getPrefs(context).edit().putString(KEY_PLAYER_STYLE, normalized).apply()
    }

    fun isYouTubeStylePlayer(context: Context): Boolean {
        return getPlayerStyle(context) == PLAYER_STYLE_YOUTUBE
    }

    fun getPlayerActivityClass(context: Context): Class<*> {
        return if (isYouTubeStylePlayer(context)) {
            com.lagradost.cloudstream3.ui.animebox.AnimeBoxYouTubePlayerActivity::class.java
        } else {
            com.lagradost.cloudstream3.ui.animebox.AnimeBoxPlayerActivity::class.java
        }
    }

    fun createPlayerIntent(context: Context): android.content.Intent {
        return android.content.Intent(context, getPlayerActivityClass(context))
    }

    fun isPlayer2(context: Context): Boolean {
        return false
    }

    fun getActivePlayerTimelineTheme(context: Context): String {
        return if (isPlayer2(context)) {
            getPlayer2TimelineTheme(context)
        } else {
            getPlayerTimelineTheme(context)
        }
    }

    // --- Download Video Quality Preference ---
    fun getDownloadQuality(context: Context): String {
        val raw = getPrefs(context).getString(KEY_DOWNLOAD_QUALITY, DEFAULT_DOWNLOAD_QUALITY) ?: DEFAULT_DOWNLOAD_QUALITY
        return when {
            raw.contains("1080") -> "1080p"
            raw.contains("720") -> "720p"
            raw.contains("480") -> "480p"
            raw.contains("360") -> "360p"
            raw.contains("ask", ignoreCase = true) -> "ask"
            else -> "1080p"
        }
    }

    fun setDownloadQuality(context: Context, quality: String) {
        getPrefs(context).edit().putString(KEY_DOWNLOAD_QUALITY, quality).apply()
    }

    const val KEY_DOWNLOAD_SUBTITLE_LANGUAGE = "download_subtitle_language"

    fun getDownloadSubtitleLanguage(context: Context): String {
        return getPrefs(context).getString(KEY_DOWNLOAD_SUBTITLE_LANGUAGE, "ask") ?: "ask"
    }

    fun setDownloadSubtitleLanguage(context: Context, lang: String) {
        getPrefs(context).edit().putString(KEY_DOWNLOAD_SUBTITLE_LANGUAGE, lang).apply()
    }

    fun getAutomaticMlKitTranslationLanguage(context: Context): String =
        getPrefs(context).getString(KEY_AUTO_MLKIT_TRANSLATION_LANGUAGE, "off") ?: "off"

    fun setAutomaticMlKitTranslationLanguage(context: Context, languageCode: String) {
        getPrefs(context).edit().putString(KEY_AUTO_MLKIT_TRANSLATION_LANGUAGE, languageCode.lowercase()).apply()
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
        return getPrefs(context).getBoolean(KEY_SKIP_INTRO_ENABLED, false)
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

    // --- Player Launch Mode (Direct Video Player vs Watch Screen) ---
    fun getPlayerLaunchMode(context: Context): String {
        return getPrefs(context).getString(KEY_PLAYER_LAUNCH_MODE, LAUNCH_MODE_DIRECT) ?: LAUNCH_MODE_DIRECT
    }

    fun isWatchScreenModeEnabled(context: Context): Boolean {
        return false
    }

    fun setPlayerLaunchMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_PLAYER_LAUNCH_MODE, mode).apply()
    }

    // --- Autoplay Next Episode ---
    fun isAutoplayNextEpisodeEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTOPLAY_NEXT_EPISODE, false)
    }

    fun setAutoplayNextEpisodeEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTOPLAY_NEXT_EPISODE, enabled).apply()
    }

    // --- Autoplay Previews & Trailers ---
    fun isAutoplayPreviewsEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTOPLAY_PREVIEWS, false)
    }

    fun setAutoplayPreviewsEnabled(context: Context, enabled: Boolean) {
        val editor = getPrefs(context).edit().putBoolean(KEY_AUTOPLAY_PREVIEWS, enabled)
        if (enabled) editor.putBoolean(KEY_LOW_PERF_MODE, false)
        editor.apply()
    }

    // --- Maturity Rating ---
    fun getMaturityRating(context: Context): String {
        return getPrefs(context).getString(KEY_MATURITY_RATING, "With restrictions") ?: "With restrictions"
    }

    fun setMaturityRating(context: Context, rating: String) {
        getPrefs(context).edit().putString(KEY_MATURITY_RATING, rating).apply()
    }

    // --- Default Graphic Enhancer Preset (Default: off) ---
    const val KEY_DEFAULT_GRAPHICS_ENHANCER_PRESET = "default_graphics_enhancer_preset"

    fun getDefaultGraphicsEnhancerPreset(context: Context): String {
        return getPrefs(context).getString(KEY_DEFAULT_GRAPHICS_ENHANCER_PRESET, "off") ?: "off"
    }

    fun setDefaultGraphicsEnhancerPreset(context: Context, presetId: String) {
        getPrefs(context).edit().putString(KEY_DEFAULT_GRAPHICS_ENHANCER_PRESET, presetId).apply()
        syncDefaultGraphicsEnhancerToPlayer(context)
    }

    /**
     * Makes the video player start with the graphics enhancer state chosen in Settings
     * ("off" keeps it disabled instead of silently enabling the 4K preset).
     * Runs only when the Settings default changed since it was last applied, so manual
     * in-player changes made afterwards are kept.
     */
    fun syncDefaultGraphicsEnhancerToPlayer(context: Context) {
        val presetId = getDefaultGraphicsEnhancerPreset(context)
        val playerPrefs = context.getSharedPreferences("AnimeBoxPlayerPrefs", Context.MODE_PRIVATE)
        if (playerPrefs.getString("graphics_default_applied", null) == presetId) return
        val edit = playerPrefs.edit().putString("graphics_default_applied", presetId)
        if (presetId == "off") {
            edit.putBoolean("graphics_upscaler_enabled", false)
        } else {
            val preset = com.lagradost.cloudstream3.ui.animebox.enhancer.EnhancerPreset.fromId(presetId)
            edit.putBoolean("graphics_upscaler_enabled", true)
                .putString("enhancer_preset_id", preset.id)
                .putFloat("enhancer_sharpness", preset.defaultSharpness)
                .putFloat("enhancer_contrast", preset.defaultContrast)
                .putFloat("enhancer_saturation", preset.defaultSaturation)
                .putFloat("enhancer_line_clarity", preset.defaultLineClarity)
        }
        edit.apply()
    }

    // --- Default Audio Enhancer Preset (Default: off) ---
    const val KEY_DEFAULT_AUDIO_ENHANCER_PRESET = "default_audio_enhancer_preset"

    fun getDefaultAudioEnhancerPreset(context: Context): String {
        return getPrefs(context).getString(KEY_DEFAULT_AUDIO_ENHANCER_PRESET, "off") ?: "off"
    }

    fun setDefaultAudioEnhancerPreset(context: Context, presetId: String) {
        getPrefs(context).edit().putString(KEY_DEFAULT_AUDIO_ENHANCER_PRESET, presetId).apply()
    }

    // --- Display Language ---
    fun getDisplayLanguage(context: Context): String {
        return "English"
    }

    fun setDisplayLanguage(context: Context, lang: String) {
        getPrefs(context).edit().putString(KEY_DISPLAY_LANGUAGE, "English").apply()
    }

    // --- Clustered Watch History ---
    fun isClusteredWatchHistoryEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_CLUSTERED_WATCH_HISTORY, false)
    }

    fun setClusteredWatchHistoryEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_CLUSTERED_WATCH_HISTORY, enabled).apply()
        com.lagradost.cloudstream3.ui.animebox.history.WatchHistoryManager.revisionFlow.value = System.currentTimeMillis()
    }

    // --- Subtitle Outline Width & Custom Font Family ---
    fun getSubOutlineWidth(context: Context): Float {
        val pPrefs = context.getSharedPreferences("AnimeBoxPlayerPrefs", Context.MODE_PRIVATE)
        return pPrefs.getFloat("subOutlineWidth", getPrefs(context).getFloat(KEY_SUB_OUTLINE_WIDTH, 12f))
    }

    fun setSubOutlineWidth(context: Context, width: Float) {
        val clamped = width.coerceIn(0f, 24f)
        context.getSharedPreferences("AnimeBoxPlayerPrefs", Context.MODE_PRIVATE)
            .edit().putFloat("subOutlineWidth", clamped).apply()
        getPrefs(context).edit().putFloat(KEY_SUB_OUTLINE_WIDTH, clamped).apply()
    }

    fun getSubFontFamily(context: Context): String {
        val pPrefs = context.getSharedPreferences("AnimeBoxPlayerPrefs", Context.MODE_PRIVATE)
        return pPrefs.getString("subFontFamily", getPrefs(context).getString(KEY_SUB_FONT_FAMILY, "Default (Google Sans)")) ?: "Default (Google Sans)"
    }

    fun setSubFontFamily(context: Context, family: String) {
        context.getSharedPreferences("AnimeBoxPlayerPrefs", Context.MODE_PRIVATE)
            .edit().putString("subFontFamily", family).apply()
        getPrefs(context).edit().putString(KEY_SUB_FONT_FAMILY, family).apply()
    }

    fun getCustomSubFontPath(context: Context): String {
        val pPrefs = context.getSharedPreferences("AnimeBoxPlayerPrefs", Context.MODE_PRIVATE)
        return pPrefs.getString("customSubFontPath", getPrefs(context).getString(KEY_CUSTOM_SUB_FONT_PATH, "")) ?: ""
    }

    fun setCustomSubFontPath(context: Context, path: String) {
        context.getSharedPreferences("AnimeBoxPlayerPrefs", Context.MODE_PRIVATE)
            .edit().putString("customSubFontPath", path).apply()
        getPrefs(context).edit().putString(KEY_CUSTOM_SUB_FONT_PATH, path).apply()
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
            pObj.put("decorationUrl", p.decorationUrl)
            profilesArr.put(pObj)
        }
        root.put("profiles", profilesArr)
        root.put("activeProfileId", ProfileManager.getActiveProfile(context))

        // 2. Library & History per profile (All categories & continue watching records & custom collections)
        val libObj = JSONObject()
        val collectionsObj = JSONObject()
        val histObj = JSONObject()
        val profSettingsObj = JSONObject()
        profiles.forEach { p ->
            val libPrefs = context.getSharedPreferences("AnimeBoxLibrary_${p.id}", Context.MODE_PRIVATE)
            val histPrefs = context.getSharedPreferences("AnimeBoxHistory_${p.id}", Context.MODE_PRIVATE)
            val pPrefs = context.getSharedPreferences("AnimeBoxPrefs_${p.id}", Context.MODE_PRIVATE)
            libObj.put(p.id, libPrefs.getString("library_list", "[]"))
            collectionsObj.put(p.id, libPrefs.getString("custom_collections_v1", "[]"))
            histObj.put(p.id, histPrefs.getString("history_list", "[]"))

            // Profile specific preferences
            val ps = JSONObject()
            pPrefs.all.forEach { (k, v) ->
                ps.put(k, v)
            }
            profSettingsObj.put(p.id, ps)
        }
        root.put("libraryPerProfile", libObj)
        root.put("collectionsPerProfile", collectionsObj)
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
        settingsObj.put(KEY_CLUSTERED_WATCH_HISTORY, isClusteredWatchHistoryEnabled(context))
        settingsObj.put(KEY_SUB_OUTLINE_WIDTH, getSubOutlineWidth(context).toDouble())
        settingsObj.put(KEY_SUB_FONT_FAMILY, getSubFontFamily(context))
        settingsObj.put(KEY_CUSTOM_SUB_FONT_PATH, getCustomSubFontPath(context))
        settingsObj.put(KEY_AUTO_RESUME_PLAYBACK, isAutoResumeEnabled(context))
        settingsObj.put(KEY_EXTERNAL_PLAYER_ENABLED, isExternalPlayerEnabled(context))
        settingsObj.put(KEY_NON_JAPANESE_SUB_OFF, isNonJapaneseSubOff(context))
        settingsObj.put(KEY_NAVBAR_BG_THEME, getNavbarBgTheme(context))
        settingsObj.put(KEY_NAVBAR_OUTLINE_THEME, getNavbarOutlineTheme(context))

        val globalPrefs = context.getSharedPreferences("AnimeBoxPrefs", Context.MODE_PRIVATE)
        settingsObj.put("wifi_only_downloads", globalPrefs.getBoolean("wifi_only_downloads", false))
        settingsObj.put("smart_downloads", globalPrefs.getBoolean("smart_downloads", true))
        settingsObj.put("download_quality", globalPrefs.getString("download_quality", "ask"))
        settingsObj.put(KEY_AUTO_MLKIT_TRANSLATION_LANGUAGE, getAutomaticMlKitTranslationLanguage(context))
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
                        pObj.optString("avatarUrl", ""),
                        pObj.optString("decorationUrl", "")
                    )
                )
            }
            if (importedProfiles.isNotEmpty()) {
                ProfileManager.saveProfiles(context, importedProfiles)
            }
            if (root.has("activeProfileId")) {
                ProfileManager.setActiveProfile(context, root.getString("activeProfileId"))
            }

            // 2. Restore Library & Collections per profile
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

            if (root.has("collectionsPerProfile")) {
                val collObj = root.getJSONObject("collectionsPerProfile")
                val keys = collObj.keys()
                while (keys.hasNext()) {
                    val pId = keys.next()
                    val collStr = collObj.getString(pId)
                    val libPrefs = context.getSharedPreferences("AnimeBoxLibrary_$pId", Context.MODE_PRIVATE)
                    libPrefs.edit().putString("custom_collections_v1", collStr).apply()
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
                setLowPerformanceMode(context, settingsObj.optBoolean(KEY_LOW_PERF_MODE, true))
                val theme = settingsObj.optString(KEY_APP_THEME, "lavender")
                setAppTheme(context, theme)
                setPlayerTimelineTheme(context, settingsObj.optString(KEY_PLAYER_TIMELINE_THEME, theme))
                setCustomTimelineColor(context, settingsObj.optString(KEY_CUSTOM_TIMELINE_COLOR, "#D0BCFF"))
                setSkipIntroTheme(context, settingsObj.optString(KEY_SKIP_INTRO_THEME, "lavender"))
                setSkipIntroEnabled(context, settingsObj.optBoolean(KEY_SKIP_INTRO_ENABLED, false))
                setBrightnessMode(context, settingsObj.optString(KEY_BRIGHTNESS_MODE, "gesture"))
                setVolumeMode(context, settingsObj.optString(KEY_VOLUME_MODE, "gesture"))
                setDefaultEpisodeViewMode(context, settingsObj.optString(KEY_EPISODE_VIEW_MODE, "image"))
                setAutoplayNextEpisodeEnabled(context, settingsObj.optBoolean(KEY_AUTOPLAY_NEXT_EPISODE, false))
                setAutoplayPreviewsEnabled(context, settingsObj.optBoolean(KEY_AUTOPLAY_PREVIEWS, false))
                setMaturityRating(context, settingsObj.optString(KEY_MATURITY_RATING, "With restrictions"))
                setDisplayLanguage(context, settingsObj.optString(KEY_DISPLAY_LANGUAGE, "English"))
                if (settingsObj.has("dns_mode")) {
                    setDnsMode(context, settingsObj.optString("dns_mode", "system"))
                }
                if (settingsObj.has(KEY_CLUSTERED_WATCH_HISTORY)) {
                    setClusteredWatchHistoryEnabled(context, settingsObj.optBoolean(KEY_CLUSTERED_WATCH_HISTORY, false))
                }
                if (settingsObj.has(KEY_SUB_OUTLINE_WIDTH)) {
                    setSubOutlineWidth(context, settingsObj.optDouble(KEY_SUB_OUTLINE_WIDTH, 12.0).toFloat())
                }
                if (settingsObj.has(KEY_SUB_FONT_FAMILY)) {
                    setSubFontFamily(context, settingsObj.optString(KEY_SUB_FONT_FAMILY, "Default (Google Sans)"))
                }
                if (settingsObj.has(KEY_CUSTOM_SUB_FONT_PATH)) {
                    setCustomSubFontPath(context, settingsObj.optString(KEY_CUSTOM_SUB_FONT_PATH, ""))
                }
                if (settingsObj.has(KEY_AUTO_RESUME_PLAYBACK)) {
                    setAutoResumeEnabled(context, settingsObj.optBoolean(KEY_AUTO_RESUME_PLAYBACK, false))
                }
                if (settingsObj.has(KEY_EXTERNAL_PLAYER_ENABLED)) {
                    setExternalPlayerEnabled(context, settingsObj.optBoolean(KEY_EXTERNAL_PLAYER_ENABLED, false))
                }
                if (settingsObj.has(KEY_NON_JAPANESE_SUB_OFF)) {
                    setNonJapaneseSubOff(context, settingsObj.optBoolean(KEY_NON_JAPANESE_SUB_OFF, false))
                }

                if (settingsObj.has(KEY_NAVBAR_BG_THEME)) {
                    setNavbarBgTheme(context, settingsObj.optString(KEY_NAVBAR_BG_THEME, "pure_black"))
                }
                if (settingsObj.has(KEY_NAVBAR_OUTLINE_THEME)) {
                    setNavbarOutlineTheme(context, settingsObj.optString(KEY_NAVBAR_OUTLINE_THEME, "pure_white"))
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
                if (settingsObj.has(KEY_AUTO_MLKIT_TRANSLATION_LANGUAGE)) {
                    setAutomaticMlKitTranslationLanguage(context, settingsObj.optString(KEY_AUTO_MLKIT_TRANSLATION_LANGUAGE, "off"))
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

            com.lagradost.cloudstream3.ui.animebox.library.LibraryManager.notifyLibraryChanged()

            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
