# Implementation mapping: Feature 112

## Checkout implementation

| Area | Implemented in this checkout | Evidence / remaining boundary |
|---|---|---|
| Owner home | Dashboard reads owned venues using the existing Feature 111 repository; loading/error/retry/empty states and calendar/venue navigation are present. | `OwnerHomeDashboardViewModel`; no fabricated booking, revenue, or drop-in counts. |
| Calendar | Day/week navigation, venue selection, active court list, date navigation, refresh, slot status display and Firestore realtime slot snapshots. Dates use `Asia/Ho_Chi_Minh`. | `OwnerCalendarViewModel`, `FirestoreOwnerCalendarRepository`; reads public slot projection only and does not read booking docs or map `qrToken`, `bookingId`, or `holdBy`. Booking overlay, blocked-hour source and HOLD status reconciliation await contract confirmation. |
| Booking queue UI/domain | Owner booking model, queue filtering/sorting, approval deadline countdown, venue/filter controls, details, error/empty/loading/retry states, Owner bottom navigation and `w600dp` resources. | `OwnerBooking`, `OwnerBookingQueue`, `OwnerBookingsViewModel`, `OwnerBookingsFragment`; `ContractPendingOwnerBookingRepository` fails closed because raw Firestore booking reads expose `qrToken` and Rules deny owner reads. Approve/reject controls remain disabled pending T005. |
| Booking check-in UI | Google Play Services QR scanner, manual code entry, owner-confirmed payment checkbox, pending/error/success state and ViewModel. | `OwnerBookingCheckInFragment`, `BookingCheckInViewModel`; `ContractPendingBookingCheckInRepository` does not guess the `checkin-booking` wire payload. No function implementation exists in this checkout. |
| Drop-in form validation | Pure validation for capacity, VND price, date/time, required venue/court/level and capacity not below current registrations. | `domain/dropin/OwnerDropInModels.kt`, `OwnerDropInDraftValidationTest` (4 PASS); client validation only; session/roster reads and writes fail closed pending 080/081 contract/schema alignment. |
| Drop-in owner UI/domain | Session list/form/roster fragments, active venue/court selectors, schema level choices, QR/manual roster check-in UI, navigation, ViewModel StateFlow and phone/`w600dp` layouts. | `OwnerDropInViewModel`, repository interface and fail-closed adapter; no production mock data or server commands. UI/test sources compile; connected instrumentation needs a device. |
| Voucher management UI/domain | Venue-scoped list/form ViewModel, validation, activation switch, navigation and phone/`w600dp` layouts; draft uses path context and omits server-owned `usedCount`. | `OwnerVoucherDraft`, `OwnerVoucherViewModel`, list/form fragments and fail-closed repo. Validator tests (3 PASS); Feature 050 data-model divergence and 111/112 ownership decision prevent wire DTO/CRUD. |
| Statistics UI/domain | Day/week/custom date controls, Asia/Ho_Chi_Minh query ranges, loading/empty/error/partial-freshness states, chart view and venue selection. | `OwnerStatisticsViewModel`, typed query/result/repository, fail-closed adapter, `OwnerStatisticsViewModelTest` (2 PASS). No formulas or values are fabricated; bounded live query source is user-selected, but server projection/freshness/denominator/allocation remain unresolved. |
| Owner authorization tests | Rules Emulator test matrix for approved/pending/revoked owner, cross-venue/court and locked venue. | `owner-operations.test.ts`; Emulator suite PASS 17 files/151 tests. Existing nested court write Rules do not check locked parent consistently; Feature 111 handoff remains. |
| Test infrastructure | Fake calendar repository, coroutine test setup, booking queue/check-in/drop-in/voucher/statistics unit tests, accessibility and phone/dark/`w600dp` layout assertions. | Latest focused run: 17 owner tests PASS; `:app:compileDebugAndroidTestKotlin` PASS. Connected instrumentation remains dependent on an Android device. |
| Booking read/approve/reject/cancel | Queue and detail UI scaffold implemented; list not populated until server-safe read exists; decision controls disabled. | T005 unresolved: slot `HOLD` vs `HELD`; README `items/subtotal/total` vs Feature 060 `slotIds/subtotalAmount/paidAmount/remainingAmount`; no approved owner-safe booking read projection or mutation transaction. Do not read booking docs directly because they contain `qrToken`. |
| Drop-in owner CRUD, roster, waitlist promotion, QR | UI, draft validation, ViewModel and repository contract are present; no sessions/roster are read and no command is sent. | T006/T008: reconcile README §4.9 vs Features 080/081 and confirm function/transaction/test ownership. |
| Booking QR check-in | QR/manual code UI and ViewModel implemented; backend adapter is fail-closed. | Feature 060/database owner must confirm `checkin-booking` source, request/response, check-in window, replay/payment semantics and test harness. |
| Voucher management backend | No persistence, CRUD or redemption implementation is enabled. | T006: confirm `venueId` path context vs stored field and whether Feature 111 or 112 owns venue voucher CRUD; do not touch `systemVouchers` or Feature 050 redemption. |
| Statistics data | UI/domain query contract exists, but no report data is connected and no synthetic values are displayed. | User selected bounded live booking/slot queries with no new aggregate schema. T007 remains open for safe server projection/limit/freshness, historical availability denominator and multi-court/slot allocation; current FR-015 wording still says `dailyStats` when available and needs source-owner alignment. |

