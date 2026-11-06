package com.lagradost.cloudstream3.ui.animebox.history

import android.content.Context
import com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager
import com.lagradost.cloudstream3.ui.animebox.sync.AnimeBoxAccountSyncManager
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class WatchHistoryItem(
    val anilistId: Int,
    val animeTitle: String,
    val coverImageUrl: String,
    val episodeNumber: Int,
    val progressPositionMs: Long,
    val totalDurationMs: Long,
    val lastWatchedTimestamp: Long = System.currentTimeMillis(),
    val showCoverUrl: String = ""
)

/**
 * Persist watch progress ("Continue Watching") locally bound specifically to the current active profile
 * with live reactive StateFlow and automatic cloud/AniList/MAL synchronization.
 */
class WatchHistoryManager(private val context: Context, private val profileId: String? = null) {

    companion object {
        private val _historyFlowMap = MutableStateFlow<Map<String, List<WatchHistoryItem>>>(emptyMap())
        val historyFlowMap: StateFlow<Map<String, List<WatchHistoryItem>>> = _historyFlowMap.asStateFlow()

        // Global revision trigger to force recomposition across screens
        val revisionFlow = MutableStateFlow(0L)

        private val syncScope = CoroutineScope(Dispatchers.IO)
        private val lastSyncedEpisodeMap = java.util.concurrent.ConcurrentHashMap<String, Int>()
    }

    private val activeProfile: String
        get() = profileId ?: ProfileManager.getActiveProfile(context)

    private fun getPrefs(): android.content.SharedPreferences {
        return context.getSharedPreferences("AnimeBoxHistory_$activeProfile", Context.MODE_PRIVATE)
    }

