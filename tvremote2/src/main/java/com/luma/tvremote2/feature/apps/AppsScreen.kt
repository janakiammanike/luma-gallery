package com.luma.tvremote2.feature.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luma.tvremote2.core.RemoteViewModel
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
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard("Apps") {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text("Filter apps") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.reportPending("Search TV") }, modifier = Modifier.weight(1f)) {
                    Text("Search TV")
                }
                OutlinedButton(onClick = vm::loadApps, modifier = Modifier.weight(1f)) {
                    Text("Reload")
                }
            }
            Text(
                text = if (state.apps.isEmpty()) {
                    "0 app(s)\nNo apps returned by the TV. Tap Reload."
                } else {
                    "${apps.size} of ${state.apps.size} app(s)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        apps.forEach { app ->
            OutlinedButton(
                onClick = { vm.launchApp(app.id) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("${app.title}  ·  ${app.id}", modifier = Modifier.weight(1f))
            }
        }
    }
}
