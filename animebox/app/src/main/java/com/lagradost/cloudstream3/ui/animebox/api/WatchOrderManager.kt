package com.lagradost.cloudstream3.ui.animebox.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class WatchOrderItem(
    val id: Int,
    val title: String,
    val description: String = "",
    val coverUrl: String = "",
    val bannerUrl: String = "",
    val format: String = "",
    val episodes: Int? = null,
    val year: Int? = null,
    val relationType: String = "",
    val rawRelationType: String = "",
    val isCurrent: Boolean = false,
    val status: String = "",
    val sortKey: Long = 0L
)

object WatchOrderManager {

    private val watchOrderCache = ConcurrentHashMap<Int, List<WatchOrderItem>>()

    fun clearCache() {
        watchOrderCache.clear()
    }

    suspend fun getWatchOrder(
        currentAnimeId: Int,
        currentAnimeTitle: String? = null,
        currentAnimeCover: String? = null,
        currentDesc: String? = null,
        currentFormat: String? = null,
        currentEpisodes: Int? = null,
        currentYear: Int? = null,
        onProgress: ((foundCount: Int) -> Unit)? = null
    ): List<WatchOrderItem> = withContext(Dispatchers.IO) {
        if (currentAnimeId <= 0) return@withContext emptyList()

        watchOrderCache[currentAnimeId]?.let { cached ->
            // Re-mark isCurrent appropriately if opened from a sibling
            onProgress?.invoke(cached.size)
            return@withContext cached.map { it.copy(isCurrent = (it.id == currentAnimeId)) }
        }

        val collectedMap = mutableMapOf<Int, WatchOrderItem>()

        // 1. Fetch details for current anime
        val detailsStr = AniListClient.getAnimeDetails(currentAnimeId)
        val currentMedia = if (!detailsStr.isNullOrBlank()) {
            try {
                JSONObject(detailsStr).optJSONObject("data")?.optJSONObject("Media")
            } catch (_: Exception) { null }
        } else null

        val curTitle = currentMedia?.let { extractTitle(it) }?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?: (currentAnimeTitle?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) } ?: "Anime #$currentAnimeId")
        val curCoverObj = currentMedia?.optJSONObject("coverImage")
        val curCover = curCoverObj?.optString("large", "")?.takeIf { it.isNotBlank() && it != "null" }
            ?: curCoverObj?.optString("extraLarge", "")?.takeIf { it.isNotBlank() && it != "null" }
            ?: (currentAnimeCover ?: "")
        val curBanner = currentMedia?.optString("bannerImage", "")?.takeIf { it != "null" } ?: ""
        val curDesc = cleanHtmlDescription(currentMedia?.optString("description", "") ?: (currentDesc ?: ""))
        val curFormat = currentMedia?.optString("format", "") ?: (currentFormat ?: "TV")
        val curEp = if (currentMedia?.has("episodes") == true && !currentMedia.isNull("episodes")) {
            currentMedia.getInt("episodes")
        } else currentEpisodes
        val curYear = currentMedia?.optJSONObject("startDate")?.optInt("year", 0)?.takeIf { it > 0 } ?: currentYear
        val curStatus = currentMedia?.optString("status", "") ?: ""
        val curSortKey = extractSortKey(currentMedia, curYear ?: 0)

        val isCurTv = (curEp ?: 0) > 1 || curFormat.equals("TV", ignoreCase = true) || curFormat.equals("TV_SHORT", ignoreCase = true)
        val isCurrentMovie = !isCurTv && (
            curFormat.equals("MOVIE", ignoreCase = true) ||
            (curEp == 1 && curFormat.equals("SPECIAL", ignoreCase = true))
        )

        val currentItem = WatchOrderItem(
            id = currentAnimeId,
            title = curTitle,
            description = curDesc,
            coverUrl = curCover,
            bannerUrl = curBanner,
            format = curFormat,
            episodes = curEp,
            year = curYear,
            relationType = if (isCurrentMovie) "Movie" else "Main Story",
            rawRelationType = "CURRENT",
            isCurrent = true,
            status = curStatus,
            sortKey = curSortKey
        )
        collectedMap[currentAnimeId] = currentItem

        // Walk the relation graph in batches: each depth now costs one AniList request.
        val visited = linkedSetOf(currentAnimeId)
        var frontier = listOf(currentAnimeId)
        val lowerCurTitle = curTitle.lowercase()
        if (lowerCurTitle.contains("fate/") || lowerCurTitle.startsWith("fate ") || lowerCurTitle.contains("el-melloi")) {
            frontier = (frontier + listOf(356, 10087, 155986)).distinct()
            visited.addAll(frontier)
        }
        val expandableRelations = setOf("PREQUEL", "SEQUEL", "PARENT", "SIDE_STORY", "SPIN_OFF")

        for (depth in 0..3) {
            val batch = if (depth == 0) {
                listOfNotNull(currentMedia) + AniListClient.getAnimeDetailsBatch(frontier.filter { it != currentAnimeId })
            } else {
                AniListClient.getAnimeDetailsBatch(frontier)
            }
            val nextFrontier = linkedSetOf<Int>()
            batch.forEach { mediaObj ->
                parseRelations(mediaObj).forEach { rel ->
                    if (rel.status.equals("NOT_YET_RELEASED", ignoreCase = true)) return@forEach
                    val existing = collectedMap[rel.id]
                    if (existing == null) {
                        collectedMap[rel.id] = rel
                        onProgress?.invoke(collectedMap.size)
                    } else if (existing.description.isEmpty() || existing.sortKey == 0L) {
                        collectedMap[rel.id] = existing.copy(
                            description = existing.description.ifEmpty { rel.description },
                            sortKey = if (existing.sortKey > 0L) existing.sortKey else rel.sortKey
                        )
                    }
                    if (depth < 3 && rel.rawRelationType.uppercase() in expandableRelations &&
                        visited.size < 60 && visited.add(rel.id)) {
                        nextFrontier.add(rel.id)
                    }
                }
            }
            frontier = nextFrontier.take(50)
            if (frontier.isEmpty()) break
        }

        // 3. Filter and sort chronologically by release date and story structure
        val sortedList = collectedMap.values
            .filter { item ->
                // Do not show unreleased animes unless it is the currently viewed anime
                !item.status.equals("NOT_YET_RELEASED", ignoreCase = true) || item.isCurrent
            }
            .sortedWith(
                compareBy<WatchOrderItem> { it.sortKey }
                    .thenBy { item ->
                        when (item.format.uppercase()) {
                            "TV" -> 0
                            "TV_SHORT" -> 1
                            "MOVIE" -> 2
                            "OVA" -> 3
                            "ONA" -> 4
                            else -> 5
                        }
                    }
                    .thenBy { it.id }
            )

        // Cache for all IDs in this franchise
        for (item in sortedList) {
            watchOrderCache[item.id] = sortedList
        }

        return@withContext sortedList
    }

    private fun parseRelations(mediaObj: JSONObject?): List<WatchOrderItem> {
        if (mediaObj == null) return emptyList()
        val relationsObj = mediaObj.optJSONObject("relations") ?: return emptyList()
        val edges = relationsObj.optJSONArray("edges") ?: return emptyList()
        val result = mutableListOf<WatchOrderItem>()

        for (i in 0 until edges.length()) {
            val edge = edges.optJSONObject(i) ?: continue
            val relType = edge.optString("relationType", "")
            if (relType.uppercase() !in setOf("PREQUEL", "SEQUEL", "PARENT", "SIDE_STORY", "SPIN_OFF", "SUMMARY")) continue
            val node = edge.optJSONObject("node") ?: continue

            val format = node.optString("format", "").uppercase()
            // Only include watchable animated entries (filter out Manga, Novels, Music, etc.)
            if (format !in listOf("TV", "TV_SHORT", "MOVIE", "OVA", "ONA", "SPECIAL")) continue

            if (AniListClient.isBlockedMedia(node)) continue

            val id = node.optInt("id", 0)
            if (id <= 0) continue

            val status = node.optString("status", "")
            // Do not include unreleased anime in timeline
            if (status.equals("NOT_YET_RELEASED", ignoreCase = true)) continue

            val title = extractTitle(node)
            val desc = cleanHtmlDescription(node.optString("description", ""))
            val coverObj = node.optJSONObject("coverImage")
            val coverUrl = coverObj?.optString("large", "")?.takeIf { it.isNotBlank() && it != "null" }
                ?: coverObj?.optString("extraLarge", "")?.takeIf { it.isNotBlank() && it != "null" }
                ?: ""
            val bannerUrl = node.optString("bannerImage", "")?.takeIf { it != "null" } ?: ""
            val ep = if (node.has("episodes") && !node.isNull("episodes")) node.getInt("episodes") else null
            val year = node.optJSONObject("startDate")?.optInt("year", 0)?.takeIf { it > 0 }
            val sortKey = extractSortKey(node, year ?: 0)

            val isRelTv = (ep ?: 0) > 1 || format.equals("TV", ignoreCase = true) || format.equals("TV_SHORT", ignoreCase = true)
            val isMovie = !isRelTv && (
                format.equals("MOVIE", ignoreCase = true) ||
                (ep == 1 && format in listOf("SPECIAL", "OVA") && relType.contains("MOVIE", ignoreCase = true))
            )

            val badgeType = when {
                isMovie -> "Movie"
                relType.equals("PREQUEL", ignoreCase = true) -> "Prequel"
                relType.equals("SEQUEL", ignoreCase = true) -> "Sequel"
                relType.equals("SIDE_STORY", ignoreCase = true) -> if (isMovie) "Movie" else "Side Story"
                relType.equals("SPIN_OFF", ignoreCase = true) -> "Spin-off"
                relType.equals("ALTERNATIVE", ignoreCase = true) -> "Alternative"
                relType.equals("PARENT", ignoreCase = true) -> "Main Story"
                relType.equals("SUMMARY", ignoreCase = true) -> "Recap"
                else -> if (format == "TV" || format == "TV_SHORT") "Main Story" else format
            }

            result.add(
                WatchOrderItem(
                    id = id,
                    title = title,
                    description = desc,
                    coverUrl = coverUrl,
                    bannerUrl = bannerUrl,
                    format = format,
                    episodes = ep,
                    year = year,
                    relationType = badgeType,
                    rawRelationType = relType,
                    isCurrent = false,
                    status = status,
                    sortKey = sortKey
                )
            )
        }
        return result
    }

    fun extractTitle(mediaObj: JSONObject): String {
        val titleObj = mediaObj.optJSONObject("title") ?: return "Anime"
        fun sanitize(s: String?): String {
            if (s == null || s.isBlank() || s.equals("null", ignoreCase = true)) return ""
            return s.trim()
        }
        val eng = sanitize(titleObj.optString("english", ""))
        val rom = sanitize(titleObj.optString("romaji", ""))
        val userPref = sanitize(titleObj.optString("userPreferred", ""))
        val nativeTitle = sanitize(titleObj.optString("native", ""))
        return eng.ifEmpty { rom.ifEmpty { userPref.ifEmpty { nativeTitle.ifEmpty { "Anime" } } } }
    }

    private fun extractSortKey(mediaObj: JSONObject?, fallbackYear: Int): Long {
        if (mediaObj == null) return fallbackYear.toLong() * 10000L
        val startDate = mediaObj.optJSONObject("startDate")
        val y = startDate?.optInt("year", fallbackYear) ?: fallbackYear
        val m = startDate?.optInt("month", 1) ?: 1
        val d = startDate?.optInt("day", 1) ?: 1
        return (y.toLong() * 10000L) + (m.toLong() * 100L) + d.toLong()
    }

    private fun cleanHtmlDescription(rawHtml: String): String {
        if (rawHtml.isBlank()) return ""
        return rawHtml
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<i>(.*?)</i>", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("<b>(.*?)</b>", RegexOption.IGNORE_CASE), "$1")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .trim()
    }
}
