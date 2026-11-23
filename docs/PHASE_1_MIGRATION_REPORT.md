# Phase 1 — Migration Report

**Status:** Foundation complete. Debug APK built, installed and smoke-tested on a physical Android phone.

## What was done

1. Inspected the legacy TV Remote module (see `PHASE_1_SOURCE_INVENTORY.md`).
2. Restored the deleted legacy module from git (`9ef2cb6`) so it remains a working fallback. Both `include(":tvremote")` and `include(":tvremote2")` are present in `settings.gradle.kts`.
3. Created `tvremote2/` — Kotlin + Compose + Material 3 + Navigation Compose + StateFlow + ViewModel, Keystore-backed storage.
4. Built the package structure: `app`, `core`, `designsystem`, `feature`, `data`, `network`, `platform/webos`.
5. Migrated the exact purple colour tokens and the UI shell: connect, remote, cursor, keyboard, apps, inputs, search, music, cast, more dialog, settings dialog.
6. Prepared the LG boundary (models, `WebOsPlatform` interface, `WebOsProtocol` constants) without reimplementing `WebOsClient`.
7. Added the three documentation deliverables.

## Files created

```
tvremote2/build.gradle.kts
tvremote2/proguard-rules.pro
tvremote2/src/main/AndroidManifest.xml
tvremote2/src/main/res/values/strings.xml
tvremote2/src/main/res/values/styles.xml
tvremote2/src/main/res/drawable/ic_launcher.xml
tvremote2/src/main/java/com/luma/tvremote2/
  app/MainActivity.kt
  app/RemoteApp.kt
  core/Routes.kt
  core/RemoteViewModel.kt
  designsystem/LumaColors.kt
  designsystem/LumaTheme.kt
  designsystem/Components.kt
  feature/connect/ConnectScreen.kt
  feature/remote/RemoteScreen.kt
  feature/cursor/CursorScreen.kt
  feature/keyboard/KeyboardScreen.kt
  feature/apps/AppsScreen.kt
  feature/more/MoreScreen.kt
  feature/inputs/InputsScreen.kt
  feature/search/SearchScreen.kt
  feature/music/MusicScreen.kt
  feature/cast/CastScreen.kt
  feature/settings/SettingsScreen.kt
  data/SecureTvStore.kt
  network/TvDiscovery.kt
  platform/webos/WebOsModels.kt
  platform/webos/WebOsPlatform.kt
  platform/webos/WebOsProtocol.kt
docs/PHASE_1_SOURCE_INVENTORY.md
docs/PHASE_1_ARCHITECTURE.md
docs/PHASE_1_MIGRATION_REPORT.md
```

Modified outside the new module: `settings.gradle.kts` (two module includes), `gradle/libs.versions.toml` (two versions + two library aliases). No existing source file was overwritten.

## Dependencies added

- `androidx.navigation:navigation-compose:2.9.1`
- `androidx.security:security-crypto:1.1.0-alpha06`

Everything else came from the existing version catalog. No Firebase, no WebView, no CSS framework.

## Build and runtime verification

| Check | Result |
|---|---|
| `:tvremote2:assembleDebug` | BUILD SUCCESSFUL |
| `:app:assembleDebug` (gallery, untouched) | BUILD SUCCESSFUL earlier in this phase |
| APK size | ~11.99 MB |
| `adb install -r` | Success |
| Cold start | Connect screen rendered, no FATAL in logcat |
| Navigation | Remote → Cursor → Apps → More dialog all rendered via NavHost |
| Theme | Purple Material 3 scheme rendered from migrated tokens |
| Status honesty | Top bar shows `UI preview · LG logic pending`, never "Connected" |

## Known issues

- **LG connectivity is not implemented.** No command was sent to the TV from 2.0. This is by design for Phase 1.
- The legacy app's pairing data was uninstalled before this phase; there is nothing to import. New keys are generated on first real pairing and stored in Keystore.
- NDK `27.0.12077973` has no `source.properties`; Gradle emits `[CXX1101]` and packages `libandroidx.graphics.path.so` unstripped. Harmless for this Kotlin-only module but worth cleaning later.
- Some migrated screens use static placeholder text (e.g. `0 app(s)`, `Volume 0`) because their logic is Phase 2.
- No UI screenshots exist in the repo, so visual parity was checked against live UI dumps rather than image diffs.

## Migration safety

- `app/` and `tvcompanion/` untouched in this phase.
- Companion beacon/port/command protocol unchanged.
- Original `tvremote/` restored and still buildable as fallback.
- No credentials copied to plain text; no legacy preferences read.
- All changes committed on `main`; recoverable from git history.

## Next recommended migration step

Phase 2A — **LG transport first, behind `WebOsPlatform`**: port `CompanionScanner` and the companion-proxy branch of `WebOsClient` (the path that is actually proven working on the real TV), then wire a real `connectedHost` into `RemoteViewModel`. Once that lands, replace `Preview shell` with real connect and migrate the Remote tab buttons end-to-end, verifying with a physical volume change like the legacy app did (`9 → 12`).
