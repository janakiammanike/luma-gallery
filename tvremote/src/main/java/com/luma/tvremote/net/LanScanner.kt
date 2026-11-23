package com.luma.tvremote.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.Collections
import java.util.concurrent.Semaphore

object LanScanner {
    suspend fun scan(context: Context): List<String> = withContext(Dispatchers.IO) {
        val prefix = wifiPrefix(context) ?: return@withContext emptyList()
        val found = Collections.synchronizedSet(linkedSetOf<String>())
        val limiter = Semaphore(48)

        (1..254).map { last ->
            async {
                limiter.acquire()
                try {
                    val ip = "$prefix.$last"
                    if (probe(ip, 3001) || probe(ip, 3000)) {
                        found.add(ip)
                    }
                } finally {
                    limiter.release()
                }
            }
        }.awaitAll()

        found.toList().sortedBy { ip ->
            ip.split(".").getOrNull(3)?.toIntOrNull() ?: 0
        }
    }

    private fun probe(host: String, port: Int): Boolean = try {
        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 220)
            true
        }
    } catch (_: Exception) {
        false
    }

    private fun wifiPrefix(context: Context): String? {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return null
        val network = manager.activeNetwork ?: return null
        val caps = manager.getNetworkCapabilities(network) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        ) return null

        val link: LinkProperties = manager.getLinkProperties(network) ?: return null
        val address = link.linkAddresses.firstOrNull { it.address is java.net.Inet4Address }
            ?.address?.hostAddress ?: return null
        if (address.startsWith("127.")) return null
        return address.substringBeforeLast('.')
    }
}
