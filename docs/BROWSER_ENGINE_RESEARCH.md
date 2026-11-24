# Browser Engine Research — webOS TV target

**Date:** 2026-11-25  
**Purpose:** keep `Luma Companion 2.0` inside features that the target TV web engine can safely execute, and document why Phase 3 uses an in-app frame rather than a system-browser launch.

## Engine facts

Official LG documentation now publishes a per-release web engine table:

| webOS TV release | Documented web engine |
|---|---|
| TV 24 | Chromium 108 |
| TV 25 | Chromium 120 |
| TV 26 | Chromium 132 |

Source: https://webostv.developer.lge.com/develop/specifications/web-api-and-web-engine

The older LG document also contains a legacy user-agent example with `Chrome/53.0.2785.34`. Earlier drafts of this document treated Chromium 53 as the target baseline. That was an over-conservative reading of one sample user agent, not a statement about this physical TV.

The CLI reports SDK `10.3.2` for device `lumatv`, model `55UR7500PSC`, firmware `33.31.75`. The SDK number is **not** a direct webOS TV year or Chromium version. No device-reported user agent or engine version has been captured yet.

**Decision:** Chromium 53 remains the *coding* baseline. It costs nothing and keeps the ES5/CSS rules already used by the dashboard valid on both old and new engines. No claim is made that the physical TV actually runs Chromium 53.

## Browser approach decision

Three options were considered for Phase 3:

| Option | Decision | Reason |
|---|---|---|
| System browser launch through Luna `ApplicationManager` | Not used | The old LG browser guide URL returns 404 and the exact `launch` payload for a URL was not confirmed for this SDK. It would also hand control to another app, which conflicts with the foreground-only, single-app boundary. |
| External WebView plugin or Enact web app | Not used | No framework or native plugin is allowed by the project constraints. |
| In-app `<iframe>` with validated absolute http(s) URL | Chosen | Uses only HTML5 already present in the app. No Luna dependency, no JS service, no extra permission, and the frame is closed when the app hides. |

The official Application Manager reference was reviewed, but it was not used to justify an unverified launch call. The browser guide URL `https://webostv.developer.lge.com/develop/guides/browser` returned 404 during research, so no system-browser behaviour is assumed.

## iframe constraints

The frame is deliberately constrained:

- only absolute `http:` or `https:` URLs are accepted,
- credentials in the URL (`user:pass@host`) are rejected,
- control characters, leading/trailing whitespace and URLs over 2048 characters are rejected,
- `sandbox="allow-scripts allow-forms"` is applied; same-origin access, top-level navigation and popups are **not** granted,
- the app never reads the frame's document, cookies, storage or navigation history,
- visited URLs are never written to telemetry, logs or local storage,
- `browser.close` resets the frame to `about:blank`,
- hiding or disconnecting the TV app closes the page.

Many real sites send `X-Frame-Options` or a `Content-Security-Policy` that refuses framing. DRM-protected playback is also not supported. The UI reports `Loaded` only when the frame itself fires `load`; it does not promise that a specific site will render.

## JavaScript baseline

The code stays in ES5-style JavaScript to remain valid on every Chromium version listed above.

| Used today | Why it is safe |
|---|---|
| `var`, function expressions, IIFEs | Stable across old and new Chromium. |
| `JSON.parse`, `JSON.stringify` | Sufficient for validation and counters. |
| `Object.prototype.toString.call(value)` | Reliable object detection without newer helpers. |
| `isFinite`, `Math.floor`, `Date.now` | Stable. |
| `document.createElement('a')` URL parsing | Available in all Chromium versions; used only for local validation. |
| `addEventListener` with capture flag | Stable for TV lifecycle and key events. |
| `keyCode` / `which` for remote keys | More predictable on older TV engines than `event.key`. |
| `navigator.onLine` and online/offline events | Stable, but treated as a hint, not proof of reachability. |

Deliberately avoided:

| Avoided | Reason |
|---|---|
| `async` / `await` | Chrome 55+ feature. |
| `Object.values` / `Object.entries` | Chrome 54+ features. |
| `Array.prototype.flat` | Much newer. |
| `String.prototype.padStart` | Chrome 57+. |
| Template literals, arrow functions, classes | ES5 keeps the package predictable across the whole engine range. |
| `fetch` for LAN access | `file://` origin and CORS behaviour on this TV are unverified; the app uses WebSocket only. |
| `postMessage` bridge with the framed page | Would require reading foreign page content, which is explicitly excluded. |

## CSS baseline

Chromium 53 supports CSS custom properties, flexbox, gradients, transforms, transitions, `calc()`, `inline-flex`, box shadows and attribute selectors. Those remain the only layout tools used.

Deliberately avoided:

| Avoided | Reason |
|---|---|
| CSS Grid (`display: grid`) | Unprefixed Grid shipped in Chrome 57. |
| Flexbox `gap` | Chrome 84+. Margins are used instead. |
| `inset` shorthand | Chrome 87+. Explicit offsets are used. |
| `aspect-ratio` | Chrome 88+. |
| `min()` / `max()` / `clamp()` | Chrome 79+. |
| `:focus-visible` | Chrome 86+. The dashboard uses `:focus`. |
| `backdrop-filter` | Chrome 76+. |

The browser frame is a plain block-level `<iframe>` with an explicit `height: 520px`; no newer sizing feature is used.

## Storage behaviour

Packaged webOS apps run from `file://`. Official LG docs state that cookies are not supported for packaged apps. `localStorage` may exist but can throw. The app stores only two aggregate counters through guarded try/catch wrappers, and never stores URLs or identifiers.

## Platform bridges

`PalmSystem` is checked first, then `webOSSystem`. `webOS.fetchAppInfo` and `webOS.service.request` are used only when present and are guarded. The Luna `getSystemInfo` probe is one-shot and cancelled on deactivation. No Luna call is required for the browser view.

## Lifecycle events

Registered events: `webOSLaunch`, `webOSRelaunch`, `visibilitychange`, `webkitvisibilitychange`, `pageshow`, `pagehide`. On hide, timers, platform probes, the WebSocket channel **and the browser frame** are closed.

## Network posture

One plain `ws://` outbound client is used with a fixed path and no credentials in the URL. The browser frame may cause the TV engine to make its own HTTPS request to the chosen site; that is normal page loading, not a companion-protocol message. It is never treated as telemetry, and no page content is read back into the app.

The live TV-to-phone WebSocket upgrade is still not proven by capture. It must be accepted manually before remote control is trusted.

## Verification status

| Item | Status |
|---|---|
| Official per-release Chromium table | Source-verified from LG documentation. |
| Physical TV engine version | Not captured; SDK `10.3.2` is not a Chromium version. |
| ES5/CSS constraints applied to all Phase 3 source | Verified by static check and source review. |
| In-app frame implementation | Implemented in `js/browser-view.js`; static check passed. |
| URL validation unit coverage | Covered by local harness review; no URL is logged. |
| Physical frame rendering on LG `55UR7500PSC` | Not captured; no screenshot tool available on the TV. |
| Arbitrary site framing / DRM | Explicitly not promised. |
| Outbound WebSocket from packaged TV app | Implemented; live TV-to-phone upgrade still not tested. |
