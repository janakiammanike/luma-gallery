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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun InputsTab(vm: RemoteViewModel) {
    val state by vm.ui.collectAsState()

    LaunchedEffect(Unit) { vm.refreshAudio() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Sound & audio", fontWeight = FontWeight.SemiBold)
                Text(
                    "Volume ${state.audioVolume} · ${if (state.audioMuted) "muted" else "unmuted"} · Output: ${state.soundOutput}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::refreshAudio, modifier = Modifier.weight(1f)) {
                        Text("Refresh")
                    }
                    OutlinedButton(
                        onClick = { vm.setSoundOutput("tv_speaker") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("TV speakers")
                    }
                }
                Text(
                    "Sound Sharing to phone/headphones needs LG's pairing flow and is not exposed by this LAN API.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("App & input control", fontWeight = FontWeight.SemiBold)
                state.foregroundApp?.let {
                    Text("Foreground: $it", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = vm::closeForegroundApp) {
                        Text("Close foreground app")
                    }
                } ?: Text("Foreground app unknown", style = MaterialTheme.typography.bodySmall)
                Text("Inputs", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.selectInput("com.webos.app.hdmi1") }) { Text("HDMI 1") }
                    Button(onClick = { vm.selectInput("com.webos.app.hdmi2") }) { Text("HDMI 2") }
                    Button(onClick = { vm.selectInput("com.webos.app.hdmi3") }) { Text("HDMI 3") }
                }
            }
        }
    }
}
