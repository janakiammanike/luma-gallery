package com.luma.gallery.web

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import fi.iki.elonen.NanoHTTPD.Response.Status
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Locale

/**
 * Serves the React web UI plus the media API to browsers on the local network.
 */
class WebServer(context: Context, port: Int) : NanoHTTPD(port) {

    private val app = context.applicationContext
    private val media = WebMedia(app)

    override fun serve(session: IHTTPSession): Response {
        return try {
            route(session)
        } catch (t: Throwable) {
            json(
                Status.INTERNAL_ERROR,
                JSONObject().put("error", t.message ?: "Server error")
            )
        }
    }

    private fun route(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        if (!uri.startsWith("/api/")) return serveAsset(uri)

        when {
            uri == "/api/session" -> return sessionInfo(session)
            uri == "/api/login" && session.method == Method.POST -> return login(session)
            uri == "/api/logout" -> return logout()
        }

        if (!isAuthenticated(session)) {
            return json(
                Status.UNAUTHORIZED,
                JSONObject().put("error", "PIN required")
            )
        }

        return when {
            uri == "/api/media" -> mediaList(session)
            uri == "/api/albums" -> albums()
            uri.startsWith("/api/thumb/") -> thumb(session, uri.removePrefix("/api/thumb/"))
            uri.startsWith("/api/image/") -> image(session, uri.removePrefix("/api/image/"))
            uri.startsWith("/api/video/") -> video(session, uri.removePrefix("/api/video/"))
            uri.startsWith("/api/download/") ->
                download(session, uri.removePrefix("/api/download/"), inline = false)
            uri.startsWith("/api/original/") ->
                download(session, uri.removePrefix("/api/original/"), inline = true)
            uri.startsWith("/api/live/") -> liveZip(uri.removePrefix("/api/live/"))
            uri == "/api/upload" && session.method == Method.POST -> upload(session)
            else -> json(Status.NOT_FOUND, JSONObject().put("error", "Not found"))
        }
    }

    // ----- auth ------------------------------------------------------------

