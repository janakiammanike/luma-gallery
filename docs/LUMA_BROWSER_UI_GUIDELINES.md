# Luma Browser UI Guidelines

**Date:** 2026-11-25  
**Applies to:** `luma-companion-2` 0.1.0, packaged webOS app `com.luma.companion2`  
**Status:** implemented UI foundation plus real media-capability detection, dedicated audio player,
local playback diagnostics and TV-safe local search results. Live TV rendering, Enter/Search and real
result-button clicks were verified through DevTools; physical Magic Remote input is still a manual
acceptance item.

## 1. Design intent

Luma Browser is a desktop-browser-inspired interface adapted for a large TV screen. It uses
an original Luma identity: deep charcoal surfaces, dark graphite chrome, Luma purple accent,
soft grey secondary text and restrained borders. It does not copy Chrome, Edge, Arc or any
streaming-app branding.

The browser remains inside the existing app architecture:

- HTML5 + CSS3 + ES5-style plain JavaScript only.
- No Enact, React, Vue, framework, build transpiler or remote CDN asset.
- No change to Android production code in this UI work.
- No telemetry. The toggle from the earlier short-lived experiment was removed.

## 2. Tokens and visual language

The legacy token set in `css/styles.css` is reused. `css/browser.css` only adds browser
layout tokens and responsive scaling.

| Token | Value | Use |
|---|---|---|
| `--bg` | `#08060e` | Page/website viewing surface |
| `--surface` | `rgba(22, 16, 36, 0.92)` | Cards and panels |
| `--surface-strong` | `#171026` | Navigation chrome |
| `--accent` | `#8b5cf6` | Primary action, active tab, focus |
| `--accent-bright` | `#b99cff` | Icons, labels, focus glow |
| `--accent-deep` | `#5b21b6` | Gradients and shadows |
| `--text` | `#f5f2ff` | Primary text |
| `--muted` | `#aaa0c2` | Secondary text |
| `--border-focus` | `#b69cff` | 4 px visible focus outline |

Accent choices in Settings change the three accent variables at runtime. The theme is always
dark; there is no unimplemented light-theme switch.

## 3. Layout

```text
┌──────────────────────────────────────────────┐
│ tab bar: logo · tabs · + · overflow · Settings│
├──────────────────────────────────────────────┤
│ ‹ › ↻ ⌂ | security · address · clear · Go | ☆ ▶ ☰ ⚙ │
├──────────────┬───────────────────────────────┤
│ side menu    │ website frame OR browser screen│
│ (collapsible)│ home / bookmarks / history /   │
│              │ tabs / settings / video /      │
│              │ audio / media capabilities     │
├──────────────┴───────────────────────────────┤
│ status bar: page · tabs · session · network    │
└──────────────────────────────────────────────┘
```

- The address bar is the largest and most important control in the chrome.
- The website frame gets the full remaining viewport: no card wrapper, no decorative border,
  no forced 520 px height.
- The side menu is collapsed by default so it does not permanently consume viewing area.
- The status bar is informational only; it contains no fake action.

## 4. Typography and target sizes

- Font stack: `"LG SmartTV", "Noto Sans", "Segoe UI", Arial, sans-serif`.
- 1080p baseline: tab title 21 px, address 25 px, navigation icons 58 px, side items 66 px.
- 720p fallback: density is reduced through `@media (max-width: 1400px)` but text remains
  at least 15 px for labels and 21 px for the address field.
- 4K tiers: `@media (min-width: 2500px)` and `(min-width: 3500px)` scale chrome, fonts, icons,
  grids and sidebar width. No `clamp()` is used because older webOS engines may not support it.
- Interactive targets are never smaller than 40 px at 1080p; icon buttons are 58 px.

## 5. 4K and DPR policy

1. The app reads `window.innerWidth`, `window.innerHeight` and `window.devicePixelRatio`
   through `LumaBrowserController.updateViewportInfo()` and publishes them to
   `window.LumaDiagnosticsInfo`. Settings and Home display these detected values.
2. CSS pixels are not assumed to equal physical pixels. The 4K tiers respond to CSS viewport
   width, not to a hard-coded 3840 px assumption.
