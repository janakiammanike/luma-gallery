"use strict";
// Experimental read-only analysis of a Luma-owned video element.
(function (root) {
  var samples = [];

  function record(element) {
    if (!element || (element.tagName || "").toLowerCase() !== "video") {
      return { ok: false, error: "A video element is required." };
    }
    try {
      var width = element.videoWidth || 0;
      var height = element.videoHeight || 0;
      samples.push({
        at: Date.now(),
        currentTime: element.currentTime || 0,
        buffered: element.buffered && element.buffered.length
          ? element.buffered.end(element.buffered.length - 1) : null,
        decodedFrames: typeof element.webkitDecodedFrameCount === "number" ? element.webkitDecodedFrameCount : null,
        droppedFrames: typeof element.webkitDroppedFrameCount === "number" ? element.webkitDroppedFrameCount : null,
        width: width,
        height: height
      });
      if (samples.length > 120) samples.shift();
      return { ok: true, samples: samples.length };
    } catch (err) {
      return { ok: false, error: String((err && err.message) || err) };
    }
  }

  function report() {
    if (!samples.length) return { status: "Not detected", note: "No samples recorded." };
    var last = samples[samples.length - 1];
    var first = samples[0];
    var elapsed = (last.at - first.at) / 1000;
    var frames = (last.decodedFrames != null && first.decodedFrames != null)
      ? last.decodedFrames - first.decodedFrames : null;
    var dropped = (last.droppedFrames != null && first.droppedFrames != null)
      ? last.droppedFrames - first.droppedFrames : null;
    return {
      status: "Partially working",
      sourceResolution: last.width && last.height ? last.width + "x" + last.height : "Unknown",
      quality: root.LumaMediaDetector ? root.LumaMediaDetector.qualityLabel(last.width, last.height) : "Unknown",
      estimatedFps: frames != null && elapsed > 0 ? (frames / elapsed).toFixed(1) : "Unknown",
      droppedFrames: dropped != null ? String(dropped) : "Not detected",
      bufferAhead: last.buffered != null && last.currentTime != null
        ? Math.max(0, last.buffered - last.currentTime).toFixed(2) + " s" : "Not detected",
      fps: "Unknown",
      bitrate: "Unknown",
      hdr: "Unknown",
      note: "FPS is an estimate from decoded frame counters; bitrate/HDR are not exposed by HTML5."
    };
  }

  function reset() { samples = []; return { ok: true }; }

  root.LumaVideoAnalyzer = { record: record, report: report, reset: reset };
})(typeof globalThis !== "undefined" ? globalThis : this);
