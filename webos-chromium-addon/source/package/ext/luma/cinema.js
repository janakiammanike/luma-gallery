"use strict";
// UI for the Luma Cinema Control Panel. All processing is opt-in and local.
(function () {
  var state = null;
  var panelVideo = null;

  function say(message) {
    document.getElementById("status").textContent = message || "";
  }

  function show(id, value) {
    document.getElementById(id).textContent = typeof value === "string" ? value : JSON.stringify(value, null, 2);
  }

  function numeric(id) { return Number(document.getElementById(id).value) || 0; }
  function checked(id) { return !!document.getElementById(id).checked; }
  function value(id) { return document.getElementById(id).value; }

  function buildNumberControls(hostId, fields) {
    var host = document.getElementById(hostId);
    host.innerHTML = "";
    fields.forEach(function (field) {
      var label = document.createElement("label");
      label.textContent = field.label + " ";
      var input = document.createElement("input");
      input.type = "number";
      input.id = field.id;
      input.min = String(field.min);
      input.max = String(field.max);
      input.step = "1";
      input.value = String(field.value || 0);
      label.appendChild(input);
      host.appendChild(label);
    });
  }

  function buildCheckbox(hostId, id, label, checkedValue) {
    var host = document.getElementById(hostId);
    var labelEl = document.createElement("label");
    var input = document.createElement("input");
    input.type = "checkbox";
    input.id = id;
    input.checked = !!checkedValue;
    labelEl.appendChild(input);
    labelEl.appendChild(document.createTextNode(" " + label));
    host.appendChild(labelEl);
  }

  function fillSelect(id, values, selected) {
    var el = document.getElementById(id);
    el.innerHTML = "";
    values.forEach(function (name) {
      var option = document.createElement("option");
      option.value = name; option.textContent = name;
      option.selected = name === selected;
      el.appendChild(option);
    });
  }

  function render(data) {
    state = data;
    fillSelect("movieMode", LumaProfileStorage.movieModes, data.movieMode);
    fillSelect("audioProfile", LumaProfileStorage.audioProfiles, data.audioProfile);
    buildNumberControls("pictureControls", [
      { id: "brightness", label: "Brightness", min: -10, max: 10, value: data.picture.brightness },
      { id: "contrast", label: "Contrast", min: -10, max: 10, value: data.picture.contrast },
      { id: "saturation", label: "Saturation", min: -10, max: 10, value: data.picture.saturation },
      { id: "sharpness", label: "Sharpness", min: -10, max: 10, value: data.picture.sharpness },
      { id: "noiseReduction", label: "Noise reduction", min: 0, max: 5, value: data.picture.noiseReduction },
      { id: "darkDetail", label: "Dark detail", min: 0, max: 10, value: data.picture.darkDetail }
    ]);
    buildCheckbox("pictureControls", "enhancementEnabled", "Enable Luma picture processing", data.enhancementEnabled);
    buildNumberControls("audioControls", [
      { id: "bass", label: "Bass", min: -12, max: 12, value: data.audio.bass },
      { id: "treble", label: "Treble", min: -12, max: 12, value: data.audio.treble },
      { id: "equalizer", label: "Equalizer", min: -12, max: 12, value: data.audio.equalizer },
      { id: "dialogue", label: "Dialogue", min: -6, max: 6, value: data.audio.dialogue },
      { id: "balance", label: "Balance", min: -1, max: 1, value: data.audio.balance }
    ]);
    buildCheckbox("audioControls", "normalization", "Volume normalization", data.audio.normalization);
    buildCheckbox("audioControls", "virtualSurround", "Virtual surround (experimental)", data.audio.virtualSurround);
  }

  function load() {
    return LumaProfileStorage.get().then(function (data) { render(data); say("Profiles loaded."); })
      .catch(function (err) { say(err.message); });
  }

  function currentPicturePatch() {
    return {
      brightness: numeric("brightness"), contrast: numeric("contrast"), saturation: numeric("saturation"),
      sharpness: numeric("sharpness"), noiseReduction: numeric("noiseReduction"), darkDetail: numeric("darkDetail"),
      aspectRatio: "original", enhancementEnabled: checked("enhancementEnabled"), customPicture: true
    };
  }

  function currentAudioPatch() {
    return {
      bass: numeric("bass"), treble: numeric("treble"), equalizer: numeric("equalizer"),
      dialogue: numeric("dialogue"), balance: numeric("balance"),
      normalization: checked("normalization"), virtualSurround: checked("virtualSurround"), customAudio: true
    };
  }

  function applyLive() {
    if (!panelVideo) {
      say("No Luma playback element selected. Use Inspect or open a Luma media page first.");
      return { ok: false, error: "No panel video" };
    }
    return LumaMediaEngine.applyProfiles(panelVideo, state, LumaAudioEngine.isProcessing());
  }

  function ensurePanelVideo() {
    if (!panelVideo) panelVideo = document.getElementById("lumaVideo");
    return panelVideo;
  }

  function validUrl(raw) {
    try {
      var url = new URL(String(raw || ""));
      return url.protocol === "http:" || url.protocol === "https:" ? url.href : null;
    } catch (err) { return null; }
  }

  function loadUrl() {
    var url = validUrl(value("lumaUrl"));
    if (!url) { say("Enter an absolute http(s) video URL."); return; }
    LumaAudioEngine.original();
    var video = ensurePanelVideo();
    video.src = url;
    video.load();
    LumaVideoEngine.use(video);
    LumaVideoAnalyzer.reset();
    say("Source set. Press Play / Pause to start.");
  }

  function inspect() {
    LumaBus.send("media-inspect-active", {}).then(function (reply) {
      var value = reply && reply.value ? reply.value : reply;
      show("mediaInfo", value || { status: "Not detected" });
      say(value && value.count ? "Media elements found: " + value.count : "No accessible HTML5 media found.");
    }).catch(function (err) { say(err.message); });
  }

  function wire() {
    document.getElementById("loadUrl").addEventListener("click", loadUrl);
    document.getElementById("playPause").addEventListener("click", function () {
      var video = ensurePanelVideo();
      LumaVideoEngine.use(video);
      LumaVideoEngine.control("toggle").then(function (result) {
        say(result.ok ? "Playback toggled." : (result.error || "Playback failed."));
        LumaVideoAnalyzer.record(video);
      });
    });
    document.getElementById("fullscreen").addEventListener("click", function () {
      LumaVideoEngine.control("fullscreen").then(function (result) {
        say(result.ok ? "Fullscreen requested." : (result.error || "Fullscreen unavailable."));
      });
    });
    document.getElementById("inspect").addEventListener("click", inspect);
    document.getElementById("applyMovie").addEventListener("click", function () {
      LumaProfileStorage.selectMovie(value("movieMode")).then(function (data) {
        render(data); applyLive(); say("Movie mode applied: " + data.movieMode);
      }).catch(function (err) { say(err.message); });
    });
    document.getElementById("applyAudio").addEventListener("click", function () {
      LumaProfileStorage.selectAudio(value("audioProfile")).then(function (data) {
        render(data);
        var name = data.audioProfile;
        if (name === "Original Audio" || name === "Hi-Res Stereo") {
          LumaAudioEngine.original();
          say(name + " applied: native audio path, no Luma processing.");
          return;
        }
        LumaAudioEngine.enable(ensurePanelVideo()).then(function (result) {
          if (!result.ok) { say(result.error || "Web Audio could not be enabled."); return; }
          applyLive();
          say(name + " applied: Luma Web Audio processing is active on the Luma playback surface.");
        });
      }).catch(function (err) { say(err.message); });
    });
    document.getElementById("originalPicture").addEventListener("click", function () {
      LumaVideoEnhancement.restore(panelVideo);
      LumaProfileStorage.selectMovie("Original").then(function (data) { render(data); say("Original picture restored."); });
    });
    document.getElementById("originalAudio").addEventListener("click", function () {
      LumaAudioEngine.original();
      LumaProfileStorage.selectAudio("Original Audio").then(function (data) { render(data); say("Original Audio restored."); });
    });
    document.getElementById("resetAll").addEventListener("click", function () {
      LumaAudioEngine.original();
      LumaProfileStorage.reset().then(function (data) { render(data); say("All Luma media settings reset."); });
    });
    document.getElementById("diagnostics").addEventListener("click", function () {
      show("capabilities", LumaMediaEngine.diagnostics()); say("Diagnostics complete.");
    });
    document.getElementById("benchmark").addEventListener("click", function () {
      say("Running WebGL benchmark…");
      LumaUpscaling.benchmark(ensurePanelVideo(), 30).then(function (result) {
        show("capabilities", result); say("Benchmark complete: " + result.status);
      });
    });
    ["brightness", "contrast", "saturation", "sharpness", "noiseReduction", "darkDetail", "enhancementEnabled",
      "bass", "treble", "equalizer", "dialogue", "balance", "normalization", "virtualSurround"].forEach(function (id) {
      document.addEventListener("change", function (event) {
        if (!event.target || event.target.id !== id) return;
        var patch = id.indexOf("brightness") === 0 || ["contrast", "saturation", "sharpness", "noiseReduction", "darkDetail", "enhancementEnabled"].indexOf(id) >= 0
          ? currentPicturePatch() : currentAudioPatch();
        LumaProfileStorage.set(patch).then(function (data) { state = data; applyLive(); });
      });
    });
    show("capabilities", LumaMediaCapability.probe());
  }

  wire();
  load();
})();
