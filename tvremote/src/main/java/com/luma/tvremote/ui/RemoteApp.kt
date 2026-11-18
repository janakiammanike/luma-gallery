package com.luma.tvremote.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel

private val TABS = listOf("Remote", "Cursor", "Keyboard", "Apps")

@Composable
fun RemoteApp(vm: RemoteViewModel = viewModel()) {
    val state by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableIntStateOf(0) }

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
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (state.connectedHost != null) {
                        TextButton(onClick = { vm.disconnect() }) {
                            Text("Disconnect")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (state.connectedHost == null) {
            ConnectScreen(
                state = state,
                modifier = Modifier.padding(padding),
                onHostInput = vm::onHostInput,
                onConnect = { vm.connect() },
                onScan = vm::scan,
                onPickHost = { vm.connect(it) },
                onForget = vm::forgetHost
            )
        } else {
            Column(modifier = Modifier.padding(padding)) {
                TabRow(selectedTabIndex = tab) {
                    TABS.forEachIndexed { index, title ->
                        Tab(
                            selected = tab == index,
                            onClick = { tab = index },
                            text = { Text(title) }
                        )
                    }
                }
                when (tab) {
                    0 -> RemoteTab(vm)
                    1 -> CursorTab(vm)
                    2 -> KeyboardTab(vm)
                    3 -> AppsTab(vm)
                }
            }
        }
    }
}
