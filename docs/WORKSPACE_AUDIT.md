# Workspace Audit — Luma monorepo

**Date:** 2026-11-24  
**Phase:** Luma Companion 2.0 Phase 1  
**Method:** read-only source audit. No existing source/document was modified during the audit.

## Snapshot

At audit start the repository was clean at `89c44dd`. `settings.gradle.kts:23-26` defines three Gradle modules:

| Path | Identity | Role in this phase |
|---|---|---|
| `app/` | `com.luma.gallery` | Luma Photos & Videos gallery; unrelated Android app. Do not touch. |
| `tvremote/` | `com.luma.tvremote` | Legacy remote kept as fallback. Reference only. |
| `tvremote2/` | `com.luma.tvremote2` (debug `.debug`) | Working Android TV Remote 2.0. Reference only. |
| `tvcompanion/` | webOS app `com.luma.tvcompanion` + Node service | Deprecated companion. Reference only; source-verified below. |
| `webui/` | React 19 + Vite + TypeScript | Gallery's LAN web UI (`index.html:10-14`, `package.json:12-21`). Not a TV companion app. |
| `docs/` | Markdown notes | Existing verification/protocol reports listed below. |

`tvcompanion/` and `webui/` are plain directories, not Gradle modules. The new companion is intentionally created as an independent sibling directory `luma-companion-2/`, not added to `settings.gradle.kts`.

## Existing docs

| File | Purpose |
|---|---|
| `developer-verification.md` | Google developer-verification timeline and Luma checklist. |
| `PHASE_1_ARCHITECTURE.md` | Original Remote 2.0 architecture. |
| `PHASE_1_MIGRATION_REPORT.md` | Original Remote 2.0 migration report. |
| `PHASE_1_SOURCE_INVENTORY.md` | Original Remote 2.0 source inventory. |
| `PHASE_2_REPORT.md` | Final verified transport/feature report for `tvremote2`. |
| `tvremote-protocol.md` | Detailed legacy webOS protocol notes. |
| `UI_UX_REFINEMENT_REPORT.md` | Phase 3A UI polish and release artifact report. |

## Android Remote 2.0 findings (source-verified)

### App shell and state

- `tvremote2/src/main/java/com/luma/tvremote2/app/MainActivity.kt`, `app/RemoteApp.kt` and `core/Routes.kt` provide the Compose shell and five-tab navigation.
- `core/RemoteViewModel.kt` holds connection, pairing, command and UI state with coroutines/`StateFlow`; `core/CalibrationState.kt` holds the guided air-mouse state machine. This is corroborated by `docs/PHASE_2_REPORT.md:9-17`.
- `designsystem/LumaTokens.kt`, `LumaColors.kt`, `LumaTheme.kt` and `LumaComponents.kt` define the purple Luma visual identity used by the new TV dashboard as a design reference only.

### Capability interface

`platform/webos/WebOsModels.kt:20-64` defines `WebOsPlatform`: connect/disconnect, pointer buttons/move/click/scroll, keyboard type/delete/Enter, app list/launch, optional TV search/media/mirroring, volume/channel/power and media playback.

### Secure storage

`data/SecureTvStore.kt:18-32` uses `EncryptedSharedPreferences` with an AES256-GCM `MasterKey` held by Android Keystore. Stored keys (`SecureTvStore.kt:34-151`):

- `last_host`
- per-host pairing key `key:<host>`
- `dark_theme`, `haptics`
- air mouse: enabled, sensitivity, invert X/Y, gain X/Y, span X/Y, axis sign X/Y, shake-to-center
- `visible_groups`

Fresh pairing is intentional; old plain-text `TvStore` keys are never imported (`SecureTvStore.kt:8-16`).

### Transport

