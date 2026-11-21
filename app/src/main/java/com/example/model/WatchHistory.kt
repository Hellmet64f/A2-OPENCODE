package com.example.model

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class WatchHistoryItem(
    val id: String, // URL principal do filme ou da série
    val title: String,
    val coverUrl: String,
    val currentUrl: String, // URL direta do filme ou do episódio atual
    val isSeries: Boolean,
    val season: Int = 1,
    val episodeNumber: Int = 1,
    val episodeTitle: String = "",
    val watchedEpisodes: List<String> = emptyList(), // ex: ["1x1", "1x2"]
    val favorite: Boolean = false,
    val completed: Boolean = false,
    val progressSeconds: Long = 0L,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun episodeKey(s: Int = season, ep: Int = episodeNumber): String = "${s}x${ep}"

    fun isEpisodeWatched(s: Int, ep: Int): Boolean {
        val key = "${s}x${ep}"
        return watchedEpisodes.contains(key)
    }
}

object WatchHistoryManager {
    private const val PREFS_NAME = "a2_miguel_watch_history_prefs"
    private const val KEY_HISTORY = "watch_history_json"
    private const val KEY_RECENT_SEARCHES = "recent_searches_json"
    private const val MAX_HISTORY_SIZE = 100
    private const val MAX_WATCHED_EPS = 300
    private const val MAX_RECENT_SEARCHES = 15

