/*
 * Luma Companion 2.0 — read-only platform diagnostics.
 *
 * Uses official webOS browser bridges only when present. If a bridge is absent
 * or denied, the dashboard degrades honestly instead of inventing values.
 */
(function (global) {
  'use strict';

  function textOr(value, fallback) {
    return typeof value === 'string' && value.length > 0 ? value : fallback;
  }

  function userAgentInfo() {
    var ua = navigator.userAgent || '';
    var match = ua.match(/Chrome\/([\d.]+)/);
    return {
      userAgent: ua,
      engine: match ? 'Chromium ' + match[1] : 'Unknown engine',
      webosTv: /Web0S; Linux\/SmartTV/i.test(ua)
    };
  }

  function systemBridge() {
    if (global.PalmSystem) return 'PalmSystem';
    if (global.webOSSystem) return 'webOSSystem';
    return 'unavailable';
  }

  function collectAppInfo(onDone) {
    var webOS = global.webOS;
    var path;

    if (webOS && typeof webOS.fetchAppInfo === 'function') {
      try {
        path = typeof webOS.fetchAppRootPath === 'function' ? webOS.fetchAppRootPath() : '';
        webOS.fetchAppInfo(function (info) {
          onDone(info || null);
        }, path ? path + 'appinfo.json' : undefined);
        return;
      } catch (error) {
        onDone(null);
        return;
      }
    }
    onDone(null);
  }

  /*
   * One-shot TV systemInfo request. No subscription is created, so cleanup is a
   * no-op on webOS 4.x. The cancel function is returned for future safety.
   */
  function collectDeviceInfo(onDone) {
    var result = { model: 'unavailable', sdk: 'unavailable', firmware: 'unavailable' };
    var webOS = global.webOS;
    var cancelled = false;

    if (!webOS || !webOS.service || typeof webOS.service.request !== 'function') {
      onDone(result);
      return { cancel: function () { cancelled = true; } };
    }

    var handle;
    try {
      handle = webOS.service.request('luna://com.webos.service.tv.systemproperty', {
        method: 'getSystemInfo',
        parameters: { keys: ['modelName', 'firmwareVersion', 'sdkVersion'] },
        onComplete: function (response) {
          if (cancelled) return;
          if (response && response.returnValue === true) {
            result.model = textOr(response.modelName, result.model);
            result.sdk = textOr(response.sdkVersion, result.sdk);
            result.firmware = textOr(response.firmwareVersion, result.firmware);
          }
          onDone(result);
        }
      });
    } catch (error) {
      onDone(result);
      return { cancel: function () { cancelled = true; } };
    }

    return {
      cancel: function () {
        cancelled = true;
        if (handle && typeof handle.cancel === 'function') {
          try { handle.cancel(); } catch (error) { /* ignore cleanup errors */ }
        }
      }
    };
  }

  global.LumaDiagnostics = {
    userAgentInfo: userAgentInfo,
    systemBridge: systemBridge,
    collectAppInfo: collectAppInfo,
    collectDeviceInfo: collectDeviceInfo
  };
})(window);
