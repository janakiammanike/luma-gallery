"use strict";
// Read-only platform capability checks. Nothing is enabled or patched here.
(function (root) {
  function status(value) {
    return value ? "Working" : "Unsupported";
  }

  function has(obj, name) {
    try { return typeof obj[name] !== "undefined"; } catch (err) { return false; }
  }

  function probe() {
    var video = document.createElement("video");
    var audio = document.createElement("audio");

    var result = {
      html5Video: status(has(window, "HTMLVideoElement")),
      html5Audio: status(has(window, "HTMLAudioElement")),
      mediaSource: status(has(window, "MediaSource")),
      mediaSourceApi: status(has(window, "MediaSource") && typeof window.MediaSource.isTypeSupported === "function"),
      nativeHls: "Unsupported",
      audioContext: status(has(window, "AudioContext") || has(window, "webkitAudioContext")),
      audioWorklet: "Unsupported",
      mediaCapabilities: "Not detected",
      mediaSession: status(has(navigator, "mediaSession")),
      fullscreen: status(typeof video.requestFullscreen === "function" || typeof video.webkitEnterFullscreen === "function"),
      webgl: "Unsupported",
      webgl2: "Unsupported",
      webgpu: "Unsupported",
      bluetooth: "Not detected",
      probedAt: Date.now()
    };

    try {
      if (video.canPlayType("application/vnd.apple.mpegurl") ||
          video.canPlayType("application/x-mpegURL")) result.nativeHls = "Working";
    } catch (err) { result.nativeHls = "Not testable"; }

    try {
      var Ctx = window.AudioContext || window.webkitAudioContext;
      if (Ctx) {
        var ctx = new Ctx();
        result.audioWorklet = has(ctx, "audioWorklet") ? "Working" : "Unsupported";
        result.sampleRate = ctx.sampleRate || null;
        ctx.close();
      }
    } catch (err) {
      result.audioContext = "Not testable";
    }

    try {
      if (navigator.mediaCapabilities && typeof navigator.mediaCapabilities.decodingInfo === "function") {
        result.mediaCapabilities = "Working";
      }
    } catch (err) { result.mediaCapabilities = "Not detected"; }

    try {
      var canvas = document.createElement("canvas");
      result.webgl = canvas.getContext("webgl") ? "Working" : "Unsupported";
      result.webgl2 = canvas.getContext("webgl2") ? "Working" : "Unsupported";
      result.webgpu = has(navigator, "gpu") ? "Working" : "Unsupported";
    } catch (err) {
      result.webgl = "Not testable";
      result.webgl2 = "Not testable";
      result.webgpu = "Not testable";
    }

    try {
      result.bluetooth = has(navigator, "bluetooth") ? "Partially working" : "Not detected";
    } catch (err) { result.bluetooth = "Not detected"; }

    result.canPlayTypeSupport = {
      audioElement: typeof audio.canPlayType === "function"
    };
    return result;
  }

  root.LumaMediaCapability = {
    probe: probe,
    status: status
  };
})(typeof globalThis !== "undefined" ? globalThis : this);
