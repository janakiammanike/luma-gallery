# Android Telemetry Storage — future Room design

**Date:** 2026-11-24  
**Status:** specification only. The current repository has no Room dependency or entity. No Android source is changed.

## Finding from source audit

A repository-wide search of `*.kt` files for `androidx.room`, `@Entity`, `@Dao` and `RoomDatabase` returned no matches. Current Android settings are held by `data/SecureTvStore.kt` using `EncryptedSharedPreferences`, but there is no telemetry database.

The design below is therefore a **target**, not existing behaviour.

## Proposed database

```text
LumaTelemetryDb
  telemetry_event
  telemetry_state
```

### `telemetry_event`

| Column | Type | Constraint |
|---|---|---|
| `event_id` | TEXT | PRIMARY KEY, unique, 32–64 hex characters |
| `schema_version` | INTEGER | Must be 1 |
| `type` | TEXT | Allow-listed type enum |
| `ts` | INTEGER | Epoch milliseconds |
| `session_id` | TEXT | Random per foreground session |
| `payload_json` | TEXT | Allow-listed JSON only; no free text |
| `received_at` | INTEGER | Epoch milliseconds on Android |
| `attempts` | INTEGER | Non-negative, default 0 |
| `acked` | INTEGER | 0 or 1 |

Indexes:

- unique index on `event_id`,
- index on `acked`, `ts`,
- index on `received_at` for retention pruning.

### `telemetry_state`

Single-row table for durable counters:

| Column | Type | Meaning |
|---|---|---|
| `id` | INTEGER | Always 1 |
| `dropped_count` | INTEGER | Events dropped due to queue overflow |
| `rejected_count` | INTEGER | Events rejected by validation |
| `last_flush_ts` | INTEGER | Last successful flush time |

## DAO operations

| Operation | Rule |
|---|---|
| `insertOrIgnore(event)` | Uses unique `event_id`; duplicates never create rows. |
| `pendingBatch(limit)` | Returns unacked events ordered by `ts`, capped at 64. |
| `markAccepted(eventIds)` | Sets `acked = 1`. |
| `incrementAttempts(batchIds)` | Caps at 5. |
| `pruneOlderThan(ts)` | Deletes rows older than seven days. |
| `countPending()` | Enforces the 1000-row bound. |
| `clearAll()` | Used by the user delete action. |

## Write policy

1. Validate the batch envelope before any database write.
2. Check the telemetry-disable flag before any database write.
3. Insert each event with `INSERT OR IGNORE`.
4. Return accepted IDs for both new inserts and duplicates.
5. Return rejected IDs for invalid events.
6. Batch all inserts in one transaction.
7. Never log `payload_json`.

## Queue bounds

| Item | Limit |
|---|---|
| TV in-memory queue | 1000 events |
| Android pending rows | 1000 |
| Batch size | 64 events |
| Raw batch bytes | 64 KB |
| Attempts per batch | 5 |
| Retention | 7 days |

When the bound is reached, drop newest low-value events and increment `dropped_count`. Never grow without limit.

## Retry and dedupe flow

```text
receive batch
  -> validate envelope
  -> disable flag check
  -> insertOrIgnore by event_id
  -> ACK accepted/rejected
  -> TV removes accepted IDs
  -> unacknowledged IDs retry with 1/2/4/8/16s backoff
  -> after 5 attempts, drop batch and increment counter
```

Duplicate batches are safe because `event_id` is unique. Duplicate delivery is acknowledged but stored once.

## Settings storage

Telemetry enabled/disabled is a user preference, not a secret. A future implementation may keep it in normal app preferences. Tokens and client keys must remain in `SecureTvStore` / Android Keystore and must never be placed in the telemetry database.

## Failure behaviour

| Failure | Response |
|---|---|
| Database locked | Retry the transaction once, then ACK all as rejected. |
| Disk full | Reject batch; increment `rejected_count`; show no user text with paths. |
| Malformed JSON | Reject entire batch; no partial insert. |
| Unknown schema | Reject entire batch. |
| Disable flag on | Reject entire batch and clear TV queue on next ACK. |
| App update | Preserve database, but prune records older than retention. |

## Test gates before enabling

- [ ] Room dependency and entities added in a reviewed Android change.
- [ ] Duplicate batch test: one row persisted, ACK accepted.
- [ ] Offline test: bounded retries, no UI block.
- [ ] Disable test: no rows inserted, TV queue cleared.
- [ ] Retention test: rows older than seven days removed.
- [ ] Overflow test: queue capped at 1000.
- [ ] No secrets or excluded fields in `payload_json`.

No gate is passed in Phase 1.
