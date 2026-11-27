# Booking notification decisions, 24/11/2026

Status: **B1 and B2 approved by the user on 24/11/2026**. B1 source payment will be provided later; B2 limits were explicitly accepted. D1/D2-W1/T1/C1/P1/S1 remain approved. Only the B1/B2 source contracts and data.reminderStartAt described here are approved. Other producer/schema changes, deployment and live migration remain unapproved.

## Audit and missing source

- `_shared/owner-booking-command.ts` already enqueues CONFIRMED/REJECTED/owner-CANCELLED in its business transaction using family booking and identity JSON([bookingId, canonicalType]). `booking-timeout/handler.ts` uses the same REJECTED identity and writes refund/inbox/job atomically. Replay is guarded by source state and the shared job lookup. These are W1, not pending decisions.
- `player-cancel-booking/handler.ts` has no inbox/job writer. Its cancellation/refund transaction itself is outside this continuation's scope; test notification exclusion without presenting the older handler as a complete transactional cancellation implementation.
- No production payment confirmation endpoint or HOLD-to-PENDING transaction was found in the current checkout. `feature/booking/ui/HoldToPaymentArgs.kt` is an argument DTO; `specs/050-payment-voucher/contracts/ui-contracts.md` describes the PENDING destination but contains no server payment transaction. Scripts seed PENDING directly for fixtures and are not a payment producer. Do not invent a payment endpoint or derive BOOKING_NEW from fixtures.
- CSDL §4.5 defines bookings.startAt as earliest items[].startAt and rescheduleRequest as a pending/accepted/declined request. There is no confirmed reschedule acceptance producer here. Source routing still lacks an owner booking target URI; BOOKING_NEW keeps saved detail.

## B1. BOOKING_NEW proposal

Approve internal enqueue contract only: family booking; identity JSON([bookingId, "BOOKING_NEW"]); recipient from venues/{booking.venueId}.ownerId read inside the actual transaction changing HOLD to PENDING. Recheck current ACTIVE venue and ACTIVE, non-deleting APPROVED OWNER recipient. Do not trust ownerUid from a client. Persist inbox/job together with PENDING and pendingAt/approvalDeadline. Source-side replay returns the existing result and never re-enqueues; no post-commit callback or second inbox writer.

Keep an existing notification ID if the source already creates one; if no writer exists use deterministic n_{jobId}, supported by the shared helper, and the existing six inbox fields. Proposed content: title "Có đơn đặt sân mới", body indicating a pending booking and its existing approval deadline; data {bookingId, venueId}. BOOKING_NEW remains forced push-eligible under OS restrictions. No new payment fields, marker or HTTP envelope.

**Missing input:** the actual payment transaction file/commit from Feature 050 must be supplied or integrated before wiring. Approval of B1 alone does not manufacture that producer or authorize a replacement payment flow. Opens contract portion of T004/T018; T015/T018 remain partial until the actual producer and tests exist.

## B2. BOOKING_REMINDER proposal

Choose **one inbox reminder for the lifetime of a booking ID**, matching FR-010/FR-014. Family booking-reminder; identity JSON([bookingId, "BOOKING_REMINDER"]); deterministic inbox n_{jobId}. Existing inbox prevents a new job even after terminal-job retention. No booking marker field or new collection.

Eligibility: trusted server now, status CONFIRMED, nonempty valid items[].startAt Timestamp, min(items.startAt) in (now, now+2h], derived booking.startAt equal to that minimum, recipient booking.userId ACTIVE/non-deleting. A mismatch or invalid/missing timestamp is skipped and counted for review, not silently repaired. Check again in the same transaction as inbox/job creation. The cron runs every minute, using x-cron-secret, never directly sending FCM.

Query: CONFIRMED and derived startAt > captured invocation now and <= now+2h, ASC startAt then document ID, pages of 100 with cursor carrying the Timestamp and document reference. Cursor is invocation-local, no persisted scheduler state. Process with a time budget, and next invocation starts at the current window; exhausted-budget/backlog fairness must be verified, not assumed. Add/verify the actual state/startAt index only after approval. Server REST cursor support is a query API change, not a new event transport.

Late handling: emit at the first successful tick between due time and start, including a booking confirmed within two hours of start. Never emit a new reminder once the game has started. This accepts a missing reminder if the scheduler was unavailable for the whole eligible window; no stale catch-up after start.

Reschedule: before the first reminder, use the latest committed items/time. Once an inbox reminder exists, do not create a second one after a move. Store the chosen time as data.reminderStartAt (Timestamp) with bookingId/venueId so delivery can recheck booking ownership/status/current earliest time. If time changed or booking is no longer CONFIRMED before sending, SKIP the old job while retaining saved inbox. This deliberately trades a replacement push for the one-reminder invariant; tap still checks current source access. No reschedule API or generation field is approved by this proposal.

Tests after approval: exact boundary, earliest of unordered items, all non-CONFIRMED states, invalid/derived mismatch, late tick/after-start, reschedule before/after first reminder, concurrent scheduler/replay, deletion race, failure rollback and FCM failure leaving inbox. Opens T016/T019 and reminder portion T005; does not close T005 across unrelated producers.

## Environment and coordination

Docker daemon is still unavailable on 24/11, including outside sandbox (dockerDesktopLinuxEngine pipe absent). Supabase local endpoint/cron SQL validation remains unrun. Auth/Firestore emulator and fake FCM can independently verify approved source handlers.

Coordination requests will be shown directly in the conversation, not saved as message files. Feature B needs to identify/integrate the actual HOLD-to-PENDING transaction and confirm the B1 boundary; reminder/reschedule semantics require the explicit B2 choice before dependent implementation.
