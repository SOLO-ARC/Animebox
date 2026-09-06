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
    val lastWatchedTimestamp: Long = System.currentTimeMillis()
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
     * Save watch position for a specific anime episode
     */
    fun saveWatchProgress(
        anilistId: Int,
        animeTitle: String,
        coverImageUrl: String,
        episodeNumber: Int,
        progressPositionMs: Long,
        totalDurationMs: Long,
        showCoverUrl: String = ""
    ) {
        val historyList = getWatchHistory().toMutableList()
        
        // Remove existing entry for this specific episode to update its progress
        historyList.removeAll { it.anilistId == anilistId && it.episodeNumber == episodeNumber }

        // Use episode thumbnail for history list (Continue Watching), fallback to show poster
        val historyCover = coverImageUrl.ifEmpty { showCoverUrl }

        val newItem = WatchHistoryItem(
            anilistId = anilistId,
            animeTitle = animeTitle,
            coverImageUrl = historyCover,
            episodeNumber = episodeNumber,
            progressPositionMs = progressPositionMs,
            totalDurationMs = totalDurationMs,
            lastWatchedTimestamp = System.currentTimeMillis()
        )

        // Insert new item at the top
        historyList.add(0, newItem)

        // Keep maximum of 25 history items
        val trimmedList = historyList.take(25)
        
        getPrefs().edit().putString("history_list", trimmedList.toJson()).apply()

        // Update reactive flow live immediately
        val currentMap = _historyFlowMap.value.toMutableMap()
        currentMap[activeProfile] = trimmedList
        _historyFlowMap.value = currentMap
        revisionFlow.value = System.currentTimeMillis()

        // Asynchronously sync to AniList and local Library when user watches an episode
        val syncKey = "${activeProfile}_$anilistId"
        val lastSyncedEp = lastSyncedEpisodeMap[syncKey]
        if (lastSyncedEp != episodeNumber && anilistId > 0 && episodeNumber > 0) {
            lastSyncedEpisodeMap[syncKey] = episodeNumber
            val posterForLibrary = when {
                showCoverUrl.isNotBlank() && !showCoverUrl.contains("episode") && !showCoverUrl.contains("backdrop", ignoreCase = true) && !showCoverUrl.contains("banner", ignoreCase = true) -> showCoverUrl
                coverImageUrl.isNotBlank() && !coverImageUrl.contains("episode") && !coverImageUrl.contains("backdrop", ignoreCase = true) && !coverImageUrl.contains("banner", ignoreCase = true) -> coverImageUrl
                else -> showCoverUrl.ifEmpty { coverImageUrl }
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
     * Retrieve complete watch history
     */
    fun getWatchHistory(): List<WatchHistoryItem> {
        val json = getPrefs().getString("history_list", null) ?: return emptyList()
        return try {
            tryParseJson<List<WatchHistoryItem>>(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Merge remote history items (from Supabase or backup) into current profile history
     */
    fun mergeRemoteHistory(remoteList: List<WatchHistoryItem>) {
        if (remoteList.isEmpty()) return
        val localList = getWatchHistory().toMutableList()

        for (item in remoteList) {
            val exists = localList.any { it.anilistId == item.anilistId && it.episodeNumber == item.episodeNumber }
            if (!exists) {
                localList.add(item)
            }
        }

        val sorted = localList.sortedByDescending { it.lastWatchedTimestamp }.take(25)
        getPrefs().edit().putString("history_list", sorted.toJson()).apply()

        val currentMap = _historyFlowMap.value.toMutableMap()
        currentMap[activeProfile] = sorted
        _historyFlowMap.value = currentMap
        revisionFlow.value = System.currentTimeMillis()
    }

    /**
     * Get saved watch position for a specific episode (to resume playback)
     */
    fun getSavedProgress(anilistId: Int, episodeNumber: Int): Long {
        val items = getWatchHistory()
        val match = items.find { it.anilistId == anilistId && it.episodeNumber == episodeNumber }
        return match?.progressPositionMs ?: 0L
    }

    /**
     * Remove item from watch history
     */
    fun removeHistoryItem(anilistId: Int, episodeNumber: Int) {
        val historyList = getWatchHistory().toMutableList()
        historyList.removeAll { it.anilistId == anilistId && it.episodeNumber == episodeNumber }
        getPrefs().edit().putString("history_list", historyList.toJson()).apply()

        val currentMap = _historyFlowMap.value.toMutableMap()
        currentMap[activeProfile] = historyList
        _historyFlowMap.value = currentMap
        revisionFlow.value = System.currentTimeMillis()
    }
}
