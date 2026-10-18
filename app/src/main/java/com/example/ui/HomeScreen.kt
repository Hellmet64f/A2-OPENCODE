package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.AppState
import com.example.model.Movie
import com.example.model.WatchHistoryItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NetflixHome(
    state: AppState.HomeLoaded,
    onMovieClick: (Movie) -> Unit,
    onLoadMore: () -> Unit,
    onOpenSearch: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onSelectTab: (String) -> Unit,
    onSelectAnimeGenre: (String) -> Unit = {},
    onSelectAnimeAudio: (String) -> Unit = {},
    onPlayHistoryItem: (WatchHistoryItem) -> Unit = {},
    onOpenHistoryDetails: (WatchHistoryItem) -> Unit = {},
    onRemoveHistoryItem: (WatchHistoryItem) -> Unit = {},
    onClearHistory: () -> Unit = {}
) {
    var showCreditsDialog by remember { mutableStateOf(false) }
    val isAnimeTab = state.currentTab == "Animes"
    val isHistoryTab = state.currentTab == "MeusTitulos"
    val currentList = if (isAnimeTab) state.animeMovies else state.allMovies

    val filteredMovies = if (isAnimeTab) {
        currentList.filter { anime ->
            val titleLower = anime.title.lowercase()
            val matchesGenre = when (state.selectedAnimeGenre) {
                "Todos" -> true
                "Shonen" -> titleLower.contains("shippuden") || titleLower.contains("piece") ||
                        titleLower.contains("bleach") || titleLower.contains("dragon") ||
                        titleLower.contains("hunter") || titleLower.contains("jujutsu") ||
                        titleLower.contains("hero") || titleLower.contains("naruto") ||
                        titleLower.contains("boruto") || titleLower.contains("clover") ||
                        anime.genre.contains("Shonen", true)
                "Isekai" -> titleLower.contains("slime") || titleLower.contains("re:zero") ||
                        titleLower.contains("sword art") || titleLower.contains("shield") ||
                        titleLower.contains("overlord") || titleLower.contains("mushoku") ||
                        titleLower.contains("isekai") || titleLower.contains("world") ||
                        anime.genre.contains("Isekai", true)
                "Ação" -> titleLower.contains("attack") || titleLower.contains("titan") ||
                        titleLower.contains("solo") || titleLower.contains("demon") ||
                        titleLower.contains("slayer") || titleLower.contains("chainsaw") ||
                        titleLower.contains("fate") || titleLower.contains("tokyo") ||
                        anime.genre.contains("Ação", true)
                "Romance" -> titleLower.contains("kaguya") || titleLower.contains("horimiya") ||
                        titleLower.contains("toradora") || titleLower.contains("dress-up") ||
                        titleLower.contains("love") || titleLower.contains("shigatsu") ||
                        anime.genre.contains("Romance", true)
                "Fantasia" -> titleLower.contains("magic") || titleLower.contains("dungeon") ||
                        titleLower.contains("fairy") || titleLower.contains("danmachi") ||
                        titleLower.contains("frieren") || anime.genre.contains("Fantasia", true)
                "Dublados" -> titleLower.contains("dub") || titleLower.contains("dublado") ||
                        anime.audio.contains("Dub", true) || true
                else -> true
            }
            val matchesAudio = when (state.selectedAnimeAudio) {
                "Dublado" -> titleLower.contains("dub") || titleLower.contains("dublado") ||
                        anime.audio.contains("Dub", true) || true
                "Legendado" -> true
                else -> true
            }
            matchesGenre && matchesAudio
        }
    } else {
        when (state.selectedCategory) {
            "Filmes" -> currentList.filter { !it.isSeries }
            "Séries" -> currentList.filter { it.isSeries }
            else -> currentList
        }
    }

    val featuredMovie = filteredMovies.firstOrNull() ?: currentList.firstOrNull()
    val trendingItems = remember(currentList) { currentList.take(12) }
    val secondRowItems = remember(currentList) { currentList.drop(12).take(12) }
    val catalogList = filteredMovies.take(state.visibleCount)

    val firstCardFocusRequester = remember { FocusRequester() }

    // Request initial focus on the first card whenever tab or filter changes
    LaunchedEffect(state.currentTab, state.selectedCategory, state.selectedAnimeGenre, state.selectedAnimeAudio) {
        delay(200)
        try {
            firstCardFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F12))
    ) {
        if (showCreditsDialog) {
            CreditsDialog(onDismiss = { showCreditsDialog = false })
        }

        // 1. Top Bar: App brand, Credits, Search, and Tabs [🎬 Filmes | ⚡ Animes | 🕒 Meus Títulos]
        HomeTopBar(
            currentTab = state.currentTab,
            onSelectTab = onSelectTab,
            onOpenSearch = onOpenSearch,
            onOpenCredits = { showCreditsDialog = true }
        )

        // 2. Sub-Category / Filter Pills in Header
        if (!isHistoryTab) {
            if (!isAnimeTab) {
                CategoryTabs(
                    selectedCategory = state.selectedCategory,
                    onSelectCategory = onSelectCategory
                )
            } else {
                AnimeGenreTabs(
                    selectedGenre = state.selectedAnimeGenre,
                    onSelectGenre = onSelectAnimeGenre
                )
                Spacer(modifier = Modifier.height(4.dp))
                AnimeAudioTabs(
                    selectedAudio = state.selectedAnimeAudio,
                    onSelectAudio = onSelectAnimeAudio
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        // 3. Main Content: Direct Grid matching SearchScreen structure
        if (isHistoryTab) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                MyTitlesSection(
                    history = state.watchHistory,
                    onPlayItem = onPlayHistoryItem,
                    onOpenDetails = onOpenHistoryDetails,
                    onRemoveItem = onRemoveHistoryItem,
                    onClearAll = onClearHistory,
                    onExploreCatalog = { onSelectTab("Filmes") }
                )
            }
        } else if (state.isLoadingAnime && isAnimeTab && catalogList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color(0xFF6C5CE7))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Carregando catálogo AniChan...", color = Color.LightGray, fontSize = 14.sp)
                }
            }
        } else if (catalogList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Nenhum título encontrado nesta categoria",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 130.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                itemsIndexed(catalogList, key = { index, movie -> if (movie.url.isNotBlank()) movie.url else "${movie.title}_$index" }) { index, movie ->
                    MoviePosterCard(
                        movie = movie,
                        onClick = { onMovieClick(movie) },
                        focusRequester = if (index == 0) firstCardFocusRequester else null
                    )
                }

                if (state.visibleCount < filteredMovies.size) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            var isBtnFocused by remember { mutableStateOf(false) }
                            Button(
                                onClick = onLoadMore,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isAnimeTab) Color(0xFF6C5CE7) else Color(0xFFE50914)
                                ),
                                shape = RoundedCornerShape(24.dp),
                                border = if (isBtnFocused) BorderStroke(2.5.dp, Color(0xFFFFD166)) else null,
                                modifier = Modifier
                                    .onFocusChanged { isBtnFocused = it.isFocused }
                                    .focusable(),
                                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp)
                            ) {
                                Text(
                                    "Carregar Mais Títulos",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MyTitlesSection(
    history: List<WatchHistoryItem>,
    onPlayItem: (WatchHistoryItem) -> Unit,
    onOpenDetails: (WatchHistoryItem) -> Unit,
    onRemoveItem: (WatchHistoryItem) -> Unit,
    onClearAll: () -> Unit,
    onExploreCatalog: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        if (history.isEmpty()) {
            // Empty State
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = Color(0xFF1E2028),
                        shape = CircleShape,
                        modifier = Modifier.size(80.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = Color(0xFFE50914),
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Nenhum título no seu histórico",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Quando você assistir a filmes, séries ou animes, eles serão salvos aqui com o episódio exato onde você parou!",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 32.dp),
                        lineHeight = 18.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onExploreCatalog,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Explorar Catálogo", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            // Header with count & Clear button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Continuar Assistindo",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${history.size} títulos com progresso salvo",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }

                TextButton(
                    onClick = onClearAll,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.LightGray)
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = "Limpar Tudo", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Limpar", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // History List
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                history.forEach { item ->
                    WatchHistoryCard(
                        item = item,
                        onPlay = { onPlayItem(item) },
                        onDetails = { onOpenDetails(item) },
                        onRemove = { onRemoveItem(item) }
                    )
                }
            }
        }
    }
}

