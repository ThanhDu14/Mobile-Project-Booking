# Implementation Plan: Hồ sơ và quản lý tài khoản

**Branch**: `feature/01-account-plan` | **Date**: 2026-10-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/011-account-profile/spec.md`

## Summary

Người dùng đã đăng nhập quản lý tài khoản ở trang "Tài khoản": sửa hồ sơ và ảnh đại diện, xem ví giả lập, đăng xuất, theo dõi trạng thái chủ sân, chuyển chế độ (chủ sân), đổi mật khẩu, đổi email và xóa tài khoản.

Spec này **xây trên plan 010**: dùng lại `core/session`, Rules `users/{uid}`, `_shared/auth.ts`, `_shared/identity.ts`, các rule kiểm tra mật khẩu và email. Chỉ mở rộng, không viết lại.

**Cách làm:**
- **Sửa hồ sơ:** client ghi thẳng Firestore. Rules kiểm tra từng trường 010 để lại: `level` thuộc 3 giá trị hoặc `null`, `favoriteDistrictIds` 0–5 phần tử không trùng và có trong `districts`, `avatarPath` đúng tiền tố `uid` ([R2](research.md#r2-rules-kiểm-tra-từng-trường-của-usersuid)).
- **Ảnh đại diện:** bucket public `avatars` trên Supabase Storage.
  - Đường dẫn có phiên bản `{uid}/avatar_{ms}.jpg` để không bị bộ nhớ đệm.
  - RLS chỉ cho ghi và xóa thư mục của chính mình.
  - Thứ tự: tải lên → ghi `avatarPath` → xóa object cũ ([R3](research.md#r3-ảnh-đại-diện-trên-supabase-storage)).
  - Photo Picker không cần quyền; camera xin quyền lúc bấm; cắt vuông bằng thư viện; nén JPEG 512 px ([R4](research.md#r4-chọn-cắt-và-nén-ảnh)).
- **Đăng xuất:** thêm cơ chế `SignOutHook` vào `SessionManager.signOut()`. Hook FCM gỡ token khỏi `fcmTokens` và `deleteToken()` trên máy; hook Google xóa trạng thái Credential Manager ([R5](research.md#r5-đăng-xuất-fr-007)).
- **Mục chủ sân:** suy ra từ claim `appRole`, `ownerStatus`, `ownerRevokedReason` và hồ sơ gần nhất, đủ 5 trạng thái của FR-008. Màn hình chi tiết hồ sơ thuộc spec 110 ([R6](research.md#r6-mục-chủ-sân-trên-trang-tài-khoản-fr-008-fr-009-fr-013)).
- **Chuyển chế độ:** `AppModeController` giữ chế độ trong bộ nhớ. Chuyển sang "Quản lý sân" thì làm mới token và kiểm tra claim `OWNER`. `MainActivity` (010) điều hướng theo `(appRole, mode)` ([R7](research.md#r7-chuyển-chế-độ-người-chơi--quản-lý-sân-fr-010fr-012)).
- **Xác nhận lại danh tính:** luôn làm trước 3 thao tác nhạy cảm (mật khẩu, Google hoặc OTP tùy tài khoản). Server kiểm `auth_time ≤ 5 phút` cho xóa tài khoản ([R8](research.md#r8-xác-nhận-lại-danh-tính-fr-017-fr-018)).
- **Đổi mật khẩu:** `reauthenticate` → `updatePassword`. Firebase thu hồi phiên ở các máy khác ([R9](research.md#r9-đổi-mật-khẩu-us5-fr-014-fr-015)).
- **Đổi email:** `verifyBeforeUpdateEmail`. Do bật Email enumeration protection, app **không biết** email mới đã có người dùng hay chưa (cần sửa US7-2). Edge Function mới `sync-account-email` ghi `users.email` từ token đã xác minh ([R10](research.md#r10-đổi-email-đăng-nhập-us7-fr-016)).
- **Xóa tài khoản:** Edge Function `delete-account` chạy 3 giai đoạn ([R11](research.md#r11-xóa-tài-khoản-delete-account-us6-fr-018fr-022a)):
  1. **Xem trước**, chỉ đọc (FR-022).
  2. **Chốt** trong **một Firestore transaction**: hủy đơn, nhả slot, hoàn tiền khách, ẩn cơ sở, đánh dấu `deletionRequestedAt`. Lỗi thì không đổi gì (FR-021).
  3. **Dọn dẹp** idempotent: tắt Auth, báo khách, ẩn danh, xóa dữ liệu và ảnh, xóa Auth sau cùng. `delete-account-sweeper` (pg_cron 5 phút) làm tiếp nếu chưa xong.

  Các bước đụng dữ liệu của B, C, D gọi helper của chính nhóm đó ([R12](research.md#r12-ẩn-danh-và-các-bước-do-nhóm-khác-cung-cấp)).
- **Ví giả lập:** chỉ hiển thị; nút "Nạp ví" mở chức năng của spec 050 bằng deep link ([R13](research.md#r13-ví-giả-lập-fr-001a)).
- **Đã kiểm chứng tài liệu:** Email enumeration protection, thu hồi phiên, giới hạn Firestore (270 giây, 500 field transform, 10 MiB) và Edge Functions (150 giây, 2 giây CPU). Có 7 chỗ cần sửa spec ([R16](research.md#r16-những-chỗ-cần-sửa-spec)).

## Technical Context

**Language/Version**: Kotlin (tích hợp trong AGP 9.4), JVM target 11; TypeScript/Deno cho Edge Function; SQL (pgTAP) cho test Storage

**Primary Dependencies**:
- Có sẵn từ PR `chore(core)` của 010: Fragment/Navigation và Safe Args, ViewBinding, Material 3, Lifecycle ViewModel/StateFlow, Coroutines, Hilt, Firebase BoM (Auth, Firestore, Messaging), supabase-kt (`functions-kt`, `storage-kt`), Coil, DataStore, AndroidX Credentials và `googleid`.
- **Mới:** `com.vanniktech:android-image-cropper` để cắt ảnh vuông ([R4](research.md#r4-chọn-cắt-và-nén-ảnh), ghi ở Complexity Tracking).
- Server: không thêm thư viện; Storage API gọi bằng `fetch` với service role key trong secrets.

**Storage**:
- Firestore: `users/{uid}` (mở rộng), `districts/{id}` (schema tối thiểu), `accountDeletions/{uid}` (mới, chỉ server).
- Supabase Storage: bucket `avatars` (public, RLS).
- Firebase Auth: mật khẩu, email, cờ `disabled`, xóa người dùng.
- Chế độ chủ sân: trong bộ nhớ, không lưu.

**Testing**:
- Vitest và `@firebase/rules-unit-testing` cho Rules (P1–P18, D1–D4, A1; U1–U18 của 010 vẫn xanh).
- pgTAP (`supabase test db`) cho chính sách Storage (ST1–ST8).
- `deno test` trên Auth/Firestore Emulator cho `delete-account`, sweeper, `sync-account-email` (X1–X16, E1–E3).
- JUnit4, MockK, Turbine, coroutines-test cho rule kiểm tra dữ liệu nhập, `OwnerSection`, `AppModeController`, `SessionManager.signOut`, các ViewModel.
- Espresso (nên có) cho sửa hồ sơ và đăng xuất.

**Target Platform**: Android `minSdk 29`, `targetSdk 37`; Supabase Edge Runtime (gói Free)

**Project Type**: Mobile app (Android) + server function (Supabase)

**Performance Goals**:
- Sửa và lưu hồ sơ < 1 phút (SC-001).
- Ảnh mới hiện < 5 giây ở 95% số lần trên 4G (SC-002): ảnh ≤ 512 px, thường < 150 KB.
- Chuyển chế độ ≤ 1 giây (SC-003): sang "Người chơi" không gọi mạng; sang "Quản lý sân" làm mới token, timeout 3 giây.

**Constraints**:
- Timeout 15 giây cho lời gọi mạng thông thường (giống 010); `delete-account confirm` 120 giây.
- Firestore transaction: 270 giây, 500 field transform, 10 MiB. `delete-account` từ chối khi ước tính > 450 field transform.
- Edge Functions gói Free: 150 giây wall clock, 2 giây CPU mỗi request. Phần dọn dẹp dài được chia cho sweeper.
- Supabase Storage gói Free khoảng 1 GB: ảnh đại diện ≤ 150 KB, xóa object cũ mỗi lần đổi.
- Rules: tối đa 10 lượt `get()` mỗi request (dùng 5).
- Email enumeration protection bật: không báo email đã dùng khi đổi email.

**Scale/Scope**: Quy mô đồ án. 6 màn hình, 2 bottom sheet và 1 hộp thoại trong `nav_account`. 3 Edge Function (`delete-account`, `delete-account-sweeper`, `sync-account-email`) và 1 module `_shared/account-deletion.ts`. 1 migration Storage. Mở rộng 5 thành phần `core/`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Nguyên tắc | Đánh giá | Ghi chú |
|---|---|---|
| I. MVVM, package theo tính năng | ✅ | Màn hình ở `feature/account/ui`. Fragment → ViewModel → UseCase → `AccountRepository` → data source. ViewModel không gọi Firebase hay Supabase SDK. Phần cả nhóm dùng (`AppModeController`, `SignOutHook`, `StorageUrls`, mở rộng `MyProfile`) đặt ở `core/`. Mục dẫn sang tính năng khác dùng deep link, không import `ui/` của nhóm khác ([R14](research.md#r14-trang-tài-khoản-trong-điều-hướng-và-lối-sang-tính-năng-khác)) |
| II. Bảo mật phía server | ✅ | Rules kiểm từng trường người dùng sửa; trường 🔒 vẫn bị chặn (P16–P18). RLS cho bucket `avatars` có test trên Supabase local (ST1–ST8). `email` chỉ server ghi từ token đã xác minh. Xóa tài khoản chạy ở Edge Function, kiểm `auth_time` ở server. Service role key chỉ trong secrets |
| III. Toàn vẹn dữ liệu đặt lịch | ✅ | Hủy đơn, nhả slot, hoàn tiền khi xóa tài khoản chạy trong **một Firestore transaction** bằng helper của B/C/D (đã chạy trong transaction của chính họ). `delete-account` idempotent theo `uid` (X10). Tiền là `Long` VND |
| IV. Kiểm thử có trọng tâm | ✅ | Test Rules, test Storage trên Supabase local, test Edge Function trên Emulator (X7: lỗi transaction thì không đổi gì; X10: gọi lặp/song song), unit test ViewModel/UseCase. Không test trên project thật |
| V. Giao diện XML nhất quán | ✅ | XML, Material 3 DayNight, `strings_account.xml` tiền tố `account_`, `w600dp`, `contentDescription` cho ảnh, vùng chạm 48dp. Trang Tài khoản, Sửa hồ sơ (mục khu vực), Xóa tài khoản có đủ trạng thái tải, có dữ liệu, lỗi + thử lại. Không có trạng thái rỗng vì hồ sơ luôn tồn tại |
| VI. Quyền riêng tư | ✅ | Quyền camera xin lúc bấm; Photo Picker không cần quyền đọc ảnh; từ chối không làm hỏng chức năng khác (FR-024). Xóa tài khoản xóa vĩnh viễn dữ liệu cá nhân, ẩn danh dữ liệu liên quan người khác (Nghị định 13). Ví ghi rõ "giả lập". `accountDeletions` không chứa thông tin cá nhân |
| VII. Đơn giản, minh bạch AI | ✅ (có 1 mục Complexity Tracking) | Thêm thư viện cắt ảnh ngoài bảng công nghệ: ghi lý do bên dưới. `AppModeController` và `SignOutHook` là trừu tượng nhỏ, cần vì nhiều nhóm dùng. Log AI ở `docs/ai-log/` |

**Kết quả:** đạt. Một mục thêm thư viện được ghi ở Complexity Tracking.

**Re-check sau Phase 1:** vẫn đạt. Ba điểm cả nhóm cần biết:
1. `requireUser()` (010) trả thêm `403 ACCOUNT_DELETING`. Mọi function của nhóm tự được bảo vệ, không cần sửa. Chỉ `delete-account` truyền tham số tùy chọn `{ allowDeleting: true }`. App coi mã này là "tài khoản đã được xóa".
4. Trang Tài khoản mở "Điều khoản và quyền riêng tư" bằng deep link `smashnow://legal/{doc}`, không import `LegalDocumentFragment` của `feature/auth/ui` (nguyên tắc I).
2. `MainActivity` (010) điều hướng theo `(appRole, mode)` thay vì chỉ `appRole`.
3. Bảng công nghệ ghi DataStore cho "chế độ chủ sân". Spec 011 chốt chế độ **chỉ trong phiên** (FR-011) nên không lưu. Đây là lựa chọn lưu trữ, không phải bắt buộc, nên không vi phạm.

