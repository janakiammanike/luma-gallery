package com.luma.tvremote2.designsystem

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared measurements for the Luma remote UI.
 *
 * These are layout-only tokens. Colours stay in [LumaColors] / [LumaTheme] so
 * the migrated purple identity is not rebranded.
 */
object LumaTokens {
    // 4dp rhythm
    val SpaceXS = 4.dp
    val SpaceSM = 8.dp
    val SpaceMD = 12.dp
    val SpaceLG = 16.dp
    val SpaceXL = 24.dp

    val ScreenPadding = 16.dp
    val CardPadding = 16.dp
    val ItemGap = 8.dp

    // Comfortable touch targets (>= 48dp) with compact labels.
    val ButtonHeight = 48.dp
    val CompactButtonHeight = 44.dp
    val RemoteKeyHeight = 56.dp
    val RemoteKeyMinWidth = 68.dp
    val DPadKeySize = 64.dp
    val TextFieldHeight = 56.dp
    val TouchTarget = 48.dp

    val IconSize = 20.dp
    val IconSmall = 18.dp
    val AppIconSize = 40.dp

    val RadiusSM = 8.dp
    val RadiusMD = 12.dp
    val RadiusLG = 16.dp
    val RadiusPill = 28.dp

    val TouchpadHeightCompact = 160.dp
    val TouchpadHeight = 200.dp
}

/** One type scale for every screen; Material 3 roles are kept intact. */
val LumaTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
)
