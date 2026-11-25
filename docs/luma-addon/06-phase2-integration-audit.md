# Phase 2 — Integration Capability Audit (read-only)

**Date:** 2026-11-25
**Status:** Audit only. No implementation, no engine/UI change, no rebuild, no IPK install, no cloud build, no commit and no push.
**Goal:** Prepare the integration foundation for the Luma Android Remote while keeping the original Chromium desktop experience unchanged.

---

## 1. Phase 1 verification report

Re-checked from disk/git; previous claims were not assumed.

| Check | Result |
|---|---|
| Workspace git HEAD | `135fd91 Document A1 Luma add-on implementation and TV verification` |
| Workspace working tree | Clean at the time of this audit |
| Commit contents | 7 files under `docs/luma-addon/`, 600 insertions |
| Backup | `C:\Users\janak\Downloads\WebOS-Chromium-main\WebOS-Chromium-main.backup-A1-20261125-105633` exists, 246 files |
| Current source tree | `C:\Users\janak\Downloads\WebOS-Chromium-main\WebOS-Chromium-main`, 268 files |
| Changed existing files | **1**: `source/package/run.sh` |
| New Luma files | **22** files under `source/package/ext/luma/**` |
| Original Chromium exec block | SHA256 `17DEEF769D95FDCB886773AFFAC87779ACCEBFAAC65B0FAACD8271AE4D4C46A4` in both backup and current `run.sh` — unchanged |
| Opt-in switch | `CN_LUMA=1` or launch JSON `{"luma":true}`; default path still loads only `ext/tvtools` |
| TV evidence (from Phase 1) | Opt-in path loaded `.../ext/tvtools,.../ext/luma`; `hw.json` written; no Luma extension-load errors in `browser.log` |
| Luma extension ID | Derived from manifest key: `pohebphbjffnadkbboejjbbmohaimiai` (old doc value corrected) |
| Source tree git status | The Downloads Chromium tree is **not** a git repository; its changes are on disk, in the backup and in the custom IPK only |

**Conclusion:** Phase 1 A1 is present and additive. A2–A4, A5 and B1–B9 are still not approved or implemented.

---

## 2. Supported integration API list

Evidence comes from the actual `source/package/ext/**`, `run.sh`, patches and the existing Android/webOS projects. Where the source tree cannot prove support, the status is marked *requires runtime probe* — it is not claimed as verified.

| Interface | Evidence in source | Status / limitation |
|---|---|---|
| `chrome.runtime.onMessage`, `chrome.runtime.sendMessage` | `ext/luma/shared/bus.js`, `ext/luma/worker.js` | Supported and used. Namespaced `source:"luma"`; worker rejects messages from any other extension ID. Same-extension only. |
| `chrome.tabs.query/create/update/remove/reload` | `ext/luma/worker.js` (`get-state`, `open-url`, `navigate`) | Supported and used. Basic navigation is already proven. |
| `chrome.tabs.goBack`, `chrome.tabs.goForward` | `ext/luma/worker.js` guards with `typeof` and an explicit error message | Intended API; the guard exists because build support was not assumed. Needs runtime confirmation before depending on it. |
| `chrome.tabs.sendMessage` + content scripts | `ext/luma/shared/bus.js`, `content/bridge.js`, manifest `all_frames: true` | Supported and used. Isolated world; cannot patch page JS. Cloudflare challenge frame excluded. |
| `chrome.storage.local` | `ext/luma/worker.js`, `ext/luma/shared/storage.js`, TV Tools | Supported and used. Local settings only. |
| `fetch(chrome.runtime.getURL("hw.json"))` | `ext/luma/worker.js`, TV Tools `bg.js` | Supported and used for launcher-provided hardware state. |
| `chrome.alarms`, `chrome.declarativeNetRequest` | `ext/tvtools/bg.js`, TV Tools manifest | Supported in TV Tools, not currently enabled in Luma. |
| Standard `WebSocket` client | Web API; used by `luma-companion-2/js/remote-session.js` on the TV app side | A client is possible from an extension service worker. **Correction made during implementation after checking Chrome's official MV3 WebSocket sample:** no `host_permissions` are needed for the worker's own WebSocket; the sample manifest has none. No permission was added. An extension still cannot open a listening server. |
| `chrome.scripting` (MV3 `executeScript`) | Standard Chromium MV3 API; not present in current Luma source | **Not enabled / not verified in this build.** Would require `scripting` permission plus host permissions and a runtime probe. |
| `chrome.debugger` + CDP `Input.dispatchMouseEvent`, `Input.dispatchKeyEvent`, `Input.insertText` | Standard Chromium extension API; not present in current Luma source | **Not enabled / not verified in this build.** Would require `debugger` permission, tab attach, and a runtime probe. It affects page content only, never browser chrome/OS cursor. |
| Native messaging (`connectNative` / `sendNativeMessage`) | No native host manifest or `nativeMessaging` permission anywhere in the package | **Unsupported** under the strict rules. Adding it needs a native host component and package changes; do not use. |
| webOS app-to-app / Luna from extension | `cnrun` owns the TV media service; `PalmSystem` / `webOS.service.request` exist only in packaged web apps such as `luma-companion-2`, not in Chrome extension pages | **Unsupported directly.** No Luna bridge is exposed to the extension. Any Luna call would need a native host — out of scope. |
| Physical Magic Remote / keyboard input | Patches `0015`, `0016`, `0017`, `0043`, `0057`, `0061`, `0063` handle Wayland keys, focus, Back, on-screen keyboard and extension popups at the browser/engine layer | Extension code cannot synthesize *trusted* events into browser UI. Only page-level DOM/CDP input is possible from the Luma layer. |
| TV Tools interface | `ext/tvtools/bridge.js` (MAIN world), `tvtools-config` DOM event, `localStorage.__tvtools_cfg` | No command API to Luma. Cross-extension messages are rejected by the Luma worker; a bridge would need an additive `externally_connectable` manifest change and approval. |
| Existing Android Host mode | `tvremote2/.../host/CompanionServer.kt`, `Envelope.kt`, `luma-companion-2/js/remote-session.js` | WebSocket server on the phone at `ws://<ip>:8787/luma/companion/v1`, v1 envelope, manual `Approve TV`, rate limits, 64 KB frames. It currently accepts only producer `com.luma.companion2` and only `diagnostics.refresh`, `browser.open`, `browser.close`. |

