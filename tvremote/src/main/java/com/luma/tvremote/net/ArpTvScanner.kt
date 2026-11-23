package com.luma.tvremote.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Resolves a TV's current LAN IP from the Android ARP cache. DHCP leases change
 * (a saved 192.168.1.10 can silently become .2), so a remembered MAC is far more
 * reliable than a remembered IP. Every candidate is confirmed by probing the
 * webOS ports before it is returned.
 */
object ArpTvScanner {
    /** LG OUIs seen on webOS TVs (wired + wireless). */
    private val LG_OUIS = listOf("64:E4:A5", "04:70:56", "F8:8F:CA", "78:6A:EC", "D8:3A:DD")

    data class ArpEntry(val ip: String, val mac: String)

    suspend fun entries(): List<ArpEntry> = withContext(Dispatchers.IO) {
        val result = mutableListOf<ArpEntry>()
        runCatching {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                reader.readLine() // header
                while (true) {
                    val line = reader.readLine() ?: break
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size < 4) continue
                    val ip = parts[0]
                    val mac = parts[3].uppercase()
                    if (ip.startsWith("127.") || mac == "00:00:00:00:00:00") continue
                    if (!mac.matches(Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}"))) continue
                    result += ArpEntry(ip, mac)
                }
            }
        }
        result
    }

    /** Current IP for a MAC we already know, or null if it isn't on the LAN. */
    suspend fun ipForMac(mac: String?): String? {
        if (mac.isNullOrBlank()) return null
        val normalized = normalize(mac)
        return entries().firstOrNull { normalize(it.mac) == normalized }?.ip
    }

    suspend fun scan(
        knownMacs: Set<String> = emptySet(),
        bleAddresses: Set<String> = emptySet()
    ): List<DiscoveredTv> = withContext(Dispatchers.IO) {
        val known = knownMacs.map(::normalize).toSet()
        val ble = bleAddresses.map(::normalize).toSet()

        entries().mapNotNull { entry ->
            val mac = normalize(entry.mac)
            val isKnown = mac in known || mac in ble
            val isLgOui = LG_OUIS.any { oui -> mac.startsWith(normalize(oui)) }
            if (!isKnown && !isLgOui) return@mapNotNull null

            val reachable = probe(entry.ip, 3001) || probe(entry.ip, 3000)
            if (!reachable) return@mapNotNull null

            DiscoveredTv(
                host = entry.ip,
                mac = mac,
                model = null,
                source = if (isKnown) "Bluetooth/ARP" else "ARP"
            )
        }.distinctBy { it.host }
    }

    private fun normalize(mac: String): String =
        mac.trim().uppercase().replace('-', ':')

    private fun probe(host: String, port: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 300)
            true
        }
    } catch (_: Exception) {
        false
    }
}
