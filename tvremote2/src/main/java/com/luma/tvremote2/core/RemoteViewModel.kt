package com.luma.tvremote2.core

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luma.tvremote2.data.SecureTvStore
import com.luma.tvremote2.input.AirMouseController
import com.luma.tvremote2.network.CompanionScanner
import com.luma.tvremote2.network.TvScanner
import com.luma.tvremote2.platform.webos.DirectWebOsPlatform
import com.luma.tvremote2.platform.webos.LgWebOsPlatform
import com.luma.tvremote2.platform.webos.WebOsApp
import com.luma.tvremote2.platform.webos.WebOsDevice
import com.luma.tvremote2.platform.webos.WebOsPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

data class RemoteUiState(
    val hostInput: String = "",
    val detectedHost: String? = null,
    val status: String = "Not connected",
    val connectedHost: String? = null,
    val isConnecting: Boolean = false,
    val darkTheme: Boolean = false,
    val haptics: Boolean = true,
    val keyboardText: String = "",
    val searchText: String = "",
    val musicQuery: String = "",
    val castUrl: String = "",
    val apps: List<WebOsApp> = emptyList(),
    val audioVolume: Int? = null,
    val audioMuted: Boolean = false,
    val connectionMode: String? = null,
    val airMouseEnabled: Boolean = false,
    val airMouseAvailable: Boolean = false,
    val airMouseSensor: String? = null,
    val airMouseStatus: String? = null,
    val airMouseSensitivity: Float = 1f,
    val airMouseInvertX: Boolean = false,
    val airMouseInvertY: Boolean = false,
    val visibleGroups: Set<String> = SecureTvStore.DEFAULT_VISIBLE_GROUPS,
    val message: String? = null
)

/** Drives the shell and real LG commands (companion proxy or direct SSAP). */
class RemoteViewModel(application: Application) : AndroidViewModel(application) {
    private val store = SecureTvStore(application)
    private val companionPlatform: WebOsPlatform = LgWebOsPlatform(store)
    private val directPlatform = DirectWebOsPlatform(store) { status ->
        _ui.update { it.copy(status = status) }
    }
    private var platform: WebOsPlatform = companionPlatform

    private val _ui = MutableStateFlow(
        RemoteUiState(
            hostInput = store.lastHost().orEmpty(),
            darkTheme = store.darkTheme(),
            haptics = store.haptics(),
            airMouseEnabled = store.airMouseEnabled(),
            airMouseSensitivity = store.airMouseSensitivity(),
            airMouseInvertX = store.airMouseInvertX(),
            airMouseInvertY = store.airMouseInvertY(),
            visibleGroups = store.visibleGroups()
        )
    )
    val ui: StateFlow<RemoteUiState> = _ui.asStateFlow()

    private val _calibration = MutableStateFlow(
        CalibrationUiState(
            spanX = store.airSpanX(),
            spanY = store.airSpanY(),
            gainX = store.airGainX(),
            gainY = store.airGainY(),
            axisSignX = store.airAxisSignX(),
            axisSignY = store.airAxisSignY(),
            shakeToCenter = store.shakeToCenter()
        )
    )
    val calibration: StateFlow<CalibrationUiState> = _calibration.asStateFlow()

    // Sensor events can arrive ~80 times a second. A single consumer keeps the
    // input socket writes serialised; the bounded buffer drops the oldest
    // sample if the TV is slow instead of growing without limit.
    private val airMoves = Channel<Pair<Int, Int>>(128)

    private val airMouse = AirMouseController(
        getApplication(),
        onMove = { dx, dy ->
            airMoves.trySend(dx to dy)
            if (_calibration.value.cornerScanActive) {
                _calibration.update {
                    it.copy(accumDx = it.accumDx + abs(dx), accumDy = it.accumDy + abs(dy))
                }
            }
        },
        onStatus = { text -> _ui.update { it.copy(airMouseStatus = text) } },
        onProgress = { progress, text ->
            _calibration.update { it.copy(progress = progress, message = text) }
        },
        onDirection = { direction, avgX, avgY -> recordDirection(direction, avgX, avgY) },
        onShake = {
            if (_calibration.value.shakeToCenter && _ui.value.connectedHost != null) recenter()
        }
    )

    init {
        Haptics.init(getApplication())
        airMouse.configure(
            store.airMouseSensitivity(),
            store.airMouseInvertX(),
            store.airMouseInvertY()
        )
        airMouse.applyGain(store.airGainX(), store.airGainY())
        airMouse.applyAxisSign(store.airAxisSignX(), store.airAxisSignY())
        _ui.update {
            it.copy(
                airMouseAvailable = airMouse.isAvailable(),
                airMouseSensor = if (airMouse.isAvailable()) airMouse.sensorName() else null
            )
        }
        viewModelScope.launch {
            for ((dx, dy) in airMoves) {
                runCatching { platform.move(dx, dy) }
            }
        }
    }

