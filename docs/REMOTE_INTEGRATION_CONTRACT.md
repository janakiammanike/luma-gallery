# Remote Integration Contract v1

**Date:** 2026-11-25  
**Status:** approved for Phase 2 remote connection, extended for Phase 3 browser view and updated for the current full browser UI. Envelope validation remains the single boundary. The TV client and the opt-in Host mode inside `tvremote2` 3.0.0 implement the handshake. The Android Host mode exposes `diagnostics.refresh`, `browser.open` and `browser.close`; the TV also prepares additional browser/video command states for a future Host-mode UI. `app.focus` and telemetry remain disabled.

## Scope

This contract defines the message boundary between:

- **TV app:** `com.luma.companion2`, running in the foreground on the physical LG TV (CLI reports SDK `10.3.2`).
- **Android host:** a future, opt-in LAN endpoint in the Luma Android app.

It does **not** define the deprecated companion service, SSAP `3000/3001`, or the old UDP `50550` beacon. Those remain Android-side implementation details described in `COMMUNICATION_PROTOCOL.md`.

## Transport assumptions

| Rule | Detail |
|---|---|
| Direction | TV app connects outbound; Android hosts the endpoint. |
| TV listener | Not allowed. No TCP, UDP, WebSocket server or JS service on the TV. |
| Network | Same LAN only. No cloud relay, no Internet fallback. |
| Address | Manually entered IP/port first. No automatic discovery until separately verified. |
| Lifetime | Open only while the TV app is foregrounded. Close on hide/termination. |
| URL/path | Fixed for this release as `ws://<ip>:<port>/luma/companion/v1`. No query string and no credential in the URL. |
| Retry | Bounded exponential backoff: 1s, 2s, 4s, 8s, 16s. No tight reconnect loop. |
| Host | Opt-in **Host mode** inside `tvremote2` (`com.luma.tvremote2`, version `3.0.0`), default port `8787`. It runs only while that screen is foregrounded. The former separate `companion-host` module was removed. |

## Envelope

Every message, in either direction, uses this exact shape:

```json
{
  "v": 1,
  "type": "hello",
  "id": "msg_0001",
  "ts": 1730000000000,
  "payload": {}
}
```

### Field rules

| Field | Type | Rule |
|---|---|---|
| `v` | number | Must be `1`. |
| `type` | string | Must be one of `hello`, `capability`, `command`, `event`, `ack`, `telemetry_batch`. |
| `id` | string | 1–128 characters, opaque, unique per direction per session. |
| `ts` | number | Finite epoch milliseconds. |
| `payload` | object | Required. Shape depends on `type`. |
| Raw frame | string/bytes | Maximum 65536 bytes. |

These rules are implemented today in `luma-companion-2/js/remote-contract.js`.

## Rejection reasons

The Phase 1 validator returns exactly these reasons:

| Reason | Trigger |
|---|---|
| `message_too_large` | Raw string exceeds 64 KB. |
| `invalid_json` | String is not valid JSON. |
| `not_an_object` | Parsed value or input is not a plain object. |
| `unsupported_version` | `v` is not `1`. |
| `unknown_type` | `type` is not allow-listed. |
| `invalid_id` | `id` is missing, empty or longer than 128 characters. |
| `invalid_timestamp` | `ts` is missing, non-numeric or not finite. |
| `invalid_payload` | `payload` is missing or not an object. |

Rejected payload contents must never be echoed or logged.

## Message types

### `hello` — TV to Android

```json
{
  "v": 1,
  "type": "hello",
  "id": "hello_0001",
  "ts": 1730000000000,
  "payload": {
    "producer": "com.luma.companion2",
    "app_version": "0.1.0",
    "session_id": "b21e8d7c4a6f4c9e8a3d1f0b5c6d7e8f",
    "capabilities": [
      "diagnostics.refresh",
      "browser.open",
      "browser.close",
      "browser.back",
      "browser.forward",
      "browser.reload",
      "browser.home",
      "browser.new_tab",
      "browser.close_tab",
      "browser.next_tab",
      "browser.previous_tab",
      "video.play",
      "video.pause",
      "video.seek"
    ]
  }
}
```

No token or key is included in `hello`. The capability list is the TV's local support set. The
extra browser/video entries are prepared TV states; they do not mean the phone UI already sends
them.

