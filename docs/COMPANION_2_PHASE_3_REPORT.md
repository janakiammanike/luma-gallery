# Luma Companion 2.0 — Phase 3 Browser View Report

**Date:** 2026-11-25  
**Scope approved:** browser view first, telemetry second. This report covers the browser phase only.  
**Update:** the single-frame browser card below was superseded by the full browser shell described
in `LUMA_BROWSER_UI_GUIDELINES.md`, `BROWSER_SCREEN_STRUCTURE.md`,
`MAGIC_REMOTE_NAVIGATION.md` and `BROWSER_UI_TEST_RESULTS.md`. Current numbers are in the last
named file.

## Result

Phase 3 adds a constrained in-app browser view to `com.luma.companion2` and enables
`browser.open` / `browser.close` as allow-listed commands on both sides of the v1 contract.
`app.focus` remains rejected. Telemetry stays disabled.

Per user direction, the Android endpoint is no longer a separate `companion-host` APK. It is
now **Host mode inside `tvremote2` 3.0.0**, reachable from the Connect screen and the More
menu. The old SSAP remote path in `tvremote2` is preserved. The separate `companion-host/`
module was removed from `settings.gradle.kts` and deleted.

## What was delivered

| Area | Files | Summary |
|---|---|---|
| TV browser module | `luma-companion-2/js/browser-view.js` | Absolute http(s) URL validation, credential/control-character rejection, sandboxed iframe, `browser.close` reset, no page-content reads. |
| TV UI | `luma-companion-2/index.html`, `css/styles.css`, `css/browser.css`, `js/app.js` | Initially a browser card; now a full premium TV browser shell with tab bar, address bar, side menu, Home, Bookmarks, History, Tab Manager, Settings, Video, Audio and Media Capabilities. The Phase 1/2 dashboard is preserved as the Companion screen. Frames close on app hide. |
| TV browser modules | `js/browser-model.js`, `js/browser-view.js`, `js/browser-components.js`, `js/browser-controller.js`, `js/media-capabilities.js` | Multi-tab state/persistence, sandboxed per-tab iframe renderer, render-only components, event/focus/video/audio/command controller, and read-only runtime capability detection. Replaces the removed single-frame snapshot/subscribe APIs. |
| TV remote client | `luma-companion-2/js/remote-session.js` | `browser.open` and `browser.close` allow-listed; `app.focus` stays reserved. Later prepared extra browser/video/audio command states on the TV side for a future Host-mode UI. ACK handler accepts `batch_id` and `partial` for the later telemetry phase, but telemetry is inactive. |
| Android host | `tvremote2/src/main/java/com/luma/tvremote2/host/` | `CompanionServer.kt` and `Envelope.kt` moved into the app with the new allow-list and telemetry-ready ACK parsing. |
| Android UI | `tvremote2/src/main/java/com/luma/tvremote2/feature/host/HostScreen.kt`, `host/HostViewModel.kt` | Compose Host mode with Start/Stop, Approve/Reject, Refresh TV, Open Page, Close Page and activity log. Server stops on pause or dispose. |
| Navigation | `Routes.kt`, `RemoteApp.kt`, `ConnectScreen.kt`, `MoreScreen.kt` | New `host` destination, direct entry from Connect, entry in More menu. |
| Version | `tvremote2/build.gradle.kts` | `versionCode = 3`, `versionName = "3.0.0"`. |
| Build wiring | `settings.gradle.kts` | `:companion-host` include removed. |
| Documentation | contract, architecture, capability matrix, browser research, integration requirements, READMEs, this report | Updated for Phase 3 and the `tvremote2` direction change. |

## Browser behaviour

```text
Phone Host mode -- command(browser.open, args.url) -->
TV remote-session -> URL validation -> browser-view -> sandboxed iframe
TV -- ack(ref_id, status=accepted|rejected) --> Phone Host mode

Phone Host mode -- command(browser.close) -->
TV browser-view -> iframe reset to about:blank -> ACK
```

Rules:

- accepted URL: absolute `http:` or `https:`, 1–2048 characters, host present, no credentials, no control characters or surrounding whitespace,
- iframe sandbox: `allow-scripts allow-forms allow-popups`; no same-origin access and no top-level navigation, while helper popups remain sandboxed; search results are local Luma actions, not framed links,
- the app never reads the framed document, cookies, storage or history,
- no visited URL is stored, logged or sent as telemetry,
- hiding the TV app or disconnecting closes the frame,
- sites that refuse framing or require DRM are not promised to work,
- the TV additionally prepares `browser.back`, `browser.forward`, `browser.reload`,
  `browser.home`, `browser.new_tab`, `browser.close_tab`, `browser.next_tab`,
  `browser.previous_tab`, `video.play`, `video.pause`, `video.seek`, `audio.play`, `audio.pause` and
  `audio.seek`. These are TV-side states for a future Host-mode UI; the phone app currently exposes
  only the three commands named above.

## Safety decisions

- No system-browser launch through Luna was used. The official browser guide URL returned 404 and the exact launch payload was not confirmed, so no unverified call is made.
- No Enact, React, Vue, native plugin, JS service, TCP listener, UDP beacon or cloud relay.
- No new Android package or background service. Host mode stops when its screen pauses.
- The existing LG SSAP path in `tvremote2` is unchanged.
- The pending remote handshake state was left as-is; no Approve or Refresh tap was performed in this phase.

## Verification summary

- Original Phase 3: contract 11/11, storage 8/8, 35 referenced IDs with 0 missing, 13 focusable
  elements, remote stub 20/20 and `.\gradlew :tvremote2:assembleDebug --no-daemon` successful.
- Current full browser shell with media and local-search additions: `npm run check` pass;
  DOM/metadata pass with 56 referenced IDs, 0 missing and controller wiring verified;
  `browser-view` 40/40; `browser-model` 87/87; `browser-components` 82/82;
  `browser-controller` 60/60; remote session 29/29.
- `npm run package`: successful. The regenerated IPK is at
  `luma-companion-2/ipk/com.luma.companion2_0.1.0_all.ipk`.
- This exact IPK was installed and launched on `lumatv`. Live CDP evidence includes the browser
  shell, honest Media Capabilities, a real local-result click into Wikipedia and a blocked YouTube
  result that shows a notice without replacing the app. Details are in `docs/BROWSER_UI_TEST_RESULTS.md`.
- No ADB automation was used in either phase.

Historical evidence is in `docs/PHASE_3_BROWSER_TEST_RESULTS.md`; current evidence is in
`docs/BROWSER_UI_TEST_RESULTS.md`.

## Remaining acceptance gate

1. ~~Install the already regenerated `com.luma.companion2` IPK on `lumatv`.~~ Done; live Home-screen rendering was confirmed through DevTools.
2. Install `tvremote2` 3.0.0 on the phone and open **Companion Host** from the Connect screen.
3. Start the endpoint on port `8787` and approve the TV peer.
4. Send Open Page with an absolute http(s) address and confirm the TV frame loads or honestly reports a rejected frame. Known framing blockers such as Google, GitHub, YouTube and DuckDuckGo are now refused locally with a visible notice; Bing, Wikipedia and example.com were confirmed loading on device.
5. Send Close Page and confirm the frame resets.
6. Hide the TV app and confirm both the socket and frame close.
7. Capture Magic Remote input and visual rendering; no TV screenshot tool is available through CLI alone.

Until those steps are confirmed, the browser UI foundation is implemented but not accepted as
end-to-end working. Telemetry remains disabled and gated for a later phase.
