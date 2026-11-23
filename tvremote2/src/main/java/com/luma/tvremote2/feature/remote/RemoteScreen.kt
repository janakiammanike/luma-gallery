package com.luma.tvremote2.feature.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luma.tvremote2.core.RemoteViewModel
import com.luma.tvremote2.designsystem.SectionCard

@Composable
fun RemoteScreen(vm: RemoteViewModel, modifier: Modifier = Modifier) {
    val state by vm.ui.collectAsState()

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (state.audioVolume != null) {
            Text(
                "Volume: ${state.audioVolume}${if (state.audioMuted) " · Muted" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        SectionCard("System") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("⌂  Home" to "HOME", "◀  Back" to "BACK", "⊞  Recent" to "RECENT", "☰  Menu" to "MENU")
                    .forEach { (label, name) ->
                        OutlinedButton(onClick = { vm.button(name) }, modifier = Modifier.weight(1f)) {
                            Text(label)
                        }
                    }
            }
        }

        SectionCard("D-pad") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Button(onClick = { vm.button("UP") }) { Text("▲") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.button("LEFT") }) { Text("◀") }
                    Button(onClick = { vm.button("OK") }) { Text("OK") }
                    Button(onClick = { vm.button("RIGHT") }) { Text("▶") }
                }
                Button(onClick = { vm.button("DOWN") }) { Text("▼") }
            }
        }

        SectionCard("Scroll") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.scroll(-200, 0) }, modifier = Modifier.weight(1f)) { Text("◀ Left") }
                OutlinedButton(onClick = { vm.scroll(0, -200) }, modifier = Modifier.weight(1f)) { Text("▲ Up") }
                OutlinedButton(onClick = { vm.scroll(0, 200) }, modifier = Modifier.weight(1f)) { Text("▼ Down") }
                OutlinedButton(onClick = { vm.scroll(200, 0) }, modifier = Modifier.weight(1f)) { Text("Right ▶") }
            }
        }

        SectionCard("Volume & channels") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::volumeDown, modifier = Modifier.weight(1f)) { Text("Vol −") }
                OutlinedButton(onClick = vm::toggleMute, modifier = Modifier.weight(1f)) { Text("Mute") }
                OutlinedButton(onClick = vm::volumeUp, modifier = Modifier.weight(1f)) { Text("Vol +") }
                OutlinedButton(onClick = vm::channelDown, modifier = Modifier.weight(1f)) { Text("Ch −") }
                OutlinedButton(onClick = vm::channelUp, modifier = Modifier.weight(1f)) { Text("Ch +") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.reportPending("Playback") }, modifier = Modifier.weight(1f)) {
                    Text("Playback")
                }
                OutlinedButton(onClick = vm::powerOn, modifier = Modifier.weight(1f)) { Text("Power on TV") }
                OutlinedButton(onClick = vm::powerOff, modifier = Modifier.weight(1f)) { Text("Power off TV") }
            }
        }

        Text(
            text = "Customize buttons",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
