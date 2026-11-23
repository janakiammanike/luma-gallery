package com.luma.tvremote2.feature.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luma.tvremote2.core.RemoteViewModel
import com.luma.tvremote2.designsystem.SectionCard

@Composable
fun ConnectScreen(vm: RemoteViewModel, modifier: Modifier = Modifier) {
    val state = vm.ui.value
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Control your LG TV", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Same Wi‑Fi. No LG account needed. Pairing happens on the TV.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Wi‑Fi connected  ▮▮▮▮▯", style = MaterialTheme.typography.bodySmall)

        SectionCard("Before you connect") {
            listOf(
                "1. On the TV: Settings → General → LG Connect Apps → On",
                "2. Keep the phone and TV on the same Wi‑Fi network",
                "3. Accept the pairing prompt that appears on the TV",
                "4. Ports 3000/3001 must be reachable on the LAN"
            ).forEach {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }

        SectionCard("TV address") {
            OutlinedTextField(
                value = state.hostInput,
                onValueChange = vm::onHostInput,
                label = { Text("TV IP address") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(onClick = { vm.reportPending("Connect") }, modifier = Modifier.fillMaxWidth()) {
                Text("Connect")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.reportPending("Scan network") }, modifier = Modifier.weight(1f)) {
                    Text("Scan network")
                }
                OutlinedButton(onClick = { vm.reportPending("TV app") }, modifier = Modifier.weight(1f)) {
                    Text("TV app")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.reportPending("Bluetooth") }, modifier = Modifier.weight(1f)) {
                    Text("Bluetooth")
                }
                OutlinedButton(onClick = { vm.reportPending("mDNS") }, modifier = Modifier.weight(1f)) {
                    Text("mDNS")
                }
            }
        }

        Text(
            "Luma TV Remote 2.0 · ${state.status}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
