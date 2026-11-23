package com.luma.tvremote.net

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class BleDevice(
    val address: String,
    val name: String?,
    val rssi: Int
)

data class BleScanResult(
    val supported: Boolean,
    val enabled: Boolean,
    val devices: List<BleDevice>
)

/**
 * BLE proximity scan, similar to the first step LG ThinQ uses: find nearby
 * Bluetooth devices. BLE does NOT carry the TV's LAN IP, so the result is
 * combined with [ArpTvScanner] to turn a MAC address into a reachable IP.
 */
object BluetoothTvScanner {
    private const val SCAN_MS = 8_000L

    @SuppressLint("MissingPermission")
    suspend fun scan(context: Context): BleScanResult {
        val adapter = runCatching {
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        }.getOrNull()
            ?: return BleScanResult(false, false, emptyList())

        if (!adapter.isEnabled) return BleScanResult(true, false, emptyList())
        val leScanner = runCatching { adapter.bluetoothLeScanner }.getOrNull()
            ?: return BleScanResult(true, true, emptyList())

        return suspendCancellableCoroutine { cont ->
            val found = linkedMapOf<String, BleDevice>()
            val handler = Handler(Looper.getMainLooper())
            var finished = false
            var callback: ScanCallback? = null

            fun finish() {
                if (finished) return
                finished = true
                handler.removeCallbacksAndMessages(null)
                callback?.let { runCatching { leScanner.stopScan(it) } }
                if (cont.isActive) {
                    cont.resume(
                        BleScanResult(true, true, found.values.sortedBy { -it.rssi })
                    )
                }
            }

            callback = object : ScanCallback() {
                override fun onScanResult(callbackType: Int, result: ScanResult) {
                    val device = result.device ?: return
                    val address = device.address ?: return
                    val name = runCatching { device.name }.getOrNull()
                        ?: result.scanRecord?.deviceName
                    found[address] = BleDevice(address, name, result.rssi)
                }

                override fun onScanFailed(errorCode: Int) {
                    finish()
                }
            }
            val activeCallback = callback!!

            handler.postDelayed({ finish() }, SCAN_MS)
            runCatching {
                leScanner.startScan(
                    null,
                    ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                        .build(),
                    activeCallback
                )
            }.onFailure { finish() }

            cont.invokeOnCancellation { finish() }
        }
    }

    /** Names that commonly identify an LG TV or its remote over BLE. */
    fun looksLikeTv(device: BleDevice): Boolean {
        val name = device.name.orEmpty()
        if (name.isBlank()) return false
        val n = name.lowercase()
        return listOf("lg", "webos", "tv", "ur75", "uHD", "oled", "nanocell", "thinQ")
            .any { n.contains(it.lowercase()) }
    }
}
