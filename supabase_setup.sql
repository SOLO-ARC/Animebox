-- =====================================================================
-- AnimeBox / FireFly Full Supabase Database Schema & Setup Script
-- =====================================================================
-- Description:
--   Ready-to-run SQL script that recreates 100% of the Supabase backend
--   for the AnimeBox (FireFly) Android application.
--
-- Instructions:
--   1. Open your Supabase Dashboard: https://supabase.com/dashboard
--   2. Go to "SQL Editor" on the left menu.
--   3. Paste this entire script into a new query and click "Run".
--   4. Copy your Project URL & Anon Key from Settings -> API and paste
--      them into `AnimeBoxConfig.kt` in the Android project.
-- =====================================================================

-- ---------------------------------------------------------------------
-- 0. EXTENSIONS
-- ---------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ---------------------------------------------------------------------
-- 1. TABLE: anime_tmdb_mappings
-- Used in AnimeMovieTmdbMapping.kt to map AniList anime IDs to TMDB IDs,
-- media type (tv/movie), and clear logos.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.anime_tmdb_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    anilist_id BIGINT NOT NULL UNIQUE,
    tmdb_id BIGINT NOT NULL,
    tmdb_type TEXT NOT NULL DEFAULT 'tv', -- 'tv' or 'movie'
    logo_url TEXT,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_anime_tmdb_mappings_anilist_id ON public.anime_tmdb_mappings(anilist_id);

ALTER TABLE public.anime_tmdb_mappings ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow public read anime_tmdb_mappings" 
    ON public.anime_tmdb_mappings FOR SELECT TO anon, authenticated USING (true);

CREATE POLICY "Allow public insert/update anime_tmdb_mappings" 
    ON public.anime_tmdb_mappings FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);


-- ---------------------------------------------------------------------
-- 2. TABLE: anime_views & RPC: increment_anime_views
-- Used in AnimeViewCountManager.kt for atomic view tracking per anime.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.anime_views (
    anilist_id BIGINT PRIMARY KEY,
    views BIGINT DEFAULT 1 NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

ALTER TABLE public.anime_views ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow public read anime_views" 
    ON public.anime_views FOR SELECT TO anon, authenticated USING (true);

CREATE POLICY "Allow public write anime_views" 
    ON public.anime_views FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

-- RPC Function called by Android client via /rest/v1/rpc/increment_anime_views
CREATE OR REPLACE FUNCTION public.increment_anime_views(p_anilist_id BIGINT)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    INSERT INTO public.anime_views (anilist_id, views, updated_at)
    VALUES (p_anilist_id, 1, timezone('utc'::text, now()))
    ON CONFLICT (anilist_id)
    DO UPDATE SET 
        views = public.anime_views.views + 1,
        updated_at = timezone('utc'::text, now());
END;
$$;


-- ---------------------------------------------------------------------
-- 3. TABLE: custom_streams
-- Used in CustomStreamManager.kt for manual overrides and custom stream
-- links (e.g. Hindi dubs, OkRu, Byse, direct mp4/m3u8).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.custom_streams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    anilist_id BIGINT NOT NULL,
    episode_number INT NOT NULL,
    language TEXT NOT NULL DEFAULT 'Hindi',
    server_name TEXT NOT NULL DEFAULT 'OkRu',
    video_url TEXT NOT NULL,
    title TEXT,
    quality TEXT DEFAULT '720p',
    is_active BOOLEAN DEFAULT true NOT NULL,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_custom_streams_lookup 
    ON public.custom_streams(anilist_id, episode_number, is_active);

ALTER TABLE public.custom_streams ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow public read custom_streams" 
    ON public.custom_streams FOR SELECT TO anon, authenticated USING (true);

CREATE POLICY "Allow public write custom_streams" 
    ON public.custom_streams FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);


-- ---------------------------------------------------------------------
-- 4. TABLE: anime_custom_episode_covers
-- Used in EpisodeCoverUploader.kt for custom screenshot covers.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.anime_custom_episode_covers (
    id UUID DEFAULT gen_random_uuid(),
    anilist_id BIGINT NOT NULL,
    episode_num INT NOT NULL,
    cover_url TEXT NOT NULL,
    anime_title TEXT,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    PRIMARY KEY (anilist_id, episode_num)
);

CREATE INDEX IF NOT EXISTS idx_anime_custom_episode_covers_lookup 
    ON public.anime_custom_episode_covers(anilist_id, episode_num);

ALTER TABLE public.anime_custom_episode_covers ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow public read custom episode covers" 
    ON public.anime_custom_episode_covers FOR SELECT TO anon, authenticated USING (true);

CREATE POLICY "Allow public upsert custom episode covers" 
    ON public.anime_custom_episode_covers FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);


