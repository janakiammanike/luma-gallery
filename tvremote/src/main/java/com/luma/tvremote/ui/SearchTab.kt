package com.luma.tvremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private val SUGGESTIONS = listOf("Netflix", "YouTube", "Prime Video", "Cricket", "News")

@Composable
fun SearchTab(vm: RemoteViewModel) {
    val state by vm.ui.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Content search & discovery", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = state.keyboardText,
                    onValueChange = vm::onKeyboardText,
                    label = { Text("Search the TV") },
                    placeholder = { Text("Movie, show, app…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            vm.addRecentSearch(state.keyboardText)
                            vm.tvSearch(state.keyboardText)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Search TV")
                    }
                    OutlinedButton(
                        onClick = { vm.addRecentSearch(state.keyboardText); vm.appSearch("youtube.leanback.v4", state.keyboardText) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("YouTube")
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Suggestions", fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SUGGESTIONS.take(3).forEach { suggestion ->
                        OutlinedButton(onClick = { vm.onKeyboardText(suggestion) }) {
                            Text(suggestion)
                        }
                    }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Recent searches", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    if (state.recentSearches.isNotEmpty()) {
                        TextButton(onClick = vm::clearRecentSearches) { Text("Clear") }
                    }
                }
                if (state.recentSearches.isEmpty()) {
                    Text(
                        "No recent searches yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.recentSearches.forEach { query ->
                        TextButton(onClick = {
                            vm.onKeyboardText(query)
                            vm.tvSearch(query)
                        }) {
                            Text(query)
                        }
                    }
                }
            }
        }

        Text(
            "Search is honest/partial: opens the TV's own search UI and filters the local app list. Universal content search needs LG's cloud APIs.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
