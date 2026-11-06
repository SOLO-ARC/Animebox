package com.lagradost.cloudstream3.ui.animebox.library



import android.content.Context

import com.lagradost.cloudstream3.ui.animebox.AnimeBrief

import com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager

import com.lagradost.cloudstream3.utils.AppUtils.toJson

import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

import kotlinx.coroutines.launch

import org.json.JSONArray

import org.json.JSONObject



data class AnimeCustomCollection(

    val id: String,

    val name: String,

    val animeIds: List<Int>

)



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



        val libraryRevisionFlow = kotlinx.coroutines.flow.MutableStateFlow(0L)



        fun notifyLibraryChanged() {

            libraryRevisionFlow.value = System.currentTimeMillis()

        }



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



    /**

     * Efficiently batch adds/merges multiple library items from sync (e.g. AniList)

     * Performs a single disk write and prevents flood of duplicate remote mutations.

     */

    fun addOrUpdateLibraryItemsBatch(items: List<Pair<AnimeBrief, String>>) {

        if (items.isEmpty()) return

        val currentList = getLibraryItems().toMutableList()

        val existingMap = currentList.associateBy { it.id }

        val incomingIds = items.map { it.first.id }.toSet()

        currentList.removeAll { incomingIds.contains(it.id) }



        val processedItems = items.map { (anime, category) ->

            val normalized = normalizeCategory(category)

            val isShinChan = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(anime.id)

            val existing = existingMap[anime.id]

            fun isPosterValid(url: String?): Boolean {

                if (url.isNullOrBlank()) return false

                if (url.contains("episode", ignoreCase = true)) return false

                if (url.contains("backdrop", ignoreCase = true)) return false

                if (url.contains("banner", ignoreCase = true)) return false

                if (url.contains("okcdn.ru", ignoreCase = true)) return false

                if (url.contains("videoPreview", ignoreCase = true)) return false

                if (url.contains("image.tmdb.org/t/p/", ignoreCase = true)) return false
            if (url.contains("hentaimama", ignoreCase = true) || url.contains("hentaihaven", ignoreCase = true)) return false
            if (url.contains("cover_${anime.id}_")) return false

                return true

            }



            val effectiveCover = when {

                isShinChan -> com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_POSTER_URL

                existing != null && isPosterValid(existing.coverUrl) -> existing.coverUrl

                isPosterValid(anime.coverUrl) -> anime.coverUrl

                existing != null && existing.coverUrl.isNotBlank() -> existing.coverUrl

                else -> anime.coverUrl

            }

            val cleanTitle = if (anime.title.isBlank() || anime.title.equals("null", ignoreCase = true)) {

                existing?.title?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: "Anime #${anime.id}"

            } else {

                anime.title.trim()

            }



            anime.copy(

                title = cleanTitle,

                customListCategory = normalized,

                coverUrl = effectiveCover

            )

        }



        // Add all processed items at the top

        currentList.addAll(0, processedItems)

        getPrefs().edit().putString("library_list", currentList.toJson()).apply()

        notifyLibraryChanged()

    }



    fun addOrUpdateLibraryItem(anime: AnimeBrief, category: String = "Plan to Watch") {

        val normalized = normalizeCategory(category)

        val currentList = getLibraryItems().toMutableList()

        val existing = currentList.find { it.id == anime.id }

        currentList.removeAll { it.id == anime.id }



        val isShinChan = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(anime.id)

        fun isPosterValid(url: String?): Boolean {

            if (url.isNullOrBlank()) return false

            if (url.contains("episode", ignoreCase = true)) return false

            if (url.contains("backdrop", ignoreCase = true)) return false

            if (url.contains("banner", ignoreCase = true)) return false

            if (url.contains("okcdn.ru", ignoreCase = true)) return false

            if (url.contains("videoPreview", ignoreCase = true)) return false

            if (url.contains("image.tmdb.org/t/p/", ignoreCase = true)) return false
            if (url.contains("hentaimama", ignoreCase = true) || url.contains("hentaihaven", ignoreCase = true)) return false
            if (url.contains("cover_${anime.id}_")) return false

            return true

        }



        val effectiveCover = when {

            isShinChan -> com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_POSTER_URL

            existing != null && isPosterValid(existing.coverUrl) -> existing.coverUrl

            isPosterValid(anime.coverUrl) -> anime.coverUrl

            existing != null && existing.coverUrl.isNotBlank() -> existing.coverUrl

            else -> anime.coverUrl

        }

        val cleanTitle = if (anime.title.isBlank() || anime.title.equals("null", ignoreCase = true)) {

            existing?.title?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: "Anime #${anime.id}"

        } else {

            anime.title.trim()

        }



        val updated = anime.copy(

            title = cleanTitle,

            customListCategory = normalized,

            coverUrl = effectiveCover

        )

        currentList.add(0, updated)

        getPrefs().edit().putString("library_list", currentList.toJson()).apply()

        notifyLibraryChanged()



        val profId = profileId ?: ProfileManager.getActiveProfile(context)

        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {

            try {

                if (!isShinChan && (effectiveCover.isBlank() || !isPosterValid(effectiveCover))) {

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

                        notifyLibraryChanged()

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

        notifyLibraryChanged()

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

        notifyLibraryChanged()

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

            var modified = false

            val sanitized = items.map { item ->

                val isShinChan = com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.isShinChan(item.id)

                val isInvalidCover = isShinChan || item.coverUrl.isBlank() ||

                        item.coverUrl.contains("episode", ignoreCase = true) ||

                        item.coverUrl.contains("okcdn.ru", ignoreCase = true) ||

                        item.coverUrl.contains("videoPreview", ignoreCase = true) ||

                        item.coverUrl.contains("backdrop", ignoreCase = true) ||

                        item.coverUrl.contains("banner", ignoreCase = true) ||

                        item.coverUrl.contains("image.tmdb.org/t/p/", ignoreCase = true) ||
                        item.coverUrl.contains("hentaimama", ignoreCase = true) ||
                        item.coverUrl.contains("hentaihaven", ignoreCase = true) ||
                        item.coverUrl.contains("cover_${item.id}_")

                val cleanCover = when {

                    isShinChan -> com.lagradost.cloudstream3.ui.animebox.api.ShinChanEpisodeProvider.SHINCHAN_POSTER_URL

                    !isInvalidCover -> item.coverUrl

                    else -> ""

                }

                val isNullTitle = item.title.isBlank() || item.title.equals("null", ignoreCase = true)

                val cleanTitle = if (isNullTitle) {

                    modified = true

                    "Anime #${item.id}"

                } else {

                    item.title.trim()

                }



                if (item.coverUrl != cleanCover || item.title != cleanTitle) {

                    modified = true

                }

                item.copy(

                    title = cleanTitle,

                    customListCategory = normalizeCategory(item.customListCategory),

                    coverUrl = cleanCover

                )

            }

            if (modified) {

                getPrefs().edit().putString("library_list", sanitized.toJson()).apply()

            }

            sanitized

        } catch (e: Exception) {

            emptyList()

        }

    }



    fun getCollections(): List<AnimeCustomCollection> {

        val raw = getPrefs().getString("custom_collections_v1", null) ?: return emptyList()

        return runCatching {

            val array = JSONArray(raw)

            (0 until array.length()).mapNotNull { index ->

                val item = array.optJSONObject(index) ?: return@mapNotNull null

                AnimeCustomCollection(

                    id = item.optString("id"),

                    name = item.optString("name").trim().take(40).ifBlank { "Collection" },

                    animeIds = item.optJSONArray("animeIds")?.let { ids ->

                        (0 until ids.length()).mapNotNull { ids.optInt(it).takeIf { id -> id > 0 } }.distinct()

                    }.orEmpty()

                ).takeIf { it.id.isNotBlank() }

            }

        }.getOrDefault(emptyList())

    }



    fun createCollection(name: String): AnimeCustomCollection? {

        val cleanName = name.trim().take(40)

        if (cleanName.isBlank()) return null

        val existing = getCollections()

        if (existing.any { it.name.equals(cleanName, ignoreCase = true) }) return null

        val created = AnimeCustomCollection(java.util.UUID.randomUUID().toString(), cleanName, emptyList())

        saveCollections(existing + created)

        return created

    }



    fun deleteCollection(collectionId: String) {

        saveCollections(getCollections().filterNot { it.id == collectionId })

    }



    fun setCollectionAnimeIds(collectionId: String, animeIds: List<Int>) {

        saveCollections(getCollections().map { collection ->

            if (collection.id == collectionId) collection.copy(animeIds = animeIds.filter { it > 0 }.distinct()) else collection

        })

    }



    fun getPublicCollections(): List<AnimeCustomCollection> {
        val raw = getPrefs().getString("public_collections_v1", null)
        if (raw.isNullOrBlank()) {
            return seedPublicCollections()
        }
        val collections = runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                AnimeCustomCollection(
                    id = item.optString("id"),
                    name = item.optString("name").trim().take(40).ifBlank { "Collection" },
                    animeIds = item.optJSONArray("animeIds")?.let { ids ->
                        (0 until ids.length()).mapNotNull { ids.optInt(it).takeIf { id -> id > 0 } }.distinct()
                    }.orEmpty()
                ).takeIf { it.id.isNotBlank() }
            }
        }.getOrDefault(emptyList())

        return if (collections.isEmpty()) seedPublicCollections() else collections
    }

    private fun seedPublicCollections(): List<AnimeCustomCollection> {
        return runCatching {
            val jsonString = context.assets.open("anime_movie_franchises.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(jsonString)
            val keys = root.keys()
            val list = mutableListOf<AnimeCustomCollection>()
            while (keys.hasNext()) {
                val franchise = keys.next()
                val array = root.optJSONArray(franchise) ?: continue
                val items = (0 until array.length()).mapNotNull { array.optJSONObject(it) }
                // Sort movies by release year in descending order: newest movies first and older movies below
                val sortedItems = items.sortedByDescending { it.optInt("year", 0) }
                val sortedIds = sortedItems.mapNotNull { it.optInt("id").takeIf { id -> id > 0 } }.distinct()
                val collId = "pub_" + franchise.lowercase().replace(Regex("[^a-z0-9]"), "_").trim('_')
                list.add(AnimeCustomCollection(id = collId, name = franchise, animeIds = sortedIds))
            }
            savePublicCollections(list)
            list
        }.getOrDefault(emptyList())
    }

    fun savePublicCollections(collections: List<AnimeCustomCollection>) {
        val array = JSONArray()
        collections.forEach { collection ->
            array.put(JSONObject().apply {
                put("id", collection.id)
                put("name", collection.name)
                put("animeIds", JSONArray(collection.animeIds))
            })
        }
        getPrefs().edit().putString("public_collections_v1", array.toString()).apply()
        notifyLibraryChanged()
    }

    fun createPublicCollection(name: String): AnimeCustomCollection? {
        val cleanName = name.trim().take(40)
        if (cleanName.isBlank()) return null
        val existing = getPublicCollections()
        if (existing.any { it.name.equals(cleanName, ignoreCase = true) }) return null
        val created = AnimeCustomCollection(java.util.UUID.randomUUID().toString(), cleanName, emptyList())
        savePublicCollections(existing + created)
        return created
    }

    fun deletePublicCollection(collectionId: String) {
        savePublicCollections(getPublicCollections().filterNot { it.id == collectionId })
    }

    fun setPublicCollectionAnimeIds(collectionId: String, animeIds: List<Int>) {
        savePublicCollections(getPublicCollections().map { collection ->
            if (collection.id == collectionId) collection.copy(animeIds = animeIds.filter { it > 0 }.distinct()) else collection
        })
    }

    fun saveCollectionAnimeBrief(anime: AnimeBrief) {

        val prefs = getPrefs()

        val cacheJson = prefs.getString("collection_anime_cache", null)

        val cache = try {

            if (cacheJson != null) tryParseJson<List<AnimeBrief>>(cacheJson)?.toMutableList() ?: mutableListOf()

            else mutableListOf()

        } catch (_: Exception) { mutableListOf() }

        cache.removeAll { it.id == anime.id }

        cache.add(0, anime)

        prefs.edit().putString("collection_anime_cache", cache.toJson()).apply()

        notifyLibraryChanged()

    }



    fun saveCollectionAnimeBriefsBatch(animes: List<AnimeBrief>) {

        if (animes.isEmpty()) return

        val prefs = getPrefs()

        val cacheJson = prefs.getString("collection_anime_cache", null)

        val cache = try {

            if (cacheJson != null) tryParseJson<List<AnimeBrief>>(cacheJson)?.toMutableList() ?: mutableListOf()

            else mutableListOf()

        } catch (_: Exception) { mutableListOf() }

        animes.forEach { anime ->

            cache.removeAll { it.id == anime.id }

            cache.add(0, anime)

        }

        prefs.edit().putString("collection_anime_cache", cache.toJson()).apply()

        notifyLibraryChanged()

    }



    fun getCollectionAnimeBrief(id: Int): AnimeBrief? {

        val prefs = getPrefs()

        val cacheJson = prefs.getString("collection_anime_cache", null) ?: return null

        return try {

            tryParseJson<List<AnimeBrief>>(cacheJson)?.find { it.id == id }

        } catch (_: Exception) { null }

    }



    fun getAllCachedCollectionBriefs(): List<AnimeBrief> {

        val prefs = getPrefs()

        val cacheJson = prefs.getString("collection_anime_cache", null) ?: return emptyList()

        return try {

            tryParseJson<List<AnimeBrief>>(cacheJson) ?: emptyList()

        } catch (_: Exception) { emptyList() }

    }



    fun saveMyListBackground(uri: String?) {

        getPrefs().edit().putString("my_list_background_uri", uri.orEmpty()).apply()

        notifyLibraryChanged()

    }



    fun getMyListBackground(): String? = getPrefs().getString("my_list_background_uri", null)?.takeIf { it.isNotBlank() }



    fun collectionSharePayload(collection: AnimeCustomCollection): JSONObject {

        val libraryById = getLibraryItems().associateBy { it.id }

        val cachedById = getAllCachedCollectionBriefs().associateBy { it.id }

        val titles = JSONArray()

        collection.animeIds.mapNotNull { libraryById[it] ?: cachedById[it] }.forEach { anime ->

            titles.put(JSONObject().apply {

                put("id", anime.id)

                put("title", anime.title.take(160))

                put("cover", anime.coverUrl.takeIf { it.startsWith("https://") || it.startsWith("http://") }.orEmpty())

            })

        }

        return JSONObject().apply {

            put("version", 1)

            put("name", collection.name)

            put("anime", titles)

        }

    }



    private fun saveCollections(collections: List<AnimeCustomCollection>) {

        val array = JSONArray()

        collections.forEach { collection ->

            array.put(JSONObject().apply {

                put("id", collection.id)

                put("name", collection.name)

                put("animeIds", JSONArray(collection.animeIds))

            })

        }

        getPrefs().edit().putString("custom_collections_v1", array.toString()).apply()

        notifyLibraryChanged()

    }

}

