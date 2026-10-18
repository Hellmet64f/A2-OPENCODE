package com.example.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class WatchHistoryItem(
    val id: String, // Unique identifier (typically main movie/series URL)
    val title: String,
    val coverUrl: String,
    val currentUrl: String, // Direct URL of the episode or movie to play
    val isSeries: Boolean,
    val isAnime: Boolean,
    val season: Int = 1,
    val episodeNumber: Int = 1,
    val episodeTitle: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

object WatchHistoryManager {
    private const val PREFS_NAME = "a2_miguel_watch_history_prefs"
    private const val KEY_HISTORY = "watch_history_json"

    fun getHistory(context: Context): List<WatchHistoryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val list = mutableListOf<WatchHistoryItem>()
        try {
            val arr = JSONArray(jsonString)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    WatchHistoryItem(
                        id = obj.optString("id", ""),
                        title = obj.optString("title", "Sem Título"),
                        coverUrl = obj.optString("coverUrl", ""),
                        currentUrl = obj.optString("currentUrl", ""),
                        isSeries = obj.optBoolean("isSeries", false),
                        isAnime = obj.optBoolean("isAnime", false),
                        season = obj.optInt("season", 1),
                        episodeNumber = obj.optInt("episodeNumber", 1),
                        episodeTitle = obj.optString("episodeTitle", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list.sortedByDescending { it.timestamp }
    }

    fun saveProgress(context: Context, item: WatchHistoryItem): List<WatchHistoryItem> {
        val current = getHistory(context).toMutableList()
        val existing = current.find { it.id == item.id || (it.title.equals(item.title, ignoreCase = true) && it.isAnime == item.isAnime) }
        val cover = if (item.coverUrl.isNotBlank()) item.coverUrl else (existing?.coverUrl ?: "")
        val effectiveId = if (item.id.isNotBlank()) item.id else (existing?.id ?: item.currentUrl)
        val finalItem = item.copy(id = effectiveId, coverUrl = cover, timestamp = System.currentTimeMillis())

        // Remove existing entry for the same series/movie id or same title
        current.removeAll { it.id == finalItem.id || (it.title.equals(finalItem.title, ignoreCase = true) && it.isAnime == finalItem.isAnime) }
        // Add updated item to top
        current.add(0, finalItem)

        val maxHistorySize = 50
        val trimmed = current.take(maxHistorySize)

        val arr = JSONArray()
        for (h in trimmed) {
            val obj = JSONObject()
            obj.put("id", h.id)
            obj.put("title", h.title)
            obj.put("coverUrl", h.coverUrl)
            obj.put("currentUrl", h.currentUrl)
            obj.put("isSeries", h.isSeries)
            obj.put("isAnime", h.isAnime)
            obj.put("season", h.season)
            obj.put("episodeNumber", h.episodeNumber)
            obj.put("episodeTitle", h.episodeTitle)
            obj.put("timestamp", h.timestamp)
            arr.put(obj)
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
        return trimmed
    }

    fun removeItem(context: Context, id: String): List<WatchHistoryItem> {
        val current = getHistory(context).toMutableList()
        current.removeAll { it.id == id }

        val arr = JSONArray()
        for (h in current) {
            val obj = JSONObject()
            obj.put("id", h.id)
            obj.put("title", h.title)
            obj.put("coverUrl", h.coverUrl)
            obj.put("currentUrl", h.currentUrl)
            obj.put("isSeries", h.isSeries)
            obj.put("isAnime", h.isAnime)
            obj.put("season", h.season)
            obj.put("episodeNumber", h.episodeNumber)
            obj.put("episodeTitle", h.episodeTitle)
            obj.put("timestamp", h.timestamp)
            arr.put(obj)
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
        return current
    }

    fun clearAll(context: Context): List<WatchHistoryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_HISTORY).apply()
        return emptyList()
    }
}
