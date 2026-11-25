// Headless smoke test for the Phase 3 Luma media modules.
// Run: node tools/luma-media-smoke.js
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const base = path.join(__dirname, '..', 'webos-chromium-addon', 'source', 'package', 'ext', 'luma');
const store = {};
function load(rel, ctx) {
  vm.runInNewContext(fs.readFileSync(path.join(base, rel), 'utf8'), ctx, { filename: rel });
}
function makeCtx() {
  const ctx = {
    console, Date, Math, Number, Object, Array, Promise, String, JSON, Error,
    setTimeout, clearTimeout,
    chrome: { runtime: { lastError: null }, storage: { local: {
      get(keys, cb) { const out = {}; (Array.isArray(keys) ? keys : [keys]).forEach(k => { if (store[k]) out[k] = store[k]; }); cb(out); },
      set(obj, cb) { Object.assign(store, obj); cb && cb(); }
    } } },
    navigator: { userAgent: 'smoke', platform: 'test', hardwareConcurrency: 4, deviceMemory: 8 },
    AudioContext: function () {
      this.sampleRate = 48000;
      this.currentTime = 0;
      this.state = 'running';
      this.destination = { maxChannelCount: 2 };
      this.close = () => Promise.resolve();
      this.resume = () => Promise.resolve();
    },
    performance: { now: () => 1000 },
    location: { href: 'about:blank' },
    requestAnimationFrame: fn => fn(),
    document: {
      createElement(tag) {
        if (tag === 'video' || tag === 'audio') return {
          canPlayType: m => (m.indexOf('avc1') >= 0 ? 'probably' : m.indexOf('vp9') >= 0 ? 'maybe' : ''),
          addEventListener() {}, removeAttribute() {}, load() {}
        };
        if (tag === 'canvas') return { getContext: () => ({ getExtension: () => null, getParameter: () => 'x', clearColor() {}, clear() {} }) };
        return {};
      },
      querySelectorAll: () => [],
      title: '',
      body: { insertBefore() {} },
      getElementById: () => ({ textContent: '', value: '', checked: false, innerHTML: '', appendChild() {}, addEventListener() {} }),
      addEventListener() {}
    }
  };
  ctx.window = ctx;
  ctx.globalThis = ctx;
  return ctx;
}
(async () => {
  const ctx = makeCtx();
  ['media/capability.js', 'media/codecs.js', 'media/hls.js', 'media/detector.js', 'media/video-engine.js',
    'media/video-analyzer.js', 'media/video-enhancement.js', 'media/upscaling.js', 'media/audio-engine.js',
    'media/audio-decoder.js', 'media/bluetooth-audio.js', 'media/av-sync.js', 'media/profiles.js',
    'media/diagnostics.js', 'media/engine.js'].forEach(f => load(f, ctx));
  const checks = [];
  checks.push(['engine.ensure', ctx.LumaMediaEngine.ensure() === true]);
  checks.push(['quality.4k', ctx.LumaMediaDetector.qualityLabel(3840, 2160) === '4K UHD']);
  checks.push(['quality.unknown', ctx.LumaMediaDetector.qualityLabel(0, 0) === 'Unknown']);
  const defaults = ctx.LumaProfileStorage.defaults();
  checks.push(['default.original', defaults.movieMode === 'Original' && defaults.audioProfile === 'Original Audio']);
  const saved = await ctx.LumaProfileStorage.selectMovie('Horror');
  checks.push(['horror.saved', saved.movieMode === 'Horror' && saved.picture.darkDetail === 4]);
  const audio = await ctx.LumaProfileStorage.selectAudio('Night Mode');
  checks.push(['night.saved', audio.audioProfile === 'Night Mode' && audio.audio.normalization === true]);
  const reset = await ctx.LumaProfileStorage.reset();
  checks.push(['reset.ok', reset.movieMode === 'Original' && reset.audioProfile === 'Original Audio']);
  store['luma.media.profiles'] = { movieMode: 'Bad', picture: 'broken', audio: { bass: 'x' } };
  const sanitized = await ctx.LumaProfileStorage.get();
  checks.push(['corrupt.safe', sanitized.movieMode === 'Original' && sanitized.picture.brightness === 0]);
  const matrix = ctx.LumaCodecManager.matrix();
  checks.push(['codec.matrix', matrix.video.length === 6 && matrix.audio.length === 8]);
  checks.push(['codec.candidate', matrix.video[0].browserCandidate === 'Working (candidate)']);
  const diag = ctx.LumaMediaDiagnostics.run();
  checks.push(['diagnostics.keys', !!diag.html5Video && !!diag.hls && !!diag.bluetooth]);
  const caps = ctx.LumaMediaCapability.probe();
  checks.push(['capability.keys', typeof caps.html5Video === 'string' && typeof caps.webgpu === 'string']);
  const hls = ctx.LumaHlsManager.detect();
  checks.push(['hls.no_bundle', hls.bundledHlsJs === false]);
  const bt = ctx.LumaBluetoothAudio.detect();
  checks.push(['bt.honest', bt.codec === 'Not detected' && bt.systemRoutingApi === 'Unsupported']);
  const av = ctx.LumaAvSync.probe();
  checks.push(['av.no_fake_delay', av.audioDelayControl === 'Unsupported']);
  const snap = ctx.LumaMediaEngine.snapshot();
  checks.push(['engine.snapshot', snap.ok === true]);
  let failed = 0;
  checks.forEach(([name, ok]) => { console.log((ok ? 'PASS ' : 'FAIL ') + name); if (!ok) failed++; });
  console.log('TOTAL=' + checks.length + ' FAILED=' + failed);
  process.exit(failed ? 1 : 0);
})().catch(err => { console.error(err); process.exit(1); });
