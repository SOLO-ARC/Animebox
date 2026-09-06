package com.lagradost.cloudstream3.ui.animebox.notifications

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class ScheduleAlert(
    val scheduleId: Int,
    val anilistId: Int,
    val title: String,
    val episode: Int,
    val airingAt: Long,
    val coverUrl: String,
    val notified: Boolean = false
)

object ScheduleAlertManager {

    private const val PREFS_NAME = "firefly_schedule_alerts_prefs"
    private const val KEY_ALERTS = "saved_schedule_alerts_json"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @Synchronized
    fun getAlerts(context: Context): List<ScheduleAlert> {
        val raw = getPrefs(context).getString(KEY_ALERTS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            val list = mutableListOf<ScheduleAlert>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ScheduleAlert(
                        scheduleId = obj.getInt("scheduleId"),
                        anilistId = obj.getInt("anilistId"),
                        title = obj.getString("title"),
                        episode = obj.getInt("episode"),
                        airingAt = obj.getLong("airingAt"),
                        coverUrl = obj.optString("coverUrl", ""),
                        notified = obj.optBoolean("notified", false)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun getAlertIds(context: Context): Set<Int> {
        return getAlerts(context).map { it.scheduleId }.toSet()
    }

    @Synchronized
    fun saveAlert(context: Context, alert: ScheduleAlert) {
        val current = getAlerts(context).toMutableList()
        current.removeAll { it.scheduleId == alert.scheduleId }
        current.add(alert)
        saveAlertsList(context, current)
    }

    @Synchronized
    fun removeAlert(context: Context, scheduleId: Int) {
        val current = getAlerts(context).toMutableList()
        current.removeAll { it.scheduleId == scheduleId }
        saveAlertsList(context, current)
    }

    @Synchronized
    fun isAlertSet(context: Context, scheduleId: Int): Boolean {
        return getAlerts(context).any { it.scheduleId == scheduleId }
    }

    /**
     * Check if any saved schedule alert has already aired (airingAt <= currentTime)
     * and has not yet been notified to the user.
     * Returns the earliest due alert to show once on opening the app.
     */
    @Synchronized
    fun checkDueAlert(context: Context): ScheduleAlert? {
        val nowSeconds = System.currentTimeMillis() / 1000L
        val alerts = getAlerts(context)
        return alerts.firstOrNull { it.airingAt <= nowSeconds && !it.notified }
    }

    /**
     * Mark an alert as notified so it never appears again.
     */
    @Synchronized
    fun markAlertNotified(context: Context, scheduleId: Int) {
        val current = getAlerts(context).toMutableList()
        val index = current.indexOfFirst { it.scheduleId == scheduleId }
        if (index != -1) {
            current[index] = current[index].copy(notified = true)
            saveAlertsList(context, current)
        }
    }

    private fun saveAlertsList(context: Context, list: List<ScheduleAlert>) {
        val array = JSONArray()
        list.forEach { item ->
            val obj = JSONObject().apply {
                put("scheduleId", item.scheduleId)
                put("anilistId", item.anilistId)
                put("title", item.title)
                put("episode", item.episode)
                put("airingAt", item.airingAt)
                put("coverUrl", item.coverUrl)
                put("notified", item.notified)
            }
            array.put(obj)
        }
        getPrefs(context).edit().putString(KEY_ALERTS, array.toString()).apply()
    }
}
