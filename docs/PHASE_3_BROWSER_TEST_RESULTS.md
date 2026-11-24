# Luma Companion 2.0 — Phase 3 Browser Test Results

**Date:** 2026-11-25  
**Status:** historical Phase 3 record; superseded by `docs/BROWSER_UI_TEST_RESULTS.md` after the
full browser-shell rewrite. Telemetry remains disabled. No ADB automation was used.

## Historical scope

This file first recorded the constrained single-frame browser card, the `browser.open` /
`browser.close` allow-list and the `tvremote2` 3.0.0 Host-mode build. Those checks were valid for
that implementation. The current browser shell, multi-tab model, component set, controller and
focus system are documented and verified in `docs/BROWSER_UI_TEST_RESULTS.md`.

## Original environment

- Workspace: `D:\apps\photos galary app`
- TV device in CLI: `lumatv` at `prisoner@192.168.1.2:9922`
- CLI: `@webos-tools/cli` 3.2.6
- TV report from earlier phase: model `55UR7500PSC`, SDK `10.3.2`, firmware `33.31.75`
- Local Node: `v26.12.1`, npm `11.20.0`
- Android host app: `tvremote2` 3.0.0 (`versionCode = 3`)

## Original Phase 3 results (superseded)

| Check | Result at that time |
|---|---|
| `npm run check` | Pass, including the original `js/browser-view.js`. |
| `contract-check.js` | Pass, 11/11. |
| `storage-check.js` | Pass, 8/8. |
| `dom-meta-check.js` | Pass: 35 referenced IDs, 0 missing, 13 focusable elements. |
| `remote-session-check.js` | Pass, 20/20 with stub WebSocket. |
| `browser-view-check.js` | Pass, 22/22 for the single-frame URL validator and frame lifecycle. |
| `.\gradlew :tvremote2:assembleDebug --no-daemon` | Pass. |

The original remote harness verified invalid IPv4 rejection, fixed path
`ws://192.168.1.3:8787/luma/companion/v1`, a `hello` with no token/key, 32-hex session id,
approval-gated `capability`, `diagnostics.refresh`, `browser.open`, `app.focus` rejection,
invalid-JSON counting, suspend close and manual disconnect.

## What changed after that record

1. The browser card was replaced by a full shell: tab bar, address bar, side menu, Home,
   Bookmarks, History, Tab Manager, Settings, Video and Companion screens.
2. `browser-view.js` became a multi-tab sandboxed iframe renderer.
3. `browser-model.js`, `browser-components.js` and `browser-controller.js` were added.
4. `app.js` was rewritten to own lifecycle/diagnostics/LAN only and to delegate browser actions
   to `LumaBrowserController.command()`.
5. The TV side additionally prepares browser/video command states for a future Host-mode UI;
   the Android Host mode still exposes only `diagnostics.refresh`, `browser.open` and
   `browser.close`.
6. Telemetry source created during an interim step was fully removed.

Because of these changes, the old 22/22 browser check and 20/20 remote check are not the current
acceptance evidence. Current results are:

- `browser-view-check.js`: 38/38
- `browser-model-check.js`: 48/48
- `browser-components-check.js`: 61/61
- `browser-controller-check.js`: 38/38
- `remote-session-check.js`: 26/26
- `contract-check.js`: 11/11
- `storage-check.js`: 8/8
- `npm run package`: success

## Device state at this time

The original Phase 3 IPK was not installed in that run. A later full-browser IPK was regenerated
at `luma-companion-2/ipk/com.luma.companion2_0.1.0_all.ipk`, but it has **not** been installed or
launched. Physical TV rendering, Magic Remote input and the real phone handshake remain manual
acceptance items.

The last confirmed live Phase 2 process was PID `15834`. The TV was unreachable in the current
run (`ares-device -i -d lumatv` timed out; port `9922` was closed from this PC).

## Not verified

| Item | Status |
|---|---|
| TV-to-phone WebSocket upgrade | Pending. |
| Phone approval and capability on real devices | Pending; no Approve tap was performed. |
| Browser frame rendering on LG `55UR7500PSC` | Not captured. |
| Third-party sites that refuse framing | Not promised. |
| DRM or HLS/DASH playback | Explicitly excluded. |
| Magic Remote entry of URL / IP / port | Not captured. |
| In-app Luna response after source changes | Not captured. |
| `tvremote2` 3.0.0 install on the phone | Not performed; build output only. |
| ADB automation | Not used. |
| Commit / push | Not performed. |

## Conclusion

Phase 3 established the safe in-app framing boundary and the Host-mode integration direction.
The current browser UI foundation builds on that boundary and passes all local static and
stub-DOM checks. It is not accepted as end-to-end working until a user-supervised device test
confirms rendering, input, cleanup and the handshake. Telemetry remains disabled.
