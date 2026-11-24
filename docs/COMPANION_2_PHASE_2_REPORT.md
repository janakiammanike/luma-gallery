# Luma Companion 2.0 — Phase 2 Remote Report

**Date:** 2026-11-24  
**Scope approved:** remote connection only. Browser view and telemetry were not enabled.

> **2026-11-25 update:** the Android host described here was later moved into
> `tvremote2` 3.0.0 as Host mode, and the separate `companion-host` module was removed.
> This report is kept as the historical Phase 2 record.

## Result

The opt-in LAN remote foundation is implemented. The TV app opens one outbound WebSocket
while foregrounded; a new Android host app accepts it and sends `capability` only after
explicit phone approval. Allow-listed `diagnostics.refresh` commands are executed on the
TV and acknowledged. Browser actions are rejected. No token, client key or telemetry is
stored on the TV, and no existing Android production module was changed.

## What was delivered

| Area | Files | Summary |
|---|---|---|
| TV client | `luma-companion-2/js/remote-session.js` | Manual IPv4/port entry, outbound WebSocket, hello/capability handshake, command ACK, bounded reconnect, lifecycle close. |
| TV UI | `luma-companion-2/index.html`, `css/styles.css`, `js/app.js` | Connection card with IP, port, Connect/Disconnect, session prefix, handshake state, retry attempt and protocol counters. Inputs keep normal caret keys; OK/Enter submits. |
| TV validation | `luma-companion-2/js/remote-contract.js` | Existing v1 envelope boundary reused for every incoming frame. |
| Android host | `companion-host/` | New module `com.luma.companionhost`, foreground-only Activity, minimal WebSocket server, pending approval, capability, command ACK, reject and rate-limit paths. |
| Build wiring | `settings.gradle.kts` | Added the new module only; existing modules untouched. |
| Documentation | `docs/REMOTE_INTEGRATION_CONTRACT.md`, `docs/COMPANION_2_ARCHITECTURE.md`, this report | Contract, architecture and evidence updated. |

## Protocol actually used

```text
TV -> ws://<phone-ip>:8787/luma/companion/v1
TV -- hello(session_id, producer, app_version, capabilities) -->
Phone -- pending approval shown to user
Phone -- capability(accepted_version=1, telemetry_enabled=false) -->
TV -- command(action=diagnostics.refresh, request_id) -->
TV <-- ack(ref_id=request_id, status=accepted|rejected) --
```

No pairing token is exchanged in this release. The TV stores no credential. The approval
is bound to the `session_id` displayed as a short prefix on the phone. Token rotation
remains a later Android-owned feature.

## Safety decisions

- The TV still has no TCP listener, UDP beacon, JS service or background worker.
- The channel is closed on `pagehide`, `visibilitychange` and manual disconnect.
- Only `diagnostics.refresh` executes. `browser.open`, `browser.close` and `app.focus` are rejected.
- Incoming and outgoing frames are capped at 64 KB and must pass the v1 envelope rules.
- Repeated invalid frames close the channel; command and message rates are bounded.
- No payload contents, addresses, identifiers or credentials are logged.
- Telemetry remains off; no Room dependency was added.

## Verification summary

- `npm run check`: pass.
- Contract validator: 11/11 pass.
- Storage guard: 8/8 pass.
- DOM/metadata: 28 referenced IDs present, 0 missing, 10 focusable elements.
- Remote session stub harness: 19/19 pass.
- `.\gradlew :companion-host:assembleDebug --no-daemon`: build successful; APK present.
- `.\gradlew :tvremote2:assembleDebug --no-daemon`: build successful; old app unchanged.
- TV package: rebuilt with corrected phone IP `192.168.1.3`, installed, launched, listed as running, PID `15834` alive.

Detailed evidence is in `docs/PHASE_2_REMOTE_TEST_RESULTS.md`.

## Remaining acceptance gate

A real-device acceptance test is still required:

1. Build/install `companion-host-debug.apk` on the phone — done via ADB; package present.
2. Open the host app and tap Start with port `8787`.
3. On the TV, keep the prefilled phone address `192.168.1.3` or correct it, then tap Connect.
4. Confirm the phone shows a pending peer, then tap Approve.
5. Confirm the TV shows `Connected · session ready`.
6. Tap **Refresh TV** on the phone to send `diagnostics.refresh`, then verify the TV's `Last updated` changes and the phone log shows `ack accepted`.
7. Hide the TV app and confirm the phone shows the peer closed.

Until those steps are confirmed, the remote connection is implemented but not accepted as
end-to-end working. Browser and telemetry features remain gated and untouched.
