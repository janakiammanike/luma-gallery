package com.luma.tvremote2.platform.webos

import com.luma.tvremote2.data.SecureTvStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
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

/**
 * Direct LG webOS SSAP transport — used when the TV companion is not running.
 *
 * Ported from the legacy WebOsClient's direct branch (the same code that
 * originally paired with this TV). It uses two WebSockets:
 *   - main SSAP socket: wss://host:3001, falls back to ws://host:3000
 *   - pointer input socket: opened via getPointerInputSocket
 *
 * The TV uses a self-signed certificate, so the trust manager accepts it,
 * exactly as the legacy client did. No Origin header is sent on upgrade.
 */
class DirectWebOsPlatform(
    private val store: SecureTvStore,
    private val onStatus: (String) -> Unit = {}
) : WebOsPlatform {

    private val counter = AtomicInteger(0)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

    @Volatile
    private var client: OkHttpClient? = null

    @Volatile
    private var mainSocket: WebSocket? = null

    @Volatile
    private var inputSocket: WebSocket? = null

    @Volatile
    private var connected = false

    @Volatile
    private var currentHost: String? = null

    @Volatile
    private var clientKey: String? = null

    private var inputSecure = false
    private var inputPort = WebOsProtocol.SSAP_PLAIN_PORT

    override fun isConnected(): Boolean = connected && mainSocket != null

    override fun connectedHost(): String? = if (isConnected()) currentHost else null

    override suspend fun connect(device: WebOsDevice): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val host = device.host.trim()
            onStatus("Connecting to $host…")
            val http = buildClient()
            client = http

            clientKey = store.clientKeyFor(host)?.takeIf {
                it.isNotBlank() && it != "null"
            }

            mainSocket = openMainSocket(http, host)
            onStatus("Handshaking with TV…")
            handshake()

            val newKey = clientKey
            if (!newKey.isNullOrBlank()) {
                store.saveClientKey(host, newKey)
                store.setLastHost(host)
            }

            onStatus("Opening input channel…")
            val socketPath = request(WebOsProtocol.ENDPOINT_INPUT_SOCKET).getString("socketPath")
            openInputSocket(http, host, socketPath)

            // Enables standard webOS text input on most firmware versions.
            try {
                request(
                    WebOsProtocol.ENDPOINT_REGISTER_KEYBOARD,
                    type = "subscribe",
                    timeoutMs = 5_000
                )
            } catch (_: Exception) {
                // Some firmware does not expose this service; keyboard may still work.
            }

            currentHost = host
            connected = true
            onStatus("Connected directly to TV")
            Result.success(Unit)
        } catch (e: Exception) {
            close()
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        close()
        onStatus("Not connected")
    }

    private fun close() {
        connected = false
        currentHost = null
        val failure = IllegalStateException("Connection closed")
        pending.values.forEach { it.completeExceptionally(failure) }
        pending.clear()
        mainSocket?.close(1000, "Done")
        inputSocket?.close(1000, "Done")
        mainSocket = null
        inputSocket = null
        client?.connectionPool?.evictAll()
        client = null
    }

    // ---- handshake ---------------------------------------------------------------------

    private suspend fun handshake() {
        val hello = CompletableDeferred<JSONObject>()
        pending["hello"] = hello
        val ws = mainSocket ?: error("Main socket missing")
        ws.send(JSONObject().put("id", "hello").put("type", "hello").put("payload", JSONObject()).toString())
        val response = withTimeout(10_000) { hello.await() }
        if (response.optString("type") != "hello") error("Unexpected TV hello response")

        // Newer firmware expects this before registration; harmless on webOS 4.1.
        try {
            request("system/getSystemInfo", timeoutMs = 8_000)
        } catch (_: Exception) {
            // Best effort only.
        }

        if (!tryRegister(signed = true) && !tryRegister(signed = false)) {
            error("TV rejected the pairing request — accept the prompt on the TV")
        }
        if (clientKey.isNullOrBlank()) error("TV did not return a pairing key")
    }

    private suspend fun tryRegister(signed: Boolean): Boolean {
        val deferred = CompletableDeferred<JSONObject>()
        pending[REGISTER_ID] = deferred
        val ws = mainSocket ?: return false
        val message = JSONObject()
            .put("id", REGISTER_ID)
            .put("type", "register")
            .put("payload", registrationPayload(signed))
        ws.send(message.toString())

        val response = try {
            withTimeout(PAIR_TIMEOUT_MS) { deferred.await() }
        } catch (e: Exception) {
            pending.remove(REGISTER_ID)
            return false
        }
        return when (response.optString("type")) {
            "registered" -> {
                response.optJSONObject("payload")
                    ?.optString("client-key")
                    ?.takeIf { it.isNotBlank() && it != "null" }
                    ?.let { clientKey = it }
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

        if (signed) {
            manifest.put("permissions", JSONArray(basePermissions))
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
        clientKey?.takeIf { it.isNotBlank() && it != "null" }?.let {
            payload.put("client-key", it)
        }
        return payload
    }

    // ---- socket plumbing ----------------------------------------------------------------

    private suspend fun openMainSocket(http: OkHttpClient, host: String): WebSocket {
        var lastError: Exception? = null
        for (url in listOf("wss://$host:${WebOsProtocol.SSAP_TLS_PORT}", "ws://$host:${WebOsProtocol.SSAP_PLAIN_PORT}")) {
            val opened = CompletableDeferred<Unit>()
            try {
                val ws = http.newWebSocket(Request.Builder().url(url).build(), socketListener(opened))
                withTimeout(CONNECT_TIMEOUT_MS) { opened.await() }
                inputSecure = url.startsWith("wss://")
                inputPort = if (inputSecure) WebOsProtocol.SSAP_TLS_PORT else WebOsProtocol.SSAP_PLAIN_PORT
                return ws
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("No webOS port responded on $host")
    }

    private suspend fun openInputSocket(http: OkHttpClient, host: String, socketPath: String) {
        val url = when {
            socketPath.startsWith("ws://") || socketPath.startsWith("wss://") -> socketPath
            socketPath.startsWith("/") ->
                "${if (inputSecure) "wss" else "ws"}://$host:$inputPort$socketPath"
            else ->
                "${if (inputSecure) "wss" else "ws"}://$host:$inputPort/$socketPath"
        }
        val opened = CompletableDeferred<Unit>()
        val ws = http.newWebSocket(Request.Builder().url(url).build(), socketListener(opened))
        withTimeout(CONNECT_TIMEOUT_MS) { opened.await() }
        inputSocket = ws
    }

    private fun socketListener(opened: CompletableDeferred<Unit>) = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            opened.complete(Unit)
        }

        override fun onMessage(webSocket: WebSocket, text: String) = handleMessage(text)

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            opened.completeExceptionally(t)
            connected = false
            failPending(t)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            connected = false
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            connected = false
            failPending(IllegalStateException("TV closed the connection"))
        }
    }

    private fun handleMessage(text: String) {
        val message = try {
            JSONObject(text)
        } catch (_: Exception) {
            return
        }
        val type = message.optString("type")
        if (type == "hello") {
            pending.remove("hello")?.complete(message)
            return
        }
        val id = message.opt("id")?.toString() ?: return
        if (id == REGISTER_ID && (type == "registered" || type == "error")) {
            pending.remove(id)?.complete(message)
            return
        }
        if (id == REGISTER_ID && type == "response") {
            val pairingType = message.optJSONObject("payload")?.optString("pairingType")
            if (pairingType == "PROMPT") onStatus("Approve the pairing prompt on your TV…")
            return
        }
        if (type == "response" || type == "registered" || type == "error") {
            pending.remove(id)?.complete(message)
        }
    }

    private fun failPending(error: Throwable) {
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
    }

    private suspend fun request(
        uri: String,
        payload: Map<String, Any?> = emptyMap(),
        type: String = "request",
        timeoutMs: Long = REQUEST_TIMEOUT_MS
    ): JSONObject = withContext(Dispatchers.IO) {
        val id = "cmd_${counter.incrementAndGet()}"
        val deferred = CompletableDeferred<JSONObject>()
        pending[id] = deferred
        val ws = mainSocket ?: throw IllegalStateException("Not connected to TV")
        val message = JSONObject()
            .put("id", id)
            .put("type", type)
            .put("uri", "ssap://$uri")
            .put("payload", JSONObject(payload))
        if (!ws.send(message.toString())) throw IllegalStateException("TV connection is closed")
        parseResponse(withTimeout(timeoutMs) { deferred.await() })
    }

    private suspend fun sendInput(frame: String) = withContext(Dispatchers.IO) {
        val ws = inputSocket ?: throw IllegalStateException("Input channel not ready")
        if (!ws.send(frame)) throw IllegalStateException("Input channel is closed")
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
        if (!ok) throw IllegalStateException("TV command failed: $payload")
        return payload
    }

    private fun buildClient(): OkHttpClient {
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
        }
        val ssl = SSLContext.getInstance("TLS")
        ssl.init(null, arrayOf(trustManager), SecureRandom())
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(0, TimeUnit.MILLISECONDS)
            .pingInterval(5, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .sslSocketFactory(ssl.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    // ---- WebOsPlatform ------------------------------------------------------------------

    override suspend fun button(name: String) = sendInput("type:button\nname:$name\n\n")
    override suspend fun move(dx: Int, dy: Int) = sendInput("type:move\ndx:$dx\ndy:$dy\ndown:0\n\n")
    override suspend fun click() = sendInput("type:click\n\n")
    override suspend fun scroll(dx: Int, dy: Int) = sendInput("type:scroll\ndx:$dx\ndy:$dy\n\n")

    override suspend fun typeText(text: String) {
        request(WebOsProtocol.ENDPOINT_INSERT_TEXT, mapOf("text" to text, "replace" to 0))
    }

    override suspend fun deleteCharacters(count: Int) {
        request(WebOsProtocol.ENDPOINT_DELETE_CHARS, mapOf("count" to count))
    }

    override suspend fun sendEnter() {
        request(WebOsProtocol.ENDPOINT_SEND_ENTER)
    }

    override suspend fun apps(): List<WebOsApp> {
        val payload = request(WebOsProtocol.ENDPOINT_LAUNCH_POINTS)
        val array = payload.optJSONArray("launchPoints") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optString("id")
            if (id.isBlank()) return@mapNotNull null
            WebOsApp(
                id = id,
                title = item.optString("title").ifBlank { id },
                type = item.optString("appType").ifBlank { "app" },
                icon = item.optString("icon").ifBlank { null }
            )
        }.distinctBy { it.id }.sortedBy { it.title.lowercase() }
    }

    override suspend fun launchApp(id: String) {
        request(WebOsProtocol.ENDPOINT_LAUNCH_APP, mapOf("id" to id))
    }

    override suspend fun searchOnTv(query: String): String = WebOsOptionalFeatures.searchOnTv(
        request = { uri, payload, timeout -> request(uri, payload, timeoutMs = timeout) },
        apps = { apps() },
        query = query
    )

    override suspend fun openAppSearch(appId: String, query: String): String =
        WebOsOptionalFeatures.openAppSearch(
            request = { uri, payload, timeout -> request(uri, payload, timeoutMs = timeout) },
            apps = { apps() },
            keyword = appId,
            query = query
        )

    override suspend fun selectInput(inputAppId: String) {
        request(WebOsProtocol.ENDPOINT_LAUNCH_APP, mapOf("id" to inputAppId))
    }

    override suspend fun playMediaUrl(url: String): String = WebOsOptionalFeatures.playMediaUrl(
        request = { uri, payload, timeout -> request(uri, payload, timeoutMs = timeout) },
        apps = { apps() },
        url = url
    )

    override suspend fun openScreenMirroring(): String =
        WebOsOptionalFeatures.openScreenMirroring(
            request = { uri, payload, timeout -> request(uri, payload, timeoutMs = timeout) },
            apps = { apps() }
        )

    override suspend fun audioVolume(): Int? {
        val payload = request(WebOsProtocol.ENDPOINT_AUDIO_STATUS)
        val status = payload.optJSONObject("volumeStatus") ?: payload
        val volume = status.optInt("volume", -1)
        return if (volume >= 0) volume else null
    }

    override suspend fun volumeUp() { request(WebOsProtocol.ENDPOINT_VOLUME_UP) }
    override suspend fun volumeDown() { request(WebOsProtocol.ENDPOINT_VOLUME_DOWN) }
    override suspend fun setMute(mute: Boolean) {
        request(WebOsProtocol.ENDPOINT_SET_MUTE, mapOf("mute" to mute))
    }
    override suspend fun channelUp() { request("tv/channelUp") }
    override suspend fun channelDown() { request("tv/channelDown") }
    override suspend fun powerOff() { request("system/turnOff") }
    override suspend fun powerOn() { request("system/turnOn") }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 3_000L
        private const val REQUEST_TIMEOUT_MS = 15_000L
        private const val PAIR_TIMEOUT_MS = 60_000L
        private const val REGISTER_ID = "register_0"

        private const val SIGNATURE =
            "eyJhbGdvcml0aG0iOiJSU0EtU0hBMjU2Iiwia2V5SWQiOiJ0ZXN0LXNpZ25p" +
                "ctY2VydCIsInNpZ25hdHVyZVZlcnNpb24iOjF9.hrVRgjCwXVvE2OOSpDZ58hR" +
                "+59aFNwYDyjQgKk3auukd7pcegmE2CzPCa0bJ0ZsRAcKkCTJrWo5iDzNhMBWRy" +
                "aMOv5zWSrthlf7G128qvIlpMT0YNY+n/FaOHE73uLrS/g7swl3/qH/BGFG2Hu4" +
                "RlL48eb3lLKqTt2xKHdCs6Cd4RMfJPYnzgvI4BNrFUKsjkcu+WD4OO2A27Pq1n" +
                "50cMchmcaXadJhGrOqH5YmHdOCj5NSHzJYrsW0HPlpuAx/ECMeIZYDh6RMqaFM" +
                "2DXzdKX9NmmyqzJ3o/0lkk/N97gfVRLW5hA29yeAwaCViZNCP8iC9aO0q9fQoj" +
                "oa7NQnAtw=="
    }
}
