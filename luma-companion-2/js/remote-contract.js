/*
 * Luma Companion 2.0 — versioned remote message validator.
 *
 * Phase 1 has no transport. This module exists so future remote integration has
 * one strict validation boundary instead of trusting arbitrary JSON.
 */
(function (global) {
  'use strict';

  var VERSION = 1;
  var MAX_MESSAGE_BYTES = 65536;
  var MAX_ID_LENGTH = 128;
  var KNOWN_TYPES = {
    hello: true,
    capability: true,
    command: true,
    event: true,
    ack: true,
    telemetry_batch: true
  };

  var counters = {
    valid: 0,
    invalid: 0,
    tooLarge: 0,
    unknownType: 0
  };

  function byteLength(value) {
    return unescape(encodeURIComponent(value)).length;
  }

  function isObject(value) {
    return Object.prototype.toString.call(value) === '[object Object]';
  }

  function reject(reason) {
    counters.invalid += 1;
    return { ok: false, reason: reason, message: null };
  }

  function validateEnvelope(raw) {
    var message;

    if (typeof raw === 'string') {
      if (byteLength(raw) > MAX_MESSAGE_BYTES) {
        counters.tooLarge += 1;
        return reject('message_too_large');
      }
      try {
        message = JSON.parse(raw);
      } catch (error) {
        return reject('invalid_json');
      }
    } else if (isObject(raw)) {
      message = raw;
    } else {
      return reject('not_an_object');
    }

    if (!isObject(message)) return reject('not_an_object');
    if (message.v !== VERSION) return reject('unsupported_version');
    if (typeof message.type !== 'string' || !KNOWN_TYPES[message.type]) {
      counters.unknownType += 1;
      return reject('unknown_type');
    }
    if (typeof message.id !== 'string' || message.id.length === 0 || message.id.length > MAX_ID_LENGTH) {
      return reject('invalid_id');
    }
    if (typeof message.ts !== 'number' || !isFinite(message.ts)) {
      return reject('invalid_timestamp');
    }
    if (!isObject(message.payload)) return reject('invalid_payload');

    counters.valid += 1;
    return { ok: true, reason: 'ok', message: message };
  }

  function resetCounters() {
    counters.valid = 0;
    counters.invalid = 0;
    counters.tooLarge = 0;
    counters.unknownType = 0;
  }

  function stats() {
    return {
      valid: counters.valid,
      invalid: counters.invalid,
      tooLarge: counters.tooLarge,
      unknownType: counters.unknownType
    };
  }

  global.LumaRemoteContract = {
    version: VERSION,
    maxMessageBytes: MAX_MESSAGE_BYTES,
    validateEnvelope: validateEnvelope,
    stats: stats,
    resetCounters: resetCounters
  };
})(window);
