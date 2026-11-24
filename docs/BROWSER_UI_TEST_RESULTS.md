# Luma Browser UI Test Results

**Date:** 2026-11-25  
**Scope:** full browser UI foundation inside `luma-companion-2` 0.1.0. Telemetry remains off.  
**Device state:** no TV install, launch or input capture in this run. No ADB automation. No commit or push.

## 1. Environment

| Item | Value |
|---|---|
| Workspace | `D:\apps\photos galary app` |
| App | `com.luma.companion2`, version `0.1.0` |
| TV in CLI | `lumatv`, earlier reported `192.168.1.2:9922`, LG `55UR7500PSC`, SDK `10.3.2` |
| Local Node | `v26.12.1`, npm `11.20.0` |
| CLI | `@webos-tools/cli` 3.2.6 |
| Package output | `luma-companion-2/ipk/com.luma.companion2_0.1.0_all.ipk` |

The TV was not reachable in this run (`ares-device -i -d lumatv` timed out and port `9922` was
closed from this PC). Therefore all evidence below is local source, static analysis and stub-DOM
verification.

## 2. Static checks

| Check | Command | Result |
|---|---|---|
| JS syntax + appinfo JSON | `npm run check` | Pass. Includes all nine JS files: contract, storage, diagnostics, remote session, browser view/model/components/controller and app. |
| Envelope validator | `contract-check.js` | Pass, 11/11. |
| Storage guard | `storage-check.js` | Pass, 8/8. |
| DOM + metadata wiring | `dom-meta-check.js` | Pass. Details below. |
| Package | `npm run package` | Success. Non-minified IPK regenerated after the final `app.js` fix. |

`dom-meta-check.js` reported:

```text
js_files=9
referenced_ids=47
missing_dom_ids=[]
missing_appinfo_fields=[]
appinfo_version_valid=true
package_name=luma-companion-2
focusable_static=9
focusable_generated_markers=3
asset_refs=11
missing_assets=[]
controller_init_wired=true
stale_browser_api=false
script_order_ok=true
DOM_METADATA_OK
```

The generated-ID scanner recognises IDs emitted by `browser-components.js`, so dynamic controls
such as `#address-input`, `#luma-video` and `#video-seek` are covered. The harness also checks
that `app.js` calls `LumaBrowserController.init()` and `.command()`, that no removed
`LumaBrowserView.snapshot()/subscribe()/init()` call remains, and that browser scripts load in
dependency order.

## 3. Browser module harnesses

All harnesses live in the temporary verification folder and are not shipped in the IPK.

| Harness | Coverage | Result |
|---|---|---|
| `browser-view-check.js` | URL validation, per-tab frames, sandbox attribute, show/hide, zoom, reload, stop, callbacks, tab sync, close | 38/38 pass |
| `browser-model-check.js` | tabs, history stacks, bookmarks, shortcuts, settings, persistence, startup screens, direct-video detection | 48/48 pass |
| `browser-components-check.js` | all component screens, real controls, honest empty/unsupported states, focus markers, no placeholder links | 61/61 pass |
| `browser-controller-check.js` | chrome render, commands, tab cycle, video play, Ctrl+L focus, Back key, suspend/resume | 38/38 pass |
| `remote-session-check.js` | IPv4 guard, fixed path, no credentials in hello, advertised capabilities, ACKs, extra TV-side commands, `app.focus` rejection, suspend/resume | 26/26 pass |

Notable verified behaviours:

- `javascript:`, `file:`, `ftp:`, relative values, credentials, control characters, missing host
  and oversized URLs are rejected by the frame validator.
- Unsupported address text becomes a safe search URL; it is never executed as a scheme.
- Three tabs can coexist as three hidden frames; closing a tab destroys only its frame.
- `video.play` is accepted for a direct `.mp4` URL and rejected for a normal webpage.
- `app.focus` remains rejected by both the remote boundary and the controller.
- Ctrl+L focuses the address bar and prevents the default browser shortcut.
- Back key (461) prevents webOS app exit and returns to Home.
- Suspend pauses the video; resume re-renders the chrome.

## 4. Issues found and fixed during this rewrite

| Issue | Fix |
|---|---|
| Old `browser-view-check.js` called the removed `view.init()` and crashed. | Rewritten for the multi-tab `load/showFrame/setZoom/reload/stop/syncTabs/close` API. |
| `clearHistory()` cleared state but returned `undefined`. | Returns `true` so remote/UI callers get an honest ACK/result. |
| Remote command limit was implicitly 5 per 10 s; fourth browser command was rate-limited. | Explicit `MAX_COMMANDS_PER_WINDOW = 20`; 26/26 remote cases now pass. |
| Dot-named remote commands (`video.play`) did not map to hyphen UI actions (`video-play`). | Controller converts `.` to `-` before dispatch. |
| Several actions requested focus after render, so focus landed on the wrong element. | Focus requests are now set before model mutation; error overlay requests focus before render. |
| `app.js` never called the new controller's `init()`, so the browser shell would not render on device. | Added guarded `LumaBrowserController.init()` in startup and a static harness assertion. |
| DOM harness treated dynamic component IDs as missing. | Harness now scans generated `id="..."` and `data-focusable="true"` markers in JS sources. |

## 5. Old results superseded

The earlier `docs/PHASE_3_BROWSER_TEST_RESULTS.md` recorded a single-frame browser card with
22/22 URL/frame checks. That result is valid for the old Phase 3 view only. The current numbers
in this file supersede it after the full browser-shell rewrite.

## 6. Telemetry and privacy

- No `js/telemetry.js` exists in active source and no telemetry wiring was added.
- The v1 handshake still carries `telemetry_enabled: false`; that is a protocol field, not active
  collection.
- Bookmarks, history, shortcuts, tabs and settings stay in local `localStorage` under
  `luma.browser.v1`. No URL, typed text or media metadata is sent by this UI.
- The Developer Mode passphrase is not present in project files, docs, logs or commits.

## 7. Not verified

| Item | Status |
|---|---|
| TV visual rendering at 3840×2160, 1920×1080 and 1280×720 | Not captured; CSS tiers are source-verified only. |
| Real Magic Remote pointer/arrow input | Not captured; no key-injection tool was used. |
| LG Chromium iframe behaviour for arbitrary third-party sites | Pending; X-Frame-Options/CSP refusals are reported honestly. |
| Native 4K video decode, DRM, HLS/DASH | Explicitly excluded. |
| System-browser Luna launch | Not attempted; not claimed. |
| TV-to-phone WebSocket handshake and phone approval | Pending manual device test. |
| Extra TV-side browser/video commands from the phone UI | Prepared only; Android Host mode currently exposes three commands. |
| Install / launch of the regenerated IPK | Not performed; user chose docs-only in this run. |
| ADB automation | Not used. |
| Commit / push | Not performed. |

## 8. Acceptance gate

1. Install the regenerated IPK on `lumatv` and launch `com.luma.companion2`.
2. Confirm the tab bar, address bar, side menu and Home screen render at the TV's CSS viewport.
3. Open an absolute http(s) page and confirm the sandboxed frame loads or shows the honest
   framed-page error.
4. Exercise arrow keys, OK/Enter, Back, Ctrl+L equivalent and focus restoration.
5. Confirm hide/resume cleans up frames, watchdog timers and video playback.
6. Only then test the phone handshake and the three commands the Android Host mode exposes.

Until these device steps are confirmed, the UI foundation is implemented and locally verified,
but not accepted as end-to-end working on the TV.
