"use strict";
// Isolated-world DOM media control. It never patches page JavaScript and never
// touches the launcher's NDL socket. TV Tools keeps its own MAIN-world bridge.
(function () {
  function mediaElements() {
    return Array.prototype.slice.call(document.querySelectorAll("video, audio"));
  }

  function control(action, value) {
    var known = ["play", "pause", "toggle", "seek", "volume", "mute"];
    if (known.indexOf(action) < 0) return { ok: false, error: "Unknown media action: " + action };
    var elements = mediaElements();
    if (!elements.length) return { ok: false, error: "No video or audio element on this page." };

    var failed = 0;
    elements.forEach(function (m) {
      try {
        switch (action) {
          case "play":
            var p = m.play();
            if (p && typeof p.catch === "function") p.catch(function () { failed++; });
            break;
          case "pause":
            m.pause();
            break;
          case "toggle":
            if (m.paused || m.ended) {
              var q = m.play();
              if (q && typeof q.catch === "function") q.catch(function () { failed++; });
            } else {
              m.pause();
            }
            break;
          case "seek":
            m.currentTime = Math.max(0, Math.min(m.duration || 0, Number(value) || 0));
            break;
          case "volume":
            m.volume = Math.max(0, Math.min(1, Number(value)));
            break;
          case "mute":
            m.muted = !m.muted;
            break;
        }
      } catch (err) {
        failed++;
      }
    });

    return { ok: true, count: elements.length, failed: failed };
  }

  function probe() {
    if (!globalThis.LumaCapability) return Promise.resolve({ ok: false, error: "Capability probe not loaded." });
    return globalThis.LumaCapability.probe().then(function (result) { return { ok: true, value: result }; });
  }

  globalThis.LumaBus.on(function (message) {
    if (message.type === "media-control") {
      return control(message.payload.action, message.payload.value);
    }
    if (message.type === "probe-media") {
      return probe();
    }
  });
})();
