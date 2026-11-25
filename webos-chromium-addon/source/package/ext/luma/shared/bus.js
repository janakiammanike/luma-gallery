"use strict";
// Namespaced message helper. Every Luma message carries source:"luma" so it can
// coexist with TV Tools in the same browser profile.
(function (root) {
  var SOURCE = "luma";

  function send(type, payload, tabId) {
    var message = { source: SOURCE, type: type, payload: payload || {} };
    if (tabId !== undefined && tabId !== null && chrome.tabs && chrome.tabs.sendMessage) {
      return chrome.tabs.sendMessage(tabId, message);
    }
    return chrome.runtime.sendMessage(message);
  }

  function on(handler) {
    chrome.runtime.onMessage.addListener(function (message, sender, sendResponse) {
      if (!message || message.source !== SOURCE) return;
      var result;
      try {
        result = handler(message, sender, sendResponse);
      } catch (err) {
        sendResponse({ ok: false, error: String((err && err.message) || err) });
        return;
      }
      if (result && typeof result.then === "function") {
        result.then(function (value) { sendResponse({ ok: true, value: value }); })
              .catch(function (err) { sendResponse({ ok: false, error: String((err && err.message) || err) }); });
        return true;
      }
      if (result !== undefined) sendResponse(result);
    });
  }

  root.LumaBus = { SOURCE: SOURCE, send: send, on: on };
})(typeof globalThis !== "undefined" ? globalThis : this);
