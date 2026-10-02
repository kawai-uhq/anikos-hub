package com.anikoshub.app

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    private val prefs by lazy { getSharedPreferences("anikoshub", MODE_PRIVATE) }
    private val client by lazy { TmdbClient { prefs.getString("tmdb_token", "") ?: "" } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AnikosHubApp(client, prefs) }
    }
}

data class Media(val id:Int, val title:String, val poster:String?, val backdrop:String?, val overview:String, val type:String, val rating:Double, val year:String)

class TmdbClient(private val tokenProvider: () -> String) {
    suspend fun get(path:String): JSONObject = withContext(Dispatchers.IO) {
        val token = tokenProvider()
        if (token.isBlank()) error("Add your TMDB API Read Access Token in Settings.")
        val conn = URL(API + path).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Authorization", "Bearer $token")
        conn.setRequestProperty("accept", "application/json")
        conn.connectTimeout = 15000; conn.readTimeout = 20000
        val body = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
        if (conn.responseCode !in 200..299) error("TMDB ${conn.responseCode}: $body")
        JSONObject(body)
    }
    suspend fun popular(type:String):List<Media> = get("/$type/popular?language=en-US&page=1").toMedia(type)
    suspend fun trending():List<Media> = get("/trending/all/day?language=en-US").toMedia("all")
    suspend fun search(q:String):List<Media> = get("/search/multi?query=${URLEncoder.encode(q,"UTF-8")}&include_adult=false&language=en-US&page=1").toMedia("all")
    suspend fun detail(id:Int,type:String):Media? = get("/$type/$id?language=en-US").toMedia(type).firstOrNull()
    private fun JSONObject.toMedia(fallback:String):List<Media> {
        val a = optJSONArray("results") ?: return listOfNotNull(parseMedia(this, fallback))
        return (0 until a.length()).mapNotNull { parseMedia(a.optJSONObject(it), fallback) }
    }
    private fun parseMedia(o:JSONObject?, fallback:String):Media? {
        if (o == null) return null
        val type = if (fallback == "all") o.optString("media_type").ifBlank { fallback } else fallback
        if (type !in listOf("movie","tv")) return null
        val title = o.optString(if(type=="movie") "title" else "name").ifBlank { "Untitled" }
        val date = o.optString(if(type=="movie") "release_date" else "first_air_date")
        return Media(o.optInt("id"), title, o.optString("poster_path").ifBlank{null}, o.optString("backdrop_path").ifBlank{null}, o.optString("overview"), type, o.optDouble("vote_average",0.0), date.take(4))
    }
}

@Composable fun AnikosHubApp(client:TmdbClient, prefs:android.content.SharedPreferences) {
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Media?>(null) }
    var player by remember { mutableStateOf<Triple<Media,String,Pair<Int,Int>?>?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    MaterialTheme(colorScheme = darkColorScheme(primary=Purple, background=Bg, surface=Card)) {
        if (player != null) PlayerScreen(player!!, onBack={player=null})
        else if (selected != null) DetailScreen(selected!!, client, prefs, onBack={selected=null}, onPlay={m,p,ep -> player=Triple(m,p,ep)})
        else MainScreen(client, prefs, tab, {tab=it}, {selected=it}, reload)
    }
}

@Composable private fun MainScreen(client:TmdbClient, prefs:android.content.SharedPreferences, tab:Int, setTab:(Int)->Unit, open:(Media)->Unit, reload:Int) {
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<Media>>(emptyList()) }
    var tv by remember { mutableStateOf<List<Media>>(emptyList()) }
    var movies by remember { mutableStateOf<List<Media>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tab, reload) {
        if (tab==0 && items.isEmpty()) runCatching { items=client.trending() }.onFailure {error=it.message}
        if (tab==1 && query.isBlank()) runCatching { movies=client.popular("movie") }.onFailure {error=it.message}
        if (tab==2) runCatching { tv=client.popular("tv") }.onFailure {error=it.message}
    }
    Scaffold(bottomBar={NavigationBar(containerColor=Bg){ listOf("⌂" to "Home","⌕" to "Search","▣" to "Shows","⚙" to "Settings").forEachIndexed {i,p -> NavigationBarItem(selected=tab==i,onClick={setTab(i)},icon={Text(p.first,fontSize=20.sp)},label={Text(p.second)}) } }}, containerColor=Bg) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal=16.dp)) {
            Spacer(Modifier.height(14.dp)); Text("ANIKO'S HUB",color=Purple,fontSize=22.sp,fontWeight=FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            if(tab==3) SettingsScreen(prefs)
            else if(tab==1) {
                OutlinedTextField(
    value = query,
    onValueChange = { query = it },
    modifier = Modifier.fillMaxWidth(),
    placeholder = { Text("Search movies & shows") },
    singleLine = true
)
                Spacer(Modifier.height(12.dp)); SearchResults(client,query,open)
            } else {
                if(error!=null) Text(error!!,color=Color.Red)
                if(tab==0) MediaSection("Trending",items,open)
                else if(tab==2) MediaSection("Popular TV",tv,open)
            }
        }
    }
}

@Composable private fun SearchResults(client:TmdbClient,q:String,open:(Media)->Unit) {
    var results by remember { mutableStateOf<List<Media>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    LaunchedEffect(q) { if(q.length>=2){ loading=true; results=runCatching{client.search(q)}.getOrDefault(emptyList()); loading=false } else results=emptyList() }
    if(loading) CircularProgressIndicator()
    MediaGrid(results,open)
}

@Composable private fun MediaSection(title:String,list:List<Media>,open:(Media)->Unit) {
    Text(title,fontSize=21.sp,fontWeight=FontWeight.Bold); Spacer(Modifier.height(10.dp)); LazyRow(horizontalArrangement=Arrangement.spacedBy(10.dp)){items(list){Poster(it,open)}}
}

@Composable private fun MediaGrid(list:List<Media>,open:(Media)->Unit){ Column(Modifier.verticalScroll(rememberScrollState())){ list.chunked(2).forEach{row-> Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){row.forEach{Poster(it,open,Modifier.weight(1f))}; if(row.size==1) Spacer(Modifier.weight(1f))}; Spacer(Modifier.height(12.dp))} } }

