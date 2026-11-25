"use strict";
// Read-only media capability probes. No monkey-patching of site APIs, no
// wrapper conflicts with TV Tools, and no result leaves the TV.
(function (root) {
  var TYPES = [
    'video/mp4; codecs="avc1.42E01E"',
    'video/mp4; codecs="avc1.640028"',
    'video/mp4; codecs="av01.0.05M.08"',
    'video/webm; codecs="vp9"',
    'video/webm; codecs="vp8, vorbis"'
  ];

  function probe() {
    var mediaSource = {};
    var canPlayType = {};
    var el = document.createElement("video");

    TYPES.forEach(function (type) {
      try {
        mediaSource[type] = !!(window.MediaSource && MediaSource.isTypeSupported(type));
      } catch (err) {
        mediaSource[type] = false;
      }
      try {
        canPlayType[type] = el.canPlayType(type) || "";
      } catch (err) {
        canPlayType[type] = "";
      }
    });

    var decodingInfo = null;
    try {
      if (navigator.mediaCapabilities && navigator.mediaCapabilities.decodingInfo) {
        decodingInfo = navigator.mediaCapabilities.decodingInfo({
          type: "file",
          file: {
            contentType: "video/mp4",
            width: 1920,
            height: 1080,
            bitrate: 5000000,
            framerate: 30
          }
        }).then(function (info) {
          return { supported: !!info.supported, smooth: !!info.smooth, powerEfficient: !!info.powerEfficient };
        }).catch(function (err) {
          return { error: String((err && err.message) || err) };
        });
      }
    } catch (err) {
      decodingInfo = Promise.resolve({ error: String((err && err.message) || err) });
    }

    return Promise.resolve(decodingInfo).then(function (info) {
      return {
        mediaSource: mediaSource,
        canPlayType: canPlayType,
        decodingInfo: info,
        probedAt: Date.now()
      };
    });
  }

  root.LumaCapability = { probe: probe };
})(typeof globalThis !== "undefined" ? globalThis : this);
