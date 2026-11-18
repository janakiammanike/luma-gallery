package com.luma.tvremote.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luma.tvremote.data.TvStore
import com.luma.tvremote.net.ConnectResult
import com.luma.tvremote.net.LanScanner
import com.luma.tvremote.net.TvApp
import com.luma.tvremote.net.WebOsClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RemoteUiState(
    val hostInput: String = "",
    val savedHosts: Set<String> = emptySet(),
    val detectedHosts: List<String> = emptyList(),
    val connectedHost: String? = null,
    val status: String = "Not connected",
    val isConnecting: Boolean = false,
    val isScanning: Boolean = false,
    val message: String? = null,
    val apps: List<TvApp> = emptyList(),
    val appQuery: String = "",
    val keyboardText: String = ""
)

class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val store = TvStore(application)
    private var client: WebOsClient? = null

    private val _ui = MutableStateFlow(
        RemoteUiState(
            hostInput = store.lastHost().orEmpty(),
            savedHosts = store.savedHosts()
        )
    )
    val ui: StateFlow<RemoteUiState> = _ui.asStateFlow()

    fun onHostInput(value: String) {
        _ui.update { it.copy(hostInput = value) }
    }

    fun onAppQuery(value: String) {
        _ui.update { it.copy(appQuery = value) }
    }

    fun onKeyboardText(value: String) {
        _ui.update { it.copy(keyboardText = value) }
    }

    fun consumeMessage() {
        _ui.update { it.copy(message = null) }
    }

    fun scan() {
        viewModelScope.launch {
            _ui.update { it.copy(isScanning = true, message = null) }
            val found = LanScanner.scan(getApplication())
            _ui.update {
                it.copy(
                    isScanning = false,
                    detectedHosts = found,
                    message = if (found.isEmpty()) {
                        "No TV found. Enter the TV IP address manually."
                    } else {
                        "Found ${found.size} device(s) with an open webOS port."
                    }
                )
            }
        }
    }

    fun connect(hostOverride: String? = null) {
        val host = (hostOverride ?: _ui.value.hostInput)
            .trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore("/")
        if (host.isBlank()) {
            _ui.update { it.copy(message = "Enter your TV's IP address") }
            return
        }

        viewModelScope.launch {
            _ui.update {
                it.copy(
                    hostInput = host,
                    isConnecting = true,
                    status = "Connecting…",
                    message = null
                )
            }
            client?.close()
            val newClient = WebOsClient(
                host = host,
                storedKey = store.keyFor(host),
                onStatus = { status -> _ui.update { it.copy(status = status) } },
                onClientKey = { key -> store.saveKey(host, key) }
            )
            when (val result = newClient.connect()) {
                is ConnectResult.Success -> {
                    client = newClient
                    store.addHost(host)
                    store.setLastHost(host)
                    _ui.update {
                        it.copy(
                            connectedHost = host,
                            savedHosts = store.savedHosts(),
                            isConnecting = false,
                            status = "Connected · $host",
                            message = "Pairing saved. You're connected!"
                        )
                    }
                    loadApps()
                }

                is ConnectResult.Failure -> {
                    newClient.close()
                    _ui.update {
                        it.copy(
                            isConnecting = false,
                            status = "Connection failed",
                            message = result.message
                        )
                    }
                }
            }
        }
    }

    fun disconnect() {
        client?.close()
        client = null
        _ui.update {
            it.copy(
                connectedHost = null,
                status = "Not connected",
                apps = emptyList(),
                message = "Disconnected"
            )
        }
    }

    fun forgetHost(host: String) {
        store.removeHost(host)
        _ui.update {
            it.copy(
                savedHosts = store.savedHosts(),
                detectedHosts = it.detectedHosts - host,
                message = "Forgot $host"
            )
        }
    }

    fun reloadApps() = loadApps()

    private fun loadApps() {
        viewModelScope.launch {
            try {
                val apps = client?.apps().orEmpty()
                _ui.update { it.copy(apps = apps) }
            } catch (e: Exception) {
                _ui.update { it.copy(message = e.message ?: "Could not load TV apps") }
            }
        }
    }

    fun pressButton(name: String) = runCommand { it.button(name) }

    fun movePointer(dx: Int, dy: Int) = runCommand { it.move(dx, dy) }

    fun clickPointer() = runCommand { it.click() }

    fun scroll(dx: Int, dy: Int) = runCommand { it.scroll(dx, dy) }

    fun volumeUp() = runCommand { it.volumeUp() }

    fun volumeDown() = runCommand { it.volumeDown() }

    fun toggleMute() = runCommand { it.setMute(true) }

    fun channelUp() = runCommand { it.channelUp() }

    fun channelDown() = runCommand { it.channelDown() }

    fun powerOff() = runCommand { it.powerOff() }

    fun play() = runCommand { it.play() }

    fun pause() = runCommand { it.pause() }

    fun stop() = runCommand { it.stop() }

    fun rewind() = runCommand { it.rewind() }

    fun fastForward() = runCommand { it.fastForward() }

    fun sendText() {
        val text = _ui.value.keyboardText
        if (text.isBlank()) {
            _ui.update { it.copy(message = "Type something first") }
            return
        }
        runCommand { it.typeText(text) }
    }

    fun sendEnter() = runCommand { it.sendEnter() }

    fun backspace() {
        val current = _ui.value.keyboardText
        if (current.isNotEmpty()) {
            _ui.update { it.copy(keyboardText = current.dropLast(1)) }
        }
        runCommand { it.deleteCharacters(1) }
    }

    fun clearKeyboard() {
        val count = _ui.value.keyboardText.length
        _ui.update { it.copy(keyboardText = "") }
        if (count > 0) runCommand { it.deleteCharacters(count.coerceAtMost(20)) }
    }

    fun launchApp(id: String) = runCommand { it.launchApp(id) }

    fun tvSearch(query: String) {
        if (query.isBlank()) {
            _ui.update { it.copy(message = "Type a search term first") }
            return
        }
        viewModelScope.launch {
            try {
                val message = client?.searchOnTv(query) ?: "Not connected"
                _ui.update { it.copy(message = message) }
            } catch (e: Exception) {
                _ui.update { it.copy(message = e.message ?: "Search failed") }
            }
        }
    }

    private fun runCommand(block: suspend (WebOsClient) -> Unit) {
        viewModelScope.launch {
            val active = client
            if (active == null || !active.isConnected()) {
                _ui.update { it.copy(message = "Connect to a TV first") }
                return@launch
            }
            try {
                block(active)
            } catch (e: Exception) {
                _ui.update { it.copy(message = e.message ?: "Command failed") }
            }
        }
    }
}
