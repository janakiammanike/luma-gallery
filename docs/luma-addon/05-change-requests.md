# 05 — Change requests and blocked ideas

This document lists anything that cannot be delivered by adding new files alone. Only A1 was approved and applied; A2–A5 were not approved. B1–B9 were preservation-checked only, with no destructive changes.

## A. Existing-file changes needing approval

| ID | Request | File(s) | Why not automatic | Default |
|---|---|---|---|---|
| A1 | Load Luma beside TV Tools | `source/package/run.sh` | Implemented as a separate opt-in exec branch; original exec untouched. Triggered by `CN_LUMA=1` or `{"luma":true}`. | Approved and applied |
| A2 | Replace default New Tab page | New `manifest.json` with `chrome_url_overrides.newtab` | Supported MV3 override, but it replaces existing browser behavior. | Off |
| A3 | Rebrand app/title/icon | `config.env`, `appinfo.json.in`, icons, `mkipk.py` inputs | Changes app identity and existing files; user asked to keep browser intact. | Off |
| A4 | Pin Luma toolbar icon | `run.sh` `pinned_extensions` JSON | Edits existing toolbar preference initialization; must not remove TV Tools pin. | Off |
| A5 | Remove older telemetry fields | Companion/Kotlin/docs files listed in `04` | Separate cleanup of existing files; not part of the add-on. | Off |

## B. Chromium core / host changes

These have no supported extension path in the inspected source. They are reported, not built.

| ID | Desired behavior | Missing capability | Needed change | Verdict |
|---|---|---|---|---|
| B1 | Separate webOS app calls into Chromium via LS2 | `packageinfo.json` has `"services": []`; no LS2 service in Chromium | Add a native service/host in the app's luna role | ⛔ Core/host change |
| B2 | Native messaging between extension and a local helper | No native messaging host manifest, `connectNative` or `sendNativeMessage` in source | Register host manifest + helper binary at system/profile path | ⛔ Host change |
| B3 | Inject text directly into omnibox from phone keyboard | Content scripts cannot write browser chrome | Modify Chromium omnibox/WebUI | ⛔ Core change |
| B4 | Move native pointer / simulate Air Mouse cursor from Chromium | No extension API for native pointer; Magic Remote handled by OS/browser patches | Modify Ozone/Wayland input path | ⛔ Core change |
| B5 | Global Back/Home override for all pages | Back/exit is owned by patch `0057`; MV3 `goBack` is tab-scoped only | Change browser input handling | ⛔ Core change |
| B6 | Control NDL hardware video plane from Luma UI | `cnrun.c` socket / NDL libraries are launcher-private | Extend launcher protocol and expose it to extension | ⛔ Core/host change |
| B7 | Enable Widevine/DRM/HDR | `args.gn` sets Widevine off; repo documents no DRM/HDR | Rebuild browser with different flags and libraries | ⛔ Core change |
| B8 | Read Chrome internal process/performance metrics | MV3 exposes no general process table beyond `system.memory` (unproven here) | Add a browser-level API or custom WebUI | ⛔ Core change |
| B9 | Guarantee a codec plays on this TV | Browser capability APIs are best-effort; hardware decode depends on launcher/NDL state | Add a native probe service | ⛔ Core/host change |

## C. APIs that are standard but unproven on this TV build

Do not treat these as approved until a TV probe succeeds:

- `chrome.tabs.goBack`, `chrome.tabs.goForward`.
- `chrome.system.memory.getInfo` with `system.memory` permission.
- `chrome.scripting.executeScript` / `insertCSS`.
- `chrome_url_overrides.newtab`.
- WebSocket from an MV3 worker to the Android Host LAN address.
- `navigator.mediaCapabilities.decodingInfo` on this PowerVR/GLES webOS build.

## D. Explicitly not requested / not added

- Analytics, telemetry, crash reporting, cloud sync, device fingerprinting.
- Google account sync, AI Mode or omnibox suggestions changes.
- Replacement of bookmarks, history or downloads UI.
- Any modification to licenses or removal of notices.

## Decision status

1. **A1** — approved and applied as a separate opt-in path.
2. **A2–A4** — not approved; left off and untouched.
3. **A5** — not approved; telemetry cleanup deferred and untouched.
4. **B1–B9** — reported as blocked; preservation checks only, no destructive changes.
5. **Build/install/device test/commit/push** — still pending explicit permission.
