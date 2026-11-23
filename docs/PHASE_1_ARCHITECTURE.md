# Phase 1 — Architecture

**Module:** `tvremote2/` · **Package:** `com.luma.tvremote2` · **Type:** single Android application module.

## Why one module

Phase 1 is a foundation. The plan asks for clear responsibilities but also "avoid unnecessary abstractions". A single Gradle module with strict package boundaries gives us that without 5 extra `build.gradle.kts` files, slower builds and cross-module publication plumbing. The packages are the seams; if a later phase needs real Gradle modules, each package can be lifted out without changing call sites.

```
tvremote2/
├── app/                  # MainActivity, NavHost shell (composition root)
├── core/                 # Routes, nav items, RemoteViewModel + RemoteUiState
├── designsystem/         # LumaColors, LumaTheme, SectionCard
├── feature/              # One package per screen
│   ├── connect/  remote/  cursor/  keyboard/  apps/
│   ├── inputs/   search/  music/   cast/
│   └── more/     settings/
├── data/                 # SecureTvStore (Android Keystore)
├── network/              # TvDiscovery contract (implementations later)
└── platform/webos/       # LG boundary: models, WebOsPlatform, protocol constants
```

## Dependency direction

```
app  →  core, designsystem, feature:*
feature:*  →  core, designsystem
core  →  data, platform/webos (models only)
data  →  (Android Keystore, security-crypto)
network  →  platform/webos (models only)
platform/webos  →  nothing app-specific
```

No feature imports another feature. No `platform` code imports Compose. `network` returns `WebOsDevice` instead of a raw HTTP/WebSocket type so future TV brands can share the discovery layer.

## Navigation

Legacy used `mutableStateOf("remote")` inside `RemoteApp`. Phase 1 uses Navigation Compose with the **same route keys**:

```
connect  →  remote | cursor | keyboard | apps
          →  more (dialog) → inputs | search | music | cast
settings (dialog)
```

`Preview shell` is a development affordance on the connect screen. It renders the migrated UI without pretending a TV is connected — the top bar reads `UI preview · LG logic pending`. It will be replaced by a real `connectedHost` state when LG connectivity migrates.

## State

`RemoteViewModel : AndroidViewModel` exposes a single `StateFlow<RemoteUiState>`. This mirrors the legacy contract (one `ui` flow consumed with `collectAsState`) so screen code can be migrated with minimal churn. The full legacy state (apps, volume, air-mouse, cast, now-playing, etc.) will be reintroduced incrementally in Phase 2 — only shell fields exist now.

## Credentials

`SecureTvStore` uses `EncryptedSharedPreferences` with an AES256-GCM `MasterKey` held by Android Keystore; keys and values are encrypted with AES256-SIV / AES256-GCM. Pairing keys are stored per host and never written as plain text. The legacy app's plain `luma_tv_remote` preferences are not read. Fresh pairing is intentional (see Migration Report).

## LG platform boundary

`WebOsPlatform` is an interface describing the capabilities the legacy `WebOsClient` exposes: connect/disconnect, buttons, pointer, scroll, keyboard, apps, audio. `WebOsProtocol` records the concrete wire constants and the legacy dependency inventory (two sockets, TLS fallback, register handshake, companion proxy endpoints). No implementation exists in Phase 1 — nothing claims LG connectivity works.

Magic Remote / MRCU is deliberately excluded: it is a TV-side Luna service for the physical remote and is unavailable on the target webOS 4.1 TV for the `sensor2` family.

## Versions (read from the existing catalog)

| Item | Version |
|---|---|
| AGP | 8.13.2 |
| Kotlin | 2.2.20 |
| Compose BOM | 2025.09.00 |
| Navigation Compose | 2.9.1 (added) |
| security-crypto | 1.1.0-alpha06 (added) |
| OkHttp (legacy, Phase 2) | 4.12.0 |
| Coroutines | 1.10.2 |
| compile / min / target SDK | 36 / 26 / 36 |
| JVM target | 17 |

## Explicit non-goals for Phase 1

- No SSAP/WebSocket implementation, no pairing flow.
- No discovery implementations.
- No DLNA, mirroring, air-mouse sensors, haptics engine, now-playing listener.
- No changes to `tvcompanion/` or its protocol.
- No redesign of the purple visual identity.
