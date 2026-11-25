# Phase 3 — Project, integration and capability audit

**Date:** 2026-11-25  
**Scope:** Audit only. No Chromium core, GPU/NDL, browser UI, Android Remote or Luma Gallery change is included.

## 1. Project and previous-phase verification

| Item | Finding |
|---|---|
| Workspace git | `D:\apps\photos galary app`, branch `main`, HEAD `01ea4f1`, working tree clean at audit start |
| Last commit | `01ea4f1 Implement Phase 2 Luma remote host and verify final 0.2.2 TV build` |
| Phase 1 | `source/package/run.sh` has one additive opt-in branch; original Chromium exec block unchanged; only `ext/tvtools` without `CN_LUMA=1` or `{"luma":true}` |
| Phase 2 | `source/package/ext/luma/**` is 25 files; final installed version `0.2.2`; permissions `storage,tabs`; no `debugger`, no native messaging, no telemetry send path |
| Chromium source tree | `C:\Users\janak\Downloads\WebOS-Chromium-main\WebOS-Chromium-main` is a downloaded tree, not a git repository |
| Installed TV package | official v1.1.1 repackaged locally with only `bin/run.sh` + `ext/luma/**`; version/control/appinfo/browser binaries unchanged |

## 2. Chromium integration points (all inside `ext/luma/**`)

| Interface | Current use | Phase 3 use |
|---|---|---|
| `manifest.json` content_scripts | `shared/bus.js`, `content/capability.js`, `content/bridge.js` in isolated world | Add media detection module; no monkey-patching of site APIs |
| `shared/bus.js` | Namespaced `source:"luma"` runtime messages | Reuse for media state and diagnostics messages |
| `shared/storage.js` | `luma.settings`, `luma.quickLinks`, `luma.remote` | Add `luma.media.profiles`; never writes Chromium profile config |
| `worker.js` | MV3 service worker, storage, tabs, remote dispatcher | Add profile persistence + message routing only; no DOM APIs in worker |
| Extension pages | `home.html`, `options.html`, `popup.html` | Add a separate Luma Cinema Control Panel; do not alter Chromium UI |
| `content/capability.js` | Read-only `canPlayType`/MSE/`decodingInfo` probe | Reuse and extend; no result leaves the TV |
| `content/bridge.js` | HTML5 media play/pause/seek/volume/mute via DOM | Reuse; Phase 3 adds read-only detail detection |

## 3. Web/platform interfaces that are actually available

Verified by source inspection and the shipped build; runtime capability status must still be reported by diagnostics, not assumed.

| Interface | Availability from extension | Limitation |
|---|---|---|
| `HTMLMediaElement` (`querySelectorAll("video,audio")`, `play`, `pause`, `currentTime`, `duration`, `videoWidth/Height`, `error`) | Available in content script and extension pages | Cross-origin/DRM media state may be opaque; do not claim access |
| `HTMLVideoElement.canPlayType` | Available | Returns candidate strings, not a playback guarantee |
| `MediaSource.isTypeSupported` | Available | MSE support does not guarantee HLS.js usability or codec playback |
| `navigator.mediaCapabilities.decodingInfo` | Conditionally available | If absent, report `Not detected` |
| Web Audio (`AudioContext`, `AudioWorklet`, `BiquadFilterNode`, `MediaElementAudioSourceNode`) | Available in extension page | Media element routing is blocked for cross-origin/DRM sources; no silent processing |
| WebGL / WebGL2 | Conditionally available | Experimental local processing only; no claim about LG native AI upscaling |
| WebGPU | Conditionally available | Must be probed; do not rely on it |
| `navigator.bluetooth` / webOS Bluetooth codec APIs | Not available to this extension | Pairing/routing stays in TV system settings; report `Not detected` |
| `chrome.debugger` / CDP | Removed from final `0.2.2` | Requires separate explicit approval; Phase 3 does not use it |
| Native messaging, Luna/LS2, `PalmSystem`, NDL socket | Unavailable by design | No attempt; no core change |
| `chrome.tabs` | Available | Used for active-tab media info only |

## 4. Capability rules adopted for Phase 3

- Every capability is reported as `Working`, `Partially working`, `Unsupported`, `Not detected`, or `Not testable`.
- `canPlayType` results are reported as browser candidates, not as actual playback proof.
- Native HLS is reported only if `canPlayType("application/vnd.apple.mpegurl")` is non-empty; HLS.js is not bundled.
- No large codec library, AI model or shader pipeline is downloaded or embedded without a separate size/license/performance report.
- Enhancement controls are applied only to media elements owned by the Luma Cinema page, never to an arbitrary website player.
- Original picture and Original Audio are instant, always available defaults.
- HDR, frame rate, bitrate, audio codec, channel count and Hi-Res values are shown only when exposed by the media element/API; otherwise `Unknown`/`Not detected`.
- No claim of Atmos, DTS:X, LDAC, aptX HD, lossless Bluetooth, true AI upscaling or frame interpolation.

## 5. Decision

The whole Phase 3 scope can be implemented inside `ext/luma/**` as a capability-aware foundation: diagnostics, read-only detection, a Luma-owned playback surface, profiles and a separate control panel. Anything requiring Chromium core, NDL, system Bluetooth or DRM/CORS bypass is reported as `Requires future native integration` and is not attempted.