### `capability` — Android to TV

```json
{
  "v": 1,
  "type": "capability",
  "id": "cap_0001",
  "ts": 1730000000100,
  "payload": {
    "accepted_version": 1,
    "supported_types": ["command", "event", "ack", "telemetry_batch"],
    "features": ["browser.open", "diagnostics.refresh"],
    "telemetry_enabled": false
  }
}
```

The TV app must not send telemetry when `telemetry_enabled` is `false`.

### `command` — Android to TV

Sent from the Host mode screen only after a TV peer has been explicitly approved.

```json
{
  "v": 1,
  "type": "command",
  "id": "cmd_0001",
  "ts": 1730000000200,
  "payload": {
    "action": "diagnostics.refresh",
    "request_id": "req_0001",
    "args": {},
    "timeout_ms": 10000
  }
}
```

Rules:

- `action` must be an allow-listed string.
- `request_id` is echoed in the ACK.
- `args` must contain only approved primitive fields.
- `timeout_ms` is 1000–10000.
- Unknown actions are rejected without executing anything.
- Android Host mode currently sends: `diagnostics.refresh`, `browser.open`, `browser.close`.
- TV-side accepted actions are the same three plus `browser.back`, `browser.forward`,
  `browser.reload`, `browser.home`, `browser.new_tab`, `browser.close_tab`, `browser.next_tab`,
  `browser.previous_tab`, `video.play`, `video.pause` and `video.seek`. The extras are prepared
  states only and are not claimed as a tested phone flow.
- `browser.open` accepts `args.url` (or `args.address`): an absolute `http`/`https` URL without
  credentials, at most 2048 characters. Invalid URLs receive a rejected ACK and are never loaded.
- `browser.close` takes empty `args`, clears the frame host and returns the TV browser to Home.
- `browser.new_tab` accepts an optional `url`; `browser.close_tab` accepts an optional tab `id`.
- `video.play`, `video.pause` and `video.seek` apply only to a direct video file in the active
  tab. `video.seek` uses `args.position` as a percentage.
- Reserved and rejected: `app.focus`. It must never change another app's focus.
- The TV ACK reflects whether the local handler actually executed the action.
- Command rate limit: 20 commands per 10 seconds. Excess commands get a rejected ACK.

### `event` — either direction

```json
{
  "v": 1,
  "type": "event",
  "id": "evt_0001",
  "ts": 1730000000300,
  "payload": {
    "name": "lifecycle.visibility",
    "data": { "state": "suspended" }
  }
}
```

`name` is a fixed enum. `data` uses only coarse counters, states or durations. No URLs, keys, typed text or media metadata.

### `ack` — either direction

General ACK:

```json
{
  "v": 1,
  "type": "ack",
  "id": "ack_0001",
  "ts": 1730000000400,
  "payload": {
    "ref_id": "cmd_0001",
    "status": "accepted",
    "reason": null
  }
}
```

Telemetry ACK is a specialised form reserved for Phase 4 and defined in `TELEMETRY_EVENT_SCHEMA.md`:

```json
{
  "v": 1,
  "type": "ack",
  "id": "ack_0002",
  "ts": 1730000000500,
  "payload": {
    "batch_id": "batch_0001",
    "status": "accepted",
    "reason": null
  }
}
```

`status` is `accepted`, `rejected` or `partial`. Either `ref_id` (command ACK) or `batch_id` (telemetry ACK) must be present.

### `telemetry_batch` — TV to Android

Defined in `TELEMETRY_EVENT_SCHEMA.md`. It is not emitted in Phase 1.

## Handshake sequence

```text
TV app foregrounded
  -> user enters phone IPv4 address and port
  -> opens outbound LAN channel to /luma/companion/v1
  -> sends hello with producer, app_version, session_id and capabilities
Android validates envelope
  -> shows a pending approval with producer and short session id
User approves in `tvremote2` Host mode on the phone
  -> Android sends capability with telemetry_enabled=false
TV app reads capability
  -> stores accepted version and feature list in memory
Channel used only for allow-listed messages
  -> Android Host mode sends diagnostics.refresh, browser.open, browser.close with ACK
  -> TV also accepts prepared browser/video command states, not yet exposed by the phone UI
TV app hidden or user taps Disconnect
  -> clears timers/queue -> closes channel, hides frames and pauses video
```

