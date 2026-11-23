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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luma.tvremote2.core.RemoteViewModel
import com.luma.tvremote2.designsystem.LumaButton
import com.luma.tvremote2.designsystem.LumaOutlinedButton
import com.luma.tvremote2.designsystem.LumaTokens
import com.luma.tvremote2.designsystem.SectionCard
import kotlin.math.roundToInt

@Composable
fun CursorScreen(
    vm: RemoteViewModel,
    onCalibration: () -> Unit,
    modifier: Modifier = Modifier
) {
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

    val touchpadHeight = if (LocalConfiguration.current.screenWidthDp < 360) {
        LumaTokens.TouchpadHeightCompact
    } else {
        LumaTokens.TouchpadHeight
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(LumaTokens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(LumaTokens.SpaceMD)
    ) {
        SectionCard("Air mouse") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Motion pointer",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        "Move the phone to point. Phone roll is ignored.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                    )
                }
                Switch(
                    checked = state.airMouseEnabled,
                    enabled = state.airMouseAvailable,
                    onCheckedChange = vm::setAirMouseEnabled
                )
            }

            Row(
                modifier = Modifier.padding(top = LumaTokens.SpaceSM),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(LumaTokens.SpaceSM)
            ) {
                val active = state.airMouseEnabled && state.airMouseAvailable
                Surface(
                    shape = RoundedCornerShape(LumaTokens.RadiusPill),
                    color = if (active) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ) {
                    Text(
                        text = if (active) "Active" else "Off",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (active) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(
                            horizontal = LumaTokens.SpaceSM,
                            vertical = LumaTokens.SpaceXS
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
                state.airMouseSensor?.let { sensor ->
                    Text(
                        text = sensor,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            state.airMouseStatus?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2
                )
            }
            if (!state.airMouseAvailable) {
                Text(
                    text = "No motion sensor found. The touchpad still works normally.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = LumaTokens.SpaceSM),
                horizontalArrangement = Arrangement.spacedBy(LumaTokens.SpaceSM)
            ) {
                LumaOutlinedButton(
                    text = "Calibrate",
                    onClick = vm::recalibrateAirMouse,
                    enabled = state.airMouseEnabled,
                    modifier = Modifier.weight(1f)
                )
                LumaButton(
                    text = "Air click",
                    onClick = vm::clickPointer,
                    enabled = state.airMouseEnabled,
                    modifier = Modifier.weight(1f)
                )
            }
            LumaButton(
                text = "Open calibration wizard",
                onClick = onCalibration,
                modifier = Modifier.fillMaxWidth().padding(top = LumaTokens.SpaceSM)
            )

            Text(
                text = "Sensitivity ${(state.airMouseSensitivity * 10).roundToInt() / 10f}x",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = LumaTokens.SpaceSM),
                maxLines = 1,
                softWrap = false
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
                horizontalArrangement = Arrangement.spacedBy(LumaTokens.SpaceSM)
            ) {
                Text(
                    "Invert X",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    softWrap = false
                )
                Switch(
                    checked = state.airMouseInvertX,
                    enabled = state.airMouseEnabled,
                    onCheckedChange = vm::setAirMouseInvertX
                )
                Text(
                    "Invert Y",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    softWrap = false
                )
                Switch(
                    checked = state.airMouseInvertY,
                    enabled = state.airMouseEnabled,
                    onCheckedChange = vm::setAirMouseInvertY
                )
            }

            Text(
                text = "Bias is tracked while the phone is steady, so the pointer does not drift.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }

        SectionCard("Touchpad") {
            Text(
                text = "Drag to move the TV cursor. Tap to click. Use Back to return.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(touchpadHeight)
                    .padding(vertical = LumaTokens.SpaceSM)
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
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    softWrap = false
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = LumaTokens.SpaceSM),
                horizontalArrangement = Arrangement.spacedBy(LumaTokens.SpaceSM)
            ) {
                LumaOutlinedButton(
                    text = "◀ Back",
                    onClick = { vm.button("BACK") },
                    modifier = Modifier.weight(1f)
                )
                LumaButton(
                    text = "OK / Click",
                    onClick = vm::clickPointer,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        SectionCard("Scroll") {
            Text(
                text = "Scroll webOS lists and pages.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = LumaTokens.SpaceSM),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(LumaTokens.SpaceSM)
            ) {
                LumaOutlinedButton(
                    text = "▲  Scroll up",
                    onClick = { vm.scroll(0, -160) },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(LumaTokens.SpaceSM)
                ) {
                    LumaOutlinedButton(
                        text = "◀ Left",
                        onClick = { vm.scroll(-160, 0) },
                        modifier = Modifier.weight(1f)
                    )
                    LumaButton(
                        text = "Click",
                        onClick = vm::clickPointer,
                        modifier = Modifier.weight(1f)
                    )
                    LumaOutlinedButton(
                        text = "Right ▶",
                        onClick = { vm.scroll(160, 0) },
                        modifier = Modifier.weight(1f)
                    )
                }
                LumaOutlinedButton(
                    text = "▼  Scroll down",
                    onClick = { vm.scroll(0, 160) },
                    modifier = Modifier.fillMaxWidth()
                )
                LumaOutlinedButton(
                    text = "Recenter cursor (edge bump)",
                    onClick = vm::recenter,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Text(
            text = "The pointer works in webOS apps and browsers that accept mouse input. Some apps draw their own cursor and ignore it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3
        )
    }
}
