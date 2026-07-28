package com.lagradost.cloudstream3.ui.animebox.library

import android.content.Context
import com.lagradost.cloudstream3.ui.animebox.AnimeBrief
import com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

class LibraryManager(private val context: Context) {

    private val activeProfile = ProfileManager.getActiveProfile(context)
    private val prefs = context.getSharedPreferences("AnimeBoxLibrary_$activeProfile", Context.MODE_PRIVATE)

    fun toggleLibraryItem(anime: AnimeBrief): Boolean {
        val currentList = getLibraryItems().toMutableList()
        val exists = currentList.any { it.id == anime.id }
        if (exists) {
            currentList.removeAll { it.id == anime.id }
        } else {
            currentList.add(0, anime)
        }
        prefs.edit().putString("library_list", currentList.toJson()).apply()
        return !exists
    }

    fun isInLibrary(animeId: Int): Boolean {
        return getLibraryItems().any { it.id == animeId }
    }

    fun getLibraryItems(): List<AnimeBrief> {
        val json = prefs.getString("library_list", null) ?: return emptyList()
        return try {
            tryParseJson<List<AnimeBrief>>(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
