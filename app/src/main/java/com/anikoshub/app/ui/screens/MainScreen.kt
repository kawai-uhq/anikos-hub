package com.anikoshub.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anikoshub.app.data.AppPreferences
import com.anikoshub.app.data.Media
import com.anikoshub.app.data.TmdbClient
import com.anikoshub.app.data.UiState
import com.anikoshub.app.ui.components.*
import com.anikoshub.app.ui.theme.Bg
import com.anikoshub.app.ui.theme.Purple
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    client: TmdbClient,
    prefs: AppPreferences,
    tab: Int,
    onTabChange: (Int) -> Unit,
    onOpen: (Media) -> Unit
) {
    var trending by remember { mutableStateOf<UiState<List<Media>>>(UiState.Loading) }
    var popularMovies by remember { mutableStateOf<UiState<List<Media>>>(UiState.Idle) }
    var popularTv by remember { mutableStateOf<UiState<List<Media>>>(UiState.Idle) }
    var favorites by remember { mutableStateOf(prefs.getFavorites()) }
    var searchQuery by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    fun loadTrending() {
        scope.launch {
            trending = UiState.Loading
            trending = runCatching { client.trending() }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "Failed to load") }
                )
        }
    }

    fun loadPopularMovies() {
        scope.launch {
            popularMovies = UiState.Loading
            popularMovies = runCatching { client.popular("movie") }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "Failed to load") }
                )
        }
    }

    fun loadPopularTv() {
        scope.launch {
            popularTv = UiState.Loading
            popularTv = runCatching { client.popular("tv") }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error(it.message ?: "Failed to load") }
                )
        }
    }

    LaunchedEffect(Unit) { loadTrending() }

    LaunchedEffect(tab) {
        when (tab) {
            0 -> if (popularMovies is UiState.Idle) loadPopularMovies()
            2 -> if (popularTv is UiState.Idle) loadPopularTv()
            3 -> favorites = prefs.getFavorites()
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = Bg) {
                listOf(
                    "⌂" to "Home",
                    "⌕" to "Search",
                    "▣" to "Shows",
                    "♥" to "Favorites",
                    "⚙" to "Settings"
                ).forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { onTabChange(index) },
                        icon = { Text(item.first, fontSize = 20.sp) },
                        label = { Text(item.second, fontSize = 11.sp) }
                    )
                }
            }
        },
        containerColor = Bg
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(12.dp))
            Text(
                "ANIKO'S HUB",
                color = Purple,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))

            when (tab) {
                1 -> SearchTab(client, searchQuery, { searchQuery = it }, onOpen)
                2 -> CatalogTab(
                    state = popularTv,
                    title = "Popular TV",
                    onRetry = ::loadPopularTv,
                    onOpen = onOpen
                )
                3 -> FavoritesTab(favorites, onOpen)
                4 -> SettingsScreen(prefs)
                else -> HomeTab(
                    trending = trending,
                    movies = popularMovies,
                    onRetryTrending = ::loadTrending,
                    onRetryMovies = ::loadPopularMovies,
                    onOpen = onOpen
                )
            }
        }
    }
}

@Composable
private fun HomeTab(
    trending: UiState<List<Media>>,
    movies: UiState<List<Media>>,
    onRetryTrending: () -> Unit,
    onRetryMovies: () -> Unit,
    onOpen: (Media) -> Unit
) {
    Column(Modifier.verticalScroll(rememberScrollState())) {
        SectionBlock("Trending", trending, onRetryTrending, onOpen)
        Spacer(Modifier.height(20.dp))
        SectionBlock("Popular Movies", movies, onRetryMovies, onOpen)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionBlock(
    title: String,
    state: UiState<List<Media>>,
    onRetry: () -> Unit,
    onOpen: (Media) -> Unit
) {
    when (state) {
        is UiState.Loading, is UiState.Idle -> LoadingBox()
        is UiState.Error -> ErrorBox(state.message, onRetry)
        is UiState.Success -> MediaSection(title, state.data, onOpen)
    }
}

@Composable
private fun CatalogTab(
    state: UiState<List<Media>>,
    title: String,
    onRetry: () -> Unit,
    onOpen: (Media) -> Unit
) {
    when (state) {
        is UiState.Loading, is UiState.Idle -> LoadingBox()
        is UiState.Error -> ErrorBox(state.message, onRetry)
        is UiState.Success -> MediaSection(title, state.data, onOpen)
    }
}

@Composable
private fun SearchTab(
    client: TmdbClient,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpen: (Media) -> Unit
) {
    var results by remember { mutableStateOf<UiState<List<Media>>>(UiState.Idle) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(query) {
        if (query.length < 2) {
            results = UiState.Idle
            return@LaunchedEffect
        }
        results = UiState.Loading
        results = runCatching { client.search(query) }
            .fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error(it.message ?: "Search failed") }
            )
    }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search movies & shows") },
        singleLine = true
    )
    Spacer(Modifier.height(12.dp))

    when (val r = results) {
        is UiState.Idle -> EmptyBox("Type at least 2 characters")
        is UiState.Loading -> LoadingBox()
        is UiState.Error -> ErrorBox(r.message)
        is UiState.Success -> MediaGrid(r.data, onOpen)
    }
}

@Composable
private fun FavoritesTab(
    favorites: List<Media>,
    onOpen: (Media) -> Unit
) {
    if (favorites.isEmpty()) {
        EmptyBox("No favorites yet. Heart items from the detail screen.")
    } else {
        MediaGrid(favorites, onOpen)
    }
}
