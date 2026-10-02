package com.anikoshub.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

private const val IMAGE = "https://image.tmdb.org/t/p/w500"
private const val API = "https://api.themoviedb.org/3"

private val Bg = Color(0xFF08080A)
private val Card = Color(0xFF15151A)
private val Purple = Color(0xFF9B5CFF)

class MainActivity : ComponentActivity() {

    private val prefs by lazy {
        getSharedPreferences("anikoshub", MODE_PRIVATE)
    }

    private val client by lazy {
        TmdbClient {
            prefs.getString("tmdb_token", "") ?: ""
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AnikosHubApp(client, prefs)
        }
    }
}

data class Media(
    val id: Int,
    val title: String,
    val poster: String?,
    val backdrop: String?,
    val overview: String,
    val type: String,
    val rating: Double,
    val year: String
)

data class TvSeason(
    val number: Int,
    val name: String,
    val episodeCount: Int
)

data class Episode(
    val number: Int,
    val name: String,
    val overview: String,
    val still: String?,
    val rating: Double,
    val runtime: Int
)


private suspend fun checkForUpdate(
    currentVersion: String
): String? = withContext(Dispatchers.IO) {
    try {
        val connection =
            URL(
                "https://api.github.com/repos/" +
                    "kawai-uhq/anikos-hub/releases/latest"
            ).openConnection() as HttpURLConnection

        connection.requestMethod = "GET"
        connection.setRequestProperty(
            "Accept",
            "application/vnd.github+json"
        )
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        if (connection.responseCode !in 200..299) {
            connection.disconnect()
            return@withContext null
        }

        val body =
            connection.inputStream
                .bufferedReader()
                .use { it.readText() }

        connection.disconnect()

        val json = JSONObject(body)

        val latest =
            json.optString("tag_name")
                .removePrefix("v")
                .trim()

        if (
            latest.isBlank() ||
            !isNewerVersion(latest, currentVersion)
        ) {
            return@withContext null
        }

        val assets =
            json.optJSONArray("assets")
                ?: return@withContext null

        for (i in 0 until assets.length()) {
            val asset =
                assets.optJSONObject(i)
                    ?: continue

            if (
                asset.optString("name")
                    .equals(
                        "Anikos-Hub.apk",
                        ignoreCase = true
                    )
            ) {
                return@withContext asset
                    .optString("browser_download_url")
                    .ifBlank { null }
            }
        }

        null
    } catch (_: Exception) {
        null
    }
}

private fun isNewerVersion(
    latest: String,
    current: String
): Boolean {

    fun parts(value: String): List<Int> {
        val values =
            value
                .removePrefix("v")
                .split(".")
                .map {
                    it.takeWhile { ch ->
                        ch.isDigit()
                    }.toIntOrNull() ?: 0
                }

        return listOf(
            values.getOrElse(0) { 0 },
            values.getOrElse(1) { 0 },
            values.getOrElse(2) { 0 }
        )
    }

    val a = parts(latest)
    val b = parts(current)

    for (i in 0..2) {
        if (a[i] != b[i]) {
            return a[i] > b[i]
        }
    }

    return false
}

