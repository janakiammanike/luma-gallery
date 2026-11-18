package com.luma.tvremote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AppsTab(vm: RemoteViewModel) {
    val state by vm.ui.collectAsState()
    val query = state.appQuery.trim()
    val apps = state.apps.filter {
        query.isBlank() ||
            it.title.contains(query, ignoreCase = true) ||
            it.id.contains(query, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = state.appQuery,
            onValueChange = vm::onAppQuery,
            label = { Text("Filter apps") },
            placeholder = { Text("Netflix, YouTube, HDMI…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { vm.tvSearch(query) }) { Text("Search TV") }
            OutlinedButton(onClick = vm::reloadApps) { Text("Reload") }
            Text(
                "${apps.size} app(s)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (apps.isEmpty()) {
            Text(
                text = if (state.apps.isEmpty()) {
                    "No apps returned by the TV. Tap Reload."
                } else {
                    "No apps match “$query”."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(apps, key = { it.id }) { app ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.launchApp(app.id) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(app.title, fontWeight = FontWeight.SemiBold)
                            Text(
                                app.id,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "Launch",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
