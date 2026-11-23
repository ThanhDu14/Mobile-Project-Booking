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
| attemptCount | integer | Diagnostic worker claim/run count; retry budget belongs to each token receipt |
| nextAttemptAt | timestamp | Due job query |
| leaseUntil | timestamp or null | Recover jobs after worker termination |
| leaseOwner | string or null | Fresh claim nonce for each acquisition, not a reusable process name; reject stale completion |
| createdAt / updatedAt | timestamp | Server clock |
| lastErrorCode | string or null | Sanitized code, no token/content |
| targetsInitializedAt | timestamp or null | All frozen targets have durable receipts before sending |
| targetHashes | array of strings or null | Recommended bounded initialization amendment: frozen unique hashes; no raw tokens |
| initializedThrough | integer | Recommended amendment: atomically advanced receipt initialization cursor |

Stable notification identity comes from source event identity, recipient and type. Each producer must define identity at its transition/version boundary; entity ID alone is insufficient for repeatable changes. SYSTEM retains its existing admin UID/requestId identity. Document IDs store hashes; no new eventId field is required on inbox documents.

## Worker proposal

pg_cron invokes an authenticated server worker once per minute. Claim due PENDING jobs or expired LEASED jobs inside a transaction, with a two-minute lease and worker identity; process bounded batches of 50. Completion checks leaseOwner before writing state. Re-read recipient account, inbox, preferences and chat presence before sending. BOOKING_NEW overrides preference false; PROMOTION missing key defaults false as user approved. Preference-off/viewing-chat/removed recipient produce SKIPPED without removing inbox.

Transient failures retry at 1, 5, 15, 60 and 180 minutes, then FAILED (six total attempts including initial, per token). Honor a longer provider Retry-After. UNREGISTERED removes only the rejected token; permanent payload/auth/config errors stop and expose a sanitized error for operator recovery. The per-token design below is also pending approval; the aggregate job alone is insufficient.

FCM success followed by a worker crash before SENT can duplicate push. At-least-once transport is the achievable guarantee; use stable notification identity for Android notification replacement. Do not claim exactly-once device delivery. Inbox remains exactly one record per event/recipient/type.

## Retention/security/deletion

Retain completed job metadata for seven days, then delete through server cleanup. Account deletion removes pending/completed delivery jobs for that uid in addition to existing inbox/token/profile deletion. No client Rules grant is added. Any new indexes for state/nextAttemptAt and lease expiry are added only with the approved collection design and verified local queries.

## Approval required

Database owners/user must approve the collection/fields, retry/retention limits and per-token receipt design. Producer owners must approve event identities and transaction enqueue boundary. This proposal cannot by itself close T004–T006, T011 or T014. Final push payload/lock-screen privacy remains a separate decision; recommend generic lock-screen text and an authenticated inbox identifier rather than body content.

## Per-token receipts — concrete schema proposal, not approved

Recommend server-only `notificationDeliveries/{jobId}/tokens/{sha256(token)}` instead of a growing result map on the job. No raw token is copied out of the existing profile. Proposed receipt fields:

| Field | Type | Meaning |
|---|---|---|
| state | PENDING / SENDING / SENT / SKIPPED / FAILED | Per-token progress |
| attemptCount | integer | Counts sends reserved before calling FCM; maximum six |
| nextAttemptAt | timestamp or null | Per-token backoff, including provider Retry-After |
| attemptId | string or null | Fences a completion from an older lease/attempt |
| acceptedAt | timestamp or null | FCM accepted the request, not proof of device display |
| lastErrorCode | string or null | Allowlisted FCM/transport code; no response body/token |
| updatedAt | timestamp | Last receipt transition |

The proposed parent includes targetsInitializedAt. On first eligible worker claim, read the current account/profile and freeze its unique token hashes, then initialize receipts through the bounded procedure in the 23/11 design review below. Set targetsInitializedAt only when initialization completes. New tokens registered later are not backfilled for an old event. Read raw tokens from the current profile only when sending; hashes no longer present become SKIPPED. A job with no tokens is SKIPPED, with inbox retained. Recheck account deletion, preference and chat suppression on each resumed send; do not undo a previous SENT receipt. Receipt schema and token lifecycle ownership T011 must be approved together.

FCM HTTP v1 sends one message per token. Before each call, transactionally check the lease owner/expiry and reserve that token's attemptId/count. After success/failure, commit a result only if both job leaseOwner and receipt attemptId still match. Renew the lease while processing; stop before starting another token if ownership is lost. SENT tokens are never deliberately sent again during another token's retry. Unknown outcome after crash/timeout may be retried and duplicate that token's push; leases cannot make an external FCM call atomic with Firestore. A stable notification identity replaces Android tray entries, not necessarily all alerts.

