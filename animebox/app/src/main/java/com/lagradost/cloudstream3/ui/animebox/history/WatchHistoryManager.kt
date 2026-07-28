package com.lagradost.cloudstream3.ui.animebox.history

import android.content.Context
import com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

import kotlinx.serialization.Serializable

@Serializable
data class WatchHistoryItem(
    val anilistId: Int,
    val animeTitle: String,
    val coverImageUrl: String,
    val episodeNumber: Int,
    val progressPositionMs: Long,
    val totalDurationMs: Long,
    val lastWatchedTimestamp: Long = System.currentTimeMillis()
)

/**
 * Persist watch progress ("Continue Watching") locally bound specifically to the current active profile
 */
class WatchHistoryManager(context: Context) {

    private val activeProfile = ProfileManager.getActiveProfile(context)
    private val prefs = context.getSharedPreferences("AnimeBoxHistory_$activeProfile", Context.MODE_PRIVATE)

    /**
     * Save watch position for a specific anime episode
     */
    fun saveWatchProgress(
        anilistId: Int,
        animeTitle: String,
        coverImageUrl: String,
        episodeNumber: Int,
        progressPositionMs: Long,
        totalDurationMs: Long
    ) {
        val historyList = getWatchHistory().toMutableList()
        
        // Remove existing item to avoid duplicate
        historyList.removeAll { it.anilistId == anilistId }

        // Insert new item at the top
        historyList.add(0, WatchHistoryItem(
            anilistId = anilistId,
            animeTitle = animeTitle,
            coverImageUrl = coverImageUrl,
            episodeNumber = episodeNumber,
            progressPositionMs = progressPositionMs,
            totalDurationMs = totalDurationMs
        ))

        // Keep maximum of 15 history items
        val trimmedList = historyList.take(15)
        
        prefs.edit().putString("history_list", trimmedList.toJson()).apply()
    }

    /**
     * Retrieve complete watch history
     */
    fun getWatchHistory(): List<WatchHistoryItem> {
        val json = prefs.getString("history_list", null) ?: return emptyList()
        return try {
            tryParseJson<List<WatchHistoryItem>>(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get saved watch position for a specific episode (to resume playback)
     */
    fun getSavedProgress(anilistId: Int, episodeNumber: Int): Long {
        val items = getWatchHistory()
        val match = items.find { it.anilistId == anilistId && it.episodeNumber == episodeNumber }
        return match?.progressPositionMs ?: 0L
    }
}
