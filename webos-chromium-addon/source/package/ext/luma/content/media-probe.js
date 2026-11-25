"use strict";
// Read-only detail probe for the Luma Cinema panel. No player is patched.
(function () {
  if (!window.LumaBus || !window.LumaMediaDetector) return;
  LumaBus.on(function (message) {
    if (message.type !== "probe-media-detail") return undefined;
    return { ok: true, value: LumaMediaDetector.inspect() };
  });
})();
