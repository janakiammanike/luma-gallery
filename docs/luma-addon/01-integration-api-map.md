# 01 — Integration API map

## Evidence levels used in this package

| Mark | Meaning |
|---|---|
| **PROVEN** | Actually used in the downloaded WebOS-Chromium source (TV Tools or launcher). |
| **STANDARD-UNVERIFIED** | Documented Chrome/Chromium MV3 interface, but not exercised by this repository. Must be probed on the TV before a feature depends on it. |
| **CORE/HOST CHANGE** | No supported extension or separate-webOS-app path found in the source. Requires modifying Chromium core, the launcher's native role, or a system-level host registration. |

## Integration points found in the downloaded source

### A. Built-in unpacked MV3 extension path — PROVEN

`source/package/mkipk.py:144` ships every file under `package/ext/` into the IPK.  
`source/package/run.sh:229-247` refreshes a writable copy of only `ext/tvtools` and writes `hw.json` beside it.  
`source/package/run.sh:377-378` launches Chromium with `--load-extension="$EXT"`.  
`source/package/run.sh:390` explicitly lists `DisableLoadExtensionCommandLineSwitch` inside `--disable-features=...`, i.e. the switch that would block `--load-extension` is itself disabled. Extension loading from the command line is therefore deliberate in this build.

TV Tools manifest (`source/package/ext/tvtools/manifest.json:1-67`) proves this build accepts:

- `manifest_version: 3`
- `action` with popup and icon
- `options_page`
- `background.service_worker`
- `permissions`: `storage`, `tabs`, `alarms`, `declarativeNetRequest`
- `host_permissions`: `<all_urls>`
- `content_scripts` in both `MAIN` and isolated worlds, `all_frames`, `match_about_blank`, `document_start`
- a fixed `key` for a stable extension ID

**Luma use:** add a second extension under `source/package/ext/luma/`. `mkipk.py:144` ships its files automatically. `run.sh` originally copied and loaded only `ext/tvtools`. Under approved A1, a separate opt-in branch now refreshes only `ext/luma` and execs with `--load-extension="$EXT,$LUMA"`; the original exec remains unchanged. This is **not** a browser-core change.

### B. Extension APIs actually exercised by TV Tools — PROVEN

| API / interface | Evidence | What it can do for Luma |
|---|---|---|
| `chrome.storage.local.get/set/onChanged` | `settings.js:65-76`, `bg.js:9-15, 94-99`, `bridge.js:22-24`, `options.js:51-56` | Persist Luma shortcuts, settings and local diagnostics. Same storage area as TV Tools, so use a distinct key prefix such as `luma.*`. |
| `chrome.runtime.getURL()` + `fetch()` | `bg.js:9-15` reads `hw.json` beside the extension; `run.sh:334` writes it to `$EXT/hw.json` | Read launcher-provided hardware-video state (`hardware`, `h264`, `lowmem`, `freeMB`) without inventing a native API. A second extension needs its own copy written by the approved `run.sh` change; it cannot read TV Tools' file by default. |
| `chrome.runtime.onMessage` / `sendMessage` | `bg.js:79-91`, `bridge.js:28-36` | Bridge commands between Luma popup/options/worker and content scripts. Validate `sender` before acting, as TV Tools does. |
| `chrome.tabs.query` | `bg.js:67-74, 111-115`, `popup.js:45` | Read active tab URL/host, tab count, `active`, `pinned`, `audible`, `discarded`, `lastAccessed`. |
| `chrome.tabs.remove`, `reload`, `discard`, `tabs.onCreated` | `bg.js:73, 89, 100-102, 115` | Close, reload and unload tabs; react to new tabs. |
| `chrome.alarms.create/onAlarm` | `bg.js:104-119` | Periodic local cleanup/checks. Do not use for cloud telemetry. |
| `chrome.declarativeNetRequest.updateDynamicRules` | `bg.js:20-59` | Header rules. TV Tools owns rule IDs `1` and `2`; Luma must use a disjoint range and never remove TV Tools rules. |
| DOM media `video/audio.pause()` | `bridge.js:38-45` | Page-level play/pause/seek/volume controls from a content script. |
| DOM `MediaSource.isTypeSupported`, `canPlayType`, `mediaCapabilities.decodingInfo` | `page.js:88-115` | Media compatibility answers. Note: TV Tools wraps these in the MAIN world above configured limits. |
| MV3 content script message events | `bridge.js:8-15, 26`, `page.js:23-39` | Isolated-to-main-world config delivery via `CustomEvent` on `document`. |
| Remote-friendly extension pages | `ui.js:60-112`, `popup.js:59-62`, `options.js:39-44`, patches `0061` and `0063` in `source/patches/INDEX.md:57, 59` | Large focusable buttons, visible focus, LG keyboard in text fields and extension popups. |

### C. Standard Chromium MV3 interfaces — STANDARD-UNVERIFIED

Official Chromium documentation confirms these APIs exist in modern Chromium; the downloaded repo does not prove every method was exercised on this TV build.

