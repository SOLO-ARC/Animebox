package com.lagradost.cloudstream3.ui.animebox.api

import androidx.annotation.DrawableRes
import com.lagradost.cloudstream3.R

data class AnimeStudioInfo(
    val name: String,
    val queryName: String,
    @DrawableRes val logoResId: Int? = null,
    val isPopular: Boolean = false
)

data class StudioCardItem(
    val name: String,
    val queryName: String,
    val posterUrl: String,
    val popularAnimeTitle: String,
    val releaseYear: Int,
    val themeColor: Long = 0xFF6366F1
)

object AnimeStudioRepository {

    // 13 studios with high-res logos (arranged with best/most prominent first)
    val LOGO_STUDIOS = listOf(
        AnimeStudioInfo(name = "MAPPA", queryName = "MAPPA", logoResId = R.drawable.ic_studio_mappa, isPopular = true),
        AnimeStudioInfo(name = "ufotable", queryName = "ufotable", logoResId = R.drawable.ic_studio_ufotable, isPopular = true),
        AnimeStudioInfo(name = "WIT Studio", queryName = "WIT Studio", logoResId = R.drawable.ic_studio_wit, isPopular = true),
        AnimeStudioInfo(name = "CloverWorks", queryName = "CloverWorks", logoResId = R.drawable.ic_studio_cloverworks, isPopular = true),
        AnimeStudioInfo(name = "Kyoto Animation", queryName = "Kyoto Animation", logoResId = R.drawable.ic_studio_kyoto_animation, isPopular = true),
        AnimeStudioInfo(name = "Bones", queryName = "Bones", logoResId = R.drawable.ic_studio_bones, isPopular = true),
        AnimeStudioInfo(name = "Madhouse", queryName = "Madhouse", logoResId = R.drawable.ic_studio_madhouse, isPopular = true),
        AnimeStudioInfo(name = "A-1 Pictures", queryName = "A-1 Pictures", logoResId = R.drawable.ic_studio_a1_pictures, isPopular = true),
        AnimeStudioInfo(name = "Toei Animation", queryName = "Toei Animation", logoResId = R.drawable.ic_studio_toei_animation, isPopular = true),
        AnimeStudioInfo(name = "Studio Pierrot", queryName = "Studio Pierrot", logoResId = R.drawable.ic_studio_pierrot, isPopular = true),
        AnimeStudioInfo(name = "Studio Ghibli", queryName = "Studio Ghibli", logoResId = R.drawable.ic_studio_ghibli, isPopular = true),
        AnimeStudioInfo(name = "OLM", queryName = "OLM", logoResId = R.drawable.ic_studio_olm, isPopular = true),
        AnimeStudioInfo(name = "Bandai Namco", queryName = "Bandai Namco Filmworks", logoResId = R.drawable.ic_studio_bandai_namco, isPopular = true)
    )

