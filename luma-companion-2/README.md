# Luma Companion 2.0

Independent, foreground-only webOS TV app for the Luma ecosystem. Phase 1 delivered the
basic dashboard, Phase 2 added an opt-in LAN remote session, and Phase 3 established a safe
in-app frame. The current build adds the full premium Luma Browser UI on top of that boundary.
Telemetry remains disabled; no collection source is active.

The Android endpoint is **Host mode inside `tvremote2` 3.0.0**, not a separate app.

## Identity

- App ID: `com.luma.companion2`
- Version: `0.1.0`
- Type: packaged basic web app (`type: "web"`)
- UI: plain HTML5, CSS3 and ES5-style JavaScript — no Enact, React, Vue or other framework
- Physical TV: LG `55UR7500PSC`; CLI reports SDK `10.3.2`, firmware `33.31.75`

## What is included

- Premium dark browser shell: tab bar, address/search bar, collapsible side menu and status bar
- Real screens: Home, page framing, Bookmarks, History, Tab Manager, Settings, direct-video player and the preserved Companion dashboard
- Magic Remote/D-pad spatial focus, visible 4 px focus outline, focus restoration and staged Back handling
- Responsive chrome tiers for 1280 × 720, 1920 × 1080 and 4K CSS viewports; websites keep their own responsive layout
- Read-only platform detection through `navigator.userAgent`, `PalmSystem` / `webOSSystem`, and optional official `webOS.service.request`
- Foreground lifecycle cleanup on `visibilitychange`, `webOSLaunch`, `webOSRelaunch`, `pageshow`, and `pagehide`
- Strict versioned message validation reused by every remote frame
- Safe local counters with storage-failure fallback
- Outbound WebSocket client at `ws://<phone-ip>:8787/luma/companion/v1`
- Manual IPv4/port entry and explicit phone approval; Android Host mode exposes `diagnostics.refresh`, `browser.open` and `browser.close` with ACK
- TV-side prepared browser/video command states for a future Host-mode UI; not claimed as a tested phone flow
- One sandboxed iframe per tab with absolute http(s) URL validation; no page content is read or reported
- Local bookmarks, history, shortcuts, tabs and settings under `localStorage` key `luma.browser.v1`

## Explicitly excluded

- No system-browser launch, proxy, HLS/DASH/DRM player
- No TCP listener, UDP beacon, HTTP server or SSAP call on the TV
- No pairing token, client key or legacy `TvStore` import stored on the TV
- No JS service, Android background service or background telemetry
- No Firebase, analytics SDK, cloud endpoint or remote logging
- No change to `tvremote`, `tvcompanion`, gallery `app/` or `webui/`

`tvremote2` is changed only to add Host mode and raise its version to 3.0.0; its existing remote
path is preserved. Its Host-mode UI currently sends only the three original commands. Extra
browser/video commands are prepared TV states, not a verified phone integration.

## Local static check

```powershell
cd "D:\apps\photos galary app\luma-companion-2"
npm run check
```

Local verification harnesses in the temporary workspace cover the contract (11/11), storage
(8/8), DOM/metadata wiring, URL/frame lifecycle (38/38), browser model (66/66), components
(61/61), controller/focus (41/41) and remote session (26/26). See `docs/BROWSER_UI_TEST_RESULTS.md`.

## Package and install

The official webOS CLI `@webos-tools/cli` 3.2.6 is installed globally in this
workspace. The configured TV device is named `lumatv`.

```powershell
cd "D:\apps\photos galary app\luma-companion-2"
npm run package
ares-install -d lumatv ipk/com.luma.companion2_0.1.0_all.ipk
ares-launch -d lumatv com.luma.companion2
```

For debugging, keep the non-minified package (`npm run package`). Use
`ares-inspect --device lumatv --app com.luma.companion2 --open` while the app runs.
The user's Developer Mode passphrase is stored only in the CLI device list and
is not written to this project.

The Phase 2 IPK was packaged, installed, launched and confirmed running on the
physical LG TV. The current full-browser IPK was also installed and launched on `lumatv`; a live
DevTools inspection confirmed the tab bar, address bar, Home screen and 56 focus markers at
1920 × 1080 CSS / DPR 2. The `errorCode=-27` Enter/Search crash on Google and other frame-blocking
sites was reproduced and fixed: the app now refuses those hosts before creating an iframe, shows a
visible notice, and loads Bing searches successfully. Physical Magic Remote input, in-app Luna
permission and the real TV-to-phone handshake still need manual acceptance.
See `docs/BROWSER_UI_TEST_RESULTS.md`.

## Remote session test flow

1. Build and install `../tvremote2/build/outputs/apk/debug/tvremote2-debug.apk` on the phone.
2. Open Luma TV Remote 3.0.0, then choose **Open Companion Host** on the Connect screen (or Companion Host from More).
3. Tap Start with port `8787`. Keep the screen open; the endpoint stops when it pauses or closes.
4. On the TV, confirm the phone IP field, for example `192.168.1.3`, then tap Connect.
5. On the phone, tap **Approve TV** when the pending peer appears.
6. The TV should show `Connected · session ready`. It shows `Waiting for phone approval` until capability arrives.
7. Use Refresh TV, Open Page or Close Page in Host mode. Only absolute http(s) addresses are sent; extra TV-side browser/video states are not yet exposed by the phone UI.
8. Tap Disconnect or hide either app to close the channel, clear frames and pause video.

No credential is typed on the TV. The `hello` carries only producer, app version,
session id and capabilities.

## Documentation

See the repository `docs/` folder for the audit, protocol, webOS capability
matrix, browser-engine research, telemetry design, integration requirements and
Phase 1/Phase 2/Phase 3 results and reports.

Current browser UI references:

- `docs/LUMA_BROWSER_UI_GUIDELINES.md`
- `docs/BROWSER_SCREEN_STRUCTURE.md`
- `docs/MAGIC_REMOTE_NAVIGATION.md`
- `docs/BROWSER_UI_TEST_RESULTS.md`
