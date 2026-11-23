package com.luma.tvremote2.feature.cursor

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luma.tvremote2.core.RemoteViewModel
import com.luma.tvremote2.designsystem.SectionCard
import kotlin.math.roundToInt

@Composable
fun CursorScreen(vm: RemoteViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val touchSlop = remember { ViewConfiguration.get(context).scaledTouchSlop }
    val state by vm.ui.collectAsState()

    // Listen to the motion sensor only while this tab is visible and the toggle
    // is on. The touchpad below always works, even with no sensor available.
    DisposableEffect(state.airMouseEnabled, state.connectedHost) {
        if (state.airMouseEnabled && state.connectedHost != null) {
            vm.startAirMouse()
        } else {
            vm.stopAirMouse()
        }
        onDispose { vm.stopAirMouse() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard("Air mouse") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Motion pointer", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Move the phone left / right / up / down to point. Phone roll is ignored.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = state.airMouseEnabled,
                    enabled = state.airMouseAvailable,
                    onCheckedChange = vm::setAirMouseEnabled
                )
            }

            state.airMouseSensor?.let { sensor ->
                Text(
                    "Sensor: $sensor",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!state.airMouseAvailable) {
                Text(
                    "No motion sensor was found on this phone. The touchpad still works normally.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            state.airMouseStatus?.let { status ->
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = vm::recalibrateAirMouse,
                    enabled = state.airMouseEnabled,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Calibrate")
                }
                Button(
                    onClick = vm::clickPointer,
                    enabled = state.airMouseEnabled
                ) {
                    Text("Air click")
                }
            }

            Text(
                "Sensitivity ${(state.airMouseSensitivity * 10).roundToInt() / 10f}x",
                style = MaterialTheme.typography.bodySmall
            )
            Slider(
                value = state.airMouseSensitivity,
                onValueChange = vm::setAirMouseSensitivity,
                valueRange = 0.25f..3f,
                steps = 10,
                enabled = state.airMouseEnabled
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Invert X", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Switch(
                    checked = state.airMouseInvertX,
                    enabled = state.airMouseEnabled,
                    onCheckedChange = vm::setAirMouseInvertX
                )
                Text("Invert Y", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Switch(
                    checked = state.airMouseInvertY,
                    enabled = state.airMouseEnabled,
                    onCheckedChange = vm::setAirMouseInvertY
                )
            }

            Text(
                "Calibrate once while holding the phone comfortably still. Bias is tracked while the phone is steady, so the pointer does not drift. The sensor stops when you leave this tab.",
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(vertical = 8.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(20.dp)
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        RoundedCornerShape(20.dp)
                    )
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var previous = down.position
                            var total = Offset.Zero
                            var moved = false
                            var pressed = true

                            while (pressed) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
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

        SectionCard("Scroll") {
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
                OutlinedButton(
                    onClick = { vm.scroll(0, -160) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("▲  Scroll up")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { vm.scroll(-160, 0) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("◀ Left")
                    }
                    Button(onClick = vm::clickPointer) { Text("Click") }
                    OutlinedButton(
                        onClick = { vm.scroll(160, 0) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Right ▶")
                    }
                }
                OutlinedButton(
                    onClick = { vm.scroll(0, 160) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("▼  Scroll down")
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
