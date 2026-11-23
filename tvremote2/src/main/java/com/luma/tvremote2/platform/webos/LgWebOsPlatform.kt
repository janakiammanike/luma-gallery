package com.luma.tvremote2.platform.webos

import com.luma.tvremote2.data.SecureTvStore
import com.luma.tvremote2.network.CompanionBeacon
import com.luma.tvremote2.network.CompanionScanner
import com.luma.tvremote2.platform.webos.WebOsProtocol.COMPANION_STATUS_PORT
import com.luma.tvremote2.platform.webos.WebOsProtocol.ENDPOINT_AUDIO_STATUS
import com.luma.tvremote2.platform.webos.WebOsProtocol.ENDPOINT_DELETE_CHARS
import com.luma.tvremote2.platform.webos.WebOsProtocol.ENDPOINT_INSERT_TEXT
import com.luma.tvremote2.platform.webos.WebOsProtocol.ENDPOINT_LAUNCH_APP
import com.luma.tvremote2.platform.webos.WebOsProtocol.ENDPOINT_LAUNCH_POINTS
import com.luma.tvremote2.platform.webos.WebOsProtocol.ENDPOINT_MEDIA_FORWARD
import com.luma.tvremote2.platform.webos.WebOsProtocol.ENDPOINT_MEDIA_PAUSE
import com.luma.tvremote2.platform.webos.WebOsProtocol.ENDPOINT_MEDIA_PLAY
import com.luma.tvremote2.platform.webos.WebOsProtocol.ENDPOINT_MEDIA_REWIND
import com.luma.tvremote2.platform.webos.WebOsProtocol.ENDPOINT_MEDIA_STOP
import com.luma.tvremote2.platform.webos.WebOsProtocol.ENDPOINT_SEND_ENTER
import com.luma.tvremote2.platform.webos.WebOsProtocol.ENDPOINT_SET_MUTE
import com.luma.tvremote2.platform.webos.WebOsProtocol.ENDPOINT_VOLUME_DOWN
import com.luma.tvremote2.platform.webos.WebOsProtocol.ENDPOINT_VOLUME_UP
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.ConnectException
import java.util.concurrent.TimeUnit

/**
 * LG webOS transport over the TV companion's local HTTP proxy.
 *
 * The phone never speaks SSAP directly in this mode: it POSTs to
 * http://<tv>:8081/command and the companion executes the command against the
 * TV's own SSAP server on 127.0.0.1:3000. This is the path already proven on
 * the real TV (legacy app changed volume 9 -> 12), so Phase 2 ports it first.
 *
 * Companion app and protocol are unchanged.
 */
