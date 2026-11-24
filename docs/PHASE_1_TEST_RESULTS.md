# Luma Companion 2.0 — Phase 1 Test Results

**Date:** 2026-11-24  
**Scope:** local static and behavioural checks, followed by physical webOS TV packaging, installation, launch and process verification. ADB automation was not used.

## Environment

- Workspace: `D:\apps\photos galary app`
- Repository state before new files: clean at `89c44dd`
- Node.js `v26.12.1`, npm `11.20.0`; LG recommends Node 14–16, but the CLI worked at this version.
- Official webOS CLI `@webos-tools/cli` `3.2.6` installed globally after user approval.
- Configured TV device: `lumatv` at `prisoner@192.168.1.2:9922`; the passphrase was supplied by the user and was not written to any file.
- `websearch` returned HTTP 402 during research; official LG documentation was read directly with `webfetch`.

## 1. Syntax and metadata

| Check | Command / method | Result |
|---|---|---|
| JavaScript syntax | `npm run check` in `luma-companion-2/` | Pass. `node --check` passed for `remote-contract.js`, `storage.js`, `diagnostics.js`, `app.js`. |
| `appinfo.json` parse | Included in `npm run check` | Pass. |
| `package.json` parse | Local Node JSON parse in `dom-meta-check.js` | Pass; `name = luma-companion-2`. |
| Mandatory appinfo fields | Local check against `id`, `title`, `type`, `main`, `icon`, `version` | Pass; none missing. |
| Version format | Regex `^\d+\.\d+\.\d+$` on `0.1.0` | Pass. |
| App type | `type === "web"` | Pass. |

## 2. Remote contract validator

Test harness simulated `window` and loaded `js/remote-contract.js` in Node.

| Case | Expected | Actual |
|---|---|---|
| Valid object envelope | `ok` | Pass |
| Valid JSON string envelope | `ok` | Pass |
| `v: 2` | `unsupported_version` | Pass |
| Unknown `type` | `unknown_type` | Pass |
| Empty `id` | `invalid_id` | Pass |
| 129-character `id` | `invalid_id` | Pass |
| String `ts` | `invalid_timestamp` | Pass |
| Array `payload` | `invalid_payload` | Pass |
| `null` input | `not_an_object` | Pass |
| Broken JSON string | `invalid_json` | Pass |
| String larger than 64 KB | `message_too_large` | Pass |

Summary: **11/11 passed**. Counters after the run: `valid=2`, `invalid=9`, `tooLarge=1`, `unknownType=1`. Overall result: `CONTRACT_VALIDATOR_OK`.

No transport is opened by this module; the test only exercised validation.

## 3. Storage guard

Test harness replaced `localStorage` with a controllable fake implementation.

| Case | Result |
|---|---|
| Initial read returns safe defaults | Pass |
| Normal write succeeds | Pass |
| Read after write returns saved counters | Pass |
| Read while storage throws returns `available:false` | Pass |
| Write while storage throws returns `false` | Pass |
| Clear while storage throws returns `false` | Pass |
| Malformed stored JSON falls back to defaults | Pass |
| Clear succeeds when storage recovers | Pass |

Summary: **8/8 passed**. Overall result: `STORAGE_GUARD_OK`.

The app controller also marks storage degraded, increments `storageFailures`, and restores `available` after a later successful write.

## 4. DOM and wiring

| Check | Result |
|---|---|
| DOM IDs referenced by `app.js` | 18 referenced |
| Missing DOM IDs | 0 |
| Focusable elements | 6 |
| Every `setText` target exists | Pass |
| Refresh button exists and is wired | Pass |

Overall result: `DOM_METADATA_OK`.

## 5. CSS engine compatibility

Grep over `css/styles.css` for `display: grid`, `grid-template`, `gap:` and `inset:` returned **no matches**.

Layout uses flexbox, explicit margins, explicit `top/right/bottom/left`, CSS variables, gradients and transforms. This matches the Chromium 53 baseline documented in `BROWSER_ENGINE_RESEARCH.md`.

## 6. Forbidden runtime patterns

Grep over `luma-companion-2/js/*.js` for the following returned **no matches**:

- `new WebSocket`, `WebSocket(`
- `XMLHttpRequest`
- `fetch(`
- `require(`
- ES `import`
- `Enact`, `React`, `Vue`
- `firebase`, `analytics`
- `createServer`, `dgram`

The dashboard therefore contains no network client, no framework, no Node-only module and no analytics SDK.

## 7. Assets

