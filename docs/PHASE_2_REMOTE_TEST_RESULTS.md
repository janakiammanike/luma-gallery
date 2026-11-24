# Luma Companion 2.0 — Phase 2 Remote Test Results

**Date:** 2026-11-24  
**Scope:** remote connection only. Browser view and telemetry remain disabled. No ADB automation was used, and no phone device was attached during this run.

> **2026-11-25 update:** these are historical Phase 2 results. The separate
> `companion-host` APK was later replaced by Host mode inside `tvremote2` 3.0.0.

## Environment

- Workspace: `D:\apps\photos galary app`
- TV device in CLI: `lumatv` at `prisoner@192.168.1.2:9922`
- CLI: `@webos-tools/cli` 3.2.6
- TV report: model `55UR7500PSC`, SDK `10.3.2`, firmware `33.31.75`
- Local Node: `v26.12.1`, npm `11.20.0`
- Gradle: wrapper with AGP 8.13.2, Kotlin 2.2.20
- Android device connected later by `adb devices`: `VO6H4P5PBICUNZY5`, model `RMX3771`
- Phone Wi-Fi IP verified by `adb shell ip -f inet addr show wlan0`: `192.168.1.3/24`

## 1. TV source checks

| Check | Command / method | Result |
|---|---|---|
| JavaScript + metadata syntax | `npm run check` | Pass. Includes `remote-session.js` and `appinfo.json` parse. |
| Envelope validator vectors | `contract-check.js` | Pass, 11/11. |
| Storage guard | `storage-check.js` | Pass, 8/8. |
| DOM and metadata | `dom-meta-check.js` | Pass. 28 referenced IDs present, 0 missing; 10 focusable elements. |
| Remote session behaviour | `remote-session-check.js` with stub WebSocket | Pass, 19/19. |

The remote session harness verified:

- invalid IPv4 and valid IPv4/port handling,
- fixed URL `ws://192.168.1.3:8787/luma/companion/v1`,
- `hello` envelope with no token, key or client-key field,
- 32-hex session id,
- `handshaking` state before approval,
- `capability` acceptance with `telemetry_enabled: false`,
- `diagnostics.refresh` execution with accepted ACK,
- `browser.open` rejection with rejected ACK and no execution,
- invalid JSON rejection counter,
- channel close on suspend and return to idle on manual disconnect.

## 2. Android host checks

| Check | Command / method | Result |
|---|---|---|
| New module compile | `.\gradlew :companion-host:assembleDebug --no-daemon` | Pass. |
| APK produced | `companion-host/build/outputs/apk/debug/companion-host-debug.apk` | Present. |
| Existing remote app unaffected | `.\gradlew :tvremote2:assembleDebug --no-daemon` | Pass. No source file in `tvremote2` was modified. |
| ADB install | `adb -s VO6H4P5PBICUNZY5 install -r companion-host-debug.apk` | Pass. Streamed install succeeded. |
| Installed package check | `adb shell pm list packages com.luma.companionhost` | Pass. Package present. |
| Command button added | `Refresh TV` button in `HostActivity.kt` | Pass. Rebuilt and reinstalled after adding outbound `diagnostics.refresh` and ACK logging. |

The host implements a text-only WebSocket endpoint at `/luma/companion/v1`, one active peer at a time, SHA-1 upgrade acceptance, masked client-frame validation, 64 KB frame cap, pending approval UI, capability after approval, command ACKs, rate limiting and close on repeated invalid frames.

## 3. Physical TV packaging

| Check | Command / method | Result |
|---|---|---|
| Repackage | `npm run package` | Pass. `com.luma.companion2_0.1.0_all.ipk` created. |
| Reinstall | `ares-install -d lumatv <ipk>` | Pass. |
| Launch | `ares-launch -d lumatv com.luma.companion2` | Pass. |
| Running state | `ares-launch -d lumatv --running` | Pass. App listed. |
| Live process | `ares-device -d lumatv -r -id com.luma.companion2` | Pass. PID `15834`, CPU 6.24%, memory about 38 MB. |

## 4. Not verified

| Item | Status |
|---|---|
| TV-to-phone WebSocket upgrade on the packaged TV app | Not verified; no host was running on the phone during CLI verification. |
| Phone approval and capability delivery on real devices | Not verified; pending manual test. |
| Magic Remote text entry into host IP/port fields | Not verified. |
| Visual rendering of the new connection card | Not captured; no TV screenshot tool available. |
| In-app Luna response after Phase 2 repackage | Not captured. |
| Android background behavior | No service is used; host stops when its Activity pauses. |
| ADB automation | Not used. |
| Commit / push | Not performed. |

## Conclusion

The remote connection code is implemented on both sides and passes local validation. The TV app was repackaged, reinstalled, launched and confirmed alive on the physical LG TV. The real TV-to-phone handshake still requires a manual acceptance test with the host app running on the phone. No remote feature should be treated as production-ready until that test passes.
