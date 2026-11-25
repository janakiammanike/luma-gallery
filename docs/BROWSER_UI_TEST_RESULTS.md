# Luma Browser UI Test Results

**Date:** 2026-11-25  
**Scope:** full browser UI foundation inside `luma-companion-2` 0.1.0. Telemetry remains off.  
**Device state:** the final media/audio/local-search IPK was packaged, installed and launched on
`lumatv`. Live DevTools verification confirmed the browser shell, honest capability reporting, a
real search-result click into Wikipedia, and the blocked YouTube result showing a notice without
killing the app. Physical Magic Remote input was not injected. No ADB automation. The initial UI
work was pushed as `d5b06a1` and the framing fix as `499320f`; all later work remains uncommitted.

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
| JS syntax + appinfo JSON | `npm run check` | Pass. Includes all ten JS files: contract, storage, diagnostics, remote session, browser view/model/components, media capabilities, browser controller and app. |
| Envelope validator | `contract-check.js` | Pass, 11/11. |
| Storage guard | `storage-check.js` | Pass, 8/8. |
| DOM + metadata wiring | `dom-meta-check.js` | Pass. Details below. |
| Package | `npm run package` | Success. Non-minified IPK regenerated after the framing fix, media-engine additions and local-search result flow. |

`dom-meta-check.js` reported:

```text
js_files=10
referenced_ids=56
missing_dom_ids=[]
missing_appinfo_fields=[]
appinfo_version_valid=true
package_name=luma-companion-2
focusable_static=9
focusable_generated_markers=3
asset_refs=12
missing_assets=[]
controller_init_wired=true
stale_browser_api=false
script_order_ok=true
DOM_METADATA_OK
```

The generated-ID scanner recognises IDs emitted by `browser-components.js`, so dynamic controls
such as `#address-input`, `#luma-video`, `#luma-audio`, `#browser-notice`, `#video-seek` and
`#audio-seek` are covered. The
harness also checks that `app.js` calls `LumaBrowserController.init()` and `.command()`, that no
removed `LumaBrowserView.snapshot()/subscribe()/init()` call remains, and that browser scripts load
in dependency order.

## 3. Browser module harnesses

All harnesses live in the temporary verification folder and are not shipped in the IPK.

| Harness | Coverage | Result |
|---|---|---|
| `browser-view-check.js` | URL validation, per-tab frames, sandbox/popups/fullscreen, show/hide, zoom, reload, stop, callbacks, tab sync, close | 40/40 pass |
| `browser-model-check.js` | tabs, history, bookmarks, shortcuts, settings, persistence, framing denylist, legacy migration, startup screens, direct-video/audio detection, search entries and entry metadata | 87/87 pass |
| `browser-components-check.js` | all component screens including capabilities, audio and local search results, real controls, honest empty/unsupported states, focus markers, no placeholder links | 82/82 pass |
| `browser-controller-check.js` | chrome render, commands, blocked-site notice, tab cycle, video/audio play, subtitles, diagnostics, capabilities render, local result fetch/render/click actions, Ctrl+L, Back key, suspend/resume | 60/60 pass |
| `remote-session-check.js` | IPv4 guard, fixed path, no credentials in hello, advertised browser/video/audio capabilities, ACKs, extra TV-side commands, `app.focus` rejection, suspend/resume | 29/29 pass |

Notable verified behaviours:

- `javascript:`, `file:`, `ftp:`, relative values, credentials, control characters, missing host
  and oversized URLs are rejected by the frame validator.
- Unsupported address text becomes a safe search URL; it is never executed as a scheme.
- Hosts known to block framing are rejected before an iframe is created, and a visible notice
  explains why.
- Saved Google/DuckDuckGo settings from an older build migrate to Bing on startup.
- Typed search text becomes a local results screen backed by DuckDuckGo Instant Answers; blocked
  engine pages are not framed and every result destination is rechecked before opening. Saved search
  entries still use that screen after restore, back, forward, reload and tab reactivation.
- Three tabs can coexist as three hidden frames; closing a tab destroys only its frame.
- `video.play` is accepted for a direct `.mp4` URL and rejected for a normal webpage.
- `audio.play` is accepted for a direct `.mp3` URL and rejected for a normal webpage.
- Optional WebVTT subtitles are attached only from an absolute http(s) address; a missing or
  invalid address is reported in the player.
