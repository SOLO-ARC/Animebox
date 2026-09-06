package com.lagradost.cloudstream3.ui.animebox.api

object AnimeMovieTmdbMapping {

    private val staticMovieMappings = mapOf(
        // === CRAYON SHIN-CHAN MOVIES ===
        8358 to 128868,    // 1993: Action Mask vs. Leotard Devil
        3745 to 128869,    // 1994: The Hidden Treasure of the Buri Buri Kingdom
        6217 to 128870,    // 1995: Unkokusai's Ambition
        8359 to 128871,    // 1996: Great Adventure In Henderland
        6460 to 89458,     // 1997: Pursuit of the Balls of Darkness
        8360 to 128875,    // 1998: Blitzkrieg! Pig's Hoof's Secret Mission
        8361 to 128878,    // 1999: Explosion! The Hot Spring's Feel Good Final Battle
        8362 to 117087,    // 2000: Jungle That Invites Storm
        2450 to 37280,     // 2001: The Adult Empire Strikes Back
        3744 to 117084,    // 2002: The Battle of the Warring States
        8363 to 103124,    // 2003: Yakiniku Road of Honor
        8364 to 160446,    // 2004: The Kasukabe Boys of the Evening Sun
        8365 to 128881,    // 2005: The Legend Called Buri Buri: 3 Minutes Charge
        8366 to 128883,    // 2006: The Legend Called: Dance! Amigo!
        2172 to 128884,    // 2007: The Singing Buttocks Bomb
        8367 to 128867,    // 2008: The Hero of Kinpoko
        8368 to 128887,    // 2009: Roar! Kasukabe Animal Kingdom
        8369 to 117352,    // 2010: Super-Dimension! The Storm Called My Bride
        10116 to 163754,   // 2011: Operation Golden Spy
        12499 to 163772,   // 2012: Me and the Space Princess
        17113 to 163786,   // 2013: Very Tasty! B-class Gourmet Survival!!
        20535 to 305143,   // 2014: Intense Battle! Robo Dad Strikes Back
        98744 to 371236,   // 2015: My Moving Story! Cactus Large Attack!
        100289 to 424656,  // 2016: Fast Asleep! The Great Assault on Dreamy World!
        100290 to 453575,  // 2017: Invasion!! Alien Shiriri
        103690 to 507562,  // 2018: Burst Serving! Kung Fu Boys ~Ramen Rebellion~
        109931 to 596302,  // 2019: Honeymoon Hurricane ~The Lost Hiroshi~
        111312 to 731249,  // 2020: Crash! Scribble Kingdom and Almost Four Heroes
        127331 to 795564,  // 2021: Shrouded in Mystery! The Flowers of Tenkasu Academy
        142549 to 955666,  // 2022: The Tornado Legend of Ninja Mononoke
        158595 to 1059572, // 2023: New Dimension! 3D Movie: Flying Sushi Battle
        171145 to 1221404, // 2024: Our Dinosaur Diary
        185537 to 1404404, // 2025: Super Hot! The Spicy Kasukabe Dancers
        208829 to 1598766, // 2026: Very Bizarre! My Yokai Vacation

        // === DORAEMON MOVIES ===
        3727 to 64789,     // 1980: Nobita's Dinosaur
        2662 to 147575,    // 1981: Nobita and the Spaceblazer
        2671 to 184004,    // 1982: Nobita and the Haunts of Evil
        2667 to 147576,    // 1983: Nobita's Undersea Fortress
        2672 to 161699,    // 1984: Nobita's Great Adventure in the World of Magic
        2669 to 161704,    // 1985: Nobita's Little Star Wars
        2665 to 88625,     // 1986: Nobita and the Platoon of Iron Men
        2657 to 72565,     // 1987: Nobita and the Knights on Dinosaurs
        2658 to 136241,    // 1988: Nobita's Parallel Journey to the West
        2666 to 142527,    // 1989: Nobita and the Birth of Japan
        2677 to 161707,    // 1990: Nobita's Animal Planet
        2670 to 188403,    // 1991: Nobita in Dorabian Nights
        2661 to 134162,    // 1992: Nobita and the Kingdom of Clouds
        2659 to 161822,    // 1993: Nobita and the Tin Labyrinth
        2676 to 161715,    // 1994: Nobita's Three Visionary Swordsmen
        2648 to 280507,    // 1995: 2112: The Birth of Doraemon
        2674 to 162069,    // 1995: Nobita's Diary on the Creation of the World
        2675 to 106164,    // 1996: Nobita and the Galaxy Super-express
        2678 to 163394,    // 1997: Nobita and the Spiral City
        2651 to 326148,    // 1998: Doraemon Comes Back
        2664 to 105444,    // 1998: Nobita's Great Adventure in the South Seas
        2660 to 325567,    // 1999: Nobita's the Night Before a Wedding
        2668 to 162072,    // 1999: Nobita Drifts in the Universe
        2649 to 132382,    // 2000: A Grandmother's Recollections
        7045 to 31352,     // 2000: Nobita and the Legend of the Sun King
        2653 to 326236,    // 2001: Ganbare! Gian!!
        2654 to 162092,    // 2001: Nobita and the Winged Braves
        2652 to 326238,    // 2002: The Day When I Was Born
        2655 to 162095,    // 2002: Nobita and the Robot Kingdom
        2393 to 98613,     // 2003: Nobita and the Windmasters
        2656 to 192904,    // 2004: Nobita in the Wan-Nyan Spacetime Odyssey
        2392 to 70841,     // 2006: Nobita's Dinosaur 2006
        2673 to 52795,     // 2007: Nobita's New Great Adventure Into the Underworld
        5096 to 136686,    // 2008: Nobita and the Green Giant Legend
        6930 to 95935,     // 2009: The New Record of Nobita's Spaceblazer
        6988 to 86184,     // 2010: Nobita's Great Battle of the Mermaid King
        10534 to 137227,   // 2011: Nobita and the New Steel Troops: Winged Angels
        11053 to 160642,   // 2012: Nobita and the Island of Miracles
        15925 to 218726,   // 2013: Nobita's Secret Gadget Museum
        19645 to 285812,   // 2014: New Nobita's Great Demon ~Peko and the 5 Explorers~
        20515 to 265712,   // 2014: Stand by Me Doraemon
        100291 to 350650,  // 2015: Nobita and the Space Heroes
        87438 to 384345,   // 2016: Nobita and the Birth of Japan 2016
        99085 to 462677,   // 2017: Nobita's Great Adventure in the Antarctic Kachi Kochi
        99084 to 495925,   // 2018: Nobita's Treasure Island
        106493 to 575222,  // 2019: Nobita's Chronicle of the Moon Exploration
        113669 to 682153,  // 2020: Nobita's New Dinosaur
        122575 to 728754,  // 2020: Stand by Me Doraemon 2
        126783 to 782054,  // 2022: Nobita's Little Star Wars 2021
        151896 to 1030587, // 2023: Nobita's Sky Utopia
        166976 to 1148677, // 2024: Nobita's Earth Symphony
        182333 to 1372489, // 2025: Nobita's Art World Tales
        198368 to 1542261, // 2026: New Nobita and the Castle of the Undersea Devil

        // === OTHER POPULAR MOVIES ===
        20954 to 378064,   // 2016: A Silent Voice (Koe no Katachi)
        21519 to 372058,   // 2016: Your Name. (Kimi no Na wa.)
        106286 to 568160,  // 2019: Weathering With You (Tenki no Ko)
        142770 to 916224,  // 2022: Suzume (Suzume no Tojimari)
        99750 to 504253,   // 2018: I Want to Eat Your Pancreas (Kimi no Suizou wo Tabetai)
        178788 to 1311031, // 2025: Demon Slayer: Kimetsu no Yaiba Infinity Castle
        112151 to 635302,  // 2020: Demon Slayer: Kimetsu no Yaiba - The Movie: Mugen Train
        131573 to 810693,  // 2021: Jujutsu Kaisen 0
        100869 to 508883,  // 2023: The Boy and the Heron (Kimitachi wa Dou Ikiru ka)
        175373 to 1244811, // 2024: Look Back
        199 to 129,        // 2001: Spirited Away (Sen to Chihiro no Kamikakushi)
        431 to 4935,       // 2004: Howl's Moving Castle (Howl no Ugoku Shiro)
        164 to 128,        // 1997: Princess Mononoke (Mononoke Hime)
        523 to 8392,       // 1988: My Neighbor Totoro (Tonari no Totoro)
        16662 to 149870,   // 2013: The Wind Rises (Kaze Tachinu)
        379 to 18148,      // 2007: 5 Centimeters per Second
        16782 to 198375,   // 2013: The Garden of Words
        578 to 12477,      // 1988: Grave of the Fireflies
        47 to 149,         // 1988: Akira
        437 to 10494,      // 1997: Perfect Blue
        12355 to 110420,   // 2012: Wolf Children
        2236 to 14069,     // 2006: The Girl Who Leapt Through Time
        21403 to 417830,   // 2017: Sword Art Online The Movie: Ordinal Scale
        141350 to 900667,  // 2022: One Piece Film: Red
        105333 to 568012,  // 2019: One Piece: Stampede
        21481 to 363612,   // 2016: One Piece Film: Gold
        12859 to 149397,   // 2012: One Piece Film: Z
        4155 to 21138,     // 2009: One Piece Film: Strong World
        116674 to 736074,  // 2021: Evangelion: 3.0+1.0 Thrice Upon a Time
        2890 to 18491,     // 2007: Evangelion: 1.0 You Are (Not) Alone
        4789 to 24188,     // 2009: Evangelion: 2.0 You Can (Not) Advance
        10521 to 109088,   // 2012: Evangelion: 3.0 You Can (Not) Redo

        // === DETECTIVE CONAN (CASE CLOSED) MOVIES ===
        779 to 21422,      // 1997: The Time-Bombed Skyscraper (M01)
        780 to 21452,      // 1998: The Fourteenth Target (M02)
        781 to 28808,      // 1999: The Last Wizard of the Century (M03)
        1363 to 20677,     // 2000: Captured in Her Eyes (M04)
        1364 to 32022,     // 2001: Countdown to Heaven (M05)
        1365 to 26662,     // 2002: The Phantom of Baker Street (M06)
        1366 to 39202,     // 2003: Crossroad in the Ancient Capital (M07)
        1367 to 39203,     // 2004: Magician of the Silver Sky (M08)
        1505 to 39204,     // 2005: Strategy Above the Depths (M09)
        9785 to 572707,    // 2005: Conan vs. Kid - Shark & Jewel
        1506 to 39205,     // 2006: The Private Eyes' Requiem (M10)
        2171 to 39206,     // 2007: Jolly Roger in the Deep Azure (M11)
        4447 to 39207,     // 2008: Full Score of Fear (M12)
        5460 to 28764,     // 2009: The Raven Chaser (M13)
        6467 to 97375,     // 2010: The Lost Ship in the Sky (M14)
        9963 to 77617,     // 2011: Quarter of Silence (M15)
        12117 to 122583,   // 2012: The Eleventh Striker (M16)
        14735 to 228805,   // 2013: Private Eye in the Distant Sea (M17)
        18429 to 239529,   // 2013: Lupin the Third vs. Detective Conan: The Movie
        20546 to 257512,   // 2014: Dimensional Sniper (M18)
        21646 to 316873,   // 2015: Sunflowers of Inferno (M19)
        21470 to 374856,   // 2016: The Darkest Nightmare (M20)
        98222 to 438058,   // 2017: The Crimson Love Letter (M21)
        100653 to 493006,  // 2018: Zero the Enforcer (M22)
        106206 to 566555,  // 2019: The Fist of Blue Sapphire (M23)
        113653 to 662638,  // 2021: The Scarlet Bullet (M24)
        131770 to 801293,  // 2021: The Scarlet Alibi
        142219 to 903939,  // 2022: The Bride of Halloween (M25)
        156841 to 1047041, // 2023: Black Iron Submarine (M26)
        158997 to 1058906, // 2023: Black Iron Mystery Train
        169754 to 1209217, // 2024: The Million-Dollar Pentagram (M27)
        184369 to 1244064, // 2024: Detective Conan vs. Kid the Phantom Thief
        185212 to 1396965, // 2025: Detective Conan: One-Eyed Flashback (M28)
        198369 to 1545621, // 2026: Detective Conan: Fallen Angel of the Highway (M29)

        // === POKÉMON MOVIES ===
        528 to 10228,      // 1998: Pokémon: The First Movie (M01)
        1117 to 12599,     // 1999: Pokémon the Movie 2000 (M02)
        1118 to 10991,     // 2000: Pokémon 3: The Movie (M03)
        1119 to 12600,     // 2001: Pokémon 4Ever (M04)
        1120 to 33875,     // 2002: Pokémon Heroes (M05)
        1121 to 36218,     // 2003: Pokémon: Jirachi Wish Maker (M06)
        1122 to 34065,     // 2004: Pokémon: Destiny Deoxys (M07)
        1526 to 34067,     // 2005: Pokémon: Lucario and the Mystery of Mew (M08)
        2201 to 16808,     // 2006: Pokémon Ranger and the Temple of the Sea (M09)
        2847 to 25961,     // 2007: Pokémon: The Rise of Darkrai (M10)
        4026 to 47292,     // 2008: Pokémon: Giratina and the Sky Warrior (M11)
        6178 to 39057,     // 2009: Pokémon: Arceus and the Jewel of Life (M12)
        7695 to 50087,     // 2010: Pokémon: Zoroark - Master of Illusions (M13)
        9917 to 88557,     // 2011: Pokémon the Movie: White - Victini and Zekrom (M14B)
        10740 to 115223,   // 2011: Pokémon the Movie: Black - Victini and Reshiram (M14A)
        12671 to 150213,   // 2012: Pokémon the Movie: Kyurem vs. the Sword of Justice (M15)
        16680 to 227679,   // 2013: Pokémon the Movie: Genesect and the Legend Awakened (M16)
        20644 to 303903,   // 2014: Pokémon the Movie: Diancie and the Cocoon of Destruction (M17)
        21266 to 350499,   // 2015: Pokémon the Movie: Hoopa and the Clash of Ages (M18)
        21651 to 382190,   // 2016: Pokémon the Movie: Volcanion and the Mechanical Marvel (M19)
        98298 to 436931,   // 2017: Pokémon the Movie: I Choose You! (M20)
        100744 to 494407,  // 2018: Pokémon the Movie: The Power of Us (M21)
        106287 to 571891,  // 2019: Pokémon the Movie: Mewtwo Strikes Back - Evolution (M22 3D)
        114564 to 662708,  // 2020: Pokémon the Movie: Secrets of the Jungle (M23)

        // === YO-KAI WATCH MOVIES ===
        20936 to 358651,   // 2014: Yo-kai Watch: The Movie
        20937 to 412869,   // 2015: Yo-kai Watch The Movie 2: Lord Enma and the Stories
        121663 to 433145,  // 2016: Yo-kai Watch: The Flying Whale & the Double World
        114814 to 545841,  // 2017: Yo-kai Watch Shadowside: The Return of the Oni King
        103050 to 550104,  // 2018: Yo-kai Watch: Friends Forever
        113273 to 657355,  // 2019: Yo-kai Watch Jam: Yo-kai Academy Y
        145958 to 1356044, // 2021: Yo-kai Watch the Movie: How Nate and I Met Nyan!
        157638 to 1068525, // 2023: Yo-kai Watch: Jibanyan vs. Komasan

        // === DRAGON BALL MOVIES & SPECIALS ===
        502 to 39144,      // 1986: Dragon Ball: Curse of the Blood Rubies
        891 to 39145,      // 1987: Dragon Ball: Sleeping Princess in Devil's Castle
        892 to 116776,     // 1988: Dragon Ball: Mystical Adventure
        894 to 28609,      // 1989: Dragon Ball Z: Dead Zone
        895 to 39100,      // 1990: Dragon Ball Z: The World's Strongest
        896 to 39101,      // 1990: Dragon Ball Z: The Tree of Might
        897 to 39102,      // 1991: Dragon Ball Z: Lord Slug
        898 to 24752,      // 1991: Dragon Ball Z: Cooler's Revenge
        899 to 39103,      // 1992: Dragon Ball Z: The Return of Cooler
        900 to 39104,      // 1992: Dragon Ball Z: Super Android 13!
        901 to 34433,      // 1993: Dragon Ball Z: Broly - The Legendary Super Saiyan
        902 to 39105,      // 1993: Dragon Ball Z: Bojack Unbound
        903 to 44251,      // 1994: Dragon Ball Z: Broly - Second Coming
        904 to 39106,      // 1994: Dragon Ball Z: Bio-Broly
        905 to 39107,      // 1995: Dragon Ball Z: Fusion Reborn
        906 to 39108,      // 1995: Dragon Ball Z: Wrath of the Dragon
        893 to 39148,      // 1996: Dragon Ball: The Path to Power
        987 to 18095,      // 1997: Dragon Ball GT: A Hero's Legacy
        14837 to 126963,   // 2013: Dragon Ball Z: Battle of Gods
        20778 to 303857,   // 2015: Dragon Ball Z: Resurrection 'F'
        101302 to 503314,  // 2018: Dragon Ball Super: Broly
        133898 to 610150,  // 2022: Dragon Ball Super: SUPER HERO

        // === NARUTO & BORUTO MOVIES ===
        442 to 16907,      // 2004: Naruto the Movie: Ninja Clash in the Land of Snow
        936 to 16910,      // 2005: Naruto the Movie: Legend of the Stone of Gelel
        2144 to 18861,     // 2006: Naruto the Movie: Guardians of the Crescent Moon Kingdom
        2472 to 20982,     // 2007: Naruto Shippuden the Movie
        4437 to 17581,     // 2008: Naruto Shippuden the Movie: Bonds
        6325 to 36728,     // 2009: Naruto Shippuden the Movie: The Will of Fire
        8246 to 50723,     // 2010: Naruto Shippuden the Movie: The Lost Tower
        10589 to 75624,    // 2011: Naruto the Movie: Blood Prison
        13667 to 118406,   // 2012: Road to Ninja: Naruto the Movie
        16870 to 317442,   // 2014: The Last: Naruto the Movie
        21220 to 347201,   // 2015: Boruto: Naruto the Movie

        // === BEYBLADE MOVIES ===
        1670 to 166823,    // 2002: Beyblade the Movie: Fierce Battle (Takao VS Daichi)
        8245 to 417179     // 2010: Metal Fight Beyblade VS Taiyou: Sol Blaze
    )

