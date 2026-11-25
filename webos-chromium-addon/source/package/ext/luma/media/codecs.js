"use strict";
// Codec candidate detection. canPlayType is a browser candidate, not playback proof.
(function (root) {
  var VIDEO = [
    { name: "H.264 / AVC", mime: 'video/mp4; codecs="avc1.42E01E"', family: "H.264" },
    { name: "H.265 / HEVC", mime: 'video/mp4; codecs="hvc1.1.6.L93.B0"', family: "HEVC" },
    { name: "VP8", mime: 'video/webm; codecs="vp8"', family: "VP8" },
    { name: "VP9", mime: 'video/webm; codecs="vp9"', family: "VP9" },
    { name: "AV1", mime: 'video/mp4; codecs="av01.0.05M.08"', family: "AV1" },
    { name: "MPEG-4 Part 2", mime: "video/mp4", family: "MPEG-4" }
  ];

  var AUDIO = [
    { name: "AAC", mime: 'audio/mp4; codecs="mp4a.40.2"', family: "AAC" },
    { name: "MP3", mime: "audio/mpeg", family: "MP3" },
    { name: "FLAC", mime: 'audio/flac; codecs="flac"', family: "FLAC" },
    { name: "WAV / PCM", mime: "audio/wav", family: "WAV" },
    { name: "Opus", mime: 'audio/webm; codecs="opus"', family: "Opus" },
    { name: "Vorbis", mime: 'audio/webm; codecs="vorbis"', family: "Vorbis" },
    { name: "AC-3", mime: 'audio/mp4; codecs="ac-3"', family: "AC3" },
    { name: "E-AC-3", mime: 'audio/mp4; codecs="ec-3"', family: "EAC3" }
  ];

  function candidate(mime) {
    try {
      var el = document.createElement(document.createElement("video").canPlayType ? "video" : "audio");
      return el.canPlayType(mime) || "No";
    } catch (err) {
      return "No";
    }
  }

  function classify(value) {
    if (value === "probably") return "Working (candidate)";
    if (value === "maybe") return "Partially working (candidate)";
    return "Unsupported";
  }

  function matrix() {
    function build(list, kind) {
      return list.map(function (item) {
        var value = candidate(item.mime);
        var mse = false;
        try {
          mse = !!(window.MediaSource && MediaSource.isTypeSupported(item.mime));
        } catch (err) { mse = false; }
        return {
          format: item.name,
          family: item.family,
          kind: kind,
          browserCandidate: classify(value),
          canPlayType: value,
          mseSupported: mse,
          hardwareDecoder: "Not testable",
          softwareDecoder: "Not testable",
          actualPlayback: "Not tested",
          note: "canPlayType/MSE are capability candidates only; actual playback must be tested with a real stream."
        };
      });
    }
    return {
      video: build(VIDEO, "video"),
      audio: build(AUDIO, "audio"),
      note: "No codec library is bundled. No claim of hardware acceleration is made."
    };
  }

  root.LumaCodecManager = { matrix: matrix, candidate: candidate };
})(typeof globalThis !== "undefined" ? globalThis : this);
