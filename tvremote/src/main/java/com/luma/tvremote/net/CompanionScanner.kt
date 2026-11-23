package com.luma.tvremote.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.SocketTimeoutException
import java.net.URL

/**
 * Listens for the UDP beacon sent by the Luma TV companion app installed on
 * the TV. The beacon carries the TV's CURRENT LAN IP, so discovery and DHCP
 * recovery no longer depend on mDNS, BLE or reading the Android ARP table.
 */
data class CompanionBeacon(
    val host: String,
    val version: String,
    val token: String,
    val uuid: String?,
    val model: String?,
    val macs: List<String>,
    val timestamp: Long
)

object CompanionScanner {
    const val PORT = 50550
    const val STATUS_PORT = 8081
    private const val LISTEN_MS = 3_500L
    private const val APP_ID = "com.luma.tvcompanion"

    /** Wait briefly for a broadcast beacon from the TV. */
    suspend fun listen(timeoutMs: Long = LISTEN_MS): CompanionBeacon? = withContext(Dispatchers.IO) {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket(PORT).apply {
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
                val beacon = parse(text, packet.address?.hostAddress)
                if (beacon != null) return@withContext beacon
            }
            null
        } catch (_: Exception) {
            null
        } finally {
            socket?.close()
        }
    }

    /** Ask a known host directly (useful right after a suspected IP change). */
    suspend fun probe(host: String): CompanionBeacon? = withContext(Dispatchers.IO) {
        try {
            val connection = URL("http://${host.trim()}:$STATUS_PORT/status").openConnection()
            connection.connectTimeout = 700
            connection.readTimeout = 700
            val text = BufferedReader(InputStreamReader(connection.getInputStream(), Charsets.UTF_8)).use { reader ->
                reader.readText()
            }
            parse(text, host.trim())
        } catch (_: Exception) {
            null
        }
    }

    private fun parse(text: String, sender: String?): CompanionBeacon? {
        return try {
            val json = JSONObject(text)
            if (json.optString("app") != APP_ID) return null
            val host = json.optString("ip").takeIf { it.isNotBlank() } ?: sender ?: return null
            CompanionBeacon(
                host = host,
                version = json.optString("version"),
                token = json.optString("token"),
                uuid = json.optString("uuid").takeIf { it.isNotBlank() && it != "null" },
                model = json.optString("model").takeIf { it.isNotBlank() && it != "null" },
                macs = json.optJSONArray("macs")?.let { array ->
                    (0 until array.length()).mapNotNull { index ->
                        array.optString(index).takeIf { it.isNotBlank() }
                    }
                } ?: emptyList(),
                timestamp = json.optLong("ts")
            )
        } catch (_: Exception) {
            null
        }
    }
}
