# webOS Capability Matrix — Luma Companion 2.0

**Date:** 2026-11-25  
**Target TV:** LG `55UR7500PSC.ATR`; CLI reports model `55UR7500PSC`, SDK `10.3.2`, firmware `33.31.75`  
**Scope:** what the official LG documentation says, what this app uses through Phase 3, and what remains physically unverified.

Official sources consulted:

- Getting Started: https://webostv.developer.lge.com/develop/getting-started
- App Lifecycle: https://webostv.developer.lge.com/develop/getting-started/app-lifecycle
- App Lifecycle Management: https://webostv.developer.lge.com/develop/guides/app-lifecycle-management
- Web API and Web Engine: https://webostv.developer.lge.com/develop/specifications/web-api-and-web-engine
- appinfo.json reference: https://webostv.developer.lge.com/develop/references/appinfo-json
- CLI Developer Guide: https://webostv.developer.lge.com/develop/tools/cli-dev-guide
- TV Device Information: https://webostv.developer.lge.com/develop/references/tv-device-information
- Back Button: https://webostv.developer.lge.com/develop/guides/back-button
- Magic Remote: https://webostv.developer.lge.com/develop/references/magic-remote
- Streaming Protocol and DRM: https://webostv.developer.lge.com/develop/specifications/streaming-protocol-drm
- ACG Guide: https://webostv.developer.lge.com/develop/guides/acg-guide
- Web App Types and App Resources pages from the same official documentation set.

## Status legend

| Status | Meaning |
|---|---|
| Source-verified | Confirmed from official LG docs or local source. |
| Implemented | Used by the Phase 1 code, but not yet run on the physical TV. |
| Unverified | Plausible or documented in general, but not proven on this webOS 4.1 TV. |
| Excluded | Deliberately not used in Phase 1. |

## Matrix

