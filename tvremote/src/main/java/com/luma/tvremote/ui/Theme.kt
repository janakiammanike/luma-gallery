package com.luma.tvremote.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF120A24),
    primaryContainer = Color(0xFF3B235F),
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = Color(0xFF22D3EE),
    onSecondary = Color(0xFF05202A),
    secondaryContainer = Color(0xFF123847),
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = Color(0xFFF472B6),
    background = Color(0xFF080C18),
    onBackground = Color(0xFFE6E9F5),
    surface = Color(0xFF111829),
    onSurface = Color(0xFFE6E9F5),
    surfaceVariant = Color(0xFF1B2438),
    onSurfaceVariant = Color(0xFFA9B4CC),
    error = Color(0xFFF87171),
    onError = Color(0xFF2A0A0A)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color.White,
    secondary = Color(0xFF0891B2),
    onSecondary = Color.White,
    background = Color(0xFFF7F7FC),
    surface = Color.White
)

@Composable
fun TVRemoteTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