@Composable private fun Poster(m:Media,open:(Media)->Unit,mod:Modifier=Modifier){ Column(mod.clickable{open(m)}){AsyncImage(model=IMAGE+(m.poster?:m.backdrop),contentDescription=m.title,modifier=Modifier.fillMaxWidth().height(245.dp),contentScale=ContentScale.Crop); Spacer(Modifier.height(5.dp)); Text(m.title,maxLines=1,fontWeight=FontWeight.SemiBold); Text("${m.year}  ★ ${"%.1f".format(m.rating)}",fontSize=12.sp,color=Color.Gray)} }

@Composable private fun DetailScreen(m:Media,client:TmdbClient,prefs:android.content.SharedPreferences,onBack:()->Unit,onPlay:(Media,String,Pair<Int,Int>?)->Unit){
    var provider by remember { mutableStateOf("vidlink") }; var season by remember { mutableIntStateOf(1) }; var episode by remember { mutableIntStateOf(1) }
    Column(Modifier.fillMaxSize().background(Bg).verticalScroll(rememberScrollState())){
        Box{AsyncImage(model=IMAGE+m.backdrop,contentDescription=null,modifier=Modifier.fillMaxWidth().height(240.dp),contentScale=ContentScale.Crop); Text("‹",fontSize=42.sp,modifier=Modifier.padding(12.dp).clickable{onBack()})}
        Column(Modifier.padding(16.dp)){Text(m.title,fontSize=27.sp,fontWeight=FontWeight.Bold); Text("${m.year}  •  ${m.type.uppercase()}  •  ★ ${"%.1f".format(m.rating)}",color=Color.Gray); Spacer(Modifier.height(12.dp)); Text(m.overview.ifBlank{"No description available."},color=Color.LightGray)
            Spacer(Modifier.height(20.dp)); Text("Server",fontWeight=FontWeight.Bold); Row(Modifier.horizontalScroll(rememberScrollState())){listOf("vidlink","cinesrc","vidfast").forEach{p->FilterChip(selected=provider==p,onClick={provider=p},label={Text(p.uppercase())});Spacer(Modifier.width(8.dp))}}
            if(m.type=="tv"){Spacer(Modifier.height(14.dp));Row(verticalAlignment=Alignment.CenterVertically){Button(onClick={if(season>1)season--}){Text("−")};Text(" Season $season ",Modifier.padding(12.dp));Button(onClick={season++}){Text("+")};Spacer(Modifier.width(12.dp));Button(onClick={if(episode>1)episode--}){Text("−")};Text(" Ep $episode ",Modifier.padding(12.dp));Button(onClick={episode++}){Text("+")}}}
            Spacer(Modifier.height(18.dp));Button(onClick={onPlay(m,provider,if(m.type=="tv") Pair(season,episode) else null)},modifier=Modifier.fillMaxWidth()){Text("▶  WATCH NOW")}
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable private fun PlayerScreen(p:Triple<Media,String,Pair<Int,Int>?>,onBack:()->Unit){
    val (m,provider,ep)=p
    val url=when(provider){"cinesrc"->if(m.type=="movie")"https://cinesrc.st/embed/movie/${m.id}" else "https://cinesrc.st/embed/tv/${m.id}?s=${ep!!.first}&e=${ep.second}";"vidfast"->if(m.type=="movie")"https://vidfast.pro/movie/${m.id}" else "https://vidfast.pro/tv/${m.id}/${ep!!.first}/${ep.second}";else->if(m.type=="movie")"https://vidlink.pro/movie/${m.id}" else "https://vidlink.pro/tv/${m.id}/${ep!!.first}/${ep.second}"}
    Box(Modifier.fillMaxSize().background(Color.Black)){ AndroidView(factory={ctx->WebView(ctx).apply{settings.javaScriptEnabled=true;settings.domStorageEnabled=true;settings.mediaPlaybackRequiresUserGesture=false;webChromeClient=WebChromeClient();webViewClient=object:WebViewClient(){override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest)=false};loadUrl(url)}} ,modifier=Modifier.fillMaxSize()); Text("‹",fontSize=42.sp,color=Color.White,modifier=Modifier.padding(12.dp).clickable{onBack()}) }
}

@Composable private fun SettingsScreen(prefs:android.content.SharedPreferences){
    var token by remember { mutableStateOf(prefs.getString("tmdb_token","")?:"") }; var saved by remember{mutableStateOf(false)}
    Text("Settings",fontSize=25.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(15.dp));Text("TMDB API Read Access Token",fontWeight=FontWeight.Bold);Spacer(Modifier.height(6.dp));OutlinedTextField(token,{token=it},modifier=Modifier.fillMaxWidth(),singleLine=false);Spacer(Modifier.height(10.dp));Button(onClick={prefs.edit().putString("tmdb_token",token.trim()).apply();saved=true}){Text(if(saved)"Saved ✓" else "Save token")};Spacer(Modifier.height(20.dp));Text("This app uses TMDB for movie and TV metadata. Add your own TMDB API Read Access Token before loading the catalog.",color=Color.Gray,fontSize=13.sp);Spacer(Modifier.height(8.dp));Text("Playback providers are opened through their documented embed/player URLs.",color=Color.Gray,fontSize=13.sp)
}
