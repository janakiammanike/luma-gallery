package com.luma.tvremote.net

import android.content.Context
import android.net.wifi.WifiManager

data class WifiInfo(val ssid: String?, val bars: Int, val rssi: Int)

/** Reads the phone's Wi‑Fi link quality. No location permission is required. */
object WifiStatus {
    fun read(context: Context): WifiInfo? {
        val manager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        @Suppress("MissingPermission")
        val connection = manager.connectionInfo ?: return null
        val rssi = connection.rssi
        val bars = WifiManager.calculateSignalLevel(rssi, 4) + 1 // 1..5
        var ssid = connection.ssid
        if (ssid != null && ssid.length >= 2 && ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length - 1)
        }
        if (ssid == "<unknown ssid>") ssid = null
        return WifiInfo(ssid, bars, rssi)
    }

    /** Simple link-latency probe via a TCP connect to the TV's SSAP port. */
    fun latencyMs(host: String, port: Int = 3001): Long? {
        val start = System.currentTimeMillis()
        val ok = java.net.Socket().use { socket ->
            try {
                socket.connect(java.net.InetSocketAddress(host, port), 800)
                true
            } catch (_: Exception) {
                false
            }
        }
        return if (ok) System.currentTimeMillis() - start else null
    }
}
