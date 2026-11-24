---
description: "Task list for Feature 100 – Notification"
---

# Tasks: Thông báo

**Input**: Design documents from `/specs/100-notification/`

**Prerequisites**: plan.md, spec.md, data-model.md, contracts/, research.md, quickstart.md

**Tests**: Included where required by the specification and Constitution IV: unit, Firebase Emulator Rules, Edge Function, and focused UI/integration tests. Tests use local/emulated services only.

**Organization**: Tasks are grouped by the five user stories in spec priority order. Cross-story foundations are unlabelled.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Discovery only. T001–T003 inspect and report existing build/test conventions; they do not add dependencies, initialize services, or create implementation scaffolding. Any actual setup work must be defined after project-wide dependency and harness choices are confirmed.

- [X] T001 [P] Inventory the existing Gradle version catalog and Android test harness, and record missing notification prerequisites without selecting or adding dependencies in `gradle/libs.versions.toml` and `app/build.gradle.kts` (Constitution I, IV, VII)
- [X] T002 [P] Inventory the existing Firestore Emulator harness and Rules test entry points, recording setup gaps without creating test infrastructure in `firestore-tests/`
- [X] T003 [P] Inventory existing Supabase Edge Function and Deno conventions, recording setup gaps without creating functions or selecting dependencies in `supabase/functions/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Record unresolved integration decisions. Only directly dependent implementation tasks wait for each decision; independent work can continue. Do not invent event, idempotency, or delivery schemas.

- [ ] T004 NEEDS DECISION: agree and record source event transport, minimal payload, producer/resolver responsibility, and event identity with producer features in `specs/100-notification/contracts/event-integration.md` (FR-002; blocks only event adapters T018, T023, T024, T046–T049)
- [ ] T005 NEEDS DECISION: agree and record how to enforce one inbox record per `(business event, recipient uid, type)` and prevent duplicate reminder processing without assuming new fields/collections in `specs/100-notification/research.md` and `docs/design/database/README.md` (FR-014; blocks shared inbox writer T017 and reminder scheduling/deduplication T019; unrelated Android inbox/UI work can proceed)
- [X] T006 NEEDS DECISION: agree and record FCM retry/backoff, partial fan-out recovery, invalid-token cleanup, and whether delivery-state schema is needed in `specs/100-notification/research.md` and `docs/design/database/README.md` (blocks retry/recovery implementation in T020, T025, T048–T049; do not add fields/collections/statuses before approval)
- [X] T007 NEEDS DECISION: record and resolve the conflict between DB README §7 (Admin C), DB §4.13, Feature 120, and server-authoritative SYSTEM delivery; do not change schema, Rules, or Admin authorization until the document owners agree, then update the owning contract in `specs/100-notification/contracts/firestore-security.md` (blocks only SYSTEM authorization/fan-out T049 and any Rules change that depends on the Admin permission decision; own-user inbox Rules can proceed)
- [X] T008 NEEDS DECISION: obtain Feature 091 confirmation of the chat foreground-presence source and multi-device behavior; record it in `specs/100-notification/contracts/event-integration.md` (FR-011; blocks only deciding/implementing CHAT_MESSAGE push suppression in T048, not inbox persistence)
- [X] T009 NEEDS DECISION: obtain Feature 120 confirmation of the authoritative active-account predicate and SYSTEM fan-out behavior; record it in `specs/100-notification/contracts/event-integration.md` (FR-011; confirmed by Feature 120 admin-repairs.md Broadcast contract and handler regression tests; authorization T007 and delivery decision T006 were subsequently approved on 23/11)
- [X] T010 NEEDS DECISION: agree inbox page size/query shape and listener versus one-shot refresh strategy; record in `specs/100-notification/research.md` (FR-003; blocks final paged query in T030 only; defer index T055 until query shape is known)
- [X] T011 NEEDS DECISION: obtain Feature 011 agreement on FCM token registration, refresh, logout/account-switch, multi-token, and invalid-token lifecycle; record in `specs/100-notification/contracts/firestore-security.md` (blocks token lifecycle T051 and its Rules coverage T052; does not block inbox work)
- [X] T012 NEEDS DECISION: agree preference behavior for existing users/missing keys and whether DataStore is cache only; record in `specs/100-notification/data-model.md` (missing-key defaults approved 22/11; UID-scoped read-only DataStore fallback, server authority, no offline mutation queue and logout/deletion cleanup explicitly approved by user 23/11)
- [ ] T013 NEEDS DECISION: confirm available deep-link identifiers for each relevant type with producer features and record them in `specs/100-notification/contracts/event-integration.md` (FR-013; blocks complete routing in T035 for types whose identifiers are unconfirmed; implement confirmed routes independently)
- [X] T014 NEEDS DECISION: confirm push payload privacy and lock-screen content policy with project owners; record in `specs/100-notification/research.md` (Constitution VI; blocks final user-visible push content only, not inbox persistence)

**Checkpoint**: Resolve each NEEDS DECISION only before the tasks explicitly named as blocked by it. These decisions do not block unrelated stories or confirmed portions of a task; do not invent event protocols, schema, account predicates, or permission rules.

---

## Phase 3: User Story 1 – Nhận thông báo đặt sân và nhắc lịch (Priority: P1)

**Goal**: Persist and deliver booking outcome/new-booking notices and one eligible two-hour reminder.

**Independent Test**: Exercise confirmed/rejected/cancelled/pending/timeout booking events and a multi-slot confirmed booking; verify recipient, type, body data, one reminder at earliest start minus two hours, idempotency, and that a persisted inbox record remains available when FCM delivery fails (FR-010, FR-014; SC-001, SC-005).

### Tests for User Story 1

- [X] T015 [P] [US1] Add Edge Function tests for booking recipient/type mapping, user-initiated cancellation exclusion, timeout refund details, duplicate event replay, and FCM failure after inbox persistence (assert the inbox record remains available) in `supabase/functions/send-notification/` (FR-011, FR-014; SC-001, SC-005)
- [X] T016 [P] [US1] Add reminder scheduler tests for CONFIRMED eligibility, earliest item start, two-hour timing, and duplicate scheduler runs under the approved idempotency decision in `supabase/functions/booking-reminders/` (FR-010, FR-014)

### Implementation for User Story 1

- [X] T017 [US1] Implement shared notification event validation and server-side inbox writer using only agreed event identity and persisted fields in `supabase/functions/_shared/`
- [ ] T018 [US1] Implement booking event adapters for BOOKING_CONFIRMED, BOOKING_REJECTED, BOOKING_CANCELLED, and BOOKING_NEW with agreed producer contracts in `supabase/functions/send-notification/`
- [X] T019 [US1] Implement booking reminder query and scheduler for one BOOKING_REMINDER at two hours before earliest `items[].startAt` only while booking is CONFIRMED in `supabase/functions/booking-reminders/`
- [X] T020 [US1] Implement push eligibility and FCM HTTP v1 delivery for booking events, keeping the already-persisted inbox record available when FCM fails and applying only the approved retry contract in `supabase/functions/send-notification/` (FR-007; US1 scenario 4)

---

## Phase 4: User Story 2 – Nhận cập nhật buổi vãng lai và khuyến mãi (Priority: P1)

**Goal**: Deliver drop-in, waitlist, and eligible followed-venue promotion notifications to the agreed recipients.

**Independent Test**: Replay registration, waitlist promotion/closure, session change/cancel, and voucher events; verify each type's recipient eligibility and no self-action notice or duplicate.

### Tests for User Story 2

- [X] T021 [P] [US2] Add Edge Function tests for registered versus waitlisted recipients, waitlist closure, and self-action exclusions in `supabase/functions/send-notification/` (D3 confirmation exception approved 24/11; registration fake29, source emulator89 plus final12 and self-action5; inbox/job assertions cover registration replay/rejoin, silent waitlist/add-party/self-cancel, promotion and closure. See validation/continuation-2026-11-24.md.)
- [ ] T022 [P] [US2] Add Edge Function tests proving PROMOTION reaches only followers eligible under Feature 050 voucher rules in `supabase/functions/send-notification/`

### Implementation for User Story 2

- [X] T023 [US2] Implement DROPIN_REGISTERED, DROPIN_SPOT_OPENED, DROPIN_CHANGED, and DROPIN_WAITLIST_CLOSED adapters from the agreed Features 080/081/112 events (D3/W1 plus D4 in source handlers. Verified inline change/cancel, request replay, waitlist separation and oversized snapshot continuation.)
- [ ] T024 [US2] Implement PROMOTION recipient resolution using Feature 070 favorites as follows and Feature 050 eligibility rules in `supabase/functions/send-notification/`
- [ ] T025 [US2] Apply agreed inbox idempotency and push delivery policy to drop-in and promotion events in `supabase/functions/send-notification/`

---

## Phase 5: User Story 3 – Xem trung tâm thông báo và đánh dấu đã đọc (Priority: P1)

**Goal**: Let an authenticated user browse only their inbox, open linked content safely, and mark one or all items read.

**Independent Test**: Seed multiple users' inboxes in the Emulator; verify own-user newest-first paged list, read-one/read-all, no cross-user access, loading/content/empty/error/retry, and safe source-link fallback.

### Tests for User Story 3

- [X] T026 [P] [US3] Add Firebase Emulator Rules allow/deny tests for own inbox read, cross-user/unauthenticated denial, isRead-only update, create/delete denial in `firestore-tests/test/notification-security.test.ts`
- [X] T027 [P] [US3] Add unit tests for notification mapping, ordered page loading, read-one/read-all repository behavior, and source-link authorization fallback in `app/src/test/java/com/example/booking_app/feature/notification/`
- [X] T028 [US3] Add UI tests for inbox loading, content, empty, error/retry, and read states in both light and dark themes and on phone and `w600dp` layouts in `app/src/androidTest/java/com/example/booking_app/feature/notification/` (FR-009; Constitution V)

### Implementation for User Story 3

- [X] T029 [P] [US3] Create notification domain model, type mapping, and repository/use-case interfaces matching the 19 spec types and persisted fields in `app/src/main/java/com/example/booking_app/feature/notification/domain/`
- [X] T030 [US3] Implement paged, newest-first own-user Firestore inbox repository and mark-one/mark-all-isRead operations in `app/src/main/java/com/example/booking_app/feature/notification/data/`
- [X] T031 [US3] Implement Firestore Rules for own-user inbox reads and isRead-only updates, denying client create/delete and cross-user access in `firestore.rules`
- [X] T032 [US3] Implement inbox ViewModel StateFlow states and lifecycle-bound repository collection in `app/src/main/java/com/example/booking_app/feature/notification/ui/`
- [X] T033 [P] [US3] Implement Vietnamese XML inbox layout, notification row, empty/error/retry/loading resources, and accessible read indicators in `app/src/main/res/layout/` and `app/src/main/res/values/`
- [X] T034 [US3] Implement inbox Fragment, list adapter with ListAdapter/DiffUtil, read actions, and navigation entry in `app/src/main/java/com/example/booking_app/feature/notification/ui/` and `app/src/main/res/navigation/`
- [ ] T035 [US3] Implement notification tap routing that rechecks source existence/access and falls back to persisted title/body detail in `app/src/main/java/com/example/booking_app/feature/notification/ui/`

---

## Phase 6: User Story 4 – Bật/tắt loại thông báo (Priority: P2)

**Goal**: Persist per-type push preferences with agreed defaults while keeping BOOKING_NEW mandatory and inbox creation unaffected by toggles.

**Independent Test**: Change one type setting and generate events for it and another type; verify persisted settings, push eligibility only, BOOKING_NEW locked on, defaults, error/retry, and OS permission independence.

### Tests for User Story 4

- [X] T036 [P] [US4] Add unit tests for default and legacy preference mapping, per-type writes, BOOKING_NEW locked-on behavior, and failure state in `app/src/test/java/com/example/booking_app/feature/notification/`
- [X] T037 [US4] Add Emulator Rules tests proving users can update only their own notificationPrefs while protected profile fields and other users remain unchanged in `firestore-tests/test/notification-security.test.ts`
- [X] T038 [US4] Add UI tests for settings loading/content/error/retry and locked BOOKING_NEW toggle in both light and dark themes and on phone and `w600dp` layouts in `app/src/androidTest/java/com/example/booking_app/feature/notification/` (FR-009, FR-012; Constitution V)

### Implementation for User Story 4

- [X] T039 [US4] Implement preference model and repository operations on `users/{uid}.notificationPrefs` with agreed defaults/cache behavior in `app/src/main/java/com/example/booking_app/feature/notification/data/` (UID DataStore cache, sign-out epoch, server-only transactions, read-only fallback/retry; JVM, Rules/transport and 36/36 device matrix verified 23/11)
- [X] T040 [US4] Extend Firestore user Rules to permit only the current user's notificationPrefs updates without weakening existing protected-field rules in `firestore.rules`
- [X] T041 [P] [US4] Implement Vietnamese XML notification settings layout/resources with a non-editable BOOKING_NEW state in `app/src/main/res/layout/` and `app/src/main/res/values/`
- [X] T042 [US4] Implement settings ViewModel and Fragment with per-type updates, retry/error states, and OS permission status separated from stored preferences in `app/src/main/java/com/example/booking_app/feature/notification/ui/`
- [X] T043 [US4] Apply saved per-type push eligibility in server delivery while always retaining inbox creation and always allowing BOOKING_NEW push eligibility in `supabase/functions/send-notification/` (D1 worker checks preferences before target freeze and each send; all19 types × missing/off/on verified with fake FCM, preserved inbox, BOOKING_NEW forced and PROMOTION default off; worker19/19 and full fake65/65 pass 24/11.)

---

## Phase 7: User Story 5 – Nhận thông báo về hồ sơ, đánh giá, chat và hệ thống (Priority: P2)

**Goal**: Integrate remaining owner application, review, group activity, chat, and admin system events under the agreed contracts.

**Independent Test**: Emit each agreed source event; verify correct recipient/type and inbox idempotency, SYSTEM active-user fan-out, and chat push suppression only when presence contract says the recipient views that chat.

### Tests for User Story 5

- [ ] T044 [P] [US5] Add Edge Function tests for owner approval/rejection/revocation, review reply/new/reminder, and GROUP_ACTIVITY recipient/subtype mapping in `supabase/functions/send-notification/`
- [ ] T045 [P] [US5] Add Edge Function tests for CHAT_MESSAGE sender exclusion, inbox persistence, agreed foreground suppression, and SYSTEM active-account fan-out in `supabase/functions/send-notification/`

### Implementation for User Story 5

- [X] T046 [US5] Implement OWNER_APPROVED, OWNER_REJECTED, OWNER_REVOKED, REVIEW_REPLIED, REVIEW_NEW, and REVIEW_REMINDER adapters per Features 070/110/120 contracts (D2-W1/R1 plus O2 and RR1 in the existing source handlers. Verified replay after reapproval, concurrent completion, and journal cleanup on demo emulators.)
- [ ] T047 [US5] Implement GROUP_ACTIVITY event mapping with agreed `data.event` subtype and recipient rules in `supabase/functions/send-notification/`
- [ ] T048 [US5] Implement CHAT_MESSAGE inbox fan-out and push suppression using agreed Feature 091 presence contract in `supabase/functions/send-notification/`
- [X] T049 [US5] Implement SYSTEM server-authoritative fan-out to agreed active PLAYER and OWNER accounts per Feature 120 contract in `supabase/functions/send-notification/`

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Finish permission, token lifecycle, retention integration, and cross-story verification.

- [X] T050 [P] Implement runtime POST_NOTIFICATIONS permission request flow and denied-permission fallback without affecting inbox in `app/src/main/java/com/example/booking_app/feature/notification/` (3 JVM policy tests; two physical-device scenarios pass: denied preferences/inbox, recreation, OS-settings return/grant and fresh runtime grant; see validation/continuation-2026-11-22.md)
- [X] T051 Implement FCM token registration/refresh/logout/account-switch behavior under the agreed Feature 011 lifecycle in `app/src/main/java/com/example/booking_app/feature/notification/data/`
- [X] T052 Add notification token and preference Rules Emulator allow/deny coverage alongside inbox access cases in `firestore-tests/test/notification-security.test.ts`
- [X] T053 Implement notification channel setup and minimal agreed push payload handling in `app/src/main/java/com/example/booking_app/feature/notification/data/`
- [X] T054 Verify account deletion removes notification subcollection and related notification preferences/tokens per Feature 011 contract in `supabase/functions/delete-account/` (Auth/Firestore emulator: 405 inbox rows across batches, user profile/token/preference removal, unrelated user preserved; 21/21 tests pass)
- [X] T055 [P] Verify/add the agreed notification query index in `firestore.indexes.json` (no addition needed: parent collection createdAt DESC uses default single-field index, no notification exemption; 65 rows including tied timestamps page 30/30/5 on emulator; see validation/continuation-2026-11-22.md)
- [ ] T056 Validate the notification journeys and failure cases documented in `specs/100-notification/quickstart.md` against Android, Firebase Emulator, and Supabase local harnesses

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies; T001–T003 are discovery tasks only. Any concrete project/dependency setup is outside these tasks until shared dependency and harness choices are confirmed.
- **Foundational (Phase 2)**: Depends on discovery only. T004–T014 are decision-recording tasks, not implementation. Each one gates only its named dependent tasks; unrelated inbox, UI, own-user Rules, and confirmed event integrations can proceed.
- **User Stories (Phase 3+)**: Depend on applicable foundational decisions and any shared writer/rules foundation. UI/domain work in US3 may proceed independently of unresolved server transport decisions.
- **Polish (Phase 8)**: Depends on relevant story implementations and owner contracts.

### User Story Dependencies

- **US1 (P1)**: Booking event adapters T018 depend on T004; shared writer T017 and reminder deduplication T019 depend on T005; FCM retry behavior in T020 depends on T006. T007 does not block ordinary booking recipient/inbox work unless a specific Rules/Admin authorization decision is involved.
- **US2 (P1)**: Event adapters T023–T024 depend on T004 and their respective producer confirmations; shared writer/deduplication T017/T025 depend on T005; retry/recovery behavior T025 depends on T006. T007 applies only if the implementation uses the disputed Admin permission path.
- **US3 (P1)**: Final paged query T030 depends on T010; own-user inbox Rules/tests T026/T031 do not depend on T007's Admin permission decision. Domain/UI work may start after discovery T001–T003; complete deep-link routing T035 depends on T013 only for unconfirmed source identifiers.
- **US4 (P2)**: Preference fallback in T036/T039 depends on T012; token lifecycle T051 and token Rules coverage T052 depend on T011. Own-user notificationPrefs Rules work T037/T040 does not depend on T007's Admin notification-create decision.
- **US5 (P2)**: Event adapters depend on T004 and their source contracts. CHAT_MESSAGE inbox fan-out can proceed from confirmed Feature 091 events, but foreground push suppression in T048 depends on T008. SYSTEM recipient selection/fan-out T049 depends on T009 and authorization portion of T007. Retry behavior depends on T006; complete source routing depends on T013 only where identifiers are not confirmed.

### Within Each User Story

- Tests target behavior and contracts before or alongside implementation; no test task requires production service credentials.
- Domain/data contracts precede dependent repositories; repositories precede ViewModels/Fragments.
- Server adapters depend on event and idempotency decisions; push delivery follows inbox persistence.
- Rules changes integrate with existing shared user rules and must retain all current constraints.

### Parallel Opportunities

- T001–T003 can run in parallel as read-only discovery tasks; they do not install dependencies or create infrastructure.
- Independent decisions among T004–T014 can be resolved in parallel by their respective owners, then incorporated into only the affected implementation contracts.
- Within US1, T015 and T016 are parallel test tasks. Booking adapters depend on T004; reminder deduplication depends on T005. FCM failure assertion in T015 and delivery implementation T020 must preserve inbox persistence.
- Within US2, T021 and T022 are parallel; drop-in and promotion adapters can proceed separately after their producer contracts.
- US3 domain model, Rules tests, and UI resource work (T026, T029, T033) can proceed in parallel after relevant paths are confirmed. T026 and T037 edit the same Rules test file and must not run in parallel.
- US4 settings UI, preference tests, and Rules tests (T036–T038, T041) can proceed in parallel only across distinct files; T037 shares its file with T026 and must be sequenced with it.
- US5 event mapping tests for owner/review/group and chat/system (T044–T045) can proceed in parallel once contracts are available; foreground suppression assertions require T008 and active-account fan-out assertions require T009.

## Parallel Example: User Story 1

```text
Task: T015 booking event tests in supabase/functions/send-notification/
Task: T016 reminder eligibility tests in supabase/functions/booking-reminders/
```

## Parallel Example: User Story 3

```text
Task: T026 Firestore Rules Emulator cases in firestore-tests/test/notification-security.test.ts
Task: T029 notification domain model in app/src/main/java/com/example/booking_app/feature/notification/domain/
Task: T033 inbox XML resources in app/src/main/res/layout/ and app/src/main/res/values/
```

## Implementation Strategy

### MVP First (Scope Needs Confirmation)

The current task sequence treats US1 as a **backend booking-notification MVP**: it delivers booking events/reminders and validates inbox persistence on FCM failure. It does not include the user-facing inbox UI in US3, so it is not a complete mobile-app notification-center demo. The spec does not decide whether MVP means backend capability or an end-to-end user-visible increment. Confirm the intended MVP boundary with the team; if end-to-end is required, include the minimum US3 inbox/read path without changing business scope.

Backend booking MVP sequence:

1. Complete discovery T001–T003 and US1-relevant decisions T004–T006; T007 is required only if the chosen booking path touches the disputed Admin permission.
2. Implement shared event writer, booking notifications, and reminder behavior.
3. Validate US1 locally and confirm the inbox record remains available when FCM fails.

### Incremental Delivery

1. Complete P1 booking notices (US1).
2. Add drop-in and promotion events (US2), then inbox and read management (US3).
3. Add per-type preferences (US4).
4. Add owner/review/group/chat/system events (US5) after producer contracts are confirmed.
5. Complete cross-cutting permission, token, deletion, index, and quickstart verification.

## Known Clarifications / Conflicts

- **NEEDS DECISION (I1)**: DB README §7 lists Admin `C` on notifications, while spec 100 and DB §4.13 describe server-authoritative creation; Feature 120 requires SYSTEM through Feature 100's server path and says admin does not write inbox from client. T007 records this conflict. Do not modify schema, Rules, or Admin authorization until the CSDL, Feature 120, and notification owners agree. Directly affected: SYSTEM authorization/fan-out T049 and any Rules change specifically based on Admin notification creation; own-user inbox access is not blocked.
- **NEEDS DECISION (U1)**: Event transport/payload/event identity (T004) and atomic inbox/reminder idempotency (T005) are unspecified. They directly gate producer adapters, shared writer, and reminder deduplication named in Dependencies; they do not block independent Android inbox/settings work.
- **NEEDS DECISION (U1/U2)**: Retry/recovery and any delivery-state schema (T006), reminder deduplication state (T005), chat presence/multi-device behavior (T008, Feature 091), token lifecycle beyond confirmed 011 logout (T011), and push privacy (T014) remain open. T009 is confirmed by Feature 120; T012 cache policy is explicitly approved and T039 verified on 23/11; T055 needs no index addition. No new server field, collection, or status is approved by this task list.
- DB README §7/§4.13 and Feature 120 have an authorization conflict; tasks do not resolve it. Database notification fields and types remain authoritative; tasks add no fields, collections, or statuses. Any delivery-state/schema change requires explicit approval and an update by the database document owners.

## Android verification checkpoint (2026-11-22)

- Completed in this increment: T027, T028, T030, T032, T033, T034, T036, T038, T041, T042. Evidence: validation/results.json and validation/android-2026-11-22.md.
- T030 is verified for the authoritative Timestamp schema. Existing admin producer ISO-string createdAt writes/data must be normalized before T056 end-to-end inbox acceptance; this is not silently reinterpreted on Android.
- T035 remains partial: nine confirmed types route after SERVER/access checks; the remaining ten use saved title/body pending complete identifier/destination contracts (see implementation-review.md).
- T012/T039 remain partial for the undecided DataStore cache role. Firestore persistence, defaults, per-key/group writes, mandatory BOOKING_NEW and failure/retry are implemented/tested; no cache policy was invented.
- Delivery-state-proposal.md remains a schema proposal. No backend decision task is marked resolved by this Android work.
- Final verification: 252 JVM suites, 1,666 tests, 0 failures/errors, 2 pre-existing Windows skips; 21 notification tests pass. Device matrix: 8 tests x 4 variants = 32 pass. assembleDebug/lintDebug pass; lint has 0 errors, 856 warnings. Reviewer checklists unchanged.

## Independent continuation checkpoint (2026-11-22)

- Added completed tasks: T009 (confirmed Feature 120 predicate/fanout contract), T050 (runtime permission), T054 (actual account deletion on emulators), T055 (automatic index sufficient; no addition). Total **23/56**.
- T013/T035 remain partial: eleven canonical types route, plus the explicitly approved REVIEW_REPLY legacy alias; seven source-specific contracts remain missing and SYSTEM deliberately opens saved detail. REVIEW_REMINDER and REVIEW_REPLIED were added with SERVER/source access checks.
- Three admin producers now write native Timestamp for notification.createdAt. Historical mixed-type data remains unmigrated. Offline planner/proposal is reviewable; no hosted data operation or deployment occurred.
- Tests: 1,671 JVM tests, no failures/errors, two existing Windows skips; 26 notification tests. Delete-account emulator 21/21; Rules/profile/query emulator 28/28 (includes legacy sort/repair reproduction); producer/admin/offline planner 28/28; review reply emulator 5/5. Phone/light UI regression 8/8 and targeted permission 2/2. Detailed final commands, screenshot hashes and lint results are recorded in continuation validation.
- Backend schema, per-token receipts, identity/transaction handoffs, token/presence/cache/privacy policies and Admin permission conflict remain open as specified in implementation-review.md and delivery-state-proposal.md. No new storage or event protocol is approved by this checkpoint.

## Cache/group continuation checkpoint (2026-11-23)

- Added completed tasks: T012 and T039; total **25/56**. Cache policy was explicitly approved during this turn and is recorded in data-model.md/research.md. Core session cleanup clears cache even when account deletion bypasses authenticated hooks; stale pre-logout writes cannot repopulate it under the old epoch.
- T013/T035 remain partial: GROUP_ACTIVITY routes under the user's routing-only approval, bringing source-routed canonical types to twelve. BOOKING_NEW, PROMOTION, REVIEW_NEW and OWNER_* still lack complete target contracts; SYSTEM deliberately uses saved details. Legacy REVIEW_REPLY compatibility is retained.
- Verification: 1,682 JVM tests in 255 suites, zero failures/errors, two existing Windows-only skips; 37 notification tests. Firestore notification/profile/query/group suites 53/53, offline planner 6/6. assembleDebug/lintDebug pass, lint zero errors and 856 existing warnings.
- New cache warning/read-only UI verified on phone 393dp and simulated w600dp 720dp light/dark: 9/9 each, 36/36 total, 24 PNGs. Original PNGs and prior approved UI exceptions are unchanged. Evidence: validation/continuation-2026-11-23.md and its results/verification files.
- T011/T051/T052 remain partial: 011 owns logout arrayRemove/deleteToken hook, while registration/refresh/OS eligibility/switch guarantees and detailed token Rules are not agreed. T004–T008/T014 and delivery schema remain unapproved. See decision-review-2026-11-23.md and the amended proposal; no worker, device schema, source permission, hosted migration or deployment was introduced.

## Backend/FCM continuation — 23/11/2026

- **36/56 tasks verified**. User approved D1/D2-W1, T1/P1 and C1/P1/S1 after reviewing backend-decision-package.md. T006/T007/T008/T011/T014 are resolved in CSDL and owning contracts; proposal history is superseded only within those IDs/scopes.
- T017 shared transaction helper, T020 generic booking push eligibility/HTTP-v1 worker, T049 authenticated SYSTEM fanout, T051 lifecycle coordinator, T052 Rules and T053 channel/minimal payload handling have new implementation and local evidence. Actual files reuse source handlers plus _shared/notification-{enqueue,worker,fcm}.ts and notification-delivery/, not an invented send-notification HTTP envelope. T049 follows T009's all-ACTIVE/non-deleting predicate, not a new role filter.
- T004/T005 remain partial for identities/reminders outside D2-W1. Booking terminal, waitlist promotion/closure and application approval/rejection adapters are implemented; T018/T023/T025/T044/T046 remain unchecked where they include additional unconfirmed types. T045/T048 remain unchecked: presence/suppression and SYSTEM tests exist, but durable CHAT_MESSAGE enqueue/fanout does not.
- T013/T035 remain partial: twelve canonical source routes plus REVIEW_REPLY read compatibility; BOOKING_NEW, PROMOTION, REVIEW_NEW and OWNER_* still lack confirmed destination/identifier/access contracts. SYSTEM uses saved detail. Push taps now load the authenticated user's inbox ID before existing routing/fallback.
- T054 evidence extended with 205 receipt children, parent job cleanup and unrelated-account preservation; no worker may recreate a deleted parent. T055 keeps the inbox default single-field index conclusion; three delivery state/time indexes were added for actual worker due/expired/retention queries, with no production deployment claim.
- Validation: 64 targeted JVM tests, 64 backend fake tests, 48 Rules tests, initial worker/deletion + 42 source emulator tests, final 7 affected integration tests, device14 + push-tap1. Final assembleDebug/lintDebug pass (0 errors, 858 warnings). Supabase local/cron SQL remains unrun because Docker daemon is unavailable; T056 remains open. No full UI matrix rerun because layout/theme did not change. See validation/backend-2026-11-23.md and its evidence folder. Coordination requests are presented directly in the conversation.

## Booking continuation, 24/11/2026

User approved B1 contract with the payment source deferred and B2 once-per-booking reminder semantics. T015/T016/T019 verified: 39/56 total. See booking-decision-package-2026-11-24.md and validation/booking-2026-11-24.md. T018 remains partial until the actual HOLD-to-PENDING transaction is supplied; a helper harness does not replace payment integration. T004/T005 remain open outside B1/B2, T013/T035 unchanged. SQL migration is prepared but not applied; Supabase local remains blocked by Docker. No UI matrix rerun, deploy or real FCM. Shared Firestore cursor support preserves raw Timestamp/reference precision; status/startAt/__name__ index matches the actual query.

## D3/R1/H1 and device continuation, 24/11/2026

- **41/56 verified**, newly closing T021 and T043. D3/R1/H1 explicitly approved by user in continuation-decisions-2026-11-24.md; CSDL/source contracts updated before implementation.
- T023/T025 now include transactional DROPIN_REGISTERED, replay/rejoin and silent self actions; DROPIN_CHANGED and promotion still prevent full closure. T044/T046 now include transactional review create/first reply and server-only generation/recreate/legacy behavior; revocation, reminder and group sources remain missing as applicable. T045/T048 now include DIRECT atomic message/inbox/job and sender exclusion; GROUP still has an unconfirmed durable fanout contract and stays open.
- T004/T005 stay partial for remaining source identities/fanout. B1 payment source remains absent in T018. T013/T035 have no newly confirmed destination: twelve canonical source routes plus legacy REVIEW_REPLY; BOOKING_NEW/PROMOTION/REVIEW_NEW/OWNER_* retain saved detail until contracts are confirmed.
- Physical UI testing found and fixed transient old rows on logout (async RecyclerView removal). Added 30/30/5 paging, read-all over an unloaded page and account switch checks, plus real handler local injection/OS permission cases. Final phone/w600dp light/dark passes48/48; channel/handler/permission9/9; 24 UI and four permission PNGs compared, density/permission flags restored. Initial failures are retained separately.
- Validation: registration fake29, regression fake65, affected source emulator89, final R1/waitlist12, Rules25 and self-action5 pass (overlapping executions). Focused JVM64/12 suites, assembleDebug, final isolated app/instrumentation build and lintDebug pass; lint0 errors/858 existing warnings. See validation/continuation-2026-11-24.md and its results/log/hash folder. Docker remains unavailable; Supabase local/cron SQL and full-catalog T056 remain unverified. No real FCM, hosted migration, deploy, commit or push.

## O2/RR1 and real repository on device, 24/11/2026

- **43/56 verified**. T023 closed after implementing D4. T046 closed after implementing O2 revoke and RR1 completion. T044 is partial until GROUP_ACTIVITY tests cover every subtype.
- D4 source journal and snapshot fanout verified. F1 group chat and GROUP_ACTIVITY wiring, including paged recipients for oversized groups, remain open in T045/T047/T048. P2 approved, pending Feature 050 per-user usage counter contract. T022/T024/T025 remain open.
- B1 remains a prepared helper. Checkout still lacks the payment transaction that changes HOLD to PENDING. T013/T035 retain saved detail for source destinations without a confirmed contract.
- T056 has new Auth/Firestore Emulator transport evidence on vivo via USB reverse and real SDK repositories. Supabase local/cron remains blocked by Docker. See validation/source-device-2026-11-24.md.
