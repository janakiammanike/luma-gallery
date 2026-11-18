package com.luma.tvremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

private data class ButtonGroup(val id: String, val label: String)

private val GROUPS = listOf(
    ButtonGroup("dpad", "D-pad & system keys"),
    ButtonGroup("system", "Home / Back / Menu"),
    ButtonGroup("volume", "Volume & channels"),
    ButtonGroup("playback", "Playback"),
    ButtonGroup("power", "Power")
)

@Composable
fun RemoteTab(vm: RemoteViewModel) {
    val state by vm.ui.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = vm::toggleCustomizing,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (state.customizing) "Done customizing" else "Customize buttons")
            }
            if (state.customizing) {
                OutlinedButton(onClick = vm::resetLayout, modifier = Modifier.weight(1f)) {
                    Text("Reset")
                }
            }
        }

        if (state.customizing) {
            SectionCard("Visible button groups") {
                GROUPS.forEach { group ->
                    val enabled = group.id in state.visibleGroups
                    OutlinedButton(
                        onClick = { vm.toggleGroup(group.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("${if (enabled) "✓" else "○"}  ${group.label}")
                    }
                }
                Text(
                    "Changes are saved for ${state.connectedHost ?: "this TV"} only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if ("dpad" in state.visibleGroups && !state.customizing) {
            Card(
                modifier = Modifier.fillMaxWidth(0.9f),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if ("system" in state.visibleGroups) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallKey("Home") { vm.pressButton("HOME") }
                            SmallKey("Back") { vm.pressButton("BACK") }
                            SmallKey("Menu") { vm.pressButton("MENU") }
                            SmallKey("Exit") { vm.pressButton("EXIT") }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Key("▲", Modifier.size(64.dp)) { vm.pressButton("UP") }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Key("◀", Modifier.size(64.dp)) { vm.pressButton("LEFT") }
                            Key(
                                "OK",
                                Modifier
                                    .size(72.dp)
                                    .padding(4.dp),
                                primary = true
                            ) { vm.pressButton("ENTER") }
                            Key("▶", Modifier.size(64.dp)) { vm.pressButton("RIGHT") }
                        }
                        Key("▼", Modifier.size(64.dp)) { vm.pressButton("DOWN") }
                    }
                }
            }
        }

        if ("volume" in state.visibleGroups && !state.customizing) {
            SectionCard("Volume & channels") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallKey("Vol −") { vm.volumeDown() }
                    SmallKey("Mute") { vm.toggleMute() }
                    SmallKey("Vol +") { vm.volumeUp() }
                    SmallKey("Ch −") { vm.channelDown() }
                    SmallKey("Ch +") { vm.channelUp() }
                }
            }
        }

        if ("playback" in state.visibleGroups && !state.customizing) {
            SectionCard("Playback") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallKey("⏮") { vm.rewind() }
                    SmallKey("▶") { vm.play() }
                    SmallKey("⏸") { vm.pause() }
                    SmallKey("⏹") { vm.stop() }
                    SmallKey("⏭") { vm.fastForward() }
                }
            }
        }

        if ("power" in state.visibleGroups && !state.customizing) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = vm::powerOn) {
                    Text("Power on TV")
                }
                OutlinedButton(onClick = { vm.powerOff() }) {
                    Text("Power off TV")
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = "Tip: use the Cursor tab for mouse control and the Keyboard tab to type into TV search boxes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun Key(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = if (primary) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface
            )
        }
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SmallKey(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = ButtonDefaults.TextButtonContentPadding
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}
