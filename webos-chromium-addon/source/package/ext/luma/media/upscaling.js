"use strict";
// WebGL shader-based scaling experiment for a Luma-owned video element.
// No AI model is bundled and no LG native upscaling is controlled.
(function (root) {
  function capabilities() {
    var canvas = document.createElement("canvas");
    var gl = null;
    try { gl = canvas.getContext("webgl") || canvas.getContext("experimental-webgl"); } catch (err) {}
    var gpu = false;
    try { gpu = !!(navigator.gpu); } catch (err) {}
    return {
      webgl: gl ? "Working" : "Unsupported",
      webgl2: (function () {
        try { return canvas.getContext("webgl2") ? "Working" : "Unsupported"; } catch (err) { return "Unsupported"; }
      })(),
      webgpu: gpu ? "Working" : "Unsupported",
      renderer: gl ? (function () {
        try {
          var dbg = gl.getExtension("WEBGL_debug_renderer_info");
          return dbg ? gl.getParameter(dbg.UNMASKED_RENDERER_WEBGL) : "Not exposed";
        } catch (err) { return "Not exposed"; }
      })() : "Not detected",
      note: "WebGPU is detected only; no WebGPU pipeline is implemented."
    };
  }

  function benchmark(element, frames) {
    var caps = capabilities();
    if (caps.webgl === "Unsupported" && caps.webgl2 === "Unsupported") {
      return Promise.resolve({ status: "Unsupported", reason: "WebGL unavailable.", capabilities: caps });
    }
    var count = Math.max(1, Math.min(120, Number(frames) || 30));
    return new Promise(function (resolve) {
      var canvas = document.createElement("canvas");
      canvas.width = 64; canvas.height = 64;
      var gl = canvas.getContext("webgl2") || canvas.getContext("webgl");
      if (!gl) { resolve({ status: "Unsupported", capabilities: caps }); return; }
      var start = performance.now();
      var i = 0;
      function step() {
        gl.clearColor((i % 255) / 255, 0.5, 0.25, 1);
        gl.clear(gl.COLOR_BUFFER_BIT);
        i++;
        if (i < count) requestAnimationFrame(step);
        else {
          var elapsed = performance.now() - start;
          resolve({
            status: "Experimental",
            frames: count,
            elapsedMs: Number(elapsed.toFixed(2)),
            averageMsPerFrame: Number((elapsed / count).toFixed(3)),
            estimatedFps: Number((1000 / (elapsed / count)).toFixed(1)),
            note: " synthetic clear benchmark; not a real video-scaling measurement.",
            capabilities: caps
          });
        }
      }
      requestAnimationFrame(step);
    });
  }

  root.LumaUpscaling = { capabilities: capabilities, benchmark: benchmark };
})(typeof globalThis !== "undefined" ? globalThis : this);
