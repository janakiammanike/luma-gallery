# 03 — Luma Features Layer architecture

## Design decision

Add a **second Manifest V3 extension** named `luma` beside the untouched `tvtools` extension. No Chromium engine file, patch, GPU/NDL code, browser UI file or TV Tools file is rewritten.

The layer has three parts:

1. **New extension files** — all Luma behavior.
2. **One approval-gated packaging change** — teach `run.sh` to copy and load the second extension.
3. **Deferred cleanup** — telemetry removal from the older companion project, handled separately after approval.

## Source layout proposed (all new paths)

```text
source/package/ext/luma/
  manifest.json
  NOTICE-luma.md
  worker.js
  popup.html
  popup.css
  popup.js
  home.html
  home.css
  home.js
  options.html
  options.css
  options.js
  shared/
    bus.js
    storage.js
    theme.css
    remote-focus.js
  content/
    bridge.js          # isolated world; DOM media control + message bridge
    capability.js      # isolated world; read-only codec probes
  icons/
    16.png 32.png 48.png 128.png
```

`mkipk.py:144` already packages everything under `package/ext/`, so these files need no packaging-code change. They will not be loaded until the approved `run.sh` change is made.

## Boundary table

| Area | Status | Detail |
|---|---|---|
| Chromium source at pinned commit | Untouched | `config.env:11-12` pin stays as-is. |
| `source/patches/**` | Untouched | All 60+ webOS, remote, keyboard, GPU, media and memory patches stay in order. |
| `source/args.gn` | Untouched | GPU, Widevine-off, low-memory and codec flags stay as-is. |
| `source/package/launcher/cnrun.c`, `build.sh` | Untouched | NDL/Starfish socket, hardware decode and luna role stay with the original launcher. |
| `source/package/ext/tvtools/**` | Untouched | Its ID, DNR rule IDs `1`/`2`, storage keys and service worker stay intact. |
| `source/package/run.sh` | **Approval-gated** | Copy `$D/ext/luma` to `$S/ext/luma`, write `$S/ext/luma/hw.json`, and pass both extension paths to `--load-extension`. |
| `source/package/mkipk.py` | Untouched | `tree(HERE/ext, "ext")` already includes new files. |
| `source/package/appinfo.json.in`, icons, licenses | Untouched | No app identity or notice replacement. |
| `luma-companion-2`, `tvremote2`, old docs | Out of this layer | Telemetry cleanup is a separate post-approval task. |

## Extension contract

### Manifest shape

- `manifest_version: 3`
- New fixed `"key"` generated for Luma. **Never reuse the TV Tools key** from `manifest.json:7`; that would collide with extension ID `ikkplgnegbekkbamfelhaeahnaddgnma`.
- `action.default_popup: "popup.html"`, `options_page: "options.html"`, `background.service_worker: "worker.js"`.
- Permissions kept minimal and approved:
  - `storage` — proven in TV Tools.
  - `tabs` — proven in TV Tools.
  - `alarms` — only if periodic local cleanup is needed; proven in TV Tools.
  - No `declarativeNetRequest` unless a specific header rule is approved; TV Tools owns rule IDs `1` and `2`.
- `host_permissions`: only concrete LAN endpoints needed for the Android Host bridge, e.g. `http://192.168.*.*:PORT/*`, not `<all_urls>` unless a feature demonstrably requires it.
- Content scripts declared in the manifest (isolated world first), with `exclude_matches` for `*://challenges.cloudflare.com/*` and internal `chrome://` schemes. No dynamic `chrome.scripting.executeScript` in phase 1.

### Namespacing to avoid collisions with TV Tools

| Resource | TV Tools | Luma |
|---|---|---|
| Storage area | `chrome.storage.local` with its own keys | Same area, keys prefixed `luma.` (`luma.settings`, `luma.quickLinks`, `luma.diagnostics`) |
| Runtime messages | Its own message shapes | All messages start with `{ source: "luma", type: "..." }`; worker ignores messages without that marker |
| DOM custom events | Its bridge channel | `luma-bridge` / `luma-config` event names only |
| DNR rule IDs | `1`, `2` | Unused in phase 1; if later needed, start at `1000` |
| Hardware file | `$EXT/hw.json` where `EXT=$S/ext/tvtools` | `$S/ext/luma/hw.json`, written by the approved `run.sh` change |
| Toolbar item | Pinned TV Tools icon via `pinned_extensions` | Luma icon added only if approved; no replacement of the pinned TV Tools entry |

## Data flows

### 1. Remote / keyboard flow (no new key handling)

