# Magic Remote and Keyboard Navigation

**Date:** 2026-11-25  
**Applies to:** `luma-companion-2` browser shell  
**Status:** implemented in `js/browser-controller.js`; hardware Magic Remote input not captured.

## 1. Principles

1. Every Luma control is reachable with arrow keys and OK/Enter.
2. Focus is always visible: 4 px `--border-focus` outline plus a soft accent glow.
3. After a re-render, focus returns to the same logical control instead of jumping to the top.
4. A predictable explicit target wins over automatic restoration.
5. The remote never exits the app on Back while a browser screen is active.
6. Text fields keep normal typing behaviour; arrows inside a field do not move Luma chrome.

## 2. Focusable set

`elementsWithFocus(root)` collects elements with `data-focusable="true"` from the visible region
and drops elements that are `disabled` or have a zero-size bounding box. The controller queries
these regions in order for restoration:

```text
#browser-topbar → #browser-navbar → #browser-sidebar → #browser-screen
```

The iframe host and the Companion dashboard are not part of this set while a browser screen is
active. On the Companion screen, the existing dashboard controls keep their own focus attributes
and `app.js` behaviour.

## 3. Focus key and restoration

`focusKey(element)` produces a stable logical address:

| Element type | Key |
|---|---|
| Has an `id` | `id:<id>` |
| Generated control | `act:<data-action>:<data-id>:<data-value>` |

Before `render()`, the controller remembers `document.activeElement` by key. After rendering it
does one of two things:

1. **Explicit request** — `applyFocusRequest()` focuses the requested control. Requests are set
   *before* the model mutation so the new DOM exists when rendering finishes.
2. **Restoration** — `findByKey()` searches the four chrome/screen regions and refocuses the same
   logical control.

Explicit requests used today:

| Request | When |
|---|---|
| `address` | new tab, reload, Home, address-bar navigation, suggestion chosen |
| `home-search-input` | Home button returns to New Tab |
| `overlay` | load error, frame error, watchdog timeout |
| `tab-add` | active tab is closed |

`Ctrl/Cmd+L` focuses `#address-input` directly and opens suggestions; it does not wait for a
re-render.

## 4. Spatial movement

Arrow keys call `moveFocus(direction)`:

- The currently focused element's centre is used as the origin.
- For each candidate, the controller computes the primary-axis distance in the requested
  direction and penalises the cross-axis distance by `1.4`.
- Candidates with no forward component (`<= 2` px) are ignored; focus never wraps around the
  screen unexpectedly.
- The lowest-scoring visible, enabled candidate receives focus.
- If nothing has focus yet, the first focusable element receives it.

This gives a stable reading order across the tab bar, address bar, side menu and screen grids at
720p, 1080p and 4K.

## 5. Key map

| Key | Code | Behaviour |
|---|---|---|
| Left / Right / Up / Down | 37 / 39 / 38 / 40 | Spatial focus move; default prevented. |
| OK / Enter | 13 | Clicks the focused non-iframe control. |
| Space | 32 | Same activation as Enter; default prevented. |
| Back | 461 | Closes suggestions → closes sidebar → returns to Home → on a page goes back. Always prevented so webOS does not exit the app. |
| Escape | 27 | Same staged Back behaviour outside text fields. |
| Ctrl/Cmd + L | 76 with modifier | Focus address field and open suggestions. |
| F5 | 116 | Reload the active page. |
| Alt + Left | 37 with Alt | Back. |
| Alt + Right | 39 with Alt | Forward. |
| Enter in address/Home field | 13 | Navigates using the configured engine or absolute URL. |
| Escape/Back in text field | 27 / 461 | Closes suggestions and blurs the field. |

Arrow keys inside `INPUT`, `TEXTAREA` or `SELECT` are left to the field. Browser F5 and Alt+Left
are intercepted only inside the Luma shell; a focused website iframe owns its own key events and
is deliberately not inspected.

## 6. Suggestions

Typing or focusing `#address-input` / `#home-search-input` opens the suggestion layer. Items come
from Home shortcuts, bookmarks and local history, with a search-engine entry as the final item.
The list is capped at six entries. `focusout` closes it after 180 ms unless focus moved to the
other address field. Choosing a suggestion closes the layer and requests address focus.

## 7. Loading, error and overlay focus

When a navigation starts, the controller shows a loading overlay with a working **Stop** button
and arms an 18 second watchdog. On frame error, watchdog timeout or load failure it:

1. clears the watchdog,
2. requests overlay focus *before* marking the error,
3. renders the error card with **Try again**,
4. leaves the address bar intact so the user can correct the URL.

Focusing the overlay button is therefore immediate after the re-render.

## 8. Video controls

The dedicated player is reachable through the side menu, navigation bar or the
`screen-video` action. For direct video files:

- Play, Pause, −10 s, +10 s, seek, volume and fullscreen are focusable.
- The time label and buffering/error text are informational, not fake controls.
- Controls fade after 6 seconds only while focus is outside `.video-shell`; `focus-within`
  keeps them visible.
- `fullscreen` reports an honest message if the engine does not expose the API.
- Website video is never controlled by Luma; the unsupported state says so plainly.

## 9. Prepared Android command states

These are **TV-side states only** for a future Host-mode UI. The phone app currently exposes
`diagnostics.refresh`, `browser.open` and `browser.close`; no extra phone control is claimed as
working until the protocol is tested on real devices.

| Action | Args | TV behaviour |
|---|---|---|
| `diagnostics.refresh` | `{}` | Re-runs platform probes. |
| `browser.open` | `url` or `address` | Validates absolute http(s), navigates active tab. |
| `browser.close` | `{}` | Clears frames and returns Home. |
| `browser.back` / `browser.forward` / `browser.reload` / `browser.home` | `{}` | Mirrors the corresponding UI action. |
| `browser.new_tab` | optional `url` | Opens a new tab, focus is requested on address. |
| `browser.close_tab` | optional `id` | Closes given or active tab, focus moves to `+`. |
| `browser.next_tab` / `browser.previous_tab` | `{}` | Cycles tabs with wrap-around. |
| `video.play` / `video.pause` | `{}` | Only for a direct video file in the active tab. |
| `video.seek` | `position` in percent | Only for a direct video file. |
| `app.focus` | any | Rejected as reserved. Never changes another app's focus. |

Commands are rate-limited to 20 per 10 seconds. Unknown or disabled actions receive a rejected
ACK and execute nothing.

## 10. Lifecycle

- `app.js` calls `LumaBrowserController.init()` once during startup.
- On hide/pagehide, `suspend()` closes suggestions, cancels the load watchdog, pauses video and
  hides frames. `LumaRemoteSession.suspend()` closes the LAN channel.
- On show/pageshow, `resume()` re-applies the accent, re-renders and restores focus.
- The earlier single-frame `LumaBrowserView.snapshot()/subscribe()/init()` APIs are gone; no
  active file calls them.

## 11. Verification

`browser-controller-check.js` verifies 38/38 cases with a stub DOM, including chrome rendering,
tab cycling, Ctrl+L address focus, Back-key app-exit prevention, direct-video play,
`app.focus` rejection, suspend pause and resume rendering. `dom-meta-check.js` additionally
asserts that `app.js` wires `LumaBrowserController.init()` and `.command()`, that no stale
browser API remains, and that script order is correct.

Hardware Magic Remote input on LG `55UR7500PSC` remains a manual acceptance item; no remote
key capture is available from this workspace.
