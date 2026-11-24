package com.luma.tvremote2.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.luma.tvremote2.core.Routes
import com.luma.tvremote2.core.RemoteViewModel
import com.luma.tvremote2.core.primaryNavItems
import com.luma.tvremote2.designsystem.LumaBottomNavigation
import com.luma.tvremote2.designsystem.LumaDeviceStatus
import com.luma.tvremote2.designsystem.LumaTokens
import com.luma.tvremote2.feature.apps.AppsScreen
import com.luma.tvremote2.feature.calibration.CalibrationScreen
import com.luma.tvremote2.feature.cast.CastScreen
import com.luma.tvremote2.feature.connect.ConnectScreen
import com.luma.tvremote2.feature.cursor.CursorScreen
import com.luma.tvremote2.feature.host.HostScreen
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
    val currentRoute = nav.currentDestination?.route
    val selectedRoute = when {
        currentRoute == Routes.CALIBRATION -> Routes.CURSOR
        currentRoute in moreRoutes -> Routes.MORE
        else -> currentRoute
    }

    // Keep the message in state so it stays visible on screen (a snackbar can
    // be hidden behind the phone's soft keyboard while typing).
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    LumaDeviceStatus(
                        appName = "Luma TV Remote",
                        statusLine = if (connected) {
                            "Connected · ${state.connectedHost}"
                        } else {
                            state.status
                        },
                        connectionLabel = when (state.connectionMode) {
                            "direct" -> "Direct"
                            "companion" -> "Companion"
                            else -> null
                        },
                        connected = connected
                    )
                },
                actions = {
                    TextButton(onClick = { showSettings = true }) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(LumaTokens.SpaceXS)
                        ) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = null,
                                modifier = Modifier.size(LumaTokens.IconSmall)
                            )
                            Text("Settings", maxLines = 1, softWrap = false)
                        }
                    }
                    if (connected) {
                        TextButton(onClick = {
                            vm.disconnect()
                            nav.navigate(Routes.CONNECT) {
                                popUpTo(Routes.CONNECT) { inclusive = true }
                            }
                        }) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(LumaTokens.SpaceXS)
                            ) {
                                Icon(
                                    Icons.Filled.ExitToApp,
                                    contentDescription = null,
                                    modifier = Modifier.size(LumaTokens.IconSmall)
                                )
                                Text(
                                    "Disconnect",
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            if (connected) {
                LumaBottomNavigation(
                    items = primaryNavItems,
                    selectedRoute = selectedRoute,
                    onSelect = { item ->
                        if (item.route == Routes.MORE) {
                            showMore = true
                        } else {
                            nav.navigate(item.route) {
                                popUpTo(Routes.REMOTE) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.CONNECT,
            modifier = Modifier.padding(padding)
        ) {
            composable(Routes.CONNECT) {
                ConnectScreen(
                    vm = vm,
                    onConnected = { nav.navigate(Routes.REMOTE) },
                    onHost = { nav.navigate(Routes.HOST) }
                )
            }
            composable(Routes.REMOTE) { RemoteScreen(vm) }
            composable(Routes.CURSOR) {
                CursorScreen(vm, onCalibration = { nav.navigate(Routes.CALIBRATION) })
            }
            composable(Routes.CALIBRATION) {
                CalibrationScreen(vm, onDone = { nav.popBackStack() })
            }
            composable(Routes.KEYBOARD) { KeyboardScreen(vm) }
            composable(Routes.APPS) { AppsScreen(vm) }
            composable(Routes.INPUTS) { InputsScreen(vm) }
            composable(Routes.SEARCH) { SearchScreen(vm) }
            composable(Routes.MUSIC) { MusicScreen(vm) }
            composable(Routes.CAST) { CastScreen(vm) }
            composable(Routes.HOST) { HostScreen() }
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
    Routes.INPUTS, Routes.SEARCH, Routes.MUSIC, Routes.CAST, Routes.HOST
)
