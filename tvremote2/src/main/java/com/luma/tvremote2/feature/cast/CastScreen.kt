package com.luma.tvremote2.feature.cast

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.luma.tvremote2.designsystem.LumaButton
import com.luma.tvremote2.designsystem.LumaOutlinedButton
import com.luma.tvremote2.designsystem.LumaTextField
import com.luma.tvremote2.designsystem.LumaTokens
import com.luma.tvremote2.designsystem.SectionCard

@Composable
fun CastScreen(vm: RemoteViewModel, modifier: Modifier = Modifier) {
    val state by vm.ui.collectAsState()
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(LumaTokens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(LumaTokens.SpaceMD)
    ) {
        SectionCard("Cast & mirror") {
            Text(
                "DLNA cast for media URLs. Full screen mirroring uses the TV's built-in Miracast.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            LumaTextField(
                value = state.castUrl,
                onValueChange = vm::onCastUrl,
                label = "Media URL",
                modifier = Modifier.padding(top = LumaTokens.SpaceSM)
            )
            LumaButton(
                text = "Cast media",
                onClick = vm::castMedia,
                modifier = Modifier.fillMaxWidth().padding(top = LumaTokens.SpaceSM)
            )
            LumaOutlinedButton(
                text = "Open screen mirroring",
                onClick = vm::openMirroring,
                modifier = Modifier.fillMaxWidth().padding(top = LumaTokens.SpaceSM)
            )
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
