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

Producers use the internal transaction helper or a durable outbox processed by notification-delivery. The event and replay contracts are in [event-integration.md](contracts/event-integration.md).

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

Run Rules suites notification-delivery-security, notification-security, users-profile and chats against Firestore Emulator. With Supabase local and Docker running, use pg_cron/pg_net with a demo Vault target pointed at the local `scripts/notification/serve-local.ts` harness. That harness invokes the real handlers with a fake FCM sender. See the [26/11 validation](validation/f1-2026-11-26/README.md).

## Scenarios and expected outcomes

1. **Inbox list**: Seed mixed types and read states with different server timestamps. Verify newest-first ordering, cursor continuation without duplicates, loading/empty/error states, and no query outside current uid.
2. **Read operations**: Mark one item and mark all as read. Verify only `isRead` changes and another user's data remains unchanged.
3. **Preferences**: New profile defaults all supported types on except PROMOTION; BOOKING_NEW control is absent/locked. Disable PROMOTION and emit eligible source event: inbox row exists but fake FCM is not called. Re-enable and permitted device: FCM is called.
4. **OS permission**: Deny runtime notifications. Inbox continues to populate and read; no crash. Grant permission and verify push eligibility separately from persisted preference.
5. **Idempotency**: Deliver the same confirmed event more than once to the same recipient/type. Verify one inbox row and one logical delivery job.
6. **Booking reminder**: Confirmed multi-slot booking emits one reminder at 2h before earliest start. Non-CONFIRMED booking emits none. Re-run the scheduler and verify no duplicate.
7. **Recipients**: Validate owner/system booking cancellation rule, registered-vs-waitlisted drop-in, eligible followers, owner review notification, chat non-sender recipients, and active account fan-out against source data fixtures.
8. **Chat foreground**: Recipient viewing the same chat receives an inbox row with push suppressed. Recipient outside the chat can receive push if preference and OS permit.
9. **Navigation fallback**: Existing accessible booking/session opens source detail. Deleted/forbidden source opens saved notification detail. Recheck permission at navigation time.
10. **Failure/privacy**: Simulate network failure for list/read/settings and FCM transient/permanent failure. Verify visible retry/error, inbox persistence, bounded retry per finalized policy, invalid token handling, and no protected data in push payload.
11. **Account deletion**: Run delete-account locally and verify user notification subcollection, `notificationPrefs`, and token data are removed with account while unrelated anonymized records follow spec 011.

## Required gates

Để chạy repository thật trên điện thoại USB, build APK và APK test với `-PadminLocalAppId=true` và `-PadminTestRunner=com.example.booking_app.feature.notification.NotificationTestRunner`. Từ root, chạy `npx.cmd --yes firebase-tools@15 emulators:exec --project demo-admin-repair --only auth,firestore 'powershell -ExecutionPolicy Bypass -File scripts/notification/validate-device-emulator.ps1'`. Harness tạo hai tài khoản demo có claim PLAYER, kiểm tra inbox/preferences/route bằng SDK và Rules, rồi gỡ các USB reverse port mà nó đã mở.

Để chạy cùng các producer, khởi động `scripts/notification/serve-local.ts` với Deno, các biến emulator ở trên và `NOTIFICATION_LOCAL_CRON_SECRET` của phiên demo. Truyền cùng giá trị vào `-ProducerSecret` của script thiết bị. Dùng `-ProducerPhase group` hoặc `-ProducerPhase payment` để kiểm thử riêng. Nếu emulator dùng cổng host khác 8080/9099, truyền `-FirestorePort` và `-AuthPort`.

- Firestore Rules tests on Emulator for own/other uid, isRead-only mutation, preference/token scope.
- Server tests for event validation, idempotency, fan-out, preference routing, SYSTEM recipients, reminder eligibility, and FCM retry behavior.
- ViewModel/repository tests for pagination, states, preferences and retry; Espresso smoke for inbox/settings.
- No production Firebase project, real push token, or personal data in test runs.
