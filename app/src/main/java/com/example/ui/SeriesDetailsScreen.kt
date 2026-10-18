package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.AppState
import com.example.model.Episode
import kotlinx.coroutines.launch

@Composable
fun SeriesDetailsScreen(
    state: AppState.SeriesDetails,
    onEpisodeClick: (Episode) -> Unit,
    onSeasonSelect: (Int) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val isAnime = state.series.isAnime
    val accentColor = if (isAnime) Color(0xFF6C5CE7) else Color(0xFFE50914)

    val seasons = if (state.availableSeasons.isNotEmpty()) {
        state.availableSeasons.sorted()
    } else {
        val extracted = state.episodes.map { it.season }.distinct().sorted()
        if (extracted.isNotEmpty()) extracted else listOf(1)
    }

    val currentSeason = state.selectedSeason
    var selectedAudio by remember { mutableStateOf(state.selectedAudio) }
    var isGridView by remember { mutableStateOf(isAnime) }
    val seasonEpisodes = state.episodes.filter { it.season == currentSeason }
    val episodesListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F12))
    ) {
        // Hero Background Artwork
        AsyncImage(
            model = state.series.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.25f)
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F0F12).copy(alpha = 0.5f),
                            Color(0xFF0F0F12).copy(alpha = 0.95f),
                            Color(0xFF0F0F12)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            // Header: Back button + Title & Badges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color(0xFF1E2028), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Voltar",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = accentColor,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (isAnime) "ANIME COMPLETO • ANICHAN" else "SÉRIE COMPLETA",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (isAnime) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFF22242B),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "SUB & DUB",
                                    color = Color(0xFFA29BFE),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = state.series.title,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Quick Play First Episode Button
                if (seasonEpisodes.isNotEmpty()) {
                    Button(
                        onClick = { onEpisodeClick(seasonEpisodes.first()) },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Assistir", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Audio & View Mode Selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Audio Toggle (Dublado vs Legendado)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Dublado", "Legendado").forEach { audioOption ->
                        val isSelected = selectedAudio == audioOption
                        Surface(
                            color = if (isSelected) accentColor else Color(0xFF1E2028),
                            shape = RoundedCornerShape(10.dp),
                            border = if (isSelected) BorderStroke(1.5.dp, Color.White.copy(alpha = 0.5f)) else null,
                            modifier = Modifier
                                .clickable { selectedAudio = audioOption }
                                .focusable()
                        ) {
                            Text(
                                text = if (audioOption == "Dublado") "🎙️ Dublado" else "🇯🇵 Legendado",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Grid vs Cards Toggle
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = if (isGridView) accentColor else Color(0xFF22242B),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .clickable { isGridView = true }
                            .focusable()
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Grade de Episódios",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(18.dp)
                        )
                    }
                    Surface(
                        color = if (!isGridView) accentColor else Color(0xFF22242B),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .clickable { isGridView = false }
                            .focusable()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewList,
                            contentDescription = "Lista de Episódios",
                            tint = Color.White,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 1. SEASONS SELECTOR (if multiple seasons exist)
            if (seasons.size > 1) {
                Text(
                    text = "Temporadas (${seasons.size})",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 24.dp)
                ) {
                    items(seasons) { season ->
                        val isSelected = season == currentSeason
                        var isFocused by remember { mutableStateOf(false) }

                        Surface(
                            color = if (isSelected) accentColor else if (isFocused) Color(0xFF333642) else Color(0xFF1E2028),
                            shape = RoundedCornerShape(10.dp),
                            border = if (isFocused) BorderStroke(2.dp, Color.White) else null,
                            modifier = Modifier
                                .onFocusChanged { isFocused = it.isFocused }
                                .focusable()
                                .clickable { onSeasonSelect(season) }
                        ) {
                            Text(
                                text = "Temporada $season",
                                color = Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 2. EPISODES HEADER with SIDE BUTTONS
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isAnime) "Episódios do Anime" else "Episódios da Temporada $currentSeason",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (seasonEpisodes.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = Color(0xFF22242B),
                            shape = CircleShape
                        ) {
                            Text(
                                text = "${seasonEpisodes.size} eps",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (!isGridView && seasonEpisodes.size > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = accentColor.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .clickable {
                                    scope.launch {
                                        val target = (episodesListState.firstVisibleItemIndex - 2).coerceAtLeast(0)
                                        episodesListState.animateScrollToItem(target)
                                    }
                                }
                                .focusable()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "Passar Ep Anterior",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("Ant", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            color = accentColor,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .clickable {
                                    scope.launch {
                                        val target = (episodesListState.firstVisibleItemIndex + 2).coerceAtMost(seasonEpisodes.size - 1)
                                        episodesListState.animateScrollToItem(target)
                                    }
                                }
                                .focusable()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Próx", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Passar Próximo Ep",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Episodes Content: Grid or Card Row
            if (state.isLoading || state.isLoadingSeason) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = accentColor)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (isAnime) "Carregando episódios do AniChan..." else "Obtendo episódios...",
                            color = Color.LightGray,
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (seasonEpisodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Nenhum episódio listado no momento.",
                            color = Color.LightGray,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onSeasonSelect(currentSeason) },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                        ) {
                            Text("Recarregar Episódios", color = Color.White)
                        }
                    }
                }
            } else if (isGridView) {
                // ANICHAN ANIME EPISODE GRID VIEW: Compact, rapid selection
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 70.dp),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    itemsIndexed(seasonEpisodes) { index, ep ->
                        var isFocused by remember { mutableStateOf(false) }
                        Surface(
                            onClick = { onEpisodeClick(ep) },
                            color = if (isFocused) accentColor else Color(0xFF1E2028),
                            shape = RoundedCornerShape(10.dp),
                            border = if (isFocused) BorderStroke(2.5.dp, Color(0xFFFFD166)) else BorderStroke(1.dp, Color(0xFF2C2F3D)),
                            modifier = Modifier
                                .onFocusChanged { isFocused = it.isFocused }
                                .onKeyEvent { keyEvent ->
                                    if (keyEvent.type == KeyEventType.KeyUp && (
                                        keyEvent.key == Key.DirectionCenter ||
                                        keyEvent.key == Key.Enter ||
                                        keyEvent.key == Key.NumPadEnter
                                    )) {
                                        onEpisodeClick(ep)
                                        true
                                    } else false
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "EP",
                                    color = Color.Gray,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${ep.number}",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }
                }
            } else {
                // Detailed Horizontal Cards
                LazyRow(
                    state = episodesListState,
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 20.dp, end = 40.dp)
                ) {
                    itemsIndexed(seasonEpisodes) { _, ep ->
                        EpisodeCard(
                            episode = ep,
                            coverUrl = state.series.coverUrl,
                            accentColor = accentColor,
                            onClick = { onEpisodeClick(ep) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeCard(
    episode: Episode,
    coverUrl: String,
    accentColor: Color = Color(0xFFE50914),
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(260.dp)
            .fillMaxHeight()
            .border(
                width = if (isFocused) 3.5.dp else 0.dp,
                color = if (isFocused) Color(0xFFFFD166) else Color.Transparent,
                shape = RoundedCornerShape(14.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1D24)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 8.dp else 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Color.DarkGray)
            ) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                )

                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Assistir",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(28.dp)
                    )
                }

                Surface(
                    color = accentColor,
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "EP ${episode.number}",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${episode.number}. ${episode.title}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (episode.season > 1) "Temp ${episode.season} • Ep ${episode.number}" else "Episódio ${episode.number}",
                        color = Color.Gray,
                        fontSize = 11.sp
                    )

                    Surface(
                        color = accentColor.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "ASSISTIR",
                            color = accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
