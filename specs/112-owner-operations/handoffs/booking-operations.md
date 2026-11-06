# Handoff: booking schema, safe read and owner commands

**Feature owners:** Feature 040 (booking/slot creation), Feature 050 (payment/refund and booking lifecycle), Feature 060 (player booking data and check-in), database owner.  
**Feature 112 consumer:** owner booking list/detail, day/week labels, approve/reject/cancel and booking check-in adapter.

## Problem and current source

- Database README §4.4/§4.5 says slot state `HOLD`, booking `items`, `subtotal`, `discount`, `total`.
- Feature 060 `data-model.md` says `slotIds`, `subtotalAmount`, `discountAmount`, `paidAmount`, `remainingAmount`; shared Firestore Rules and booking tests use `HELD` for temporary slots.
- `firestore.rules` permits booking reads only to the player and Admin. `bookings` may contain `qrToken`; querying booking documents from the owner client would expose the token if the owner read were allowed.
- Database README names `checkin-booking`, but this checkout has no `supabase/functions/checkin-booking/` or Feature 112 Edge Function harness.
- Feature 112 requires PENDING approval before `approvalDeadline`, rejection with slot release, and cancellation/refund of confirmed bookings. Feature 050/060 own financial effects; Feature 040 owns slot/booking creation.

## Contract request

### Decision/source of truth

Please identify the authoritative deployed field/status model. Reconcile README, Feature 060 model, Feature 040 writers, Rules and fixtures. Do not make Feature 112 support conflicting write schemas.

### Proposed owner-safe read response (no new persisted schema)

The request must derive UID and role from verified Firebase ID token; `venueId`, date range and filters are selectors only. Server verifies `appRole == OWNER`, reads current `users/{uid}.ownerStatus == APPROVED`, verifies `venues/{venueId}.ownerId == uid`, and rejects LOCKED venues. Return only fields already present and needed by owner UI, for example:

```json
{
  "bookingId": "<id>",
  "customerDisplayName": "<approved display field>",
  "courtIds": ["<id>"],
  "startAt": "<timestamp>",
  "endAt": "<timestamp>",
  "status": "PENDING",
  "paymentStatus": "DEPOSITED",
  "totalAmount": 0,
  "depositAmount": 0,
  "approvalDeadline": "<timestamp>",
  "checkedIn": false
}
```

The projection must never contain `qrToken`, raw booking code, private player contact fields unless separately approved, or another venue's data. Confirm exact existing field mappings before freezing the DTO; no client-supplied owner UID.

### Proposed commands to confirm

Approve/reject: input contains booking ID and requested decision only. Transaction re-reads booking, venue, current owner status and every referenced slot; it requires current `PENDING`, deadline not passed, matching slot/booking IDs and eligible venue/courts. It atomically writes the approved/rejected booking state and the corresponding slot states. Concurrent or repeated commands return current state/conflict without partial writes.

Cancel confirmed: define eligibility (before start / not checked in), required reason, 100% refund, refund ledger/wallet boundary, slot release and notification-after-commit behavior. Financial side effects must not be claimed atomic with Firestore unless the actual payment store supports it. Define idempotency/retry behavior.

Booking check-in: confirm ownership of `checkin-booking`, request by QR/text booking code, accepted time window, duplicate/replay response and optional owner-confirmed payment collection. The response returns safe booking fields and never the secret token.

## Authorization, errors and acceptance

- Claims and current owner status are server-verified; owner ID is never accepted from client.
- Foreign, hidden/locked, revoked, stale-status, expired, mismatched-slot and replay cases disclose no target data.
- Define stable error categories for permission, not found, stale/conflict, expired, invalid state, slot conflict and retryable transport failure.
- Emulator/backend tests: approved owner success; pending/revoked owner denied; foreign venue denied; locked venue denied; expired and stale status do not write; all slots/bookings agree after success; forced conflict leaves no partial write; duplicate command is safe; owner DTO contains no QR/code secret; cross-venue list query cannot enumerate.

## Files/specs to update in receiving features

Update the authoritative source among `docs/design/database/README.md`, `specs/040-*/`, `specs/050-*/`, `specs/060-*/`, shared `firestore.rules`/Rules tests, plus the named function API and its real test harness. Identify function owner/path, deploy target and emulator command in the response. Do not edit Feature 112 files as a substitute for updating the owner contract.

## Feature 112 integration after approval

Implement only `feature/owner` DTO/domain/repository/viewmodel/UI adapters. Use the agreed server endpoint for private booking projections and commands. Keep player booking creation, payment/refund internals, timeout/finalize and QR issuance in their existing owners.

## Ready-to-send implementation prompt

```text
Resolve the attached Feature 112 booking handoff. First identify the authoritative deployed booking/slot schema and reconcile Feature 040, Feature 060, Database README, Rules, fixtures and checkin-booking. Define an owner-safe read response that excludes qrToken/raw QR code and private fields, with server-side appRole/current ownerStatus/venue ownership checks. Define transactional approve/reject and the cancellation/refund boundary in the feature that owns each operation. Confirm checkin-booking ownership, time window, replay behavior, and test path. Add Emulator/backend authorization, conflict, duplicate and rollback tests; run them and report exact commands/results. Edit only files owned by Features 040/050/060 or database owner. Do not edit Feature 112 or invent a persisted field/API without approval. Return the final contract, changed files and ready-to-integrate endpoint details.
```

### Current Feature 112 client seam

Feature 112 now contains a QR/manual-code check-in screen and domain repository seam, but intentionally has no wire DTO or invocation until the function owner confirms exact request/response fields. The scanner feeds the captured value into the same untrusted credential input as manual entry; the server must validate it. Specify whether owner-confirmed payment is a boolean or amount-specific command, the safe response needed for UI confirmation, stable errors, and how an ambiguous timeout is resolved by an authoritative read. Do not ask Feature 112 to submit owner UID, a token read from a raw booking document, or a client-computed amount.