| File | Dimensions | Size | Result |
|---|---|---|---|
| `icon.png` | 80x80 | 1981 bytes | Pass |
| `largeIcon.png` | 130x130 | 2904 bytes | Pass |
| `assets/splash.png` | 1920x1080 | 244050 bytes | Pass |

The splash image matches the 1920x1080 design target.

## 8. Lifecycle and cleanup source review

Reviewed in `js/app.js`:

- `activate()` starts a single 30-second render interval and a platform probe.
- `deactivate()` clears all timers and cancels the probe handle.
- `probeGeneration` prevents stale callbacks from overwriting fresh state.
- Hide events handled: `visibilitychange`, `webkitvisibilitychange`, `pagehide`.
- Show/launch events handled: `webOSLaunch`, `webOSRelaunch`, `pageshow`.
- Online/offline events only update the displayed state; they do not start a transport.

Result: Pass by source review. Runtime behaviour on TV is not verified.

## 9. Privacy and boundary source review

- No telemetry emitter exists.
- No pairing token, client key or legacy `TvStore` import exists in the new app.
- No URL, iframe, media player or DRM code exists.
- No Room dependency was added to any Android module.
- No existing Android, gallery, deprecated companion or Gradle file was modified.

At the end of Phase 1, only the new `docs/*.md` files and the new `luma-companion-2/` directory were untracked and no tracked file was modified. Later Phase 2 added `companion-host/` and one tracked change to `settings.gradle.kts`; the module was later removed in favour of Host mode inside `tvremote2` 3.0.0. Those stages are documented separately.

## 10. Physical TV verification

| Check | Command / method | Result |
|---|---|---|
| Device connection | `ares-device -d lumatv -i` | Pass. Model `55UR7500PSC`, SDK `10.3.2`, firmware `33.31.75`, board `K8LPN_DVB_AJ`, OTA `HE_DTV_W23P_AFADATAA`. |
| App ID conflict on TV | `ares-install -d lumatv --list` before install | Pass. `com.luma.companion2` absent; only `com.luma.tvcompanion` and `org.oted.idlegif` present. |
| Package generation | `npm run package` (`ares-package --no-minify -o ipk .`) | Pass. `com.luma.companion2_0.1.0_all.ipk` created. |
| Package contents | `ares-package -i` and `ares-package -I` | Pass. Package id/version/app/type/main correct; `requiredACG: ["systemconfig.query"]` present in final package. |
| ACG warning handling | First package omitted `requiredACG`; field added; package rebuilt | Pass. Second package completed without the warning. ACG is unsupported on webOS TV 25 or lower, so this is forward compatibility. |
| Installation | `ares-install -d lumatv <ipk>` | Pass. `Success`. |
| Installation verification | `ares-install -d lumatv --list` after install | Pass. `com.luma.companion2` present. |
| Launch | `ares-launch -d lumatv com.luma.companion2` | Pass. `Launched application com.luma.companion2`. |
| Running verification | `ares-launch -d lumatv --running` | Pass. `com.luma.companion2` listed. |
| Live process | `ares-device -d lumatv -r -id com.luma.companion2` | Pass. PID `14087`, memory about 41 MB, process alive. |
| Screenshot capture | `ares-novacom ... --run "which screenshot ..."` | Not available. No screenshot utility found on the TV; visual confirmation is left to the user. |

The passphrase was used only by the previously configured `lumatv` device entry. It was not added to source, documentation, commit messages or test scripts.

## 11. Not performed / still unverified

| Item | Status |
|---|---|
| Visual rendering of dashboard on TV | Not captured. No TV screenshot tool available; user visual confirmation pending. |
| Luna `getSystemInfo` response inside the app | Not captured from the running app. The CLI confirmed the same service data at device level. |
| Magic Remote arrow/OK behaviour on TV | Not verified. |
| `localStorage` availability inside packaged app on TV | Not verified; guarded in code. |
| Cookie behaviour | Source-verified as unsupported for packaged apps; not used. |
| HLS/DASH/DRM/iframe capability | Not tested; excluded from Phase 1. |
| External LG Seller Lounge ID conflict | Not verified. On-TV conflict is cleared; marketplace conflict remains unchecked. |
| ADB automation | Not used. |
| Commit / push | Not performed. |

## Conclusion

All local checks passed, and the app was successfully packaged, installed, launched and confirmed running on the physical LG TV. Visual rendering, Magic Remote input and in-app Luna permission remain unverified because no screenshot or inspector capture was taken. The Phase 1 foundation respected its agreed boundaries. Phase 2 remote connection was later approved and is documented in `docs/COMPANION_2_PHASE_2_REPORT.md`.
