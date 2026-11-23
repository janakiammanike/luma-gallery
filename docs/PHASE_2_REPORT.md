# Luma TV Remote 2.0 — Migration Report (final)

**Status:** complete and manually verified on the real TV. The new app ships as a separate `:tvremote2` module (`com.luma.tvremote2`, debug `com.luma.tvremote2.debug`). The legacy `tvremote` module remains in the monorepo as a fallback.

**Test device:** LG `55UR7500PSC.ATR`, webOS `4.1.0`, TV `192.168.1.2`, phone `192.168.1.3`. All manual verification was done by the user on the phone; no ADB automation was used for behaviour checks.

## Architecture

| Area | Files | Role |
|---|---|---|
| App shell | `app/MainActivity.kt`, `app/RemoteApp.kt`, `core/Routes.kt` | Material 3 shell, Navigation Compose, five preserved tabs (Remote, Cursor, Keyboard, Apps, More). |
| State | `core/RemoteViewModel.kt`, `core/CalibrationState.kt` | Coroutines + `StateFlow`, one serialised channel for pointer writes, guided calibration state machine. |
| Storage | `data/SecureTvStore.kt` | Android Keystore-backed preferences. Fresh pairing only; old plain-text `TvStore` keys are never imported. Stores host, client key, theme, haptics, air-mouse gain/span/axis signs, shake-to-center and visible button groups. |
| Transport | `platform/webos/LgWebOsPlatform.kt`, `DirectWebOsPlatform.kt`, `WebOsModels.kt`, `WebOsProtocol.kt` | Companion proxy first, direct SSAP fallback second. Same `WebOsPlatform` seam for both. |
| Discovery | `network/CompanionScanner.kt`, `network/TvScanner.kt` | UDP beacon `50550` + `/status` probe, then local port scan fallback. |
| Input | `input/AirMouseController.kt`, `core/Haptics.kt` | Gyroscope with rotation-vector fallback and touchpad fallback; variance-based stillness; shake detection; short vibration feedback for real commands. |
| Features | `feature/remote`, `feature/cursor`, `feature/calibration`, `feature/keyboard`, `feature/apps`, `feature/more`, `feature/cast`, `feature/settings` | Real commands, no placeholder buttons. |

Only the legacy TV Remote module was used as a migration reference. `app/` (gallery) and `tvcompanion/` were not changed. No CSS framework, WebView UI, Firebase or Python was added to the Android app.

## Transport findings

- Companion endpoint: `http://192.168.1.2:8081/status` and `/command`. On this TV the companion is down (`8081` closed); the direct SSAP fallback is the active path.
- Direct SSAP: TLS `3001` (plain `3000` available), self-signed certificate trusted, **no `Origin` header**, `pairingType: PROMPT`, client key from `SecureTvStore`.
- **Root cause of the old failure:** an empty `permissions: []` manifest made commands return `{}`. The manifest now lists non-empty SSAP permissions, including `CONTROL_INPUT_MEDIA_PLAYBACK`.
- Media uses the legacy-verified `media.controls/{play,pause,stop,rewind,fastForward}` endpoints.
- Keyboard uses `com.webos.service.ime/registerRemoteKeyboard` plus Compose state collection; text is inserted through `insertText`.

## Manual verification

| Check | Result |
|---|---|
| `:tvremote2:assembleDebug`, APK installed | Passed |
| Cold start, navigation between all tabs | Passed |
| Manual IP connect to `192.168.1.2`, pairing prompt | Passed |
| Volume / mute on the real TV (legacy proof `9 → 12`) | Passed in the new app |
| Back, Home, Recent, Menu | Passed |
| D-pad arrows and `ENTER` (not a guessed key name) | Passed |
| Apps list and launch, Inputs | Passed |
| Keyboard text into the TV web browser | Passed |
| Search TV, Cast media, Send to TV, screen mirroring | Passed |
| Playback buttons with video playing | Passed |
| Apps-screen **Search TV** using the filter field | Passed |
| Haptics toggle in More → Settings | Passed |
| **Customize buttons** persists its group selection | Passed |
| Air mouse: gyro + touchpad cursor movement | Passed |
| Guided calibration screen (stillness, four directions, four corners, save) | Passed |
| No drift after stopping the phone | Passed |
| Air mouse keeps working after leaving calibration | Passed |
| Recenter (edge bump) and touchpad **Back / OK** buttons | Passed |
| **Find TV automatic scan** on the phone | Not yet manually verified; manual IP connect is verified |

## Honest limitations

- **LG Magic Remote / MRCU is not implemented.** It needs a TV-side Luna service (`sensor2`) available only on webOS 24+, while this TV is webOS 4.1. The recenter controls here reproduce the *behaviour* with SSAP pointer commands.
- Keyboard input works in the TV browser. YouTube's own app ignores remote keyboard injection — this is a TV app limitation, not a pairing failure.
- "Music to TV" opens the TV's own YouTube search; it is not a phone DRM audio handoff.
- Some TV apps draw their own cursor and ignore SSAP pointer input.
- Air-mouse calibration is stored per install; the touchpad and previous settings remain usable throughout the wizard.

## Commit history (ready to push)

```
9ddc078 Add Back and OK buttons beside the Cursor screen touchpad
b5debcf Complete phase 3 features: playback, TV search, haptics and button customization
08352bc Keep air mouse active after leaving calibration and fix recenter pointer pause
68a57be Add guided air-mouse calibration with drift suppression and recenter tools
b63a149 Add calibrated gyroscope air mouse and wire Cursor controls to real TV input
250d32d Use proven app-launch path for TV search and YouTube link casting
19b4478 Resolve optional TV features from real launch points and keep results visible
cf207db Fix D-pad ENTER, keyboard input and wire More controls to real TV commands
3882c08 Add direct SSAP fallback and local network scan when TV companion is unavailable
77cfe36 Add Phase 2A LG companion transport and real remote commands in tvremote2
ed15c79 Add Luma TV Remote 2.0 Phase 1 foundation in separate tvremote2 module
7618514 Delete entire tvremote module source code as requested
68801c8 Remove tvremote module from Gradle settings after code deletion
9ef2cb6 Fix companion SSAP permissions and proxy disconnect detection before tvremote reset
```

The legacy module was removed in `68801c8`/`7618514` per an earlier request, then restored in `ed15c79` so it stays available as a fallback.

## Remaining optional check

Open **Find TV** on the phone and confirm the TV appears automatically. If it does not, manual IP entry remains fully working and the scan logic can be adjusted without touching the verified command path.
