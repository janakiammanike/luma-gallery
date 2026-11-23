package com.luma.tvremote2.core

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luma.tvremote2.data.SecureTvStore
import com.luma.tvremote2.network.CompanionScanner
import com.luma.tvremote2.platform.webos.LgWebOsPlatform
import com.luma.tvremote2.platform.webos.WebOsApp
import com.luma.tvremote2.platform.webos.WebOsDevice
import com.luma.tvremote2.platform.webos.WebOsPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RemoteUiState(
    val hostInput: String = "",
    val detectedHost: String? = null,
    val status: String = "Not connected",
    val connectedHost: String? = null,
    val isConnecting: Boolean = false,
    val darkTheme: Boolean = false,
    val haptics: Boolean = true,
    val keyboardText: String = "",
    val apps: List<WebOsApp> = emptyList(),
    val audioVolume: Int? = null,
    val audioMuted: Boolean = false,
    val message: String? = null
)

/** Drives the shell and, from Phase 2A, real LG commands over the companion proxy. */
class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SecureTvStore(application)
    private val platform: WebOsPlatform = LgWebOsPlatform(store)

    private val _ui = MutableStateFlow(
        RemoteUiState(
            hostInput = store.lastHost().orEmpty(),
            darkTheme = store.darkTheme(),
            haptics = store.haptics()
        )
    )
    val ui: StateFlow<RemoteUiState> = _ui.asStateFlow()

    fun onHostInput(value: String) {
        _ui.update { it.copy(hostInput = value) }
    }

    fun onKeyboardText(value: String) {
        _ui.update { it.copy(keyboardText = value) }
    }

    fun consumeMessage() {
        _ui.update { it.copy(message = null) }
    }

    fun reportPending(feature: String) {
        _ui.update { it.copy(message = "$feature migrates in a later phase") }
    }

    /** Looks for the TV companion beacon on the LAN. */
    fun discoverCompanion() {
        viewModelScope.launch {
            _ui.update { it.copy(status = "Looking for TV companion…") }
            val beacon = CompanionScanner.listen()
            if (beacon == null) {
                _ui.update {
                    it.copy(
                        status = "Not connected",
                        message = "No companion beacon found. Enter the TV IP manually."
                    )
                }
            } else {
                _ui.update {
                    it.copy(
                        detectedHost = beacon.host,
                        hostInput = beacon.host,
                        status = "Found TV · ${beacon.host}",
                        message = "Found TV companion at ${beacon.host}"
                    )
                }
            }
        }
    }

    fun connect() = connect(_ui.value.hostInput)

    fun connect(hostInput: String) {
        val host = hostInput.trim()
        if (host.isBlank()) {
            _ui.update { it.copy(message = "Enter the TV IP address first") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isConnecting = true, status = "Connecting…", message = null) }
            val device = WebOsDevice(host = host, viaCompanion = true)
            val result = platform.connect(device)
            result.fold(
                onSuccess = {
                    _ui.update {
                        it.copy(
                            isConnecting = false,
                            connectedHost = host,
                            status = "Connected · $host",
                            message = "Connected via TV companion"
                        )
                    }
                    refreshAudio()
                },
                onFailure = { error ->
                    _ui.update {
                        it.copy(
                            isConnecting = false,
                            status = "Not connected",
                            connectedHost = null,
                            message = error.message ?: "Connection failed"
                        )
                    }
                }
            )
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            platform.disconnect()
            _ui.update {
                it.copy(
                    connectedHost = null,
                    status = "Not connected",
                    apps = emptyList(),
                    audioVolume = null
                )
            }
        }
    }

    // ---- Real commands -----------------------------------------------------------------

    fun button(name: String) = command { platform.button(name) }

    fun volumeUp() = command { platform.volumeUp(); refreshAudio() }
    fun volumeDown() = command { platform.volumeDown(); refreshAudio() }
    fun setMute(mute: Boolean) = command {
        platform.setMute(mute)
        _ui.update { it.copy(audioMuted = mute) }
    }

    fun toggleMute() = setMute(!_ui.value.audioMuted)
    fun channelUp() = command { platform.channelUp() }
    fun channelDown() = command { platform.channelDown() }
    fun powerOff() = command { platform.powerOff() }
    fun powerOn() = command { platform.powerOn() }
    fun scroll(dx: Int, dy: Int) = command { platform.scroll(dx, dy) }
    fun click() = command { platform.click() }
    fun move(dx: Int, dy: Int) = command { platform.move(dx, dy) }

    fun sendKeyboardText() {
        val text = _ui.value.keyboardText
        if (text.isBlank()) return
        command { platform.typeText(text) }
    }

    fun backspace() = command { platform.deleteCharacters(1) }
    fun enter() = command { platform.sendEnter() }

    private fun refreshAudio() {
        viewModelScope.launch {
            runCatching { platform.audioVolume() }.onSuccess { volume ->
                _ui.update { it.copy(audioVolume = volume) }
            }
        }
    }

    fun loadApps() {
        viewModelScope.launch {
            runCatching { platform.apps() }.fold(
                onSuccess = { list -> _ui.update { it.copy(apps = list) } },
                onFailure = { error ->
                    _ui.update { it.copy(message = error.message ?: "Could not load apps") }
                }
            )
        }
    }

    fun launchApp(id: String) = command { platform.launchApp(id) }

    private fun command(block: suspend () -> Unit) {
        if (!platform.isConnected()) {
            _ui.update { it.copy(message = "Not connected to a TV") }
            return
        }
        viewModelScope.launch {
            runCatching { block() }.onFailure { error ->
                val lost = error is IllegalStateException &&
                    error.message?.contains("Connection lost") == true
                _ui.update {
                    it.copy(
                        connectedHost = if (lost) null else it.connectedHost,
                        status = if (lost) "Connection lost — reconnect" else it.status,
                        message = error.message ?: "Command failed"
                    )
                }
            }
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        store.setDarkTheme(enabled)
        _ui.update { it.copy(darkTheme = enabled) }
    }

    fun setHaptics(enabled: Boolean) {
        store.setHaptics(enabled)
        _ui.update { it.copy(haptics = enabled) }
    }
}
