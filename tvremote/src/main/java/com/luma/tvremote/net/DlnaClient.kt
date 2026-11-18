package com.luma.tvremote.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Minimal DLNA / UPnP AVTransport client that casts a LAN media URL to the
 * TV's built-in Digital Media Renderer. webOS exposes a DMR on port 1107.
 *
 * Unlike the SSAP `system.launcher/launch` path (which only opens the media
 * app), this actually makes the TV fetch and play the file — verified on a
 * 55UR7500PSC where the TV issued HEAD + GET for the served URL and reported
 * CurrentTransportState = PLAYING.
 */
class DlnaClient(private val host: String) {
    private val http = OkHttpClient()

    sealed class Result {
        object Success : Result()
        data class Failure(val message: String) : Result()
    }

    /** Point the TV renderer at [url], describe it with [mime], then start playback. */
    suspend fun cast(url: String, mime: String, title: String = "Luma Cast"): Result =
        withContext(Dispatchers.IO) {
            try {
                val control = discoverControlUrl()
                val didlClass = when {
                    mime.startsWith("image/") -> "object.item.imageItem.photo"
                    mime.startsWith("video/") -> "object.item.videoItem"
                    mime.startsWith("audio/") -> "object.item.audioItem.musicTrack"
                    else -> "object.item"
                }
                val protocolInfo = "http-get:*:$mime:*"
                val didl = buildDidl(title, didlClass, protocolInfo, url)
                soap(control, "SetAVTransportURI") {
                    "<InstanceID>0</InstanceID>" +
                        "<CurrentURI>${escape(url)}</CurrentURI>" +
                        "<CurrentURIMetaData>${escape(didl)}</CurrentURIMetaData>"
                }
                soap(control, "Play") {
                    "<InstanceID>0</InstanceID><Speed>1</Speed>"
                }
                Result.Success
            } catch (e: Exception) {
                Result.Failure(e.message ?: "DLNA cast failed")
            }
        }

    /** Stop playback on the TV renderer. Best-effort. */
    suspend fun stop(): Result = withContext(Dispatchers.IO) {
        try {
            val control = discoverControlUrl()
            soap(control, "Stop") { "<InstanceID>0</InstanceID>" }
            Result.Success
        } catch (e: Exception) {
            Result.Failure(e.message ?: "DLNA stop failed")
        }
    }

    /** Fetch the renderer description and extract the AVTransport control URL. */
    private fun discoverControlUrl(): String {
        val desc = http.newCall(
            Request.Builder().url("http://$host:1107/").build()
        ).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("DMR description HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
        // Locate the <service> block whose serviceType contains AVTransport:1.
        val services = Regex("<service>(.*?)</service>", RegexOption.DOT_MATCHES_ALL).findAll(desc)
        for (match in services) {
            val block = match.groupValues[1]
            if (!block.contains("AVTransport:1")) continue
            val control = Regex("<controlURL>(.*?)</controlURL>").find(block)
                ?.groupValues?.get(1)?.trim()
            if (!control.isNullOrBlank()) {
                return if (control.startsWith("http")) control else "http://$host:1107$control"
            }
        }
        throw IllegalStateException("No AVTransport service in DMR description")
    }

    private fun soap(controlUrl: String, action: String, body: () -> String) {
        val xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>" +
            "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
            "<s:Body><u:$action xmlns:u=\"$SERVICE_TYPE\">${body()}</u:$action></s:Body></s:Envelope>"
        val request = Request.Builder()
            .url(controlUrl)
            .post(xml.toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
            .header("SOAPACTION", "\"$SERVICE_TYPE#$action\"")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val fault = response.body?.string().orEmpty()
                val description = Regex("<errorDescription>(.*?)</errorDescription>")
                    .find(fault)?.groupValues?.get(1)
                throw IllegalStateException(description ?: "$action HTTP ${response.code}")
            }
        }
    }

    private fun buildDidl(title: String, itemClass: String, protocolInfo: String, url: String) =
        "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" " +
            "xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
            "<item id=\"0\" parentID=\"-1\" restricted=\"1\">" +
            "<dc:title>${escape(title)}</dc:title>" +
            "<upnp:class>$itemClass</upnp:class>" +
            "<res protocolInfo=\"${escape(protocolInfo)}\">${escape(url)}</res>" +
            "</item></DIDL-Lite>"

    private fun escape(value: String) = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private companion object {
        const val SERVICE_TYPE = "urn:schemas-upnp-org:service:AVTransport:1"
    }
}