-- ---------------------------------------------------------------------
-- 5. TABLE: public_subtitles
-- Used in PublicSubtitlesManager.kt & OnlineSubtitleSearcher.kt for
-- crowd-sourced / community VTT subtitles.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.public_subtitles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    anilist_id BIGINT NOT NULL,
    episode_num INT NOT NULL,
    anime_title TEXT NOT NULL,
    lang_code TEXT NOT NULL,
    lang_name TEXT NOT NULL,
    uploader_name TEXT NOT NULL DEFAULT 'Firefly User',
    vtt_content TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_public_subtitles_lookup 
    ON public.public_subtitles(anilist_id, episode_num);
CREATE INDEX IF NOT EXISTS idx_public_subtitles_title 
    ON public.public_subtitles(anime_title);

ALTER TABLE public.public_subtitles ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow public read subtitles" 
    ON public.public_subtitles FOR SELECT TO anon, authenticated USING (true);

CREATE POLICY "Allow public insert subtitles" 
    ON public.public_subtitles FOR INSERT TO anon, authenticated WITH CHECK (true);


-- ---------------------------------------------------------------------
-- 6. TABLE: app_audio_server_config
-- Used in ServerVisibilityConfig.kt for remote dynamic visibility toggles
-- for audio languages and streaming servers.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.app_audio_server_config (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    type TEXT NOT NULL, -- 'language' or 'server'
    key TEXT UNIQUE NOT NULL, -- e.g. 'hindi', 'english', 'vidhawk'
    display_name TEXT NOT NULL,
    category TEXT,
    is_enabled BOOLEAN DEFAULT true NOT NULL,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

ALTER TABLE public.app_audio_server_config ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow public read server config" 
    ON public.app_audio_server_config FOR SELECT TO anon, authenticated USING (true);

CREATE POLICY "Allow public write server config" 
    ON public.app_audio_server_config FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

-- Seed default languages and servers
INSERT INTO public.app_audio_server_config (type, key, display_name, category, is_enabled)
VALUES
    ('language', 'english', 'English', 'Audio Track', true),
    ('language', 'hindi', 'Hindi', 'Audio Track', true),
    ('language', 'french', 'French (VF)', 'Audio Track', true),
    ('language', 'italian', 'Italian (ITA)', 'Audio Track', true),
    ('language', 'spanish_latino', 'Spanish (Latino)', 'Audio Track', true),
    ('language', 'spanish_castellano', 'Spanish (Castellano)', 'Audio Track', true),
    ('language', 'german', 'German (Deutsch)', 'Audio Track', true)
ON CONFLICT (key) DO NOTHING;


-- ---------------------------------------------------------------------
-- 7. TABLE: shinchan_episodes
-- Used in ShinChanSupabaseManager.kt for full Shinchan episode catalog.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.shinchan_episodes (
    episode_number INT PRIMARY KEY,
    video_id TEXT NOT NULL,
    cover_image_url TEXT,
    mobile_url TEXT,
    web_url TEXT,
    type TEXT DEFAULT 'episode',
    views INT DEFAULT 0,
    source TEXT DEFAULT '999-present',
    is_active BOOLEAN DEFAULT true NOT NULL,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_shinchan_episodes_active 
    ON public.shinchan_episodes(is_active, episode_number);

ALTER TABLE public.shinchan_episodes ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow public read shinchan episodes" 
    ON public.shinchan_episodes FOR SELECT TO anon, authenticated USING (true);

CREATE POLICY "Allow public upsert shinchan episodes" 
    ON public.shinchan_episodes FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);


-- ---------------------------------------------------------------------
-- 8. TABLE: notifications
-- Used in SupabaseNotificationManager.kt for push and in-app alerts.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id TEXT, -- NULL for broadcast to all users, or user/profile id
    title TEXT NOT NULL,
    message TEXT NOT NULL,
    image_url TEXT,
    link TEXT,
    is_read BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_created 
    ON public.notifications(user_id, created_at DESC);

ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow public read notifications" 
    ON public.notifications FOR SELECT TO anon, authenticated USING (true);

CREATE POLICY "Allow public insert/update notifications" 
    ON public.notifications FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);


-- ---------------------------------------------------------------------
-- 9. TABLE: reports
-- Used in SupabaseReportManager.kt for user-submitted diagnostics & bugs.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target_id TEXT,
    type TEXT DEFAULT 'anime',
    user_name TEXT NOT NULL,
    user_id TEXT,
    email TEXT,
    anime_title TEXT,
    anilist_id BIGINT,
    episode_number TEXT,
    issue_type TEXT NOT NULL,
    description TEXT NOT NULL,
    status TEXT DEFAULT 'pending',
    admin_notes TEXT,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_reports_status_created 
    ON public.reports(status, created_at DESC);

ALTER TABLE public.reports ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow public submit report" 
    ON public.reports FOR INSERT TO anon, authenticated WITH CHECK (true);

CREATE POLICY "Allow public read reports" 
    ON public.reports FOR SELECT TO anon, authenticated USING (true);


