package com.luma.tvremote.net

import android.content.Context
import android.net.ConnectivityManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/** Sends a standard Wake-on-LAN magic packet over the active Wi-Fi LAN. */
object WakeOnLan {

    suspend fun send(context: Context, mac: String) = kotlinx.coroutines.withContext(
        kotlinx.coroutines.Dispatchers.IO
    ) {
        val bytes = normalizeMac(mac)
        require(bytes.size == 6) { "Invalid TV MAC address: $mac" }

        val packet = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) packet[i] = 0xFF.toByte()
        for (i in 0 until 16) System.arraycopy(bytes, 0, packet, 6 + i * 6, 6)

        val targets = listOfNotNull(broadcastAddress(context), "255.255.255.255").distinct()
        DatagramSocket().use { socket ->
            socket.broadcast = true
            for (target in targets) {
                val address = InetAddress.getByName(target)
                for (port in listOf(9, 7)) {
                    socket.send(DatagramPacket(packet, packet.size, address, port))
                }
            }
        }
    }

    private fun normalizeMac(mac: String): ByteArray =
        mac.replace(Regex("[^0-9A-Fa-f]"), "")
            .chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()

    private fun broadcastAddress(context: Context): String? {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return null
        val network = manager.activeNetwork ?: return null
        val link = manager.getLinkProperties(network) ?: return null
        val address = link.linkAddresses.firstOrNull { it.address is java.net.Inet4Address }
            ?.address?.hostAddress ?: return null
        if (address.startsWith("127.")) return null
        val prefix = address.substringBeforeLast('.')
        return if (prefix == address) null else "$prefix.255"
    }
}
