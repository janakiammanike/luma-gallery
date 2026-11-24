# Telemetry Event Schema v1

**Date:** 2026-11-24  
**Status:** frozen draft for design review. No events are emitted in Phase 1.

## Event object

```json
{
  "event_id": "7f3a9c1e4b8d4f0aa6c1d2e3f4a5b6c7",
  "schema": 1,
  "type": "app_launch",
  "ts": 1730000000000,
  "session_id": "b21e8d7c4a6f4c9e8a3d1f0b5c6d7e8f",
  "payload": {}
}
```

### Required fields

| Field | Type | Rule |
|---|---|---|
| `event_id` | string | 32–64 hex characters, random per event, unique across reinstalls in practice. |
| `schema` | number | Must be `1`. |
| `type` | string | One of the allowed types below. |
| `ts` | number | Finite epoch milliseconds. |
| `session_id` | string | 32–64 hex characters, new value per foreground session. |
| `payload` | object | Only allow-listed fields from this document. |

## Allowed event types and payloads

| Type | Payload fields | Meaning |
|---|---|---|
| `app_launch` | `{}` | App entered foreground. |
| `lifecycle_visibility` | `{ "state": "foreground" | "suspended" }` | Visibility transition. |
| `diagnostic_snapshot` | `{ "engine": "<enum>", "bridge": "<enum>", "network": "online" | "offline" }` | Coarse environment snapshot. |
| `validation_reject` | `{ "reason": "<reason-enum>" }` | A remote envelope failed validation. No payload echo. |
| `storage_failure` | `{ "operation": "read" | "write", "count": <integer> }` | Local counter storage failed. |
| `connection_state` | `{ "state": "idle" | "connecting" | "connected" | "failed" }` | Future connection state; not used in Phase 1. |

### Enum constraints

- `engine`: `chromium53`, `other`, `unknown`.
- `bridge`: `palmsystem`, `webossystem`, `unavailable`.
- `reason`: one of `message_too_large`, `invalid_json`, `not_an_object`, `unsupported_version`, `unknown_type`, `invalid_id`, `invalid_timestamp`, `invalid_payload`.
- `count`: non-negative integer, maximum 1000000.

No other payload key is valid. Unknown keys must be stripped or the event rejected.

## Batch envelope

A batch uses the remote envelope defined in `REMOTE_INTEGRATION_CONTRACT.md`:

```json
{
  "v": 1,
  "type": "telemetry_batch",
  "id": "batch_0001",
  "ts": 1730000000100,
  "payload": {
    "producer": "com.luma.companion2",
    "app_version": "0.1.0",
    "events": []
  }
}
```

### Batch rules

| Rule | Value |
|---|---|
| `payload.producer` | Must equal `com.luma.companion2`. |
| `payload.app_version` | Three-integer version string. |
| `payload.events` | Array of 1–64 event objects. |
| Raw frame size | Maximum 64 KB. |
| `id` | Opaque batch ID, 1–128 characters. |
| ACK correlation | ACK carries the batch `id` plus accepted/rejected `event_id` arrays. |

## ACK shape

```json
{
  "v": 1,
  "type": "ack",
  "id": "ack_0001",
  "ts": 1730000000200,
  "payload": {
    "batch_id": "batch_0001",
    "accepted": ["7f3a9c1e4b8d4f0aa6c1d2e3f4a5b6c7"],
    "rejected": []
  }
}
```

`accepted` and `rejected` must be disjoint. An `event_id` in neither list is treated as unacknowledged and may retry until the attempt cap.

## Dedupe rules

1. Android stores `event_id` as a unique key.
2. Duplicate events are acknowledged as accepted but not inserted again.
3. TV removes any `event_id` present in `accepted`, whether newly stored or duplicate.
4. Retention is seven days on the Android device.
5. After deletion, an old duplicate may be accepted again; this is acceptable because no user data is attached.

## Explicitly forbidden in this schema

URLs, domains, search text, typed text, keystrokes, media titles, file names, pairing token, client key, IP address, MAC address, serial number, advertising ID, location, cookies, contacts, photos, audio samples, voice data or free-form error messages.

## Phase 1 status

The type name `telemetry_batch` exists in `js/remote-contract.js` only so unknown/new message types can be classified. No event object, batch, queue, ACK or store exists in Phase 1.
