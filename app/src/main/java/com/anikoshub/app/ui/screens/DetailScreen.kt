package com.anikoshub.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anikoshub.app.data.*
import com.anikoshub.app.ui.components.EpisodeCard
import com.anikoshub.app.ui.components.ErrorBox
import com.anikoshub.app.ui.components.LoadingBox
import com.anikoshub.app.ui.theme.Bg
import com.anikoshub.app.ui.theme.TextMuted
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    media: Media,
    client: TmdbClient,
    prefs: AppPreferences,
    onBack: () -> Unit,
    onPlay: (Media, String, Pair<Int, Int>?) -> Unit
) {
    var providerId by remember { mutableStateOf(prefs.preferredProviderId) }
    var isFavorite by remember { mutableStateOf(prefs.isFavorite(media)) }
    var seasons by remember { mutableStateOf<List<TvSeason>>(emptyList()) }
    var episodes by remember { mutableStateOf<List<Episode>>(emptyList()) }
    var selectedSeason by remember { mutableIntStateOf(1) }
    var loadingSeasons by remember { mutableStateOf(false) }
    var loadingEpisodes by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(media.id, media.type) {
        if (media.type != "tv") return@LaunchedEffect
        loadingSeasons = true
        error = null
        runCatching { client.tvSeasons(media.id) }
            .onSuccess {
                seasons = it
                if (it.isNotEmpty()) selectedSeason = it.first().number
            }
            .onFailure { error = it.message }
        loadingSeasons = false
    }

    LaunchedEffect(media.id, selectedSeason) {
        if (media.type != "tv" || selectedSeason <= 0) return@LaunchedEffect
        loadingEpisodes = true
        runCatching { client.episodes(media.id, selectedSeason) }
            .onSuccess { episodes = it }
            .onFailure { error = it.message }
        loadingEpisodes = false
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
    ) {
        Box {
            val backdrop = media.backdrop ?: media.poster
            if (backdrop != null) {
                AsyncImage(
                    model = TmdbClient.IMAGE_BASE + backdrop,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .background(Color(0xFF1A1A20))
                )
            }
            Text(
                "‹",
                fontSize = 42.sp,
                color = Color.White,
                modifier = Modifier
                    .padding(12.dp)
                    .clickable(onClick = onBack)
            )
        }

        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    media.title,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = {
                        isFavorite = prefs.toggleFavorite(media)
                    }
                ) {
                    Text(
                        if (isFavorite) "♥" else "♡",
                        fontSize = 24.sp,
                        color = if (isFavorite) Color(0xFFFF6B8A) else Color.White
                    )
                }
            }

            Text(
                "${media.year} • ${media.type.uppercase()} • ★ ${"%.1f".format(media.rating)}",
                color = TextMuted
            )

            Spacer(Modifier.height(12.dp))

            Text(
                media.overview.ifBlank { "No description available." },
                color = Color(0xFFC8C8D0),
                fontSize = 14.sp
            )

            Spacer(Modifier.height(20.dp))

            Text("SERVER", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Providers.all.forEach { p ->
                    FilterChip(
                        selected = providerId == p.id,
                        onClick = {
                            providerId = p.id
                            prefs.preferredProviderId = p.id
                        },
                        label = { Text(p.displayName.uppercase()) }
                    )
                    Spacer(Modifier.width(8.dp))
                }
            }

            if (media.type == "tv") {
                Spacer(Modifier.height(20.dp))
                Text("SEASONS", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                when {
                    loadingSeasons -> LoadingBox()
                    seasons.isEmpty() -> Text("No season information found.", color = TextMuted)
                    else -> {
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            seasons.forEach { season ->
                                FilterChip(
                                    selected = selectedSeason == season.number,
                                    onClick = { selectedSeason = season.number },
                                    label = { Text("S${season.number}") }
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        val current = seasons.firstOrNull { it.number == selectedSeason }
                        Text(
                            "${current?.name ?: "Season $selectedSeason"} • ${current?.episodeCount ?: episodes.size} episodes",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(10.dp))

                        when {
                            loadingEpisodes -> LoadingBox()
                            episodes.isEmpty() -> Text("No episodes found.", color = TextMuted)
                            else -> {
                                episodes.forEach { ep ->
                                    EpisodeCard(ep) {
                                        onPlay(media, providerId, selectedSeason to ep.number)
                                    }
                                    Spacer(Modifier.height(10.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = { onPlay(media, providerId, null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("▶  WATCH NOW")
                }
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                ErrorBox(it) {
                    error = null
                    // re-trigger by resetting season load
                    scope.launch {
                        if (media.type == "tv") {
                            loadingSeasons = true
                            runCatching { client.tvSeasons(media.id) }
                                .onSuccess {
                                    seasons = it
                                    if (it.isNotEmpty()) selectedSeason = it.first().number
                                }
                                .onFailure { e -> error = e.message }
                            loadingSeasons = false
                        }
                    }
                }
            }
        }
    }
}