UNREGISTERED marks that receipt FAILED and removes exactly that raw token from the current user's array transactionally, preserving newly registered/other tokens. Do not delete tokens for generic INVALID_ARGUMENT unless the provider specifically confirms a token error with a valid payload. Auth/config/payload errors terminate the affected attempts for operator recovery without deleting tokens. Mixed terminal successes/failures produce aggregate FAILED, with receipts preserving successes; SENT means at least one accepted token and all others SENT/SKIPPED, SKIPPED means all skipped and none failed, and PENDING means at least one retry remains. Parent attemptCount is diagnostic lease-run count; the six-send bound belongs to each receipt. Set parent nextAttemptAt to the earliest pending receipt time. No client receipt access.

Account deletion must delete receipt subcollections before parent jobs, in bounded batches with retry/final sweep. Deleting a parent alone does not remove receipts. Completed metadata retention is seven days, including receipts. Checking deletion immediately before send reduces races but cannot recall an already in-flight FCM request; a deleted recipient's inbox/profile stays deleted. These cleanup changes are proposed only and are not covered by the current T054 test of existing storage.

## Event identities and producer transaction boundaries — decisions requested

Use `sha256(canonical JSON array [producer, eventIdentity, recipientUid, canonicalType])` as the proposed identity for new writers; hashing does not add an inbox field. Preserve existing SYSTEM notification IDs. Existing random inbox IDs must be reused by the job within the same transaction during transition; never create a second inbox to enqueue a job. Hashing rules and request changes below require producer agreement.

| Producer | Current evidence | Proposed stable identity / boundary | Unresolved agreement |
|---|---|---|---|
| Owner booking command / booking timeout | Existing inbox writes are in the state-transition transaction | bookingId + terminal transition; append job beside the existing inbox write | Confirm transition cannot recur under same booking ID and how pre-job retries reuse original inbox ID |
| BOOKING_NEW | Required at PENDING; adapter not implemented | bookingId + PENDING generation; enqueue in booking creation/payment transition transaction | Which producer owns PENDING commit; no post-commit best-effort callback |
| Waitlist | `_shared/waitlist.ts` writes inbox in promotion/closure transaction | sessionId + registration UID + waitlistTicket + promotion/closure; job in that transaction | Confirm ticket uniqueness across leave/rejoin and closure reason identity |
| Admin application review | Inbox in application decision transaction; expectedSubmittedAt guards submission version | applicationId + submittedAt canonical instant + decision; enqueue with decision/inbox | Canonicalize legacy String/native Timestamp submittedAt identically; preserve existing idempotent retry recovery |
| Admin revoke owner | Inbox in role-change transaction, random ID; retry guard PLAYER/NONE | Add a caller requestId and stable decision identity; keep server role guard | Existing UID/reason alone cannot identify a later revoke after reapproval; request/API contract needs approval |
| SYSTEM | admin UID/requestId hash and resumable 300-write batches already confirmed by 120 | Keep existing ID; inbox+job pairs in the same bounded batch/transaction; reduce batch size to fit write limit | Recover matching existing inbox with missing job without resetting isRead/createdAt; account deletion checked during enqueue |
| Review create/reply | `review/handler.ts` callback runs after review commit, errors swallowed | reviewId + create incarnation / first-reply transition; enqueue before transaction commit | Delete/recreate review uses same ID, so entity ID alone is insufficient; durable incarnation key and callback boundary must be agreed |
| Group activity | `_shared/groups.ts` callback after commit; repeated edits and member requests | groupId + subtype + durable operation generation; enqueue at membership/post transition | Data.event catalog is implemented in 090 but handoff still requests D approval; repeated update/request identity missing |
| Chat | `CHAT_MESSAGE` callback; messageId already provided | chatId + messageId + recipient; inbox+job in message transaction | Always persist inbox even muted/viewing; existing callback excludes some recipients before persistence; presence multi-device policy remains open |
| Drop-in registered/changed | 112 handoff confirms registered sessionId; callback conventions differ (`userId` vs `recipientUid`) | sessionId + registration/change generation; job in source transaction | Rejoin/change generation and fanout contract must be confirmed, not inferred from sessionId |
| Booking/review reminders | Scheduler not implemented | bookingId + scheduled milestone instant + reminder kind | Eligible time/one-time policy conflict in 070 research versus 100; agree before adding markers or job writes |
| Promotion | No complete notification adapter | voucher publication generation + eligible recipient | Confirm publication identity, follower/eligibility snapshot and atomic enqueue/fanout checkpoint |

For fanout too large for the source transaction, approve a separate **source outbox/fanout record** in the business transaction, then resumable recipient inbox+job transactions. This is an alternative schema decision, not permission to invent another collection. Do not label callbacks executed after commit durable. SYSTEM already has an explicit retry request contract, while review/group/chat need a transactional handoff change. The worker cannot recover an event that was never persisted.

## Approval choices

1. **Recommended:** approve parent job plus token receipt schema above, bounded retry/retention/deletion, and server-only access; then update authoritative CSDL and Rules contracts before implementing T006/T011/T020/T051/T052. Alternative: approve aggregate job only and accept repeated pushes to already-successful devices when another token fails (weaker multi-device behavior).
2. Agree each row's event identity and enqueue transaction with its producer owner, including any API/generation/outbox additions. This unlocks T004/T005/T017/T018 and source adapters incrementally; job schema approval alone cannot unlock all producers.
3. Approve generic lock-screen text plus inbox identifier (recommended) or explicitly approve displaying stored title/body. This unlocks T014's push payload portion; inbox content/routing is unaffected.

