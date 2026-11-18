package com.luma.tvremote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ConnectScreen(
    state: RemoteUiState,
    modifier: Modifier = Modifier,
    onHostInput: (String) -> Unit,
    onConnect: () -> Unit,
    onScan: () -> Unit,
    onPickHost: (String) -> Unit,
    onForget: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onWake: (String) -> Unit
) {
    val known = (state.savedHosts + state.detectedHosts).toList().distinct()
        .sortedWith(compareBy({ host -> host !in state.favorites }, { host -> host }))

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Control your LG TV",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Same Wi‑Fi. No LG account needed. Pairing happens on the TV.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Before you connect", fontWeight = FontWeight.SemiBold)
                Text("1. On the TV: Settings → General → LG Connect Apps → On")
                Text("2. Keep the phone and TV on the same Wi‑Fi network")
                Text("3. Accept the pairing prompt that appears on the TV")
                Text("4. Ports 3000/3001 must be reachable on the LAN", style = MaterialTheme.typography.bodySmall)
            }
        }

        OutlinedTextField(
            value = state.hostInput,
            onValueChange = onHostInput,
            label = { Text("TV IP address") },
            placeholder = { Text("192.168.1.25") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onConnect,
                enabled = !state.isConnecting,
                modifier = Modifier.weight(1f)
            ) {
                if (state.isConnecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Connect")
                }
            }
            OutlinedButton(
                onClick = onScan,
                enabled = !state.isScanning,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (state.isScanning) "Scanning…" else "Scan network")
            }
        }

        if (known.isNotEmpty()) {
            Text("Saved or found devices", fontWeight = FontWeight.SemiBold)
            known.forEach { host ->
                val favorite = host in state.favorites
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPickHost(host) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (favorite) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(host, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { onToggleFavorite(host) }) {
                                Text(if (favorite) "★" else "☆")
                            }
                            TextButton(onClick = { onWake(host) }) {
                                Text("Wake")
                            }
                            TextButton(onClick = { onForget(host) }) {
                                Text("Forget")
                            }
                        }
                        state.hostMacs[host]?.let { mac ->
                            Text(
                                "MAC $mac · Wake-on-LAN ready",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Tip: scanning checks the local /24 network for ports 3000 and 3001. Favorites stay at the top. Wake needs the TV's network standby enabled; connect once to learn its MAC address.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
