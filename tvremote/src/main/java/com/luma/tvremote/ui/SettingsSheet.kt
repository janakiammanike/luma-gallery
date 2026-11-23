package com.luma.tvremote.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun SettingsSheet(state: RemoteUiState, vm: RemoteViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Button haptics", modifier = Modifier.weight(1f))
                    Switch(checked = state.haptics, onCheckedChange = vm::setHaptics)
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Music on phone → TV", fontWeight = FontWeight.Medium)
                    Text(
                        if (state.mediaAccessGranted) "Notification access: ON — current song is detected."
                        else "Notification access: OFF — needed to read the song now playing on your phone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!state.mediaAccessGranted) {
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                                }
                                vm.refreshMediaAccess()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Enable notification access")
                        }
                    }
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
