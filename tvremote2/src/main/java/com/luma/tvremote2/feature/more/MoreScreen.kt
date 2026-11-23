package com.luma.tvremote2.feature.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.luma.tvremote2.core.moreItems

/** Legacy "More controls" dialog, same items and same order. */
@Composable
fun MoreDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "More controls",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                moreItems.forEach { item ->
                    TextButton(onClick = { onPick(item.route) }, modifier = Modifier.fillMaxWidth()) {
                        Text(item.label, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                    }
                }
            }
        }
    }
}
