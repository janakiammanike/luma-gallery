# Phase 2A — Migration Report

**Status:** LG transport ported and compiled. Real-TV verification is **pending** because the TV (`192.168.1.2`) was unreachable from both the PC and the phone at the time of testing.

## What was added

| File | Role |
|---|---|
| `network/CompanionScanner.kt` | Ported UDP beacon (50550) listener + `GET /status` probe. Parses `app`, `ip`, `token`, `clientKey`, `uuid`, `model`, `macs`, `ts`. |
| `platform/webos/LgWebOsPlatform.kt` | Companion-proxy transport. Implements `WebOsPlatform` over `POST http://<tv>:8081/command`. |
| `platform/webos/WebOsModels.kt` | Extended interface: pointer, buttons, keyboard, audio, channels, power, apps. |
| `platform/webos/WebOsProtocol.kt` | Corrected SSAP endpoint list from the verified legacy client. |

`tvremote2/build.gradle.kts` now includes `okhttp` from the existing version catalog. No new third-party dependency outside the catalog.

## Command format (verified against legacy source and companion server)

- URI command body: `{"uri":"audio/volumeUp","payload":{},"token":"…","key":"…"}`
- Input frame body: `{"input":"type:button\nname:UP\n\n","token":"…","key":"…"}`
- Companion returns the SSAP payload directly for `uri` commands, and `{"returnValue":true}` for `input` frames.
- `ConnectException` clears the connected flag, so the UI cannot keep claiming "Connected" after the companion dies. This was the root cause of the old "connected but nothing works" bug.

## ViewModel wiring

- `RemoteViewModel` now owns a real `LgWebOsPlatform` backed by `SecureTvStore`.
- `connect(host)` probes `/status`, stores the companion-provided key encrypted in the Keystore, then exposes `Connected · <host>`.
- `discoverCompanion()` fills the IP field from the beacon.
- Real actions wired: Home/Back/Recent/Menu, D-pad, scroll, volume, mute, channels, power, keyboard send/backspace/enter, app list + launch.
- Apps and volume screens now read real state instead of placeholders.
- The misleading `UI preview · LG logic pending` label and the fake "Preview shell" button were removed.

## Verification

| Check | Result |
|---|---|
| `:tvremote2:assembleDebug` | BUILD SUCCESSFUL |
| APK installed on phone | Success (~16.4 MB) |
| Cold start / no crash | Confirmed |
| Connect with empty IP | Shows `Enter the TV IP address first` |
| Connect with `192.168.1.2` while TV offline | Shows `TV companion not reachable at 192.168.1.2:8081`, no crash |
| PC → TV ping | Destination host unreachable |
| Phone (192.168.1.3) → TV ping | 100% packet loss |
| **Real volume change on TV** | **NOT YET TESTED — TV is offline** |

## Important honesty note

No command has been sent to the TV from the new app yet. The previous `9 → 12` volume proof belongs to the **legacy** app. This Phase 2 implementation copies that verified wire format, but parity is only claimed after the TV is back online and the same volume test is repeated here.

## Pending next step

1. Switch on the TV / ensure Luma companion is running.
2. In the new app tap **Find TV** or enter `192.168.1.2`, then **Connect**.
3. Open Remote tab, tap **Vol +**, and confirm the volume actually increases.
4. Check Apps tab lists launchpoints and Keyboard tab sends text.
5. Commit the result.
