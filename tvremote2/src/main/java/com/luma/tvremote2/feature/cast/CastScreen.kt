package com.luma.tvremote2.feature.cast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luma.tvremote2.core.RemoteViewModel
import com.luma.tvremote2.designsystem.SectionCard

@Composable
fun CastScreen(vm: RemoteViewModel, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionCard("Cast & mirror") {
            Text(
                "DLNA cast for media URLs. Full screen mirroring uses the TV's built-in Miracast.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = { Text("Media URL") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { vm.reportPending("Cast media") }, modifier = Modifier.fillMaxWidth()) {
                Text("Cast media")
            }
            Button(onClick = { vm.reportPending("Open mirroring") }, modifier = Modifier.fillMaxWidth()) {
                Text("Open screen mirroring")
            }
        }
    }
}
