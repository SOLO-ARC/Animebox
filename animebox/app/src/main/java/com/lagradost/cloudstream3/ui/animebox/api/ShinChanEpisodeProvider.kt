package com.lagradost.cloudstream3.ui.animebox.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ShinChanSpecialItem(
    val id: String,
    val title: String,
    val coverImageUrl: String,
    val mobileUrl: String,
    val webUrl: String
)

object ShinChanEpisodeProvider {

    const val SHINCHAN_ANILIST_ID = 966
    const val SHINCHAN_POSTER_URL = "https://media.themoviedb.org/t/p/w440_and_h660_face/nitn0zkSheK8ZH5dbw4iuzxKb2u.jpg"
    const val SHINCHAN_LOGO_URL = "https://media.themoviedb.org/t/p/w1280/vf0wuGK7MfqzWvMO1MVP8TJv3Jp.png"
    const val SHINCHAN_BACKDROP_URL = "https://media.themoviedb.org/t/p/w1000_and_h563_face/mJdHir1k42UwUF74s05PS5GTCgv.jpg"

    /**
     * Strictly matches Shinchan TV series with AniList ID 966 only.
     * Shinchan movies and other spin-offs will NOT match this.
     */
    fun isShinChan(anilistId: Int, animeTitle: String? = null): Boolean {
        return anilistId == SHINCHAN_ANILIST_ID
    }

    suspend fun getLatestEpisodeNumber(): Int = withContext(Dispatchers.IO) {
        return@withContext ShinChanSupabaseManager.getLatestEpisodeNumber()
    }

    suspend fun getShinChanEpisodeCover(episodeNum: Int): String? = withContext(Dispatchers.IO) {
        return@withContext ShinChanSupabaseManager.getEpisodeCoverUrl(episodeNum)
    }

    suspend fun getShinChanVideoUrl(episodeNum: Int): String? = withContext(Dispatchers.IO) {
        return@withContext ShinChanSupabaseManager.getVideoUrl(episodeNum)
    }

    suspend fun getShinChanEpisodeMetadata(): Map<Int, EpisodeMeta> = withContext(Dispatchers.IO) {
        return@withContext emptyMap()
    }

    suspend fun getShinChanSpecials(): List<ShinChanSpecialItem> = withContext(Dispatchers.IO) {
        return@withContext emptyList()
    }
}