class LgWebOsPlatform(
    private val store: SecureTvStore
) : WebOsPlatform {

    private val http = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

    private var beacon: CompanionBeacon? = null
    private var connected = false

    override fun isConnected(): Boolean = connected

    override fun connectedHost(): String? = if (connected) beacon?.host else null

    override suspend fun connect(device: WebOsDevice): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // The companion is authoritative for its token and client-key.
            val found = CompanionScanner.probe(device.host)
                ?: return@withContext Result.failure(
                    IllegalStateException("TV companion not reachable at ${device.host}:$COMPANION_STATUS_PORT")
                )

            // Prefer the locally stored Keystore key; the companion's /status
            // value is the fallback on first pairing.
            val key = store.clientKeyFor(found.host)
                ?: found.clientKey
                ?: return@withContext Result.failure(
                    IllegalStateException("TV companion did not provide a pairing key")
                )

            store.setLastHost(found.host)
            store.saveClientKey(found.host, key)

            beacon = found
            connected = true
            Result.success(Unit)
        } catch (e: Exception) {
            connected = false
            Result.failure(e)
        }
    }

    override suspend fun disconnect() {
        connected = false
        beacon = null
    }

    /** Sends an SSAP command through the companion proxy. */
    private suspend fun request(
        uri: String,
        payload: Map<String, Any?> = emptyMap(),
        timeoutMs: Long = 5_000
    ): JSONObject = withTimeout(timeoutMs) {
        withContext(Dispatchers.IO) {
            val current = beacon ?: error("Not connected to a TV")
            val body = JSONObject()
                .put("uri", uri)
                .put("payload", JSONObject(payload))

            val json = withTokenAndKey(body)
            val text = postCommand(current.host, json)
            val response = try {
                JSONObject(text)
            } catch (_: Exception) {
                error("Companion returned invalid JSON: ${text.take(100)}")
            }
            if (response.optBoolean("returnValue") == false &&
                response.optString("errorText").isNotBlank()
            ) {
                error(response.optString("errorText"))
            }
            response
        }
    }

    /** Sends a newline-delimited input frame (buttons, pointer, scroll). */
    private suspend fun sendInput(frame: String) = withContext(Dispatchers.IO) {
        val current = beacon ?: error("Not connected to a TV")
        val body = withTokenAndKey(JSONObject().put("input", frame))
        val text = postCommand(current.host, body)
        val result = JSONObject(text)
        if (!result.optBoolean("returnValue", false)) {
            error(result.optString("errorText").ifBlank { "Companion input failed" })
        }
    }

    private fun withTokenAndKey(body: JSONObject): String = body
        .put("token", beacon?.token.orEmpty())
        .put("key", beacon?.host?.let { store.clientKeyFor(it) }.orEmpty())
        .toString()

    private suspend fun postCommand(host: String, json: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("http://$host:$COMPANION_STATUS_PORT/command")
            .post(json.toRequestBody("application/json".toMediaType()))
            .build()
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("Companion HTTP ${response.code}: ${text.take(120)}")
                }
                text
            }
        } catch (e: ConnectException) {
            // Companion died or TV rebooted: stop claiming we are connected.
            connected = false
            throw IllegalStateException("Connection lost — TV companion is not responding", e)
        }
    }

    // ---- WebOsPlatform implementation -------------------------------------------------

    override suspend fun button(name: String) = sendInput("type:button\nname:$name\n\n")

    override suspend fun move(dx: Int, dy: Int) =
        sendInput("type:move\ndx:$dx\ndy:$dy\ndown:0\n\n")

    override suspend fun click() = sendInput("type:click\n\n")

    override suspend fun scroll(dx: Int, dy: Int) =
        sendInput("type:scroll\ndx:$dx\ndy:$dy\n\n")

    override suspend fun typeText(text: String) {
        request(ENDPOINT_INSERT_TEXT, mapOf("text" to text, "replace" to 0))
    }

    override suspend fun deleteCharacters(count: Int) {
        request(ENDPOINT_DELETE_CHARS, mapOf("count" to count))
    }

    override suspend fun sendEnter() {
        request(ENDPOINT_SEND_ENTER)
    }

    override suspend fun apps(): List<WebOsApp> {
        val payload = request(ENDPOINT_LAUNCH_POINTS)
        val array = payload.optJSONArray("launchPoints") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val id = item.optString("id")
            if (id.isBlank()) return@mapNotNull null
            WebOsApp(
                id = id,
                title = item.optString("title").ifBlank { id },
                type = item.optString("appType").ifBlank { "app" },
                icon = item.optString("icon").ifBlank { null }
            )
        }.distinctBy { it.id }.sortedBy { it.title.lowercase() }
    }

    override suspend fun launchApp(id: String) {
        request(ENDPOINT_LAUNCH_APP, mapOf("id" to id))
    }

    override suspend fun searchOnTv(query: String): String = WebOsOptionalFeatures.searchOnTv(
        request = { uri, payload, timeout -> request(uri, payload, timeoutMs = timeout) },
        apps = { apps() },
        query = query
    )

    override suspend fun openAppSearch(appId: String, query: String): String =
        WebOsOptionalFeatures.openAppSearch(
            request = { uri, payload, timeout -> request(uri, payload, timeoutMs = timeout) },
            apps = { apps() },
            keyword = appId,
            query = query
        )

    override suspend fun selectInput(inputAppId: String) {
        request(ENDPOINT_LAUNCH_APP, mapOf("id" to inputAppId))
    }

    override suspend fun playMediaUrl(url: String): String = WebOsOptionalFeatures.playMediaUrl(
        request = { uri, payload, timeout -> request(uri, payload, timeoutMs = timeout) },
        apps = { apps() },
        url = url
    )

    override suspend fun openScreenMirroring(): String =
        WebOsOptionalFeatures.openScreenMirroring(
            request = { uri, payload, timeout -> request(uri, payload, timeoutMs = timeout) },
            apps = { apps() }
        )

    override suspend fun audioVolume(): Int? {
        val payload = request(ENDPOINT_AUDIO_STATUS)
        val status = payload.optJSONObject("volumeStatus") ?: payload
        val volume = status.optInt("volume", -1)
        return if (volume >= 0) volume else null
    }

    override suspend fun volumeUp() { request(ENDPOINT_VOLUME_UP) }
    override suspend fun volumeDown() { request(ENDPOINT_VOLUME_DOWN) }
    override suspend fun setMute(mute: Boolean) { request(ENDPOINT_SET_MUTE, mapOf("mute" to mute)) }
    override suspend fun channelUp() { request("tv/channelUp") }
    override suspend fun channelDown() { request("tv/channelDown") }
    override suspend fun powerOff() { request("system/turnOff") }
    override suspend fun powerOn() { request("system/turnOn") }

    override suspend fun mediaPlay() { request(ENDPOINT_MEDIA_PLAY) }
    override suspend fun mediaPause() { request(ENDPOINT_MEDIA_PAUSE) }
    override suspend fun mediaStop() { request(ENDPOINT_MEDIA_STOP) }
    override suspend fun mediaRewind() { request(ENDPOINT_MEDIA_REWIND) }
    override suspend fun mediaFastForward() { request(ENDPOINT_MEDIA_FORWARD) }
}
