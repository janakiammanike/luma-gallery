"use strict";
// Read-only inspection of media elements. It never patches page APIs or players.
(function (root) {
  function qualityLabel(width, height) {
    if (!width || !height) return "Unknown";
    if (width >= 3840 || height >= 2160) return "4K UHD";
    if (width >= 1920 || height >= 1080) return "Full HD";
    if (width >= 1280 || height >= 720) return "HD";
    return "SD";
  }

  function describe(el, index) {
    var info = {
      index: index,
      tag: el.tagName ? el.tagName.toLowerCase() : "unknown",
      source: "Unknown",
      sourceType: "Unknown",
      sourceResolution: "Unknown",
      quality: "Unknown",
      fps: "Unknown",
      bitrate: "Unknown",
      codec: "Unknown",
      hdr: "Unknown",
      audioCodec: "Unknown",
      audioChannels: "Unknown",
      sampleRate: "Unknown",
      bitDepth: "Unknown",
      status: "Unknown",
      buffering: "Not detected",
      droppedFrames: "Not detected",
      error: null,
      restricted: false,
      note: ""
    };

    try { info.source = el.currentSrc || el.src || "Unknown"; } catch (err) { info.restricted = true; }
    try { info.status = el.paused ? "Paused" : (el.ended ? "Ended" : "Playing"); } catch (err) {}
    try { info.duration = Number.isFinite(el.duration) ? el.duration : null; } catch (err) {}
    try { info.currentTime = el.currentTime || 0; } catch (err) {}
    try {
      info.sourceResolution = el.videoWidth && el.videoHeight
        ? el.videoWidth + "x" + el.videoHeight : "Unknown";
      info.quality = qualityLabel(el.videoWidth, el.videoHeight);
    } catch (err) {}
    try {
      if (el.buffered && el.buffered.length) {
        info.buffering = Math.max(0, el.buffered.end(el.buffered.length - 1) - (el.currentTime || 0)).toFixed(2) + " s ahead";
      }
    } catch (err) {}
    try {
      if (typeof el.webkitDecodedFrameCount === "number" || typeof el.webkitDroppedFrameCount === "number") {
        info.droppedFrames = String(el.webkitDroppedFrameCount || 0) + " dropped / " + String(el.webkitDecodedFrameCount || 0) + " decoded";
      }
    } catch (err) {}
    try {
      if (el.error) info.error = "MediaError code " + el.error.code;
    } catch (err) {}
    try {
      var url = String(info.source || "").toLowerCase();
      if (url.indexOf(".m3u8") >= 0) info.sourceType = "HLS playlist";
      else if (url.indexOf(".mpd") >= 0) info.sourceType = "DASH manifest";
      else if (url.indexOf(".mp4") >= 0) info.sourceType = "MP4";
      else if (url.indexOf(".webm") >= 0) info.sourceType = "WebM";
      else if (url.indexOf(".mkv") >= 0) info.sourceType = "Matroska";
      else if (url.indexOf("blob:") === 0) info.sourceType = "MSE blob";
      else if (url.indexOf("data:") === 0) info.sourceType = "Data URL";
    } catch (err) {}

    if (info.restricted || info.source === "Unknown" || info.error) {
      info.note = "Details may be limited by cross-origin, DRM or player encapsulation.";
    }
    return info;
  }

  function inspect() {
    var elements = [];
    try { elements = Array.prototype.slice.call(document.querySelectorAll("video, audio")); } catch (err) {}
    return {
      url: location.href,
      title: document.title || "",
      count: elements.length,
      elements: elements.map(describe),
      detectedAt: Date.now(),
      note: "Source resolution is not TV output resolution. Unknown is used when metadata is unavailable."
    };
  }

  root.LumaMediaDetector = { inspect: inspect, qualityLabel: qualityLabel };
})(typeof globalThis !== "undefined" ? globalThis : this);
