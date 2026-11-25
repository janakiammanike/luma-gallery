"use strict";
// HTML5 video engine. It acts only on an element explicitly provided by Luma.
(function (root) {
  var current = null;

  function use(element) {
    if (!element || (element.tagName || "").toLowerCase() !== "video") {
      return { ok: false, error: "A video element is required." };
    }
    current = element;
    return { ok: true, tag: "video" };
  }

  function control(action, value) {
    var el = current;
    if (!el) return Promise.resolve({ ok: false, error: "No Luma-selected video element." });
    return new Promise(function (resolve) {
      try {
        var playPromise = null;
        switch (action) {
          case "play": playPromise = el.play(); break;
          case "pause": el.pause(); break;
          case "toggle":
            if (el.paused || el.ended) playPromise = el.play(); else el.pause();
            break;
          case "seek":
            el.currentTime = Math.max(0, Math.min(Number.isFinite(el.duration) ? el.duration : 0, Number(value) || 0));
            break;
          case "volume": el.volume = Math.max(0, Math.min(1, Number(value))); break;
          case "mute": el.muted = !el.muted; break;
          case "fullscreen":
            if (typeof el.webkitEnterFullscreen === "function") el.webkitEnterFullscreen();
            else if (typeof el.requestFullscreen === "function") el.requestFullscreen();
            else return resolve({ ok: false, error: "Fullscreen API not available." });
            break;
          default:
            return resolve({ ok: false, error: "Unknown video action: " + action });
        }
        if (playPromise && typeof playPromise.then === "function") {
          playPromise.then(function () { resolve({ ok: true, action: action }); })
                     .catch(function (err) { resolve({ ok: false, error: String((err && err.message) || err) }); });
        } else {
          resolve({ ok: true, action: action });
        }
      } catch (err) {
        resolve({ ok: false, error: String((err && err.message) || err) });
      }
    });
  }

  function info() {
    if (!current) return { ok: false, error: "No Luma-selected video element." };
    if (root.LumaMediaDetector) {
      var all = root.LumaMediaDetector.inspect();
      var found = all.elements.filter(function (item) { return item.tag === "video"; })[0] || null;
      return { ok: true, value: found, note: all.note };
    }
    return { ok: true, value: { source: current.currentSrc || "Unknown" } };
  }

  root.LumaVideoEngine = {
    use: use,
    control: control,
    info: info,
    current: function () { return current; }
  };
})(typeof globalThis !== "undefined" ? globalThis : this);