    private val dynamicMappingsCache = java.util.concurrent.ConcurrentHashMap<Int, Int>()
    private val dynamicTypeCache = java.util.concurrent.ConcurrentHashMap<Int, String>()
    private val dynamicLogoCache = java.util.concurrent.ConcurrentHashMap<Int, String>()

    private const val SUPABASE_URL = "https://okiuzwsldqjrtuyqylhx.supabase.co"
    private const val SUPABASE_KEY = "sb_publishable_O6CrSYrDu-GilobI41fSMg_DG7IdSbf"

    fun getMovieTmdbId(anilistId: Int): Int? = dynamicMappingsCache[anilistId] ?: staticMovieMappings[anilistId]

    fun isStaticMovie(anilistId: Int): Boolean = staticMovieMappings.containsKey(anilistId) || dynamicTypeCache[anilistId] == "movie"

    suspend fun getTmdbMapping(anilistId: Int): Int? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        dynamicMappingsCache[anilistId]?.let { return@withContext it }

        try {
            val url = "$SUPABASE_URL/rest/v1/anime_tmdb_mappings?anilist_id=eq.$anilistId&select=tmdb_id,tmdb_type,logo_url"
            val req = okhttp3.Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_KEY")
                .build()
            okhttp3.OkHttpClient().newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    if (body.isNotBlank()) {
                        val arr = org.json.JSONArray(body)
                        if (arr.length() > 0) {
                            val obj = arr.getJSONObject(0)
                            val tId = obj.optInt("tmdb_id", 0)
                            val tType = obj.optString("tmdb_type", "tv")
                            val lUrl = if (obj.has("logo_url") && !obj.isNull("logo_url")) obj.getString("logo_url").trim() else ""
                            if (tId > 0) {
                                dynamicMappingsCache[anilistId] = tId
                                dynamicTypeCache[anilistId] = tType
                                if (lUrl.isNotEmpty()) {
                                    dynamicLogoCache[anilistId] = lUrl
                                }
                                return@withContext tId
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return@withContext staticMovieMappings[anilistId]
    }

    fun getDynamicTmdbType(anilistId: Int): String? = dynamicTypeCache[anilistId]

    fun getDynamicLogo(anilistId: Int): String? = dynamicLogoCache[anilistId]

    fun setDynamicMapping(anilistId: Int, tmdbId: Int, type: String = "movie") {
        dynamicMappingsCache[anilistId] = tmdbId
        dynamicTypeCache[anilistId] = type
    }

    fun setDynamicType(anilistId: Int, type: String) {
        dynamicTypeCache[anilistId] = type
    }
}
