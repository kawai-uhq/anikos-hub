package com.anikoshub.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anikoshub.app.data.AppPreferences
import com.anikoshub.app.data.Providers
import com.anikoshub.app.ui.theme.TextMuted
import com.anikoshub.app.util.UpdateChecker
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(prefs: AppPreferences) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var token by remember { mutableStateOf(prefs.tmdbToken) }
    var saved by remember { mutableStateOf(false) }
    var preferredProvider by remember { mutableStateOf(prefs.preferredProviderId) }
    var currentVersion by remember { mutableStateOf("…") }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    var updateUrl by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        currentVersion = try {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName ?: "Unknown"
        } catch (_: Exception) {
            "Unknown"
        }
    }

    fun checkUpdates() {
        scope.launch {
            checkingUpdate = true
            updateMessage = null
            updateUrl = null
            val installed = try {
                context.packageManager
                    .getPackageInfo(context.packageName, 0)
                    .versionName ?: "0.0.0"
            } catch (_: Exception) {
                "0.0.0"
            }
            val result = UpdateChecker.check(installed)
            checkingUpdate = false
            if (result != null) {
                updateUrl = result
            } else {
                updateMessage = "You're on the latest version."
            }
        }
    }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Text("TMDB API Read Access Token", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = token,
            onValueChange = {
                token = it
                saved = false
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 2,
            placeholder = { Text("Paste your Bearer token here") }
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                prefs.tmdbToken = token
                saved = true
            }
        ) {
            Text(if (saved) "Saved ✓" else "Save token")
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        Text("Default provider", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Providers.all.forEach { p ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = preferredProvider == p.id,
                    onClick = {
                        preferredProvider = p.id
                        prefs.preferredProviderId = p.id
                    }
                )
                Text(p.displayName, modifier = Modifier.padding(start = 8.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        Text("App version", fontWeight = FontWeight.Bold)
        Text(currentVersion, color = TextMuted, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { checkUpdates() },
            enabled = !checkingUpdate
        ) {
            Text(if (checkingUpdate) "Checking…" else "Check for updates")
        }
        if (checkingUpdate) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        updateMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = TextMuted, fontSize = 13.sp)
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        Text(
            "This product uses the TMDB API but is not endorsed or certified by TMDB.\n\n" +
                "Add your own TMDB API Read Access Token before loading the catalog.\n\n" +
                "Playback providers are opened through their public embed/player URLs.",
            color = TextMuted,
            fontSize = 13.sp
        )
        Spacer(Modifier.height(32.dp))
    }

    updateUrl?.let { url ->
        AlertDialog(
            onDismissRequest = { updateUrl = null },
            title = { Text("Update available") },
            text = {
                Text("A newer version of Aniko's Hub is available. Download the latest APK?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        } catch (_: Exception) {
                            Toast.makeText(
                                context,
                                "Unable to open the APK download.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        updateUrl = null
                    }
                ) { Text("DOWNLOAD") }
            },
            dismissButton = {
                TextButton(onClick = { updateUrl = null }) { Text("LATER") }
            }
        )
    }
}
