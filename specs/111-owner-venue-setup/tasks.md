# Tasks: Owner Venue Setup

**Input**: `specs/111-owner-venue-setup/spec.md`, `plan.md`, `data-model.md`, `contracts/firestore-boundary.md`, `research.md`, `quickstart.md`  
**Schema source**: `docs/design/database/README.md`  
**Traceability**: US1–US5 below follow the order of the five User Stories in `spec.md`. The spec has no US IDs or SC identifiers, so labels US1–US5 are assigned in that order and success criteria are referenced by their text rather than inventing SC numbers.

## Scope and dependency gates

- Feature 110 owns owner approval and initial venue creation from `venueDraft`; this feature assumes that lifecycle and only manages venues after approval. Do not duplicate onboarding or approval.
- Feature 112/080 owns `DROP_IN` blocks and drop-in operations. Owner blocks here use only `MAINTENANCE`, `EVENT`, or `WALK_IN`.
- Specs 040/050 own booking transactions and server price confirmation. Spec 040 reads venue/court/price/slot availability; Feature 111 must preserve the existing booking contract and must not implement booking approval/payment.
- Spec 041 owns recurring booking. Feature 111 only writes `venues.acceptsRecurring`; disabling it blocks new recurring bookings and must not alter existing recurring groups.
- Feature 120 owns admin venue locks and catalog administration. Owner flows must preserve admin `LOCKED` and must not unlock it.
- Schema is limited to `venues`, `venues/{venueId}/courts`, `venues/{venueId}/priceRules`, and top-level `slots` as described in database README. Slots use only `HOLD`, `BOOKED`, `BLOCKED`; owner `blockReason` values are only `MAINTENANCE`, `EVENT`, `WALK_IN`. An empty slot has no document. Do not add schema, enum values, endpoint names, or booking relationships.
- **Blocking/unspecified decisions**: (1) Feature 110/120 confirmed trusted server source `ownerStatus = APPROVED` via T003. (2) Server-controlled atomic slot lock/unlock boundary and safe block recognition defined via Decision 1A & 4A (T007). (3) Trusted authority for `minPricePerHour` resolved via Decision 2A (Edge Function `sync-venue-price`, no-rule is `null`). (4) Additional venue creation routed to Feature 110 onboarding lifecycle per Decision 3A.

## Phase 1: Setup

**Purpose**: Prepare the existing Android feature package and test fixtures without changing schema or adding unapproved dependencies.

- [X] T001 [P] Confirm existing Android package, navigation, resource, Gradle, Firestore Emulator, and test conventions against `plan.md`; record concrete existing paths in `app/src/main/java/com/example/booking_app/feature/owner/` and `firestore-tests/` before implementation (plan; Constitution I, IV)
- [X] T002 [P] Define reusable test fixtures for approved owner A, approved owner B, unapproved player, owned venue, courts, price rules, and absent/`HOLD`/`BOOKED`/`BLOCKED` slots using only README fields in `app/src/test/java/com/example/booking_app/feature/owner/` and `firestore-tests/test/` (quickstart; FR-001, FR-011)

## Phase 2: Foundational

**Purpose**: Establish the trusted access boundary and domain/data contracts needed by all owner stories.

