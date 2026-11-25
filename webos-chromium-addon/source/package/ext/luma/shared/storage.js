"use strict";
// All persisted Luma state uses chrome.storage.local with luma.* keys.
// Nothing here sends data anywhere; there is no telemetry path.
(function (root) {
  var KEY_SETTINGS = "luma.settings";
  var KEY_LINKS = "luma.quickLinks";
  var KEY_REMOTE = "luma.remote";

  var DEFAULT_SETTINGS = {
    showDiagnostics: true,
    mediaScope: "active"
  };

  var DEFAULT_REMOTE = {
    host: "",
    port: 8787,
    autoConnect: false
  };

  var DEFAULT_LINKS = [
    { title: "YouTube", url: "https://www.youtube.com/" },
    { title: "Google", url: "https://www.google.com/" },
    { title: "BBC", url: "https://www.bbc.com/" }
  ];

  function get(keys) {
    return new Promise(function (resolve, reject) {
      chrome.storage.local.get(keys, function (items) {
        if (chrome.runtime.lastError) reject(new Error(chrome.runtime.lastError.message));
        else resolve(items || {});
      });
    });
  }

  function set(obj) {
    return new Promise(function (resolve, reject) {
      chrome.storage.local.set(obj, function () {
        if (chrome.runtime.lastError) reject(new Error(chrome.runtime.lastError.message));
        else resolve();
      });
    });
  }

  function getState() {
    return get([KEY_SETTINGS, KEY_LINKS]).then(function (items) {
      return {
        settings: Object.assign({}, DEFAULT_SETTINGS, items[KEY_SETTINGS] || {}),
        quickLinks: Array.isArray(items[KEY_LINKS]) ? items[KEY_LINKS] : DEFAULT_LINKS.slice()
      };
    });
  }

  function setSettings(settings) {
    var value = {};
    value[KEY_SETTINGS] = Object.assign({}, DEFAULT_SETTINGS, settings || {});
    return set(value);
  }

  function setQuickLinks(links) {
    var value = {};
    value[KEY_LINKS] = Array.isArray(links) ? links : DEFAULT_LINKS.slice();
    return set(value);
  }

  function getRemote() {
    return get([KEY_REMOTE]).then(function (items) {
      return Object.assign({}, DEFAULT_REMOTE, items[KEY_REMOTE] || {});
    });
  }

  function setRemote(remote) {
    var value = {};
    value[KEY_REMOTE] = Object.assign({}, DEFAULT_REMOTE, remote || {});
    return set(value);
  }

  function initDefaults() {
    return get([KEY_SETTINGS, KEY_LINKS, KEY_REMOTE]).then(function (items) {
      var writes = {};
      if (!items[KEY_SETTINGS]) writes[KEY_SETTINGS] = DEFAULT_SETTINGS;
      if (!items[KEY_LINKS]) writes[KEY_LINKS] = DEFAULT_LINKS;
      if (!items[KEY_REMOTE]) writes[KEY_REMOTE] = DEFAULT_REMOTE;
      return Object.keys(writes).length ? set(writes) : undefined;
    });
  }

  root.LumaStorage = {
    keys: { settings: KEY_SETTINGS, quickLinks: KEY_LINKS, remote: KEY_REMOTE },
    defaults: { settings: DEFAULT_SETTINGS, quickLinks: DEFAULT_LINKS, remote: DEFAULT_REMOTE },
    getState: getState,
    setSettings: setSettings,
    setQuickLinks: setQuickLinks,
    getRemote: getRemote,
    setRemote: setRemote,
    initDefaults: initDefaults
  };
})(typeof globalThis !== "undefined" ? globalThis : this);
