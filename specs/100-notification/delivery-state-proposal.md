# Delivery-state/job proposal — pending schema approval

Requested by user on 2026-11-22: design durable delivery-state/job instead of accepting best-effort push. This document proposes a schema; it does not authorize creating it or changing database/security contracts.

## Transaction boundary

Create the inbox document and a server-only job in the same Firestore transaction. Preferences never prevent inbox creation. The job references that inbox path; it does not copy body, QR, credentials or tokens. Producers already writing inbox inside their business transaction must enqueue this job in that transaction, not call another writer that duplicates the inbox.

## Proposed new collection

`notificationDeliveries/{notificationId_hashOfRecipient}` — server-only read/write, client deny all. Proposed fields:

| Field | Type | Purpose |
|---|---|---|
| recipientUid | string | Authoritative recipient |
| notificationId | string | Existing inbox document ID |
| state | enum PENDING/LEASED/SENT/SKIPPED/FAILED | Worker lifecycle |
| attemptCount | integer | Bounded delivery attempts |
| nextAttemptAt | timestamp | Due job query |
| leaseUntil | timestamp or null | Recover jobs after worker termination |
| leaseOwner | string or null | Reject stale worker completion |
| createdAt / updatedAt | timestamp | Server clock |
| lastErrorCode | string or null | Sanitized code, no token/content |

Stable notification identity comes from source event identity, recipient and type. Each producer must define identity at its transition/version boundary; entity ID alone is insufficient for repeatable changes. SYSTEM retains its existing admin UID/requestId identity. Document IDs store hashes; no new eventId field is required on inbox documents.

## Worker proposal

pg_cron invokes an authenticated server worker once per minute. Claim due PENDING jobs or expired LEASED jobs inside a transaction, with a two-minute lease and worker identity; process bounded batches of 50. Completion checks leaseOwner before writing state. Re-read recipient account, inbox, preferences and chat presence before sending. BOOKING_NEW overrides preference false; PROMOTION missing key defaults false as user approved. Preference-off/viewing-chat/removed recipient produce SKIPPED without removing inbox.

Transient failures retry at 1, 5, 15, 60 and 180 minutes, then FAILED (six total attempts including initial). Honor a longer provider Retry-After. UNREGISTERED removes only the rejected token; permanent payload/auth/config errors stop and expose a sanitized error for operator recovery. Cleanup and multiple-device partial delivery require a confirmed per-token receipt design before claiming those guarantees; the aggregate job above alone cannot track per-token successes safely.

FCM success followed by a worker crash before SENT can duplicate push. At-least-once transport is the achievable guarantee; use stable notification identity for Android notification replacement. Do not claim exactly-once device delivery. Inbox remains exactly one record per event/recipient/type.

## Retention/security/deletion

Retain completed job metadata for seven days, then delete through server cleanup. Account deletion removes pending/completed delivery jobs for that uid in addition to existing inbox/token/profile deletion. No client Rules grant is added. Any new indexes for state/nextAttemptAt and lease expiry are added only with the approved collection design and verified local queries.

## Approval required

Database owners/user must approve the collection/fields, retry/retention limits and per-token receipt design. Producer owners must approve event identities and transaction enqueue boundary. This proposal cannot by itself close T004–T006, T011 or T014. Final push payload/lock-screen privacy remains a separate decision; recommend generic lock-screen text and an authenticated inbox identifier rather than body content.