**Key boundary:** the safest proven transport is the existing phone-hosted WebSocket endpoint with the TV side as an outbound client. The Chromium extension would be a **new producer** (`luma.chromium`) on that endpoint, which requires a separate, approved Android-side change. No TV listener, no cloud relay, no native messaging.

---

## 3. Remote feature compatibility matrix

“Available now” means already present in Phase 1 Luma code. “Probe required” means a standard Chromium API exists but support in this specific build has not been verified. “Not reliably available” means the requested behaviour cannot be delivered from an isolated extension layer without changing browser UI/engine behaviour.

| # | Feature | Actual integration interface | Limitation / status |
|---|---|---|---|
| 1 | Air Mouse pointer movement | CDP `Input.dispatchMouseEvent(mouseMoved)` through `chrome.debugger`, or synthetic DOM pointer events from the content script | No OS/Wayland cursor injection from an extension. DOM events are untrusted. CDP needs `debugger` permission + runtime probe. Not implemented. |
| 2 | Touchpad movement | Same relative-delta mapping as Air Mouse; CDP mouse move or DOM pointer events | No multi-touch gesture API in this layer; no OS touch injection. Not implemented. |
| 3 | Single click | CDP `mousePressed` + `mouseReleased`, or content-script `.click()` | Synthetic DOM click is untrusted and will not drive browser chrome. CDP required for reliable default actions. Not implemented. |
| 4 | Double click | Two CDP click sequences with timing | DOM-level `dblclick` is untrusted; text selection/native behaviour needs CDP. Not implemented. |
| 5 | Right click | CDP right-button press/release | Native context menu is browser UI; extension cannot drive it. DOM `contextmenu` is untrusted. Not implemented. |
| 6 | Scroll | CDP `mouseWheel` with deltas, or DOM `scrollBy` fallback | DOM fallback only scrolls script-accessible containers; cannot scroll browser UI or all nested/scrollable elements. Not implemented. |
| 7 | Phone keyboard input | CDP `Input.insertText` / `dispatchKeyEvent`, or content-script `execCommand("insertText")` into the focused element | Cannot open LG's OS keyboard or type into the omnibox/browser UI. Needs focus tracking and frame routing (`all_frames` caveat). Not implemented. |
| 8 | Back / Forward | `chrome.tabs.goBack` / `goForward` in `worker.js` | Available now, behind the existing runtime guard; browser history only. Needs runtime confirmation of those two methods. |
| 9 | Reload | `chrome.tabs.reload` in `worker.js` | Available now. |
| 10 | Tab switching | `chrome.tabs.query` + `chrome.tabs.update(id, {active:true})`; worker already has `activate` | Available for activation. Next/previous ordering needs an explicit index rule, not an assumption about window order. Not implemented. |
| 11 | New tab | `chrome.tabs.create` via worker `open-url`; dedicated new-tab action to be added | Available now for validated `http(s)` URLs; blank new tab needs an additive action. |
| 12 | Play / Pause | `content/bridge.js` `media-control` actions `play`, `pause`, `toggle` via `LumaBus` | Available now for DOM `<video>/<audio>`. Hardware NDL path is owned by `cnrun`; DOM control of that hole is not guaranteed and needs verification. |
| 13 | Seek | `content/bridge.js` `media-control` action `seek` setting `currentTime` | Available now for DOM media. Limited by `duration`/seekable ranges, hardware decoder and network buffering. No DRM promised. |
| 14 | Fullscreen | No extension tab-fullscreen API; app already supports `--start-maximized` and a launcher `$FULL` option; page `requestFullscreen()` needs trusted user activation | **Not reliably available** from an isolated extension. Keep Magic Remote / launcher flag. Do not claim CDP F11 until probed. |

