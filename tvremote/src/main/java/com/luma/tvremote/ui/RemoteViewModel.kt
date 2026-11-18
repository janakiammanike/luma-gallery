package com.luma.tvremote.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luma.tvremote.data.TvStore
import com.luma.tvremote.net.ConnectResult
import com.luma.tvremote.net.DiscoveredTv
import com.luma.tvremote.net.LanScanner
import com.luma.tvremote.net.MdnsScanner
import com.luma.tvremote.net.TvApp
import com.luma.tvremote.net.WakeOnLan
import com.luma.tvremote.net.WifiInfo
import com.luma.tvremote.net.WifiStatus
import com.luma.tvremote.net.WebOsClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

val DEFAULT_BUTTON_GROUPS = setOf("dpad", "system", "volume", "playback", "power")

data class RemoteUiState(
    val hostInput: String = "",
    val savedHosts: Set<String> = emptySet(),
    val detectedHosts: List<String> = emptyList(),
    val mdnsHosts: List<DiscoveredTv> = emptyList(),
    val isDiscovering: Boolean = false,
    val wifi: WifiInfo? = null,
    val tvLatencyMs: Long? = null,
    val favorites: Set<String> = emptySet(),
    val hostMacs: Map<String, String> = emptyMap(),
    val connectedHost: String? = null,
    val status: String = "Not connected",
    val isConnecting: Boolean = false,
    val isScanning: Boolean = false,
    val message: String? = null,
    val apps: List<TvApp> = emptyList(),
    val appQuery: String = "",
    val keyboardText: String = "",
    val visibleGroups: Set<String> = DEFAULT_BUTTON_GROUPS,
    val customizing: Boolean = false
)

class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val store = TvStore(application)
    private var client: WebOsClient? = null

    private val _ui = MutableStateFlow(
        RemoteUiState(
            hostInput = store.lastHost().orEmpty(),
            savedHosts = store.savedHosts(),
            favorites = store.favorites(),
            hostMacs = store.savedHosts().mapNotNull { host ->
                store.macFor(host)?.takeIf { it.isNotBlank() }?.let { host to it }
            }.toMap(),
            wifi = WifiStatus.read(application)
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

    fun discoverMdns() {
        viewModelScope.launch {
            _ui.update { it.copy(isDiscovering = true, message = null) }
            val found = MdnsScanner.scan(getApplication())
            val macs = found.mapNotNull { tv -> tv.mac?.let { tv.host to it } }.toMap()
            found.forEach { tv ->
                tv.mac?.let { store.saveMac(tv.host, it) }
            }
            _ui.update {
                it.copy(
                    isDiscovering = false,
                    mdnsHosts = found,
                    hostMacs = it.hostMacs + macs,
                    message = if (found.isEmpty()) {
                        "No mDNS TVs found. Use Scan network or enter the IP manually."
                    } else {
                        "Found ${found.size} TV(s) via mDNS."
                    }
                )
            }
        }
    }

    fun refreshLink() {
        viewModelScope.launch {
            val wifi = WifiStatus.read(getApplication())
            val host = _ui.value.connectedHost ?: _ui.value.hostMacs.keys.firstOrNull()
            val latency = host?.let { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                WifiStatus.latencyMs(it)
            } }
            _ui.update { it.copy(wifi = wifi, tvLatencyMs = latency) }
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
                    val layout = store.buttonLayout(host) ?: DEFAULT_BUTTON_GROUPS
                    _ui.update {
                        it.copy(
                            connectedHost = host,
                            savedHosts = store.savedHosts(),
                            hostMacs = it.hostMacs + (store.macFor(host)?.let { mac -> host to mac }?.let { mapOf(it) }
                                ?: emptyMap()),
                            visibleGroups = layout,
                            customizing = false,
                            isConnecting = false,
                            status = "Connected · $host",
                            message = "Pairing saved. You're connected!"
                        )
                    }
                    loadApps()
                    learnMac(host, newClient)
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

    private fun learnMac(host: String, active: WebOsClient) {
        viewModelScope.launch {
            try {
                val mac = active.networkInfo()?.preferredMac() ?: return@launch
                store.saveMac(host, mac)
                _ui.update { it.copy(hostMacs = it.hostMacs + (host to mac)) }
            } catch (_: Exception) {
                // MAC discovery is best-effort; WoL simply won't be offered.
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
                favorites = store.favorites(),
                hostMacs = it.hostMacs - host,
                detectedHosts = it.detectedHosts - host,
                message = "Forgot $host"
            )
        }
    }

    fun toggleFavorite(host: String) {
        store.toggleFavorite(host)
        _ui.update { it.copy(favorites = store.favorites()) }
    }

    /** Wake a saved TV by MAC. Works even while the app is disconnected. */
    fun wakeHost(host: String) {
        val mac = store.macFor(host) ?: _ui.value.hostMacs[host]
        if (mac.isNullOrBlank()) {
            _ui.update { it.copy(message = "Connect to $host once to learn its MAC address") }
            return
        }
        viewModelScope.launch {
            try {
                WakeOnLan.send(getApplication(), mac)
                _ui.update { it.copy(message = "Wake-on-LAN packet sent to $host") }
            } catch (e: Exception) {
                _ui.update { it.copy(message = e.message ?: "Could not send wake packet") }
            }
        }
    }

    /** Software power-on when connected; falls back to WoL using the learned MAC. */
    fun powerOn() {
        val active = client
        if (active != null && active.isConnected()) {
            viewModelScope.launch {
                try {
                    active.powerOn()
                    _ui.update { it.copy(message = "Power-on command sent to TV") }
                } catch (e: Exception) {
                    _ui.update { it.copy(message = "TV did not accept power-on: ${e.message ?: "failed"}") }
                }
            }
        } else {
            _ui.value.connectedHost?.let { wakeHost(it) }
                ?: _ui.update { it.copy(message = "Connect to the TV first, or use Wake on a saved TV") }
        }
    }

    fun toggleCustomizing() {
        _ui.update { it.copy(customizing = !it.customizing) }
    }

    fun toggleGroup(id: String) {
        val host = _ui.value.connectedHost ?: return
        val current = _ui.value.visibleGroups
        val next = if (id in current) current - id else current + id
        store.saveButtonLayout(host, next)
        _ui.update { it.copy(visibleGroups = next) }
    }

    fun resetLayout() {
        val host = _ui.value.connectedHost ?: return
        store.clearButtonLayout(host)
        _ui.update { it.copy(visibleGroups = DEFAULT_BUTTON_GROUPS) }
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

    fun appSearch(appId: String, query: String) {
        if (query.isBlank()) {
            _ui.update { it.copy(message = "Type a search term first") }
            return
        }
        viewModelScope.launch {
            try {
                val message = client?.searchInApp(appId, query) ?: "Not connected"
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
