package com.luma.tvremote2.feature.inputs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.luma.tvremote2.core.RemoteViewModel
import com.luma.tvremote2.designsystem.LumaOutlinedButton
import com.luma.tvremote2.designsystem.LumaTokens
import com.luma.tvremote2.designsystem.SectionCard

@Composable
fun InputsScreen(vm: RemoteViewModel, modifier: Modifier = Modifier) {
    val state by vm.ui.collectAsState()

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(LumaTokens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(LumaTokens.SpaceMD)
    ) {
        SectionCard("Inputs & sound") {
            Text(
                "Sound output",
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                softWrap = false
            )
            Text(
                "TV speaker",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false
            )
            Text(
                "Volume ${state.audioVolume ?: "—"}${if (state.audioMuted) " · Muted" else ""}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = LumaTokens.SpaceSM),
                maxLines = 1,
                softWrap = false
            )
            Row(
                modifier = Modifier.padding(top = LumaTokens.SpaceSM),
                horizontalArrangement = Arrangement.spacedBy(LumaTokens.SpaceSM)
            ) {
                LumaOutlinedButton(
                    text = "Vol −",
                    onClick = vm::volumeDown,
                    modifier = Modifier.weight(1f)
                )
                LumaOutlinedButton(
                    text = "Mute",
                    onClick = vm::toggleMute,
                    modifier = Modifier.weight(1f)
                )
                LumaOutlinedButton(
                    text = "Vol +",
                    onClick = vm::volumeUp,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        SectionCard("Inputs") {
            listOf(
                "HDMI 1" to "com.webos.app.hdmi1",
                "HDMI 2" to "com.webos.app.hdmi2",
                "HDMI 3" to "com.webos.app.hdmi3",
                "Live TV" to "com.webos.app.livetv"
            ).forEach { (label, appId) ->
                LumaOutlinedButton(
                    text = label,
                    onClick = { vm.selectInput(appId) },
                    modifier = Modifier.fillMaxWidth().padding(top = LumaTokens.SpaceXS)
                )
            }
            state.message?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = LumaTokens.SpaceSM),
                    maxLines = 2
                )
            }
        }
    }
}