| Capability | Official / source fact | Phase 1 decision | Status |
|---|---|---|---|
| Packaged basic web app | `appinfo.json` `type: "web"`, main document in the package. Package apps run from `file://`. | New app uses `type: "web"`, `main: "index.html"`. | Source-verified; Implemented |
| Mandatory metadata | `id`, `title`, `type`, `main`, `icon`, `version` are required. Version is three integers. | `0.1.0`, id `com.luma.companion2`, icon present. | Source-verified; Implemented |
| App lifecycle states | `Not Launched`, `Launched`, `Suspended`. Events include `webOSLaunch`, `webOSRelaunch`, `visibilityChange`. | Dashboard listens to launch/relaunch, `visibilitychange`, `webkitvisibilitychange`, `pageshow`, `pagehide`. | Source-verified; Implemented |
| System bridge | webOS 4.x exposes `PalmSystem`; webOS 5.0+ exposes `webOSSystem` plus `PalmSystem`. | Detection prefers whichever object exists and degrades to `unavailable`. | Source-verified; Implemented |
| `webOS.fetchAppInfo` | webOSTV.js can read packaged app metadata. | Used with `fetchAppRootPath` when available; fallback values otherwise. | Documented; Implemented defensively; TV runtime unverified |
| Luna service request | `webOS.service.request` is the official webOSTV.js bridge. TV device info uses `luna://com.webos.service.tv.systemproperty`, `getSystemInfo`. | One-shot read of `modelName`, `sdkVersion`, `firmwareVersion`; cancel handle retained. | Source-verified; Implemented defensively; TV response unverified |
| Device model / SDK display | `getSystemInfo` returns the requested keys when permitted. | Shown in Platform Information; `unavailable` on denial. | Implemented; TV response unverified |
| Magic Remote key input | Standard DOM key events are used by TV remote input. Arrow keys and OK map to `keyCode` 37–40 and 13/32. | Focus navigation and OK activation implemented. | Implemented; physical remote unverified |
| Magic Remote pointer | Pointer/motion APIs are separate from ordinary DOM input. `sensor2` motion requires webOS 24 or newer. | Not implemented. CLI SDK `10.3.2` does not prove webOS 24 or newer. | Excluded; source-verified limitation |
| Back button | Official back-button guide exists. | No navigation stack in Phase 1, so no custom back handler is registered. | Excluded for Phase 1 |
| Cookies | Packaged `file://` apps do not support cookies; hosted HTTP/HTTPS apps do. | No cookies used. | Source-verified; Excluded |
| Local storage | Not guaranteed under every packaged runtime; can throw on access. | Wrapped in try/catch; only two aggregate counters stored. | Implemented defensively; TV runtime unverified |
| CSS/custom properties | Official LG table lists Chromium 108/120/132 for TV 24/25/26; the legacy user-agent sample shows Chrome 53. | Code keeps the older Chromium 53 syntax baseline and avoids CSS Grid, flex `gap` and `inset`. | Source-verified; Implemented; exact TV engine unverified |
| 1920x1080 layout | App resolution reference exists in official docs. | Fixed 1080-height dashboard with TV-safe padding and type sizes. | Source-verified; physical rendering unverified |
| Fetch/XHR to LAN | Browser engine has networking APIs, but `file://` origin, CORS and LG policy are not proven for arbitrary LAN hosts. | No network request in Phase 1. | Unverified; Excluded |
| WebSocket client | WebSocket API exists in Chromium, but outbound connections to a phone endpoint were not tested in Phase 1. | `js/remote-session.js` opens `ws://<ip>:<port>/luma/companion/v1` only while foregrounded. | Implemented; live TV-to-phone handshake pending |
| WebSocket server / TCP listener | A packaged browser app cannot run the deprecated Node `http` listener on `8081`. | Excluded forever for this app shape. | Excluded |
| UDP beacon | A packaged browser app cannot bind/broadcast UDP `50550` like the deprecated Node service. | Excluded forever for this app shape. | Excluded |
| JS service / background work | Legacy companion used a registered JS service. Phase 1 is foreground only. | No `services` field, no service package, no background timer after hide. | Excluded |
| HLS / DASH playback | Official streaming/DRM specification pages exist; support varies by platform and content protection. | No HLS or DASH player is added in Phase 3. | Unverified; Excluded |
| DRM | DRM messages and protected playback are platform-specific. | No DRM code; the browser frame does not promise protected playback. | Unverified; Excluded |
| iframe navigation / external URL | No verified safe path from a packaged app to arbitrary video sites. Some sites refuse framing. | Phase 3 uses a constrained in-app `<iframe>` with validated absolute http(s) URLs, sandbox flags and no page-content reads. No system browser launch. | Implemented; physical frame rendering unverified |
| System UI visibility / screensaver | Official guides exist. | Not called; app must not hide system UI or block screensaver behaviour. | Excluded for Phase 1 |
| App launch / deep links | Android side already resolves real launch points through SSAP. Browser-side launch is a separate unverified path. | Not used by TV app in Phase 1. | Android source-verified; TV side unverified |
| Multi-sound / mediaOption | Official guides exist for newer use cases. | Not used. | Unverified; Excluded |
| CLI packaging | `ares-package`, `ares-install`, `ares-launch`, `ares-inspect`, `ares-server` are the official tools. | `@webos-tools/cli` 3.2.6 installed; package, install, launch and running-state checks executed. | Source-verified; physically executed |
| ACG | Unsupported on webOS TV 25 or lower; required from webOS TV 27. `getSystemInfo` maps to `systemconfig.query`. | Final package declares `requiredACG: ["systemconfig.query"]` for forward compatibility. | Source-verified; implemented |
| ADB automation | Not part of webOS CLI flow. | Not used. | Excluded unless separately requested |

## Consequences for Phase 1

1. The dashboard stays inside officially documented browser surfaces: HTML, CSS, DOM events, optional webOSTV.js helpers and a one-shot Luna read.
2. Anything requiring Node modules, listening sockets, background execution, cookies, arbitrary CORS, DRM or motion sensors is excluded.
3. `unavailable` and `unverified` labels are intentional. They prevent the UI from claiming capabilities that have not been proven on the physical TV.
4. Physical installation, launch and live process state are now verified. Visual rendering, Luna permission inside the app and Magic Remote behaviour still require capture before any Phase 2 feature is enabled.

## Physical verification result

- CLI `ares-device -d lumatv -i` reported model `55UR7500PSC`, SDK `10.3.2`, firmware `33.31.75`, board `K8LPN_DVB_AJ`.
- The earlier `webOS 4.1.0` assumption is not confirmed by the device report and must not be treated as device fact.
- `com.luma.companion2` was absent from the installed list before packaging and present after installation.
- `ares-launch` launched the app; `ares-launch --running` and the resource monitor confirmed a live process.
- No screenshot tool was available on the TV, so visual rendering remains user-confirmed only.
- The Phase 2 client was repackaged with the corrected phone IP `192.168.1.3`, reinstalled and launched again; PID `15834` was observed as a live process with about 38 MB memory.
