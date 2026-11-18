package com.luma.tvremote.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel

private data class NavItem(val key: String, val label: String, val glyph: String)

private val NAV_ITEMS = listOf(
    NavItem("remote", "Remote", "◉"),
    NavItem("cursor", "Cursor", "✥"),
    NavItem("keyboard", "Keyboard", "⌨"),
    NavItem("apps", "Apps", "▦"),
    NavItem("more", "More", "⋯")
)

private val MORE_ITEMS = listOf(
    "inputs" to "Inputs & sound",
    "search" to "Content search",
    "music" to "Music to TV",
    "cast" to "Cast & mirror"
)

@Composable
fun RemoteApp(vm: RemoteViewModel = viewModel()) {
    val state by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var screen by remember { mutableStateOf("remote") }
    var showMore by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Luma TV Remote", fontWeight = FontWeight.SemiBold)
                        Text(
                            text = state.status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { showSettings = true }) { Text("Settings") }
                    if (state.connectedHost != null) {
                        TextButton(onClick = { vm.disconnect() }) {
                            Text("Disconnect")
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (state.connectedHost != null) {
                NavigationBar {
                    NAV_ITEMS.forEach { item ->
                        NavigationBarItem(
                            selected = item.key == screen ||
                                (item.key == "more" && MORE_ITEMS.any { it.first == screen }),
                            onClick = {
                                if (item.key == "more") showMore = true else screen = item.key
                            },
                            icon = { Text(item.glyph) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (state.connectedHost == null) {
            ConnectScreen(
                state = state,
                modifier = Modifier.padding(padding),
                onHostInput = vm::onHostInput,
                onConnect = { vm.connect() },
                onScan = vm::scan,
                onDiscover = vm::discoverMdns,
                onPickHost = { vm.connect(it) },
                onForget = vm::forgetHost,
                onToggleFavorite = vm::toggleFavorite,
                onWake = vm::wakeHost
            )
        } else {
            Column(modifier = Modifier.padding(padding)) {
                when (screen) {
                    "remote" -> RemoteTab(vm)
                    "cursor" -> CursorTab(vm)
                    "keyboard" -> KeyboardTab(vm)
                    "apps" -> AppsTab(vm)
                    "inputs" -> InputsTab(vm)
                    "search" -> SearchTab(vm)
                    "music" -> MusicTab(vm)
                    "cast" -> CastTab(vm)
                }
            }
        }
    }

    if (showMore) {
        Dialog(onDismissRequest = { showMore = false }) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "More controls",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    MORE_ITEMS.forEach { (key, label) ->
                        TextButton(
                            onClick = { screen = key; showMore = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(label, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        SettingsSheet(state = state, vm = vm, onDismiss = { showSettings = false })
    }
}