3. Websites are **not** forced to render at 3840 × 2160. Each sandboxed iframe uses the natural
   viewport it receives and keeps the site's own responsive layout and aspect ratio.
4. Low-resolution video is not upscaled by Luma. The dedicated player uses the source's
   intrinsic dimensions; website video stays inside the website.
5. No claim of native 4K video decoding is made without physical TV capture. The Media Capabilities
   screen reports physical pixels inferred from `screen.width × devicePixelRatio` and labels them
   as inferred.

## 5A. Media engines — evidence policy

The app includes three real, local engines; none of them fabricates a platform feature:

1. **HTML5 video engine.** A dedicated player for direct MP4/WebM/OGV/M4V/MOV addresses with
   play, pause, ±10 s, seek, volume, fullscreen (when the engine exposes it), optional WebVTT
   subtitles, audio-track reporting when `video.audioTracks` exists, and local buffering/recovery/
   decode counters. Codec rows for H.264, HEVC, VP9, AV1, HLS and DASH come from
   `video.canPlayType()`; MSE rows come from `MediaSource.isTypeSupported()`. A blank result is
   shown as `not reported`, never as a checkmark.
2. **Hi-Res audio engine.** A dedicated player for direct MP3/M4A/AAC/FLAC/WAV/OGG/OPUS addresses
   with play, pause, ±10 s, seek, volume and local status. FLAC/PCM support is reported from
   `audio.canPlayType()`. Bit depth and 24-bit/96 kHz certification are explicitly labelled
   `not exposed to HTMLMediaElement`; they are never invented.
3. **Surround and spatial audio.** Output paths (TV speakers, Bluetooth, eARC), Dolby Digital,
   Atmos, DTS:X and multichannel passthrough are shown as `not exposed to web app` because a
   packaged web app has no standard API to verify them. This is an honest absence, not a missing
   feature toggle.

Native HLS/DASH without a bundled demuxer is not claimed. DRM, YouTube account/video playback and
audio extraction from websites are not claimed; YouTube remains on the frame-block list because
the full site sends X-Frame-Options/CSP on this TV build. A YouTube search result is labelled
`Blocked in-app`; selecting it shows the honest notice instead of opening the framed site.

## 6. Screen rules

### Home / New Tab
- Search field records a normal Bing search URL in history but renders a **local results screen**
  from the DuckDuckGo Instant Answers JSON endpoint. Bing, Google and DuckDuckGo pages themselves
  are not framed for search results on this TV build; blocked destinations are rejected before load
  with a visible notice.
- Quick Access starts with Bing, Wikipedia and Example, and is user-editable; it is not a
  navigation restriction. Blocked shortcut URLs are removed during settings migration.
- Frequently visited, Recently opened, Bookmarks and Recent tabs are separate sections.
- Empty sections show a plain-language empty state, never a dead button.

### Search results
- Results are native Luma buttons, not links inside a framed search engine. A real click opens a
  safe destination in the current tab after the same absolute `http(s)` validation; a blocked host
  never creates a frame.
- Each row shows the host and an `Opens in tab` or `Blocked in-app` badge. Loading, empty and error
  states are explicit, with a working retry.
- The source is stated on screen: local DuckDuckGo Instant Answers, not a full search page.

### Bookmarks and History
- Search, open, edit and remove are real local actions.
- History is grouped by Today / Yesterday / date. Query parameters are stripped from the
  displayed address through `LumaBrowserModel.sanitizeUrl()`.
- Clearing history is immediate and TV-friendly: no browser `confirm()` dialog.

### Tab Manager
- Cards show favicon letter, title, saved address and honest loading state.
- Live previews are deliberately absent: cross-origin frame content cannot be read.

### Settings
- Implemented: homepage, search results source (TV-safe DuckDuckGo answers), startup, zoom, accent,
  sidebar-at-startup, clear history, reset shortcuts, dedicated-player autoplay and volume.
- Not shown as controls in Settings: cookies, site permissions, downloads, codec and DRM options.
  The app states why each is absent. An optional WebVTT URL belongs to the video player, not to
  global settings, because it applies to one file.
- **Media Capabilities** is reachable from Settings and the side menu. It renders only values the
  TV engine actually reported.

