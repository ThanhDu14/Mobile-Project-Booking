# Quickstart: Feature 100 validation scenarios

This guide describes local validation after implementation. It does not add tests or implementation code. Use emulators/fakes only; constitution forbids tests against real Firebase/Supabase projects.

## Prerequisites

- Android SDK and JDK compatible with repo Gradle/AGP; local Firebase config kept out of version control.
- Firebase Emulator Suite configured by `firebase.json`; Node dependencies installed under `firestore-tests/`.
- Supabase CLI + Deno for Edge Function tests/local runtime, when functions and local config are implemented.
- Test accounts in emulator: PLAYER, OWNER and ADMIN; isolated fake FCM sender/tokens. Do not use real personal data or send real pushes.

## Local commands

From repository root, use the repository wrappers/configuration that exist at implementation time:

```powershell
./gradlew.bat testDebugUnitTest
./gradlew.bat connectedDebugAndroidTest
Set-Location firestore-tests
npm test -- --run
Set-Location ..
supabase start
# Supabase runtime/cron execution additionally needs Docker; see the backend harness below.
```

The implemented W1 transport is an internal transaction helper plus notification-delivery worker, not a new generic send-notification event envelope. Legacy callback transports outside W1 remain pending. Do not serve an invented HTTP envelope to make these scenarios appear complete.

### Approved backend harness (23/11/2026)

Deno must be on PATH, or pass -DenoExecutable to scripts/notification/validate-backend.ps1. Fake suite uses --allow-env only, without network permission. Emulator suite requires explicit demo-* project/localhost hosts and injects fake FCM; the HTTP entrypoint refuses an emulator invocation without a fake sender.

```powershell
./scripts/notification/validate-backend.ps1 -Suite Fake
$env:FIREBASE_PROJECT_ID = 'demo-f100-backend'
$env:FIRESTORE_EMULATOR_HOST = '127.0.0.1:8080'
$env:FIREBASE_AUTH_EMULATOR_HOST = '127.0.0.1:9099'
npx.cmd --yes firebase-tools@15 emulators:exec --project demo-f100-backend --only auth,firestore 'powershell -ExecutionPolicy Bypass -File scripts/notification/validate-backend.ps1 -Suite Emulator'
# -Suite Affected runs worker/SYSTEM plus presence cleanup when only these changed.
# -Suite Continuation runs D3 registration, R1 review, H1 direct chat and affected waitlist/source integration.
# Run review Rules too: vitest run test/reviews.test.ts test/notification-security.test.ts test/notification-delivery-security.test.ts
```

Run Rules suites notification-delivery-security, notification-security, users-profile and chats in firestore-tests against the Firestore emulator. Validation/backend-2026-11-23.md records executed commands, outputs and limitations. Cron SQL is reviewable but was not applied; a local Supabase runtime needs Docker and a fake-sender composition, not removal of the no-real-FCM guard.

## Scenarios and expected outcomes

1. **Inbox list**: Seed mixed types and read states with different server timestamps. Verify newest-first ordering, cursor continuation without duplicates, loading/empty/error states, and no query outside current uid.
2. **Read operations**: Mark one item and mark all as read. Verify only `isRead` changes and another user's data remains unchanged.
3. **Preferences**: New profile defaults all supported types on except PROMOTION; BOOKING_NEW control is absent/locked. Disable PROMOTION and emit eligible source event: inbox row exists but fake FCM is not called. Re-enable and permitted device: FCM is called.
4. **OS permission**: Deny runtime notifications. Inbox continues to populate and read; no crash. Grant permission and verify push eligibility separately from persisted preference.
5. **Idempotency**: Deliver same confirmed event more than once to same recipient/type. Exactly one inbox row and at most one logical push after retry handling. This case cannot be made deterministic until event key contract is agreed.
6. **Booking reminder**: Confirmed multi-slot booking emits one reminder at 2h before earliest start. Non-CONFIRMED booking emits none. Re-run scheduler and verify no duplicate; test awaits finalized reminder idempotency key.
7. **Recipients**: Validate owner/system booking cancellation rule, registered-vs-waitlisted drop-in, eligible followers, owner review notification, chat non-sender recipients, and active account fan-out against source data fixtures.
8. **Chat foreground**: Recipient viewing the same chat receives inbox row with no push; recipient outside that chat can receive push if preference/OS allow. Requires agreed presence contract.
9. **Navigation fallback**: Existing accessible booking/session opens source detail. Deleted/forbidden source opens saved notification detail. Recheck permission at navigation time.
10. **Failure/privacy**: Simulate network failure for list/read/settings and FCM transient/permanent failure. Verify visible retry/error, inbox persistence, bounded retry per finalized policy, invalid token handling, and no protected data in push payload.
11. **Account deletion**: Run delete-account locally and verify user notification subcollection, `notificationPrefs`, and token data are removed with account while unrelated anonymized records follow spec 011.

## Required gates

- Firestore Rules tests on Emulator for own/other uid, isRead-only mutation, preference/token scope.
- Server tests for event validation, idempotency, fan-out, preference routing, SYSTEM recipients, reminder eligibility, and FCM retry behavior.
- ViewModel/repository tests for pagination, states, preferences and retry; Espresso smoke for inbox/settings.
- No production Firebase project, real push token, or personal data in test runs.
