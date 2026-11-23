package com.luma.tvremote.net

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

data class TvApp(
    val id: String,
    val title: String,
    val type: String,
    val icon: String? = null
)

/** MAC addresses reported by the TV, keyed by interface name (wired/wifi/p2p). */
data class TvNetworkInfo(val macAddresses: Map<String, String>) {
    /** Prefer the Ethernet MAC: that is usually the one visible in the LAN ARP table. */
    fun preferredMac(): String? = macAddresses["wiredInfo"]
        ?: macAddresses["wifiInfo"]
        ?: macAddresses["p2pInfo"]
        ?: macAddresses.values.firstOrNull()
}

sealed interface ConnectResult {
    data class Success(val clientKey: String) : ConnectResult
    data class Failure(val message: String) : ConnectResult
}

/**
 * Minimal LG webOS SSAP client for Android.
 *
 * Protocol details and the registration payload are based on the proven
 * Apache-2.0 implementation in home-assistant-libs/aiowebostv. See
 * docs/tvremote-protocol.md for attribution.
 */
class WebOsClient(
    private val host: String,
    private val storedKey: String?,
    private val onStatus: (String) -> Unit = {},
    private val onClientKey: (String) -> Unit = {},
    private val onDisconnected: () -> Unit = {},
    /** Base URL of the TV companion proxy, e.g. http://192.168.1.2:8081. */
    private val proxyBaseUrl: String? = null,
    private val initialProxyToken: String? = null
) {
    private val counter = AtomicInteger(0)
    private var proxyToken: String? = initialProxyToken
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    @Volatile
    private var mainSocket: WebSocket? = null

    @Volatile
    private var inputSocket: WebSocket? = null

    @Volatile
    private var httpClient: OkHttpClient? = null

    @Volatile
    private var connected = false

    @Volatile
    private var clientKey: String? = storedKey

    private var inputPort = 3000
    private var inputSecure = false

    suspend fun connect(): ConnectResult = withContext(Dispatchers.IO) {
        try {
            if (proxyBaseUrl != null) {
                onStatus("Connecting through TV companion…")
                httpClient = buildProxyClient()
                val info = proxyGet("/status")
                if (info.optString("app") != COMPANION_APP_ID) {
                    error("The host is not running the Luma TV companion")
                }
                // The companion is authoritative for its own token and pairing
                // key; the beacon may be a couple of seconds out of date.
                info.optString("token").takeIf { it.isNotBlank() }?.let { proxyToken = it }
                // Android's org.json can surface a JSON null as "null"; never
                // persist that as a pairing key.
                info.optString("clientKey").takeIf {
                    it.isNotBlank() && it != "null"
                }?.let { key ->
                    clientKey = key
                    onClientKey(key)
                }
                connected = true
                onStatus("Connected via TV companion")
                return@withContext ConnectResult.Success(clientKey.orEmpty())
            }

            onStatus("Connecting to $host…")
            val client = buildClient()
            httpClient = client

            mainSocket = openMainSocket(client)
            onStatus("Handshaking with TV…")
            handshake()

            onStatus("Opening input channel…")
            val socketPath = request(ENDPOINT_INPUT_SOCKET).getString("socketPath")
            openInputSocket(client, socketPath)

            // Best effort: enables standard webOS text input on most firmware.
            try {
                request(ENDPOINT_REGISTER_KEYBOARD, emptyMap(), type = "subscribe", timeoutMs = 5_000)
            } catch (_: Exception) {
                // Some firmware versions do not expose this service; ignore.
            }

            connected = true
            onStatus("Connected")
            ConnectResult.Success(clientKey.orEmpty())
        } catch (e: Exception) {
            close()
            ConnectResult.Failure(e.message ?: "Could not connect to TV")
        }
    }

    fun isConnected(): Boolean = connected

    fun close() {
        connected = false
        val failure = IllegalStateException("Connection closed")
        pending.values.forEach { it.completeExceptionally(failure) }
        pending.clear()
        mainSocket?.close(1000, "Done")
        inputSocket?.close(1000, "Done")
        mainSocket = null
        inputSocket = null
        httpClient?.dispatcher?.executorService?.shutdownNow()
        httpClient?.connectionPool?.evictAll()
        httpClient = null
    }

    suspend fun button(name: String) = sendInput("type:button\nname:$name\n\n")

    suspend fun move(dx: Int, dy: Int, down: Int = 0) =
        sendInput("type:move\ndx:$dx\ndy:$dy\ndown:$down\n\n")

    /**
     * Non-blocking fast path for sensor-driven pointer updates. The direct
     * webOS input socket is thread-safe and does not need a coroutine per
     * sample. Returns false for proxy connections so the caller can fall back
     * to the suspend path (an HTTP request is required there).
     */
    fun moveNow(dx: Int, dy: Int, down: Int = 0): Boolean {
        if (proxyBaseUrl != null || !connected) return false
        val socket = inputSocket ?: return false
        return try {
            socket.send("type:move\ndx:$dx\ndy:$dy\ndown:$down\n\n")
        } catch (_: Exception) {
            false
        }
    }

    suspend fun click() = sendInput("type:click\n\n")

    suspend fun scroll(dx: Int, dy: Int) =
        sendInput("type:scroll\ndx:$dx\ndy:$dy\n\n")

    suspend fun volumeUp() = request("audio/volumeUp")

    suspend fun volumeDown() = request("audio/volumeDown")

    suspend fun setMute(mute: Boolean) = request("audio/setMute", mapOf("mute" to mute))

    suspend fun channelUp() = request("tv/channelUp")

    suspend fun channelDown() = request("tv/channelDown")

    suspend fun powerOff() = sendOnly("system/turnOff")

    /** Software power-on. Only works when the TV is in network standby. */
    suspend fun powerOn() = request("system/turnOn")

    suspend fun networkInfo(): TvNetworkInfo? = try {
        val payload = request(ENDPOINT_NETWORK_INFO)
        val macs = linkedMapOf<String, String>()
        for (key in listOf("wiredInfo", "wifiInfo", "p2pInfo")) {
            payload.optJSONObject(key)
                ?.optString("macAddress")
                ?.takeIf { it.isNotBlank() }
                ?.let { macs[key] = it }
        }
        if (macs.isEmpty()) null else TvNetworkInfo(macs)
    } catch (_: Exception) {
        null
    }

    suspend fun play() = request("media.controls/play")

    suspend fun pause() = request("media.controls/pause")

    suspend fun stop() = request("media.controls/stop")

    suspend fun rewind() = request("media.controls/rewind")

    suspend fun fastForward() = request("media.controls/fastForward")

    suspend fun apps(): List<TvApp> {
        val payload = request("com.webos.applicationManager/listLaunchPoints")
        val array = payload.optJSONArray("launchPoints") ?: return emptyList()
        val result = mutableListOf<TvApp>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id")
            if (id.isBlank()) continue
            result += TvApp(
                id = id,
                title = item.optString("title").ifBlank { id },
                type = item.optString("appType").ifBlank { "app" },
                icon = item.optString("icon").ifBlank { null }
            )
        }
        return result.distinctBy { it.id }.sortedBy { it.title.lowercase() }
    }

    suspend fun launchApp(id: String) = request("system.launcher/launch", mapOf("id" to id))

    suspend fun typeText(text: String) =
        request(ENDPOINT_INSERT_TEXT, mapOf("text" to text, "replace" to 0))

    suspend fun deleteCharacters(count: Int) =
        request(ENDPOINT_DELETE_CHARS, mapOf("count" to count))

    suspend fun sendEnter() = request(ENDPOINT_SEND_ENTER)

    /** Current audio state: volume, mute, and sound output (e.g. tv_speaker). */
    suspend fun audioStatus(): JSONObject = request("audio/getStatus")

    /** Switch TV sound output. tv_speaker works on webOS 4.1; others vary by firmware. */
    suspend fun setSoundOutput(output: String) =
        request("audio/changeSoundOutput", mapOf("output" to output))

    /** Launch an HDMI / input app such as com.webos.app.hdmi1 or hdmi2. */
    suspend fun selectInput(inputAppId: String) =
        request("system.launcher/launch", mapOf("id" to inputAppId))

    /** Close a running app (some firmware denies this to third-party clients). */
    suspend fun closeApp(id: String) =
        request("system.launcher/close", mapOf("id" to id))

    /** Ask the TV's media player to open a URL served on the LAN. */
    suspend fun playMediaUrl(url: String) =
        request(
            "system.launcher/launch",
            mapOf(
                "id" to "com.webos.app.mediadiscovery",
                "params" to mapOf("uri" to url, "mediaUrl" to url)
            )
        )

    /** Open the TV's built-in screen mirroring receiver. */
    suspend fun openScreenMirroring() =
        request("system.launcher/launch", mapOf("id" to "sooltrstudio.screenmirroring.receiver"))

    suspend fun toast(message: String) =
        request("system.notifications/createToast", mapOf("message" to message))

    suspend fun foregroundApp(): String? = try {
        request("com.webos.applicationManager/getForegroundAppInfo").optString("appId")
            .takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    /**
     * Opens an app's own search UI with a deep link where supported.
     * Returns an honest description of which path was attempted.
     */
    suspend fun searchInApp(appId: String, query: String): String {
        if (query.isBlank()) return "Type something to search"
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val candidates = when {
            appId.contains("youtube", ignoreCase = true) -> listOf(
                "youtube://results?search_query=$encoded",
                "youtube://search?q=$encoded"
            )
            appId.contains("netflix", ignoreCase = true) -> listOf(
                "nflix://search?query=$encoded"
            )
            else -> emptyList()
        }
        for (uri in candidates) {
            try {
                request(
                    "system.launcher/launch",
                    mapOf("id" to appId, "params" to mapOf("uri" to uri))
                )
                return "Opened $appId search for “$query”"
            } catch (_: Exception) {
                // Try the next URI format.
            }
        }
        return try {
            launchApp(appId)
            "Opened $appId. Use its search box, then the Keyboard tab to type “$query”."
        } catch (e: Exception) {
            "Could not open $appId: ${e.message ?: "launch failed"}"
        }
    }

    /**
     * Hand the currently-playing phone track off to the TV. Uses the TV's own
     * apps (YouTube / Spotify / system search) because Android cannot stream
     * another app's DRM audio to the TV from here. Returns an honest status.
     */
    suspend fun sendMusicToTv(query: String, phoneApp: String?): String {
        if (query.isBlank()) return "Play a song on your phone first"
        val app = phoneApp.orEmpty()
        return when {
            app.contains("youtube", ignoreCase = true) ||
                app.contains("music", ignoreCase = true) ->
                searchInApp("youtube.leanback.v4", query)

            app.contains("spotify", ignoreCase = true) -> {
                // webOS Spotify app id varies by region; try known ids, then system search.
                for (id in listOf("spotify-beehive", "com.spotify.webos", "spotify")) {
                    try {
                        request("system.launcher/launch", mapOf("id" to id))
                        return "Opened Spotify on TV for “$query”. Use its search box to pick the song."
                    } catch (_: Exception) {
                        // try next id
                    }
                }
                searchOnTv(query)
            }

            else -> searchOnTv(query)
        }
    }

    /** Returns a short description of which search path the TV accepted. */
    suspend fun searchOnTv(query: String): String {
        if (query.isBlank()) return "Type something to search"
        return try {
            request("com.webos.service.search/search", mapOf("query" to query))
            "TV search opened for “$query”"
        } catch (_: Exception) {
            try {
                request(
                    "system.launcher/launch",
                    mapOf("id" to "com.webos.app.search", "params" to mapOf("query" to query))
                )
                "Search app launched for “$query”"
            } catch (_: Exception) {
                "Search is not exposed by this TV firmware. Use the Keyboard tab in TV search."
            }
        }
    }

    private suspend fun handshake() {
        val hello = CompletableDeferred<JSONObject>()
        pending["hello"] = hello
        val ws = mainSocket ?: error("Main socket missing")
        sendJson(ws, JSONObject().put("id", "hello").put("type", "hello").put("payload", JSONObject()))
        val helloResponse = withTimeout(10_000) { hello.await() }
        if (helloResponse.optString("type") != "hello") {
            error("Unexpected TV hello response")
        }

        // Newer webOS firmware expects system info before registration.
        request("system/getSystemInfo", timeoutMs = 10_000)

        if (!tryRegister(signed = true) && !tryRegister(signed = false)) {
            error("TV rejected the pairing request")
        }
        if (clientKey.isNullOrBlank()) {
            error("TV did not return a pairing key")
        }
    }

    private suspend fun tryRegister(signed: Boolean): Boolean {
        val deferred = CompletableDeferred<JSONObject>()
        pending[REGISTER_ID] = deferred
        val ws = mainSocket ?: return false
        val message = JSONObject()
            .put("id", REGISTER_ID)
            .put("type", "register")
            .put("payload", registrationPayload(signed))
        sendJson(ws, message)

        val response = withTimeout(PAIR_TIMEOUT_MS) { deferred.await() }
        return when (response.optString("type")) {
            "registered" -> {
                response.optJSONObject("payload")
                    ?.optString("client-key")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { key ->
                        clientKey = key
                        onClientKey(key)
                    }
                true
            }

            "error" -> {
                onStatus("Pairing error: ${response.optString("error")}")
                false
            }

            else -> false
        }
    }

    private fun registrationPayload(signed: Boolean): JSONObject {
        val basePermissions = listOf(
            "LAUNCH", "LAUNCH_WEBAPP", "APP_TO_APP", "CLOSE", "TEST_OPEN", "TEST_PROTECTED",
            "CONTROL_AUDIO", "CONTROL_DISPLAY", "CONTROL_INPUT_JOYSTICK",
            "CONTROL_INPUT_MEDIA_RECORDING", "CONTROL_INPUT_MEDIA_PLAYBACK", "CONTROL_INPUT_TV",
            "CONTROL_POWER", "CONTROL_TV_SCREEN", "READ_APP_STATUS", "READ_CURRENT_CHANNEL",
            "READ_INPUT_DEVICE_LIST", "READ_NETWORK_STATE", "READ_RUNNING_APPS",
            "READ_TV_CHANNEL_LIST", "WRITE_NOTIFICATION_TOAST", "READ_POWER_STATE",
            "READ_COUNTRY_INFO", "CONTROL_INPUT_TEXT", "CONTROL_MOUSE_AND_KEYBOARD",
            "READ_INSTALLED_APPS", "READ_SETTINGS"
        )
        val signedPermissions = listOf(
            "TEST_SECURE", "CONTROL_INPUT_TEXT", "CONTROL_MOUSE_AND_KEYBOARD",
            "READ_INSTALLED_APPS", "READ_LGE_SDX", "READ_NOTIFICATIONS", "SEARCH",
            "WRITE_SETTINGS", "WRITE_NOTIFICATION_ALERT", "CONTROL_POWER",
            "READ_CURRENT_CHANNEL", "READ_RUNNING_APPS", "READ_UPDATE_INFO",
            "UPDATE_FROM_REMOTE_APP", "READ_LGE_TV_INPUT_EVENTS", "READ_TV_CURRENT_TIME"
        )

        val manifest = JSONObject()
            .put("appVersion", "1.1")
            .put("manifestVersion", 1)
            .put("permissions", JSONArray(basePermissions))

        if (signed) {
            manifest.put(
                "signatures",
                JSONArray().put(
                    JSONObject()
                        .put("signature", SIGNATURE)
                        .put("signatureVersion", 1)
                )
            )
            manifest.put(
                "signed",
                JSONObject()
                    .put("appId", "com.lge.test")
                    .put("created", "20140509")
                    .put(
                        "localizedAppNames",
                        JSONObject()
                            .put("", "LG Remote App")
                            .put("ko-KR", "LG 리모트 앱")
                            .put("zxx-XX", "Lg R\u00f8mot A\u00f8pp")
                    )
                    .put("localizedVendorNames", JSONObject().put("", "LG Electronics"))
                    .put("permissions", JSONArray(signedPermissions))
                    .put("serial", "2f930e2d2cfe083771f68e4fe7bb07")
                    .put("vendorId", "com.lge")
            )
        } else {
            manifest.put("permissions", JSONArray((basePermissions + signedPermissions).distinct()))
        }

        val payload = JSONObject()
            .put("forcePairing", false)
            .put("manifest", manifest)
            .put("pairingType", "PROMPT")
        clientKey?.let { payload.put("client-key", it) }
        return payload
    }

    private suspend fun request(
        uri: String,
        payload: Map<String, Any?> = emptyMap(),
        type: String = "request",
        timeoutMs: Long = REQUEST_TIMEOUT_MS
    ): JSONObject = withContext(Dispatchers.IO) {
        if (proxyBaseUrl != null) {
            val result = proxyCommand(
                JSONObject()
                    .put("uri", uri)
                    .put("payload", JSONObject(payload))
            )
            // The companion returns the SSAP payload directly; wrap it so the
            // existing response validation works unchanged.
            return@withContext parseResponse(
                JSONObject().put("type", "response").put("payload", result)
            )
        }
        val id = nextId()
        val deferred = CompletableDeferred<JSONObject>()
        pending[id] = deferred
        val ws = mainSocket ?: throw IllegalStateException("Not connected")
        val message = JSONObject()
            .put("id", id)
            .put("type", type)
            .put("uri", "ssap://$uri")
            .put("payload", JSONObject(payload))
        sendJson(ws, message)
        val response = withTimeout(timeoutMs) { deferred.await() }
        parseResponse(response)
    }

    private suspend fun sendOnly(
        uri: String,
        payload: Map<String, Any?> = emptyMap(),
        type: String = "request"
    ) = withContext(Dispatchers.IO) {
        if (proxyBaseUrl != null) {
            proxyCommand(JSONObject().put("uri", uri).put("payload", JSONObject(payload)))
            return@withContext
        }
        val ws = mainSocket ?: throw IllegalStateException("Not connected")
        val message = JSONObject()
            .put("id", nextId())
            .put("type", type)
            .put("uri", "ssap://$uri")
            .put("payload", JSONObject(payload))
        sendJson(ws, message)
    }

    private suspend fun openMainSocket(client: OkHttpClient): WebSocket {
        val urls = listOf("wss://$host:3001", "ws://$host:3000")
        var lastError: Exception? = null
        for (url in urls) {
            val opened = CompletableDeferred<Unit>()
            try {
                val ws = client.newWebSocket(
                    Request.Builder().url(url).build(),
                    mainListener(opened)
                )
                withTimeout(CONNECT_TIMEOUT_MS) { opened.await() }
                inputSecure = url.startsWith("wss://")
                inputPort = if (inputSecure) 3001 else 3000
                return ws
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No webOS port responded")
    }

    private suspend fun openInputSocket(client: OkHttpClient, socketPath: String) {
        val url = when {
            socketPath.startsWith("ws://") || socketPath.startsWith("wss://") -> socketPath
            socketPath.startsWith("/") ->
                "${if (inputSecure) "wss" else "ws"}://$host:$inputPort$socketPath"
            else ->
                "${if (inputSecure) "wss" else "ws"}://$host:$inputPort/$socketPath"
        }
        val opened = CompletableDeferred<Unit>()
        val ws = client.newWebSocket(
            Request.Builder().url(url).build(),
            inputListener(opened)
        )
        withTimeout(CONNECT_TIMEOUT_MS) { opened.await() }
        inputSocket = ws
    }

    private suspend fun sendInput(text: String) = withContext(Dispatchers.IO) {
        if (proxyBaseUrl != null) {
            val result = proxyCommand(JSONObject().put("input", text))
            if (!result.optBoolean("returnValue", false)) {
                error(result.optString("errorText").ifBlank { "Companion input failed" })
            }
            return@withContext
        }
        val ws = inputSocket ?: throw IllegalStateException("Input channel not ready")
        if (!ws.send(text)) error("Input channel is closed")
    }

    private suspend fun proxyGet(path: String): JSONObject = withContext(Dispatchers.IO) {
        val client = httpClient ?: error("HTTP client not ready")
        val request = Request.Builder().url(proxyBaseUrl!! + path).get().build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Companion HTTP ${response.code}: ${body.take(120)}")
            JSONObject(body)
        }
    }

    private suspend fun proxyCommand(body: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val client = httpClient ?: error("Not connected to the TV companion")
        val json = body
            .put("token", proxyToken.orEmpty())
            .put("key", clientKey.orEmpty())
            .toString()
        val request = Request.Builder()
            .url("$proxyBaseUrl/command")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("Companion HTTP ${response.code}: ${text.take(120)}")
                }
                JSONObject(text)
            }
        } catch (e: java.net.ConnectException) {
            // The companion service died or the TV rebooted. Without this the
            // app keeps showing "Connected" while every command silently fails,
            // which looks exactly like a broken remote to the user.
            if (connected) {
                connected = false
                onDisconnected()
            }
            throw e
        }
    }

    private fun mainListener(opened: CompletableDeferred<Unit>) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            opened.complete(Unit)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleMessage(text)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            opened.completeExceptionally(t)
            if (connected) onDisconnected()
            connected = false
            failPending(t)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            if (connected) onDisconnected()
            connected = false
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (connected) onDisconnected()
            connected = false
            failPending(IllegalStateException("TV closed the connection"))
        }
    }

    private fun inputListener(opened: CompletableDeferred<Unit>) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            opened.complete(Unit)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            opened.completeExceptionally(t)
            if (connected) onDisconnected()
            connected = false
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (connected) onDisconnected()
            connected = false
        }
    }

    private fun handleMessage(text: String) {
        val message = try {
            JSONObject(text)
        } catch (_: Exception) {
            return
        }
        val type = message.optString("type")

        // webOS hello responses have no "id" field; match them by type.
        if (type == "hello") {
            pending.remove("hello")?.complete(message)
            return
        }

        val id = message.opt("id")?.toString() ?: return

        if (id == REGISTER_ID) {
            when (type) {
                "registered", "error" -> {
                    pending.remove(id)?.complete(message)
                    return
                }

                "response" -> {
                    val pairingType = message.optJSONObject("payload")?.optString("pairingType")
                    if (pairingType == "PROMPT") {
                        onStatus("Approve the pairing prompt on your TV…")
                        return
                    }
                }
            }
        }

        if (type == "response" || type == "registered" || type == "error" || type == "hello") {
            pending.remove(id)?.complete(message)
        }
    }

    private fun parseResponse(response: JSONObject): JSONObject {
        val payload = response.optJSONObject("payload")
            ?: throw IllegalStateException("Empty TV response")
        if (response.optString("type") == "error") {
            throw IllegalStateException(response.optString("error").ifBlank { "TV command failed" })
        }
        val ok = payload.optBoolean("returnValue") ||
            payload.optBoolean("subscribed") ||
            payload.optBoolean("subscription")
        if (!ok && !payload.has("returnValue") && !payload.has("subscribed") && !payload.has("subscription")) {
            throw IllegalStateException("Unexpected TV response: $payload")
        }
        if (!ok) throw IllegalStateException("TV command failed: $payload")
        return payload
    }

    private fun failPending(error: Throwable) {
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
    }

    private fun sendJson(ws: WebSocket, json: JSONObject) {
        if (!ws.send(json.toString())) error("TV connection is closed")
    }

    private fun nextId(): String = "cmd_${counter.incrementAndGet()}"

    /** HTTP-only client for the companion proxy; finite timeouts avoid hangs. */
    private fun buildProxyClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private fun buildClient(): OkHttpClient {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf(trustManager), SecureRandom())
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 2_500L
        private const val REQUEST_TIMEOUT_MS = 20_000L
        private const val PAIR_TIMEOUT_MS = 90_000L
        private const val REGISTER_ID = "register_0"
        private const val COMPANION_APP_ID = "com.luma.tvcompanion"

        private const val ENDPOINT_NETWORK_INFO = "com.webos.service.connectionmanager/getinfo"
        private const val ENDPOINT_INPUT_SOCKET = "com.webos.service.networkinput/getPointerInputSocket"
        private const val ENDPOINT_REGISTER_KEYBOARD = "com.webos.service.ime/registerRemoteKeyboard"
        private const val ENDPOINT_INSERT_TEXT = "com.webos.service.ime/insertText"
        private const val ENDPOINT_DELETE_CHARS = "com.webos.service.ime/deleteCharacters"
        private const val ENDPOINT_SEND_ENTER = "com.webos.service.ime/sendEnterKey"

        private const val SIGNATURE =
            "eyJhbGdvcml0aG0iOiJSU0EtU0hBMjU2Iiwia2V5SWQiOiJ0ZXN0LXNpZ25pbm" +
                "ctY2VydCIsInNpZ25hdHVyZVZlcnNpb24iOjF9.hrVRgjCwXVvE2OOSpDZ58hR" +
                "+59aFNwYDyjQgKk3auukd7pcegmE2CzPCa0bJ0ZsRAcKkCTJrWo5iDzNhMBWRy" +
                "aMOv5zWSrthlf7G128qvIlpMT0YNY+n/FaOHE73uLrS/g7swl3/qH/BGFG2Hu4" +
                "RlL48eb3lLKqTt2xKHdCs6Cd4RMfJPYnzgvI4BNrFUKsjkcu+WD4OO2A27Pq1n" +
                "50cMchmcaXadJhGrOqH5YmHdOCj5NSHzJYrsW0HPlpuAx/ECMeIZYDh6RMqaFM" +
                "2DXzdKX9NmmyqzJ3o/0lkk/N97gfVRLW5hA29yeAwaCViZNCP8iC9aO0q9fQoj" +
                "oa7NQnAtw=="
    }
}
