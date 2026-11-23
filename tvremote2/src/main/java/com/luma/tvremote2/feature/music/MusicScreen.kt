package com.luma.tvremote2.feature.music

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luma.tvremote2.core.RemoteViewModel
import com.luma.tvremote2.designsystem.SectionCard

@Composable
fun MusicScreen(vm: RemoteViewModel, modifier: Modifier = Modifier) {
    val state by vm.ui.collectAsState()
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard("Music to TV") {
            Text(
                "Sends music apps to the TV. Phone audio mirroring is an OS/DRM limit.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = state.musicQuery,
                onValueChange = vm::onMusicQuery,
                label = { Text("Track or playlist") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = vm::sendMusic, modifier = Modifier.fillMaxWidth()) {
                Text("Send to TV")
            }
            Text("Now playing: —", style = MaterialTheme.typography.bodySmall)
        }
    }
}
