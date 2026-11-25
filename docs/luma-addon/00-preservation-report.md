# 00 — Existing source preservation report

**Status:** inspected, unchanged.

## Source inspected

The downloaded source tree is:

`C:\Users\janak\Downloads\WebOS-Chromium-main\WebOS-Chromium-main`

Paths below are relative to that tree. It is treated as read-only evidence.

| Area | Evidence | Conclusion |
|---|---|---|
| Project scope | `source/README.md:1-8` says only the project's own work is present; Chromium is downloaded at a pinned commit and no LG firmware files are included. | The extension, launcher and packaging are the only project-owned integration surfaces in the ZIP. |
| Chromium pin | `source/config.env:11-12` pins `webOS-ports/chromium151` commit `0baacb2b1a4b885826fed5493262f759c6a5b469`. | Any browser-core change would alter this external source, not the local ZIP. Not done. |
| Patch set | `source/patches/INDEX.md:1-3, 5-62` applies local patches in name order on top of that Chromium commit. | Patches 0011, 0015, 0016, 0017, 0043, 0045, 0047, 0049, 0053, 0057, 0061, 0063, 0064 and 0066 cover webOS window/app id, remote, keyboard, menus, Back/exit, media state and memory. They are left untouched. |
| Build flags | `source/args.gn:1-10, 25-32, 37-38, 56-98` set ARM softfp, Wayland, no X11, no Native Client, no Widevine, PulseAudio, proprietary codecs and size/memory flags. | Not changed. DRM remains unavailable; no claim otherwise. |
| Browser packaging | `source/package/mkipk.py:121-158, 179-199` copies the built browser tree, one locale, licenses, `ext/`, launcher, icons and `appinfo.json`; `packageinfo.json` declares `"services": []`. | A new extension can be added under `package/ext/` and is already picked up by `tree(HERE/ext, "ext")` at line 144; no service registration exists. |
| Launcher | `source/package/launcher/cnrun.c:1-21, 48-50, 79-128, 131-148` owns the Unix media socket and dlopens LG NDL / Starfish libraries. | Hardware video stays in the existing launcher. The Luma layer must not touch it. |
| Startup script | `source/package/run.sh:136-181, 183-228, 229-247, 263-272, 377-396` contains memory/priority guards, extension copy, launch `target` parsing and the final `exec chrome` flags. | It currently loads exactly one extension (`--load-extension="$EXT"`). Adding a second extension requires a small, separately approved launcher-script change; not done. |
| Existing extension | `source/package/ext/tvtools/manifest.json:1-67` is MV3 with `storage`, `tabs`, `alarms`, `declarativeNetRequest`, `<all_urls>`, a service worker, two content-script groups, popup and options page. | This is the proven in-build extension model. Luma should follow it as a separate extension rather than replacing it. |
| TV Tools behaviour | `source/package/ext/tvtools/bg.js:9-18, 24-59, 62-75, 79-119`, `bridge.js:1-45`, `page.js:1-115, 218-240`, `settings.js:1-76`, `ui.js:1-141`, `popup.js:1-63`, `options.js:1-57`. | Proves storage, DNR rule IDs 1/2, tab query/remove/reload/discard, alarms, runtime messaging, DOM media pause, lite-mode DOM changes and remote-friendly extension pages. Must not be overwritten. |
| Licences | `source/package/licenses/README.txt:1-16`, `source/NOTICE.md:15`, `source/patches/INDEX.md:1-3`. | Existing Chromium, NSS, NSPR and webOS notices are preserved. A new Luma extension adds its own notice, not replacements. |

## What was explicitly not modified

- `source/browser/` — no files; Chromium binary/tree is not in the downloaded ZIP.
- `source/patches/` — no patch added, removed or reordered.
- `source/args.gn`, `source/config.env`, `source/container/`, `source/tvproto/`.
- `source/package/run.sh`, `source/package/mkipk.py`, `source/package/appinfo.json.in`.
- `source/package/launcher/cnrun.c` and `source/package/launcher/build.sh`.
- `source/package/ext/tvtools/**` — TV Tools remains byte-for-byte as downloaded.
- Icons, splash, `moved.html`, licences and notices, `.github/`, `ci/`, `webosbrew/`.

## Workspace changes made by this report phase

Initially only new documentation under `docs/luma-addon/` was created. After A1 approval, the following additive work was performed on the downloaded source tree:

- New files: `source/package/ext/luma/**` only.
- One existing file edited: `source/package/run.sh`, with two additive insertions (Luma opt-in capture near the top and a separate opt-in exec branch before the original exec). The original Chromium exec block was not changed.
- Backup of the full source tree before edits: `C:\Users\janak\Downloads\WebOS-Chromium-main\WebOS-Chromium-main.backup-A1-20261125-105633`
- Hash comparison of all pre-existing files shows `source/package/run.sh` as the only changed existing file.

No package was built, no IPK was installed, no device command was run, and no commit or push was made.

## Preservation verdict

The working browser can remain intact. All proposed Luma work fits one of three buckets:

1. **New extension files only** — safe, no existing Chromium file changes.
2. **Small packaging/launcher change** — still no Chromium core change, but needs explicit approval because it touches `run.sh`.
3. **Core/host change** — must be reported and left unimplemented until approved.
