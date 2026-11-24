# Telemetry Privacy Policy — design constraint

**Date:** 2026-11-24  
**Status:** privacy requirements for any future telemetry implementation. Phase 1 emits nothing.

## Position

Luma Companion 2.0 telemetry is local-only. The TV app may send coarse diagnostic counters to the user's own Android phone over an authenticated LAN channel. It must never send data to a cloud service, analytics provider, advertising network or third-party SDK.

## Data that may be collected

Only these categories are permitted:

| Category | Examples |
|---|---|
| Counters | launch count, validation reject count, storage failure count, dropped event count |
| Enum states | foreground/suspended, online/offline, bridge type, coarse engine class |
| Durations | foreground session length in whole seconds |
| Error codes | fixed reason enums from `TELEMETRY_EVENT_SCHEMA.md` |
| Version labels | app version, schema version |
| Opaque IDs | random `event_id`, random `session_id` |

## Data that must never be collected

- URLs, domains, query strings, link targets, iframe destinations.
- Search queries, typed text, keystrokes, clipboard content.
- Media titles, artists, file names, folder names, metadata.
- Pairing tokens, client keys, signatures, certificates, cookies.
- IP addresses, MAC addresses, hostnames, serial numbers, UUIDs tied to the device.
- Location, contacts, photos, videos, audio, voice, camera input.
- Free-form error messages or stack traces that might contain paths or user text.
- Advertising IDs or cross-app identifiers.

## User control

1. A clear Android setting must allow telemetry to be disabled.
2. When disabled, the TV app stops emitting immediately and clears its in-memory queue.
3. When disabled, the Android receiver rejects incoming telemetry batches.
4. A delete action must remove stored telemetry records from the device.
5. No feature may depend on telemetry being enabled.

## Storage and retention

- Android Room is the primary store in the future design.
- Default retention: seven days.
- No cloud copy.
- No export to external services.
- If the app is uninstalled, its local database is removed with the app data.

## Network rules

- LAN only.
- Authenticated peer required.
- No hostname baked into the TV package.
- No retry to any non-LAN fallback.
- No DNS-based tracking.
- No telemetry when the TV app is hidden or suspended.

## Logging rules

- Never log telemetry payloads.
- Never log tokens, keys, addresses or full batch bodies.
- Log only fixed error codes and counters.
- Validation reject reason enums are allowed; rejected payload content is not.

## Children and sensitive contexts

The app is a TV utility, not a children's product. Even so, no behavioural profile is built, no cross-session identity is retained, and no data is used for advertising.

## Compliance posture for Phase 1

Phase 1 has:

- no telemetry emitter,
- no telemetry network message,
- no telemetry storage,
- no analytics SDK,
- no cloud endpoint.

The dashboard explicitly displays `Telemetry · Off · Phase 1 only`.

## Change control

Any future addition to the allowed-data list requires:

1. update to `TELEMETRY_EVENT_SCHEMA.md`,
2. update to this privacy document,
3. explicit user approval before implementation,
4. end-to-end verification that disabled mode emits nothing.
