package com.luma.tvremote2.network

import com.luma.tvremote2.platform.webos.WebOsDevice
import com.luma.tvremote2.platform.webos.WebOsProtocol.COMPANION_STATUS_PORT
import com.luma.tvremote2.platform.webos.WebOsProtocol.SSAP_PLAIN_PORT
import com.luma.tvremote2.platform.webos.WebOsProtocol.SSAP_TLS_PORT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket

/**
 * TV discovery with a real fallback chain:
 *   1. companion UDP beacon (fast, when the companion app is running)
 *   2. probe every host in the local /24 for SSAP (3000/3001) or companion (8081)
 *
 * The scan runs inside the Android app (Kotlin coroutines). Python is only a
 * PC-side diagnostic tool; it cannot be shipped inside an APK.
 */
object TvScanner {

    /** Returns the first reachable TV, preferring the beacon result. */
    suspend fun find(): WebOsDevice? {
        CompanionScanner.listen(1_200)?.let { beacon ->
            return WebOsDevice(
                host = beacon.host,
                model = beacon.model,
                uuid = beacon.uuid,
                macAddresses = beacon.macs,
                viaCompanion = true
            )
        }
        return scanLocalNetwork()
    }

    suspend fun scanLocalNetwork(): WebOsDevice? = withContext(Dispatchers.IO) {
        val subnet = localSubnet() ?: return@withContext null
        val hosts = (1..254).map { "$subnet.$it" }.filter { it != localAddress() }

        coroutineScope {
            val results = hosts.chunked(48).map { chunk ->
                async(Dispatchers.IO) {
                    chunk.firstOrNull { host ->
                        portOpen(host, SSAP_TLS_PORT, 350) ||
                            portOpen(host, SSAP_PLAIN_PORT, 350) ||
                            portOpen(host, COMPANION_STATUS_PORT, 350)
                    }
                }
            }.awaitAll()

            val host = results.firstOrNull { it != null }
            if (host == null) null else WebOsDevice(host = host, viaCompanion = portOpen(host, COMPANION_STATUS_PORT, 350))
        }
    }

    private fun portOpen(host: String, port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(InetAddress.getByName(host), port), timeoutMs)
            true
        }
    } catch (_: Exception) {
        false
    }

    /** Local IPv4 /24 prefix, e.g. 192.168.1 — no Wi-Fi permission required. */
    private fun localSubnet(): String? {
        val address = localAddress() ?: return null
        val parts = address.split(".")
        return if (parts.size == 4) parts.take(3).joinToString(".") else null
    }

    private fun localAddress(): String? = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && it.isSiteLocalAddress }
            ?.hostAddress
    } catch (_: Exception) {
        null
    }
}
