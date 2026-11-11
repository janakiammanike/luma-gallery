package com.luma.gallery.web

import java.net.Inet4Address
import java.net.NetworkInterface

/** Discovers the addresses a TV / PC / phone on the same network can use. */
object WebNet {

    /**
     * Private-LAN IPv4 addresses only. Mobile-data interfaces are skipped because other
     * devices on the Wi-Fi cannot reach them.
     */
    fun siteAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { iface -> runCatching { iface.isUp && !iface.isLoopback }.getOrDefault(false) }
            .flatMap { iface -> iface.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .filter { address ->
                !address.isLoopbackAddress && (address.isSiteLocalAddress || address.isLinkLocalAddress)
            }
            .mapNotNull { it.hostAddress }
            .filter { it.isNotBlank() }
            .distinct()
    }.getOrDefault(emptyList())

    fun urls(port: Int): List<String> = siteAddresses().map { "http://$it:$port" }
}
