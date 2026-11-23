package com.luma.tvremote2.feature.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
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
fun KeyboardScreen(vm: RemoteViewModel, modifier: Modifier = Modifier) {
    val state by vm.ui.collectAsState()
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(LumaTokens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(LumaTokens.SpaceMD)
    ) {
        SectionCard("Keyboard") {
            Text(
                text = "Type text and send it to the TV's on-screen keyboard.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
            LumaTextField(
                value = state.keyboardText,
                onValueChange = vm::onKeyboardText,
                label = "Text to send",
                modifier = Modifier.padding(top = LumaTokens.SpaceSM)
            )
            Row(
                modifier = Modifier.padding(top = LumaTokens.SpaceMD),
                horizontalArrangement = Arrangement.spacedBy(LumaTokens.SpaceSM)
            ) {
                LumaButton(
                    text = "Send",
                    onClick = vm::sendKeyboardText,
                    modifier = Modifier.weight(1f),
                    icon = {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(LumaTokens.IconSmall)
                        )
                    }
                )
                LumaOutlinedButton(
                    text = "Del",
                    onClick = vm::backspace,
                    modifier = Modifier.weight(1f),
                    icon = {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = "Backspace",
                            modifier = Modifier.size(LumaTokens.IconSmall)
                        )
                    }
                )
                LumaOutlinedButton(
                    text = "Enter",
                    onClick = vm::enter,
                    modifier = Modifier.weight(1f),
                    icon = {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(LumaTokens.IconSmall)
                        )
                    }
                )
            }
        }
    }
}
