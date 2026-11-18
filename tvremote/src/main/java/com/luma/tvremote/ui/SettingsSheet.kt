package com.luma.tvremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun SettingsSheet(state: RemoteUiState, vm: RemoteViewModel, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Settings", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dark theme", modifier = Modifier.weight(1f))
                    Switch(checked = state.darkTheme, onCheckedChange = vm::setDarkTheme)
                }
                Text(
                    "Connected TV: ${state.connectedHost ?: "none"}\nFirmware features vary; see docs/tvremote-protocol.md.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}
