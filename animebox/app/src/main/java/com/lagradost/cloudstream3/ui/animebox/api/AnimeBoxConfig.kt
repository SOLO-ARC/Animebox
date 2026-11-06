package com.lagradost.cloudstream3.ui.animebox.api

/**
 * AnimeBox / FireFly Global Configuration
 *
 * Central configuration file for all external services (Supabase, TMDB, ImgBB).
 * Anyone deploying or building AnimeBox can simply fill in their credentials below,
 * and all features (streaming extractors, custom streams, episode covers, public subtitles,
 * server visibility toggles, Shinchan episodes, notifications, reports, tickets,
 * and community review comments) will work out of the box.
 */
object AnimeBoxConfig {

    /**
     * Supabase Project URL (e.g. "https://xxxxxxxxxxxxxxxxxxxx.supabase.co")
     * Obtain from Supabase Project Settings -> API.
     */
    const val SUPABASE_URL = "YOUR_SUPABASE_URL_HERE"

    /**
     * Supabase Public/Anon API Key (Publishable key)
     * Obtain from Supabase Project Settings -> API -> Project API keys -> anon / public.
     */
    const val SUPABASE_ANON_KEY = "YOUR_SUPABASE_ANON_KEY_HERE"

    /**
     * TheMovieDB (TMDB) v3 API Key
     * Required for fetching metadata, episode titles, backdrops, and anime movie mappings.
     * Obtain a free API key at: https://www.themoviedb.org/settings/api
     */
    const val TMDB_API_KEY = "YOUR_TMDB_API_KEY_HERE"

    /**
     * ImgBB API Key
     * Used for uploading user ticket attachments, issue report screenshots, and custom covers.
     * Obtain a free API key at: https://api.imgbb.com/
     */
    const val IMGBB_API_KEY = "YOUR_IMGBB_API_KEY_HERE"

    /**
     * Admin account credentials for reviewing and moderating community comments.
     * Create an account in your Supabase Auth dashboard with this email, or customize as needed.
     */
    const val ADMIN_EMAIL = "admin@firefly.anime"
    const val ADMIN_DEFAULT_PASS = "YOUR_ADMIN_PASSWORD_HERE"
}

