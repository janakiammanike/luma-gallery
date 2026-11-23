package com.luma.tvremote.net

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class DiscoveredTv(
    val host: String,
    val mac: String?,
    val model: String?,
    val source: String
)

/**
 * Finds TVs through mDNS/Bonjour. LG webOS TVs with AirPlay 2 advertise
 * `_airplay._tcp`; the TXT record normally carries the device MAC in `deviceid`
 * and a readable name in `model`. A fixed discovery window keeps it predictable.
 */
object MdnsScanner {
    private val TYPES = listOf("_airplay._tcp", "_raop._tcp")
    private const val DISCOVERY_MS = 6_000L

    suspend fun scan(context: Context): List<DiscoveredTv> =
        suspendCancellableCoroutine { cont ->
            val manager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            if (manager == null) {
                cont.resume(emptyList())
                return@suspendCancellableCoroutine
            }

            val results = linkedMapOf<String, DiscoveredTv>()
            val listeners = mutableListOf<NsdManager.DiscoveryListener>()
            val handler = Handler(Looper.getMainLooper())
            var finished = false

            fun finish() {
                if (finished) return
                finished = true
                handler.removeCallbacksAndMessages(null)
                listeners.forEach { listener ->
                    runCatching { manager.stopServiceDiscovery(listener) }
                }
                if (cont.isActive) cont.resume(results.values.toList())
            }

            val timeout = Runnable { finish() }
            handler.postDelayed(timeout, DISCOVERY_MS)

            TYPES.forEach { type ->
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String?) = Unit

                    override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit

                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) = Unit

                            override fun onServiceResolved(info: NsdServiceInfo) {
                                val host = info.host?.hostAddress ?: return
                                if (host.startsWith("127.")) return
                                val attrs = info.attributes
                                val rawMac = attrs["deviceid"]?.let { String(it, Charsets.UTF_8) }
                                val mac = rawMac?.let { normalizeMac(it) }
                                val model = attrs["model"]?.let { String(it, Charsets.UTF_8) }
                                if (host !in results || mac != null) {
                                    results[host] = DiscoveredTv(host, mac, model, type)
                                }
                            }
                        })
                    }

                    override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit

                    override fun onDiscoveryStopped(serviceType: String?) = Unit

                    override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) = Unit
                }
                listeners.add(listener)
                runCatching {
                    manager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                }.onFailure {
                    // One service type unavailable; the other may still work.
                }
            }

            cont.invokeOnCancellation { finish() }
        }

    private fun normalizeMac(raw: String): String {
        val hex = raw.replace(Regex("[^0-9A-Fa-f]"), "").uppercase()
        return if (hex.length == 12) hex.chunked(2).joinToString(":") else raw
    }
}