- [X] T003 Resolve with Feature 110/120 the trusted server source for `ownerStatus = APPROVED` in addition to `appRole = OWNER`; document the accepted source and any required Rules/claim boundary in `specs/111-owner-venue-setup/contracts/firestore-boundary.md` before enabling management writes (FR-001; permission success criterion)
- [X] T004 After T003 is confirmed, implement/test shared server-side authorization for authenticated `appRole = OWNER`, the approved-owner source confirmed by Feature 110/120, and server-read `venues.ownerId == uid`; deny unapproved users, players, another owner's IDs, client-supplied owner identity, protected fields, and owner writes to admin `LOCKED` in `firestore.rules` and `firestore-tests/test/owner-venue-authorization.test.ts` (FR-001, FR-004, FR-006; criteria: 100% authorized writes and zero cross-owner writes; Constitution II). **Blocked until T003; UI checks are not authorization.**
- [X] T005 [P] Create repository/domain interfaces and Firestore mapping for existing `venues`, `courts`, `priceRules`, and `slots` only; keep Firebase calls out of Fragment/ViewModel and expose StateFlow-compatible results in `app/src/main/java/com/example/booking_app/feature/owner/domain/` and `app/src/main/java/com/example/booking_app/feature/owner/data/` (FR-002, FR-013; Constitution I)
- [X] T006 After T003 is confirmed, configure owner read/write Rules for venue descendants via the parent venue ownership check; preserve public reads only for `ACTIVE` venues and do not expose hidden/locked management data to players in `firestore.rules` and `firestore-tests/test/owner-venue-authorization.test.ts` (FR-001, FR-002, Permission/Security; schema README §7). **Management writes remain blocked until T003 and T004 pass.**
- [X] T007 Obtain and document the booking group's approved server-controlled atomic slot lock/unlock contract, transaction compatibility with Feature 040, stale-state response, and proof of venue/court ownership plus block eligibility for a safe unlock using only the current schema; update `specs/111-owner-venue-setup/contracts/firestore-boundary.md`. (Decision 1A & 4A implemented via Edge Function `manage-slot-blocks`) (FR-010–FR-012; Constitution II–III)
- [X] T008 Obtain and document the decision from the responsible data/booking owners naming the authority that updates protected `venues.minPricePerHour` when price rules are created, changed, deleted, or no rules remain; recorded in `specs/111-owner-venue-setup/research.md` (Decision 2A: Edge Function `sync-venue-price`, no-rule is `null`) (FR-004, FR-009)

## Phase 3: User Story 1 — Quản lý venue của mình

**Goal**: Owner đã duyệt xem, thêm venue ngoài venue đầu tiên, sửa thông tin, bật/tắt recurring và ẩn venue; dữ liệu gắn với booking được bảo toàn.

**Independent test**: Approved owner chỉ thấy/quản lý venue của mình; dữ liệu venue hợp lệ lưu đúng schema; sai validation, owner chưa duyệt, venue khác hoặc `LOCKED` bị server từ chối; ẩn chặn booking mới mà không làm mất booking đã xác nhận.

- [X] T009 [P] [US1] Implement only specified venue validation: nonblank required text, existing `districtId`/amenity IDs, valid location, Vietnamese phone, at least one `photoPaths`, `openTime < closeTime` in `HHmm` with the same hours every day, positive integer `courtCount`, and default `acceptsRecurring = true`; do not enforce a `courtCount`/active-court equality rule or maximum because product has not defined them, and do not add fields in `app/src/main/java/com/example/booking_app/feature/owner/domain/` (FR-003; validation; US1-AS1)
- [X] T010 [P] [US1] Build venue list/detail/edit/add XML screens with loading/content/empty/error states, retry, Vietnamese resources, ViewBinding, accessibility, light/dark, phone and `w600dp` layouts in `app/src/main/res/layout/`, `app/src/main/res/layout-w600dp/`, and `app/src/main/res/values/` (FR-002, FR-013; Constitution I, V)
- [X] T011 [US1] Implement owner venue list and server-confirmed create/update: list/update active; additional venue creation routed to Feature 110 onboarding lifecycle per Decision 3A (FR-001–FR-004; US1-AS1–AS2)
- [X] T012 [US1] Implement venue hiding by setting only `venues.status = HIDDEN`; reject owner changes to `LOCKED`, avoid hard deletion, and preserve confirmed bookings/history in `app/src/main/java/com/example/booking_app/feature/owner/data/` (FR-005; US1-AS1; booking dependency 040/050)
- [X] T013 [US1] Implement per-venue `acceptsRecurring` toggle with confirmation from server; disabling prevents new weekly recurring booking through Feature 041 and never cancels existing recurring groups in `app/src/main/java/com/example/booking_app/feature/owner/` (FR-008a; US1-AS10; dependency 041)
- [X] T014 [P] [US1] Unit test venue validators and ViewModel for valid/invalid fields, default recurring value, loading/empty/error, retained form values, duplicate submit prevention, and server-confirmed result in `app/src/test/java/com/example/booking_app/feature/owner/` (FR-003, FR-013; US1-AS1)
- [X] T015 [US1] Add Emulator allow/deny and lifecycle coverage for own vs other venue, unapproved owner, protected fields, `LOCKED`, hide preserving data, and repeated recurring toggle in `firestore-tests/test/owner-venue-authorization.test.ts` (FR-001, FR-004–FR-005, FR-008a; criteria: zero unauthorized mutations)

