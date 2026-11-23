# Phase 1 — Source Inventory

**Date:** 2026-11-23  
**Reference module:** legacy `tvremote/` at commit `9ef2cb6` (restored into the working tree before migration).  
**New module:** `tvremote2/`, package `com.luma.tvremote2`.  
**Rule:** only the legacy TV Remote module was used as a migration reference. `app/` (Luma Gallery) and `tvcompanion/` were not modified.

## Inspector's note

Files were read from disk and from git history; behaviour was confirmed by running the old app against the real TV companion before the reset. The "connect but nothing works" symptom was traced to the companion registering with an empty SSAP permission list, not to the phone UI. That fix is already committed (`9ef2cb6`) and remains in `tvcompanion/`.

## Compose screens inspected (legacy)

| File | Role |
|---|---|
| `ui/RemoteApp.kt` | Root shell. `Scaffold`, `TopAppBar`, `NavigationBar`, `More` dialog, `SettingsSheet`, `MultitaskingDialog`. Navigation was a `mutableStateOf<String>` — not Navigation Compose. |
| `ui/ConnectScreen.kt` | IP input, connect, scan network, TV app / Bluetooth / mDNS discovery, favourites, Wi‑Fi status, wake-on-LAN. |
| `ui/RemoteTab.kt` | System buttons, D-pad, scroll, volume/channels, playback, power, button customisation. |
| `ui/CursorTab.kt` | Air mouse toggle, sensitivity, invert X/Y, recentre, touchpad, click, scroll. |
| `ui/KeyboardTab.kt` | Text field, send, backspace, enter. |
| `ui/AppsTab.kt` | Launchpoint list, filter, reload, search TV. |
| `ui/NavBar.kt` | Shared bottom navigation helpers. |
| `ui/InputsTab.kt` | Input selection, sound output, volume. |
| `ui/SearchTab.kt` | TV-side content search, local app filtering, recent searches. |
| `ui/MusicTab.kt` | Send music apps to TV, now-playing state. |
| `ui/CastTab.kt` | DLNA media cast, screen mirroring entry. |
| `ui/SettingsSheet.kt` | Dark theme, haptics, air-mouse preferences. |
| `ui/MultitaskingDialog.kt` | Recent apps, close app. |
| `ui/Haptics.kt` | Vibration helper. |
| `ui/AppIconLoader.kt` | Launchpoint icon loading. |
| `ui/Theme.kt` | `TVRemoteTheme(darkTheme, content)` — exact colour source. |

## Core / platform files inspected

| File | What it actually does |
|---|---|
| `ui/RemoteViewModel.kt` | Single `AndroidViewModel` (~large). Owns `RemoteUiState` (40+ fields), `TvStore`, `WebOsClient`, `MediaServer`, `DlnaClient`, `AirMouseController`. Connect order: companion proxy → direct SSAP → subnet scan. Coalesces air-mouse moves via `AtomicReference` + `AtomicBoolean`. |
| `net/WebOsClient.kt` | 690 lines. Two WebSockets: main SSAP + input socket. `wss://host:3001` then `ws://host:3000`. Self-signed TLS trusted, no `Origin` header. Register handshake with permission manifest and per-host `client-key`. Proxy path: `GET :8081/status`, `POST :8081/command`. Commands: buttons, move/click/scroll, volume, channels, power, apps, IME text/enter, audio status, input select, media, toasts, foreground app, search deep links. |
| `net/CompanionScanner.kt` | UDP beacon on `50550`, `GET :8081/status`. Parses `ip`, `ports`, `uuid`, `model`, `macs`, `token`, `clientKey`. `listen(timeout)` and `probe(host)`. |
| `data/TvStore.kt` | Plain `SharedPreferences("luma_tv_remote")`. Hosts, last host, **pairing keys in plain text** (`key:$host`), MACs, favourites, layouts, recents, theme, haptics, air-mouse settings. |
| `input/AirMouseController.kt` | `SensorManager` per-sample relative deltas. Prefers `TYPE_GAME_ROTATION_VECTOR`, falls back `TYPE_ROTATION_VECTOR`. Roll ignored, display rotation applied. Constants: `SMOOTHING=0.35f`, `DEADZONE=0.0018f`, `PIXELS_PER_RADIAN=420f`, `MAX_STEP=48`, `MAX_ANGLE=0.25f`, `MIN_INTERVAL_NS=12_000_000`. |
| `net/LanScanner.kt`, `MdnsScanner.kt`, `ArpTvScanner.kt`, `BluetoothTvScanner.kt`, `WifiStatus.kt`, `WakeOnLan.kt` | Discovery stack: subnet, mDNS, ARP-from-BLE-MAC, BLE proximity, Wi‑Fi info, WoL magic packet. |
| `net/DlnaClient.kt`, `MediaServer.kt` | Real cast path: `SetAVTransportURI` + `Play`; local HTTP server for media URLs. |
| `media/NowPlayingListener.kt` | Notification listener for music state. |
| `res/values/colors.xml` | Brand colours `#0B1020`, `#8B5CF6`, `#22D3EE`. |

## Design tokens carried into 2.0

```kotlin
primary        dark #A78BFA   light #6D28D9
onPrimary      dark #120A24   light #FFFFFF
primaryContainer dark #3B235F / on #EDE9FE
secondary      dark #22D3EE   light #0891B2
secondaryContainer dark #123847 / on #CFFAFE
tertiary       dark #F472B6
background     dark #080C18   light #F7F7FC
surface        dark #111829   light #FFFFFF
surfaceVariant dark #1B2438 / on #A9B4CC
error          dark #F87171 / on #2A0A0A
```

Source: legacy `ui/Theme.kt`. Migrated verbatim to `designsystem/LumaColors.kt` + `LumaTheme.kt`.

## External reference consulted

LG Magic Remote docs (`luna://com.webos.service.mrcu`) were read during this phase. Conclusion: that API reads the **physical** Magic Remote's sensors on the TV side. It is not a phone-remote transport; `sensor2/*` is webOS 24+ only (target TV is webOS 4.1) and the legacy `sensor/*` methods are deprecated. Our phone Air Mouse correctly uses Android `SensorManager` + SSAP move frames. No MRCU code was added.

## Gaps / limitations of this inventory

- No screenshots exist in the repo; the visual reference is source code plus live UI dumps from the old app.
- The old app data was uninstalled before this phase, so no real credential migration was possible. Fresh pairing is the chosen strategy.
- Full feature logic (SSAP, DLNA, discovery, Air Mouse) is intentionally **not** migrated in Phase 1.
