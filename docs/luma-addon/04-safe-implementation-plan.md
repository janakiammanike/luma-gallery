# 04 — Safe implementation and test plan

**Only A1 was approved. Gates D–G remain pending.**

## Order of work

### Gate A — approve the layer — APPLIED

The architecture report was reviewed and A1 was approved. A full backup was taken before any source edit.

### Gate B — create new files only — APPLIED

Created `source/package/ext/luma/**`:

1. `manifest.json` with a fresh stable Luma key (derived extension ID `pohebphbjffnadkbboejjbbmohaimiai`) and permissions `storage`, `tabs`.
2. `shared/` utilities: namespaced messaging, `luma.*` storage helpers, remote-friendly focus CSS.
3. `worker.js`: message router, tab actions, local diagnostics, no telemetry.
4. `popup.html/js/css`: quick access, current-tab actions, media controls, codec probe, diagnostics.
5. `options.html/js/css`: quick-link editor and local-only toggle; a read-only note says phone controls stay in the companion app.
6. `content/bridge.js` and `content/capability.js`: isolated-world media control and codec probes.
7. `home.html/js/css`: toolbar-reachable Luma Home without overriding Chrome's New Tab page.
8. `icons/` and `NOTICE-luma.md`.

Static verification passed: JSON validity, 9 JavaScript files syntax-checked, no forbidden API strings, no telemetry fields, no edits to `tvtools/**` or other existing files.

### Gate C — separate launcher-script change — APPLIED

Applied exactly the additive opt-in path described in `03`:

- new `LUMA_REQ` capture before launch JSON parsing;
- guarded branch that refreshes only `$S/ext/luma`, writes `$S/ext/luma/hw.json`, mirrors NDL failure state and execs with `--load-extension="$EXT,$LUMA"`;
- original Chromium exec block unchanged and used whenever `LUMA_REQ` is not `1`.

Unchanged: `cnrun.c`, `build.sh`, `args.gn`, patches, NDL/GPU flags, `mkipk.py`, `appinfo.json.in`, TV Tools files and every other pre-existing file except `run.sh`.

### Gate D — optional extras — NOT APPROVED

Off and untouched:

- `chrome_url_overrides.newtab` for feature 2 (A2 not approved).
- App title/icon/identity for feature 1 (A3 not approved).
- Toolbar pin changes (A4 not approved).
- Any browser-chrome navigation override for feature 8.
- Telemetry cleanup in the older companion project (A5 not approved).

### Gate E — build

Follow `source/BUILDING.md`:

- No ready local IPK exists. The partial download in temp is incomplete and unverified; it will not be used.
- First cloud build is a full Chromium build: about 3–6 hours on ~64 cores, roughly $15–30, with later builds reusing cache.
- Requires a Modal account/CLI and a Linux x86-64/arm64 host with glibc 2.36+ for packaging steps.
- Build is not started without your go-ahead and cost confirmation.

### Gate F — install and test on the TV

No ADB automation. Install/launch only with your permission, using the normal webOS CLI/app flow already used for companion apps. Developer Mode passphrase stays CLI-only and is never written to a file, log, commit or message.

Test checklist:

| Area | Check | Pass condition |
|---|---|---|
| Loading | Open browser, inspect extension list / toolbar | Both TV Tools and Luma present; TV Tools ID unchanged; no TV Tools settings lost. |
| Preservation | Bookmarks, history, downloads, tabs, address bar, menus | Same behavior as before. |
| Remote | Navigate popup, options, home with Magic Remote | Focus visible; Enter/Back behave as before; browser exit patch unchanged. |
| Quick access | Add/remove a link; open it | Stored under `luma.*`; tab opens in existing Chromium tab strip. |
| Settings | Toggle a Luma option and restart browser | Setting persists locally; Chrome settings untouched. |
| Video | Play a normal HTML5 video; use Luma pause/seek/volume | DOM media responds; NDL playback unaffected. |
| Compatibility | Run codec probe page | Results shown locally; no claim of DRM/HDR support. |
| Diagnostics | Read `hw.json` from Luma folder | Shows hardware/h264/lowmem/freeMB; matches TV Tools state. |
| Memory guards | Exercise existing low-memory path | `guard.log`, `mem.txt`, `lastlog` still written as before. |
| GPU/NDL | Play a known hardware-decoded clip | No regression in video plane or GPU flags. |
| No telemetry | Search new layer and handshake | No `telemetry*`, no endpoint, no device ID, no upload alarm. |
| Standards probe | `tabs.goBack/goForward`, `system.memory`, LAN WebSocket, New Tab override | Only enable the feature if the TV build responds correctly; otherwise report as blocked. |

### Gate G — commit/push

No commit or push is made automatically. After tests pass, I will show the diff and ask before committing; ask again before pushing.

## Rollback

If any preservation check fails:

1. Revert only the approved `run.sh` lines.
2. Remove the new `ext/luma/` folder from the next package.
3. Keep the previous IPK installed; do not touch profile, bookmarks, history or TV Tools data.

Because all Luma state uses `luma.*` keys and a separate extension folder, rollback does not delete TV Tools or browser data.

## Telemetry removal item (deferred)

Explicitly requested but intentionally not mixed into the Luma layer. After the add-on is approved and tested, handle as a separate change with its own diff and approval:

- `luma-companion-2/js/remote-contract.js` — remove telemetry envelope fields.
- `luma-companion-2/js/remote-session.js` — remove `telemetry_batch` / `telemetry_enabled` handling.
- `luma-companion-2/index.html`, `luma-companion-2/js/app.js` — remove any telemetry UI/state.
- `tvremote2/src/main/java/com/luma/tvremote2/host/Envelope.kt` — remove telemetry fields from the shared model.
- `tvremote2/src/main/java/com/luma/tvremote2/host/CompanionServer.kt` — remove telemetry handshake/response fields.
- `docs/REMOTE_INTEGRATION_CONTRACT.md`, `docs/TELEMETRY_*`, `docs/ANDROID_TELEMETRY_STORAGE.md` — update or retire as approved.

Acceptance criterion for that cleanup: no telemetry field is sent, stored, displayed or documented in the active remote contract.
