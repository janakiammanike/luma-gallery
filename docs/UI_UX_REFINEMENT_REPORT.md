# Luma TV Remote 2.0 — UI/UX Refinement Report (Phase 3A)

**Status:** complete and manually verified on the real TV by the user. This phase refined the existing UI only. No feature was added or removed, and no functional/protocol behaviour was touched.

**Module:** `:tvremote2` (`com.luma.tvremote2`, debug `com.luma.tvremote2.debug`). The gallery `app/`, `tvcompanion/`, the legacy `tvremote/` fallback and all `core/`, `platform/`, `network/` and `input/` sources were left unchanged.

## Scope guardrails respected

| Rule | Result |
|---|---|
| Preserve all functionality | All ViewModel/command calls kept 1:1 on every screen |
| Do not change LG webOS communication / `WebOsPlatform` | No file under `platform/` touched |
| Do not change companion communication | No file under `network/` touched |
| Do not change Air Mouse sensor logic | No file under `input/` touched; sensor start/stop effect in `CursorScreen` unchanged |
| Do not change touchpad gesture logic | `awaitEachGesture` / `awaitFirstDown` / `PointerEventPass.Initial` block verified byte-for-byte against `HEAD` |
| Do not change connection/pairing logic | `ConnectScreen` still calls `onHostInput`, `connect()`, `discoverCompanion()`, `connect(detectedHost)` and the same `LaunchedEffect` |
| Do not remove features | All 11 routes, More items in the same order, all toggles and buttons retained |
| Native Kotlin/Compose only | No CSS, WebView, Firebase, Python or new dependency added |
| Preserve Luma purple identity | Existing `LumaColors` / `LumaTheme` palette reused; no new colours introduced |

## Screens inspected and refined

1. Connect
2. Remote
3. Cursor
4. Air-mouse calibration wizard
5. Keyboard
6. Apps
7. More controls dialog
8. Inputs & sound
9. Content search
10. Music to TV
11. Cast & mirror
12. Settings dialog
13. App shell (header + bottom navigation)

## Shared design system

New files:

- `designsystem/LumaTokens.kt` — 4dp spacing rhythm, screen/card padding, minimum touch targets (48dp actions, 56dp remote keys, 64dp D-pad), corner radii, text-field height, adaptive touchpad heights.
- `designsystem/LumaComponents.kt` — `LumaButton`, `LumaOutlinedButton`, `LumaIconButton`, `LumaCard`, `LumaRemoteButton`, `LumaDPad`, `LumaTextField`, `LumaDeviceStatus`, `LumaBottomNavigation`, `LumaAdaptiveRow`.

Changed files:

- `designsystem/Components.kt` — `SectionCard` now delegates to `LumaCard` so every section has the same surface, padding and radius; title uses the shared title style and is single-line.
- `designsystem/LumaTheme.kt` — Material 3 theme now also applies `LumaTypography`, so one type scale is used everywhere.
- `designsystem/LumaTokens.kt` typography — defined Material 3 `Typography` roles with consistent weights/sizes.

`LumaAdaptiveRow` wraps controls into extra rows on narrow screens instead of squeezing them; every item still gets an equal share of the width. Labels use `maxLines = 1` with ellipsis so buttons never grow taller when text is long.

## Per-screen improvements

### App shell (`app/RemoteApp.kt`)
- Header now shows `Luma TV Remote`, a single-line `Connected · <TV IP>` status and a transport chip (`Direct` / `Companion`); Settings and Disconnect use compact icon + single-line label.
- Bottom navigation uses vector icons and single-line labels instead of glyph text.
- Selection state now also highlights `More` while on one of its sub-screens and `Cursor` while on calibration.
- Status-bar insets and existing dialogs unchanged.

### Remote
- D-pad is now the first and primary card; arrows and `OK` are 64dp squares and still send `UP/DOWN/LEFT/RIGHT/ENTER`.
- System, Scroll, Volume & channels, Playback and Power groups use uniform 56dp-tall keys with 68dp minimum width and adapt to 3/2/1 columns.
- Customize chips wrap instead of overflowing; group keys and persistence are unchanged.

### Cursor
- Air mouse status now has an `Active`/`Off` pill, sensor name, status message and clearer helper copy.
- Calibrate / Air click / wizard / sensitivity / invert controls use shared buttons and tiles.
- Touchpad height is 160dp below 360dp width and 200dp otherwise. **The pointer gesture block itself is unchanged.**
- Scroll and Back/OK controls use shared buttons.

### Keyboard, Apps, Search, Music, Cast, Inputs
- Text fields use `LumaTextField` (56dp tall, single line); actions use `LumaButton`/`LumaOutlinedButton` with equal weights.
- Apps list rows use one consistent outlined key style; long `"title · id"` labels ellipsize rather than wrap.
- Inputs/Search/Music/Cast gained vertical scrolling so the on-screen keyboard cannot block the action button.