- `platform/webos/LgWebOsPlatform.kt:32-41`: phone POSTs commands to the TV companion proxy at `http://<tv>:8081/command`; the companion executes them against TV-local SSAP.
- `LgWebOsPlatform.kt:59-85`: connect probes `/status`, prefers the stored client key, saves host/key and marks connected.
- `LgWebOsPlatform.kt:92-154`: request body is `{uri, payload, token, key}`; input body is `{input, token, key}`; HTTP failures/invalid JSON fail the command.
- `platform/webos/DirectWebOsPlatform.kt:22-31`: direct SSAP uses `wss://host:3001` first, `ws://host:3000` fallback, self-signed TLS trusted, no `Origin` header.
- `DirectWebOsPlatform.kt:66-110`: hello → `system/getSystemInfo` best effort → register → open pointer input socket → optional remote keyboard subscription.
- `DirectWebOsPlatform.kt:186-240`: registration uses a non-empty permissions manifest, `pairingType: PROMPT`, signed attempt first, unsigned fallback, and `client-key`.
- `DirectWebOsPlatform.kt:330-365`: SSAP request frame is `{id, type:"request", uri:"ssap://…", payload}`; response payload must contain `returnValue`, `subscribed` or `subscription`.
- `DirectWebOsPlatform.kt:388-476`: pointer frames and all keyboard/media/app commands are implemented through this interface.
- `WebOsProtocol.kt:10-41`: all ports, endpoints and newline-delimited input frame templates are centralized.
- **Room database:** exact search for `androidx.room`, `@Entity`, `@Dao` and `RoomDatabase` in `*.kt` returned **no matches**. No Room persistence exists in this repository.

### Discovery

- `network/CompanionScanner.kt:17-26,35-95`: listens for UDP beacon on `50550`, probes `http://<host>:8081/status`, validates `app == "com.luma.tvcompanion"`, and parses `ip`, `version`, `token`, `clientKey`, `uuid`, `model`, `macs`, `ts`.
- `network/TvScanner.kt:18-25`: fallback chain is companion UDP beacon, then local `/24` port probe for `3000/3001/8081`.

## Deprecated companion findings (source-verified)

- `tvcompanion/appinfo.json:1-16`: app `com.luma.tvcompanion`, version `1.0.0`, `"type": "web"`, main `index.html`, with a JS service `com.luma.tvcompanion.service`.
- `tvcompanion/services/luma-companion/services.json:1-9` registers that service.
- `tvcompanion/services/luma-companion/index.js:13-17` requires Node modules `dgram`, `os`, `http`, `crypto` and a local `wsclient`.
- `index.js:22-25` uses UDP `50550`, HTTP `8081`, two-second broadcasts, broadcast address `255.255.255.255`.
- `index.js:32-44` carries the SSAP permission manifest; the comment at `27-31` records the root cause that an empty permissions list returned `{}`.
- `index.js:117-135` builds `/status` payloads with `app`, `protocol`, `version`, `ip`, ports, `uuid`, `model`, `macs`, token and optional `clientKey`.
- `index.js:307-344` handles `/command`: validates optional token, accepts either `{input: string}` or `{uri, payload}`, and forwards through the service's own SSAP/pointer connections.
- `index.js:346-370` broadcasts the beacon every two seconds.
- `index.js:372-393` runs an HTTP server on `0.0.0.0:8081` with `Access-Control-Allow-Origin: *`, routes `/command`, `/health`, `/status` and `/`.
- `wsclient.js:20-137` is a tiny RFC6455 client from the Node service to TV-local `127.0.0.1:3000`.

**Conclusion:** the old companion is a TV-side **Node service with listening sockets**. A plain browser-packaged webOS app cannot copy this architecture.

## Connection roles

| Actor | Role |
|---|---|
| Android phone | Active client. Opens WebSocket to TV SSAP `3000/3001` or HTTP POST to companion `8081`. Listens for UDP beacon `50550` only during discovery. |
| TV SSAP server | Listens on local/TV LAN ports `3000` (plain) and `3001` (TLS). |
| Deprecated TV companion service | Listens on `8081`, broadcasts UDP `50550`, and internally connects to `127.0.0.1:3000`. |
| New browser companion app | Phase 1: no transport, no listener, no service. Future: outbound client only until proven otherwise. |

WebSocket exists only in the Android client/deprecated Node service. The new app must not assume the TV is a WebSocket server for *companion* traffic; SSAP `3000/3001` are system services, not a general app server.

## ID conflict check

- Source search found no `com.luma.companion2` reference anywhere in the repository.
- Existing TV companion ID is `com.luma.tvcompanion` (`appinfo.json:2`, `packageinfo.json:2-3`, service IDs in `services.json:4`).
- Proposed ID `com.luma.companion2` is therefore free within this repository and does not collide with the deprecated app.
- On-TV installed-app conflict could not be checked because `ares-install` is not available in this environment.

## Build tools

At audit start, `ares-package`, `ares-install`, `ares-launch` and `ares-device` were not on `PATH`. During the later physical TV verification step, official `@webos-tools/cli` 3.2.6 was installed globally; IPK generation, install, launch and running-state checks were then executed successfully.

## Boundaries respected

No existing Android module, deprecated companion file, gallery file or Gradle config was changed. The new work is confined to `luma-companion-2/` and new files in `docs/`.
