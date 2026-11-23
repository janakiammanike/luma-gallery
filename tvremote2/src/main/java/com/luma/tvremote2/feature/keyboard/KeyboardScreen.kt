package com.luma.tvremote2.feature.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
fun KeyboardScreen(vm: RemoteViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard("Keyboard") {
            Text(
                "Type text and send it to the TV's on-screen keyboard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = vm.ui.value.keyboardText,
                onValueChange = vm::onKeyboardText,
                label = { Text("Text to send") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.reportPending("Send text") }, modifier = Modifier.weight(1f)) {
                    Text("Send")
                }
                Button(onClick = { vm.reportPending("Backspace") }, modifier = Modifier.weight(1f)) {
                    Text("⌫ Backspace")
                }
                Button(onClick = { vm.reportPending("Enter") }, modifier = Modifier.weight(1f)) {
                    Text("Enter ↵")
                }
            }
        }
    }
}
