package com.luma.tvremote2.feature.host

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.luma.tvremote2.designsystem.LumaButton
import com.luma.tvremote2.designsystem.LumaOutlinedButton
import com.luma.tvremote2.designsystem.LumaTextField
import com.luma.tvremote2.designsystem.LumaTokens
import com.luma.tvremote2.designsystem.SectionCard
import com.luma.tvremote2.host.HostViewModel

/**
 * Foreground-only Companion Host screen.
 *
 * This replaces the separate companion-host APK. It keeps the existing LG
 * remote path untouched and only adds the opt-in LAN endpoint used by
 * Luma Companion 2.0.
 */
@Composable
fun HostScreen(vm: HostViewModel = viewModel()) {
    val state by vm.ui.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onPause(owner: LifecycleOwner) {
                vm.stop()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.stop()
        }
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(LumaTokens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(LumaTokens.SpaceMD)
    ) {
        Text(
            "Companion Host",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            softWrap = false
        )
        Text(
            "Opt-in LAN endpoint for Luma Companion 2.0. Keep this screen open while the TV is connected.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3
        )
        Text(
            "No service · no cloud · no TV listener · no pairing key",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            maxLines = 2
        )

        SectionCard("Endpoint") {
            Text(
                state.serverState,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false
            )
            Text(
                state.peer,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2
            )
            LumaTextField(
                value = state.portInput,
                onValueChange = vm::onPortInput,
                label = "Port",
                enabled = !state.running
            )
            Row(
                modifier = Modifier.padding(top = LumaTokens.SpaceSM),
                horizontalArrangement = Arrangement.spacedBy(LumaTokens.SpaceSM)
            ) {
                LumaButton(
                    text = "Start",
                    onClick = vm::start,
                    enabled = !state.running,
                    modifier = Modifier.weight(1f)
                )
                LumaOutlinedButton(
                    text = "Stop",
                    onClick = vm::stop,
                    enabled = state.running,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.padding(top = LumaTokens.SpaceSM),
                horizontalArrangement = Arrangement.spacedBy(LumaTokens.SpaceSM)
            ) {
                LumaButton(
                    text = "Approve TV",
                    onClick = vm::approve,
                    enabled = state.canApprove,
                    modifier = Modifier.weight(1f)
                )
                LumaOutlinedButton(
                    text = "Reject TV",
                    onClick = vm::reject,
                    enabled = state.canApprove,
                    modifier = Modifier.weight(1f)
                )
            }
            if (state.lastReject != "none") {
                Text(
                    "Last rejection: ${state.lastReject}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2
                )
            }
        }

        SectionCard("Commands") {
            LumaButton(
                text = "Refresh TV",
                onClick = vm::refresh,
                enabled = state.canSend,
                modifier = Modifier.fillMaxWidth()
            )
        }

        SectionCard("Browser view") {
            LumaTextField(
                value = state.browserUrlInput,
                onValueChange = vm::onBrowserUrlInput,
                label = "Absolute http(s) address",
                enabled = state.canSend
            )
            Row(
                modifier = Modifier.padding(top = LumaTokens.SpaceSM),
                horizontalArrangement = Arrangement.spacedBy(LumaTokens.SpaceSM)
            ) {
                LumaButton(
                    text = "Open Page",
                    onClick = { vm.openPage(state.browserUrlInput) },
                    enabled = state.canSend,
                    modifier = Modifier.weight(1f)
                )
                LumaOutlinedButton(
                    text = "Close Page",
                    onClick = vm::closePage,
                    enabled = state.canSend,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "Only absolute http(s) URLs without credentials are sent. No URL is stored or reported.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )
        }

        SectionCard("Activity") {
            Text(
                state.activityLines.joinToString("\n"),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 12
            )
        }

        Text(
            "Luma TV Remote 3.0.0 · Host mode",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}
