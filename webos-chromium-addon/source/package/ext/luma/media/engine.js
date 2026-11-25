"use strict";
// Unified facade. It coordinates modules but never replaces Chromium's player.
(function (root) {
  function ensure() {
    var names = ["LumaMediaCapability", "LumaCodecManager", "LumaHlsManager", "LumaMediaDetector",
      "LumaVideoEngine", "LumaVideoAnalyzer", "LumaVideoEnhancement", "LumaUpscaling",
      "LumaAudioEngine", "LumaAudioDecoder", "LumaBluetoothAudio", "LumaAvSync",
      "LumaProfileStorage", "LumaMediaDiagnostics"];
    return names.every(function (name) { return !!root[name]; });
  }

  function snapshot() {
    if (!ensure()) return { ok: false, error: "One or more media modules failed to load." };
    return {
      ok: true,
      capabilities: root.LumaMediaCapability.probe(),
      media: root.LumaMediaDetector.inspect(),
      videoAnalysis: root.LumaVideoAnalyzer.report(),
      audio: root.LumaAudioEngine.detect(),
      hls: root.LumaHlsManager.detect(),
      async: root.LumaAvSync.probe(),
      bluetooth: root.LumaBluetoothAudio.detect()
    };
  }

  function applyProfiles(element, data, processingEnabled) {
    if (!element) return { ok: false, error: "A Luma-owned element is required." };
    var result = { picture: null, audio: null };
    result.picture = root.LumaVideoEnhancement.apply(element, {
      brightness: data.picture.brightness,
      contrast: data.picture.contrast,
      saturation: data.picture.saturation,
      sharpness: data.picture.sharpness,
      noiseReduction: data.picture.noiseReduction,
      darkDetail: data.picture.darkDetail,
      aspectRatio: data.picture.aspectRatio,
      enhancementEnabled: data.enhancementEnabled === true
    });
    if (processingEnabled) {
      var audioSettings = Object.assign({}, data.audio, { volume: element.volume || 1 });
      result.audio = root.LumaAudioEngine.apply(audioSettings);
    } else {
      result.audio = { ok: true, note: "Original Audio is active; no Web Audio processing." };
    }
    return result;
  }

  root.LumaMediaEngine = {
    version: "0.3.1",
    ensure: ensure,
    snapshot: snapshot,
    diagnostics: function () { return root.LumaMediaDiagnostics.run(); },
    inspect: function () { return root.LumaMediaDetector.inspect(); },
    useVideo: function (element) { return root.LumaVideoEngine.use(element); },
    control: function (action, value) { return root.LumaVideoEngine.control(action, value); },
    applyProfiles: applyProfiles
  };
})(typeof globalThis !== "undefined" ? globalThis : this);
