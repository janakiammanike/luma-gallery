"use strict";
(function () {
  var status = document.getElementById("status");

  function say(text) { status.textContent = text || ""; }

  function call(type, payload) {
    return LumaBus.send(type, payload || {}).then(function (reply) {
      if (!reply || reply.ok === false) throw new Error((reply && reply.error) || "Luma command failed.");
      return reply.value;
    });
  }

  function renderLinks(links) {
    var box = document.getElementById("links");
    box.textContent = "";
    (links || []).forEach(function (link) {
      var b = document.createElement("button");
      b.textContent = link.title || link.url;
      b.setAttribute("data-url", link.url);
      box.appendChild(b);
    });
    if (!box.children.length) box.textContent = "No quick links yet. Add them in Settings.";
  }

  function renderTab(state) {
    var active = state.activeTab;
    var box = document.getElementById("tab");
    if (!active) { box.textContent = "No active tab is available."; return; }
    var host = "";
    try { host = new URL(active.url || "").host || active.url; } catch (err) { host = active.url || "unknown"; }
    box.textContent = (active.title || host) + "  ·  " + host + "  ·  " + state.tabs.length + " tab(s) open";
  }

  function renderHardware(state) {
    var h = state.hardware || {};
    var box = document.getElementById("hw");
    var lines = [
      "Hardware video: " + (h.hardware ? "available" : "not available"),
      "H.264 flag: " + (h.h264 ? "yes" : "no"),
      "Low memory mode: " + (h.lowmem ? "on" : "off"),
      "Free memory at start: " + (h.freeMB === null || h.freeMB === undefined ? "unknown" : h.freeMB + " MB"),
      "Open tabs: " + (state.tabs || []).length
    ];
    box.textContent = lines.join("  ·  ");
  }

  function renderProbe(result) {
    var box = document.getElementById("probe");
    box.hidden = false;
    if (!result) { box.textContent = "No probe result."; return; }
    var lines = [];
    var ms = result.mediaSource || {};
    Object.keys(ms).forEach(function (type) {
      lines.push((ms[type] ? "yes" : "no") + "  MediaSource  " + type);
    });
    if (result.decodingInfo) {
      if (result.decodingInfo.error) lines.push("decodingInfo: " + result.decodingInfo.error);
      else lines.push("decodingInfo: " + (result.decodingInfo.supported ? "supported" : "not supported"));
    }
    var pre = document.createElement("pre");
    pre.textContent = lines.join("\n");
    box.textContent = "";
    box.appendChild(pre);
  }

  function refresh() {
    return call("get-state").then(function (state) {
      renderLinks(state.quickLinks);
      renderTab(state);
      renderHardware(state);
      if (!state.settings.showDiagnostics) document.getElementById("hw").hidden = true;
    }).catch(function (err) { say(err.message); });
  }

  document.addEventListener("DOMContentLoaded", function () {
    refresh();
    document.body.addEventListener("click", function (event) {
      var b = event.target.closest("button");
      if (!b) return;
      var action = b.getAttribute("data-action");
      var url = b.getAttribute("data-url");
      if (url) {
        say("Opening " + url);
        call("open-url", { url: url }).then(function () { say("Opened in a new tab."); }).catch(function (err) { say(err.message); });
        return;
      }
      if (!action) return;
      if (action === "probe") {
        say("Checking local codec support…");
        call("probe-media").then(renderProbe).catch(function (err) { say(err.message); });
        return;
      }
      if (["play", "pause", "toggle", "mute"].indexOf(action) >= 0) {
        call("media-control", { action: action }).then(function (result) {
          say("Media control sent to " + (result && result.count ? result.count + " element(s)." : "the active tab."));
        }).catch(function (err) { say(err.message); });
        return;
      }
      if (["back", "forward", "reload", "close"].indexOf(action) >= 0) {
        call("navigate", { action: action }).then(function () {
          say(action + " done.");
          refresh();
        }).catch(function (err) { say(err.message); });
      }
    });
  });
})();
