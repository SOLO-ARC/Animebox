package com.lagradost.cloudstream3.ui.animebox.sync

import android.content.Context
import android.content.SharedPreferences
import com.lagradost.cloudstream3.ui.animebox.AnimeBrief
import com.lagradost.cloudstream3.ui.animebox.history.WatchHistoryManager
import com.lagradost.cloudstream3.ui.animebox.library.LibraryManager
import com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager
import com.lagradost.cloudstream3.ui.animebox.profiles.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SyncAccountUser(
    val id: String,
    val username: String,
    val avatarUrl: String,
    val provider: String = "anilist"
)

data class AniListSyncStats(
    val importedCount: Int,
    val uploadedCount: Int,
    val success: Boolean
)

object AnimeBoxAccountSyncManager {
    private const val PREFS_NAME = "animebox_account_sync_prefs"
    private const val ANILIST_GRAPHQL_URL = "https://graphql.anilist.co"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Resolves the primary sync user identifier for the given profile.
     */
    fun resolveSyncUserId(context: Context, profileId: String): String {
        val anilistUser = getAniListUser(context, profileId)
        if (anilistUser != null && anilistUser.id.isNotBlank()) {
            return "anilist_${anilistUser.id}"
        }
        return profileId
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ------------------------------------------------------------------------
    // Token & User Persistence Per Profile
    // ------------------------------------------------------------------------

    fun getAniListToken(context: Context, profileId: String): String? {
        return getPrefs(context).getString("anilist_token_$profileId", null)
    }

    fun getAniListUser(context: Context, profileId: String): SyncAccountUser? {
        val raw = getPrefs(context).getString("anilist_user_$profileId", null) ?: return null
        return try {
            val json = JSONObject(raw)
            SyncAccountUser(
                id = json.getString("id"),
                username = json.getString("username"),
                avatarUrl = json.optString("avatarUrl", ""),
                provider = "anilist"
            )
        } catch (_: Exception) {
            null
        }
    }

    fun saveAniListAuth(context: Context, profileId: String, token: String, user: SyncAccountUser) {
        val json = JSONObject().apply {
            put("id", user.id)
            put("username", user.username)
            put("avatarUrl", user.avatarUrl)
            put("provider", "anilist")
        }
        getPrefs(context).edit()
            .putString("anilist_token_$profileId", token)
            .putString("anilist_user_$profileId", json.toString())
            .apply()

        // Automatically update the profile avatar if user has a remote profile picture
        if (user.avatarUrl.isNotBlank()) {
            updateProfileAvatar(context, profileId, user.avatarUrl)
        }
    }

    fun logoutAniList(context: Context, profileId: String) {
        getPrefs(context).edit()
            .remove("anilist_token_$profileId")
            .remove("anilist_user_$profileId")
            .apply()
    }

    private fun updateProfileAvatar(context: Context, profileId: String, newAvatarUrl: String) {
        try {
            val profiles = ProfileManager.getProfiles(context).toMutableList()
            val index = profiles.indexOfFirst { it.id == profileId }
            if (index != -1) {
                val current = profiles[index]
                profiles[index] = UserProfile(current.id, current.name, newAvatarUrl)
                ProfileManager.saveProfiles(context, profiles)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // ------------------------------------------------------------------------
    // AniList Profile Fetcher
    // ------------------------------------------------------------------------

    suspend fun fetchAniListUserProfile(accessToken: String): SyncAccountUser? = withContext(Dispatchers.IO) {
        try {
            val query = """
                query {
                    Viewer {
                        id
                        name
                        avatar {
                            large
                            medium
                        }
                    }
                }
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                put("query", query)
            }

            val request = Request.Builder()
                .url(ANILIST_GRAPHQL_URL)
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                val dataObj = JSONObject(bodyStr).getJSONObject("data").getJSONObject("Viewer")
                val id = dataObj.getInt("id").toString()
                val name = dataObj.getString("name")
                val avatarObj = dataObj.optJSONObject("avatar")
                val avatarUrl = avatarObj?.optString("large", avatarObj.optString("medium", "")) ?: ""

                SyncAccountUser(
                    id = id,
                    username = name,
                    avatarUrl = avatarUrl,
                    provider = "anilist"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // ------------------------------------------------------------------------
    // Full Watchlist Merge & Synchronization (AniList <-> App)
    // ------------------------------------------------------------------------

    /**
     * Performs a complete bidirectional merge:
     * 1. Fetches all remote anime watchlist entries from AniList and imports/merges into app's LibraryManager.
     * 2. Pushes any local library items in the app to AniList.
     * 3. Syncs watch history episodes if progress > 0.
     */
    suspend fun syncAniListWatchlist(context: Context, profileId: String): AniListSyncStats = withContext(Dispatchers.IO) {
        val token = getAniListToken(context, profileId) ?: return@withContext AniListSyncStats(0, 0, false)
        val user = getAniListUser(context, profileId) ?: return@withContext AniListSyncStats(0, 0, false)
        val userId = user.id.toIntOrNull() ?: return@withContext AniListSyncStats(0, 0, false)

        try {
            val libraryManager = LibraryManager(context, profileId)
            val historyManager = WatchHistoryManager(context, profileId)

            // Step 1: Query entire user watchlist collection from AniList
            val query = """
                query (${"$"}userId: Int) {
                    MediaListCollection(userId: ${"$"}userId, type: ANIME) {
                        lists {
                            name
                            status
                            entries {
                                id
                                status
                                progress
                                score(format: POINT_100)
                                media {
                                    id
                                    title {
                                        romaji
                                        english
                                        native
                                    }
                                    coverImage {
                                        extraLarge
                                        large
                                    }
                                    bannerImage
                                    description
                                    genres
                                    averageScore
                                    episodes
                                    status
                                    startDate {
                                        year
                                        month
                                        day
                                    }
                                }
                            }
                        }
                    }
                }
            """.trimIndent()

            val jsonBody = JSONObject().apply {
                put("query", query)
                put("variables", JSONObject().apply { put("userId", userId) })
            }

            val request = Request.Builder()
                .url(ANILIST_GRAPHQL_URL)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                .build()

            val remoteMediaIds = mutableSetOf<Int>()
            var importedCount = 0

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (!bodyStr.isNullOrBlank()) {
                        val dataObj = JSONObject(bodyStr).optJSONObject("data")
                        val collection = dataObj?.optJSONObject("MediaListCollection")
                        val lists = collection?.optJSONArray("lists") ?: JSONArray()

                        for (i in 0 until lists.length()) {
                            val listObj = lists.getJSONObject(i)
                            val listStatus = listObj.optString("status", "")
                            val entries = listObj.optJSONArray("entries") ?: JSONArray()

                            val category = when (listStatus.uppercase()) {
                                "CURRENT" -> "Currently Watching"
                                "PLANNING" -> "Plan to Watch"
                                "COMPLETED" -> "Completed"
                                "PAUSED" -> "On Hold"
                                "DROPPED" -> "Dropped"
                                "REPEATING" -> "Re-watching"
                                else -> "Plan to Watch"
                            }

                            for (j in 0 until entries.length()) {
                                val entryObj = entries.getJSONObject(j)
                                val progress = entryObj.optInt("progress", 0)
                                val mediaObj = entryObj.optJSONObject("media") ?: continue
                                val mediaId = mediaObj.getInt("id")
                                remoteMediaIds.add(mediaId)

                                val titleObj = mediaObj.optJSONObject("title")
                                val title = titleObj?.optString("english")
                                    ?.takeIf { it.isNotBlank() }
                                    ?: titleObj?.optString("romaji")
                                    ?: titleObj?.optString("native")
                                    ?: "Anime #$mediaId"

                                val coverObj = mediaObj.optJSONObject("coverImage")
                                val coverUrl = coverObj?.optString("extraLarge", coverObj.optString("large", "")) ?: ""
                                val bannerUrl = mediaObj.optString("bannerImage", "")
                                val description = mediaObj.optString("description", "")
                                val averageScore = mediaObj.optInt("averageScore", 0)
                                val episodes = mediaObj.optInt("episodes", 0)
                                val status = mediaObj.optString("status", "")

                                val startDateObj = mediaObj.optJSONObject("startDate")
                                val releaseYear = startDateObj?.optInt("year", 0) ?: 0
                                val releaseMonth = startDateObj?.optInt("month", 0) ?: 0
                                val releaseDay = startDateObj?.optInt("day", 0) ?: 0

                                val genresArray = mediaObj.optJSONArray("genres") ?: JSONArray()
                                val genresList = mutableListOf<String>()
                                for (g in 0 until genresArray.length()) {
                                    genresList.add(genresArray.getString(g))
                                }

                                val animeBrief = AnimeBrief(
                                    id = mediaId,
                                    title = title,
                                    coverUrl = coverUrl,
                                    bannerUrl = bannerUrl,
                                    description = description,
                                    genres = genresList,
                                    averageScore = averageScore,
                                    episodes = episodes,
                                    status = status,
                                    releaseYear = releaseYear,
                                    releaseMonth = releaseMonth,
                                    releaseDay = releaseDay,
                                    customListCategory = category
                                )

                                // Add or merge in local app library (My Lists only)
                                libraryManager.addOrUpdateLibraryItem(animeBrief, category)
                                importedCount++
                            }
                        }
                    }
                }
            }

            // Step 2: Push any local library items to AniList that aren't already synced
            val localItems = libraryManager.getLibraryItems()
            var uploadedCount = 0
            for (local in localItems) {
                if (local.id > 0 && !remoteMediaIds.contains(local.id)) {
                    syncLibraryItemToRemote(context, profileId, local.id, local.customListCategory, 0)
                    uploadedCount++
                }
            }

            AniListSyncStats(importedCount, uploadedCount, true)
        } catch (e: Exception) {
            e.printStackTrace()
            AniListSyncStats(0, 0, false)
        }
    }

    /**
     * Pushes a single item state (status category / progress) directly to AniList.
     */
    suspend fun syncLibraryItemToRemote(
        context: Context,
        profileId: String,
        anilistId: Int,
        category: String,
        progress: Int = 0
    ) = withContext(Dispatchers.IO) {
        if (anilistId <= 0) return@withContext

        val anilistStatus = when (LibraryManager.normalizeCategory(category).lowercase()) {
            "currently watching", "watching" -> "CURRENT"
            "completed" -> "COMPLETED"
            "on hold", "paused" -> "PAUSED"
            "dropped" -> "DROPPED"
            "re-watching", "rewatching" -> "REPEATING"
            else -> "PLANNING"
        }

        val anilistToken = getAniListToken(context, profileId)
        if (!anilistToken.isNullOrBlank()) {
            try {
                val mutation = """
                    mutation (${"$"}mediaId: Int, ${"$"}status: MediaListStatus, ${"$"}progress: Int) {
                        SaveMediaListEntry (mediaId: ${"$"}mediaId, status: ${"$"}status, progress: ${"$"}progress) {
                            id
                            mediaId
                            status
                            progress
                        }
                    }
                """.trimIndent()

                val variables = JSONObject().apply {
                    put("mediaId", anilistId)
                    put("status", anilistStatus)
                    if (progress > 0) put("progress", progress)
                }

                val jsonBody = JSONObject().apply {
                    put("query", mutation)
                    put("variables", variables)
                }

                val request = Request.Builder()
                    .url(ANILIST_GRAPHQL_URL)
                    .header("Authorization", "Bearer $anilistToken")
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                httpClient.newCall(request).execute().close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Syncs watch progress when an episode is watched:
     * - If the anime is NOT in any other list (or already in "Currently Watching"),
     *   sets status to "CURRENT" ("Currently Watching") in AniList & local Library.
     * - If the anime IS already in another list (e.g. Completed, Plan to Watch, On Hold, Dropped),
     *   preserves its existing list category and ONLY updates the episode count / progress on AniList.
     */
    suspend fun syncWatchProgress(
        context: Context,
        profileId: String,
        anilistId: Int,
        episodeNumber: Int,
        animeTitle: String = "",
        showPosterUrl: String = ""
    ) = withContext(Dispatchers.IO) {
        if (anilistId <= 0 || episodeNumber <= 0) return@withContext

        val libraryManager = LibraryManager(context, profileId)
        val isInLocalLibrary = libraryManager.isInLibrary(anilistId)
        val localCategory = if (isInLocalLibrary) libraryManager.getAnimeCategory(anilistId) else ""

        val anilistToken = getAniListToken(context, profileId)

        var remoteStatus: String? = null
        var remoteTitle: String = animeTitle
        var remoteCover: String = showPosterUrl
        var remoteBanner: String = ""
        var remoteDesc: String = ""
        var remoteEpisodes: Int = 0

        // If show poster is blank or looks like an episode thumbnail, fetch official anime poster
        if (remoteCover.isBlank() || remoteCover.contains("episode") || remoteCover.contains("ep_") || remoteCover.contains("cover_${anilistId}_")) {
            try {
                val detailsJson = com.lagradost.cloudstream3.ui.animebox.api.AniListClient.getAnimeDetails(anilistId)
                if (!detailsJson.isNullOrEmpty()) {
                    val root = JSONObject(detailsJson)
                    val media = root.optJSONObject("data")?.optJSONObject("Media")
                    val coverObj = media?.optJSONObject("coverImage")
                    val resolvedCover = coverObj?.optString("extraLarge", coverObj.optString("large", "")) ?: ""
                    if (resolvedCover.isNotBlank()) remoteCover = resolvedCover
                    if (remoteBanner.isBlank()) remoteBanner = media?.optString("bannerImage", "") ?: ""
                    if (remoteDesc.isBlank()) remoteDesc = media?.optString("description", "") ?: ""
                    if (remoteEpisodes == 0) remoteEpisodes = media?.optInt("episodes", 0) ?: 0
                }
            } catch (_: Exception) {}
        }
        if (remoteCover.isBlank() || remoteCover.contains("episode") || remoteCover.contains("ep_") || remoteCover.contains("cover_${anilistId}_")) {
            try {
                val aniZipCover = com.lagradost.cloudstream3.ui.animebox.api.AniZipClient.getAnimeCover(anilistId)
                if (aniZipCover.isNotBlank()) remoteCover = aniZipCover
            } catch (_: Exception) {}
        }

        if (!anilistToken.isNullOrBlank()) {
            try {
                // Step 1: Query current media status and entry on AniList
                val checkQuery = """
                    query (${"$"}mediaId: Int) {
                        Media (id: ${"$"}mediaId) {
                            id
                            title {
                                romaji
                                english
                                native
                            }
                            coverImage {
                                extraLarge
                                large
                            }
                            bannerImage
                            description
                            episodes
                            mediaListEntry {
                                id
                                status
                                progress
                            }
                        }
                    }
                """.trimIndent()

                val queryBody = JSONObject().apply {
                    put("query", checkQuery)
                    put("variables", JSONObject().apply { put("mediaId", anilistId) })
                }

                val queryReq = Request.Builder()
                    .url(ANILIST_GRAPHQL_URL)
                    .header("Authorization", "Bearer $anilistToken")
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .post(queryBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                httpClient.newCall(queryReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string()
                        if (!bodyStr.isNullOrBlank()) {
                            val dataObj = JSONObject(bodyStr).optJSONObject("data")
                            val mediaObj = dataObj?.optJSONObject("Media")
                            if (mediaObj != null) {
                                val titleObj = mediaObj.optJSONObject("title")
                                val resolvedTitle = titleObj?.optString("english")
                                    ?.takeIf { it.isNotBlank() }
                                    ?: titleObj?.optString("romaji")
                                    ?: titleObj?.optString("native")
                                if (!resolvedTitle.isNullOrBlank()) remoteTitle = resolvedTitle

                                val coverObj = mediaObj.optJSONObject("coverImage")
                                val resolvedCover = coverObj?.optString("extraLarge", coverObj.optString("large", "")) ?: ""
                                if (resolvedCover.isNotBlank()) remoteCover = resolvedCover

                                remoteBanner = mediaObj.optString("bannerImage", "")
                                remoteDesc = mediaObj.optString("description", "")
                                remoteEpisodes = mediaObj.optInt("episodes", 0)

                                val entryObj = mediaObj.optJSONObject("mediaListEntry")
                                if (entryObj != null) {
                                    remoteStatus = entryObj.optString("status", "")
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Step 2: Determine if we should set status to CURRENT or preserve existing
            // If remoteStatus is null or empty, or already CURRENT, we use CURRENT.
            // If remoteStatus is PLANNING, COMPLETED, PAUSED, DROPPED, REPEATING, we keep it.
            val shouldSetCurrent = when {
                remoteStatus.isNullOrBlank() -> {
                    // Not in any list on AniList.
                    // Check if local library has it in another list
                    if (isInLocalLibrary && localCategory != "Currently Watching" && localCategory.isNotBlank()) {
                        false
                    } else {
                        true
                    }
                }
                remoteStatus.equals("CURRENT", ignoreCase = true) -> true
                else -> false
            }

            try {
                val mutation: String
                val variables = JSONObject().apply {
                    put("mediaId", anilistId)
                    put("progress", episodeNumber)
                    if (shouldSetCurrent) {
                        put("status", "CURRENT")
                    } else if (!remoteStatus.isNullOrBlank()) {
                        put("status", remoteStatus)
                    }
                }

                if (variables.has("status")) {
                    mutation = """
                        mutation (${"$"}mediaId: Int, ${"$"}progress: Int, ${"$"}status: MediaListStatus) {
                            SaveMediaListEntry (mediaId: ${"$"}mediaId, progress: ${"$"}progress, status: ${"$"}status) {
                                id
                                mediaId
                                status
                                progress
                            }
                        }
                    """.trimIndent()
                } else {
                    mutation = """
                        mutation (${"$"}mediaId: Int, ${"$"}progress: Int) {
                            SaveMediaListEntry (mediaId: ${"$"}mediaId, progress: ${"$"}progress) {
                                id
                                mediaId
                                status
                                progress
                            }
                        }
                    """.trimIndent()
                }

                val jsonBody = JSONObject().apply {
                    put("query", mutation)
                    put("variables", variables)
                }

                val mutationReq = Request.Builder()
                    .url(ANILIST_GRAPHQL_URL)
                    .header("Authorization", "Bearer $anilistToken")
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                httpClient.newCall(mutationReq).execute().close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Step 3: Update local LibraryManager
        // If the anime is not in any other list (or already in Currently Watching), add/update in Currently Watching
        if (!isInLocalLibrary || localCategory == "Currently Watching" || localCategory.isBlank()) {
            val finalTitle = remoteTitle.ifBlank { animeTitle.ifBlank { "Anime #$anilistId" } }
            val finalCover = when {
                remoteCover.isNotBlank() && !remoteCover.contains("backdrop", ignoreCase = true) && !remoteCover.contains("banner", ignoreCase = true) && !remoteCover.contains("episode", ignoreCase = true) -> remoteCover
                showPosterUrl.isNotBlank() && !showPosterUrl.contains("backdrop", ignoreCase = true) && !showPosterUrl.contains("banner", ignoreCase = true) && !showPosterUrl.contains("episode", ignoreCase = true) -> showPosterUrl
                else -> remoteCover.ifBlank { showPosterUrl }
            }
            val animeBrief = AnimeBrief(
                id = anilistId,
                title = finalTitle,
                coverUrl = finalCover,
                bannerUrl = remoteBanner,
                description = remoteDesc,
                episodes = remoteEpisodes,
                customListCategory = "Currently Watching"
            )
            libraryManager.addOrUpdateLibraryItem(animeBrief, "Currently Watching")
        }
    }
}
