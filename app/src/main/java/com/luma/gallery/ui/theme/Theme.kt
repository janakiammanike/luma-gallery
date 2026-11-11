package com.luma.gallery.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

enum class Accent(val label: String, val color: Color, val deep: Color) {
    Violet("Violet", Color(0xFFB49CFF), Color(0xFF6C4DFF)),
    Lilac("Lilac", Color(0xFFD9A7FF), Color(0xFF9E4BFF)),
    Rose("Rose", Color(0xFFFF9EC4), Color(0xFFFF4D8D)),
    Coral("Coral", Color(0xFFFFA48F), Color(0xFFFF5F45)),
    Peach("Peach", Color(0xFFFFB98A), Color(0xFFFF8A3D)),
    Amber("Amber", Color(0xFFFFC46B), Color(0xFFFF9E3D)),
    Gold("Gold", Color(0xFFF5D97B), Color(0xFFD9A400)),
    Lime("Lime", Color(0xFFC4F07A), Color(0xFF7BC400)),
    Mint("Mint", Color(0xFF7FE7C4), Color(0xFF19B98A)),
    Emerald("Emerald", Color(0xFF6FE3A8), Color(0xFF0FA35F)),
    Sky("Sky", Color(0xFF8ED6FF), Color(0xFF2196F3)),
    Ocean("Ocean", Color(0xFF7FC8FF), Color(0xFF0D74D6)),
    Indigo("Indigo", Color(0xFF9AA8FF), Color(0xFF4A5BFF)),
    Custom("Custom", Color(0xFFB49CFF), Color(0xFF6C4DFF));

    /** Primary accent colour, honouring a user-picked colour for [Custom]. */
    fun base(custom: Int?): Color =
        if (this == Custom && custom != null) Color(custom) else color

    /** Saturated version used for gradients and light-theme fills. */
    fun deepColor(custom: Int?): Color {
        if (this != Custom || custom == null) return deep
        val color = Color(custom)
        return Color(
            red = (color.red * 0.68f).coerceIn(0f, 1f),
            green = (color.green * 0.68f).coerceIn(0f, 1f),
            blue = (color.blue * 0.68f).coerceIn(0f, 1f),
            alpha = 1f
        )
    }
}

enum class ThemeMode { System, Dark, Light }

@Immutable
data class LumaPalette(
    val chrome: Color,
    val card: Color,
    val border: Color,
    val glow: Color,
    val muted: Color,
    val isDark: Boolean
)

val LocalLumaPalette = staticCompositionLocalOf {
    LumaPalette(
        chrome = Color(0xCC101016),
        card = Color(0xFF14141B),
        border = Color(0x1FFFFFFF),
        glow = Color(0x33B49CFF),
        muted = Color(0xFF9C99A8),
        isDark = true
    )
}

private val LumaTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = (-1.6).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.8).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp
    )
)

@Composable
fun LumaTheme(
    mode: ThemeMode = ThemeMode.Dark,
    accent: Accent = Accent.Violet,
    customAccent: Int? = null,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val dark = when (mode) {
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
        ThemeMode.System -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val accentColor = accent.base(customAccent)
    val accentDeep = accent.deepColor(customAccent)

    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark ->
            dynamicDarkColorScheme(context)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !dark -> dynamicLightColorScheme(context)
        dark -> darkColorScheme(
            primary = accentColor,
            onPrimary = Color(0xFF0B0910),
            primaryContainer = accentDeep.copy(alpha = 0.24f),
            onPrimaryContainer = Color(0xFFEDE6FF),
            secondary = Color(0xFFFFC46B),
            onSecondary = Color(0xFF1A1204),
            background = Color(0xFF08080B),
            onBackground = Color(0xFFF2F0F7),
            surface = Color(0xFF0F0F15),
            onSurface = Color(0xFFEFEDF6),
            surfaceVariant = Color(0xFF17171F),
            onSurfaceVariant = Color(0xFFA6A2B3),
            outline = Color(0xFF2C2B36),
            outlineVariant = Color(0xFF1F1F28),
            error = Color(0xFFFF7A90),
            onError = Color(0xFF200208)
        )
        else -> lightColorScheme(
            primary = accentDeep,
            onPrimary = Color.White,
            primaryContainer = accentColor.copy(alpha = 0.22f),
            onPrimaryContainer = Color(0xFF221A3A),
            secondary = Color(0xFF9A5B00),
            background = Color(0xFFF6F3FB),
            onBackground = Color(0xFF15131C),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF16141D),
            surfaceVariant = Color(0xFFEDE9F5),
            onSurfaceVariant = Color(0xFF5D586B),
            outline = Color(0xFFD8D3E3),
            outlineVariant = Color(0xFFE6E2EF)
        )
    }

    val palette = if (dark) {
        LumaPalette(
            chrome = Color(0xE60E0E14),
            card = Color(0xFF14141B),
            border = Color(0x1FFFFFFF),
            glow = accentColor.copy(alpha = 0.18f),
            muted = Color(0xFF9C99A8),
            isDark = true
        )
    } else {
        LumaPalette(
            chrome = Color(0xF2FFFFFF),
            card = Color(0xFFFFFFFF),
            border = Color(0x14000000),
            glow = accentColor.copy(alpha = 0.22f),
            muted = Color(0xFF6B6678),
            isDark = false
        )
    }

    CompositionLocalProvider(LocalLumaPalette provides palette) {
        MaterialTheme(
            colorScheme = scheme,
            typography = LumaTypography,
            content = content
        )
    }
}
