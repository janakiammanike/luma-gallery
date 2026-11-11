# Luma — Photos & Videos

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

A fast, native Android gallery built with Kotlin and Jetpack Compose. Luma indexes your
photos and videos with MediaStore, pairs Apple Live Photos automatically, and includes a
LAN web player for your TV, PC or iPhone.

> Made by **Janaki Ram** · GitHub [@janakiammanike](https://github.com/janakiammanike) · [janakiram25@yahoo.com](mailto:janakiram25@yahoo.com)

## Download

Grab the latest signed APK from the [Releases](../../releases) page.

- Package: `com.luma.gallery`
- Min Android: 8.0 (API 26) · Target: Android 16 (API 36)

## Features

- **Photos & videos in one place** — MediaStore-backed grid with date groups, pinch/zoom
  density (3–6 columns), folders and albums.
- **All folders, including missed files** — optional All Files Access lets Luma index media
  that MediaStore skipped (LocalSend, ADB pushes, file managers).
- **Live Photos** — a still plus its `.MOV` clip are paired by folder and file name, marked
  with a LIVE badge, and play in a full-screen overlay. Clips stay hidden from every grid.
- **Viewer** — horizontal swipe between items, chrome that auto-hides for photos and stays
  pinned for videos, ExoPlayer playback with seeking, plus share, favourite, album, edit,
  info and delete actions.
- **Albums** — custom albums, smart groupings, add/remove items.
- **Trash** — 30-day Luma-managed trash stored inside the app before permanent deletion.
- **Vault** — hide items behind biometric/device authentication.
- **Photo editor** — crop, rotate, filters and save-as-copy to `LumaEdits`.
- **Video trim** — keep a chosen range and save a copy with Media3 Transformer.
- **Themes** — dark/light/system, 14 accent presets and a hue/saturation custom accent.
- **Languages** — English, తెలుగు and हिन्दी, switchable in Settings.
- **Web & TV** — a built-in NanoHTTPD server with a React UI: browse, stream videos with
  Range requests, download originals, upload from iPhone/PC, ZIP download for Live Photos.
  PIN + cookie auth, QR code, rotating PIN, HEIC→JPEG previews, original HEIC preserved.

## Build

Requirements: JDK 17+ (JDK 21 recommended) and Android SDK 36.

```bash
./gradlew assembleDebug
# APK → app/build/outputs/apk/debug/app-debug.apk
```

The release build reads signing details from `keystore.properties` (not committed):

```properties
storeFile=luma-release.jks
storePassword=…
keyAlias=luma
keyPassword=…
```

```bash
./gradlew assembleRelease
# APK → app/build/outputs/apk/release/app-release.apk
```

The web UI lives in `webui/` (React 19 + Vite + TypeScript) and is bundled into
`app/src/main/assets/web` at build time:

```bash
cd webui
npm install
npm run build
```

## Project layout

```
app/                     Android app (single module, Compose)
  src/main/java/com/luma/gallery/
    data/                MediaStore repository, models, trash/vault stores
    ui/                  gallery, albums, viewer, editor, settings, theme
    web/                 LAN server, media pipeline, foreground service
  src/main/assets/web/   built React UI
webui/                   React + Vite source for the web UI
```

## Notes

- Live Photos are paired **locally** by folder + base file name, so Apple's `.HEIC`/`.JPG`
  plus the matching `.MOV` work without any Apple metadata.
- iOS Safari converts photo-picker uploads to JPEG and never sends the Live Photo clip;
  use the iOS Files app or LocalSend for original HEIC + MOV.
- The vault is hidden in-app but not encrypted yet; full vault encryption is planned.
- Debug builds install as `com.luma.gallery.debug` and can coexist with release builds.
- **Android developer verification (2027)** — Google now requires apps on certified devices
  to come from a verified developer. Luma's package name and signing-key fingerprint are
  documented in [`docs/developer-verification.md`](docs/developer-verification.md) for a
  one-time registration; until then the APK still installs via ADB or Android's advanced
  flow. This does not affect the GPL-3.0 license or the source code.

## License

Luma is free software: you can redistribute it and/or modify it under the terms of the
**GNU General Public License v3.0**. See [LICENSE](LICENSE) for the full text.

If you distribute a modified version — including a rebuilt APK — you must make the
corresponding source code available under the GPL-3.0 as well.

