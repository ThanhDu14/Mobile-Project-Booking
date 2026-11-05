# Implementation mapping: Feature 112

## Checkout implementation

| Area | Implemented in this checkout | Evidence / remaining boundary |
|---|---|---|
| Owner home | Dashboard reads owned venues using the existing Feature 111 repository; loading/error/retry/empty states and calendar/venue navigation are present. | `OwnerHomeDashboardViewModel`; no fabricated booking, revenue, or drop-in counts. |
| Calendar | Day/week navigation, venue selection, active court list, date navigation, refresh, slot status display and Firestore realtime slot snapshots. Dates use `Asia/Ho_Chi_Minh`. | `OwnerCalendarViewModel`, `FirestoreOwnerCalendarRepository`; reads public slot projection only and does not read booking docs or map `qrToken`, `bookingId`, or `holdBy`. Booking overlay, blocked-hour source and HOLD status reconciliation await contract confirmation. |
| Drop-in form validation | Pure validation for capacity, VND price and capacity not below current registrations. | `DropInValidation.kt`; client validation only; server commands/queue/QR remain unimplemented. |
| Owner authorization tests | Rules Emulator test matrix for approved/pending/revoked owner, cross-venue/court and locked venue. | `owner-operations.test.ts`; Emulator suite PASS 17 files/151 tests. Existing nested court write Rules do not check locked parent consistently; Feature 111 handoff remains. |
| Test infrastructure | Fake calendar repository, coroutine test setup, owner calendar ViewModel tests, accessibility layout test, and phone/w600dp light/dark instrumentation smoke test. | Focused JVM tests PASS 9/9; instrumentation test compiles but has not run without a connected device. |
| Booking queue/details/approve/reject/cancel | Not implemented. | T005 unresolved: slot `HOLD` vs `HELD`; README `items/subtotal/total` vs Feature 060 `slotIds/subtotalAmount/paidAmount/remainingAmount`; no approved owner-safe booking read projection or mutation transaction. Do not read booking docs directly because they contain `qrToken`. |
| Drop-in owner CRUD, roster, waitlist promotion, QR | Not implemented. | T006/T008: reconcile README §4.9 vs Features 080/081 and confirm function/transaction/test ownership. |
| Booking QR check-in | Not implemented. | Feature 060/database owner must confirm `checkin-booking` source, request/response and test harness. |
| Voucher management | Not implemented. | T006: confirm `venueId` path context vs stored field and whether Feature 111 or 112 owns venue voucher CRUD; do not touch `systemVouchers` or Feature 050 redemption. |
| Statistics | Not implemented; no synthetic values displayed. | User selected bounded live booking/slot queries with no new aggregate schema. T007 remains open for safe server projection/limit/freshness, historical availability denominator and multi-court/slot allocation; current FR-015 wording still says `dailyStats` when available and needs source-owner alignment. |

## UI references inspected

All ten PNG files in `specs/112-owner-operations/ui/` were opened. They show: owner dashboard; booking queue; court calendar; booking/drop-in QR; statistics; drop-in sessions; drop-in creation; registration roster; voucher list; voucher creation. Only home and calendar have working Feature 112 screens in the current checkout. The remaining references are captured in handoffs and have not been represented as implemented. Reference images were not modified.

## Open task classification

There were 52 open tasks at the start of this implementation turn. T009, T010 and T053 are now closed with evidence, leaving 49 open (9 of 58 tasks complete). Composite tasks remain open when only part of their acceptance criteria is implemented.