class TmdbClient(
    private val tokenProvider: () -> String
) {

    suspend fun get(path: String): JSONObject =
        withContext(Dispatchers.IO) {

            val token = tokenProvider()

            if (token.isBlank()) {
                error("Add your TMDB API Read Access Token in Settings.")
            }

            val conn =
                URL(API + path).openConnection() as HttpURLConnection

            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("accept", "application/json")
            conn.connectTimeout = 15000
            conn.readTimeout = 20000

            val responseCode = conn.responseCode

            val stream =
                if (responseCode in 200..299) {
                    conn.inputStream
                } else {
                    conn.errorStream
                }

            val body = stream.bufferedReader().use {
                it.readText()
            }

            conn.disconnect()

            if (responseCode !in 200..299) {
                error("TMDB $responseCode: $body")
            }

            JSONObject(body)
        }

    suspend fun popular(type: String): List<Media> =
        get("/$type/popular?language=en-US&page=1").toMedia(type)

    suspend fun trending(): List<Media> =
        get("/trending/all/day?language=en-US").toMedia("all")

    suspend fun search(q: String): List<Media> =
        get(
            "/search/multi?query=${
                URLEncoder.encode(q, "UTF-8")
            }&include_adult=false&language=en-US&page=1"
        ).toMedia("all")

    suspend fun tvSeasons(id: Int): List<TvSeason> {
        val json = get("/tv/$id?language=en-US")
        val seasons = json.optJSONArray("seasons")
            ?: return emptyList()

        return (0 until seasons.length()).mapNotNull { index ->
            val season = seasons.optJSONObject(index)
                ?: return@mapNotNull null

            val number = season.optInt("season_number", -1)

            if (number <= 0) {
                return@mapNotNull null
            }

            TvSeason(
                number = number,
                name = season.optString(
                    "name",
                    "Season $number"
                ),
                episodeCount = season.optInt(
                    "episode_count",
                    0
                )
            )
        }
    }

    suspend fun episodes(
        id: Int,
        season: Int
    ): List<Episode> {

        val json =
            get("/tv/$id/season/$season?language=en-US")

        val array =
            json.optJSONArray("episodes")
                ?: return emptyList()

        return (0 until array.length()).mapNotNull { index ->
            val e = array.optJSONObject(index)
                ?: return@mapNotNull null

            Episode(
                number = e.optInt(
                    "episode_number",
                    index + 1
                ),
                name = e.optString(
                    "name",
                    "Episode ${index + 1}"
                ),
                overview = e.optString(
                    "overview",
                    ""
                ),
                still = e.optString(
                    "still_path"
                ).ifBlank { null },
                rating = e.optDouble(
                    "vote_average",
                    0.0
                ),
                runtime = e.optInt(
                    "runtime",
                    0
                )
            )
        }
    }

    private fun JSONObject.toMedia(
        fallback: String
    ): List<Media> {

        val array = optJSONArray("results")

        if (array == null) {
            return listOfNotNull(
                parseMedia(this, fallback)
            )
        }

        return (0 until array.length()).mapNotNull {
            parseMedia(
                array.optJSONObject(it),
                fallback
            )
        }
    }

    private fun parseMedia(
        o: JSONObject?,
        fallback: String
    ): Media? {

        if (o == null) return null

        val type =
            if (fallback == "all") {
                o.optString("media_type")
                    .ifBlank { fallback }
            } else {
                fallback
            }

        if (type != "movie" && type != "tv") {
            return null
        }

        val title =
            o.optString(
                if (type == "movie") "title" else "name"
            ).ifBlank {
                "Untitled"
            }

        val date =
            o.optString(
                if (type == "movie") {
                    "release_date"
                } else {
                    "first_air_date"
                }
            )

        return Media(
            id = o.optInt("id"),
            title = title,
            poster = o.optString(
                "poster_path"
            ).ifBlank { null },
            backdrop = o.optString(
                "backdrop_path"
            ).ifBlank { null },
            overview = o.optString("overview"),
            type = type,
            rating = o.optDouble(
                "vote_average",
                0.0
            ),
            year = date.take(4)
        )
    }
}

