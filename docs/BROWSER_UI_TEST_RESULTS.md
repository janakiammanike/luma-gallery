# Luma Browser UI Test Results

**Date:** 2026-11-25  
**Scope:** full browser UI foundation inside `luma-companion-2` 0.1.0. Telemetry remains off.  
**Device state:** current IPK installed and launched on `lumatv`; live search and frame-block handling
verified through DevTools. Physical Magic Remote input was not injected. No ADB automation. The
initial UI work was pushed as `d5b06a1`; the framing fix in this document is **not** committed yet.

## 1. Environment

| Item | Value |
|---|---|
| Workspace | `D:\apps\photos galary app` |
| App | `com.luma.companion2`, version `0.1.0` |
| TV in CLI | `lumatv` at `192.168.1.2:9922`, LG `55UR7500PSC`, SDK `10.3.2`, firmware `33.31.75` |
| Local Node | `v26.12.1`, npm `11.20.0` |
| CLI | `@webos-tools/cli` 3.2.6 |
| Package output | `luma-companion-2/ipk/com.luma.companion2_0.1.0_all.ipk` |
| Engine on TV | Chromium `120.0.6099.270` |

The TV was unreachable in an earlier docs-only run, but became reachable after the user supplied
the Developer Mode passphrase again. The passphrase was **not** written to files, docs, logs or
commits; the already configured `lumatv` CLI entry was used throughout.

## 2. Static checks

| Check | Command | Result |
|---|---|---|
| JS syntax + appinfo JSON | `npm run check` | Pass. Includes all nine JS files: contract, storage, diagnostics, remote session, browser view/model/components/controller and app. |
| Envelope validator | `contract-check.js` | Pass, 11/11. |
| Storage guard | `storage-check.js` | Pass, 8/8. |
| DOM + metadata wiring | `dom-meta-check.js` | Pass. Details below. |
| Package | `npm run package` | Success. Non-minified IPK regenerated after the framing fix. |

`dom-meta-check.js` reported:

```text
js_files=9
referenced_ids=48
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
such as `#address-input`, `#luma-video`, `#browser-notice` and `#video-seek` are covered. The
harness also checks that `app.js` calls `LumaBrowserController.init()` and `.command()`, that no
removed `LumaBrowserView.snapshot()/subscribe()/init()` call remains, and that browser scripts load
in dependency order.

## 3. Browser module harnesses

All harnesses live in the temporary verification folder and are not shipped in the IPK.

| Harness | Coverage | Result |
|---|---|---|
| `browser-view-check.js` | URL validation, per-tab frames, sandbox attribute, show/hide, zoom, reload, stop, callbacks, tab sync, close | 38/38 pass |
| `browser-model-check.js` | tabs, history, bookmarks, shortcuts, settings, persistence, framing denylist, legacy migration, startup screens, direct-video detection | 66/66 pass |
| `browser-components-check.js` | all component screens, real controls, honest empty/unsupported states, focus markers, no placeholder links | 61/61 pass |
| `browser-controller-check.js` | chrome render, commands, blocked-site notice, tab cycle, video play, Ctrl+L focus, Back key, suspend/resume | 41/41 pass |
| `remote-session-check.js` | IPv4 guard, fixed path, no credentials in hello, advertised capabilities, ACKs, extra TV-side commands, `app.focus` rejection, suspend/resume | 26/26 pass |

Notable verified behaviours:

- `javascript:`, `file:`, `ftp:`, relative values, credentials, control characters, missing host
  and oversized URLs are rejected by the frame validator.
- Unsupported address text becomes a safe search URL; it is never executed as a scheme.
- Hosts known to block framing are rejected before an iframe is created, and a visible notice
  explains why.
- Saved Google/DuckDuckGo settings from an older build migrate to Bing on startup.
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
| Pressing Enter/Search on Google, GitHub, YouTube or DuckDuckGo made webOS replace the app with `loaderror.html?errorCode=-27`. | Added a conservative framing denylist before iframe creation, a visible `browser-notice`, Bing as the default search engine and safe default shortcuts. |
| Old `localStorage` settings kept Google as search engine/homepage after the fix. | `restore()` now migrates blocked engines/homepages and blocked default shortcuts to the safe defaults. |

Chromium defines `-27` as `ERR_BLOCKED_BY_RESPONSE`: the response arrived with requirements such as
`X-Frame-Options` or `CSP: frame-ancestors` that the iframe cannot satisfy. On this LG webOS build
the failed frame is surfaced as a top-level Application Error page instead of remaining inside the
frame. The app therefore refuses known blocking hosts locally rather than letting the platform
tear down the shell.

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

## 7. Live TV runtime verification

Initial verification after the full browser rewrite:

