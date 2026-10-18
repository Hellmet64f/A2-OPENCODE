package com.example.model

data class Movie(
    val title: String,
    val coverUrl: String,
    val url: String,
    val isSeries: Boolean = url.contains("/series/") || url.contains("/anime/"),
    val isAnime: Boolean = url.contains("anichan.to") || url.startsWith("/anime/"),
    val genre: String = "Geral",
    val audio: String = "Dub/Leg"
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
        val animeMovies: List<Movie> = emptyList(),
        val currentTab: String = "Filmes", // "Filmes" (Pobreflix), "Animes" (AniChan), or "MeusTitulos"
        val searchResults: List<Movie> = emptyList(),
        val visibleCount: Int = 18,
        val isSearching: Boolean = false,
        val searchQuery: String = "",
        val isLoadingSearch: Boolean = false,
        val selectedCategory: String = "Tudo", // "Tudo", "Filmes", "Séries"
        val selectedAnimeGenre: String = "Todos", // "Todos", "Shonen", "Isekai", "Ação", "Romance", "Fantasia", "Dublados"
        val selectedAnimeAudio: String = "Todos", // "Todos", "Dublado", "Legendado"
        val isLoadingAnime: Boolean = false,
        val watchHistory: List<WatchHistoryItem> = emptyList()
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
        val isSeries: Boolean = false,
        val episodes: List<Episode> = emptyList(),
        val currentEpisodeIndex: Int = 0,
        val previousState: AppState? = null
    ) : AppState()
}