    fun getHistory(context: Context): List<WatchHistoryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return parseHistoryJson(jsonString)
    }

    fun replaceHistory(context: Context, items: List<WatchHistoryItem>): List<WatchHistoryItem> {
        val sorted = items.sortedByDescending { it.timestamp }.take(MAX_HISTORY_SIZE)
        persistList(context, sorted)
        return sorted
    }

    fun saveProgress(context: Context, item: WatchHistoryItem, markCurrentWatched: Boolean = true): List<WatchHistoryItem> {
        val current = getHistory(context).toMutableList()
        val existing = current.find {
            it.id == item.id || (it.title.equals(item.title, ignoreCase = true) && it.isSeries == item.isSeries)
        }
        val cover = if (item.coverUrl.isNotBlank()) item.coverUrl else (existing?.coverUrl ?: "")
        val effectiveId = if (item.id.isNotBlank()) item.id else (existing?.id ?: item.currentUrl)

        val mergedWatched = LinkedHashSet<String>()
        existing?.watchedEpisodes?.let { mergedWatched.addAll(it) }
        mergedWatched.addAll(item.watchedEpisodes)
        if (markCurrentWatched && item.isSeries) {
            mergedWatched.add("${item.season.coerceAtLeast(1)}x${item.episodeNumber.coerceAtLeast(1)}")
        }

        val finalItem = item.copy(
            id = effectiveId,
            coverUrl = cover,
            watchedEpisodes = mergedWatched.toList().takeLast(MAX_WATCHED_EPS),
            favorite = item.favorite || (existing?.favorite ?: false),
            completed = item.completed || (existing?.completed ?: false),
            timestamp = System.currentTimeMillis()
        )

        current.removeAll {
            it.id == finalItem.id || (it.title.equals(finalItem.title, ignoreCase = true) && it.isSeries == finalItem.isSeries)
        }
        current.add(0, finalItem)

        val trimmed = current.take(MAX_HISTORY_SIZE)
        persistList(context, trimmed)
        return trimmed
    }

    fun toggleFavorite(context: Context, movie: Movie): Pair<List<WatchHistoryItem>, WatchHistoryItem> {
        val current = getHistory(context).toMutableList()
        val existing = current.find {
            it.id == movie.url || (it.title.equals(movie.title, ignoreCase = true) && it.isSeries == movie.isSeries)
        }
        val updatedItem = if (existing != null) {
            existing.copy(
                favorite = !existing.favorite,
                coverUrl = if (existing.coverUrl.isNotBlank()) existing.coverUrl else movie.coverUrl,
                timestamp = System.currentTimeMillis()
            )
        } else {
            WatchHistoryItem(
                id = movie.url,
                title = movie.title,
                coverUrl = movie.coverUrl,
                currentUrl = movie.url,
                isSeries = movie.isSeries,
                season = 1,
                episodeNumber = 1,
                episodeTitle = if (movie.isSeries) "Episódio 1" else movie.title,
                favorite = true,
                completed = false
            )
        }
        current.removeAll {
            it.id == updatedItem.id || (it.title.equals(updatedItem.title, ignoreCase = true) && it.isSeries == updatedItem.isSeries)
        }
        current.add(0, updatedItem)
        val trimmed = current.take(MAX_HISTORY_SIZE)
        persistList(context, trimmed)
        return trimmed to updatedItem
    }

    fun markCompleted(context: Context, id: String, completed: Boolean): Pair<List<WatchHistoryItem>, WatchHistoryItem?> {
        val current = getHistory(context).toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx < 0) return current to null
        val updatedItem = current[idx].copy(completed = completed, timestamp = System.currentTimeMillis())
        current[idx] = updatedItem
        val sorted = current.sortedByDescending { it.timestamp }
        persistList(context, sorted)
        return sorted to updatedItem
    }

    fun removeItem(context: Context, id: String): List<WatchHistoryItem> {
        val current = getHistory(context).toMutableList()
        current.removeAll { it.id == id }
        persistList(context, current)
        return current
    }

    fun clearAll(context: Context): List<WatchHistoryItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_HISTORY).apply()
        return emptyList()
    }

    fun getRecentSearches(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_RECENT_SEARCHES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.optString(it).takeIf { s -> s.isNotBlank() } }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addRecentSearch(context: Context, query: String): List<String> {
        val clean = query.trim()
        if (clean.length < 2) return getRecentSearches(context)
        val list = getRecentSearches(context).filterNot { it.equals(clean, ignoreCase = true) }.toMutableList()
        list.add(0, clean)
        val trimmed = list.take(MAX_RECENT_SEARCHES)
        saveRecentSearches(context, trimmed)
        return trimmed
    }

    fun removeRecentSearch(context: Context, query: String): List<String> {
        val trimmed = getRecentSearches(context).filterNot { it.equals(query, ignoreCase = true) }
        saveRecentSearches(context, trimmed)
        return trimmed
    }

    fun saveRecentSearches(context: Context, searches: List<String>) {
        val arr = JSONArray()
        searches.take(MAX_RECENT_SEARCHES).forEach { arr.put(it) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RECENT_SEARCHES, arr.toString())
            .apply()
    }

    fun exportToJson(context: Context, items: List<WatchHistoryItem> = getHistory(context)): String {
        val root = JSONObject()
        root.put("app", "A2 Miguel")
        root.put("version", 2)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("history", toJsonArray(items))

        val recentsArr = JSONArray()
        getRecentSearches(context).forEach { recentsArr.put(it) }
        root.put("recentSearches", recentsArr)

        val zoom = ScreenZoomManager.getZoomConfig(context)
        val cal = FocusCalibrationManager.getCalibration(context)
        val prefsObj = JSONObject().apply {
            put("zoomPreset", zoom.preset.lowercase())
            put("zoomScale", zoom.scale.toDouble())
            put("focusX", cal.first.toDouble())
            put("focusY", cal.second.toDouble())
        }
        root.put("preferences", prefsObj)
        return root.toString(2)
    }

    fun importFromJson(context: Context, jsonText: String): List<WatchHistoryItem> {
        val trimmed = jsonText.trim()
        if (trimmed.isEmpty()) return getHistory(context)
        val parsedHistory = if (trimmed.startsWith("[")) {
            parseHistoryJson(trimmed)
        } else {
            val root = JSONObject(trimmed)
            val histArr = root.optJSONArray("history") ?: JSONArray()
            val list = parseHistoryJson(histArr.toString())
            val recentsArr = root.optJSONArray("recentSearches")
            if (recentsArr != null) {
                val recents = (0 until recentsArr.length())
                    .mapNotNull { recentsArr.optString(it).takeIf { s -> s.isNotBlank() } }
                if (recents.isNotEmpty()) saveRecentSearches(context, recents)
            }
            list
        }

        val current = getHistory(context).toMutableList()
        parsedHistory.forEach { incoming ->
            val existingIdx = current.indexOfFirst { it.id == incoming.id }
            if (existingIdx >= 0) {
                val prev = current[existingIdx]
                val mergedWatched = (prev.watchedEpisodes + incoming.watchedEpisodes).distinct().takeLast(MAX_WATCHED_EPS)
                current[existingIdx] = if (incoming.timestamp >= prev.timestamp) {
                    incoming.copy(
                        watchedEpisodes = mergedWatched,
                        favorite = incoming.favorite || prev.favorite,
                        completed = incoming.completed || prev.completed
                    )
                } else {
                    prev.copy(
                        watchedEpisodes = mergedWatched,
                        favorite = incoming.favorite || prev.favorite,
                        completed = incoming.completed || prev.completed
                    )
                }
            } else {
                current.add(incoming)
            }
        }
        return replaceHistory(context, current)
    }

    private fun parseHistoryJson(jsonString: String): List<WatchHistoryItem> {
        val list = mutableListOf<WatchHistoryItem>()
        try {
            val arr = JSONArray(jsonString)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", obj.optString("itemUrl", ""))
                val currentUrl = obj.optString("currentUrl", id)
                if (id.isBlank() && currentUrl.isBlank()) continue

                val watchedArr = obj.optJSONArray("watchedEpisodes")
                val watchedList = mutableListOf<String>()
                if (watchedArr != null) {
                    for (j in 0 until watchedArr.length()) {
                        val epKey = watchedArr.optString(j)
                        if (epKey.isNotBlank()) watchedList.add(epKey)
                    }
                }
                val isSeriesFlag = obj.optBoolean("isSeries", obj.optString("mediaType", "") == "series")
                list.add(
                    WatchHistoryItem(
                        id = id.ifBlank { currentUrl },
                        title = obj.optString("title", "Sem Título"),
                        coverUrl = obj.optString("coverUrl", ""),
                        currentUrl = currentUrl.ifBlank { id },
                        isSeries = isSeriesFlag,
                        season = obj.optInt("season", 1).coerceAtLeast(1),
                        episodeNumber = obj.optInt("episodeNumber", 1).coerceAtLeast(1),
                        episodeTitle = obj.optString("episodeTitle", ""),
                        watchedEpisodes = watchedList,
                        favorite = obj.optBoolean("favorite", false),
                        completed = obj.optBoolean("completed", false),
                        progressSeconds = obj.optLong("progressSeconds", 0L).coerceAtLeast(0L),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        } catch (_: Exception) {}
        return list.sortedByDescending { it.timestamp }
    }

    private fun toJsonArray(items: List<WatchHistoryItem>): JSONArray {
        val arr = JSONArray()
        for (h in items) {
            val obj = JSONObject()
            obj.put("id", h.id)
            obj.put("itemUrl", h.id)
            obj.put("title", h.title)
            obj.put("coverUrl", h.coverUrl)
            obj.put("currentUrl", h.currentUrl)
            obj.put("isSeries", h.isSeries)
            obj.put("mediaType", if (h.isSeries) "series" else "movie")
            obj.put("season", h.season)
            obj.put("episodeNumber", h.episodeNumber)
            obj.put("episodeTitle", h.episodeTitle)
            val epsArr = JSONArray()
            h.watchedEpisodes.forEach { epsArr.put(it) }
            obj.put("watchedEpisodes", epsArr)
            obj.put("favorite", h.favorite)
            obj.put("completed", h.completed)
            obj.put("progressSeconds", h.progressSeconds)
            obj.put("timestamp", h.timestamp)
            arr.put(obj)
        }
        return arr
    }

    private fun persistList(context: Context, list: List<WatchHistoryItem>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_HISTORY, toJsonArray(list).toString()).apply()
    }
}
