"use strict";
// Outbound-only WebSocket session. The extension never opens a listener, never
// uses native messaging and never talks to Luna. Chrome 116+ allows WebSocket
// in MV3 service workers; a 20s application ping keeps the worker alive.
(function (root) {
  var CONTRACT = root.LumaRemoteContract;
  var AUTH = root.LumaRemoteAuth;

  var PATH = CONTRACT.path;
  var MAX_FRAME_BYTES = CONTRACT.maxFrameBytes;
  var BACKOFF_MS = [1000, 2000, 4000, 8000, 16000];
  var KEEPALIVE_MS = 20000;
  var RATE_WINDOW_MS = 10000;
  var MAX_MESSAGES_PER_WINDOW = 20;
  var MAX_COMMANDS_PER_WINDOW = 20;
  var MAX_INVALID_BEFORE_CLOSE = 5;

  var state = {
    status: "idle",
    detail: "No session. Enter the phone LAN address in Luma Settings.",
    host: "",
    port: 8787,
    sessionId: "",
    socket: null,
    autoConnect: false,
    suspended: false,
    attempt: 0,
    handshake: "Not started",
    reconnectTimer: null,
    keepaliveTimer: null,
    commandHandler: null,
    commandTimes: [],
    messageTimes: [],
    invalid: 0,
    sequence: 0,
    capability: null,
    lastError: "none",
    lastUpdated: 0,
    lastPong: 0
  };

  var listeners = [];

  function isObject(value) {
    return Object.prototype.toString.call(value) === "[object Object]";
  }

  function randomHex(length) {
    var output = "";
    var bytes = new Uint8Array(Math.ceil(length / 2));
    if (root.crypto && typeof root.crypto.getRandomValues === "function") {
      root.crypto.getRandomValues(bytes);
    } else {
      for (var j = 0; j < bytes.length; j += 1) bytes[j] = Math.floor(Math.random() * 256);
    }
    for (var i = 0; i < bytes.length; i += 1) {
      var value = bytes[i].toString(16);
      output += value.length === 1 ? "0" + value : value;
    }
    return output.slice(0, length);
  }

  function nextId(prefix) {
    state.sequence += 1;
    return prefix + "_" + state.sequence + "_" + Date.now().toString(16);
  }

  function snapshot() {
    var protocol = CONTRACT.stats();
    var capability = state.capability;
    return {
      status: state.status,
      detail: state.detail,
      host: state.host,
      port: state.port,
      sessionShort: state.sessionId ? state.sessionId.substring(0, 8) : "",
      autoConnect: state.autoConnect,
      suspended: state.suspended,
      socketOpen: !!state.socket && state.socket.readyState === 1,
      attempt: state.attempt,
      handshake: state.handshake,
      invalid: state.invalid,
      tooLarge: protocol.tooLarge,
      unknownType: protocol.unknownType,
      valid: protocol.valid,
      lastError: state.lastError,
      lastUpdated: state.lastUpdated,
      lastPong: state.lastPong,
      capability: capability ? {
        acceptedVersion: capability.accepted_version,
        features: capability.features.slice(),
        telemetryEnabled: capability.telemetry_enabled
      } : null
    };
  }

  function emit() {
    var current = snapshot();
    listeners.forEach(function (listener) {
      try { listener(current); } catch (err) { /* a render listener must never break the channel */ }
    });
  }

  function setStatus(status, detail, error) {
    state.status = status;
    state.detail = detail || "";
    if (error) state.lastError = error;
    state.lastUpdated = Date.now();
    emit();
  }

  function clearReconnect() {
    if (state.reconnectTimer !== null) {
      clearTimeout(state.reconnectTimer);
      state.reconnectTimer = null;
    }
  }

  function clearKeepalive() {
    if (state.keepaliveTimer !== null) {
      clearInterval(state.keepaliveTimer);
      state.keepaliveTimer = null;
    }
  }

  function closeSocket(code) {
    var socket = state.socket;
    state.socket = null;
    clearKeepalive();
    if (!socket) return;
    try { socket.close(code || 1000, "luma cleanup"); } catch (err) { /* best effort */ }
  }

  function validateEndpoint(host, port) {
    var value = typeof host === "string" ? host.trim() : "";
    var parts = value.split(".");
    if (parts.length !== 4) {
      return { ok: false, reason: "Use a LAN IPv4 address, for example 192.168.1.3." };
    }
    for (var i = 0; i < parts.length; i += 1) {
      var part = parts[i];
      if (!/^\d{1,3}$/.test(part)) return { ok: false, reason: "Use four numeric IPv4 octets." };
      var number = Number(part);
      if (number < 0 || number > 255 || (part.length > 1 && part.charAt(0) === "0")) {
        return { ok: false, reason: "IPv4 octets must be between 0 and 255." };
      }
    }
    if (!/^\d{1,5}$/.test(String(port))) return { ok: false, reason: "Port must be between 1 and 65535." };
    var portNumber = Number(port);
    if (portNumber < 1 || portNumber > 65535) return { ok: false, reason: "Port must be between 1 and 65535." };
    return { ok: true, host: value, port: portNumber, reason: "ok" };
  }

  function send(type, payload) {
    var socket = state.socket;
    if (!socket || socket.readyState !== 1) return false;
    var raw = JSON.stringify(CONTRACT.buildEnvelope(type, nextId(type), payload || {}));
    if (CONTRACT.byteLength(raw) > MAX_FRAME_BYTES) {
      state.lastError = "outbound_too_large";
      emit();
      return false;
    }
    try {
      socket.send(raw);
      return true;
    } catch (err) {
      state.lastError = "send_failed";
      emit();
      return false;
    }
  }

  function sendPing() {
    if (state.socket && state.socket.readyState === 1) {
      send("ping", { sent_ms: Date.now() });
    }
  }

  function startKeepalive() {
    clearKeepalive();
    sendPing();
    state.keepaliveTimer = setInterval(sendPing, KEEPALIVE_MS);
  }

  function reject(reason, closeAfter) {
    state.invalid += 1;
    state.lastError = reason;
    if (state.invalid >= MAX_INVALID_BEFORE_CLOSE || closeAfter) closeSocket(1003);
    emit();
  }

  function rateLimited() {
    var now = Date.now();
    var fresh = state.messageTimes.filter(function (time) { return now - time < RATE_WINDOW_MS; });
    state.messageTimes = fresh;
    if (fresh.length >= MAX_MESSAGES_PER_WINDOW) {
      state.lastError = "rate_limited";
      closeSocket(1008);
      emit();
      return true;
    }
    state.messageTimes.push(now);
    return false;
  }

  function sendAck(reference, status, reason) {
    return send("ack", {
      ref_id: reference || "",
      status: status,
      reason: reason || null
    });
  }

  function validString(value, max) {
    return typeof value === "string" && value.length > 0 && value.length <= max;
  }

  function handleCapability(payload) {
    if (state.capability) {
      reject("unexpected_capability", true);
      return;
    }
    var problem = AUTH.verifyCapability(payload);
    if (problem) {
      reject(problem, true);
      return;
    }
    state.capability = payload;
    state.handshake = "Approved · v" + payload.accepted_version;
    startKeepalive();
    setStatus("connected", "Session ready · " + payload.features.length + " action(s)");
  }

  function commandSucceeded(value) {
    return value !== false && !(value && value.ok === false);
  }

  function handleCommand(message) {
    var payload = message.payload;
    var action = payload.action;
    var requestId = payload.request_id;
    var args = payload.args;
    var timeout = payload.timeout_ms;
    var now = Date.now();

    if (!validString(action, 128) || !validString(requestId, CONTRACT.maxIdLength) ||
        !isObject(args) || typeof timeout !== "number" || timeout < 1000 || timeout > 10000) {
      sendAck(requestId, "rejected", "invalid_command");
      reject("invalid_command");
      return;
    }

    var recent = state.commandTimes.filter(function (time) { return now - time < RATE_WINDOW_MS; });
    state.commandTimes = recent;
    if (recent.length >= MAX_COMMANDS_PER_WINDOW) {
      sendAck(requestId, "rejected", "rate_limited");
      reject("command_rate_limited");
      return;
    }
    state.commandTimes.push(now);

    if (!AUTH.isAllowedAction(action)) {
      sendAck(requestId, "rejected", "unsupported_action");
      reject("unsupported_action");
      return;
    }
    if (typeof state.commandHandler !== "function") {
      sendAck(requestId, "rejected", "handler_unavailable");
      reject("handler_unavailable");
      return;
    }

    var result;
    try {
      result = state.commandHandler(action, args);
    } catch (err) {
      result = false;
    }

    function finish(value, failureReason) {
      var ok = commandSucceeded(value);
      sendAck(requestId, ok ? "accepted" : "rejected", ok ? null : (failureReason || "command_failed"));
      if (!ok) reject(failureReason || "command_failed");
    }

    if (result && typeof result.then === "function") {
      result.then(function (value) { finish(value, null); },
                  function () { finish(false, "command_failed"); });
    } else {
      finish(result, null);
    }
  }

  function handleEvent(payload) {
    if (!validString(payload.name, 128) || !AUTH.isAllowedEvent(payload.name) || !isObject(payload.data)) {
      reject("invalid_event");
    }
  }

  function handleAck(payload) {
    var reference = payload.ref_id;
    var batchId = payload.batch_id;
    var status = payload.status;
    var hasReference = validString(reference, CONTRACT.maxIdLength);
    var hasBatch = validString(batchId, CONTRACT.maxIdLength);
    if ((!hasReference && !hasBatch) || ["accepted", "rejected", "partial"].indexOf(status) < 0) {
      reject("invalid_ack");
    }
  }

  function handleRaw(raw) {
    var result = CONTRACT.validateEnvelope(raw);
    if (!result.ok) {
      state.invalid += 1;
      state.lastError = result.reason;
      if (state.invalid >= MAX_INVALID_BEFORE_CLOSE) closeSocket(1003);
      emit();
      return;
    }
    if (rateLimited()) return;

    var message = result.message;
    switch (message.type) {
      case "capability":
        handleCapability(message.payload);
        break;
      case "command":
        handleCommand(message);
        break;
      case "event":
        handleEvent(message.payload);
        break;
      case "ack":
        handleAck(message.payload);
        break;
      case "ping":
        send("pong", { echo_ms: typeof message.payload.sent_ms === "number" ? message.payload.sent_ms : Date.now() });
        break;
      case "pong":
        state.lastPong = Date.now();
        break;
      case "hello":
        reject("unexpected_hello", true);
        break;
      case "telemetry_batch":
        sendAck(message.id, "rejected", "telemetry_disabled");
        reject("telemetry_disabled");
        break;
      default:
        reject("unsupported_type");
    }
  }

  function scheduleReconnect() {
    if (!state.autoConnect || state.suspended) return;
    clearReconnect();
    var index = state.attempt < BACKOFF_MS.length ? state.attempt : BACKOFF_MS.length - 1;
    var delay = BACKOFF_MS[index];
    state.attempt += 1;
    setStatus("reconnecting", "Retrying in " + (delay / 1000) + "s · attempt " + state.attempt);
    state.reconnectTimer = setTimeout(function () {
      state.reconnectTimer = null;
      open();
    }, delay);
  }

  function open() {
    if (state.suspended || !state.autoConnect || state.socket) return;
    var Socket = root.WebSocket || root.MozWebSocket;
    if (!Socket) {
      state.lastError = "websocket_unavailable";
      scheduleReconnect();
      return;
    }

    clearReconnect();
    var url = "ws://" + state.host + ":" + state.port + PATH;
    var socket;
    try {
      socket = new Socket(url);
    } catch (err) {
      state.lastError = "endpoint_rejected";
      scheduleReconnect();
      return;
    }

    state.socket = socket;
    state.sessionId = randomHex(32);
    state.handshake = "Socket opening";
    setStatus("connecting", "Opening channel to " + state.host + ":" + state.port);

    socket.onopen = function () {
      if (state.socket !== socket) return;
      state.attempt = 0;
      state.invalid = 0;
      state.commandTimes = [];
      state.messageTimes = [];
      state.lastError = "none";
      state.handshake = "Hello sent · awaiting phone approval";
      startKeepalive();
      send("hello", AUTH.helloPayload(state.sessionId));
      setStatus("handshaking", "Waiting for explicit approval on the phone");
    };

    socket.onmessage = function (event) {
      if (state.socket !== socket) return;
      if (!event || typeof event.data !== "string") {
        reject("non_text_frame");
        return;
      }
      handleRaw(event.data);
    };

    socket.onerror = function () {
      if (state.socket !== socket) return;
      state.lastError = "socket_error";
      emit();
    };

    socket.onclose = function (event) {
      if (state.socket !== socket) return;
      state.socket = null;
      clearKeepalive();
      state.capability = null;
      state.handshake = "Channel closed";
      var code = event && event.code ? event.code : 0;
      if (state.suspended) {
        setStatus("paused", "Channel closed while suspended.");
        return;
      }
      if (!state.autoConnect || code === 1000) {
        setStatus("idle", "Disconnected. No channel is open.");
        return;
      }
      scheduleReconnect();
    };
  }

  function connect(host, port, autoConnect) {
    var address = validateEndpoint(host, port);
    if (!address.ok) {
      state.lastError = address.reason;
      setStatus("failed", address.reason, address.reason);
      return address;
    }
    closeSocket(1000);
    clearReconnect();
    state.host = address.host;
    state.port = address.port;
    state.autoConnect = autoConnect !== false;
    state.attempt = 0;
    state.invalid = 0;
    state.sequence = 0;
    state.capability = null;
    state.lastError = "none";
    CONTRACT.resetCounters();
    open();
    return address;
  }

  function disconnect() {
    state.autoConnect = false;
    clearReconnect();
    closeSocket(1000);
    state.capability = null;
    state.handshake = "Not started";
    setStatus("idle", "Disconnected by user. No channel is open.");
  }

  function suspend() {
    state.suspended = true;
    clearReconnect();
    closeSocket(1000);
    state.capability = null;
    state.handshake = "Paused";
    setStatus("paused", "Paused. Channel and timers were closed.");
  }

  function resume() {
    state.suspended = false;
    if (!state.autoConnect || state.socket) {
      emit();
      return;
    }
    state.attempt = 0;
    open();
  }

  function sendMessage(type, payload) {
    if (state.status !== "connected" || !isObject(payload)) return false;
    return send(type, payload);
  }

  root.LumaRemote = {
    connect: connect,
    disconnect: disconnect,
    suspend: suspend,
    resume: resume,
    subscribe: subscribe,
    setCommandHandler: setCommandHandler,
    sendMessage: sendMessage,
    snapshot: snapshot,
    validateEndpoint: validateEndpoint
  };

  function subscribe(listener) {
    if (typeof listener === "function") listeners.push(listener);
  }

  function setCommandHandler(handler) {
    state.commandHandler = typeof handler === "function" ? handler : null;
  }
})(typeof globalThis !== "undefined" ? globalThis : this);
