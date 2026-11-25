"use strict";
// Web Audio processing for Luma-owned media only. Original Audio is a real bypass.
(function (root) {
  var state = {
    element: null,
    ctx: null,
    source: null,
    nodes: {},
    processing: false
  };

  function clamp(value, min, max, fallback) {
    var n = Number(value);
    if (!Number.isFinite(n)) n = fallback;
    return Math.max(min, Math.min(max, n));
  }

  function detect() {
    var Ctx = window.AudioContext || window.webkitAudioContext;
    if (!Ctx) return { status: "Unsupported" };
    try {
      var ctx = new Ctx();
      var result = {
        status: "Working",
        sampleRate: ctx.sampleRate || null,
        maxChannelCount: ctx.destination ? ctx.destination.maxChannelCount : null,
        audioWorklet: ctx.audioWorklet ? "Working" : "Unsupported",
        baseLatency: typeof ctx.baseLatency === "number" ? ctx.baseLatency : null,
        note: "Processing is available only for media owned by Luma and same-origin. Cross-origin/DRM audio is never processed."
      };
      ctx.close();
      return result;
    } catch (err) {
      return { status: "Not testable", error: String((err && err.message) || err) };
    }
  }

  function buildChain(element) {
    var Ctx = window.AudioContext || window.webkitAudioContext;
    if (!Ctx) throw new Error("Web Audio is unavailable.");
    var ctx = state.ctx || new Ctx();
    var source = ctx.createMediaElementSource(element);
    var low = ctx.createBiquadFilter(); low.type = "lowshelf"; low.frequency.value = 180;
    var mid = ctx.createBiquadFilter(); mid.type = "peaking"; mid.frequency.value = 1200; mid.Q.value = 0.7;
    var high = ctx.createBiquadFilter(); high.type = "highshelf"; high.frequency.value = 4500;
    var dialogue = ctx.createBiquadFilter(); dialogue.type = "peaking"; dialogue.frequency.value = 1800; dialogue.Q.value = 0.9;
    var balance = ctx.createStereoPanner ? ctx.createStereoPanner() : null;
    var compressor = ctx.createDynamicsCompressor();
    compressor.threshold.value = -12; compressor.knee.value = 12; compressor.ratio.value = 4;
    compressor.attack.value = 0.01; compressor.release.value = 0.2;
    var gain = ctx.createGain();

    source.connect(low); low.connect(mid); mid.connect(high); high.connect(dialogue);
    if (balance) { dialogue.connect(balance); balance.connect(compressor); }
    else dialogue.connect(compressor);
    compressor.connect(gain); gain.connect(ctx.destination);

    state.ctx = ctx;
    state.source = source;
    state.element = element;
    state.nodes = { low: low, mid: mid, high: high, dialogue: dialogue, balance: balance, compressor: compressor, gain: gain };
    state.processing = true;
    return { ok: true, note: "Audio is now routed through Web Audio. Use Original Audio to restore the native path." };
  }

  function enable(element) {
    if (state.element && state.element !== element) original();
    try {
      return Promise.resolve(buildChain(element));
    } catch (err) {
      return Promise.resolve({ ok: false, error: String((err && err.message) || err) });
    }
  }

  function apply(settings) {
    if (!state.processing || !state.ctx) return { ok: false, error: "Processing is not active." };
    var s = settings || {};
    try {
      var now = state.ctx.currentTime;
      state.nodes.low.gain.value = clamp(s.bass, -12, 12, 0);
      state.nodes.high.gain.value = clamp(s.treble, -12, 12, 0);
      state.nodes.mid.gain.value = clamp(s.equalizer, -12, 12, 0);
      state.nodes.dialogue.gain.value = clamp(s.dialogue, -6, 6, 0);
      state.nodes.gain.gain.value = clamp(s.volume, 0, 1.5, 1);
      if (state.nodes.balance) state.nodes.balance.pan.value = clamp(s.balance, -1, 1, 0);
      if (s.normalization === true) state.nodes.compressor.threshold.value = -24;
      else state.nodes.compressor.threshold.value = -12;
      if (state.ctx.state === "suspended") state.ctx.resume();
      return { ok: true, appliedAt: now, virtualSurround: s.virtualSurround === true ? "Experimental" : "Off" };
    } catch (err) {
      return { ok: false, error: String((err && err.message) || err) };
    }
  }

  function original() {
    try {
      if (state.source) state.source.disconnect();
      Object.keys(state.nodes).forEach(function (key) {
        try { if (state.nodes[key] && state.nodes[key].disconnect) state.nodes[key].disconnect(); } catch (err) {}
      });
      var ctx = state.ctx;
      state.source = null; state.ctx = null; state.element = null; state.nodes = {}; state.processing = false;
      if (ctx) ctx.close();
      return { ok: true, note: "Web Audio chain removed. Reload the media element to restore the native audio path." };
    } catch (err) {
      return { ok: false, error: String((err && err.message) || err) };
    }
  }

  root.LumaAudioEngine = {
    detect: detect,
    enable: enable,
    apply: apply,
    original: original,
    isProcessing: function () { return state.processing; },
    audioDelay: function () {
      return { status: "Unsupported", note: "No reliable platform audio-delay control is exposed to this extension." };
    }
  };
})(typeof globalThis !== "undefined" ? globalThis : this);
