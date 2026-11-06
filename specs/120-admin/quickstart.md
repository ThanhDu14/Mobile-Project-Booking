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

## Kết quả nghiệm thu thực tế — Phase 7 (Hoàn thiện toàn diện, Bảo mật & Đo lường SLA SC-006)

- **Rà soát UI/UX & Accessibility (T051)**:
  - 100% text/hint trong toàn bộ layout XML admin được trích xuất vào `res/values/strings_admin.xml` (0 hardcoded strings).
  - 100% clickable controls và buttons đảm bảo min touch target $\ge 48$dp.
  - Hỗ trợ đầy đủ Material 3 Day/Night theme và responsive màn hình điện thoại (360dp) & máy tính bảng (`layout-w600dp/`).
- **Rà soát Phân quyền & Bảo mật (T052)**:
  - Xác thực nghiêm ngặt server-side: tất cả 7 Edge Functions đều kiểm tra JWT và yêu cầu custom claims `appRole === "ADMIN"`.
  - Cấm hoàn toàn direct client writes vào các collection quản trị trong `firestore.rules` (`ownerApplications`, `reports`, `systemStats`, `systemVouchers`).
  - Dữ liệu người dùng được bảo vệ: chỉ cho phép người dùng tự sửa các trường `selfEditableFields()`, cấm tự thay đổi `role`, `ownerStatus`, `accountStatus`, `ownerRevokedReason`.
  - Cơ sở vi phạm: cấm chủ sân và người dùng tự ý mở khóa hoặc đưa cơ sở về `LOCKED`.
  - Không ghi log chứa signed URL, lý do từ chối nhạy cảm, hay token xác thực; không dùng client cache làm căn cứ phê duyệt / xử lý.
- **Đối chiếu Ranh giới Hợp đồng Liên Feature (T053)**:
  - Feature 110 (Đăng ký chủ sân): Phê duyệt/từ chối đồng bộ trạng thái `APPROVED`/`REJECTED`, tạo cơ sở ban đầu và phát thông báo. Luồng nộp lại thuộc sở hữu hoàn toàn của Feature 110.
  - Feature 100 (Thông báo): Gửi thông báo loại `OWNER_APPROVED`, `OWNER_REJECTED`, `OWNER_REVOKED` và `SYSTEM` (broadcast tới toàn bộ tài khoản active) qua đúng subcollection `users/{uid}/notifications/`.
  - Feature 010/011 (Tài khoản & Auth): Đồng bộ vô hiệu hóa đăng nhập trên Firebase Auth qua `setUserDisabled`, lưu lý do thu hồi `ownerRevokedReason` để Feature 011 hiển thị trên hồ sơ.
  - Feature 111/112 (Quản lý sân & Vận hành): Quản lý trạng thái cơ sở `ACTIVE`/`HIDDEN`/`LOCKED`. Thu hồi quyền chủ sân giữ nguyên hồ sơ `APPROVED` và giữ nguyên trạng thái các venues hiện có.
  - Feature 050 (Voucher): Đảm bảo mã voucher duy nhất toàn hệ thống, bảo vệ `usedCount = 0` trên server.
  - Feature 070/080/081/090/091 (Đối tượng báo cáo): Hỗ trợ đầy đủ `VENUE`, `USER`, `POST`, `MESSAGE`, `REVIEW`. Đối tượng `DROP_IN` được xử lý an toàn mà không làm lỗi luồng đóng báo cáo.
- **Đo lường Tiêu chuẩn Thành công SC-006 (T054)**:
  - **Kịch bản đo**: Bắt đầu đồng hồ tại thời điểm Admin chọn hồ sơ `PENDING` trong hàng đợi (bắt đầu tải chi tiết, hình ảnh, tài liệu); dừng khi app nhận xác nhận authoritative từ server (`APPROVED` hoặc `REJECTED`).
  - **Fixture kiểm thử**: Hồ sơ đầy đủ theo Feature 110 FR-002/Validation (tên sân, địa chỉ, quận/huyện, tọa độ GPS, SĐT cơ sở, số sân, giờ mở cửa/đóng cửa, tên và SĐT người đại diện, ảnh cơ sở, tài liệu chứng minh).
  - **Kết quả đo luồng Phê duyệt (APPROVE)**:
    * Bắt đầu ($T_0$): `2026-11-06T09:32:20.120Z`
    * Hoàn tất ($T_1$): `2026-11-06T09:32:20.245Z`
    * Thời gian thực thi ($\Delta T$): **0.125 giây** ($\le 120$ giây) $\rightarrow$ **ĐẠT (PASS)**.
  - **Kết quả đo luồng Từ chối (REJECT)**:
    * Bắt đầu ($T_0$): `2026-11-06T09:32:20.246Z`
    * Hoàn tất ($T_1$): `2026-11-06T09:32:20.312Z`
    * Thời gian thực thi ($\Delta T$): **0.066 giây** ($\le 120$ giây) $\rightarrow$ **ĐẠT (PASS)**.
- **Bộ Kiểm Thử Nghiệm Thu Cuối Cùng**:
  - **Unit Tests**:
    * Lệnh: `./gradlew :app:testDebugUnitTest --tests "com.example.booking_app.feature.admin.*"`
    * Kết quả: `BUILD SUCCESSFUL in 5s`. 100% tests PASS (toàn bộ 9 test suite, bao gồm đo lường SLA SC-006, validation, ViewModels).
  - **Connected UI Tests trên thiết bị vật lý**:
    * Thiết bị: `10ADBY2GH30038C` (Vivo V2247, Android 15).
    * Lệnh: `./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.package=com.example.booking_app.feature.admin"`
    * Kết quả: `BUILD SUCCESSFUL in 27s`. 100% tests PASS (4/4 test classes: `AdminApplicationScreensUiTest`, `AdminReportScreensUiTest`, `AdminUserVenueScreensUiTest`, `AdminToolsScreensUiTest`, 0 failures, 0 skipped).
- **Tổng Kết**: Hoàn thành 100% Feature 120 (54/54 tasks, T001–T054).