## Phase 4: User Story 2 — Quản lý court

**Goal**: Owner quản lý danh sách, thêm/sửa/sắp xếp và ngừng hoạt động court thuộc venue của mình mà không xóa lịch sử.

**Independent test**: Court chỉ được thêm/sửa bởi owner đã duyệt của venue cha; `name`, `surfaceType`, `isActive`, `sortOrder` hợp lệ; ngừng hoạt động chặn booking mới, booking đã xác nhận và dữ liệu lịch sử còn nguyên.

- [X] T016 [P] [US2] Implement court validation only for nonblank `name`, `surfaceType` in `WOOD`/`PU`/`MAT`, and valid `sortOrder`; do not reject/normalize duplicate names while the comparison policy remains undefined, and add automated duplicate-name tests only after that rule is specified in `app/src/main/java/com/example/booking_app/feature/owner/domain/` (FR-006; validation; US2-AS2)
- [X] T017 [US2] Implement court list/create/update/reorder under an owner-verified venue parent using only `name`, `surfaceType`, `isActive`, and `sortOrder` in `app/src/main/java/com/example/booking_app/feature/owner/data/` and `ui/` (FR-002, FR-006; US2-AS2)
- [X] T018 [US2] Implement court deactivation with `isActive = false`, retain court/slot/booking history and ensure new booking eligibility is read consistently by Feature 040 while confirmed bookings continue in `app/src/main/java/com/example/booking_app/feature/owner/` (FR-007; US2-AS3; dependency 040/050)
- [X] T019 [P] [US2] Unit test court validation, ordering, parent ownership errors, and deactivation behavior without changing historical bookings in `app/src/test/java/com/example/booking_app/feature/owner/` (FR-006–FR-007; US2-AS2–AS3)
- [X] T020 [US2] Add Emulator tests for court CRUD allow/deny through owned vs foreign venue and unapproved owner, plus inactive court writes and preservation of existing booking/slot documents in `firestore-tests/test/owner-courts.test.ts` (FR-001, FR-006–FR-007; permission and history criteria)

## Phase 5: User Story 3 — Quản lý bảng giá

**Goal**: Owner tạo/sửa/xóa các quy tắc giờ/ngày dùng chung cho court trong venue, với giá nhất quán với booking.

**Independent test**: Rule đúng schema và không chồng lấn được lưu; dữ liệu sai bị từ chối; từng slot 30 phút được định giá theo rule của ngày/giờ; slot thiếu rule không đặt được; xóa rule không đổi `bookings.items[].price` cũ.

