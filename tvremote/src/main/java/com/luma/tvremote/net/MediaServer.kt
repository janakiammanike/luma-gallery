package com.luma.tvremote.net

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.io.File

/** Serves one cached media file to the TV over the local Wi‑Fi network. */
class MediaServer(private val context: Context, private val port: Int = 8123) :
    NanoHTTPD(port) {

    @Volatile
    private var file: File? = null

    fun serve(fileToServe: File) {
        file = fileToServe
        if (!isAlive) start(SOCKET_READ_TIMEOUT, false)
    }

    fun shutdown() {
        super.stop()
        file = null
    }

    override fun serve(session: IHTTPSession?): Response {
        Log.d("LumaCast", "request ${session?.uri} from ${session?.remoteIpAddress}")
        val target = file
        if (target == null || !target.exists()) {
            return newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, "No media loaded")
        }
        return try {
            newFixedLengthResponse(Status.OK, mimeFor(target.name), target.inputStream(), target.length())
        } catch (e: Exception) {
            newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message ?: "error")
        }
    }

    fun lanUrl(fileName: String): String? {
        @Suppress("MissingPermission", "Deprecated")
        val address = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.connectionInfo?.ipAddress ?: return null
        if (address == 0) return null
        val ip = String.format(
            java.util.Locale.US, "%d.%d.%d.%d",
            address and 0xFF,
            address shr 8 and 0xFF,
            address shr 16 and 0xFF,
            address shr 24 and 0xFF
        )
        return "http://$ip:$port/${java.net.URLEncoder.encode(fileName, "UTF-8")}"
    }

    private fun mimeFor(name: String): String = when {
        name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) -> "image/jpeg"
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".gif", true) -> "image/gif"
        name.endsWith(".webp", true) -> "image/webp"
        name.endsWith(".mp4", true) -> "video/mp4"
        name.endsWith(".mkv", true) -> "video/x-matroska"
        name.endsWith(".webm", true) -> "video/webm"
        name.endsWith(".mp3", true) -> "audio/mpeg"
        else -> "application/octet-stream"
    }
}
