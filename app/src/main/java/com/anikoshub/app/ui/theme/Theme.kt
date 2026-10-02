package com.anikoshub.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Bg = Color(0xFF08080A)
val CardBg = Color(0xFF15151A)
val Purple = Color(0xFF9B5CFF)
val PurpleDim = Color(0xFF6B3DB8)
val TextMuted = Color(0xFF9E9EA8)
val TextSecondary = Color(0xFFC8C8D0)

private val DarkColors = darkColorScheme(
    primary = Purple,
    onPrimary = Color.White,
    secondary = PurpleDim,
    background = Bg,
    surface = CardBg,
    onBackground = Color.White,
    onSurface = Color.White,
    error = Color(0xFFFF6B6B)
)

@Composable
fun AnikosHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content
    )
}