No collection, fields, event protocol, retry worker or receipt cleanup has been implemented by this proposal update.

## Design review, 23/11/2026 — still pending schema approval

The user approved Android group routing and the local preference cache in this continuation. Neither approval authorizes delivery storage, token registration policy or an event transport. The following amendments identify gaps in the initial design; they must be included in the database/producer review before implementation.

### Recovering SENDING and exhausting a token's budget

An expired job lease can contain a receipt left in SENDING. Reclaim must treat that as an unknown external-send outcome, not leave the receipt permanently stuck or reset its counter. With fewer than six reserved attempts, a new lease reserves a new attemptId and retries that token; at six attempts, record FAILED with a sanitized unknown-outcome code. Reservation consumes an attempt even if a process dies before the FCM call. A completion from the previous lease cannot overwrite the new receipt. Unknown-outcome retries can duplicate the push; already-SENT receipts remain untouched.

leaseOwner must be a fresh nonce per acquisition, including reacquisition by the same worker process. Completion/heartbeat validates this nonce and lease expiry; a stable process label alone cannot fence an older claim. Terminal retention uses the terminal parent's updatedAt, not createdAt; pending/initializing work is not silently dropped at seven days. Receipt cleanup must finish before parent removal.

Aggregate terminal state must be evaluated only when no PENDING or recoverable SENDING receipt remains. Mixed SENT/SKIPPED means SENT if no token failed; all skipped means SKIPPED; any terminal failure means FAILED once other tokens finish. FAILED with some SENT receipts is partial success, not a reason to replay all devices. No caller retry may reset receipt counters or resurrect a finished job. Manual recovery after the automated budget requires a separately agreed operator action/audit contract; none is implemented.

### Bounded initialization and a frozen target set

The original single-transaction creation of all token receipts is safe only when token cardinality fits a commit. The existing fcmTokens array has no confirmed limit, so the proposal cannot assume this is bounded. Two reviewable choices:

| Choice | Required contract/schema | Consequence |
|---|---|---|
| Bound the profile to 100 tokens | T011 agreement, CSDL/token Rules limit and reviewed handling of oversized legacy arrays | Initial receipt creation stays small; an oversized profile must fail explicitly and retain inbox, never silently truncate recipients |
| Recommended: freeze hashes, initialize receipts in pages of 100 | Add proposed parent fields targetHashes (array of unique token hashes) and initializedThrough (integer); include document-size guard in T011/schema review | Handles larger existing arrays without discarding a device, at the cost of two unapproved job fields |

For the recommended option, the first owned-lease transaction reads the current recipient profile and freezes targetHashes exactly once with initializedThrough = 0. Subsequent owned-lease transactions create at most 100 missing receipts and advance the cursor atomically. No send starts until all targets have receipts and targetsInitializedAt is set. After a crash, the cursor resumes against the same frozen list; later token registrations do not extend it. Re-read the profile when sending to exclude removed tokens. Reject over-budget/oversized snapshots visibly instead of truncating hashes. This replaces the earlier all-receipts-in-one-transaction assumption; these extra fields are proposed only.

### Recipient deletion and write boundaries

Every new inbox/job enqueue transaction must read the authoritative recipient profile and verify it still exists and is eligible/not deleting **inside that same transaction**. A prior users scan or a pre-transaction get does not prevent late writes after account deletion. Deletion's profile update/delete then conflicts with that read; a retry must see deletion and skip creation. Each fanout chunk needs this check for its recipients. SYSTEM's existing plain batches therefore need a reviewed transactional enqueue change before they can promise deletion-safe durable jobs.

Worker claim, target initialization, receipt completion and aggregation must require the parent job to exist and match the lease; use guarded updates, never blind set/merge that could recreate a deleted parent or orphan receipt. Account cleanup first blocks new writes through the recipient profile, then queries jobs by recipientUid, deletes each token subcollection in bounded pages, deletes parents and performs a final sweep. Resume partial cleanup idempotently. Receipt creation is allowed only under a validated parent/recipient transaction; this is necessary for the final sweep to converge. Retention cleanup follows the same child-before-parent rule.

An FCM call already in flight cannot be recalled by deletion or a changed preference. SENT means provider acceptance only. These races must be stated in acceptance tests; no exactly-once display or immediate cross-device revocation is claimed.

### Tests required after approval

Use a fake FCM sender and local emulator transactions to exercise crash before send, success before receipt commit, expired SENDING recovery, stale lease completion, one token's transient failure after another succeeds, budget exhaustion, invalid-token removal preserving other/refreshed tokens, initialization across pages, and deletion racing enqueue/completion. Existing T054 verifies current inbox/profile cleanup only and does not cover this proposed storage. Verify the owned-lease and recipient read sets against actual producer transactions, not a post-commit callback stub. No tests against real FCM or hosted data are authorized.
