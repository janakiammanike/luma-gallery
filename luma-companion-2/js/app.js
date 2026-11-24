/*
 * Luma Companion 2.0 — foreground lifecycle and LAN session controller.
 *
 * The browser UI is owned by LumaBrowserController. This file keeps platform
 * diagnostics, the opt-in outbound session and the Companion dashboard.
 */
(function () {
  'use strict';

  var FALLBACK_APP_INFO = { id: 'com.luma.companion2', version: '0.1.0' };

  var state = {
    active: false,
    online: navigator.onLine !== false,
    appInfo: FALLBACK_APP_INFO,
    deviceInfo: { model: 'unavailable', sdk: 'unavailable', firmware: 'unavailable' },
    userAgent: LumaDiagnostics.userAgentInfo(),
    bridge: LumaDiagnostics.systemBridge(),
    storage: LumaStorage.read(),
    remote: LumaRemoteSession.snapshot(),
    timers: [],
    probeHandle: null,
    probeGeneration: 0,
    lastUpdated: 0
  };

  function byId(id) {
    return document.getElementById(id);
  }

  function setText(id, value) {
    var element = byId(id);
    if (element) element.textContent = value;
  }

  function formatTime(timestamp) {
    if (!timestamp) return '—';
    try {
      return new Date(timestamp).toLocaleTimeString();
    } catch (error) {
      return 'just now';
    }
  }

  function markStorageFailure() {
    state.storage.storageFailures += 1;
    state.storage.available = false;
    if (!LumaStorage.write(state.storage)) {
      state.storage.storageFailures += 1;
    } else {
      state.storage.available = true;
    }
  }

  function connectionLabel(remote) {
    var labels = {
      idle: 'No session',
      connecting: 'Connecting…',
      handshaking: 'Waiting for phone approval',
      connected: 'Connected · session ready',
      reconnecting: 'Reconnecting…',
      failed: 'Connection failed',
      paused: 'Paused · app hidden'
    };
    return labels[remote.status] || remote.status;
  }

  function publishDiagnosticsInfo() {
    var info = window.LumaDiagnosticsInfo || {};
    info.version = state.appInfo.version || FALLBACK_APP_INFO.version;
    info.appId = state.appInfo.id || FALLBACK_APP_INFO.id;
    info.sdk = state.deviceInfo.sdk || 'unavailable';
    info.model = state.deviceInfo.model || 'unavailable';
    info.engine = state.userAgent.engine || 'unavailable';
    info.webosTv = !!state.userAgent.webosTv;
    info.bridge = state.bridge;
    if (!info.viewport) info.viewport = window.innerWidth + ' × ' + window.innerHeight;
    if (!info.dpr) info.dpr = window.devicePixelRatio || 1;
    window.LumaDiagnosticsInfo = info;
  }

  function render() {
    var remote = LumaRemoteSession.snapshot();
    var protocol = LumaRemoteContract.stats();
    var storageLabel = state.storage.available
      ? 'OK · local counters only'
      : 'Degraded · writes unavailable';
    var connected = remote.status === 'connected';
    var busy = remote.status === 'connecting' || remote.status === 'handshaking' || remote.status === 'reconnecting';

    publishDiagnosticsInfo();

    setText('connection-state', connectionLabel(remote));
    setText('connection-detail', remote.detail);
    setText('connection-badge', remote.status.toUpperCase());
    setText('connection-session', remote.sessionShort || '—');
    setText('connection-handshake', remote.handshake);
    setText('connection-attempt', String(remote.attempt));
    setText('connection-protocol', protocol.valid + ' valid · ' + remote.invalid + ' rejected');

    var dot = byId('connection-dot');
    if (dot) dot.className = 'dot ' + (connected ? 'dot-good' : 'dot-idle');

    var connectButton = byId('connect-button');
    if (connectButton) connectButton.disabled = remote.autoConnect || !state.active;
    var disconnectButton = byId('disconnect-button');
    if (disconnectButton) disconnectButton.disabled = !remote.autoConnect || !state.active;

    setText('companion-state', state.active
      ? (connected ? 'Foreground · session active' : 'Foreground only')
      : 'Suspended');
    setText('companion-detail', state.active
      ? 'No JS service, no TCP listener and no UDP beacon exist on the TV. The outbound channel closes on hide.'
      : 'webOS suspended the app. Timers, probes and the outbound channel were cleaned up.');

    setText('platform-app-id', state.appInfo.id || FALLBACK_APP_INFO.id);
    setText('platform-version', state.appInfo.version || FALLBACK_APP_INFO.version);
    setText('platform-engine', state.userAgent.engine);
    setText('platform-webos', state.userAgent.webosTv ? 'WEBOS TV DETECTED' : 'PLATFORM UNVERIFIED');
    setText('platform-bridge', state.bridge);
    setText('platform-model', state.deviceInfo.model);
    setText('platform-sdk', state.deviceInfo.sdk);

    setText('diagnostics-lifecycle', state.active ? 'Foreground' : 'Suspended');
    setText('diagnostics-network', !state.online
      ? 'Offline · browser can still open local addresses'
      : (connected ? 'Online · session ready' : (busy ? 'Online · session starting' : 'Online · no session')));
    setText('diagnostics-storage', storageLabel);
    setText('diagnostics-protocol', remote.status + ' · invalid ' + protocol.invalid);
    setText('diagnostics-telemetry', 'Off · not enabled');
    setText('last-updated', formatTime(state.lastUpdated));

    setText('browser-status-remote', remote.status.toUpperCase());
    setText('browser-status-network', !state.online ? 'OFFLINE' : 'ONLINE');
  }

  function clearTimers() {
    for (var i = 0; i < state.timers.length; i += 1) {
      clearInterval(state.timers[i]);
    }
    state.timers = [];
  }

  function cancelProbe() {
    if (state.probeHandle && typeof state.probeHandle.cancel === 'function') {
      try {
        state.probeHandle.cancel();
      } catch (error) {
        /* ignore cleanup errors */
      }
    }
    state.probeHandle = null;
  }

  function runPlatformProbe() {
    var generation = ++state.probeGeneration;
    cancelProbe();

    LumaDiagnostics.collectAppInfo(function (info) {
      if (generation !== state.probeGeneration) return;
      if (info && info.id) {
        state.appInfo = { id: info.id, version: info.version || FALLBACK_APP_INFO.version };
      }
      state.lastUpdated = Date.now();
      render();
    });

    state.probeHandle = LumaDiagnostics.collectDeviceInfo(function (info) {
      if (generation !== state.probeGeneration) return;
      state.deviceInfo = info;
      state.probeHandle = null;
      state.lastUpdated = Date.now();
      render();
    });
  }

  function activate() {
    if (state.active) return;
    state.active = true;
    state.online = navigator.onLine !== false;
    state.storage.launches += 1;
    if (LumaStorage.write(state.storage)) {
      state.storage.available = true;
    } else {
      markStorageFailure();
    }

    clearTimers();
    state.timers.push(setInterval(function () {
      if (!state.active) return;
      state.lastUpdated = Date.now();
      render();
    }, 30000));

    runPlatformProbe();
    LumaRemoteSession.resume();
    if (window.LumaBrowserController && typeof window.LumaBrowserController.resume === 'function') {
      window.LumaBrowserController.resume();
    }
    render();
  }

  function deactivate() {
    if (!state.active) return;
    state.active = false;
    clearTimers();
    cancelProbe();
    if (window.LumaBrowserController && typeof window.LumaBrowserController.suspend === 'function') {
      window.LumaBrowserController.suspend();
    }
    LumaRemoteSession.suspend();
    render();
  }

  function isObject(value) {
    return Object.prototype.toString.call(value) === '[object Object]';
  }

  function attachEvents() {
    document.addEventListener('visibilitychange', function () {
      if (document.hidden || document.webkitHidden) deactivate();
      else activate();
    }, true);

    document.addEventListener('webkitvisibilitychange', function () {
      if (document.hidden || document.webkitHidden) deactivate();
      else activate();
    }, true);

    document.addEventListener('webOSLaunch', activate, true);
    document.addEventListener('webOSRelaunch', activate, true);
    window.addEventListener('pageshow', activate);
    window.addEventListener('pagehide', deactivate);

    window.addEventListener('online', function () {
      state.online = true;
      render();
    });
    window.addEventListener('offline', function () {
      state.online = false;
      render();
    });
    window.addEventListener('resize', publishDiagnosticsInfo);

    var refresh = byId('refresh-button');
    if (refresh) {
      refresh.addEventListener('click', function () {
        if (!state.active) return;
        runPlatformProbe();
      });
    }

    LumaRemoteSession.subscribe(function (snapshot) {
      state.remote = snapshot;
      render();
    });

    LumaRemoteSession.setCommandHandler(function (action, args) {
      if (!state.active || !isObject(args)) return false;
      if (window.LumaBrowserController && typeof window.LumaBrowserController.command === 'function' &&
          window.LumaBrowserController.command(action, args)) {
        return true;
      }
      if (action === 'diagnostics.refresh') {
        runPlatformProbe();
        return true;
      }
      return false;
    });

    var hostInput = byId('host-input');
    var portInput = byId('port-input');
    var connectButton = byId('connect-button');
    var disconnectButton = byId('disconnect-button');

    function connectFromForm() {
      if (!state.active) return;
      var address = LumaRemoteSession.connect(hostInput ? hostInput.value : '', portInput ? portInput.value : '');
      if (!address.ok) render();
    }

    if (connectButton) connectButton.addEventListener('click', connectFromForm);
    if (disconnectButton) disconnectButton.addEventListener('click', function () {
      LumaRemoteSession.disconnect();
    });

    var inputs = [hostInput, portInput];
    for (var i = 0; i < inputs.length; i += 1) {
      if (!inputs[i]) continue;
      inputs[i].addEventListener('keydown', function (event) {
        var code = event.keyCode || event.which;
        if (code === 13) {
          connectFromForm();
          event.preventDefault();
        }
      });
    }
  }

  function init() {
    if (window.LumaBrowserController && typeof window.LumaBrowserController.init === 'function') {
      window.LumaBrowserController.init();
    }
    attachEvents();
    state.lastUpdated = Date.now();
    activate();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
