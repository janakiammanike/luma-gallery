# Luma Companion 2.0 — Architecture

**Date:** 2026-11-25  
**Status:** Phase 1 foundation, Phase 2 remote connection and Phase 3 in-app browser view implemented. Telemetry remains disabled and is queued as Phase 4.

## Goal

Create a separate, foreground-only webOS TV application that can host Luma companion features without inheriting the deprecated Node-service design. Phase 1 delivered a basic dashboard. Phase 2 added an opt-in LAN remote session. Phase 3 adds a constrained in-app browser view. Per user direction, the Android endpoint now lives inside the existing `tvremote2` app as version 3.0.0 instead of a separate APK.

## Boundaries

Included:

- independent app id `com.luma.companion2`,
- plain HTML5, CSS3 and ES5-style JavaScript,
- premium dark Luma dashboard,
- lifecycle cleanup,
- strict message validation on both sides,
- safe local counters,
- read-only platform diagnostics,
- outbound WebSocket client opened only while foregrounded,
- manual IPv4/port entry and explicit phone approval,
- allow-listed `diagnostics.refresh`, `browser.open` and `browser.close` commands with ACK,
- in-app browser frame with absolute http(s) URL validation and no page-content reads,
- bounded reconnect and repeated-invalid close,
- opt-in Host mode inside `tvremote2` 3.0.0 (`com.luma.tvremote2`).

Excluded:

- system-browser launch, proxy, HLS/DASH/DRM player,
- TCP listener, UDP beacon, HTTP server or SSAP calls on the TV,
- token import, token storage and legacy `TvStore` client key,
- JS service, Android background service or background telemetry,
- cloud analytics, Firebase, remote logging,
- changes to `tvremote`, `tvcompanion`, gallery `app/` or `webui/`.

`tvremote2` is now changed deliberately: Host mode is added and its version is raised to 3.0.0. Its existing SSAP remote path is preserved.

## File layout

```text
luma-companion-2/
  appinfo.json               packaged web app metadata
  index.html                 dashboard structure
  css/styles.css             Chromium-53-safe flexbox layout and Luma theme
  js/remote-contract.js      versioned envelope validator; no transport
  js/storage.js              guarded local counter wrapper
  js/diagnostics.js          user agent, bridge, app info and one-shot system info
  js/remote-session.js       outbound WebSocket client, handshake and command ACK
  js/browser-view.js         validated in-app iframe; no page-content reads
  js/app.js                  lifecycle, rendering, focus and cleanup controller
  assets/splash.png          splash image
  icon.png                   small app icon
  largeIcon.png              large app icon
  package.json               static check and CLI helper scripts
  README.md                  local usage notes
  .gitignore                 generated package artefacts

tvremote2/                     existing Android app, now version 3.0.0
  host/CompanionServer.kt      minimal WebSocket server, validation and ACK
  host/Envelope.kt             shared v1 envelope rules in Kotlin
  host/HostViewModel.kt        foreground-only server lifecycle and UI state
  feature/host/HostScreen.kt   Compose UI: Start/Stop, Approve/Reject, commands
```

The former `companion-host/` module was removed from `settings.gradle.kts` and deleted. No second APK is produced.

## Module responsibilities

### `appinfo.json`

Declares the app as a basic web app. `handlesRelaunch: false` is acceptable because the controller still listens for relaunch and re-activates correctly if the event arrives. No `services` field is present.

### `js/remote-contract.js`

Single validation boundary for all future remote messages. It enforces:

- envelope version `v === 1`,
- known message types,
- non-empty bounded `id`,
- finite `ts`,
- object `payload`,
- 64 KB raw frame limit,
- counters for valid, invalid, oversized and unknown-type messages.

It never processes a command and never opens a socket.

### `js/storage.js`

Wraps `localStorage` in try/catch and stores only:

```json
{ "launches": 0, "storageFailures": 0 }
```

Malformed stored JSON is replaced by defaults. Read and write failures are reported to the controller without throwing.

### `js/diagnostics.js`

Collects:

- `navigator.userAgent` and detected Chromium version,
- `PalmSystem` or `webOSSystem` presence,
- app metadata via `webOS.fetchAppInfo` when available,
- TV model, SDK and firmware via one-shot `webOS.service.request` when available.

Every bridge call is optional. Missing or denied data becomes `unavailable`, never a guessed value.

### `js/remote-session.js`

Owns the only outbound network edge. It validates the entered IPv4 address and port, opens
`ws://<ip>:<port>/luma/companion/v1`, sends `hello`, and waits for `capability` before
reporting `connected`. Every received frame goes through `remote-contract.js`.
`diagnostics.refresh`, `browser.open` and `browser.close` can execute; `app.focus`
receives a rejected ACK. The module closes the channel on hide, on manual disconnect and
after repeated invalid frames. Reconnect uses fixed bounded backoff. No token, key or
telemetry is stored. It also exposes `setAckHandler`, `sendMessage`, `sessionId` and
`capability` for the later telemetry module.

### `js/browser-view.js`