@Composable
private fun AnikosHubApp(
    client: TmdbClient,
    prefs: android.content.SharedPreferences
) {

    val context = LocalContext.current

    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Media?>(null) }
    var player by remember {
        mutableStateOf<
            Triple<Media, String, Pair<Int, Int>?>?
        >(null)
    }

    var updateUrl by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(Unit) {
        updateUrl =
            checkForUpdate(BuildConfig.VERSION_NAME)
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Purple,
            background = Bg,
            surface = Card
        )
    ) {

        if (updateUrl != null) {
            AlertDialog(
                onDismissRequest = {
                    updateUrl = null
                },
                title = {
                    Text("Update available")
                },
                text = {
                    Text(
                        "A newer version of Aniko's Hub is available."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            try {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse(updateUrl)
                                    )
                                )
                            } catch (_: Exception) {
                                Toast.makeText(
                                    context,
                                    "Unable to open the update.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                            updateUrl = null
                        }
                    ) {
                        Text("UPDATE")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            updateUrl = null
                        }
                    ) {
                        Text("LATER")
                    }
                }
            )
        }

        when {
            player != null -> {
                PlayerScreen(
                    player = player!!,
                    onBack = { player = null }
                )
            }

            selected != null -> {
                DetailScreen(
                    media = selected!!,
                    client = client,
                    onBack = { selected = null },
                    onPlay = { media, provider, episode ->
                        player =
                            Triple(
                                media,
                                provider,
                                episode
                            )
                    }
                )
            }

            else -> {
                MainScreen(
                    client = client,
                    prefs = prefs,
                    tab = tab,
                    setTab = { tab = it },
                    open = { selected = it }
                )
            }
        }
    }
}