    private fun isAuthenticated(session: IHTTPSession): Boolean {
        val cookie = session.cookies.read(COOKIE_NAME) ?: return false
        val expected = WebPrefs.sessionToken(app)
        return MessageDigest.isEqual(
            cookie.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8)
        )
    }

    private fun sessionInfo(session: IHTTPSession): Response {
        val payload = JSONObject().apply {
            put("authenticated", isAuthenticated(session))
            put("name", "Luma")
            put("itemCount", media.visibleCount())
            put("server", "Luma LAN")
        }
        return json(Status.OK, payload)
    }

    private fun login(session: IHTTPSession): Response {
        val body = readSmallBody(session)
        val supplied = runCatching { JSONObject(body).optString("pin") }.getOrDefault("")
        val expected = WebPrefs.pin(app)
        val matches = supplied.isNotEmpty() && MessageDigest.isEqual(
            supplied.toByteArray(Charsets.UTF_8),
            expected.toByteArray(Charsets.UTF_8)
        )
        if (!matches) {
            Thread.sleep(350)
            return json(
                Status.UNAUTHORIZED,
                JSONObject().put("error", "Wrong PIN")
            )
        }
        val token = WebPrefs.sessionToken(app)
        val response = json(
            Status.OK,
            JSONObject().apply {
                put("authenticated", true)
                put("itemCount", media.visibleCount())
            }
        )
        response.addHeader(
            "Set-Cookie",
            "$COOKIE_NAME=$token; Path=/; Max-Age=31536000; SameSite=Lax; HttpOnly"
        )
        return response
    }

    private fun logout(): Response {
        val response = json(Status.OK, JSONObject().put("authenticated", false))
        response.addHeader(
            "Set-Cookie",
            "$COOKIE_NAME=; Path=/; Max-Age=0; SameSite=Lax; HttpOnly"
        )
        return response
    }

    private fun readSmallBody(session: IHTTPSession): String {
        val length = session.headers["content-length"]?.toIntOrNull() ?: return ""
        if (length <= 0 || length > 8_192) return ""
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = session.inputStream.read(bytes, read, length - read)
            if (count <= 0) break
            read += count
        }
        return String(bytes, 0, read, Charsets.UTF_8)
    }

    // ----- media API -------------------------------------------------------

    private fun mediaList(session: IHTTPSession): Response {
        val params = session.parms
        val smart = params["smart"]
        val bucket = params["bucket"]?.toLongOrNull()
        val type = params["type"]
        val search = params["q"]
        val offset = (params["offset"]?.toIntOrNull() ?: 0).coerceAtLeast(0)
        val limit = (params["limit"]?.toIntOrNull() ?: 120).coerceIn(1, 400)

        val all = media.filtered(smart, bucket, type, search)
        val slice = if (offset >= all.size) emptyList() else {
            all.subList(offset, minOf(all.size, offset + limit))
        }
        val payload = JSONObject().apply {
            put("total", all.size)
            put("offset", offset)
            put("items", media.mediaArray(slice))
        }
        return json(Status.OK, payload)
    }

    private fun albums(): Response {
        val payload = JSONObject().apply {
            put("albums", media.albumArray())
        }
        return json(Status.OK, payload)
    }

    private fun thumb(session: IHTTPSession, rawId: String): Response {
        val id = rawId.substringBefore('?').toLongOrNull()
            ?: return json(Status.BAD_REQUEST, JSONObject().put("error", "Bad id"))
        val item = media.item(id)
            ?: return json(Status.NOT_FOUND, JSONObject().put("error", "Missing media"))
        val size = session.parms["size"]?.toIntOrNull() ?: 360
        val bytes = media.thumbnail(item, size)
            ?: return json(Status.NOT_FOUND, JSONObject().put("error", "No thumbnail"))
        return bytesResponse(bytes, "image/jpeg", cacheSeconds = 86_400)
    }

    private fun image(session: IHTTPSession, rawId: String): Response {
        val id = rawId.substringBefore('?').toLongOrNull()
            ?: return json(Status.BAD_REQUEST, JSONObject().put("error", "Bad id"))
        val item = media.item(id)
            ?: return json(Status.NOT_FOUND, JSONObject().put("error", "Missing media"))
        if (item.isVideo) {
            val frame = media.thumbnail(item, 1600)
                ?: return json(Status.NOT_FOUND, JSONObject().put("error", "No frame"))
            return bytesResponse(frame, "image/jpeg", cacheSeconds = 3_600)
        }
        val bytes = media.fullImage(item)
            ?: return json(Status.NOT_FOUND, JSONObject().put("error", "Cannot decode image"))
        return bytesResponse(bytes, media.stillMime(item), cacheSeconds = 3_600)
    }

    private fun video(session: IHTTPSession, rawId: String): Response {
        val id = rawId.substringBefore('?').toLongOrNull()
            ?: return json(Status.BAD_REQUEST, JSONObject().put("error", "Bad id"))
        val item = media.item(id)
            ?: return json(Status.NOT_FOUND, JSONObject().put("error", "Missing media"))
        if (!item.isVideo) {
            return json(Status.BAD_REQUEST, JSONObject().put("error", "Not a video"))
        }
        val total = media.totalBytes(item)
        val input = media.open(item)
            ?: return json(Status.NOT_FOUND, JSONObject().put("error", "Cannot open video"))
        val mime = item.mimeType.takeIf { it.startsWith("video/") } ?: "video/mp4"
        val range = session.headers["range"]

        if (total <= 0 || range.isNullOrBlank()) {
            val response = if (total > 0) {
                newFixedLengthResponse(Status.OK, mime, input, total)
            } else {
                newChunkedResponse(Status.OK, mime, input)
            }
            return response.withCommonVideoHeaders()
        }

        val spec = range.substringAfter("bytes=", "").substringBefore(',')
        val startText = spec.substringBefore('-')
        val endText = spec.substringAfter('-', "")
        val start = startText.toLongOrNull() ?: 0L
        val end = endText.toLongOrNull()?.coerceAtMost(total - 1) ?: (total - 1)
        if (start < 0 || start > end || start >= total) {
            runCatching { input.close() }
            return newFixedLengthResponse(Status.RANGE_NOT_SATISFIABLE, "text/plain", "Invalid range")
                .apply { addHeader("Content-Range", "bytes */$total") }
        }
        val count = end - start + 1
        var skipped = 0L
        while (skipped < start) {
            val step = input.skip(start - skipped)
            if (step <= 0) break
            skipped += step
        }
        val response = newFixedLengthResponse(
            Status.PARTIAL_CONTENT,
            mime,
            LimitedInputStream(input, count),
            count
        )
        response.addHeader("Content-Range", "bytes $start-$end/$total")
        return response.withCommonVideoHeaders()
    }

    private fun Response.withCommonVideoHeaders(): Response {
        addHeader("Accept-Ranges", "bytes")
        addHeader("Cache-Control", "no-store")
        return this
    }

    private fun download(session: IHTTPSession, rawId: String, inline: Boolean): Response {
        val id = rawId.substringBefore('?').toLongOrNull()
            ?: return json(Status.BAD_REQUEST, JSONObject().put("error", "Bad id"))
        val item = media.item(id)
            ?: return json(Status.NOT_FOUND, JSONObject().put("error", "Missing media"))
        val total = media.totalBytes(item)
        val input = media.open(item)
            ?: return json(Status.NOT_FOUND, JSONObject().put("error", "Cannot open file"))
        val mime = item.mimeType.ifBlank { "application/octet-stream" }
        val response = if (total > 0) {
            newFixedLengthResponse(Status.OK, mime, input, total)
        } else {
            newChunkedResponse(Status.OK, mime, input)
        }
        val disposition = if (inline) "inline" else "attachment"
        val encoded = URLEncoder.encode(media.downloadName(item), "UTF-8").replace("+", "%20")
        response.addHeader(
            "Content-Disposition",
            "$disposition; filename=\"${media.downloadName(item).replace("\"", "")}\"; " +
                "filename*=UTF-8''$encoded"
        )
        response.addHeader("Cache-Control", "no-store")
        return response
    }

    private fun liveZip(rawId: String): Response {
        val id = rawId.substringBefore('.').substringBefore('?').toLongOrNull()
            ?: return json(Status.BAD_REQUEST, JSONObject().put("error", "Bad id"))
        val item = media.item(id)
            ?: return json(Status.NOT_FOUND, JSONObject().put("error", "Missing media"))
        val bytes = media.liveZip(item)
            ?: return json(Status.NOT_FOUND, JSONObject().put("error", "Not a Live Photo"))
        val response = bytesResponse(bytes, "application/zip", cacheSeconds = 0)
        val base = item.name.substringBeforeLast('.', item.name)
        val encoded = URLEncoder.encode("$base-live.zip", "UTF-8").replace("+", "%20")
        response.addHeader(
            "Content-Disposition",
            "attachment; filename=\"$base-live.zip\"; filename*=UTF-8''$encoded"
        )
        return response
    }

    private fun upload(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val tempPath = files[FILE_FIELD]
            ?: files.values.firstOrNull { File(it).isFile }
            ?: return json(Status.BAD_REQUEST, JSONObject().put("error", "No file"))
        val name = session.parms[NAME_FIELD]
            ?: session.parms["filename"]
            ?: File(tempPath).name
        val temp = File(tempPath)
        if (temp.length() <= 0L) {
            runCatching { temp.delete() }
            return json(Status.BAD_REQUEST, JSONObject().put("error", "Empty file"))
        }
        val uploaded = media.upload(temp, name)
        runCatching { temp.delete() }
        if (uploaded == null) {
            return json(
                Status.INTERNAL_ERROR,
                JSONObject().put("error", "Could not save upload")
            )
        }
        val saved = media.item(uploaded.id)
        val payload = JSONObject().apply {
            put("saved", true)
            put("id", uploaded.id)
            put("name", uploaded.name)
            if (saved != null) put("item", media.mediaJson(saved))
        }
        return json(Status.OK, payload)
    }

    // ----- static assets ---------------------------------------------------

    private fun serveAsset(rawUri: String): Response {
        val path = rawUri.trimStart('/').ifBlank { "index.html" }
        if (path.contains("..")) {
            return textResponse(Status.NOT_FOUND, "Not found")
        }
        val assetPath = "web/$path"
        val bytes = runCatching {
            app.assets.open(assetPath).use { it.readBytes() }
        }.getOrNull()

        if (bytes == null) {
            if (path.endsWith(".html") || !path.contains('.')) {
                return serveIndex()
            }
            return textResponse(Status.NOT_FOUND, "Not found")
        }
        val mime = mimeForAsset(path)
        val response = newFixedLengthResponse(Status.OK, mime, ByteArrayInputStream(bytes), bytes.size.toLong())
        response.addHeader(
            "Cache-Control",
            if (path.startsWith("assets/")) "public, max-age=31536000, immutable" else "no-cache"
        )
        response.addHeader("X-Content-Type-Options", "nosniff")
        return response
    }

    private fun serveIndex(): Response {
        val bytes = runCatching {
            app.assets.open("web/index.html").use { it.readBytes() }
        }.getOrNull()
        if (bytes != null) {
            val response =
                newFixedLengthResponse(Status.OK, "text/html", ByteArrayInputStream(bytes), bytes.size.toLong())
            response.addHeader("Cache-Control", "no-store")
            response.addHeader("X-Content-Type-Options", "nosniff")
            return response
        }
        val message = """
            <!doctype html>
            <html><head><meta charset="utf-8"><title>Luma</title>
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <style>
              body{background:#0b0b10;color:#e9e6f2;font-family:system-ui,sans-serif;
                   display:flex;align-items:center;justify-content:center;height:100vh;margin:0}
              main{max-width:34rem;padding:2rem;line-height:1.6}
              h1{color:#b49cff;margin:0 0 .5rem}
              code{background:#1c1c26;padding:.15rem .4rem;border-radius:.3rem}
            </style></head><body><main>
            <h1>Luma</h1>
            <p>The web UI is not bundled in this build yet.</p>
            <p>Run <code>npm install &amp;&amp; npm run build</code> inside <code>webui/</code>.</p>
            </main></body></html>
        """.trimIndent()
        return textResponse(Status.OK, message, "text/html")
    }

    private fun mimeForAsset(path: String): String = when (
        path.substringAfterLast('.', "").lowercase(Locale.ROOT)
    ) {
        "html" -> "text/html"
        "js", "mjs" -> "text/javascript"
        "css" -> "text/css"
        "json", "webmanifest" -> "application/json"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "ico" -> "image/x-icon"
        "woff2" -> "font/woff2"
        "woff" -> "font/woff"
        "map" -> "application/json"
        else -> "application/octet-stream"
    }

    // ----- helpers ---------------------------------------------------------

    private fun json(status: Status, payload: JSONObject): Response {
        val bytes = payload.toString().toByteArray(Charsets.UTF_8)
        val response = newFixedLengthResponse(
            status,
            "application/json; charset=utf-8",
            ByteArrayInputStream(bytes),
            bytes.size.toLong()
        )
        response.addHeader("Cache-Control", "no-store")
        return response
    }

    private fun bytesResponse(
        bytes: ByteArray,
        mime: String,
        cacheSeconds: Int
    ): Response {
        val response =
            newFixedLengthResponse(Status.OK, mime, ByteArrayInputStream(bytes), bytes.size.toLong())
        response.addHeader(
            "Cache-Control",
            if (cacheSeconds > 0) "public, max-age=$cacheSeconds" else "no-store"
        )
        return response
    }

    private fun textResponse(status: Status, body: String, mime: String = "text/plain"): Response {
        val bytes = body.toByteArray(Charsets.UTF_8)
        return newFixedLengthResponse(status, mime, ByteArrayInputStream(bytes), bytes.size.toLong())
    }

    private class LimitedInputStream(
        input: InputStream,
        private var remaining: Long
    ) : FilterInputStream(input) {

        override fun read(): Int {
            if (remaining <= 0) return -1
            val value = super.read()
            if (value >= 0) remaining--
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining <= 0) return -1
            val want = minOf(length.toLong(), remaining).toInt()
            val read = super.read(buffer, offset, want)
            if (read > 0) remaining -= read
            return read
        }
    }

    companion object {
        private const val COOKIE_NAME = "luma_session"
        private const val FILE_FIELD = "file"
        private const val NAME_FIELD = "name"
    }
}
