# Quickstart Validation Guide: Feature 120

This is a validation guide for implementation. It does not add implementation code or new endpoints. The current repository does not yet contain the feature packages, Edge Functions, collection Rules, or complete test harness; run these scenarios after those prerequisites exist. Use Firebase Emulator and Supabase local only, never a real project.

## Prerequisites

- Android SDK/toolchain per project Gradle configuration; Firebase Emulator Suite configured for Auth/Firestore.
- `firestore-tests` dependencies installed and Vitest configuration available.
- Supabase CLI/local stack for Edge Function and Storage RLS checks.
- Seed fixture accounts with PLAYER, OWNER and ADMIN custom claims, application/report/user/venue fixtures and private `owner-docs` objects. Use fake data only.
- Schema/authorization decisions listed in [research.md](research.md) resolved before testing operations whose behavior is currently unspecified.

## Validation scenarios

| Scenario | Setup/action | Expected result |
|---|---|---|
| Admin authorization | Invoke each admin read/mutation as ADMIN, PLAYER, OWNER, unauthenticated and revoked/invalid admin | Only current valid ADMIN can access; all other direct requests are denied, independent of app UI. |
| Application review | Read PENDING application and evidence; approve then separately reject a different PENDING fixture with trimmed non-empty reason | Authorized Admin sees evidence through a five-minute signed URL. Approve yields application/user/venue/claim outcomes from spec; reject stores reason and `REJECTED`/PLAYER state. Applicant sees current status/reason through spec 110. |
| Decision race/retry | Two admin requests decide same PENDING application, or request times out then reloads | At most one decision effect. Stale request is rejected; reload shows authoritative state; retry creates no duplicate venue or notification. |
| Evidence privacy | Fetch object as applicant, Admin, unrelated user; retry after signed URL expiry | Applicant and Admin allowed only for correct application; unrelated user denied; expired URL cannot be reused. |
| Report moderation | Resolve compatible target/action, dismiss another OPEN report, try mismatched action/stale report/missing target | Report metadata matches actual effect; dismissal is `DISMISSED`/`NONE`; incompatible or stale operation has no side effect. |
| Account/venue controls | Lock/unlock account; lock venue and unlock to ACTIVE or HIDDEN; attempt same as owner/player | Admin succeeds; non-admin denied. Spec 010 observes account lock; owner cannot unlock a LOCKED venue. Revoking owner preserves APPROVED application and venue status, records reason and emits OWNER_REVOKED. |
| Voucher | Create/update/disable using spec 050 fields; attempt duplicate code against venue voucher; attempt client write to usedCount/redemptions | Valid system voucher accepted; global duplicate rejected; protected usage fields remain server-only. |
| Stats/catalog/broadcast | Read existing stats; empty stats; category mutation and SYSTEM fan-out only after unresolved semantics are agreed | Existing metrics display with provenance/time; no fabricated metrics. Category and recipient outcomes follow approved schema/semantics. |
| Error/UI states | Simulate permission denial, offline read, stale cache, expired signed URL, validation error, timeout | Clear error/retry; stale data marked and cannot drive mutation; rejection reason retained; no optimistic success or duplicate effects. |

## Commands

Use repository wrappers/configuration when the backend harness is completed. The following are expected validation entry points from current project structure; exact scripts/config flags may change when test harness is agreed:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
Set-Location firestore-tests
npm test -- --run
Set-Location ..
supabase start
supabase functions serve
```

Run only the relevant local test suites with Emulator/local services running. No test commands were executed as part of this Plan step.

## References

- [Data model](data-model.md) — existing fields, states, mappings and gaps.
- [Admin boundaries](contracts/admin-boundaries.md) — authorization and integration limits.
- [Research](research.md) — decisions, evidence and blockers.
- [Feature spec](spec.md) — acceptance scenarios and success criteria.

## Kết quả nghiệm thu thực tế — Phase 1, 2, 3 (US1 - MVP P1)

- **Unit Tests**:
  - Lệnh: `./gradlew :app:testDebugUnitTest --tests "com.example.booking_app.feature.admin.*"`
  - Kết quả: `BUILD SUCCESSFUL in 1m 24s`. 100% tests PASS (`AdminApplicationsViewModelTest`, `AdminApplicationValidationTest`).
- **Connected UI Tests trên thiết bị vật lý**:
  - Thiết bị: `10ADBY2GH30038C` (Vivo V2247, Android 15).
  - Lệnh: `./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.example.booking_app.feature.admin.AdminApplicationScreensUiTest"`
  - Kết quả: `BUILD SUCCESSFUL in 1m 5s`. 100% PASS (1/1 test class, 0 failures, 0 skipped), xác thực toàn bộ các màn hình Admin trên phone (360dp) và tablet (600dp) ở cả 2 chế độ Day/Night.
- **Backend Edge Function & Rules Tests**:
  - Edge Function: `supabase/functions/admin-review-application/` (`index.ts`, `handler.ts`, `handler.test.ts`).
  - Rules Tests: `firestore-tests/test/admin-authorization.test.ts`, `firestore-tests/test/owner-application-review.test.ts`, `firestore-tests/test/owner-docs-access.test.ts`.

## Kết quả nghiệm thu thực tế — Phase 4 (US2 - Reports Moderation P1)

