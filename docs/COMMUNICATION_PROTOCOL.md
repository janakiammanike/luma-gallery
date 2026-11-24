# Communication Protocol — audited facts and future boundary

**Date:** 2026-11-24  
**Audience:** Luma Companion 2.0 design, Remote 2.0 integration planning.

This document separates two things:

1. **Proven protocol** used by the working Android remote.
2. **Constraints** for the new browser-based TV companion.

It does not introduce a new transport in Phase 1.

## Actors

```
Phone (Android Remote 2.0)
   ├── UDP listen 50550        ← deprecated TV companion beacon
   ├── HTTP GET  :8081/status  ← deprecated TV companion service
   ├── HTTP POST :8081/command → deprecated TV companion service
   └── WebSocket 3001/3000     → TV system SSAP server

New TV browser app (Companion 2.0)
   └── Phase 1: no network transport at all
```

## 1. Deprecated companion proxy transport

Source: `tvcompanion/services/luma-companion/index.js`, observed Android client in `tvremote2/.../LgWebOsPlatform.kt`.

### Beacon

UDP broadcast to `255.255.255.255:50550` and subnet broadcast every 2 seconds. JSON fields from `status()`:

```json
{
  "app": "com.luma.tvcompanion",
  "protocol": 1,
  "version": "1.0.0",
  "ip": "192.168.1.2",
  "ports": { "ssapPlain": 3000, "ssapTls": 3001, "status": 8081 },
  "uuid": "...",
  "model": "...",
  "macs": ["..."],
  "token": "...",
  "clientKey": "...",
  "ts": 1730000000000
}
```

`clientKey` is omitted until pairing. `token` is a truncated SHA-256 derived from UUID/IP (`index.js:112-115`).

### HTTP status

`GET http://<tv>:8081/status` returns the same status JSON. Android also accepts `GET /`. `CompanionScanner.kt:63-75` uses a 700 ms connect/read timeout.

### HTTP command

`POST http://<tv>:8081/command`, `Content-Type: application/json`. Body limit in the service is 16 KB (`index.js:307-312`). CORS is `*` (`index.js:374`).

SSAP command form:

```json
{
  "uri": "audio/volumeUp",
  "payload": {},
  "token": "...",
  "key": "<client-key>"
}
```

Pointer/input form:

```json
{
  "input": "type:button\nname:UP\n\n",
  "token": "...",
  "key": "<client-key>"
}
```

Response: JSON with `returnValue`, and optionally `errorText`. If the token is present and wrong, service returns HTTP 403. Missing `uri`/`input` returns `returnValue:false`.

The service internally registers with SSAP using `pairingType: PROMPT`, the non-empty permission manifest, and `client-key`. It opens the pointer input socket via `com.webos.service.networkinput/getPointerInputSocket`.

## 2. Direct SSAP transport

Source: `tvremote2/.../DirectWebOsPlatform.kt` and `WebOsProtocol.kt`. This is the active path on the user's TV because `8081` is down.

### Sockets

- Main socket: `wss://<tv>:3001`, fallback `ws://<tv>:3000`.
- Self-signed TV certificate is accepted; hostname verification is relaxed.
- No `Origin` header on upgrade (`WebOsProtocol.kt:50`).
- Pointer socket URL comes from `getPointerInputSocket`; relative paths are expanded against the same host/port and scheme.

### Handshake

1. Send `{"id":"hello","type":"hello","payload":{}}`.
2. Await `type:"hello"`.
3. Best-effort `system/getSystemInfo`.
4. Register:

```json
{
  "id": "register_0",
  "type": "register",
  "payload": {
    "forcePairing": false,
    "manifest": {
      "appVersion": "1.1",
      "manifestVersion": 1,
      "permissions": ["LAUNCH", "CONTROL_AUDIO", "..."],
      "signatures": ["..."],
      "signed": { "...": "..." }
    },
    "pairingType": "PROMPT",
    "client-key": "<optional stored key>"
  }
}
```

Signed attempt first, unsigned fallback. Critical fact: an empty permissions manifest made privileged commands return `{}`; a non-empty manifest fixes it.

### SSAP request/response

Request:

```json
{
  "id": "cmd_1",
  "type": "request",
  "uri": "ssap://audio/volumeUp",
  "payload": {}
}
```

Success response payload contains `returnValue`, `subscribed` or `subscription`. Failures are surfaced as TV error text.

### Pointer input frames

Newline-delimited plain text, **not JSON** (`WebOsProtocol.kt:37-41`):

```text
type:button
name:UP

```

```text
type:move
dx:12
dy:-4
down:0

```

```text
type:click

```

```text
type:scroll
dx:0
dy:3

```

### Endpoint inventory

| Category | Endpoint |
|---|---|
| Pointer channel | `com.webos.service.networkinput/getPointerInputSocket` |
| Network info | `com.webos.service.connectionmanager/getinfo` |
| Keyboard | `com.webos.service.ime/registerRemoteKeyboard` |
| Text | `com.webos.service.ime/insertText` |
| Delete | `com.webos.service.ime/deleteCharacters` |
| Enter | `com.webos.service.ime/sendEnterKey` |
| Apps | `com.webos.applicationManager/listLaunchPoints` |
| Launch | `system.launcher/launch` |
| Audio | `audio/getStatus`, `audio/volumeUp`, `audio/volumeDown`, `audio/setMute` |
| Media | `media.controls/play`, `pause`, `stop`, `rewind`, `fastForward` |
| TV/power | `tv/channelUp`, `tv/channelDown`, `system/turnOff`, `system/turnOn` |

Optional features (TV search, YouTube deep links, media URL, mirroring) are resolved from the TV's own launch points rather than guessed service endpoints (`WebOsOptionalFeatures.kt:20-170`).

## 3. Why the new companion cannot reuse this directly

A packaged browser webOS app runs in the TV web engine. It does not have Node's `http`, `dgram`, `net`, `os` or `webos-service` module, so it cannot:

- bind TCP `8081`,
- broadcast UDP `50550`,
- open a raw WebSocket server,
- connect to TV-local `127.0.0.1:3000` as the Node service does.

From a `file://` packaged app origin, arbitrary LAN SSAP WebSocket requests must not be assumed to work: same-origin policy, CORS, TLS certificate handling and LG security policy are unverified on this TV.

**Therefore:** do not design Companion 2.0 as a TV-side server or as a direct SSAP client until proven. The safe future shape is an outbound, authenticated connection from the foreground TV app to an opt-in endpoint hosted by the phone. That is a future, versioned contract; Phase 1 opens no connection.

## 4. Security notes

- Never import the legacy plain-text `TvStore` key. Fresh pairing only.
- Treat token and client key as credentials: no logs, no telemetry, no URLs, no persisted plain text in the TV browser app.
- Invalid messages must be rejected without echoing payload contents.
- Any future retry/dedupe must use opaque event/message IDs only.