No token or client key is exchanged in this release. The TV stores no pairing
credential. Token rotation remains a later Android-owned feature.

If `v` is unsupported, the receiver closes the channel. If a message is invalid, the receiver increments its reject counter and may close after a small repeated-failure threshold.

## Security rules

1. Fresh pairing only. Never import the legacy plain-text `TvStore` key.
2. Tokens and client keys are credentials: never placed in message payloads, logs, telemetry or URLs.
3. Android shows a pending peer and sends `capability` only after explicit user approval. No token exists in this release.
4. TV must not treat a socket as connected until `capability` arrives; it must not trust an unauthenticated `command`.
5. No arbitrary SSAP frame crosses this contract. Android translates allow-listed actions into its own proven SSAP commands.
6. No free-form text from a remote peer is rendered as HTML.
7. Unknown fields are ignored for compatibility but never executed.
8. Rate-limit commands and telemetry batches to prevent a runaway loop. The TV currently allows
   20 commands per 10 seconds; telemetry batches remain inactive.

## Compatibility with existing Android protocol

| Item | Owner | Rule |
|---|---|---|
| SSAP `3001` TLS / `3000` plain | Android `tvremote2` | Keep unchanged. |
| Non-empty permissions manifest | Android `tvremote2` | Keep unchanged. |
| `pairingType: PROMPT`, `client-key` | Android `tvremote2` | Keep unchanged. |
| Newline pointer frames | Android `tvremote2` | Keep unchanged. |
| Typed companion messages | This contract | New boundary; not active in Phase 1. |

## Test vectors

### Valid

```json
{"v":1,"type":"hello","id":"hello_1","ts":1730000000000,"payload":{"producer":"com.luma.companion2"}}
```

Expected: `ok: true`, reason `ok`.

### Invalid examples

| Input | Expected reason |
|---|---|
| `{"v":2,"type":"hello","id":"x","ts":1,"payload":{}}` | `unsupported_version` |
| `{"v":1,"type":"unknown","id":"x","ts":1,"payload":{}}` | `unknown_type` |
| `{"v":1,"type":"hello","id":"","ts":1,"payload":{}}` | `invalid_id` |
| `{"v":1,"type":"hello","id":"x","ts":"1","payload":{}}` | `invalid_timestamp` |
| `{"v":1,"type":"hello","id":"x","ts":1,"payload":[]}` | `invalid_payload` |
| `{broken json` as a string | `invalid_json` |

## Implementation status (Phase 2 + Phase 3 + current browser UI)

| Item | Status |
|---|---|
| Envelope validator | Implemented in `js/remote-contract.js` and Kotlin `Envelope.kt`. |
| Counters for valid/invalid/oversized/unknown type | Implemented on both sides. |
| Outbound WebSocket client | Implemented in `js/remote-session.js`; no TV listener. |
| Android LAN host | Implemented as Host mode in `tvremote2` 3.0.0; separate `companion-host` module removed. |
| Explicit phone approval | Implemented. Capability is sent only after the user taps Approve TV. |
| Handshake | Implemented: hello -> pending -> capability. |
| `diagnostics.refresh` command with ACK | Implemented and blocked until capability is accepted. |
| `browser.open` / `browser.close` with ACK | Implemented in Phase 3. URL validation on both sides; frames clear and the TV returns Home on close/hide. |
| Extra TV browser/video commands | Prepared on the TV controller and remote allow-list. Not exposed by the Android Host mode UI and not claimed as a tested phone flow. |
| Command rate limit | 20 commands per 10 seconds on the TV; excess commands get a rejected ACK. |
| `app.focus` | Rejected as reserved; no focus change is performed. |
| Payload-schema validation | Implemented for hello, capability, command, event and ack. |
| ACK/retry/dedupe | ACK for commands implemented. Handshake accepts `batch_id` and `partial` for future telemetry. Bounded reconnect implemented. Telemetry dedupe deferred. |
| Token/pairing key storage | Not implemented by design; no credential is stored on the TV. |
| Physical TV-to-phone handshake | Pending manual test; TV app package and process were verified, but no live handshake capture exists. |
| Physical browser frame rendering | Pending manual test; arbitrary-site framing and DRM are not promised. |
