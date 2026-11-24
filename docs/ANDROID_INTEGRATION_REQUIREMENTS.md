# Android Integration Requirements

**Date:** 2026-11-25  
**Status:** Phase 2 remote connection and Phase 3 browser commands are implemented in `tvremote2` 3.0.0. Telemetry remains a future requirement.

These are the conditions that a later Android release must satisfy before Companion 2.0 can connect. They are deliberately stated now so the TV app does not guess protocol details.

## 1. Product boundary

- `tvremote2` (`com.luma.tvremote2`) remains the single Android host app and is now version `3.0.0` (versionCode `3`).
- Keep legacy `tvremote/`, deprecated `tvcompanion/`, gallery `app/` and `webui/` untouched.
- No new package is added to `settings.gradle.kts`; the former `companion-host` module was removed.
- Host mode is opt-in, LAN-only and stopped whenever its screen pauses or is disposed.
- The existing SSAP remote path in `tvremote2` is preserved; Host mode is additive.
- No cloud relay, Firebase, analytics SDK or account sign-in.

## 2. Connection model

The TV browser app cannot listen on a TCP port and must not be treated as a WebSocket server. A future Android build may host a local endpoint; the TV app acts as the client while foregrounded.

| Requirement | Detail |
|---|---|
| Endpoint ownership | Android phone owns the local WebSocket/HTTP endpoint. |
| Discovery | Manual IP/port entry first; any automatic discovery must be separately verified. |
| Direction | TV app connects outbound; no TV listener, no TV UDP beacon, no TV JS service. |
| Lifetime | Connection exists only while the TV app is visible/foreground; cleanup on hidden/termination. |
| Network | Same LAN only; no Internet fallback; no hostname baked into the app. |
| Failure handling | Surface offline/unreachable states; reconnect with bounded exponential backoff; never claim remote control is ready without handshake/ACK. |

## 3. Handshake and pairing

- Fresh pairing only; never import the old plain-text `TvStore` client key.
- Store Android-side keys with Keystore-backed `SecureTvStore` only.
- Pairing token is per-install, random, revocable and never logged.
- TV app stores no pairing key in Phase 1. Future storage must be encrypted or avoided.
- Pairing approval must be explicit on the phone; no silent trust of any LAN device.
- Rotate token on app reinstall, token leak suspicion or user action.

## 4. Versioned message contract

Use `docs/REMOTE_INTEGRATION_CONTRACT.md` as the source of truth.

- Every message has `v`, `type`, `id`, `ts`, `payload`.
- Unknown version/type, oversized frames, malformed JSON and invalid IDs are rejected.
- Commands carry an ACK correlation ID.
- Telemetry batches carry `event_id` values for dedupe.
- Do not send arbitrary SSAP frames from the TV app; use typed commands so the Android side remains the protocol adapter.

## 5. Compatibility with existing LG protocol

The proven LG details in `docs/COMMUNICATION_PROTOCOL.md` remain the Android-side implementation reference:

- Direct SSAP `3001` TLS first, `3000` plain fallback.
- No `Origin` header, self-signed certificate accepted only for this constrained TV connection.
- Non-empty permissions manifest; `pairingType: PROMPT`; client key from secure store.
- Newline-delimited pointer frames; real keyboard/media/app endpoints only.

Companion 2.0 must not alter these working paths. Any future bridge translates typed companion commands into these proven commands.

## 6. Telemetry requirements

- Android Room is the primary persistent telemetry store in a future Android build.
- TV app emits batches to the authenticated Android endpoint only.
- Batch with ACK, bounded retry, exponential backoff and dedupe by `event_id`.
- Bounded queue with overflow counter; no unbounded memory growth.
- User can disable telemetry; TV app must honor the flag and stop emitting.
- Strict exclusions in `docs/TELEMETRY_PRIVACY.md`; no cloud transport.

## 7. Verification gates before enabling integration

| Gate | Status |
|---|---|
| Versioned contract approved | Approved for Phase 2 remote connection; extended for Phase 3 browser commands |
| Android endpoint implemented and tested | Implemented as Host mode in `tvremote2` 3.0.0; `:tvremote2:assembleDebug` succeeded; live LAN test pending |
| TV app outbound connection verified on the TV | Client implemented and app repackaged/reinstalled; live connect pending manual test |
| Pairing/token rotation tested | Explicit approval implemented; token/pairing-key storage intentionally deferred |
| Reconnect and invalid-message tests on TV | Local stub harness 20/20; physical TV test pending |
| Browser URL validation and command ACK | Implemented on both sides; static checks and 20/20 stub harness passed |
| Physical browser frame rendering | Pending manual test; arbitrary-site framing and DRM not promised |
| Telemetry ACK/retry/dedupe tested with Room | Deferred; telemetry remains disabled |
| Physical LG TV acceptance | Partial: install, launch and live process verified; visual/input/handshake acceptance pending |

No gate is considered passed by static source review.
