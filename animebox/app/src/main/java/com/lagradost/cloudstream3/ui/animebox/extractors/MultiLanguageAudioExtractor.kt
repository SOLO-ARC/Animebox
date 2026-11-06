package com.lagradost.cloudstream3.ui.animebox.extractors
import com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxDnsHelper

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import com.lagradost.cloudstream3.app
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class MultiLanguageStreamResult(
    val hlsUrl: String,
    val referer: String,
    val provider: String,
    val language: String,
    val dubName: String,
    val subtitleUrl: String = "",
    val subtitles: List<Map<String, String>> = emptyList(),
    val backupHlsUrl: String = "",
    val backupProvider: String = ""
)

data class FrenchEmbedResult(
    val streamUrl: String,
    val provider: String,
    val detectedEp: Int? = null,
    val referer: String = "https://animes-sama.fr/"
)

object MultiLanguageAudioExtractor {

    private val client = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val noRedirectClient = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    // ── Known AniList Mappings (from Multi-Language Anime API) ──
    val KNOWN_VF_MAPPINGS = mapOf(
        116589 to "86-eighty-six",
        131586 to "86-eighty-six",
        1689 to "5-cm-per-second",
        101347 to "jujutsu-kaisen",
        145064 to "jujutsu-kaisen",
        127230 to "chainsaw-man",
        151807 to "solo-leveling",
        154587 to "frieren",
        // Demon Slayer
        101922 to "demon-slayer",
        142329 to "demon-slayer",
        145139 to "demon-slayer",
        166240 to "demon-slayer",
        // Attack on Titan
        16498 to "shingeki-no-kyojin",
        20958 to "shingeki-no-kyojin",
        99147 to "shingeki-no-kyojin",
        104578 to "shingeki-no-kyojin",
        110277 to "shingeki-no-kyojin",
        131681 to "shingeki-no-kyojin",
        163142 to "shingeki-no-kyojin",
        // Dr. STONE
        105333 to "dr-stone",
        113936 to "dr-stone",
        144844 to "dr-stone",
        131518 to "dr-stone",
        162670 to "dr-stone",
        171638 to "dr-stone",
        21459 to "my-hero-academia",
        20 to "naruto",
        1735 to "naruto-shippuden",
        21 to "one-piece",
        269 to "bleach",
        1535 to "death-note",
        20605 to "tokyo-ghoul",
        140960 to "spy-x-family",
        129201 to "summer-time-rendering",
        171018 to "dandadan",
        153288 to "kaiju-no-8",
        163270 to "wind-breaker",
        137822 to "blue-lock",
        189046 to "re-zero",
        163134 to "re-zero",
        108632 to "re-zero",
        119661 to "re-zero",
        21355 to "re-zero",
        100049 to "re-zero",
        104270 to "re-zero",
        108465 to "mushoku-tensei",
        127720 to "mushoku-tensei",
        146065 to "mushoku-tensei",
        166873 to "mushoku-tensei",
        132405 to "sono-bisque-doll-wa-koi-wo-suru",
        154768 to "sono-bisque-doll-wa-koi-wo-suru"
    )

    val KNOWN_VF_SEASONS = mapOf(
        // Demon Slayer
        101922 to "saison1",
        142329 to "saison2",
        145139 to "saison3",
        166240 to "saison4",
        // Attack on Titan
        16498 to "saison1",
        20958 to "saison2",
        99147 to "saison3",
        104578 to "saison3",
        110277 to "saison4",
        131681 to "saison4",
        163142 to "saison4",
        // Dr. STONE
        105333 to "saison1",
        113936 to "saison2",
        144844 to "oav",
        131518 to "saison3",
        162670 to "saison3",
        171638 to "saison4"
    )

    val KNOWN_VF_OFFSETS = mapOf(
        104578 to 12, // AoT S3 P2 is eps 13-22 in saison3
        131681 to 16, // AoT Final Season P2 is eps 17-28 in saison4
        163142 to 28, // AoT Final Chapters is eps 29-30 in saison4
        162670 to 11  // Dr. STONE New World P2 is eps 12-22 in saison3
    )

    val KNOWN_ITA_MAPPINGS = mapOf(
        189046 to "rezero-kara-hajimeru-isekai-seikatsu-4-ita",
        163134 to "rezero-kara-hajimeru-isekai-seikatsu-3-ita",
        108632 to "rezero-kara-hajimeru-isekai-seikatsu-2-ita",
        21355 to "rezero-kara-hajimeru-isekai-seikatsu-shin-henshuu-ban-ita",
        132405 to "my-dress-up-darling-ita",
        154768 to "my-dress-up-darling-2-ita",
        1735 to "naruto-shippuden-ita",
        108465 to "mushoku-tensei-isekai-ittara-honki-dasu-ita",
        127720 to "mushoku-tensei-jobless-reincarnation-part-2-ita",
        146065 to "mushoku-tensei-ii-isekai-ittara-honki-dasu-part-2-ita",
        166873 to "mushoku-tensei-jobless-reincarnation-3-ita",
        178789 to "mushoku-tensei-jobless-reincarnation-3-ita",
        20 to "naruto-ita",
        // Attack on Titan
        16498 to "lattacco-dei-giganti-ita",
        20958 to "lattacco-dei-giganti-2-ita",
        99147 to "lattacco-dei-giganti-3-ita",
        104578 to "lattacco-dei-giganti-3-parte-2-ita",
        110277 to "lattacco-dei-giganti-4-ita",
        131681 to "lattacco-dei-giganti-4-parte-2-ita",
        163142 to "lattacco-dei-giganti-4-parte-3-ita",
        // Demon Slayer
        101922 to "demon-slayer-kimetsu-no-yaiba-ita",
        129874 to "demon-slayer-kimetsu-no-yaiba-mugen-train-arc-tv-ita",
        112151 to "demon-slayer-movie-mugen-train-ita",
        142329 to "demon-slayer-kimetsu-no-yaiba-entertainment-district-arc-ita",
        145139 to "demon-slayer-kimetsu-no-yaiba-swordsmith-village-arc-ita",
        166240 to "demon-slayer-kimetsu-no-yaiba-hashira-training-arc-ita",
        // Dr. STONE
        105333 to "dr-stone-ita",
        113936 to "dr-stone-2-ita",
        144844 to "dr-stone-ryuusui-ita",
        131518 to "dr-stone-4-ita",
        162670 to "dr-stone-4-part-2-ita",
        171638 to "dr-stone-4-part-3-ita",
        101347 to "jujutsu-kaisen-ita",
        145064 to "jujutsu-kaisen-2-ita",
        127230 to "chainsaw-man-ita",
        151807 to "solo-leveling-ita",
        154587 to "frieren-oltre-la-fine-del-viaggio-ita",
        21 to "one-piece-ita",
        269 to "bleach-ita",
        1535 to "death-note-ita",
        20605 to "tokyo-ghoul-ita",
        140960 to "spy-x-family-ita",
        129201 to "summer-time-rendering-ita",
        116589 to "86-eighty-six-ita",
        131586 to "86-eighty-six-part-2-ita",
        171018 to "dandadan-ita",
        153288 to "kaiju-no-8-ita",
        163270 to "wind-breaker-ita",
        137822 to "blue-lock-ita"
    )

    val KNOWN_LATINO_MAPPINGS = mapOf(
        189046 to "rezero-kara-hajimeru-isekai-seikatsu-s4-latino",
        129201 to "summertime-render-latino",
        163134 to "rezero-kara-hajimeru-isekai-seikatsu-s3-latino",
        108632 to "rezero-kara-hajimeru-isekai-seikatsu-2nd-season-latino",
        21355 to "re-zero-s1-directors-cut-latino",
        108465 to "mushoku-tensei-isekai-ittara-honki-dasu-latino",
        127720 to "mushoku-tensei-isekai-ittara-honki-dasu-latino",
        146065 to "mushoku-tensei-ii-isekai-ittara-honki-dasu-s2-latino",
        166873 to "mushoku-tensei-ii-isekai-ittara-honki-dasu-s2-latino",
        20 to "naruto-latino",
        1735 to "naruto-shippuden-latino",
        // Attack on Titan
        16498 to "shingeki-no-kyojin-latino",
        20958 to "shingeki-no-kyojin-s2-latino",
        99147 to "shingeki-no-kyojin-s3-latino",
        104578 to "shingeki-no-kyojin-s3-latino",
        110277 to "shingeki-no-kyojin-the-final-season-latino",
        131681 to "shingeki-no-kyojin-the-final-season-latino",
        163142 to "shingeki-no-kyojin-the-final-season-latino",
        // Demon Slayer
        101922 to "kimetsu-no-yaiba-latino",
        129874 to "kimetsu-no-yaiba-mugen-ressha-hen-tv-latino",
        112151 to "kimetsu-no-yaiba-movie-mugen-ressha-hen-latino",
        142329 to "kimetsu-no-yaiba-yuukaku-hen-latino",
        145139 to "kimetsu-no-yaiba-katanakaji-no-sato-hen-latino",
        166240 to "kimetsu-no-yaiba-hashira-geiko-hen-latino",
        // Dr. STONE
        105333 to "dr-stone-s1-latino",
        113936 to "dr-stone-stone-wars-latino-s2",
        144844 to "dr-stone-ryuusui-latino-s3",
        131518 to "dr-stone-new-world-s3-latino",
        162670 to "dr-stone-new-world-s3-latino",
        171638 to "dr-stone-s4-latino",
        // Inuyasha
        249 to "inuyasha-remaster-2023-latino",
        // Other popular shonen
        21 to "one-piece-latino",
        1535 to "death-note-latino",
        20605 to "tokyo-ghoul-latino",
        5114 to "fullmetal-alchemist-brotherhood-latino",
        101347 to "jujutsu-kaisen-latino",
        145064 to "jujutsu-kaisen-s2-latino",
        127230 to "chainsaw-man-latino",
        151807 to "solo-leveling-latino",
        173548 to "solo-leveling-s2-latino",
        154587 to "frieren-mas-alla-del-final-del-viaje-latino",
        153288 to "kaijuu-8-gou-latino",
        171018 to "dandadan-latino",
        137822 to "blue-lock-latino",
        163270 to "wind-breaker-latino",
        116589 to "86-eighty-six-latino",
        131586 to "86-eighty-six-latino",
        140960 to "spy-x-family-latino",
        // My Hero Academia
        21459 to "boku-no-hero-academia-s1-latino",
        21856 to "boku-no-hero-academia-s2-latino",
        100166 to "boku-no-hero-academia-s3-latino",
        104276 to "boku-no-hero-academia-s4-latino",
        117193 to "boku-no-hero-academia-s5-latino",
        139630 to "boku-no-hero-academia-s6-latino",
        163139 to "boku-no-hero-academia-s7-latino",
        182896 to "my-hero-academia-temporada-8",
        132405 to "sono-bisque-doll-wa-koi-wo-suru-latino",
        154768 to "my-dress-up-darling-2-latino"
    )

    val KNOWN_LATINO_OFFSETS = mapOf(
        127720 to 11, // Mushoku Tensei S1 P2 is eps 12-23
        166873 to 12, // Mushoku Tensei S2 P2 is eps 13-24
        104578 to 12, // AoT S3 P2 is eps 13-22 in shingeki-no-kyojin-s3-latino
        131681 to 16, // AoT Final Season P2 is eps 17-28 in shingeki-no-kyojin-the-final-season-latino
        163142 to 28, // AoT Final Chapters is eps 29-30 in shingeki-no-kyojin-the-final-season-latino
        162670 to 11  // Dr. STONE New World P2 is eps 12-22 in dr-stone-new-world-s3-latino
    )

    val KNOWN_CASTELLANO_MAPPINGS = mapOf(
        189046 to "rezero-kara-hajimeru-isekai-seikatsu-s4-castellano",
        129201 to "summertime-render-castellano",
        163134 to "rezero-kara-hajimeru-isekai-seikatsu-s3-castellano",
        108632 to "rezero-kara-hajimeru-isekai-seikatsu-s2-castellano",
        21355 to "re-zero-s1-castellano",
        108465 to "mushoku-tensei-isekai-ittara-honki-dasu-s1-castellano",
        127720 to "mushoku-tensei-isekai-ittara-honki-dasu-s1-castellano",
        146065 to "mushoku-tensei-ii-isekai-ittara-honki-dasu-s2-castellano",
        166873 to "mushoku-tensei-ii-isekai-ittara-honki-dasu-s2-castellano",
        20 to "naruto-castellano",
        1735 to "naruto-shippuden-castellano",
        // Attack on Titan
        16498 to "shingeki-no-kyojin-castellano",
        20958 to "shingeki-no-kyojin-2-castellano",
        99147 to "shingeki-no-kyojin-3-castellano",
        104578 to "shingeki-no-kyojin-3-parte-2-castellano",
        110277 to "shingeki-no-kyojin-the-final-season-castellano",
        131681 to "shingeki-no-kyojin-the-final-season-castellano",
        163142 to "shingeki-no-kyojin-the-final-season-castellano",
        // Demon Slayer
        101922 to "kimetsu-no-yaiba-castellano",
        129874 to "kimetsu-no-yaiba-mugen-ressha-hen-tv-castellano",
        112151 to "kimetsu-no-yaiba-movie-mugen-ressha-hen-castellano",
        142329 to "kimetsu-no-yaiba-yuukaku-hen-castellano",
        145139 to "kimetsu-no-yaiba-katanakaji-no-sato-hen-castellano",
        166240 to "kimetsu-no-yaiba-hashira-geiko-hen-castellano",
        // Dr. STONE
        105333 to "dr-stone-s1-castellano",
        113936 to "dr-stone-stone-wars-castellano-s2",
        144844 to "dr-stone-ryuusui-castellano",
        131518 to "dr-stone-new-world-s3-castellano",
        162670 to "dr-stone-new-world-s3-castellano",
        171638 to "dr-stone-s4-castellano",
        // Inuyasha
        249 to "inuyasha-castellano",
        // Other popular shonen
        127230 to "chainsaw-man-castellano",
        101347 to "jujutsu-kaisen-castellano",
        145064 to "jujutsu-kaisen-s2-castellano",
        151807 to "solo-leveling-castellano",
        173548 to "solo-leveling-s2-castellano",
        154587 to "frieren-mas-alla-del-final-del-viaje-castellano",
        153288 to "kaijuu-8-gou-castellano",
        1535 to "death-note-castellano",
        21 to "one-piece-castellano",
        20605 to "tokyo-ghoul-castellano",
        171018 to "dandadan-castellano",
        137822 to "blue-lock-castellano",
        163270 to "wind-breaker-castellano",
        132405 to "sono-bisque-doll-wa-koi-wo-suru-castellano",
        154768 to "my-dress-up-darling-temporada-2-castellano",
        // My Hero Academia
        21459 to "boku-no-hero-academia-s1-castellano",
        21856 to "boku-no-hero-academia-s2-castellano",
        100166 to "boku-no-hero-academia-s3-castellano",
        104276 to "boku-no-hero-academia-s4-castellano",
        117193 to "boku-no-hero-academia-s5-castellano",
        139630 to "boku-no-hero-academia-s6-castellano",
        163139 to "boku-no-hero-academia-s7-castellano",
        182896 to "boku-no-hero-academia-s8-especial-castellano"
    )

    val KNOWN_CASTELLANO_OFFSETS = mapOf(
        127720 to 11, // Mushoku Tensei S1 P2 is eps 12-23
        166873 to 12, // Mushoku Tensei S2 P2 is eps 13-24
        131681 to 16, // AoT Final Season P2 is eps 17-28 in shingeki-no-kyojin-the-final-season-castellano
        163142 to 28  // AoT Final Chapters is eps 29-30 in shingeki-no-kyojin-the-final-season-castellano
    )