### More, Settings and calibration dialogs
- More items keep the same order with leading vector icons and single-line labels.
- Settings dialog uses the shared surface radius and single-line toggle rows.
- Calibration keeps all four stages, all `vm` calls and the progress indicator, but uses shared buttons, tokens and bounded text lines.

## Verification

### Build and tests
- `./gradlew :tvremote2:assembleDebug :tvremote2:testDebugUnitTest --console=plain` → **BUILD SUCCESSFUL**.
- APK: `tvremote2/build/outputs/apk/debug/tvremote2-debug.apk` (16,383,864 bytes), installed on the phone with `adb install -r` → **Success**.
- `:tvremote2` currently has **no test sources** (`src/test` does not exist). `testDebugUnitTest` succeeded with zero tests; no UI/compose tests were added in this phase.

### Responsive layout audit (real phone)
Device: `1080x2412`, `480dpi` → **360dp logical width**, i.e. the compact breakpoint case.

| Screen | Elements | Horizontal overflow |
|---|---|---|
| Remote | audited | 0 |
| Cursor | 91 | 0 |
| Keyboard | 71 | 0 |
| Apps | 88 | 0 |

Touch target audit on Remote: D-pad keys 192px (64dp), section buttons 168px (56dp), header actions 144px (48dp), bottom-nav items 240px tall. All exceed the 48dp minimum.

### Screenshots
"Before" and "after" captures for all ten main screens are stored in:
- `docs/screenshots/before/` — `connect`, `remote`, `cursor`, `keyboard`, `apps`, `more`, `inputs`, `search`, `music`, `cast`
- `docs/screenshots/after/` — same ten names

These were captured with `adb screencap` after explicit approval. During capture only navigation taps were used; no TV command (volume, power, playback, pointer) was sent by automation. The assistant cannot visually open images in this session, so the screenshots need the user's visual review; the layout audit above is programmatic (UI hierarchy bounds), not a human visual judgement.

## Release artifact

- Final version: `versionName = 2.0.0`, `versionCode = 2` (was `2.0.0-phase1` / `1`).
- Signed release APK: `tvremote2/build/outputs/apk/release/tvremote2-release.apk` (8,740,042 bytes).
- `apksigner verify`: valid, single signer (`CN=Janaki Ram, OU=Luma`), APK Signature Scheme v2. `aapt2`: `com.luma.tvremote2`, versionCode 2, versionName 2.0.0, minSdk 26, targetSdk 36.
- No AAB was produced: the user will upload the APK to the Play Store directly.

## Manual verification (user)

After the Phase 3A build was installed, the user reported that **everything is working** on the real TV/phone. This covers the rewritten Remote, Cursor, Keyboard, Apps, More, Inputs, Search, Music and Cast layouts and the shared header/navigation. No regression was reported.

## Remaining issues

- **Find TV automatic scan** was not explicitly called out in the final confirmation; manual IP connect remains the verified path (already open from the previous phase).
- Screenshots were captured but not visually reviewed by the assistant; they are available for the user's reference.
- No UI tests were added; the module has no test harness yet.

## Modified files

Modified (15):
1. `tvremote2/src/main/java/com/luma/tvremote2/app/RemoteApp.kt`
2. `tvremote2/src/main/java/com/luma/tvremote2/designsystem/Components.kt`
3. `tvremote2/src/main/java/com/luma/tvremote2/designsystem/LumaTheme.kt`
4. `tvremote2/src/main/java/com/luma/tvremote2/feature/apps/AppsScreen.kt`
5. `tvremote2/src/main/java/com/luma/tvremote2/feature/calibration/CalibrationScreen.kt`
6. `tvremote2/src/main/java/com/luma/tvremote2/feature/cast/CastScreen.kt`
7. `tvremote2/src/main/java/com/luma/tvremote2/feature/connect/ConnectScreen.kt`
8. `tvremote2/src/main/java/com/luma/tvremote2/feature/cursor/CursorScreen.kt`
9. `tvremote2/src/main/java/com/luma/tvremote2/feature/inputs/InputsScreen.kt`
10. `tvremote2/src/main/java/com/luma/tvremote2/feature/keyboard/KeyboardScreen.kt`
11. `tvremote2/src/main/java/com/luma/tvremote2/feature/more/MoreScreen.kt`
12. `tvremote2/src/main/java/com/luma/tvremote2/feature/music/MusicScreen.kt`
13. `tvremote2/src/main/java/com/luma/tvremote2/feature/remote/RemoteScreen.kt`
14. `tvremote2/src/main/java/com/luma/tvremote2/feature/search/SearchScreen.kt`
15. `tvremote2/src/main/java/com/luma/tvremote2/feature/settings/SettingsScreen.kt`

Added (3 + screenshots):
16. `tvremote2/src/main/java/com/luma/tvremote2/designsystem/LumaTokens.kt`
17. `tvremote2/src/main/java/com/luma/tvremote2/designsystem/LumaComponents.kt`
18. `docs/UI_UX_REFINEMENT_REPORT.md`
19. `docs/screenshots/before/*.png`, `docs/screenshots/after/*.png`
