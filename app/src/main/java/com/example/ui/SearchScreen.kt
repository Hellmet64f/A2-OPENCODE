package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppState
import com.example.model.Movie
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    state: AppState.HomeLoaded,
    onMovieClick: (Movie) -> Unit,
    onSearch: (String) -> Unit,
    onRemoveRecentSearch: (String) -> Unit = {},
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf(state.searchQuery) }
    var filterType by remember { mutableStateOf("Todos") } // "Todos", "Filmes", "Séries"
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    BackHandler(enabled = true) {
        onClose()
    }

    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.length >= 2 && trimmed != state.searchQuery) {
            delay(450)
            onSearch(trimmed)
        }
    }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val historyByUrl = remember(state.watchHistory) { state.watchHistory.associateBy { it.id } }
    val results = state.searchResults
    val filteredResults = remember(results, filterType) {
        when (filterType) {
            "Filmes" -> results.filter { !it.isSeries }
            "Séries" -> results.filter { it.isSeries }
            else -> results
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Header Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(48.dp)
                    .background(ObsidianCard, CircleShape)
                    .testTag("search_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = TextPrimary
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        "Buscar filmes ou séries...",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        if (query.isNotBlank()) onSearch(query.trim())
                    }
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = if (state.isLoadingSearch) CrimsonPrimary else TextSecondary
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                query = ""
                                onSearch("")
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Limpar busca",
                                tint = TextSecondary
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = ObsidianCard,
                    unfocusedContainerColor = ObsidianCard,
                    cursorColor = CrimsonPrimary,
                    focusedBorderColor = CrimsonPrimary,
                    unfocusedBorderColor = ObsidianStroke
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .testTag("search_input")
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                            keyboardController?.hide()
                            if (query.isNotBlank()) onSearch(query.trim())
                            true
                        } else false
                    }
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    keyboardController?.hide()
                    if (query.isNotBlank()) onSearch(query.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("search_submit_button")
            ) {
                Text("Buscar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // Type Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf("Todos", "Filmes", "Séries").forEach { type ->
                val isSelected = filterType == type
                FilterChip(
                    selected = isSelected,
                    onClick = { filterType = type },
                    label = {
                        Text(
                            text = type,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = CrimsonPrimary,
                        selectedLabelColor = Color.White,
                        containerColor = ObsidianCard,
                        labelColor = TextSecondary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = ObsidianStroke
                    ),
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
        }

        // Recent Searches
        if (state.recentSearches.isNotEmpty() && query.isBlank()) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text(
                    text = "Buscas Recentes",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.recentSearches.forEach { recent ->
                        Surface(
                            color = ObsidianCard,
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, ObsidianStroke),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable {
                                        query = recent
                                        onSearch(recent)
                                    }
                                    .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
                            ) {
                                Icon(Icons.Default.History, contentDescription = null, tint = AmberGold, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(recent, color = TextPrimary, fontSize = 12.sp)
                                IconButton(
                                    onClick = { onRemoveRecentSearch(recent) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Remover busca", tint = TextSecondary, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = ObsidianStroke, thickness = 1.dp)

        // Results
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoadingSearch -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = CrimsonPrimary)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text("Pesquisando filmes e séries...", color = TextSecondary, fontSize = 14.sp)
                    }
                }

                state.searchQuery.isNotBlank() && filteredResults.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Nenhum resultado para \"${state.searchQuery}\"",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Dica: tente uma palavra simples, sem acentos ou só o nome principal.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { onSearch(state.searchQuery) },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonPrimary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Text("Tentar Novamente", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                filteredResults.isNotEmpty() -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "${filteredResults.size} títulos encontrados",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 4.dp)
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 120.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredResults) { movie ->
                                val hist = historyByUrl[movie.url]
                                MoviePosterCard(
                                    movie = movie,
                                    isFavorite = hist?.favorite == true,
                                    isWatched = hist != null && (hist.completed || hist.watchedEpisodes.isNotEmpty()),
                                    onClick = { onMovieClick(movie) }
                                )
                            }
                        }
                    }
                }

                else -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = ObsidianStroke,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Pesquise filmes e séries completos",
                            color = TextPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Sugestões rápidas:",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("Batman", "Vingadores", "Breaking Bad", "Stranger Things", "Homem-Aranha").forEach { suggestion ->
                                AssistChip(
                                    onClick = {
                                        query = suggestion
                                        onSearch(suggestion)
                                    },
                                    label = { Text(suggestion, color = TextPrimary, fontSize = 12.sp) },
                                    colors = AssistChipDefaults.assistChipColors(containerColor = ObsidianCard),
                                    border = BorderStroke(1.dp, ObsidianStroke),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