    // Curated high-res post-2005 AniList posters for the Studio Cards row (All 100% verified HTTP 200)
    val STUDIO_CARDS: List<StudioCardItem> = listOf(
        StudioCardItem(
            name = "MAPPA",
            queryName = "MAPPA",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx127230-DdP4vAdssLoz.png",
            popularAnimeTitle = "Chainsaw Man",
            releaseYear = 2022,
            themeColor = 0xFFDC2626
        ),
        StudioCardItem(
            name = "ufotable",
            queryName = "ufotable",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx21718-Hjj26Sapx1bd.jpg",
            popularAnimeTitle = "Fate/stay night [Heaven's Feel] II. lost butterfly",
            releaseYear = 2019,
            themeColor = 0xFF8B5CF6
        ),
        StudioCardItem(
            name = "WIT Studio",
            queryName = "WIT Studio",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx140960-Kb6R5nYQfjmP.jpg",
            popularAnimeTitle = "SPY x FAMILY",
            releaseYear = 2022,
            themeColor = 0xFF10B981
        ),
        StudioCardItem(
            name = "CloverWorks",
            queryName = "CloverWorks",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx130003-HTDmeL4RGeJ4.png",
            popularAnimeTitle = "BOCCHI THE ROCK!",
            releaseYear = 2022,
            themeColor = 0xFFDB2777
        ),
        StudioCardItem(
            name = "Kyoto Animation",
            queryName = "Kyoto Animation",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20954-sYRfE5jQRtSB.jpg",
            popularAnimeTitle = "A Silent Voice",
            releaseYear = 2016,
            themeColor = 0xFF0284C7
        ),
        StudioCardItem(
            name = "Bones",
            queryName = "Bones",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx21459-nYh85uj2Fuwr.jpg",
            popularAnimeTitle = "My Hero Academia",
            releaseYear = 2016,
            themeColor = 0xFFDC2626
        ),
        StudioCardItem(
            name = "Madhouse",
            queryName = "Madhouse",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx11061-y5gsT1hoHuHw.png",
            popularAnimeTitle = "Hunter x Hunter (2011)",
            releaseYear = 2011,
            themeColor = 0xFF059669
        ),
        StudioCardItem(
            name = "A-1 Pictures",
            queryName = "A-1 Pictures",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx151807-it355ZgzquUd.png",
            popularAnimeTitle = "Solo Leveling",
            releaseYear = 2024,
            themeColor = 0xFF2563EB
        ),
        StudioCardItem(
            name = "Toei Animation",
            queryName = "Toei Animation",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx21175-EH06qlfF8TnB.jpg",
            popularAnimeTitle = "Dragon Ball Super",
            releaseYear = 2015,
            themeColor = 0xFFEA580C
        ),
        StudioCardItem(
            name = "Studio Pierrot",
            queryName = "Studio Pierrot",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx1735-kGfVm0YqCPcu.png",
            popularAnimeTitle = "Naruto: Shippuden",
            releaseYear = 2007,
            themeColor = 0xFFF97316
        ),
        StudioCardItem(
            name = "Studio Ghibli",
            queryName = "Studio Ghibli",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx199-sWefXJvXkDOb.jpg",
            popularAnimeTitle = "Spirited Away",
            releaseYear = 2001,
            themeColor = 0xFF059669
        ),
        StudioCardItem(
            name = "OLM",
            queryName = "OLM",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx129201-HJBauga2be8I.png",
            popularAnimeTitle = "Summer Time Rendering",
            releaseYear = 2022,
            themeColor = 0xFF0D9488
        ),
        StudioCardItem(
            name = "Trigger",
            queryName = "Trigger",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx120377-ayZPoxiWt4Li.jpg",
            popularAnimeTitle = "Cyberpunk: Edgerunners",
            releaseYear = 2022,
            themeColor = 0xFFFF4500
        ),
        StudioCardItem(
            name = "CoMix Wave Films",
            queryName = "CoMix Wave",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx142770-dDaDIRnsv5jN.jpg",
            popularAnimeTitle = "Suzume",
            releaseYear = 2022,
            themeColor = 0xFF0284C7
        ),
        StudioCardItem(
            name = "Studio Bind",
            queryName = "Studio Bind",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx108465-1ANspF1EWyFx.jpg",
            popularAnimeTitle = "Mushoku Tensei: Jobless Reincarnation",
            releaseYear = 2021,
            themeColor = 0xFF4F46E5
        ),
        StudioCardItem(
            name = "Shaft",
            queryName = "Shaft",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx9756-QnUGwlwwnsuN.jpg",
            popularAnimeTitle = "Puella Magi Madoka Magica",
            releaseYear = 2011,
            themeColor = 0xFFEC4899
        ),
        StudioCardItem(
            name = "Production I.G",
            queryName = "Production I.G",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20464-ooZUyBe4ptp9.png",
            popularAnimeTitle = "HAIKYU!!",
            releaseYear = 2014,
            themeColor = 0xFF0891B2
        ),
        StudioCardItem(
            name = "Doga Kobo",
            queryName = "Doga Kobo",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx150672-WqmmwZ4nMzAy.png",
            popularAnimeTitle = "OSHI NO KO",
            releaseYear = 2023,
            themeColor = 0xFFEC4899
        ),
        StudioCardItem(
            name = "White Fox",
            queryName = "White Fox",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx21355-wRVUrGxpvIQQ.jpg",
            popularAnimeTitle = "Re:ZERO -Starting Life in Another World-",
            releaseYear = 2016,
            themeColor = 0xFF4338CA
        ),
        StudioCardItem(
            name = "David Production",
            queryName = "david production",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx14719-VT5dRzTBSZ0w.jpg",
            popularAnimeTitle = "JoJo's Bizarre Adventure (TV)",
            releaseYear = 2012,
            themeColor = 0xFFD97706
        ),
        StudioCardItem(
            name = "P.A. Works",
            queryName = "P.A.WORKS",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx6547-SYexAn5aFyss.png",
            popularAnimeTitle = "Angel Beats!",
            releaseYear = 2010,
            themeColor = 0xFF06B6D4
        ),
        StudioCardItem(
            name = "J.C.Staff",
            queryName = "J.C.STAFF",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx21804-As6tDLAvEvNY.jpg",
            popularAnimeTitle = "The Disastrous Life of Saiki K.",
            releaseYear = 2016,
            themeColor = 0xFF10B981
        ),
        StudioCardItem(
            name = "Sunrise",
            queryName = "Sunrise",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx1575-hsmWM2ydNm1m.jpg",
            popularAnimeTitle = "Code Geass: Lelouch of the Rebellion",
            releaseYear = 2006,
            themeColor = 0xFFB91C1C
        ),
        StudioCardItem(
            name = "Kinema Citrus",
            queryName = "Kinema Citrus",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx97986-TQ7dCgbS3y5s.jpg",
            popularAnimeTitle = "Made in Abyss",
            releaseYear = 2017,
            themeColor = 0xFF047857
        ),
        StudioCardItem(
            name = "8bit",
            queryName = "Eight Bit",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx101280-tDxCVJm714nt.jpg",
            popularAnimeTitle = "That Time I Got Reincarnated as a Slime",
            releaseYear = 2018,
            themeColor = 0xFF0EA5E9
        ),
        StudioCardItem(
            name = "LIDENFILMS",
            queryName = "LIDENFILMS",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx120120-cWDmnmeEntSe.jpg",
            popularAnimeTitle = "Tokyo Revengers",
            releaseYear = 2021,
            themeColor = 0xFFBE123C
        ),
        StudioCardItem(
            name = "Lerche",
            queryName = "Lerche",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx20755-dWrhs569YGUO.jpg",
            popularAnimeTitle = "Assassination Classroom",
            releaseYear = 2015,
            themeColor = 0xFF854D0E
        ),
        StudioCardItem(
            name = "Silver Link.",
            queryName = "SILVER LINK.",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx112301-f88Fs2es4pSr.jpg",
            popularAnimeTitle = "The Misfit of Demon King Academy",
            releaseYear = 2020,
            themeColor = 0xFF6D28D9
        ),
        StudioCardItem(
            name = "Science SARU",
            queryName = "Science SARU",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx171018-60q1B6GK2Ghb.jpg",
            popularAnimeTitle = "DAN DA DAN",
            releaseYear = 2024,
            themeColor = 0xFF10B981
        ),
        StudioCardItem(
            name = "Studio Deen",
            queryName = "Studio DEEN",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx21202-mPOr80AEjUcZ.png",
            popularAnimeTitle = "KONOSUBA -God's blessing on this wonderful world!",
            releaseYear = 2016,
            themeColor = 0xFF14B8A6
        ),
        StudioCardItem(
            name = "TMS Entertainment",
            queryName = "TMS Entertainment",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx105333-GybuoSoOZfpH.jpg",
            popularAnimeTitle = "Dr. STONE",
            releaseYear = 2019,
            themeColor = 0xFF475569
        ),
        StudioCardItem(
            name = "Orange",
            queryName = "Orange",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx107660-hgknnyaLchJW.png",
            popularAnimeTitle = "BEASTARS",
            releaseYear = 2019,
            themeColor = 0xFFF97316
        ),
        StudioCardItem(
            name = "Troyca",
            queryName = "Troyca",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/nx98389-xDRFlDQwu2I9.jpg",
            popularAnimeTitle = "Killing Bites",
            releaseYear = 2018,
            themeColor = 0xFF64748B
        ),
        StudioCardItem(
            name = "BUG FILMS",
            queryName = "BUG FILMS",
            posterUrl = "https://s4.anilist.co/file/anilistcdn/media/anime/cover/large/bx159831-cJUNqCqzuApc.png",
            popularAnimeTitle = "Zom 100: Bucket List of the Dead",
            releaseYear = 2023,
            themeColor = 0xFF84CC16
        )
    )