@Composable
fun WatchHistoryCard(
    item: WatchHistoryItem,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
    onRemove: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onPlay,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isFocused) 3.5.dp else 1.dp,
                color = if (isFocused) Color(0xFFFFD166) else Color(0xFF262936),
                shape = RoundedCornerShape(16.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyUp && (
                    keyEvent.key == Key.DirectionCenter ||
                    keyEvent.key == Key.Enter ||
                    keyEvent.key == Key.NumPadEnter
                )) {
                    onPlay()
                    true
                } else false
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isFocused) Color(0xFF262938) else Color(0xFF161822)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 6.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover Image
            Box(
                modifier = Modifier
                    .width(90.dp)
                    .height(125.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF22242F))
            ) {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Type badge
                Surface(
                    color = if (item.isAnime) Color(0xFF6C5CE7) else if (item.isSeries) Color(0xFFE50914) else Color(0xFF06D6A0),
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = if (item.isAnime) "ANIME" else if (item.isSeries) "SÉRIE" else "FILME",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Details and actions
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Title
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Episode Stopped Marker Badge
                Surface(
                    color = (if (item.isAnime) Color(0xFF6C5CE7) else Color(0xFFE50914)).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, (if (item.isAnime) Color(0xFF6C5CE7) else Color(0xFFE50914)).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bookmark,
                            contentDescription = null,
                            tint = if (item.isAnime) Color(0xFFA29BFE) else Color(0xFFFF6B6B),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (item.isAnime) {
                                "Parou no Episódio ${item.episodeNumber}"
                            } else if (item.isSeries) {
                                "Parou na Temp ${item.season} • Ep ${item.episodeNumber}"
                            } else {
                                "Assistido Recentemente"
                            },
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (item.episodeTitle.isNotBlank() && item.episodeTitle != "Episódio ${item.episodeNumber}") {
                    Text(
                        text = item.episodeTitle,
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Actions: [ ▶ Continuar ] and [ Mudar Ep ] and [ Delete ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Direct Play Button
                    Button(
                        onClick = onPlay,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (item.isAnime) Color(0xFF6C5CE7) else Color(0xFFE50914)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (item.isSeries || item.isAnime) "Continuar Ep ${item.episodeNumber}" else "Assistir",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Change Episode / Details
                    if (item.isSeries || item.isAnime) {
                        Surface(
                            color = Color(0xFF222432),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF333648)),
                            modifier = Modifier
                                .clickable { onDetails() }
                                .focusable()
                        ) {
                            Text(
                                text = "Mudar Ep",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                            )
                        }
                    }

                    // Remove item from history
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remover do histórico",
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnimeGenreTabs(
    selectedGenre: String,
    onSelectGenre: (String) -> Unit
) {
    val genres = listOf("Todos", "Shonen", "Isekai", "Ação", "Romance", "Fantasia", "Dublados")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        genres.forEach { genre ->
            val isSelected = selectedGenre == genre
            var isFocused by remember { mutableStateOf(false) }
            Surface(
                color = if (isSelected) Color(0xFF6C5CE7) else if (isFocused) Color(0xFF333642) else Color(0xFF1E2028),
                shape = RoundedCornerShape(16.dp),
                border = if (isFocused) BorderStroke(2.dp, Color(0xFFFFD166)) else null,
                modifier = Modifier
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusable()
                    .clickable { onSelectGenre(genre) }
            ) {
                Text(
                    text = genre,
                    color = Color.White,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
fun AnimeAudioTabs(
    selectedAudio: String,
    onSelectAudio: (String) -> Unit
) {
    val audios = listOf("Todos", "Dublado", "Legendado")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        audios.forEach { audio ->
            val isSelected = selectedAudio == audio
            var isFocused by remember { mutableStateOf(false) }
            Surface(
                color = if (isSelected) Color(0xFFA29BFE).copy(alpha = 0.25f) else Color(0xFF161820),
                shape = RoundedCornerShape(12.dp),
                border = if (isFocused) BorderStroke(2.dp, Color(0xFFFFD166)) else BorderStroke(1.dp, if (isSelected) Color(0xFF6C5CE7) else Color(0xFF2E313D)),
                modifier = Modifier
                    .onFocusChanged { isFocused = it.isFocused }
                    .focusable()
                    .clickable { onSelectAudio(audio) }
            ) {
                Text(
                    text = when (audio) {
                        "Dublado" -> "🎙️ Dublado"
                        "Legendado" -> "🇯🇵 Legendado"
                        else -> "Todos os Áudios"
                    },
                    color = if (isSelected) Color(0xFFA29BFE) else Color.LightGray,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
fun HomeTopBar(
    currentTab: String,
    onSelectTab: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenCredits: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onOpenCredits() }
                    .focusable()
            ) {
                Text(
                    text = "A2 Miguel",
                    color = Color(0xFFE50914),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = Color(0xFF6C5CE7).copy(alpha = 0.25f),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, Color(0xFF6C5CE7).copy(alpha = 0.5f))
                ) {
                    Text(
                        text = "NO-ADS",
                        color = Color(0xFFA29BFE),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                var isCreditsFocused by remember { mutableStateOf(false) }
                Surface(
                    color = if (isCreditsFocused) Color(0xFFE50914) else Color(0xFF22242B),
                    shape = CircleShape,
                    border = if (isCreditsFocused) BorderStroke(2.5.dp, Color(0xFFFFD166)) else null,
                    modifier = Modifier
                        .onFocusChanged { isCreditsFocused = it.isFocused }
                        .clickable { onOpenCredits() }
                        .focusable()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Créditos",
                            tint = if (isCreditsFocused) Color.White else Color(0xFFFFD166),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Créditos",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                var isSearchFocused by remember { mutableStateOf(false) }
                Surface(
                    color = if (isSearchFocused) Color(0xFFE50914) else Color(0xFF22242B),
                    shape = CircleShape,
                    border = if (isSearchFocused) BorderStroke(2.5.dp, Color(0xFFFFD166)) else null,
                    modifier = Modifier
                        .onFocusChanged { isSearchFocused = it.isFocused }
                        .clickable { onOpenSearch() }
                        .focusable()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Pesquisar",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Buscar",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Main Navigation Tabs: [ Filmes & Séries ] | [ Animes (AniChan) ] | [ Meus Títulos ]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val isFilmes = currentTab == "Filmes"
            val isAnimes = currentTab == "Animes"
            val isMeusTitulos = currentTab == "MeusTitulos"

            // 1. Filmes & Séries
            var isFilmesFocused by remember { mutableStateOf(false) }
            Surface(
                onClick = { onSelectTab("Filmes") },
                color = if (isFilmes) Color(0xFFE50914) else if (isFilmesFocused) Color(0xFF2E3345) else Color(0xFF1E2028),
                shape = RoundedCornerShape(12.dp),
                border = if (isFilmesFocused) BorderStroke(2.5.dp, Color(0xFFFFD166)) else null,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isFilmesFocused = it.isFocused }
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyUp && (
                            keyEvent.key == Key.DirectionCenter ||
                            keyEvent.key == Key.Enter ||
                            keyEvent.key == Key.NumPadEnter
                        )) {
                            onSelectTab("Filmes")
                            true
                        } else false
                    }
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🎬 Filmes",
                        color = Color.White,
                        fontWeight = if (isFilmes || isFilmesFocused) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }

            // 2. Animes
            var isAnimesFocused by remember { mutableStateOf(false) }
            Surface(
                onClick = { onSelectTab("Animes") },
                color = if (isAnimes) Color(0xFF6C5CE7) else if (isAnimesFocused) Color(0xFF2E3345) else Color(0xFF1E2028),
                shape = RoundedCornerShape(12.dp),
                border = if (isAnimesFocused) BorderStroke(2.5.dp, Color(0xFFFFD166)) else null,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { isAnimesFocused = it.isFocused }
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyUp && (
                            keyEvent.key == Key.DirectionCenter ||
                            keyEvent.key == Key.Enter ||
                            keyEvent.key == Key.NumPadEnter
                        )) {
                            onSelectTab("Animes")
                            true
                        } else false
                    }
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⚡ Animes",
                        color = Color.White,
                        fontWeight = if (isAnimes || isAnimesFocused) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }

            // 3. Meus Títulos (Histórico e Onde Parou)
            var isHistoryFocused by remember { mutableStateOf(false) }
            Surface(
                onClick = { onSelectTab("MeusTitulos") },
                color = if (isMeusTitulos) Color(0xFF06D6A0) else if (isHistoryFocused) Color(0xFF2E3345) else Color(0xFF1E2028),
                shape = RoundedCornerShape(12.dp),
                border = if (isHistoryFocused) BorderStroke(2.5.dp, Color(0xFFFFD166)) else null,
                modifier = Modifier
                    .weight(1.1f)
                    .onFocusChanged { isHistoryFocused = it.isFocused }
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyUp && (
                            keyEvent.key == Key.DirectionCenter ||
                            keyEvent.key == Key.Enter ||
                            keyEvent.key == Key.NumPadEnter
                        )) {
                            onSelectTab("MeusTitulos")
                            true
                        } else false
                    }
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🕒 Meus Títulos",
                        color = if (isMeusTitulos) Color.Black else Color.White,
                        fontWeight = if (isMeusTitulos || isHistoryFocused) FontWeight.ExtraBold else FontWeight.Medium,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun CategoryTabs(
    selectedCategory: String,
    onSelectCategory: (String) -> Unit
) {
    val categories = listOf("Tudo", "Filmes", "Séries")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        categories.forEach { cat ->
            val isSelected = selectedCategory == cat
            var isFocused by remember { mutableStateOf(false) }
            Surface(
                onClick = { onSelectCategory(cat) },
                color = if (isSelected) Color(0xFFE50914) else if (isFocused) Color(0xFF333642) else Color(0xFF1E2028),
                shape = RoundedCornerShape(20.dp),
                border = if (isFocused) BorderStroke(2.dp, Color(0xFFFFD166)) else null,
                modifier = Modifier
                    .onFocusChanged { isFocused = it.isFocused }
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyUp && (
                            keyEvent.key == Key.DirectionCenter ||
                            keyEvent.key == Key.Enter ||
                            keyEvent.key == Key.NumPadEnter
                        )) {
                            onSelectCategory(cat)
                            true
                        } else false
                    }
            ) {
                Text(
                    text = cat,
                    color = Color.White,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun HeroBanner(
    movie: Movie,
    isAnime: Boolean,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = if (isFocused) 3.5.dp else 0.dp,
                color = if (isFocused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() }
    ) {
        AsyncImage(
            model = movie.coverUrl,
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.5f),
                            Color(0xFF0F0F12).copy(alpha = 0.95f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (isAnime) Color(0xFF6C5CE7) else Color(0xFFE50914),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isAnime) "ANIME DESTAQUE" else if (movie.isSeries) "SÉRIE EM DESTAQUE" else "DESTAQUE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isAnime) "SUB & DUB" else "FULL HD",
                        color = Color.LightGray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = movie.title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAnime) Color(0xFF6C5CE7) else Color(0xFFE50914)
                ),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (movie.isSeries || isAnime) "Ver Episódios" else "Assistir Agora",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun SectionCarousel(
    title: String,
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    color = Color(0xFF22242B),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable {
                            scope.launch {
                                val next = (listState.firstVisibleItemIndex - 3).coerceAtLeast(0)
                                listState.animateScrollToItem(next)
                            }
                        }
                        .focusable()
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Anterior",
                        tint = Color.White,
                        modifier = Modifier.padding(6.dp)
                    )
                }

                Surface(
                    color = Color(0xFF22242B),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable {
                            scope.launch {
                                val next = (listState.firstVisibleItemIndex + 3).coerceAtMost(movies.size - 1)
                                listState.animateScrollToItem(next)
                            }
                        }
                        .focusable()
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Próximo",
                        tint = Color.White,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(movies) { movie ->
                Box(modifier = Modifier.width(130.dp)) {
                    MoviePosterCard(
                        movie = movie,
                        onClick = { onMovieClick(movie) }
                    )
                }
            }
        }
    }
}

@Composable
fun MoviePosterCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    val baseModifier = modifier
        .fillMaxWidth()
        .aspectRatio(0.67f)
        .border(
            width = if (isFocused) 3.5.dp else 0.dp,
            color = if (isFocused) Color(0xFFFFD166) else Color.Transparent,
            shape = RoundedCornerShape(10.dp)
        )
        .onFocusChanged { isFocused = it.isFocused }

    val focusableModifier = if (focusRequester != null) {
        baseModifier.focusRequester(focusRequester)
    } else {
        baseModifier
    }

    Card(
        onClick = onClick,
        modifier = focusableModifier
            .onKeyEvent { keyEvent ->
                if ((keyEvent.type == KeyEventType.KeyUp || keyEvent.type == KeyEventType.KeyDown) && (
                    keyEvent.key == Key.DirectionCenter ||
                    keyEvent.key == Key.Enter ||
                    keyEvent.key == Key.NumPadEnter
                )) {
                    if (keyEvent.type == KeyEventType.KeyDown) {
                        onClick()
                    }
                    true
                } else false
            },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = if (isFocused) Color(0xFF2E3244) else Color(0xFF1E2028)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 8.dp else 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = movie.coverUrl,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            if (movie.isAnime) {
                Surface(
                    color = Color(0xFF6C5CE7),
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "ANIME",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            } else if (movie.isSeries) {
                Surface(
                    color = Color(0xFFE50914),
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "SÉRIE",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Text(
                    text = movie.title,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun CreditsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF161822),
        titleContentColor = Color.White,
        textContentColor = Color.LightGray,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "A2 Miguel",
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFE50914),
                    fontSize = 20.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = Color(0xFF6C5CE7).copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFF6C5CE7))
                ) {
                    Text(
                        text = "CRÉDITOS",
                        color = Color(0xFFA29BFE),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Surface(
                    color = Color(0xFF202330),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "👑 Criador do Aplicativo",
                            color = Color(0xFFFFD166),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Miguel Rafael Montana",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "App: A2 Miguel",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/miguel_rafael_montana_/"))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                try { context.startActivity(intent) } catch (e: Exception) { e.printStackTrace() }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFE1306C)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "📸 Instagram: @miguel_rafael_montana_",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                Surface(
                    color = Color(0xFF202330),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "🌐 Sites Originais Integrados",
                            color = Color.LightGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.pobreflixtv.delivery/"))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                try { context.startActivity(intent) } catch (e: Exception) { e.printStackTrace() }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Color(0xFFE50914))
                        ) {
                            Text("🎬 Pobreflix TV (pobreflixtv.delivery)", color = Color.White, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://anichan.to/"))
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                try { context.startActivity(intent) } catch (e: Exception) { e.printStackTrace() }
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Color(0xFF6C5CE7))
                        ) {
                            Text("⚡ AniChan (anichan.to)", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }

                Surface(
                    color = Color(0xFF1B1D26),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "🛡️ Sistema Sem Anúncios (No-Ads)",
                            color = Color(0xFF06D6A0),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Bloqueio avançado de popups, redirecionamentos e anúncios invasivos com player embutido.",
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFE50914))
            ) {
                Text("Fechar", fontWeight = FontWeight.Bold)
            }
        }
    )
}
