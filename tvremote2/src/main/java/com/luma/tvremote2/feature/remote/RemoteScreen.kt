package com.luma.tvremote2.feature.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luma.tvremote2.core.RemoteViewModel
import com.luma.tvremote2.designsystem.LumaAdaptiveRow
import com.luma.tvremote2.designsystem.LumaDPad
import com.luma.tvremote2.designsystem.LumaRemoteButton
import com.luma.tvremote2.designsystem.LumaTokens
import com.luma.tvremote2.designsystem.SectionCard

@Composable
fun RemoteScreen(vm: RemoteViewModel, modifier: Modifier = Modifier) {
    val state by vm.ui.collectAsState()
    val groups = state.visibleGroups

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(LumaTokens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(LumaTokens.SpaceMD)
    ) {
        // D-pad is the primary control; everything else is arranged around it.
        SectionCard("D-pad") {
            LumaDPad(
                onDirection = { vm.button(it) },
                onEnter = { vm.button("ENTER") }
            )
        }

        if (state.audioVolume != null) {
            Text(
                text = "Volume: ${state.audioVolume}${if (state.audioMuted) " · Muted" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }

        if ("system" in groups) {
            SectionCard("System") {
                LumaAdaptiveRow(
                    items = listOf(
                        "Home" to "HOME",
                        "Back" to "BACK",
                        "Recent" to "RECENT",
                        "Menu" to "MENU"
                    ),
                    minItemWidth = 80.dp
                ) { (label, name), itemModifier ->
                    LumaRemoteButton(label, { vm.button(name) }, itemModifier)
                }
            }
        }

        if ("scroll" in groups) {
            SectionCard("Scroll") {
                LumaAdaptiveRow(
                    items = listOf(
                        Triple("◀ Left", -200, 0),
                        Triple("▲ Up", 0, -200),
                        Triple("▼ Down", 0, 200),
                        Triple("Right ▶", 200, 0)
                    ),
                    minItemWidth = 80.dp
                ) { (label, dx, dy), itemModifier ->
                    LumaRemoteButton(
                        label = label,
                        onClick = { vm.scroll(dx, dy) },
                        modifier = itemModifier
                    )
                }
            }
        }

        if ("volume" in groups) {
            SectionCard("Volume & channels") {
                LumaAdaptiveRow(
                    items = listOf(
                        "Vol −" to 0,
                        "Mute" to 1,
                        "Vol +" to 2,
                        "Ch −" to 3,
                        "Ch +" to 4
                    ),
                    minItemWidth = 72.dp
                ) { (label, action), itemModifier ->
                    LumaRemoteButton(
                        label = label,
                        onClick = {
                            when (action) {
                                0 -> vm.volumeDown()
                                1 -> vm.toggleMute()
                                2 -> vm.volumeUp()
                                3 -> vm.channelDown()
                                else -> vm.channelUp()
                            }
                        },
                        modifier = itemModifier
                    )
                }
            }
        }

        if ("playback" in groups) {
            SectionCard("Playback") {
                LumaAdaptiveRow(
                    items = listOf("⏮", "▶", "⏸", "⏹", "⏭"),
                    minItemWidth = 64.dp
                ) { label, itemModifier ->
                    LumaRemoteButton(
                        label = label,
                        onClick = {
                            when (label) {
                                "⏮" -> vm.rewind()
                                "▶" -> vm.play()
                                "⏸" -> vm.pause()
                                "⏹" -> vm.stop()
                                else -> vm.fastForward()
                            }
                        },
                        modifier = itemModifier
                    )
                }
            }
        }

        if ("power" in groups) {
            LumaAdaptiveRow(
                items = listOf("Power on TV" to true, "Power off TV" to false),
                minItemWidth = 120.dp,
                modifier = Modifier.fillMaxWidth()
            ) { (label, on), itemModifier ->
                LumaRemoteButton(
                    label = label,
                    onClick = { if (on) vm.powerOn() else vm.powerOff() },
                    modifier = itemModifier
                )
            }
        }

        SectionCard("Customize buttons") {
            Text(
                "Choose which button groups appear on this screen. Your choice is remembered.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LumaAdaptiveRow(
                items = listOf(
                    "system" to "System",
                    "dpad" to "D-pad",
                    "scroll" to "Scroll",
                    "volume" to "Volume",
                    "playback" to "Playback",
                    "power" to "Power"
                ),
                minItemWidth = 104.dp,
                modifier = Modifier.padding(top = LumaTokens.SpaceSM)
            ) { (key, label), itemModifier ->
                FilterChip(
                    selected = key in groups,
                    onClick = { vm.toggleGroup(key) },
                    label = {
                        Text(label, maxLines = 1, softWrap = false)
                    },
                    modifier = itemModifier.fillMaxWidth()
                )
            }
        }

        Text(
            text = "Tip: use the Cursor tab for the air mouse and the Keyboard tab to type into TV search boxes.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3
        )
    }
}