```text
LG Magic Remote
  -> existing Chromium patches + --enable-spatial-navigation
  -> focus moves inside Luma popup/options/home page
  -> Enter/Space activates a normal <button>/<a>
  -> Luma handler sends a namespaced runtime message
```

Luma contributes only large focusable controls, visible `:focus` rings and `tabindex` order. It does not register global key handlers or alter Back/exit patch `0057`.

### 2. Home / Quick Access / Settings flow

```text
popup.html or home.html
  -> shared/storage.js
  -> chrome.storage.local ("luma.*")
  -> worker.js
  -> chrome.tabs.query/create/update/remove (approved tab actions)
  -> existing browser tab UI (unchanged)
```

Chrome's own New Tab page stays default unless the `chrome_url_overrides.newtab` option is explicitly approved. The safe default is a Luma Home extension page opened from the toolbar action.

### 3. Video control flow

```text
Luma popup button
  -> runtime.sendMessage({source:"luma", type:"media-control", action:"pause"})
  -> worker.js checks sender and active tab
  -> chrome.tabs.sendMessage(tabId, message)
  -> content/bridge.js (isolated world)
  -> document.querySelectorAll("video,audio") -> pause()/currentTime/volume
```

This mirrors the proven TV Tools pattern but uses isolated-world DOM access and never patches site JS. It cannot reach NDL's video plane, DRM or hardware decode controls.

### 4. Diagnostics / compatibility flow

```text
worker.js
  -> fetch(chrome.runtime.getURL("hw.json"))       # Luma's own copy
  -> {hardware, h264, lowmem, freeMB}
content/capability.js
  -> MediaSource.isTypeSupported(codec)
  -> HTMLMediaElement.canPlayType(type)
  -> navigator.mediaCapabilities.decodingInfo(...)  # if probe succeeds
  -> result shown locally in popup/options
```

No result is uploaded. No telemetry key, endpoint or alarm is created for diagnostics.

### 5. Android Air Mouse / phone keyboard flow

```text
Phone (existing com.luma.companion2 Host mode)
  -> existing LAN JSON/WebSocket contract
  -> Luma worker (only if host_permissions approved and TV probe succeeds)
  -> tab/media/navigation command
```

Luma does not replace the phone app. If the command targets browser chrome (omnibox/global pointer), it is out of scope and reported, not implemented.

## Applied `run.sh` change (A1, additive only)

The original Chromium exec block remains byte-for-byte unchanged:

```sh
exec "$B/chrome" \
    --load-extension="$EXT" \
    ...
```

Two additive insertions were made:

1. Near the top, `LUMA_REQ` is captured before the launch JSON is shifted. It becomes `1` only when `CN_LUMA=1` or the launch JSON contains `"luma":true`.
2. Immediately before the original exec, a guarded branch copies/refreshes `$S/ext/luma`, writes `$S/ext/luma/hw.json`, mirrors the NDL failure state, and execs Chromium with `--load-extension="$EXT,$LUMA"` using the same flags as the original path.

When `LUMA_REQ` is not `1`, the branch is skipped and the original exec runs exactly as before. The TV Tools copy logic above was not edited; the Luma branch refreshes only `$S/ext/luma`, never `$S/ext` as a whole.

`-rdynamic`, `cnrun`, `CN_MEDIA_SOCK`, `hw.state`, GPU flags, memory guard, `mkipk.py`, `appinfo.json.in` and all other existing files remain unchanged.

## What is deliberately absent

- No `chrome://` WebUI replacement.
- No browser toolbar/menu/omnibox rewrite.
- No `nativeMessaging`, `connectNative`, LS2 service or host manifest.
- No NDL/GPU/patch modification.
- No changes to Widevine/DRM/HDR flags.
- No telemetry, analytics, cloud upload, device ID or periodic reporting.
- No ADB automation and no install/build without explicit permission.
- No commit or push without asking first.

## Approval gates

| Gate | Needed for | Status |
|---|---|---|
| A | Accept architecture and create new `ext/luma/**` files | Approved and applied |
| B | Allow the small `run.sh` edit to load Luma beside TV Tools | Approved and applied as a separate opt-in path; original exec untouched |
| C | Allow optional New Tab override | Not approved, default off |
| D | Allow branding changes to app title/icon/identity | Not approved, default off |
| E | Allow telemetry cleanup in older companion files | Not approved, deferred |
| F | Allow build/install/device test | Pending |
| G | Allow commit/push | Pending |
| H | B1–B9 core/host changes | Preserve-only; no changes made |