-- ---------------------------------------------------------------------
-- 10. TABLE: anime_review_comments
-- Used in AnimeReviewCommentsManager.kt for episode and anime comments.
-- Supports nested comment threads and admin moderation.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.anime_review_comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    anilist_media_id BIGINT NOT NULL,
    anime_title TEXT,
    parent_id UUID REFERENCES public.anime_review_comments(id) ON DELETE CASCADE,
    root_comment_id UUID REFERENCES public.anime_review_comments(id) ON DELETE CASCADE,
    depth INT DEFAULT 0,
    author_id TEXT NOT NULL,
    author_type TEXT DEFAULT 'guest', -- 'anilist', 'guest', or 'admin'
    author_name TEXT NOT NULL,
    author_avatar_url TEXT,
    body TEXT NOT NULL,
    image_urls JSONB DEFAULT '[]'::jsonb,
    shared_collection JSONB,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_comments_media 
    ON public.anime_review_comments(anilist_media_id, created_at ASC);
CREATE INDEX IF NOT EXISTS idx_comments_parent 
    ON public.anime_review_comments(parent_id);

ALTER TABLE public.anime_review_comments ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow public read comments" 
    ON public.anime_review_comments FOR SELECT TO anon, authenticated USING (true);

CREATE POLICY "Allow public create comments" 
    ON public.anime_review_comments FOR INSERT TO anon, authenticated WITH CHECK (true);

CREATE POLICY "Allow delete comments" 
    ON public.anime_review_comments FOR DELETE TO anon, authenticated USING (true);


-- ---------------------------------------------------------------------
-- 11. TABLE: tickets & ticket_messages
-- Used in SupabaseTicketManager.kt for user-to-admin support threads.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_number BIGINT GENERATED ALWAYS AS IDENTITY,
    anilist_id TEXT NOT NULL,
    anilist_username TEXT NOT NULL,
    anilist_avatar_url TEXT,
    profile_id TEXT,
    title TEXT NOT NULL,
    category TEXT NOT NULL,
    description TEXT NOT NULL,
    initial_image_url TEXT,
    status TEXT DEFAULT 'open', -- 'open', 'resolved', 'closed'
    priority TEXT DEFAULT 'normal', -- 'normal', 'high', 'urgent'
    admin_notes TEXT,
    last_message_preview TEXT,
    last_message_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_tickets_user_lookup 
    ON public.tickets(anilist_id, last_message_at DESC);

ALTER TABLE public.tickets ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow public read tickets" 
    ON public.tickets FOR SELECT TO anon, authenticated USING (true);

CREATE POLICY "Allow public create tickets" 
    ON public.tickets FOR INSERT TO anon, authenticated WITH CHECK (true);

CREATE POLICY "Allow public update tickets" 
    ON public.tickets FOR UPDATE TO anon, authenticated USING (true) WITH CHECK (true);


CREATE TABLE IF NOT EXISTS public.ticket_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ticket_id UUID NOT NULL REFERENCES public.tickets(id) ON DELETE CASCADE,
    sender_type TEXT DEFAULT 'user', -- 'user' or 'admin'
    sender_name TEXT NOT NULL,
    sender_avatar_url TEXT,
    sender_anilist_id TEXT,
    message TEXT NOT NULL,
    image_url TEXT,
    is_read BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ticket_messages_thread 
    ON public.ticket_messages(ticket_id, created_at ASC);

ALTER TABLE public.ticket_messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow public read ticket messages" 
    ON public.ticket_messages FOR SELECT TO anon, authenticated USING (true);

CREATE POLICY "Allow public send ticket messages" 
    ON public.ticket_messages FOR INSERT TO anon, authenticated WITH CHECK (true);


-- ---------------------------------------------------------------------
-- 12. STORAGE BUCKETS (covers & profile_icons)
-- Used for screenshot uploads and custom profile avatars.
-- ---------------------------------------------------------------------
INSERT INTO storage.buckets (id, name, public)
VALUES 
    ('covers', 'covers', true),
    ('profile_icons', 'profile_icons', true)
ON CONFLICT (id) DO UPDATE SET public = true;

-- Storage RLS policies for public uploads and viewing
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE policyname = 'Public Access Covers' AND tablename = 'objects'
    ) THEN
        CREATE POLICY "Public Access Covers" ON storage.objects
            FOR ALL TO anon, authenticated
            USING (bucket_id = 'covers')
            WITH CHECK (bucket_id = 'covers');
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE policyname = 'Public Access Profile Icons' AND tablename = 'objects'
    ) THEN
        CREATE POLICY "Public Access Profile Icons" ON storage.objects
            FOR ALL TO anon, authenticated
            USING (bucket_id = 'profile_icons')
            WITH CHECK (bucket_id = 'profile_icons');
    END IF;
END $$;

-- =====================================================================
-- SUCCESS: All tables, functions, indexes, and storage buckets created!
-- =====================================================================

