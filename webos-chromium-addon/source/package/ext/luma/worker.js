"use strict";
importScripts(
  "shared/bus.js",
  "shared/storage.js",
  "shared/contract.js",
  "remote/authentication.js",
  "remote/connection.js"
);

(function () {
  var DEFAULT_HW = { hardware: false, h264: false, lowmem: false, freeMB: null };

  function activeTab(sender) {
    if (sender && sender.tab && typeof sender.tab.id === "number") return Promise.resolve(sender.tab);
    return chrome.tabs.query({ active: true, lastFocusedWindow: true }).then(function (tabs) {
      if (tabs && tabs[0]) return tabs[0];
      return chrome.tabs.query({ active: true });
    }).then(function (tabs) {
      return Array.isArray(tabs) ? tabs[0] : tabs;
    });
  }

  function hardware() {
    return fetch(chrome.runtime.getURL("hw.json")).then(function (response) {
      if (!response.ok) return DEFAULT_HW;
      return response.json();
    }).catch(function () { return DEFAULT_HW; });
  }

  function tabSummary() {
    return chrome.tabs.query({}).then(function (tabs) {
      return (tabs || []).map(function (t) {
        return {
          id: t.id,
          title: t.title || "",
          url: t.url || "",
          active: !!t.active,
          audible: !!t.audible,
          discarded: !!t.discarded
        };
      });
    }).catch(function () { return []; });
  }

  function safeUrl(raw) {
    try {
      var u = new URL(String(raw || ""));
      if (u.protocol === "http:" || u.protocol === "https:") return u.href;
    } catch (err) { /* invalid URL */ }
    return null;
  }

  function toTab(type, sender) {
    return activeTab(sender).then(function (tab) {
      if (!tab || typeof tab.id !== "number") throw new Error("No active tab is available.");
      return tab;
    });
  }

  function mediaTab() {
    return chrome.tabs.query({}).then(function (tabs) {
      var candidates = (tabs || []).filter(function (tab) {
        return typeof tab.id === "number" && /^https?:\/\//i.test(tab.url || "");
      });
      if (!candidates.length) throw new Error("No web page tab is available for media inspection.");
      return candidates.filter(function (tab) { return tab.active && tab.audible; })[0] ||
             candidates.filter(function (tab) { return tab.audible; })[0] ||
             candidates.filter(function (tab) { return tab.active; })[0] ||
             candidates[0];
    });
  }

  function navigate(action, sender) {
    return toTab("navigate", sender).then(function (tab) {
      var id = tab.id;
      switch (action) {
        case "back":
          if (typeof chrome.tabs.goBack !== "function") throw new Error("Tab history back is not available on this Chromium build.");
          return chrome.tabs.goBack(id);
        case "forward":
          if (typeof chrome.tabs.goForward !== "function") throw new Error("Tab history forward is not available on this Chromium build.");
          return chrome.tabs.goForward(id);
        case "reload":
          return chrome.tabs.reload(id);
        case "close":
          return chrome.tabs.remove(id);
        case "activate":
          return chrome.tabs.update(id, { active: true });
        default:
          throw new Error("Unknown navigation action: " + action);
      }
    }).then(function () { return { ok: true }; });
  }

  function switchTab(delta) {
    return chrome.tabs.query({ currentWindow: true }).then(function (tabs) {
      if (!Array.isArray(tabs) || tabs.length < 2) throw new Error("Only one tab is open in this window.");
      var index = 0;
      for (var i = 0; i < tabs.length; i += 1) {
        if (tabs[i].active) { index = i; break; }
      }
      var next = (index + delta + tabs.length) % tabs.length;
      return chrome.tabs.update(tabs[next].id, { active: true });
    }).then(function () { return { ok: true }; });
  }

  function numericArg(value, min, max) {
    var number = Number(value);
    if (!isFinite(number)) throw new Error("A numeric value is required.");
    return Math.max(min, Math.min(max, number));
  }

  function media(action, value) {
    return toContent("media-control", { action: action, value: value }, null);
  }

  function remoteAction(action, args) {
    var payload = args || {};
    switch (action) {
      case "diagnostics.refresh":
        return Promise.all([hardware(), tabSummary()]).then(function (values) {
          return { hardware: values[0], tabCount: values[1].length };
        });

      case "browser.open":
      case "navigation.open":
        var url = safeUrl(payload.url);
        if (!url) return Promise.resolve({ ok: false, error: "Only http:// and https:// links can be opened." });
        return chrome.tabs.create({ url: url }).then(function (tab) { return { ok: true, id: tab.id }; });

      case "browser.close":
      case "navigation.close":
        return navigate("close", null);
      case "navigation.back":
        return navigate("back", null);
      case "navigation.forward":
        return navigate("forward", null);
      case "navigation.reload":
        return navigate("reload", null);
      case "navigation.activate":
        return navigate("activate", null);
      case "navigation.next":
        return switchTab(1);
      case "navigation.previous":
        return switchTab(-1);

      case "media.play":
        return media("play");
      case "media.pause":
        return media("pause");
      case "media.toggle":
        return media("toggle");
      case "media.seek":
        return media("seek", numericArg(payload.value, 0, Number.MAX_SAFE_INTEGER));
      case "media.volume":
        return media("volume", numericArg(payload.value, 0, 1));
      case "media.mute":
        return media("mute", !!payload.value);
      case "media.probe":
        return toContent("probe-media", {}, null);

      default:
        return Promise.resolve({ ok: false, error: "unsupported_action" });
    }
  }

  function toContent(type, payload, sender) {
    return toTab(type, sender).then(function (tab) {
      return LumaBus.send(type, payload, tab.id);
    }).then(function (reply) {
      if (reply && reply.ok === false) return { ok: false, error: reply.error || "Content script reported an error." };
      return reply && reply.value !== undefined ? reply.value : { ok: true };
    });
  }

  chrome.runtime.onInstalled.addListener(function () {
    LumaStorage.initDefaults().catch(function () { /* storage will init on first use */ });
  });

  LumaRemote.setCommandHandler(remoteAction);
  LumaStorage.getRemote().then(function (remote) {
    if (remote.host && remote.autoConnect) LumaRemote.connect(remote.host, remote.port, true);
  }).catch(function () { /* a fresh profile will save the endpoint in Settings */ });

  LumaBus.on(function (message, sender) {
    if (sender && sender.id && sender.id !== chrome.runtime.id) {
      return { ok: false, error: "Message is not from Luma." };
    }

    var payload = message.payload || {};

    switch (message.type) {
      case "get-state":
        return Promise.all([
          LumaStorage.getState(),
          hardware(),
          tabSummary(),
          activeTab(sender).catch(function () { return null; })
        ]).then(function (values) {
          return {
            settings: values[0].settings,
            quickLinks: values[0].quickLinks,
            hardware: values[1],
            tabs: values[2],
            activeTab: values[3]
          };
        });

      case "set-settings":
        return LumaStorage.setSettings(payload.settings).then(function () { return { ok: true }; });

      case "set-quick-links":
        return LumaStorage.setQuickLinks(payload.links).then(function () { return { ok: true }; });

      case "open-url":
        var url = safeUrl(payload.url);
        if (!url) return { ok: false, error: "Only http:// and https:// links can be opened." };
        return chrome.tabs.create({ url: url }).then(function (tab) {
          return { ok: true, id: tab.id };
        });

      case "navigate":
        return navigate(payload.action, sender);

      case "media-control":
        return toContent("media-control", {
          action: payload.action,
          value: payload.value
        }, sender);

      case "probe-media":
        return toContent("probe-media", {}, sender);

      case "media-inspect-active":
        return mediaTab().then(function (tab) {
          return LumaBus.send("probe-media-detail", {}, tab.id);
        }).then(function (reply) {
          if (reply && reply.ok === false) return { ok: false, error: reply.error || "Media probe failed." };
          return reply && reply.value !== undefined ? reply.value : { ok: true };
        });

      case "remote-state":
        return Promise.resolve(LumaRemote.snapshot());

      case "remote-connect":
        var address = LumaRemote.validateEndpoint(payload.host, payload.port);
        if (!address.ok) return { ok: false, error: address.reason };
        return LumaStorage.setRemote({
          host: address.host,
          port: address.port,
          autoConnect: payload.autoConnect !== false
        }).then(function () {
          var started = LumaRemote.connect(address.host, address.port, payload.autoConnect !== false);
          if (!started.ok) throw new Error(started.reason);
          return LumaRemote.snapshot();
        });

      case "remote-disconnect":
        return LumaStorage.getRemote().then(function (remote) {
          return LumaStorage.setRemote({
            host: remote.host,
            port: remote.port,
            autoConnect: false
          });
        }).then(function () {
          LumaRemote.disconnect();
          return LumaRemote.snapshot();
        });

      default:
        return { ok: false, error: "Unknown Luma message type: " + message.type };
    }
  });
})();
