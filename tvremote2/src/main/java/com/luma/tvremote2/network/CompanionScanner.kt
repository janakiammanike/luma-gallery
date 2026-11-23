package com.luma.tvremote2.network

import com.luma.tvremote2.platform.webos.WebOsProtocol.COMPANION_APP_ID
import com.luma.tvremote2.platform.webos.WebOsProtocol.COMPANION_BEACON_PORT
import com.luma.tvremote2.platform.webos.WebOsProtocol.COMPANION_STATUS_PORT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException
import java.net.URL

/** Beacon payload broadcast by the TV companion over UDP/50550. */
data class CompanionBeacon(
    val host: String,
    val version: String,
    val token: String,
    val clientKey: String?,
    val uuid: String?,
    val model: String?,
    val macs: List<String>,
    val timestamp: Long
)

/**
 * Ported from the legacy module without protocol changes. The companion app
 * itself is untouched; this only reads the beacon and /status payloads.
 */
object CompanionScanner {
    private const val LISTEN_MS = 1_500L

    suspend fun listen(timeoutMs: Long = LISTEN_MS): CompanionBeacon? =
        withContext(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket(COMPANION_BEACON_PORT).apply {
                    reuseAddress = true
                    soTimeout = timeoutMs.toInt()
                }
                val deadline = System.currentTimeMillis() + timeoutMs
                while (System.currentTimeMillis() < deadline) {
                    val buffer = ByteArray(2048)
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        break
                    }
                    val text = String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                    parse(text, packet.address?.hostAddress)?.let { return@withContext it }
                }
                null
            } catch (_: Exception) {
                null
            } finally {
                socket?.close()
            }
        }

    suspend fun probe(host: String): CompanionBeacon? = withContext(Dispatchers.IO) {
        try {
            val connection = URL("http://${host.trim()}:$COMPANION_STATUS_PORT/status").openConnection()
            connection.connectTimeout = 700
            connection.readTimeout = 700
            val text = BufferedReader(InputStreamReader(connection.getInputStream(), Charsets.UTF_8)).use {
                it.readText()
            }
            parse(text, host.trim())
        } catch (_: Exception) {
            null
        }
    }

    private fun parse(text: String, sender: String?): CompanionBeacon? {
        val json = try { JSONObject(text) } catch (_: Exception) { return null }
        if (json.optString("app") != COMPANION_APP_ID) return null
        val host = json.optString("ip").takeIf { it.isNotBlank() } ?: sender ?: return null
        return CompanionBeacon(
            host = host,
            version = json.optString("version"),
            token = json.optString("token"),
            clientKey = json.optString("clientKey").takeIf { it.isNotBlank() && it != "null" },
            uuid = json.optString("uuid").takeIf { it.isNotBlank() && it != "null" },
            model = json.optString("model").takeIf { it.isNotBlank() && it != "null" },
            macs = json.optJSONArray("macs")?.let { array ->
                (0 until array.length()).mapNotNull { i ->
                    array.optString(i).takeIf { it.isNotBlank() }
                }
            } ?: emptyList(),
            timestamp = json.optLong("ts")
        )
    }
}
