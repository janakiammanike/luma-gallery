/*
 * Luma Companion 2.0 — Phase 2 remote session client.
 *
 * This module owns the only outbound network edge in the TV app. It uses a
 * plain WebSocket client, never opens a listener, and every frame passes
 * through LumaRemoteContract before it is acted upon.
 *
 * Scope is deliberately narrow: hello, capability, command, event and ack.
 * Browser commands are allow-listed; app.focus stays disabled. Telemetry is
 * emitted only by the separate telemetry module after explicit opt-in.
 */
(function (global) {
  'use strict';

  var VERSION = 1;
  var PATH = '/luma/companion/v1';
  var MAX_FRAME_BYTES = 65536;
  var MAX_ID_LENGTH = 128;
  var MAX_INVALID_BEFORE_CLOSE = 5;
  var RATE_WINDOW_MS = 10000;
  var MAX_MESSAGES_PER_WINDOW = 20;
  var MAX_COMMANDS_PER_WINDOW = 20;
  var BACKOFF_MS = [1000, 2000, 4000, 8000, 16000];

  var CAPABILITIES = [
    'diagnostics.refresh',
    'browser.open',
    'browser.close',
    'browser.back',
    'browser.forward',
    'browser.reload',
    'browser.home',
    'browser.new_tab',
    'browser.close_tab',
    'browser.next_tab',
    'browser.previous_tab',
    'video.play',
    'video.pause',
    'video.seek'
  ];
  var ALLOWED_ACTIONS = {};
  for (var capabilityIndex = 0; capabilityIndex < CAPABILITIES.length; capabilityIndex += 1) {
    ALLOWED_ACTIONS[CAPABILITIES[capabilityIndex]] = true;
  }
  var RESERVED_ACTIONS = {
    'app.focus': true
  };
  var ALLOWED_EVENTS = {
    'lifecycle.visibility': true,
    'diagnostics.snapshot': true,
    'connection_state': true
  };

  var state = {
    status: 'idle',
    detail: 'No session. Enter the phone LAN address to connect.',
    host: '',
    port: 8787,
    sessionId: '',
    socket: null,
    autoConnect: false,
    suspended: false,
    attempt: 0,
    handshake: 'Not started',
    reconnectTimer: null,
    commandHandler: null,
    ackHandler: null,
    commandTimes: [],
    messageTimes: [],
    invalid: 0,
    tooLarge: 0,
    unknownType: 0,
    lastError: 'none',
    lastUpdated: 0,
    sequence: 0,
    capability: null
  };

  var listeners = [];

  function isObject(value) {
    return Object.prototype.toString.call(value) === '[object Object]';
  }

  function byteLength(value) {
    return unescape(encodeURIComponent(value)).length;
  }

  function randomHex(length) {
    var output = '';
    var i;
    var value;
    var cryptoApi = global.crypto || global.msCrypto;

    if (cryptoApi && typeof cryptoApi.getRandomValues === 'function') {
      var bytes = new Uint8Array(length / 2);
      cryptoApi.getRandomValues(bytes);
      for (i = 0; i < bytes.length; i += 1) {
        value = bytes[i].toString(16);
        output += value.length === 1 ? '0' + value : value;
      }
      return output;
    }

    for (i = 0; i < length / 2; i += 1) {
      value = Math.floor(Math.random() * 256).toString(16);
      output += value.length === 1 ? '0' + value : value;
    }
    return output;
  }

  function nextId(prefix) {
    state.sequence += 1;
    return prefix + '_' + state.sequence + '_' + Date.now().toString(16);
  }

  function snapshot() {
    var protocol = global.LumaRemoteContract ? global.LumaRemoteContract.stats() : {
      valid: 0,
      invalid: 0,
      tooLarge: 0,
      unknownType: 0
    };
    return {
      status: state.status,
      detail: state.detail,
      host: state.host,
      port: state.port,
      sessionShort: state.sessionId ? state.sessionId.substring(0, 8) : '',
      autoConnect: state.autoConnect,
      suspended: state.suspended,
      socketOpen: !!state.socket && state.socket.readyState === 1,
      attempt: state.attempt,
      handshake: state.handshake,
      invalid: state.invalid || protocol.invalid,
      tooLarge: state.tooLarge || protocol.tooLarge,
      unknownType: state.unknownType || protocol.unknownType,
      valid: protocol.valid,
      lastError: state.lastError,
      lastUpdated: state.lastUpdated,
      capability: state.capability
    };
  }

  function emit() {
    var current = snapshot();
    for (var i = 0; i < listeners.length; i += 1) {
      try {
        listeners[i](current);
      } catch (error) {
        /* a render listener must never break the channel */
      }
    }
  }

  function setStatus(status, detail, error) {
    state.status = status;
    state.detail = detail;
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

  function closeSocket(code) {
    var socket = state.socket;
    state.socket = null;
    if (!socket) return;
    try {
      socket.close(code || 1000, 'client cleanup');
    } catch (error) {
      /* closing a failed socket is best effort only */
    }
  }

  function scheduleReconnect() {
    if (!state.autoConnect || state.suspended) return;
    clearReconnect();
    var index = state.attempt < BACKOFF_MS.length ? state.attempt : BACKOFF_MS.length - 1;
    var delay = BACKOFF_MS[index];
    state.attempt += 1;
    setStatus('reconnecting', 'Retrying in ' + (delay / 1000) + 's · attempt ' + state.attempt, state.lastError);
    state.reconnectTimer = setTimeout(function () {
      state.reconnectTimer = null;
      open();
    }, delay);
  }

  function fail(reason) {
    state.lastError = reason;
    closeSocket(1000);
    scheduleReconnect();
  }

  function validateEndpoint(host, port) {
    var value = typeof host === 'string' ? host.replace(/^\s+|\s+$/g, '') : '';
    var parts = value.split('.');
    var i;
    var part;
    var number;

    if (parts.length !== 4) {
      return { ok: false, reason: 'Use a LAN IPv4 address, for example 192.168.1.3.' };
    }
    for (i = 0; i < parts.length; i += 1) {
      part = parts[i];
      if (!/^\d{1,3}$/.test(part)) {
        return { ok: false, reason: 'Use four numeric IPv4 octets.' };
      }
      number = Number(part);
      if (number < 0 || number > 255 || (part.length > 1 && part.charAt(0) === '0')) {
        return { ok: false, reason: 'IPv4 octets must be between 0 and 255.' };
      }
    }
    if (!/^\d{1,5}$/.test(String(port))) {
      return { ok: false, reason: 'Port must be a number between 1 and 65535.' };
    }
    number = Number(port);
    if (number < 1 || number > 65535) {
      return { ok: false, reason: 'Port must be between 1 and 65535.' };
    }
    return { ok: true, host: value, port: number, reason: 'ok' };
  }

  function send(type, payload) {
    var socket = state.socket;
    if (!socket || socket.readyState !== 1) return false;
    if (!global.LumaRemoteContract) return false;

    var message = {
      v: VERSION,
      type: type,
      id: nextId(type),
      ts: Date.now(),
      payload: payload
    };
    var raw = JSON.stringify(message);
    if (byteLength(raw) > MAX_FRAME_BYTES) {
      state.lastError = 'outbound_too_large';
      emit();
      return false;
    }
    try {
      socket.send(raw);
      return true;
    } catch (error) {
      state.lastError = 'send_failed';
      emit();
      return false;
    }
  }

  function sendCommandAck(reference, accepted, reason) {
    return send('ack', {
      ref_id: reference || '',
      status: accepted ? 'accepted' : 'rejected',
      reason: accepted ? null : (reason || 'rejected')
    });
  }

  function reject(reason, closeAfter) {
    state.invalid += 1;
    state.lastError = reason;
    if (state.invalid >= MAX_INVALID_BEFORE_CLOSE || closeAfter) {
      closeSocket(1003);
    }
    emit();
  }

  function rateLimited() {
    var now = Date.now();
    var fresh = [];
    var i;
    for (i = 0; i < state.messageTimes.length; i += 1) {
      if (now - state.messageTimes[i] < RATE_WINDOW_MS) fresh.push(state.messageTimes[i]);
    }
    state.messageTimes = fresh;
    if (fresh.length >= MAX_MESSAGES_PER_WINDOW) {
      state.lastError = 'rate_limited';
      closeSocket(1008);
      emit();
      return true;
    }
    state.messageTimes.push(now);
    return false;
  }

  function validString(value, max) {
    return typeof value === 'string' && value.length > 0 && value.length <= (max || MAX_ID_LENGTH);
  }

  function handleHello(payload) {
    if (state.sessionId && state.handshake !== 'Not started') {
      reject('unexpected_hello', true);
      return;
    }
    if (payload.producer !== 'com.luma.companion2') {
      reject('invalid_producer', true);
      return;
    }
    if (!isObject(payload.capabilities)) {
      reject('invalid_capabilities', true);
      return;
    }
    state.handshake = 'Hello sent · awaiting phone approval';
    emit();
  }

  function handleCapability(payload) {
    if (payload.accepted_version !== VERSION) {
      reject('unsupported_accepted_version', true);
      return;
    }
    if (Object.prototype.toString.call(payload.supported_types) !== '[object Array]') {
      reject('invalid_supported_types', true);
      return;
    }
    if (Object.prototype.toString.call(payload.features) !== '[object Array]') {
      reject('invalid_features', true);
      return;
    }
    if (typeof payload.telemetry_enabled !== 'boolean') {
      reject('invalid_telemetry_flag', true);
      return;
    }
    var telemetryEnabled = payload.telemetry_enabled === true;
    if (telemetryEnabled && (!global.LumaTelemetry ||
        typeof global.LumaTelemetry.isEnabled !== 'function' ||
        !global.LumaTelemetry.isEnabled())) {
      reject('telemetry_not_enabled', true);
      return;
    }
    state.capability = {
      acceptedVersion: payload.accepted_version,
      supportedTypes: payload.supported_types,
      features: payload.features,
      telemetryEnabled: telemetryEnabled
    };
    state.handshake = 'Approved · v' + payload.accepted_version;
    setStatus('connected', 'Session ready · ' + payload.features.join(', '));
  }

  function handleCommand(message) {
    var payload = message.payload;
    var action = payload.action;
    var requestId = payload.request_id;
    var args = payload.args;
    var timeout = payload.timeout_ms;
    var commandTime = Date.now();
    var recent = [];

    if (!validString(action, 128) || !validString(requestId, 128) || !isObject(args) ||
        typeof timeout !== 'number' || timeout < 1000 || timeout > 10000) {
      sendCommandAck(requestId, false, 'invalid_command');
      reject('invalid_command');
      return;
    }

    for (var i = 0; i < state.commandTimes.length; i += 1) {
      if (commandTime - state.commandTimes[i] < RATE_WINDOW_MS) recent.push(state.commandTimes[i]);
    }
    state.commandTimes = recent;
    if (recent.length >= MAX_COMMANDS_PER_WINDOW) {
      sendCommandAck(requestId, false, 'rate_limited');
      reject('command_rate_limited');
      return;
    }
    state.commandTimes.push(commandTime);

    if (RESERVED_ACTIONS[action] || !ALLOWED_ACTIONS[action]) {
      sendCommandAck(requestId, false, 'action_disabled');
      reject('unsupported_action');
      return;
    }

    if (typeof state.commandHandler !== 'function') {
      sendCommandAck(requestId, false, 'handler_unavailable');
      reject('handler_unavailable');
      return;
    }

    var handled = false;
    try {
      handled = state.commandHandler(action, args) === true;
    } catch (error) {
      handled = false;
    }
    sendCommandAck(requestId, handled, handled ? null : 'command_failed');
    if (!handled) reject('command_failed');
  }

  function handleEvent(payload) {
    if (!validString(payload.name, 128) || !ALLOWED_EVENTS[payload.name] || !isObject(payload.data)) {
      reject('invalid_event');
    }
  }

  function handleAck(payload) {
    if (!validString(payload.ref_id, 128) && !validString(payload.batch_id, 128)) {
      reject('invalid_ack');
      return;
    }
    if (payload.status !== 'accepted' && payload.status !== 'rejected' &&
        payload.status !== 'partial') {
      reject('invalid_ack');
      return;
    }
    if (typeof state.ackHandler === 'function') {
      try {
        state.ackHandler(payload);
      } catch (error) {
        reject('ack_handler_failed');
      }
    }
  }

  function handleRaw(raw) {
    if (!global.LumaRemoteContract) return;
    var result = global.LumaRemoteContract.validateEnvelope(raw);
    if (!result.ok) {
      state.invalid += 1;
      state.lastError = result.reason;
      if (result.reason === 'message_too_large') state.tooLarge += 1;
      if (result.reason === 'unknown_type') state.unknownType += 1;
      if (state.invalid >= MAX_INVALID_BEFORE_CLOSE) closeSocket(1003);
      emit();
      return;
    }
    if (rateLimited()) return;

    var message = result.message;
    if (message.type === 'capability') handleCapability(message.payload);
    else if (message.type === 'command') handleCommand(message);
    else if (message.type === 'event') handleEvent(message.payload);
    else if (message.type === 'ack') handleAck(message.payload);
    else if (message.type === 'hello') handleHello(message.payload);
    else if (message.type === 'telemetry_batch') {
      sendCommandAck(message.id, false, 'telemetry_disabled');
      reject('telemetry_disabled');
    }
  }

  function open() {
    if (state.suspended || !state.autoConnect || state.socket) return;
    clearReconnect();

    var WebSocketCtor = global.WebSocket || global.MozWebSocket;
    if (!WebSocketCtor) {
      fail('websocket_unavailable');
      return;
    }

    var url = 'ws://' + state.host + ':' + state.port + PATH;
    var socket;
    try {
      socket = new WebSocketCtor(url);
    } catch (error) {
      fail('endpoint_rejected');
      return;
    }

    state.socket = socket;
    state.sessionId = randomHex(32);
    state.handshake = 'Socket opening';
    setStatus('connecting', 'Opening channel to ' + state.host + ':' + state.port);

    socket.onopen = function () {
      if (state.socket !== socket) return;
      state.attempt = 0;
      state.commandTimes = [];
      state.messageTimes = [];
      state.invalid = 0;
      state.lastError = 'none';
      state.handshake = 'Hello sent · awaiting phone approval';
      setStatus('handshaking', 'Waiting for explicit approval on the phone');
      send('hello', {
        producer: 'com.luma.companion2',
        app_version: '0.1.0',
        session_id: state.sessionId,
        capabilities: CAPABILITIES.slice(0)
      });
    };

    socket.onmessage = function (event) {
      if (state.socket !== socket) return;
      if (!event || typeof event.data !== 'string') {
        reject('non_text_frame');
        return;
      }
      handleRaw(event.data);
    };

    socket.onerror = function () {
      if (state.socket !== socket) return;
      state.lastError = 'socket_error';
      emit();
    };

    socket.onclose = function (event) {
      if (state.socket !== socket) return;
      state.socket = null;
      state.handshake = 'Channel closed';
      state.capability = null;
      var code = event && event.code ? event.code : 0;
      if (state.suspended) {
        setStatus('paused', 'App hidden. Channel and timers were closed.');
        return;
      }
      if (!state.autoConnect || code === 1000) {
        setStatus('idle', state.autoConnect ? 'Channel closed.' : 'Disconnected by user.', state.lastError);
        return;
      }
      scheduleReconnect();
    };
  }

  function connect(host, port) {
    var address = validateEndpoint(host, port);
    if (!address.ok) {
      state.lastError = address.reason;
      setStatus('failed', address.reason, address.reason);
      return address;
    }
    if (state.socket) closeSocket(1000);
    clearReconnect();
    state.host = address.host;
    state.port = address.port;
    state.autoConnect = true;
    state.attempt = 0;
    state.invalid = 0;
    state.tooLarge = 0;
    state.unknownType = 0;
    state.lastError = 'none';
    state.capability = null;
    if (global.LumaRemoteContract) global.LumaRemoteContract.resetCounters();
    open();
    return address;
  }

  function disconnect() {
    state.autoConnect = false;
    clearReconnect();
    closeSocket(1000);
    state.handshake = 'Not started';
    state.capability = null;
    setStatus('idle', 'Disconnected by user. No channel is open.');
  }

  function suspend() {
    state.suspended = true;
    clearReconnect();
    closeSocket(1000);
    state.handshake = 'Paused';
    state.capability = null;
    setStatus('paused', 'App hidden. Channel and timers were closed.');
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

  function subscribe(listener) {
    if (typeof listener === 'function') listeners.push(listener);
  }

  function setCommandHandler(handler) {
    state.commandHandler = typeof handler === 'function' ? handler : null;
  }

  function setAckHandler(handler) {
    state.ackHandler = typeof handler === 'function' ? handler : null;
  }

  function sendMessage(type, payload) {
    if (!state.socket || state.socket.readyState !== 1 || state.status !== 'connected') return false;
    if (!isObject(payload)) return false;
    send(type, payload);
    return true;
  }

  function sessionId() {
    return state.sessionId;
  }

  function capability() {
    if (!state.capability) return null;
    return {
      acceptedVersion: state.capability.acceptedVersion,
      supportedTypes: state.capability.supportedTypes.slice(0),
      features: state.capability.features.slice(0),
      telemetryEnabled: state.capability.telemetryEnabled
    };
  }

  global.LumaRemoteSession = {
    path: PATH,
    connect: connect,
    disconnect: disconnect,
    suspend: suspend,
    resume: resume,
    subscribe: subscribe,
    setCommandHandler: setCommandHandler,
    setAckHandler: setAckHandler,
    sendMessage: sendMessage,
    sessionId: sessionId,
    capability: capability,
    snapshot: snapshot,
    validateEndpoint: validateEndpoint
  };
})(window);
