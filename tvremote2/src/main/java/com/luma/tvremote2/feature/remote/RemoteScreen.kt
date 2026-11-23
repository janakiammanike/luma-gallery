package com.luma.tvremote2.feature.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luma.tvremote2.core.RemoteViewModel
import com.luma.tvremote2.designsystem.SectionCard

@Composable
fun RemoteScreen(vm: RemoteViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard("System") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("⌂  Home", "◀  Back", "⊞  Recent", "☰  Menu").forEach {
                    OutlinedButton(onClick = { vm.reportPending(it) }, modifier = Modifier.weight(1f)) {
                        Text(it)
                    }
                }
            }
        }

        SectionCard("D-pad") {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Button(onClick = { vm.reportPending("Up") }) { Text("▲") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.reportPending("Left") }) { Text("◀") }
                    Button(onClick = { vm.reportPending("OK") }) { Text("OK") }
                    Button(onClick = { vm.reportPending("Right") }) { Text("▶") }
                }
                Button(onClick = { vm.reportPending("Down") }) { Text("▼") }
            }
        }

        SectionCard("Scroll") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("◀ Left", "▲ Up", "▼ Down", "Right ▶").forEach {
                    OutlinedButton(onClick = { vm.reportPending(it) }, modifier = Modifier.weight(1f)) {
                        Text(it)
                    }
                }
            }
        }

        SectionCard("Volume & channels") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Vol −", "Mute", "Vol +", "Ch −", "Ch +").forEach {
                    OutlinedButton(onClick = { vm.reportPending(it) }, modifier = Modifier.weight(1f)) {
                        Text(it)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Playback", "Power on TV", "Power off TV").forEach {
                    OutlinedButton(onClick = { vm.reportPending(it) }, modifier = Modifier.weight(1f)) {
                        Text(it)
                    }
                }
            }
        }

        Text(
            text = "Customize buttons",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
