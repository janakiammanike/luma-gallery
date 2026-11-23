package com.luma.tvremote2.feature.inputs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luma.tvremote2.core.RemoteViewModel
import com.luma.tvremote2.designsystem.SectionCard

@Composable
fun InputsScreen(vm: RemoteViewModel, modifier: Modifier = Modifier) {
    val state by vm.ui.collectAsState()

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard("Inputs & sound") {
            Text("Sound output", style = MaterialTheme.typography.bodyMedium)
            Text("TV speaker", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "Volume ${state.audioVolume ?: "—"}${if (state.audioMuted) " · Muted" else ""}",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::volumeDown, modifier = Modifier.weight(1f)) { Text("Vol −") }
                OutlinedButton(onClick = vm::toggleMute, modifier = Modifier.weight(1f)) { Text("Mute") }
                OutlinedButton(onClick = vm::volumeUp, modifier = Modifier.weight(1f)) { Text("Vol +") }
            }
        }
        SectionCard("Inputs") {
            listOf("HDMI 1", "HDMI 2", "HDMI 3", "TV", "Live TV").forEach {
                OutlinedButton(onClick = { vm.reportPending(it) }, modifier = Modifier.fillMaxWidth()) { Text(it) }
            }
        }
    }
}
