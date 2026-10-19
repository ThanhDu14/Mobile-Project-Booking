---

description: "Danh sách task triển khai spec 010 – Đăng ký và đăng nhập tài khoản"
---

# Tasks: Đăng ký và đăng nhập tài khoản

**Input**: tài liệu thiết kế trong `specs/010-account-auth/`: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Có. Constitution nguyên tắc IV bắt buộc test cho:
- Security Rules `users/{uid}` (U1–U18) trên Firebase Emulator;
- Edge Function `on-signup` (S1–S12) trên Auth/Firestore Emulator, gồm **gọi nhiều lần liên tiếp và đồng thời** (idempotent);
- rule kiểm tra dữ liệu nhập, UseCase, ViewModel và `SessionManager`.

Viết test trước, chạy thấy **fail**, rồi mới cài đặt.

**Organization**: nhóm theo user story để mỗi story làm và kiểm thử độc lập được. Phase 2 tách thành 3 PR để nhóm review và merge sớm.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: làm song song được (file khác nhau, không phụ thuộc task chưa xong)
- **[Story]**: user story của spec (US1…US5)

## Quy ước đường dẫn

| Viết tắt | Đường dẫn thật |
|---|---|
| `APP/` | `app/src/main/java/com/example/booking_app/` |
| `CORE/` | `app/src/main/java/com/example/booking_app/core/` |
| `AUTH/` | `app/src/main/java/com/example/booking_app/feature/auth/` |
| `RES/` | `app/src/main/res/` |
| `TEST/` | `app/src/test/java/com/example/booking_app/` |
| `ATEST/` | `app/src/androidTest/java/com/example/booking_app/` |
| `FN/` | `supabase/functions/` |

---

## Phase 1: Setup (thư viện và cấu hình, thuộc PR `chore(core)`)

**Purpose**: thư viện và cấu hình chung. Spec 010 là spec đầu tiên viết code nên A làm phần này; các task T001–T009 của [tasks 070](../070-review-favorite/tasks.md) trùng phần lớn: **phần nào đã có thì đánh dấu xong và dùng lại, không làm trùng**.

