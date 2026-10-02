package com.anikoshub.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.anikoshub.app.data.AppPreferences
import com.anikoshub.app.data.Media
import com.anikoshub.app.data.TmdbClient
import com.anikoshub.app.ui.screens.DetailScreen
import com.anikoshub.app.ui.screens.MainScreen
import com.anikoshub.app.ui.screens.PlayerScreen
import com.anikoshub.app.ui.theme.AnikosHubTheme
import com.anikoshub.app.util.UpdateChecker

class MainActivity : ComponentActivity() {

    private val prefs by lazy { AppPreferences(this) }
    private val client by lazy { TmdbClient { prefs.tmdbToken } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AnikosHubTheme {
                AnikosHubApp(client, prefs)
            }
        }
    }
}

private data class PlayerRequest(
    val media: Media,
    val providerId: String,
    val episode: Pair<Int, Int>?
)

@Composable
private fun AnikosHubApp(
    client: TmdbClient,
    prefs: AppPreferences
) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Media?>(null) }
    var player by remember { mutableStateOf<PlayerRequest?>(null) }
    var updateUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val currentVersion = try {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName ?: "0.0.0"
        } catch (_: Exception) {
            "0.0.0"
        }
        updateUrl = UpdateChecker.check(currentVersion)
    }

    when {
        player != null -> {
            val req = player!!
            PlayerScreen(
                media = req.media,
                providerId = req.providerId,
                episode = req.episode,
                onBack = { player = null }
            )
        }
        selected != null -> {
            DetailScreen(
                media = selected!!,
                client = client,
                prefs = prefs,
                onBack = { selected = null },
                onPlay = { media, providerId, episode ->
                    prefs.recordWatch(
                        media = media,
                        season = episode?.first,
                        episode = episode?.second,
                        providerId = providerId
                    )
                    player = PlayerRequest(media, providerId, episode)
                }
            )
        }
        else -> {
            MainScreen(
                client = client,
                prefs = prefs,
                tab = tab,
                onTabChange = { tab = it },
                onOpen = { selected = it }
            )
        }
    }

    updateUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { updateUrl = null },
            title = { Text("Update available") },
            text = { Text("A newer version of Aniko's Hub is available.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
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
                ) { Text("UPDATE") }
            },
            dismissButton = {
                TextButton(onClick = { updateUrl = null }) { Text("LATER") }
            }
        )
    }
}
