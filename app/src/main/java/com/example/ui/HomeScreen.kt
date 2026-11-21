package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.model.AppState
import com.example.model.Movie
import com.example.model.WatchHistoryItem
import com.example.model.WatchHistoryManager
import com.example.network.AdBlockManager
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NetflixHome(
    state: AppState.HomeLoaded,
    userEmail: String?,
    userName: String?,
    adBlockRuleCount: Int,
    adBlockBlockedSession: Int,
    adBlockUpdating: Boolean,
    adBlockAutoUpdate: Boolean,
    onMovieClick: (Movie) -> Unit,
    onLoadMore: () -> Unit,
    onRefreshCatalog: () -> Unit,
    onOpenSearch: () -> Unit,
    onSelectCategory: (String) -> Unit,
    onSelectTab: (String) -> Unit,
    onPlayHistoryItem: (WatchHistoryItem) -> Unit = {},
    onRestartHistoryItem: (WatchHistoryItem) -> Unit = {},
    onOpenHistoryDetails: (WatchHistoryItem) -> Unit = {},
    onToggleFavoriteMovie: (Movie) -> Unit = {},
    onToggleCompletedHistory: (WatchHistoryItem) -> Unit = {},
    onRemoveHistoryItem: (WatchHistoryItem) -> Unit = {},
    onClearHistory: () -> Unit = {},
    onToggleAdBlockAutoUpdate: (Boolean) -> Unit = {},
    onForceUpdateAdBlock: () -> Unit = {},
    onSyncCloudNow: () -> Unit = {},
    onImportJsonBackup: (String) -> Unit = {},
    onSignOut: () -> Unit = {}
) {
    val context = LocalContext.current
    var showDataAdBlockDialog by remember { mutableStateOf(false) }

    val historyByUrl = remember(state.watchHistory) {
        state.watchHistory.associateBy { it.id }
    }

    val displayedMovies = remember(state.allMovies, state.currentTab, state.selectedCategory, state.watchHistory) {
        val base = when (state.currentTab) {
            "Filmes" -> state.allMovies.filter { !it.isSeries }
            "Series" -> state.allMovies.filter { it.isSeries }
            else -> state.allMovies
        }
        when (state.selectedCategory) {
            "Filmes" -> base.filter { !it.isSeries }
            "Séries" -> base.filter { it.isSeries }
            "Favoritos" -> base.filter { m -> historyByUrl[m.url]?.favorite == true }
            "Já Vistos" -> base.filter { m ->
                val h = historyByUrl[m.url]
                h != null && (h.completed || h.watchedEpisodes.isNotEmpty())
            }
            else -> base
        }
    }

    val featuredMovie = remember(displayedMovies, state.allMovies) {
        displayedMovies.firstOrNull() ?: state.allMovies.firstOrNull()
    }
    val catalogList = remember(displayedMovies, state.visibleCount) {
        displayedMovies.take(state.visibleCount)
    }

    val firstCardFocusRequester = remember { FocusRequester() }
    LaunchedEffect(state.currentTab, state.selectedCategory) {
        delay(180)
        try {
            firstCardFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    if (showDataAdBlockDialog) {
        DataAndAdBlockDialog(
            userEmail = userEmail,
            userName = userName,
            history = state.watchHistory,
            adBlockRuleCount = adBlockRuleCount,
            adBlockBlockedSession = adBlockBlockedSession,
            adBlockUpdating = adBlockUpdating,
            adBlockAutoUpdate = adBlockAutoUpdate,
            onToggleAdBlockAutoUpdate = onToggleAdBlockAutoUpdate,
            onForceUpdateAdBlock = onForceUpdateAdBlock,
            onSyncCloudNow = onSyncCloudNow,
            onImportJsonBackup = onImportJsonBackup,
            onSignOut = {
                showDataAdBlockDialog = false
                onSignOut()
            },
            onDismiss = { showDataAdBlockDialog = false }
        )
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        val isWideScreen = maxWidth >= 680.dp

        Row(modifier = Modifier.fillMaxSize()) {
            // Canonical NavigationRail for Tablets / TVs / Wide PC windows
            if (isWideScreen) {
                NavigationRail(
                    containerColor = ObsidianSurface,
                    contentColor = TextPrimary,
                    modifier = Modifier
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    header = {
                        Image(
                            painter = painterResource(id = R.drawable.ic_a2_logo),
                            contentDescription = "A2 Miguel",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .padding(top = 10.dp)
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    NavigationRailItem(
                        selected = state.currentTab == "Inicio",
                        onClick = { onSelectTab("Inicio") },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Início") },
                        label = { Text("Início", fontSize = 11.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = CrimsonPrimary,
                            indicatorColor = CrimsonPrimary,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_rail_inicio")
                    )
                    NavigationRailItem(
                        selected = state.currentTab == "Filmes",
                        onClick = { onSelectTab("Filmes") },
                        icon = { Icon(Icons.Default.Movie, contentDescription = "Filmes") },
                        label = { Text("Filmes", fontSize = 11.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = CrimsonPrimary,
                            indicatorColor = CrimsonPrimary,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_rail_filmes")
                    )
                    NavigationRailItem(
                        selected = state.currentTab == "Series",
                        onClick = { onSelectTab("Series") },
                        icon = { Icon(Icons.Default.Tv, contentDescription = "Séries") },
                        label = { Text("Séries", fontSize = 11.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color.White,
                            selectedTextColor = CrimsonPrimary,
                            indicatorColor = CrimsonPrimary,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_rail_series")
                    )
                    NavigationRailItem(
                        selected = state.currentTab == "MeusTitulos",
                        onClick = { onSelectTab("MeusTitulos") },
                        icon = { Icon(Icons.Default.History, contentDescription = "Histórico") },
                        label = { Text("Histórico", fontSize = 11.sp) },
                        colors = NavigationRailItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = EmeraldAdBlock,
                            indicatorColor = EmeraldAdBlock,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_rail_history")
                    )
                }
            }

            Scaffold(
                containerColor = ObsidianBg,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (!isWideScreen) {
                        NavigationBar(
                            containerColor = ObsidianSurface,
                            tonalElevation = 6.dp
                        ) {
                            NavigationBarItem(
                                selected = state.currentTab == "Inicio",
                                onClick = { onSelectTab("Inicio") },
                                icon = { Icon(Icons.Default.Home, contentDescription = "Início") },
                                label = { Text("Início", maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = CrimsonPrimary,
                                    indicatorColor = CrimsonPrimary,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_bottom_inicio")
                            )
                            NavigationBarItem(
                                selected = state.currentTab == "Filmes",
                                onClick = { onSelectTab("Filmes") },
                                icon = { Icon(Icons.Default.Movie, contentDescription = "Filmes") },
                                label = { Text("Filmes", maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = CrimsonPrimary,
                                    indicatorColor = CrimsonPrimary,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_bottom_filmes")
                            )
                            NavigationBarItem(
                                selected = state.currentTab == "Series",
                                onClick = { onSelectTab("Series") },
                                icon = { Icon(Icons.Default.Tv, contentDescription = "Séries") },
                                label = { Text("Séries", maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.White,
                                    selectedTextColor = CrimsonPrimary,
                                    indicatorColor = CrimsonPrimary,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_bottom_series")
                            )
                            NavigationBarItem(
                                selected = state.currentTab == "MeusTitulos",
                                onClick = { onSelectTab("MeusTitulos") },
                                icon = { Icon(Icons.Default.Bookmarks, contentDescription = "Salvos e Histórico") },
                                label = { Text("Histórico", maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Color.Black,
                                    selectedTextColor = EmeraldAdBlock,
                                    indicatorColor = EmeraldAdBlock,
                                    unselectedIconColor = TextSecondary,
                                    unselectedTextColor = TextSecondary
                                ),
                                modifier = Modifier.testTag("nav_bottom_history")
                            )
                        }
                    }
                },
                modifier = Modifier.weight(1f)
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    HomeTopBar(
                        adBlockRuleCount = adBlockRuleCount,
                        isRefreshingCatalog = state.isRefreshingCatalog,
                        onOpenSearch = onOpenSearch,
                        onRefreshCatalog = onRefreshCatalog,
                        onOpenSettings = { showDataAdBlockDialog = true }
                    )

                    if (state.currentTab == "MeusTitulos") {
                        MyTitlesSection(
                            history = state.watchHistory,
                            onPlayItem = onPlayHistoryItem,
                            onRestartItem = onRestartHistoryItem,
                            onOpenDetails = onOpenHistoryDetails,
                            onToggleCompleted = onToggleCompletedHistory,
                            onRemoveItem = onRemoveHistoryItem,
                            onClearAll = onClearHistory,
                            onExploreCatalog = { onSelectTab("Inicio") },
                            onOpenCloudSettings = { showDataAdBlockDialog = true },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        CategoryFilterChips(
                            selectedCategory = state.selectedCategory,
                            onSelectCategory = onSelectCategory
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        if (catalogList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f)
                                    .padding(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.MovieFilter,
                                        contentDescription = null,
                                        tint = TextMuted,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    Text(
                                        text = if (state.isRefreshingCatalog) "Atualizando catálogo..." else "Nenhum título encontrado neste filtro",
                                        color = TextPrimary,
                                        style = MaterialTheme.typography.titleLarge,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Button(
                                        onClick = {
                                            if (state.selectedCategory != "Tudo") {
                                                onSelectCategory("Tudo")
                                            } else {
                                                onRefreshCatalog()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text(
                                            if (state.selectedCategory != "Tudo") "Mostrar Tudo" else "Recarregar Catálogo",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = if (isWideScreen) 150.dp else 116.dp),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f)
                            ) {
                                // Hero Banner on "Inicio" tab when "Tudo" is selected
                                if (state.currentTab == "Inicio" && state.selectedCategory == "Tudo" && featuredMovie != null) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        val featuredHistory = historyByUrl[featuredMovie.url]
                                        HeroBanner(
                                            movie = featuredMovie,
                                            isFavorite = featuredHistory?.favorite == true,
                                            onPlayClick = { onMovieClick(featuredMovie) },
                                            onToggleFavorite = { onToggleFavoriteMovie(featuredMovie) }
                                        )
                                    }
                                }

                                // "Continuar Assistindo" carousel directly on Início
                                if (state.currentTab == "Inicio" && state.watchHistory.isNotEmpty() && state.selectedCategory == "Tudo") {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        ContinueWatchingCarousel(
                                            history = state.watchHistory.take(12),
                                            onPlayItem = onPlayHistoryItem,
                                            onOpenDetails = onOpenHistoryDetails,
                                            onSeeAll = { onSelectTab("MeusTitulos") }
                                        )
                                    }
                                }

                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = when (state.currentTab) {
                                                "Filmes" -> "Filmes Disponíveis"
                                                "Series" -> "Séries Completas"
                                                else -> "Catálogo Completo (${displayedMovies.size})"
                                            },
                                            style = MaterialTheme.typography.titleLarge,
                                            color = TextPrimary
                                        )
                                        Text(
                                            text = "Exibindo ${catalogList.size}",
                                            color = TextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                itemsIndexed(
                                    catalogList,
                                    key = { index, movie -> if (movie.url.isNotBlank()) "${movie.url}_$index" else "${movie.title}_$index" }
                                ) { index, movie ->
                                    val hist = historyByUrl[movie.url]
                                    MoviePosterCard(
                                        movie = movie,
                                        isFavorite = hist?.favorite == true,
                                        isWatched = hist != null && (hist.completed || hist.watchedEpisodes.isNotEmpty()),
                                        onClick = { onMovieClick(movie) },
                                        focusRequester = if (index == 0) firstCardFocusRequester else null
                                    )
                                }

                                if (state.visibleCount < displayedMovies.size) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Button(
                                                onClick = onLoadMore,
                                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                                                shape = RoundedCornerShape(24.dp),
                                                modifier = Modifier
                                                    .heightIn(min = 48.dp)
                                                    .testTag("load_more_button"),
                                                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp)
                                            ) {
                                                Icon(Icons.Default.ExpandMore, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Carregar Mais (${displayedMovies.size - catalogList.size} restantes)", fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HomeTopBar(
    adBlockRuleCount: Int,
    isRefreshingCatalog: Boolean,
    onOpenSearch: () -> Unit,
    onRefreshCatalog: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable { onOpenSettings() }
                .padding(4.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_a2_logo),
                contentDescription = "A2 Miguel",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "A2 MIGUEL",
                    style = MaterialTheme.typography.titleLarge,
                    color = CrimsonPrimary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Filmes & Séries",
                    color = TextSecondary,
                    fontSize = 10.sp
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // AdBlock + Salvar Dados Pill
            Surface(
                onClick = onOpenSettings,
                color = EmeraldAdBlock.copy(alpha = 0.14f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, EmeraldAdBlock.copy(alpha = 0.5f)),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("adblock_settings_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "AdBlock e Dados na Nuvem",
                        tint = EmeraldAdBlock,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "$adBlockRuleCount+ AdBlock",
                        color = EmeraldAdBlock,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }

            IconButton(
                onClick = onRefreshCatalog,
                modifier = Modifier
                    .size(48.dp)
                    .background(ObsidianCard, CircleShape)
                    .testTag("refresh_catalog_button")
            ) {
                if (isRefreshingCatalog) {
                    CircularProgressIndicator(
                        color = CrimsonPrimary,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Atualizar catálogo",
                        tint = TextPrimary
                    )
                }
            }

            IconButton(
                onClick = onOpenSearch,
                modifier = Modifier
                    .size(48.dp)
                    .background(CrimsonPrimary, CircleShape)
                    .testTag("home_search_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Buscar filmes e séries",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
fun CategoryFilterChips(
    selectedCategory: String,
    onSelectCategory: (String) -> Unit
) {
    val categories = listOf("Tudo", "Filmes", "Séries", "Favoritos", "Já Vistos")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        categories.forEach { cat ->
            val isSelected = selectedCategory == cat
            var isFocused by remember { mutableStateOf(false) }
            FilterChip(
                selected = isSelected,
                onClick = { onSelectCategory(cat) },
                label = {
                    Text(
                        text = cat,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = if (cat == "Já Vistos") EmeraldAdBlock else if (cat == "Favoritos") AmberGold else CrimsonPrimary,
                    selectedLabelColor = if (cat == "Favoritos" || cat == "Já Vistos") Color.Black else Color.White,
                    containerColor = ObsidianCard,
                    labelColor = TextSecondary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = if (isFocused) AmberGold else ObsidianStroke,
                    borderWidth = if (isFocused) 2.dp else 1.dp
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .onFocusChanged { isFocused = it.isFocused }
                    .testTag("category_chip_${cat.lowercase().replace(" ", "_")}")
            )
        }
    }
}

@Composable
fun HeroBanner(
    movie: Movie,
    isFavorite: Boolean,
    onPlayClick: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) AmberGold else ObsidianStroke,
                shape = RoundedCornerShape(18.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onPlayClick() }
            .testTag("hero_banner_card")
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
                            ObsidianBg.copy(alpha = 0.45f),
                            ObsidianBg.copy(alpha = 0.96f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    color = CrimsonPrimary,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = if (movie.isSeries) "SÉRIE EM DESTAQUE" else "FILME EM DESTAQUE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
                Surface(
                    color = EmeraldAdBlock.copy(alpha = 0.22f),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, EmeraldAdBlock.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = "HD • SEM ANÚNCIOS",
                        color = EmeraldAdBlock,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = movie.title,
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onPlayClick,
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (movie.isSeries) "Ver Temporadas & Episódios" else "Assistir Agora",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(48.dp)
                        .background(ObsidianCardElevated.copy(alpha = 0.85f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remover favorito" else "Salvar favorito",
                        tint = if (isFavorite) AmberGold else Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ContinueWatchingCarousel(
    history: List<WatchHistoryItem>,
    onPlayItem: (WatchHistoryItem) -> Unit,
    onOpenDetails: (WatchHistoryItem) -> Unit,
    onSeeAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PlayCircle, contentDescription = null, tint = EmeraldAdBlock, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Continuar Assistindo",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary
                )
            }
            TextButton(onClick = onSeeAll) {
                Text("Ver Tudo", color = CrimsonPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(history, key = { it.id }) { item ->
                Card(
                    onClick = { onPlayItem(item) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                    border = BorderStroke(1.dp, ObsidianStroke),
                    modifier = Modifier
                        .width(210.dp)
                        .testTag("continue_card_${item.title.take(12)}")
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = item.coverUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(58.dp)
                                .height(84.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ObsidianCardElevated)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (item.isSeries) "T${item.season} • Ep ${item.episodeNumber}" else "Filme",
                                color = AmberGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                            if (item.watchedEpisodes.isNotEmpty()) {
                                Text(
                                    text = "${item.watchedEpisodes.size} eps vistos",
                                    color = EmeraldAdBlock,
                                    fontSize = 10.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            if (item.isSeries) {
                                Text(
                                    text = "Trocar ep / temp →",
                                    color = CyanAccent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onOpenDetails(item) }
                                        .padding(vertical = 2.dp)
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
fun MoviePosterCard(
    movie: Movie,
    isFavorite: Boolean = false,
    isWatched: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    val baseModifier = modifier
        .fillMaxWidth()
        .aspectRatio(0.67f)
        .border(
            width = if (isFocused) 3.dp else 1.dp,
            color = if (isFocused) AmberGold else ObsidianStroke.copy(alpha = 0.6f),
            shape = RoundedCornerShape(12.dp)
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
                if (keyEvent.type == KeyEventType.KeyDown && (
                        keyEvent.key == Key.DirectionCenter ||
                            keyEvent.key == Key.Enter ||
                            keyEvent.key == Key.NumPadEnter
                        )
                ) {
                    onClick()
                    true
                } else false
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isFocused) ObsidianCardElevated else ObsidianCard),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 8.dp else 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = movie.coverUrl,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Type Badge (SÉRIE / FILME)
            Surface(
                color = if (movie.isSeries) CrimsonPrimary else CyanAccent,
                shape = RoundedCornerShape(bottomEnd = 8.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = if (movie.isSeries) "SÉRIE" else "FILME",
                    color = if (movie.isSeries) Color.White else Color.Black,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            // Watched or Favorite indicators
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (isWatched) {
                    Surface(
                        color = EmeraldAdBlock,
                        shape = CircleShape,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Já visto",
                                tint = Color.Black,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
                if (isFavorite) {
                    Surface(
                        color = ObsidianBg.copy(alpha = 0.85f),
                        shape = CircleShape,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Favorito",
                                tint = AmberGold,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, ObsidianBg.copy(alpha = 0.96f))
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Text(
                    text = movie.title,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun MyTitlesSection(
    history: List<WatchHistoryItem>,
    onPlayItem: (WatchHistoryItem) -> Unit,
    onRestartItem: (WatchHistoryItem) -> Unit,
    onOpenDetails: (WatchHistoryItem) -> Unit,
    onToggleCompleted: (WatchHistoryItem) -> Unit,
    onRemoveItem: (WatchHistoryItem) -> Unit,
    onClearAll: () -> Unit,
    onExploreCatalog: () -> Unit,
    onOpenCloudSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        color = ObsidianCard,
                        shape = CircleShape,
                        modifier = Modifier.size(84.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = CrimsonPrimary,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = "Seu histórico está vazio",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Filmes e séries que você assistir ou favoritar aparecem aqui com os episódios vistos salvos na nuvem gratuitamente.",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = onExploreCatalog,
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Explorar Catálogo", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onOpenCloudSettings,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, ObsidianStroke),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = EmeraldAdBlock, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Importar JSON", color = TextPrimary)
                        }
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Histórico & Já Vistos",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "${history.size} títulos salvos na sua conta gratuita",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onOpenCloudSettings,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, EmeraldAdBlock.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.CloudDone, contentDescription = null, tint = EmeraldAdBlock, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Backup", color = EmeraldAdBlock, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(
                        onClick = onClearAll,
                        colors = ButtonDefaults.textButtonColors(contentColor = TextSecondary),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "Limpar Tudo", modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Limpar", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                history.forEach { item ->
                    WatchHistoryCard(
                        item = item,
                        onPlay = { onPlayItem(item) },
                        onRestart = { onRestartItem(item) },
                        onDetails = { onOpenDetails(item) },
                        onToggleCompleted = { onToggleCompleted(item) },
                        onRemove = { onRemoveItem(item) }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun WatchHistoryCard(
    item: WatchHistoryItem,
    onPlay: () -> Unit,
    onRestart: () -> Unit,
    onDetails: () -> Unit,
    onToggleCompleted: () -> Unit,
    onRemove: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onPlay,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isFocused) 2.5.dp else 1.dp,
                color = if (isFocused) AmberGold else ObsidianStroke,
                shape = RoundedCornerShape(16.dp)
            )
            .onFocusChanged { isFocused = it.isFocused },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (isFocused) ObsidianCardElevated else ObsidianCard),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 6.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(84.dp)
                    .height(120.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianCardElevated)
            ) {
                AsyncImage(
                    model = item.coverUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    color = if (item.isSeries) CrimsonPrimary else CyanAccent,
                    shape = RoundedCornerShape(bottomEnd = 6.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = if (item.isSeries) "SÉRIE" else "FILME",
                        color = if (item.isSeries) Color.White else Color.Black,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                if (item.completed) {
                    Surface(
                        color = EmeraldAdBlock,
                        shape = RoundedCornerShape(topStart = 6.dp),
                        modifier = Modifier.align(Alignment.BottomEnd)
                    ) {
                        Text(
                            text = "VISTO ✓",
                            color = Color.Black,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onRemove,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remover do histórico",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Stopped marker + watched count
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = CrimsonPrimary.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, CrimsonPrimary.copy(alpha = 0.45f))
                    ) {
                        Text(
                            text = if (item.isSeries) "Temp ${item.season} • Ep ${item.episodeNumber}" else "Filme salvo",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (item.isSeries && item.watchedEpisodes.isNotEmpty()) {
                        Surface(
                            color = EmeraldAdBlock.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "${item.watchedEpisodes.size} vistos",
                                color = EmeraldAdBlock,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                // Action Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPlay,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (item.isSeries) "Continuar" else "Assistir",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (item.isSeries) {
                        OutlinedButton(
                            onClick = onDetails,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, ObsidianStroke),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Temp / Ep", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    OutlinedButton(
                        onClick = onRestart,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ObsidianStroke),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = "Reiniciar", tint = AmberGold, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reiniciar", color = TextPrimary, fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onToggleCompleted,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (item.completed) EmeraldAdBlock else ObsidianStroke),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Marcar como já visto",
                            tint = if (item.completed) EmeraldAdBlock else TextSecondary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (item.completed) "Já Visto" else "Marcar Visto", color = TextPrimary, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DataAndAdBlockDialog(
    userEmail: String?,
    userName: String?,
    history: List<WatchHistoryItem>,
    adBlockRuleCount: Int,
    adBlockBlockedSession: Int,
    adBlockUpdating: Boolean,
    adBlockAutoUpdate: Boolean,
    onToggleAdBlockAutoUpdate: (Boolean) -> Unit,
    onForceUpdateAdBlock: () -> Unit,
    onSyncCloudNow: () -> Unit,
    onImportJsonBackup: (String) -> Unit,
    onSignOut: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var importJsonInput by remember { mutableStateOf("") }
    var showImportBox by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ObsidianSurface,
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        shape = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.drawable.ic_a2_logo),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Central A2 Miguel", fontWeight = FontWeight.Black, color = CrimsonPrimary, fontSize = 18.sp)
                    Text("AdBlock 70+ • Dados Grátis na Nuvem", color = TextSecondary, fontSize = 11.sp)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. AdBlock Section
                Surface(
                    color = ObsidianCard,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, EmeraldAdBlock.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = EmeraldAdBlock, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("AdBlock 70+ Auto-Atualizável", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Text(
                                text = "$adBlockRuleCount regras",
                                color = EmeraldAdBlock,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            text = "Bloqueados nesta sessão: $adBlockBlockedSession popups/anúncios.",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Atualizar listas automaticamente", color = TextPrimary, fontSize = 12.sp)
                            Switch(
                                checked = adBlockAutoUpdate,
                                onCheckedChange = onToggleAdBlockAutoUpdate,
                                colors = SwitchDefaults.colors(checkedTrackColor = EmeraldAdBlock)
                            )
                        }
                        Button(
                            onClick = onForceUpdateAdBlock,
                            enabled = !adBlockUpdating,
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldAdBlock, contentColor = Color.Black),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("force_update_adblock_button")
                        ) {
                            if (adBlockUpdating) {
                                CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Atualizando listas...", fontWeight = FontWeight.Bold)
                            } else {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Atualizar Regras AdBlock Agora", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 2. Free Cloud Account & Portable JSON Backup
                Surface(
                    color = ObsidianCard,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ObsidianStroke),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("☁️ Salvar Dados de Graça (Celular & PC)", color = AmberGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            text = "Conta conectada: ${userName ?: userEmail ?: "Google"}",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!userEmail.isNullOrBlank()) {
                            Text(text = userEmail, color = TextSecondary, fontSize = 11.sp)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = onSyncCloudNow,
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Sincronizar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = onSignOut,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, ObsidianStroke),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sair", color = TextPrimary, fontSize = 12.sp)
                            }
                        }

                        HorizontalDivider(color = ObsidianStroke)

                        Text("Transferir entre Celular e PC (JSON grátis):", color = TextSecondary, fontSize = 11.sp)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    val json = WatchHistoryManager.exportToJson(context, history)
                                    clipboard.setText(AnnotatedString(json))
                                    Toast.makeText(context, "Backup JSON copiado! Cole no PC ou compartilhe.", Toast.LENGTH_LONG).show()
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, CyanAccent.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Exportar JSON", color = CyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = { showImportBox = !showImportBox },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Importar JSON", color = AmberGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (showImportBox) {
                            OutlinedTextField(
                                value = importJsonInput,
                                onValueChange = { importJsonInput = it },
                                label = { Text("Cole aqui o JSON do PC ou backup", fontSize = 11.sp) },
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    if (importJsonInput.isNotBlank()) {
                                        onImportJsonBackup(importJsonInput)
                                        importJsonInput = ""
                                        showImportBox = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AmberGold, contentColor = Color.Black),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Restaurar & Salvar na Nuvem", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 3. Credits (Pobreflix & Creator, no AniChan)
                Surface(
                    color = ObsidianCard,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, ObsidianStroke),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("👑 Criado por Miguel Rafael Montana", color = AmberGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/miguel_rafael_montana_/"))
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                border = BorderStroke(1.dp, CrimsonPrimary.copy(alpha = 0.6f))
                            ) {
                                Text("Instagram", color = TextPrimary, fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.pobreflixtv.delivery/"))
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    try { context.startActivity(intent) } catch (_: Exception) {}
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                border = BorderStroke(1.dp, ObsidianStroke)
                            ) {
                                Text("Pobreflix TV", color = TextPrimary, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Fechar", color = CrimsonPrimary, fontWeight = FontWeight.Bold)
            }
        }
    )
}
