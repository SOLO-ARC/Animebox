package com.lagradost.cloudstream3.ui.animebox.library

import android.content.Context
import com.lagradost.cloudstream3.ui.animebox.AnimeBrief
import com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.coroutines.launch

class LibraryManager(private val context: Context, private val profileId: String? = null) {

    private fun getPrefs(): android.content.SharedPreferences {
        val activeProfile = profileId ?: ProfileManager.getActiveProfile(context)
        return context.getSharedPreferences("AnimeBoxLibrary_$activeProfile", Context.MODE_PRIVATE)
    }

    companion object {
        val ANILIST_CATEGORIES = listOf(
            "Currently Watching",
            "Completed",
            "Plan to Watch",
            "On Hold",
            "Dropped",
            "Re-watching",
            "Favorites"
        )

        fun normalizeCategory(category: String): String {
            return when (category.trim().lowercase()) {
                "current", "watching", "currently watching" -> "Currently Watching"
                "planning", "plan to watch", "plantowatch" -> "Plan to Watch"
                "completed", "finished" -> "Completed"
                "paused", "on hold", "onhold", "hold" -> "On Hold"
                "dropped" -> "Dropped"
                "repeating", "rewatching", "re-watching" -> "Re-watching"
                "favorites", "favourite", "favorite" -> "Favorites"
                else -> if (category.isNotEmpty()) category else "Plan to Watch"
            }
        }
    }

    fun addOrUpdateLibraryItem(anime: AnimeBrief, category: String = "Plan to Watch") {
        val normalized = normalizeCategory(category)
        val currentList = getLibraryItems().toMutableList()
        val existing = currentList.find { it.id == anime.id }
        currentList.removeAll { it.id == anime.id }
        val isInvalidCover = anime.coverUrl.isBlank() ||
            anime.coverUrl.contains("episode", ignoreCase = true) ||
            anime.coverUrl.contains("backdrop", ignoreCase = true) ||
            anime.coverUrl.contains("banner", ignoreCase = true) ||
            anime.coverUrl.contains("cover_${anime.id}_")

        val effectiveCover = when {
            !isInvalidCover -> anime.coverUrl
            existing != null && existing.coverUrl.isNotBlank() && !existing.coverUrl.contains("episode", ignoreCase = true) && !existing.coverUrl.contains("backdrop", ignoreCase = true) && !existing.coverUrl.contains("banner", ignoreCase = true) -> existing.coverUrl
            else -> anime.coverUrl
        }
        val updated = anime.copy(
            customListCategory = normalized,
            coverUrl = effectiveCover
        )
        currentList.add(0, updated)
        getPrefs().edit().putString("library_list", currentList.toJson()).apply()

        val profId = profileId ?: ProfileManager.getActiveProfile(context)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (effectiveCover.isBlank() || isInvalidCover) {
                    val realCover = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getAnimeCover(updated.id)
                    if (realCover.isNotBlank()) {
                        val fixedItem = updated.copy(coverUrl = realCover)
                        val refreshedList = getLibraryItems().toMutableList()
                        val idx = refreshedList.indexOfFirst { it.id == updated.id }
                        if (idx >= 0) {
                            refreshedList[idx] = fixedItem
                        } else {
                            refreshedList.add(0, fixedItem)
                        }
                        getPrefs().edit().putString("library_list", refreshedList.toJson()).apply()
                    }
                }
                com.lagradost.cloudstream3.ui.animebox.sync.AnimeBoxAccountSyncManager.syncLibraryItemToRemote(
                    context, profId, updated.id, updated.customListCategory
                )
            } catch (_: Exception) {}
        }
    }

    fun removeLibraryItem(animeId: Int) {
        val currentList = getLibraryItems().toMutableList()
        currentList.removeAll { it.id == animeId }
        getPrefs().edit().putString("library_list", currentList.toJson()).apply()
    }

    fun toggleLibraryItem(anime: AnimeBrief, defaultCategory: String = "Plan to Watch"): Boolean {
        val currentList = getLibraryItems().toMutableList()
        val exists = currentList.any { it.id == anime.id }
        val profId = profileId ?: ProfileManager.getActiveProfile(context)
        if (exists) {
            currentList.removeAll { it.id == anime.id }
        } else {
            val normalized = normalizeCategory(if (anime.customListCategory.isEmpty()) defaultCategory else anime.customListCategory)
            val updated = anime.copy(customListCategory = normalized)
            currentList.add(0, updated)
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    com.lagradost.cloudstream3.ui.animebox.sync.AnimeBoxAccountSyncManager.syncLibraryItemToRemote(
                        context, profId, updated.id, updated.customListCategory
                    )
                } catch (_: Exception) {}
            }
        }
        getPrefs().edit().putString("library_list", currentList.toJson()).apply()
        return !exists
    }

    fun getAnimeCategory(animeId: Int): String {
        val raw = getLibraryItems().find { it.id == animeId }?.customListCategory ?: "Plan to Watch"
        return normalizeCategory(raw)
    }

    fun isInLibrary(animeId: Int): Boolean {
        return getLibraryItems().any { it.id == animeId }
    }

    fun getLibraryItems(): List<AnimeBrief> {
        val json = getPrefs().getString("library_list", null) ?: return emptyList()
        return try {
            val items = tryParseJson<List<AnimeBrief>>(json) ?: emptyList()
            items.map { item ->
                val isInvalidCover = item.coverUrl.isBlank() ||
                        item.coverUrl.contains("episode", ignoreCase = true) ||
                        item.coverUrl.contains("backdrop", ignoreCase = true) ||
                        item.coverUrl.contains("banner", ignoreCase = true) ||
                        item.coverUrl.contains("cover_${item.id}_")
                val cleanCover = if (!isInvalidCover) item.coverUrl else ""
                item.copy(
                    customListCategory = normalizeCategory(item.customListCategory),
                    coverUrl = cleanCover
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
