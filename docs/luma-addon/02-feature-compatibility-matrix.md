# 02 — Feature compatibility matrix

Eleven requested Luma features, checked against the real interfaces in [01-integration-api-map.md](01-integration-api-map.md).

**Legend**

- ✅ **ADD-ON SAFE** — can be delivered as new MV3 extension files/pages using proven or standard-unverified APIs, without changing existing Chromium files.
- ⚠️ **APPROVAL NEEDED** — technically possible, but it changes existing browser UI/startup identity or behavior. Implement only after review.
- ⛔ **CORE/HOST CHANGE** — no supported add-on path in the shipped source. Report and wait for explicit approval.

| # | Luma feature | Target behavior | Best supported surface | Verdict | Why / limit |
|---|---|---|---|---|---|
| 1 | Luma branding | Make browser feel like Luma without renaming/replacing Chrome UI. | `action` toolbar icon + popup + options + injected home page content; optional new-tab override. | ✅ / ⚠️ | Toolbar extension icon and extension pages are safe. Full browser title/icon/app-name changes touch `config.env`, `appinfo.json.in`, icons and `mkipk.py` (existing files) → approval needed. Replacing default NTP is a supported override but replaces existing page → approval needed. |
| 2 | Home / New Tab page | A Luma home with quick links and search. | `chrome_url_overrides.newtab` (standard, one override) **or** extension popup/options page reached by toolbar icon. | ⚠️ / ✅ | Override is a supported extension point but changes default browser behavior; do not silently replace. Safe alternative: Luma Home as extension page opened by toolbar click or as a separate launched `https://` page. |
| 3 | Quick Access | Frequent sites, recent tabs, one-click launch. | `chrome.storage.local`, `tabs.query/onCreated`, popup grid, `tabs.create/update`. | ✅ | All needed APIs are proven in TV Tools (`tabs`, `storage`) or standard tab APIs. Do not read browser history API unless probed; prefer tracking tabs the extension observes. |
| 4 | Browser Settings | Luma-specific toggles separate from Chrome settings. | Extension `options_page`, `storage.local`, runtime messages to worker/content scripts. | ✅ | TV Tools proves remote-friendly options UI and storage. It must not modify Chrome's own settings UI or `Preferences` file directly. |
| 5 | Magic Remote integration | TV remote works in Luma pages and actions. | Existing Chromium patches + spatial navigation; focus-visible DOM; popup/toolbar extension action. | ✅ (reuse only) | Remote support is already in the browser (patch 0017, 0061, 0063; `--enable-spatial-navigation`). A Luma page cannot remap native Back/Home keys or override global browser focus; trying that is ⛔ core change. |
| 6 | Android Air Mouse / touchpad | Phone acts as pointer/keyboard for TV. | Existing `com.luma.companion2` Android Host/WebSocket path; Luma extension can show status/help. | ✅ / ⚠️ | This capability already lives in the separate phone app and LAN host, not in Chromium. Adding a LAN command bridge in the extension is standard web tech but needs TV verification. Moving the native cursor from inside Chromium would be ⛔. |
| 7 | Phone keyboard | Phone types into TV fields. | Existing Android Host JSON contract; focus in extension pages/content fields. | ✅ / ⚠️ | Works through the separate phone app and injected text into focused DOM fields, if the existing contract supports it. Cannot inject into omnibox/browser chrome from a content script; that is ⛔ core/browser-UI change. |
| 8 | Navigation commands | Back/forward/home from phone/remote. | Tab-level `goBack`/`goForward`/`create/update` (standard-unverified) + existing browser Back patch. | ✅ / ⚠️ | Tab history actions are standard MV3. Native Back key behaviour and exit are controlled by patch 0057; do not replace. If you want a global Luma command that works on every Chrome page including internal pages, probe on TV; if blocked, report as ⛔. |
| 9 | Video playback controls | Play/pause/seek/volume from popup or phone. | Content script DOM media controls (`video/audio.pause()`, currentTime, volume) + runtime messages. | ✅ | Proven in TV Tools `bridge.js:38-45`. Works for normal HTML media. It cannot control NDL hardware-video plane, DRM/HDCP, or YouTube/native player internals beyond DOM; those are ⛔. |
| 10 | Diagnostics / performance | Show memory, tab pressure, hardware decode status, health checks. | `hw.json` via `runtime.getURL` (PROVEN), `tabs.query`, `alarms`, local `storage`; optional `system.memory` after probe. | ✅ / ⚠️ | `hw.json` gives `hardware`, `h264`, `lowmem`, `freeMB`. Tab count/open media/discards are observable. Chrome internal performance pages/process metrics are not exposed to normal MV3; anything needing them is ⛔ core change. No telemetry sent anywhere. |
| 11 | Media compatibility | Check whether a file/site codec will play on this TV. | MAIN-world `MediaSource.isTypeSupported`, `canPlayType`, `mediaCapabilities.decodingInfo`, `hw.json` flags. | ✅ | Proven in TV Tools `page.js:88-115`. This is a best-effort browser-level probe, not a guarantee of NDL hardware decode; Widevine/DRM is disabled in `args.gn:56`. |

## Feature groups and implementation shape

### Group A — fully add-on safe now

Features 3, 4, 7, 9, 10, 11 and the toolbar/extension-page part of 1, 5, 6, 8.

Deliver as:

- `source/package/ext/luma/manifest.json` (new)
- `source/package/ext/luma/worker.js` (new)
- `source/package/ext/luma/popup.html`, `popup.js`, `popup.css` (new)
- `source/package/ext/luma/options.html`, `options.js`, `options.css` (new)
- `source/package/ext/luma/content/bridge.js`, `content/media.js`, `content/home.js` (new)
- `source/package/ext/luma/shared/*.js/css` (new)

### Group B — approval before implementation

- True New Tab override (`chrome_url_overrides.newtab`).
- Changing app title/icon/identity or browser toolbar beyond a normal extension action.
- Replacing global navigation key handling or browser UI.
- Writing to Chrome `Preferences`, internal WebUI, or NDL socket.

### Group C — not possible without core/host change

- Native messaging host registration.
- LS2 service inside Chromium.
- Native pointer/keyboard injection into browser chrome.
- DRM/Widevine/HDR/NDL decoder control.
- Reading Chrome internal performance/process data not exposed by extension APIs.

## Explicit non-feature

Telemetry is removed from scope. It must not appear in the Luma extension manifest permissions, messages, storage keys, UI, docs written for this layer, or the Android handshake. Existing telemetry references in the older companion project are a separate cleanup item and are not part of this add-on architecture.
