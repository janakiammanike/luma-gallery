"use strict";
// CSS-filter based picture adjustment, only on a Luma-owned video element.
(function (root) {
  var original = {
    brightness: 0, contrast: 0, saturation: 0, sharpness: 0,
    noiseReduction: 0, darkDetail: 0, aspectRatio: "original", enhancementEnabled: false
  };

  function clamp(n, min, max) {
    var value = Number(n);
    if (!Number.isFinite(value)) value = 0;
    return Math.max(min, Math.min(max, value));
  }

  function apply(element, settings) {
    if (!element || (element.tagName || "").toLowerCase() !== "video") {
      return { ok: false, error: "A video element is required." };
    }
    var s = Object.assign({}, original, settings || {});
    s.enhancementEnabled = !!s.enhancementEnabled;
    var css = "none";
    if (s.enhancementEnabled) {
      var brightness = 1 + clamp(s.brightness, -10, 10) / 100;
      var contrast = 1 + clamp(s.contrast, -10, 10) / 100;
      var saturate = 1 + clamp(s.saturation, -10, 10) / 100;
      var blur = clamp(s.noiseReduction, 0, 5) * 0.08;
      css = "brightness(" + brightness.toFixed(3) + ") contrast(" + contrast.toFixed(3) +
            ") saturate(" + saturate.toFixed(3) + ")" + (blur ? " blur(" + blur.toFixed(2) + "px)" : "");
    }
    var ratio = s.aspectRatio === "original" ? "auto" : String(s.aspectRatio);
    try {
      element.style.filter = css;
      element.style.objectFit = ratio === "auto" ? "contain" : "fill";
      return {
        ok: true,
        active: s.enhancementEnabled,
        css: css,
        note: "Sharpness/dark-scene detail are represented through filter tuning only; no LG native picture engine control is claimed."
      };
    } catch (err) {
      return { ok: false, error: String((err && err.message) || err) };
    }
  }

  function restore(element) {
    if (!element) return { ok: false, error: "A video element is required." };
    element.style.filter = "none";
    element.style.objectFit = "contain";
    return { ok: true, note: "Original presentation restored." };
  }

  root.LumaVideoEnhancement = { apply: apply, restore: restore, defaults: original };
})(typeof globalThis !== "undefined" ? globalThis : this);