    /**
     * Retrieve complete raw watch history without clustering filter
     */
    fun getRawWatchHistory(): List<WatchHistoryItem> {
        val json = getPrefs().getString("history_list", null) ?: return emptyList()
        return try {
            tryParseJson<List<WatchHistoryItem>>(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Retrieve watch history respecting the Clustered Watch History setting
     * When Clustered is OFF (default): only keeps the latest episode for each anime.
     * When Clustered is ON: returns all watched episodes.
     */
    fun getWatchHistory(): List<WatchHistoryItem> {
        val raw = getRawWatchHistory()
        val isClustered = com.lagradost.cloudstream3.ui.animebox.settings.AnimeBoxSettings.isClusteredWatchHistoryEnabled(context)
        return if (isClustered) {
            raw
        } else {
            raw.distinctBy { if (it.anilistId > 0) it.anilistId.toString() else it.animeTitle.trim().lowercase() }
        }
    }

    /**
     * Save exact resume position for a specific anime episode in a dedicated episode progress store.
     * This guarantees that ALL episodes watched by the user (even if clustered mode is OFF or when offline)
     * keep their watch progress and resume time recorded.
     */
    fun saveEpisodeProgress(
        anilistId: Int,
        episodeNumber: Int,
        progressPositionMs: Long,
        totalDurationMs: Long
    ) {
        if (anilistId <= 0 || episodeNumber <= 0) return
        try {
            val epPrefs = context.getSharedPreferences("AnimeBoxEpProgress_$activeProfile", Context.MODE_PRIVATE)
            epPrefs.edit()
                .putLong("pos_${anilistId}_$episodeNumber", progressPositionMs)
                .putLong("dur_${anilistId}_$episodeNumber", totalDurationMs)
                .putLong("time_${anilistId}_$episodeNumber", System.currentTimeMillis())
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Save watch position for a specific anime episode.
     * When isOffline = true (downloaded episodes), records resume progress but DOES NOT add to Continue Watching.
     * When isOffline = false, records resume progress for all episodes and preserves them in history.
     */
    fun saveWatchProgress(
        anilistId: Int,
        animeTitle: String,
        coverImageUrl: String,
        episodeNumber: Int,
        progressPositionMs: Long,
        totalDurationMs: Long,
        showCoverUrl: String = "",
        isOffline: Boolean = false
    ) {
        // Always record episode-level resume progress regardless of offline/online or clustered mode
        saveEpisodeProgress(anilistId, episodeNumber, progressPositionMs, totalDurationMs)

        val historyList = getRawWatchHistory().toMutableList()

        if (isOffline) {
            // Downloaded episodes: strictly DO NOT show in Continue Watching. Remove any existing record if present.
            val removed = historyList.removeAll { it.anilistId == anilistId && it.episodeNumber == episodeNumber }
            if (removed) {
                getPrefs().edit().putString("history_list", historyList.toJson()).apply()
                val currentMap = _historyFlowMap.value.toMutableMap()
                currentMap[activeProfile] = getWatchHistory()
                _historyFlowMap.value = currentMap
                revisionFlow.value = System.currentTimeMillis()
            }
            return
        }

        // Clustered history or unclustered: keep all watched episodes in raw storage.
        // Unclustered view in getWatchHistory() will automatically deduplicate per anime for UI.
        historyList.removeAll { it.anilistId == anilistId && it.episodeNumber == episodeNumber }

        // Use episode thumbnail for history list (Continue Watching), fallback to show poster
        val historyCover = coverImageUrl.ifEmpty { showCoverUrl }
        val finalShowPoster = showCoverUrl.ifEmpty { historyCover }

        val newItem = WatchHistoryItem(
            anilistId = anilistId,
            animeTitle = animeTitle,
            coverImageUrl = historyCover,
            episodeNumber = episodeNumber,
            progressPositionMs = progressPositionMs,
            totalDurationMs = totalDurationMs,
            lastWatchedTimestamp = System.currentTimeMillis(),
            showCoverUrl = finalShowPoster
        )

        // Insert new item at the top
        historyList.add(0, newItem)

        // Keep generous history list size (up to 150 items) so older watched episodes are not prematurely lost
        val trimmedList = historyList.take(150)
        
        getPrefs().edit().putString("history_list", trimmedList.toJson()).apply()

        // Update reactive flow live immediately with current clustered/unclustered view
        val currentMap = _historyFlowMap.value.toMutableMap()
        currentMap[activeProfile] = getWatchHistory()
        _historyFlowMap.value = currentMap
        revisionFlow.value = System.currentTimeMillis()

        // Asynchronously sync to AniList and local Library when user watches an episode
        val syncKey = "${activeProfile}_$anilistId"
        val lastSyncedEp = lastSyncedEpisodeMap[syncKey]
        if (lastSyncedEp != episodeNumber && anilistId > 0 && episodeNumber > 0) {
            lastSyncedEpisodeMap[syncKey] = episodeNumber
            val isShinChan = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(anilistId)
            val posterForLibrary = when {
                isShinChan -> com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_POSTER_URL
                finalShowPoster.isNotBlank() && !finalShowPoster.contains("episode") && !finalShowPoster.contains("okcdn.ru") && !finalShowPoster.contains("videoPreview") && !finalShowPoster.contains("backdrop", ignoreCase = true) && !finalShowPoster.contains("banner", ignoreCase = true) -> finalShowPoster
                coverImageUrl.isNotBlank() && !coverImageUrl.contains("episode") && !coverImageUrl.contains("okcdn.ru") && !coverImageUrl.contains("videoPreview") && !coverImageUrl.contains("backdrop", ignoreCase = true) && !coverImageUrl.contains("banner", ignoreCase = true) -> coverImageUrl
                else -> finalShowPoster.ifEmpty { coverImageUrl }
            }
            syncScope.launch {
                try {
                    AnimeBoxAccountSyncManager.syncWatchProgress(
                        context = context,
                        profileId = activeProfile,
                        anilistId = anilistId,
                        episodeNumber = episodeNumber,
                        animeTitle = animeTitle,
                        showPosterUrl = posterForLibrary
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /**
     * Merge remote history items (from Supabase or backup) into current profile history
     */
    fun mergeRemoteHistory(remoteList: List<WatchHistoryItem>) {
        if (remoteList.isEmpty()) return
        val localList = getRawWatchHistory().toMutableList()

        for (item in remoteList) {
            val exists = localList.any { it.anilistId == item.anilistId && it.episodeNumber == item.episodeNumber }
            if (!exists) {
                localList.add(item)
            }
        }

        val sorted = localList.sortedByDescending { it.lastWatchedTimestamp }.take(150)
        getPrefs().edit().putString("history_list", sorted.toJson()).apply()

        val currentMap = _historyFlowMap.value.toMutableMap()
        currentMap[activeProfile] = getWatchHistory()
        _historyFlowMap.value = currentMap
        revisionFlow.value = System.currentTimeMillis()
    }

    /**
     * Get saved watch position for a specific episode (to resume playback).
     * Checks dedicated episode progress store first, then falls back to raw watch history items.
     */
    fun getSavedProgress(anilistId: Int, episodeNumber: Int): Long {
        if (anilistId <= 0 || episodeNumber <= 0) return 0L
        try {
            val epPrefs = context.getSharedPreferences("AnimeBoxEpProgress_$activeProfile", Context.MODE_PRIVATE)
            val pos = epPrefs.getLong("pos_${anilistId}_$episodeNumber", -1L)
            if (pos >= 0L) return pos
        } catch (_: Exception) {}

        val items = getRawWatchHistory()
        val match = items.find { it.anilistId == anilistId && it.episodeNumber == episodeNumber }
        return match?.progressPositionMs ?: 0L
    }

    /**
     * Remove item from watch history
     */
    fun removeHistoryItem(anilistId: Int, episodeNumber: Int) {
        val historyList = getRawWatchHistory().toMutableList()
        historyList.removeAll { it.anilistId == anilistId && it.episodeNumber == episodeNumber }
        getPrefs().edit().putString("history_list", historyList.toJson()).apply()

        val currentMap = _historyFlowMap.value.toMutableMap()
        currentMap[activeProfile] = getWatchHistory()
        _historyFlowMap.value = currentMap
        revisionFlow.value = System.currentTimeMillis()
    }
}
