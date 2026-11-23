package com.luma.tvremote2.platform.webos

/**
 * Protocol constants and dependency inventory for the LG webOS layer.
 *
 * These are transcribed from the legacy WebOsClient / CompanionScanner so the
 * Phase 2 migration does not have to guess wire details. No behaviour is
 * implemented here yet — this is the isolated boundary only.
 */
object WebOsProtocol {
    const val SSAP_TLS_PORT = 3001
    const val SSAP_PLAIN_PORT = 3000
    const val COMPANION_STATUS_PORT = 8081
    const val COMPANION_BEACON_PORT = 50550

    const val COMPANION_APP_ID = "com.luma.tvcompanion"

    // SSAP endpoints used by the legacy client (verified against real TV).
    const val ENDPOINT_INPUT_SOCKET = "com.webos.service.networkinput/getPointerInputSocket"
    const val ENDPOINT_NETWORK_INFO = "com.webos.service.connectionmanager/getinfo"
    const val ENDPOINT_REGISTER_KEYBOARD = "com.webos.service.ime/registerRemoteKeyboard"
    const val ENDPOINT_INSERT_TEXT = "com.webos.service.ime/insertText"
    const val ENDPOINT_DELETE_CHARS = "com.webos.service.ime/deleteCharacters"
    const val ENDPOINT_SEND_ENTER = "com.webos.service.ime/sendEnterKey"
    const val ENDPOINT_LAUNCH_POINTS = "com.webos.applicationManager/listLaunchPoints"
    const val ENDPOINT_LAUNCH_APP = "system.launcher/launch"
    const val ENDPOINT_AUDIO_STATUS = "audio/getStatus"
    const val ENDPOINT_VOLUME_UP = "audio/volumeUp"
    const val ENDPOINT_VOLUME_DOWN = "audio/volumeDown"
    const val ENDPOINT_SET_MUTE = "audio/setMute"

    // Input socket frames are newline-delimited text, not JSON.
    const val FRAME_BUTTON = "type:button\nname:%s\n\n"
    const val FRAME_CLICK = "type:click\n\n"
    const val FRAME_MOVE = "type:move\ndx:%d\ndy:%d\n\n"
    const val FRAME_SCROLL = "type:scroll\ndx:%d\ndy:%d\n\n"

    /**
     * Legacy WebOsClient dependencies (must be preserved on migration):
     *  - OkHttpClient, two WebSocket connections (main SSAP + input)
     *  - wss://host:3001 first, ws://host:3000 fallback, self-signed TLS trusted
     *  - register handshake with permissions manifest, client-key per TV
     *  - companion proxy: GET /status, POST /command over HTTP 8081
     *  - JSONObject for payloads, coroutines + CompletableDeferred for replies
     *  - no Origin header on socket upgrade
     *
     * LG Magic Remote note:
     *  luna://com.webos.service.mrcu reads sensors from the PHYSICAL Magic
     *  Remote on the TV side. It is not used by a phone remote. The sensor2 API
     *  is webOS 24+ only; the legacy sensor API is deprecated. Our Air Mouse uses phone
     *  SensorManager (TYPE_GAME_ROTATION_VECTOR) and sends SSAP move frames,
     *  which is the correct architecture for this app.
     */
    val documentedDependencies = listOf(
        "okhttp OkHttpClient (main + input WebSocket)",
        "org.json JSONObject payloads",
        "kotlinx.coroutines CompletableDeferred / withContext(IO)",
        "Android Keystore via SecureTvStore (no plain-text keys)",
        "CompanionScanner UDP 50550 + HTTP 8081/status",
        "AirMouseController SensorManager relative deltas"
    )
}
