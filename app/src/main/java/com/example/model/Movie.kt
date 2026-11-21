package com.example.model

data class Movie(
    val title: String,
    val coverUrl: String,
    val url: String,
    val isSeries: Boolean = url.contains("/series/") || url.contains("/episodios/"),
    val genre: String = "Geral",
    val audio: String = "Dub/Leg",
    val year: String = ""
)

data class Episode(
    val title: String,
    val url: String,
    val season: Int,
    val number: Int,
    val audio: String = "Dublado"
)

sealed class AppState {
    object WebLoading : AppState()

    data class HomeLoaded(
        val allMovies: List<Movie> = emptyList(),
        val currentTab: String = "Inicio", // "Inicio", "Filmes", "Series", "MeusTitulos"
        val searchResults: List<Movie> = emptyList(),
        val visibleCount: Int = 24,
        val isSearching: Boolean = false,
        val searchQuery: String = "",
        val isLoadingSearch: Boolean = false,
        val selectedCategory: String = "Tudo", // "Tudo", "Filmes", "Séries", "Favoritos"
        val watchHistory: List<WatchHistoryItem> = emptyList(),
        val recentSearches: List<String> = emptyList(),
        val isRefreshingCatalog: Boolean = false
    ) : AppState()

    data class SeriesDetails(
        val series: Movie,
        val previousState: HomeLoaded,
        val episodes: List<Episode> = emptyList(),
        val availableSeasons: List<Int> = listOf(1),
        val selectedSeason: Int = 1,
        val selectedAudio: String = "Dublado", // "Dublado" or "Legendado"
        val isLoading: Boolean = true,
        val isLoadingSeason: Boolean = false
    ) : AppState()

    data class Playing(
        val url: String,
        val title: String = "",
        val seriesUrl: String = "",
        val coverUrl: String = "",
        val isSeries: Boolean = false,
        val season: Int = 1,
        val availableSeasons: List<Int> = listOf(1),
        val episodes: List<Episode> = emptyList(),
        val currentEpisodeIndex: Int = 0,
        val previousState: AppState? = null
    ) : AppState()
}
