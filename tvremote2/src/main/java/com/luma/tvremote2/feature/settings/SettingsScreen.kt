package com.luma.tvremote2.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import com.luma.tvremote2.core.RemoteViewModel
import com.luma.tvremote2.designsystem.LumaOutlinedButton
import com.luma.tvremote2.designsystem.LumaTokens

/** Legacy Settings sheet, reduced to the toggles that exist in Phase 1. */
@Composable
fun SettingsDialog(vm: RemoteViewModel, onDismiss: () -> Unit) {
    val state = vm.ui.value
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(LumaTokens.RadiusLG),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Column(modifier = Modifier.padding(LumaTokens.ScreenPadding)) {
                Text(
                    "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    softWrap = false
                )
                ToggleRow("Dark theme", state.darkTheme, vm::setDarkTheme)
                ToggleRow("Haptics", state.haptics, vm::setHaptics)
                LumaOutlinedButton(
                    text = "Close",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().padding(top = LumaTokens.SpaceSM)
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = LumaTokens.SpaceXS),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            softWrap = false
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
