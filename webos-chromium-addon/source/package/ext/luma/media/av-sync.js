"use strict";
// A/V sync investigation. No fake delay control is exposed.
(function (root) {
  function probe() {
    var ctx = null;
    try {
      var Ctx = window.AudioContext || window.webkitAudioContext;
      if (!Ctx) return { status: "Unsupported", note: "AudioContext unavailable." };
      ctx = new Ctx();
      var result = {
        status: "Partially working",
        audioContextTime: Number(ctx.currentTime.toFixed(3)),
        baseLatency: typeof ctx.baseLatency === "number" ? Number(ctx.baseLatency.toFixed(4)) : "Not detected",
        outputLatency: typeof ctx.outputLatency === "number" ? Number(ctx.outputLatency.toFixed(4)) : "Not detected",
        videoTimingSource: "HTMLMediaElement.currentTime (read-only)",
        audioDelayControl: "Unsupported",
        bufferingControls: "Not detected",
        note: "No reliable platform A/V offset control is exposed, so Luma does not simulate a sync slider."
      };
      ctx.close();
      return result;
    } catch (err) {
      if (ctx) { try { ctx.close(); } catch (e) {} }
      return { status: "Not testable", error: String((err && err.message) || err) };
    }
  }

  root.LumaAvSync = { probe: probe };
})(typeof globalThis !== "undefined" ? globalThis : this);