### Dedicated Luma Video Player
- Opens direct `.mp4`, `.webm`, `.ogv`, `.ogg`, `.m4v`, `.mov` addresses only.
- Shows play/pause, seek, time, ±10 s, volume and fullscreen where the engine exposes it.
- Shows buffering and error states, and fades the control overlay when idle while keeping
  focus-within fully visible.
- It never claims control over a website's own video player.
- WebVTT subtitles are loaded only from an absolute http(s) URL the TV engine can fetch; a failed
  fetch is reported in the player instead of being hidden.
- `audioTracks` are shown only when the browser exposes them; otherwise the player says so.

### Dedicated Luma Audio Player
- Opens direct `.mp3`, `.m4a`, `.aac`, `.flac`, `.wav`, `.ogg`, `.opus` addresses only.
- Play/pause, ±10 s, seek, volume and local buffering/decode status are real element controls.
- No fake Hi-Res badge: bit depth, sample-rate file measurement and surround output are absent
  unless a web API exposes them.

### Companion screen
The Phase 1/2 dashboard is preserved as the **Companion** side-menu screen. It keeps the LAN
connection form, platform information and diagnostics. This avoids replacing the existing
project architecture.

## 7. Loading, error and empty states

| State | Behaviour |
|---|---|
| Initial launch | Home screen with detected engine/viewport foot and real shortcuts. |
| Page loading | Overlay with sanitized address and a working Stop button. |
| Slow / blocked frame | 18 s watchdog reports that the site may block in-app framing, with Retry. |
| Known framing blocker | `browser-notice` toast explains the refusal before any iframe is created; the app shell stays alive. |
| Search loading / error / empty | Local results screen shows a spinner, a retry action or a plain phrase suggestion; no search-engine frame is left blank. |
| Offline | Status bar shows `OFFLINE`; Home/connection text explains that local addresses can still be used. |
| Navigation failure | Overlay gives a plain message and Retry; technical codes are not primary text. |
| No bookmarks / history | Empty state with the action that creates the first entry. |
| No open tabs | Model always keeps one tab; tab manager has an empty state for corrupted saved state. |
| Video unsupported | Honest message listing the direct file types; website video remains with the website. |
| Video decode error | Player error text; no codec/DRM option is invented. |

## 8. Performance

- One iframe per open tab, maximum 8 tabs; stale frames are destroyed on tab close.
- No page-content polling, no screenshot capture, no preview generation.
- Renders only on model changes; input typing does not re-render the whole chrome.
- One CSS animation keyframe only: the loading spinner, reused by the search results spinner.
  Transitions are 120–200 ms.
- No expensive blur filters, large images or decorative gradients inside website area.

## 9. Honest platform boundary

- Sandbox is `allow-scripts allow-forms allow-popups`; no `allow-same-origin`, no top-level
  navigation and no page data reads. Popups inherit the same sandbox; search does not rely on them.
- Third-party sites may refuse framing through X-Frame-Options or CSP. On LG webOS a blocked
  frame can replace the whole app with `loaderror.html?errorCode=-27`. Known offenders are
  rejected in `browser-model.js` before the iframe is created, and the reason is shown in the
  `browser-notice` toast. Sites outside that denylist are still not promised to work.
- No system-browser launch is attempted. The old LG browser guide URL was 404 and the exact
  Luna payload was not verified.
- No DRM, HLS/DASH, download API, cookie editor, site-permission editor or native 4K claim.

## 10. Files

| File | Role |
|---|---|
| `index.html` | Browser shell, Companion dashboard, script/style order. |
| `css/styles.css` | Existing Luma tokens, dashboard, cards, controls. |
| `css/browser.css` | Browser shell, 4K/1080p/720p responsive tiers, focus states. |
| `js/browser-model.js` | Tabs, history stacks, bookmarks, shortcuts, settings, persistence, framing denylist and safe search endpoints. |
| `js/browser-view.js` | URL validation, sandboxed per-tab iframes, load/error callbacks. |
| `js/browser-components.js` | Reusable render-only UI components. |
| `js/browser-controller.js` | Events, focus, screen routing, video/audio, Android command states. |
| `js/media-capabilities.js` | Read-only runtime codec, display, MSE and Web Audio detection. |
| `js/app.js` | Lifecycle, diagnostics, LAN session, Companion dashboard. |
