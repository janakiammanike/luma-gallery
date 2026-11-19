package com.luma.tvremote.ui

import android.view.ViewConfiguration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun CursorTab(vm: RemoteViewModel) {
    val context = LocalContext.current
    val touchSlop = remember { ViewConfiguration.get(context).scaledTouchSlop }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        NavBar(vm)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Touchpad", fontWeight = FontWeight.SemiBold)
                Text(
                    "Drag to move the TV cursor. Tap to click.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(20.dp)
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                            RoundedCornerShape(20.dp)
                        )
                        .pointerInput(vm) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var previous = down.position
                                var total = Offset.Zero
                                var moved = false
                                var pressed = true

                                while (pressed) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: break
                                    val delta = change.position - previous
                                    previous = change.position
                                    total += delta
                                    if (total.getDistance() > touchSlop) moved = true
                                    if (moved) {
                                        vm.movePointer(delta.x.roundToInt(), delta.y.roundToInt())
                                    }
                                    change.consume()
                                    pressed = event.changes.any { it.pressed }
                                }
                                if (!moved) vm.clickPointer()
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Drag here",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Scroll", fontWeight = FontWeight.SemiBold)
                Text(
                    "Scroll up / down / left / right in webOS lists and pages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { vm.scroll(0, -160) }, modifier = Modifier.fillMaxWidth()) {
                        Text("▲  Scroll up")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = { vm.scroll(-160, 0) }, modifier = Modifier.weight(1f)) {
                            Text("◀ Left")
                        }
                        Button(onClick = { vm.clickPointer() }) { Text("Click") }
                        OutlinedButton(onClick = { vm.scroll(160, 0) }, modifier = Modifier.weight(1f)) {
                            Text("Right ▶")
                        }
                    }
                    OutlinedButton(onClick = { vm.scroll(0, 160) }, modifier = Modifier.fillMaxWidth()) {
                        Text("▼  Scroll down")
                    }
                }
            }
        }

        Text(
            text = "The pointer works in webOS apps and browsers that accept mouse input. Some apps draw their own cursor and ignore it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
