package com.luma.tvremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
fun MusicTab(vm: RemoteViewModel) {
    val state by vm.ui.collectAsState()
    val track = state.nowPlaying

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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Music on phone → TV", fontWeight = FontWeight.SemiBold)
                Text(
                    "Playing a song in Spotify, YouTube Music or another app? Send it to the TV in one tap — the app opens the song's search on the TV so you can play it on the big screen.",
                    style = MaterialTheme.typography.bodyMedium
                )

                when {
                    !state.mediaAccessGranted -> {
                        Text(
                            "Notification access is off. Enable it in Settings so Luma can see the song now playing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    track == null -> {
                        Text(
                            "No music detected yet. Start a song on your phone, then come back here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    else -> {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(track.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            if (track.artist.isNotBlank()) {
                                Text(track.artist, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Button(onClick = vm::sendMusicToTv, modifier = Modifier.fillMaxWidth()) {
                            Text("▶  Play this on TV")
                        }
                    }
                }
                OutlinedButton(onClick = vm::refreshMediaAccess, modifier = Modifier.fillMaxWidth()) {
                    Text("Refresh song status")
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
                Text("How it works", fontWeight = FontWeight.SemiBold)
                Text(
                    "Android does not let one app stream another app's protected audio (Spotify / YouTube Music use DRM), so this is an honest hand-off: Luma reads the track title and opens the matching TV app's search. Pick the song there to play it on the TV.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
