package com.luma.tvremote2.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = LumaColors.PrimaryDark,
    onPrimary = LumaColors.OnPrimaryDark,
    primaryContainer = LumaColors.PrimaryContainerDark,
    onPrimaryContainer = LumaColors.OnPrimaryContainerDark,
    secondary = LumaColors.SecondaryDark,
    onSecondary = LumaColors.OnSecondaryDark,
    secondaryContainer = LumaColors.SecondaryContainerDark,
    onSecondaryContainer = LumaColors.OnSecondaryContainerDark,
    tertiary = LumaColors.TertiaryDark,
    background = LumaColors.BackgroundDark,
    onBackground = LumaColors.OnBackgroundDark,
    surface = LumaColors.SurfaceDark,
    onSurface = LumaColors.OnSurfaceDark,
    surfaceVariant = LumaColors.SurfaceVariantDark,
    onSurfaceVariant = LumaColors.OnSurfaceVariantDark,
    error = LumaColors.ErrorDark,
    onError = LumaColors.OnErrorDark
)

private val LightColors = lightColorScheme(
    primary = LumaColors.PrimaryLight,
    onPrimary = LumaColors.OnPrimaryLight,
    secondary = LumaColors.SecondaryLight,
    onSecondary = LumaColors.OnSecondaryLight,
    background = LumaColors.BackgroundLight,
    surface = LumaColors.SurfaceLight
)

/** Same signature and same colours as the legacy TVRemoteTheme. */
@Composable
fun LumaTheme(darkTheme: Boolean? = null, content: @Composable () -> Unit) {
    val dark = darkTheme ?: isSystemInDarkTheme()
    val colors = if (dark) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = LumaTypography, content = content)
}
