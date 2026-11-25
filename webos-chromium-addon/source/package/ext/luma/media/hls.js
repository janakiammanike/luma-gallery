"use strict";
// HLS investigation only. HLS.js is not bundled; no large library is downloaded.
(function (root) {
  function detect() {
    var native = "Unsupported";
    var mse = "Unsupported";
    var hlsJs = "Not detected";
    try {
      var video = document.createElement("video");
      var value = video.canPlayType("application/vnd.apple.mpegurl") ||
                  video.canPlayType("application/x-mpegURL");
      if (value) native = "Working";
    } catch (err) { native = "Not testable"; }
    try {
      mse = window.MediaSource && typeof MediaSource.isTypeSupported === "function"
        ? "Working" : "Unsupported";
    } catch (err) { mse = "Not testable"; }
    try {
      hlsJs = typeof window.Hls === "function" ? "Working" : "Not detected";
    } catch (err) { hlsJs = "Not detected"; }
    return {
      nativeHls: native,
      mediaSource: mse,
      hlsJsPresent: hlsJs,
      bundledHlsJs: false,
      verdict: native === "Working" ? "Native HLS candidate available"
        : mse === "Working" ? "MSE available; HLS.js would need a separately approved library"
        : "No verified HLS path",
      note: "Native HLS is not assumed. No HLS stream is fetched by this module."
    };
  }

  function testStream(url) {
    return new Promise(function (resolve) {
      if (!url || !/^https?:\/\//i.test(url)) {
        resolve({ status: "Not testable", reason: "Only absolute http(s) URLs are accepted." });
        return;
      }
      var video = document.createElement("video");
      var timer = null;
      function finish(status, detail) {
        if (timer) clearTimeout(timer);
        video.removeAttribute("src");
        video.load();
        resolve({ status: status, detail: detail || "" });
      }
      timer = setTimeout(function () { finish("Not testable", "timeout"); }, 12000);
      video.preload = "metadata";
      video.muted = true;
      video.addEventListener("loadedmetadata", function () {
        finish("Working", { width: video.videoWidth, height: video.videoHeight, duration: video.duration });
      });
      video.addEventListener("error", function () {
        finish("Unsupported", video.error ? String(video.error.code) : "media error");
      });
      video.src = url;
    });
  }

  root.LumaHlsManager = { detect: detect, testStream: testStream };
})(typeof globalThis !== "undefined" ? globalThis : this);
