# Phase 2 — Remote integration implementation

**Date:** 2026-11-25  
**Status:** Final `0.2.2` package built, installed on `lumatv`, runtime-verified and committed. No cloud build was used. The phone-to-TV remote handshake remains a manual pending test.

This is the implementation that follows `06-phase2-integration-audit.md`. It stays additive: the Chromium engine, browser UI, tabs, navigation, address bar, menus, bookmarks/history, downloads, GPU rendering, NDL video, Magic Remote and webOS integration are unchanged.

---

## 1. Backup

Created before implementation:

```text
C:\Users\janak\Downloads\WebOS-Chromium-main\WebOS-Chromium-main.backup-phase2-20261125-114331
```

It contains the pre-implementation `ext/luma/**` (22 files), the pre-implementation Android `host/**` folder, and the git `HEAD` copy of `Envelope.kt`.

---

## 2. Files changed

### Android Host — `tvremote2/src/main/java/com/luma/tvremote2/host/`

| File | Change |
|---|---|
| `Envelope.kt` | Added contract types `ping` and `pong`. |
| `CompanionServer.kt` | Accepts producer `luma.chromium` in addition to `com.luma.companion2`; stores advertised capabilities; capability frame is producer-specific; adds generic `sendCommand(action,args,timeoutMs)`; handles `ping`/`pong`; keeps `app.focus` reserved. |
| `HostViewModel.kt` | Keeps and displays the approved producer name. No screen layout was redesigned. |

The existing phone UI still uses only `diagnostics.refresh`, `browser.open` and `browser.close`. Those action names are aliased by the extension, so the Companion 2 flow is unchanged. The new producer can also receive the broader allow-listed action set from a future remote-control UI.

### Luma extension — `source/package/ext/luma/`

New files:

```text
shared/contract.js          versioned envelope validator, 64 KB frame limit
remote/authentication.js    producer luma.chromium, capability list, telemetry rejection
remote/connection.js        outbound WebSocket, handshake, keepalive, backoff, command ack
```

Modified files:

```text
manifest.json               version 0.2.0, description updated; no new permissions
worker.js                   imported new modules, action dispatcher, remote-control messages
shared/storage.js           luma.remote endpoint persistence
options.html / options.js   phone IPv4/port, Connect/Disconnect, local status only
```

No existing Chromium source outside `ext/luma/**` was changed in Phase 2. `source/package/run.sh` still contains only the A1 additive opt-in branch; no Phase 2 edit was made to it.

---

## 3. Implemented capability

| Area | Implemented actions | Source used |
|---|---|---|
| Session | Outbound `ws://<phone-ip>:8787/luma/companion/v1`, v1 hello/capability, explicit phone approval, 1/2/4/8/16 s backoff, 20 s ping keepalive | `remote/connection.js` |
| Navigation | `browser.open`, `browser.close`, `navigation.open`, `navigation.close`, `back`, `forward`, `reload`, `activate`, `next`, `previous` | `chrome.tabs` in `worker.js` |
| Media | `play`, `pause`, `toggle`, `seek`, `volume`, `mute`, `probe` | existing `content/bridge.js` |
| Diagnostics | `diagnostics.refresh` returns local `hw.json` state and tab count; no URLs or page content | `worker.js` |
| Security | Action allow-list, capability check on both sides, reserved `app.focus`, telemetry rejected, URL restricted to `http`/`https`, 64 KB frames, 20 messages/10 s, 20 commands/10 s | both Host and extension |

### Not implemented in this step

- Air Mouse, touchpad, click, double click, right click, scroll and phone keyboard — these need `chrome.debugger`/CDP or DOM fallback and a runtime probe on this TV build. No `debugger` permission was added.
- Fullscreen — no reliable extension-only API; Magic Remote and launcher flags remain unchanged.
- `chrome.scripting`, native messaging, Luna/`PalmSystem`, TV listener, cloud relay — none used.
- No telemetry is sent. `telemetry_enabled:true` is rejected; `telemetry_batch` is ACK-rejected.

### Manifest note

Chrome's official MV3 WebSocket example runs from a service worker with no `host_permissions`. The Luma manifest therefore keeps only:

```json
"permissions": ["storage", "tabs"]
```

The audit's earlier host-permission assumption was corrected in `06-phase2-integration-audit.md` after checking the official documentation.

---

## 4. Verification completed

| Check | Result |
|---|---|
| JS syntax (`node --check`) | 12 files checked, 0 failures |
| Manifest JSON parse | OK, final version `0.2.2`, permissions `storage,tabs`, `host_permissions=none` |
| Android Kotlin compile | `.\gradlew.bat :tvremote2:compileDebugKotlin --offline --console=plain` → **BUILD SUCCESSFUL** |
| Android APK build | `.\gradlew.bat :tvremote2:assembleDebug --offline --console=plain` → **BUILD SUCCESSFUL**; `tvremote2-debug.apk` (12,782,012 bytes) installed on the phone as `com.luma.tvremote2.debug` v3.0.0 |
| New Luma files vs backup | 3 (`contract.js`, `authentication.js`, `connection.js`) |
| Modified Luma files vs backup | 5 (`manifest.json`, `worker.js`, `shared/storage.js`, `options.html`, `options.js`) |
| `run.sh` vs A1 backup | only the original A1 additive insertions; no Phase 2 change |
| Forbidden API scan | `connectNative`, `sendNativeMessage`, `nativeMessaging`, `chrome.debugger`, `chrome.scripting`, `host_permissions`, `PalmSystem`, `webOS.service.request`, `luna-send` → 0 hits |
| Telemetry scan | `telemetry_batch` appears only in validator/rejection; no send path |

### TV runtime verification

The official v1.1.1 IPK was repackaged locally (no cloud build) with only `bin/run.sh` + `ext/luma/**`; browser binaries, libraries, `appinfo.json`, control version and TV Tools were unchanged. Final IPK SHA256 `f6f8f97a4ac7ed75731959d8fceef6f8824375ecef230d009120562aa04ae1a8`.

| Check | Result |
|---|---|
| Default launch | `--load-extension=.../ext/tvtools` only; Luma not loaded |
| Opt-in launch | `--load-extension=.../ext/tvtools,.../ext/luma`; manifest `0.2.2`, permissions `storage,tabs` |
| Installed Luma files | 25 package files + generated `hw.json`; no `probe.json` and no `remote/input.js` |
| Extension errors | none in the browser log |
| HTML5 video | Playback confirmed successfully by manual TV test |

Two candidate video URLs returned `HTTP 403` from the TV network (`commondatastorage.googleapis.com` and `w3schools`), so the first playback attempt failed for network reasons, not a browser or extension bug. Working URLs confirmed `HTTP 200`: `https://media.w3.org/2010/05/sintel/trailer.mp4` and `https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4`.

### CDP probe (temporary, now removed)

A temporary `0.2.1` diagnostic build added `debugger` permission, `remote/input.js` and a local probe UI. It only attempted `getTargets` → `attach` → `Runtime.evaluate(1+1)` → `detach`; it never dispatched `Input.*` events and never enabled Air Mouse or phone keyboard. The probe result could not be read back from the TV profile (permission-restricted), so no CDP input feature was implemented. All probe artifacts were removed from the final `0.2.2` package; `debugger` permission is gone.

### Phone Host status

The new Host-capable app is installed as `com.luma.tvremote2.debug` v3.0.0 alongside the untouched older `com.luma.tvremote2` v2.0.0. The phone was observed in the direct Remote/Cursor screen connected to `192.168.1.2`; Host mode was not started, port `8787` stayed closed, and no `luma.chromium` hello/capability/approval was captured. The TV-side Luma Settings endpoint and phone approval therefore remain a manual pending test. No claim of a completed end-to-end phone-to-TV remote session is made.

---

## 5. Remaining manual step

1. On the phone, open **Host mode** and tap **Start**; confirm `8787` is listening.
2. On the TV, open Luma Settings → Phone Controls, enter phone IPv4 `192.168.1.3` and port `8787`, then **Connect**.
3. On the phone, approve the pending `luma.chromium` peer and verify the capability action list and command ACKs.
4. Only if Air Mouse/phone keyboard is still wanted, make a separate decision to implement `chrome.debugger`/CDP input with an explicit permission change.