**Summary:** navigation, tabs, reloading and DOM media control are directly supported today. Pointer/click/scroll/keyboard fidelity would depend on `chrome.debugger` and a runtime probe. Fullscreen and OS-level input remain outside the Luma layer.

---

## 4. Proposed module architecture

All new browser-side code stays inside `source/package/ext/luma/**`. The original Chromium exec block, engine, UI, NDL decoder and TV Tools remain untouched.

```text
Android Luma TV Remote (phone, Host mode)
        |  ws://<phone-ip>:8787/luma/companion/v1
        |  contract v1, explicit phone approval
        v
luma/connection     WebSocket client, endpoint validation, backoff, envelope framing
luma/authentication hello/capability handshake, producer check, action allow-list
        |
        v
command router (worker.js, namespaced LumaBus only)
        |
        +--> luma/navigation   tabs.goBack/goForward/reload/create/query/update
        +--> luma/media        existing content/bridge.js media-control + probe-media
        +--> luma/input        CDP Input adapter (probe required) / DOM fallback
        +--> luma/diagnostics  local hw.json, tab summary, counters — no telemetry
```

### Module responsibilities

- **luma/connection** — outbound-only WebSocket client, manual IPv4/port, fixed path, bounded backoff, 64 KB frames, no TV listener.
- **luma/authentication** — reuse v1 `hello` / `capability`; new producer `luma.chromium`; no commands before approval; `telemetry_enabled:false`.
- **luma/input** — decode allow-listed pointer/key/wheel commands; prefer CDP only after a successful probe; keep DOM fallback clearly labelled as limited.
- **luma/navigation** — wrap the already-used `chrome.tabs` calls; explicit next/previous tab ordering; URL validation unchanged (`http`/`https` only).
- **luma/media** — reuse `content/bridge.js`; report honest capability instead of assuming hardware decoder behaviour.
- **luma/diagnostics** — local-only state from `hw.json`, tab summary and counters; no logs of URLs or page content.

**Required additive approvals before any code:** a distinct Android Host producer/action set, and — only if a runtime probe succeeds — `debugger` or `scripting` permission. The official Chrome sample shows that the MV3 service worker's own WebSocket needs no new host permission. These are A2/B-scope decisions, not part of this audit.

---

## 5. Security and authentication design

| Control | Design |
|---|---|
| Transport | LAN-only `ws://` client from TV extension to phone Host; no TV listener, no cloud relay, no Internet fallback. |
| Envelope | Contract v1 (`v`, `type`, `id`, `ts`, `payload`); max 64 KB; type and payload allow-lists; reject and close after 5 invalid frames. |
| Pairing | Explicit phone-side **Approve TV** before any command. No credential or pairing key stored in the extension. |
| Producer | New `luma.chromium` producer, distinct from `com.luma.companion2`; Android Host must accept it explicitly. No shared session with the companion app. |
| Capability | Android sends `capability` with `telemetry_enabled:false`; extension rejects telemetry frames. |
| Actions | Per-session allow-list; `app.focus` stays reserved; unknown actions are ACK-rejected without execution. |
| Rate limits | 20 messages and 20 commands per 10 s window, inherited from the existing contract; bounded backoff 1/2/4/8/16 s. |
| URLs | Reuse existing validation: absolute `http`/`https`, no credentials, length limit; no `file:`, `chrome:` or `javascript:`. |
| Least privilege | Same-extension `LumaBus` only; isolated-world content scripts; no page content read back; Cloudflare frame excluded. |
| CDP caution | If `chrome.debugger` is approved, attach only after approval, detach on disconnect/hide, and expose no hidden telemetry or page-data upload. |
| Excluded | No native messaging, no Luna/service call from the extension, no ADB automation, no telemetry. |

---

## 6. Implementation sequence (pending approval)

1. **Approve this audit** and decide whether A2 includes `debugger`/`scripting` permissions or stays with already-proven APIs only.
2. **Android Host change (separate approval):** accept producer `luma.chromium`; add the allowed remote actions; preserve the existing `com.luma.companion2` flow untouched.
3. **Manifest check:** no `host_permissions` are required for the worker WebSocket; add `debugger`/`scripting` only if separately approved and probed.
4. **connection + authentication modules:** v1 client, handshake, session teardown on hide/disconnect; no command execution before `capability`.
5. **navigation + media modules first:** use already-used `chrome.tabs` and existing content bridge; lowest risk, no engine change.
6. **input module behind feature flags:** probe CDP on the TV; enable pointer/click/scroll/keyboard only for verified capabilities; keep DOM fallback limited and documented.
7. **diagnostics module:** local counters only; no telemetry.
8. **Static checks → user-approved repack/install/test:** verify default launch still loads only `ext/tvtools`, opt-in launch loads the new modules, no errors in `browser.log`, then report.

**STOP.** No implementation started; no IPK installed; no cloud build; no commit or push.
