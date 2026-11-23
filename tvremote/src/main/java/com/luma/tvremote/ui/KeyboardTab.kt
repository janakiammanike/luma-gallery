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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun KeyboardTab(vm: RemoteViewModel) {
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Type on TV", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = state.keyboardText,
                    onValueChange = vm::onKeyboardText,
                    label = { Text("Text") },
                    placeholder = { Text("Type here, then tap Send") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::sendText, modifier = Modifier.weight(1f)) {
                        Text("Send text")
                    }
                    OutlinedButton(onClick = vm::sendEnter) { Text("Enter") }
                    OutlinedButton(onClick = vm::backspace) { Text("⌫") }
                    OutlinedButton(onClick = vm::clearKeyboard) { Text("Clear") }
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Quick search", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.tvSearch("Netflix") }) { Text("Netflix") }
                    OutlinedButton(onClick = { vm.tvSearch("YouTube") }) { Text("YouTube") }
                    OutlinedButton(onClick = { vm.tvSearch("Prime Video") }) { Text("Prime") }
                }
                OutlinedTextField(
                    value = state.keyboardText,
                    onValueChange = vm::onKeyboardText,
                    label = { Text("Search the TV") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(onClick = { vm.tvSearch(state.keyboardText) }) {
                    Text("Search on TV")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Search inside an app", fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Uses the text above. Opens the app's own search UI with a deep link where supported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { vm.appSearch("youtube.leanback.v4", state.keyboardText) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("YouTube")
                    }
                    OutlinedButton(
                        onClick = { vm.appSearch("netflix", state.keyboardText) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Netflix")
                    }
                }
            }
        }

        Text(
            text = "Standard webOS fields accept Send text directly. Apps with a custom keyboard (for example YouTube) may ignore typed text — use the app-specific search above, or navigate with the Cursor tab.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