    // Complete list of animation studios for search screen tags
    val ALL_STUDIO_NAMES = listOf(
        "MAPPA",
        "ufotable",
        "WIT Studio",
        "CloverWorks",
        "Kyoto Animation",
        "Bones",
        "Madhouse",
        "A-1 Pictures",
        "Toei Animation",
        "Studio Pierrot",
        "Studio Ghibli",
        "OLM",
        "Bandai Namco Filmworks",
        "Production I.G",
        "Shaft",
        "Trigger",
        "J.C.Staff",
        "Doga Kobo",
        "CoMix Wave Films",
        "Studio Bind",
        "P.A. Works",
        "TMS Entertainment",
        "Sunrise",
        "Silver Link.",
        "Lerche",
        "Kinema Citrus",
        "8bit",
        "LIDENFILMS",
        "David Production",
        "Science SARU",
        "Orange",
        "Millepensee",
        "Studio Deen",
        "White Fox",
        "Brain's Base",
        "Tatsunoko Production",
        "Gonzo",
        "Gainax",
        "Manglobe",
        "feel.",
        "Passione",
        "ENGI",
        "Project No.9",
        "Bibury Animation Studios",
        "C-Station",
        "Troyca",
        "Studio VOLN",
        "BUG FILMS",
        "Geno Studio"
    ).distinct()

    fun getQueryForStudio(studioName: String): String {
        val s = studioName.trim()
        return when (s.lowercase()) {
            "8bit", "eight bit" -> "Eight Bit"
            "p.a. works", "p.a.works" -> "P.A.WORKS"
            "comix wave films", "comix wave" -> "CoMix Wave"
            "silver link.", "silver link" -> "SILVER LINK."
            "david production" -> "david production"
            "white fox" -> "WHITE FOX"
            "wit studio" -> "WIT STUDIO"
            "studio deen" -> "Studio DEEN"
            "j.c.staff", "j.c. staff" -> "J.C.STAFF"
            "cloverworks" -> "CloverWorks"
            "studio ghibli", "ghibli" -> "Studio Ghibli"
            "kyoto animation", "kyoani" -> "Kyoto Animation"
            "toei animation", "toei" -> "Toei Animation"
            "studio pierrot", "pierrot" -> "Studio Pierrot"
            "tms entertainment" -> "TMS Entertainment"
            "science saru" -> "Science SARU"
            "kinema citrus" -> "Kinema Citrus"
            "lidenfilms", "liden films" -> "LIDENFILMS"
            "bug films" -> "BUG FILMS"
            else -> s
        }
    }
}