## UI references inspected

All ten PNG files in `specs/112-owner-operations/ui/` were opened. They show: owner dashboard; booking queue; court calendar; booking/drop-in QR; statistics; drop-in sessions; drop-in creation; registration roster; voucher list; voucher creation. Android screens now exist for booking queue/check-in, drop-in list/form/roster, voucher list/form and statistics, in addition to home/calendar. Backend data/commands remain fail-closed where contracts are unresolved. Layout implementation follows the supplied page structure and project components; device visual comparison remains unverified because no connected device is available. Reference images were not modified.

## Open task classification

There were 49 open tasks at the start of this implementation turn. T014 is now closed with domain/interface implementation evidence; the checkout has 58 tasks, 10 closed and 48 open. T011/T017–T019/T024/T028–T030/T031–T037/T038–T043/T047/T049–T051 have implementation subparts, but remain open until their full acceptance criteria and contract/device evidence are met. Composite tasks remain open when only part of their acceptance criteria is implemented.

| Group | Open task IDs / scope | Next action |
|---|---|---|
| A — independent work in progress | T011 (queue tests PASS; source refresh test needs real projection); T017–T020 (booking UI/ViewModel and existing calendar; safe data, mutations, device/network evidence remain); T024/T028–T030 (drop-in model/list/form/roster/UI tests added; server projection/commands/device execution remain); T031/T033–T037 (check-in UI/input/ViewModel tests added; approved wire mapping, amount projection and device test remain); T038–T043 (voucher input/list/form/activation UI and validator/layout tests added; CRUD ownership/schema remains); T047/T049–T051 (typed statistics query/UI/period tests added; formulas/data source/device verification remain); T056. | Implement independent UI/domain work done for this pass; continue after contract decisions. T020 needs a real test device/network measurement. |
| B — owner/product decision needed | T004 (HIDDEN/LOCKED and non-disclosing response policy); T005 (booking schema/read projection/transaction boundary); T006 (drop-in and voucher ownership plus `venueId` meaning); T007 (statistics safe projection, freshness, occupancy definitions; source selected as bounded live query); T008 (canonical drop-in status/waitlist schema). | Decisions are itemized with options and recommendations in `handoffs/`. Statistics source selection received 2026-11-05; no schema or ownership changes were made. |
| C — work/confirmation owned by other features | T004–T008 unresolved decisions; T012–T016 booking safe read/transaction; T022–T029 drop-in server/QR; T032–T035 booking check-in contract/function; T038–T043 voucher ownership/CRUD; T044–T051 statistics server source/math; T054 transaction coverage; T058 usability study; T059 booking transaction tests. | Owner-specific requests and sendable prompts are in `handoffs/`. Do not edit other Feature source before authorization. |

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
| `:app:assembleDebug` | PASS after QR scanner, drop-in, voucher and statistics navigation/resource integration. |
| Focused `:app:testDebugUnitTest` | PASS, 17 tests: owner accessibility 2, booking queue 3, check-in ViewModel 3, voucher validation 3, drop-in validation 4, statistics ViewModel 2. |
| Full `:app:testDebugUnitTest` | FAIL, 521 tests completed, 7 failed, 2 skipped; all 7 failures are in existing `FirebaseSessionManagerTest`. No Feature 112 test failed after the new 48dp fix. |
| `:app:compileDebugAndroidTestKotlin` | PASS; booking/drop-in/voucher/statistics layout test sources compile. |
| `:app:connectedDebugAndroidTest` | BLOCKED: Gradle reports `No connected devices!`; APK compilation completed. |
| `:app:lintDebug` | Latest run in progress; record outcome after completion. |
| `npm test` in `firestore-tests/` | PASS with Firestore Emulator: 17 files/151 tests, including Feature 112 Rules matrix. |
| Edge Function tests | Not available: no Feature 112 function/test harness and several owning-feature paths/contracts are unconfirmed. |
| `git diff --check` | PASS after final edits; Git only reports configured LF-to-CRLF normalization notices. |