- Media Capabilities renders codec, display, MSE, Web Audio and output-path rows from runtime
  detection. `not reported` and `not exposed` are shown instead of checkmarks.
- `app.focus` remains rejected by both the remote boundary and the controller.
- Ctrl+L focuses the address bar and prevents the default browser shortcut.
- Back key (461) prevents webOS app exit and returns to Home.
- Suspend pauses both the video and the audio elements; resume re-renders the chrome.

## 4. Issues found and fixed during this rewrite

| Issue | Fix |
|---|---|
| Old `browser-view-check.js` called the removed `view.init()` and crashed. | Rewritten for the multi-tab `load/showFrame/setZoom/reload/stop/syncTabs/close` API. |
| `clearHistory()` cleared state but returned `undefined`. | Returns `true` so remote/UI callers get an honest ACK/result. |
| Remote command limit was implicitly 5 per 10 s; fourth browser command was rate-limited. | Explicit `MAX_COMMANDS_PER_WINDOW = 20`; 29/29 remote cases now pass. |
| Dot-named remote commands (`video.play`) did not map to hyphen UI actions (`video-play`). | Controller converts `.` to `-` before dispatch. |
| Several actions requested focus after render, so focus landed on the wrong element. | Focus requests are now set before model mutation; error overlay requests focus before render. |
| `app.js` never called the new controller's `init()`, so the browser shell would not render on device. | Added guarded `LumaBrowserController.init()` in startup and a static harness assertion. |
| DOM harness treated dynamic component IDs as missing. | Harness now scans generated `id="..."` and `data-focusable="true"` markers in JS sources. |
| Pressing Enter/Search on Google, GitHub, YouTube or DuckDuckGo made webOS replace the app with `loaderror.html?errorCode=-27`. | Added a conservative framing denylist before iframe creation, a visible `browser-notice`, Bing as the default search engine and safe default shortcuts. |
| Old `localStorage` settings kept Google as search engine/homepage after the fix. | `restore()` now migrates blocked engines/homepages and blocked default shortcuts to the safe defaults. |
| Desktop Bing result links used `target="_blank"` and did nothing in the opaque iframe. | `allow-popups`, popup flags and Bing `NEWWND=0` variants were tested and rejected. Bing `mobile=1` removed `target`, but its YouTube click still replaced the app with `loaderror.html?errorCode=-27`, even with the shipped opaque sandbox. The shipped fix renders DuckDuckGo Instant Answers locally: each result is a normal Luma button, is validated by the same URL guard, and blocked hosts show the honest notice instead of being opened. |
| No honest place existed to inspect codec, display or audio support. | Added `js/media-capabilities.js` and a Media Capabilities screen with runtime-only values. |
| Video and audio players exposed no local recovery evidence. | Added buffering/recovery/decode counters, optional WebVTT loading, audio-track reporting and suspend cleanup for both media elements. |

Chromium defines `-27` as `ERR_BLOCKED_BY_RESPONSE`: the response arrived with requirements such as
`X-Frame-Options` or `CSP: frame-ancestors` that the iframe cannot satisfy. On this LG webOS build
the failed frame is surfaced as a top-level Application Error page instead of remaining inside the
frame. The app therefore refuses known blocking hosts locally rather than letting the platform
tear down the shell.

## 4A. Media smoke check (local)

The rebuilt IPK passed the following local checks before packaging:

| Check | Evidence |
|---|---|
| Capability detection module loads | `npm run check` includes `js/media-capabilities.js`. |
| Video codec rows | `browser-components-check.js` verifies H.264, HEVC, VP9, AV1, HLS and DASH rows are rendered from `canPlayType()`, with `not reported` for unsupported answers. |
| Audio format rows | FLAC, WAV/PCM, AAC, MP3 and Ogg rows are rendered from `audio.canPlayType()`. |
| No fake output status | Output rows read `not exposed to web app` / `not exposed to HTMLMediaElement`. |
| Video player | Direct `.mp4` accepted, play/screen verified, local diagnostics and optional WebVTT submit verified, `video-close` returns to the page. |
| Audio player | Direct `.mp3` accepted, play/audio-screen verified, suspend pauses the audio element. |
| Remote states | `audio.play/pause/seek` added to the prepared TV-side capability list; hello advertises them and the harness confirms ACKs; `app.focus` stays reserved and rejected. |

