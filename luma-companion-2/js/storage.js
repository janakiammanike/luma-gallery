/*
 * Luma Companion 2.0 — resilience wrapper for non-sensitive local counters.
 *
 * Storage failures must never crash the dashboard. Only aggregate counters are
 * stored; no URLs, tokens, keys, keystrokes, media titles or identifiers.
 */
(function (global) {
  'use strict';

  var KEY = 'luma.companion2.diagnostics.v1';
  var DEFAULTS = { launches: 0, storageFailures: 0 };

  function safeNumber(value) {
    return typeof value === 'number' && isFinite(value) && value >= 0 ? Math.floor(value) : 0;
  }

  function defaults(available) {
    return {
      launches: DEFAULTS.launches,
      storageFailures: DEFAULTS.storageFailures,
      available: available
    };
  }

  function read() {
    var raw;
    try {
      raw = global.localStorage.getItem(KEY);
    } catch (error) {
      return defaults(false);
    }
    if (!raw) return defaults(true);
    try {
      var parsed = JSON.parse(raw);
      return {
        launches: safeNumber(parsed && parsed.launches),
        storageFailures: safeNumber(parsed && parsed.storageFailures),
        available: true
      };
    } catch (error) {
      return defaults(true);
    }
  }

  function write(value) {
    try {
      global.localStorage.setItem(KEY, JSON.stringify({
        launches: safeNumber(value && value.launches),
        storageFailures: safeNumber(value && value.storageFailures)
      }));
      return true;
    } catch (error) {
      return false;
    }
  }

  function clear() {
    try {
      global.localStorage.removeItem(KEY);
      return true;
    } catch (error) {
      return false;
    }
  }

  global.LumaStorage = {
    read: read,
    write: write,
    clear: clear
  };
})(window);
