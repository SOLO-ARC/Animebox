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
    const val KEY_PRIORITY_FRENCH = "pref_server_priority_french"
    const val KEY_PRIORITY_ITALIAN = "pref_server_priority_italian"
    const val KEY_PRIORITY_SPANISH_LATINO = "pref_server_priority_spanish_latino"
    const val KEY_PRIORITY_SPANISH_CASTELLANO = "pref_server_priority_spanish_castellano"
    const val KEY_PRIORITY_GERMAN = "pref_server_priority_german"
    const val KEY_PRIORITY_HENTAI_JP = "pref_server_priority_hentai_jp"
    const val KEY_DOWNLOAD_SERVER = "pref_download_server"

    const val SERVER_AUTO = "auto"

    val DOWNLOAD_SERVERS: List<ServerOption> by lazy {
        listOf(ServerOption(SERVER_AUTO, "Auto (Default)")) +
            (SUB_SERVERS + DUB_SERVERS + HARDSUB_SERVERS + HINDI_SERVERS + FRENCH_SERVERS +
                ITALIAN_SERVERS + SPANISH_LATINO_SERVERS + SPANISH_CASTELLANO_SERVERS + GERMAN_SERVERS + HENTAI_JP_SERVERS)
                .filter { it.key != SERVER_AUTO }
                .distinctBy { it.key }
    }

    fun getDownloadServer(context: Context): String {
        val selected = getPrefs(context).getString(KEY_DOWNLOAD_SERVER, SERVER_AUTO) ?: SERVER_AUTO
        return if (selected == SERVER_AUTO || com.lagradost.cloudstream3.ui.animebox.api.ServerVisibilityConfig.isServerEnabled(selected)) selected else SERVER_AUTO
    }

    fun setDownloadServer(context: Context, serverKey: String) {
        getPrefs(context).edit().putString(KEY_DOWNLOAD_SERVER, serverKey).apply()
        com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.clearCache()
    }

    val SUB_SERVERS = listOf(
        ServerOption("auto", "Auto (Default)"),
        ServerOption("megaplay", "MegaPlay"),
        ServerOption("reanime", "ReAnime"),
        ServerOption("anipm", "Ani.pm"),
        ServerOption("kiwi", "Kiwi"),
        ServerOption("animepahe", "AnimePahe"),
        ServerOption("bee", "Bee"),
        ServerOption("bun", "Bun"),
        ServerOption("sun", "Sun"),
        ServerOption("hop", "Hop"),
        ServerOption("knob", "Knob"),
        ServerOption("2dhive", "2Dhive"),
        ServerOption("kickassanime", "KissAssAnime"),
        ServerOption("fouranimo", "FourAnimo"),
        ServerOption("anibd", "AniBD"),
        ServerOption("animegg", "AnimeGG"),
        ServerOption("animeav1", "AnimeAV1")
    )

    val DUB_SERVERS = listOf(
        ServerOption("auto", "Auto (Default)"),
        ServerOption("reanime_dub", "ReAnime"),
        ServerOption("anipm_dub", "Ani.pm"),
        ServerOption("kiwi_dub", "Kiwi"),
        ServerOption("animepahe_dub", "AnimePahe"),
        ServerOption("megaplay_dub", "MegaPlay"),
        ServerOption("bee", "Bee"),
        ServerOption("bun", "Bun"),
        ServerOption("sun", "Sun"),
        ServerOption("hop", "Hop"),
        ServerOption("knob", "Knob"),
        ServerOption("2dhive", "2Dhive"),
        ServerOption("kickassanime", "KissAssAnime"),
        ServerOption("fouranimo_dub", "FourAnimo"),
        ServerOption("animegg_dub", "AnimeGG"),
        ServerOption("aniwaves_dub", "AniWave")
    )

    val HARDSUB_SERVERS = listOf(
        ServerOption("auto", "Auto (Default)"),
        ServerOption("reanime_hardsub", "ReAnime"),
        ServerOption("animepahe", "AnimePahe"),
        ServerOption("kiwi", "Kiwi"),
        ServerOption("bee", "Bee"),
        ServerOption("bun", "Bun"),
        ServerOption("sun", "Sun"),
        ServerOption("hop", "Hop"),
        ServerOption("knob", "Knob"),
        ServerOption("2dhive", "2Dhive"),
        ServerOption("kickassanime", "KissAssAnime"),
        ServerOption("anineko", "AniNeko"),
        ServerOption("animegg_hardsub", "AnimeGG"),
        ServerOption("anibd", "AniBD"),
        ServerOption("aniwaves", "AniWave")
    )

    val HINDI_SERVERS = listOf(
        ServerOption("auto", "Auto (Default)"),
        ServerOption("animedekho", "AnimeDekho"),
        ServerOption("blakite", "Blakite"),
        ServerOption("rareanime", "RareAnime")
    )

    val FRENCH_SERVERS = listOf(
        ServerOption("auto", "Auto (Default)"),
        ServerOption("helios", "Helios"),
        ServerOption("aura", "Aura"),
        ServerOption("mirage", "Mirage")
    )

    val ITALIAN_SERVERS = listOf(
        ServerOption("auto", "Auto (Default)"),
        ServerOption("titan", "Titan"),
        ServerOption("solaris", "Solaris")
    )

    val SPANISH_LATINO_SERVERS = listOf(
        ServerOption("auto", "Auto (Default)"),
        ServerOption("animeav1", "AnimeAV1"),
        ServerOption("neptune", "Neptune"),
        ServerOption("jupiter", "Jupiter"),
        ServerOption("mercury", "Mercury")
    )

    val SPANISH_CASTELLANO_SERVERS = listOf(
        ServerOption("auto", "Auto (Default)"),
        ServerOption("eclipse", "Eclipse"),
        ServerOption("kagura", "Kagura")
    )

    val GERMAN_SERVERS = listOf(
        ServerOption("auto", "Auto (Default)"),
        ServerOption("nexus", "Nexus"),
        ServerOption("bifrost", "Bifrost")
    )

    val HENTAI_JP_SERVERS = listOf(
        ServerOption("auto", "Auto (Default)"),
        ServerOption("hentaimama", "Hentai Mama"),
        ServerOption("hahomoe", "Haho Moe"),
        ServerOption("reanime", "ReAnime"),
        ServerOption("hentaihaven", "Hentai Haven")
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
            "french", "vf" -> KEY_PRIORITY_FRENCH
            "italian", "ita" -> KEY_PRIORITY_ITALIAN
            "spanish_latino", "latino" -> KEY_PRIORITY_SPANISH_LATINO
            "spanish_castellano", "castellano" -> KEY_PRIORITY_SPANISH_CASTELLANO
            "german", "deutsch", "de" -> KEY_PRIORITY_GERMAN
            "hentai", "hentai-jp", "hentai_jp", "hentaijp" -> KEY_PRIORITY_HENTAI_JP
            else -> return SERVER_AUTO
        }
        var pref = getPrefs(context).getString(key, SERVER_AUTO) ?: SERVER_AUTO
        pref = when (pref) {
            "valkyrie" -> "helios"
            "zenith" -> "neptune"
            "shinobi" -> "jupiter"
            else -> pref
        }
        if (pref.equals("dorara", ignoreCase = true) ||
            pref.equals("blisseyhusband", ignoreCase = true) ||
            pref.equals("blissey", ignoreCase = true)
        ) {
            return SERVER_AUTO
        }
        if (pref != SERVER_AUTO && !com.lagradost.cloudstream3.ui.animebox.api.ServerVisibilityConfig.isServerEnabled(pref)) {
            return SERVER_AUTO
        }
        return pref
    }

    fun setPreferredServer(context: Context, streamType: String, serverKey: String) {
        if (serverKey.equals("dorara", ignoreCase = true) ||
            serverKey.equals("blisseyhusband", ignoreCase = true) ||
            serverKey.equals("blissey", ignoreCase = true)
        ) {
            return // Never store dorara or blisseyhusband into global category preferences
        }
        val key = when (streamType.lowercase()) {
            "sub" -> KEY_PRIORITY_SUB
            "dub" -> KEY_PRIORITY_DUB
            "hardsub" -> KEY_PRIORITY_HARDSUB
            "hindi" -> KEY_PRIORITY_HINDI
            "french", "vf" -> KEY_PRIORITY_FRENCH
            "italian", "ita" -> KEY_PRIORITY_ITALIAN
            "spanish_latino", "latino" -> KEY_PRIORITY_SPANISH_LATINO
            "spanish_castellano", "castellano" -> KEY_PRIORITY_SPANISH_CASTELLANO
            "german", "deutsch", "de" -> KEY_PRIORITY_GERMAN
            "hentai", "hentai-jp", "hentai_jp", "hentaijp" -> KEY_PRIORITY_HENTAI_JP
            else -> return
        }
        getPrefs(context).edit().putString(key, serverKey).apply()
        try {
            com.lagradost.cloudstream3.ui.animebox.extractors.AnimeStreamExtractorEngine.clearCache()
        } catch (_: Throwable) {}
    }

    fun getServersForCategory(streamType: String): List<ServerOption> {
        val allOptions = when (streamType.lowercase()) {
            "sub" -> SUB_SERVERS
            "dub" -> DUB_SERVERS
            "hardsub" -> HARDSUB_SERVERS
            "hindi" -> HINDI_SERVERS
            "french", "vf" -> FRENCH_SERVERS
            "italian", "ita" -> ITALIAN_SERVERS
            "spanish_latino", "latino" -> SPANISH_LATINO_SERVERS
            "spanish_castellano", "castellano" -> SPANISH_CASTELLANO_SERVERS
            "german", "deutsch", "de" -> GERMAN_SERVERS
            "hentai", "hentai-jp", "hentai_jp", "hentaijp" -> HENTAI_JP_SERVERS
            else -> emptyList()
        }
        return allOptions.filter { opt ->
            opt.key == SERVER_AUTO || com.lagradost.cloudstream3.ui.animebox.api.ServerVisibilityConfig.isServerEnabled(opt.key)
        }
    }
}
