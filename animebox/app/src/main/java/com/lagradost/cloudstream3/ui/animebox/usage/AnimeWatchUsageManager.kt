package com.lagradost.cloudstream3.ui.animebox.usage

import android.content.Context
import com.lagradost.cloudstream3.ui.animebox.history.WatchHistoryManager
import com.lagradost.cloudstream3.ui.animebox.profiles.ProfileManager
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Serializable
data class AnimeUsageRecord(
    val anilistId: Int,
    val animeTitle: String,
    val posterUrl: String,
    val watchTimeMs: Long,
    val episodesWatched: List<Int> = emptyList(),
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)

object AnimeWatchUsageManager {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val displayDateFormat = SimpleDateFormat("EEE, d MMM", Locale.US)
    private val dayNameFormat = SimpleDateFormat("EEE", Locale.US)

    private fun getPrefs(context: Context, profileId: String?): android.content.SharedPreferences {
        val resolvedProfile = profileId ?: ProfileManager.getActiveProfile(context)
        return context.getSharedPreferences("AnimeBoxAppUsage_$resolvedProfile", Context.MODE_PRIVATE)
    }

    fun getDateKey(timestampMs: Long = System.currentTimeMillis()): String {
        return dateFormat.format(Date(timestampMs))
    }

    fun formatDateLabel(calendar: Calendar): String {
        return displayDateFormat.format(calendar.time)
    }

    fun formatShortDayName(calendar: Calendar): String {
        return dayNameFormat.format(calendar.time)
    }

    /**
     * Format milliseconds into human readable duration matching screenshot:
     * "15 hrs, 56 mins" or "18 minutes" or "1 hr, 28 mins"
     */
    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "0 mins"
        val totalMinutes = ms / 60000L
        val hours = totalMinutes / 60L
        val mins = totalMinutes % 60L

