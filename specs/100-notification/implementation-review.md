# Implementation review — 2026-11-22

## Verified discovery (T001–T003)

- Android: `gradle/libs.versions.toml` and `app/build.gradle.kts` already provide Hilt/KSP, Lifecycle, Coroutines/tasks, Firebase Auth/Firestore/Messaging, DataStore, JUnit, MockK, Turbine and coroutine tests. No new dependency is needed for notification work. The starter-only descriptions in plan/research are historical.
- Rules: `firestore-tests/package.json` runs firebase-tools 15, demo-smashnow and Vitest. `test/setup.ts` loads root Rules; tests are sequential. Firebase CLI requires Java 21+. Existing user update Rules already permit notificationPrefs, with server updatedAt and protected-field constraints.
- Server: TypeScript/Deno handlers and injected dependencies already exist in `supabase/functions/`; local/emulator tests and `_shared/firestore.ts` can be reused. No send-notification or booking-reminders directory currently exists. Do not introduce another framework.
- Ignore audit: node_modules, build, Gradle and secrets are covered; added `.kotlin/` for compiler state produced by verification. No Docker/ESLint/Prettier/Terraform/Helm or npm publishing setup is introduced.
- Explicit feature prerequisite override resolves `specs/100-notification`; default persisted context resolves Feature 112. No context file was changed. No AGENTS.md or `.specify/extensions.yml` was found.
- Requirements checklist: 24 checked, 0 unchecked; markers left untouched.

## Existing implementation and decision evidence

- T004/T013: `specs/112-owner-operations/handoffs/notifications.md` confirms only DROPIN_REGISTERED envelope `{type,userId,data:{sessionId}}`. The same document leaves retry/deduplication open. `_shared/chats.ts` instead sends `recipientUid`. Do not infer a universal envelope.
- T005/T006: `admin-broadcast-notification/handler.ts` and `specs/120-admin/contracts/admin-repairs.md` define SYSTEM requestId, deterministic sys_ ID and resume missing recipients. This is evidence for SYSTEM only, not a shared event/reminder key or push retry policy.
- T007: current admin contract requires a server path, and current Rules deny client notification creation. This review does not resolve the conflicting DB permissions table on behalf of its owners.
- T008: Feature 091 research R7 defines participant viewingUntil (60-second lease, 45-second renewal). It also excludes muted/viewing recipients before calling notification delivery, conflicting with Feature 100's always-persist-inbox requirement. Cross-device presence semantics remain unconfirmed.
- T009: admin-repairs explicitly confirms ACTIVE and not deleting as broadcast recipient predicate; implementation scans all users. Current code includes active admins too; Feature 100 requires PLAYER and OWNER. Do not silently narrow the existing broadcast without integration verification.
- T010: no notification-specific approved page strategy found.
- T011: `core/session/FcmSignOutHook.kt` removes current device token then deletes local token, tolerating failures; Feature 011 research R5 requires UNREGISTERED cleanup. Registration/refresh/account-switch contract still incomplete.
- T012: on-signup currently persists an empty notificationPrefs map. New-account defaults are specified but missing-key semantics for existing users remain unconfirmed. Domain defaults added here are explicitly new-account defaults only; no legacy fallback is applied.
- T013: bookingId/sessionId are documented; chat source supplies chatId/messageId. Complete authorization-checked routing remains unimplemented.
- T014: no final lock-screen content policy found.
- T054: `_shared/account-deletion.ts` clears tokens and deletes notifications with other user subcollections. End-to-end deletion verification remains pending.

## UI decisions and evidence

Both original PNGs were directly opened, together with ui/README.md. User explicitly selected **keep settings as pictured** when asked about grouped controls versus per-type rows. Original UI documentation has not been changed.

No implemented-screen screenshot comparison has been performed. UI layouts, navigation, state rendering and instrumentation are still outstanding; opening the references is not UI acceptance evidence.

## Proposals awaiting approval (not implementation contracts)

1. T010: 30 items/page, parent-scoped createdAt descending with document snapshot cursor; one-shot load, explicit refresh, no listener. Mark-all must traverse the whole inbox, not only loaded pages. Do not add a composite index unless this query needs one.
2. T012: missing keys use new-account defaults (all on except PROMOTION); BOOKING_NEW forced on. Firestore is authoritative; do not add a second writable preference source. DataStore cache role still needs confirmation against constitution.
3. Group controls: checked when all members on, unchecked when all off, intermediate otherwise. A tap on intermediate enables all members; a checked tap disables all. Persist individual type keys, preserving independent types. BOOKING_NEW remains locked.
4. T004/T005: extend each producer contract with stable source event identity; hash event identity + recipient + type into the existing notification document ID; atomic create prevents duplicate inbox entries without adding persisted fields. Existing transaction writers must be integrated individually to avoid a second inbox record. Keep the confirmed DROPIN_REGISTERED envelope compatible.
5. T006: inbox commit precedes push; bounded synchronous transient retries and invalid-token cleanup. Durable recovery after process death is not achievable with this proposal alone: choose and approve either a delivery-state schema/job or an explicitly accepted best-effort delivery limitation before claiming retry/recovery complete.
6. Chat source must include muted/viewing recipients for inbox creation and pass suppression evaluation to the delivery layer. Confirm multi-device lease behavior with Feature 091 before altering its contract.

Unresolved decision tasks remain unchecked. Partial evidence does not satisfy a whole task.

## Verification

- NotificationContractTest: 3 tests, 0 failures/errors (JUnit XML in app/build/test-results/testDebugUnitTest).
- Firebase Emulator with Android Studio JBR 25: `vitest run test/notification-security.test.ts test/users-profile.test.ts` — 2 files, 26 tests passed, exit 0. Initial sandbox CLI run failed on registry permissions; elevated default-Java attempt failed because Firebase CLI requires Java 21+. Retried successfully with bundled JBR.
- Gradle `testDebugUnitTest assembleDebug lintDebug`: debug APK assembled; lint report has 0 errors, 849 warnings. Final aggregate command result must be checked separately.
- T040 required no new user Rules: own notificationPrefs writes already pass existing profile restrictions; T037 tests verify scope/protected fields.
- Connected UI tests, phone/w600dp light/dark screenshots, Supabase local notification delivery/reminder tests and full quickstart journeys remain unrun because corresponding notification implementations are absent or decision-blocked. Reference image inspection is complete; visual acceptance is not.

## Subsequent approvals and verification correction

User explicitly approved proposals 1–3. T010 resolved; T012 fallback resolved but DataStore cache role outstanding. Data repositories added; repository-specific tests still pending. User selected delivery-state/job design; delivery-state-proposal.md is not yet schema approval.

Correction: lint report (0 errors, 849 warnings) was dated 2026-11-21, not fresh verification. Aggregate Gradle run still pending. Focused rebuild started after repository additions.

Final verification for this increment: aggregate Gradle testDebugUnitTest assembleDebug lintDebug exited 0 (1,648 tests, 0 failures/errors, 2 skipped; fresh lint 0 errors/849 warnings). After adding data repositories and approved preference mapping, focused NotificationContractTest + assembleDebug also exited 0 (5 tests). Repository behavior tests and final lint after repository additions are still outstanding. No UI screenshot/connected test or backend delivery test is claimed. Extension hooks absent on final recheck.