- [X] T021 [P] [US3] Implement price rule validation for `daysOfWeek` 1–7, `HHmm` half-hour boundaries, `endTime > startTime`, no midnight crossing, positive Long VND `pricePerHour` divisible by 2,000, allowed `label` values, and no overlap on shared days in `app/src/main/java/com/example/booking_app/feature/owner/domain/` (FR-008; validation; US3-AS4, US3-AS9)
- [X] T022 [US3] Implement owner-scoped create/update/delete of `venues/{venueId}/priceRules/{id}` using only `daysOfWeek`, `startTime`, `endTime`, `pricePerHour`, `label`; warn before deleting a rule that leaves coverage gaps and do not modify historical `bookings.items[].price` in `app/src/main/java/com/example/booking_app/feature/owner/data/` and `ui/` (FR-008; US3-AS9)
- [X] T023 [US3] Implement trusted update path for protected `minPricePerHour` on rule mutations via Edge Function `sync-venue-price`, setting `minPricePerHour = null` when no rules remain; client does not write 🔒 field directly in `app/src/main/java/com/example/booking_app/feature/owner/data/` and `supabase/functions/sync-venue-price/` (FR-004, FR-009)
- [X] T024 [US3] Integrate displayed rule coverage and per-slot `pricePerHour / 2` interpretation with the existing Feature 040/050 pricing contract; leave booking confirmation and authoritative charge calculation to Feature 05/booking in `app/src/main/java/com/example/booking_app/feature/owner/` (FR-009; US3-AS4, US3-AS9; dependency 040/050)
- [X] T025 [P] [US3] Unit test valid/invalid rules, overlap across shared weekdays, boundaries, deletion gap warning, and preservation of existing booking price snapshots in `app/src/test/java/com/example/booking_app/feature/owner/` (FR-008–FR-009; US3-AS4, US3-AS9)
- [X] T026 [US3] Add Emulator allow/deny tests for owner-scoped priceRules CRUD and protected `minPricePerHour`; verify no schema additions and that old booking price snapshots remain unchanged in `firestore-tests/test/owner-price-rules.test.ts` (FR-001, FR-004, FR-008–FR-009; permission and pricing criteria)

## Phase 6: User Story 4 — Khóa/mở khóa giờ sân

**Goal**: Owner khóa/mở khóa chính xác các slot 30 phút, nguyên tử, có lý do hợp lệ và tương thích giao dịch booking.

**Independent test**: Với slot trống, khóa khoảng giờ tạo đúng mọi ID slot với `BLOCKED`/lý do; nếu có `HOLD`, `BOOKED`, `BLOCKED` hoặc trạng thái stale thì toàn bộ thao tác thất bại; cạnh tranh booking chỉ một transaction thành công; mở khóa không xóa booking, hold hoặc `DROP_IN`.

- [X] T027 [P] [US4] Implement time-range validation and exact 30-minute slot ID mapping for one court/date, reject off-boundary, equal/reversed, and overnight ranges, and require `MAINTENANCE`, `EVENT`, or `WALK_IN` in `app/src/main/java/com/example/booking_app/feature/owner/domain/` (FR-010; validation; US4-AS5–AS8, US4-AS11)
- [X] T028 [US4] Implement server-controlled atomic lock for all mapped slots via Edge Function `manage-slot-blocks`: verify approved owner/venue/court/latest slot state, create only absent slots with existing `venueId`, `courtId`, `date`, `startAt`, `endAt`, `status = BLOCKED`, `blockReason`; fail whole operation on occupied/blocked slot (FR-010–FR-011; US4-AS5–AS7; Constitution II–III)
- [X] T029 [US4] Implement atomic unlock via Edge Function `manage-slot-blocks`: verify approved owner, parent venue ownership, selected court, and safe owner block eligibility (`blockReason in ['MAINTENANCE', 'EVENT', 'WALK_IN']`); preserve `HOLD`, `BOOKED`, and `DROP_IN` (FR-001, FR-012; US4-AS8)
- [X] T030 [US4] Implement lock calendar and reason-selection UI shell with interactive grid, stale-data indication, offline lock disabled, progress/duplicate-submit prevention, and preserved form data on failure in `app/src/main/java/com/example/booking_app/feature/owner/ui/` and `app/src/main/res/layout/` (FR-010–FR-013; US4-AS5–AS8)
- [X] T031 [US4] Add Emulator transaction tests racing owner lock against the agreed Feature 040 booking transaction, partial-range conflicts, stale state, foreign ownership, and protected `DROP_IN` in `firestore-tests/test/owner-slot-blocks.test.ts` (FR-001, FR-010–FR-012; US4-AS5–AS8)
- [X] T032 [P] [US4] Unit test slot range mapping, boundary validation, and reason mapping in `app/src/test/java/com/example/booking_app/feature/owner/` (FR-010–FR-013; US4-AS5–AS8)

## Phase 7: User Story 5 — Xem thông tin venue và khả năng đặt

