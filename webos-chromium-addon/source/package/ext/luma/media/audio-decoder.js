"use strict";
// Audio decoding checks. No library is bundled and no protected audio is handled.
(function (root) {
  var TYPES = [
    { name: "FLAC", mime: "audio/flac" },
    { name: "WAV", mime: "audio/wav" },
    { name: "PCM", mime: "audio/wave" },
    { name: "AAC", mime: "audio/mp4; codecs=\"mp4a.40.2\"" },
    { name: "MP3", mime: "audio/mpeg" },
    { name: "Opus", mime: "audio/webm; codecs=\"opus\"" },
    { name: "Vorbis", mime: "audio/webm; codecs=\"vorbis\"" },
    { name: "AC-3", mime: "audio/mp4; codecs=\"ac-3\"" },
    { name: "E-AC-3", mime: "audio/mp4; codecs=\"ec-3\"" }
  ];

  function decodeCapability() {
    var Ctx = window.AudioContext || window.webkitAudioContext;
    if (!Ctx) return { status: "Unsupported", note: "AudioContext unavailable." };
    try {
      var ctx = new Ctx();
      var result = {
        status: "Working",
        sampleRate: ctx.sampleRate || null,
        decodeAudioData: typeof ctx.decodeAudioData === "function" ? "Working" : "Unsupported",
        channels: ctx.destination ? ctx.destination.maxChannelCount : null,
        bitDepth: "Not detected",
        hiResSource: "Not detected",
        hiResOutput: "Not detected",
        note: "Sample rate/bit depth describe the audio context output; they are not proof of Hi-Res source decoding or Hi-Res Bluetooth output."
      };
      ctx.close();
      return result;
    } catch (err) {
      return { status: "Not testable", error: String((err && err.message) || err) };
    }
  }

  function candidates() {
    var audio = document.createElement("audio");
    return TYPES.map(function (t) {
      var value = "";
      try { value = audio.canPlayType(t.mime) || "No"; } catch (err) { value = "No"; }
      return {
        format: t.name,
        canPlayType: value,
        status: value === "probably" ? "Working (candidate)" : value === "maybe" ? "Partially working (candidate)" : "Unsupported",
        actualPlayback: "Not tested",
        bitDepth: "Not detected"
      };
    });
  }

  root.LumaAudioDecoder = { decodeCapability: decodeCapability, candidates: candidates };
})(typeof globalThis !== "undefined" ? globalThis : this);
