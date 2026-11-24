# Luma Browser UI Guidelines

**Date:** 2026-11-25  
**Applies to:** `luma-companion-2` 0.1.0, packaged webOS app `com.luma.companion2`  
**Status:** implemented UI foundation; physical TV rendering not captured in this run.

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
│              │ tabs / settings / video        │
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
5. No claim of native 4K video decoding is made without physical TV capture.

## 6. Screen rules

### Home / New Tab
- Search field uses the configured engine; Google is the default.
- Quick Access is a user-editable shortcut grid, not a navigation restriction.
- Frequently visited, Recently opened, Bookmarks and Recent tabs are separate sections.
- Empty sections show a plain-language empty state, never a dead button.

### Bookmarks and History
- Search, open, edit and remove are real local actions.
- History is grouped by Today / Yesterday / date. Query parameters are stripped from the
  displayed address through `LumaBrowserModel.sanitizeUrl()`.
- Clearing history is immediate and TV-friendly: no browser `confirm()` dialog.

### Tab Manager
- Cards show favicon letter, title, saved address and honest loading state.
- Live previews are deliberately absent: cross-origin frame content cannot be read.

### Settings
- Implemented: homepage, search engine, startup, zoom, accent, sidebar-at-startup, clear
  history, reset shortcuts, dedicated-player autoplay and volume.
- Not shown as controls: cookies, site permissions, downloads, subtitle tracks, codec and DRM
  options. The app states why each is absent.

### Dedicated Luma Video Player
- Opens direct `.mp4`, `.webm`, `.ogv`, `.ogg`, `.m4v`, `.mov` addresses only.
- Shows play/pause, seek, time, ±10 s, volume and fullscreen where the engine exposes it.
- Shows buffering and error states, and fades the control overlay when idle while keeping
  focus-within fully visible.
- It never claims control over a website's own video player.

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
- One CSS animation only: the loading spinner. Transitions are 120–200 ms.
- No expensive blur filters, large images or decorative gradients inside website area.

## 9. Honest platform boundary

- Sandbox is `allow-scripts allow-forms`; no `allow-same-origin`, no top-level navigation,
  no popups and no page data reads.
- Third-party sites may refuse framing through X-Frame-Options or CSP; the app reports the
  frame's own load result and never claims a site works.
- No system-browser launch is attempted. The old LG browser guide URL was 404 and the exact
  Luna payload was not verified.
- No DRM, HLS/DASH, download API, cookie editor, site-permission editor or native 4K claim.

## 10. Files

| File | Role |
|---|---|
| `index.html` | Browser shell, Companion dashboard, script/style order. |
| `css/styles.css` | Existing Luma tokens, dashboard, cards, controls. |
| `css/browser.css` | Browser shell, 4K/1080p/720p responsive tiers, focus states. |
| `js/browser-model.js` | Tabs, history stacks, bookmarks, shortcuts, settings, persistence. |
| `js/browser-view.js` | URL validation, sandboxed per-tab iframes, load/error callbacks. |
| `js/browser-components.js` | Reusable render-only UI components. |
| `js/browser-controller.js` | Events, focus, screen routing, video, Android command states. |
| `js/app.js` | Lifecycle, diagnostics, LAN session, Companion dashboard. |