        return when {
            hours > 0L && mins > 0L -> {
                val hrUnit = if (hours == 1L) "hr" else "hrs"
                "$hours $hrUnit, $mins mins"
            }
            hours > 0L -> {
                val hrUnit = if (hours == 1L) "hr" else "hrs"
                "$hours $hrUnit"
            }
            else -> {
                if (mins <= 1L) "1 minute" else "$mins minutes"
            }
        }
    }

    /**
     * Keep only the last 7 days of activity records. Prunes any records older than 7 days.
     */
    fun pruneOldWeeklyData(context: Context, profileId: String? = null) {
        try {
            val prefs = getPrefs(context, profileId)
            val cutoffCalendar = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -7)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val cutoffDateKey = dateFormat.format(cutoffCalendar.time)

            val editor = prefs.edit()
            var modified = false
            prefs.all.keys.forEach { key ->
                if (key.startsWith("usage_")) {
                    val datePart = key.removePrefix("usage_")
                    if (datePart.length == 10 && datePart < cutoffDateKey) {
                        editor.remove(key)
                        modified = true
                    }
                }
            }
            if (modified) {
                editor.apply()
            }
        } catch (e: Exception) {
            // Non-fatal safety catch
        }
    }

    /**
     * Record incremental watch time for an anime on the current date
     */
    fun recordWatchTime(
        context: Context,
        profileId: String? = null,
        anilistId: Int,
        animeTitle: String,
        posterUrl: String,
        deltaMs: Long,
        episodeNumber: Int = 0
    ) {
        if (deltaMs <= 0 || anilistId <= 0) return
        pruneOldWeeklyData(context, profileId)
        val dateKey = getDateKey()
        val prefs = getPrefs(context, profileId)
        val currentRecords = getDailyUsageRaw(context, profileId, dateKey).toMutableList()

        val existingIndex = currentRecords.indexOfFirst { it.anilistId == anilistId }
        val resolvedPoster = when {
            posterUrl.isNotBlank() && !posterUrl.contains("episode") && !posterUrl.contains("okcdn.ru") && !posterUrl.contains("videoPreview") && !posterUrl.contains("backdrop", ignoreCase = true) && !posterUrl.contains("banner", ignoreCase = true) -> posterUrl
            else -> com.lagradost.cloudstream3.ui.animebox.continueWatchingPosterCache[anilistId] ?: posterUrl
        }

        if (existingIndex >= 0) {
            val old = currentRecords[existingIndex]
            val updatedEpisodes = if (episodeNumber > 0 && !old.episodesWatched.contains(episodeNumber)) {
                old.episodesWatched + episodeNumber
            } else {
                old.episodesWatched
            }
            currentRecords[existingIndex] = old.copy(
                watchTimeMs = old.watchTimeMs + deltaMs,
                episodesWatched = updatedEpisodes,
                posterUrl = resolvedPoster.ifEmpty { old.posterUrl },
                animeTitle = animeTitle.ifEmpty { old.animeTitle },
                lastUpdatedTimestamp = System.currentTimeMillis()
            )
        } else {
            val episodes = if (episodeNumber > 0) listOf(episodeNumber) else emptyList()
            currentRecords.add(
                AnimeUsageRecord(
                    anilistId = anilistId,
                    animeTitle = animeTitle,
                    posterUrl = resolvedPoster,
                    watchTimeMs = deltaMs,
                    episodesWatched = episodes,
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )
            )
        }

        prefs.edit().putString("usage_$dateKey", currentRecords.toJson()).apply()
    }

    private fun getDailyUsageRaw(context: Context, profileId: String?, dateKey: String): List<AnimeUsageRecord> {
        val prefs = getPrefs(context, profileId)
        val json = prefs.getString("usage_$dateKey", null) ?: return emptyList()
        return try {
            tryParseJson<List<AnimeUsageRecord>>(json) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Returns recorded activity only; watch-history progress is not elapsed watch time. */
    fun getDailyUsage(context: Context, profileId: String? = null, dateKey: String): List<AnimeUsageRecord> {
        pruneOldWeeklyData(context, profileId)
        var records = getDailyUsageRaw(context, profileId, dateKey)
        // Remove rows from the old chart-filling demo seeder while preserving records backed by
        // a real watch-history entry for this date.
        val knownDemoIds = setOf(16498, 154587, 101922)
        if (records.any { it.anilistId in knownDemoIds }) {
            val realHistoryIds = WatchHistoryManager(context, profileId).getRawWatchHistory()
                .filter { getDateKey(it.lastWatchedTimestamp) == dateKey }
                .map { it.anilistId }
                .toSet()
            val legacyDemoRows = records.filter { row ->
                row.anilistId !in realHistoryIds && row.episodesWatched == listOf(1, 2) &&
                    when (row.anilistId) {
                        16498 -> row.watchTimeMs == 3_420_000L
                        154587 -> row.watchTimeMs == 1_680_000L
                        101922 -> row.watchTimeMs == 1_140_000L
                        else -> false
                    }
            }
            if (legacyDemoRows.isNotEmpty()) {
                records = records - legacyDemoRows.toSet()
                getPrefs(context, profileId).edit().putString("usage_$dateKey", records.toJson()).apply()
            }
        }
        if (records.isNotEmpty()) {
            return records.sortedByDescending { it.watchTimeMs }
        }
        return emptyList()
    }

    /**
     * Calculate 7-day week breakdown (Sunday to Saturday) matching screenshot
     */
    fun getWeeklyBreakdown(
        context: Context,
        profileId: String? = null,
        referenceTimeMs: Long = System.currentTimeMillis()
    ): List<DayUsageData> {
        pruneOldWeeklyData(context, profileId)
        val cal = Calendar.getInstance().apply {
            timeInMillis = referenceTimeMs
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Seek back to Sunday of this week
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)

        val result = mutableListOf<DayUsageData>()
        for (i in 0 until 7) {
            val dayCal = cal.clone() as Calendar
            val key = dateFormat.format(dayCal.time)
            val items = getDailyUsage(context, profileId, key)
            val totalMs = items.sumOf { it.watchTimeMs }
            result.add(
                DayUsageData(
                    calendar = dayCal,
                    dateKey = key,
                    dayName = formatShortDayName(dayCal),
                    displayDate = formatDateLabel(dayCal),
                    totalWatchTimeMs = totalMs,
                    items = items
                )
            )
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return result
    }
}

data class DayUsageData(
    val calendar: Calendar,
    val dateKey: String,
    val dayName: String,
    val displayDate: String,
    val totalWatchTimeMs: Long,
    val items: List<AnimeUsageRecord>
)
