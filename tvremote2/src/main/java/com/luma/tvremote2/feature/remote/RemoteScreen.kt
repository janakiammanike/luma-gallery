package com.luma.tvremote2.feature.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
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
    val groups = state.visibleGroups

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

        if ("system" in groups) {
            SectionCard("System") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "⌂  Home" to "HOME",
                        "◀  Back" to "BACK",
                        "⊞  Recent" to "RECENT",
                        "☰  Menu" to "MENU"
                    ).forEach { (label, name) ->
                        OutlinedButton(
                            onClick = { vm.button(name) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(label)
                        }
                    }
                }
            }
        }

        if ("dpad" in groups) {
            SectionCard("D-pad") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = { vm.button("UP") }) { Text("▲") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.button("LEFT") }) { Text("◀") }
                        Button(onClick = { vm.button("ENTER") }) { Text("OK") }
                        Button(onClick = { vm.button("RIGHT") }) { Text("▶") }
                    }
                    Button(onClick = { vm.button("DOWN") }) { Text("▼") }
                }
            }
        }

        if ("scroll" in groups) {
            SectionCard("Scroll") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.scroll(-200, 0) }, modifier = Modifier.weight(1f)) {
                        Text("◀ Left")
                    }
                    OutlinedButton(onClick = { vm.scroll(0, -200) }, modifier = Modifier.weight(1f)) {
                        Text("▲ Up")
                    }
                    OutlinedButton(onClick = { vm.scroll(0, 200) }, modifier = Modifier.weight(1f)) {
                        Text("▼ Down")
                    }
                    OutlinedButton(onClick = { vm.scroll(200, 0) }, modifier = Modifier.weight(1f)) {
                        Text("Right ▶")
                    }
                }
            }
        }

        if ("volume" in groups) {
            SectionCard("Volume & channels") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::volumeDown, modifier = Modifier.weight(1f)) {
                        Text("Vol −")
                    }
                    OutlinedButton(onClick = vm::toggleMute, modifier = Modifier.weight(1f)) {
                        Text("Mute")
                    }
                    OutlinedButton(onClick = vm::volumeUp, modifier = Modifier.weight(1f)) {
                        Text("Vol +")
                    }
                    OutlinedButton(onClick = vm::channelDown, modifier = Modifier.weight(1f)) {
                        Text("Ch −")
                    }
                    OutlinedButton(onClick = vm::channelUp, modifier = Modifier.weight(1f)) {
                        Text("Ch +")
                    }
                }
            }
        }

        if ("playback" in groups) {
            SectionCard("Playback") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = vm::rewind, modifier = Modifier.weight(1f)) {
                        Text("⏮")
                    }
                    OutlinedButton(onClick = vm::play, modifier = Modifier.weight(1f)) {
                        Text("▶")
                    }
                    OutlinedButton(onClick = vm::pause, modifier = Modifier.weight(1f)) {
                        Text("⏸")
                    }
                    OutlinedButton(onClick = vm::stop, modifier = Modifier.weight(1f)) {
                        Text("⏹")
                    }
                    OutlinedButton(onClick = vm::fastForward, modifier = Modifier.weight(1f)) {
                        Text("⏭")
                    }
                }
            }
        }

        if ("power" in groups) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = vm::powerOn, modifier = Modifier.weight(1f)) {
                    Text("Power on TV")
                }
                OutlinedButton(onClick = vm::powerOff, modifier = Modifier.weight(1f)) {
                    Text("Power off TV")
                }
            }
        }

        SectionCard("Customize buttons") {
            Text(
                "Choose which button groups appear on this screen. Your choice is remembered.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val options = listOf(
                "system" to "System", "dpad" to "D-pad", "scroll" to "Scroll",
                "volume" to "Volume", "playback" to "Playback", "power" to "Power"
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                options.take(3).forEach { (key, label) ->
                    FilterChip(
                        selected = key in groups,
                        onClick = { vm.toggleGroup(key) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                options.drop(3).forEach { (key, label) ->
                    FilterChip(
                        selected = key in groups,
                        onClick = { vm.toggleGroup(key) },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Text(
            text = "Tip: use the Cursor tab for the air mouse and the Keyboard tab to type into TV search boxes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Normal
        )
    }
}
