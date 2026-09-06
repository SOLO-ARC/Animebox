package com.lagradost.cloudstream3.ui.animebox.settings

import android.content.Context
import android.content.SharedPreferences

data class ServerOption(
    val key: String,
    val displayName: String,
    val description: String = ""
)

object ServerPriorityManager {
    private const val PREFS_NAME = "animebox_server_priority_prefs"

    const val KEY_PRIORITY_SUB = "pref_server_priority_sub"
    const val KEY_PRIORITY_DUB = "pref_server_priority_dub"
    const val KEY_PRIORITY_HARDSUB = "pref_server_priority_hardsub"
    const val KEY_PRIORITY_HINDI = "pref_server_priority_hindi"

    const val SERVER_AUTO = "auto"

    val SUB_SERVERS = listOf(
        ServerOption("auto", "Auto (Default)"),
        ServerOption("megaplay", "MegaPlay"),
        ServerOption("fouranimo", "FourAnimo"),
        ServerOption("reanime", "ReAnime"),
        ServerOption("anibd", "AniBD"),
        ServerOption("animegg", "AnimeGG")
    )

    val DUB_SERVERS = listOf(
        ServerOption("auto", "Auto (Default)"),
        ServerOption("megaplay_dub", "MegaPlay"),
        ServerOption("fouranimo_dub", "FourAnimo"),
        ServerOption("animegg_dub", "AnimeGG")
    )

    val HARDSUB_SERVERS = listOf(
        ServerOption("auto", "Auto (Default)"),
        ServerOption("anineko", "AniNeko"),
        ServerOption("animegg_hardsub", "AnimeGG"),
        ServerOption("anibd", "AniBD"),
        ServerOption("mkissa_default", "MKissa (Default)"),
        ServerOption("mkissa_ok", "MKissa (OK)"),
        ServerOption("mkissa_sak", "MKissa (Sak)"),
        ServerOption("mkissa_mp4", "MKissa (Mp4Upload)")
    )

    val HINDI_SERVERS = listOf(
        ServerOption("auto", "Auto (Default)"),
        ServerOption("animedekho", "AnimeDekho"),
        ServerOption("rareanime", "RareAnime")
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getPreferredServer(context: Context, streamType: String): String {
        val key = when (streamType.lowercase()) {
            "sub" -> KEY_PRIORITY_SUB
            "dub" -> KEY_PRIORITY_DUB
            "hardsub" -> KEY_PRIORITY_HARDSUB
            "hindi" -> KEY_PRIORITY_HINDI
            else -> return SERVER_AUTO
        }
        return getPrefs(context).getString(key, SERVER_AUTO) ?: SERVER_AUTO
    }

    fun setPreferredServer(context: Context, streamType: String, serverKey: String) {
        val key = when (streamType.lowercase()) {
            "sub" -> KEY_PRIORITY_SUB
            "dub" -> KEY_PRIORITY_DUB
            "hardsub" -> KEY_PRIORITY_HARDSUB
            "hindi" -> KEY_PRIORITY_HINDI
            else -> return
        }
        getPrefs(context).edit().putString(key, serverKey).apply()
        try {
            com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.clearCache()
        } catch (_: Throwable) {}
    }

    fun getServersForCategory(streamType: String): List<ServerOption> {
        return when (streamType.lowercase()) {
            "sub" -> SUB_SERVERS
            "dub" -> DUB_SERVERS
            "hardsub" -> HARDSUB_SERVERS
            "hindi" -> HINDI_SERVERS
            else -> emptyList()
        }
    }
}
