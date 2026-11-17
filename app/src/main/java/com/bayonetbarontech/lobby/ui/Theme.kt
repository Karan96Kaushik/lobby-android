package com.bayonetbarontech.lobby.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LobbyBackground = Color(0xFF0E120E)
val LobbyAccent = Color(0xFF95FF50)
val LobbySurface = Color(0xFF1A221A)

private val scheme = darkColorScheme(
    primary = LobbyAccent,
    onPrimary = LobbyBackground,
    background = LobbyBackground,
    onBackground = Color.White,
    surface = LobbyBackground,
    onSurface = Color.White,
    surfaceContainer = LobbySurface,
    error = Color(0xFFFF8A80)
)

@Composable
fun LobbyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