| Check | Result |
|---|---|
| Port reachability | `Test-NetConnection 192.168.1.2:9922` → `True`. |
| Device identity | `ares-device -i -d lumatv`: model `55UR7500PSC`, SDK `10.3.2`, firmware `33.31.75`. |
| Install / launch | `ares-install` → **Success**; `ares-launch` → **Launched**; running app listed. |
| Remote inspector | Title `Luma Browser · Companion 2.0`, URL `file:///media/developer/apps/usr/palm/applications/com.luma.companion2/index.html`. |

CDP `Runtime.evaluate` on that live page reported Home/New Tab active, 1 tab, 56 focus markers,
tab bar and address bar present, Companion hidden, status `New Tab`, remote `IDLE`, viewport
`1920 × 1080` at DPR `2`, and engine `Chromium 120.0.6099.270`.

### Bug reproduced and fixed on device

The user reported that pressing Enter/Search crashed the app and showed TV error code `27`.
Reproduction through the live DevTools target confirmed:

| Action before fix | Observed result |
|---|---|
| `https://www.google.com/search?q=test` in the iframe | Target replaced by `loaderror.html?errorCode=-27&hostname=www.google.com`, title `Application Error`. |
| `https://github.com` | Same error page with `hostname=github.com`. |
| `https://html.duckduckgo.com/html/?q=test` | Same error page with `hostname=html.duckduckgo.com`. |
| `https://www.youtube.com` | Same error page with `hostname=www.youtube.com`. |
| `https://example.com/` | Loaded correctly; shell stayed alive. |
| `https://www.bing.com/search?q=test` | Loaded correctly; shell stayed alive. |

After installing the rebuilt IPK with the denylist, notice toast and legacy-setting migration, the
same DevTools checks reported:

| Action after fix | Observed result |
|---|---|
| `LumaBrowserModel.navigateActive('https://www.google.com/search?q=test')` | Returns `false`; app target stays `index.html`; notice: “This site blocks in-app framing on LG webOS. Try Bing, Wikipedia or example.com.” |
| Synthetic Enter on Home search field with text `luma tv` | Returns `true`; `screen=page`; URL `https://www.bing.com/search?q=luma%20tv`; app stays alive. |
| Synthetic Enter with text `google.com` | Returns `false`; app stays alive; notice visible. |
| `LumaBrowserModel.navigateActive('https://github.com')` | Returns `false`; app stays alive; notice visible. |
| Bing search frame | `screen=page`, `loading=false`, `error=null`, frame `src` equals the Bing URL. |

The persisted pre-fix setting `searchEngine='google'` was migrated to `bing` on launch, which was
necessary for the Enter/Search path to work on the user's TV. A second screenshot of the live
notice state was captured through CDP and shown in the OpenCode browser tab for review; it is not
embedded in the repository.

## 8. Not verified

| Item | Status |
|---|---|
| 4K and 720p CSS viewport tiers | Live page measured 1920×1080 CSS with DPR 2. Other tiers are source-verified only. |
| Physical Magic Remote pointer/arrow input | Not injected; synthetic Enter/key events were used through DevTools. |
| Sites outside the denylist that also send `X-Frame-Options` / `frame-ancestors` | May still produce the LG error page; not promised. The denylist is conservative and grows with device evidence. |
| Native 4K video decode, DRM, HLS/DASH | Explicitly excluded. |
| System-browser Luna launch | Not attempted; not claimed. |
| TV-to-phone WebSocket handshake and phone approval | Pending manual device test. |
| Extra TV-side browser/video commands from the phone UI | Prepared only; Android Host mode currently exposes three commands. |
| Hide/resume cleanup on the TV | Verified locally in stub DOM; not yet captured on device. |
| ADB automation | Not used. |
| Commit / push of the framing fix | Not performed; requires explicit permission. |

## 9. Acceptance gate

1. ~~Install the regenerated IPK on `lumatv` and launch `com.luma.companion2`.~~ Done.
2. ~~Confirm the tab bar, address bar, side menu and Home screen render at the TV's CSS viewport.~~ Done through CDP inspection.
3. ~~Open an absolute http(s) page and confirm the frame loads or reports an honest error.~~ Done: Bing and example.com load; blocking hosts show an in-app notice without killing the app.
4. ~~Exercise Enter/Search with a safe engine.~~ Done through synthetic key events. Physical Magic Remote input remains manual.
5. Confirm hide/resume cleans up frames, watchdog timers and video playback on the device.
6. Only then test the phone handshake and the three commands the Android Host mode exposes.

The UI foundation is implemented, locally verified and confirmed rendering on the physical TV.
End-to-end acceptance still needs physical remote input, hide/resume cleanup and handshake steps.
