package com.luma.tvremote2.host

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** UI state for the in-app Companion Host screen. */
data class HostUiState(
    val serverState: String = "Stopped",
    val peer: String = "No peer",
    val pendingId: String? = null,
    val approvedId: String? = null,
    val portInput: String = "8787",
    val browserUrlInput: String = "https://example.com",
    val activityLines: List<String> = listOf("host ready"),
    val lastReject: String = "none"
) {
    val running: Boolean get() = serverState != "Stopped" && serverState != "Start failed"
    val canApprove: Boolean get() = running && pendingId != null
    val canSend: Boolean get() = running && approvedId != null && pendingId == null
}

/**
 * Owns one foreground-only CompanionServer instance.
 *
 * The server is stopped whenever the host screen is paused or disposed; there
 * is no Android service, no receiver and no background restart.
 */
class HostViewModel : ViewModel(), CompanionHostEvents {

    private val _ui = MutableStateFlow(HostUiState())
    val ui: StateFlow<HostUiState> = _ui.asStateFlow()

    private var server: CompanionServer = CompanionServer(DEFAULT_PORT, this)

    fun onPortInput(value: String) {
        _ui.value = _ui.value.copy(portInput = value)
    }

    fun onBrowserUrlInput(value: String) {
        _ui.value = _ui.value.copy(browserUrlInput = value)
    }

    fun start() {
        val port = _ui.value.portInput.trim().toIntOrNull()
        if (port == null || port !in 1..65535) {
            appendActivity("invalid_port")
            _ui.value = _ui.value.copy(lastReject = "invalid_port")
            return
        }
        server.stop()
        server = CompanionServer(port, this)
        server.start()
    }

    fun stop() {
        server.stop()
    }

    fun approve() = server.approve()

    fun reject() = server.reject()

    fun refresh() = server.sendDiagnosticsRefresh()

    fun openPage(rawUrl: String) = server.sendBrowserOpen(rawUrl)

    fun closePage() = server.sendBrowserClose()

    override fun onCleared() {
        server.stop()
        super.onCleared()
    }

    override fun onServerState(state: String) {
        val base = _ui.value
        val cleared = state == "Stopped"
        _ui.value = base.copy(
            serverState = state,
            peer = if (cleared) "No peer" else base.peer,
            pendingId = if (cleared) null else base.pendingId,
            approvedId = if (cleared) null else base.approvedId
        )
    }

    override fun onPeerPending(sessionId: String, producer: String, appVersion: String) {
        _ui.value = _ui.value.copy(
            pendingId = sessionId,
            approvedId = null,
            peer = "Pending · $producer $appVersion · ${sessionId.take(8)}"
        )
        appendActivity("hello received · ${sessionId.take(8)}")
    }

    override fun onPeerConnected(sessionId: String) {
        _ui.value = _ui.value.copy(
            pendingId = null,
            approvedId = sessionId,
            peer = "Approved · ${sessionId.take(8)}"
        )
        appendActivity("capability approved · ${sessionId.take(8)}")
    }

    override fun onPeerClosed() {
        _ui.value = _ui.value.copy(
            pendingId = null,
            approvedId = null,
            peer = "No peer"
        )
    }

    override fun onActivity(summary: String) = appendActivity(summary)

    override fun onReject(reason: String) {
        _ui.value = _ui.value.copy(lastReject = reason)
        appendActivity("rejected · $reason")
    }

    private fun appendActivity(line: String) {
        val lines = _ui.value.activityLines + line
        _ui.value = _ui.value.copy(activityLines = lines.takeLast(MAX_ACTIVITY_LINES))
    }

    private companion object {
        const val DEFAULT_PORT = 8787
        const val MAX_ACTIVITY_LINES = 12
    }
}
