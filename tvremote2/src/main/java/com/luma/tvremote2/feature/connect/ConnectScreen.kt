package com.luma.tvremote2.feature.connect

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.luma.tvremote2.core.RemoteViewModel
import com.luma.tvremote2.designsystem.LumaButton
import com.luma.tvremote2.designsystem.LumaOutlinedButton
import com.luma.tvremote2.designsystem.LumaTextField
import com.luma.tvremote2.designsystem.LumaTokens
import com.luma.tvremote2.designsystem.SectionCard

@Composable
fun ConnectScreen(
    vm: RemoteViewModel,
    onConnected: () -> Unit,
    onHost: () -> Unit = {}
) {
    val state by vm.ui.collectAsState()

    LaunchedEffect(state.connectedHost) {
        if (state.connectedHost != null) onConnected()
    }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .padding(LumaTokens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(LumaTokens.SpaceMD)
    ) {
        Text(
            "Control your LG TV",
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            softWrap = false
        )
        Text(
            "Same Wi‑Fi. No LG account needed. Pairing happens on the TV.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )

        SectionCard("Before you connect") {
            listOf(
                "1. On the TV: Settings → General → LG Connect Apps → On",
                "2. Keep the phone and TV on the same Wi‑Fi network",
                "3. Accept the pairing prompt that appears on the TV",
                "4. Ports 3000/3001 must be reachable on the LAN"
            ).forEach {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    modifier = Modifier.padding(vertical = LumaTokens.SpaceXS)
                )
            }
        }

        SectionCard("TV address") {
            LumaTextField(
                value = state.hostInput,
                onValueChange = vm::onHostInput,
                label = "TV IP address",
                enabled = !state.isConnecting
            )
            if (state.detectedHost != null) {
                Text(
                    "Found via companion: ${state.detectedHost}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = LumaTokens.SpaceXS),
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
            LumaButton(
                text = if (state.isConnecting) "Connecting…" else "Connect",
                onClick = { vm.connect() },
                enabled = !state.isConnecting,
                modifier = Modifier.fillMaxWidth().padding(top = LumaTokens.SpaceSM)
            )
            Row(
                modifier = Modifier.padding(top = LumaTokens.SpaceSM),
                horizontalArrangement = Arrangement.spacedBy(LumaTokens.SpaceSM)
            ) {
                LumaOutlinedButton(
                    text = "Find TV",
                    onClick = vm::discoverCompanion,
                    modifier = Modifier.weight(1f)
                )
                LumaOutlinedButton(
                    text = "TV app",
                    onClick = { vm.connect(state.detectedHost.orEmpty()) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        SectionCard("Luma Companion 2.0") {
            Text(
                "Use Companion Host when the TV runs Luma Companion 2.0. The TV connects to this phone; no LG account is needed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3
            )
            LumaOutlinedButton(
                text = "Open Companion Host",
                onClick = onHost,
                modifier = Modifier.fillMaxWidth().padding(top = LumaTokens.SpaceSM)
            )
        }

        Text(
            "Luma TV Remote 3.0.0 · ${state.status}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
    }
}