Final rebuild result: `npm run check` passed; contract 11/11, storage 8/8, DOM/metadata pass,
view 40/40, model 87/87, components 82/82, controller 60/60 and remote session 29/29 passed.
`npm run package` produced `com.luma.companion2_0.1.0_all.ipk` successfully. The TV was reachable,
so this exact build was installed and launched; the live results are in section 7.

YouTube is deliberately **not** included as a working source. The full site's X-Frame-Options/CSP
caused error code `-27` on the TV, and audio extraction from YouTube is not possible inside the
sandboxed iframe. The app says so instead of offering a fake player. HLS/DASH are detected but not
claimed: without a bundled demuxer and verified device support they remain `not reported`.

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

### Final local-search build on device

The rebuilt IPK was installed with `ares-install -d lumatv ipk/com.luma.companion2_0.1.0_all.ipk`
and launched with `ares-launch -d lumatv com.luma.companion2`. CDP reported the live target title
`Luma Browser · Companion 2.0` at `file:///media/developer/apps/usr/palm/applications/com.luma.companion2/index.html`.

| Live check | Result |
|---|---|
| Initial state | `screen=home`, 1 tab, no frame, telemetry flag false, 72 `[data-focusable="true"]` markers. |
| Typed search via `LumaBrowserController.command('browser.open', { url: 'youtube' })` | Accepted; `screen=search`; 12 local results rendered (1 openable, 11 labelled `Blocked in-app`); no search-engine frame and no error overlay. 48 focus markers. |
| Real mouse click on the Wikipedia result | `screen=page`; URL and frame `src` both `https://en.wikipedia.org/wiki/YouTube`; `loading=false`; frame sandbox `allow-scripts allow-forms allow-popups`, allow `fullscreen; autoplay`; app target still alive; telemetry false; 35 focus markers. |
| Back, then real mouse click on the YouTube result | No navigation; `screen=search`; visible notice “This site blocks in-app framing on LG webOS. Try Bing, Wikipedia or example.com.”; frame `src` unchanged; app target still alive. |
| Target after both clicks | One live app target at `index.html`; no `Application Error` / `loaderror.html` replacement. |

CDP screenshots of the local results, the opened Wikipedia page and the blocked-result notice were
captured in the temporary verification folder; they are not embedded in the repository. This is a
real click path from a TV-rendered Luma button, not a synthetic `a.click()` inside a framed search
engine.

## 8. Not verified

| Item | Status |
|---|---|
| 4K and 720p CSS viewport tiers | Live page measured 1920×1080 CSS with DPR 2. Other tiers are source-verified only. |
| Physical Magic Remote pointer/arrow input | Not injected; synthetic Enter/key events were used through DevTools. |
| Sites outside the denylist that also send `X-Frame-Options` / `frame-ancestors` | May still produce the LG error page; not promised. The denylist is conservative and grows with device evidence. |
| Native 4K video decode, DRM, HLS/DASH | Explicitly excluded. |
| System-browser Luna launch | Not attempted; not claimed. |
| TV-to-phone WebSocket handshake and phone approval | Pending manual device test. |
| Extra TV-side browser/video/audio commands from the phone UI | Prepared only; Android Host mode currently exposes three commands. |
| Hide/resume cleanup on the TV | Verified locally in stub DOM; not yet captured on device. |
| ADB automation | Not used. |
| Commit / push of the media, capability and local-search work | Not performed; requires explicit permission. Only the earlier UI and framing-fix commits were pushed. |

## 9. Acceptance gate

1. ~~Install the regenerated IPK on `lumatv` and launch `com.luma.companion2`.~~ Done.
2. ~~Confirm the tab bar, address bar, side menu and Home screen render at the TV's CSS viewport.~~ Done through CDP inspection.
3. ~~Open an absolute http(s) page and confirm the frame loads or reports an honest error.~~ Done: Bing and example.com load; blocking hosts show an in-app notice without killing the app.
4. ~~Exercise Enter/Search with a safe engine.~~ Done through synthetic key events and the local
   results screen. Physical Magic Remote input remains manual.
5. ~~Verify search-result clicks.~~ Done on device with real CDP mouse clicks: Wikipedia opens in
   the same tab, YouTube shows the honest blocked notice, and the app shell survives both.
6. Confirm hide/resume cleans up frames, watchdog timers and video playback on the device.
7. Only then test the phone handshake and the three commands the Android Host mode exposes.

The UI foundation is implemented, locally verified and confirmed rendering on the physical TV.
End-to-end acceptance still needs physical remote input, hide/resume cleanup and handshake steps.
