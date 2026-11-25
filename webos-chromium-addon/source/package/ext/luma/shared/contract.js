"use strict";
// Versioned remote-envelope validator. Shared by the extension service worker.
// It mirrors the Android Host boundary in tvremote2/host/Envelope.kt.
(function (root) {
  var VERSION = 1;
  var PATH = "/luma/companion/v1";
  var MAX_FRAME_BYTES = 65536;
  var MAX_ID_LENGTH = 128;
  var KNOWN_TYPES = {
    hello: true,
    capability: true,
    command: true,
    event: true,
    ack: true,
    telemetry_batch: true,
    ping: true,
    pong: true
  };

  var counters = { valid: 0, invalid: 0, tooLarge: 0, unknownType: 0 };

  function isObject(value) {
    return Object.prototype.toString.call(value) === "[object Object]";
  }

  function byteLength(value) {
    if (typeof TextEncoder === "function") return new TextEncoder().encode(String(value)).length;
    return unescape(encodeURIComponent(String(value))).length;
  }

  function reject(reason) {
    counters.invalid += 1;
    return { ok: false, reason: reason, message: null };
  }

  function validateEnvelope(raw) {
    var message;
    if (typeof raw === "string") {
      if (byteLength(raw) > MAX_FRAME_BYTES) {
        counters.tooLarge += 1;
        return reject("message_too_large");
      }
      try {
        message = JSON.parse(raw);
      } catch (err) {
        return reject("invalid_json");
      }
    } else if (isObject(raw)) {
      message = raw;
    } else {
      return reject("not_an_object");
    }

    if (!isObject(message)) return reject("not_an_object");
    if (message.v !== VERSION) return reject("unsupported_version");
    if (typeof message.type !== "string" || !KNOWN_TYPES[message.type]) {
      counters.unknownType += 1;
      return reject("unknown_type");
    }
    if (typeof message.id !== "string" || !message.id || message.id.length > MAX_ID_LENGTH) {
      return reject("invalid_id");
    }
    if (typeof message.ts !== "number" || !isFinite(message.ts)) {
      return reject("invalid_timestamp");
    }
    if (!isObject(message.payload)) return reject("invalid_payload");

    counters.valid += 1;
    return { ok: true, reason: "ok", message: message };
  }

  function buildEnvelope(type, id, payload) {
    return {
      v: VERSION,
      type: type,
      id: id,
      ts: Date.now(),
      payload: payload || {}
    };
  }

  function resetCounters() {
    counters.valid = 0;
    counters.invalid = 0;
    counters.tooLarge = 0;
    counters.unknownType = 0;
  }

  root.LumaRemoteContract = {
    version: VERSION,
    path: PATH,
    maxFrameBytes: MAX_FRAME_BYTES,
    maxIdLength: MAX_ID_LENGTH,
    validateEnvelope: validateEnvelope,
    buildEnvelope: buildEnvelope,
    byteLength: byteLength,
    resetCounters: resetCounters,
    stats: function () {
      return {
        valid: counters.valid,
        invalid: counters.invalid,
        tooLarge: counters.tooLarge,
        unknownType: counters.unknownType
      };
    }
  };
})(typeof globalThis !== "undefined" ? globalThis : this);
