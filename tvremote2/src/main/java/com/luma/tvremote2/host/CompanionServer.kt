package com.luma.tvremote2.host

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

private const val HOST_PATH = "/luma/companion/v1"
private const val WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
private const val MAX_INVALID_BEFORE_CLOSE = 5
private const val RATE_WINDOW_MS = 10000
private const val MAX_MESSAGES_PER_WINDOW = 20

private val ALLOWED_ACTIONS = setOf("diagnostics.refresh", "browser.open", "browser.close")
private val RESERVED_ACTIONS = setOf("app.focus")
private val ALLOWED_EVENTS = setOf("lifecycle.visibility", "diagnostics.snapshot", "connection_state")

interface CompanionHostEvents {
    fun onServerState(state: String)
    fun onPeerPending(sessionId: String, producer: String, appVersion: String)
    fun onPeerConnected(sessionId: String)
    fun onPeerClosed()
    fun onActivity(summary: String)
    fun onReject(reason: String)
}

private data class Frame(
    val fin: Boolean,
    val opcode: Int,
    val payload: ByteArray,
    val valid: Boolean,
    val reason: String
)

class CompanionServer(private val port: Int, private val events: CompanionHostEvents) {

    @Volatile
    private var running = false

    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var clientThread: Thread? = null
    private var client: ClientConnection? = null
    private var pendingId: String? = null
    private var connectedId: String? = null

    private val sendLock = Any()
    private val messageTimes = mutableListOf<Long>()
    private var invalidCount = 0

    fun isRunning(): Boolean = running

