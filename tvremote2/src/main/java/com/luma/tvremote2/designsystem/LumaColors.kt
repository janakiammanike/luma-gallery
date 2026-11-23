package com.luma.tvremote2.designsystem

import androidx.compose.ui.graphics.Color

/**
 * Luma TV Remote colour tokens.
 *
 * Migrated verbatim from the legacy module's Theme.kt / colors.xml so the
 * 2.0 shell keeps the exact same visual identity. Do not rebrand here.
 */
object LumaColors {
    // Dark scheme (legacy DarkColors)
    val PrimaryDark = Color(0xFFA78BFA)
    val OnPrimaryDark = Color(0xFF120A24)
    val PrimaryContainerDark = Color(0xFF3B235F)
    val OnPrimaryContainerDark = Color(0xFFEDE9FE)
    val SecondaryDark = Color(0xFF22D3EE)
    val OnSecondaryDark = Color(0xFF05202A)
    val SecondaryContainerDark = Color(0xFF123847)
    val OnSecondaryContainerDark = Color(0xFFCFFAFE)
    val TertiaryDark = Color(0xFFF472B6)
    val BackgroundDark = Color(0xFF080C18)
    val OnBackgroundDark = Color(0xFFE6E9F5)
    val SurfaceDark = Color(0xFF111829)
    val OnSurfaceDark = Color(0xFFE6E9F5)
    val SurfaceVariantDark = Color(0xFF1B2438)
    val OnSurfaceVariantDark = Color(0xFFA9B4CC)
    val ErrorDark = Color(0xFFF87171)
    val OnErrorDark = Color(0xFF2A0A0A)

    // Light scheme (legacy LightColors)
    val PrimaryLight = Color(0xFF6D28D9)
    val OnPrimaryLight = Color.White
    val SecondaryLight = Color(0xFF0891B2)
    val OnSecondaryLight = Color.White
    val BackgroundLight = Color(0xFFF7F7FC)
    val SurfaceLight = Color.White

    // Legacy XML branding colours (launcher / system chrome)
    val BrandBackground = Color(0xFF0B1020)
    val BrandPrimary = Color(0xFF8B5CF6)
    val BrandAccent = Color(0xFF22D3EE)
}
