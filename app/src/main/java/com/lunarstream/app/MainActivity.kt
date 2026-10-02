package com.anikoshub.app

import android.app.AlertDialog
import android.os.Build
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
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

            if (responseCode !in 200..299) {
                error("TMDB $responseCode: $body")
            }

            JSONObject(body)
        }

    suspend fun popular(type: String): List<Media> =
        get(
            "/$type/popular?language=en-US&page=1"
        ).toMedia(type)

    suspend fun trending(): List<Media> =
        get(
            "/trending/all/day?language=en-US"
        ).toMedia("all")

    suspend fun search(q: String): List<Media> =
        get(
            "/search/multi?query=${
                URLEncoder.encode(q, "UTF-8")
            }&include_adult=false&language=en-US&page=1"
        ).toMedia("all")

    suspend fun detail(
        id: Int,
        type: String
    ): Media? =
        get(
            "/$type/$id?language=en-US"
        ).toMedia(type).firstOrNull()

    suspend fun tvSeasons(id: Int): List<TvSeason> {

        val json =
            get("/tv/$id?language=en-US")

        val seasons =
            json.optJSONArray("seasons")
                ?: return emptyList()

        return (0 until seasons.length())
            .mapNotNull { index ->

                val s = seasons.optJSONObject(index)
                    ?: return@mapNotNull null

                val number =
                    s.optInt("season_number", -1)

                if (number <= 0) {
                    return@mapNotNull null
                }

                TvSeason(
                    number = number,
                    name = s.optString(
                        "name",
                        "Season $number"
                    ),
                    episodeCount = s.optInt(
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
            get(
                "/tv/$id/season/$season?language=en-US"
            )

        val array =
            json.optJSONArray("episodes")
                ?: return emptyList()

        return (0 until array.length())
            .mapNotNull { index ->

                val e =
                    array.optJSONObject(index)
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

        val array =
            optJSONArray("results")

        if (array == null) {
            return listOfNotNull(
                parseMedia(this, fallback)
            )
        }

        return (0 until array.length())
            .mapNotNull {
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

        if (type !in listOf("movie", "tv")) {
            return null
        }

        val title =
            o.optString(
                if (type == "movie")
                    "title"
                else
                    "name"
            ).ifBlank {
                "Untitled"
            }

        val date =
            o.optString(
                if (type == "movie")
                    "release_date"
                else
                    "first_air_date"
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
fun AnikosHubApp(
    client: TmdbClient,
    prefs: android.content.SharedPreferences
) {

    var tab by remember {
        mutableIntStateOf(0)
    }

    var selected by remember {
        mutableStateOf<Media?>(null)
    }

    var player by remember {
        mutableStateOf<
            Triple<
                Media,
                String,
                Pair<Int, Int>?
            >?
        >(null)
    }

    var reload by remember {
        mutableIntStateOf(0)
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Purple,
            background = Bg,
            surface = Card
        )
    ) {

        if (player != null) {

            PlayerScreen(
                player!!,
                onBack = {
                    player = null
                }
            )

        } else if (selected != null) {

            DetailScreen(
                selected!!,
                client,
                prefs,
                onBack = {
                    selected = null
                },
                onPlay = { media, provider, ep ->
                    player =
                        Triple(
                            media,
                            provider,
                            ep
                        )
                }
            )

        } else {

            MainScreen(
                client,
                prefs,
                tab,
                { tab = it },
                { selected = it },
                reload
            )
        }
    }
}

@Composable
private fun MainScreen(
    client: TmdbClient,
    prefs: android.content.SharedPreferences,
    tab: Int,
    setTab: (Int) -> Unit,
    open: (Media) -> Unit,
    reload: Int
) {

    var query by remember {
        mutableStateOf("")
    }

    var items by remember {
        mutableStateOf<List<Media>>(emptyList())
    }

    var tv by remember {
        mutableStateOf<List<Media>>(emptyList())
    }

    var movies by remember {
        mutableStateOf<List<Media>>(emptyList())
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(tab, reload) {

        if (tab == 0 && items.isEmpty()) {

            runCatching {
                items = client.trending()
            }.onFailure {
                error = it.message
            }
        }

        if (tab == 1 && query.isBlank()) {

            runCatching {
                movies = client.popular("movie")
            }.onFailure {
                error = it.message
            }
        }

        if (tab == 2) {

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
                ).forEachIndexed { i, p ->

                    NavigationBarItem(
                        selected = tab == i,
                        onClick = {
                            setTab(i)
                        },
                        icon = {
                            Text(
                                p.first,
                                fontSize = 20.sp
                            )
                        },
                        label = {
                            Text(p.second)
                        }
                    )
                }
            }
        },
        containerColor = Bg
    ) { pad ->

        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp)
        ) {

            Spacer(
                Modifier.height(14.dp)
            )

            Text(
                "ANIKO'S HUB",
                color = Purple,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                Modifier.height(12.dp)
            )

            if (tab == 3) {

                SettingsScreen(prefs)

            } else if (tab == 1) {

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

                Spacer(
                    Modifier.height(12.dp)
                )

                SearchResults(
                    client,
                    query,
                    open
                )

            } else {

                if (error != null) {
                    Text(
                        error!!,
                        color = Color.Red
                    )
                }

                if (tab == 0) {

                    MediaSection(
                        "Trending",
                        items,
                        open
                    )

                } else if (tab == 2) {

                    MediaSection(
                        "Popular TV",
                        tv,
                        open
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    client: TmdbClient,
    q: String,
    open: (Media) -> Unit
) {

    var results by remember {
        mutableStateOf<List<Media>>(emptyList())
    }

    var loading by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(q) {

        if (q.length >= 2) {

            loading = true

            results =
                runCatching {
                    client.search(q)
                }.getOrDefault(emptyList())

            loading = false

        } else {

            results = emptyList()
        }
    }

    if (loading) {
        CircularProgressIndicator()
    }

    MediaGrid(
        results,
        open
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

    Spacer(
        Modifier.height(10.dp)
    )

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

    Column(
        Modifier.verticalScroll(
            rememberScrollState()
        )
    ) {

        list.chunked(2).forEach { row ->

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

            Spacer(
                Modifier.height(12.dp)
            )
        }
    }
}

@Composable
private fun Poster(
    m: Media,
    open: (Media) -> Unit,
    mod: Modifier = Modifier
) {

    Column(
        mod.clickable {
            open(m)
        }
    ) {

        val image =
            m.poster ?: m.backdrop

        if (image != null) {

            AsyncImage(
                model = IMAGE + image,
                contentDescription = m.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(245.dp),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(
            Modifier.height(5.dp)
        )

        Text(
            m.title,
            maxLines = 1,
            fontWeight = FontWeight.SemiBold
        )

        Text(
            "${m.year} ★ ${
                "%.1f".format(m.rating)
            }",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun DetailScreen(
    m: Media,
    client: TmdbClient,
    prefs: android.content.SharedPreferences,
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

    var selectedEpisode by remember {
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

    LaunchedEffect(m.id, m.type) {

        if (m.type == "tv") {

            loadingSeasons = true

            runCatching {

                val loaded =
                    client.tvSeasons(m.id)

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
        m.id,
        selectedSeason
    ) {

        if (
            m.type == "tv" &&
            selectedSeason > 0
        ) {

            loadingEpisodes = true

            runCatching {

                episodes =
                    client.episodes(
                        m.id,
                        selectedSeason
                    )

                if (episodes.isNotEmpty()) {
                    selectedEpisode =
                        episodes.first().number
                }

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
                m.backdrop ?: m.poster

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
                    .clickable {
                        onBack()
                    }
            )
        }

        Column

        suspend fun checkForUpdate(
    currentVersion: String
): String? = withContext(Dispatchers.IO) {

    try {

        val url = URL(
            "https://api.github.com/repos/" +
            "kawai-uhq/anikos-hub/releases/latest"
        )

        val connection =
            url.openConnection() as HttpURLConnection

        connection.requestMethod = "GET"

        connection.setRequestProperty(
            "Accept",
            "application/vnd.github+json"
        )

        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        if (connection.responseCode !in 200..299) {
            return@withContext null
        }

        val body =
            connection.inputStream
                .bufferedReader()
                .use {
                    it.readText()
                }

        val json = JSONObject(body)

        val latestTag =
            json.optString("tag_name")

        val latestVersion =
            latestTag.removePrefix("v")

        if (
            latestVersion.isNotBlank() &&
            latestVersion != currentVersion
        ) {

            val assets =
                json.optJSONArray("assets")

            if (
                assets != null &&
                assets.length() > 0
            ) {

                val asset =
                    assets.getJSONObject(0)

                return@withContext asset.optString(
                    "browser_download_url"
                )
            }
        }

        null

    } catch (e: Exception) {

        null
    }
        }
