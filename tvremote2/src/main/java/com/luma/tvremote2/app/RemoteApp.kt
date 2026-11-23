package com.luma.tvremote2.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.luma.tvremote2.core.Routes
import com.luma.tvremote2.core.RemoteViewModel
import com.luma.tvremote2.core.primaryNavItems
import com.luma.tvremote2.feature.apps.AppsScreen
import com.luma.tvremote2.feature.cast.CastScreen
import com.luma.tvremote2.feature.connect.ConnectScreen
import com.luma.tvremote2.feature.cursor.CursorScreen
import com.luma.tvremote2.feature.inputs.InputsScreen
import com.luma.tvremote2.feature.keyboard.KeyboardScreen
import com.luma.tvremote2.feature.more.MoreDialog
import com.luma.tvremote2.feature.music.MusicScreen
import com.luma.tvremote2.feature.remote.RemoteScreen
import com.luma.tvremote2.feature.search.SearchScreen
import com.luma.tvremote2.feature.settings.SettingsDialog

/** Shell with the same screens/navigation as the legacy app, now using real state. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteApp(vm: RemoteViewModel) {
    val state by vm.ui.collectAsState()
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    var showMore by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val connected = state.connectedHost != null

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
                    if (connected) {
                        TextButton(onClick = {
                            vm.disconnect()
                            nav.navigate(Routes.CONNECT) {
                                popUpTo(Routes.CONNECT) { inclusive = true }
                            }
                        }) { Text("Disconnect") }
                    }
                }
            )
        },
        bottomBar = {
            if (connected) {
                NavigationBar {
                    primaryNavItems.forEach { item ->
                        val current = nav.currentDestination?.route
                        NavigationBarItem(
                            selected = current == item.route ||
                                (item.route == Routes.MORE && current in moreRoutes),
                            onClick = {
                                if (item.route == Routes.MORE) {
                                    showMore = true
                                } else {
                                    nav.navigate(item.route) {
                                        popUpTo(Routes.REMOTE) { inclusive = false }
                                        launchSingleTop = true
                                    }
                                }
                            },
                            icon = { Text(item.glyph) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.CONNECT,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.CONNECT) { ConnectScreen(vm, onConnected = { nav.navigate(Routes.REMOTE) }) }
            composable(Routes.REMOTE) { RemoteScreen(vm) }
            composable(Routes.CURSOR) { CursorScreen(vm) }
            composable(Routes.KEYBOARD) { KeyboardScreen(vm) }
            composable(Routes.APPS) { AppsScreen(vm) }
            composable(Routes.INPUTS) { InputsScreen(vm) }
            composable(Routes.SEARCH) { SearchScreen(vm) }
            composable(Routes.MUSIC) { MusicScreen(vm) }
            composable(Routes.CAST) { CastScreen(vm) }
        }
    }

    if (showMore) {
        MoreDialog(
            onPick = { route ->
                showMore = false
                nav.navigate(route) { launchSingleTop = true }
            },
            onDismiss = { showMore = false }
        )
    }

    if (showSettings) {
        SettingsDialog(vm = vm, onDismiss = { showSettings = false })
    }
}

private val moreRoutes = setOf(
    Routes.INPUTS, Routes.SEARCH, Routes.MUSIC, Routes.CAST
)
