package com.example.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppState
import com.example.model.Movie
import kotlinx.coroutines.delay

@Composable
fun SearchScreen(
    state: AppState.HomeLoaded,
    onMovieClick: (Movie) -> Unit,
    onSearch: (String, String) -> Unit, // query, source ("Filmes" or "Animes")
    onClose: () -> Unit
) {
    var query by remember { mutableStateOf(state.searchQuery) }
    var searchSource by remember { mutableStateOf(if (state.currentTab == "Animes") "Animes" else "Filmes") }
    var filterType by remember { mutableStateOf("Todos") } // "Todos", "Filmes", "Séries"
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    BackHandler(enabled = true) {
        onClose()
    }

    // Auto-search debounce after typing 2 or more characters
    LaunchedEffect(query, searchSource) {
        if (query.trim().length >= 2 && query.trim() != state.searchQuery) {
            delay(500)
            onSearch(query.trim(), searchSource)
        }
    }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    val results = state.searchResults
    val filteredResults = when (filterType) {
        "Filmes" -> results.filter { !it.isSeries }
        "Séries" -> results.filter { it.isSeries }
        else -> results
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F12))
            .statusBarsPadding()
    ) {
        // Search Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClose,
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

            Spacer(modifier = Modifier.width(12.dp))

            // Search Text Field
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        if (searchSource == "Animes") "Buscar animes no AniChan (ex: Naruto)..." else "Buscar filmes e séries no Pobreflix...",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        keyboardController?.hide()
                        if (query.isNotBlank()) onSearch(query.trim(), searchSource)
                    }
                ),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = if (state.isLoadingSearch) {
                            if (searchSource == "Animes") Color(0xFF6C5CE7) else Color(0xFFE50914)
                        } else Color.LightGray
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            onSearch("", searchSource)
                        }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Limpar",
                                tint = Color.LightGray
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color(0xFF1E2028),
                    unfocusedContainerColor = Color(0xFF1E2028),
                    cursorColor = if (searchSource == "Animes") Color(0xFF6C5CE7) else Color(0xFFE50914),
                    focusedBorderColor = if (searchSource == "Animes") Color(0xFF6C5CE7) else Color(0xFFE50914),
                    unfocusedBorderColor = Color(0xFF2A2D38)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                            keyboardController?.hide()
                            if (query.isNotBlank()) onSearch(query.trim(), searchSource)
                            true
                        } else {
                            false
                        }
                    }
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Search Submit Button
            Button(
                onClick = {
                    keyboardController?.hide()
                    if (query.isNotBlank()) onSearch(query.trim(), searchSource)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (searchSource == "Animes") Color(0xFF6C5CE7) else Color(0xFFE50914)
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text("Buscar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        // Search Source & Filter Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Source: Pobreflix
            Surface(
                color = if (searchSource == "Filmes") Color(0xFFE50914) else Color(0xFF1E2028),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .clickable {
                        searchSource = "Filmes"
                        if (query.isNotBlank()) onSearch(query.trim(), "Filmes")
                    }
                    .focusable()
            ) {
                Text(
                    text = "🎬 Pobreflix",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = if (searchSource == "Filmes") FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            // Source: AniChan
            Surface(
                color = if (searchSource == "Animes") Color(0xFF6C5CE7) else Color(0xFF1E2028),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .clickable {
                        searchSource = "Animes"
                        if (query.isNotBlank()) onSearch(query.trim(), "Animes")
                    }
                    .focusable()
            ) {
                Text(
                    text = "⚡ AniChan (Animes)",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = if (searchSource == "Animes") FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }

            if (searchSource == "Filmes") {
                Spacer(modifier = Modifier.width(4.dp))
                listOf("Todos", "Filmes", "Séries").forEach { type ->
                    val isSelected = filterType == type
                    Surface(
                        color = if (isSelected) Color(0xFF333644) else Color(0xFF161820),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .clickable { filterType = type }
                            .focusable()
                    ) {
                        Text(
                            text = type,
                            color = if (isSelected) Color.White else Color.LightGray,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = Color(0xFF1E2028), thickness = 1.dp)

        // Results Content
        Box(modifier = Modifier.fillMaxSize()) {
            if (state.isLoadingSearch) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = if (searchSource == "Animes") Color(0xFF6C5CE7) else Color(0xFFE50914)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        if (searchSource == "Animes") "Buscando animes no AniChan..." else "Pesquisando catálogo Pobreflix...",
                        color = Color.LightGray,
                        fontSize = 14.sp
                    )
                }
            } else if (state.searchQuery.isNotBlank() && filteredResults.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Nenhum resultado encontrado para \"${state.searchQuery}\"",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (searchSource == "Animes") "Tente buscar pelo nome em inglês ou japonês (ex: Naruto, Jujutsu Kaisen, Solo Leveling)." else "Dica: Tente pesquisar apenas uma palavra (ex: Batman, Vingadores, Velozes).",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = {
                            val newSource = if (searchSource == "Animes") "Filmes" else "Animes"
                            searchSource = newSource
                            if (query.isNotBlank()) onSearch(query.trim(), newSource)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (searchSource == "Animes") Color(0xFFE50914) else Color(0xFF6C5CE7)
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            text = if (searchSource == "Animes") "🎬 Buscar no Pobreflix (Filmes/Séries)" else "⚡ Buscar no AniChan (Animes)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            } else if (filteredResults.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "${filteredResults.size} títulos encontrados em ${if (searchSource == "Animes") "AniChan" else "Pobreflix"}",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 8.dp)
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 130.dp),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredResults) { movie ->
                            MoviePosterCard(
                                movie = movie,
                                onClick = { onMovieClick(movie) }
                            )
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF333642),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        if (searchSource == "Animes") "Digite o nome de um anime para pesquisar" else "Digite um filme ou série para pesquisar",
                        color = Color.LightGray,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (searchSource == "Animes") "Ex: One Piece, Jujutsu Kaisen, Demon Slayer, Naruto" else "Ex: Batman, Resident Evil, Homem-Aranha, Lanternas",
                        color = Color.DarkGray,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}
