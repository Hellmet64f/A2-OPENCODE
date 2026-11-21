package com.example.data

import androidx.annotation.Keep
import com.example.model.WatchHistoryItem
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import java.security.MessageDigest

@Keep
@IgnoreExtraProperties
data class WatchHistoryDoc(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val title: String = "",
    val coverUrl: String? = null,
    val itemUrl: String = "",
    val currentUrl: String = "",
    val mediaType: String = "movie", // "movie" | "series"
    val season: Int = 1,
    val episodeNumber: Int = 1,
    val episodeTitle: String? = null,
    val watchedEpisodes: List<String> = emptyList(),
    val favorite: Boolean = false,
    val completed: Boolean = false,
    val progressSeconds: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toWatchHistoryItem(): WatchHistoryItem = WatchHistoryItem(
        id = itemUrl,
        title = title.ifBlank { "Sem Título" },
        coverUrl = coverUrl.orEmpty(),
        currentUrl = currentUrl.ifBlank { itemUrl },
        isSeries = mediaType == "series",
        season = season.coerceIn(1, 200),
        episodeNumber = episodeNumber.coerceIn(1, 5000),
        episodeTitle = episodeTitle.orEmpty(),
        watchedEpisodes = watchedEpisodes.take(300),
        favorite = favorite,
        completed = completed,
        progressSeconds = progressSeconds.coerceIn(0L, 86400L),
        timestamp = (updatedAt ?: createdAt)?.toDate()?.time ?: System.currentTimeMillis()
    )
}

@Keep
@IgnoreExtraProperties
data class UserPreferencesDoc(
    @DocumentId val prefId: String = "",
    val userId: String = "",
    val zoomPreset: String = "normal",
    val zoomScale: Double = 1.0,
    val focusX: Double = 0.50,
    val focusY: Double = 0.48,
    val adBlockAutoUpdate: Boolean = true,
    val recentSearches: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

object FirestoreCodec {
    private val VALID_ZOOM_PRESETS = setOf("normal", "fit", "cinema", "full", "custom")

    fun stableDocId(rawUrl: String): String {
        val normalized = rawUrl.trim().ifBlank { "empty" }
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(40)
    }

    fun toHistoryCreateMap(userId: String, item: WatchHistoryItem): Map<String, Any> {
        val base = toHistoryCommonMap(userId, item).toMutableMap()
        base["createdAt"] = FieldValue.serverTimestamp()
        base["updatedAt"] = FieldValue.serverTimestamp()
        return base
    }

    fun toHistoryUpdateMap(userId: String, item: WatchHistoryItem): Map<String, Any> {
        val base = toHistoryCommonMap(userId, item).toMutableMap()
        base.remove("userId") // Imutável na regra de update
        base["updatedAt"] = FieldValue.serverTimestamp()
        return base
    }

    private fun toHistoryCommonMap(userId: String, item: WatchHistoryItem): Map<String, Any> {
        val cleanTitle = item.title.trim().take(200).ifBlank { "Sem Título" }
        val cleanItemUrl = item.id.trim().take(1000).ifBlank { "https://www.pobreflixtv.delivery/" }
        val cleanCurrentUrl = item.currentUrl.trim().take(1000).ifBlank { cleanItemUrl }
        val cleanCover = item.coverUrl.trim().take(1000).takeIf { it.isNotEmpty() }
        val cleanEpTitle = item.episodeTitle.trim().take(200).takeIf { it.isNotEmpty() }
        val cleanWatched = item.watchedEpisodes
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(300)

        val map = mutableMapOf<String, Any?>(
            "userId" to userId,
            "title" to cleanTitle,
            "coverUrl" to cleanCover,
            "itemUrl" to cleanItemUrl,
            "currentUrl" to cleanCurrentUrl,
            "mediaType" to if (item.isSeries) "series" else "movie",
            "season" to item.season.coerceIn(1, 200),
            "episodeNumber" to item.episodeNumber.coerceIn(1, 5000),
            "episodeTitle" to cleanEpTitle,
            "watchedEpisodes" to cleanWatched,
            "favorite" to item.favorite,
            "completed" to item.completed,
            "progressSeconds" to item.progressSeconds.coerceIn(0L, 86400L)
        )
        @Suppress("UNCHECKED_CAST")
        return map.filterValues { it != null } as Map<String, Any>
    }

    fun toPreferencesCreateMap(
        userId: String,
        zoomPreset: String,
        zoomScale: Double,
        focusX: Double,
        focusY: Double,
        adBlockAutoUpdate: Boolean,
        recentSearches: List<String>
    ): Map<String, Any> {
        val map = toPreferencesCommonMap(
            userId, zoomPreset, zoomScale, focusX, focusY, adBlockAutoUpdate, recentSearches
        ).toMutableMap()
        map["createdAt"] = FieldValue.serverTimestamp()
        map["updatedAt"] = FieldValue.serverTimestamp()
        return map
    }

    fun toPreferencesUpdateMap(
        userId: String,
        zoomPreset: String,
        zoomScale: Double,
        focusX: Double,
        focusY: Double,
        adBlockAutoUpdate: Boolean,
        recentSearches: List<String>
    ): Map<String, Any> {
        val map = toPreferencesCommonMap(
            userId, zoomPreset, zoomScale, focusX, focusY, adBlockAutoUpdate, recentSearches
        ).toMutableMap()
        map.remove("userId") // Imutável na regra de update
        map["updatedAt"] = FieldValue.serverTimestamp()
        return map
    }

    private fun toPreferencesCommonMap(
        userId: String,
        zoomPreset: String,
        zoomScale: Double,
        focusX: Double,
        focusY: Double,
        adBlockAutoUpdate: Boolean,
        recentSearches: List<String>
    ): Map<String, Any> {
        val safePreset = zoomPreset.trim().lowercase().let {
            if (it in VALID_ZOOM_PRESETS) it else "normal"
        }
        val safeScale = zoomScale.coerceIn(1.0, 2.5)
        val safeFocusX = focusX.coerceIn(0.05, 0.95)
        val safeFocusY = focusY.coerceIn(0.05, 0.95)
        val cleanSearches = recentSearches
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .take(20)

        return mapOf(
            "userId" to userId,
            "zoomPreset" to safePreset,
            "zoomScale" to safeScale,
            "focusX" to safeFocusX,
            "focusY" to safeFocusY,
            "adBlockAutoUpdate" to adBlockAutoUpdate,
            "recentSearches" to cleanSearches
        )
    }
}