    fun start() {
        if (running) return
        running = true
        try {
            val socket = ServerSocket()
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(port))
            serverSocket = socket
            events.onServerState("Listening on port $port")
            acceptThread = Thread({
                acceptLoop(socket)
            }, "luma-host-accept")
            acceptThread?.isDaemon = true
            acceptThread?.start()
        } catch (error: Exception) {
            running = false
            serverSocket = null
            events.onServerState("Start failed")
            events.onReject("bind_failed")
        }
    }

    fun stop() {
        if (!running && client == null) return
        running = false
        try {
            serverSocket?.close()
        } catch (error: Exception) {
            /* best effort */
        }
        serverSocket = null
        client?.close()
        try {
            acceptThread?.join(1000)
            clientThread?.join(1000)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        acceptThread = null
        clientThread = null
        pendingId = null
        connectedId = null
        events.onServerState("Stopped")
    }

    fun approve() {
        val session = pendingId ?: return
        val payload = JSONObject()
        payload.put("accepted_version", CONTRACT_VERSION)
        payload.put("supported_types", JSONArray(listOf("command", "event", "ack", "telemetry_batch")))
        payload.put(
            "features",
            JSONArray(listOf("diagnostics.refresh", "browser.open", "browser.close"))
        )
        payload.put("telemetry_enabled", false)
        send("capability", newId("cap"), payload)
        connectedId = session
        pendingId = null
        events.onPeerConnected(session)
        events.onActivity("capability approved")
    }

    fun reject() {
        val session = pendingId
        if (session != null) {
            sendAck(session, "rejected", "pairing_rejected")
        }
        pendingId = null
        client?.close()
        events.onActivity("peer rejected")
    }

    fun sendDiagnosticsRefresh() {
        if (connectedId == null || pendingId != null) {
            events.onReject("no_approved_peer")
            return
        }
        val payload = JSONObject()
        payload.put("action", "diagnostics.refresh")
        payload.put("request_id", newId("req"))
        payload.put("args", JSONObject())
        payload.put("timeout_ms", 5000)
        send("command", newId("cmd"), payload)
        events.onActivity("command sent · diagnostics.refresh")
    }

    fun sendBrowserOpen(rawUrl: String) {
        if (connectedId == null || pendingId != null) {
            events.onReject("no_approved_peer")
            return
        }
        val checked = validateBrowserUrl(rawUrl)
        if (!checked.ok) {
            events.onReject(checked.reason)
            return
        }
        val payload = JSONObject()
        payload.put("action", "browser.open")
        payload.put("request_id", newId("req"))
        payload.put("args", JSONObject().put("url", checked.url))
        payload.put("timeout_ms", 5000)
        send("command", newId("cmd"), payload)
        events.onActivity("command sent · browser.open")
    }

    fun sendBrowserClose() {
        if (connectedId == null || pendingId != null) {
            events.onReject("no_approved_peer")
            return
        }
        val payload = JSONObject()
        payload.put("action", "browser.close")
        payload.put("request_id", newId("req"))
        payload.put("args", JSONObject())
        payload.put("timeout_ms", 5000)
        send("command", newId("cmd"), payload)
        events.onActivity("command sent · browser.close")
    }

    private fun validateBrowserUrl(raw: String): BrowserUrlValidation {
        if (raw.length > 2048 || raw.isBlank()) {
            return BrowserUrlValidation(false, "", "invalid_url")
        }
        val uri = try {
            URI.create(raw.trim())
        } catch (error: Exception) {
            return BrowserUrlValidation(false, "", "invalid_url")
        }
        val scheme = uri.scheme?.lowercase() ?: return BrowserUrlValidation(false, "", "url_scheme_not_allowed")
        if (scheme != "http" && scheme != "https") {
            return BrowserUrlValidation(false, "", "url_scheme_not_allowed")
        }
        if (uri.host.isNullOrEmpty() || uri.userInfo != null) {
            return BrowserUrlValidation(false, "", "invalid_url")
        }
        if (uri.port < -1 || uri.rawFragment?.contains(' ') == true) {
            return BrowserUrlValidation(false, "", "invalid_url")
        }
        return BrowserUrlValidation(true, uri.toString(), "ok")
    }

    private data class BrowserUrlValidation(
        val ok: Boolean,
        val url: String,
        val reason: String
    )

    private fun acceptLoop(socket: ServerSocket) {
        while (running) {
            try {
                val incoming = socket.accept()
                if (client != null && client?.alive == true) {
                    incoming.close()
                    events.onActivity("second peer ignored")
                    continue
                }
                val connection = ClientConnection(incoming)
                client = connection
                pendingId = null
                connectedId = null
                invalidCount = 0
                messageTimes.clear()
                events.onActivity("peer socket opened")
                clientThread = Thread({
                    connection.run()
                }, "luma-host-client")
                clientThread?.isDaemon = true
                clientThread?.start()
            } catch (error: Exception) {
                if (!running) break
                events.onActivity("accept error")
            }
        }
    }

    private inner class ClientConnection(val socket: Socket) {

        @Volatile
        var alive = true

        fun run() {
            val input: DataInputStream
            val output: OutputStream
            try {
                input = DataInputStream(socket.getInputStream())
                output = socket.getOutputStream()
                if (!readHandshake(input, output)) {
                    events.onActivity("upgrade failed")
                    return
                }

                while (alive && running) {
                    val frame = readFrame(input)
                    if (!frame.valid) {
                        invalidCount += 1
                        events.onReject(frame.reason)
                        if (invalidCount >= MAX_INVALID_BEFORE_CLOSE) {
                            sendFrame(0x8, ByteArray(0))
                            break
                        }
                        continue
                    }

                    when (frame.opcode) {
                        0x1 -> {
                            if (!frame.fin) {
                                invalidCount += 1
                                events.onReject("fragmented_frame")
                            } else {
                                handleText(String(frame.payload, StandardCharsets.UTF_8))
                            }
                        }
                        0x8 -> {
                            sendFrame(0x8, ByteArray(0))
                            break
                        }
                        0x9 -> sendFrame(0xA, frame.payload)
                        0xA -> Unit
                        else -> {
                            invalidCount += 1
                            events.onReject("unsupported_opcode")
                        }
                    }

                    if (invalidCount >= MAX_INVALID_BEFORE_CLOSE) {
                        sendFrame(0x8, ByteArray(0))
                        break
                    }
                }
            } catch (error: Exception) {
                if (alive && running) events.onActivity("peer disconnected")
            } finally {
                close()
            }
        }

        private fun readHandshake(input: InputStream, output: OutputStream): Boolean {
            val buffer = ByteArray(8192)
            var total = 0
            while (total < buffer.size) {
                val count = input.read(buffer, total, buffer.size - total)
                if (count < 0) return false
                total += count
                val head = String(buffer, 0, total, StandardCharsets.ISO_8859_1)
                val end = head.indexOf("\r\n\r\n")
                if (end < 0) continue

                val lines = head.substring(0, end).split("\r\n")
                val requestLine = lines.firstOrNull() ?: return false
                if (!requestLine.startsWith("GET $HOST_PATH ", ignoreCase = false)) {
                    output.write("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1))
                    output.flush()
                    return false
                }

                val keyLine = lines.firstOrNull {
                    it.startsWith("Sec-WebSocket-Key:", ignoreCase = true)
                } ?: return false
                val key = keyLine.substringAfter(":").trim()
                if (key.isEmpty()) return false

                val accept = sha1Base64(key + WS_GUID)
                val response = "HTTP/1.1 101 Switching Protocols\r\n" +
                    "Upgrade: websocket\r\n" +
                    "Connection: Upgrade\r\n" +
                    "Sec-WebSocket-Accept: $accept\r\n\r\n"
                output.write(response.toByteArray(StandardCharsets.ISO_8859_1))
                output.flush()
                return true
            }
            return false
        }

        private fun readFrame(input: DataInputStream): Frame {
            val first = input.readUnsignedByte()
            val fin = (first and 0x80) != 0
            val opcode = first and 0x0F
            val second = input.readUnsignedByte()
            val masked = (second and 0x80) != 0
            var length = second and 0x7F

            if (length == 126) {
                length = input.readUnsignedShort()
            } else if (length == 127) {
                val longLength = input.readLong()
                if (longLength > MAX_FRAME_BYTES.toLong()) {
                    return Frame(false, 0, ByteArray(0), false, "message_too_large")
                }
                length = longLength.toInt()
            }

            if (length > MAX_FRAME_BYTES) {
                return Frame(false, 0, ByteArray(0), false, "message_too_large")
            }
            if (!masked) {
                return Frame(false, 0, ByteArray(0), false, "unmasked_client_frame")
            }

            val mask = ByteArray(4)
            input.readFully(mask)
            val payload = ByteArray(length)
            input.readFully(payload)
            for (index in payload.indices) {
                payload[index] = (payload[index].toInt() xor mask[index % 4].toInt()).toByte()
            }
            return Frame(fin, opcode, payload, true, "ok")
        }

        fun close() {
            if (!alive) return
            alive = false
            try {
                socket.close()
            } catch (error: Exception) {
                /* best effort */
            }
            if (client === this) {
                client = null
                pendingId = null
                connectedId = null
                events.onPeerClosed()
            }
        }
    }

    private fun handleText(raw: String) {
        val validation = validateEnvelope(raw)
        if (!validation.ok) {
            invalidCount += 1
            events.onReject(validation.reason)
            return
        }
        if (rateLimited()) return

        val message = validation.envelope ?: return
        when (message.type) {
            "hello" -> handleHello(message.payload)
            "capability" -> rejectUnexpected("unexpected_capability")
            "command" -> handleCommand(message.payload)
            "event" -> handleEvent(message.payload)
            "ack" -> handleAck(message.payload)
            "telemetry_batch" -> sendAck(message.id, "rejected", "telemetry_disabled")
            else -> rejectUnexpected("unsupported_type")
        }
    }

    private fun handleHello(payload: JSONObject) {
        val producer = payload.optString("producer", "")
        val appVersion = payload.optString("app_version", "")
        val sessionId = payload.optString("session_id", "")
        val capabilities = payload.opt("capabilities")

        if (producer != "com.luma.companion2" || !validString(appVersion, 32) ||
            !validString(sessionId, MAX_ID_LENGTH) || capabilities !is JSONArray
        ) {
            rejectUnexpected("invalid_hello")
            return
        }
        if (pendingId != null || connectedId != null) {
            rejectUnexpected("duplicate_hello")
            return
        }

        pendingId = sessionId
        events.onPeerPending(sessionId, producer, appVersion)
        events.onActivity("hello received")
    }

    private fun handleCommand(payload: JSONObject) {
        val action = payload.optString("action", "")
        val requestId = payload.optString("request_id", "")
        val args = payload.optJSONObject("args")
        val timeoutValue = payload.opt("timeout_ms") as? Number

        if (!validString(action, 128) || !validString(requestId, MAX_ID_LENGTH) ||
            args == null || timeoutValue == null
        ) {
            sendAck(requestId, "rejected", "invalid_command")
            rejectUnexpected("invalid_command")
            return
        }
        val timeout = timeoutValue.toDouble()
        if (timeout < 1000 || timeout > 10000) {
            sendAck(requestId, "rejected", "invalid_command")
            rejectUnexpected("invalid_command")
            return
        }

        if (action !in ALLOWED_ACTIONS) {
            if (action in RESERVED_ACTIONS) {
                sendAck(requestId, "rejected", "action_disabled")
                rejectUnexpected("action_disabled")
            } else {
                sendAck(requestId, "rejected", "unsupported_action")
                rejectUnexpected("unsupported_action")
            }
            return
        }

        sendAck(requestId, "accepted", null)
        events.onActivity("command accepted")
    }

    private fun handleEvent(payload: JSONObject) {
        val name = payload.optString("name", "")
        val data = payload.optJSONObject("data")
        if (!validString(name, 128) || name !in ALLOWED_EVENTS || data == null) {
            rejectUnexpected("invalid_event")
        }
    }

    private fun handleAck(payload: JSONObject) {
        val reference = payload.optString("ref_id", "")
        val batchId = payload.optString("batch_id", "")
        val status = payload.optString("status", "")
        val hasReference = validString(reference, MAX_ID_LENGTH)
        val hasBatchId = validString(batchId, MAX_ID_LENGTH)
        if ((!hasReference && !hasBatchId) ||
            status !in setOf("accepted", "rejected", "partial")
        ) {
            rejectUnexpected("invalid_ack")
            return
        }
        val label = if (hasReference) reference else batchId
        events.onActivity("ack " + status + " · " + label.take(8))
    }

    private fun rejectUnexpected(reason: String) {
        invalidCount += 1
        events.onReject(reason)
    }

    private fun rateLimited(): Boolean {
        val now = System.currentTimeMillis()
        val fresh = messageTimes.filter { now - it < RATE_WINDOW_MS }.toMutableList()
        messageTimes.clear()
        messageTimes.addAll(fresh)
        if (fresh.size >= MAX_MESSAGES_PER_WINDOW) {
            invalidCount += 1
            events.onReject("rate_limited")
            sendFrame(0x8, ByteArray(0))
            client?.close()
            return true
        }
        messageTimes.add(now)
        return false
    }

    private fun sendAck(reference: String, status: String, reason: String?) {
        val payload = JSONObject()
        payload.put("ref_id", reference)
        payload.put("status", status)
        payload.put("reason", reason ?: JSONObject.NULL)
        send("ack", newId("ack"), payload)
    }

    private fun send(type: String, id: String, payload: JSONObject) {
        val raw = envelope(type, id, payload)
        if (byteLength(raw) > MAX_FRAME_BYTES) {
            events.onReject("outbound_too_large")
            return
        }
        sendFrame(0x1, raw.toByteArray(StandardCharsets.UTF_8))
        events.onActivity("sent $type")
    }

    private fun sendFrame(opcode: Int, payload: ByteArray) {
        synchronized(sendLock) {
            val output = try {
                client?.socket?.getOutputStream()
            } catch (error: Exception) {
                null
            } ?: return

            val header = when {
                payload.size < 126 -> byteArrayOf(
                    (0x80 or opcode).toByte(),
                    payload.size.toByte()
                )
                payload.size <= 65535 -> byteArrayOf(
                    (0x80 or opcode).toByte(),
                    126.toByte(),
                    (payload.size ushr 8).toByte(),
                    (payload.size and 0xFF).toByte()
                )
                else -> {
                    val size = payload.size.toLong()
                    byteArrayOf(
                        (0x80 or opcode).toByte(),
                        127.toByte(),
                        ((size shr 56) and 0xFF).toByte(),
                        ((size shr 48) and 0xFF).toByte(),
                        ((size shr 40) and 0xFF).toByte(),
                        ((size shr 32) and 0xFF).toByte(),
                        ((size shr 24) and 0xFF).toByte(),
                        ((size shr 16) and 0xFF).toByte(),
                        ((size shr 8) and 0xFF).toByte(),
                        (size and 0xFF).toByte()
                    )
                }
            }

            try {
                output.write(header)
                output.write(payload)
                output.flush()
            } catch (error: Exception) {
                /* a closed peer is handled by the reader thread */
            }
        }
    }

    private fun sha1Base64(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1")
            .digest(value.toByteArray(StandardCharsets.US_ASCII))
        return Base64.getEncoder().encodeToString(digest)
    }

    private fun validString(value: String, maxLength: Int): Boolean {
        return value.isNotEmpty() && value.length <= maxLength
    }
}
