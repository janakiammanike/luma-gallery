package com.luma.tvremote2.feature.calibration

import androidx.compose.foundation.background
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.luma.tvremote2.core.CalibrationStage
import com.luma.tvremote2.core.RemoteViewModel
import com.luma.tvremote2.designsystem.SectionCard
import kotlin.math.roundToInt

/**
 * Guided wizard: stable bias -> four direction checks -> four TV corners.
 * Everything is best-effort; skipping a step leaves the last saved values in
 * place, so the air mouse keeps working with its existing fallback.
 */
@Composable
fun CalibrationScreen(vm: RemoteViewModel, onDone: () -> Unit, modifier: Modifier = Modifier) {
    val state by vm.calibration.collectAsState()

    DisposableEffect(Unit) {
        vm.startCalibration()
        onDispose { vm.stopAirMouse() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SectionCard("Air mouse calibration") {
            Text(
                "Calibrate once for this phone and this TV. The wizard measures the gyroscope bias, checks all four tilt directions, then measures how far the TV pointer travels across the screen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val stages = listOf(
                "1 Still", "2 Directions", "3 TV corners", "4 Done"
            )
            val activeIndex = when (state.stage) {
                CalibrationStage.STILL -> 0
                CalibrationStage.DIRECTIONS -> 1
                CalibrationStage.CORNERS -> 2
                CalibrationStage.DONE -> 3
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                stages.forEachIndexed { index, label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (index == activeIndex) FontWeight.Bold else FontWeight.Normal,
                        color = if (index <= activeIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(6.dp)
                    .background(
                        MaterialTheme.colorScheme.surface,
                        RoundedCornerShape(3.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(state.progress.coerceIn(0f, 1f))
                        .height(6.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(3.dp)
                        )
                )
            }

            Text(
                state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        when (state.stage) {
            CalibrationStage.STILL -> SectionCard("Step 1 · Stability") {
                Text(
                    "Place the phone on a steady surface or hold it comfortably. Keep it completely still for about two seconds while the bias is measured.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = vm::measureStillness,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Start stability check")
                }
                OutlinedButton(
                    onClick = vm::startDirectionChecks,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    Text("Continue to four directions")
                }
                OutlinedButton(
                    onClick = vm::moveToCorners,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Skip · go to TV corners")
                }
            }

            CalibrationStage.DIRECTIONS -> SectionCard("Step 2 · Four directions") {
                val current = state.directions.getOrNull(state.directionIndex)
                if (current != null) {
                    Text(
                        "Tilt the phone towards $current and hold it there. The wizard checks that the TV pointer moves the same way and corrects the axis if needed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = { vm.measureDirection(current) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Text("Measure $current")
                    }
                } else {
                    Button(
                        onClick = vm::moveToCorners,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Continue to TV corners")
                    }
                }
                Text(
                    state.directionResults.entries.joinToString("   ") { "${it.key}: ${it.value}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedButton(
                    onClick = vm::moveToCorners,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Skip directions · go to TV corners")
                }
            }

            CalibrationStage.CORNERS -> SectionCard("Step 3 · TV corners") {
                Text(
                    "Use the touchpad or air mouse to move the TV pointer to each corner in order. Tap Mark at every corner so the app can measure the full screen width and height.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val corner = state.corners.getOrNull(state.cornerIndex)
                if (corner != null) {
                    Text(
                        "${state.cornerIndex + 1}/4 · $corner",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                Button(
                    onClick = vm::markCorner,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    enabled = corner != null
                ) {
                    Text(if (corner != null) "Mark $corner" else "Finish")
                }
                Text(
                    "Measured width: ${state.spanX} · height: ${state.spanY}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = vm::recenter,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Recenter cursor now (edge bump)")
                }
            }

            CalibrationStage.DONE -> SectionCard("Step 4 · Saved") {
                Text(
                    "Width ${state.spanX} · height ${state.spanY}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "Pointer gain ${(state.gainX * 100).roundToInt() / 100f}x horizontal, " +
                        "${(state.gainY * 100).roundToInt() / 100f}x vertical",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Axis signs: X ${state.axisSignX}, Y ${state.axisSignY}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = vm::recenter,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Recenter cursor now")
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Shake phone to recenter",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = state.shakeToCenter,
                        onCheckedChange = vm::setShakeToCenter
                    )
                }
                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Back to Cursor")
                }
            }
        }

        Text(
            text = "If calibration does not feel right, you can repeat it any time. The touchpad and the previous settings keep working throughout.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
