package com.luma.tvremote.net

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response.Status
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile

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
        val target = file
        if (target == null || !target.exists()) {
            return newFixedLengthResponse(Status.NOT_FOUND, MIME_PLAINTEXT, "No media loaded")
        }
        val mime = mimeFor(target.name)
        val range = session?.headers?.get("range")
        Log.d("LumaCast", "request ${session?.uri} range=$range from ${session?.remoteIpAddress}")
        return try {
            if (range.isNullOrBlank()) {
                newFixedLengthResponse(Status.OK, mime, FileInputStream(target), target.length()).apply {
                    addHeader("Accept-Ranges", "bytes")
                }
            } else {
                serveRange(target, mime, range)
            }
        } catch (e: Exception) {
            Log.e("LumaCast", "serve error", e)
            newFixedLengthResponse(Status.INTERNAL_ERROR, MIME_PLAINTEXT, e.message ?: "error")
        }
    }

    /** Handle `Range: bytes=start-end` so video players can seek/stream. */
    private fun serveRange(target: File, mime: String, range: String): Response {
        val size = target.length()
        val match = Regex("bytes=(\\d*)-(\\d*)").find(range)
            ?: return newFixedLengthResponse(Status.OK, mime, FileInputStream(target), size)
        var start = match.groupValues[1].toLongOrNull()
        var end = match.groupValues[2].toLongOrNull()

        when {
            start == null && end != null -> {
                // Suffix range: last `end` bytes.
                start = (size - end).coerceAtLeast(0)
                end = size - 1
            }
            start == null -> start = 0
            end == null || end >= size -> end = size - 1
        }
        if (start!! > end!! || start >= size) {
            return newFixedLengthResponse(Status.RANGE_NOT_SATISFIABLE, MIME_PLAINTEXT, "").apply {
                addHeader("Content-Range", "bytes */$size")
            }
        }

        val length = end - start + 1
        val raf = RandomAccessFile(target, "r")
        raf.seek(start)
        val stream = LimitedFileStream(raf, length)
        return newFixedLengthResponse(Status.PARTIAL_CONTENT, mime, stream, length).apply {
            addHeader("Content-Range", "bytes $start-$end/$size")
            addHeader("Accept-Ranges", "bytes")
        }
    }

    /** Input stream over a RandomAccessFile that stops after a fixed number of bytes. */
    private class LimitedFileStream(private val raf: RandomAccessFile, private var remaining: Long) :
        java.io.InputStream() {
        override fun read(): Int {
            if (remaining <= 0L) return -1
            val value = raf.read()
            if (value >= 0) remaining--
            return value
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0L) return -1
            val toRead = len.toLong().coerceAtMost(remaining).toInt()
            val count = raf.read(b, off, toRead)
            if (count > 0) remaining -= count
            return count
        }

        override fun close() {
            raf.close()
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
        name.endsWith(".3gp", true) -> "video/3gpp"
        name.endsWith(".mp3", true) -> "audio/mpeg"
        else -> "application/octet-stream"
    }
}
