package com.lagradost.cloudstream3.ui.animebox.extractors

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object PokemonHindiExtractor {

    private val client get() = AnililiHttpClient.client

    private const val BASE_URL = "https://blisseyhusband.net"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    // Verified AniList IDs for all 23 Pokemon movies (Hindi)
    private val POKEMON_MOVIE_URLS = mapOf(
        528 to "https://blisseyhusband.net/mewto-ka-badla/",
        1117 to "https://blisseyhusband.net/ash-pikachu-aur-lugia-in-danger-hindi/",
        1118 to "https://blisseyhusband.net/pokemon-movie-3-unown-ka-tehelka-hindi/",
        1119 to "https://blisseyhusband.net/pokemon-movie-4-khatre-ka-jungle-hindi/",
        1120 to "https://blisseyhusband.net/pokemon-movie-5-soul-dew-ka-raaz-hindi/",
        1121 to "https://blisseyhusband.net/pokemon-movie-6-jirachi-ka-wonder-hindi/",
        1122 to "https://blisseyhusband.net/pokemon-movie-7-deoxy-aur-tory-ki-story/",
        1526 to "https://blisseyhusband.net/lucario-ki-toofani-shakti-hindi/",
        2201 to "https://blisseyhusband.net/pokemon-ranger-and-the-temple-of-the-sea-hindi/",
        2847 to "https://blisseyhusband.net/darkrai-dost-ya-dushman-hindi/",
        4026 to "https://blisseyhusband.net/giratina-aur-ek-maha-yodhha-hindi/",
        6178 to "https://blisseyhusband.net/arceus-and-the-jewel-of-life-hindi/",
        7695 to "https://blisseyhusband.net/zoroark-mayajaal-ka-ustaad-hindi/",
        10740 to "https://blisseyhusband.net/victini-aur-reshiram-hindi/",
        9917 to "https://blisseyhusband.net/victini-aur-reshiram-hindi/",
        12671 to "https://blisseyhusband.net/kyurem-ka-muqabala-hindi/",
        16680 to "https://blisseyhusband.net/genesect-aur-mewtwo-ek-shaandar-kahani-hindi/",
        20644 to "https://blisseyhusband.net/dabang-diancie-aur-diamond/",
        21266 to "https://blisseyhusband.net/hoopa-and-the-magic-rings/",
        21651 to "https://blisseyhusband.net/pokemon-movie-19-volcanion-and-the-mechanical-marve-hindi/",
        98298 to "https://blisseyhusband.net/aap-selected-ho-pokemon-movie-20-i-choose-youaap-selected-ho/",
        100744 to "https://blisseyhusband.net/the-power-of-us-hindi-subbed-2/",
        106287 to "https://blisseyhusband.net/mewtwo-strikes-back-evolution-hindi/",
        114564 to "https://blisseyhusband.net/pokemon-the-movie-secrets-of-the-jungle-hindi/",
    )

    // AniList IDs for TV Seasons
    val POKEMON_TV_ANILIST_IDS = setOf(
        527,    // Original 1997 series (Indigo, Orange, Johto / S1-S5)
        1564,   // Advanced Generation (Ruby/Sapphire / Battle Frontier / S6-S9)
        1565,   // Diamond & Pearl (Sinnoh / S10-S13)
        9107,   // Best Wishes! / Black & White (S14)
        14093,  // Best Wishes! Season 2 / Rival Destinies (S15)
        17115,  // Best Wishes! Season 2: Episode N (S16 Part 1)
        17873,  // Best Wishes! Season 2: Decolora Adventure (S16 Part 2)
        20570,  // Decolora Adventure Specials
        19291,  // XY (Kalos / S17-S18)
        21356,  // XYZ (S19)
        97634,  // Sun & Moon (Alola / S20-S22)
        112151, // Journeys
        112153, // Journeys (S23-S25)
        158870, // Aim to Be a Pokemon Master (S25 finale)
        160488, // Aim to Be a Pokemon Master
        158871, // Pokemon Horizons: The Series (2023 / S26)
        171616  // Pokemon Horizons Season 2: The Search for Laqua (S27)
    )

    val POKEMON_ENGLISH_SPECIAL_ANILIST_IDS = setOf(
        97635,  // Pokémon Generations (18 eps)
        2363,   // Pokémon Chronicles (22 eps)
        20159,  // Pokémon: The Origin (4 eps)
        119964, // Pokétoon (8 eps)
        138699  // Pokémon Evolutions (8 eps)
    )

    val POKEMON_OKRU_SPECIAL_URLS = mapOf(
        1709 to "https://ok.ru/video/6982616222463" // Pokémon: Mewtwo Returns (English Dub)
    )

    private val POKEMON_ENGLISH_SPECIAL_URLS: Map<Int, Map<Int, String>> = mapOf(
        // Pokémon Generations (AniList ID: 97635)
        97635 to mapOf(
            1 to "https://blisseyhusband.net/pokemon-generations-the-adventure/",
            2 to "https://blisseyhusband.net/pokemon-generations-the-chase/",
            3 to "https://blisseyhusband.net/pokemon-generations-the-challenger/",
            4 to "https://blisseyhusband.net/pokemon-generations-the-lake-of-rage/",
            5 to "https://blisseyhusband.net/pokemon-generations-the-legacy/",
            6 to "https://blisseyhusband.net/pokemon-generations-the-reawakening/",
            7 to "https://blisseyhusband.net/pokemon-generations-the-vision/",
            8 to "https://blisseyhusband.net/pokemon-generations-the-cavern/",
            9 to "https://blisseyhusband.net/pokemon-generations-the-scoop-2/",
            10 to "https://blisseyhusband.net/pokemon-generations-the-old-chateau/",
            11 to "https://blisseyhusband.net/pokemon-generations-the-new-world/",
            12 to "https://blisseyhusband.net/pokemon-generations-the-magma-stone/",
            13 to "https://blisseyhusband.net/pokemon-generations-the-uprising-2/",
            14 to "https://blisseyhusband.net/pokemon-generations-the-frozen-world/",
            15 to "https://blisseyhusband.net/pokemon-generations-the-king-returns/",
            16 to "https://blisseyhusband.net/pokemon-generations-the-beauty-eternal-2/",
            17 to "https://blisseyhusband.net/pokemon-generations-the-investigation/",
            18 to "https://blisseyhusband.net/pokemon-generations-the-redemption-2/"
        ),
        // Pokémon Chronicles (AniList ID: 2363)
        2363 to mapOf(
            1 to "https://blisseyhusband.net/pokemon-chronicles-(dub)-episode-1/",
            2 to "https://blisseyhusband.net/pokemon-chronicles-(dub)-episode-1/",
            3 to "https://blisseyhusband.net/pokemon-chronicles-(dub)-episode-1/",
            4 to "https://blisseyhusband.net/pokemon-chronicles-dub-episode-4/",
            5 to "https://blisseyhusband.net/pokemon-chronicles-dub-episode-5/",
            6 to "https://blisseyhusband.net/pokemon-chronicles-dub-episode-6/",
            7 to "https://blisseyhusband.net/pokemon-chronicles-dub-episode-7/",
            8 to "https://blisseyhusband.net/pokemon-chronicles-dub-episode-8/",
            9 to "https://blisseyhusband.net/pokemon-chronicles-dub-episode-9/",
            10 to "https://blisseyhusband.net/pokemon-chronicles-(dub)-episode-10/",
            11 to "https://blisseyhusband.net/pokemon-chronicles-dub-episode-11/",
            12 to "https://blisseyhusband.net/pokemon-chronicles-(dub)-episode-12/",
            13 to "https://blisseyhusband.net/pokemon-chronicles-dub-episode-13/",
            14 to "https://blisseyhusband.net/pokemon-chronicles-dub-episode-14/",
            15 to "https://blisseyhusband.net/pokemon-chronicles-dub-episode-15/",
            16 to "https://blisseyhusband.net/pokemon-chronicles-dub-episode-16/",
            17 to "https://blisseyhusband.net/pokemon-chronicles-dub-episode-17/",
            18 to "https://blisseyhusband.net/pokemon-chronicles-dub-episode-18/",
            19 to "https://blisseyhusband.net/pokemon-chronicles-dub-episode-19/",
            20 to "https://blisseyhusband.net/pokemon-chronicles-dub-episode-20/",
            21 to "https://blisseyhusband.net/pokemon-chronicles-dub-episode-21/",
            22 to "https://blisseyhusband.net/pokemon-chronicles-dub-episode-22/"
        ),
        // Pokémon: The Origin (AniList ID: 20159)
        20159 to mapOf(
            1 to "https://blisseyhusband.net/pokemon-origins-episode-1/",
            2 to "https://blisseyhusband.net/pokemon-origins-episode-2/",
            3 to "https://blisseyhusband.net/pokemon-origins-episode-3/",
            4 to "https://blisseyhusband.net/pokemon-origins-episode-4/"
        ),
        // Pokétoon (AniList ID: 119964)
        119964 to mapOf(
            1 to "https://blisseyhusband.net/poketoon-episode-1-scraggy-and-mimikyu/",
            2 to "https://blisseyhusband.net/poketoon-episode-2-the-pancham-who-wants-to-be-a-hero/",
            3 to "https://blisseyhusband.net/poketoon-episode-3-a-budding-dream/",
            4 to "https://blisseyhusband.net/poketoon-episode-4-wait-here-magikarp/",
            5 to "https://blisseyhusband.net/poketoon-episode-5-the-warm-and-cozy-slugma-house/",
            6 to "https://blisseyhusband.net/poketoon-episode-6-ive-become-a-gengar/",
            7 to "https://blisseyhusband.net/poketoon-episode-7/",
            8 to "https://blisseyhusband.net/poketoon-episode-8-english-subbed/"
        ),
        // Pokemon Evolutions (AniList ID: 138699)
        138699 to mapOf(
            1 to "https://blisseyhusband.net/pokemon-evolutions-episode-1-the-champion/",
            2 to "https://blisseyhusband.net/pokemon-evolutions-episode-2-the-eclipse/",
            3 to "https://blisseyhusband.net/pokemon-evolutions-episode-3-the-visionary/",
            4 to "https://blisseyhusband.net/pokemon-evolutions-episode-4-the-plan/",
            5 to "https://blisseyhusband.net/pokemon-evolutions-episode-5/",
            6 to "https://blisseyhusband.net/pokemon-evolutions-episode-6/",
            7 to "https://blisseyhusband.net/pokemon-evolutions-episode-7/",
            8 to "https://blisseyhusband.net/pokemon-evolutions-episode-8/"
        )
    )

    fun isPokemon(anilistId: Int, animeTitle: String? = null): Boolean {
        if (POKEMON_ENGLISH_SPECIAL_ANILIST_IDS.contains(anilistId)) return false
        if (POKEMON_OKRU_SPECIAL_URLS.containsKey(anilistId)) return false
        if (POKEMON_MOVIE_URLS.containsKey(anilistId)) return true
        if (POKEMON_TV_ANILIST_IDS.contains(anilistId)) return true
        val title = animeTitle?.lowercase() ?: ""
        if (isPokemonEnglishSpecial(anilistId, animeTitle)) return false
        return title.contains("pokemon") || title.contains("pokémon") || title.contains("pocket monsters")
    }

    fun isPokemonEnglishSpecial(anilistId: Int, animeTitle: String? = null): Boolean {
        if (POKEMON_ENGLISH_SPECIAL_ANILIST_IDS.contains(anilistId)) return true
        if (POKEMON_OKRU_SPECIAL_URLS.containsKey(anilistId)) return true
        val title = animeTitle?.lowercase() ?: ""
        return (title.contains("pokemon") || title.contains("pokémon") || title.contains("poketoon")) &&
                (title.contains("generation") || title.contains("chronicle") || title.contains("origin") || title.contains("toon") || title.contains("evolution") || title.contains("mewtwo returns") || title.contains("mewtwo!"))
    }

    fun getPokemonGenerationLabel(anilistId: Int, animeTitle: String? = null): String? {
        val mapped = when (anilistId) {
            527 -> "Generation 1 & 2"
            1564 -> "Generation 3"
            1565 -> "Generation 4"
            9107 -> "Generation 5 Part 1"
            14093 -> "Generation 5 Part 2"
            17115 -> "Generation 5 Part 3"
            17873, 20570 -> "Generation 5 Part 4"
            19291 -> "Generation 6 Part 1"
            21356 -> "Generation 6 Part 2"
            97634 -> "Generation 7"
            112153 -> "Generation 8 Part 1"
            158870, 160488 -> "Generation 8 Part 2"
            158871 -> "Generation 9 Part 1"
            171616 -> "Generation 9 Part 2"
            161868 -> "Generation 9"
            2363, 97635, 20159, 119964, 138699 -> "Specials"
            else -> null
        }
        if (mapped != null) return mapped

        val title = animeTitle?.lowercase() ?: ""
        if (!isPokemon(anilistId, animeTitle)) return null

        // Movies should not receive TV generation tags
        if (POKEMON_MOVIE_URLS.containsKey(anilistId) || title.contains("movie") || title.contains("mewtwo strikes back") || title.contains("i choose you") || title.contains("power of us") || title.contains("secrets of the jungle")) {
            return null
        }

        return when {
            title.contains("decolora") -> "Generation 5 Part 4"
            title.contains("episode n") -> "Generation 5 Part 3"
            title.contains("best wishes! season 2") || title.contains("rival destinies") -> "Generation 5 Part 2"
            title.contains("best wishes") || title.contains("black & white") || title.contains("black and white") -> "Generation 5 Part 1"
            title.contains("xyz") || title.contains("xy&z") -> "Generation 6 Part 2"
            title.contains("xy") || title.contains("kalos") -> "Generation 6 Part 1"
            title.contains("mezase") || title.contains("aim to be a pok") -> "Generation 8 Part 2"
            title.contains("journeys") -> "Generation 8 Part 1"
            title.contains("sun & moon") || title.contains("sun and moon") || title.contains("alola") -> "Generation 7"
            title.contains("diamond & pearl") || title.contains("diamond and pearl") || title.contains("sinnoh") -> "Generation 4"
            title.contains("advanced generation") || title.contains("ruby & sapphire") || title.contains("battle frontier") -> "Generation 3"
            title.contains("horizons") -> "Generation 9"
            title.contains("chronicle") || title.contains("origin") || title.contains("evolution") || title.contains("poketoon") || title.contains("special") -> "Specials"
            title.contains("pokemon") || title.contains("pokémon") || title.contains("pocket monsters") -> "Generation 1 & 2"
            else -> null
        }
    }

    // Mapping Dedicated Hindi Season & Episode -> Episode Page URL (from /pokemon-seasons-hindi-tamil-telugu/)
    private val SEASON_EPISODES: Map<Int, Map<Int, String>> = mapOf(
        1 to mapOf(
            1 to "https://blisseyhusband.net/pokemon-indigo-league-episode-1-hindi/",
            2 to "https://blisseyhusband.net/pokemon-indigo-league-episode-2-hindi/",
            3 to "https://blisseyhusband.net/pokemon-indigo-league-episode-3-hindi/",
            4 to "https://blisseyhusband.net/pokemon-indigo-league-episode-4-hindi/",
            5 to "https://blisseyhusband.net/pokemon-indigo-league-episode-5-hindi/",
            6 to "https://blisseyhusband.net/pokemon-indigo-league-episode-6-hindi/",
            7 to "https://blisseyhusband.net/pokemon-indigo-league-episode-7-hindi/",
            8 to "https://blisseyhusband.net/pokemon-indigo-league-episode-8-hindi/",
            9 to "https://blisseyhusband.net/pokemon-indigo-league-episode-9-hindi/",
            10 to "https://blisseyhusband.net/pokemon-indigo-league-episode-10-hindi/",
            11 to "https://blisseyhusband.net/pokemon-indigo-league-episode-11-hindi/",
            12 to "https://blisseyhusband.net/pokemon-indigo-league-episode-12-hindi/",
            13 to "https://blisseyhusband.net/pokemon-indigo-league-episode-13-hindi/",
            14 to "https://blisseyhusband.net/pokemon-indigo-league-episode-14-hindi/",
            15 to "https://blisseyhusband.net/pokemon-indigo-league-episode-15-hindi/",
            16 to "https://blisseyhusband.net/pokemon-indigo-league-episode-16-hindi/",
            17 to "https://blisseyhusband.net/pokemon-indigo-league-episode-17-hindi/",
            18 to "https://blisseyhusband.net/pokemon-indigo-league-episode-18-hindi/",
            19 to "https://blisseyhusband.net/pokemon-indigo-league-episode-19-hindi/",
            20 to "https://blisseyhusband.net/pokemon-indigo-league-episode-20-hindi/",
            21 to "https://blisseyhusband.net/pokemon-indigo-league-episode-21-hindi/",
            22 to "https://blisseyhusband.net/pokemon-indigo-league-episode-22-hindi/",
            23 to "https://blisseyhusband.net/pokemon-indigo-league-episode-23-hindi/",
            24 to "https://blisseyhusband.net/pokemon-indigo-league-episode-24-hindi/",
            25 to "https://blisseyhusband.net/pokemon-indigo-league-episode-25-hindi/",
            26 to "https://blisseyhusband.net/pokemon-indigo-league-episode-26-hindi/",
            27 to "https://blisseyhusband.net/pokemon-indigo-league-episode-27-hindi/",
            28 to "https://blisseyhusband.net/pokemon-indigo-league-episode-28-hindi/",
            29 to "https://blisseyhusband.net/pokemon-indigo-league-episode-29-hindi/",
            30 to "https://blisseyhusband.net/pokemon-indigo-league-episode-30-hindi/",
            31 to "https://blisseyhusband.net/pokemon-indigo-league-episode-31-hindi/",
            32 to "https://blisseyhusband.net/pokemon-indigo-league-episode-32-hindi/",
            33 to "https://blisseyhusband.net/pokemon-indigo-league-episode-33-hindi/",
            34 to "https://blisseyhusband.net/pokemon-indigo-league-episode-34-hindi/",
            35 to "https://blisseyhusband.net/pokemon-indigo-league-episode-35-hindi/",
            36 to "https://blisseyhusband.net/pokemon-indigo-league-episode-36-hindi/",
            37 to "https://blisseyhusband.net/pokemon-indigo-league-episode-37-hindi/",
            38 to "https://blisseyhusband.net/pokemon-indigo-league-episode-38-hindi/",
            39 to "https://blisseyhusband.net/pokemon-indigo-league-episode-39-hindi/",
            40 to "https://blisseyhusband.net/pokemon-indigo-league-episode-40-hindi/",
            41 to "https://blisseyhusband.net/pokemon-indigo-league-episode-41-hindi/",
            42 to "https://blisseyhusband.net/pokemon-indigo-league-episode-42-hindi/",
            43 to "https://blisseyhusband.net/pokemon-indigo-league-episode-43-hindi/",
            44 to "https://blisseyhusband.net/pokemon-indigo-league-episode-44-hindi/",
            45 to "https://blisseyhusband.net/pokemon-indigo-league-episode-45-hindi/",
            46 to "https://blisseyhusband.net/pokemon-indigo-league-episode-46-hindi/",
            47 to "https://blisseyhusband.net/pokemon-indigo-league-episode-47-hindi/",
            48 to "https://blisseyhusband.net/pokemon-indigo-league-episode-48-hindi/",
            49 to "https://blisseyhusband.net/pokemon-indigo-league-episode-49-hindi/",
            50 to "https://blisseyhusband.net/pokemon-indigo-league-episode-50-hindi/",
            51 to "https://blisseyhusband.net/pokemon-indigo-league-episode-51-hindi/",
            52 to "https://blisseyhusband.net/pokemon-indigo-league-episode-52-hindi/",
            53 to "https://blisseyhusband.net/pokemon-indigo-league-episode-53-hindi/",
            54 to "https://blisseyhusband.net/pokemon-indigo-league-episode-54-hindi/",
            55 to "https://blisseyhusband.net/pokemon-indigo-league-episode-55-hindi/",
            56 to "https://blisseyhusband.net/pokemon-indigo-league-episode-56-hindi/",
            57 to "https://blisseyhusband.net/pokemon-indigo-league-episode-57-hindi/",
            58 to "https://blisseyhusband.net/pokemon-indigo-league-episode-58-hindi/",
            59 to "https://blisseyhusband.net/pokemon-indigo-league-episode-59-hindi/",
            60 to "https://blisseyhusband.net/pokemon-indigo-league-episode-60-hindi/",
            61 to "https://blisseyhusband.net/pokemon-indigo-league-episode-61-hindi/",
            62 to "https://blisseyhusband.net/pokemon-indigo-league-episode-62-hindi/",
            63 to "https://blisseyhusband.net/pokemon-indigo-league-episode-63-hindi/",
            64 to "https://blisseyhusband.net/pokemon-indigo-league-episode-64-hindi/",
            65 to "https://blisseyhusband.net/pokemon-indigo-league-episode-65-hindi/",
            66 to "https://blisseyhusband.net/pokemon-indigo-league-episode-66-hindi/",
            67 to "https://blisseyhusband.net/pokemon-indigo-league-episode-67-hindi/",
            68 to "https://blisseyhusband.net/pokemon-indigo-league-episode-68-hindi/",
            69 to "https://blisseyhusband.net/pokemon-indigo-league-episode-69-hindi/",
            70 to "https://blisseyhusband.net/pokemon-indigo-league-episode-70-hindi/",
            71 to "https://blisseyhusband.net/pokemon-indigo-league-episode-71-hindi/",
            72 to "https://blisseyhusband.net/pokemon-indigo-league-episode-72-hindi/",
            73 to "https://blisseyhusband.net/pokemon-indigo-league-episode-73-hindi/",
            74 to "https://blisseyhusband.net/pokemon-indigo-league-episode-74-hindi/",
            75 to "https://blisseyhusband.net/pokemon-indigo-league-episode-75hindi/",
            76 to "https://blisseyhusband.net/pokemon-indigo-league-episode-76-hindi/",
            77 to "https://blisseyhusband.net/pokemon-indigo-league-episode-77-hindi/",
            78 to "https://blisseyhusband.net/pokemon-indigo-league-episode-78-hindi/",
        ),
        2 to mapOf(
            1 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-1/",
            2 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-2",
            3 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-3",
            4 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-4",
            5 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-5",
            6 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-6",
            7 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-7/",
            8 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-8/",
            9 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-9/",
            10 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-10/",
            11 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-11/",
            12 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-12/",
            13 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-13/",
            14 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-14/",
            15 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-15/",
            16 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-16/",
            17 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-17/",
            18 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-18/",
            19 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-19/",
            20 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-20/",
            21 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-21",
            22 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-22/",
            24 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-24/",
            25 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-25/",
            26 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-26/",
            27 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-27/",
            28 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-28/",
            29 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-29/",
            30 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-30/",
            31 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-31",
            32 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-32",
            33 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-33",
            34 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-34",
            35 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-35",
            36 to "https://blisseyhusband.net/pokemon-adventures-in-the-orange-islands-season-2-hindi-tamil-telugu-episode-36",
        ),
        3 to mapOf(
            1 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-1/",
            2 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-2",
            3 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-3",
            4 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-4",
            5 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-5",
            6 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-6",
            7 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-7",
            8 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-8",
            9 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-9",
            10 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-10",
            11 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-11",
            12 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-12",
            13 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-13",
            14 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-14",
            15 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-15",
            16 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-16",
            17 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-17",
            18 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-18",
            19 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-19",
            20 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-20",
            21 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-21",
            22 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-22",
            23 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-23",
            24 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-24",
            25 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-25",
            26 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-26",
            27 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-27",
            28 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-28",
            29 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-29",
            30 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-30",
            31 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-31",
            32 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-32",
            33 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-33",
            34 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-34",
            35 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-35",
            36 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-36",
            37 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-37",
            38 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-38",
            39 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-39",
            40 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-40",
            41 to "https://blisseyhusband.net/pokemon-the-johto-journeys-season-3-hindi-episode-41",
        ),
        4 to mapOf(
            1 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-",
            2 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-2",
            3 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-3",
            4 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-4",
            5 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-5",
            6 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-6",
            7 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-7",
            8 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-8",
            9 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-9",
            10 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-10",
            11 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-11",
            12 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-12",
            13 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-13",
            14 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-14",
            15 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-15",
            16 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-16",
            17 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-17",
            18 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-18",
            19 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-19",
            20 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-20",
            21 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-21",
            22 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-22",
            23 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-23",
            24 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-24",
            25 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-25",
            26 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-26",
            27 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-27",
            28 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-28",
            29 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-29",
            30 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-30",
            31 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-31",
            32 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-32",
            33 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-3",
            34 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-34",
            35 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-35",
            36 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-36",
            37 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-37",
            38 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-38",
            39 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-39",
            40 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-40",
            41 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-41",
            42 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-42",
            43 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-43",
            44 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-44",
            45 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-45",
            46 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-46",
            47 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-47",
            48 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-48",
            49 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-49",
            50 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-50",
            51 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-51",
            52 to "https://blisseyhusband.net/pokemon-season-4-johto-league-champions-english-hindi-episode-52",
        ),
        5 to mapOf(
            1 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-1",
            2 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-2",
            3 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-3",
            4 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-4",
            5 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-5",
            6 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-6",
            7 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-7",
            8 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-8",
            9 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-9",
            10 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-10",
            11 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-11",
            12 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-12",
            13 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-13",
            14 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-14",
            15 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-15",
            16 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-16",
            17 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-17",
            18 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-18",
            19 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-19",
            20 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-20",
            21 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-21",
            22 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-22",
            23 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-23",
            24 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-24",
            25 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-25",
            26 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-26",
            27 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-27",
            28 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-28",
            29 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-29",
            30 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-30",
            31 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-31",
            32 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-32",
            33 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-33",
            34 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-34",
            35 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-35",
            36 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-36",
            37 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-37",
            38 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-38",
            39 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-39",
            40 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-40",
            41 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-41",
            42 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-42",
            43 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-43",
            44 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-44",
            45 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-45",
            46 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-46",
            47 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-47",
            48 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-48",
            49 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-49",
            50 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-50",
            51 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-51",
            52 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-52",
            53 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-53",
            54 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-54",
            55 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-55",
            56 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-56",
            57 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-57",
            58 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-58",
            59 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-59",
            60 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-60",
            61 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-61",
            62 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-62",
            63 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-63",
            64 to "https://blisseyhusband.net/pokemon-season-5-master-quest-dual-audio-english-hindi-episode-64",
        ),
        6 to mapOf(
            1 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-1",
            2 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-2",
            3 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-3",
            4 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-4",
            5 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-5",
            6 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-6",
            7 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-7",
            8 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-8",
            9 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-9",
            10 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-10",
            11 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-11",
            12 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-12",
            13 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-13",
            14 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-14",
            15 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-15",
            16 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-16",
            17 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-17",
            18 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-18",
            19 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-19",
            20 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-20",
            21 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-21",
            22 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-22",
            23 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-23",
            24 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-24",
            25 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-25",
            26 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-26",
            27 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-27",
            28 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-28",
            29 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-29",
            30 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-30",
            31 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-31",
            32 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-32",
            33 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-33",
            34 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-34",
            35 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-35",
            36 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-36",
            37 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-37",
            38 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-38",
            39 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-39",
            40 to "https://blisseyhusband.net/pokemon-season-6-advanced-dual-audio-english-hindi-episode-40",
        ),
        7 to mapOf(
            1 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-1/",
            2 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-2",
            3 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-3",
            4 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-4",
            5 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-5",
            6 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-6",
            7 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-7",
            8 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-8",
            9 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-9",
            10 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-10",
            11 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-11",
            12 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-12",
            13 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-13",
            14 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-14",
            15 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-15",
            16 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-16",
            17 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-17",
            18 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-18",
            19 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-19",
            20 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-20",
            21 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-21",
            22 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-22",
            23 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-23",
            24 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-24",
            25 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-25",
            26 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-26",
            27 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-27",
            28 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-28",
            29 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-29",
            30 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-30",
            31 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-31",
            32 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-32",
            33 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-33",
            34 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-34",
            35 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-35",
            36 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-36",
            37 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-37",
            38 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-38",
            39 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-39",
            40 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-40",
            41 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-41",
            42 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-42",
            43 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-43",
            44 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-44",
            45 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-45",
            46 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-46",
            47 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-47",
            48 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-48",
            49 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-49",
            50 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-50",
            51 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-51",
            52 to "https://blisseyhusband.net/pokemon-season-7-advanced-challenge-dual-audio-english-hindi-episode-52",
        ),
        8 to mapOf(
            1 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-1",
            2 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-2",
            3 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-3",
            4 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-4",
            5 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-5",
            6 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-6",
            7 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-7",
            8 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-8",
            9 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-9",
            10 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-10",
            11 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-11",
            12 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-12",
            13 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-13",
            14 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-14",
            15 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-15",
            16 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-16",
            17 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-17",
            18 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-18",
            19 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-19",
            20 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-20",
            21 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-21",
            22 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-22",
            23 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-23",
            24 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-24",
            25 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-25",
            26 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-26",
            27 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-27",
            28 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-28",
            29 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-29",
            30 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-30",
            31 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-31",
            32 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-32",
            33 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-33",
            34 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-34",
            35 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-35",
            36 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-36",
            37 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-37",
            38 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-38",
            39 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-39",
            40 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-40",
            41 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-41",
            42 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-42",
            43 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-43",
            44 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-44",
            45 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-45",
            46 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-46",
            47 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-47",
            48 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-48",
            49 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-49",
            50 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-50",
            51 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-51",
            52 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-52",
        ),
        9 to mapOf(
            1 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-1",
            2 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-2",
            3 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-3",
            4 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-4",
            5 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-5",
            6 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-6",
            7 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-7",
            8 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-8",
            9 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-9",
            10 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-10",
            11 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-11",
            12 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-12",
            13 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-13",
            14 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-14",
            15 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-15",
            16 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-16",
            17 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-17",
            18 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-18",
            19 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-19",
            20 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-20",
            21 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-21",
            22 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-22",
            23 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-23",
            24 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-24",
            25 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-25",
            26 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-26",
            27 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-27",
            28 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-28",
            29 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-29",
            30 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-30",
            31 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-31",
            32 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-32",
            33 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-33",
            34 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-34",
            35 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-35",
            36 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-36",
            37 to "https://blisseyhusband.net/pokemon-season-9-battle-frontier-dual-audio-english-hindi-episode-37",
            38 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-38",
            39 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-39",
            40 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-40",
            41 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-41",
            42 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-42",
            43 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-43",
            44 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-44",
            45 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-45",
            46 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-46",
            47 to "https://blisseyhusband.net/pokemon-season-8-advanced-battl-dual-audio-english-hindi-episode-47",
        ),
        10 to mapOf(
            1 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-1/",
            2 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-2/",
            3 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-3/",
            4 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-4/",
            5 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-5/",
            6 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-6/",
            7 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-7/",
            8 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-8/",
            9 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-9/",
            10 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-10",
            11 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-11",
            12 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-12",
            13 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-13",
            14 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-14",
            15 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-15",
            16 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-16",
            17 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-17",
            18 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-18",
            19 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-19",
            20 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-20",
            21 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-21",
            22 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-22",
            23 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-23",
            24 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-24",
            25 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-25",
            26 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-26",
            27 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-27",
            28 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-28",
            29 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-29",
            30 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-30",
            31 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-31",
            32 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-32",
            33 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-33",
            34 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-34",
            35 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-35",
            36 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-36",
            37 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-37",
            38 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-38",
            39 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-39",
            40 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-40",
            41 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-41",
            42 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-42",
            43 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-43",
            44 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-44",
            45 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-45",
            46 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-46",
            47 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-47",
            48 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-48",
            49 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-49",
            50 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-50",
            51 to "https://blisseyhusband.net/pokemon-season-10-diamond-and-pearl-hindi-episode-51",
        ),
        11 to mapOf(
            1 to "https://blisseyhusband.net/tears-for-fears/",
            2 to "https://blisseyhusband.net/once-there-were-greenfields/",
            3 to "https://blisseyhusband.net/throwing-the-track-switch/",
            4 to "https://blisseyhusband.net/the-keystone-pops/",
            5 to "https://blisseyhusband.net/bibarel-gnaws-best/",
            6 to "https://blisseyhusband.net/nosing-round-the-mountain/",
            7 to "https://blisseyhusband.net/luxray-vision/",
            8 to "https://blisseyhusband.net/journey-to-the-unown/",
            9 to "https://blisseyhusband.net/team-shocker/",
            10 to "https://blisseyhusband.net/tanks-for-the-memories/",
            11 to "https://blisseyhusband.net/hot-springing-a-leak/",
            12 to "https://blisseyhusband.net/riding-the-winds-of-change/",
            13 to "https://blisseyhusband.net/sleight-of-sand/",
            14 to "https://blisseyhusband.net/lost-leader-strategy/",
            15 to "https://blisseyhusband.net/crossing-the-battle-line/",
            16 to "https://blisseyhusband.net/a-triple-fighting-chance/",
            17 to "https://blisseyhusband.net/enter-galactic/",
            18 to "https://blisseyhusband.net/the-bells-are-singing/",
            19 to "https://blisseyhusband.net/crossing-paths/",
            20 to "https://blisseyhusband.net/pika-and-goliath/",
            21 to "https://blisseyhusband.net/our-cup-runneth-over/",
            24 to "https://blisseyhusband.net/pruning-a-passel-of-pals/",
            25 to "https://blisseyhusband.net/strategy-with-a-smile/",
            26 to "https://blisseyhusband.net/the-thief-that-keeps-on-thieving/",
            27 to "https://blisseyhusband.net/chim-charred/",
            28 to "https://blisseyhusband.net/cream-of-the-croagunk-crop/",
            29 to "https://blisseyhusband.net/pokemon-ranger-and-the-kidnapped-riolu-part-one/",
            30 to "https://blisseyhusband.net/pokemon-ranger-and-the-kidnapped-riolu-part-two/",
            31 to "https://blisseyhusband.net/a-crasher-course-in-power/",
            32 to "https://blisseyhusband.net/hungry-for-the-good-life/",
            33 to "https://blisseyhusband.net/fighting-fear-with-fear/",
            34 to "https://blisseyhusband.net/arriving-in-style/",
            35 to "https://blisseyhusband.net/the-psyduck-stops-here/",
            36 to "https://blisseyhusband.net/camping-it-up/",
            37 to "https://blisseyhusband.net/up-close-and-personable/",
            38 to "https://blisseyhusband.net/ghoul-daze/",
            39 to "https://blisseyhusband.net/one-team-two-team-red-team-blue-team/",
            40 to "https://blisseyhusband.net/a-lean-mean-team-rocket-machine/",
            41 to "https://blisseyhusband.net/playing-the-leveling-field/",
            42 to "https://blisseyhusband.net/doc-brock/",
            43 to "https://blisseyhusband.net/battling-the-generation-gap/",
            44 to "https://blisseyhusband.net/losing-its-lustrous/",
            45 to "https://blisseyhusband.net/double-team-turnover/",
            46 to "https://blisseyhusband.net/if-the-scarf-fits-wear-it/",
            47 to "https://blisseyhusband.net/a-trainer-and-child-reunion/",
            48 to "https://blisseyhusband.net/aiding-the-enemy/",
            49 to "https://blisseyhusband.net/barrys-busting-out-all-over/",
            50 to "https://blisseyhusband.net/shield-with-a-twist/",
            51 to "https://blisseyhusband.net/jumping-rocket-ship/",
            52 to "https://blisseyhusband.net/sleepless-in-pre-battle/",
        ),
        12 to mapOf(
            1 to "https://blisseyhusband.net/get-your-rotom-running/",
            2 to "https://blisseyhusband.net/a-breed-stampede/",
            3 to "https://blisseyhusband.net/ancient-family-matters/",
            4 to "https://blisseyhusband.net/dealing-with-defensive-types/",
            5 to "https://blisseyhusband.net/leading-a-stray/",
            6 to "https://blisseyhusband.net/steeling-peace-of-mind/",
            7 to "https://blisseyhusband.net/saving-the-world-from-ruins/",
            8 to "https://blisseyhusband.net/cheers-on-castaways-isle/",
            9 to "https://blisseyhusband.net/hold-the-phione/",
            10 to "https://blisseyhusband.net/another-one-gabites-the-dust/",
            11 to "https://blisseyhusband.net/stealing-the-conversation/",
            12 to "https://blisseyhusband.net/the-drifting-snorunt/",
            13 to "https://blisseyhusband.net/noodles-roamin-off/",
            14 to "https://blisseyhusband.net/pursuing-a-lofty-goal/",
            15 to "https://blisseyhusband.net/trials-and-adulations/",
            16 to "https://blisseyhusband.net/the-lonely-snover/",
            17 to "https://blisseyhusband.net/stopped-in-the-name-of-love/",
            18 to "https://blisseyhusband.net/old-rivals-new-tricks/",
            19 to "https://blisseyhusband.net/to-thine-own-pokemon-be-true/",
            20 to "https://blisseyhusband.net/battling-a-cute-drama/",
            21 to "https://blisseyhusband.net/classroom-training/",
            22 to "https://blisseyhusband.net/sliding-into-seventh/",
            23 to "https://blisseyhusband.net/a-pyramiding-rage/",
            24 to "https://blisseyhusband.net/pillars-of-friendship/",
            25 to "https://blisseyhusband.net/frozen-on-their-tracks/",
            26 to "https://blisseyhusband.net/pedal-to-the-mettle/",
            27 to "https://blisseyhusband.net/evolving-strategies/",
            28 to "https://blisseyhusband.net/uncrushing-defeat/",
            29 to "https://blisseyhusband.net/promoting-healthy-tangrowth/",
            30 to "https://blisseyhusband.net/beating-the-bustle-and-hustle/",
            31 to "https://blisseyhusband.net/gateway-to-ruin/",
            32 to "https://blisseyhusband.net/three-sides-to-every-story/",
            33 to "https://blisseyhusband.net/strategy-begins-at-home/",
            34 to "https://blisseyhusband.net/a-faux-oak-finish/",
            35 to "https://blisseyhusband.net/historical-mystery-tour/",
            36 to "https://blisseyhusband.net/challenging-a-towering-figure/",
            37 to "https://blisseyhusband.net/where-no-togepi-has-gone-before/",
            38 to "https://blisseyhusband.net/an-egg-scramble/",
            39 to "https://blisseyhusband.net/gone-with-the-windworks/",
            40 to "https://blisseyhusband.net/a-rivalry-to-gible-on/",
            41 to "https://blisseyhusband.net/dressed-for-jess-success/",
            43 to "https://blisseyhusband.net/try-for-the-family-stone/",
            44 to "https://blisseyhusband.net/sticking-with-who-you-know/",
            45 to "https://blisseyhusband.net/unlocking-the-red-chain-of-events/",
            46 to "https://blisseyhusband.net/the-needs-of-the-three/",
            47 to "https://blisseyhusband.net/the-battle-finale-of-legend/",
            48 to "https://blisseyhusband.net/the-treasure-is-all-mine/",
            49 to "https://blisseyhusband.net/mastering-current-events/",
            50 to "https://blisseyhusband.net/double-time-battle-training/",
            51 to "https://blisseyhusband.net/a-meteoric-rise-to-excellence/",
            52 to "https://blisseyhusband.net/gotta-get-a-gible/",
        ),
        13 to mapOf(
            2 to "https://blisseyhusband.net/short-and-to-the-punch/",
            3 to "https://blisseyhusband.net/a-marathon-rivalry/",
            4 to "https://blisseyhusband.net/yes-in-dee-dee-its-dawn/",
            5 to "https://blisseyhusband.net/playing-the-performance-encore/",
            6 to "https://blisseyhusband.net/fighting-ire-with-fire/",
            7 to "https://blisseyhusband.net/piplup-up-and-away/",
            8 to "https://blisseyhusband.net/flint-sparks-the-fire/",
            9 to "https://blisseyhusband.net/the-fleeing-tower-of-sunyshore/",
            10 to "https://blisseyhusband.net/teaching-the-student-teacher/",
            11 to "https://blisseyhusband.net/keeping-in-top-forme/",
            12 to "https://blisseyhusband.net/pokemon-ranger-heatran-rescue/",
            13 to "https://blisseyhusband.net/an-elite-coverup/",
            14 to "https://blisseyhusband.net/dawn-of-a-royal-day/",
            15 to "https://blisseyhusband.net/with-the-easiest-of-grace/",
            16 to "https://blisseyhusband.net/dealing-with-a-fierce-double-ditto-drama/",
            17 to "https://blisseyhusband.net/last-call-first-round/",
            18 to "https://blisseyhusband.net/opposites-interact/",
            19 to "https://blisseyhusband.net/coming-full-festival-circle/",
            20 to "https://blisseyhusband.net/a-grand-fight-for-winning/",
            21 to "https://blisseyhusband.net/for-the-love-of-meowth/",
            22 to "https://blisseyhusband.net/the-eighth-wonder-of-the-sinnoh-world/",
            23 to "https://blisseyhusband.net/four-roads-diverged-in-a-pokemon-port/",
            24 to "https://blisseyhusband.net/bucking-the-treasure-trend/",
            25 to "https://blisseyhusband.net/an-old-family-blend/",
            26 to "https://blisseyhusband.net/league-unleashed/",
            27 to "https://blisseyhusband.net/casting-a-paul-on-barry/",
            28 to "https://blisseyhusband.net/working-on-a-right-move/",
            29 to "https://blisseyhusband.net/familiarity-breeds-strategy/",
            30 to "https://blisseyhusband.net/a-real-rival-rouser/",
            31 to "https://blisseyhusband.net/battling-a-thaw-in-relations/",
            32 to "https://blisseyhusband.net/the-semi-final-frontier/",
            33 to "https://blisseyhusband.net/the-brockster-is-in/",
            34 to "https://blisseyhusband.net/memories-are-made-of-bliss/",
        ),
        14 to mapOf(
            1 to "https://blisseyhusband.net/in-the-shadow-of-zekrom/",
            2 to "https://blisseyhusband.net/enter-iris-and-axew/",
            3 to "https://blisseyhusband.net/a-sandile-gusher-of-change/",
            4 to "https://blisseyhusband.net/the-battle-club-and-tepigs-choice/",
            5 to "https://blisseyhusband.net/triple-leaders-team-threats/",
            6 to "https://blisseyhusband.net/dreams-by-the-yard-full/",
            7 to "https://blisseyhusband.net/snivy-plays-hard-to-catch/",
            8 to "https://blisseyhusband.net/saving-darmanitan-from-the-bell/",
            9 to "https://blisseyhusband.net/the-bloom-is-on-axew/",
            10 to "https://blisseyhusband.net/a-rival-battle-for-club-champ/",
            11 to "https://blisseyhusband.net/a-home-for-dwebble/",
            12 to "https://blisseyhusband.net/here-comes-the-trubbish-squad/",
            13 to "https://blisseyhusband.net/minccino-neat-and-tidy/",
            14 to "https://blisseyhusband.net/a-night-in-the-nacrene-city-museum/",
            15 to "https://blisseyhusband.net/the-battle-according-to-lenora/",
            16 to "https://blisseyhusband.net/rematch-at-the-nacrene-gym/",
            17 to "https://blisseyhusband.net/scraggy-hatched-to-be-wild/",
            18 to "https://blisseyhusband.net/sewaddle-and-burgh-in-pinwheel-forest/",
            19 to "https://blisseyhusband.net/a-connosseurs-revenge/",
            20 to "https://blisseyhusband.net/dancing-with-the-ducklett-trio/",
            21 to "https://blisseyhusband.net/the-lost-world-of-gothitelle/",
            22 to "https://blisseyhusband.net/a-venipede-stampede/",
            23 to "https://blisseyhusband.net/battling-for-the-love-of-bug-types/",
            24 to "https://blisseyhusband.net/emolga-the-irresistible/",
            25 to "https://blisseyhusband.net/emolga-and-the-new-volt-switch/",
            26 to "https://blisseyhusband.net/scare-at-the-litwick-mansion/",
            27 to "https://blisseyhusband.net/the-dragon-masters-path/",
            28 to "https://blisseyhusband.net/oshawotts-lost-scalchop/",
            29 to "https://blisseyhusband.net/cottonee-in-love/",
            30 to "https://blisseyhusband.net/a-ufo-for-elgyem/",
            31 to "https://blisseyhusband.net/ash-and-trips-third-battle/",
            32 to "https://blisseyhusband.net/facing-fear-with-eyes-wide-open/",
            33 to "https://blisseyhusband.net/iris-and-excadrill-against-the-dragon-buster/",
            34 to "https://blisseyhusband.net/gotta-catch-a-roggenrola/",
            35 to "https://blisseyhusband.net/where-did-you-go-audino/",
            36 to "https://blisseyhusband.net/archeops-in-the-modern-world/",
            37 to "https://blisseyhusband.net/a-fishing-connosseur-in-a-fishy-competition/",
            38 to "https://blisseyhusband.net/movie-time-zorua-in-the-legend-of-the-pokemon-knight/",
            39 to "https://blisseyhusband.net/reunion-battles-in-nimbasa/",
            40 to "https://blisseyhusband.net/cilan-versus-trip-ash-versus-georgia/",
            41 to "https://blisseyhusband.net/the-club-battle-hearts-of-fury-emolga-versus-sawk/",
            42 to "https://blisseyhusband.net/the-club-battle-finale-a-heroes-outcome/",
            43 to "https://blisseyhusband.net/meowths-scrafty-tactics/",
            44 to "https://blisseyhusband.net/purrloin-sweet-or-sneaky/",
            45 to "https://blisseyhusband.net/beheeyem-duosion-and-the-dream-thief/",
            46 to "https://blisseyhusband.net/the-beartic-mountain-feud/",
            47 to "https://blisseyhusband.net/crisis-from-the-underground-up/",
            48 to "https://blisseyhusband.net/battle-for-the-underground/",
        ),
        15 to mapOf(
            1 to "https://blisseyhusband.net/enter-elesa-electrifying-gym-leader/",
            2 to "https://blisseyhusband.net/dazzling-the-nimbasa-gym/",
            3 to "https://blisseyhusband.net/lost-at-the-stamp-rally/",
            4 to "https://blisseyhusband.net/ash-versus-the-champion/",
            5 to "https://blisseyhusband.net/a-maractus-musical/",
            6 to "https://blisseyhusband.net/the-four-seasons-of-sawsbuck/",
            7 to "https://blisseyhusband.net/scraggy-and-the-demanding-gothita/",
            8 to "https://blisseyhusband.net/the-lonely-deino/",
            9 to "https://blisseyhusband.net/the-mighty-accelguard-to-the-rescue/",
            10 to "https://blisseyhusband.net/a-call-for-brotherly-love/",
            11 to "https://blisseyhusband.net/stopping-the-rage-of-legends-episode-1/",
            12 to "https://blisseyhusband.net/stopping-the-rage-of-legends-episode-2/",
            13 to "https://blisseyhusband.net/battling-the-king-of-the-mines/",
            14 to "https://blisseyhusband.net/crisis-at-chargestone-cave/",
            15 to "https://blisseyhusband.net/evolution-exchange-excitement/",
            16 to "https://blisseyhusband.net/explorers-of-the-heros-ruin/",
            17 to "https://blisseyhusband.net/battling-the-bully/",
            18 to "https://blisseyhusband.net/baffling-the-bouffalant/",
            19 to "https://blisseyhusband.net/cilan-takes-flight/",
            20 to "https://blisseyhusband.net/an-amazing-aerial-battle/",
            21 to "https://blisseyhusband.net/climbing-the-tower-of-success/",
            22 to "https://blisseyhusband.net/the-clubsplosion-begins/",
            23 to "https://blisseyhusband.net/search-for-the-clubultimate/",
            24 to "https://blisseyhusband.net/a-clubsplosion-of-excitement/",
            25 to "https://blisseyhusband.net/commanding-the-clubsplosion-crown/",
            26 to "https://blisseyhusband.net/battling-the-leaf-thieves/",
            27 to "https://blisseyhusband.net/a-restoration-confrontation-episode-1/",
            28 to "https://blisseyhusband.net/a-restoration-confrontation-episode-2/",
            29 to "https://blisseyhusband.net/evolution-by-fire/",
            30 to "https://blisseyhusband.net/guarding-the-guardian-of-the-mountain/",
            31 to "https://blisseyhusband.net/caution-icy-battle-conditions/",
            32 to "https://blisseyhusband.net/clash-of-the-connoisseurs/",
            33 to "https://blisseyhusband.net/crisis-at-ferroseed-research/",
            34 to "https://blisseyhusband.net/an-epic-defense-force/",
            35 to "https://blisseyhusband.net/rocking-the-virbank-gym-episode-1/",
            36 to "https://blisseyhusband.net/rocking-the-virbank-gym-episode-2/",
            37 to "https://blisseyhusband.net/all-for-the-love-of-meloetta/",
            38 to "https://blisseyhusband.net/piplup-pansage-and-a-meeting-of-the-times/",
            39 to "https://blisseyhusband.net/expedition-to-onix-island/",
            40 to "https://blisseyhusband.net/the-mystery-of-the-missing-cubchoo/",
            41 to "https://blisseyhusband.net/iris-and-the-rogue-dragonite/",
            42 to "https://blisseyhusband.net/jostling-for-the-junior-cup/",
            43 to "https://blisseyhusband.net/battling-authority-once-again/",
            44 to "https://blisseyhusband.net/ash-iris-and-trip-then-there-were-three/",
            45 to "https://blisseyhusband.net/goodbye-junior-cup-hello-adventure/",
            46 to "https://blisseyhusband.net/the-road-to-humilau/",
            47 to "https://blisseyhusband.net/unrest-at-the-nursery/",
            48 to "https://blisseyhusband.net/meloetta-and-the-undersea-temple/",
            49 to "https://blisseyhusband.net/unovas-survival-crisis/",
        ),
        16 to mapOf(
            1 to "https://blisseyhusband.net/beauties-battling-for-pride-and-prestige/",
            3 to "https://blisseyhusband.net/a-village-homecoming/",
            4 to "https://blisseyhusband.net/drayden-versus-iris-past-present-and-future/",
            5 to "https://blisseyhusband.net/team-eevee-and-the-pokemon-rescue-squad/",
            6 to "https://blisseyhusband.net/curtain-up-unova-league/",
            7 to "https://blisseyhusband.net/mission-defeat-your-rival/",
            8 to "https://blisseyhusband.net/lost-at-the-league/",
            9 to "https://blisseyhusband.net/strong-strategy-steals-the-show/",
            10 to "https://blisseyhusband.net/camerons-secret-weapon/",
            11 to "https://blisseyhusband.net/a-unova-league-evolution/",
            12 to "https://blisseyhusband.net/new-places-familiar-faces/",
            13 to "https://blisseyhusband.net/the-names-n/",
            14 to "https://blisseyhusband.net/theres-a-new-gym-leader-in-town/",
            15 to "https://blisseyhusband.net/team-plasmas-pokemon-power-plot/",
            16 to "https://blisseyhusband.net/the-light-of-floccesy-ranch/",
            17 to "https://blisseyhusband.net/saving-braviary/",
            18 to "https://blisseyhusband.net/the-pokemon-harbor-patrol/",
            19 to "https://blisseyhusband.net/the-fires-of-a-red-hot-reunion/",
            20 to "https://blisseyhusband.net/team-plasmas-pokemon-manipulation/",
            21 to "https://blisseyhusband.net/secrets-from-out-of-the-fog/",
            22 to "https://blisseyhusband.net/meowth-colress-and-team-rivalry/",
            23 to "https://blisseyhusband.net/ash-and-n-a-clash-of-ideals/",
            24 to "https://blisseyhusband.net/team-plasma-and-the-awakening-ceremony/",
            25 to "https://blisseyhusband.net/what-lies-beyond-truth-and-ideals/",
            26 to "https://blisseyhusband.net/farewell-unova-setting-sail-for-new-adventures/",
            27 to "https://blisseyhusband.net/danger-sweet-as-honey/",
            28 to "https://blisseyhusband.net/cilan-and-the-case-of-the-purrloin-witness/",
            29 to "https://blisseyhusband.net/crowning-the-scalchop-king/",
            30 to "https://blisseyhusband.net/the-island-of-illusions/",
            31 to "https://blisseyhusband.net/to-catch-a-rotom/",
            32 to "https://blisseyhusband.net/the-pirates-of-decolore/",
            33 to "https://blisseyhusband.net/butterfree-and-me/",
            34 to "https://blisseyhusband.net/the-path-that-leads-to-goodbye/",
            35 to "https://blisseyhusband.net/searching-for-a-wish/",
            36 to "https://blisseyhusband.net/capacia-island-ufo/",
            37 to "https://blisseyhusband.net/the-journalist-from-another-region/",
            38 to "https://blisseyhusband.net/mystery-on-a-deserted-island/",
            39 to "https://blisseyhusband.net/a-pokemon-of-a-different-color/",
            40 to "https://blisseyhusband.net/celebrating-the-heros-comet/",
            41 to "https://blisseyhusband.net/go-go-gogoat/",
            42 to "https://blisseyhusband.net/team-rockets-shocking-recruit/",
            43 to "https://blisseyhusband.net/survival-of-the-striaton-gym/",
            44 to "https://blisseyhusband.net/best-wishes-until-we-meet-again/",
            45 to "https://blisseyhusband.net/the-dream-continues/",
        ),
        17 to mapOf(
            1 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-1",
            2 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-2",
            3 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-3",
            4 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-4",
            5 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-5",
            6 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-6",
            7 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-7",
            8 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-8",
            9 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-9",
            10 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-10",
            11 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-11",
            12 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-12",
            13 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-13",
            14 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-14",
            15 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-15",
            16 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-16",
            17 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-17",
            18 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-18",
            19 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-19",
            20 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-20",
            21 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-21",
            22 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-22",
            23 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-23",
            24 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-24",
            25 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-25",
            26 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-26",
            27 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-27",
            28 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-28",
            29 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-29",
            30 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-30",
            31 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-31",
            32 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-32",
            33 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-33",
            34 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-34",
            35 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-35",
            36 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-36",
            37 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-37",
            38 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-38",
            39 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-39",
            40 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-40",
            41 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-41",
            42 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-42",
            43 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-43",
            44 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-44",
            45 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-45",
            46 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-46",
            47 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-47",
            48 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-48",
            49 to "https://blisseyhusband.net/pokemon-season-17-xy-the-kalos-journeys-hindi-episode-49/",
        ),
        18 to mapOf(
            1 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-1/",
            2 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-2/",
            3 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-3",
            4 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-4",
            5 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-5",
            6 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-6",
            7 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-7",
            8 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-8",
            9 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-9",
            10 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-10",
            11 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-11",
            12 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-12",
            13 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-13",
            14 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-14",
            15 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-15",
            16 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-16",
            17 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-17",
            18 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-18",
            19 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-19",
            20 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-20",
            21 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-21",
            22 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-22",
            23 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-23",
            24 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-24",
            25 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-25",
            26 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-26",
            27 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-27",
            28 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-28",
            29 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-29",
            30 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-30",
            31 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-31",
            32 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-32",
            33 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-33",
            34 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-34",
            35 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-35",
            36 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-36",
            37 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-37",
            38 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-38",
            39 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-39",
            40 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-40",
            41 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-41",
            42 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-42",
            43 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-43",
            44 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-44",
            45 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-45",
            46 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-46",
            47 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-47",
            48 to "https://blisseyhusband.net/pokemon-season-18-xy-kalos-quest-hindi-episode-48",
        ),
        19 to mapOf(
        ),
        20 to mapOf(
            1 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-1/",
            2 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-2/",
            3 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-3/",
            4 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-4/",
            5 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-5/",
            6 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-6/",
            7 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-7/",
            8 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-8/",
            9 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-9/",
            10 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-10/",
            11 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-11/",
            12 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-12/",
            13 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-13/",
            14 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-14/",
            15 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-15/",
            16 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-16/",
            17 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-17/",
            18 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-18/",
            19 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-19/",
            20 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-20/",
            21 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-21/",
            22 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-22/",
            23 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-23/",
            24 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-24/",
            25 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-25/",
            26 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-26/",
            27 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-27/",
            28 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-28/",
            29 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-29/",
            30 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-30",
            31 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-31",
            32 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-32",
            33 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-33",
            34 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-34",
            35 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-35",
            36 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-36",
            37 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-37",
            38 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-38",
            39 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-39",
            40 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-40",
            41 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-41",
            42 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-42",
            43 to "https://blisseyhusband.net/pokemon-season-20-pokemon-sun-moon-hindi-episode-43",
        ),
        21 to mapOf(
            1 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-1/",
            2 to "https://blisseyhusband.net/nebby-panic-sudden-teleportation/",
            3 to "https://blisseyhusband.net/cmon-lets-search-for-the-transformed-ditto/",
            4 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-4/",
            5 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-5",
            6 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-6",
            7 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-7",
            8 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-8",
            9 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-9",
            10 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-10",
            11 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-11",
            12 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-12",
            13 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-13",
            14 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-14",
            15 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-15",
            16 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-16",
            17 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-17",
            18 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-18",
            19 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-19",
            20 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-20",
            21 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-21",
            22 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-22",
            23 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-23",
            24 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-24",
            25 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-25",
            26 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-26",
            27 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-27",
            28 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-28",
            29 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-29",
            30 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-30",
            31 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-31",
            32 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-32",
            33 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-33",
            34 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-34",
            35 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-35",
            36 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-36",
            37 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-37",
            38 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-38",
            39 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-39",
            40 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-40",
            41 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-41",
            42 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-42",
            43 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-43",
            44 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-44",
            45 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-45",
            46 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-46",
            47 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-47",
            48 to "https://blisseyhusband.net/pokemon-sun-moon-ultra-adventures-uncut-episode-48",
        ),
        22 to mapOf(
            1 to "https://blisseyhusband.net/lillier-and-the-staff/",
            2 to "https://blisseyhusband.net/a-haunted-house-for-everyone/",
            3 to "https://blisseyhusband.net/sparking-confusion/",
            4 to "https://blisseyhusband.net/dont-ignore-the-small-stufful/",
            5 to "https://blisseyhusband.net/no-stone-unturned/",
            6 to "https://blisseyhusband.net/bright-lights-big-changes/",
            7 to "https://blisseyhusband.net/we-know-where-youre-going-eevee/",
            8 to "https://blisseyhusband.net/battling-the-beast-within/",
            9 to "https://blisseyhusband.net/parallel-friendships/",
            10 to "https://blisseyhusband.net/alola-alola/",
            11 to "https://blisseyhusband.net/heart-of-fire-heart-of-stone/",
            12 to "https://blisseyhusband.net/thats-some-spicy-island-research/",
            13 to "https://blisseyhusband.net/showdown-on-poni-island/",
            14 to "https://blisseyhusband.net/evolving-research/",
            15 to "https://blisseyhusband.net/run-heroes-run/",
            16 to "https://blisseyhusband.net/memories-in-the-mist/",
            17 to "https://blisseyhusband.net/a-grand-debut/",
            18 to "https://blisseyhusband.net/keeping-your-eyes-on-the-ball/",
            19 to "https://blisseyhusband.net/show-me-the-metal/",
            20 to "https://blisseyhusband.net/got-meltan/",
            21 to "https://blisseyhusband.net/this-magik-moment/",
            22 to "https://blisseyhusband.net/beauty-is-only-crystal-deep/",
            23 to "https://blisseyhusband.net/the-dealer-of-destruction/",
            24 to "https://blisseyhusband.net/the-secret-princess/",
            25 to "https://blisseyhusband.net/drawn-with-the-wind/",
            26 to "https://blisseyhusband.net/aiming-for-the-top-floor/",
            27 to "https://blisseyhusband.net/a-high-speed-awakening/",
            28 to "https://blisseyhusband.net/the-one-that-didnt-get-away/",
            29 to "https://blisseyhusband.net/a-recipe-for-success/",
            30 to "https://blisseyhusband.net/spying-for-the-big-guy/",
            31 to "https://blisseyhusband.net/a-fiery-training-camp-trick/",
            32 to "https://blisseyhusband.net/living-on-the-cutting-edge/",
            33 to "https://blisseyhusband.net/a-timeless-encounter/",
            34 to "https://blisseyhusband.net/pikachus-exciting-adventure/",
            35 to "https://blisseyhusband.net/chasing-memories-creating-dreams/",
            36 to "https://blisseyhusband.net/league-offenders-and-defenders/",
            37 to "https://blisseyhusband.net/battle-royale-151/",
            38 to "https://blisseyhusband.net/battling-besties/",
            39 to "https://blisseyhusband.net/the-battlefield-of-truth-and-love/",
            40 to "https://blisseyhusband.net/imitation-is-the-sincerest-form-of-strategy/",
            41 to "https://blisseyhusband.net/battling-on-the-wing/",
            42 to "https://blisseyhusband.net/the-road-to-the-semi-finals/",
            43 to "https://blisseyhusband.net/the-final-four/",
            44 to "https://blisseyhusband.net/getting-down-to-the-ire/",
            45 to "https://blisseyhusband.net/the-wisdom-not-to-run/",
            46 to "https://blisseyhusband.net/final-rivals/",
            47 to "https://blisseyhusband.net/enter-the-champion/",
            48 to "https://blisseyhusband.net/z-move-showdown/",
            49 to "https://blisseyhusband.net/exhibition-unmasked/",
            50 to "https://blisseyhusband.net/a-full-battle-bounty/",
            51 to "https://blisseyhusband.net/fiery-surprises/",
            52 to "https://blisseyhusband.net/from-z-to-shining-z/",
            53 to "https://blisseyhusband.net/dreaming-of-the-sun-and-moon/",
            54 to "https://blisseyhusband.net/thank-you-alola-the-journey-continues/",
        ),
        23 to mapOf(
            1 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-1",
            2 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-2",
            3 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-3",
            4 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-4",
            5 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-5",
            6 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-6",
            7 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-7",
            8 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-8",
            9 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-9",
            10 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-10",
            11 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-11",
            12 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-12",
            13 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-13",
            14 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-14",
            15 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-15",
            16 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-16",
            17 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-17",
            18 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-18",
            19 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-19",
            20 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-20",
            21 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-21",
            22 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-22",
            23 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-23",
            24 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-24",
            25 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-25",
            26 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-26",
            27 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-27",
            28 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-28",
            29 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-29",
            30 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-30",
            31 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-31",
            32 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-32",
            33 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-33",
            34 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-34",
            35 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-35",
            36 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-36",
            37 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-37",
            38 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-38",
            39 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-39",
            40 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-40",
            41 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-41",
            42 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-42",
            43 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-43",
            44 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-44",
            45 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-45",
            46 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-46",
            47 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-47",
            48 to "https://blisseyhusband.net/pokemon-season-23-pokemon-journeys-hindi-episode-48",
        ),
        24 to mapOf(
            1 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-1",
            2 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-2",
            3 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-3",
            4 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-4",
            5 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-5",
            6 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-6",
            7 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-7",
            8 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-8",
            9 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-9",
            10 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-10",
            11 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-11",
            12 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-12",
            13 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-13",
            14 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-14",
            15 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-15",
            16 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-16",
            17 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-17",
            18 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-18",
            19 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-19",
            20 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-20",
            21 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-21",
            22 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-22",
            23 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-23",
            24 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-24",
            25 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-25",
            26 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-26",
            27 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-27",
            28 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-28",
            29 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-29",
            30 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-30",
            31 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-31",
            32 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-32",
            33 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-33",
            34 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-34",
            35 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-35",
            36 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-36",
            37 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-37",
            38 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-38",
            39 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-39",
            40 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-40",
            41 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-41",
            42 to "https://blisseyhusband.net/pokemon-season-24-pokemon-master-journeys-hindi-episode-42",
        ),
        25 to mapOf(
            1 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-1/",
            2 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-2",
            3 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-3",
            4 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-4",
            5 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-5",
            6 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-6",
            7 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-7",
            8 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-8",
            9 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-9",
            10 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-10",
            11 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-11",
            12 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-12",
            13 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-13",
            14 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-14",
            15 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-15",
            16 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-16",
            17 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-17",
            18 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-18",
            19 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-19",
            20 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-20",
            21 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-21",
            22 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-22",
            23 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-23",
            24 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-2",
            25 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-25",
            26 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-26",
            27 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-27",
            28 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-28",
            29 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-29",
            30 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-30",
            31 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-31",
            32 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-32",
            33 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-33",
            34 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-34",
            35 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-35",
            36 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-36",
            37 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-37",
            38 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-38",
            39 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-39",
            40 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-40",
            41 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-41",
            42 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-42",
            43 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-43",
            44 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-44",
            45 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-45",
            46 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-46",
            47 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-47",
            48 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-48",
            49 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-49",
            50 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-50",
            51 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-51",
            52 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-52",
            53 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-53",
            54 to "https://blisseyhusband.net/pokemon-season-25-pokemon-ultimate-journeys-hindi-episode-54",
        ),
        26 to mapOf(
            1 to "https://blisseyhusband.net/pokemon-horizons-episode-1-hindi-dub/",
            2 to "https://blisseyhusband.net/pokemon-horizons-episode-2-hindi-dub/",
            3 to "https://blisseyhusband.net/pokemon-horizons-episode-3-hindi-dub/",
            4 to "https://blisseyhusband.net/pokemon-horizons-episode-4-hindi-dub/",
            5 to "https://blisseyhusband.net/pokemon-horizons-episode-5-hindi-dub/",
            6 to "https://blisseyhusband.net/pokemon-horizons-episode-6-hindi-dub/",
            7 to "https://blisseyhusband.net/pokemon-horizons-episode-7-hindi-dub/",
            8 to "https://blisseyhusband.net/pokemon-horizons-episode-8-hindi-dub/",
            9 to "https://blisseyhusband.net/pokemon-horizons-episode-9-hindi-dub/",
            10 to "https://blisseyhusband.net/pokemon-horizons-episode-10-hindi-dub/",
            11 to "https://blisseyhusband.net/pokemon-horizons-episode-11-hindi-dub/",
            12 to "https://blisseyhusband.net/pokemon-horizons-episode-12-hindi-dub/",
            13 to "https://blisseyhusband.net/pokemon-horizons-episode-13-hindi-dub/",
            14 to "https://blisseyhusband.net/pokemon-horizons-episode-14-hindi-dub/",
            15 to "https://blisseyhusband.net/pokemon-horizons-episode-15-hindi-dub/",
            16 to "https://blisseyhusband.net/pokemon-horizons-episode-16-hindi-dub/",
        ),
    )

    // Dedicated Hindi Season index page URLs for dynamic fallback scraping
    private val SEASON_PAGE_URLS = mapOf(
        1 to "$BASE_URL/indigo-league-hindi/",
        2 to "$BASE_URL/pokemon-the-series-sun-moon-ultra-legends/pokemon-adventures-on-the-orange-islands-multi-audio/",
        3 to "$BASE_URL/pokemon-the-series-sun-moon-ultra-legends/pokemon-season-3-the-johto-journeys-english-hindi-multi-dub/",
        4 to "$BASE_URL/pokemon-the-series-sun-moon-ultra-legends/pokemon-season-4-johto-league-champions-english-hindi-multi-dub/",
        5 to "$BASE_URL/pokemon-the-series-sun-moon-ultra-legends/pokemon-season-5-master-quest-dual-audio-english-hindi/",
        6 to "$BASE_URL/pokemon-the-series-sun-moon-ultra-legends/pokemon-season-6-advanced-english-hindi-multi-dub/",
        7 to "$BASE_URL/pokemon-the-series-sun-moon-ultra-legends/pokemon-season-7-advanced-challenge-english-hindi-multi-dub/",
        8 to "$BASE_URL/pokemon-the-series-sun-moon-ultra-legends/pokemon-season-8-advanced-battle-english-hindi-multi-dub/",
        9 to "$BASE_URL/pokemon-the-series-sun-moon-ultra-legends/pokemon-season-9-battle-frontier-english-hindi-multi-dub/",
        10 to "$BASE_URL/pokemon-the-series-diamond-and-pearl-season-10-hindi-dub/",
        11 to "$BASE_URL/pokemon-dp-battle-dimension",
        12 to "$BASE_URL/pokemon-dp-galactic-battles",
        13 to "$BASE_URL/diamond-and-pearl-sinnoh-league-victors",
        14 to "$BASE_URL/pokemon-black-white",
        15 to "$BASE_URL/pokemon-black-white-rival-destinies",
        16 to "$BASE_URL/pokemon-black-white-adventures-in-unova-and-beyond",
        17 to "$BASE_URL/pokemon-the-series-xy-kalos-journeys-hindi-season-17-episodes/",
        18 to "$BASE_URL/pokemon-the-series-xy-kalos-quest-hindi-season-18-episodes/",
        19 to "$BASE_URL/pokemon-the-series-xyz",
        20 to "$BASE_URL/pokemon-sun-moon-hindi-dub/",
        21 to "$BASE_URL/pokemon-sun-moon-ultra-adventures-uncut/",
        22 to "$BASE_URL/pokemon-sun-moon-ultra-legends-uncut/",
        23 to "$BASE_URL/pokemon-the-series-pokemon-journeys-hindi-season-23-episodes/",
        24 to "$BASE_URL/pokemon-the-series-pokemon-master-journeys-hindi-season-24-episodes/",
        25 to "$BASE_URL/pokemon-the-series-pokemon-ultimate-journeys-hindi-season-25-episodes/",
        26 to "$BASE_URL/pokemon-horizons-hindi-dub/",
        27 to "$BASE_URL/pokmon-horizons-season-2-the-search-for-laqua/"
    )

    fun getSeasonAndRelativeEpisode(anilistId: Int, episode: Int, animeTitle: String? = null): Pair<Int, Int> {
        val titleLower = animeTitle?.lowercase() ?: ""
        return when (anilistId) {
            527 -> {
                // Original 1997 series
                when {
                    episode <= 82 -> 1 to episode
                    episode <= 118 -> 2 to (episode - 82)
                    episode <= 159 -> 3 to (episode - 118)
                    episode <= 211 -> 4 to (episode - 159)
                    else -> 5 to (episode - 211)
                }
            }
            1564 -> {
                // Advanced Generation (Ruby/Sapphire / S6-S9)
                when {
                    episode <= 40 -> 6 to episode
                    episode <= 92 -> 7 to (episode - 40)
                    episode <= 145 -> 8 to (episode - 92)
                    else -> 9 to (episode - 145)
                }
            }
            1565 -> {
                // Diamond & Pearl (S10-S13 / 191 eps)
                when {
                    episode <= 52 -> 10 to episode
                    episode <= 104 -> 11 to (episode - 52)
                    episode <= 157 -> 12 to (episode - 104)
                    else -> 13 to (episode - 157)
                }
            }
            9107 -> {
                // Best Wishes! (84 eps on AniList)
                when {
                    episode <= 48 -> 14 to episode
                    else -> 15 to (episode - 48)
                }
            }
            14093 -> {
                // Best Wishes! Season 2 / Rival Destinies
                15 to episode
            }
            17115 -> {
                // Best Wishes! Season 2: Episode N -> S16 Part 1
                16 to episode
            }
            17873, 20570 -> {
                // Best Wishes! Season 2: Decolora Adventure -> S16 Part 2
                16 to (episode + 14)
            }
            19291 -> {
                // XY (93 eps)
                when {
                    episode <= 48 -> 17 to episode
                    else -> 18 to (episode - 48)
                }
            }
            21356 -> {
                // XYZ
                19 to episode
            }
            97634 -> {
                // Sun & Moon (146 eps)
                when {
                    episode <= 43 -> 20 to episode
                    episode <= 91 -> 21 to (episode - 43)
                    else -> 22 to (episode - 91)
                }
            }
            112151, 112153 -> {
                // Journeys (136 eps)
                when {
                    episode <= 48 -> 23 to episode
                    episode <= 90 -> 24 to (episode - 48)
                    else -> 25 to (episode - 90)
                }
            }
            158870, 160488 -> {
                // Aim to Be a Pokemon Master (11 eps / Ash finale) -> Season 25 conclusion
                25 to (42 + episode)
            }
            158871 -> {
                // Horizons: The Series
                when {
                    episode <= 45 -> 26 to episode
                    else -> 27 to (episode - 45)
                }
            }
            171616 -> {
                // Horizons Season 2 (The Search for Laqua / Terastal Debut)
                27 to episode
            }
            else -> {
                // Title-based keyword fallback
                when {
                    titleLower.contains("horizons") -> {
                        if (titleLower.contains("season 2") || titleLower.contains("laqua") || titleLower.contains("terastal") || episode > 45) {
                            27 to (if (episode > 45) episode - 45 else episode)
                        } else {
                            26 to episode
                        }
                    }
                    (titleLower.contains("diamond") && titleLower.contains("pearl")) || titleLower.contains("sinnoh") -> {
                        when {
                            episode <= 52 -> 10 to episode
                            episode <= 104 -> 11 to (episode - 52)
                            episode <= 157 -> 12 to (episode - 104)
                            else -> 13 to (episode - 157)
                        }
                    }
                    titleLower.contains("black") && titleLower.contains("white") || titleLower.contains("best wishes") || titleLower.contains("rival destinies") || titleLower.contains("unova") -> {
                        when {
                            titleLower.contains("rival destinies") -> 15 to episode
                            titleLower.contains("unova") || titleLower.contains("decolora") || titleLower.contains("episode n") -> 16 to episode
                            episode <= 48 -> 14 to episode
                            episode <= 97 -> 15 to (episode - 48)
                            else -> 16 to (episode - 97)
                        }
                    }
                    titleLower.contains("sun") && titleLower.contains("moon") -> {
                        when {
                            episode <= 43 -> 20 to episode
                            episode <= 91 -> 21 to (episode - 43)
                            else -> 22 to (episode - 91)
                        }
                    }
                    titleLower.contains("journeys") || titleLower.contains("master journeys") || titleLower.contains("ultimate journeys") -> {
                        when {
                            titleLower.contains("master journeys") -> 24 to episode
                            titleLower.contains("ultimate journeys") -> 25 to episode
                            episode <= 48 -> 23 to episode
                            episode <= 90 -> 24 to (episode - 48)
                            else -> 25 to (episode - 90)
                        }
                    }
                    titleLower.contains("xyz") -> 19 to episode
                    titleLower.contains("xy") -> {
                        when {
                            episode <= 48 -> 17 to episode
                            else -> 18 to (episode - 48)
                        }
                    }
                    titleLower.contains("advanced") || titleLower.contains("ruby") || titleLower.contains("sapphire") -> {
                        when {
                            episode <= 40 -> 6 to episode
                            episode <= 92 -> 7 to (episode - 40)
                            episode <= 145 -> 8 to (episode - 92)
                            else -> 9 to (episode - 145)
                        }
                    }
                    titleLower.contains("orange island") -> 2 to episode
                    titleLower.contains("johto") -> {
                        when {
                            episode <= 41 -> 3 to episode
                            episode <= 93 -> 4 to (episode - 41)
                            else -> 5 to (episode - 93)
                        }
                    }
                    else -> 1 to episode
                }
            }
        }
    }

    suspend fun extractPokemonStream(
        anilistId: Int,
        episode: Int = 1,
        animeTitle: String? = null
    ): AnimeDekhoStreamResult? = withContext(Dispatchers.IO) {
        try {
            if (!isPokemon(anilistId, animeTitle)) return@withContext null

            val targetUrl: String? = if (POKEMON_MOVIE_URLS.containsKey(anilistId)) {
                // 1. Check if it's a Movie
                POKEMON_MOVIE_URLS[anilistId]
            } else {
                // 2. Resolve TV Season & Episode URL
                val (seasonNum, relEpNum) = getSeasonAndRelativeEpisode(anilistId, episode, animeTitle)
                val epMap = SEASON_EPISODES[seasonNum]
                epMap?.get(relEpNum) ?: epMap?.get(episode) ?: fetchDynamicSeasonEpisodeUrl(seasonNum, relEpNum)
            }

            if (targetUrl.isNullOrEmpty()) return@withContext null

            // 3. Fetch episode / movie page and extract Videas embed iframe
            val pageHtml = fetchHtml(targetUrl, referer = "$BASE_URL/")
            if (pageHtml.isEmpty()) return@withContext null

            val iframePattern = Pattern.compile("<iframe[^>]+src=[\"']([^\"']*(?:videas|app\\.videas\\.fr)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
            var matcher = iframePattern.matcher(pageHtml)
            var videasEmbedUrl = ""
            if (matcher.find()) {
                videasEmbedUrl = matcher.group(1) ?: ""
            }

            if (videasEmbedUrl.isEmpty()) {
                val anyIframePattern = Pattern.compile("<iframe[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
                val anyMatcher = anyIframePattern.matcher(pageHtml)
                while (anyMatcher.find()) {
                    val ifr = anyMatcher.group(1) ?: continue
                    if (ifr.contains("videas", ignoreCase = true)) {
                        videasEmbedUrl = ifr
                        break
                    }
                }
            }

            if (videasEmbedUrl.isEmpty()) return@withContext null

            val providerLabel = "blisseyhusband (Videas)"
            val titleLabel = "Hindi (Videas)"

            // 4. Try Videas API-based extraction first
            val apiStream = extractVideasStream(videasEmbedUrl, targetUrl)
            if (apiStream != null) {
                return@withContext AnimeDekhoStreamResult(
                    hlsUrl = apiStream,
                    referer = "https://app.videas.fr/",
                    provider = providerLabel,
                    title = titleLabel
                )
            }

            // Fallback: Fetch Videas embed page and extract direct playlist.m3u8 if present
            val videasHtml = fetchHtml(videasEmbedUrl, referer = targetUrl)
            if (videasHtml.isNotEmpty()) {
                val m3u8Pattern = Pattern.compile("https?://[^\\s\"'<>]+?\\.m3u8[^\\s\"'<>]*", Pattern.CASE_INSENSITIVE)
                val m3u8Matcher = m3u8Pattern.matcher(videasHtml)
                if (m3u8Matcher.find()) {
                    val hlsUrl = m3u8Matcher.group(0) ?: return@withContext null
                    return@withContext AnimeDekhoStreamResult(
                        hlsUrl = hlsUrl,
                        referer = "https://app.videas.fr/",
                        provider = providerLabel,
                        title = titleLabel
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private suspend fun extractVideasStream(embedUrl: String, referer: String): String? = withContext(Dispatchers.IO) {
        try {
            val uuidPattern = Pattern.compile("(?:embed/media/|embed/|/v/|media/)([a-f0-9\\-]{32,36})", Pattern.CASE_INSENSITIVE)
            val uuidMatcher = uuidPattern.matcher(embedUrl)
            var embedId = if (uuidMatcher.find()) uuidMatcher.group(1) else null

            if (embedId.isNullOrEmpty()) {
                val html = fetchHtml(embedUrl, referer = referer)
                val uidPattern = Pattern.compile("[\"'](?:uid|asset_id|embed_id)[\"']\\s*:\\s*[\"']([a-f0-9\\-]{32,36})[\"']", Pattern.CASE_INSENSITIVE)
                val uidMatcher = uidPattern.matcher(html)
                if (uidMatcher.find()) {
                    embedId = uidMatcher.group(1)
                }
            }

            if (embedId.isNullOrEmpty()) return@withContext null

            // 1. GET https://app.videas.fr/api/visitor/embed/<embedId>/
            val getReq = Request.Builder()
                .url("https://app.videas.fr/api/visitor/embed/$embedId/")
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", embedUrl)
                .addHeader("Accept", "application/json")
                .build()

            var playbackToken: String? = null
            var sessionId: String? = null
            var assetId: String = embedId

            client.newCall(getReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    if (body.isNotEmpty()) {
                        val json = JSONObject(body)
                        playbackToken = json.optString("playback_token", "")
                        sessionId = json.optString("session_id", "")
                        val optAsset = json.optString("asset_id", "")
                        if (optAsset.isNotEmpty()) assetId = optAsset
                    }
                }
            }

            if (playbackToken.isNullOrEmpty() || sessionId.isNullOrEmpty()) return@withContext null

            // 2. POST https://app.videas.fr/api/visitor/playback/info/
            val postJson = JSONObject().apply {
                put("asset_id", assetId)
                put("playback_token", playbackToken)
                put("session_id", sessionId)
            }
            val postReq = Request.Builder()
                .url("https://app.videas.fr/api/visitor/playback/info/")
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Referer", embedUrl)
                .post(postJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(postReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    if (body.isNotEmpty()) {
                        val json = JSONObject(body)
                        val hlsObj = json.optJSONObject("hls_url")
                        var rawUrl = hlsObj?.optString("url", "") ?: json.optString("hls_url", "")
                        if (rawUrl.isNotEmpty()) {
                            if (rawUrl.startsWith("/")) {
                                rawUrl = "https://app.videas.fr$rawUrl"
                            }
                            return@withContext rawUrl
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    private suspend fun fetchDynamicSeasonEpisodeUrl(seasonNum: Int, episodeNum: Int): String? = withContext(Dispatchers.IO) {
        try {
            val seasonUrl = SEASON_PAGE_URLS[seasonNum] ?: return@withContext null
            val html = fetchHtml(seasonUrl, referer = "$BASE_URL/")
            if (html.isEmpty()) return@withContext null

            val linkPattern = Pattern.compile("<a[^>]+href=[\"'](https?://blisseyhusband\\.net/[^\"']+)[\"'][^>]*>(.*?)</a>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
            val matcher = linkPattern.matcher(html)
            while (matcher.find()) {
                val href = matcher.group(1) ?: continue
                val text = matcher.group(2) ?: ""
                val epMatch = Pattern.compile("(?:episode|ep\\.?)\\s*(\\d+)", Pattern.CASE_INSENSITIVE).matcher(text)
                if (epMatch.find()) {
                    val ep = epMatch.group(1)?.toIntOrNull() ?: continue
                    if (ep == episodeNum) {
                        return@withContext href
                    }
                }
            }
        } catch (_: Exception) {}
        return@withContext null
    }

    private suspend fun fetchHtml(url: String, referer: String? = null): String = withContext(Dispatchers.IO) {
        try {
            val builder = Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
            if (referer != null) {
                builder.addHeader("Referer", referer)
            }
            client.newCall(builder.build()).execute().use { resp ->
                if (resp.isSuccessful) {
                    return@withContext resp.body?.string() ?: ""
                }
            }
        } catch (_: Exception) {}
        return@withContext ""
    }
}
