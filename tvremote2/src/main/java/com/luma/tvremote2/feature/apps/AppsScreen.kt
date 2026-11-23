package com.luma.tvremote2.feature.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.luma.tvremote2.core.RemoteViewModel
import com.luma.tvremote2.designsystem.LumaOutlinedButton
import com.luma.tvremote2.designsystem.LumaTextField
import com.luma.tvremote2.designsystem.LumaTokens
import com.luma.tvremote2.designsystem.SectionCard

@Composable
fun AppsScreen(vm: RemoteViewModel, modifier: Modifier = Modifier) {
    val state by vm.ui.collectAsState()
    var filter by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.loadApps() }

    val apps = state.apps.filter {
        filter.isBlank() || it.title.contains(filter, ignoreCase = true) ||
            it.id.contains(filter, ignoreCase = true)
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(LumaTokens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(LumaTokens.SpaceMD)
    ) {
        SectionCard("Apps") {
            LumaTextField(
                value = filter,
                onValueChange = { filter = it },
                label = "Filter apps",
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(LumaTokens.IconSmall)
                    )
                }
            )
            Row(
                modifier = Modifier.padding(top = LumaTokens.SpaceSM),
                horizontalArrangement = Arrangement.spacedBy(LumaTokens.SpaceSM)
            ) {
                LumaOutlinedButton(
                    text = "Search TV",
                    onClick = { vm.searchTvFor(filter) },
                    modifier = Modifier.weight(1f)
                )
                LumaOutlinedButton(
                    text = "Reload",
                    onClick = vm::loadApps,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = if (state.apps.isEmpty()) {
                    "No apps returned by the TV. Tap Reload."
                } else {
                    "${apps.size} of ${state.apps.size} app(s)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = LumaTokens.SpaceSM),
                maxLines = 2
            )
            state.message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = LumaTokens.SpaceXS),
                    maxLines = 2
                )
            }
        }

        apps.forEach { app ->
            LumaOutlinedButton(
                text = "${app.title}  ·  ${app.id}",
                onClick = { vm.launchApp(app.id) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
