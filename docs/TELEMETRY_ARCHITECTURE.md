# Telemetry Architecture — design only

**Date:** 2026-11-24  
**Status:** specification. No telemetry is emitted in Companion 2.0 Phase 1. No Android production source is changed.

## Purpose

Define a privacy-preserving, local-only telemetry path for a future release. The TV app may emit small, coarse diagnostic events only while foregrounded. The Android app is the receiver and primary store. There is no cloud backend and no third-party analytics SDK.

## Important source finding

The current repository contains **no Room database**. A repository-wide search for `androidx.room`, `@Entity`, `@Dao` and `RoomDatabase` in Kotlin files returned no matches. Therefore all Room references below are a **future design target**, not a description of existing code.

## Actors

```text
Companion 2.0 TV app (foreground only)
   |
   | telemetry_batch over authenticated LAN channel (future)
   v
Android Remote 2.0 companion receiver (future)
   |
   | validate -> dedupe -> persist
   v
Android Room primary store (future)
   |
   +-- user can disable or delete
   +-- no cloud upload
```

## Design principles

1. **Local only.** The Android phone is the final destination. No Internet relay, no Firebase, no account service.
2. **Opt-in and disableable.** A user setting can stop emission on the TV and stop acceptance on the phone.
3. **Data minimisation.** Only counters, enum states, durations and error codes. See `TELEMETRY_PRIVACY.md`.
4. **Foreground lifetime.** The TV app emits nothing when hidden or suspended.
5. **Bounded resources.** Fixed batch size, bounded queue and capped retries.
6. **Idempotent delivery.** Every event has an opaque `event_id`; ACK and dedupe use that ID.
7. **Honest failure handling.** Offline, auth failure and storage failure degrade quietly; the dashboard never claims telemetry was delivered without an ACK.

## Event flow

1. A coarse event occurs in the TV app: launch, visibility change, diagnostic snapshot, validation reject, storage failure or connection state change.
2. The emitter creates an event with schema v1 and a random `event_id`.
3. Events are queued in memory with a hard cap.
4. A batch is flushed when one of these triggers occurs:
   - 16 events accumulated,
   - 30 seconds elapsed while foregrounded,
   - app is about to hide, with a best-effort final flush,
   - user taps Refresh in Diagnostics Status.
5. The batch is sent as a `telemetry_batch` message over the future authenticated channel.
6. Android validates the envelope, checks the telemetry disable flag, deduplicates by `event_id`, and inserts accepted events into Room.
7. Android replies with `ack` containing accepted and rejected `event_id` lists.
8. TV removes acknowledged IDs. Rejected IDs are not retried. Unacknowledged IDs retry with bounded exponential backoff until the attempt cap is reached.

## Batching rules

| Rule | Value |
|---|---|
| Maximum events per batch | 64 |
| Maximum raw batch size | 64 KB, shared with the remote envelope limit |
| In-memory queue cap | 1000 events |
| Overflow behaviour | Drop newest low-value events and increment `dropped_count` |
| Flush interval | 30 seconds while foregrounded |
| Retry schedule | 1s, 2s, 4s, 8s, 16s |
| Maximum attempts | 5 per batch |
| Dedupe key | `event_id` |
| ACK unit | list of `event_id` values |

## Android receiver responsibilities

- Authenticate the LAN peer before accepting a batch.
- Reject unknown schema versions and malformed batches.
- Never log payload contents, tokens, addresses or identifiers.
- Check the user telemetry-disable flag before persisting.
- Use `INSERT OR IGNORE` on unique `event_id` for idempotence.
- Return explicit accepted/rejected IDs in the ACK.
- Prune records older than the retention window.

See `ANDROID_TELEMETRY_STORAGE.md` for the proposed Room shape.

## Failure handling

| Failure | Behaviour |
|---|---|
| TV offline | Queue locally in memory; show `Offline` in Diagnostics Status. |
| Channel unreachable | Bounded retry; no blocking of dashboard UI. |
| Auth failure | Stop emitting for that session; require explicit re-pairing. |
| Empty/invalid ACK | Treat batch as unacknowledged until attempt cap. |
| Room insert failure | Return rejection for that batch; do not crash the app. |
| Queue overflow | Drop events and increment counter; never grow without bound. |
| User disables telemetry | Clear queue and stop all emit timers immediately. |

## What Phase 1 actually does

The dashboard shows `Telemetry: Off · Phase 1 only`. It contains no emitter, no queue, no ACK handling and no Room dependency. `remote-contract.js` recognises the `telemetry_batch` message type so a future implementation has a single validation boundary, but no such message is produced or consumed.

## Verification gates

- [ ] Privacy review approved.
- [ ] Event schema frozen.
- [ ] Android receiver and Room store implemented and tested.
- [ ] ACK/retry/dedupe tested with forced offline and duplicate batches.
- [ ] User-disable tested end to end.
- [ ] Physical TV test confirms no background emission after hide.

None of these gates is passed in Phase 1.
