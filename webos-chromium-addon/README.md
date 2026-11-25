# webOS Chromium — Luma Features overlay

This directory contains only the additive Luma overlay for the untouched upstream `WebOS-Chromium` package.

- `source/package/run.sh` — the only modified package script. The original Chromium exec block is unchanged; Luma is loaded only when `CN_LUMA=1` or the launch JSON contains `luma:true`.
- `source/package/ext/luma/**` — the complete Luma Features Layer, including the Phase 3 `Luma Cinema` media engine.

No Chromium binary, library, UI resource, `appinfo.json`, control file, TV Tools extension or engine source is stored here. Apply this overlay on top of the official `io.github.sarahahzub.chromium` v1.1.1 IPK before installing on the TV.

The repack command used for verification keeps the package/control version at v1.1.1 and changes only `bin/run.sh` and `ext/luma/**`. The extension itself is versioned by `source/package/ext/luma/manifest.json` (`0.3.0` for Phase 3).