## Phối hợp với nhóm

| Với | Việc | Hạn |
|---|---|---|
| **B** (040, 050, 060) | 1. Export `cancelBookingInTx(tx, booking, { cancelledBy, reason, refund: "POLICY" \| "FULL" })` và `computeBookingRefund(booking, now)` trong `_shared/booking.ts`; xóa slot trong cùng transaction.<br>2. `anonymizeBookings(uid)`; thêm trường `bookings.userDeleted`. Nếu đơn có lưu tên/số điện thoại người đặt (cho chủ sân xem) thì hàm này xóa các trường đó.<br>3. Chức năng nạp ví giả lập mở được bằng deep link `smashnow://wallet/topup`.<br>4. `_shared/firestore.ts` có `runTransaction` với `runQuery` trong transaction (đã cần cho 080). | Họp 16/10 |
| **C** (070, 080, 081, 090, 091) | 1. `cancelRegistrationInTx` (080) và `leaveWaitlistInTx` (081): đã có trong contract, chỉ cần xác nhận.<br>2. `anonymizeReviews(uid)` theo 070 R9.<br>3. `anonymizeCommunity(uid)`: snapshot tên/ảnh trong bài đăng, tin nhắn về `null` + `authorDeleted`; chuyển quyền chủ nhóm (090); chat riêng chỉ đọc (091).<br>4. Hiện ảnh người dùng bằng `StorageUrls.publicUrl` của `core/media`; đường dẫn ảnh đã đổi thành `{uid}/avatar_{ms}.jpg`.<br>5. Deep link `smashnow://favorites`, `smashnow://dropin/mine`, `smashnow://chat/owner`. | Họp 16/10 |
| **D** (100, 110, 111, 112, 120) | 1. `hideVenueForOwnerDeletionInTx(tx, venueId)` và trường `venues.hiddenReason` 🔒 (`OWNER_DELETED`, chủ sân không mở lại được).<br>2. `cancelSessionInTx(tx, session, { reason, refund: "FULL" })` (112), gồm đóng hàng chờ.<br>3. `send-notification` nhận khóa idempotent (`{kind}_{id}_{recipientUid}_OWNER_DELETED`) để sweeper gửi lại không trùng; gỡ token trả `UNREGISTERED`.<br>4. `deleteOwnerApplications(uid)` xóa hồ sơ và `owner-docs/{uid}/`.<br>5. Xác nhận màn hình "Hồ sơ chủ sân" (wireframe `buoc4`) thuộc spec 110; deep link `smashnow://owner/apply?mode=new\|resubmit`, `smashnow://owner/application`, `smashnow://owner/venues`, `smashnow://notifications/settings`.<br>6. 120 thu hồi quyền: ghi `ownerRevokedReason` cùng lúc với `ownerStatus = NONE`, ghi claim trước (010 R6).<br>7. 120 quản lý `districts` theo schema ở [data-model §3](data-model.md#3-firestore-districtsid-a). | Họp 16/10 |
| **Cả nhóm** | 1. Biết `requireUser()` trả `ACCOUNT_DELETING`; `SessionManager.signOut(reason)` mới có thân mặc định nên fake cũ không phải sửa.<br>2. Dùng `SignOutHook` nếu tính năng có dữ liệu cần dọn khi đăng xuất. | Khi review PR |
| A (mình) | 1. Sửa spec theo [R16](research.md#r16-những-chỗ-cần-sửa-spec) trước `/speckit-tasks`.<br>2. Cập nhật đặc tả CSDL theo [danh sách cuối data-model.md](data-model.md#danh-sách-thay-đổi-cần-đưa-vào-docsdesigndatabasereadmemd).<br>3. Sửa code 010 theo hai điểm: `MainActivity` điều hướng theo `(appRole, mode)`; `SessionManager` xử lý `SESSION_REVOKED` và gọi `SignOutHook`.<br>4. Seed `districts`. | Trước họp 16/10 |

## Project Structure

### Documentation (this feature)

```text
specs/011-account-profile/
├── spec.md
├── plan.md                                  # file này
├── research.md                              # Phase 0 (R1–R16)
├── data-model.md                            # Phase 1
├── quickstart.md                            # Phase 1
├── contracts/
│   ├── security-rules.md                    # users (mở rộng), districts, accountDeletions; P1–P18, D1–D4, A1
│   ├── storage-avatars.md                   # bucket avatars, RLS, ST1–ST8
│   ├── edge-functions-account.md            # delete-account, sweeper, sync-account-email; X1–X16, E1–E3
│   └── ui-screens.md                        # nav_account, 6 màn hình + 2 sheet + 1 hộp thoại, UiState, mở rộng core/session
├── ui/                                      # wireframe (đã có)
├── checklists/requirements.md
└── tasks.md                                 # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
app/src/main/java/com/example/booking_app/
├── MainActivity.kt                                  # [sửa 010] điều hướng theo (appRole, mode)
├── core/
│   ├── session/MyProfile.kt                         # [sửa 010] thêm trường
│   ├── session/AppMode.kt, AppModeController.kt, DefaultAppModeController.kt
│   ├── session/SignOutHook.kt                       # + FirebaseSessionManager gọi hook [sửa 010]
│   ├── session/FcmSignOutHook.kt, GoogleSignOutHook.kt
│   └── media/StorageUrls.kt
└── feature/account/
    ├── domain/
    │   ├── model/ (PlayerLevel, District, ProfileDraft, OwnerSection, ReauthMethod,
    │   │           DeletionImpact, AccountError)
    │   ├── validation/ (DistrictSelectionRule, DifferentPasswordRule, DeleteConfirmRule, AvatarImageRule)
    │   ├── OwnerSectionResolver.kt
    │   ├── AccountRepository.kt, DistrictRepository.kt, AvatarRepository.kt   # interface
    │   └── usecase/ (SaveProfile, ChangeAvatar, RemoveAvatar, SignOut, SwitchMode, Reauthenticate,
    │                 ChangePassword, ChangeEmail, LoadDeletionImpact, DeleteAccount, SyncAccountEmail)
    ├── data/
    │   ├── FirestoreAccountDataSource.kt            # update users, ownerApplications gần nhất
    │   ├── FirestoreDistrictDataSource.kt
    │   ├── SupabaseAvatarDataSource.kt              # storage-kt
    │   ├── ImageProcessor.kt                        # ImageDecoder, nén JPEG 512 px
    │   ├── FirebaseReauthDataSource.kt              # reauthenticate, updatePassword, verifyBeforeUpdateEmail
    │   ├── AccountFunctionsApi.kt                   # delete-account, sync-account-email
    │   └── AccountRepositoryImpl.kt, …
    └── ui/ account/, edit/, district/, avatar/, password/, email/, delete/, reauth/

app/src/main/res/
├── navigation/nav_account.xml                       # include vào nav_player, nav_owner
├── layout/fragment_account*.xml, sheet_account_*.xml, dialog_account_reauth.xml
├── values/strings_account.xml
└── xml/file_paths.xml                               # FileProvider cho ảnh camera

app/src/test/java/com/example/booking_app/
├── core/session/ (AppModeControllerTest, FirebaseSessionManagerSignOutTest)
└── feature/account/ (validation/*Test, OwnerSectionResolverTest, usecase/*Test, ui/*ViewModelTest)

supabase/
├── migrations/<ts>_avatars_bucket.sql               # bucket + RLS
├── migrations/<ts>_cron_delete_account_sweeper.sql  # pg_cron 5 phút
├── tests/storage_avatars_test.sql                   # ST1–ST8
└── functions/
    ├── _shared/account-deletion.ts                  # A: điều phối bước xóa
    ├── _shared/auth.ts, identity.ts                 # [sửa 010] ACCOUNT_DELETING, authTime, deleteUser
    ├── delete-account/index.ts, handler.ts, handler.test.ts, test_fakes.ts
    ├── delete-account-sweeper/index.ts, index.test.ts
    └── sync-account-email/index.ts, index.test.ts

firestore.rules                                      # mở rộng users; districts; accountDeletions
firestore.indexes.json                               # districts, ownerApplications, accountDeletions
firestore-tests/test/users-profile.test.ts, districts.test.ts
scripts/seed/seed.mjs                                # (của nhóm) nạp districts; 011 không viết seed riêng
scripts/admin/rerun-deletion-step.ts
```

**Structure Decision**:
- **App:** package `feature/account` (nhóm 01, tách khỏi `feature/auth` của 010 vì người dùng đã đăng nhập). Phần cả nhóm dùng đặt ở `core/session` và `core/media`.
- **Server:** `delete-account` chỉ điều phối; logic của từng collection nằm trong `_shared/` của nhóm sở hữu. `sync-account-email` tách riêng vì khác điều kiện xác thực với `on-signup`.

## Complexity Tracking

| Vi phạm / bổ sung | Vì sao cần | Phương án đơn giản hơn đã bị loại vì |
|---|---|---|
| Thêm `com.vanniktech:android-image-cropper` (không có trong bảng công nghệ) | US1-3 và FR-003 yêu cầu người dùng cắt ảnh vuông rồi xác nhận | Tự viết màn hình cắt (kéo, phóng to) tốn nhiều mã cử chỉ, không phải trọng tâm đồ án; cắt giữa tự động thì không cho người dùng chọn vùng ảnh ([R4](research.md#r4-chọn-cắt-và-nén-ảnh)) |
| Thêm `io.coil-kt.coil3:coil` + `coil-network-okhttp` 3.4.0 (implement T029; R15 dự kiến PR core của 010 có Coil nhưng chưa có) | Hiện ảnh đại diện từ URL public của bucket `avatars`, có cache bộ nhớ/đĩa, hủy tải theo vòng đời view | Tự tải bằng `HttpURLConnection` + `BitmapFactory` phải tự viết cache và hủy request; Glide nặng hơn và dùng annotation processor. 3.4.0 là bản mới nhất biên dịch bằng Kotlin ≤ 2.3 (AGP 9.4.1 dùng Kotlin 2.2.10). Spec 030 cũng cần Coil: dùng chung khai báo này, không thêm trùng |
