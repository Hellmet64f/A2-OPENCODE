package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.AppState
import com.example.model.Episode
import com.example.model.WatchHistoryItem
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SeriesDetailsScreen(
    state: AppState.SeriesDetails,
    historyItem: WatchHistoryItem?,
    onEpisodeClick: (Episode) -> Unit,
    onRestartSeries: () -> Unit,
    onContinueSeries: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSeasonSelect: (Int) -> Unit,
    onAudioSelect: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val seasons = if (state.availableSeasons.isNotEmpty()) {
        state.availableSeasons.sorted()
    } else {
        val extracted = state.episodes.map { it.season }.distinct().sorted()
        if (extracted.isNotEmpty()) extracted else listOf(1)
    }

    val currentSeason = state.selectedSeason
    var isGridView by remember { mutableStateOf(false) }
    val seasonEpisodes = state.episodes.filter { it.season == currentSeason }
    val episodesListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val isFavorite = historyItem?.favorite == true
    val watchedKeys = historyItem?.watchedEpisodes.orEmpty().toSet()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        AsyncImage(
            model = state.series.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.22f)
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            ObsidianBg.copy(alpha = 0.5f),
                            ObsidianBg.copy(alpha = 0.94f),
                            ObsidianBg
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .background(ObsidianCard, CircleShape)
                        .testTag("details_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = if (state.series.isAnime) AmberGold else CrimsonPrimary,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (state.series.isAnime) "ANIME COMPLETO" else "SÉRIE COMPLETA",
                                color = if (state.series.isAnime) Color.Black else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                        if (watchedKeys.isNotEmpty()) {
                            Surface(
                                color = EmeraldAdBlock.copy(alpha = 0.18f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, EmeraldAdBlock.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "${watchedKeys.size} episódios vistos",
                                    color = EmeraldAdBlock,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.series.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(48.dp)
                        .background(ObsidianCard, CircleShape)
                        .testTag("details_favorite_button")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = if (isFavorite) "Remover favorito" else "Favoritar série",
                        tint = if (isFavorite) AmberGold else TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary Playback CTAs: Continuar + Reiniciar Série
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (historyItem != null) {
                    Button(
                        onClick = onContinueSeries,
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .heightIn(min = 48.dp)
                            .testTag("details_continue_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Continuar T${historyItem.season} E${historyItem.episodeNumber}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    OutlinedButton(
                        onClick = onRestartSeries,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, AmberGold.copy(alpha = 0.6f)),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("details_restart_button")
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reiniciar (T1 E1)", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (seasonEpisodes.isNotEmpty()) {
                    Button(
                        onClick = { onEpisodeClick(seasonEpisodes.first()) },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("details_watch_first_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Assistir Temporada $currentSeason • Ep ${seasonEpisodes.first().number}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Audio Toggle (Dublado vs Legendado) + View Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Dublado", "Legendado").forEach { audioOption ->
                        val isSelected = state.selectedAudio == audioOption
                        Surface(
                            onClick = { onAudioSelect(audioOption) },
                            color = if (isSelected) CrimsonPrimary else ObsidianCard,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (isSelected) Color.White.copy(alpha = 0.5f) else ObsidianStroke),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = if (audioOption == "Dublado") "🎙️ Dublado" else "💬 Legendado",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(
                        onClick = { isGridView = true },
                        modifier = Modifier
                            .size(48.dp)
                            .background(if (isGridView) CrimsonPrimary else ObsidianCard, RoundedCornerShape(10.dp))
                    ) {
                        Icon(Icons.Default.GridView, contentDescription = "Grade de Episódios", tint = Color.White)
                    }
                    IconButton(
                        onClick = { isGridView = false },
                        modifier = Modifier
                            .size(48.dp)
                            .background(if (!isGridView) CrimsonPrimary else ObsidianCard, RoundedCornerShape(10.dp))
                    ) {
                        Icon(Icons.Default.ViewList, contentDescription = "Cartões de Episódios", tint = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Season Selector
            if (seasons.size > 1) {
                Text(
                    text = "Mudar Temporada (${seasons.size} disponíveis)",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(end = 16.dp)
                ) {
                    items(seasons) { season ->
                        val isSelected = season == currentSeason
                        var isFocused by remember { mutableStateOf(false) }

                        Surface(
                            onClick = { onSeasonSelect(season) },
                            color = if (isSelected) CrimsonPrimary else if (isFocused) ObsidianCardElevated else ObsidianCard,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.5.dp, if (isFocused) AmberGold else ObsidianStroke),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .onFocusChanged { isFocused = it.isFocused }
                                .testTag("season_chip_$season")
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = "Temporada $season",
                                    color = Color.White,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Episodes Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Episódios • Temporada $currentSeason",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                    if (seasonEpisodes.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(color = ObsidianCard, shape = CircleShape) {
                            Text(
                                text = "${seasonEpisodes.size} eps",
                                color = TextSecondary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                if (!isGridView && seasonEpisodes.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val target = (episodesListState.firstVisibleItemIndex - 2).coerceAtLeast(0)
                                    episodesListState.animateScrollToItem(target)
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(ObsidianCard, CircleShape)
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Episódios anteriores", tint = Color.White)
                        }
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val target = (episodesListState.firstVisibleItemIndex + 2).coerceAtMost(seasonEpisodes.size - 1)
                                    episodesListState.animateScrollToItem(target)
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(CrimsonPrimary, CircleShape)
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Próximos episódios", tint = Color.White)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            when {
                state.isLoading || state.isLoadingSeason -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = CrimsonPrimary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Carregando episódios...", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }

                seasonEpisodes.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Nenhum episódio encontrado nesta temporada.", color = TextSecondary, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { onSeasonSelect(currentSeason) },
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Recarregar Episódios")
                            }
                        }
                    }
                }

                isGridView -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 84.dp),
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 18.dp)
                    ) {
                        itemsIndexed(seasonEpisodes) { _, ep ->
                            val isEpWatched = watchedKeys.contains("${ep.season}x${ep.number}")
                            val isCurrentStopped = historyItem?.season == ep.season && historyItem.episodeNumber == ep.number
                            var isFocused by remember { mutableStateOf(false) }

                            Surface(
                                onClick = { onEpisodeClick(ep) },
                                color = when {
                                    isCurrentStopped -> CrimsonPrimary
                                    isFocused -> ObsidianCardElevated
                                    else -> ObsidianCard
                                },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    width = if (isFocused) 2.5.dp else 1.dp,
                                    color = when {
                                        isFocused -> AmberGold
                                        isEpWatched -> EmeraldAdBlock.copy(alpha = 0.6f)
                                        else -> ObsidianStroke
                                    }
                                ),
                                modifier = Modifier
                                    .heightIn(min = 64.dp)
                                    .onFocusChanged { isFocused = it.isFocused }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text("EP ${ep.number}", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Black)
                                    if (isEpWatched) {
                                        Text("Visto ✓", color = if (isCurrentStopped) Color.White else EmeraldAdBlock, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                else -> {
                    LazyRow(
                        state = episodesListState,
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 12.dp, end = 24.dp)
                    ) {
                        itemsIndexed(seasonEpisodes) { _, ep ->
                            val isEpWatched = watchedKeys.contains("${ep.season}x${ep.number}")
                            val isCurrentStopped = historyItem?.season == ep.season && historyItem.episodeNumber == ep.number
                            EpisodeCard(
                                episode = ep,
                                coverUrl = state.series.coverUrl,
                                isWatched = isEpWatched,
                                isCurrent = isCurrentStopped,
                                onClick = { onEpisodeClick(ep) }
                            )
                        }
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
    isWatched: Boolean = false,
    isCurrent: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = Modifier
            .width(250.dp)
            .fillMaxHeight()
            .border(
                width = if (isFocused) 3.dp else 1.dp,
                color = if (isFocused) AmberGold else if (isCurrent) CrimsonPrimary else ObsidianStroke,
                shape = RoundedCornerShape(14.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isFocused) 8.dp else 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(ObsidianCardElevated)
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
                        .background(Color.Black.copy(alpha = 0.35f))
                )
                Surface(
                    color = CrimsonPrimary.copy(alpha = 0.9f),
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Assistir",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(10.dp)
                            .size(26.dp)
                    )
                }

                Surface(
                    color = CrimsonPrimary,
                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "T${episode.season} • EP ${episode.number}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                if (isWatched || isCurrent) {
                    Surface(
                        color = if (isCurrent) AmberGold else EmeraldAdBlock,
                        shape = RoundedCornerShape(bottomStart = 8.dp),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text(
                            text = if (isCurrent) "PAROU AQUI" else "VISTO ✓",
                            color = Color.Black,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .padding(12.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${episode.number}. ${episode.title}",
                    color = TextPrimary,
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
                        text = episode.audio,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                    Surface(
                        color = CrimsonPrimary.copy(alpha = 0.18f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (isCurrent) "CONTINUAR" else "ASSISTIR",
                            color = CrimsonPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }
    }
}
