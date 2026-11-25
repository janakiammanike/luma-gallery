// Headless integration test for the Phase 3 worker media-inspect route.
// Run: node tools/luma-worker-smoke.js
const fs = require('fs');
const path = require('path');
const vm = require('vm');
const base = path.join(__dirname, '..', 'webos-chromium-addon', 'source', 'package', 'ext', 'luma');
const sent = [];
let listener = null;
const store = {};
function load(rel, ctx) {
  vm.runInNewContext(fs.readFileSync(path.join(base, rel), 'utf8'), ctx, { filename: rel });
}
const ctx = {
  console, Date, Math, Number, Object, Array, Promise, String, JSON, Error, URL,
  setTimeout, clearTimeout, setInterval, clearInterval,
  fetch: () => Promise.resolve({ ok: false }),
  importScripts: () => {},
  navigator: { userAgent: 'worker-smoke' },
  WebSocket: function () { this.close = () => {}; },
  chrome: {
    runtime: {
      id: 'luma-test-id',
      lastError: null,
      getURL: p => 'chrome-extension://luma-test-id/' + p,
      sendMessage: (message) => { sent.push(message); return Promise.resolve({ ok: true, value: { count: 1 } }); },
      onMessage: { addListener: fn => { listener = fn; } },
      onInstalled: { addListener: () => {} },
      onConnect: { addListener: () => {} }
    },
    storage: { local: {
      get(keys, cb) { const out = {}; (Array.isArray(keys) ? keys : [keys]).forEach(k => { if (store[k]) out[k] = store[k]; }); cb(out); },
      set(obj, cb) { Object.assign(store, obj); cb && cb(); }
    } },
    tabs: {
      query: () => Promise.resolve([
        { id: 3, url: 'chrome-extension://luma-test-id/cinema.html', active: true },
        { id: 9, url: 'https://media.w3.org/2010/05/sintel/trailer.mp4', active: true, audible: true }
      ]),
      create: () => Promise.resolve({ id: 11 }),
      update: () => Promise.resolve({}),
      reload: () => Promise.resolve({}),
      remove: () => Promise.resolve({}),
      sendMessage: (id, message) => { sent.push({ tabId: id, message }); return Promise.resolve({ ok: true, value: { count: 2 } }); },
      goBack: () => Promise.resolve({}),
      goForward: () => Promise.resolve({})
    }
  }
};
ctx.window = ctx;
ctx.globalThis = ctx;
['shared/bus.js', 'shared/storage.js', 'shared/contract.js', 'remote/authentication.js',
  'remote/connection.js', 'worker.js'].forEach(f => load(f, ctx));
(async () => {
  if (typeof listener !== 'function') throw new Error('Worker did not register an onMessage listener');
  const response = await new Promise(resolve => {
    const result = listener({ source: 'luma', type: 'media-inspect-active', payload: {} },
      { id: ctx.chrome.runtime.id }, resolve);
    if (result && typeof result.catch === 'function') result.catch(err => resolve({ ok: false, error: String(err) }));
  });
  const routed = sent.some(s => s.tabId === 9 && s.message && s.message.type === 'probe-media-detail');
  const extensionTabUsed = sent.some(s => s.tabId === 3);
  const checks = [
    ['worker.listener', typeof listener === 'function'],
    ['worker.response', response && response.ok !== false],
    ['worker.skips_extension_tab', extensionTabUsed === false],
    ['worker.routes_to_web_tab', routed === true]
  ];
  let failed = 0;
  checks.forEach(([name, ok]) => { console.log((ok ? 'PASS ' : 'FAIL ') + name); if (!ok) failed++; });
  console.log('TOTAL=' + checks.length + ' FAILED=' + failed);
  process.exit(failed ? 1 : 0);
})().catch(err => { console.error(err); process.exit(1); });
