package com.luma.tvremote2.host

import org.json.JSONObject
import java.nio.charset.StandardCharsets

const val CONTRACT_VERSION = 1
const val MAX_FRAME_BYTES = 65536
const val MAX_ID_LENGTH = 128

val KNOWN_TYPES = setOf(
    "hello",
    "capability",
    "command",
    "event",
    "ack",
    "telemetry_batch"
)

data class Envelope(
    val v: Int,
    val type: String,
    val id: String,
    val ts: Long,
    val payload: JSONObject
)

data class Validation(
    val ok: Boolean,
    val reason: String,
    val envelope: Envelope?
)

fun byteLength(text: String): Int = text.toByteArray(StandardCharsets.UTF_8).size

fun validateEnvelope(raw: String): Validation {
    if (byteLength(raw) > MAX_FRAME_BYTES) {
        return Validation(false, "message_too_large", null)
    }

    val objectValue = try {
        JSONObject(raw)
    } catch (error: Exception) {
        return Validation(false, "invalid_json", null)
    }

    val version = objectValue.opt("v")
    if (version !is Number || version.toDouble() != CONTRACT_VERSION.toDouble()) {
        return Validation(false, "unsupported_version", null)
    }

    val type = objectValue.opt("type")
    if (type !is String || type !in KNOWN_TYPES) {
        return Validation(false, "unknown_type", null)
    }

    val id = objectValue.opt("id")
    if (id !is String || id.isEmpty() || id.length > MAX_ID_LENGTH) {
        return Validation(false, "invalid_id", null)
    }

    val timestamp = objectValue.opt("ts")
    if (timestamp !is Number) {
        return Validation(false, "invalid_timestamp", null)
    }
    val timestampValue = timestamp.toDouble()
    if (java.lang.Double.isNaN(timestampValue) || java.lang.Double.isInfinite(timestampValue)) {
        return Validation(false, "invalid_timestamp", null)
    }

    val payload = objectValue.opt("payload") as? JSONObject
        ?: return Validation(false, "invalid_payload", null)

    return Validation(
        true,
        "ok",
        Envelope(CONTRACT_VERSION, type, id, timestamp.toLong(), payload)
    )
}

fun envelope(type: String, id: String, payload: JSONObject): String {
    val message = JSONObject()
    message.put("v", CONTRACT_VERSION)
    message.put("type", type)
    message.put("id", id)
    message.put("ts", System.currentTimeMillis())
    message.put("payload", payload)
    return message.toString()
}

fun newId(prefix: String): String {
    val suffix = Math.abs((Math.random() * 0xFFFFFFF).toInt()).toString(16)
    return prefix + "_" + System.currentTimeMillis().toString(16) + "_" + suffix
}