Owns the in-app frame. It validates an absolute http(s) URL locally, rejects credentials
and control characters, applies `sandbox="allow-scripts allow-forms"`, and never reads the
framed document. `browser.close` and app deactivation reset the frame to `about:blank`.
The UI reports `Loading`, `Loaded`, `Rejected` or `Unavailable` without promising that a
third-party site permits framing.

### `tvremote2` Host mode

Host mode is a Compose destination inside the existing Android app, reachable from the
Connect screen and the More menu. `HostViewModel` owns one `CompanionServer`; the server is
stopped when the screen is paused or disposed, so there is no Android service, receiver or
background restart. It accepts one TV peer at a time, shows a pending approval, sends
`capability` only after the user approves, and answers allow-listed commands. The WebSocket
implementation is minimal, text-only, mask-checking and capped at 64 KB. It never logs
payloads, addresses or identifiers.

### `js/app.js`

Owns application state and lifecycle:

1. `init()` → attach events → `activate()`.
2. `activate()` → increment launch counter → start 30-second local render timer → run platform probe → render.
3. `deactivate()` → clear timers → cancel probe → render paused state.
4. Probe callbacks use a generation number, so stale callbacks cannot overwrite fresh state.
5. Arrow keys move focus between `[data-focusable="true"]` elements; Enter/Space activates the focused control.

## UI sections

| Section | Current behaviour |
|---|---|
| Browser View | URL field plus Open/Close buttons and an in-app frame. Also driven by approved `browser.open` / `browser.close` commands. No page data is read. |
| Connection Status | Manual IPv4/port form, Connect/Disconnect, session id prefix, handshake state, retry attempt and valid/rejected counters. |
| Companion Status | Reports foreground-only, active session or suspended. States that no TV listener/beacon exists. |
| Platform Information | Shows app id, version, engine, system bridge, TV model and SDK with honest fallbacks. |
| Diagnostics Status | Shows lifecycle, network, storage, protocol handler, telemetry and last update time. Refresh reruns local probes and is also callable by an approved `diagnostics.refresh` command. |

## Lifecycle state machine

```text
Not Launched
   |
   | webOSLaunch / pageshow / DOM ready
   v
Launched (active)
   |                     ^
   | hidden/pagehide     | visible/pageshow/relaunch
   v                     |
Suspended (inactive) ----+
```

Only the active state runs timers and platform probes. The suspended state performs no network or platform work.

## Data flow

```text
TV runtime
  ├─ userAgent ----------> diagnostics.js ---> app.js ---> DOM
  ├─ PalmSystem/webOSSystem --> diagnostics.js ---> app.js ---> DOM
  ├─ webOS.fetchAppInfo --> diagnostics.js ---> app.js ---> DOM
  └─ Luna getSystemInfo --> diagnostics.js ---> app.js ---> DOM

localStorage <--- storage.js <--- app.js
remote-contract.js ---> validation counters ---> app.js ---> DOM

remote-session.js -- ws://phone:8787/luma/companion/v1 --> tvremote2 Host mode
     hello -------------------------------> pending approval on phone
     capability <------------------------- after explicit approval
     command + request_id ----------------> diagnostics.refresh / browser.open / browser.close
     ack(ref_id) <------------------------- accepted or rejected
browser-view.js -- validated URL --------> local iframe (no app-content reads)
```

The only network edge is outbound from the TV while foregrounded. The TV never listens.

## Design language

The dashboard follows the existing Luma visual identity from `docs/UI_UX_REFINEMENT_REPORT.md`: dark background, purple accent, rounded cards, generous TV spacing and large type. The layout is fixed for 1920x1080 and uses flexbox because the target engine is Chromium 53.

## Future extension points

| Future feature | Extension point | Required gate |
|---|---|---|
| Remote connection | Implemented in `js/remote-session.js` and `tvremote2` Host mode behind the contract. | Physical TV-to-phone handshake still needs manual acceptance. |
| Browser view | Implemented in `js/browser-view.js` as a constrained in-app frame. | Physical frame rendering test; arbitrary-site framing and DRM are not promised. |
| Telemetry | Emitter that produces schema v1 batches only after explicit opt-in. ACK handler hooks are prepared in `remote-session.js`. | Privacy review + Android Room receiver; Phase 4. |
| Back stack | Custom back handler only after multiple views exist. | Official back-button guide test. |
| Motion remote | Only for webOS 24+; the CLI-reported SDK `10.3.2` is not treated as supporting it. | New target device or explicit capability test. |

## Verification posture

Phase 1 was statically checked and then packaged, installed, launched and confirmed running on the physical TV. Phase 2 added local stub tests for the TV session logic, a successful Android host build, and a repackaged/reinstalled TV app confirmed running. Phase 3 adds `browser-view.js` to the static check, updates the stub harness to verify `browser.open` and the continued rejection of `app.focus`, and builds `tvremote2` 3.0.0 successfully. The TV-to-phone handshake, browser frame rendering, Magic Remote input and in-app Luna response are still unverified by capture. No claim of full end-to-end remote or browser success is made.
