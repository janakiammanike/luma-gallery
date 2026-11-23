package com.luma.tvremote2.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.luma.tvremote2.core.NavItem
import com.luma.tvremote2.core.Routes
import kotlin.math.floor

/** Base surface used by every screen section. */
@Composable
fun LumaCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LumaTokens.RadiusLG),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = LumaTokens.SpaceXS)
    ) {
        Column(modifier = Modifier.padding(LumaTokens.CardPadding)) {
            content()
        }
    }
}

/** Primary action. Labels never wrap and the target is at least 48dp tall. */
@Composable
fun LumaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = LumaTokens.ButtonHeight),
        enabled = enabled,
        shape = RoundedCornerShape(LumaTokens.RadiusMD),
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LumaTokens.SpaceSM)
        ) {
            icon?.invoke()
            SingleLineText(text)
        }
    }
}

/** Secondary action with the same height and label rules as [LumaButton]. */
@Composable
fun LumaOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = LumaTokens.ButtonHeight),
        enabled = enabled,
        shape = RoundedCornerShape(LumaTokens.RadiusMD),
        contentPadding = ButtonDefaults.ContentPadding
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LumaTokens.SpaceSM)
        ) {
            icon?.invoke()
            SingleLineText(text)
        }
    }
}

/** Icon-only action with a full 48dp touch target and an accessibility label. */
@Composable
fun LumaIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(LumaTokens.TouchTarget),
        enabled = enabled
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            modifier = Modifier.size(LumaTokens.IconSize)
        )
    }
}

/** Compact remote key: single line, predictable minimum width and height. */
@Composable
fun LumaRemoteButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false
) {
    val heights = Modifier.defaultMinSize(
        minWidth = LumaTokens.RemoteKeyMinWidth,
        minHeight = LumaTokens.RemoteKeyHeight
    )
    if (primary) {
        Button(
            onClick = onClick,
            modifier = modifier.then(heights),
            enabled = enabled,
            shape = RoundedCornerShape(LumaTokens.RadiusMD),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = LumaTokens.SpaceSM,
                vertical = LumaTokens.SpaceSM
            )
        ) {
            SingleLineText(label, textAlign = TextAlign.Center)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.then(heights),
            enabled = enabled,
            shape = RoundedCornerShape(LumaTokens.RadiusMD),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = LumaTokens.SpaceSM,
                vertical = LumaTokens.SpaceSM
            )
        ) {
            SingleLineText(label, textAlign = TextAlign.Center)
        }
    }
}

/** D-pad kept as the primary visual element on the Remote screen. */
@Composable
fun LumaDPad(
    onDirection: (String) -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier
) {
    val key = Modifier.size(LumaTokens.DPadKeySize)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LumaTokens.SpaceSM)
    ) {
        LumaRemoteButton("▲", { onDirection("UP") }, key, primary = true)
        Row(
            horizontalArrangement = Arrangement.spacedBy(LumaTokens.SpaceSM),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LumaRemoteButton("◀", { onDirection("LEFT") }, key, primary = true)
            LumaRemoteButton("OK", onEnter, key, primary = true)
            LumaRemoteButton("▶", { onDirection("RIGHT") }, key, primary = true)
        }
        LumaRemoteButton("▼", { onDirection("DOWN") }, key, primary = true)
    }
}

/** Text field with a stable height and one-line input for TV search boxes. */
@Composable
fun LumaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { SingleLineText(label) },
        modifier = modifier.fillMaxWidth().defaultMinSize(minHeight = LumaTokens.TextFieldHeight),
        enabled = enabled,
        singleLine = true,
        maxLines = 1,
        shape = RoundedCornerShape(LumaTokens.RadiusMD),
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon
    )
}

/** Connection line in the app bar: state, IP and transport stay on two lines. */
@Composable
fun LumaDeviceStatus(
    appName: String,
    statusLine: String,
    connectionLabel: String?,
    connected: Boolean
) {
    Column {
        SingleLineText(
            appName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(LumaTokens.SpaceSM)
        ) {
            Text(
                text = statusLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (connected && connectionLabel != null) {
                Surface(
                    shape = RoundedCornerShape(LumaTokens.RadiusPill),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = connectionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(
                            horizontal = LumaTokens.SpaceSM,
                            vertical = LumaTokens.SpaceXS
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

/** Bottom navigation with vector icons and single-line labels. */
@Composable
fun LumaBottomNavigation(
    items: List<NavItem>,
    selectedRoute: String?,
    onSelect: (NavItem) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(modifier = modifier) {
        items.forEach { item ->
            NavigationBarItem(
                selected = selectedRoute == item.route,
                onClick = { onSelect(item) },
                icon = {
                    Icon(
                        imageVector = iconForRoute(item.route),
                        contentDescription = item.label,
                        modifier = Modifier.size(LumaTokens.IconSize)
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        maxLines = 1,
                        softWrap = false,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                alwaysShowLabel = true
            )
        }
    }
}

/**
 * Wraps a row of controls into one or more rows when the screen is narrow.
 * Each item still gets an equal share of the available width.
 */
@Composable
fun <T> LumaAdaptiveRow(
    items: List<T>,
    minItemWidth: Dp,
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(LumaTokens.ItemGap),
    itemContent: @Composable (T, Modifier) -> Unit
) {
    if (items.isEmpty()) return
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val usable = (screenWidth - LumaTokens.ScreenPadding * 2).coerceAtLeast(160.dp)
    val gap = LumaTokens.ItemGap.value
    val columns = floor((usable.value + gap) / (minItemWidth.value + gap))
        .toInt()
        .coerceIn(1, items.size)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LumaTokens.ItemGap)
    ) {
        items.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = horizontalArrangement
            ) {
                row.forEach { item ->
                    itemContent(item, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SingleLineText(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelLarge,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    textAlign: TextAlign = TextAlign.Center
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis
    )
}

private fun iconForRoute(route: String): ImageVector = when (route) {
    Routes.REMOTE -> Icons.Filled.Home
    Routes.CURSOR -> Icons.AutoMirrored.Filled.ArrowForward
    Routes.KEYBOARD -> Icons.Filled.Edit
    Routes.APPS -> Icons.AutoMirrored.Filled.List
    Routes.MORE -> Icons.Filled.MoreVert
    else -> Icons.Filled.Settings
}