| Interface | Evidence | Limitation / verification need |
|---|---|---|
| `chrome_url_overrides.newtab` | Chrome docs: [Override Chrome pages](https://developer.chrome.com/docs/extensions/develop/ui/override-chrome-pages) allows `newtab`, `bookmarks` and `history`; one override per extension. No local patch disables it. | It **replaces the existing New Tab page**, so it needs your explicit approval even though it is a supported extension point. Probe on TV after packaging. |
| `chrome.tabs.create/update/get/goBack/goForward/sendMessage` | Chrome docs: [tabs API](https://developer.chrome.com/docs/extensions/reference/api/tabs). `goBack`/`goForward` documented since Chrome 72. | `goBack/goForward` are tab history actions, not the browser's native Back key/exit behaviour from patch `0057`. Verify on TV. |
| `chrome.scripting.executeScript/insertCSS` | Referenced by Chrome tabs docs as host-permission-gated APIs; not used by TV Tools. | Prefer pre-declared content scripts in the manifest first. Avoid dynamic injection unless a TV probe succeeds. |
| `chrome.system.memory.getInfo` | Chrome docs: [system.memory](https://developer.chrome.com/docs/extensions/reference/api/system/memory), requires `system.memory` permission. | Not used in this repo. Could supplement, not replace, launcher-provided `freeMB`. Probe required. |
| WebSocket / fetch from extension worker/page to a LAN address | Standard web platform inside an MV3 worker/page; new extension would need matching `host_permissions`. | TV Tools does not do this. Must validate with the existing Android Host mode and CORS/CSP behaviour. Never store credentials in URLs. |

### D. Launch parameter path — PROVEN, limited

`source/package/run.sh:263-272` accepts one JSON argument with `target` and optional `fullscreen`; only `http://` and `https://` targets are accepted. It ignores `chrome-extension://`, `file://` and other schemes.

**Luma use:** a separate webOS app can ask webOS to launch Chromium with an `https://` target. This opens a URL in the existing browser; it does **not** control existing tabs, send messages to an extension or move the native pointer.

### E. Native messaging — NOT AVAILABLE as a simple extension addition

Official docs require a registered native messaging host manifest: [Native messaging](https://developer.chrome.com/docs/extensions/develop/concepts/native-messaging). On Linux the manifest lives under fixed system or user-profile `NativeMessagingHosts/` paths, with an absolute host binary path and `allowed_origins`.

In this source:

- `source/package/mkipk.py:195-199` writes `packageinfo.json` with `"services": []`.
- No native messaging host, `connectNative`, `sendNativeMessage` or host manifest exists in the extension.
- The app runs as its own webOS user inside Developer Mode jail (`source/package/run.sh:2-4`, `source/package/README` install notes).

**Conclusion:** native messaging is **CORE/HOST CHANGE**, not a supported "add a file" integration.

### F. Separate webOS app ↔ Chromium — limited to launch and LAN

No LS2 service is registered by the Chromium package, and no shared profile/storage exists between `com.luma.companion2` and `io.github.sarahahzub.chromium`.

Available paths:

| Path | Status | What it can do |
|---|---|---|
| webOS launch with JSON `target` | PROVEN (`run.sh:263-272`) | Open `http(s)` URL in Chromium. |
| LAN WebSocket from Luma extension to Android Host mode | STANDARD-UNVERIFIED | Page/extension-level commands only; must be probed. |
| LS2 call from Luma app into Chromium | CORE/HOST CHANGE | No service exists; would require a native host or core change. |
| Shared localStorage/profile | NOT AVAILABLE | Separate app sandboxes. |

### G. Hardware video boundary — PROVEN but closed to extensions

`source/package/launcher/cnrun.c:1-21, 48-50, 79-128` and `source/args.gn:25-32` show hardware decoding lives in the launcher's Unix socket and LG NDL/Starfish libraries. `source/package/run.sh:229-247, 332-334` passes `hw.json` to TV Tools.

Extensions can **read the resulting flags** and control ordinary DOM media. They cannot open the media socket, change the decoder, enable DRM, or choose NDL/Starfish directly. `source/args.gn:56` has `enable_widevine = false`.

### H. Magic Remote / keyboard boundary — PROVEN in the browser, reusable by extension pages

`source/patches/INDEX.md:23` (0017 remote), `:39` (0043 Enter), `:43` (0045 pointer input region), `:57` (0061 remote in bubbles/Chrome pages), `:59` (0063 keyboard in extension popups), and `run.sh:394` (`--enable-spatial-navigation`) preserve existing support.

A Luma extension gets this for free in its popup/options/home pages and content scripts. It cannot move the native pointer or inject text into the browser omnibox from a content script.

## API map verdict

The safe foundation is a **second MV3 extension** packaged with the browser and following the TV Tools manifest pattern. It can cover branding inside extension pages, local settings, quick access, page-level video controls, content-script media capability probes, tab actions and a LAN command bridge. It cannot replace browser chrome, native pointer/keyboard, NDL control, DRM, or app-level identity without changes that need approval.