- **Unit Tests**:
  - Lệnh: `./gradlew :app:testDebugUnitTest --tests "com.example.booking_app.feature.admin.*"`
  - Kết quả: `BUILD SUCCESSFUL in 56s`. 100% tests PASS (`AdminReportsViewModelTest`, `AdminReportValidationTest`, `AdminApplicationsViewModelTest`, `AdminApplicationValidationTest`).
- **Connected UI Tests trên thiết bị vật lý**:
  - Thiết bị: `10ADBY2GH30038C` (Vivo V2247, Android 15).
  - Lệnh: `./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.example.booking_app.feature.admin.AdminReportScreensUiTest"`
  - Kết quả: `BUILD SUCCESSFUL in 53s`. 100% PASS (1/1 test class, 0 failures, 0 skipped), xác thực màn hình hàng đợi báo cáo (`fragment_admin_report_queue`), chi tiết báo cáo (`fragment_admin_report_detail`) và card item trên phone (360dp) và tablet (600dp) ở cả 2 chế độ Day/Night.
- **Backend Edge Function & Rules Tests**:
  - Edge Function: `supabase/functions/admin-moderate-report/` (`index.ts`, `handler.ts`, `handler.test.ts`).
  - Rules Tests: `firestore-tests/test/report-moderation.test.ts`.

## Kết quả nghiệm thu thực tế — Phase 5 (US3 - Quản lý tài khoản và venue vi phạm P2)

- **Unit Tests**:
  - Lệnh: `./gradlew :app:testDebugUnitTest --tests "com.example.booking_app.feature.admin.*"`
  - Kết quả: `BUILD SUCCESSFUL in 50s`. 100% tests PASS (`AdminUserVenueViewModelTest`, `OwnerRevocationValidationTest`, `AdminReportsViewModelTest`, `AdminReportValidationTest`, `AdminApplicationsViewModelTest`, `AdminApplicationValidationTest`).
- **Connected UI Tests trên thiết bị vật lý**:
  - Thiết bị: `10ADBY2GH30038C` (Vivo V2247, Android 15).
  - Lệnh: `./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.example.booking_app.feature.admin.AdminUserVenueScreensUiTest"`
  - Kết quả: `BUILD SUCCESSFUL in 58s`. 100% PASS (1/1 test class, 0 failures, 0 skipped), xác thực màn hình quản lý user (`fragment_admin_user_management`), dialog thu hồi chủ sân (`dialog_admin_revoke_owner`) và màn hình quản lý cơ sở (`fragment_admin_venue_management`) trên phone (360dp) và tablet (600dp) ở cả 2 chế độ Day/Night.
- **Backend Edge Functions & Rules Tests**:
  - Edge Functions:
    * `supabase/functions/admin-manage-user/` (`index.ts`, `handler.ts`, `handler.test.ts`).
    * `supabase/functions/admin-revoke-owner/` (`index.ts`, `handler.ts`, `handler.test.ts`).
    * `supabase/functions/admin-manage-venue/` (`index.ts`, `handler.ts`, `handler.test.ts`).
  - Rules Tests: `firestore-tests/test/user-venue-management.test.ts` (kiểm tra allow/deny tra cứu tài khoản, cấm client tự ý sửa trường quản trị, owner không được tự mở khóa venue LOCKED và không được tự đặt venue thành LOCKED).

## Kết quả nghiệm thu thực tế — Phase 6 (US4 - Theo dõi tình hình và công cụ quản trị P3)

- **Unit Tests**:
  - Lệnh: `./gradlew :app:testDebugUnitTest --tests "com.example.booking_app.feature.admin.*"`
  - Kết quả: `BUILD SUCCESSFUL in 40s`. 100% tests PASS (`AdminToolsViewModelTest`, `SystemVoucherValidationTest`, `AdminUserVenueViewModelTest`, `OwnerRevocationValidationTest`, `AdminReportsViewModelTest`, `AdminReportValidationTest`, `AdminApplicationsViewModelTest`, `AdminApplicationValidationTest`).
- **Connected UI Tests trên thiết bị vật lý**:
  - Thiết bị: `10ADBY2GH30038C` (Vivo V2247, Android 15).
  - Lệnh: `./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.example.booking_app.feature.admin.AdminToolsScreensUiTest"`
  - Kết quả: `BUILD SUCCESSFUL in 1m 5s`. 100% PASS (1/1 test class, 0 failures, 0 skipped), xác thực màn hình dashboard công cụ (`fragment_admin_tools_dashboard`), danh sách voucher hệ thống (`fragment_admin_system_vouchers`), item voucher (`item_admin_system_voucher`) và form broadcast notification (`fragment_admin_broadcast`) trên phone (360dp) và tablet (600dp) ở cả 2 chế độ Day/Night.
- **Backend Edge Functions & Rules Tests**:
  - Edge Functions:
    * `supabase/functions/admin-broadcast-notification/` (`index.ts`, `handler.ts`, `handler.test.ts`).
    * `supabase/functions/admin-manage-voucher/` (`index.ts`, `handler.ts`, `handler.test.ts`).
  - Rules Tests: `firestore-tests/test/admin-tools.test.ts` (kiểm thử đọc systemStats chỉ dành cho Admin, cấm client ghi usedCount của voucher, cấm non-admin sửa districts và amenities).

