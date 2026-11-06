package com.lagradost.cloudstream3.ui.animebox.api
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class AudioServerConfigItem(
    val id: String,
    val type: String, // "language" or "server"
    val key: String,
    val displayName: String,
    val category: String,
    val isEnabled: Boolean
)

object ServerVisibilityConfig {
    private const val PREFS_NAME = "animebox_server_visibility_config"
    private const val KEY_CACHED_CONFIG = "cached_config_json"
    private const val KEY_LAST_SYNC = "last_sync_timestamp"
    private const val SYNC_INTERVAL_MS = 60_000L // 1 minute cache validity

    private val SUPABASE_URL get() = AnimeBoxConfig.SUPABASE_URL
    private val SUPABASE_ANON_KEY get() = AnimeBoxConfig.SUPABASE_ANON_KEY

    private val client: OkHttpClient by lazy {
        AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    // In-memory lookup maps: key -> isEnabled
    private val languageStatusMap = ConcurrentHashMap<String, Boolean>()
    private val serverStatusMap = ConcurrentHashMap<String, Boolean>()
    private var isInitialized = false

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Initializes config from local persistent cache first, ensuring instant zero-latency startup.
     */
    fun init(context: Context) {
        if (isInitialized) return
        val cachedJson = getPrefs(context).getString(KEY_CACHED_CONFIG, null)
        if (!cachedJson.isNullOrEmpty()) {
            parseConfigJson(cachedJson)
        }
        isInitialized = true
    }

    /**
     * Syncs latest visibility rules from Supabase in the background.
     */
    suspend fun syncConfig(context: Context, force: Boolean = false) = withContext(Dispatchers.IO) {
        init(context)
        val now = System.currentTimeMillis()
        val lastSync = getPrefs(context).getLong(KEY_LAST_SYNC, 0L)
        if (!force && now - lastSync < SYNC_INTERVAL_MS && languageStatusMap.isNotEmpty()) {
            return@withContext
        }

        try {
            val req = Request.Builder()
                .url("$SUPABASE_URL/rest/v1/app_audio_server_config?select=*")
                .header("apikey", SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .get()
                .build()

            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (!body.isNullOrEmpty()) {
                    parseConfigJson(body)
                    getPrefs(context).edit()
                        .putString(KEY_CACHED_CONFIG, body)
                        .putLong(KEY_LAST_SYNC, now)
                        .apply()
                }
            }
        } catch (_: Exception) {
            // Keep existing cached values if offline or error occurs
        }
    }

    private fun parseConfigJson(jsonStr: String) {
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val type = obj.optString("type", "").lowercase()
                val key = obj.optString("key", "").lowercase()
                val isEnabled = obj.optBoolean("is_enabled", true)
                if (key.isNotEmpty()) {
                    if (type == "language") {
                        languageStatusMap[key] = isEnabled
                    } else if (type == "server") {
                        serverStatusMap[key] = isEnabled
                    }
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Check whether an audio language track is enabled.
     * Always returns true for Japanese (Original) fallback.
     */
    fun isLanguageEnabled(langKey: String): Boolean {
        val norm = when (langKey.lowercase()) {
            "japanese", "sub", "ja" -> return true
            "english", "dub", "en" -> "english"
            "hindi", "hi" -> "hindi"
            "french", "vf", "fr" -> "french"
            "italian", "ita", "it" -> "italian"
            "spanish_latino", "latino", "es-la" -> "spanish_latino"
            "spanish_castellano", "castellano", "es-es" -> "spanish_castellano"
            "german", "deutsch", "de" -> "german"
            else -> langKey.lowercase()
        }
        return languageStatusMap[norm] ?: true
    }

    /**
     * Check whether a user-facing audio label is enabled.
     */
    fun isAudioLabelEnabled(label: String): Boolean {
        return when (label) {
            "Japanese (Original)" -> true
            "English" -> isLanguageEnabled("english")
            "Hindi" -> isLanguageEnabled("hindi")
            "French (VF)" -> isLanguageEnabled("french")
            "Italian (ITA)" -> isLanguageEnabled("italian")
            "Spanish (Latino)" -> isLanguageEnabled("spanish_latino")
            "Spanish (Castellano)" -> isLanguageEnabled("spanish_castellano")
            "German (Deutsch)" -> isLanguageEnabled("german")
            else -> true
        }
    }

    /**
     * Returns fallback audio label: "Japanese (Original)" if current label is disabled.
     */
    fun getEffectiveAudioLabel(label: String): String {
        return if (isAudioLabelEnabled(label)) label else "Japanese (Original)"
    }

    /**
     * Check whether a streaming server is enabled.
     */
    fun isServerEnabled(serverKeyOrName: String): Boolean {
        val norm = serverKeyOrName.lowercase().trim()
        if (norm == "auto" || norm.isEmpty()) return true

        // 1. Direct exact key match
        serverStatusMap[norm]?.let { return it }

        // 2. Substring or prefix matching
        for ((key, enabled) in serverStatusMap) {
            if (norm == key || norm.contains(key) || key.contains(norm)) {
                if (!enabled) return false
            }
        }
        return true
    }

    /**
     * Filter server list removing disabled servers.
     */
    fun <T> filterServers(servers: List<T>, nameSelector: (T) -> String): List<T> {
        return servers.filter { isServerEnabled(nameSelector(it)) }
    }
}
