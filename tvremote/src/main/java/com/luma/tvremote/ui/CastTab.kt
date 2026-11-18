package com.luma.tvremote.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun CastTab(vm: RemoteViewModel) {
    val state by vm.ui.collectAsState()
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) vm.castMedia(uri) }

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
                Text("Screen mirroring & casting", fontWeight = FontWeight.SemiBold)
                Text(
                    "Cast a photo or video from this phone to the TV. The file is served over your Wi‑Fi LAN — no cloud upload.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(
                    onClick = { picker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cast a photo")
                }
                Button(
                    onClick = { picker.launch("video/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cast a video")
                }
                state.castUrl?.let { url ->
                    Text("Now playing: $url", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = vm::stopCast, modifier = Modifier.fillMaxWidth()) {
                        Text("Stop casting")
                    }
                }
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
                Text("TV's built-in mirroring", fontWeight = FontWeight.SemiBold)
                Text(
                    "LG Screen Mirroring (Miracast) runs on the TV. Open the receiver to pair compatible phones.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = vm::openScreenMirroring, modifier = Modifier.fillMaxWidth()) {
                    Text("Open Screen Mirroring on TV")
                }
            }
        }

        Text(
            "Full phone-screen mirroring and LG Magic Tap need system-level Wi‑Fi Direct / NFC APIs that a regular Android app cannot use. This tab casts media files over LAN instead.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