    val KNOWN_GERMAN_MAPPINGS = mapOf(
        20 to Pair("naruto", 1),
        1735 to Pair("naruto-shippuden", 1),
        189046 to Pair("rezero-starting-life-in-another-world", 4),
        163134 to Pair("rezero-starting-life-in-another-world", 3),
        108632 to Pair("rezero-starting-life-in-another-world", 2),
        119661 to Pair("rezero-starting-life-in-another-world", 2),
        21355 to Pair("rezero-starting-life-in-another-world", 1),
        100049 to Pair("rezero-starting-life-in-another-world", 1),
        104270 to Pair("rezero-starting-life-in-another-world", 1),
        116589 to Pair("86-eighty-six", 1),
        131586 to Pair("86-eighty-six", 2),
        // Attack on Titan
        16498 to Pair("attack-on-titan", 1),
        20958 to Pair("attack-on-titan", 2),
        99147 to Pair("attack-on-titan", 3),
        104578 to Pair("attack-on-titan", 3),
        110277 to Pair("attack-on-titan", 4),
        131681 to Pair("attack-on-titan", 4),
        163142 to Pair("attack-on-titan", 4),
        // Demon Slayer
        101922 to Pair("demon-slayer-kimetsu-no-yaiba", 1),
        129874 to Pair("demon-slayer-kimetsu-no-yaiba", 2),
        142329 to Pair("demon-slayer-kimetsu-no-yaiba", 2),
        145139 to Pair("demon-slayer-kimetsu-no-yaiba", 3),
        166240 to Pair("demon-slayer-kimetsu-no-yaiba", 4),
        // Dr. STONE
        105333 to Pair("dr-stone", 1),
        113936 to Pair("dr-stone", 2),
        131518 to Pair("dr-stone", 3),
        162670 to Pair("dr-stone", 3),
        171638 to Pair("dr-stone", 4),
        // My Hero Academia
        21459 to Pair("my-hero-academia", 1),
        21856 to Pair("my-hero-academia", 2),
        100166 to Pair("my-hero-academia", 3),
        104276 to Pair("my-hero-academia", 4),
        117193 to Pair("my-hero-academia", 5),
        139630 to Pair("my-hero-academia", 6),
        163139 to Pair("my-hero-academia", 7),
        182896 to Pair("my-hero-academia", 8),
        127230 to Pair("chainsaw-man", 1),
        154587 to Pair("frieren-beyond-journeys-end", 1),
        129201 to Pair("summer-time-rendering", 1),
        108465 to Pair("mushoku-tensei-jobless-reincarnation", 1),
        127720 to Pair("mushoku-tensei-jobless-reincarnation", 1),
        146065 to Pair("mushoku-tensei-jobless-reincarnation", 2),
        166873 to Pair("mushoku-tensei-jobless-reincarnation", 2),
        21 to Pair("one-piece", 1),
        269 to Pair("bleach", 1),
        1535 to Pair("death-note", 1),
        20605 to Pair("tokyo-ghoul", 1),
        171018 to Pair("dan-da-dan", 1),
        153288 to Pair("kaiju-no-8", 1),
        163270 to Pair("wind-breaker", 1),
        132405 to Pair("my-dress-up-darling", 1),
        154768 to Pair("my-dress-up-darling", 2)
    )

    val KNOWN_GERMAN_OFFSETS = mapOf(
        142329 to 7,   // Demon Slayer Entertainment District is S2 E8-18 on FireAnime
        104578 to 12,  // AoT S3 P2 is S3 E13-22 on FireAnime
        131681 to 16,  // AoT Final Season P2 is S4 E17-28 on FireAnime
        163142 to 28,  // AoT Final Chapters is S4 E29-30 on FireAnime
        162670 to 11   // Dr. STONE New World P2 is S3 E12-22 on FireAnime
    )

    private val vfCache = ConcurrentHashMap<Int, String>()
    private val itaCache = ConcurrentHashMap<Int, String>()
    private val latinoCache = ConcurrentHashMap<Int, String>()
    private val castellanoCache = ConcurrentHashMap<Int, String>()
    private val germanCache = ConcurrentHashMap<Int, Pair<String, Int>>()

    fun seasonToRoman(season: Int): String = when (season) {
        2 -> "ii"
        3 -> "iii"
        4 -> "iv"
        5 -> "v"
        6 -> "vi"
        else -> ""
    }

