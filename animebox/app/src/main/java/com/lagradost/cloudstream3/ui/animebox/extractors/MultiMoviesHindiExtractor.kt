package com.lagradost.cloudstream3.ui.animebox.extractors
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object MultiMoviesHindiExtractor {
    private val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private const val HINDI_API_BASE = "https://multimovieapi-main.vercel.app/api/hindi"

    suspend fun extractHindiStreamInfo(
        anilistId: Int,
        episode: Int,
        animeTitle: String? = null
    ): Map<String, Any>? = withContext(Dispatchers.IO) {
        val hindiStreamsArr = JSONArray()
        var primaryHls = ""
        var primaryReferer = ""
        var primaryProvider = ""
        var primarySubtitle = ""
        var primarySubtitlesJson = "[]"
        var firstBackupHls = ""
        var firstBackupProvider = ""

        // Step 0: Exclusively for Pokemon seasons & movies with Hindi audio (Blissey's Husband + Videas)
        if (PokemonHindiExtractor.isPokemon(anilistId, animeTitle)) {
            try {
                val pokemonRes = PokemonHindiExtractor.extractPokemonStream(anilistId, episode, animeTitle)
                if (pokemonRes != null && pokemonRes.hlsUrl.isNotEmpty()) {
                    primaryHls = pokemonRes.hlsUrl
                    primaryReferer = pokemonRes.referer
                    primaryProvider = pokemonRes.provider
                    primarySubtitle = pokemonRes.subtitleUrl
                    hindiStreamsArr.put(JSONObject().apply {
                        put("hls", pokemonRes.hlsUrl)
                        put("referer", pokemonRes.referer)
                        put("provider", pokemonRes.provider)
                    })
                    return@withContext mapOf(
                        "hls" to primaryHls,
                        "referer" to primaryReferer,
                        "subtitle" to primarySubtitle,
                        "subtitlesJson" to primarySubtitlesJson,
                        "isHardSub" to false,
                        "provider" to primaryProvider,
                        "backupHls" to "",
                        "backupProvider" to "",
                        "hindiStreamsJson" to hindiStreamsArr.toString()
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Run all normal Hindi extractors in parallel for fast loading!
        coroutineScope {
            val dekhoDeferred = async {
                withTimeoutOrNull(7500L) {
                    try {
                        AnimeDekhoHindiExtractor.extractAllDekhoStreams(anilistId, episode, animeTitle)
                    } catch (_: Exception) { emptyList() }
                } ?: emptyList()
            }

            val blakiteDeferred = async {
                withTimeoutOrNull(6500L) {
                    try {
                        LovableHindiExtractor.extractHindiStreams(anilistId, episode)
                    } catch (_: Exception) { emptyList() }
                } ?: emptyList()
            }

            val rareDeferred = async {
                withTimeoutOrNull(8500L) {
                    try {
                        val r = RareAnimeHindiExtractor.extract(anilistId, episode, animeTitle)
                        if (r != null && r.hlsUrl.isNotEmpty()) {
                            r
                        } else {
                            val t = ToonoHindiExtractor.extract(anilistId, episode, animeTitle ?: "")
                            if (t != null && t.hlsUrl.isNotEmpty()) {
                                RareAnimeResult(
                                    hlsUrl = t.hlsUrl,
                                    referer = t.referer,
                                    provider = t.provider,
                                    title = t.title
                                )
                            } else null
                        }
                    } catch (_: Exception) { null }
                }
            }

            val multiApiDeferred = async {
                withTimeoutOrNull(5500L) {
                    try {
                        val url = "$HINDI_API_BASE?anilistId=$anilistId&episode=$episode"
                        val req = Request.Builder()
                            .url(url)
                            .addHeader("User-Agent", "FireFly-App/1.0")
                            .build()
                        client.newCall(req).execute().use { resp ->
                            if (resp.isSuccessful) resp.body?.string() ?: "" else ""
                        }
                    } catch (_: Exception) { "" }
                } ?: ""
            }

            // Step 1: Collect AnimeDekho Hindi streams
            val dekhoList = dekhoDeferred.await()
            for (dekhoRes in dekhoList) {
                if (dekhoRes.hlsUrl.isNotEmpty()) {
                    if (primaryHls.isEmpty()) {
                        primaryHls = dekhoRes.hlsUrl
                        primaryReferer = dekhoRes.referer
                        primaryProvider = dekhoRes.provider
                        primarySubtitle = dekhoRes.subtitleUrl
                        if (dekhoRes.subtitles.isNotEmpty()) {
                            val subsJson = JSONArray()
                            dekhoRes.subtitles.forEach { subMap ->
                                subsJson.put(JSONObject(subMap))
                            }
                            primarySubtitlesJson = subsJson.toString()
                        }
                    } else if (firstBackupHls.isEmpty() && dekhoRes.hlsUrl != primaryHls) {
                        firstBackupHls = dekhoRes.hlsUrl
                        firstBackupProvider = dekhoRes.provider
                    }

                    hindiStreamsArr.put(JSONObject().apply {
                        put("hls", dekhoRes.hlsUrl)
                        put("referer", dekhoRes.referer)
                        put("provider", dekhoRes.provider)
                    })
                }
            }

            // Step 1.2: Collect Blakite Hindi streams (Blakite / Rumble CDN HD)
            val blakiteStreams = blakiteDeferred.await()
            if (blakiteStreams.isNotEmpty()) {
                val best = blakiteStreams.last()
                if (primaryHls.isEmpty()) {
                    primaryHls = best.url
                    primaryReferer = best.referer
                    primaryProvider = "Blakite"
                } else if (firstBackupHls.isEmpty() && best.url != primaryHls) {
                    firstBackupHls = best.url
                    firstBackupProvider = "Blakite"
                }

                blakiteStreams.reversed().forEach { s ->
                    hindiStreamsArr.put(JSONObject().apply {
                        put("hls", s.url)
                        put("referer", s.referer)
                        put("provider", "Blakite (${s.label})")
                    })
                }
            }

            // Step 1.4: Collect RareAnime Hindi stream (Anilili RSS + codedew zipper + Argon JuicyCodes)
            val rareRes = rareDeferred.await()
            if (rareRes != null && rareRes.hlsUrl.isNotEmpty()) {
                if (primaryHls.isEmpty()) {
                    primaryHls = rareRes.hlsUrl
                    primaryReferer = rareRes.referer
                    primaryProvider = rareRes.provider
                } else if (firstBackupHls.isEmpty() && rareRes.hlsUrl != primaryHls) {
                    firstBackupHls = rareRes.hlsUrl
                    firstBackupProvider = rareRes.provider
                }
                hindiStreamsArr.put(JSONObject().apply {
                    put("hls", rareRes.hlsUrl)
                    put("referer", rareRes.referer)
                    put("provider", rareRes.provider)
                })
            }

            // Step 2: Collect multimoviesapi Hindi endpoint (StreamHG primary + UPNS/Lovable backups)
            val body = multiApiDeferred.await()
            if (body.isNotEmpty()) {
                try {
                    val json = JSONObject(body)
                    val primary = json.optJSONObject("primary")
                    val backups = json.optJSONArray("backups")
                    val apiPrimaryHls = primary?.optString("hls", "") ?: ""

                    if (primary != null && apiPrimaryHls.isNotEmpty()) {
                        val headers = primary.optJSONObject("headers")
                        val referer = when {
                            headers == null -> "https://hanerix.com/"
                            headers.has("Referer") -> headers.optString("Referer", "https://hanerix.com/")
                            headers.has("referer") -> headers.optString("referer", "https://hanerix.com/")
                            else -> "https://hanerix.com/"
                        }
                        val provider = primary.optString("provider", "streamhg")

                        if (primaryHls.isEmpty()) {
                            primaryHls = apiPrimaryHls
                            primaryReferer = referer
                            primaryProvider = provider
                        } else if (firstBackupHls.isEmpty() && apiPrimaryHls != primaryHls) {
                            firstBackupHls = apiPrimaryHls
                            firstBackupProvider = provider
                        }

                        hindiStreamsArr.put(JSONObject().apply {
                            put("hls", apiPrimaryHls)
                            put("referer", referer)
                            put("provider", provider)
                        })
                    }

                    if (backups != null) {
                        for (i in 0 until backups.length()) {
                            val b = backups.optJSONObject(i) ?: continue
                            val bHls = if (b.has("hls")) b.optString("hls", "") else b.optString("url", "")
                            val headers = b.optJSONObject("headers")
                            val referer = when {
                                headers == null -> ""
                                headers.has("Referer") -> headers.optString("Referer", "")
                                headers.has("referer") -> headers.optString("referer", "")
                                else -> ""
                            }
                            val provider = b.optString("provider", "backup")

                            if (bHls.isNotEmpty()) {
                                if (primaryHls.isEmpty()) {
                                    primaryHls = bHls
                                    primaryReferer = referer
                                    primaryProvider = provider
                                } else if (firstBackupHls.isEmpty() && bHls != primaryHls) {
                                    firstBackupHls = bHls
                                    firstBackupProvider = provider
                                }
                                hindiStreamsArr.put(JSONObject().apply {
                                    put("hls", bHls)
                                    put("referer", referer)
                                    put("provider", provider)
                                })
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        if (primaryHls.isNotEmpty() || hindiStreamsArr.length() > 0) {
            return@withContext mapOf(
                "hls" to primaryHls,
                "referer" to primaryReferer,
                "subtitle" to primarySubtitle,
                "subtitlesJson" to primarySubtitlesJson,
                "isHardSub" to false,
                "provider" to primaryProvider,
                "backupHls" to firstBackupHls,
                "backupProvider" to firstBackupProvider,
                "hindiStreamsJson" to hindiStreamsArr.toString()
            )
        }

        return@withContext null
    }
}
