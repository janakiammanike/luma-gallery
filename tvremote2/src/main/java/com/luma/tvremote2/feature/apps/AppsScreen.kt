package com.luma.tvremote2.feature.apps

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
import androidx.compose.ui.unit.dp
import com.luma.tvremote2.core.RemoteViewModel
import com.luma.tvremote2.designsystem.SectionCard

@Composable
fun AppsScreen(vm: RemoteViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard("Apps") {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                label = { Text("Filter apps") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.reportPending("Search TV") }, modifier = Modifier.weight(1f)) {
                    Text("Search TV")
                }
                OutlinedButton(onClick = { vm.reportPending("Reload") }, modifier = Modifier.weight(1f)) {
                    Text("Reload")
                }
            }
            Text(
                "0 app(s)\nNo apps returned by the TV. Tap Reload.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(onClick = { vm.reportPending("Launch app") }, modifier = Modifier.fillMaxWidth()) {
            Text("Launch app")
        }
    }
}
