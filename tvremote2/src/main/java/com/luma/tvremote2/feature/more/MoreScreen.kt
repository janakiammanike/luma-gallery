package com.luma.tvremote2.feature.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import com.luma.tvremote2.core.Routes
import com.luma.tvremote2.core.moreItems
import com.luma.tvremote2.designsystem.LumaTokens

/** Legacy "More controls" dialog, same items and same order. */
@Composable
fun MoreDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(LumaTokens.ScreenPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "More controls",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = LumaTokens.SpaceMD)
            )
            androidx.compose.material3.Surface(
                shape = androidx.compose.foundation.shape.RoundedCornerShape(LumaTokens.RadiusLG),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(LumaTokens.SpaceSM)) {
                    moreItems.forEach { item ->
                        TextButton(
                            onClick = { onPick(item.route) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = iconForRoute(item.route),
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(end = LumaTokens.SpaceSM)
                                    .size(LumaTokens.IconSmall)
                            )
                            Text(
                                item.label,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun iconForRoute(route: String) = when (route) {
    Routes.INPUTS -> Icons.Filled.Settings
    Routes.SEARCH -> Icons.Filled.Search
    Routes.MUSIC -> Icons.Filled.PlayArrow
    Routes.CAST -> Icons.Filled.Share
    else -> Icons.Filled.Settings
}
