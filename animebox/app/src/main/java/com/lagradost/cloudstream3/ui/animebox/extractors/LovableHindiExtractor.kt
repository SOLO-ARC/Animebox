package com.lagradost.cloudstream3.ui.animebox.extractors
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class HindiQualityStream(
    val label: String,
    val code: String,
    val url: String,
    val referer: String = "https://blakiteapi.xyz"
)

object LovableHindiExtractor {
    private val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private const val FRIBB_URL = "https://raw.githubusercontent.com/Fribb/anime-lists/master/anime-list-full.json"
    private const val ANIZIP_URL = "https://api.ani.zip/mappings"
    private const val BLAKITE_BASE = "https://blakiteapi.xyz"
    private const val CDN_BASE = "https://hugh.cdn.rumble.cloud/video/"

    private val QUALITY_CODES = listOf("oaa", "baa", "caa", "gaa", "haa")
    private val QUALITY_LABELS = listOf("240p", "360p", "480p", "720p", "1080p")

    private var fribbCache: JSONArray? = null
    private var fribbCacheTime: Long = 0L

    private suspend fun getFribbList(): JSONArray? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (fribbCache != null && (now - fribbCacheTime < 6 * 3600 * 1000L)) {
            return@withContext fribbCache
        }
        try {
            val req = Request.Builder()
                .url(FRIBB_URL)
                .addHeader("User-Agent", "FireFly-Hindi-Client/1.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val jsonStr = resp.body?.string() ?: ""
                    if (jsonStr.isNotEmpty()) {
                        val arr = JSONArray(jsonStr)
                        fribbCache = arr
                        fribbCacheTime = now
                        return@withContext arr
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext fribbCache
    }

    private suspend fun getAniZip(anilistId: Int): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("$ANIZIP_URL?anilist_id=$anilistId")
                .addHeader("User-Agent", "FireFly-Hindi-Client/1.0")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val jsonStr = resp.body?.string() ?: ""
                    if (jsonStr.isNotEmpty()) return@withContext JSONObject(jsonStr)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private fun normalizeTmdbId(v: Any?, isMovie: Boolean): String? {
        if (v == null || v == JSONObject.NULL) return null
        if (v is JSONObject) {
            val first = if (isMovie) {
                if (v.has("movie") && !v.isNull("movie")) v.get("movie") else v.opt("tv")
            } else {
                if (v.has("tv") && !v.isNull("tv")) v.get("tv") else v.opt("movie")
            }
            if (first == null || first == JSONObject.NULL) return null
            val s = first.toString().trim()
            return if (s.isNotEmpty() && s != "0") s else null
        }
        val s = v.toString().split(",")[0].trim()
        return if (s.isNotEmpty() && s != "0") s else null
    }

    suspend fun extractHindiStreams(anilistId: Int, episode: Int): List<HindiQualityStream> = withContext(Dispatchers.IO) {
        val qualityStreams = mutableListOf<HindiQualityStream>()
        try {
            val aniZip = getAniZip(anilistId)
            val mappings = aniZip?.optJSONObject("mappings")
            val malId = mappings?.optInt("mal_id", -1)
            val imdbId = mappings?.optString("imdb_id", "")
            val aniZipType = mappings?.optString("type", "")?.lowercase() ?: ""
            var isMovie = aniZipType == "movie"

            var tmdbId: String? = null
            var fribbSeason = 1

            if (mappings != null && mappings.has("themoviedb_id")) {
                tmdbId = normalizeTmdbId(mappings.get("themoviedb_id"), isMovie)
            }

            if (tmdbId == null) {
                val fribbList = getFribbList()
                if (fribbList != null) {
                    for (i in 0 until fribbList.length()) {
                        val entry = fribbList.optJSONObject(i) ?: continue
                        val entryMalId = entry.optInt("mal_id", -2)
                        val entryImdbId = entry.optString("imdb_id", "")

                        val matchMal = malId != null && malId > 0 && entryMalId == malId
                        val matchImdb = imdbId != null && imdbId.isNotEmpty() && entryImdbId == imdbId

                        if (matchMal || matchImdb) {
                            if (entry.optString("type", "").lowercase() == "movie") {
                                isMovie = true
                            }
                            val tmdbVal = entry.opt("themoviedb_id")
                            val norm = normalizeTmdbId(tmdbVal, isMovie)
                            if (norm != null) {
                                tmdbId = norm
                                val sObj = entry.optJSONObject("season")
                                if (sObj != null && sObj.has("tmdb")) {
                                    fribbSeason = sObj.optInt("tmdb", 1)
                                }
                                break
                            }
                        }
                    }
                }
            }

            if (tmdbId == null) return@withContext emptyList()

            var season = fribbSeason
            if (!isMovie) {
                val episodesObj = aniZip?.optJSONObject("episodes")
                if (episodesObj != null) {
                    val epEntry = episodesObj.optJSONObject(episode.toString())
                    if (epEntry != null && epEntry.has("seasonNumber")) {
                        val sn = epEntry.optInt("seasonNumber", 1)
                        if (sn > 0) season = sn
                    }
                }
            }

            val cleanTmdb = tmdbId.trimStart('0')
            val epForBlakite = if (isMovie) 1 else episode

            suspend fun queryBlakite(tmdbCandidate: String): List<HindiQualityStream> {
                val streams = mutableListOf<HindiQualityStream>()
                val url = if (isMovie) {
                    "$BLAKITE_BASE/api/get.php?tmdbId=${java.net.URLEncoder.encode(tmdbCandidate, "UTF-8")}"
                } else {
                    "$BLAKITE_BASE/api/get.php?id=$season-$epForBlakite&tmdbId=${java.net.URLEncoder.encode(tmdbCandidate, "UTF-8")}"
                }

                val req = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
                    .addHeader("Referer", "$BLAKITE_BASE/embed/$tmdbCandidate/${if (isMovie) "" else "$season-$epForBlakite"}")
                    .addHeader("Origin", BLAKITE_BASE)
                    .addHeader("Accept", "application/json, text/plain, */*")
                    .addHeader("X-Requested-With", "XMLHttpRequest")
                    .build()

                try {
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return emptyList()
                        val jsonStr = resp.body?.string() ?: ""
                        val jsonObj = JSONObject(jsonStr)
                        if (!jsonObj.optBoolean("success", false) || !jsonObj.has("data")) {
                            return emptyList()
                        }

                        val data = jsonObj.getJSONObject("data")
                        val dataId = data.optString("dataId", "")
                        val format = data.optString("format", "")
                        val ranges = data.optString("ranges", "")
                        val qid = data.optInt("qid", QUALITY_LABELS.size)

                        if (dataId.isEmpty()) return emptyList()

                        if (format == "M3U8" && ranges.isNotEmpty()) {
                            val rangeMap = mutableMapOf<String, String>()
                            ranges.split("\n").forEach { line ->
                                val trimmed = line.trim()
                                val match = Regex("""^(\d+-\d+)\s*\(([^)]+)\)""").find(trimmed)
                                if (match != null) {
                                    val rangeVal = match.groupValues[1]
                                    val labelVal = match.groupValues[2].trim()
                                    rangeMap[labelVal] = rangeVal
                                }
                            }

                            QUALITY_LABELS.forEachIndexed { i, label ->
                                if (rangeMap.containsKey(label)) {
                                    val range = rangeMap[label]
                                    val streamUrl = "$CDN_BASE$dataId.${QUALITY_CODES[i]}.tar?r_file=chunklist.m3u8&r_type=application%2Fvnd.apple.mpegurl&r_range=$range"
                                    streams.add(HindiQualityStream(label, QUALITY_CODES[i], streamUrl, BLAKITE_BASE))
                                }
                            }
                        }

                        if (streams.isEmpty()) {
                            val count = minOf(qid, QUALITY_LABELS.size)
                            for (i in 0 until count) {
                                val streamUrl = "$CDN_BASE$dataId.${QUALITY_CODES[i]}.mp4"
                                streams.add(HindiQualityStream(QUALITY_LABELS[i], QUALITY_CODES[i], streamUrl, BLAKITE_BASE))
                            }
                        }
                    }
                } catch (_: Exception) {}
                return streams
            }

            // 1. Try normal TMDB ID first
            var results = queryBlakite(cleanTmdb)
            // 2. If nothing found or failed, try with leading zero (fandub)
            if (results.isEmpty()) {
                results = queryBlakite("0$cleanTmdb")
            }
            return@withContext results
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext emptyList()
    }
}
