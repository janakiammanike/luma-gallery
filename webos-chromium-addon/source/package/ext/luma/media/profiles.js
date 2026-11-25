"use strict";
// Movie/Audio profile definitions and safe local persistence.
(function (root) {
  var KEY = "luma.media.profiles";

  var MOVIE_MODES = {
    Horror:   { brightness: -3, contrast: 2, saturation: -3, sharpness: -2, noiseReduction: 2, darkDetail: 4, aspectRatio: "original", audioProfile: "Horror Immersive" },
    Cinema:   { brightness: 0, contrast: 2, saturation: 0, sharpness: 1, noiseReduction: 1, darkDetail: 1, aspectRatio: "original", audioProfile: "Cinema Balanced" },
    Action:   { brightness: 1, contrast: 3, saturation: 2, sharpness: 2, noiseReduction: 0, darkDetail: 0, aspectRatio: "original", audioProfile: "Action Dynamic" },
    Romance:  { brightness: 2, contrast: -1, saturation: 1, sharpness: 0, noiseReduction: 1, darkDetail: 1, aspectRatio: "original", audioProfile: "Dialogue Clear" },
    Animation:{ brightness: 0, contrast: 1, saturation: 3, sharpness: 1, noiseReduction: 0, darkDetail: 0, aspectRatio: "original", audioProfile: "Cinema Balanced" },
    Sports:   { brightness: 1, contrast: 2, saturation: 1, sharpness: 2, noiseReduction: 0, darkDetail: 0, aspectRatio: "original", audioProfile: "Dialogue Clear" },
    Original: { brightness: 0, contrast: 0, saturation: 0, sharpness: 0, noiseReduction: 0, darkDetail: 0, aspectRatio: "original", audioProfile: "Original Audio" }
  };

  var AUDIO_PROFILES = {
    "Horror Immersive": { bass: 2, treble: 0, equalizer: 0, dialogue: 2, normalization: false, balance: 0, virtualSurround: false, note: "Stereo preserved. Surround off by default." },
    "Cinema Balanced":  { bass: 1, treble: 1, equalizer: 0, dialogue: 1, normalization: false, balance: 0, virtualSurround: false },
    "Dialogue Clear":   { bass: 0, treble: 1, equalizer: 0, dialogue: 4, normalization: false, balance: 0, virtualSurround: false },
    "Action Dynamic":   { bass: 3, treble: 1, equalizer: 0, dialogue: 2, normalization: false, balance: 0, virtualSurround: false },
    "Night Mode":       { bass: -2, treble: 1, equalizer: 0, dialogue: 4, normalization: true, balance: 0, virtualSurround: false },
    "Hi-Res Stereo":    { bass: 0, treble: 0, equalizer: 0, dialogue: 0, normalization: false, balance: 0, virtualSurround: false, note: "No resampling or processing is introduced by this profile." },
    "Virtual Surround": { bass: 0, treble: 0, equalizer: 0, dialogue: 1, normalization: false, balance: 0, virtualSurround: true, note: "Experimental virtual surround; not a native spatial format." },
    "Original Audio":   { bass: 0, treble: 0, equalizer: 0, dialogue: 0, normalization: false, balance: 0, virtualSurround: false }
  };

  function defaults() {
    return {
      version: 1,
      movieMode: "Original",
      audioProfile: "Original Audio",
      customPicture: false,
      customAudio: false,
      picture: Object.assign({}, MOVIE_MODES.Original),
      audio: Object.assign({}, AUDIO_PROFILES["Original Audio"]),
      enhancementEnabled: false
    };
  }

  function storageGet() {
    return new Promise(function (resolve) {
      try {
        chrome.storage.local.get(KEY, function (items) {
          if (chrome.runtime && chrome.runtime.lastError) { resolve(defaults()); return; }
          resolve(items && items[KEY] ? items[KEY] : defaults());
        });
      } catch (err) { resolve(defaults()); }
    });
  }

  function storageSet(value) {
    return new Promise(function (resolve) {
      var payload = {};
      payload[KEY] = value;
      try {
        chrome.storage.local.set(payload, function () { resolve({ ok: true }); });
      } catch (err) { resolve({ ok: false, error: String((err && err.message) || err) }); }
    });
  }

  function sanitize(raw) {
    var base = defaults();
    if (!raw || typeof raw !== "object" || Array.isArray(raw)) return base;
    base.movieMode = MOVIE_MODES[raw.movieMode] ? raw.movieMode : "Original";
    base.audioProfile = AUDIO_PROFILES[raw.audioProfile] ? raw.audioProfile : "Original Audio";
    base.customPicture = !!raw.customPicture;
    base.customAudio = !!raw.customAudio;
    base.enhancementEnabled = !!raw.enhancementEnabled;
    if (raw.picture && typeof raw.picture === "object") {
      Object.keys(base.picture).forEach(function (key) {
        if (typeof raw.picture[key] === typeof base.picture[key]) base.picture[key] = raw.picture[key];
      });
    }
    if (raw.audio && typeof raw.audio === "object") {
      Object.keys(base.audio).forEach(function (key) {
        if (typeof raw.audio[key] === typeof base.audio[key]) base.audio[key] = raw.audio[key];
      });
    }
    return base;
  }

  function get() { return storageGet().then(sanitize); }

  function set(patch) {
    return get().then(function (current) {
      var next = sanitize(Object.assign({}, current, patch || {}));
      return storageSet(next).then(function () { return next; });
    });
  }

  function selectMovie(name) {
    if (!MOVIE_MODES[name]) return Promise.resolve({ ok: false, error: "Unknown movie mode." });
    return get().then(function (current) {
      var picture = Object.assign({}, MOVIE_MODES[name]);
      var audioName = current.customAudio ? current.audioProfile : picture.audioProfile;
      var next = sanitize(Object.assign({}, current, {
        movieMode: name,
        customPicture: false,
        picture: picture,
        audioProfile: audioName,
        audio: AUDIO_PROFILES[audioName] ? Object.assign({}, AUDIO_PROFILES[audioName]) : current.audio
      }));
      return storageSet(next).then(function () { return next; });
    });
  }

  function selectAudio(name) {
    if (!AUDIO_PROFILES[name]) return Promise.resolve({ ok: false, error: "Unknown audio profile." });
    return get().then(function (current) {
      var next = sanitize(Object.assign({}, current, {
        audioProfile: name,
        customAudio: false,
        audio: Object.assign({}, AUDIO_PROFILES[name])
      }));
      return storageSet(next).then(function () { return next; });
    });
  }

  function reset() { return storageSet(defaults()).then(function () { return defaults(); }); }

  root.LumaProfileStorage = {
    KEY: KEY,
    defaults: defaults,
    get: get,
    set: set,
    reset: reset,
    selectMovie: selectMovie,
    selectAudio: selectAudio,
    movieModes: Object.keys(MOVIE_MODES),
    audioProfiles: Object.keys(AUDIO_PROFILES),
    MOVIE_MODES: MOVIE_MODES,
    AUDIO_PROFILES: AUDIO_PROFILES
  };
})(typeof globalThis !== "undefined" ? globalThis : this);
