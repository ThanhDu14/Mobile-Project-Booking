# Handoff: enforce locked parent venue for court writes

**Feature owner:** Feature 111 (shared venue/court Firestore Rules).  
**Feature 112 impact:** court/slot operations must not accept a venue whose parent status is `LOCKED`.

## Finding

In current `firestore.rules`, venue update rejects writes when the existing venue is LOCKED, but nested `venues/{venueId}/courts/{courtId}` create/update use `ownsVenue(venueId)` without a parent-status check. `ownsVenue` verifies APPROVED owner and `ownerId`, not venue status. This permits an approved owner to mutate child court config under a locked venue through the shared Rules boundary.

## Required change and tests

Feature 111 should add a reusable parent-venue-operable predicate or equivalent check to court create/update (and any other owner child writes intended to stop at LOCKED), preserving Admin behavior and the existing contract for ACTIVE/HIDDEN venues. Do not broaden owner reads or modify slot/booking Rules as part of this fix.

Add Firestore Rules Emulator cases: approved owner can create/update court under ACTIVE venue; same owner denied under LOCKED venue; owner denied under foreign venue; pending/revoked owner denied; Admin behavior unchanged. Confirm whether HIDDEN permits court maintenance separately from Feature 112 operational commands.

## Receiving files and integration

Update Feature 111 `contracts/firestore-boundary.md`, `firestore.rules`, `firestore-tests/test/owner-courts.test.ts` (or the owning suite) and run its existing Emulator command. Feature 112 already has a locked-parent assertion in `firestore-tests/test/owner-operations.test.ts`; the assertion remains unverified until the owner fixes Rules and the Emulator passes.

## Ready-to-send implementation prompt

```text
Implement the attached Feature 112 handoff in Feature 111 only. Inspect shared Firestore Rules: nested court create/update currently use ownsVenue(venueId) without checking that the parent venue is not LOCKED. Add the contract-consistent parent status gate while preserving Admin behavior and ACTIVE/HIDDEN policy. Add and run Rules Emulator allow/deny tests for ACTIVE, LOCKED, foreign, pending/revoked and Admin cases. Update Feature 111's Firestore boundary contract and report exact test command/results. Do not edit Feature 112 or weaken owner authorization.
```