@Composable
private fun MainScreen(
    client: TmdbClient,
    prefs: android.content.SharedPreferences,
    tab: Int,
    setTab: (Int) -> Unit,
    open: (Media) -> Unit
) {

    var query by remember {
        mutableStateOf("")
    }

    var trending by remember {
        mutableStateOf<List<Media>>(emptyList())
    }

    var tv by remember {
        mutableStateOf<List<Media>>(emptyList())
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(Unit) {
        runCatching {
            trending = client.trending()
        }.onFailure {
            error = it.message
        }
    }

    LaunchedEffect(tab) {
        if (tab == 2 && tv.isEmpty()) {
            runCatching {
                tv = client.popular("tv")
            }.onFailure {
                error = it.message
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Bg
            ) {
                listOf(
                    "⌂" to "Home",
                    "⌕" to "Search",
                    "▣" to "Shows",
                    "⚙" to "Settings"
                ).forEachIndexed { index, item ->

                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { setTab(index) },
                        icon = {
                            Text(
                                item.first,
                                fontSize = 20.sp
                            )
                        },
                        label = {
                            Text(item.second)
                        }
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

            Spacer(Modifier.height(14.dp))

            Text(
                "ANIKO'S HUB",
                color = Purple,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(12.dp))

            when (tab) {

                1 -> {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text("Search movies & shows")
                        },
                        singleLine = true
                    )

                    Spacer(Modifier.height(12.dp))

                    SearchResults(
                        client = client,
                        query = query,
                        open = open
                    )
                }

                2 -> {
                    if (error != null) {
                        Text(
                            error!!,
                            color = Color.Red
                        )
                    }

                    MediaSection(
                        title = "Popular TV",
                        list = tv,
                        open = open
                    )
                }

                3 -> {
                    SettingsScreen(prefs)
                }

                else -> {
                    if (error != null) {
                        Text(
                            error!!,
                            color = Color.Red
                        )
                    }

                    MediaSection(
                        title = "Trending",
                        list = trending,
                        open = open
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    client: TmdbClient,
    query: String,
    open: (Media) -> Unit
) {

    var results by remember {
        mutableStateOf<List<Media>>(emptyList())
    }

    var loading by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(query) {
        if (query.length >= 2) {
            loading = true

            results =
                runCatching {
                    client.search(query)
                }.getOrDefault(emptyList())

            loading = false
        } else {
            results = emptyList()
        }
    }

    if (loading) {
        CircularProgressIndicator()
        Spacer(Modifier.height(10.dp))
    }

    MediaGrid(
        list = results,
        open = open
    )
}

@Composable
private fun MediaSection(
    title: String,
    list: List<Media>,
    open: (Media) -> Unit
) {

    Text(
        title,
        fontSize = 21.sp,
        fontWeight = FontWeight.Bold
    )

    Spacer(Modifier.height(10.dp))

    LazyRow(
        horizontalArrangement =
            Arrangement.spacedBy(10.dp)
    ) {
        items(list) {
            Poster(it, open)
        }
    }
}

@Composable
private fun MediaGrid(
    list: List<Media>,
    open: (Media) -> Unit
) {

    LazyColumn(
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        items(
            list.chunked(2)
        ) { row ->

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement =
                    Arrangement.spacedBy(10.dp)
            ) {

                row.forEach {
                    Poster(
                        it,
                        open,
                        Modifier.weight(1f)
                    )
                }

                if (row.size == 1) {
                    Spacer(
                        Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun Poster(
    media: Media,
    open: (Media) -> Unit,
    modifier: Modifier = Modifier
) {

    Column(
        modifier.clickable {
            open(media)
        }
    ) {

        val image =
            media.poster ?: media.backdrop

        if (image != null) {

            AsyncImage(
                model = IMAGE + image,
                contentDescription = media.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(245.dp),
                contentScale = ContentScale.Crop
            )
        } else {

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(245.dp)
                    .background(Color(0xFF222228)),
                contentAlignment =
                    Alignment.Center
            ) {
                Text(
                    "No image",
                    color = Color.Gray
                )
            }
        }

        Spacer(Modifier.height(5.dp))

        Text(
            media.title,
            maxLines = 1,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            "${media.year} • ★ ${
                "%.1f".format(media.rating)
            }",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun DetailScreen(
    media: Media,
    client: TmdbClient,
    onBack: () -> Unit,
    onPlay: (
        Media,
        String,
        Pair<Int, Int>?
    ) -> Unit
) {

    var provider by remember {
        mutableStateOf("vidlink")
    }

    var seasons by remember {
        mutableStateOf<List<TvSeason>>(emptyList())
    }

    var episodes by remember {
        mutableStateOf<List<Episode>>(emptyList())
    }

    var selectedSeason by remember {
        mutableIntStateOf(1)
    }

    var loadingSeasons by remember {
        mutableStateOf(false)
    }

    var loadingEpisodes by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(media.id, media.type) {

        if (media.type == "tv") {

            loadingSeasons = true

            runCatching {
                val loaded =
                    client.tvSeasons(media.id)

                seasons = loaded

                if (loaded.isNotEmpty()) {
                    selectedSeason =
                        loaded.first().number
                }
            }.onFailure {
                error = it.message
            }

            loadingSeasons = false
        }
    }

    LaunchedEffect(
        media.id,
        selectedSeason
    ) {

        if (
            media.type == "tv" &&
            selectedSeason > 0
        ) {

            loadingEpisodes = true

            runCatching {
                episodes =
                    client.episodes(
                        media.id,
                        selectedSeason
                    )
            }.onFailure {
                error = it.message
            }

            loadingEpisodes = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(
                rememberScrollState()
            )
    ) {

        Box {

            val backdrop =
                media.backdrop ?: media.poster

            if (backdrop != null) {

                AsyncImage(
                    model = IMAGE + backdrop,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentScale = ContentScale.Crop
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

        Column(
            Modifier.padding(16.dp)
        ) {

            Text(
                media.title,
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                "${media.year} • ${
                    media.type.uppercase()
                } • ★ ${
                    "%.1f".format(media.rating)
                }",
                color = Color.Gray
            )

            Spacer(Modifier.height(12.dp))

            Text(
                media.overview.ifBlank {
                    "No description available."
                },
                color = Color.LightGray
            )

            Spacer(Modifier.height(20.dp))

            Text(
                "SERVER",
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Row(
                Modifier.horizontalScroll(
                    rememberScrollState()
                )
            ) {

                listOf(
                    "vidlink",
                    "cinesrc",
                    "vidfast"
                ).forEach { name ->

                    FilterChip(
                        selected =
                            provider == name,
                        onClick = {
                            provider = name
                        },
                        label = {
                            Text(name.uppercase())
                        }
                    )

                    Spacer(Modifier.width(8.dp))
                }
            }

            if (media.type == "tv") {

                Spacer(Modifier.height(20.dp))

                Text(
                    "SEASONS",
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                if (loadingSeasons) {

                    CircularProgressIndicator()

                } else if (seasons.isEmpty()) {

                    Text(
                        "No season information found.",
                        color = Color.Gray
                    )

                } else {

                    LazyRow(
                        horizontalArrangement =
                            Arrangement.spacedBy(8.dp)
                    ) {

                        items(seasons) { season ->

                            FilterChip(
                                selected =
                                    selectedSeason ==
                                            season.number,
                                onClick = {
                                    selectedSeason =
                                        season.number
                                },
                                label = {
                                    Text(
                                        "Season ${season.number}"
                                    )
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    val currentSeason =
                        seasons.firstOrNull {
                            it.number ==
                                    selectedSeason
                        }

                    Text(
                        "${
                            currentSeason?.name
                                ?: "Season $selectedSeason"
                        } • ${
                            currentSeason?.episodeCount
                                ?: episodes.size
                        } episodes",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(Modifier.height(10.dp))

                    if (loadingEpisodes) {

                        CircularProgressIndicator()

                    } else if (episodes.isEmpty()) {

                        Text(
                            "No episodes found.",
                            color = Color.Gray
                        )

                    } else {

                        episodes.forEach { episode ->

                            EpisodeCard(
                                episode = episode,
                                onPlay = {
                                    onPlay(
                                        media,
                                        provider,
                                        Pair(
                                            selectedSeason,
                                            episode.number
                                        )
                                    )
                                }
                            )

                            Spacer(
                                Modifier.height(10.dp)
                            )
                        }
                    }
                }

            } else {

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        onPlay(
                            media,
                            provider,
                            null
                        )
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                ) {
                    Text("▶ WATCH NOW")
                }
            }

            if (error != null) {

                Spacer(Modifier.height(12.dp))

                Text(
                    error!!,
                    color = Color.Red
                )
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: Episode,
    onPlay: () -> Unit
) {

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Card
        ),
        shape = RoundedCornerShape(12.dp)
    ) {

        Row(
            Modifier.padding(10.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            if (episode.still != null) {

                AsyncImage(
                    model = IMAGE + episode.still,
                    contentDescription =
                        episode.name,
                    modifier = Modifier
                        .width(130.dp)
                        .height(75.dp),
                    contentScale =
                        ContentScale.Crop
                )

            } else {

                Box(
                    Modifier
                        .width(130.dp)
                        .height(75.dp)
                        .background(
                            Color(0xFF222228)
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {
                    Text(
                        "EP ${episode.number}",
                        color = Color.Gray
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(
                Modifier.weight(1f)
            ) {

                Text(
                    "E${episode.number} • ${episode.name}",
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )

                if (episode.runtime > 0) {

                    Text(
                        "${episode.runtime} min • ★ ${
                            "%.1f".format(
                                episode.rating
                            )
                        }",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                if (episode.overview.isNotBlank()) {

                    Spacer(Modifier.height(3.dp))

                    Text(
                        episode.overview,
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        maxLines = 3
                    )
                }
            }

            Spacer(Modifier.width(6.dp))

            Button(
                onClick = onPlay,
                contentPadding =
                    PaddingValues(
                        horizontal = 12.dp
                    )
            ) {
                Text("▶")
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PlayerScreen(
    player: Triple<
        Media,
        String,
        Pair<Int, Int>?
    >,
    onBack: () -> Unit
) {

    val media = player.first
    val provider = player.second
    val episode = player.third

    val context = LocalContext.current

    val url = when (provider) {

        "cinesrc" -> {
            if (media.type == "movie") {
                "https://cinesrc.st/embed/movie/${media.id}"
            } else {
                "https://cinesrc.st/embed/tv/${media.id}" +
                    "?s=${episode!!.first}&e=${episode.second}"
            }
        }

        "vidfast" -> {
            if (media.type == "movie") {
                "https://vidfast.pro/movie/${media.id}"
            } else {
                "https://vidfast.pro/tv/${media.id}" +
                    "/${episode!!.first}/${episode.second}"
            }
        }

        else -> {
            if (media.type == "movie") {
                "https://vidlink.pro/movie/${media.id}"
            } else {
                "https://vidlink.pro/tv/${media.id}" +
                    "/${episode!!.first}/${episode.second}"
            }
        }
    }

    var pageError by remember {
        mutableStateOf<String?>(null)
    }

    var webViewRef by remember {
        mutableStateOf<WebView?>(null)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF101014))
                .padding(6.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                "‹",
                fontSize = 36.sp,
                color = Color.White,
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp)
            )

            Text(
                media.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )

            TextButton(
                onClick = {
                    webViewRef?.reload()
                }
            ) {
                Text(
                    "Reload",
                    color = Purple
                )
            }

            TextButton(
                onClick = {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse(url)
                        )
                    )
                }
            ) {
                Text(
                    "Browser",
                    color = Purple
                )
            }
        }

        if (pageError != null) {

            Text(
                pageError!!,
                color = Color.Red,
                modifier = Modifier.padding(10.dp)
            )
        }

        AndroidView(
            factory = { ctx ->

                WebView(ctx).apply {

                    webViewRef = this

                    setBackgroundColor(
                        android.graphics.Color.BLACK
                    )

                    settings.apply {

                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true

                        mediaPlaybackRequiresUserGesture =
                            false

                        javaScriptCanOpenWindowsAutomatically =
                            true

                        setSupportMultipleWindows(true)

                        allowFileAccess = true
                        allowContentAccess = true

                        mixedContentMode =
                            WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                        userAgentString =
                            "Mozilla/5.0 (Linux; Android 14; Mobile) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/140.0.0.0 Mobile Safari/537.36"
                    }

                    val cookies =
                        CookieManager.getInstance()

                    cookies.setAcceptCookie(true)

                    cookies.setAcceptThirdPartyCookies(
                        this,
                        true
                    )

                    webChromeClient =
                        WebChromeClient()

                    webViewClient =
                        object : WebViewClient() {

                            override fun shouldOverrideUrlLoading(
                                view: WebView,
                                request: WebResourceRequest
                            ): Boolean {
                                return false
                            }

                            override fun onReceivedError(
                                view: WebView,
                                request: WebResourceRequest,
                                error: WebResourceError
                            ) {

                                if (request.isForMainFrame) {
                                    pageError =
                                        "Player failed to load: " +
                                            error.description
                                }
                            }
                        }

                    loadUrl(url)
                }

            },
            update = {
                webViewRef = it
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

@Composable
private fun SettingsScreen(
    prefs: android.content.SharedPreferences
) {

    var token by remember {
        mutableStateOf(
            prefs.getString(
                "tmdb_token",
                ""
            ) ?: ""
        )
    }

    var saved by remember {
        mutableStateOf(false)
    }

    Column {

        Text(
            "Settings",
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(15.dp))

        Text(
            "TMDB API Read Access Token",
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(6.dp))

        OutlinedTextField(
            value = token,
            onValueChange = {
                token = it
                saved = false
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false
        )

        Spacer(Modifier.height(10.dp))

        Button(
            onClick = {
                prefs.edit()
                    .putString(
                        "tmdb_token",
                        token.trim()
                    )
                    .apply()

                saved = true
            }
        ) {
            Text(
                if (saved) {
                    "Saved ✓"
                } else {
                    "Save token"
                }
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            """
            This app uses TMDB for movie and TV metadata.
            Add your own TMDB API Read Access Token before loading the catalog.
            """.trimIndent(),
            color = Color.Gray,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Playback providers are opened through their documented " +
                "embed/player URLs.",
            color = Color.Gray,
            fontSize = 13.sp
        )
    }
}
