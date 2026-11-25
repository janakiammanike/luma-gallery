"use strict";
// Aggregated diagnostics. Every result is classified; nothing is fabricated.
(function (root) {
  function safe(fn) {
    try { return fn(); } catch (err) { return { status: "Not testable", error: String((err && err.message) || err) }; }
  }

  function run() {
    return {
      generatedAt: Date.now(),
      environment: {
        userAgent: safe(function () { return navigator.userAgent || "Unknown"; }),
        platform: safe(function () { return navigator.platform || "Unknown"; }),
        hardwareConcurrency: safe(function () { return navigator.hardwareConcurrency || "Not detected"; }),
        deviceMemoryGB: safe(function () { return navigator.deviceMemory || "Not detected"; })
      },
      html5Video: safe(function () { return { status: typeof window.HTMLVideoElement !== "undefined" ? "Working" : "Unsupported" }; }),
      hls: safe(function () { return root.LumaHlsManager ? root.LumaHlsManager.detect() : { status: "Not detected" }; }),
      mse: safe(function () { return { status: window.MediaSource ? "Working" : "Unsupported" }; }),
      codecs: safe(function () { return root.LumaCodecManager ? root.LumaCodecManager.matrix() : { status: "Not detected" }; }),
      videoAnalysis: safe(function () { return root.LumaVideoAnalyzer ? root.LumaVideoAnalyzer.report() : { status: "Not detected" }; }),
      audio: safe(function () { return root.LumaAudioEngine ? root.LumaAudioEngine.detect() : { status: "Not detected" }; }),
      audioDecoding: safe(function () { return root.LumaAudioDecoder ? root.LumaAudioDecoder.decodeCapability() : { status: "Not detected" }; }),
      audioCandidates: safe(function () { return root.LumaAudioDecoder ? root.LumaAudioDecoder.candidates() : []; }),
      webgl: safe(function () { return root.LumaUpscaling ? root.LumaUpscaling.capabilities() : { status: "Not detected" }; }),
      avSync: safe(function () { return root.LumaAvSync ? root.LumaAvSync.probe() : { status: "Not detected" }; }),
      bluetooth: safe(function () { return root.LumaBluetoothAudio ? root.LumaBluetoothAudio.detect() : { status: "Not detected" }; }),
      telemetry: { status: "Working", note: "No telemetry sender exists in the Luma extension." },
      note: "Performance metrics require actual TV playback. Unmeasured values are Not detected/Not testable."
    };
  }

  root.LumaMediaDiagnostics = { run: run };
})(typeof globalThis !== "undefined" ? globalThis : this);