| Group | Open task IDs / scope | Next action |
|---|---|---|
| A — independent work in progress | T017–T020 (calendar portion of T017/T018 and layout-test portion of T019 are implemented; booking queue portions remain); T030 (drop-in validator unit-test portion is implemented; session/roster UI test remains); T056. | Continue with current screens, update evidence and run device-dependent checks when a device is available. T020 needs a real test device/network measurement; do not substitute a local mock. T053 UI audit is complete for current screens. |
| B — owner/product decision needed | T004 (HIDDEN/LOCKED and non-disclosing response policy); T005 (booking schema/read projection/transaction boundary); T006 (drop-in and voucher ownership plus `venueId` meaning); T007 (statistics safe projection, freshness, occupancy definitions; source selected as bounded live query); T008 (canonical drop-in status/waitlist schema). | Decisions are itemized with options and recommendations in `handoffs/`. Statistics source selection received 2026-11-05; no schema or ownership changes were made. |
| C — work/confirmation owned by other features | T011–T016 (booking read/transaction contract and implementation, owners 040/050/060/100); T022–T029 (drop-in server/QR contract and implementation, owners 080/081); T031–T037 (booking check-in, owner 060/database owner); T038–T043 (voucher validation/CRUD contract, owners 050/111/database owner); T044–T051 (statistics source/aggregation, owners 050/060/120/database owner); T054 (cross-feature transaction coverage after those contracts exist); T058 (usability study after complete operational flows); T059 (booking transaction tests after function owner/harness confirmation). | Ready-to-send owner-specific requests, API/data shapes, authorization, concurrency, acceptance tests, receiving files and integration notes are in `handoffs/`. Do not edit other Feature source before authorization. |

The classification is about the remaining acceptance criteria, not a reason to pause Group A. Within composite tasks, the already implemented subparts are listed above; the checkbox remains open until the entire task is evidenced.

## Approved boundaries and integration order

1. Reuse Feature 111's current approved-owner/venue repository path for venue/court reads. Server commands must verify Firebase `appRole == OWNER`, current `users/{uid}.ownerStatus == APPROVED`, and venue ownership; client owner IDs are not authorization.
2. Continue the calendar on public slot projection. Add booking status only through an owner-safe, server-authorized projection; never grant owner reads to raw booking documents containing QR secrets.
3. Implement booking mutation only after Feature 040/050/060/100 confirms the canonical states/fields, transaction ownership, refund/release behavior and notification semantics.
4. Implement drop-in owner mutations/roster/QR only after Features 080/081 confirm canonical registration/waitlist fields and transaction function ownership.
5. Integrate booking QR only through the Feature 060/database-owner `checkin-booking` contract.
6. Implement voucher CRUD only after Feature 050/111/database owners resolve venue path vs `venueId` field and ownership boundary.
7. Implement reports only after Feature 120/database owner resolves source, metric math, historical availability and freshness.
8. Feature 111 owns venue/court/price/blocked-hour rules; its handoff records the parent `LOCKED` Rules gap. Feature 100 owns notification delivery/retry/idempotency. Feature 120 owns admin operations and any approved daily aggregation.

## Validation results (2026-11-05)

| Command | Result |
|---|---|
| `:app:assembleDebug` | PASS. |
| Focused `:app:testDebugUnitTest` | PASS, 9 tests: owner accessibility 2, calendar ViewModel 3, drop-in validation 4. |
| Full `:app:testDebugUnitTest` | FAIL, 515 completed, 7 failed, 2 skipped; all 7 failures are in existing `FirebaseSessionManagerTest`. Owner accessibility failures found in the first run were fixed and passed on rerun. |
| `:app:compileDebugAndroidTestKotlin` | PASS. |
| `:app:connectedDebugAndroidTest` | Not run: Gradle reports `No connected devices!`; APK compilation completed. |
| `:app:lintDebug` | FAIL: one error and 357 warnings. Sole error is outside Feature 112, Feature 040 `SlotMatrixRowAdapter.kt:203` (`DiffUtilEquals`, identity `===`); the calendar text-format warning has been fixed. |
| `npm test` in `firestore-tests/` | PASS with Firestore Emulator: 17 files/151 tests, including Feature 112 Rules matrix. |
| Edge Function tests | Not available: no Feature 112 function/test harness and several owning-feature paths/contracts are unconfirmed. |
| `git diff --check` | PASS after final edits; Git only reports configured LF-to-CRLF normalization notices. |
