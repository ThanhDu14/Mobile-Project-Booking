# Implementation Plan: Đăng ký và đăng nhập tài khoản

**Branch**: `feature/01-account-plan` | **Date**: 2026-10-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/010-account-auth/spec.md`

## Summary

Người dùng tạo tài khoản SmashNow và đăng nhập bằng một trong ba cách: email/mật khẩu, Google, hoặc số điện thoại có OTP. Mọi tài khoản mới đều là người chơi và phải đồng ý điều khoản. Sau khi đăng nhập, app điều hướng theo vai trò. Tài khoản bị khóa không vào được app.

**Cách làm:**
- **Khởi tạo tài khoản:** gói Spark không có trigger Auth, nên **app gọi** Edge Function `on-signup` ngay sau khi xác thực xong và người dùng đã đồng ý điều khoản ([R1](research.md#r1-khởi-tạo-tài-khoản-app-gọi-on-signup-không-dùng-trigger)).
  - Function tạo `users/{uid}` trước, rồi mới gộp claim `role: "authenticated"`, `appRole: "PLAYER"`.
  - Function idempotent: gọi lại bao nhiêu lần cũng được ([R2](research.md#r2-thứ-tự-ghi-và-tính-idempotent)).
  - App gọi `getIdToken(true)` sau khi function trả về.
- **"Đã khởi tạo" = token có claim `appRole`** ([R4](research.md#r4-app-nhận-biết-chưa-khởi-tạo-bằng-claim-appRole)). Nếu chưa có:
  - người đăng ký bằng email (đã đồng ý trên form) thấy màn hình chờ, app tự gọi lại;
  - người đăng nhập Google/OTP lần đầu phải qua bước Hoàn tất tài khoản.
- **Khóa tài khoản:** dùng `accountStatus = LOCKED` **kèm cờ `disabled`** của Firebase Auth. Cả hai do Edge Function `set-account-status` (D) ghi. Firebase tự chặn mọi phương thức đăng nhập và làm mới phiên ([R5](research.md#r5-khóa-tài-khoản-fr-014-accountstatus-kèm-cờ-disabled-của-firebase-auth)).
- **Điều hướng** đọc claim `appRole`. Listener `users/{uid}` phát hiện vai trò thay đổi hoặc tài khoản bị khóa khi đang mở app ([R6](research.md#r6-điều-hướng-theo-vai-trò-fr-015-và-cập-nhật-khi-vai-trò-đổi)).
- **Phần `core/` dùng chung:** spec 010 là spec nền, đặt ở `core/` các thành phần `SessionManager`, `IdTokenProvider`, `SupabaseClient` (Third-party Auth), `AppError` / timeout 15 giây và banner xác minh email ([R11](research.md#r11-phần-core-dùng-chung-cho-cả-nhóm)). Code server dùng chung: `_shared/auth.ts`, `_shared/identity.ts`.
- **Đã kiểm chứng giới hạn của Firebase** (F3, F4, F8 từ analyze), xem [R7](research.md#r7-liên-kết-google-với-emailmật-khẩu-cùng-email-fr-012-analyze-f3)–[R9](research.md#r9-đăng-ký-đăng-nhập-email-và-quên-mật-khẩu-fr-004-fr-011-fr-018-fr-019-analyze-f8). Có 4 chỗ cần sửa spec ([R14](research.md#r14-những-chỗ-cần-sửa-spec-sau-khi-kiểm-chứng)).

## Technical Context

**Language/Version**: Kotlin (tích hợp trong AGP 9.4), JVM target 11; TypeScript/Deno cho Edge Function

**Primary Dependencies**:
- Fragment/Navigation và Safe Args, ViewBinding, Material 3.
- Lifecycle ViewModel/StateFlow, Coroutines, Hilt (KSP).
- Firebase BoM: Auth, Firestore.
- supabase-kt `functions-kt` với Ktor OkHttp, kotlinx-serialization.
- Secrets Gradle Plugin: đọc `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `USE_EMULATOR` từ `local.properties` vào `BuildConfig`, có `local.defaults.properties` (giá trị giả, được commit) để build không lỗi khi thiếu khóa. Constitution nguyên tắc II bắt buộc dùng plugin này.
- Mới: AndroidX DataStore Preferences, AndroidX Credentials và `googleid` ([R12](research.md#r12-google-sign-in-bằng-credential-manager), [R13](research.md#r13-thư-viện-cấu-hình-chung-lỗi-mạng-và-timeout)).
- Server: `jose` (xác minh JWT).

**Storage**:
- Firebase Auth: tài khoản, custom claims, cờ `disabled`.
- Firestore: `users/{uid}`.
- DataStore: `PendingSignup`, mốc chờ gửi lại email.
- Supabase chỉ chạy Edge Function, không lưu dữ liệu.

**Testing**:
- Vitest và `@firebase/rules-unit-testing` cho Rules (U1–U18).
- `deno test` trên Auth/Firestore Emulator cho `on-signup` (S1–S12).
- JUnit4, MockK, Turbine, coroutines-test cho ViewModel, UseCase, `SessionManager` và các rule kiểm tra dữ liệu nhập.
- Espresso (nên có) cho luồng đăng ký và đăng nhập.

**Target Platform**: Android `minSdk 29`, `targetSdk 37`; Supabase Edge Runtime

**Project Type**: Mobile app (Android) + server function (Supabase)

**Performance Goals**:
- Có kết quả đăng nhập ≤ 3 giây ở 95% số lần trên 4G (SC-003). Một lời gọi Firebase, cộng `getIdToken(true)` khi mở app.
- Đăng ký email ≤ 2 phút (SC-001).
- Google tối đa 3 lần chạm (SC-002).

**Constraints**:
- Timeout 15 giây cho mọi lời gọi mạng (FR-021).
- Không lưu mật khẩu, OTP hay token ngoài SDK (FR-024).
- Gói Spark: chỉ dùng số điện thoại thử nghiệm (tối đa 10); 150 email đặt lại mật khẩu mỗi ngày; 1000 email xác minh mỗi ngày.
- Thời hạn OTP và số lần nhập sai do app thực thi ([R8](research.md#r8-otp-số-điện-thoại-fr-016-fr-017-analyze-f4)).

**Scale/Scope**: Quy mô đồ án. 10 màn hình hoặc hộp thoại trong `nav_auth`, 3 màn hình chính tạm thời. 1 Edge Function cộng 2 module `_shared`. 1 match Rules. Gói `core/session`, `core/network`, `core/di` dùng chung cả nhóm.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Nguyên tắc | Đánh giá | Ghi chú |
|---|---|---|
| I. MVVM, package theo tính năng | ✅ | Màn hình ở `feature/auth/ui`. Fragment → ViewModel → UseCase → `AuthRepository` → `FirebaseAuthDataSource`. ViewModel không gọi Firebase SDK. Phần dùng chung ở `core/session` và `core/network`, các nhóm chỉ dùng qua interface `SessionManager`, `IdTokenProvider`, `UserProfileReader`. Một Activity, Navigation và Safe Args |
| II. Bảo mật phía server | ✅ | `role`, `ownerStatus`, `accountStatus`, `walletBalance`, `termsAcceptedAt` chỉ `on-signup` hoặc D ghi bằng service account. Rules chặn client `create`, `delete` và sửa trường 🔒 (U7–U17). Vai trò lưu ở claim `appRole`, có bản sao trong document. Khóa tài khoản chặn ở Firebase Auth (cờ `disabled`). `on-signup` tự xác minh token. Không commit `google-services.json` hay secrets. Rules có test Emulator |
| III. Toàn vẹn dữ liệu đặt lịch | ✅ (không áp dụng trực tiếp) | Không có thao tác đặt lịch. `on-signup` idempotent (tạo có điều kiện `exists=false`), đúng tinh thần idempotent của nguyên tắc này. Thời gian ghi bằng giờ server |
| IV. Kiểm thử có trọng tâm | ✅ | Test phân quyền U1–U18 (SC-006), test `on-signup` S1–S12 trên Emulator, unit test ViewModel/UseCase/`SessionManager`. Không test trên project thật (project `demo-smashnow`) |
| V. Giao diện XML nhất quán | ✅ | XML, Material 3 DayNight, `strings_auth.xml` với tiền tố `auth_`, `TextInputLayout` hiện lỗi dưới ô, `w600dp`, nhãn cho trình đọc màn hình, vùng chạm 48dp. Màn hình có tải dữ liệu (`AccountSetup`) có đủ trạng thái tải, lỗi và thử lại |
| VI. Quyền riêng tư | ✅ | Chỉ thu họ tên, email hoặc số điện thoại. Có màn hình đồng ý điều khoản, ghi `termsAcceptedAt` bằng giờ server (🔒). Nội dung điều khoản ghi rõ phiên bản học tập. `users` chỉ chủ tài khoản và admin đọc được ([R10](research.md#r10-security-rules-cho-usersuid)). Email enumeration protection bật |
| VII. Đơn giản, minh bạch AI | ✅ | Thư viện mới (DataStore, Credentials/googleid, `jose`) đều có trong bảng công nghệ hoặc là cách chính thức để làm việc bắt buộc. Có lý do ở R12, R13. Không thêm module Gradle. Log AI ở `docs/ai-log/` |

**Kết quả:** đạt, không có vi phạm cần ghi vào Complexity Tracking.

**Re-check sau Phase 1:** vẫn đạt. Thiết kế có hai thay đổi chung cần cả nhóm biết:
1. Rules nền: `appRole()` không còn mặc định `PLAYER`, và thêm `isInitialized()`.
2. `users` không cho người khác đọc.

Cả hai đều chặt hơn nguyên tắc II, VI, không nới lỏng.

## Phối hợp với nhóm

| Với | Việc | Hạn |
|---|---|---|
| **D** (spec 120) | 1. Viết Edge Function **`set-account-status`** `{ uid, status: ACTIVE \| LOCKED }`, chỉ `appRole == ADMIN` gọi được. Trong một lần gọi: ghi `accountStatus` và gọi `setUserDisabled(uid, status == LOCKED)` của `_shared/identity.ts`. Admin **không** sửa `accountStatus` qua Rules (U16).<br>2. Thu hồi quyền chủ sân: `mergeClaims(uid, { appRole: "PLAYER" })` **trước**, rồi mới ghi `users.role = PLAYER`, `ownerStatus = NONE`. | Họp 16/10 |
| **D** (spec 110/120) | `review-owner-application`: `mergeClaims(uid, { appRole: "OWNER" })` **trước** khi ghi `users.role/ownerStatus`. App phát hiện `role` khác claim thì tự làm mới token và chuyển giao diện ([R6](research.md#r6-điều-hướng-theo-vai-trò-fr-015-và-cập-nhật-khi-vai-trò-đổi)). Luôn dùng `mergeClaims`, không ghi đè claim (nếu ghi đè thì mất `role` và Supabase coi là `anon`). | Họp 16/10 |
| **D** (111/112, 120) | Thay `OwnerHomePlaceholderFragment`, `AdminHomePlaceholderFragment` trong `nav_owner`, `nav_admin` bằng màn hình thật. | Khi làm UI |
| **B** | `_shared/firestore.ts` cần có `get` và `commit` với `currentDocument.exists=false`, và xuất hàm lấy access token của service account để `_shared/identity.ts` dùng chung. Nếu B chưa có thì A viết bản tối thiểu và báo B. | Trước khi code |
| **C** (070, 080) | 1. `_shared/auth.ts` do A viết theo [contract §4](contracts/edge-function-on-signup.md#4-_sharedauthts-dùng-chung-cả-nhóm). Task T020 của 070 và T007 của 080 dùng file này, không tự viết.<br>2. `users.level` là `String?`, `null` nghĩa là chưa chọn (080 cảnh báo trình độ).<br>3. Không đọc `users/{uid}` của người khác. Dùng snapshot như 070. | Họp 16/10 |
| **Cả nhóm** | 1. Review PR `chore(core)` của A: Hilt, `core/session`, `core/network`, `core/di` (Firebase và Supabase client), Safe Args, DataStore, Credentials, cùng thư viện chung đã chốt ở plan 070 R12.<br>2. Rules nền đổi `appRole()` và thêm `isInitialized()`. Các bạn dùng `isInitialized()` thay `isSignedIn()` cho quyền ghi.<br>3. Lấy `uid` và token qua `SessionManager` / `IdTokenProvider`, không gọi `FirebaseAuth` trong `feature/*`.<br>4. Gửi SHA-1 debug cho người quản lý Firebase. | Trước khi code |
| A (mình) | 1. Cập nhật đặc tả CSDL theo [danh sách cuối data-model.md](data-model.md#danh-sách-thay-đổi-cần-đưa-vào-docsdesigndatabasereadmemd).<br>2. Sửa spec theo [R14](research.md#r14-những-chỗ-cần-sửa-spec-sau-khi-kiểm-chứng).<br>3. Cấu hình Firebase Console theo [quickstart §0](quickstart.md#0-chuẩn-bị).<br>4. Viết script `scripts/admin/grant-role.ts`. | Trước họp 16/10 |

## Project Structure

### Documentation (this feature)

```text
specs/010-account-auth/
├── spec.md
├── plan.md                              # file này
├── research.md                          # Phase 0 (R1–R14)
├── data-model.md                        # Phase 1
├── quickstart.md                        # Phase 1
├── contracts/
│   ├── edge-function-on-signup.md       # on-signup, _shared/auth.ts, _shared/identity.ts, test S1–S12
│   ├── security-rules.md                # users/{uid}, sửa Rules nền, test U1–U18
│   └── ui-screens.md                    # nav graph, 10 màn hình, UiState, interface core/session
├── ui/                                  # wireframe (đã có)
├── checklists/requirements.md
└── tasks.md                             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
app/src/main/java/com/example/booking_app/
├── SmashNowApp.kt                                  # @HiltAndroidApp
├── MainActivity.kt                                 # thay FirstFragment/SecondFragment mẫu; thu thập sessionState
├── core/
│   ├── di/FirebaseModule.kt, SupabaseModule.kt, DataStoreModule.kt, DispatchersModule.kt
│   ├── network/AppError.kt, TaskAwait.kt (awaitWithTimeout 15s), IdTokenProvider.kt, FirebaseIdTokenProvider.kt
│   ├── session/SessionManager.kt, FirebaseSessionManager.kt, SessionState.kt, AppRole.kt,
│   │           UserProfileReader.kt, FirestoreUserProfileReader.kt, SessionPrefs.kt (DataStore)
│   └── ui/VerifyEmailBannerView.kt, VerifyEmailBannerViewModel.kt, LauncherFragment.kt
└── feature/auth/
    ├── domain/
    │   ├── model/ (AuthError, ConsentPrefill, OtpSession, PhoneNumber)
    │   ├── validation/ (NameRule, EmailRule, PasswordRule, PhoneRule, OtpRule)
    │   ├── AuthRepository.kt                       # interface
    │   └── usecase/ (RegisterWithEmail, SignInWithEmail, SignInWithGoogle, LinkGoogleToPassword,
    │                 SendOtp, VerifyOtp, CompleteSignup, SendPasswordReset, ResendVerificationEmail)
    ├── data/
    │   ├── FirebaseAuthDataSource.kt               # bọc FirebaseAuth, đổi exception thành AuthError
    │   ├── GoogleCredentialDataSource.kt           # Credential Manager
    │   ├── OnSignupApi.kt                          # supabase.functions.invoke("on-signup")
    │   └── AuthRepositoryImpl.kt
    └── ui/ welcome/, login/, register/, phone/, otp/, consent/, setup/, forgot/, legal/, link/
        └── (placeholder) home/PlayerHomePlaceholderFragment.kt, OwnerHome…, AdminHome…

app/src/main/res/
├── navigation/nav_main.xml, nav_auth.xml, nav_player.xml, nav_owner.xml, nav_admin.xml
├── layout/fragment_auth_*.xml, dialog_auth_link_account.xml, view_verify_email_banner.xml
├── layout-w600dp/fragment_auth_*.xml               # nếu cần (hoặc dùng dimens w600dp)
├── raw/terms_vi.html, privacy_vi.html
└── values/strings_auth.xml, values-w600dp/dimens.xml

app/src/test/java/com/example/booking_app/
├── core/session/FirebaseSessionManagerTest.kt
└── feature/auth/ (validation/*Test, usecase/*Test, ui/*ViewModelTest)

supabase/
├── config.toml                                     # [functions.on-signup] verify_jwt = false
└── functions/
    ├── _shared/auth.ts, auth.test.ts               # A, dùng chung
    ├── _shared/identity.ts, identity.test.ts       # A, dùng chung (D dùng)
    └── on-signup/index.ts, handler.ts, handler.test.ts

firestore.rules                                     # sửa appRole(), thêm isInitialized(), match users
firestore-tests/test/users.test.ts                  # U1–U18
scripts/admin/grant-role.ts                         # cấp ADMIN/OWNER thủ công bằng service account
gradle/libs.versions.toml, app/build.gradle.kts, build.gradle.kts   # PR chore(core)
```

**Structure Decision**:
- **App:**
  - Package `feature/auth` (nhóm 01 Tài khoản; spec 011 dùng `feature/account`).
  - Phần dùng chung cho mọi nhóm đặt ở `core/session`, `core/network`, `core/di`, để các tính năng không phải import `feature/auth`.
  - Màn hình chính của từng vai trò chỉ là placeholder; spec sở hữu sẽ thay.
- **Server:** `on-signup` là function riêng. Xác thực token và thao tác Identity Toolkit đặt ở `_shared/` vì C và D cần dùng.

## Complexity Tracking

Không có vi phạm constitution cần giải thích.
