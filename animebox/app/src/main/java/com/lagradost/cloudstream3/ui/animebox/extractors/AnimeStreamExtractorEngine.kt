package com.lagradost.cloudstream3.ui.animebox.extractors

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object AnimeStreamExtractorEngine {

    private val streamCache = java.util.concurrent.ConcurrentHashMap<String, Map<String, Any>>()

    private fun resolveSourceEpisode(anilistId: Int, episodeNum: Int): Pair<Int, Int> {
        if (anilistId != 269) return anilistId to episodeNum
        return when (episodeNum) {
            in 367..379 -> 116674 to (episodeNum - 366) // TYBW: The Blood Warfare
            in 380..392 -> 159322 to (episodeNum - 379) // TYBW: The Separation
            in 393..406 -> 169755 to (episodeNum - 392) // TYBW: The Conflict
            in 407..416 -> 185874 to (episodeNum - 406) // TYBW: The Calamity
            else -> anilistId to episodeNum
        }
    }

    fun clearCache() {
        streamCache.clear()
    }

    fun clearEpisodeCache(anilistId: Int, episodeNum: Int) {
        val prefix = "${anilistId}_${episodeNum}_"
        val keysToRemove = streamCache.keys.filter { it.startsWith(prefix) }
        for (k in keysToRemove) {
            streamCache.remove(k)
        }
    }

    private data class SubSourceInfo(
        val subtitleUrl: String = "",
        val subtitles: List<Map<String, String>> = emptyList(),
        val introStart: Long = 0L,
        val introEnd: Long = 0L,
        val outroStart: Long = 0L,
        val outroEnd: Long = 0L
    )

    private suspend fun fetchEnglishSubSource(anilistId: Int, episodeNum: Int): SubSourceInfo {
        return try {
            val mp = MegaPlayExtractor.extract(anilistId, episodeNum, "sub")
            if (mp != null) {
                SubSourceInfo(
                    subtitleUrl = mp.subtitleUrl,
                    subtitles = mp.subtitles,
                    introStart = mp.introStart,
                    introEnd = mp.introEnd,
                    outroStart = mp.outroStart,
                    outroEnd = mp.outroEnd
                )
            } else {
                val re = ReAnimeExtractor.extract(anilistId, episodeNum, "sub")
                if (re != null) {
                    SubSourceInfo(
                        subtitleUrl = re.subtitleUrl,
                        subtitles = re.subtitles,
                        introStart = re.introStart,
                        introEnd = re.introEnd,
                        outroStart = re.outroStart,
                        outroEnd = re.outroEnd
                    )
                } else {
                    val apm = AniPmExtractor.extract(anilistId, episodeNum, "sub")
                    if (apm != null) {
                        SubSourceInfo(
                            subtitleUrl = apm.subtitleUrl,
                            subtitles = apm.subtitles,
                            introStart = apm.introStart,
                            introEnd = apm.introEnd,
                            outroStart = apm.outroStart,
                            outroEnd = apm.outroEnd
                        )
                    } else {
                        val anibd = AniBdExtractor.extract(anilistId, episodeNum)
                        if (anibd != null) {
                            SubSourceInfo(
                                subtitleUrl = anibd.subtitleUrl,
                                subtitles = anibd.subtitles
                            )
                        } else SubSourceInfo()
                    }
                }
            }
        } catch (_: Exception) { SubSourceInfo() }
    }

    suspend fun prefetchStreamInfo(
        context: Context,
        anilistId: Int,
        episodeNum: Int,
        streamType: String,
        animeTitle: String? = null
    ) = withContext(Dispatchers.IO) {
        val pref = com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.getPreferredServer(context, streamType)
        val cacheKey = "${anilistId}_${episodeNum}_${streamType}_${pref}"
        if (!streamCache.containsKey(cacheKey)) {
            getStreamInfo(context, anilistId, episodeNum, streamType, animeTitle)
        }
    }

    suspend fun getStreamInfo(
        context: Context,
        anilistId: Int,
        episodeNum: Int,
        streamType: String,
        animeTitle: String? = null,
        forceRefresh: Boolean = false,
        serverOverride: String? = null
    ): Map<String, Any>? = withContext(Dispatchers.IO) {
        val pref = serverOverride?.takeIf { it.isNotBlank() && it != com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.SERVER_AUTO }
            ?: com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.getPreferredServer(context, streamType)
        val cacheKey = "${anilistId}_${episodeNum}_${streamType}_${pref}"
        if (forceRefresh) {
            clearEpisodeCache(anilistId, episodeNum)
        } else {
            streamCache[cacheKey]?.let { return@withContext it }
        }

        val (sourceAnimeId, sourceEpisodeNum) = resolveSourceEpisode(anilistId, episodeNum)
        val res = extractStreamInfoInternal(
            context,
            sourceAnimeId,
            sourceEpisodeNum,
            streamType,
            animeTitle,
            serverOverride,
            customStreamAnimeId = anilistId,
            customStreamEpisodeNum = episodeNum
        )
        if (res != null) {
            streamCache[cacheKey] = res
        }
        return@withContext res
    }

    private suspend fun extractStreamInfoInternal(
        context: Context,
        anilistId: Int,
        episodeNum: Int,
        streamType: String,
        animeTitle: String? = null,
        serverOverride: String? = null,
        customStreamAnimeId: Int = anilistId,
        customStreamEpisodeNum: Int = episodeNum
    ): Map<String, Any>? = withContext(Dispatchers.IO) {
        val type = streamType.lowercase()

        // Check User Preferred Server Priority first (Fixed priority selected in My Space or explicit serverOverride)
        val effectivePref = serverOverride?.takeIf { it.isNotBlank() && !it.equals(com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.SERVER_AUTO, ignoreCase = true) }
            ?: com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.getPreferredServer(context, type)
        val hasSpecificServer = !effectivePref.equals(com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.SERVER_AUTO, ignoreCase = true)

        var isFallbackShift = false
        var failedRequestedServer: String? = null

        if (hasSpecificServer) {
            try {
                val prefRes = tryPreferredServer(context, anilistId, episodeNum, type, animeTitle, effectivePref)
                if (prefRes != null && (prefRes["hls"] as? String)?.isNotEmpty() == true) {
                    return@withContext prefRes
                }
            } catch (_: Exception) {}
            // Preferred server failed or unavailable: mark fallback shift and continue to auto waterfall!
            // Dorara is a custom stream specific to certain episodes; if unavailable, seamlessly fall back without alarm
            if (!effectivePref.equals("dorara", ignoreCase = true)) {
                isFallbackShift = true
                failedRequestedServer = effectivePref
            }
        }

        val waterfallRes = extractWaterfall(
            context,
            anilistId,
            episodeNum,
            streamType,
            animeTitle,
            customStreamAnimeId,
            customStreamEpisodeNum
        )

        if (waterfallRes != null && isFallbackShift && !failedRequestedServer.isNullOrBlank()) {
            val m = waterfallRes.toMutableMap()
            m["isFallback"] = true
            m["fallbackFrom"] = failedRequestedServer
            return@withContext m
        }
        return@withContext waterfallRes
    }

    private suspend fun extractWaterfall(
        context: Context,
        anilistId: Int,
        episodeNum: Int,
        streamType: String,
        animeTitle: String? = null,
        customStreamAnimeId: Int = anilistId,
        customStreamEpisodeNum: Int = episodeNum
    ): Map<String, Any>? = withContext(Dispatchers.IO) {
        val type = streamType.lowercase()

        // 0. Auto Default / Top Priority: Check Manual Custom Streams from Supabase (Dorara)
        try {
            val customStream = com.lagradost.cloudstream3.ui.animebox.api.CustomStreamManager.resolveCustomStream(
                anilistId = customStreamAnimeId,
                episodeNum = customStreamEpisodeNum,
                streamType = streamType,
                animeTitle = animeTitle
            )
            if (customStream != null && (customStream["hls"] as? String)?.isNotEmpty() == true) {
                return@withContext customStream
            }
        } catch (_: Exception) {}

        val isShinChan = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(anilistId, animeTitle)

        // Shin Chan Japanese HardSub source (default for Shin Chan)
        if (isShinChan && (type != "hindi")) {
            val shinChanRes = ShinChanOkRuExtractor.extractShinChanStream(anilistId, episodeNum, animeTitle)
            if (shinChanRes != null && shinChanRes.hlsUrl.isNotEmpty()) {
                return@withContext mapOf(
                    "hls" to shinChanRes.hlsUrl,
                    "referer" to shinChanRes.referer,
                    "subtitle" to "",
                    "subtitlesJson" to "[]",
                    "isHardSub" to true,
                    "provider" to shinChanRes.provider,
                    "title" to shinChanRes.title
                )
            }
        }

        // 1. Hindi Dub Request (AnimeDekho Primary -> Blakite -> RareAnime -> MultiMovies Backups; Blissey's Husband exclusively for Pokemon Hindi)
        if (type == "hindi") {
            if (!com.lagradost.cloudstream3.ui.animebox.api.ServerVisibilityConfig.isLanguageEnabled("hindi")) {
                return@withContext null
            }
            val subInfoDeferred = async(Dispatchers.IO) {
                kotlinx.coroutines.withTimeoutOrNull(2500L) {
                    fetchEnglishSubSource(anilistId, episodeNum)
                }
            }
            if (PokemonHindiExtractor.isPokemon(anilistId, animeTitle)) {
                try {
                    val pRes = PokemonHindiExtractor.extractPokemonStream(anilistId, episodeNum, animeTitle)
                    if (pRes != null && pRes.hlsUrl.isNotEmpty()) {
                        val subInfo = subInfoDeferred.await() ?: SubSourceInfo()
                        val subtitle = subInfo.subtitleUrl.ifEmpty { pRes.subtitleUrl }
                        val subsJson = if (subInfo.subtitles.isNotEmpty()) {
                            JSONArray().apply { subInfo.subtitles.forEach { put(JSONObject(it)) } }.toString()
                        } else "[]"
                        return@withContext mapOf(
                            "hls" to pRes.hlsUrl,
                            "referer" to pRes.referer,
                            "subtitle" to subtitle,
                            "subtitlesJson" to subsJson,
                            "isHardSub" to false,
                            "provider" to pRes.provider,
                            "title" to pRes.title,
                            "introStart" to subInfo.introStart,
                            "introEnd" to subInfo.introEnd,
                            "outroStart" to subInfo.outroStart,
                            "outroEnd" to subInfo.outroEnd
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            val hindiResult = MultiMoviesHindiExtractor.extractHindiStreamInfo(anilistId, episodeNum, animeTitle)
            if (hindiResult != null && (hindiResult["hls"] as? String)?.isNotEmpty() == true) {
                val subInfo = subInfoDeferred.await() ?: SubSourceInfo()
                val subtitle = subInfo.subtitleUrl.ifEmpty { (hindiResult["subtitle"] as? String ?: "") }
                val subsJson = if (subInfo.subtitles.isNotEmpty()) {
                    JSONArray().apply { subInfo.subtitles.forEach { put(JSONObject(it)) } }.toString()
                } else (hindiResult["subtitlesJson"] as? String ?: "[]")

                val introStart = if (subInfo.introStart > 0) subInfo.introStart else ((hindiResult["introStart"] as? Number)?.toLong() ?: 0L)
                val introEnd = if (subInfo.introEnd > 0) subInfo.introEnd else ((hindiResult["introEnd"] as? Number)?.toLong() ?: 0L)
                val outroStart = if (subInfo.outroStart > 0) subInfo.outroStart else ((hindiResult["outroStart"] as? Number)?.toLong() ?: 0L)
                val outroEnd = if (subInfo.outroEnd > 0) subInfo.outroEnd else ((hindiResult["outroEnd"] as? Number)?.toLong() ?: 0L)

                val mutableMap = hindiResult.toMutableMap()
                if (subtitle.isNotEmpty()) mutableMap["subtitle"] = subtitle
                if (subsJson != "[]") mutableMap["subtitlesJson"] = subsJson
                if (introStart > 0) mutableMap["introStart"] = introStart
                if (introEnd > 0) mutableMap["introEnd"] = introEnd
                if (outroStart > 0) mutableMap["outroStart"] = outroStart
                if (outroEnd > 0) mutableMap["outroEnd"] = outroEnd

                return@withContext mutableMap
            }
            subInfoDeferred.cancel()
            return@withContext null
        }

        // 1b. Regional Audio Requests (French VF, Italian ITA, Spanish Latino/Castellano, German)
        if (type in listOf("french", "vf", "fr", "italian", "ita", "it", "spanish", "es", "spanish_latino", "latino", "spanish_castellano", "castellano", "german", "deutsch", "de")) {
            if (!com.lagradost.cloudstream3.ui.animebox.api.ServerVisibilityConfig.isLanguageEnabled(type)) {
                return@withContext null
            }
            val regionalResult = MultiLanguageAudioExtractor.extractRegionalAudio(anilistId, episodeNum, type, animeTitle)
            if (regionalResult != null && regionalResult.hlsUrl.isNotEmpty()) {
                val primaryEnabled = com.lagradost.cloudstream3.ui.animebox.api.ServerVisibilityConfig.isServerEnabled(regionalResult.provider)
                val backupEnabled = regionalResult.backupHlsUrl.isNotEmpty() && com.lagradost.cloudstream3.ui.animebox.api.ServerVisibilityConfig.isServerEnabled(regionalResult.backupProvider)

                val (chosenHls, chosenRef, chosenProv) = when {
                    primaryEnabled -> Triple(regionalResult.hlsUrl, regionalResult.referer, regionalResult.provider)
                    backupEnabled -> Triple(regionalResult.backupHlsUrl, regionalResult.referer, regionalResult.backupProvider)
                    else -> return@withContext null
                }

                // Background fetch English subtitle and skip intro/outro markers from Japanese/Sub source
                val subInfo = fetchEnglishSubSource(anilistId, episodeNum)
                val subtitle = subInfo.subtitleUrl.ifEmpty { regionalResult.subtitleUrl }
                val subsJson = if (subInfo.subtitles.isNotEmpty()) {
                    JSONArray().apply { subInfo.subtitles.forEach { put(JSONObject(it)) } }.toString()
                } else {
                    JSONArray().apply { regionalResult.subtitles.forEach { put(JSONObject(it)) } }.toString()
                }

                val introStart = subInfo.introStart
                val introEnd = subInfo.introEnd
                val outroStart = subInfo.outroStart
                val outroEnd = subInfo.outroEnd

                val resMap = mutableMapOf<String, Any>(
                    "hls" to chosenHls,
                    "referer" to chosenRef,
                    "subtitle" to subtitle,
                    "subtitlesJson" to subsJson,
                    "isHardSub" to false,
                    "provider" to chosenProv,
                    "introStart" to introStart,
                    "introEnd" to introEnd,
                    "outroStart" to outroStart,
                    "outroEnd" to outroEnd
                )
                if (chosenHls == regionalResult.hlsUrl && backupEnabled) {
                    resMap["backupHls"] = regionalResult.backupHlsUrl
                    resMap["backupProvider"] = regionalResult.backupProvider
                }
                return@withContext resMap
            }
            return@withContext null
        }

        // 2. Hard Sub Request (AniChan primary -> AnimeGG secondary -> Miruro -> Animex -> AniNeko)
        if (type == "hardsub") {
            val hardSubResult = HardSubExtractor.extractHardSub(anilistId, episodeNum, animeTitle, context)
            if (hardSubResult != null && hardSubResult.hlsUrl.isNotEmpty()) {
                val subsJson = JSONArray().apply { hardSubResult.subtitles.forEach { put(JSONObject(it)) } }
                return@withContext mapOf(
                    "hls" to hardSubResult.hlsUrl,
                    "referer" to hardSubResult.referer,
                    "subtitle" to hardSubResult.subtitleUrl,
                    "subtitlesJson" to subsJson.toString(),
                    "isHardSub" to true,
                    "provider" to hardSubResult.provider,
                    "introStart" to hardSubResult.introStart,
                    "introEnd" to hardSubResult.introEnd,
                    "outroStart" to hardSubResult.outroStart,
                    "outroEnd" to hardSubResult.outroEnd
                )
            }
            return@withContext null
        }

        // 3. English Dub Request (MegaPlay Dub -> FourAnimo Dub -> Dedicated Pokemon/Special -> AnimeGG Dub)
        if (type == "dub") {
            if (!com.lagradost.cloudstream3.ui.animebox.api.ServerVisibilityConfig.isLanguageEnabled("english")) {
                return@withContext null
            }
            // Special #1 Priority for AniList ID 2158 (Toward the Terra): AnimeGG Dub
            if (anilistId == 2158) {
                val animeGgDub2158 = try {
                    AnimeGGExtractor.extract(anilistId, episodeNum, animeTitle ?: "Toward the Terra", mode = "dub")
                } catch (e: Exception) { null }

                if (animeGgDub2158 != null && animeGgDub2158.streamUrl.isNotEmpty()) {
                    return@withContext mapOf(
                        "hls" to animeGgDub2158.streamUrl,
                        "referer" to animeGgDub2158.referer,
                        "subtitle" to "",
                        "subtitlesJson" to "[]",
                        "isHardSub" to false,
                        "provider" to animeGgDub2158.provider,
                        "introStart" to 0L,
                        "introEnd" to 0L,
                        "outroStart" to 0L,
                        "outroEnd" to 0L
                    )
                }
            }

            // 3a. MegaPlay Dub (Top priority for Dub)
            val megaPlayDub = try {
                MegaPlayExtractor.extract(anilistId, episodeNum, "dub")
            } catch (e: Exception) { null }

            if (megaPlayDub != null && megaPlayDub.hlsUrl.isNotEmpty()) {
                val subsJson = JSONArray().apply {
                    megaPlayDub.subtitles.forEach { subMap ->
                        put(JSONObject(subMap))
                    }
                }
                return@withContext mapOf(
                    "hls" to megaPlayDub.hlsUrl,
                    "referer" to megaPlayDub.referer,
                    "subtitle" to megaPlayDub.subtitleUrl,
                    "subtitlesJson" to subsJson.toString(),
                    "isHardSub" to false,
                    "provider" to "megaplay (dub)",
                    "introStart" to megaPlayDub.introStart,
                    "introEnd" to megaPlayDub.introEnd,
                    "outroStart" to megaPlayDub.outroStart,
                    "outroEnd" to megaPlayDub.outroEnd
                )
            }

            // 3b. Ani.pm Dub (Direct AniList mapping with multi-subtitles and skip markers)
            val aniPmDub = try {
                AniPmExtractor.extract(anilistId, episodeNum, "dub")
            } catch (e: Exception) { null }

            if (aniPmDub != null && aniPmDub.hlsUrl.isNotEmpty()) {
                val subsJson = JSONArray().apply {
                    aniPmDub.subtitles.forEach { subMap -> put(JSONObject(subMap)) }
                }
                return@withContext mapOf(
                    "hls" to aniPmDub.hlsUrl,
                    "referer" to aniPmDub.referer,
                    "subtitle" to aniPmDub.subtitleUrl,
                    "subtitles" to subsJson.toString(),
                    "subtitlesJson" to subsJson.toString(),
                    "isHardSub" to false,
                    "provider" to aniPmDub.provider,
                    "introStart" to aniPmDub.introStart,
                    "introEnd" to aniPmDub.introEnd,
                    "outroStart" to aniPmDub.outroStart,
                    "outroEnd" to aniPmDub.outroEnd
                )
            }

            // 3b-2. Kiwi (Miruro) Dub
            val kiwiDub = try {
                MiruroKiwiExtractor.extract(context, anilistId, episodeNum, "dub", "kiwi")
            } catch (e: Exception) { null }

            if (kiwiDub != null && kiwiDub.hlsUrl.isNotEmpty()) {
                val subsJson = JSONArray().apply {
                    kiwiDub.subtitles.forEach { subMap -> put(JSONObject(subMap)) }
                }
                return@withContext mapOf(
                    "hls" to kiwiDub.hlsUrl,
                    "referer" to kiwiDub.referer,
                    "subtitle" to kiwiDub.subtitleUrl,
                    "subtitles" to subsJson.toString(),
                    "subtitlesJson" to subsJson.toString(),
                    "isHardSub" to false,
                    "provider" to kiwiDub.provider,
                    "introStart" to kiwiDub.introStart,
                    "introEnd" to kiwiDub.introEnd,
                    "outroStart" to kiwiDub.outroStart,
                    "outroEnd" to kiwiDub.outroEnd
                )
            }

            // 3b-3. AnimePahe Dub
            val paheDub = try {
                AnimePaheExtractor.extract(anilistId, episodeNum, animeTitle, mode = "dub", context = context)
            } catch (e: Exception) { null }

            if (paheDub != null && paheDub.hlsUrl.isNotEmpty()) {
                return@withContext mapOf(
                    "hls" to paheDub.hlsUrl,
                    "referer" to paheDub.referer,
                    "subtitle" to "",
                    "subtitlesJson" to "[]",
                    "isHardSub" to false,
                    "provider" to paheDub.provider,
                    "introStart" to 0L,
                    "introEnd" to 0L,
                    "outroStart" to 0L,
                    "outroEnd" to 0L
                )
            }

            // 3c. Re:ANIME Dub (Above 4Animo Dub)
            val reAnimeDub = try {
                ReAnimeExtractor.extract(anilistId, episodeNum, "dub")
            } catch (e: Exception) { null }

            if (reAnimeDub != null && reAnimeDub.hlsUrl.isNotEmpty()) {
                val subsJson = JSONArray().apply {
                    reAnimeDub.subtitles.forEach { subMap -> put(JSONObject(subMap)) }
                }
                return@withContext mapOf(
                    "hls" to reAnimeDub.hlsUrl,
                    "referer" to reAnimeDub.referer,
                    "subtitle" to reAnimeDub.subtitleUrl,
                    "subtitles" to subsJson.toString(),
                    "subtitlesJson" to subsJson.toString(),
                    "isHardSub" to false,
                    "provider" to reAnimeDub.provider,
                    "introStart" to reAnimeDub.introStart,
                    "introEnd" to reAnimeDub.introEnd,
                    "outroStart" to reAnimeDub.outroStart,
                    "outroEnd" to reAnimeDub.outroEnd
                )
            }

            // 3c. 4Animo Dub (Below ReAnime)
            val fourAnimoDub = try {
                FourAnimoExtractor.extract(anilistId, episodeNum, "dub")
            } catch (e: Exception) { null }

            if (fourAnimoDub != null && fourAnimoDub.hlsUrl.isNotEmpty()) {
                val subsJson = JSONArray().apply {
                    fourAnimoDub.subtitles.forEach { subMap -> put(JSONObject(subMap)) }
                }
                return@withContext mapOf(
                    "hls" to fourAnimoDub.hlsUrl,
                    "referer" to fourAnimoDub.referer,
                    "subtitle" to fourAnimoDub.subtitleUrl,
                    "subtitlesJson" to subsJson.toString(),
                    "isHardSub" to false,
                    "provider" to fourAnimoDub.provider,
                    "introStart" to fourAnimoDub.introStart,
                    "introEnd" to fourAnimoDub.introEnd,
                    "outroStart" to fourAnimoDub.outroStart,
                    "outroEnd" to fourAnimoDub.outroEnd
                )
            }

            // 3c. AnimeGG Dub fallback (MegaPlay and 4Animo above AnimeGG)
            val animeGgDub = try {
                AnimeGGExtractor.extract(anilistId, episodeNum, animeTitle, mode = "dub")
            } catch (e: Exception) { null }

            if (animeGgDub != null && animeGgDub.streamUrl.isNotEmpty()) {
                return@withContext mapOf(
                    "hls" to animeGgDub.streamUrl,
                    "referer" to animeGgDub.referer,
                    "subtitle" to "",
                    "subtitlesJson" to "[]",
                    "isHardSub" to false,
                    "provider" to animeGgDub.provider,
                    "introStart" to 0L,
                    "introEnd" to 0L,
                    "outroStart" to 0L,
                    "outroEnd" to 0L
                )
            }

            // 3d. AniWave Dub fallback (at bottom of dub category with soft sub AI dubtitle)
            val aniWaveDub = try {
                AniwavesExtractor.extractDub(anilistId, episodeNum, animeTitle)
            } catch (e: Exception) { null }

            if (aniWaveDub != null && aniWaveDub.hlsUrl.isNotEmpty()) {
                val subsJson = JSONArray().apply {
                    aniWaveDub.subtitles.forEach { subMap -> put(JSONObject(subMap)) }
                }
                return@withContext mapOf(
                    "hls" to aniWaveDub.hlsUrl,
                    "referer" to aniWaveDub.referer,
                    "subtitle" to aniWaveDub.subtitleUrl,
                    "subtitlesJson" to subsJson.toString(),
                    "isHardSub" to false,
                    "provider" to "AniWave Dub",
                    "introStart" to aniWaveDub.introStart,
                    "introEnd" to aniWaveDub.introEnd,
                    "outroStart" to aniWaveDub.outroStart,
                    "outroEnd" to aniWaveDub.outroEnd
                )
            }

            return@withContext null
        }

        // 4. Soft Sub Request (Special 2158 / 178788 SR -> MegaPlay #1 -> 4Animo -> AniBd (SR) -> VidHawk -> Animex other -> AniKoto -> HardSub)
        // Special #1 Priority for AniList ID 2158 (Toward the Terra) & 178788 (Demon Slayer Infinity Castle): AniBd SR provider
        if (anilistId == 2158 || anilistId == 178788) {
            val aniBdSpecial = try {
                AniBdExtractor.extract(anilistId, episodeNum)
            } catch (_: Exception) { null }

            if (aniBdSpecial != null && aniBdSpecial.hlsUrl.isNotEmpty()) {
                val subsJson = JSONArray().apply {
                    aniBdSpecial.subtitles.forEach { subMap -> put(JSONObject(subMap)) }
                }
                return@withContext mapOf(
                    "hls" to aniBdSpecial.hlsUrl,
                    "referer" to aniBdSpecial.referer,
                    "subtitle" to aniBdSpecial.subtitleUrl,
                    "subtitlesJson" to subsJson.toString(),
                    "isHardSub" to false,
                    "provider" to "anibd (${aniBdSpecial.provider})",
                    "introStart" to 0L,
                    "introEnd" to 0L,
                    "outroStart" to 0L,
                    "outroEnd" to 0L
                )
            }
        }

        // 4a. MegaPlay Soft Sub (Strict Primary #1 Source - Instant Direct Open)
        val megaPlayResult = try {
            MegaPlayExtractor.extract(anilistId, episodeNum, "sub")
        } catch (_: Exception) { null }

        if (megaPlayResult != null && megaPlayResult.hlsUrl.isNotEmpty()) {
            val subsJson = JSONArray().apply {
                megaPlayResult.subtitles.forEach { subMap ->
                    put(JSONObject(subMap))
                }
            }
            return@withContext mapOf(
                "hls" to megaPlayResult.hlsUrl,
                "referer" to megaPlayResult.referer,
                "subtitle" to megaPlayResult.subtitleUrl,
                "subtitlesJson" to subsJson.toString(),
                "isHardSub" to false,
                "provider" to "megaplay (sub)",
                "introStart" to megaPlayResult.introStart,
                "introEnd" to megaPlayResult.introEnd,
                "outroStart" to megaPlayResult.outroStart,
                "outroEnd" to megaPlayResult.outroEnd
            )
        }

        // 4b. Ani.pm Soft Sub (Direct AniList mapping with multi-subtitles and skip markers)
        val aniPmResult = try {
            AniPmExtractor.extract(anilistId, episodeNum, "sub")
        } catch (_: Exception) { null }

        if (aniPmResult != null && aniPmResult.hlsUrl.isNotEmpty()) {
            val subsJson = JSONArray().apply {
                aniPmResult.subtitles.forEach { subMap -> put(JSONObject(subMap)) }
            }
            return@withContext mapOf(
                "hls" to aniPmResult.hlsUrl,
                "referer" to aniPmResult.referer,
                "subtitle" to aniPmResult.subtitleUrl,
                "subtitlesJson" to subsJson.toString(),
                "isHardSub" to false,
                "provider" to aniPmResult.provider,
                "introStart" to aniPmResult.introStart,
                "introEnd" to aniPmResult.introEnd,
                "outroStart" to aniPmResult.outroStart,
                "outroEnd" to aniPmResult.outroEnd
            )
        }

        // 4b-2. Kiwi (Miruro) Sub
        val kiwiSubResult = try {
            MiruroKiwiExtractor.extract(context, anilistId, episodeNum, "sub", "kiwi")
        } catch (_: Exception) { null }

        if (kiwiSubResult != null && kiwiSubResult.hlsUrl.isNotEmpty()) {
            val subsJson = JSONArray().apply {
                kiwiSubResult.subtitles.forEach { subMap -> put(JSONObject(subMap)) }
            }
            return@withContext mapOf(
                "hls" to kiwiSubResult.hlsUrl,
                "referer" to kiwiSubResult.referer,
                "subtitle" to kiwiSubResult.subtitleUrl,
                "subtitlesJson" to subsJson.toString(),
                "isHardSub" to kiwiSubResult.subtitleUrl.isEmpty(),
                "provider" to kiwiSubResult.provider,
                "introStart" to kiwiSubResult.introStart,
                "introEnd" to kiwiSubResult.introEnd,
                "outroStart" to kiwiSubResult.outroStart,
                "outroEnd" to kiwiSubResult.outroEnd
            )
        }

        // 4c. Re:ANIME Soft Sub (Above FourAnimo, with English subtitle fallback)
        val reAnimeResult = try {
            ReAnimeExtractor.extract(anilistId, episodeNum, "sub")
        } catch (_: Exception) { null }

        if (reAnimeResult != null && reAnimeResult.hlsUrl.isNotEmpty()) {
            var subtitleUrl = reAnimeResult.subtitleUrl
            var subsJson = JSONArray().apply {
                reAnimeResult.subtitles.forEach { subMap -> put(JSONObject(subMap)) }
            }.toString()
            val hasEnglish = reAnimeResult.subtitles.any { (it["lang"] ?: "").contains("eng", true) }

            if (subtitleUrl.isEmpty() || !hasEnglish) {
                // Try to get English subtitle from soft sub sources (MegaPlay -> AniBD -> FourAnimo)
                val megaSubs = try { MegaPlayExtractor.extract(anilistId, episodeNum, "sub") } catch (_: Exception) { null }
                if (megaSubs != null && megaSubs.subtitles.isNotEmpty()) {
                    subtitleUrl = megaSubs.subtitleUrl
                    subsJson = JSONArray().apply { megaSubs.subtitles.forEach { put(JSONObject(it)) } }.toString()
                } else {
                    val anibdSubs = try { AniBdExtractor.extract(anilistId, episodeNum) } catch (_: Exception) { null }
                    if (anibdSubs != null && anibdSubs.subtitles.isNotEmpty()) {
                        subtitleUrl = anibdSubs.subtitleUrl
                        subsJson = JSONArray().apply { anibdSubs.subtitles.forEach { put(JSONObject(it)) } }.toString()
                    } else {
                        val fourSubs = try { FourAnimoExtractor.extract(anilistId, episodeNum, "sub") } catch (_: Exception) { null }
                        if (fourSubs != null && fourSubs.subtitles.isNotEmpty()) {
                            subtitleUrl = fourSubs.subtitleUrl
                            subsJson = JSONArray().apply { fourSubs.subtitles.forEach { put(JSONObject(it)) } }.toString()
                        }
                    }
                }
            }

            return@withContext mapOf(
                "hls" to reAnimeResult.hlsUrl,
                "referer" to reAnimeResult.referer,
                "subtitle" to subtitleUrl,
                "subtitlesJson" to subsJson,
                "isHardSub" to false,
                "provider" to reAnimeResult.provider,
                "introStart" to reAnimeResult.introStart,
                "introEnd" to reAnimeResult.introEnd,
                "outroStart" to reAnimeResult.outroStart,
                "outroEnd" to reAnimeResult.outroEnd
            )
        }

        // 4c. 4Animo Sub (Below ReAnime)
        val fourAnimoResult = try {
            FourAnimoExtractor.extract(anilistId, episodeNum, "sub")
        } catch (_: Exception) { null }

        if (fourAnimoResult != null && fourAnimoResult.hlsUrl.isNotEmpty()) {
            val subsJson = JSONArray().apply {
                fourAnimoResult.subtitles.forEach { subMap ->
                    put(JSONObject(subMap))
                }
            }
            return@withContext mapOf(
                "hls" to fourAnimoResult.hlsUrl,
                "referer" to fourAnimoResult.referer,
                "subtitle" to fourAnimoResult.subtitleUrl,
                "subtitlesJson" to subsJson.toString(),
                "isHardSub" to false,
                "provider" to fourAnimoResult.provider,
                "introStart" to fourAnimoResult.introStart,
                "introEnd" to fourAnimoResult.introEnd,
                "outroStart" to fourAnimoResult.outroStart,
                "outroEnd" to fourAnimoResult.outroEnd
            )
        }

        // 4d. AniBd SR provider (Soft Sub provider after Re:ANIME)
        val aniBdResult = try {
            AniBdExtractor.extract(anilistId, episodeNum)
        } catch (_: Exception) { null }

        if (aniBdResult != null && aniBdResult.hlsUrl.isNotEmpty()) {
            val subsJson = JSONArray().apply {
                aniBdResult.subtitles.forEach { subMap ->
                    put(JSONObject(subMap))
                }
            }
            return@withContext mapOf(
                "hls" to aniBdResult.hlsUrl,
                "referer" to aniBdResult.referer,
                "subtitle" to aniBdResult.subtitleUrl,
                "subtitlesJson" to subsJson.toString(),
                "isHardSub" to false,
                "provider" to "anibd (${aniBdResult.provider})",
                "introStart" to 0L,
                "introEnd" to 0L,
                "outroStart" to 0L,
                "outroEnd" to 0L
            )
        }

        // 4e. AnimeGG Sub fallback
        val animeGgSub = try {
            AnimeGGExtractor.extract(anilistId, episodeNum, animeTitle, mode = "sub")
        } catch (_: Exception) { null }

        if (animeGgSub != null && animeGgSub.streamUrl.isNotEmpty()) {
            return@withContext mapOf(
                "hls" to animeGgSub.streamUrl,
                "referer" to animeGgSub.referer,
                "subtitle" to "",
                "subtitlesJson" to "[]",
                "isHardSub" to false,
                "provider" to animeGgSub.provider,
                "introStart" to 0L,
                "introEnd" to 0L,
                "outroStart" to 0L,
                "outroEnd" to 0L
            )
        }

        // 4f. KissAssAnime Soft Sub (bottom of soft sub category for Japanese only)
        val kaaSub = try {
            KissAssAnimeExtractor.extract(anilistId, episodeNum, animeTitle)
        } catch (_: Exception) { null }

        if (kaaSub != null && kaaSub.hlsUrl.isNotEmpty()) {
            val subsJson = JSONArray().apply {
                kaaSub.subtitles.forEach { subMap -> put(JSONObject(subMap)) }
            }
            return@withContext mapOf(
                "hls" to kaaSub.hlsUrl,
                "referer" to kaaSub.referer,
                "subtitle" to kaaSub.subtitleUrl,
                "subtitlesJson" to subsJson.toString(),
                "isHardSub" to false,
                "provider" to "KissAssAnime",
                "introStart" to kaaSub.introStart,
                "introEnd" to kaaSub.introEnd,
                "outroStart" to kaaSub.outroStart,
                "outroEnd" to kaaSub.outroEnd
            )
        }

        // 4g. Fallback to HardSubExtractor (AniNeko -> AnimeGG -> AniBD -> AniWave)
        val hardSubBackup = try {
            HardSubExtractor.extractHardSub(anilistId, episodeNum, animeTitle, context)
        } catch (e: Exception) { null }

        if (hardSubBackup != null && hardSubBackup.hlsUrl.isNotEmpty()) {
            val subsJson = JSONArray().apply { hardSubBackup.subtitles.forEach { put(JSONObject(it)) } }
            return@withContext mapOf(
                "hls" to hardSubBackup.hlsUrl,
                "referer" to hardSubBackup.referer.ifEmpty { "https://anineko.to/" },
                "subtitle" to hardSubBackup.subtitleUrl,
                "subtitlesJson" to subsJson.toString(),
                "isHardSub" to true,
                "provider" to hardSubBackup.provider,
                "introStart" to hardSubBackup.introStart,
                "introEnd" to hardSubBackup.introEnd,
                "outroStart" to hardSubBackup.outroStart,
                "outroEnd" to hardSubBackup.outroEnd
            )
        }

        // 4g. RAW Stream Fallback (For animes where subtitles are not available or are raw)
        val megaPlayRaw = try { MegaPlayExtractor.extract(anilistId, episodeNum, "raw") } catch (_: Exception) { null }
        if (megaPlayRaw != null && megaPlayRaw.hlsUrl.isNotEmpty()) {
            return@withContext mapOf(
                "hls" to megaPlayRaw.hlsUrl,
                "referer" to megaPlayRaw.referer,
                "subtitle" to "",
                "subtitlesJson" to "[]",
                "isHardSub" to false,
                "provider" to "megaplay (raw)",
                "introStart" to megaPlayRaw.introStart,
                "introEnd" to megaPlayRaw.introEnd,
                "outroStart" to megaPlayRaw.outroStart,
                "outroEnd" to megaPlayRaw.outroEnd
            )
        }

        val fourAnimoRaw = try { FourAnimoExtractor.extract(anilistId, episodeNum, "raw") } catch (_: Exception) { null }
        if (fourAnimoRaw != null && fourAnimoRaw.hlsUrl.isNotEmpty()) {
            return@withContext mapOf(
                "hls" to fourAnimoRaw.hlsUrl,
                "referer" to fourAnimoRaw.referer,
                "subtitle" to "",
                "subtitlesJson" to "[]",
                "isHardSub" to false,
                "provider" to "${fourAnimoRaw.provider} (raw)",
                "introStart" to fourAnimoRaw.introStart,
                "introEnd" to fourAnimoRaw.introEnd,
                "outroStart" to fourAnimoRaw.outroStart,
                "outroEnd" to fourAnimoRaw.outroEnd
            )
        }

        val animexRaw = try { AnimexExtractor.extract(anilistId, episodeNum, "raw") } catch (_: Exception) { null }
        if (animexRaw != null && animexRaw.url.isNotEmpty()) {
            return@withContext mapOf(
                "hls" to animexRaw.url,
                "referer" to animexRaw.referer,
                "subtitle" to "",
                "subtitlesJson" to "[]",
                "isHardSub" to false,
                "provider" to "animex (${animexRaw.provider} raw)",
                "introStart" to animexRaw.introStart,
                "introEnd" to animexRaw.introEnd,
                "outroStart" to animexRaw.outroStart,
                "outroEnd" to animexRaw.outroEnd
            )
        }

        val animeGgRaw = try { AnimeGGExtractor.extract(anilistId, episodeNum, animeTitle, mode = "raw") } catch (_: Exception) { null }
        if (animeGgRaw != null && animeGgRaw.streamUrl.isNotEmpty()) {
            return@withContext mapOf(
                "hls" to animeGgRaw.streamUrl,
                "referer" to animeGgRaw.referer,
                "subtitle" to "",
                "subtitlesJson" to "[]",
                "isHardSub" to false,
                "provider" to "${animeGgRaw.provider} (raw)",
                "introStart" to 0L,
                "introEnd" to 0L,
                "outroStart" to 0L,
                "outroEnd" to 0L
            )
        }

        return@withContext null
    }

    private suspend fun tryPreferredServer(
        context: Context,
        anilistId: Int,
        episodeNum: Int,
        streamType: String,
        animeTitle: String? = null,
        serverOverride: String? = null
    ): Map<String, Any>? {
        val pref = (serverOverride?.takeIf { it.isNotBlank() && it != com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.SERVER_AUTO }
            ?: com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.getPreferredServer(context, streamType)).lowercase()
        if (pref == com.lagradost.cloudstream3.ui.animebox.settings.ServerPriorityManager.SERVER_AUTO) return null
        if (pref != "dorara" && !com.lagradost.cloudstream3.ui.animebox.api.ServerVisibilityConfig.isServerEnabled(pref)) return null

        return when (pref) {
            "dorara" -> {
                val res = try {
                    com.lagradost.cloudstream3.ui.animebox.api.CustomStreamManager.resolveCustomStream(
                        anilistId = anilistId,
                        episodeNum = episodeNum,
                        streamType = streamType,
                        animeTitle = animeTitle
                    )
                } catch (_: Exception) { null }
                if (res != null && (res["hls"] as? String)?.isNotEmpty() == true) res else null
            }
            // --- Soft Sub options ---
            "anipm" -> {
                val res = try { AniPmExtractor.extract(anilistId, episodeNum, "sub") } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    val subsJson = JSONArray().apply { res.subtitles.forEach { put(JSONObject(it)) } }
                    mapOf(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to res.subtitleUrl,
                        "subtitlesJson" to subsJson.toString(),
                        "isHardSub" to false,
                        "provider" to res.provider,
                        "introStart" to res.introStart,
                        "introEnd" to res.introEnd,
                        "outroStart" to res.outroStart,
                        "outroEnd" to res.outroEnd
                    )
                } else null
            }
            "kiwi" -> {
                val mode = if (streamType.equals("dub", true)) "dub" else "sub"
                val res = try { MiruroKiwiExtractor.extract(context, anilistId, episodeNum, mode, "kiwi") } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    val subsJson = JSONArray().apply { res.subtitles.forEach { put(JSONObject(it)) } }
                    mapOf(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to res.subtitleUrl,
                        "subtitles" to subsJson.toString(),
                        "subtitlesJson" to subsJson.toString(),
                        "isHardSub" to (streamType.equals("hardsub", true) || res.subtitleUrl.isEmpty()),
                        "provider" to res.provider,
                        "introStart" to res.introStart,
                        "introEnd" to res.introEnd,
                        "outroStart" to res.outroStart,
                        "outroEnd" to res.outroEnd
                    )
                } else null
            }
            "bee", "bun", "sun", "hop", "knob" -> {
                val mode = if (streamType.equals("dub", true)) "dub" else "sub"
                val res = try { MiruroKiwiExtractor.extract(context, anilistId, episodeNum, mode, pref) } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    val subsJson = JSONArray().apply { res.subtitles.forEach { put(JSONObject(it)) } }
                    mapOf(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to res.subtitleUrl,
                        "subtitles" to subsJson.toString(),
                        "subtitlesJson" to subsJson.toString(),
                        "isHardSub" to (streamType.equals("hardsub", true) || res.subtitleUrl.isEmpty()),
                        "provider" to res.provider,
                        "introStart" to res.introStart,
                        "introEnd" to res.introEnd,
                        "outroStart" to res.outroStart,
                        "outroEnd" to res.outroEnd
                    )
                } else null
            }
            "2dhive" -> {
                val mode = if (streamType.equals("dub", true)) "dub" else "sub"
                val res = try { TwoDHiveExtractor.extract(anilistId, episodeNum, mode, animeTitle) } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    val subsJson = JSONArray().apply { res.subtitles.forEach { put(JSONObject(it)) } }
                    mapOf(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to res.subtitleUrl,
                        "subtitles" to subsJson.toString(),
                        "subtitlesJson" to subsJson.toString(),
                        "isHardSub" to (streamType.equals("hardsub", true) || res.subtitleUrl.isEmpty()),
                        "provider" to res.provider,
                        "introStart" to res.introStart,
                        "introEnd" to res.introEnd,
                        "outroStart" to res.outroStart,
                        "outroEnd" to res.outroEnd
                    )
                } else null
            }
            "megaplay" -> {
                val res = try { MegaPlayExtractor.extract(anilistId, episodeNum, "sub") } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    val subsJson = JSONArray().apply { res.subtitles.forEach { put(JSONObject(it)) } }
                    mapOf(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to res.subtitleUrl,
                        "subtitlesJson" to subsJson.toString(),
                        "isHardSub" to false,
                        "provider" to "MegaPlay",
                        "introStart" to res.introStart,
                        "introEnd" to res.introEnd,
                        "outroStart" to res.outroStart,
                        "outroEnd" to res.outroEnd
                    )
                } else null
            }
            "fouranimo" -> {
                val res = try { FourAnimoExtractor.extract(anilistId, episodeNum, "sub") } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    val subsJson = JSONArray().apply { res.subtitles.forEach { put(JSONObject(it)) } }
                    mapOf(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to res.subtitleUrl,
                        "subtitlesJson" to subsJson.toString(),
                        "isHardSub" to false,
                        "provider" to "FourAnimo",
                        "introStart" to res.introStart,
                        "introEnd" to res.introEnd,
                        "outroStart" to res.outroStart,
                        "outroEnd" to res.outroEnd
                    )
                } else null
            }
            "reanime" -> {
                val res = try { ReAnimeExtractor.extract(anilistId, episodeNum, "sub") } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    var subtitleUrl = res.subtitleUrl
                    var subsJson = JSONArray().apply {
                        res.subtitles.forEach { subMap -> put(JSONObject(subMap)) }
                    }.toString()
                    val hasEnglish = res.subtitles.any { (it["lang"] ?: "").contains("eng", true) }

                    if (subtitleUrl.isEmpty() || !hasEnglish) {
                        // Subtitle fallback for ReAnime
                        val megaSubs = try { MegaPlayExtractor.extract(anilistId, episodeNum, "sub") } catch (_: Exception) { null }
                        if (megaSubs != null && megaSubs.subtitles.isNotEmpty()) {
                            subtitleUrl = megaSubs.subtitleUrl
                            subsJson = JSONArray().apply { megaSubs.subtitles.forEach { put(JSONObject(it)) } }.toString()
                        } else {
                            val anibdSubs = try { AniBdExtractor.extract(anilistId, episodeNum) } catch (_: Exception) { null }
                            if (anibdSubs != null && anibdSubs.subtitles.isNotEmpty()) {
                                subtitleUrl = anibdSubs.subtitleUrl
                                subsJson = JSONArray().apply { anibdSubs.subtitles.forEach { put(JSONObject(it)) } }.toString()
                            } else {
                                val fourSubs = try { FourAnimoExtractor.extract(anilistId, episodeNum, "sub") } catch (_: Exception) { null }
                                if (fourSubs != null && fourSubs.subtitles.isNotEmpty()) {
                                    subtitleUrl = fourSubs.subtitleUrl
                                    subsJson = JSONArray().apply { fourSubs.subtitles.forEach { put(JSONObject(it)) } }.toString()
                                }
                            }
                        }
                    }

                    mapOf(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to subtitleUrl,
                        "subtitlesJson" to subsJson,
                        "isHardSub" to false,
                        "provider" to res.provider,
                        "introStart" to res.introStart,
                        "introEnd" to res.introEnd,
                        "outroStart" to res.outroStart,
                        "outroEnd" to res.outroEnd
                    )
                } else null
            }
            "anibd" -> {
                val res = try { AniBdExtractor.extract(anilistId, episodeNum) } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    val isSub = streamType.lowercase() == "sub"
                    val subsJson = JSONArray().apply { res.subtitles.forEach { put(JSONObject(it)) } }
                    mapOf(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to res.subtitleUrl,
                        "subtitlesJson" to subsJson.toString(),
                        "isHardSub" to !isSub,
                        "provider" to "AniBD"
                    )
                } else null
            }
            "animegg" -> {
                val res = try { AnimeGGExtractor.extract(anilistId, episodeNum, animeTitle, mode = "sub") } catch (_: Exception) { null }
                if (res != null && res.streamUrl.isNotEmpty()) {
                    mapOf(
                        "hls" to res.streamUrl,
                        "referer" to res.referer,
                        "subtitle" to "",
                        "subtitlesJson" to "[]",
                        "isHardSub" to false,
                        "provider" to res.provider
                    )
                } else null
            }
            "kickassanime", "kissassanime", "kaa" -> {
                val mode = if (streamType.equals("dub", true)) "dub" else "sub"
                val res = try { KissAssAnimeExtractor.extract(anilistId, episodeNum, animeTitle, mode = mode) } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    val subsJson = JSONArray().apply { res.subtitles.forEach { put(JSONObject(it)) } }
                    mapOf(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to res.subtitleUrl,
                        "subtitles" to subsJson.toString(),
                        "subtitlesJson" to subsJson.toString(),
                        "isHardSub" to (streamType.equals("hardsub", true) || res.subtitleUrl.isEmpty()),
                        "provider" to res.provider,
                        "introStart" to res.introStart,
                        "introEnd" to res.introEnd,
                        "outroStart" to res.outroStart,
                        "outroEnd" to res.outroEnd
                    )
                } else null
            }

            // --- English Dub options ---
            "anipm_dub" -> {
                val res = try { AniPmExtractor.extract(anilistId, episodeNum, "dub") } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    val subsJson = JSONArray().apply { res.subtitles.forEach { put(JSONObject(it)) } }
                    mapOf(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to res.subtitleUrl,
                        "subtitles" to subsJson.toString(),
                        "subtitlesJson" to subsJson.toString(),
                        "isHardSub" to false,
                        "provider" to res.provider,
                        "introStart" to res.introStart,
                        "introEnd" to res.introEnd,
                        "outroStart" to res.outroStart,
                        "outroEnd" to res.outroEnd
                    )
                } else null
            }
            "kiwi_dub" -> {
                val res = try { MiruroKiwiExtractor.extract(context, anilistId, episodeNum, "dub", "kiwi") } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    val subsJson = JSONArray().apply { res.subtitles.forEach { put(JSONObject(it)) } }
                    mapOf(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to res.subtitleUrl,
                        "subtitles" to subsJson.toString(),
                        "subtitlesJson" to subsJson.toString(),
                        "isHardSub" to false,
                        "provider" to res.provider,
                        "introStart" to res.introStart,
                        "introEnd" to res.introEnd,
                        "outroStart" to res.outroStart,
                        "outroEnd" to res.outroEnd
                    )
                } else null
            }
            "animepahe_dub" -> {
                val res = try { AnimePaheExtractor.extract(anilistId, episodeNum, animeTitle, mode = "dub", context = context) } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    mapOf(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to "",
                        "subtitlesJson" to "[]",
                        "isHardSub" to false,
                        "provider" to res.provider
                    )
                } else null
            }
            "reanime_dub" -> {
                val res = try { ReAnimeExtractor.extract(anilistId, episodeNum, "dub") } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    var subtitleUrl = res.subtitleUrl
                    var subsJson = JSONArray().apply {
                        res.subtitles.forEach { subMap -> put(JSONObject(subMap)) }
                    }.toString()
                    if (subtitleUrl.isEmpty()) {
                        val subSource = fetchEnglishSubSource(anilistId, episodeNum)
                        if (subSource.subtitleUrl.isNotEmpty()) {
                            subtitleUrl = subSource.subtitleUrl
                            subsJson = JSONArray().apply { subSource.subtitles.forEach { put(JSONObject(it)) } }.toString()
                        }
                    }
                    mapOf(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to subtitleUrl,
                        "subtitles" to subsJson,
                        "subtitlesJson" to subsJson,
                        "isHardSub" to false,
                        "provider" to "ReAnime Dub",
                        "introStart" to res.introStart,
                        "introEnd" to res.introEnd,
                        "outroStart" to res.outroStart,
                        "outroEnd" to res.outroEnd
                    )
                } else null
            }
            "megaplay_dub" -> {
                val res = try { MegaPlayExtractor.extract(anilistId, episodeNum, "dub") } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    mapOf(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to res.subtitleUrl,
                        "subtitlesJson" to "[]",
                        "isHardSub" to false,
                        "provider" to "MegaPlay Dub"
                    )
                } else null
            }
            "fouranimo_dub" -> {
                val res = try { FourAnimoExtractor.extract(anilistId, episodeNum, "dub") } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    mapOf(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to res.subtitleUrl,
                        "subtitlesJson" to "[]",
                        "isHardSub" to false,
                        "provider" to "FourAnimo Dub"
                    )
                } else null
            }
            "animegg_dub" -> {
                val res = try { AnimeGGExtractor.extract(anilistId, episodeNum, animeTitle, mode = "dub") } catch (_: Exception) { null }
                if (res != null && res.streamUrl.isNotEmpty()) {
                    mapOf(
                        "hls" to res.streamUrl,
                        "referer" to res.referer,
                        "subtitle" to "",
                        "subtitlesJson" to "[]",
                        "isHardSub" to false,
                        "provider" to "AnimeGG Dub"
                    )
                } else null
            }
            "aniwaves_dub" -> {
                val res = try { AniwavesExtractor.extractDub(anilistId, episodeNum, animeTitle) } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    val subsJson = JSONArray().apply { res.subtitles.forEach { put(JSONObject(it)) } }
                    mapOf(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to res.subtitleUrl,
                        "subtitlesJson" to subsJson.toString(),
                        "isHardSub" to false,
                        "provider" to "AniWave Dub",
                        "introStart" to res.introStart,
                        "introEnd" to res.introEnd,
                        "outroStart" to res.outroStart,
                        "outroEnd" to res.outroEnd
                    )
                } else null
            }

            // --- Hard Sub options ---
            "animepahe" -> {
                val res = try { AnimePaheExtractor.extract(anilistId, episodeNum, animeTitle, mode = "sub", context = context) } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    mapOf(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to "",
                        "subtitlesJson" to "[]",
                        "isHardSub" to true,
                        "provider" to res.provider
                    )
                } else null
            }
            "reanime_hardsub" -> {
                val res = try { ReAnimeExtractor.extract(anilistId, episodeNum, "sub") } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    var subtitleUrl = res.subtitleUrl
                    var subsJson = JSONArray().apply {
                        res.subtitles.forEach { subMap -> put(JSONObject(subMap)) }
                    }.toString()
                    val hasEnglish = res.subtitles.any { (it["lang"] ?: "").contains("eng", true) }
                    if (subtitleUrl.isEmpty() || !hasEnglish) {
                        val subSource = fetchEnglishSubSource(anilistId, episodeNum)
                        if (subSource.subtitleUrl.isNotEmpty()) {
                            subtitleUrl = subSource.subtitleUrl
                            subsJson = JSONArray().apply { subSource.subtitles.forEach { put(JSONObject(it)) } }.toString()
                        }
                    }
                    mapOf(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to subtitleUrl,
                        "subtitlesJson" to subsJson,
                        "isHardSub" to true,
                        "provider" to "ReAnime",
                        "introStart" to res.introStart,
                        "introEnd" to res.introEnd,
                        "outroStart" to res.outroStart,
                        "outroEnd" to res.outroEnd
                    )
                } else null
            }
            "anineko" -> {
                val res = try { HardSubExtractor.extractAniNeko(anilistId, episodeNum) } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    mapOf(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to "",
                        "subtitlesJson" to "[]",
                        "isHardSub" to true,
                        "provider" to res.provider
                    )
                } else null
            }
            "animegg_hardsub" -> {
                val res = try { AnimeGGExtractor.extract(anilistId, episodeNum, animeTitle, mode = "sub") } catch (_: Exception) { null }
                if (res != null && res.streamUrl.isNotEmpty()) {
                    mapOf(
                        "hls" to res.streamUrl,
                        "referer" to res.referer,
                        "subtitle" to "",
                        "subtitlesJson" to "[]",
                        "isHardSub" to true,
                        "provider" to res.provider
                    )
                } else null
            }
            "aniwaves" -> {
                val res = try { AniwavesExtractor.extractHardSub(anilistId, episodeNum, animeTitle) } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    val subsJson = JSONArray().apply { res.subtitles.forEach { put(JSONObject(it)) } }
                    mapOf(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to res.subtitleUrl,
                        "subtitlesJson" to subsJson.toString(),
                        "isHardSub" to true,
                        "provider" to "AniWave",
                        "introStart" to res.introStart,
                        "introEnd" to res.introEnd,
                        "outroStart" to res.outroStart,
                        "outroEnd" to res.outroEnd
                    )
                } else null
            }

            // --- Hindi Dub options ---
            "blisseyhusband", "blissey" -> {
                if (!streamType.equals("hindi", ignoreCase = true) || !PokemonHindiExtractor.isPokemon(anilistId, animeTitle)) {
                    null
                } else {
                    val pRes = try {
                        PokemonHindiExtractor.extractPokemonStream(anilistId, episodeNum, animeTitle)
                    } catch (_: Exception) { null }
                    if (pRes != null && pRes.hlsUrl.isNotEmpty()) {
                        val subInfo = kotlinx.coroutines.withTimeoutOrNull(2500L) { fetchEnglishSubSource(anilistId, episodeNum) }
                            ?: SubSourceInfo()
                        val subtitle = subInfo.subtitleUrl.ifEmpty { pRes.subtitleUrl }
                        val subsJson = if (subInfo.subtitles.isNotEmpty()) {
                            JSONArray().apply { subInfo.subtitles.forEach { put(JSONObject(it)) } }.toString()
                        } else "[]"
                        mapOf(
                            "hls" to pRes.hlsUrl,
                            "referer" to pRes.referer,
                            "subtitle" to subtitle,
                            "subtitlesJson" to subsJson,
                            "isHardSub" to false,
                            "provider" to pRes.provider,
                            "title" to pRes.title,
                            "introStart" to subInfo.introStart,
                            "introEnd" to subInfo.introEnd,
                            "outroStart" to subInfo.outroStart,
                            "outroEnd" to subInfo.outroEnd
                        )
                    } else null
                }
            }
            "blakite" -> {
                val res = try { LovableHindiExtractor.extractHindiStreams(anilistId, episodeNum).firstOrNull() } catch (_: Exception) { null }
                if (res != null && res.url.isNotEmpty()) {
                    val subInfo = fetchEnglishSubSource(anilistId, episodeNum)
                    val subtitle = subInfo.subtitleUrl
                    val subsJson = if (subInfo.subtitles.isNotEmpty()) {
                        JSONArray().apply { subInfo.subtitles.forEach { put(JSONObject(it)) } }.toString()
                    } else "[]"

                    mapOf(
                        "hls" to res.url,
                        "referer" to res.referer,
                        "subtitle" to subtitle,
                        "subtitlesJson" to subsJson,
                        "isHardSub" to false,
                        "provider" to "Blakite (${res.label})",
                        "introStart" to subInfo.introStart,
                        "introEnd" to subInfo.introEnd,
                        "outroStart" to subInfo.outroStart,
                        "outroEnd" to subInfo.outroEnd
                    )
                } else null
            }
            "animedekho" -> {
                val res = try { AnimeDekhoHindiExtractor.extractDekhoStream(anilistId, episodeNum, animeTitle) } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    mapOf(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to res.subtitleUrl,
                        "subtitlesJson" to "[]",
                        "isHardSub" to false,
                        "provider" to res.provider,
                        "title" to res.title
                    )
                } else null
            }
            "rareanime" -> {
                val rareRes = try { RareAnimeHindiExtractor.extract(anilistId, episodeNum, animeTitle) } catch (_: Exception) { null }
                if (rareRes != null && rareRes.hlsUrl.isNotEmpty()) {
                    mapOf(
                        "hls" to rareRes.hlsUrl,
                        "referer" to rareRes.referer,
                        "subtitle" to "",
                        "subtitlesJson" to "[]",
                        "isHardSub" to false,
                        "provider" to rareRes.provider,
                        "title" to rareRes.title
                    )
                } else null
            }
            "multimovies" -> {
                val res = try { MultiMoviesHindiExtractor.extractHindiStreamInfo(anilistId, episodeNum, animeTitle) } catch (_: Exception) { null }
                if (res != null && (res["hls"] as? String)?.isNotEmpty() == true) res else null
            }
            "lovable" -> {
                val res = try { LovableHindiExtractor.extractHindiStreams(anilistId, episodeNum).firstOrNull() } catch (_: Exception) { null }
                if (res != null && res.url.isNotEmpty()) {
                    mapOf(
                        "hls" to res.url,
                        "referer" to res.referer,
                        "subtitle" to "",
                        "subtitlesJson" to "[]",
                        "isHardSub" to false,
                        "provider" to "lovable (${res.label})"
                    )
                } else null
            }
            "streamhg" -> {
                val res = try { MultiMoviesHindiExtractor.extractHindiStreamInfo(anilistId, episodeNum, animeTitle) } catch (_: Exception) { null }
                if (res != null && (res["hls"] as? String)?.isNotEmpty() == true) res else null
            }

            // --- Regional Audio Preferred Server Options ---
            "valkyrie", "helios", "aura", "mirage" -> {
                val res = try { MultiLanguageAudioExtractor.extractRegionalAudio(anilistId, episodeNum, "french", animeTitle, serverPreference = pref) } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    val subInfo = fetchEnglishSubSource(anilistId, episodeNum)
                    val m = mutableMapOf<String, Any>(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to subInfo.subtitleUrl.ifEmpty { res.subtitleUrl },
                        "subtitlesJson" to if (subInfo.subtitles.isNotEmpty()) JSONArray().apply { subInfo.subtitles.forEach { put(JSONObject(it)) } }.toString() else "[]",
                        "isHardSub" to false,
                        "provider" to res.provider,
                        "introStart" to subInfo.introStart,
                        "introEnd" to subInfo.introEnd,
                        "outroStart" to subInfo.outroStart,
                        "outroEnd" to subInfo.outroEnd
                    )
                    if (res.backupHlsUrl.isNotEmpty()) {
                        m["backupHls"] = res.backupHlsUrl
                        m["backupProvider"] = res.backupProvider
                    }
                    m
                } else null
            }
            "titan", "solaris" -> {
                val res = try { MultiLanguageAudioExtractor.extractRegionalAudio(anilistId, episodeNum, "italian", animeTitle, serverPreference = pref) } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    val subInfo = fetchEnglishSubSource(anilistId, episodeNum)
                    val m = mutableMapOf<String, Any>(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to subInfo.subtitleUrl.ifEmpty { res.subtitleUrl },
                        "subtitlesJson" to if (subInfo.subtitles.isNotEmpty()) JSONArray().apply { subInfo.subtitles.forEach { put(JSONObject(it)) } }.toString() else "[]",
                        "isHardSub" to false,
                        "provider" to res.provider,
                        "introStart" to subInfo.introStart,
                        "introEnd" to subInfo.introEnd,
                        "outroStart" to subInfo.outroStart,
                        "outroEnd" to subInfo.outroEnd
                    )
                    if (res.backupHlsUrl.isNotEmpty()) {
                        m["backupHls"] = res.backupHlsUrl
                        m["backupProvider"] = res.backupProvider
                    }
                    m
                } else null
            }
            "animeav1" -> {
                val res = try {
                    if (streamType.equals("sub", ignoreCase = true)) {
                        AnimeAV1Extractor.extractSub(anilistId, episodeNum, animeTitle)
                    } else {
                        AnimeAV1Extractor.extractLatino(anilistId, episodeNum, animeTitle)
                    }
                } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    val subInfo = fetchEnglishSubSource(anilistId, episodeNum)
                    val m = mutableMapOf<String, Any>(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to subInfo.subtitleUrl.ifEmpty { res.subtitleUrl },
                        "subtitlesJson" to if (subInfo.subtitles.isNotEmpty()) JSONArray().apply { subInfo.subtitles.forEach { put(JSONObject(it)) } }.toString() else "[]",
                        "isHardSub" to false,
                        "provider" to res.provider,
                        "introStart" to subInfo.introStart,
                        "introEnd" to subInfo.introEnd,
                        "outroStart" to subInfo.outroStart,
                        "outroEnd" to subInfo.outroEnd
                    )
                    if (res.backupHlsUrl.isNotEmpty()) {
                        m["backupHls"] = res.backupHlsUrl
                        m["backupProvider"] = res.backupProvider
                    }
                    m
                } else null
            }
            "zenith", "neptune" -> {
                val res = try { MultiLanguageAudioExtractor.extractRegionalAudio(anilistId, episodeNum, "spanish_latino", animeTitle, serverPreference = "neptune") } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    val subInfo = fetchEnglishSubSource(anilistId, episodeNum)
                    val m = mutableMapOf<String, Any>(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to subInfo.subtitleUrl.ifEmpty { res.subtitleUrl },
                        "subtitlesJson" to if (subInfo.subtitles.isNotEmpty()) JSONArray().apply { subInfo.subtitles.forEach { put(JSONObject(it)) } }.toString() else "[]",
                        "isHardSub" to false,
                        "provider" to res.provider,
                        "introStart" to subInfo.introStart,
                        "introEnd" to subInfo.introEnd,
                        "outroStart" to subInfo.outroStart,
                        "outroEnd" to subInfo.outroEnd
                    )
                    if (res.backupHlsUrl.isNotEmpty()) {
                        m["backupHls"] = res.backupHlsUrl
                        m["backupProvider"] = res.backupProvider
                    }
                    m
                } else null
            }
            "shinobi", "jupiter" -> {
                val res = try { MultiLanguageAudioExtractor.extractRegionalAudio(anilistId, episodeNum, "spanish_latino", animeTitle, serverPreference = "jupiter") } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    val subInfo = fetchEnglishSubSource(anilistId, episodeNum)
                    val m = mutableMapOf<String, Any>(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to subInfo.subtitleUrl.ifEmpty { res.subtitleUrl },
                        "subtitlesJson" to if (subInfo.subtitles.isNotEmpty()) JSONArray().apply { subInfo.subtitles.forEach { put(JSONObject(it)) } }.toString() else "[]",
                        "isHardSub" to false,
                        "provider" to res.provider,
                        "introStart" to subInfo.introStart,
                        "introEnd" to subInfo.introEnd,
                        "outroStart" to subInfo.outroStart,
                        "outroEnd" to subInfo.outroEnd
                    )
                    if (res.backupHlsUrl.isNotEmpty()) {
                        m["backupHls"] = res.backupHlsUrl
                        m["backupProvider"] = res.backupProvider
                    }
                    m
                } else null
            }
            "mercury", "sololatino" -> {
                val res = try { MultiLanguageAudioExtractor.extractRegionalAudio(anilistId, episodeNum, "spanish_latino", animeTitle, serverPreference = "mercury") } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    val subInfo = fetchEnglishSubSource(anilistId, episodeNum)
                    val m = mutableMapOf<String, Any>(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to subInfo.subtitleUrl.ifEmpty { res.subtitleUrl },
                        "subtitlesJson" to if (subInfo.subtitles.isNotEmpty()) JSONArray().apply { subInfo.subtitles.forEach { put(JSONObject(it)) } }.toString() else "[]",
                        "isHardSub" to false,
                        "provider" to res.provider,
                        "introStart" to subInfo.introStart,
                        "introEnd" to subInfo.introEnd,
                        "outroStart" to subInfo.outroStart,
                        "outroEnd" to subInfo.outroEnd
                    )
                    if (res.backupHlsUrl.isNotEmpty()) {
                        m["backupHls"] = res.backupHlsUrl
                        m["backupProvider"] = res.backupProvider
                    }
                    m
                } else null
            }
            "eclipse", "kagura" -> {
                val res = try { MultiLanguageAudioExtractor.extractRegionalAudio(anilistId, episodeNum, "spanish_castellano", animeTitle) } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    val subInfo = fetchEnglishSubSource(anilistId, episodeNum)
                    val m = mutableMapOf<String, Any>(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to subInfo.subtitleUrl.ifEmpty { res.subtitleUrl },
                        "subtitlesJson" to if (subInfo.subtitles.isNotEmpty()) JSONArray().apply { subInfo.subtitles.forEach { put(JSONObject(it)) } }.toString() else "[]",
                        "isHardSub" to false,
                        "provider" to res.provider,
                        "introStart" to subInfo.introStart,
                        "introEnd" to subInfo.introEnd,
                        "outroStart" to subInfo.outroStart,
                        "outroEnd" to subInfo.outroEnd
                    )
                    if (res.backupHlsUrl.isNotEmpty()) {
                        m["backupHls"] = res.backupHlsUrl
                        m["backupProvider"] = res.backupProvider
                    }
                    m
                } else null
            }
            "nexus", "bifrost" -> {
                val res = try { MultiLanguageAudioExtractor.extractRegionalAudio(anilistId, episodeNum, "german", animeTitle) } catch (_: Exception) { null }
                if (res != null && res.hlsUrl.isNotEmpty()) {
                    val subInfo = fetchEnglishSubSource(anilistId, episodeNum)
                    val m = mutableMapOf<String, Any>(
                        "hls" to res.hlsUrl,
                        "referer" to res.referer,
                        "subtitle" to subInfo.subtitleUrl.ifEmpty { res.subtitleUrl },
                        "subtitlesJson" to if (subInfo.subtitles.isNotEmpty()) JSONArray().apply { subInfo.subtitles.forEach { put(JSONObject(it)) } }.toString() else "[]",
                        "isHardSub" to false,
                        "provider" to res.provider,
                        "introStart" to subInfo.introStart,
                        "introEnd" to subInfo.introEnd,
                        "outroStart" to subInfo.outroStart,
                        "outroEnd" to subInfo.outroEnd
                    )
                    if (res.backupHlsUrl.isNotEmpty()) {
                        m["backupHls"] = res.backupHlsUrl
                        m["backupProvider"] = res.backupProvider
                    }
                    m
                } else null
            }

            else -> null
        }
    }
}