    fun onHostInput(value: String) {
        _ui.update { it.copy(hostInput = value) }
    }

    fun onKeyboardText(value: String) {
        _ui.update { it.copy(keyboardText = value) }
    }

    fun onSearchText(value: String) {
        _ui.update { it.copy(searchText = value) }
    }

    fun onMusicQuery(value: String) {
        _ui.update { it.copy(musicQuery = value) }
    }

    fun onCastUrl(value: String) {
        _ui.update { it.copy(castUrl = value) }
    }

    fun consumeMessage() {
        _ui.update { it.copy(message = null) }
    }

    /** Beacon first; if absent, scan the local network for webOS ports. */
    fun discoverCompanion() {
        viewModelScope.launch {
            _ui.update { it.copy(status = "Looking for TV…") }
            val found = TvScanner.find()
            if (found == null) {
                _ui.update {
                    it.copy(
                        status = "Not connected",
                        message = "No TV found on this network. Enter the TV IP manually."
                    )
                }
            } else {
                _ui.update {
                    it.copy(
                        detectedHost = found.host,
                        hostInput = found.host,
                        status = "Found TV · ${found.host}",
                        message = if (found.viaCompanion) {
                            "Found TV companion at ${found.host}"
                        } else {
                            "Found TV at ${found.host} (direct SSAP)"
                        }
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
            // Prefer the companion proxy; fall back to direct SSAP when the
            // companion app is not running (which is the current TV state).
            val viaCompanion = CompanionScanner.probe(host) != null
            val chosen: WebOsPlatform = if (viaCompanion) companionPlatform else directPlatform
            platform = chosen
            val device = WebOsDevice(host = host, viaCompanion = viaCompanion)
            val result = chosen.connect(device)
            result.fold(
                onSuccess = {
                    val mode = if (viaCompanion) "companion" else "direct"
                    _ui.update {
                        it.copy(
                            isConnecting = false,
                            connectedHost = host,
                            connectionMode = mode,
                            status = "Connected · $host · $mode",
                            message = "Connected via $mode"
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
        stopAirMouse()
        viewModelScope.launch {
            platform.disconnect()
            _ui.update {
                it.copy(
                    connectedHost = null,
                    connectionMode = null,
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
    fun clickPointer() = click()
    fun movePointer(dx: Int, dy: Int) = command { platform.move(dx, dy) }

    // ---- Air mouse -------------------------------------------------------------

    fun setAirMouseEnabled(enabled: Boolean) {
        store.setAirMouseEnabled(enabled)
        _ui.update {
            it.copy(
                airMouseEnabled = enabled,
                airMouseStatus = if (enabled) {
                    "Air mouse ready — hold the phone still to calibrate"
                } else {
                    "Air mouse off · touchpad still active"
                }
            )
        }
        if (!enabled) stopAirMouse()
    }

    fun setAirMouseSensitivity(value: Float) {
        val limited = value.coerceIn(0.25f, 3f)
        store.setAirMouseSensitivity(limited)
        airMouse.configure(limited, _ui.value.airMouseInvertX, _ui.value.airMouseInvertY)
        _ui.update { it.copy(airMouseSensitivity = limited) }
    }

    fun setAirMouseInvertX(enabled: Boolean) {
        val state = _ui.value
        store.setAirMouseInvert(enabled, state.airMouseInvertY)
        airMouse.configure(state.airMouseSensitivity, enabled, state.airMouseInvertY)
        _ui.update { it.copy(airMouseInvertX = enabled) }
    }

    fun setAirMouseInvertY(enabled: Boolean) {
        val state = _ui.value
        store.setAirMouseInvert(state.airMouseInvertX, enabled)
        airMouse.configure(state.airMouseSensitivity, state.airMouseInvertX, enabled)
        _ui.update { it.copy(airMouseInvertY = enabled) }
    }

    /** Measures gyroscope bias while the phone is still; safe to call anytime. */
    fun recalibrateAirMouse() {
        airMouse.beginStillCalibration(strict = false)
        _ui.update { it.copy(message = "Hold the phone still for a fresh gyroscope bias") }
    }

    /** Called by the Cursor screen while it is visible and the toggle is on. */
    fun startAirMouse() {
        if (!_ui.value.airMouseEnabled || _ui.value.connectedHost == null) return
        airMouse.configure(
            _ui.value.airMouseSensitivity,
            _ui.value.airMouseInvertX,
            _ui.value.airMouseInvertY
        )
        airMouse.start()
        airMouse.beginStillCalibration(strict = false)
    }

    fun stopAirMouse() {
        airMouse.stop()
    }

    // ---- Guided calibration ----------------------------------------------------

    /** Opens the wizard and starts the sensor even if the quick toggle is off. */
    fun startCalibration() {
        airMouse.start()
        _calibration.update {
            it.copy(
                stage = CalibrationStage.STILL,
                progress = 0f,
                message = "Hold the phone comfortably, then start the stability check.",
                directionIndex = 0,
                directionResults = emptyMap(),
                cornerIndex = 0,
                accumDx = 0,
                accumDy = 0
            )
        }
    }

    fun measureStillness() {
        airMouse.beginStillCalibration()
    }

    fun startDirectionChecks() {
        _calibration.update {
            it.copy(
                stage = CalibrationStage.DIRECTIONS,
                progress = 0f,
                message = "Tilt the phone UP and hold it there."
            )
        }
    }

    fun measureDirection(direction: String) {
        airMouse.beginDirectionCalibration(direction)
    }

    fun moveToCorners() {
        _calibration.update {
            it.copy(
                stage = CalibrationStage.CORNERS,
                cornerIndex = 0,
                accumDx = 0,
                accumDy = 0,
                progress = 0f,
                message = "Move the TV cursor to the TOP-LEFT corner, then tap Mark."
            )
        }
    }

    /** Records pointer travel between the four TV corners to learn the span. */
    fun markCorner() {
        val state = _calibration.value
        if (state.stage != CalibrationStage.CORNERS) return
        when (state.cornerIndex) {
            0 -> _calibration.update {
                it.copy(
                    cornerIndex = 1,
                    accumDx = 0,
                    accumDy = 0,
                    message = "Now move the cursor to the TOP-RIGHT corner, then tap Mark."
                )
            }
            1 -> _calibration.update {
                it.copy(
                    cornerIndex = 2,
                    spanX = state.accumDx,
                    accumDx = 0,
                    accumDy = 0,
                    message = "Width measured. Move the cursor to the BOTTOM-RIGHT corner."
                )
            }
            2 -> _calibration.update {
                it.copy(
                    cornerIndex = 3,
                    spanY = state.accumDy,
                    accumDx = 0,
                    accumDy = 0,
                    message = "Height measured. Move the cursor to the BOTTOM-LEFT corner."
                )
            }
            else -> saveSpan(state.spanY)
        }
    }

    private fun saveSpan(height: Int) {
        val state = _calibration.value
        val spanX = state.spanX.coerceAtLeast(200)
        val spanY = height.coerceAtLeast(150)
        val gainX = (TARGET_SPAN_X / spanX.toFloat()).coerceIn(0.25f, 4f)
        val gainY = (TARGET_SPAN_Y / spanY.toFloat()).coerceIn(0.25f, 4f)
        airMouse.applyGain(gainX, gainY)
        store.saveAirCalibration(gainX, gainY, spanX, spanY, state.axisSignX, state.axisSignY)
        _calibration.update {
            it.copy(
                stage = CalibrationStage.DONE,
                progress = 1f,
                spanX = spanX,
                spanY = spanY,
                gainX = gainX,
                gainY = gainY,
                cornerIndex = 4,
                message = "Calibration saved. One sweep now covers the whole TV screen."
            )
        }
        _ui.update { it.copy(message = "Air mouse calibration saved") }
    }

    private fun recordDirection(direction: String, avgX: Float, avgY: Float) {
        val state = _calibration.value
        var signX = state.axisSignX
        var signY = state.axisSignY
        var corrected = false
        when (direction) {
            "UP" -> if (avgY > 0f) { signY = -signY; corrected = true }
            "DOWN" -> if (avgY < 0f) { signY = -signY; corrected = true }
            "LEFT" -> if (avgX > 0f) { signX = -signX; corrected = true }
            "RIGHT" -> if (avgX < 0f) { signX = -signX; corrected = true }
        }
        if (signX != state.axisSignX || signY != state.axisSignY) {
            airMouse.applyAxisSign(signX, signY)
        }
        val tooSmall = kotlin.math.hypot(avgX, avgY) < 0.03f
        val result = if (tooSmall) "$direction · too small, repeat" else "$direction ✓"
        val next = state.directionIndex + 1
        _calibration.update {
            it.copy(
                axisSignX = signX,
                axisSignY = signY,
                directionResults = it.directionResults + (direction to result),
                directionIndex = next,
                progress = 0f,
                message = when {
                    corrected -> "$direction axis corrected — tilt it again to confirm"
                    tooSmall -> "Tilt $direction a little further and hold it"
                    else -> "$direction confirmed"
                },
                stage = if (next >= it.directions.size) {
                    CalibrationStage.CORNERS
                } else {
                    CalibrationStage.DIRECTIONS
                }
            )
        }
        if (next >= state.directions.size) {
            _calibration.update {
                it.copy(message = "Directions done. Move the TV cursor to the TOP-LEFT corner.")
            }
        }
    }

    fun setShakeToCenter(enabled: Boolean) {
        store.setShakeToCenter(enabled)
        _calibration.update { it.copy(shakeToCenter = enabled) }
        _ui.update {
            it.copy(message = if (enabled) {
                "Shake the phone side to side to recenter the TV cursor"
            } else {
                "Shake to recenter is off"
            })
        }
    }

    /** LG-style edge bump: push past the edge, then return by half the span. */
    fun recenter() {
        if (_ui.value.connectedHost == null) {
            _ui.update { it.copy(message = "Connect to a TV before recentering") }
            return
        }
        airMouse.pausePointer(700)
        val state = _calibration.value
        val pushX = if (state.spanX > 0) -(state.spanX + 500) else -4000
        val pushY = if (state.spanY > 0) -(state.spanY + 400) else -3000
        command {
            platform.move(pushX, pushY)
            delay(80)
            platform.move(-pushX / 2, -pushY / 2)
            _ui.update { it.copy(message = "Cursor recentred on the TV") }
        }
    }

    private companion object {
        const val TARGET_SPAN_X = 1600
        const val TARGET_SPAN_Y = 900
    }

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

    fun selectInput(inputAppId: String) = command {
        platform.selectInput(inputAppId)
        _ui.update { it.copy(message = "Switched TV input") }
    }

    fun searchTv() {
        val query = _ui.value.searchText.trim()
        if (query.isBlank()) {
            _ui.update { it.copy(message = "Type something to search first") }
            return
        }
        command {
            val result = platform.searchOnTv(query)
            _ui.update { it.copy(message = result) }
        }
    }

    fun sendMusic() {
        val query = _ui.value.musicQuery.trim()
        if (query.isBlank()) {
            _ui.update { it.copy(message = "Enter a track or playlist name first") }
            return
        }
        command {
            // Android cannot hand another app's DRM audio to the TV, so we open
            // the TV's own YouTube search — the same honest path the legacy app used.
            val result = platform.openAppSearch("youtube.leanback.v4", query)
            _ui.update { it.copy(message = result) }
        }
    }

    fun castMedia() {
        val url = _ui.value.castUrl.trim()
        if (url.isBlank()) {
            _ui.update { it.copy(message = "Enter a media URL first") }
            return
        }
        command {
            val result = platform.playMediaUrl(url)
            _ui.update { it.copy(message = result) }
        }
    }

    fun openMirroring() = command {
        val result = platform.openScreenMirroring()
        _ui.update { it.copy(message = result) }
    }

    /** The Apps screen reuses its filter field as a TV search query. */
    fun searchTvFor(query: String) {
        val text = query.trim()
        if (text.isBlank()) {
            _ui.update { it.copy(message = "Type a word in Filter apps first") }
            return
        }
        searchTv(text)
    }

    private fun searchTv(query: String) {
        command {
            val result = platform.searchOnTv(query)
            _ui.update { it.copy(message = result) }
        }
    }

    fun toggleGroup(group: String) {
        val current = _ui.value.visibleGroups
        val updated = if (group in current) current - group else current + group
        if (updated.isEmpty()) {
            _ui.update { it.copy(message = "Keep at least one button group visible") }
            return
        }
        store.setVisibleGroups(updated)
        _ui.update { it.copy(visibleGroups = updated) }
    }

    fun play() = command { platform.mediaPlay() }
    fun pause() = command { platform.mediaPause() }
    fun stop() = command { platform.mediaStop() }
    fun rewind() = command { platform.mediaRewind() }
    fun fastForward() = command { platform.mediaFastForward() }

    private fun command(block: suspend () -> Unit) {
        if (!platform.isConnected()) {
            _ui.update { it.copy(message = "Not connected to a TV") }
            return
        }
        Haptics.tick(_ui.value.haptics)
        viewModelScope.launch {
            runCatching { block() }.onFailure { error ->
                val lost = error is IllegalStateException &&
                    error.message?.contains("Connection lost") == true
                _ui.update {
                    it.copy(
                        connectedHost = if (lost) null else it.connectedHost,
                        connectionMode = if (lost) null else it.connectionMode,
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

    override fun onCleared() {
        stopAirMouse()
        airMoves.close()
        super.onCleared()
    }
}
