"use strict";
// Producer identity and capability negotiation. No credential, token or pairing
// secret is stored here: approval always happens on the phone.
(function (root) {
  var PRODUCER = "luma.chromium";
  var APP_VERSION = "0.2.2";

  var CAPABILITIES = [
    "diagnostics.refresh",
    "browser.open",
    "browser.close",
    "navigation.back",
    "navigation.forward",
    "navigation.reload",
    "navigation.close",
    "navigation.activate",
    "navigation.next",
    "navigation.previous",
    "navigation.open",
    "media.play",
    "media.pause",
    "media.toggle",
    "media.seek",
    "media.volume",
    "media.mute",
    "media.probe"
  ];

  var ALLOWED = {};
  CAPABILITIES.forEach(function (action) { ALLOWED[action] = true; });
  var RESERVED = { "app.focus": true };
  var ALLOWED_EVENTS = {
    "lifecycle.visibility": true,
    "diagnostics.snapshot": true,
    "connection_state": true
  };

  function helloPayload(sessionId) {
    return {
      producer: PRODUCER,
      app_version: APP_VERSION,
      session_id: sessionId,
      capabilities: CAPABILITIES.slice()
    };
  }

  function verifyCapability(payload) {
    if (!payload || payload.accepted_version !== 1) return "unsupported_accepted_version";
    if (Object.prototype.toString.call(payload.supported_types) !== "[object Array]") {
      return "invalid_supported_types";
    }
    if (Object.prototype.toString.call(payload.features) !== "[object Array]") {
      return "invalid_features";
    }
    if (typeof payload.telemetry_enabled !== "boolean") return "invalid_telemetry_flag";
    if (payload.telemetry_enabled === true) return "telemetry_not_allowed";
    return null;
  }

  function isAllowedAction(action) {
    return typeof action === "string" && !!ALLOWED[action] && !RESERVED[action];
  }

  function isAllowedEvent(name) {
    return typeof name === "string" && !!ALLOWED_EVENTS[name];
  }

  root.LumaRemoteAuth = {
    producer: PRODUCER,
    appVersion: APP_VERSION,
    capabilities: CAPABILITIES.slice(),
    helloPayload: helloPayload,
    verifyCapability: verifyCapability,
    isAllowedAction: isAllowedAction,
    isAllowedEvent: isAllowedEvent
  };
})(typeof globalThis !== "undefined" ? globalThis : this);
