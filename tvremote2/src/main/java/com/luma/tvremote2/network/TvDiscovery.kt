package com.luma.tvremote2.network

import com.luma.tvremote2.platform.webos.WebOsDevice

/**
 * Discovery contract for the network layer.
 *
 * Phase 2 will provide companion-beacon (UDP 50550), mDNS and LAN-scan
 * implementations behind this interface. Kept as a boundary now so feature
 * screens never depend on a concrete scanner.
 */
interface TvDiscovery {
    suspend fun discover(timeoutMs: Long = 1_500): List<WebOsDevice>
}
