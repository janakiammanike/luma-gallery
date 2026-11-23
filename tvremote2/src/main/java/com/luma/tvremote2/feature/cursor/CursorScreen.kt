package com.luma.tvremote2.feature.cursor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.luma.tvremote2.core.RemoteViewModel
import com.luma.tvremote2.designsystem.SectionCard

@Composable
fun CursorScreen(vm: RemoteViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard("Air mouse") {
            Text(
                "Move the phone left / right / up / down to point. Phone roll is ignored.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Air mouse recentred · Game rotation vector",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.reportPending("Recentre") }, modifier = Modifier.weight(1f)) {
                    Text("Recentre")
                }
                OutlinedButton(onClick = { vm.reportPending("Air click") }, modifier = Modifier.weight(1f)) {
                    Text("Air click")
                }
            }
            Text("Sensitivity 1.0x", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.reportPending("Invert X") }, modifier = Modifier.weight(1f)) {
                    Text("Invert X")
                }
                OutlinedButton(onClick = { vm.reportPending("Invert Y") }, modifier = Modifier.weight(1f)) {
                    Text("Invert Y")
                }
            }
            Text(
                "Air mouse stops when you leave this tab.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard("Touchpad") {
            Text(
                "Drag to move the TV cursor. Tap to click.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.material3.Surface(
                modifier = Modifier.fillMaxWidth().height(160.dp).padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(
                    "Drag here",
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(Alignment.CenterVertically),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.reportPending("Click") }, modifier = Modifier.weight(1f)) { Text("Click") }
            }
        }

        SectionCard("Scroll") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("▲  Scroll up", "◀ Left", "Right ▶", "▼  Scroll down").forEach {
                    OutlinedButton(onClick = { vm.reportPending(it) }, modifier = Modifier.weight(1f)) {
                        Text(it)
                    }
                }
            }
        }
    }
}