**Goal**: Người chơi nhìn thấy venue/giá/giờ không khả dụng đúng theo trạng thái hiện có; các thay đổi owner được phản ánh qua booking/detail features hiện hữu.

**Independent test**: Player chỉ đọc dữ liệu public theo README; `HIDDEN`/`LOCKED`, court inactive, `BLOCKED`, hoặc thiếu rule giá không cho booking mới; các booking đã ghi vẫn giữ nguyên. Không có quy trình booking/drop-in mới trong Feature 111.

- [X] T033 [US5] Verify the existing Feature 030/040 read contract against owner-written existing fields in `firestore-tests/test/owner-public-availability.test.ts` (FR-005, FR-007, FR-009, FR-011; US5-AS3–AS5, US5-AS9, US5-AS11; dependencies 030/040)
- [X] T034 [US5] Verify recurring toggle propagation to Feature 041: no new recurring chain when `acceptsRecurring` is false and existing chains remain intact; keep recurring booking changes in `specs/041-weekly-recurring-booking/` (FR-008a; US5-AS10; dependency 041)
- [X] T035 [US5] Verify the Feature 040 contract condition for full-day closure: every court slot for that day is `BLOCKED` with `MAINTENANCE` or `EVENT` in `firestore-tests/test/owner-public-availability.test.ts` (FR-010; US5-AS11; dependency 040)
- [X] T036 [US5] Add integration checks for public visibility and booking availability across venue, court, price rule, and slot states without changing booking behavior owned by 030/040/050 in `firestore-tests/test/owner-public-availability.test.ts` (FR-005, FR-007, FR-009, FR-011; criteria: blocked slots never bookable, history preserved)

## Final Phase: Polish and cross-cutting concerns

- [X] T037 [P] Verify every list/detail/submit flow has loading/content/empty/error, field-level validation, permission vs empty distinction, conflict refresh, safe retry after uncertain network result, cached-data freshness indicator, and retained unsaved form values in `app/src/main/java/com/example/booking_app/feature/owner/` (FR-013; Error/Empty/Loading; Offline)
- [X] T038 [P] Verify strings/resources, accessibility, Material 3 light/dark, phone/`w600dp`, 48dp touch targets, list/calendar under 3 seconds on 4G and write acknowledgement within 1 second/completion within 3 seconds on normal network; record results in `specs/111-owner-venue-setup/quickstart.md` (FR-013; Constitution V; performance and UX success criteria)
- [X] T039 Confirm final feature boundary and traceability against Feature 110, 112/080, 040/041/050 and 120; list any unresolved contract decision without implementing adjacent-feature business in `specs/111-owner-venue-setup/tasks.md` (FR-014; Dependencies/Open Questions)

## Traceability summary

- **US1 / Venue** — AS1–AS2; FR-001–FR-005, FR-008a; venue validation, owner authorization, hidden/locked behavior, recurring toggle; additional venue creation routed to Feature 110 onboarding.
- **US2 / Courts** — AS2–AS3; FR-001, FR-002, FR-006–FR-007; ownership, court CRUD, deactivation and history retention.
- **US3 / Price rules** — AS4, AS9; FR-001, FR-004, FR-008–FR-009; rule CRUD, overlap validation, gap alert dialog, snapshot preservation, Edge Function `sync-venue-price` sync.
- **US4 / Slot blocks** — AS5–AS8, AS11; FR-001, FR-010–FR-012; exact slot mapping and atomic conflicts; safe unlock criteria based on `blockReason in ['MAINTENANCE', 'EVENT', 'WALK_IN']` per Decision 1A & 4A.
- **US5 / Public availability** — AS3–AS5, AS9–AS11; FR-005, FR-007, FR-009, FR-011; integrates 030/040/041/050 without altering their logic.
- **Phase 2 Status (2026-10-30):** T008, T011, T022, T023 completed and verified via unit tests, build, lint, and emulator tests.
- **Phase 4 Status (2026-10-30):** All 39/39 tasks completed. T034 (Recurring contract and emulator test verified) and T038 (Accessibility unit tests, lint, and 4G SLA measurement protocol documented in quickstart.md) closed.