    suspend fun resolveTitleVariants(anilistId: Int, animeTitle: String?): List<String> {
        val list = mutableListOf<String>()
        val seen = mutableSetOf<String>()

        fun addTitle(raw: String?) {
            if (raw.isNullOrBlank()) return
            val t = raw.trim()
            val key = t.lowercase()
            if (seen.add(key)) {
                list.add(t)
            }
        }

        addTitle(animeTitle)

        if (anilistId > 0) {
            try {
                val aniZip = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getAniZipJson(anilistId)
                if (aniZip != null) {
                    val tObj = aniZip.optJSONObject("titles")
                    if (tObj != null) {
                        addTitle(tObj.optString("en"))
                        addTitle(tObj.optString("x-jat"))
                        addTitle(tObj.optString("en_jp"))
                        addTitle(tObj.optString("ja"))
                    }
                }
            } catch (_: Exception) {}

            try {
                val rawDetails = com.lagradost.cloudstream3.ui.animebox.api.AniListClient.getAnimeDetails(anilistId)
                if (!rawDetails.isNullOrEmpty()) {
                    val mediaObj = org.json.JSONObject(rawDetails).optJSONObject("data")?.optJSONObject("Media")
                    val tObj = mediaObj?.optJSONObject("title")
                    if (tObj != null) {
                        addTitle(tObj.optString("english"))
                        addTitle(tObj.optString("romaji"))
                        addTitle(tObj.optString("userPreferred"))
                    }
                    val syns = mediaObj?.optJSONArray("synonyms")
                    if (syns != null) {
                        for (i in 0 until syns.length()) {
                            addTitle(syns.optString(i))
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return if (list.isNotEmpty()) list else if (!animeTitle.isNullOrBlank()) listOf(animeTitle.trim()) else emptyList()
    }

    fun scoreSeasonMatch(slug: String, title: String?, targetSeason: Int): Int {
        // Strip CMS deduplication suffixes like "-ueav2-2" or "-bvrpp-2" so random hashes aren't treated as season numbers
        val cleanSlug = slug.replace(Regex("""-[a-z0-9]{4,6}(-\d+)?$"""), "")
        val s = cleanSlug.lowercase()
        val t = (title ?: "").lowercase()
        val combined = "$s $t"

        val patterns = listOf(
            Regex("""[-_ ]s([0-9]+)[-_ ]"""),
            Regex("""[-_ ]s([0-9]+)$"""),
            Regex("""[-_ ]temporada[-_ ]?([0-9]+)"""),
            Regex("""[-_ ]season[-_ ]?([0-9]+)"""),
            Regex("""[-_ ]([0-9]+)(?:nd|rd|th|st)[-_ ]season"""),
            Regex("""[-_ ]saison[-_ ]?([0-9]+)"""),
            Regex("""[-_ ]([0-9]+)[-_ ](?:ita|latino|castellano|dub)"""),
            Regex("""[-_ ]([0-9]+)$""")
        )
        val detectedSeasons = mutableSetOf<Int>()
        for (pat in patterns) {
            pat.findAll(combined).forEach { m ->
                m.groupValues[1].toIntOrNull()?.let { detectedSeasons.add(it) }
            }
        }

        // Detect Roman numerals in slug or title
        if (Regex("""\b(?:season|saison|temporada|staffel|part|cour)?\s*[-_ ]?(vi)\b""").containsMatchIn(combined) || Regex("""[-_]vi[-_]""").containsMatchIn(combined)) detectedSeasons.add(6)
        if (Regex("""\b(?:season|saison|temporada|staffel|part|cour)?\s*[-_ ]?(v)\b""").containsMatchIn(combined) || Regex("""[-_]v[-_]""").containsMatchIn(combined)) detectedSeasons.add(5)
        if (Regex("""\b(?:season|saison|temporada|staffel|part|cour)?\s*[-_ ]?(iv)\b""").containsMatchIn(combined) || Regex("""[-_]iv[-_]""").containsMatchIn(combined)) detectedSeasons.add(4)
        if (Regex("""\b(?:season|saison|temporada|staffel|part|cour)?\s*[-_ ]?(iii)\b""").containsMatchIn(combined) || Regex("""[-_]iii[-_]""").containsMatchIn(combined)) detectedSeasons.add(3)
        if (Regex("""\b(?:season|saison|temporada|staffel|part|cour)?\s*[-_ ]?(ii)\b""").containsMatchIn(combined) || Regex("""[-_]ii[-_]""").containsMatchIn(combined)) detectedSeasons.add(2)

        return if (targetSeason == 1) {
            if (detectedSeasons.any { it > 1 }) -100
            else if (detectedSeasons.contains(1)) 100
            else 80
        } else {
            if (detectedSeasons.contains(targetSeason)) 100
            else if (detectedSeasons.any { it != targetSeason && it > 0 }) -100
            else 10
        }
    }

    fun resolveSeasonNumber(anilistId: Int, animeTitle: String?): Int {
        when (anilistId) {
            182896 -> return 8
            163139 -> return 7
            139630 -> return 6
            117193 -> return 5
            189046, 166240, 110277, 131681, 163142, 171638, 104276 -> return 4
            163134, 200637, 145139, 99147, 104578, 131518, 162670, 100166, 178789 -> return 3
            108632, 119661, 154768, 145064, 146065, 166873, 131586, 173548, 210031, 129874, 142329, 20958, 113936, 21856 -> return 2
            21355, 100049, 104270, 132405, 101347, 108465, 127720, 116589, 184951, 101922, 16498, 105333, 21459 -> return 1
        }
        var titleSeason: Int? = null
        if (!animeTitle.isNullOrEmpty()) {
            val raw = animeTitle.trim()
            val t = raw.lowercase()

            // 0. Final season for My Hero Academia -> season 8
            if (t.contains("final season") || t.contains("temporada final") || t.contains("saison finale")) {
                if (t.contains("hero academia") || t.contains("boku no hero") || anilistId == 182896) {
                    return 8
                }
            }

            // 1. Roman numerals with word boundaries
            if (Regex("""\b(?:season|saison|temporada|staffel|part|cour)\s*viii\b""", RegexOption.IGNORE_CASE).containsMatchIn(t) || Regex("""\bVIII\b""").containsMatchIn(raw)) titleSeason = 8
            else if (Regex("""\b(?:season|saison|temporada|staffel|part|cour)\s*vii\b""", RegexOption.IGNORE_CASE).containsMatchIn(t) || Regex("""\bVII\b""").containsMatchIn(raw)) titleSeason = 7
            else if (Regex("""\b(?:season|saison|temporada|staffel|part|cour)\s*vi\b""", RegexOption.IGNORE_CASE).containsMatchIn(t) || Regex("""\bVI\b""").containsMatchIn(raw)) titleSeason = 6
            else if (Regex("""\b(?:season|saison|temporada|staffel|part|cour)\s*v\b""", RegexOption.IGNORE_CASE).containsMatchIn(t) || Regex("""\bV\b""").containsMatchIn(raw)) titleSeason = 5
            else if (Regex("""\b(?:season|saison|temporada|staffel|part|cour)\s*iv\b""", RegexOption.IGNORE_CASE).containsMatchIn(t) || Regex("""\bIV\b""").containsMatchIn(raw)) titleSeason = 4
            else if (Regex("""\b(?:season|saison|temporada|staffel|part|cour)\s*iii\b""", RegexOption.IGNORE_CASE).containsMatchIn(t) || Regex("""\bIII\b""").containsMatchIn(raw)) titleSeason = 3
            else if (Regex("""\b(?:season|saison|temporada|staffel|part|cour)\s*ii\b""", RegexOption.IGNORE_CASE).containsMatchIn(t) || Regex("""\bII\b""").containsMatchIn(raw)) titleSeason = 2

            // 2. Ordinals
            if (titleSeason == null) {
                if (t.contains("2nd season") || t.contains("second season") || t.contains("2ª temporada") || t.contains("2a temporada") || t.contains("deuxième saison")) titleSeason = 2
                else if (t.contains("3rd season") || t.contains("third season") || t.contains("3ª temporada") || t.contains("3a temporada") || t.contains("troisième saison")) titleSeason = 3
                else if (t.contains("4th season") || t.contains("fourth season") || t.contains("4ª temporada") || t.contains("4a temporada")) titleSeason = 4
                else if (t.contains("5th season") || t.contains("fifth season") || t.contains("5ª temporada") || t.contains("5a temporada")) titleSeason = 5
                else if (t.contains("6th season") || t.contains("sixth season")) titleSeason = 6
                else if (t.contains("7th season") || t.contains("seventh season")) titleSeason = 7
                else if (t.contains("8th season") || t.contains("eighth season") || t.contains("8ª temporada") || t.contains("8a temporada")) titleSeason = 8
            }

            // 3. Keyword matches
            if (titleSeason == null) {
                val sMatch = Regex("""(?:season|saison|temporada|staffel|part|cour|s)\s*(\d+)""").find(t)
                if (sMatch != null) {
                    val num = sMatch.groupValues[1].toIntOrNull()
                    if (num != null && num in 1..20) titleSeason = num
                }
            }
            if (titleSeason == null) {
                val thMatch = Regex("""(\d+)(?:st|nd|rd|th)\s+(?:season|temporada|saison|staffel)""").find(t)
                if (thMatch != null) {
                    val num = thMatch.groupValues[1].toIntOrNull()
                    if (num != null && num in 1..20) titleSeason = num
                }
            }
            if (titleSeason == null) {
                val numEndMatch = Regex("""\s+(\d+)$""").find(t.trim())
                if (numEndMatch != null) {
                    val num = numEndMatch.groupValues[1].toIntOrNull()
                    if (num != null && num in 2..10) titleSeason = num
                }
            }
        }

        if (titleSeason != null && titleSeason > 1) {
            return titleSeason
        }

        val aniZipSeason = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getSeasonNumber(anilistId)
        if (aniZipSeason != null && aniZipSeason in 1..20) {
            return aniZipSeason
        }

        return titleSeason ?: 1
    }

    suspend fun extractRegionalAudio(
        anilistId: Int,
        episodeNum: Int,
        targetLang: String, // "french", "italian", "spanish_latino", "spanish_castellano", "german"
        animeTitle: String? = null,
        serverPreference: String? = null
    ): MultiLanguageStreamResult? = withContext(Dispatchers.IO) {
        val normLang = when (targetLang.lowercase()) {
            "french", "vf", "fr" -> "vf"
            "italian", "ita", "it" -> "ita"
            "spanish", "es", "spanish_latino", "latino" -> "latino"
            "spanish_castellano", "castellano" -> "castellano"
            "german", "deutsch", "de" -> "de"
            else -> targetLang.lowercase()
        }

        // Tier 1: Try Multi-Language Anime API Bridge (10.0.2.2:3000 / 127.0.0.1:3000)
        tryBridgeApi(anilistId, episodeNum, normLang, animeTitle)?.let { return@withContext it }

        // Tier 2: Resilient Native Upstream Extraction (Direct HLS / MP4 streams only)
        when (normLang) {
            "vf" -> extractFrench(anilistId, episodeNum, animeTitle)
            "ita" -> extractItalian(anilistId, episodeNum, animeTitle)
            "latino" -> extractSpanishLatino(anilistId, episodeNum, animeTitle, serverPreference)
            "castellano" -> extractSpanishCastellano(anilistId, episodeNum, animeTitle)
            "de" -> extractGerman(anilistId, episodeNum, animeTitle)
            else -> null
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Tier 1: Multi-Language API Bridge
    // ─────────────────────────────────────────────────────────────────────────────
    private suspend fun tryBridgeApi(anilistId: Int, episodeNum: Int, lang: String, animeTitle: String?): MultiLanguageStreamResult? {
        val bridgeHosts = listOf("http://10.0.2.2:3000", "http://127.0.0.1:3000")
        val seasonNum = resolveSeasonNumber(anilistId, animeTitle)
        for (host in bridgeHosts) {
            try {
                val url = "$host/api/anime/$anilistId/watch?season=$seasonNum&episode=$episodeNum&lang=$lang"
                val fastClient = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
                    .connectTimeout(1500, TimeUnit.MILLISECONDS)
                    .readTimeout(4000, TimeUnit.MILLISECONDS)
                    .build()
                val req = Request.Builder().url(url).build()
                val respStr = fastClient.newCall(req).execute().use { r ->
                    if (r.isSuccessful) r.body?.string() else null
                } ?: continue

                val json = JSONObject(respStr)
                val sources = json.optJSONArray("sources")
                if (sources != null && sources.length() > 0) {
                    val validSources = mutableListOf<JSONObject>()
                    for (i in 0 until sources.length()) {
                        val src = sources.getJSONObject(i)
                        val sUrl = src.optString("streamUrl").ifEmpty { src.optString("url") }
                        if (sUrl.isEmpty()) continue
                        val isHls = src.optBoolean("isHls", false) || sUrl.contains(".m3u8")
                        val isMp4 = sUrl.contains(".mp4") || sUrl.contains("streampeaker") || sUrl.contains("sibnet") || sUrl.contains("tiktokcdn") || sUrl.contains("mxcontent")
                        if ((isHls || isMp4) && !sUrl.endsWith(".html") && !sUrl.contains("/embed") && !sUrl.contains("/e/")) {
                            validSources.add(src)
                        }
                    }

                    // STRICT SELECTION PRIORITY:
                    // For Spanish audio (Latino & Castellano), prioritize direct stable MP4 (Mixdrop/mxcontent, Mp4Upload)
                    // over Savefiles HLS, because Savefiles CDN frequently aborts connections on mobile,
                    // whereas Mixdrop plays smoothly without error (as verified on Castellano).
                    val isSpanishLang = lang in listOf("latino", "spanish_latino", "spanish", "es", "castellano", "spanish_castellano")
                    val chosenSource = if (isSpanishLang) {
                        validSources.firstOrNull {
                            val u = it.optString("streamUrl").ifEmpty { it.optString("url") }
                            u.contains("mxcontent") || u.contains("mixdrop") || u.contains("mp4upload") || u.contains("uqload")
                        } ?: validSources.firstOrNull {
                            it.optBoolean("isHls", false) || (it.optString("streamUrl").ifEmpty { it.optString("url") }).contains(".m3u8")
                        } ?: validSources.firstOrNull()
                    } else {
                        // 1) First look for stream with isHls == true or .m3u8 in url
                        val hlsSource = validSources.firstOrNull {
                            it.optBoolean("isHls", false) || (it.optString("streamUrl").ifEmpty { it.optString("url") }).contains(".m3u8")
                        }
                        // 2) If no HLS stream found, look for direct .mp4 type
                        hlsSource ?: validSources.firstOrNull {
                            val u = it.optString("streamUrl").ifEmpty { it.optString("url") }
                            u.contains(".mp4") || !it.optBoolean("isHls", false)
                        }
                    }

                    // 3) Don't use any other gotten stream
                    if (chosenSource != null) {
                        val sUrl = chosenSource.optString("streamUrl").ifEmpty { chosenSource.optString("url") }
                        val serverName = chosenSource.optString("server", "")
                        val cleanProvider = when (lang) {
                            "vf", "french" -> if (serverName.isNotEmpty() && !serverName.contains("Anime", true)) serverName else "Helios"
                            "ita", "italian" -> if (serverName.isNotEmpty() && !serverName.contains("Anime", true)) serverName else "Titan"
                            "latino", "spanish_latino" -> if (serverName.isNotEmpty() && !serverName.contains("Latanime", true)) serverName else "Neptune"
                            "castellano", "spanish_castellano" -> if (serverName.isNotEmpty() && !serverName.contains("Latanime", true)) serverName else "Eclipse"
                            "de", "german" -> if (serverName.isNotEmpty() && !serverName.contains("Fire", true)) serverName else "Nexus"
                            else -> "Titan"
                        }
                        val cleanDubName = when (lang) {
                            "vf", "french" -> "French (VF)"
                            "ita", "italian" -> "Italian (ITA)"
                            "latino", "spanish_latino" -> "Spanish (Latino)"
                            "castellano", "spanish_castellano" -> "Spanish (Castellano)"
                            "de", "german" -> "German (Deutsch)"
                            else -> lang.uppercase()
                        }
                        val sourceEmbed = chosenSource.optString("embedUrl", "$host/")
                        val effectiveBridgeRef = when {
                            sUrl.contains("uqload", ignoreCase = true) -> "https://uqload.ws/"
                            sUrl.contains("streampeaker", ignoreCase = true) -> "https://animesaturn.ro/"
                            sUrl.contains("sibnet", ignoreCase = true) -> "https://video.sibnet.ru/"
                            sUrl.contains("savefiles", ignoreCase = true) -> "https://savefiles.com/"
                            sUrl.contains("mixdrop", ignoreCase = true) || sUrl.contains("mxcontent", ignoreCase = true) -> "https://mixdrop.top/"
                            sUrl.contains("ansembed", ignoreCase = true) || sUrl.contains("vmget", ignoreCase = true) || sUrl.contains("vmpx", ignoreCase = true) -> "https://ansembed.net/"
                            else -> sourceEmbed
                        }
                        val backupSource = validSources.firstOrNull { it != chosenSource }
                        val bUrl = backupSource?.let { it.optString("streamUrl").ifEmpty { it.optString("url") } } ?: ""
                        val bServer = backupSource?.optString("server", "") ?: ""
                        return MultiLanguageStreamResult(
                            hlsUrl = sUrl,
                            referer = effectiveBridgeRef,
                            provider = cleanProvider,
                            language = lang,
                            dubName = cleanDubName,
                            backupHlsUrl = bUrl,
                            backupProvider = bServer
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 1. French Dub (VF) via Anime-Sama (Anime-inspired name: Valkyrie / Aura)
    // ─────────────────────────────────────────────────────────────────────────────
    private suspend fun extractFrench(anilistId: Int, episodeNum: Int, animeTitle: String?): MultiLanguageStreamResult? {
        val slug = KNOWN_VF_MAPPINGS[anilistId] ?: vfCache[anilistId] ?: resolveVfSlug(anilistId, animeTitle)
        if (slug.isNullOrEmpty()) return null
        vfCache[anilistId] = slug

        val seasonNum = resolveSeasonNumber(anilistId, animeTitle)
        val seasonCandidates = mutableListOf<String>()
        val specificVfSeason = KNOWN_VF_SEASONS[anilistId]
        if (!specificVfSeason.isNullOrEmpty()) {
            seasonCandidates.add(specificVfSeason)
        }
        if (seasonNum > 1) {
            seasonCandidates.add("saison$seasonNum")
            seasonCandidates.add("saison${seasonNum}-cour2")
            seasonCandidates.add("saison${seasonNum}-partie2")
            seasonCandidates.add("saison${seasonNum}-part2")
            seasonCandidates.add("saison${seasonNum}-cour1")
            seasonCandidates.add("saison${seasonNum}-partie1")
            seasonCandidates.add("saison${seasonNum}-part1")
        } else {
            seasonCandidates.add("saison1")
            seasonCandidates.add("film")
        }

        for (sSlug in seasonCandidates) {
            val vfUrl = "https://animes-sama.fr/catalogue/$slug/$sSlug/vf"
            try {
                val req = Request.Builder()
                    .url(vfUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.8")
                    .addHeader("Referer", "https://animes-sama.fr/")
                    .build()

                var csrf = ""
                var cookiesHeader = ""
                val html = client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val setCookieList = resp.headers("Set-Cookie")
                        if (setCookieList.isNotEmpty()) {
                            cookiesHeader = setCookieList.joinToString("; ") { it.substringBefore(";") }
                        }
                        resp.body?.string()
                    } else null
                } ?: continue

                val csrfMatch = Pattern.compile("""csrf:\s*["']([a-f0-9]+)["']""", Pattern.CASE_INSENSITIVE).matcher(html)
                if (csrfMatch.find()) {
                    csrf = csrfMatch.group(1) ?: ""
                }

                val vfOffset = KNOWN_VF_OFFSETS[anilistId] ?: 0
                val effectiveEp = episodeNum + vfOffset
                val epNumbers = parseSamaEpisodeNumbers(html)
                val (targetEp, epIdx) = resolveSamaEpisode(effectiveEp, epNumbers)

                // 1. Try modern Anime-Sama signed player API
                if (csrf.isNotEmpty()) {
                    val matchedHls = mutableListOf<MultiLanguageStreamResult>()
                    val neutralHls = mutableListOf<MultiLanguageStreamResult>()
                    val mismatchedHls = mutableListOf<MultiLanguageStreamResult>()

                    val matchedMp4 = mutableListOf<MultiLanguageStreamResult>()
                    val neutralMp4 = mutableListOf<MultiLanguageStreamResult>()
                    val mismatchedMp4 = mutableListOf<MultiLanguageStreamResult>()

                    for (lecteur in 1..4) {
                        val signUrl = "https://animes-sama.fr/api/player/sign/$slug/$sSlug/vf"
                        val payload = JSONObject().apply {
                            put("ep", targetEp)
                            put("lecteur", lecteur)
                        }.toString()

                        val postReqBuilder = Request.Builder()
                            .url(signUrl)
                            .addHeader("User-Agent", USER_AGENT)
                            .addHeader("Content-Type", "application/json")
                            .addHeader("X-CSRF-Token", csrf)
                            .addHeader("X-Requested-With", "XMLHttpRequest")
                            .addHeader("Referer", vfUrl)
                            .addHeader("Origin", "https://animes-sama.fr")
                            .post(payload.toRequestBody("application/json".toMediaType()))

                        if (cookiesHeader.isNotEmpty()) {
                            postReqBuilder.addHeader("Cookie", cookiesHeader)
                        }

                        val signResp = client.newCall(postReqBuilder.build()).execute().use { r ->
                            if (r.isSuccessful) r.body?.string() else null
                        } ?: continue

                        val signJson = JSONObject(signResp)
                        val playerPath = signJson.optString("url")
                        if (playerPath.isNotEmpty()) {
                            val playerUrl = if (playerPath.startsWith("http")) playerPath else "https://animes-sama.fr$playerPath"
                            val embedRes = resolveAnimeSamaPlayerPage(playerUrl, vfUrl, cookiesHeader)
                            if (embedRes != null && embedRes.streamUrl.isNotEmpty()) {
                                val sRes = MultiLanguageStreamResult(
                                    hlsUrl = embedRes.streamUrl,
                                    referer = embedRes.referer,
                                    provider = embedRes.provider,
                                    language = "french",
                                    dubName = "French (VF)"
                                )
                                val isHls = embedRes.streamUrl.contains(".m3u8")
                                when {
                                    embedRes.detectedEp != null && embedRes.detectedEp == targetEp -> {
                                        if (isHls) matchedHls.add(sRes) else matchedMp4.add(sRes)
                                    }
                                    embedRes.detectedEp != null && embedRes.detectedEp != targetEp -> {
                                        if (isHls) mismatchedHls.add(sRes) else mismatchedMp4.add(sRes)
                                    }
                                    else -> {
                                        if (isHls) neutralHls.add(sRes) else neutralMp4.add(sRes)
                                    }
                                }
                            }
                        }
                    }

                    val chosenFromApi = matchedHls.firstOrNull()
                        ?: neutralHls.firstOrNull()
                        ?: matchedMp4.firstOrNull()
                        ?: neutralMp4.firstOrNull()
                        ?: mismatchedHls.firstOrNull()
                        ?: mismatchedMp4.firstOrNull()

                    if (chosenFromApi != null) {
                        val backupList = (matchedHls + neutralHls + matchedMp4 + neutralMp4).filter { it != chosenFromApi }
                        val backup = backupList.firstOrNull()
                        return chosenFromApi.copy(
                            backupHlsUrl = backup?.hlsUrl ?: "",
                            backupProvider = backup?.provider ?: ""
                        )
                    }
                }

                // 2. Fallback: Parse embed arrays if present in the page (eps1, eps2...)
                val embedUrls = extractEmbedUrlsFromJs(html, epIdx)
                val hlsCandidates = mutableListOf<Pair<String, String>>()
                val mp4Candidates = mutableListOf<Pair<String, String>>()

                for (embed in embedUrls) {
                    val embedRes = resolveFrenchEmbed(embed)
                    if (embedRes != null && embedRes.streamUrl.isNotEmpty()) {
                        val pair = Pair(embedRes.streamUrl, embedRes.provider)
                        if (embedRes.streamUrl.contains(".m3u8")) {
                            hlsCandidates.add(pair)
                        } else if (embedRes.streamUrl.contains(".mp4") || embedRes.streamUrl.contains("sibnet")) {
                            mp4Candidates.add(pair)
                        }
                    }
                }

                // Strictly prefer HLS first, then direct MP4
                val chosen = hlsCandidates.firstOrNull() ?: mp4Candidates.firstOrNull()
                if (chosen != null) {
                    return MultiLanguageStreamResult(
                        hlsUrl = chosen.first,
                        referer = "https://animes-sama.fr/",
                        provider = chosen.second,
                        language = "french",
                        dubName = "French (VF)"
                    )
                }
            } catch (_: Exception) {}
        }
        return null
    }

    private suspend fun resolveAnimeSamaPlayerPage(playerUrl: String, referer: String, cookiesHeader: String = ""): FrenchEmbedResult? {
        try {
            val reqBuilder = Request.Builder()
                .url(playerUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", referer)
            if (cookiesHeader.isNotEmpty()) {
                reqBuilder.addHeader("Cookie", cookiesHeader)
            }

            val pResp = client.newCall(reqBuilder.build()).execute().use { r ->
                if (r.isSuccessful) r.body?.string() else null
            } ?: return null

            // 1. Fallback 302 redirect (data-fallback-url)
            val fbMatch = Pattern.compile("""data-fallback-url=["']([^"']+)["']""").matcher(pResp)
            if (fbMatch.find()) {
                val fbRel = fbMatch.group(1)!!
                val fbUrl = if (fbRel.startsWith("http")) fbRel else "https://animes-sama.fr$fbRel"
                val fbReqBuilder = Request.Builder()
                    .url(fbUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", playerUrl)
                if (cookiesHeader.isNotEmpty()) {
                    fbReqBuilder.addHeader("Cookie", cookiesHeader)
                }

                val loc = noRedirectClient.newCall(fbReqBuilder.build()).execute().use { r ->
                    if (r.code in 300..399) r.header("Location") else null
                }
                if (!loc.isNullOrEmpty()) {
                    val streamRes = resolveFrenchEmbed(loc)
                    if (streamRes != null) {
                        return streamRes
                    }
                }
            }

            // 2. Direct Sibnet player.src in page
            val sibMatch = Pattern.compile("""player\.src\(\[\{\s*src:\s*["']([^"']+)["']""").matcher(pResp)
            val sibPath = if (sibMatch.find()) sibMatch.group(1) else {
                val vMatch = Pattern.compile("""["'](/v/[^"']+\.mp4)["']""").matcher(pResp)
                if (vMatch.find()) vMatch.group(1) else null
            }
            if (!sibPath.isNullOrEmpty()) {
                val fullSibnet = if (sibPath.startsWith("http")) sibPath else "https://video.sibnet.ru$sibPath"
                val pTitleMatch = Pattern.compile("""<title>(.*?)</title>""", Pattern.CASE_INSENSITIVE).matcher(pResp)
                val pTitle = if (pTitleMatch.find()) pTitleMatch.group(1) ?: "" else ""
                return FrenchEmbedResult(
                    streamUrl = fullSibnet,
                    provider = "Helios",
                    detectedEp = extractEpFromTitle(pTitle),
                    referer = "https://video.sibnet.ru/"
                )
            }

            // 3. Iframe embed
            val iframeMatch = Pattern.compile("""<iframe[^>]+src=["']([^"']+)["']""").matcher(pResp)
            if (iframeMatch.find()) {
                val ifr = iframeMatch.group(1)!!
                val streamRes = resolveFrenchEmbed(ifr)
                if (streamRes != null) {
                    return streamRes
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private suspend fun searchAnimeSama(query: String): String? {
        if (query.isBlank()) return null
        try {
            val url = "https://animes-sama.fr/catalogue?search=${URLEncoder.encode(query.trim(), "UTF-8")}"
            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", "https://animes-sama.fr/")
                .build()
            val html = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return null
            val pattern = Pattern.compile("""href=["']/catalogue/([^/'"\s]+)/?["']""", Pattern.CASE_INSENSITIVE)
            val m = pattern.matcher(html)
            while (m.find()) {
                val s = m.group(1) ?: continue
                if (s !in listOf("liste", "planning", "random", "catalogue")) {
                    return s
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private suspend fun resolveVfSlug(anilistId: Int, animeTitle: String?): String? {
        val titles = resolveTitleVariants(anilistId, animeTitle)
        for (t in titles) {
            val clean = t.lowercase()
                .replace(Regex("""(?i)\b(season\s*\d+|2nd\s*season|3rd\s*season|4th\s*season|\d+(?:st|nd|rd|th)\s+season|part\s*\d+|s\d+|temporada\s*\d+|saison\s*\d+|staffel\s*\d+)\b"""), "")
                .trim()
            val searched = searchAnimeSama(clean) ?: (if (clean != t.lowercase()) searchAnimeSama(t) else null)
            if (!searched.isNullOrEmpty()) return searched
        }
        for (t in titles) {
            val clean = t.lowercase()
                .replace(Regex("""(?i)\b(season\s*\d+|2nd\s*season|3rd\s*season|4th\s*season|\d+(?:st|nd|rd|th)\s+season|part\s*\d+|s\d+|temporada\s*\d+|saison\s*\d+|staffel\s*\d+)\b"""), "")
                .trim()
            val baseSlug = clean.replace(Regex("[^a-z0-9]+"), "-").trim('-')
            if (baseSlug.isNotEmpty()) return baseSlug
        }
        return null
    }

    private fun parseSamaEpisodeNumbers(html: String): List<Int> {
        val epMatch = Pattern.compile("""window\.__EPISODE_NUMBERS\s*=\s*(\[[^\]]+\]);""").matcher(html)
        if (epMatch.find()) {
            try {
                val jsonArr = JSONArray(epMatch.group(1) ?: "[]")
                val list = mutableListOf<Int>()
                for (i in 0 until jsonArr.length()) {
                    list.add(jsonArr.getInt(i))
                }
                if (list.isNotEmpty()) return list
            } catch (_: Exception) {}
        }

        // Fallback: parse eps1 array count
        val eps1Match = Pattern.compile("""var\s+eps1\s*=\s*\[(.*?)\];""", Pattern.DOTALL).matcher(html)
        if (eps1Match.find()) {
            val content = eps1Match.group(1) ?: ""
            val count = content.split(",").count { it.trim().trim('\'', '"').startsWith("http") }
            if (count > 0) {
                return (1..count).toList()
            }
        }
        return emptyList()
    }

    private fun resolveSamaEpisode(requestedEp: Int, epNumbers: List<Int>): Pair<Int, Int> {
        if (epNumbers.isEmpty()) return Pair(requestedEp, (requestedEp - 1).coerceAtLeast(0))

        // 1. Direct match: requestedEp is in epNumbers list
        val directIdx = epNumbers.indexOf(requestedEp)
        if (directIdx != -1) {
            return Pair(requestedEp, directIdx)
        }

        // 2. Relative index: requestedEp in 1..epNumbers.size (0-based)
        val relIdx = requestedEp - 1
        if (relIdx in epNumbers.indices) {
            return Pair(epNumbers[relIdx], relIdx)
        }

        // 3. Absolute offset if requestedEp is within range of first and last
        val first = epNumbers.first()
        val last = epNumbers.last()
        if (requestedEp in first..last) {
            val idx = requestedEp - first
            if (idx in epNumbers.indices) {
                return Pair(epNumbers[idx], idx)
            }
        }

        // 4. Modulo for continuous absolute episode numbers across seasons
        if (requestedEp > last && epNumbers.isNotEmpty()) {
            val modIdx = (requestedEp - 1) % epNumbers.size
            if (modIdx in epNumbers.indices) {
                return Pair(epNumbers[modIdx], modIdx)
            }
        }

        // Fallback to clamped index
        val clampedIdx = (requestedEp - 1).coerceIn(0, epNumbers.size - 1)
        return Pair(epNumbers[clampedIdx], clampedIdx)
    }

    private fun extractEmbedUrlsFromJs(js: String, index: Int): List<String> {
        val out = mutableListOf<String>()
        val arrRegex = Pattern.compile("""var\s+eps\d*\s*=\s*\[(.*?)\];""", Pattern.DOTALL)
        val m = arrRegex.matcher(js)
        while (m.find()) {
            val content = m.group(1) ?: continue
            val parts = content.split(",").map { it.trim().trim('\'', '"') }.filter { it.startsWith("http") }
            if (index in parts.indices) {
                out.add(parts[index])
            }
        }
        return out
    }

    fun extractEpFromTitle(title: String): Int? {
        if (title.isBlank()) return null
        val p1 = Regex("""(?i)\b(?:s\d+[-_ ]*)?(?:ep|episode|e)\s*0?(\d+)\b""").find(title)
        if (p1 != null) return p1.groupValues[1].toIntOrNull()

        val p2 = Regex("""(?i)\bs\d+\s+0?(\d+)\b""").find(title)
        if (p2 != null) return p2.groupValues[1].toIntOrNull()

        val p3 = Regex("""(?i)\b0?(\d+)\s*(?:vf|vostfr)\b""").find(title)
        if (p3 != null) return p3.groupValues[1].toIntOrNull()

        return null
    }

    private suspend fun resolveFrenchEmbed(embedUrl: String): FrenchEmbedResult? {
        try {
            // Sibnet direct video resolution
            if (embedUrl.contains("sibnet.ru")) {
                val sibnetUrl = if (embedUrl.startsWith("http")) embedUrl else "https://video.sibnet.ru$embedUrl"
                val req = Request.Builder()
                    .url(sibnetUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", "https://video.sibnet.ru/")
                    .build()
                val html = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
                if (html != null) {
                    val m = Pattern.compile("""['"](/v/[^'"]+\.mp4[^'"]*)['"]""").matcher(html)
                    if (m.find()) {
                        val path = m.group(1)!!
                        val fullStream = if (path.startsWith("http")) path else "https://video.sibnet.ru$path"
                        val sibTitleMatch = Pattern.compile("""<title>(.*?)</title>""", Pattern.CASE_INSENSITIVE).matcher(html)
                        val sibTitle = if (sibTitleMatch.find()) sibTitleMatch.group(1) ?: "" else ""
                        return FrenchEmbedResult(fullStream, "Helios", extractEpFromTitle(sibTitle), "https://video.sibnet.ru/")
                    }
                }
            }

            // VOE resolution
            if (embedUrl.contains("voe.sx") || embedUrl.contains("/e/")) {
                val voeRes = resolveVoeStream(embedUrl)
                if (voeRes != null && voeRes.first.isNotEmpty()) {
                    return FrenchEmbedResult(voeRes.first, "Bifrost", null, voeRes.second)
                }
            }

            // General embed resolution (AnsEmbed, Sendvid, etc.)
            val req = Request.Builder()
                .url(embedUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", "https://animes-sama.fr/")
                .build()

            val html = client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return null

            val titleMatch = Pattern.compile("""<title>(.*?)</title>""", Pattern.CASE_INSENSITIVE).matcher(html)
            val embedTitle = if (titleMatch.find()) titleMatch.group(1) ?: "" else ""
            val detectedEp = extractEpFromTitle(embedTitle)

            val m3u8Regex = Pattern.compile("""["'](https?://[^"']+\.m3u8[^"']*)["']""")
            val m = m3u8Regex.matcher(html)
            if (m.find()) {
                val candidate = m.group(1)
                if (candidate != null && !candidate.contains("sample", ignoreCase = true) && !candidate.contains("bigbuckbunny", ignoreCase = true)) {
                    return FrenchEmbedResult(candidate, "Aura", detectedEp, embedUrl)
                }
            }

            val mp4Regex = Pattern.compile("""(?:file|src|source)\s*[:=]\s*["'](https?://[^"']+\.mp4[^"']*)["']""")
            val m2 = mp4Regex.matcher(html)
            if (m2.find()) {
                val path = m2.group(1)
                if (!path.isNullOrEmpty()) {
                    return FrenchEmbedResult(path, "Mirage", detectedEp, embedUrl)
                }
            }
        } catch (_: Exception) {}
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 2. Italian Dub (ITA) via AnimeSaturn (Anime-inspired name: Titan)
    // ─────────────────────────────────────────────────────────────────────────────
    private suspend fun extractItalian(anilistId: Int, episodeNum: Int, animeTitle: String?): MultiLanguageStreamResult? {
        val targetSeason = resolveSeasonNumber(anilistId, animeTitle)
        val titles = resolveTitleVariants(anilistId, animeTitle)
        val slug = KNOWN_ITA_MAPPINGS[anilistId] ?: itaCache[anilistId] ?: searchAnimeSaturn(titles, targetSeason) ?: resolveItaSlug(anilistId, titles.firstOrNull(), targetSeason)
        if (slug.isNullOrEmpty()) return null
        itaCache[anilistId] = slug

        val baseSaturn = "https://animesaturn.ro"
        val cleanSlug = slug.replace(Regex("""-[a-z0-9]{4,6}(-\d+)?$"""), "")

        // 1. Discovery via anime page (parses #episodes and .eplister real episode links)
        val animePageCandidates = mutableListOf<String>()
        animePageCandidates.add("$baseSaturn/anime/$slug/")
        animePageCandidates.add("$baseSaturn/anime/$cleanSlug/")
        animePageCandidates.add("$baseSaturn/anime/${cleanSlug.removeSuffix("-ita")}-ita/")
        if (targetSeason > 1) {
            val root = cleanSlug.removeSuffix("-ita")
            animePageCandidates.add("$baseSaturn/anime/$root-$targetSeason-ita/")
            animePageCandidates.add("$baseSaturn/anime/$root-s$targetSeason-ita/")
            animePageCandidates.add("$baseSaturn/anime/$root-$targetSeason/")
            animePageCandidates.add("$baseSaturn/anime/$root-s$targetSeason/")
        }

        for (aUrl in animePageCandidates) {
            try {
                val req = Request.Builder()
                    .url(aUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
                    .addHeader("Referer", "$baseSaturn/")
                    .build()

                val html = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: continue

                // Match all real episode links
                val epLinkPattern = Pattern.compile("""href=["'](https?://(?:www\.)?animesaturn\.ro/[^"']*(?:episode|episodio|ep|a-ep)-(\d+)/?)["']""", Pattern.CASE_INSENSITIVE)
                val m = epLinkPattern.matcher(html)
                val epMap = mutableMapOf<Int, String>()
                while (m.find()) {
                    val url = m.group(1) ?: continue
                    val num = m.group(2)?.toIntOrNull() ?: continue
                    if (!epMap.containsKey(num)) {
                        epMap[num] = url
                    }
                }

                val targetEpUrl = epMap[episodeNum]
                if (targetEpUrl != null) {
                    val res = fetchAndExtractSaturnEpisode(targetEpUrl, baseSaturn)
                    if (res != null) return res
                }
            } catch (_: Exception) {}
        }

        // 2. Direct candidate URLs fallback
        val episodeCandidates = mutableListOf<String>()
        episodeCandidates.add("$baseSaturn/$slug-episodio-$episodeNum/")
        episodeCandidates.add("$baseSaturn/$slug-episode-$episodeNum/")
        episodeCandidates.add("$baseSaturn/${cleanSlug}-episodio-$episodeNum/")
        episodeCandidates.add("$baseSaturn/${cleanSlug}-episode-$episodeNum/")
        episodeCandidates.add("$baseSaturn/${cleanSlug}-ep-$episodeNum/")
        episodeCandidates.add("$baseSaturn/$slug-ep-$episodeNum/")
        if (targetSeason > 1) {
            val root = cleanSlug.removeSuffix("-ita")
            episodeCandidates.add("$baseSaturn/$root-$targetSeason-ita-ep-$episodeNum/")
            episodeCandidates.add("$baseSaturn/$root-$targetSeason-ita-episodio-$episodeNum/")
            episodeCandidates.add("$baseSaturn/$root-$targetSeason-ita-episode-$episodeNum/")
        }

        for (epUrl in episodeCandidates) {
            val res = fetchAndExtractSaturnEpisode(epUrl, baseSaturn)
            if (res != null) return res
        }

        return null
    }

    private suspend fun searchAnimeSaturn(titles: List<String>, targetSeason: Int = 1): String? {
        val queryVariants = mutableListOf<String>()
        val coreTokens = mutableListOf<String>()
        val roman = seasonToRoman(targetSeason)

        for (raw in titles) {
            val clean = raw.replace(Regex("""(?i)\b(season\s*\d+|2nd\s*season|3rd\s*season|4th\s*season|\d+(?:st|nd|rd|th)\s+season|part\s*\d+|s\d+|temporada\s*\d+|saison\s*\d+|staffel\s*\d+|cour\s*\d+)\b"""), "").trim()
            val baseQuery = if (clean.isNotEmpty()) clean else raw
            if (targetSeason > 1) {
                if (roman.isNotEmpty()) queryVariants.add("$baseQuery $roman")
                queryVariants.add("$baseQuery $targetSeason")
                queryVariants.add("$baseQuery season $targetSeason")
                queryVariants.add("$baseQuery s$targetSeason")
            }
            queryVariants.add(baseQuery)
            if (baseQuery != raw) queryVariants.add(raw)
            if (baseQuery.contains("rezero", ignoreCase = true) || baseQuery.contains("re:zero", ignoreCase = true)) {
                queryVariants.add("re:zero")
                queryVariants.add("rezero")
            }

            val toks = baseQuery.lowercase()
                .replace(Regex("[^a-z0-9]"), " ")
                .split(" ")
                .filter { it.length >= 3 && !it.all { c -> c.isDigit() } && it !in listOf("the", "and", "for", "with", "from", "der", "die", "das", "del", "della", "degli", "dei", "con", "per", "tra", "fra") }
            for (tok in toks) {
                if (!coreTokens.contains(tok)) coreTokens.add(tok)
            }
        }

        var bestSlug: String? = null
        var bestScore = -999

        for (q in queryVariants.distinct()) {
            if (q.trim().length < 4) continue

            try {
                val url = "https://animesaturn.ro/?s=${URLEncoder.encode(q, "UTF-8")}"
                val req = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", "https://animesaturn.ro/")
                    .build()
                val html = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: continue

                val searchBlock = if (html.contains("search-page")) {
                    html.substringAfter("search-page").substringBefore("footer")
                } else if (html.contains("listupd")) {
                    html.substringAfter("listupd").substringBefore("footer")
                } else {
                    html
                }

                val pattern = Pattern.compile("""href=["']https?://(?:www\.)?animesaturn\.ro/anime/([^/"'\s]+)/?["']([^>]*>.*?</a>)?""", Pattern.CASE_INSENSITIVE)
                val m = pattern.matcher(searchBlock)
                while (m.find()) {
                    val slug = m.group(1) ?: continue
                    val text = m.group(2) ?: ""
                    val lowerSlug = slug.lowercase()

                    val isTokenMatch = coreTokens.isNotEmpty() && coreTokens.any { lowerSlug.contains(it) }
                    if (!isTokenMatch) continue

                    val isSubIta = slug.contains("sub-ita", ignoreCase = true) || text.contains("sub ita", ignoreCase = true)
                    // Italian Dub must explicitly have (ITA) badge in text/title or end in -ita without sub
                    val hasItaBadge = Regex("""(?i)\(ita\)|\bita\s*ita\b|\bdub\s*ita\b|\bita\s*dub\b""").containsMatchIn(text) ||
                            (slug.endsWith("-ita") && !slug.contains("sub-ita", ignoreCase = true) && !text.contains("sub ita", ignoreCase = true))
                    val isIta = hasItaBadge && !isSubIta

                    if (isIta) {
                        var score = scoreSeasonMatch(slug, text, targetSeason)
                        if (Regex("""(?i)\(ita\)""").containsMatchIn(text)) score += 30
                        if (score > bestScore && score > 0) {
                            bestScore = score
                            bestSlug = slug
                            if (score >= 120) return bestSlug
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return if (bestScore > 0) bestSlug else null
    }

    private suspend fun fetchAndExtractSaturnEpisode(episodeUrl: String, baseSaturn: String): MultiLanguageStreamResult? {
        try {
            val req = Request.Builder()
                .url(episodeUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
                .addHeader("Referer", "$baseSaturn/")
                .build()

            val epHtml = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return null

            // Parse server buttons: value contains base64 encoded iframe html
            val btnPattern = Pattern.compile("""value:\s*['"]([A-Za-z0-9+/=]+)['"]""")
            val mBtn = btnPattern.matcher(epHtml)
            val hlsStreams = mutableListOf<String>()
            val mp4Streams = mutableListOf<String>()

            while (mBtn.find()) {
                val b64 = mBtn.group(1) ?: continue
                val decoded = try {
                    String(Base64.decode(b64, Base64.DEFAULT))
                } catch (_: Exception) { "" }

                val iframeMatch = Pattern.compile("""src=["']([^"']+)["']""").matcher(decoded)
                if (iframeMatch.find()) {
                    var playerUrl = iframeMatch.group(1) ?: continue
                    playerUrl = playerUrl.replace("&#038;", "&").replace("&amp;", "&")
                    if (playerUrl.startsWith("/")) playerUrl = "$baseSaturn$playerUrl"

                    if (playerUrl.contains("svcms", ignoreCase = true) || playerUrl.contains("megaplay", ignoreCase = true) || playerUrl.contains("animestream", ignoreCase = true)) {
                        val m3u8Stream = resolveMegaplayStream(playerUrl, episodeUrl)
                        if (!m3u8Stream.isNullOrEmpty()) {
                            hlsStreams.add(m3u8Stream)
                        }
                        val saturnStream = resolveSaturnPlayer(playerUrl, episodeUrl)
                        if (!saturnStream.isNullOrEmpty()) {
                            if (saturnStream.contains(".m3u8")) hlsStreams.add(saturnStream)
                            else mp4Streams.add(saturnStream)
                        }
                    } else {
                        val streamUrl = resolveSaturnPlayer(playerUrl, episodeUrl)
                        if (!streamUrl.isNullOrEmpty()) {
                            if (streamUrl.contains(".m3u8")) {
                                hlsStreams.add(streamUrl)
                            } else if (streamUrl.contains(".mp4") || streamUrl.contains("streampeaker")) {
                                mp4Streams.add(streamUrl)
                            }
                        }
                    }
                }
            }

            // Also check direct iframes in epHtml (e.g. animestream-child player)
            val directIframeMatch = Pattern.compile("""<iframe[^>]+src=["']([^"']+)["']""").matcher(epHtml)
            while (directIframeMatch.find()) {
                var playerUrl = directIframeMatch.group(1) ?: continue
                playerUrl = playerUrl.replace("&#038;", "&").replace("&amp;", "&")
                if (playerUrl.startsWith("/")) playerUrl = "$baseSaturn$playerUrl"

                if (playerUrl.contains("svcms", ignoreCase = true) || playerUrl.contains("megaplay", ignoreCase = true) || playerUrl.contains("animestream", ignoreCase = true)) {
                    val m3u8Stream = resolveMegaplayStream(playerUrl, episodeUrl)
                    if (!m3u8Stream.isNullOrEmpty() && !hlsStreams.contains(m3u8Stream)) {
                        hlsStreams.add(m3u8Stream)
                    }
                }
            }

            // Strictly prefer direct MP4 / Streampeaker HD stream, fallback to master HLS (Megaplay)
            val chosen = mp4Streams.firstOrNull() ?: hlsStreams.firstOrNull()
            if (chosen != null) {
                val backup = if (chosen in mp4Streams) hlsStreams.firstOrNull() else mp4Streams.firstOrNull()
                val effectiveRef = if (chosen.contains("megaplay", ignoreCase = true)) "https://cdn2.megaplay.su/" else "https://animesaturn.ro/"
                return MultiLanguageStreamResult(
                    hlsUrl = chosen,
                    referer = effectiveRef,
                    provider = "Titan",
                    language = "italian",
                    dubName = "Italian (ITA)",
                    backupHlsUrl = backup ?: "",
                    backupProvider = if (backup != null) "Titan Backup" else ""
                )
            }
        } catch (_: Exception) {}
        return null
    }

    private fun resolveItaSlug(anilistId: Int, animeTitle: String?, targetSeason: Int = 1): String? {
        val clean = animeTitle?.lowercase()?.replace(Regex("[^a-z0-9]+"), "-")?.trim('-') ?: return null
        val seasonSuffix = if (targetSeason > 1 && !clean.contains("-$targetSeason") && !clean.contains("-s$targetSeason")) "-$targetSeason" else ""
        val base = "$clean$seasonSuffix"
        return if (!base.endsWith("-ita")) "$base-ita" else base
    }

    private suspend fun resolveSaturnPlayer(playerUrl: String, referer: String): String? {
        try {
            val req = Request.Builder()
                .url(playerUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
                .addHeader("Referer", referer)
                .build()

            val html = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return null

            // Streampeaker script: var src = 'https://srv*.streampeaker.org/...mp4';
            val srcPattern = Pattern.compile("""var\s+src\s*=\s*["']([^"']+)["']""")
            val mSrc = srcPattern.matcher(html)
            if (mSrc.find()) {
                val raw = mSrc.group(1)!!.replace("\\u0026", "&").replace("&#038;", "&").replace("&amp;", "&")
                if (raw.startsWith("http") && !raw.contains("megaplay")) {
                    return raw
                }
            }

            val m3u8Match = Pattern.compile("""["'](https?://[^"']+\.m3u8[^"']*)["']""").matcher(html)
            if (m3u8Match.find()) return m3u8Match.group(1)

            val mp4Match = Pattern.compile("""(?:file|src)\s*[:=]\s*["'](https?://[^"']+\.mp4[^"']*)["']""").matcher(html)
            if (mp4Match.find()) return mp4Match.group(1)
        } catch (_: Exception) {}
        return null
    }

    private suspend fun resolveMegaplayStream(playerUrl: String, referer: String): String? {
        try {
            val req = Request.Builder()
                .url(playerUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", referer)
                .build()

            val phtml = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return null

            // Direct m3u8 in page
            val directM3u8 = Pattern.compile("""https?://[^\s"\'<>]+\.m3u8[^\s"\'<>]*""").matcher(phtml)
            if (directM3u8.find()) return directM3u8.group(0)

            val ifrMatch = Pattern.compile("""<iframe[^>]+src=['"]([^'"]+)['"]""").matcher(phtml)
            val megaUrl = if (ifrMatch.find()) ifrMatch.group(1) else {
                if (playerUrl.contains("megaplay")) playerUrl else null
            } ?: return null

            val megaReq = Request.Builder()
                .url(megaUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", playerUrl)
                .build()

            val mHtml = client.newCall(megaReq).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return null
            val m3u8Match = Pattern.compile("""https?://[^\s"\'<>]+\.m3u8[^\s"\'<>]*""").matcher(mHtml)
            if (m3u8Match.find()) {
                return m3u8Match.group(0)
            }
        } catch (_: Exception) {}
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 3 & 4. Spanish Latino & Castellano via AnimeAV1 & Latanime
    // ─────────────────────────────────────────────────────────────────────────────
    private suspend fun extractSpanishLatino(
        anilistId: Int,
        episodeNum: Int,
        animeTitle: String?,
        serverPreference: String? = null
    ): MultiLanguageStreamResult? {
        val targetSeason = resolveSeasonNumber(anilistId, animeTitle)

        // 1. If user explicitly selected Mercury (SoloLatino), directly route to it
        if (serverPreference != null && (serverPreference.equals("mercury", ignoreCase = true) || serverPreference.equals("sololatino", ignoreCase = true))) {
            return extractSoloLatino(anilistId, episodeNum, animeTitle, targetSeason)
        }

        // 2. If user explicitly selected Neptune (Latanime), directly route to it
        if (serverPreference != null && (serverPreference.equals("neptune", ignoreCase = true) || serverPreference.equals("zenith", ignoreCase = true))) {
            return extractLatanime(anilistId, episodeNum, animeTitle, targetSeason)
        }

        // 3. Primary server for Spanish Latino: AnimeAV1
        // (Plays DUB first if available; otherwise plays Spanish SUB under the same server name "AnimeAV1")
        val av1Res = AnimeAV1Extractor.extractLatino(anilistId, episodeNum, animeTitle)
        if (av1Res != null && av1Res.hlsUrl.isNotEmpty()) {
            return av1Res
        }

        // If user explicitly selected AnimeAV1, do not fall back
        if (serverPreference != null && serverPreference.equals("animeav1", ignoreCase = true)) {
            return null
        }

        // 4. Fallback 1: Latanime (Neptune)
        val latanimeRes = extractLatanime(anilistId, episodeNum, animeTitle, targetSeason)
        if (latanimeRes != null && latanimeRes.hlsUrl.isNotEmpty()) {
            return latanimeRes
        }

        // 5. Fallback 2: SoloLatino server (Mercury)
        return extractSoloLatino(anilistId, episodeNum, animeTitle, targetSeason)
    }

    private suspend fun extractLatanime(
        anilistId: Int,
        episodeNum: Int,
        animeTitle: String?,
        targetSeason: Int
    ): MultiLanguageStreamResult? {
        val titles = resolveTitleVariants(anilistId, animeTitle)
        val slug = KNOWN_LATINO_MAPPINGS[anilistId]
            ?: latinoCache[anilistId]
            ?: searchLatanime(titles, "latino", targetSeason)
            ?: resolveLatinoSlug(anilistId, titles.firstOrNull(), targetSeason)

        if (!slug.isNullOrEmpty()) {
            latinoCache[anilistId] = slug
            val offset = KNOWN_LATINO_OFFSETS[anilistId] ?: 0
            val effectiveEp = episodeNum + offset

            val candidates = mutableListOf<String>()
            val isFinalSeason = targetSeason == 8 || (animeTitle != null && (animeTitle.contains("final season", ignoreCase = true) || animeTitle.contains("temporada final", ignoreCase = true)))

            if (isFinalSeason) {
                val base = slug.replace("-audio-latino", "").replace("-latino", "").replace("-temporada-8", "").replace("-s8", "")
                if (base.isNotEmpty()) {
                    candidates.add("https://latanime.org/ver/$base-final-season-latino-episodio-$effectiveEp")
                    candidates.add("https://latanime.org/ver/$base-final-season-audio-latino-episodio-$effectiveEp")
                }
                if (!slug.contains("final-season")) {
                    candidates.add("https://latanime.org/ver/$slug-episodio-$effectiveEp")
                }
                if (base.isNotEmpty()) {
                    candidates.add("https://latanime.org/ver/$base-temporada-8-episodio-$effectiveEp")
                }
            } else {
                candidates.add("https://latanime.org/ver/$slug-episodio-$effectiveEp")
                if (targetSeason > 1) {
                    val base = slug.replace("-audio-latino", "").replace("-latino", "")
                    candidates.add("https://latanime.org/ver/$base-s$targetSeason-latino-episodio-$effectiveEp")
                    candidates.add("https://latanime.org/ver/$base-temporada-$targetSeason-latino-episodio-$effectiveEp")
                } else {
                    if (slug.contains("-audio-latino")) {
                        candidates.add("https://latanime.org/ver/${slug.replace("-audio-latino", "-latino")}-episodio-$effectiveEp")
                    } else if (slug.contains("-latino")) {
                        candidates.add("https://latanime.org/ver/${slug.replace("-latino", "-audio-latino")}-episodio-$effectiveEp")
                    }
                }
            }

            for (watchUrl in candidates.distinct()) {
                val res = extractLatanimeWatch(watchUrl, "latino", "Spanish (Latino)", isCastellano = false)
                if (res != null) return res
            }

            // Fallback: If direct candidate watchUrls don't hit, inspect the anime details page https://latanime.org/anime/$slug
            val animePageRes = extractFromAnimePage(slug, effectiveEp, "latino", "Spanish (Latino)", isCastellano = false)
            if (animePageRes != null) return animePageRes
        }
        return null
    }

    private suspend fun extractSpanishCastellano(anilistId: Int, episodeNum: Int, animeTitle: String?): MultiLanguageStreamResult? {
        val targetSeason = resolveSeasonNumber(anilistId, animeTitle)
        val titles = resolveTitleVariants(anilistId, animeTitle)
        val slug = KNOWN_CASTELLANO_MAPPINGS[anilistId] ?: castellanoCache[anilistId] ?: searchLatanime(titles, "castellano", targetSeason) ?: resolveCastellanoSlug(anilistId, titles.firstOrNull(), targetSeason)
        if (slug.isNullOrEmpty()) return null
        castellanoCache[anilistId] = slug

        val offset = KNOWN_CASTELLANO_OFFSETS[anilistId] ?: 0
        val effectiveEp = episodeNum + offset

        val candidates = mutableListOf<String>()
        val isFinalSeason = targetSeason == 8 || (animeTitle != null && (animeTitle.contains("final season", ignoreCase = true) || animeTitle.contains("temporada final", ignoreCase = true)))

        if (isFinalSeason) {
            val base = slug.replace("-castellano", "").replace("-s8-especial", "").replace("-s8", "")
            if (base.isNotEmpty()) {
                candidates.add("https://latanime.org/ver/$base-final-season-castellano-episodio-$effectiveEp")
                candidates.add("https://latanime.org/ver/$base-temporada-final-castellano-episodio-$effectiveEp")
            }
            if (!slug.contains("final-season")) {
                candidates.add("https://latanime.org/ver/$slug-episodio-$effectiveEp")
            }
            if (base.isNotEmpty()) {
                candidates.add("https://latanime.org/ver/$base-s8-castellano-episodio-$effectiveEp")
                candidates.add("https://latanime.org/ver/$base-s8-especial-castellano-episodio-$effectiveEp")
            }
            candidates.add("https://latanime.org/ver/boku-no-hero-academia-s8-especial-castellano-episodio-$effectiveEp")
        } else {
            candidates.add("https://latanime.org/ver/$slug-episodio-$effectiveEp")
            if (targetSeason > 1) {
                val base = slug.replace("-castellano", "")
                candidates.add("https://latanime.org/ver/$base-s$targetSeason-castellano-episodio-$effectiveEp")
                candidates.add("https://latanime.org/ver/$base-temporada-$targetSeason-castellano-episodio-$effectiveEp")
            } else {
                if (slug.contains("-castellano")) {
                    candidates.add("https://latanime.org/ver/${slug.replace("-castellano", "-s1-castellano")}-episodio-$effectiveEp")
                }
            }
        }
        for (watchUrl in candidates.distinct()) {
            val res = extractLatanimeWatch(watchUrl, "castellano", "Spanish (Castellano)", isCastellano = true)
            if (res != null) return res
        }

        // Fallback: If direct candidate watchUrls don't hit, inspect the anime details page https://latanime.org/anime/$slug
        val animePageRes = extractFromAnimePage(slug, effectiveEp, "castellano", "Spanish (Castellano)", isCastellano = true)
        if (animePageRes != null) return animePageRes

        return null
    }

    private suspend fun extractFromAnimePage(slug: String, effectiveEp: Int, langKey: String, dubLabel: String, isCastellano: Boolean): MultiLanguageStreamResult? {
        try {
            val animeUrl = "https://latanime.org/anime/$slug"
            val req = Request.Builder()
                .url(animeUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", "https://latanime.org/")
                .build()
            val html = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return null

            val pattern = Pattern.compile("""href=["']((?:https?://(?:www\.)?latanime\.org)?/ver/[^/"'\s]+-episodio-$effectiveEp)["']""", Pattern.CASE_INSENSITIVE)
            val m = pattern.matcher(html)
            if (m.find()) {
                var watchUrl = m.group(1) ?: return null
                if (watchUrl.startsWith("/")) watchUrl = "https://latanime.org$watchUrl"
                return extractLatanimeWatch(watchUrl, langKey, dubLabel, isCastellano)
            }
        } catch (_: Exception) {}
        return null
    }

    private suspend fun searchLatanime(titles: List<String>, dubType: String, targetSeason: Int = 1): String? {
        val queryVariants = mutableListOf<String>()
        for (t in titles.distinct()) {
            val clean = t.replace(Regex("""(?i)\b(season\s*\d+|2nd\s*season|3rd\s*season|4th\s*season|\d+(?:st|nd|rd|th)\s+season|part\s*\d+|s\d+|temporada\s*\d+|saison\s*\d+|staffel\s*\d+|cour\s*\d+)\b"""), "")
                .replace(Regex("[:!?,._-]+"), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
            if (clean.isNotEmpty()) {
                if (dubType == "castellano") {
                    if (targetSeason > 1) {
                        queryVariants.add("$clean $targetSeason castellano")
                    }
                    queryVariants.add("$clean castellano")
                }
                if (targetSeason > 1) {
                    queryVariants.add("$clean $targetSeason")
                }
                queryVariants.add(clean)
            }
        }
        if (queryVariants.isEmpty()) {
            val first = titles.firstOrNull() ?: return null
            queryVariants.add(first)
        }

        var bestSlug: String? = null
        var bestScore = -999

        val cardPattern = Pattern.compile(
            """href=["']https?://(?:www\.)?latanime\.org/anime/([^/"'\s]+)/?["'].*?<h3[^>]*>(.*?)</h3>""",
            Pattern.DOTALL or Pattern.CASE_INSENSITIVE
        )
        val fallbackPattern = Pattern.compile(
            """href=["']https?://(?:www\.)?latanime\.org/anime/([^/"'\s]+)/?["']""",
            Pattern.CASE_INSENSITIVE
        )

        val fastLatanimeClient = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        for (q in queryVariants.distinct().take(4)) {
            try {
                val url = "https://latanime.org/buscar?q=${URLEncoder.encode(q, "UTF-8")}"
                val req = Request.Builder()
                    .url(url)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", "https://latanime.org/")
                    .build()
                val html = fastLatanimeClient.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: continue

                val mCard = cardPattern.matcher(html)
                var cardMatched = false
                while (mCard.find()) {
                    cardMatched = true
                    val slug = mCard.group(1) ?: continue
                    val cardTitle = mCard.group(2) ?: ""
                    val isCastellano = slug.contains("-castellano", ignoreCase = true) || cardTitle.contains("castellano", ignoreCase = true)
                    val isLatino = slug.contains("-latino", ignoreCase = true) || slug.contains("-audio-latino", ignoreCase = true) || cardTitle.contains("latino", ignoreCase = true)
                    val matchesDub = if (dubType == "castellano") isCastellano else isLatino
                    if (matchesDub) {
                        val score = scoreSeasonMatch(slug, cardTitle, targetSeason)
                        if (score > bestScore && score > 0) {
                            bestScore = score
                            bestSlug = slug
                            if (score >= 100) return bestSlug
                        }
                    }
                }

                if (!cardMatched) {
                    val m = fallbackPattern.matcher(html)
                    while (m.find()) {
                        val slug = m.group(1) ?: continue
                        val isCastellano = slug.contains("-castellano", ignoreCase = true)
                        val isLatino = slug.contains("-latino", ignoreCase = true) || slug.contains("-audio-latino", ignoreCase = true)
                        val matchesDub = if (dubType == "castellano") isCastellano else isLatino
                        if (matchesDub) {
                            val score = scoreSeasonMatch(slug, null, targetSeason)
                            if (score > bestScore && score > 0) {
                                bestScore = score
                                bestSlug = slug
                                if (score >= 100) return bestSlug
                            }
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        return if (bestScore > 0) bestSlug else null
    }

    private fun resolveLatinoSlug(anilistId: Int, animeTitle: String?, targetSeason: Int = 1): String? {
        val clean = animeTitle?.lowercase()?.replace(Regex("[^a-z0-9]+"), "-")?.trim('-') ?: return null
        return if (targetSeason > 1 && !clean.contains("s$targetSeason") && !clean.contains("temporada-$targetSeason")) {
            "$clean-s$targetSeason-latino"
        } else if (!clean.endsWith("-latino") && !clean.endsWith("-audio-latino")) {
            "$clean-latino"
        } else {
            clean
        }
    }

    private fun resolveCastellanoSlug(anilistId: Int, animeTitle: String?, targetSeason: Int = 1): String? {
        val clean = animeTitle?.lowercase()?.replace(Regex("[^a-z0-9]+"), "-")?.trim('-') ?: return null
        return if (targetSeason > 1 && !clean.contains("s$targetSeason") && !clean.contains("temporada-$targetSeason")) {
            "$clean-s$targetSeason-castellano"
        } else if (!clean.endsWith("-castellano")) {
            "$clean-castellano"
        } else {
            clean
        }
    }

    private suspend fun extractLatanimeWatch(watchUrl: String, langKey: String, dubLabel: String, isCastellano: Boolean): MultiLanguageStreamResult? {
        val serverPrefix = if (isCastellano) "Eclipse" else "Neptune"
        try {
            val req = Request.Builder()
                .url(watchUrl)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .addHeader("Referer", "https://latanime.org/")
                .build()

            val html = client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return null

            // Find all data-player base64 attributes
            val playerPattern = Pattern.compile("""data-player=["']([A-Za-z0-9+/=]+)["']""")
            val m = playerPattern.matcher(html)
            val embedList = mutableListOf<String>()
            while (m.find()) {
                val b64 = m.group(1) ?: continue
                try {
                    val decoded = String(Base64.decode(b64, Base64.DEFAULT)).trim()
                    if (decoded.startsWith("http")) {
                        embedList.add(decoded)
                    }
                } catch (_: Exception) {}
            }

            // Filter out known junk/ad embed hosts
            val directEmbedCandidates = embedList.filter { u ->
                val l = u.lowercase()
                !l.contains("mega.nz") && !l.contains("dsvplay") && !l.contains("hexload")
            }

            // Sort: prioritize Savefiles & Byse (fast, unblocked 1080p HLS), followed by VOE, YourUpload, Filemoon, Uqload, OK.ru, Mixdrop, Mp4Upload
            val sortedCandidates = directEmbedCandidates.sortedBy { u ->
                val l = u.lowercase()
                when {
                    l.contains("savefiles") -> 0
                    l.contains("byse") || l.contains("gn1r5n") || l.contains("mfw") -> 1
                    l.contains("voe.sx") || l.contains("shanelocaldog") || l.contains("mojon.latanime.org") || (l.contains("voe") && !l.contains("yourupload")) -> 2
                    l.contains("yourupload") -> 3
                    l.contains("filemoon") -> 4
                    l.contains("uqload") -> 5
                    l.contains("ok.ru") -> 6
                    l.contains("mixdrop") || l.contains("mxdrop") -> 7
                    l.contains("mp4upload") -> 8
                    l.contains(".m3u8") -> 9
                    else -> 10
                }
            }

            val hlsResults = mutableListOf<Pair<String, String>>()
            val mp4Results = mutableListOf<Pair<String, String>>()

            for (embed in sortedCandidates) {
                val streamRes = unpackLatanimeEmbed(embed, serverPrefix)
                if (streamRes != null && streamRes.first.isNotEmpty()) {
                    if (streamRes.first.contains(".m3u8")) {
                        hlsResults.add(streamRes)
                    } else if (streamRes.first.contains(".mp4") || streamRes.first.contains("mixdrop") || streamRes.first.contains("mxcontent") || streamRes.first.contains("okcdn.ru")) {
                        mp4Results.add(streamRes)
                    }
                }
            }

            // Strictly prefer reliable HLS first (Savefiles/Byse/VOE 1080p master), with MP4 as backup
            val chosen = hlsResults.firstOrNull() ?: mp4Results.firstOrNull()
            if (chosen != null) {
                val backup = if (chosen in hlsResults) (hlsResults.drop(1).firstOrNull() ?: mp4Results.firstOrNull()) else hlsResults.firstOrNull()
                val effectiveRef = when {
                    chosen.first.contains("yourupload", ignoreCase = true) -> "https://www.yourupload.com/"
                    chosen.first.contains("uqload", ignoreCase = true) -> "https://uqload.ws/"
                    chosen.first.contains("savefiles", ignoreCase = true) -> "https://savefiles.com/"
                    chosen.first.contains("mixdrop", ignoreCase = true) || chosen.first.contains("mxcontent", ignoreCase = true) -> "https://mixdrop.top/"
                    chosen.first.contains("ok.ru", ignoreCase = true) || chosen.first.contains("okcdn.ru", ignoreCase = true) -> "https://ok.ru/"
                    else -> watchUrl
                }
                return MultiLanguageStreamResult(
                    hlsUrl = chosen.first,
                    referer = effectiveRef,
                    provider = chosen.second,
                    language = langKey,
                    dubName = dubLabel,
                    backupHlsUrl = backup?.first ?: "",
                    backupProvider = backup?.second ?: ""
                )
            }
        } catch (_: Exception) {}
        return null
    }

    private suspend fun unpackLatanimeEmbed(embedUrl: String, serverPrefix: String): Pair<String, String>? {
        val lower = embedUrl.lower()
        try {
            // 1. Byse resolution (High quality 1080p HLS)
            if (lower.contains("byse") || lower.contains("gn1r5n") || lower.contains("mfw") || ByseExtractor.canExtract(embedUrl)) {
                val byseRes = ByseExtractor.extract(embedUrl, "https://latanime.org/")
                if (byseRes.isNotEmpty()) {
                    return Pair(byseRes.first(), "$serverPrefix HLS")
                }
            }

            // 2. VOE resolution (direct voe.sx, redirect domains, or mojon/aqua proxy)
            if (lower.contains("voe.sx") || lower.contains("shanelocaldog") || lower.contains("mojon.latanime.org") || (lower.contains("voe") && !lower.contains("yourupload"))) {
                val voeTarget = if (embedUrl.contains("url=")) {
                    embedUrl.substringAfter("url=").substringBefore("&")
                } else embedUrl
                val voeRes = resolveVoeStream(voeTarget)
                if (voeRes != null && voeRes.first.isNotEmpty()) {
                    return Pair(voeRes.first, "$serverPrefix HLS")
                }
            }

            // 3. Savefiles -> 1080p master .m3u8 playlist
            if (lower.contains("savefiles")) {
                val fileCode = embedUrl.substringAfterLast("/").substringBefore(".html").trim()
                val formBody = okhttp3.FormBody.Builder()
                    .add("op", "embed")
                    .add("file_code", fileCode)
                    .add("auto", "1")
                    .add("referer", "https://latanime.org/")
                    .build()
                val postReq = Request.Builder()
                    .url("https://savefiles.com/dl")
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", embedUrl)
                    .addHeader("Origin", "https://savefiles.com")
                    .post(formBody)
                    .build()
                val postHtml = client.newCall(postReq).execute().use { if (it.isSuccessful) it.body?.string() else null }
                if (postHtml != null) {
                    val m = Pattern.compile("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""").matcher(postHtml)
                    if (m.find()) return Pair(m.group(0)!!, "$serverPrefix HLS")
                }
            }

            // 3b. YourUpload -> direct high-speed MP4
            if (lower.contains("yourupload")) {
                try {
                    val req = Request.Builder()
                        .url(embedUrl)
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("Referer", "https://latanime.org/")
                        .build()
                    val html = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
                    if (html != null) {
                        val m = Pattern.compile("""og:video"\s*content="([^"]+)"""").matcher(html)
                        val mAlt = if (!m.find()) Pattern.compile("""property="og:video"\s*content="([^"]+)"""").matcher(html) else null
                        val videoUrl = if (m.find()) m.group(1) else if (mAlt != null && mAlt.find()) mAlt.group(1) else null
                        if (!videoUrl.isNullOrEmpty() && !videoUrl.contains("novideo")) {
                            return Pair(videoUrl, "$serverPrefix MP4")
                        }
                    }
                } catch (_: Exception) {}
            }

            // 4. Filemoon direct / packed unpacking
            if (lower.contains("filemoon")) {
                try {
                    val req = Request.Builder()
                        .url(embedUrl)
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("Referer", "https://latanime.org/")
                        .build()
                    val html = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
                    if (html != null) {
                        val mDirect = Pattern.compile("""['"](https?://[^\s'"]+\.m3u8[^\s'"]*)['"]""").matcher(html)
                        if (mDirect.find()) {
                            return Pair(mDirect.group(1)!!, "$serverPrefix HLS")
                        }
                        val mPacker = Pattern.compile("""eval\(function\(p,a,c,k,e,d\)\{.*?\}\((.*?)\)\)""", Pattern.DOTALL).matcher(html)
                        if (mPacker.find()) {
                            val unpacked = unpackPacker(mPacker.group(1) ?: "")
                            val mUnpackedM3u8 = Pattern.compile("""(?:file|src)\s*:\s*['"](https?://[^'"]+\.m3u8[^'"]*)['"]""").matcher(unpacked)
                            if (mUnpackedM3u8.find()) {
                                return Pair(mUnpackedM3u8.group(1)!!, "$serverPrefix HLS")
                            }
                            val mUnpackedAny = Pattern.compile("""['"](https?://[^\s'"]+\.m3u8[^\s'"]*)['"]""").matcher(unpacked)
                            if (mUnpackedAny.find()) {
                                return Pair(mUnpackedAny.group(1)!!, "$serverPrefix HLS")
                            }
                        }
                    }
                } catch (_: Exception) {}
            }

            // 2. Uqload -> direct stream (HLS or MP4)
            if (lower.contains("uqload")) {
                val req = Request.Builder()
                    .url(embedUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", "https://latanime.org/")
                    .build()
                val html = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
                if (html != null) {
                    val m3u8 = Pattern.compile("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""").matcher(html)
                    if (m3u8.find()) return Pair(m3u8.group(0)!!, "$serverPrefix HLS")
                    val mp4 = Pattern.compile("""https?://[^\s"'<>]+\.mp4[^\s"'<>]*""").matcher(html)
                    if (mp4.find()) return Pair(mp4.group(0)!!, serverPrefix)

                    // Uqload packed JavaScript decoder
                    val unpacked = unpackUqload(html)
                    if (unpacked.isNotEmpty()) {
                        val unpM3u8 = Pattern.compile("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""").matcher(unpacked)
                        if (unpM3u8.find()) return Pair(unpM3u8.group(0)!!, "$serverPrefix HLS")
                        val unpMp4 = Pattern.compile("""https?://[^\s"'<>]+\.mp4[^\s"'<>]*""").matcher(unpacked)
                        if (unpMp4.find()) return Pair(unpMp4.group(0)!!, serverPrefix)
                    }
                }
            }

            // 3. Mixdrop -> direct MP4 extraction
            if (lower.contains("mixdrop") || lower.contains("mxdrop")) {
                val req = Request.Builder()
                    .url(embedUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", "https://latanime.org/")
                    .build()
                val html = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
                if (html != null) {
                    val m = Pattern.compile("""wurl=["']([^"']+)["']""").matcher(html)
                    if (m.find()) {
                        var delivery = m.group(1)!!
                        if (delivery.startsWith("//")) delivery = "https:$delivery"
                        return Pair(delivery, serverPrefix)
                    }
                    val mPacker = Pattern.compile("""eval\(function\(p,a,c,k,e,d\)\{.*?\}\((.*?)\)\)""", Pattern.DOTALL).matcher(html)
                    if (mPacker.find()) {
                        val unpacked = unpackPacker(mPacker.group(1) ?: "")
                        val mDelivery = Pattern.compile("""wurl=["']([^"']+)["']""").matcher(unpacked)
                        if (mDelivery.find()) {
                            var delivery = mDelivery.group(1)!!
                            if (delivery.startsWith("//")) delivery = "https:$delivery"
                            return Pair(delivery, serverPrefix)
                        }
                    }
                }
            }

            // 4. Mp4Upload -> direct MP4
            if (lower.contains("mp4upload")) {
                val req = Request.Builder()
                    .url(embedUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", "https://latanime.org/")
                    .build()
                val html = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
                if (html != null) {
                    val m = Pattern.compile("""(?:file|src)\s*[:=]\s*["'](https?://[^"']+\.mp4[^"']*)["']""").matcher(html)
                    if (m.find()) return Pair(m.group(1)!!, "$serverPrefix MP4")
                }
            }

            // 5. OK.ru (Odnoklassniki) -> direct video extraction (HLS or direct MP4)
            if (lower.contains("ok.ru")) {
                val okReq = Request.Builder()
                    .url(embedUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", "https://ok.ru/")
                    .build()
                val okHtml = client.newCall(okReq).execute().use { if (it.isSuccessful) it.body?.string() else null }
                if (okHtml != null) {
                    val optsMatch = Pattern.compile("""data-options=["'](\{.*?\})["']""").matcher(okHtml)
                    if (optsMatch.find()) {
                        val unescaped = optsMatch.group(1)!!.replace("&quot;", "\"").replace("&amp;", "&")
                        val opts = try { JSONObject(unescaped) } catch (_: Exception) { null }
                        val flashvars = opts?.optJSONObject("flashvars")
                        val metaObj = flashvars?.optJSONObject("metadata") ?: flashvars?.optString("metadata")?.let {
                            if (it.isNotEmpty()) try { JSONObject(it) } catch (_: Exception) { null } else null
                        }

                        if (metaObj != null) {
                            val videos = metaObj.optJSONArray("videos")
                            if (videos != null && videos.length() > 0) {
                                var bestUrl: String? = null
                                val prefQualities = listOf("full", "hd", "sd", "low", "lowest", "mobile")
                                for (pref in prefQualities) {
                                    for (i in 0 until videos.length()) {
                                        val v = videos.getJSONObject(i)
                                        if (v.optString("name").equals(pref, ignoreCase = true)) {
                                            bestUrl = v.optString("url")
                                            break
                                        }
                                    }
                                    if (!bestUrl.isNullOrEmpty()) break
                                }
                                if (bestUrl != null && bestUrl.isNotEmpty()) {
                                    return Pair(bestUrl, serverPrefix)
                                }
                            }
                            val hlsMaster = metaObj.optString("hlsMasterPlaylistUrl")
                            if (hlsMaster.isNotEmpty()) {
                                return Pair(hlsMaster, "$serverPrefix HLS")
                            }
                        }
                    }

                    val okM3u8 = Pattern.compile("""https?://[^\s"\'<>&]+\.m3u8[^\s"\'<>&]*""").matcher(okHtml.replace("&amp;", "&"))
                    if (okM3u8.find()) {
                        return Pair(okM3u8.group(0)!!.replace("\\u0026", "&"), "$serverPrefix HLS")
                    }
                }
            }

            // 6. Direct .m3u8 / .mp4 check
            if (lower.contains(".m3u8")) {
                return Pair(embedUrl, "$serverPrefix HLS")
            }
            if (lower.contains(".mp4")) {
                return Pair(embedUrl, serverPrefix)
            }

            // 7. Cloudstream registered ExtractorApi fallback (Filemoon, etc.)
            try {
                var foundLink: Pair<String, String>? = null
                com.lagradost.cloudstream3.utils.loadExtractor(
                    url = embedUrl,
                    referer = "https://latanime.org/",
                    subtitleCallback = {}
                ) { link ->
                    if (foundLink == null && link.url.isNotEmpty()) {
                        val isHls = link.url.contains(".m3u8")
                        foundLink = Pair(link.url, if (isHls) "$serverPrefix HLS" else serverPrefix)
                    }
                }
                if (foundLink != null) return foundLink
            } catch (_: Exception) {}
        } catch (_: Exception) {}
        return null
    }

    private fun unpackPacker(args: String): String {
        return try {
            val pMatch = Pattern.compile("""["'](.*?)["'],(\d+),(\d+),["'](.*?)["']\.split""").matcher(args)
            if (!pMatch.find()) return ""
            val p = pMatch.group(1) ?: ""
            val a = pMatch.group(2)?.toIntOrNull() ?: 36
            val c = pMatch.group(3)?.toIntOrNull() ?: 0
            val k = (pMatch.group(4) ?: "").split("|")

            fun e(cVal: Int): String {
                val digits = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
                return if (cVal < a) {
                    if (cVal > 35) (cVal + 29).toChar().toString() else digits[cVal].toString()
                } else {
                    e(cVal / a) + (if (cVal % a > 35) (cVal % a + 29).toChar().toString() else digits[cVal % a].toString())
                }
            }

            val kDict = mutableMapOf<String, String>()
            for (i in 0 until c) {
                val key = e(i)
                val value = if (i < k.size && k[i].isNotEmpty()) k[i] else key
                kDict[key] = value
            }

            val wordPattern = Pattern.compile("""\b\w+\b""")
            val matcher = wordPattern.matcher(p)
            val sb = StringBuffer()
            while (matcher.find()) {
                val w = matcher.group(0) ?: ""
                matcher.appendReplacement(sb, MatcherQuote(kDict[w] ?: w))
            }
            matcher.appendTail(sb)
            sb.toString()
        } catch (_: Exception) { "" }
    }

    private fun MatcherQuote(s: String): String = java.util.regex.Matcher.quoteReplacement(s)

    private fun unpackUqload(html: String): String {
        return try {
            val pattern = Pattern.compile("""eval\(function\(p,a,c,k,e,d\)\{while\(c--\).*?return p\}\('(.*?)',(\d+),(\d+),'(.*?)'\.split\('\|'\)\)\)""", Pattern.DOTALL)
            val m = pattern.matcher(html)
            if (!m.find()) return ""
            val p = m.group(1) ?: return ""
            val a = m.group(2)?.toIntOrNull() ?: 36
            val c = m.group(3)?.toIntOrNull() ?: 0
            val k = (m.group(4) ?: "").split("|")

            fun int2base(x: Int, base: Int): String {
                val digs = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
                if (x == 0) return "0"
                var temp = x
                val sb = StringBuilder()
                while (temp > 0) {
                    sb.append(digs[temp % base])
                    temp /= base
                }
                return sb.reverse().toString()
            }

            val replacements = mutableMapOf<String, String>()
            for (i in 0 until c) {
                val key = int2base(i, a)
                val value = if (i < k.size && k[i].isNotEmpty()) k[i] else key
                replacements[key] = value
            }

            val wordPattern = Pattern.compile("""\b\w+\b""")
            val wMatcher = wordPattern.matcher(p)
            val sb = StringBuffer()
            while (wMatcher.find()) {
                val word = wMatcher.group(0) ?: ""
                val rep = replacements[word] ?: word
                wMatcher.appendReplacement(sb, MatcherQuote(rep))
            }
            wMatcher.appendTail(sb)
            sb.toString()
        } catch (_: Exception) { "" }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 3b. SoloLatino Backup for Spanish Latino (Solar System name: Mercury)
    // ─────────────────────────────────────────────────────────────────────────────
    val KNOWN_SOLOLATINO_SLUG_MAP = mapOf(
        249 to "inuyasha",
        101922 to "demon-slayer-kimetsu-no-yaiba",
        129874 to "demon-slayer-kimetsu-no-yaiba",
        142329 to "demon-slayer-kimetsu-no-yaiba",
        145139 to "demon-slayer-kimetsu-no-yaiba",
        166240 to "demon-slayer-kimetsu-no-yaiba",
        20 to "naruto",
        1735 to "naruto-shippuden",
        16498 to "shingeki-no-kyojin",
        20958 to "shingeki-no-kyojin",
        99147 to "shingeki-no-kyojin",
        104578 to "shingeki-no-kyojin",
        110277 to "shingeki-no-kyojin",
        131681 to "shingeki-no-kyojin",
        163142 to "shingeki-no-kyojin",
        108465 to "mushoku-tensei-jobless-reincarnation",
        127720 to "mushoku-tensei-jobless-reincarnation",
        146065 to "mushoku-tensei-jobless-reincarnation",
        166873 to "mushoku-tensei-jobless-reincarnation",
        178789 to "mushoku-tensei-jobless-reincarnation",
        21 to "one-piece",
        269 to "bleach",
        30984 to "bleach",
        1535 to "death-note",
        20605 to "tokyo-ghoul",
        101347 to "jujutsu-kaisen",
        145064 to "jujutsu-kaisen",
        127230 to "chainsaw-man",
        151807 to "solo-leveling",
        154587 to "frieren",
        182896 to "my-hero-academia",
        163139 to "my-hero-academia",
        139630 to "my-hero-academia",
        117193 to "my-hero-academia",
        184951 to "dragon-ball-daima",
        34747 to "dragon-ball",
        12697 to "dragon-ball-z",
        60572 to "pokemon"
    )

    val TMDB_SOLOLATINO_SLUG_MAP = mapOf(
        35610 to "inuyasha",
        85937 to "demon-slayer-kimetsu-no-yaiba",
        37854 to "one-piece",
        46260 to "naruto",
        31910 to "naruto-shippuden",
        30984 to "bleach",
        60572 to "pokemon",
        34747 to "dragon-ball",
        12697 to "dragon-ball-z",
        80609 to "kiteretsu",
        132791 to "perman",
        80885 to "ninja-hattori",
        44872 to "idaten-jump",
        1429 to "shingeki-no-kyojin",
        95479 to "jujutsu-kaisen",
        114410 to "chainsaw-man",
        127532 to "solo-leveling",
        106379 to "frieren",
        65930 to "my-hero-academia",
        94664 to "mushoku-tensei-jobless-reincarnation"
    )

    private suspend fun resolveSoloLatinoSlug(anilistId: Int, animeTitle: String?): String? {
        KNOWN_SOLOLATINO_SLUG_MAP[anilistId]?.let { return it }

        try {
            val anizipUrl = "https://api.ani.zip/mappings?anilist_id=$anilistId"
            val fastZip = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .build()
            val req = Request.Builder().url(anizipUrl).addHeader("User-Agent", USER_AGENT).build()
            val respStr = fastZip.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
            if (!respStr.isNullOrEmpty()) {
                val json = JSONObject(respStr)
                val mappings = json.optJSONObject("mappings")
                val tmdbId = mappings?.optString("themoviedb_id")?.toIntOrNull()
                if (tmdbId != null && TMDB_SOLOLATINO_SLUG_MAP.containsKey(tmdbId)) {
                    return TMDB_SOLOLATINO_SLUG_MAP[tmdbId]
                }
                val anidbTitle = json.optJSONObject("titles")?.optString("en")
                if (!anidbTitle.isNullOrEmpty()) {
                    val slug = anidbTitle.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
                    if (slug.isNotEmpty()) return slug
                }
            }
        } catch (_: Exception) {}

        val clean = animeTitle?.lowercase()
            ?.replace(Regex("""(?i)\b(season\s*\d+|2nd\s*season|3rd\s*season|4th\s*season|\d+(?:st|nd|rd|th)\s+season|part\s*\d+|s\d+|temporada\s*\d+|saison\s*\d+|staffel\s*\d+|cour\s*\d+)\b"""), "")
            ?.replace(Regex("[^a-z0-9]+"), "-")?.trim('-')
        return if (!clean.isNullOrEmpty()) clean else null
    }

    private suspend fun extractSoloLatino(
        anilistId: Int,
        episodeNum: Int,
        animeTitle: String?,
        targetSeason: Int = 1
    ): MultiLanguageStreamResult? {
        try {
            val slug = resolveSoloLatinoSlug(anilistId, animeTitle) ?: return null
            val offset = KNOWN_LATINO_OFFSETS[anilistId] ?: 0
            val effectiveEp = episodeNum + offset

            val candidates = mutableListOf<String>()
            candidates.add("https://sololatino.net/serie/$slug/temporada-$targetSeason/episodio-$effectiveEp")
            if (targetSeason > 1) {
                candidates.add("https://sololatino.net/serie/$slug/temporada-1/episodio-$effectiveEp")
            }

            val soloFastClient = AnimeBoxDnsHelper.applyDns(OkHttpClient.Builder())
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .build()

            for (targetUrl in candidates) {
                val req = Request.Builder()
                    .url(targetUrl)
                    .addHeader("User-Agent", USER_AGENT)
                    .addHeader("Referer", "https://sololatino.net/")
                    .build()
                val pageHtml = try {
                    app.get(targetUrl, headers = mapOf("Referer" to "https://sololatino.net/")).text
                } catch (_: Exception) {
                    try {
                        soloFastClient.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null }
                    } catch (_: Exception) { null }
                } ?: continue

                val embedUrls = mutableListOf<String>()
                val ifrMatcher = Pattern.compile("""<iframe[^>]+src=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(pageHtml)
                while (ifrMatcher.find()) {
                    val src = ifrMatcher.group(1) ?: continue
                    if (src.startsWith("//")) embedUrls.add("https:$src")
                    else if (src.startsWith("http")) embedUrls.add(src)
                }

                val optMatcher = Pattern.compile("""data-(?:src|url|video)=["']([^"']+)["']""", Pattern.CASE_INSENSITIVE).matcher(pageHtml)
                while (optMatcher.find()) {
                    val s = optMatcher.group(1) ?: continue
                    if (s.startsWith("//")) embedUrls.add("https:$s")
                    else if (s.startsWith("http")) embedUrls.add(s)
                }

                val hlsResults = mutableListOf<Pair<String, String>>()
                val mp4Results = mutableListOf<Pair<String, String>>()

                for (embed in embedUrls.distinct().take(3)) {
                    val streamRes = unpackLatanimeEmbed(embed, "Mercury")
                    if (streamRes != null && streamRes.first.isNotEmpty()) {
                        if (streamRes.first.contains(".m3u8")) {
                            hlsResults.add(streamRes)
                        } else if (streamRes.first.contains(".mp4") || streamRes.first.contains("mixdrop") || streamRes.first.contains("mxcontent") || streamRes.first.contains("okcdn.ru")) {
                            mp4Results.add(streamRes)
                        }
                    }
                }

                val chosen = mp4Results.firstOrNull() ?: hlsResults.firstOrNull()
                if (chosen != null) {
                    val backup = if (chosen in mp4Results) hlsResults.firstOrNull() else mp4Results.firstOrNull()
                    val effectiveRef = when {
                        chosen.first.contains("uqload", ignoreCase = true) -> "https://uqload.ws/"
                        chosen.first.contains("savefiles", ignoreCase = true) -> "https://savefiles.com/"
                        chosen.first.contains("mixdrop", ignoreCase = true) || chosen.first.contains("mxcontent", ignoreCase = true) -> "https://mixdrop.top/"
                        chosen.first.contains("ok.ru", ignoreCase = true) || chosen.first.contains("okcdn.ru", ignoreCase = true) -> "https://ok.ru/"
                        else -> targetUrl
                    }
                    return MultiLanguageStreamResult(
                        hlsUrl = chosen.first,
                        referer = effectiveRef,
                        provider = "Mercury",
                        language = "spanish_latino",
                        dubName = "Spanish (Latino)",
                        backupHlsUrl = backup?.first ?: "",
                        backupProvider = if (backup != null) "Mercury Backup" else ""
                    )
                }
            }
        } catch (_: Exception) {}
        return null
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // 5. German Dub via FireAnime (Connect RPC) (Anime-inspired name: Nexus / Bifrost)
    // ─────────────────────────────────────────────────────────────────────────────
    private suspend fun extractGerman(anilistId: Int, episodeNum: Int, animeTitle: String?): MultiLanguageStreamResult? {
        val mapping = resolveGermanMapping(anilistId, animeTitle) ?: return null
        val (slug, seasonNum) = mapping
        val epOffset = KNOWN_GERMAN_OFFSETS[anilistId] ?: 0
        val effectiveEp = episodeNum + epOffset

        try {
            val url = "https://fireani.me/api.v1.anime.AnimeService/GetEpisode"
            val payload = JSONObject().apply {
                put("slug", slug)
                put("season", seasonNum.toString())
                put("episode", effectiveEp.toString())
            }.toString()

            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Connect-Protocol-Version", "1")
                .addHeader("Referer", "https://fireani.me/")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            val respStr = client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return null

            val root = JSONObject(respStr)
            val data = root.optJSONObject("data") ?: return null
            val rawLinks = data.optJSONArray("animeEpisodeLinks") ?: return null

            // Prioritize German Dub, fallback to German Sub
            val dubLinks = mutableListOf<JSONObject>()
            val subLinks = mutableListOf<JSONObject>()

            for (i in 0 until rawLinks.length()) {
                val item = rawLinks.getJSONObject(i)
                val lang = item.optString("lang", "").lowercase()
                if (lang == "ger-dub") dubLinks.add(item)
                else if (lang == "ger-sub") subLinks.add(item)
            }

            val targetList = if (dubLinks.isNotEmpty()) dubLinks else subLinks

            var hlsResult: MultiLanguageStreamResult? = null
            var mp4Result: MultiLanguageStreamResult? = null

            // 1) First look for VOE / ProxyPlayer links -> direct master.m3u8 HLS (Bifrost)
            val voeLinks = targetList.filter {
                val n = it.optString("name")
                val l = it.optString("link")
                n.equals("VOE", ignoreCase = true) || l.contains("voe.sx") || l.contains("/e/") || (l.contains("/embed?") && l.contains("id="))
            }

            for (voeObj in voeLinks) {
                val rawLink = voeObj.optString("link")
                val effectiveVoeLink = if (rawLink.contains("/embed?") && rawLink.contains("id=")) {
                    val voeId = Pattern.compile("""id=([a-zA-Z0-9_-]+)""").matcher(rawLink).let { if (it.find()) it.group(1) else "" }
                    if (voeId.isNotEmpty()) "https://voe.sx/e/$voeId" else rawLink
                } else rawLink

                if (effectiveVoeLink.isNotEmpty()) {
                    val voeRes = resolveVoeStream(effectiveVoeLink)
                    if (voeRes != null && voeRes.first.isNotEmpty()) {
                        hlsResult = MultiLanguageStreamResult(
                            hlsUrl = voeRes.first,
                            referer = voeRes.second,
                            provider = "Bifrost",
                            language = "german",
                            dubName = "German (Deutsch)"
                        )
                        break
                    }
                }
            }

            // 2) Look for CookieStream -> direct high-speed CDN MP4 (Nexus)
            val cookieLinks = targetList.filter {
                val n = it.optString("name")
                val l = it.optString("link")
                n.equals("CookieStream", ignoreCase = true) || l.contains("cookiestream")
            }

            for (cookieObj in cookieLinks) {
                val link = cookieObj.optString("link")
                if (link.isNotEmpty()) {
                    val cookieReq = Request.Builder()
                        .url(link)
                        .addHeader("User-Agent", USER_AGENT)
                        .addHeader("Referer", "https://fireani.me/")
                        .build()

                    val loc = noRedirectClient.newCall(cookieReq).execute().use { r ->
                        if (r.code in 300..399) r.header("Location") else null
                    }
                    if (!loc.isNullOrEmpty() && (loc.contains(".mp4") || loc.contains(".m3u8") || loc.contains("tiktokcdn.com"))) {
                        mp4Result = MultiLanguageStreamResult(
                            hlsUrl = loc,
                            referer = "https://fireani.me/",
                            provider = "Nexus",
                            language = "german",
                            dubName = "German (Deutsch)"
                        )
                        break
                    }
                }
            }

            if (hlsResult != null) {
                return hlsResult.copy(
                    backupHlsUrl = mp4Result?.hlsUrl ?: "",
                    backupProvider = mp4Result?.provider ?: ""
                )
            } else if (mp4Result != null) {
                return mp4Result
            }
        } catch (_: Exception) {}
        return null
    }

    internal suspend fun resolveVoeStream(rawLink: String): Pair<String, String>? {
        try {
            var currentUrl = rawLink
            val req = Request.Builder()
                .url(currentUrl)
                .addHeader("User-Agent", USER_AGENT)
                .build()

            var html = client.newCall(req).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: return null

            // Follow window.location.href redirects up to 3 times
            var hops = 0
            while (hops < 3) {
                hops++
                val redMatch = Pattern.compile("""window\.location\.href\s*=\s*['"]([^'"]+)['"]""").matcher(html)
                if (redMatch.find()) {
                    currentUrl = redMatch.group(1)!!
                    val redReq = Request.Builder()
                        .url(currentUrl)
                        .addHeader("User-Agent", USER_AGENT)
                        .build()
                    html = client.newCall(redReq).execute().use { if (it.isSuccessful) it.body?.string() else null } ?: break
                } else {
                    break
                }
            }

            // Direct m3u8 in html
            val m3u8Match = Pattern.compile("""['"](https?://[^\s'"]+\.m3u8[^\s'"]*)['"]""").matcher(html)
            if (m3u8Match.find()) {
                val m = m3u8Match.group(1)!!
                if (!m.contains("sample", ignoreCase = true)) return Pair(m, currentUrl)
            }

            // Script payload: <script>["..."]</script> (Using [^"]+ to match all obfuscated delimiter symbols)
            val scriptMatch = Pattern.compile("""<script[^>]*>\s*\["([^"]+)"\]\s*</script>""").matcher(html)
            if (scriptMatch.find()) {
                val payload = scriptMatch.group(1) ?: ""
                val decoded = decodeVoePayload(payload)
                if (!decoded.isNullOrEmpty()) return Pair(decoded, currentUrl)
            }

            // Alternate hls json
            val hlsMatch = Pattern.compile("""['"]hls['"]\s*:\s*['"]([^'"]+)['"]""").matcher(html)
            if (hlsMatch.find()) {
                val hls = hlsMatch.group(1)
                if (!hls.isNullOrEmpty()) {
                    return Pair(hls, currentUrl)
                }
            }
        } catch (_: Exception) {}
        return null
    }

    internal fun decodeVoePayload(payload: String): String? {
        try {
            // 1. ROT13
            val rot13 = buildString {
                for (c in payload) {
                    when (c) {
                        in 'a'..'m', in 'A'..'M' -> append(c + 13)
                        in 'n'..'z', in 'N'..'Z' -> append(c - 13)
                        else -> append(c)
                    }
                }
            }

            // 2. Strip delimiters: @$, ^^, ~@, %?, *~, !!, #&
            var stripped = rot13
            val delims = listOf("@$", "^^", "~@", "%?", "*~", "!!", "#&")
            for (d in delims) {
                stripped = stripped.replace(d, "")
            }

            // 3. Base64 decode bytes
            val b3 = Base64.decode(stripped, Base64.DEFAULT)

            // 4. Shift charcode - 3
            val s4 = buildString {
                for (b in b3) {
                    append(((b.toInt() and 0xFF) - 3).toChar())
                }
            }

            // 5. Reverse string
            val s5 = s4.reversed()

            // 6. Base64 decode utf-8
            val b6 = Base64.decode(s5, Base64.DEFAULT)
            val jsonText = String(b6, Charsets.UTF_8)
            val obj = JSONObject(jsonText)

            val source = obj.optString("source")
            if (source.isNotEmpty() && (source.contains(".m3u8") || source.startsWith("http"))) {
                return source
            }
            val directAccess = obj.optString("direct_access_url")
            if (directAccess.isNotEmpty()) return directAccess
            val direct = obj.optString("direct_link")
            if (direct.isNotEmpty()) return direct
        } catch (_: Exception) {}
        return null
    }

    private suspend fun resolveGermanMapping(anilistId: Int, animeTitle: String?): Pair<String, Int>? {
        KNOWN_GERMAN_MAPPINGS[anilistId]?.let { return it }
        germanCache[anilistId]?.let { return it }

        val targetSeason = resolveSeasonNumber(anilistId, animeTitle)
        val titles = resolveTitleVariants(anilistId, animeTitle)
        if (titles.isEmpty()) return null

        val searchQueries = mutableListOf<String>()
        val coreTokens = mutableListOf<String>()

        fun addQuery(q: String) {
            val trimmed = q.trim()
            if (trimmed.length >= 3 && !searchQueries.contains(trimmed)) {
                searchQueries.add(trimmed)
            }
        }

        for (rawTitle in titles) {
            var primaryTitle = rawTitle.split(Regex(""":\s+|\s+-\s*|[(\[]""")).firstOrNull()?.trim() ?: rawTitle
            primaryTitle = primaryTitle.replace(Regex("""(?i)\b(season\s*\d+|2nd\s*season|3rd\s*season|4th\s*season|\d+(?:st|nd|rd|th)\s+season|part\s*\d+|s\d+|temporada\s*\d+|saison\s*\d+|staffel\s*\d+|cour\s*\d+)\b"""), "").trim()
            if (primaryTitle.isEmpty()) primaryTitle = rawTitle

            if (targetSeason > 1) {
                val roman = seasonToRoman(targetSeason)
                if (roman.isNotEmpty()) addQuery("$primaryTitle $roman")
                addQuery("$primaryTitle season $targetSeason")
                addQuery("$primaryTitle $targetSeason")
                addQuery("$primaryTitle s$targetSeason")
            }
            addQuery(primaryTitle)

            if (primaryTitle.contains("re:zero", ignoreCase = true) || primaryTitle.contains("rezero", ignoreCase = true)) {
                addQuery("rezero")
                addQuery("re:zero")
            }

            val strippedFull = rawTitle.replace(Regex("""(?i)\b(season\s*\d+|2nd\s*season|3rd\s*season|4th\s*season|\d+(?:st|nd|rd|th)\s+season|part\s*\d+|s\d+|temporada\s*\d+|saison\s*\d+|staffel\s*\d+|cour\s*\d+)\b"""), "").trim()
            if (strippedFull.isNotEmpty()) {
                addQuery(strippedFull)
            }

            val toks = primaryTitle.lowercase().replace(Regex("[^a-z0-9]"), " ").split(" ")
                .filter { it.length > 2 && !it.all { c -> c.isDigit() } && it !in listOf("the", "and", "for", "with", "from", "der", "die", "das") }
            for (t in toks) {
                if (!coreTokens.contains(t)) coreTokens.add(t)
            }
        }

        val testedSlugs = mutableSetOf<String>()

        for (q in searchQueries) {
            val candidates = searchFireAnimeCandidates(q)
            for (candidateSlug in candidates) {
                if (!testedSlugs.add(candidateSlug)) continue

                val lowerSlug = candidateSlug.lowercase()
                val isRezero = (q.contains("re:zero", ignoreCase = true) || q.contains("rezero", ignoreCase = true)) && lowerSlug.contains("rezero")
                val isTokenMatch = coreTokens.isNotEmpty() && coreTokens.any { lowerSlug.contains(it) }
                if (!isRezero && !isTokenMatch) continue

                val seasons = fetchFireAnimeSeasons(candidateSlug)
                if (seasons.isNotEmpty()) {
                    if (seasons.contains(targetSeason.toString())) {
                        val result = Pair(candidateSlug, targetSeason)
                        germanCache[anilistId] = result
                        return result
                    }
                    val roman = seasonToRoman(targetSeason)
                    val isSeasonSlugMatch = candidateSlug.contains("-$targetSeason") ||
                            candidateSlug.contains("-season-$targetSeason") ||
                            candidateSlug.contains("-s$targetSeason") ||
                            (roman.isNotEmpty() && (candidateSlug.contains("-$roman") || candidateSlug.contains("-$roman-")))

                    if (targetSeason > 1 && isSeasonSlugMatch) {
                        if (seasons.contains("1")) {
                            val result = Pair(candidateSlug, 1)
                            germanCache[anilistId] = result
                            return result
                        }
                    }
                    if (targetSeason == 1 && seasons.contains("1")) {
                        val result = Pair(candidateSlug, 1)
                        germanCache[anilistId] = result
                        return result
                    }
                }
            }
        }

        // Direct candidate slug fallback
        for (rawTitle in titles) {
            var primaryTitle = rawTitle.split(Regex(""":\s+|\s+-\s*|[(\[]""")).firstOrNull()?.trim() ?: rawTitle
            primaryTitle = primaryTitle.replace(Regex("""(?i)\b(season\s*\d+|2nd\s*season|3rd\s*season|4th\s*season|\d+(?:st|nd|rd|th)\s+season|part\s*\d+|s\d+|temporada\s*\d+|saison\s*\d+|staffel\s*\d+|cour\s*\d+)\b"""), "").trim()
            val fallbackSlug = primaryTitle.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            if (fallbackSlug.isNotEmpty()) {
                val seasons = fetchFireAnimeSeasons(fallbackSlug)
                if (seasons.contains(targetSeason.toString())) {
                    val result = Pair(fallbackSlug, targetSeason)
                    germanCache[anilistId] = result
                    return result
                }
            }
        }

        return null
    }

    private suspend fun searchFireAnimeCandidates(query: String): List<String> {
        if (query.isBlank()) return emptyList()
        try {
            val url = "https://fireani.me/api.v1.AnimeSearchService/SearchAnimes"
            val payload = JSONObject().apply {
                put("q", query.trim())
                put("limit", 6)
            }.toString()

            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Connect-Protocol-Version", "1")
                .addHeader("Referer", "https://fireani.me/")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            val respStr = client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return emptyList()

            val root = JSONObject(respStr)
            val data = root.optJSONArray("data") ?: return emptyList()
            val list = mutableListOf<String>()
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val s = item.optString("slug")
                if (s.isNotEmpty() && !list.contains(s)) {
                    list.add(s)
                }
            }
            return list
        } catch (_: Exception) {}
        return emptyList()
    }

    private suspend fun fetchFireAnimeSeasons(slug: String): List<String> {
        try {
            val url = "https://fireani.me/api.v1.anime.AnimeService/GetAnime"
            val payload = JSONObject().apply {
                put("slug", slug.trim())
            }.toString()

            val req = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .addHeader("Connect-Protocol-Version", "1")
                .addHeader("Referer", "https://fireani.me/")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()

            val respStr = client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            } ?: return emptyList()

            val root = JSONObject(respStr)
            val data = root.optJSONObject("data") ?: return emptyList()
            val animeSeasons = data.optJSONArray("animeSeasons") ?: return emptyList()
            val result = mutableListOf<String>()
            for (i in 0 until animeSeasons.length()) {
                val seasonObj = animeSeasons.getJSONObject(i)
                val sNum = seasonObj.optString("season")
                if (sNum.isNotEmpty()) {
                    result.add(sNum)
                }
            }
            return result
        } catch (_: Exception) {}
        return emptyList()
    }

    private fun String.lower(): String = this.lowercase()
}