- [x] T001 Thêm vào `gradle/libs.versions.toml` (danh sách đã chốt ở plan 070 R12 và [research R13](research.md#r13-thư-viện-cấu-hình-chung-lỗi-mạng-và-timeout)):
  - Hilt và KSP, `lifecycle-viewmodel-ktx`, `lifecycle-runtime-ktx`, `kotlinx-coroutines-android`, plugin Navigation Safe Args;
  - supabase-kt `functions-kt` (kèm `storage-kt` cho 070), `ktor-client-okhttp`, `kotlinx-serialization-json`;
  - **mới của 010**: `androidx.datastore:datastore-preferences`, `androidx.credentials:credentials`, `credentials-play-services-auth`, `com.google.android.libraries.identity.googleid:googleid`;
  - plugin **Secrets Gradle Plugin** (`com.google.android.libraries.mapsplatform.secrets-gradle-plugin`), bắt buộc theo constitution nguyên tắc II để đọc khóa phía app từ `local.properties`;
  - test: MockK, Turbine, `kotlinx-coroutines-test`.
- [x] T002 Áp dụng plugin (Hilt, KSP, kotlin serialization, Safe Args, Secrets Gradle Plugin) và khai báo dependency trong `build.gradle.kts`, `app/build.gradle.kts`; bật `viewBinding`, `buildConfig`. Cấu hình Secrets Gradle Plugin:
  - khối `secrets { propertiesFileName = "local.properties"; defaultPropertiesFileName = "local.defaults.properties" }`, sinh `BuildConfig.SUPABASE_URL`, `BuildConfig.SUPABASE_PUBLISHABLE_KEY`, `BuildConfig.USE_EMULATOR`;
  - **không** tự đọc `local.properties` bằng `Properties()` trong Gradle;
  - tạo `local.defaults.properties` (được commit) với giá trị giả: `SUPABASE_URL=http://10.0.2.2:54321`, `SUPABASE_PUBLISHABLE_KEY=DEFAULT_KEY`, `USE_EMULATOR=false`, để máy chưa có khóa thật vẫn build được;
  - plugin sinh mọi giá trị dạng `String`, nên code so sánh `BuildConfig.USE_EMULATOR == "true"` (T015);
  - kiểm tra phiên bản plugin chạy được với AGP 9.4; nếu không chạy được thì dừng lại và báo nhóm, không tự quay về cách đọc tay.
- [x] T003 [P] Kiểm tra `.gitignore` chặn `app/google-services.json`, `local.properties`, `supabase/.env*` (trừ `*.example`), và **không** chặn `local.defaults.properties`; tạo `supabase/.env.example` với `FIREBASE_PROJECT_ID`, `FIREBASE_SERVICE_ACCOUNT` giả
- [x] T004 Tạo `APP/SmashNowApp.kt` (`@HiltAndroidApp`), khai báo trong `app/src/main/AndroidManifest.xml`; gắn `@AndroidEntryPoint` cho `APP/MainActivity.kt`; xóa `APP/FirstFragment.kt`, `APP/SecondFragment.kt`, `RES/navigation/nav_graph.xml` mẫu và layout đi kèm
- [ ] T005 [P] Cấu hình Firebase Console theo [quickstart §0](quickstart.md#0-chuẩn-bị) (người quản lý project làm): bật Email/Password, Google, Phone; tối đa 10 số thử nghiệm (không ghi vào repo); Email enumeration protection bật; "Link accounts that use the same email"; Password policy min 8, Require numeric, chế độ **Require**; template email tiếng Việt; SHA-1 debug của từng thành viên. Ghi ngày làm vào `docs/guides/firebase-auth-setup.md`

**Checkpoint**: `./gradlew assembleDebug` chạy được.

---

## Phase 2: Foundational (chặn mọi user story)

**⚠️ CRITICAL**: chưa xong phase này thì chưa làm user story nào. Chia 3 PR, merge theo thứ tự 2A → 2B → 2C (2B làm song song với 2A được).

### 2A. PR `chore(core)`: phần dùng chung cả nhóm ([research R11](research.md#r11-phần-core-dùng-chung-cho-cả-nhóm))

**Tests (viết trước, phải fail)**

- [x] T006 [P] Unit test `TEST/core/network/TaskAwaitTest.kt` và `TEST/core/network/FunctionErrorsTest.kt`:
  - `awaitWithTimeout`: Task xong trước 15 s → trả kết quả; quá `15_000` ms (thời gian ảo) → `AppError.Timeout`;
  - `FirebaseNetworkException` → `AppError.Network`; `FirebaseTooManyRequestsException` → `AppError.RateLimited`; `FirebaseAuthInvalidUserException` mã `ERROR_USER_DISABLED` → `AppError.Locked`;
  - body lỗi Edge Function `{ "error": { "code": "ACCOUNT_LOCKED" } }` → `AppError.Locked` và phát sự kiện tới `AccountLockedBus`; `401` → làm mới token (`getToken(true)`) và thử lại **đúng 1 lần**.
- [x] T007 [P] Unit test `TEST/core/session/FirebaseSessionManagerTest.kt` (fake `SessionAuthSource`, fake `SessionPrefs`, fake `UserProfileReader`, Turbine), mỗi nhánh của `SessionState`:
  - không có người dùng → `SignedOut(reason = null)`;
  - có người dùng, token **không có** claim `appRole`, có `PendingSignup` cùng `uid` → `Initializing`;
  - như trên nhưng không có `PendingSignup` → `NeedsConsent(prefill)` với `provider`, `email`, `phone`, `displayName` lấy từ Firebase; `PendingSignup` của `uid` khác bị xóa;
  - claim `appRole` = `PLAYER` / `OWNER` / `ADMIN` → `SignedIn(uid, appRole, emailVerified)`; giá trị lạ → `PLAYER`;
  - khi mở app: gọi `reload()` và `getIdToken(true)`; lỗi `ERROR_USER_DISABLED` → `signOut()` và `SignedOut(SignOutReason.LOCKED)`; mất mạng → dùng claim của token đã lưu (`getIdToken(false)`), vẫn `SignedIn` (FR-013);
  - listener `users/{uid}`: `accountStatus == LOCKED` → đăng xuất ngay, `SignedOut(LOCKED)`; `role` khác `appRole` của token → gọi `getIdToken(true)` rồi phát `SignedIn` với vai trò mới (R6);
  - `AccountLockedBus` phát sự kiện → `SignedOut(LOCKED)`;
  - `signOut()` xóa `pending_signup_*`, `last_verification_email_at`, **giữ** `welcome_seen`; `currentUid()` / `currentAppRole()` trả `null` khi chưa `SignedIn`;
  - `isGuest` là `true` đúng khi `SignedOut` (chế độ khách, [R15](research.md#r15-chế-độ-khách-và-quay-lại-đúng-chỗ-sau-khi-đăng-nhập-fr-025-fr-026)).

**Implementation**

- [x] T008 [P] Tạo `CORE/session/AppRole.kt` (`PLAYER`, `OWNER`, `ADMIN`; hàm `fromClaim(String?)`: `null` → `null`, giá trị lạ → `PLAYER`), `CORE/session/SessionState.kt` (`Loading` · `SignedOut(reason: SignOutReason?)` · `NeedsConsent(prefill)` · `Initializing` · `InitFailed(error: AppError)` · `SignedIn(uid, appRole, emailVerified)`), `SignOutReason` (`LOCKED`), `CORE/session/ConsentPrefill.kt` (`provider: GOOGLE | PHONE | PASSWORD`, `email: String?`, `phone: String?`, `displayName: String?`) theo [data-model §4](data-model.md#4-mô-hình-phía-app-coresession-featureauthdomain). `ConsentPrefill` đặt ở `core/session` vì `SessionState` dùng nó
- [x] T009 [P] Tạo `CORE/network/AppError.kt` (`Network`, `Timeout`, `RateLimited`, `Locked`, `NotSignedIn`, `Unknown(cause)`), `CORE/network/TaskAwait.kt` (`suspend fun <T> Task<T>.awaitWithTimeout(ms = 15_000)` kèm ánh xạ exception ở T006), `CORE/network/AccountLockedBus.kt` (`SharedFlow<Unit>`), `CORE/network/FunctionErrors.kt` (đọc body `{ error: { code, message } }` của supabase-kt, `invokeWithAuthRetry` thử lại 1 lần khi `401`). Cho T006 xanh
- [x] T010 [P] Tạo `CORE/session/SessionPrefs.kt` (DataStore `session_prefs`) với các key **đúng bảng [data-model §3](data-model.md#3-trên-máy-datastore-session_prefs)**: `pending_signup_uid` (String), `pending_signup_name` (String), `pending_signup_terms_at` (Long), `last_verification_email_at` (Long), `last_password_reset_at` (Long), `welcome_seen` (Boolean, không xóa khi đăng xuất); `data class PendingSignup(uid, name, termsAt)`; **không lưu mật khẩu, OTP hay token** (FR-024). Tạo `CORE/di/DataStoreModule.kt`
- [x] T011 [P] Tạo `CORE/network/IdTokenProvider.kt` (`suspend fun getToken(forceRefresh: Boolean = false): String`, ném `AppError.NotSignedIn`) và `CORE/network/FirebaseIdTokenProvider.kt` (dùng `awaitWithTimeout`)
- [x] T012 [P] Tạo `CORE/session/UserProfileReader.kt` (`fun observeMyProfile(): Flow<MyProfile?>`), `CORE/session/MyProfile.kt` (`displayName`, `level: String?` — `null` là chưa chọn, `favoriteDistrictIds`, `avatarPath`, `role`, `accountStatus`) và `CORE/session/FirestoreUserProfileReader.kt` (`callbackFlow` trên `users/{uid}` của chính mình, đổi `uid` thì đổi listener)
- [x] T013 Tạo `CORE/session/SessionAuthSource.kt` (interface: `authState: Flow<AuthUser?>`, `getClaims(forceRefresh)`, `reload()`, `signOut()`; `AuthUser` gồm `uid`, `email`, `phone`, `displayName`, `providers`, `isEmailVerified`) và `CORE/session/FirebaseSessionAuthSource.kt` bọc `FirebaseAuth`. Đây là chỗ duy nhất ngoài `feature/auth/data` được gọi `FirebaseAuth`
- [x] T014 Tạo `CORE/session/SessionManager.kt` (interface đúng [contracts/ui-screens.md §4](contracts/ui-screens.md#4-giao-diện-coresession-cho-các-nhóm-khác): `sessionState`, `isGuest`, `refresh(forceTokenRefresh)`, `signOut()`, `currentUid()`, `currentAppRole()`) và `CORE/session/FirebaseSessionManager.kt` (`@Singleton`) theo [R4](research.md#r4-app-nhận-biết-chưa-khởi-tạo-bằng-claim-appRole), [R5](research.md#r5-khóa-tài-khoản-fr-014-accountstatus-kèm-cờ-disabled-của-firebase-auth), [R6](research.md#r6-điều-hướng-theo-vai-trò-fr-015-và-cập-nhật-khi-vai-trò-đổi). Cho T007 xanh
- [x] T015 [P] Tạo `CORE/di/FirebaseModule.kt` (`FirebaseAuth`, `FirebaseFirestore`; `BuildConfig.USE_EMULATOR == "true"` thì trỏ `10.0.2.2:9099` / `10.0.2.2:8080`), `CORE/di/SupabaseModule.kt` (`SupabaseClient` có Functions, `accessToken = { idTokenProvider.getToken(false) }`), `CORE/di/DispatchersModule.kt`, `CORE/di/SessionModule.kt` (bind `SessionManager`, `IdTokenProvider`, `UserProfileReader`, `SessionAuthSource`)
- [x] T016 Tạo điều hướng nền theo [contracts/ui-screens.md §1](contracts/ui-screens.md#1-nav-graph):
  - `RES/navigation/nav_main.xml` (start `LauncherFragment`, `include` 4 graph con);
  - `RES/navigation/nav_auth.xml` (tạm chỉ có `WelcomeFragment` khung; các story thêm destination; khai báo deep link `smashnow://auth/login?returnTo={returnTo}` và `smashnow://auth/register?returnTo={returnTo}` cho chế độ khách, R15);
  - `RES/navigation/nav_player.xml`, `nav_owner.xml`, `nav_admin.xml` với `AUTH/ui/home/PlayerHomePlaceholderFragment.kt`, `OwnerHomePlaceholderFragment.kt`, `AdminHomePlaceholderFragment.kt` (layout đơn giản hiện tên vai trò).
- [x] T017 Tạo `CORE/ui/LauncherFragment.kt` + `RES/layout/fragment_launcher.xml` (logo, vòng xoay). `APP/MainActivity.kt` thu thập `sessionState` (`repeatOnLifecycle`):
  - `SignedIn` → `nav_player` / `nav_owner` / `nav_admin` theo `appRole`, `popUpTo(nav_main, inclusive = true)`; `appRole` đổi khi đang mở app → điều hướng lại;
  - chọn đích khi mở app bằng `SessionRouter` (T080–T081): `SignedOut` chưa `welcome_seen` → `nav_auth` / `WelcomeFragment`; `SignedOut` đã xem → `nav_player` chế độ khách; `reason == LOCKED` → `nav_player` chế độ khách rồi `LoginFragment(lockedNotice = true)` chồng lên (nối ở US2);
  - đang đăng nhập mà chuyển sang `SignedOut` (đăng xuất, bị khóa) → `nav_player` chế độ khách, `popUpTo(nav_main, inclusive = true)`, **không** về `nav_auth`;
  - `Initializing` / `InitFailed` / `NeedsConsent`: để `TODO` có comment, nối ở US1 (T047) và US4 (T066).
- [x] T018 [P] Chuẩn hóa theme `Theme.Material3.DayNight` trong `RES/values/themes.xml` và `RES/values-night/themes.xml`; thêm `support_email` vào `RES/values/strings.xml`; tạo `RES/values/dimens.xml` và `RES/values-w600dp/dimens.xml` (`auth_form_max_width = 480dp`, căn giữa)

**Checkpoint 2A**: T006, T007, T080 xanh; app cài mới mở lên vào `LauncherFragment` rồi `WelcomeFragment`; đã qua màn hình chào thì vào `nav_player` chế độ khách. Mở PR `chore(core)`, nhờ cả nhóm review và báo quy ước: lấy `uid`/token qua `SessionManager` / `IdTokenProvider`, không gọi `FirebaseAuth` trong `feature/*`.

### 2B. PR `feat(auth)`: server dùng chung, `on-signup` và Rules

- [ ] T019 **(phụ thuộc B)** Kiểm tra `FN/_shared/firestore.ts` có: `get(path)`, `commit(writes)` hỗ trợ `currentDocument.exists = false` (trả lỗi `ALREADY_EXISTS` phân biệt được), và **xuất** hàm lấy access token service account theo scope (để `identity.ts` dùng chung). Nếu B chưa có: viết bản tối thiểu đúng 3 hàm này, ghi `// TODO(B): thay bằng bản đầy đủ` và báo B
- [ ] T020 [P] Viết test `FN/_shared/auth.test.ts` (chạy thấy fail):
  - chế độ Emulator: token không ký của Auth Emulator → `verifyIdToken` trả `{ uid, appRole, email, emailVerified, phone, name, signInProvider }`, có log cảnh báo;
  - chế độ thật với JWKS giả: sai chữ ký, hết hạn, sai `aud`, sai `iss`, thiếu header → `401 UNAUTHENTICATED`;
  - `requireUser`: thiếu `appRole` → `403 NOT_INITIALIZED`; `accountStatus == LOCKED` → `403 ACCOUNT_LOCKED`;
  - `requireRole(user, "ADMIN")` với `PLAYER` → `403 FORBIDDEN`;
  - `errorResponse` trả đúng dạng `{ "error": { "code", "message", ...extra } }`.
- [ ] T021 Cài `FN/_shared/auth.ts` đúng [contract §4](contracts/edge-function-on-signup.md#4-_sharedauthts-dùng-chung-cả-nhóm) (`jose.createRemoteJWKSet`, `iss = https://securetoken.google.com/<projectId>`, `aud = <projectId>`). Cho T020 xanh. **Báo C**: task T020 của 070 và T007 của 080 dùng file này, không tự viết
- [ ] T022 [P] Viết test `FN/_shared/identity.test.ts` trên Auth Emulator:
  - `getCustomClaims` / `setCustomClaims`;
  - `mergeClaims(uid, { appRole: "OWNER" })` giữ nguyên `role: "authenticated"` đã có;
  - claim JSON ≥ 1000 byte → bị từ chối;
  - `setUserDisabled(uid, true)` → đăng nhập bằng mật khẩu trả `USER_DISABLED`; `false` → đăng nhập lại được.
- [ ] T023 Cài `FN/_shared/identity.ts` đúng [contract §5](contracts/edge-function-on-signup.md#5-_sharedidentityts-dùng-chung-a-d) (Identity Toolkit `accounts:lookup`, `accounts:update` với `customAttributes` / `disableUser`; Emulator dùng `Authorization: Bearer owner`). Cho T022 xanh
- [ ] T024 [P] Viết test `FN/on-signup/handler.test.ts` (Auth + Firestore Emulator), đủ **S1–S12** của [contract §6](contracts/edge-function-on-signup.md#6-test-deno-test-firebase-emulator-auth--firestore), thêm:
  - **gọi liên tiếp**: 5 lần cho cùng `uid` → lần 1 `CREATED`, 4 lần sau `ALREADY_INITIALIZED`, `createdAt` không đổi;
  - **gọi đồng thời**: 10 lời gọi song song (`Promise.all`) cho cùng `uid` → cả 10 trả `200`, đúng 1 document, claim đúng `{ role: "authenticated", appRole: "PLAYER" }`; chạy 10 lần liên tiếp đều xanh;
  - S1 kiểm tra đủ trường theo [data-model §2](data-model.md#2-firestore-usersuid-a): `level == null`, `favoriteDistrictIds == []`, `role == "PLAYER"`, `ownerStatus == "NONE"`, `accountStatus == "ACTIVE"`, `fcmTokens == []`, `notificationPrefs == {}`, `walletBalance == 0`, `termsAcceptedAt`/`createdAt`/`updatedAt` là giờ server, `email` đã `lowercase`, không có `ownerRevokedReason`;
  - `GET` → `405 METHOD_NOT_ALLOWED`; token không có email lẫn `phone_number` → `400 MISSING_CONTACT`.
- [ ] T025 Cài `FN/on-signup/handler.ts` (logic thuần, nhận `firestore`, `identity` qua tham số để test) và `FN/on-signup/index.ts` theo [contract §1–§3](contracts/edge-function-on-signup.md#1-request): `displayName` sau `trim` dài 2–50 (trống thì dùng `token.name`), `termsAccepted == true` khi chưa có document, `email`/`phone` lấy **từ token**, bỏ qua mọi trường lạ trong body; tạo document trước, gộp claim sau, không bao giờ hạ `appRole`. Thêm `[functions.on-signup] verify_jwt = false` vào `supabase/config.toml`. Cho T024 xanh
- [ ] T026 [P] Viết test `firestore-tests/test/users.test.ts` đủ **U1–U18** của [contracts/security-rules.md §3](contracts/security-rules.md#3-bảng-test-mỗi-dòng-ít-nhất-một-test-theo-đặc-tả-csdl-mục-7); sửa `firestore-tests/test/base.test.ts` nếu đang dựa vào mặc định `PLAYER` của `appRole()`
- [ ] T027 Sửa `firestore.rules` đúng [contracts/security-rules.md §1–§2](contracts/security-rules.md#1-sửa-rules-nền): `appRole()` trả `null` khi thiếu claim, thêm `isInitialized()`, `isPlayer()`, thêm `match /users/{uid}` (`get` của mình hoặc admin, `list` chỉ admin, `create`/`delete` cấm, `update` chỉ `selfEditableFields()` với `updatedAt == request.time`, `displayName` 2–50). Cho T026 và 3 test nền xanh. **Báo cả nhóm** dùng `isInitialized()` thay `isSignedIn()` cho quyền ghi
- [ ] T028 [P] Viết `scripts/admin/grant-role.ts <uid> <ADMIN|OWNER|PLAYER>` theo [quickstart §5](quickstart.md#5-cấp-tài-khoản-admin-thủ-công-constitution-ii): dùng service account, **`mergeClaims` trước rồi mới ghi `users.role`**; từ chối khi `users/{uid}` chưa có

**Checkpoint 2B**: `deno test -A on-signup/ _shared/` và `npm test` trong `firestore-tests/` đều xanh; chạy tay `curl` ở [quickstart §2](quickstart.md#2-test-edge-function-on-signup-bắt-buộc) trả `CREATED` rồi `ALREADY_INITIALIZED`.

### 2C. Domain `feature/auth` dùng chung cho các story

- [x] T029 [P] Unit test validation trong `TEST/feature/auth/domain/validation/` (`NameRuleTest`, `EmailRuleTest`, `PasswordRuleTest`, `ConfirmPasswordRuleTest`, `PhoneRuleTest`, `OtpRuleTest`), đúng bảng [data-model §4](data-model.md#kiểm-tra-dữ-liệu-nhập-thuần-kotlin-có-unit-test):
  - `NameRule`: "`trim` dài 2–50" (biên 1, 2, 50, 51; chỉ khoảng trắng);
  - `EmailRule`: "`trim().lowercase()` khớp `Patterns.EMAIL_ADDRESS`" (dùng regex tương đương trong unit test);
  - `PasswordRule`: "≥ 8 ký tự, có ít nhất một chữ cái (`Char.isLetter`) và một chữ số"; trả riêng `len8`, `letterDigit` cho hai dấu tích;
  - `ConfirmPasswordRule`: bằng mật khẩu;
  - `PhoneRule`: "`^(0|\+84)(3|5|7|8|9)\d{8}$` sau khi bỏ khoảng trắng, chuẩn hóa thành `+84…`" (`0912 345 678` → `+84912345678`; đầu `2`, `4`, `6` bị từ chối);
  - `OtpRule`: đúng 6 chữ số.
- [x] T030 [P] Cài `AUTH/domain/validation/NameRule.kt`, `EmailRule.kt`, `PasswordRule.kt`, `ConfirmPasswordRule.kt`, `PhoneRule.kt`, `OtpRule.kt` (thuần Kotlin, trả mã lỗi `auth_error_name_length`, `auth_error_email_format`, `auth_error_password_rule`, `auth_error_password_mismatch`, `auth_error_phone_format`, `auth_error_otp_format`). Cho T029 xanh
- [x] T031 [P] Unit test `TEST/feature/auth/data/AuthErrorMapperTest.kt`: `INVALID_LOGIN_CREDENTIALS` / `ERROR_WRONG_PASSWORD` / `ERROR_USER_NOT_FOUND` → `InvalidCredentials` (cùng một lỗi, FR-011); `ERROR_USER_DISABLED` → `AccountLocked`; `ERROR_EMAIL_ALREADY_IN_USE` → `EmailInUse`; `ERROR_INVALID_VERIFICATION_CODE` → `InvalidCode`; `ERROR_SESSION_EXPIRED` → `CodeExpired`; `FirebaseTooManyRequestsException` → `TooManyAttempts`; `ERROR_QUOTA_EXCEEDED` / `BILLING_NOT_ENABLED` → `PhoneNotSupported`; `ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL` → `NeedsLinking(email, pendingCredential)`; `AppError.Network` / `Timeout` → `Network`; còn lại → `Unknown`
- [x] T032 Tạo `AUTH/domain/model/AuthError.kt` (đủ 12 lỗi của [data-model §4](data-model.md#4-mô-hình-phía-app-coresession-featureauthdomain)), `AUTH/domain/AuthRepository.kt` (interface cho mọi UseCase), `AUTH/data/AuthErrorMapper.kt`, khung `AUTH/data/FirebaseAuthDataSource.kt` (mọi lời gọi qua `awaitWithTimeout`), khung `AUTH/data/AuthRepositoryImpl.kt`, `AUTH/data/di/AuthModule.kt`. Cho T031 xanh
- [x] T033 [P] Unit test `TEST/feature/auth/domain/usecase/CompleteSignupUseCaseTest.kt` (fake `OnSignupApi`, fake `SessionManager`, fake `SessionPrefs`):
  - `200` → gọi `sessionManager.refresh(forceTokenRefresh = true)` rồi xóa `PendingSignup`;
  - `400 TERMS_REQUIRED` / `INVALID_DISPLAY_NAME` → lỗi tương ứng, giữ `PendingSignup`;
  - `403 ACCOUNT_LOCKED` → `AuthError.AccountLocked`, **không** gọi `signOut()`: `FunctionErrors` đã báo `AccountLockedBus`, `SessionManager` tự đăng xuất để giữ lý do `LOCKED` (sửa khi cài đặt, 2026-10-19);
  - `401` → lấy lại token, thử lại 1 lần; vẫn lỗi → `signOut()`;
  - hết 15 s / mất mạng → `AuthError.Network`, giữ `PendingSignup`.
- [x] T034 Tạo `AUTH/data/OnSignupApi.kt` (`@Serializable` `OnSignupRequest(displayName, termsAccepted)`, `OnSignupResponse(status, appRole)`, gọi `supabase.functions.invoke("on-signup")` qua `invokeWithAuthRetry`, timeout 15 s) và `AUTH/domain/usecase/CompleteSignupUseCase.kt`. Cho T033 xanh
- [x] T035 [P] Tạo `RES/values/strings_auth.xml`: mọi chuỗi tiền tố `auth_` cho 10 màn hình, mã lỗi validation ở T030, thông báo cho từng `AuthError` (gồm "Email hoặc mật khẩu không đúng", "Tài khoản của bạn đã bị khóa", "Bạn đã thử quá nhiều lần, vui lòng thử lại sau", "Không gửi được mã tới số này. Hãy dùng email hoặc Google", "Không có kết nối mạng", "Thử lại"); tạo `RES/raw/terms_vi.html`, `RES/raw/privacy_vi.html` có dòng "Phiên bản học tập" (FR-003, theo tinh thần Nghị định 13/2023/NĐ-CP)

### 2D. Chế độ khách (bổ sung 2026-10-19, [research R15](research.md#r15-chế-độ-khách-và-quay-lại-đúng-chỗ-sau-khi-đăng-nhập-fr-025-fr-026))

Thêm sau khi `/speckit-analyze` spec 020 phát hiện khách không vào được tìm kiếm. ID nối tiếp sau T079 để không đánh số lại; **thứ tự làm**: T080–T081 cùng T017 (thuộc PR `chore(core)`), T082–T083 sau T016.

- [x] T080 [P] Unit test `TEST/core/session/SessionRouterTest.kt` cho hàm thuần `SessionRouter.startDestination(state, welcomeSeen)` và `SessionRouter.afterSignIn(appRole, hasPlayerGraphBelowAuth, returnTo)`: đủ các dòng của bảng R15 (`SignedOut` chưa/đã `welcome_seen`, `SignedOut(LOCKED)`, `SignedIn` theo từng vai trò, `NeedsConsent`/`Initializing`/`InitFailed`); sau đăng nhập `PLAYER` có `nav_player` bên dưới → `PopAuth(returnTo)`, `OWNER`/`ADMIN` hoặc không có `nav_player` bên dưới → `ResetTo(graph)`
- [x] T081 Tạo `CORE/session/SessionRouter.kt` (kiểu trả về `RouteDecision`: `Welcome`, `PlayerGuest(showLockedLogin: Boolean)`, `RoleHome(appRole)`, `AccountSetup`, `Consent`, `PopAuth(returnTo: Uri?)`, `ResetTo(appRole)`); `LauncherFragment` và `MainActivity` (T017, T053) chỉ thực hiện quyết định, không tự rẽ nhánh. Cho T080 xanh
- [x] T082 [P] Tạo `CORE/session/AuthNavigator.kt` (interface `openLogin(navController, returnTo: Uri?)`, `openRegister(...)`) và `CORE/session/DeepLinkAuthNavigator.kt` (điều hướng tới `smashnow://auth/login?returnTo=…` / `smashnow://auth/register?returnTo=…` **không** `popUpTo`, để `nav_auth` nằm chồng lên màn hình hiện tại); bind trong `CORE/di/SessionModule.kt`
- [x] T083 Tạo `CORE/ui/LoginPromptView.kt` + `RES/layout/view_login_prompt.xml` (biểu tượng, câu mời truyền vào, nút "Đăng nhập", "Đăng ký" gọi `AuthNavigator` với `returnTo` là màn hình hiện tại; vùng chạm 48dp, `contentDescription`); dùng trong `AUTH/ui/home/PlayerHomePlaceholderFragment.kt` và các tab placeholder của `nav_player` khi `SessionManager.isGuest`; thêm chuỗi `auth_guest_*` vào `RES/values/strings_auth.xml` ("Xem sân trước", "Đăng nhập để dùng tính năng này")

**Checkpoint**: nền xong, các user story bắt đầu được.

---

## Phase 3: User Story 1 – Đăng ký bằng email và mật khẩu (Priority: P1) 🎯 MVP

**Goal**: người mới đăng ký bằng email, đồng ý điều khoản, vào thẳng màn hình chính người chơi, thấy banner xác minh email.

**Independent Test**: cài app mới, đăng ký email chưa dùng → vào `nav_player`; Firestore có `users/{uid}` đúng họ tên, email, `termsAcceptedAt`; token có `appRole: PLAYER`, `role: authenticated` (quickstart M1–M4).

### Tests cho User Story 1 ⚠️ viết trước, phải fail

- [ ] T036 [P] [US1] Unit test `TEST/feature/auth/domain/usecase/RegisterWithEmailUseCaseTest.kt`:
  - email được `trim().lowercase()` trước khi tạo;
  - thứ tự: `createUserWithEmailAndPassword` → lưu `PendingSignup(uid, name, termsAt)` → `sendEmailVerification()` (lỗi gửi email bị bỏ qua, ghi `last_verification_email_at` khi gửi được) → `CompleteSignupUseCase`;
  - `EmailInUse` → không tạo, không lưu `PendingSignup`;
  - **thử lại sau timeout** (R13): `currentUser?.email == email` → không gọi tạo lại, đi tiếp bước khởi tạo.
- [ ] T037 [P] [US1] Unit test `TEST/feature/auth/ui/register/RegisterViewModelTest.kt` (Turbine):
  - `canSubmit == false` khi chưa đánh dấu điều khoản (US1-2);
  - lỗi từng ô hiện khi rời ô hoặc bấm "Đăng ký", **không gửi yêu cầu** (US1-6, FR-020);
  - `passwordChecks(len8, letterDigit)` cập nhật khi gõ;
  - bấm "Đăng ký" 2 lần → chỉ 1 lời gọi (FR-022);
  - hết 15 s → sự kiện snackbar mạng; `name`, `email` giữ trong `SavedStateHandle`, mật khẩu không giữ (FR-021);
  - `EmailInUse` → `emailError` "Email đã được sử dụng" kèm hai link (US1-3);
  - thành công → không tự điều hướng, chờ `SessionState` đổi.
- [ ] T038 [P] [US1] Unit test `TEST/feature/auth/ui/setup/AccountSetupViewModelTest.kt`: có `PendingSignup` → tự gọi `CompleteSignupUseCase`, `Loading`; lỗi → `Failed(AppError)`; "Thử lại" gọi lại; "Đăng xuất" → `signOut()`
- [ ] T039 [P] [US1] Unit test `TEST/core/ui/VerifyEmailBannerViewModelTest.kt`:
  - `visible` khi `SignedIn`, có provider `password`, `!emailVerified`; ẩn với tài khoản Google hoặc chỉ có số điện thoại (FR-009);
  - `onResume` → `reload()`; đã xác minh → `getIdToken(true)` rồi ẩn;
  - "Gửi lại email" → `resendInSeconds` đếm từ 60; mốc lấy từ `last_verification_email_at` nên tạo lại ViewModel vẫn còn đếm.

### Implementation cho User Story 1

- [ ] T040 [US1] Cài `AUTH/domain/usecase/RegisterWithEmailUseCase.kt`, `ResendVerificationEmailUseCase.kt`; thêm `createUser`, `sendEmailVerification`, `reload` vào `FirebaseAuthDataSource` và `AuthRepositoryImpl`. Cho T036 xanh
- [ ] T041 [US1] Cài `AUTH/ui/register/RegisterViewModel.kt` (`StateFlow<RegisterUiState>` đúng [contracts/ui-screens.md §3](contracts/ui-screens.md#3-viewmodel--stateflowuistate), sự kiện qua `Channel`). Cho T037 xanh
- [ ] T042 [US1] Tạo `AUTH/ui/welcome/WelcomeFragment.kt` + `RES/layout/fragment_auth_welcome.xml` theo wireframe `buoc1_man-chao` (nút "Đăng ký", "Đăng nhập", **"Xem sân trước"** (FR-025: ghi `welcome_seen = true` rồi vào `nav_player` chế độ khách, `popUpTo(nav_main, inclusive = true)`); bấm "Đăng ký"/"Đăng nhập" cũng ghi `welcome_seen = true`; nút Google và "Dùng số điện thoại" nối ở US4, US5)
- [ ] T043 [US1] Tạo `AUTH/ui/register/RegisterFragment.kt` + `RES/layout/fragment_auth_register.xml` theo wireframe `buoc3_dang-ky-email`: dòng "Mọi tài khoản bắt đầu là người chơi…" (FR-002), **không** có lựa chọn vai trò (US1-4), hai dấu tích mật khẩu, checkbox điều khoản có link mở `LegalDocumentFragment`, `TextInputLayout.setError`, ô mật khẩu `android:saveEnabled="false"`, nút có `CircularProgressIndicator` khi `isSubmitting`, `contentDescription`/`hint`, vùng chạm 48dp, form tối đa `auth_form_max_width`
- [ ] T044 [P] [US1] Tạo `AUTH/ui/legal/LegalDocumentFragment.kt` + `RES/layout/fragment_auth_legal.xml` (arg `doc: TERMS | PRIVACY`, đọc `res/raw/*_vi.html`, hiển thị bằng `HtmlCompat`) (**011:** destination `legal` trong `nav_auth.xml` có thêm `<deepLink app:uri="smashnow://legal/{doc}"/>` để `feature/account` mở được mà không import class trong `feature/auth/ui`, constitution I)
- [ ] T045 [US1] Cài `AUTH/ui/setup/AccountSetupViewModel.kt` + `AccountSetupFragment.kt` + `RES/layout/fragment_auth_account_setup.xml` ("Đang chuẩn bị tài khoản…"; lỗi có "Thử lại" và "Đăng xuất"). Cho T038 xanh
- [ ] T046 [US1] Cài `CORE/ui/VerifyEmailBannerViewModel.kt`, `CORE/ui/VerifyEmailBannerView.kt` + `RES/layout/view_verify_email_banner.xml`; nhúng vào `PlayerHomePlaceholderFragment`. Cho T039 xanh
- [ ] T047 [US1] Thêm `welcome`, `register`, `legal`, `accountSetup` vào `RES/navigation/nav_auth.xml` (Safe Args); `MainActivity` điều hướng `Initializing` / `InitFailed` → `accountSetup`

**Checkpoint**: US1 chạy độc lập; quickstart M1, M2, M3, M4 đạt.

---

## Phase 4: User Story 2 – Đăng nhập bằng email và mật khẩu (Priority: P1) 🎯 MVP

**Goal**: người đã có tài khoản đăng nhập, vào đúng giao diện theo vai trò, giữ phiên giữa các lần mở app; tài khoản bị khóa không vào được.

**Independent Test**: tài khoản có sẵn đăng nhập đúng → màn hình chính; tắt hẳn app mở lại → vẫn ở màn hình chính; sai thông tin → "Email hoặc mật khẩu không đúng"; tài khoản bị khóa → thông báo khóa (quickstart M5, M6, M18–M23).

**Phụ thuộc**: khóa tài khoản dùng `set-account-status` của **D**. Nếu D chưa xong: bật cờ `disabled` trong Firebase Console và sửa `accountStatus = LOCKED` bằng tay để chạy M18–M19.

### Tests cho User Story 2 ⚠️ viết trước, phải fail

- [x] T048 [P] [US2] Unit test `TEST/feature/auth/domain/usecase/SignInWithEmailUseCaseTest.kt`: email `trim().lowercase()`; thành công → `sessionManager.refresh(true)`; sai email và sai mật khẩu → cùng `InvalidCredentials`; `ERROR_USER_DISABLED` → `AccountLocked`
- [x] T049 [P] [US2] Unit test `TEST/feature/auth/ui/login/LoginViewModelTest.kt`:
  - bấm 2 lần → 1 lời gọi;
  - `InvalidCredentials` → `formError` chung, không chỉ ra ô nào (US2-4);
  - `AccountLocked` → sự kiện hiện hộp thoại khóa (US2-6); arg `lockedNotice = true` → hiện ngay khi mở;
  - có `returnTo` → sự kiện `navigate(Uri)` thay vì về màn hình chính;
  - hết 15 s → snackbar mạng, giữ `email`, xóa mật khẩu.

### Implementation cho User Story 2

- [x] T050 [US2] Cài `AUTH/domain/usecase/SignInWithEmailUseCase.kt`; thêm `signInWithEmail` vào `FirebaseAuthDataSource`, `AuthRepositoryImpl`. Cho T048 xanh
- [x] T051 [US2] Cài `AUTH/ui/login/LoginViewModel.kt`. Cho T049 xanh
- [x] T052 [US2] Tạo `AUTH/ui/login/LoginFragment.kt` + `RES/layout/fragment_auth_login.xml` theo wireframe `buoc2_dang-nhap` (cả chế độ tối): Safe Args `returnTo: String?`, `lockedNotice: Boolean = false`; link "Quên mật khẩu" (nối ở US3); `MaterialAlertDialog` "Tài khoản của bạn đã bị khóa" kèm `support_email`; thêm `login` vào `nav_auth.xml`, nối từ `WelcomeFragment` và link "Đăng nhập" ở lỗi `EmailInUse` của `RegisterFragment`
- [x] T053 [US2] Hoàn thiện `MainActivity`: `SignedOut(LOCKED)` → `nav_player` chế độ khách rồi `LoginFragment(lockedNotice = true)` chồng lên (R15); đăng nhập xong với `PLAYER` khi dưới `nav_auth` có `nav_player` → `popBackStack(R.id.nav_auth, inclusive = true)` rồi `navigate(returnTo)` nếu có (FR-026); `OWNER`/`ADMIN` hoặc `nav_auth` là gốc → `popUpTo(nav_main, inclusive = true)`; `SignedIn` với `OWNER` / `ADMIN` → `nav_owner` / `nav_admin` placeholder; vai trò đổi khi đang mở app → điều hướng lại (Edge Case vai trò đổi); Back ở màn hình chính → thoát app (Edge Case cuối)

**Checkpoint**: US1 + US2 = MVP; quickstart M5, M6, M18–M23 đạt (M21 dùng `scripts/admin/grant-role.ts`).

---

## Phase 5: User Story 3 – Quên và đặt lại mật khẩu (Priority: P2)

**Goal**: người quên mật khẩu nhận email đặt lại, đặt mật khẩu mới trên trang của Firebase.

**Independent Test**: gửi yêu cầu cho email có và không có tài khoản → cùng thông báo; mở link, đặt mật khẩu mới; đăng nhập được bằng mật khẩu mới, không được bằng mật khẩu cũ (quickstart M16, M17).

### Tests cho User Story 3 ⚠️ viết trước, phải fail

- [ ] T054 [P] [US3] Unit test `TEST/feature/auth/domain/usecase/SendPasswordResetUseCaseTest.kt`: email `trim().lowercase()`; email không tồn tại → vẫn thành công (FR-018); `TooManyAttempts` → lỗi tương ứng; ghi `last_password_reset_at` khi gửi
- [ ] T055 [P] [US3] Unit test `TEST/feature/auth/ui/forgot/ForgotPasswordViewModelTest.kt`: arg `email` điền sẵn; email sai định dạng → lỗi dưới ô, không gửi; gửi xong `sent = true` với cùng một thông báo; `resendInSeconds` đếm từ 60 (US3-3), lấy mốc từ DataStore

### Implementation cho User Story 3

- [ ] T056 [US3] Cài `AUTH/domain/usecase/SendPasswordResetUseCase.kt`; thêm `sendPasswordResetEmail` vào data source. Cho T054 xanh
- [ ] T057 [US3] Cài `AUTH/ui/forgot/ForgotPasswordViewModel.kt` + `ForgotPasswordFragment.kt` + `RES/layout/fragment_auth_forgot_password.xml` theo wireframe `buoc7_quen-mat-khau` (Safe Args `email: String?`); thêm vào `nav_auth.xml`, nối từ `LoginFragment` và link "quên mật khẩu" ở lỗi `EmailInUse`. Cho T055 xanh

**Checkpoint**: quickstart M16, M17 đạt.

---

## Phase 6: User Story 4 – Đăng nhập bằng Google (Priority: P2)

**Goal**: đăng nhập bằng Google qua Credential Manager; lần đầu phải qua bước Hoàn tất tài khoản; email trùng thì về cùng một tài khoản.

**Independent Test**: tài khoản Google mới → bước Hoàn tất tài khoản → màn hình chính người chơi; lần hai vào thẳng (tối đa 3 lần chạm); hủy bottom sheet → ở lại, không báo lỗi (quickstart M7–M11).

**Phụ thuộc**: SHA-1 debug của từng thành viên đã thêm vào Firebase (T005).

### Tests cho User Story 4 ⚠️ viết trước, phải fail

- [ ] T058 [P] [US4] Unit test `TEST/feature/auth/domain/usecase/SignInWithGoogleUseCaseTest.kt` (fake `GoogleCredentialDataSource`): `GetCredentialCancellationException` → `GoogleCancelled`; `NoCredentialException` → `NoGoogleAccount`; collision → `NeedsLinking(email, pendingCredential)`; thành công → `sessionManager.refresh(true)`
- [ ] T059 [P] [US4] Unit test `TEST/feature/auth/domain/usecase/LinkGoogleToPasswordUseCaseTest.kt`: `signInWithEmail` rồi `linkWithCredential(pendingCredential)`; sai mật khẩu → `InvalidCredentials`, không liên kết; thành công → `refresh(true)`
- [ ] T060 [P] [US4] Unit test `TEST/feature/auth/ui/consent/TermsConsentViewModelTest.kt`:
  - `prefill` lấy từ `SessionState.NeedsConsent` (provider, email/số, họ tên sửa được);
  - nút "Bắt đầu dùng SmashNow" tắt khi chưa đánh dấu; `NameRule` lỗi → không gửi;
  - bấm 2 lần → 1 lời gọi `CompleteSignupUseCase(termsAccepted = true)`;
  - "Dùng tài khoản khác" → `signOut()`;
  - hết 15 s → snackbar mạng, giữ họ tên.
- [ ] T061 [P] [US4] Bổ sung `LoginViewModelTest`: `GoogleCancelled` → không có `formError` (US4-4); `NeedsLinking` → sự kiện mở `LinkAccountDialog(email)`

### Implementation cho User Story 4

- [ ] T062 [US4] Tạo `AUTH/data/GoogleCredentialDataSource.kt` theo [R12](research.md#r12-google-sign-in-bằng-credential-manager) (`GetGoogleIdOption`, `setFilterByAuthorizedAccounts(false)`, `serverClientId = default_web_client_id`); thêm `signInWithCredential`, `linkWithCredential` vào `FirebaseAuthDataSource`
- [ ] T063 [US4] Cài `AUTH/domain/usecase/SignInWithGoogleUseCase.kt`, `LinkGoogleToPasswordUseCase.kt`. Cho T058, T059 xanh
- [ ] T064 [US4] Cài `AUTH/ui/consent/TermsConsentViewModel.kt` + `TermsConsentFragment.kt` + `RES/layout/fragment_auth_terms_consent.xml` theo wireframe `buoc6_hoan-tat-tai-khoan` (thẻ "Đã xác thực bằng Google / số điện thoại", họ tên điền sẵn, tóm tắt điều khoản, link `LegalDocumentFragment`). Cho T060 xanh
- [ ] T065 [US4] Tạo `AUTH/ui/link/LinkAccountDialog.kt` + `RES/layout/dialog_auth_link_account.xml` (arg `email`, ô mật khẩu, nút "Liên kết"); thêm nút "Tiếp tục với Google" vào `WelcomeFragment`, `LoginFragment`, nối `LoginViewModel` với `SignInWithGoogleUseCase`. Cho T061 xanh
- [ ] T066 [US4] Thêm `termsConsent`, `linkAccount` vào `nav_auth.xml`; `MainActivity` điều hướng `NeedsConsent` → `termsConsent` (đóng app ở bước đồng ý thì mở lại vẫn về đây – Edge Case)

**Checkpoint**: quickstart M7, M8, M11 đạt (M9, M10 chạy ở T076).

---

## Phase 7: User Story 5 – Đăng ký và đăng nhập bằng số điện thoại với OTP (Priority: P3)

**Goal**: đăng nhập bằng số thử nghiệm và mã 6 số; số mới qua bước Hoàn tất tài khoản.

**Independent Test**: với một số thử nghiệm, tạo tài khoản mới, đăng xuất, đăng nhập lại bằng cùng số; nhập sai 5 lần hoặc quá 5 phút thì phải gửi lại mã (quickstart M12–M15).

**Phụ thuộc**: `TermsConsentFragment` của US4 (T064, T066) cho số mới.

### Tests cho User Story 5 ⚠️ viết trước, phải fail

- [ ] T067 [P] [US5] Unit test `TEST/feature/auth/domain/model/OtpSessionTest.kt`: `isUsable == false` khi `now >= sentAtElapsed + 300_000` hoặc `wrongCount >= 5`; `remainingAttempts = 5 - wrongCount` (FR-016)
- [ ] T068 [P] [US5] Unit test `TEST/feature/auth/domain/usecase/SendOtpUseCaseTest.kt` và `VerifyOtpUseCaseTest.kt`: số chuẩn hóa E.164 trước khi gửi (FR-017); `PhoneNotSupported`, `TooManyAttempts` đúng ánh xạ; mã sai → `InvalidCode` và `wrongCount + 1`; phiên không còn dùng được → `CodeExpired` mà **không gọi Firebase**; `ERROR_SESSION_EXPIRED` → `CodeExpired`; đúng mã → `refresh(true)`
- [ ] T069 [P] [US5] Unit test `TEST/feature/auth/ui/phone/PhoneLoginViewModelTest.kt` và `TEST/feature/auth/ui/otp/OtpViewModelTest.kt`:
  - số sai định dạng → lỗi dưới ô, không gửi; bấm "Gửi mã" 2 lần → 1 lời gọi;
  - `state` chuyển `Editing → Verifying → WrongCode` (kèm `remainingAttempts`) `→ Locked5` sau 5 lần sai; quá 5 phút → `Expired`;
  - "Gửi lại mã" chỉ bật sau 60 s; gửi lại thì `wrongCount = 0`;
  - số hiển thị dạng che bớt `+84 9•• ••• 123`;
  - `OtpSession` giữ trong `SavedStateHandle` khi xoay màn hình, mã đã nhập thì không.

### Implementation cho User Story 5

- [ ] T070 [US5] Tạo `AUTH/domain/model/OtpSession.kt` (`phoneE164`, `verificationId`, `resendToken`, `sentAtElapsed`, `wrongCount`; `isUsable`), `AUTH/domain/model/PhoneNumber.kt`; thêm `verifyPhoneNumber` (`PhoneAuthOptions`, timeout 60 s, gắn Activity, `callbackFlow`) và `signInWithPhoneCredential` vào `FirebaseAuthDataSource`. Cho T067 xanh
- [ ] T071 [US5] Cài `AUTH/domain/usecase/SendOtpUseCase.kt`, `VerifyOtpUseCase.kt` theo [R8](research.md#r8-otp-số-điện-thoại-fr-016-fr-017-analyze-f4). Cho T068 xanh
- [ ] T072 [US5] Cài `AUTH/ui/phone/PhoneLoginViewModel.kt` + `PhoneLoginFragment.kt` + `RES/layout/fragment_auth_phone.xml` theo wireframe `buoc4_dang-nhap-so-dien-thoai`; nối nút "Dùng số điện thoại" ở `WelcomeFragment`, `LoginFragment`
- [ ] T073 [US5] Cài `AUTH/ui/otp/OtpViewModel.kt` + `OtpVerifyFragment.kt` + `RES/layout/fragment_auth_otp.xml` theo wireframe `buoc5_nhap-otp-sai-ma` (6 ô số có `contentDescription`, đếm ngược "Gửi lại mã", thông báo "Mã không đúng" kèm số lần còn lại); Safe Args `phoneE164: String`; thêm `phone`, `otp` vào `nav_auth.xml`. Cho T069 xanh

**Checkpoint**: cả 5 user story chạy độc lập; quickstart M12–M15 đạt.

---

## Phase 8: Polish & Cross-Cutting

- [ ] T074 [P] Cập nhật `docs/design/database/README.md` theo [danh sách cuối data-model.md](data-model.md#danh-sách-thay-đổi-cần-đưa-vào-docsdesigndatabasereadmemd):
  - mục 4.1: `level` thành `String?`, `termsAcceptedAt` thêm 🔒, câu về `email`/`phone` lấy từ ID token, đoạn "Custom claims" (gộp claim, ghi claim **trước** `users.role`);
  - mục 7 dòng `users`: người chơi "R của mình, U của mình trừ 🔒; không C/D", admin "R; khóa/mở khóa qua `set-account-status`";
  - mục 8a: mô tả mới của `on-signup`, thêm `set-account-status` (D), `_shared/auth.ts`, `_shared/identity.ts` (A);
  - mục 1: "dữ liệu cá nhân `users` chỉ chủ tài khoản và admin đọc; tính năng khác dùng snapshot".
- [ ] T075 [P] Espresso (nên có) `ATEST/feature/auth/RegisterAndLoginFlowTest.kt` trên Auth/Firestore Emulator: đăng ký email → màn hình chính; đăng xuất → đăng nhập lại; Back ở màn hình chính thoát app
- [ ] T076 Chạy kịch bản thủ công [quickstart §4](quickstart.md#4-kịch-bản-kiểm-thử-thủ-công-trên-máy-với-project-thật-tài-khoản-thử) trên project thật, **bắt buộc ghi kết quả thật** cho:
  - **M9**: Gmail đã xác minh + Google → cùng `uid`, 2 provider;
  - **M10**: Gmail chưa xác minh + Google → cùng `uid`, provider password bị gỡ (so với dự đoán ở R7);
  - **M17**: đặt mật khẩu `abcdefgh` trên trang của Firebase → bị từ chối;
  - **M20**: tài khoản bị khóa nhập sai mật khẩu → Firebase trả `INVALID_LOGIN_CREDENTIALS` hay `USER_DISABLED` (có lộ email bị khóa không).

  Kết quả khác dự đoán thì sửa `research.md` (R7, R9) và báo nhóm. Ghi kết quả vào log AI
- [ ] T077 [P] Kiểm tra FR-023 / M24 trên cả 10 màn hình: chế độ tối, `w600dp` (form ≤ 480dp, căn giữa; thêm `RES/layout-w600dp/` nếu cần), TalkBack đọc được nhãn, vùng chạm ≥ 48dp; M25 mất mạng ở từng nút không crash (SC-007)
- [ ] T078 [P] Cập nhật `docs/features/01-account/README.md` (link spec, plan, tasks, hướng dẫn cấu hình Firebase) và cột *Spec* nhóm 01 trong `docs/features/README.md`; lưu ảnh/video M1, M7, M12, M18 vào `docs/features/01-account/screenshots/`
- [ ] T079 Chạy `./gradlew lint testDebugUnitTest assembleDebug`, `deno test -A on-signup/ _shared/` (với Emulator), `npm test` trong `firestore-tests/`; không còn cảnh báo Lint mức Error

---

## Dependencies & Execution Order

### Phụ thuộc giữa các phase

- **Setup (Phase 1)**: không phụ thuộc. T005 (Firebase Console) làm song song.
- **2A `chore(core)`**: sau Setup. **Chặn mọi user story và các nhóm khác** → merge sớm nhất có thể.
- **2B server + Rules**: không phụ thuộc 2A, làm song song. Cần `_shared/firestore.ts` (T019).
- **2C domain auth**: sau 2A (dùng `AppError`, `SessionManager`, `SessionPrefs`); T034 cần `on-signup` của 2B để chạy thật (unit test dùng fake).
- **US1 (Phase 3)**: sau Phase 2.
- **US2 (Phase 4)**: sau Phase 2; độc lập với US1 về code, nhưng cần tài khoản có sẵn (tạo bằng US1 hoặc Auth Emulator + `on-signup`).
- **US3 (Phase 5)**: cần `LoginFragment` (T052) để có lối vào.
- **US4 (Phase 6)**: cần `LoginFragment` (T052), `WelcomeFragment` (T042).
- **US5 (Phase 7)**: cần `TermsConsentFragment` của US4 (T064, T066).
- **Polish (Phase 8)**: sau các story cần có trong bản demo.

### Phụ thuộc ngoài nhóm (bảng "Phối hợp với nhóm" trong [plan.md](plan.md#phối-hợp-với-nhóm))

| Task | Cần | Nếu chưa có |
|---|---|---|
| T019, T023 | **B**: `_shared/firestore.ts` có `get`, `commit` với `exists=false`, xuất hàm lấy access token service account | A viết bản tối thiểu 3 hàm, đánh dấu `TODO(B)`, báo B thay bằng bản đầy đủ |
| T021 | **C**: dùng `_shared/auth.ts` của A (070 T020, 080 T007), không tự viết | Nếu C đã viết trước: gộp vào bản theo contract §4 của 010, giữ chữ ký `requireUser`, `errorResponse` |
| T027 | **Cả nhóm**: chuyển quyền ghi sang `isInitialized()` | Collection của nhóm khác vẫn chạy với `isSignedIn()`; báo trong PR, không chặn merge |
| T053, M18, M19 | **D**: Edge Function `set-account-status` (ghi `accountStatus` + `setUserDisabled`) | Bật cờ `disabled` trong Firebase Console và sửa `accountStatus` bằng tay để kiểm thử |
| T053, M21 | **D**: `review-owner-application` / thu hồi quyền gọi **`mergeClaims` trước** khi ghi `users.role` | Dùng `scripts/admin/grant-role.ts` (T028) để giả lập cấp/thu hồi `OWNER` |
| T016 | **D**: thay `OwnerHomePlaceholderFragment`, `AdminHomePlaceholderFragment` bằng màn hình thật | Placeholder giữ nguyên, điều hướng vẫn đúng |
| T074 | **D, C**: đồng ý thay đổi đặc tả CSDL (`level` nullable, quyền đọc `users`) | Chốt ở họp 16/10; chưa chốt thì hoãn T074, không chặn code |

### Trong mỗi user story

Test (fail) → UseCase / data source → ViewModel (test xanh) → layout / Fragment → nav graph.

### Cơ hội làm song song

- Phase 1: T003, T005 song song với T001–T002.
- 2A: T006, T007 cùng lúc; T008–T012, T015, T018 khác file nhau.
- 2B làm song song toàn bộ với 2A; trong 2B: T020, T022, T024, T026, T028 cùng lúc.
- 2C: T029–T031, T033, T035 song song.
- US1: T036–T039 song song; T044 song song với T040–T043.
- US3, US4 làm song song được sau khi có `LoginFragment` (T052).

## Parallel Example: Phase 2B

```bash
# Viết test trước, cùng lúc:
Task: "T020 Test _shared/auth.ts trong supabase/functions/_shared/auth.test.ts"
Task: "T022 Test _shared/identity.ts trong supabase/functions/_shared/identity.test.ts"
Task: "T024 Test S1–S12 + gọi liên tiếp/đồng thời trong supabase/functions/on-signup/handler.test.ts"
Task: "T026 Test Rules U1–U18 trong firestore-tests/test/users.test.ts"

# Sau đó: T021 → T023 → T025 (on-signup dùng cả hai), song song với T027
```

## Parallel Example: User Story 1

```bash
Task: "T036 RegisterWithEmailUseCaseTest"
Task: "T037 RegisterViewModelTest"
Task: "T038 AccountSetupViewModelTest"
Task: "T039 VerifyEmailBannerViewModelTest"
# Rồi T040–T043 tuần tự, T044 song song
```

## Implementation Strategy

### MVP (US1 + US2)

1. Phase 1 + **2A → mở PR `chore(core)` ngay** để B, C, D dùng `SessionManager`, `IdTokenProvider`, `SupabaseClient`.
2. 2B song song: **T024 (gọi đồng thời) xanh 10/10 lần** trước khi merge.
3. 2C → US1 → US2.
4. **Dừng và kiểm tra**: quickstart M1–M6, M18–M23. Đây là phần demo cho mốc nhóm 01.

### Tăng dần

5. US3 (quên mật khẩu) và US4 (Google) song song.
6. US5 (OTP) sau US4.
7. Polish, đặc biệt T076 (M9, M10, M17, M20) trước khi đóng băng tính năng.

### Ghi chú

- Mỗi task hoặc nhóm task liên quan: một commit Conventional Commits, scope `core` cho 2A, `auth` cho phần còn lại (ví dụ `feat(auth): on-signup idempotent`).
- Ghi log AI vào `docs/ai-log/AI_log_1.md` cho mỗi lần dùng AI.
- Không commit `app/google-services.json`, `local.properties`, `supabase/.env`, số điện thoại thử nghiệm.
