---

description: "Danh sách task triển khai spec 011 – Hồ sơ và quản lý tài khoản"
---

# Tasks: Hồ sơ và quản lý tài khoản

**Input**: tài liệu thiết kế trong `specs/011-account-profile/`: [plan.md](plan.md), [spec.md](spec.md), [research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/), [quickstart.md](quickstart.md)

**Tests**: Có. Constitution nguyên tắc IV bắt buộc test cho:
- Security Rules `users/{uid}`, `districts`, `accountDeletions` (P1–P18, D1–D4, A1) trên Firebase Emulator; bộ U1–U18 của 010 vẫn phải xanh;
- chính sách Storage bucket `avatars` (ST1–ST8) bằng pgTAP trên Supabase local;
- Edge Functions `delete-account`, `delete-account-sweeper`, `sync-account-email` (X1–X16, E1–E3) trên Auth/Firestore Emulator; X7 và X10 chạy **10 lần liên tiếp** đều xanh;
- rule kiểm tra dữ liệu nhập, `OwnerSectionResolver`, `AppModeController`, `SessionManager.signOut()`, UseCase và ViewModel (Turbine).

Viết test trước, chạy thấy **fail**, rồi mới cài đặt.

**Spec này xây trên 010**: task 011 **sửa** code của 010 (`core/session`, `MainActivity`, `_shared/auth.ts`, `_shared/identity.ts`, `firestore.rules`) chỉ bằng cách **thêm**, không đổi chữ ký đã công bố cho nhóm. Mọi task phụ thuộc code 010 đã merge (PR `chore(core)` của [tasks 010](../010-account-auth/tasks.md) Phase 1–2).

**Organization**: nhóm theo user story (US1–US7) để mỗi story làm và kiểm thử độc lập được. Phase 2A tách thành PR riêng để nhóm review và merge sớm.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: làm song song được (file khác nhau, không phụ thuộc task chưa xong)
- **[Story]**: user story của spec (US1…US7)

## Quy ước đường dẫn

| Viết tắt | Đường dẫn thật |
|---|---|
| `APP/` | `app/src/main/java/com/example/booking_app/` |
| `CORE/` | `app/src/main/java/com/example/booking_app/core/` |
| `ACC/` | `app/src/main/java/com/example/booking_app/feature/account/` |
| `RES/` | `app/src/main/res/` |
| `TEST/` | `app/src/test/java/com/example/booking_app/` |
| `ATEST/` | `app/src/androidTest/java/com/example/booking_app/` |
| `FN/` | `supabase/functions/` |

---

## Phase 1: Setup (thư viện, cấu hình, dữ liệu mẫu)

**Purpose**: thư viện và tài nguyên chung của spec 011.

- [x] T001 Thêm thư viện vào `gradle/libs.versions.toml` và `app/build.gradle.kts`:
  - `com.vanniktech:android-image-cropper` (bản ổn định mới nhất, ít nhất 2 tuần tuổi; ghi lý do theo [plan Complexity Tracking](plan.md#complexity-tracking));
  - `io.github.jan-tennert.supabase:storage-kt` nếu PR `chore(core)` chưa có (cùng BoM với `functions-kt`);
  - kiểm tra `./gradlew assembleDebug` qua với AGP 9.4; thư viện cắt ảnh không chạy được thì dừng lại báo nhóm, không tự đổi thư viện khác.
- [x] T002 [P] Khai báo trong `app/src/main/AndroidManifest.xml`: quyền `android.permission.CAMERA` (không khai báo `READ_MEDIA_IMAGES` vì Photo Picker không cần quyền, [R4](research.md#r4-chọn-cắt-và-nén-ảnh)), `<uses-feature android:name="android.hardware.camera" android:required="false"/>`, `FileProvider` authority `${applicationId}.fileprovider` trỏ `RES/xml/file_paths.xml` (`<cache-path name="avatar" path="avatar/"/>`); khai báo `CropImageActivity` nếu thư viện cắt ảnh yêu cầu
- [x] T003 [P] Tạo `RES/values/strings_account.xml` (tiền tố `account_`) với mọi chuỗi trong [contracts/ui-screens.md §2](contracts/ui-screens.md#2-chi-tiết-theo-wireframe) và [contracts/edge-functions-account.md §1.4](contracts/edge-functions-account.md#14-mã-lỗi), gồm: nhãn trình độ `Mới chơi`, `Trung bình`, `Khá/Nâng cao`; `Ví giả lập · không phải tiền thật`; `Bỏ các thay đổi chưa lưu?`; `Chỉ hỗ trợ ảnh JPG, PNG, WebP`; `Mật khẩu hiện tại không đúng`; `Mật khẩu mới phải khác mật khẩu hiện tại`; thông báo chung đổi email `Nếu email này chưa được dùng cho tài khoản khác, bạn sẽ nhận được thư xác minh. Email đăng nhập chỉ đổi sau khi bấm link.`; `Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại`; `Hãy chọn đúng tài khoản Google đang đăng nhập`; `Tài khoản đã được xóa`; `Quyền chủ sân đã bị thu hồi`; `SmashNow · Phiên bản học tập`; các chuỗi lỗi `account_error_district_max`, `account_error_same_password`, `account_error_same_email`, `account_error_delete_confirm`, `account_error_image_type`, `account_error_image_size`
- [x] T004 [P] *(Sửa khi implement 2026-10-25: không tạo `districts.ts` vì seed chung `scripts/seed/seed.mjs` của Duy đã nạp 22 quận cùng ID; seed ghi `cityName` thay cho `city`, app không đọc trường này nên data-model §3 đổi theo seed. `q-cu` chỉ tạo trong dữ liệu test. Đã thêm 3 index.)* ~~Tạo `scripts/seed/districts.ts` (service account, chạy được với Emulator qua `FIRESTORE_EMULATOR_HOST`): 22 quận/huyện TP.HCM với ID tất định chữ thường không dấu (`q1`, `q3`, `q10`, `thu-duc`, `binh-thanh`…), trường đúng [data-model §3](data-model.md#3-firestore-districtsid-a) (`name`, `city = "HCM"`, `sortOrder`, `isActive`, `createdAt`, `updatedAt`), thêm 1 bản ghi `q-cu` có `isActive = false` để test; ghi đè idempotent. Thêm vào `firestore.indexes.json`: `districts` (`isActive` ASC, `sortOrder` ASC), `ownerApplications` (`uid` ASC, `submittedAt` DESC), `accountDeletions` (`state` ASC, `requestedAt` ASC)~~ → chỉ phần index còn hiệu lực

- [x] T005 [P] Chuẩn bị môi trường chạy tay **cục bộ** cho quickstart §5 (constitution IV, không chạy trên `mobile-22e6c` hay Supabase `smashnow` thật): `local.properties` có `USE_EMULATOR=true`, `SUPABASE_URL=http://10.0.2.2:54321`; Firebase Emulator (Auth, Firestore, project `demo-smashnow`); `npx supabase start` + `npx supabase functions serve` với `supabase/functions/.env.local` (không commit). Kiểm tra Supabase local có nhận ID token do Auth Emulator cấp không (`supabase/config.toml` đang để `[auth.third_party.firebase] project_id = "mobile-22e6c"`, token Emulator không ký nên dự kiến bị từ chối): thử `upload` một ảnh vào `avatars` bằng token Emulator. Bị từ chối thì ghi lại kết quả, **dừng các kịch bản Storage (Q5, Q8, Q9) và báo nhóm chốt cách làm**; không tự sửa `config.toml` đã commit và không chuyển sang project thật
  - **Kết quả 2026-10-25:** `supabase start` (CLI 2.121.0) và Firebase Emulator (`demo-smashnow`, Auth 9099, Firestore 8080) chạy được. ID token do Auth Emulator cấp có header `{"alg":"none"}`; gọi `GET /storage/v1/bucket` và `POST /storage/v1/object/avatars/...` trên Supabase local đều bị từ chối: `400 {"statusCode":"403","message":"\"alg\" (Algorithm) Header Parameter value not allowed"}`. **Dừng Q5, Q8, Q9 (Storage trên máy ảo) và báo nhóm chốt cách làm**; chính sách bucket vẫn kiểm bằng pgTAP ST1–ST8 (T032), không cần token Firebase. Không sửa `config.toml`, không dùng project thật

**Checkpoint**: `./gradlew assembleDebug` qua; `node scripts/seed/seed.mjs --emulator` tạo 22 document `districts`; đã có kết quả kiểm tra token của T005.

---

## Phase 2: Foundational (chặn mọi user story)

### Phase 2A: PR `chore(core): mở rộng session cho 011`

**Purpose**: phần mở rộng `core/` dùng chung cả nhóm theo [research R1](research.md#r1-phần-mở-rộng-core-so-với-010) và [contracts/ui-screens.md §4](contracts/ui-screens.md#4-mở-rộng-coresession-cho-cả-nhóm). Đây là **sửa trên `core/session` của 010**: chỉ thêm trường, thêm giá trị enum, thêm interface và overload; **không đổi chữ ký cũ** (`sessionState`, `refresh(forceTokenRefresh)`, `signOut()`, `currentUid()`, `currentAppRole()`, `observeMyProfile()`). Tách PR riêng, mở ngay khi xanh để B, C, D review.

**Tests (viết trước, phải fail)**

- [x] T006 [P] Unit test `TEST/core/session/DefaultAppModeControllerTest.kt` (fake `SessionManager`, `StandardTestDispatcher`, Turbine):
  - `SignedIn(appRole = OWNER)` → `mode = OWNER` (mặc định, FR-011); `PLAYER` / `ADMIN` → luôn `PLAYER`; `SignedOut` rồi đăng nhập lại `OWNER` → về `OWNER`;
  - `switchTo(PLAYER)` → đổi ngay, **không** gọi `refresh` (SC-003);
  - `switchTo(OWNER)` → gọi `refresh(forceTokenRefresh = true)`; claim vẫn `OWNER` → `Result.success(OWNER)`;
  - `switchTo(OWNER)` khi claim mới là `PLAYER` → ở lại `PLAYER`, `Result.failure(OwnerRevokedException)`;
  - `refresh` ném `AppError.Network` hoặc quá **3_000 ms** (thời gian ảo) → dùng `currentAppRole()` của token đang có: còn `OWNER` thì chuyển (R7 bước 4);
  - người không phải `OWNER` gọi `switchTo(OWNER)` → `Result.failure`, `mode` không đổi.
- [x] T007 [P] Unit test `TEST/core/session/FirebaseSessionManagerSignOutTest.kt` (fake `SessionAuthSource`, fake `SessionPrefs`, 2 `SignOutHook` giả ghi thứ tự gọi):
  - `signOut()` gọi **mọi** hook với `uid` hiện tại **trước** `SessionAuthSource.signOut()`;
  - hook ném lỗi → hook còn lại vẫn chạy, vẫn đăng xuất; hook chạy quá **5_000 ms** (thời gian ảo) bị hủy, vẫn đăng xuất;
  - `signOut(SignOutReason.ACCOUNT_DELETED)` **không** gọi hook (token đã chết), phát `SignedOut(ACCOUNT_DELETED)`;
  - `AccountDeletedBus` phát sự kiện → `SignedOut(ACCOUNT_DELETED)`, không gọi hook;
  - `getIdToken` / `reload` ném `FirebaseAuthInvalidUserException` mã `ERROR_USER_TOKEN_EXPIRED` → `SignedOut(SESSION_REVOKED)` (không gọi hook; khác `LOCKED`, R9);
  - `signOut()` vẫn xóa `pending_signup_*`, `last_verification_email_at` như test 010 (010-T007 vẫn xanh).
- [x] T008 [P] Unit test `TEST/core/media/StorageUrlsTest.kt`: `publicUrl("avatars", "u1/avatar_1760500000000.jpg")` = `{SUPABASE_URL}/storage/v1/object/public/avatars/u1/avatar_1760500000000.jpg`; `SUPABASE_URL` có hoặc không có `/` cuối đều ra một `/`; `path` có ký tự đặc biệt được mã hóa từng đoạn, giữ `/`
- [x] T009 [P] Unit test `TEST/core/session/RootGraphTest.kt` cho hàm thuần `rootGraphFor(appRole, mode)`: `OWNER + OWNER` → `nav_owner`; `OWNER + PLAYER` → `nav_player`; `PLAYER + *` → `nav_player`; `ADMIN + *` → `nav_admin`
- [x] T010 [P] Unit test `TEST/core/network/FunctionErrorsTest.kt` (bổ sung file của 010): body `{ "error": { "code": "ACCOUNT_DELETING" } }` → `AppError.AccountDeleted` và phát `AccountDeletedBus` (**không** phải `AccountLockedBus`: người dùng thấy "Tài khoản đã được xóa", không thấy thông báo khóa; [contract §4](contracts/edge-functions-account.md#4-thay-đổi-cho-code-dùng-chung-của-010)); các mã cũ của 010 không đổi

**Implementation**

- [x] T011 [P] Sửa `CORE/session/MyProfile.kt`: thêm (giữ nguyên trường cũ, trường mới có giá trị mặc định để code của nhóm khác không phải sửa) `email: String? = null`, `phone: String? = null`, `walletBalance: Long = 0`, `ownerStatus: String = "NONE"`, `ownerRevokedReason: String? = null`, `deletionRequestedAt: Instant? = null`; sửa `CORE/session/FirestoreUserProfileReader.kt` đọc các trường này (`walletBalance` đọc `Long`, thiếu thì `0`)
- [x] T012 [P] Sửa `CORE/session/SessionState.kt`: `SignOutReason` thêm `SESSION_REVOKED`, `ACCOUNT_DELETED` (giữ `LOCKED`)
- [x] T013 [P] Tạo `CORE/session/SignOutHook.kt` (`fun interface SignOutHook { suspend fun beforeSignOut(uid: String) }`) và khai báo `@Multibinds abstract fun signOutHooks(): Set<SignOutHook>` trong `CORE/di/SessionModule.kt` để tập rỗng vẫn inject được; KDoc ghi quy ước: nhóm nào có dữ liệu cần dọn khi đăng xuất thì `@Binds @IntoSet` một hook riêng
- [x] T014 Sửa `CORE/session/SessionManager.kt` thêm `suspend fun signOut(reason: SignOutReason?) { signOut() }` **có thân mặc định** để các fake `SessionManager` trong test của B, C, D vẫn biên dịch mà không phải sửa; `CORE/session/FirebaseSessionManager.kt` override cả hai (`signOut()` = `signOut(null)`), inject `Set<SignOutHook>`, nghe `AccountDeletedBus` → `signOut(ACCOUNT_DELETED)`; `signOut(reason)` chạy từng hook trong `withTimeout(5_000)` + `runCatching` **trước** `authSource.signOut()`, bỏ qua hook khi `reason == ACCOUNT_DELETED`; ánh xạ `ERROR_USER_TOKEN_EXPIRED` → `SignedOut(SESSION_REVOKED)`. Cho T007 xanh, 010-T007 vẫn xanh
- [x] T015 [P] *(Implement 2026-10-25: thêm cả `AppError.SessionRevoked` để `TaskAwait` ánh xạ `ERROR_USER_TOKEN_EXPIRED` và test được bằng fake; `when` vét cạn trên `AppError` phải thêm 2 nhánh, đã sửa `CompleteSignupUseCase` của 010)* Thêm `AccountDeleted` vào `CORE/network/AppError.kt`, tạo `CORE/network/AccountDeletedBus.kt` (`SharedFlow<Unit>`, cạnh `AccountLockedBus`); sửa `CORE/network/FunctionErrors.kt`: mã `ACCOUNT_DELETING` → `AppError.AccountDeleted` + `AccountDeletedBus`. Cho T010 xanh
- [x] T016 [P] Tạo `CORE/session/AppMode.kt` (`PLAYER`, `OWNER`), `CORE/session/AppModeController.kt` (`val mode: StateFlow<AppMode>`, `suspend fun switchTo(target: AppMode): Result<AppMode>`), `CORE/session/OwnerRevokedException.kt`, `CORE/session/DefaultAppModeController.kt` (`@Singleton`, giữ chế độ **trong bộ nhớ, không lưu DataStore** theo FR-011; `switchTo(OWNER)` gọi `refresh(true)` trong `withTimeout(3_000)`, lỗi mạng/hết giờ thì dùng `currentAppRole()`); bind trong `CORE/di/SessionModule.kt`. Cho T006 xanh
- [x] T017 [P] Tạo `CORE/media/StorageUrls.kt` (`object StorageUrls { fun publicUrl(bucket: String, path: String): String }`, đọc `BuildConfig.SUPABASE_URL`). Cho T008 xanh
- [x] T018 *(Implement 2026-10-25: thêm `AppModeController.modeFor(state)` tính đồng bộ để `MainActivity` không nháy qua `nav_player` khi chủ sân vừa đăng nhập; `SessionRouter.afterSignOut(reason)` và `GuestNotice`; argument `sessionRevokedNotice` cho `LoginFragment`)* Tạo `CORE/session/RootGraph.kt` (`fun rootGraphFor(appRole: AppRole, mode: AppMode): Int`); sửa `APP/MainActivity.kt` (010-T017, 010-T053) thu thập `combine(sessionState, appModeController.mode)` và điều hướng theo `rootGraphFor`, chỉ điều hướng lại khi graph đích đổi, `popUpTo(nav_main, inclusive = true)`; `SignedOut(SESSION_REVOKED)` → `nav_player` chế độ khách rồi `LoginFragment` chồng lên kèm thông báo "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại"; `SignedOut(ACCOUNT_DELETED)` → `nav_player` chế độ khách kèm `Snackbar` "Tài khoản đã được xóa". Quyết định đích dùng `SessionRouter` của 010 (010 T081, research R15), mở rộng cho hai lý do mới. Cho T009 xanh
- [x] T019 [P] Sửa tài liệu 010 cho khớp code: `specs/010-account-auth/plan.md` và `specs/010-account-auth/contracts/ui-screens.md` §1, §4 ghi `MainActivity` điều hướng theo `(appRole, mode)`, `SignOutReason` có `SESSION_REVOKED`, `ACCOUNT_DELETED`, `SessionManager.signOut(reason)` và `SignOutHook`; thêm ghi chú "sửa bởi 011 T014, T018" cạnh 010-T017, 010-T053 trong `specs/010-account-auth/tasks.md`; thêm ghi chú cho 010-T044: destination `legal` có deep link `smashnow://legal/{doc}` để `feature/account` mở được mà không import `LegalDocumentFragment` (constitution I)

**Checkpoint 2A**: T006–T010 xanh, test 010 vẫn xanh (`./gradlew testDebugUnitTest`). Mở PR `chore(core)`, báo nhóm: (1) dùng `StorageUrls.publicUrl("avatars", path)` để hiện ảnh đại diện; (2) `SignOutHook` cho dữ liệu cần dọn khi đăng xuất; (3) `MainActivity` điều hướng theo `(appRole, mode)`; (4) `ACCOUNT_DELETING` đưa người dùng về màn hình chào với "Tài khoản đã được xóa"; (5) `SessionManager.signOut(reason)` có thân mặc định nên fake cũ không phải sửa.

### Phase 2B: Domain và xác nhận lại danh tính dùng chung trong `feature/account`

**Purpose**: model, lỗi và luồng xác nhận lại danh tính ([R8](research.md#r8-xác-nhận-lại-danh-tính-fr-017-fr-018)) mà US5, US6, US7 cùng dùng.

**Tests (viết trước, phải fail)**

- [x] T020 [P] Unit test `TEST/feature/account/data/AccountErrorMapperTest.kt`: `INVALID_LOGIN_CREDENTIALS` / `ERROR_WRONG_PASSWORD` / `ERROR_INVALID_CREDENTIAL` → `WrongCurrentPassword`; `PASSWORD_DOES_NOT_MEET_REQUIREMENTS` / `ERROR_WEAK_PASSWORD` → `WeakPassword`; `TOO_MANY_ATTEMPTS_TRY_LATER` → `AppError.RateLimited`; hủy bottom sheet Google / hộp thoại OTP → `ReauthCancelled`; `ERROR_USER_MISMATCH` (chọn tài khoản Google khác tài khoản đang đăng nhập) → `ReauthWrongAccount`; `FirebaseNetworkException` → `Network`; còn lại → `Unknown` — *Implement (2026-10-29):* thêm `ERROR_INVALID_VERIFICATION_CODE` → `WrongOtpCode`, `ERROR_SESSION_EXPIRED` → `OtpExpired`, `NoCredentialException` → `ReauthWrongAccount` (chỉ lọc tài khoản đã cấp quyền)
- [x] T021 [P] Unit test `TEST/feature/account/domain/usecase/ReauthenticateUseCaseTest.kt` (fake `ReauthDataSource`): `providers` chứa `password` → `ReauthMethod.Password`; chỉ `google.com` → `Google`; chỉ `phone` → `Phone(phoneE164)`; có nhiều thì ưu tiên `password` → Google → số điện thoại; sau `reauthenticate` thành công gọi `IdTokenProvider.getToken(forceRefresh = true)` (để token có `auth_time` mới)

**Implementation**

- [x] T022 [P] Tạo các model trong `ACC/domain/model/` đúng [data-model §6](data-model.md#6-mô-hình-phía-app): `PlayerLevel` (`BEGINNER`, `INTERMEDIATE`, `ADVANCED`), `District(id, name, sortOrder)`, `ProfileDraft(displayName, level: PlayerLevel?, districtIds: List<String>)`, `OwnerSection` (`Hidden` · `CanApply` · `Revoked(reason)` · `Pending(submittedAt)` · `Rejected(reason)` · `Approved`), `ReauthMethod` (`Password`, `Google`, `Phone(phoneE164)`), `DeletionImpact(impactVersion, myBookings: List<ImpactBooking>, myDropIns: List<ImpactDropIn>, waitlistCount, ownerImpact: OwnerImpact?, walletBalance)`, `ImpactBooking(bookingId, venueName, startAt, status, paidAmount, refundAmount)`, `ImpactDropIn(sessionId, venueName, startAt, paidAmount, refundAmount)`, `OwnerImpact(venueCount, guestBookingCount, guestDropInCount, guestCount)`; tiền là `Long` VND — *Implement (làm sớm vì US1 cần):* `District` thêm `isActive` (mặc định `true`) để hiện chip "Khu vực đã ngừng"
- [x] T023 [P] Tạo `ACC/domain/model/AccountError.kt` (`WrongCurrentPassword`, `SamePassword`, `WeakPassword`, `SameEmail`, `ReauthCancelled`, `ReauthWrongAccount`, `ReauthRequired`, `ImpactChanged(newImpact)`, `DeleteTooLarge`, `AdminSelfDelete`, `ImageTooLarge`, `UnsupportedImage`, `PermissionDenied`, `OwnerRevoked(reason)`, `Network`, `Unknown`) và `ACC/data/AccountErrorMapper.kt`. Cho T020 xanh — *Implement (2026-10-29):* bổ sung `WrongOtpCode`, `OtpExpired` vào `AccountError` cho xác nhận bằng OTP; mapper có thêm `map(AuthError)` cho kết quả `SendOtpUseCase`
- [x] T024 Tạo `ACC/data/ReauthDataSource.kt` (interface) và `ACC/data/FirebaseReauthDataSource.kt`: `reauthWithPassword(password)` (`EmailAuthProvider.getCredential(email, password)`), `reauthWithGoogle(idToken)` (`GoogleAuthProvider.getCredential(idToken, null)`), `reauthWithPhone(credential)`; mọi lời gọi qua `awaitWithTimeout` (15 s). Đây là chỗ duy nhất trong `feature/account` gọi `FirebaseAuth` — *Implement (2026-10-29):* interface có thêm `currentAccount(): ReauthAccount?` (providers, email, số điện thoại); `reauthWithPhone(verificationId, code)` thay cho truyền `PhoneAuthCredential` để tầng gọi không cần kiểu Firebase
- [x] T025 Bảo đảm có `CORE/ui/CurrentActivityProvider.kt` cho `PhoneAuthProvider.verifyPhoneNumber` (cần `Activity`; điểm H2 khi analyze 010 chưa có thiết kế): nếu 010 đã làm thì dùng lại và đánh dấu xong; chưa có thì tạo (`Application.ActivityLifecycleCallbacks` giữ `WeakReference` tới Activity đang `RESUMED`, `suspend fun awaitActivity(): Activity` chờ tối đa 5 s rồi ném `AppError.Unknown`), đăng ký trong `APP/SmashNowApp.kt`, và báo để `SendOtpUseCase` của 010 dùng chung. ViewModel và UseCase không giữ tham chiếu `Activity` — *Implement (2026-10-29):* **không tạo** `CurrentActivityProvider`: 010 đã có cách lấy `Activity` là Fragment truyền `requireActivity()` vào từng lời gọi (`PhoneLoginViewModel.onSubmit(activity)`, `OtpViewModel.onResend(activity)`), ViewModel không giữ lại. `ReauthViewModel` làm giống vậy
- [x] T026 Tạo `ACC/domain/usecase/ReauthenticateUseCase.kt` (chọn `ReauthMethod`, gọi data source, ép làm mới token) và `ACC/ui/reauth/ReauthViewModel.kt` + `ACC/ui/reauth/ReauthDialog.kt` + `RES/layout/dialog_account_reauth.xml`: Google dùng Credential Manager (`GetGoogleIdOption`, `setFilterByAuthorizedAccounts(true)`); số điện thoại dùng lại `SendOtpUseCase` và model `OtpSession` ở `feature/auth/domain` của 010 (5 phút, 5 lần sai; chỉ dùng tầng `domain/`, không import `ui/` hay `data/` của `feature/auth`), lấy `Activity` qua `CurrentActivityProvider` (T025). Cho T021 xanh — *Implement (2026-10-29):* Google lấy ID token qua `ACC/data/GoogleReauthTokenSource.kt` (không import `GoogleCredentialDataSource` ở `feature/auth/data`); máy tự xác minh OTP thì `SendOtpUseCase` đã đăng nhập lại cùng số nên chỉ làm mới token; kết quả trả qua Fragment Result `AccountResults.REAUTH`. Chưa có test cho `ReauthViewModel`, chưa nối vào màn hình nào (US6 dùng)

### Phase 2C: Khung trang Tài khoản và điều hướng

**Purpose**: `nav_account` và khung `AccountFragment` để mỗi story gắn phần của mình ([contracts/ui-screens.md §1](contracts/ui-screens.md#1-nav-graph-nav_account-a), [R14](research.md#r14-trang-tài-khoản-trong-điều-hướng-và-lối-sang-tính-năng-khác)).

- [x] T027 [P] Unit test `TEST/feature/account/ui/account/AccountEntriesTest.kt` cho hàm thuần `accountEntries(providers, appRole, availableDeepLinks)`: không có provider `password` → không có `CHANGE_PASSWORD`, `CHANGE_EMAIL` (FR-015); `ADMIN` → không có `DELETE_ACCOUNT` (FR-018); deep link chưa có destination → mục bị ẩn (R14)
- [x] T028 Tạo `RES/navigation/nav_account.xml` (graph lồng, start `AccountFragment`, đủ destination và Safe Args theo bảng [ui-screens §1](contracts/ui-screens.md#1-nav-graph-nav_account-a): `DistrictPickerBottomSheet(selectedIds: String[])`, `AvatarCropFragment(sourceUri: String)`, `ReauthDialog(purpose)`); thêm `<deepLink app:uri="smashnow://legal/{doc}"/>` cho destination `legal` trong `RES/navigation/nav_auth.xml` (010); `include` vào `RES/navigation/nav_player.xml` (tab "Tài khoản") và `RES/navigation/nav_owner.xml` (mục tài khoản); destination chưa làm thì để Fragment khung — *Implement:* `nav_player`/`nav_owner` có action `action_{playerHome|ownerHome}_to_account` và nút "Tài khoản" trên màn hình tạm của 010 (chưa có thanh điều hướng dưới); US5–US7 là Fragment khung không layout, chưa khai báo deep link
- [x] T029 Tạo `ACC/ui/account/AccountEntry.kt` + `accountEntries(...)` (bảng deep link R14: `smashnow://bookings/mine`, `smashnow://dropin/mine`, `smashnow://favorites`, `smashnow://notifications/settings`, `smashnow://owner/apply?mode=new|resubmit`, `smashnow://owner/application`, `smashnow://owner/venues`, `smashnow://chat/owner`, `smashnow://wallet/topup`, `smashnow://legal/{doc}`; kiểm tra bằng `navController.graph.hasDeepLink`); `ACC/ui/account/AccountFragment.kt` + `RES/layout/fragment_account.xml` khung: đầu trang (ảnh hoặc chữ cái đầu qua `StorageUrls` + Coil, họ tên, email hoặc số điện thoại), danh sách mục, mục "Điều khoản và quyền riêng tư" mở bằng deep link `smashnow://legal/TERMS` (**không** import `LegalDocumentFragment` ở `feature/auth/ui`, constitution I), dòng "SmashNow · Phiên bản học tập"; trạng thái đang tải (skeleton), có dữ liệu, lỗi + "Thử lại" (FR-025); `w600dp` giới hạn rộng 480dp (`auth_form_max_width` của 010). Cho T027 xanh — *Implement:* thêm Coil 3.4.0 (`coil` + `coil-network-okhttp`, ghi ở Complexity Tracking). Màn hình trong `nav_account` cũng có deep link `smashnow://account/{password|email|delete}` chỉ để kiểm tra "đã làm chưa"; bấm vẫn đi bằng action vì `nav_account` được include hai lần
- [x] T030 Tạo `ACC/ui/account/AccountViewModel.kt` khung: `StateFlow<AccountUiState>` (`Loading` · `Error(AppError)` · `Content(profile, entries, ...)`) từ `UserProfileReader.observeMyProfile()` và `SessionManager.sessionState`; kênh sự kiện một lần (`Channel`) cho điều hướng deep link; các story bổ sung trường `districtNames`, `ownerSection`, `mode`, `canDelete`, `showVerifyBanner` — *Implement:* listener `users/{uid}` không báo lỗi (mất mạng chỉ chờ) nên quá 15 giây chưa có hồ sơ thì `Error(Timeout)`; Fragment truyền tập deep link có destination vào `setAvailableDeepLinks`. Test: `AccountViewModelTest`

**Checkpoint 2**: app vào tab "Tài khoản" thấy khung trang với tên và email; test Phase 2 xanh. Các user story bắt đầu được, song song nếu đủ người.

---

## Phase 3: User Story 1 – Xem và sửa hồ sơ cá nhân (Priority: P1) 🎯 MVP

**Goal**: xem đủ hồ sơ; sửa họ tên, trình độ, khu vực, ảnh đại diện; hỏi khi thoát mà chưa lưu.

**Independent Test**: đăng nhập `player1`, sửa tên, chọn "Trung bình", 2 khu vực, đổi ảnh, lưu; tắt hẳn app, mở lại thấy thông tin mới (quickstart Q1–Q10).

### Tests for User Story 1 (viết trước, phải fail)

- [x] T031 [P] [US1] Viết test `firestore-tests/test/users-profile.test.ts` đủ **P1–P18** của [contracts/security-rules.md §4](contracts/security-rules.md#4-bảng-test) (seed `districts/q1`, `q3` `isActive = true`, `q-cu` `isActive = false`; `avatarPath` khớp `{uid}/avatar_[0-9]{13}.jpg`, P12 thử `'{uid}/../x.jpg'` và `'{uid}/avatar.png'`; P15 `arrayRemove` token phục vụ US2) và `firestore-tests/test/districts.test.ts` đủ **D1–D4, A1** — *Implement:* thêm P5b (đúng 5), P14b (một trường sai chặn cả request), P-updatedAt, D1b (get khu vực đã ngừng), A1b (admin cũng không ghi); dữ liệu mẫu chung ở `firestore-tests/test/fixtures.ts`
- [x] T032 [P] [US1] Viết test pgTAP `supabase/tests/storage_avatars_test.sql` đủ **ST1–ST8** của [contracts/storage-avatars.md §4](contracts/storage-avatars.md#4-test-pgtap-giả-lập-jwt): `create extension if not exists pgtap`, mỗi ca `set local role authenticated` + `set_config('request.jwt.claims', '{"sub":"<uid>","appRole":"PLAYER","role":"authenticated"}', true)`; ST7 dùng role `anon`; ST8 bucket `owner-docs` — *Implement:* Storage mới có trigger `storage.protect_delete()` chặn `DELETE` thẳng bằng SQL; test đặt `storage.allow_delete_query = true` (giống Storage API) để kiểm tra RLS của DELETE. Chỉ chạy trên Supabase local
- [x] T033 [P] [US1] Unit test `TEST/feature/account/domain/validation/DistrictSelectionRuleTest.kt`: 0–5 phần tử, không trùng → hợp lệ; 6 phần tử hoặc trùng → `account_error_district_max`
- [x] T034 [P] [US1] Unit test `TEST/feature/account/domain/validation/AvatarImageRuleTest.kt`: MIME `image/jpeg`, `image/png`, `image/webp` → hợp lệ; `image/heic`, `image/gif`, `null` → `UnsupportedImage`; byte sau nén > `2_097_152` → `ImageTooLarge`
- [x] T035 [P] [US1] Unit test `TEST/feature/account/domain/usecase/ChangeAvatarUseCaseTest.kt` (fake `AvatarRepository`, fake `AccountRepository`, đồng hồ giả) theo thứ tự [R3](research.md#r3-ảnh-đại-diện-trên-supabase-storage):
  - thành công: tải lên `{uid}/avatar_{now}.jpg` (`upsert = false`) → ghi `avatarPath` mới → xóa object cũ; lỗi xóa object cũ bị bỏ qua;
  - lỗi tải lên → không ghi Firestore, `avatarPath` giữ nguyên;
  - lỗi ghi Firestore → xóa object mới vừa tải (bỏ qua lỗi), trả lỗi;
  - `RemoveAvatarUseCase`: ghi `avatarPath = null` rồi xóa object.
- [x] T036 [P] [US1] Unit test `TEST/feature/account/ui/edit/EditProfileViewModelTest.kt` (Turbine, fake repository): — *Implement:* `AvatarState.Uploading` không có `progress` vì supabase-kt `upload` không báo tiến độ; hiện vòng xoay không xác định
  - `isDirty` = `draft != saved`; sửa rồi trả lại như cũ → `false`;
  - lỗi tên dùng `NameRule` của 010 (2–50 ký tự sau `trim`);
  - bấm Lưu 3 lần liên tiếp → `SaveProfileUseCase` được gọi **1 lần**, `isSaving` khóa nút (FR-023, Edge Case);
  - lưu thành công → sự kiện "Đã lưu", `saved = draft`; mất mạng → giữ `draft`, báo lỗi + thử lại;
  - danh mục khu vực lỗi → `districtsState = Error`, các ô khác vẫn sửa và lưu được (Edge Case);
  - Back khi `isDirty` → sự kiện hỏi "Bỏ các thay đổi chưa lưu?" (FR-006);
  - đổi ảnh: `Processing` → `Uploading` → `Idle`; lỗi → `avatar = Error`, ảnh cũ giữ nguyên.
- [x] T037 [P] [US1] Unit test `TEST/feature/account/ui/account/AccountViewModelProfileTest.kt`: `Content` có `districtNames` theo thứ tự `favoriteDistrictIds` (ID không còn trong danh mục thì bỏ qua), nhãn trình độ, `walletBalance` định dạng `50.000 đ`, `showVerifyBanner` khi có provider `password` và chưa xác minh

- [x] T038 [P] [US1] Unit test `TEST/feature/account/data/AccountRepositoryImplTest.kt` và `TEST/feature/account/data/DistrictRepositoryImplTest.kt` (fake data source; constitution IV bắt buộc test Repository): `updateProfile` chỉ gửi các trường khác bản đã lưu và luôn kèm `updatedAt`; không có trường nào đổi → không ghi; lỗi mạng → `AccountError.Network`; danh mục sắp theo `sortOrder`; ID khu vực đã `isActive = false` vẫn lấy được tên để hiển thị

### Implementation for User Story 1

- [x] T039 [US1] Sửa `firestore.rules` đúng [contracts/security-rules.md §1–§3](contracts/security-rules.md#1-bổ-sung-trong-match-usersuid): thêm `changed()`, `validLevel()` (`null` hoặc `BEGINNER` | `INTERMEDIATE` | `ADVANCED`), `activeDistrict()`, `validDistricts()` (0–5 phần tử, không trùng, mỗi ID là `districts/{id}` có `isActive == true`, tối đa 5 lượt `get()`), `validAvatarPath()` (`null` hoặc `uid + '/avatar_[0-9]{13}[.]jpg'`) vào điều kiện `update` của `match /users/{uid}`; thêm `match /districts/{id}` (`get, list: if isInitialized()`, `write: if isAdmin()`) và `match /accountDeletions/{uid}` (`read, write: if false`). Cho T031 xanh; `firestore-tests/test/users.test.ts` (U1–U18 của 010) vẫn xanh — *Implement:* U8 của 010 sửa dữ liệu mẫu (seed `districts/q1`, `avatarPath` theo mẫu mới) vì 011 siết kiểm tra; ý nghĩa U8 không đổi. 55/55 xanh
- [x] T040 [US1] Tạo migration `supabase/migrations/<timestamp>_avatars_bucket.sql` đúng [contracts/storage-avatars.md §1–§2](contracts/storage-avatars.md#1-bucket): bucket `avatars` public, `file_size_limit = 2097152`, `allowed_mime_types = image/jpeg, image/png, image/webp` (`on conflict do update`); policy `avatars_insert_own`, `avatars_delete_own`, `avatars_select_own` với `bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.jwt() ->> 'sub' and auth.jwt() ->> 'appRole' is not null`; **không** tạo policy `UPDATE`. Cho T032 xanh (`npx supabase db reset && npx supabase test db`) — *Implement:* `supabase/migrations/20261025080000_avatars_bucket.sql`, chỉ `db reset` trên local; chưa áp lên project thật
- [x] T041 [P] [US1] Tạo `ACC/domain/validation/DistrictSelectionRule.kt`, `ACC/domain/validation/AvatarImageRule.kt`. Cho T033, T034 xanh — *Implement:* phần thuần của T043 (kích thước thu nhỏ, các mức chất lượng 85→50) nằm ở `AvatarImageRule`, có test
- [x] T042 [P] [US1] Tạo `ACC/domain/DistrictRepository.kt` + `ACC/data/FirestoreDistrictDataSource.kt` (`where isActive == true order by sortOrder`, có `get(id)` cho tên khu vực đã ngừng) + `ACC/data/DistrictRepositoryImpl.kt`. Cho phần `DistrictRepositoryImplTest` của T038 xanh — *Implement:* `DistrictRepositoryImpl` `@Singleton`, giữ danh mục trong bộ nhớ suốt phiên
- [x] T043 [P] [US1] Tạo `ACC/data/ImageProcessor.kt`: đọc MIME bằng `ContentResolver.getType`; `ImageDecoder` thu về tối đa **512 × 512 px**; `Bitmap.compress(JPEG, 85)`; > 2 MB thì giảm chất lượng mỗi lần 10, tối thiểu 50, vẫn quá thì `ImageTooLarge`; chạy trên `Dispatchers.Default`; xóa ảnh tạm trong `cacheDir/avatar/` sau khi xong — *Implement:* kiểm tra MIME ở ảnh gốc trước khi cắt (`EditProfileFragment`) và lần nữa ở ảnh cắt (`file://` dùng `MimeTypeMap`)
- [x] T044 [P] [US1] Tạo `ACC/domain/AvatarRepository.kt` + `ACC/data/SupabaseAvatarDataSource.kt` (`storage.from("avatars").upload(path, bytes) { upsert = false; contentType = Jpeg }`, `delete(path)`; token là Firebase ID token qua `SupabaseModule` của 010) + `ACC/data/AvatarRepositoryImpl.kt` — *Implement:* lỗi kết nối supabase-kt (`HttpRequestException`) và quá 15 giây → `AccountError.Network`
- [x] T045 [US1] Tạo `ACC/domain/AccountRepository.kt` + `ACC/data/FirestoreAccountDataSource.kt` + `ACC/data/AccountRepositoryImpl.kt`: `updateProfile(displayName, level, favoriteDistrictIds)` và `setAvatarPath(path?)` đều ghi `updatedAt = FieldValue.serverTimestamp()` và chỉ các trường đổi (khớp `onlyChanges(selfEditableFields())`), qua `awaitWithTimeout`. Cho phần `AccountRepositoryImplTest` của T038 xanh
- [x] T046 [US1] Tạo `ACC/domain/usecase/SaveProfileUseCase.kt`, `ChangeAvatarUseCase.kt`, `RemoveAvatarUseCase.kt` theo thứ tự R3. Cho T035 xanh
- [x] T047 [US1] Tạo `ACC/ui/edit/EditProfileViewModel.kt` (`draft`, `saved`, `nameError`, `districtsState`, `avatar: Idle | Processing | Uploading(progress) | Error`, `isSaving`, `isDirty`; `SavedStateHandle` giữ `draft` khi xoay màn hình). Cho T036 xanh
- [x] T048 [US1] Tạo `ACC/ui/edit/EditProfileFragment.kt` + `RES/layout/fragment_account_edit_profile.xml` theo wireframe `ui/01_account_buoc2_sua-ho-so.png` và [ui-screens §2](contracts/ui-screens.md#2-chi-tiết-theo-wireframe): ảnh tròn lớn, "Đổi ảnh", "Xóa ảnh", "Ảnh được cắt vuông, tối đa 2 MB"; ô họ tên đếm `12/50`; email chỉ đọc + chip "Chưa xác minh"; `MaterialButtonToggleGroup` trình độ (`singleSelection`, cho bỏ chọn); chip khu vực có nút xóa, đếm `2/5`, "+ Thêm khu vực" tắt khi đủ 5, lỗi danh mục + "Thử lại" ngay trong mục; thanh dưới "Có thay đổi chưa lưu" + "Lưu thay đổi"; `OnBackPressedCallback` khi `isDirty` — *Implement:* chuỗi thêm `account_district_chip_inactive`
- [x] T049 [P] [US1] Tạo `ACC/ui/district/DistrictPickerBottomSheet.kt` + `RES/layout/sheet_account_district_picker.xml` (danh sách chọn nhiều, tối đa 5, trả kết quả bằng Fragment Result API; đủ 4 trạng thái: đang tải, có dữ liệu, rỗng "Chưa có khu vực nào", lỗi + "Thử lại") — *Implement:* thêm `DistrictPickerViewModel` + test (lựa chọn sống qua xoay màn hình); chuỗi `account_district_empty`
- [x] T050 [US1] Tạo `ACC/ui/avatar/AvatarSourceBottomSheet.kt` + `RES/layout/sheet_account_avatar_source.xml` ("Chụp ảnh", "Chọn từ thư viện") và `ACC/ui/avatar/AvatarCropFragment.kt` + `RES/layout/fragment_account_avatar_crop.xml` (khung cố định 1:1): thư viện dùng `PickVisualMedia(ImageOnly)` (không xin quyền); camera xin `CAMERA` **chỉ khi bấm** (FR-024), từ chối thì `MaterialAlertDialog` giải thích và vẫn cho chọn thư viện (US1-4), "Don't ask again" thì có nút mở Cài đặt ứng dụng; `TakePicture` ghi vào `FileProvider` `cacheDir/avatar/`; ảnh được lưu ngay khi xác nhận cắt, có vòng tiến trình trên ảnh — *Implement:* launcher (Photo Picker, `TakePicture`, xin quyền camera) đặt ở `EditProfileFragment` vì sheet đóng ngay sau khi chọn; cắt bằng `CropImageView` trong Fragment của mình (không dùng `CropImageActivity`); chuỗi `account_open_settings`
- [x] T051 [US1] Hoàn thiện phần hồ sơ của `ACC/ui/account/AccountFragment.kt` và `AccountViewModel.kt` theo wireframe `ui/01_account_buoc1_tai-khoan.png`: chip trình độ và khu vực, nút "Sửa hồ sơ", thẻ ví "Ví giả lập · không phải tiền thật" + số dư + nút "Nạp ví" (deep link `smashnow://wallet/topup`, ẩn nếu chưa có destination, FR-001a, R13), `VerifyEmailBannerView` của 010; `contentDescription` cho ảnh đại diện. Cho T037 xanh

**Checkpoint**: quickstart Q1–Q10 đạt; `npm test` (Rules) và `npx supabase test db` (Storage) xanh.

---

## Phase 4: User Story 2 – Đăng xuất (Priority: P1) 🎯 MVP

**Goal**: đăng xuất sau xác nhận, về Trang chủ chế độ khách, máy không nhận thông báo đẩy của tài khoản cũ.

**Independent Test**: đăng nhập, đăng xuất, mở lại app vào Trang chủ chế độ khách; token máy không còn trong `users/{uid}.fcmTokens` (quickstart Q11–Q12).

### Tests for User Story 2 (viết trước, phải fail)

- [x] T052 [P] [US2] Unit test `TEST/core/session/FcmSignOutHookTest.kt` (fake `FcmTokenSource`, fake ghi Firestore): ghi `fcmTokens = arrayRemove(token)` + `updatedAt` rồi `deleteToken()`; ghi Firestore lỗi (mất mạng) → `deleteToken()` vẫn chạy; lấy token lỗi → không ném ra ngoài
- [x] T053 [P] [US2] Unit test `TEST/feature/account/ui/account/AccountViewModelSignOutTest.kt`: bấm "Đăng xuất" → sự kiện mở hộp thoại xác nhận; xác nhận → gọi `SessionManager.signOut()` đúng 1 lần, bấm lặp không gọi thêm

### Implementation for User Story 2

- [x] T054 [P] [US2] Tạo `CORE/session/FcmSignOutHook.kt` (`FirebaseMessaging.getInstance().token` → `users/{uid}` `fcmTokens = FieldValue.arrayRemove(token)`, `updatedAt = serverTimestamp()` → `FirebaseMessaging.getInstance().deleteToken()`) và `CORE/session/GoogleSignOutHook.kt` (`CredentialManager.clearCredentialState()`); `@Binds @IntoSet` trong `CORE/di/SessionModule.kt`. Cho T052 xanh — *Implement:* tách `FcmTokenSource`/`FcmTokenStore` để test không cần Firebase; lấy token lỗi vẫn gọi `deleteToken()`
- [x] T055 [US2] Thêm nút "Đăng xuất" vào `ACC/ui/account/AccountFragment.kt` mở `MaterialAlertDialog` xác nhận; `AccountViewModel.signOut()` gọi `SessionManager.signOut()`; `MainActivity` (T018) đưa về `nav_player` chế độ khách và xóa back stack (FR-007, 010 R15). Cho T053 xanh — *Implement:* nút khóa và hiện vòng xoay trong lúc chạy `SignOutHook`; `MainActivity` (T018) đã `popUpTo(nav_main, inclusive)` nên Back không quay lại trang Tài khoản
- [ ] T056 [P] [US2] (Nên có) Espresso `ATEST/feature/account/SignOutTest.kt` trên Emulator: đăng xuất → `LoginFragment`, `pressBack()` không quay lại trang Tài khoản — *Chưa làm (2026-10-25):* cần máy ảo đang chạy và `USE_EMULATOR=true`; lưu ý FR-007 về Trang chủ khách chứ không phải `LoginFragment`, sửa mô tả khi làm

**Checkpoint**: US1 + US2 = MVP; quickstart Q11, Q12 đạt.

---

## Phase 5: User Story 3 – Đăng ký làm chủ sân và theo dõi trạng thái duyệt (Priority: P1)

**Goal**: mục chủ sân hiển thị đúng 5 trạng thái của FR-008 (cộng ẩn với admin), cập nhật khi admin đổi trạng thái.

**Independent Test**: 5 tài khoản `player1`, `player-pending`, `player-rejected`, `player-revoked`, `owner1` mỗi tài khoản thấy đúng nội dung và nút (quickstart Q13–Q14).

### Tests for User Story 3 (viết trước, phải fail)

- [x] T057 [P] [US3] Unit test `TEST/feature/account/domain/OwnerSectionResolverTest.kt` đủ **6 dòng** bảng [research R6](research.md#r6-mục-chủ-sân-trên-trang-tài-khoản-fr-008-fr-009-fr-013): `ADMIN` + bất kỳ → `Hidden`; `PLAYER` + `NONE` + `ownerRevokedReason == null` → `CanApply`; `PLAYER` + `NONE` + có lý do → `Revoked(reason)`; `PLAYER` + `PENDING` → `Pending(submittedAt)`; `PLAYER` + `REJECTED` → `Rejected(rejectReason)`; `OWNER` + `APPROVED` → `Approved`; thêm ca lệch tạm thời (claim `OWNER`, `ownerStatus` còn `PENDING`) → `Approved` (claim là nguồn quyền)
- [x] T058 [P] [US3] Unit test `TEST/feature/account/ui/account/AccountViewModelOwnerTest.kt`: chỉ mở listener hồ sơ gần nhất khi `ownerStatus` là `PENDING` hoặc `REJECTED`; `MyProfile` đổi (admin duyệt) → `ownerSection` mới mà không đăng xuất (US3-5, FR-009); bấm "Sửa và nộp lại" → deep link `smashnow://owner/apply?mode=resubmit`; "Đăng ký làm chủ sân" (kể cả `Revoked`) → `mode=new`

### Implementation for User Story 3

- [x] T059 [P] [US3] Tạo `ACC/domain/OwnerSectionResolver.kt` (hàm thuần). Cho T057 xanh
- [x] T060 [US3] Thêm vào `ACC/data/FirestoreAccountDataSource.kt` `observeLatestOwnerApplication(uid)` (`ownerApplications where uid == me order by submittedAt desc limit 1`, trả `status`, `submittedAt`, `rejectReason`)
- [x] T061 [US3] Thêm mục chủ sân vào `ACC/ui/account/AccountFragment.kt` / `AccountViewModel.kt` theo wireframe `ui/01_account_buoc1_tai-khoan-bi-thu-hoi-quyen.png`: `CanApply` ("Đăng ký làm chủ sân" + mô tả ngắn), `Pending` ("Đang chờ duyệt" + ngày nộp, không có nút), `Rejected` ("Bị từ chối" + lý do + "Sửa và nộp lại"), `Revoked` ("Quyền chủ sân đã bị thu hồi" + lý do + "Đăng ký làm chủ sân"), `Approved` ("Đã là chủ sân", khối chuyển chế độ thêm ở US4), `Hidden` (FR-013); mục nào chưa có destination (spec 110 chưa làm) thì ẩn nút, vẫn hiện trạng thái. Cho T058 xanh

**Checkpoint**: quickstart Q13, Q14 đạt (Q14 dùng `scripts/admin/grant-role.ts` của 010 nếu màn hình duyệt của D chưa có).

---

## Phase 6: User Story 4 – Chủ sân chuyển giữa chế độ "Người chơi" và "Quản lý sân" (Priority: P2)

**Goal**: chủ sân đã duyệt chuyển qua lại trong ≤ 1 giây; quyền bị thu hồi thì không vào được "Quản lý sân".

**Independent Test**: `owner1` chuyển 5 lần, mỗi lần đúng giao diện; thu hồi quyền rồi bấm "Quản lý sân" → ở lại người chơi kèm lý do (quickstart Q15–Q17).

**Phụ thuộc**: T016, T018 (Phase 2A), T061 (US3).

### Tests for User Story 4 (viết trước, phải fail)

- [x] T062 [P] [US4] Unit test `TEST/feature/account/ui/account/AccountViewModelModeTest.kt` (fake `AppModeController`): chọn "Người chơi" → `switchTo(PLAYER)`; chọn "Quản lý sân" trả `OwnerRevokedException` → toggle về "Người chơi" và sự kiện snackbar "Quyền chủ sân đã bị thu hồi" kèm `ownerRevokedReason` (US4-4, FR-012); tài khoản không phải `OWNER` → không có khối chuyển chế độ (US4-3)

### Implementation for User Story 4

- [x] T063 [US4] Tạo `ACC/domain/usecase/SwitchModeUseCase.kt` (bọc `AppModeController.switchTo`, ánh xạ `OwnerRevokedException` → `AccountError.OwnerRevoked(reason)` với lý do lấy từ `MyProfile.ownerRevokedReason`)
- [x] T064 [US4] Thêm khối "Chế độ đang dùng" vào `RES/layout/fragment_account.xml` và `ACC/ui/account/AccountFragment.kt` theo wireframe `ui/01_account_buoc3_chu-san-kiem-nguoi-choi.png`: `MaterialButtonToggleGroup` "Người chơi" / "Quản lý sân", dòng "Mỗi lần mở app, bạn vào giao diện quản lý sân" (FR-011); nhóm "CHỦ SÂN" (Cơ sở của tôi `smashnow://owner/venues`, Tin nhắn khách hàng `smashnow://chat/owner`, Hồ sơ chủ sân `smashnow://owner/application`), ẩn mục chưa có destination. Cho T062 xanh
- [x] T065 [US4] Kiểm tra trên máy: `AccountFragment` mở được từ cả `nav_player` và `nav_owner` (nhờ `include` ở T028) và giữ trạng thái toggle đúng `mode` sau khi `MainActivity` đổi graph; đo 5 lần chuyển ≤ 1 giây (SC-003), ghi kết quả vào checklist quickstart

**Checkpoint**: quickstart Q15–Q17 đạt.

---

## Phase 7: User Story 5 – Đổi mật khẩu (Priority: P2)

**Goal**: người có mật khẩu đổi được mật khẩu; sai mật khẩu hiện tại hoặc trùng mật khẩu cũ thì báo lỗi; mục ẩn với Google/số điện thoại.

**Independent Test**: đổi mật khẩu, đăng xuất, đăng nhập bằng mật khẩu mới được, cũ không được; máy thứ hai bị đăng xuất (quickstart Q19–Q20).

**Phụ thuộc**: Phase 2B (xác nhận lại danh tính).

### Tests for User Story 5 (viết trước, phải fail)

- [ ] T066 [P] [US5] Unit test `TEST/feature/account/domain/validation/DifferentPasswordRuleTest.kt`: mật khẩu mới ≠ mật khẩu hiện tại → hợp lệ; bằng nhau → `account_error_same_password` (so chính xác, phân biệt hoa thường)
- [ ] T067 [P] [US5] Unit test `TEST/feature/account/ui/password/ChangePasswordViewModelTest.kt` (fake `ChangePasswordUseCase`): dấu tích `len8`, `letterDigit` theo `PasswordRule` của 010; nhập lại không khớp → lỗi `ConfirmPasswordRule`; trùng mật khẩu cũ → lỗi, **không** gọi use case (US5-3); `WrongCurrentPassword` → lỗi dưới ô mật khẩu hiện tại "Mật khẩu hiện tại không đúng" (US5-2); `WeakPassword` → lỗi dưới ô mật khẩu mới; thành công → sự kiện quay lại + snackbar; `isSubmitting` khóa nút; mất mạng → giữ dữ liệu, xóa các ô mật khẩu (FR-023)

### Implementation for User Story 5

- [ ] T068 [P] [US5] Tạo `ACC/domain/validation/DifferentPasswordRule.kt`. Cho T066 xanh
- [ ] T069 [US5] Thêm `updatePassword(newPassword)` vào `ACC/data/FirebaseReauthDataSource.kt` và tạo `ACC/domain/usecase/ChangePasswordUseCase.kt`: `reauthWithPassword(current)` → `updatePassword(new)`; ánh xạ lỗi qua `AccountErrorMapper`
- [ ] T070 [US5] Tạo `ACC/ui/password/ChangePasswordViewModel.kt` + `ACC/ui/password/ChangePasswordFragment.kt` + `RES/layout/fragment_account_change_password.xml` (3 ô: hiện tại, mới, nhập lại; hai dấu tích như màn hình đăng ký 010; nút "Đổi mật khẩu"). Cho T067 xanh
- [ ] T071 [US5] Nối mục "Đổi mật khẩu" trong `AccountFragment` (chỉ khi có provider `password`, theo `accountEntries` T029, FR-015)

**Checkpoint**: quickstart Q18 (phần Google/phone), Q19, Q20 đạt.

---

## Phase 8: User Story 6 – Xóa tài khoản và dữ liệu cá nhân (Priority: P2)

**Goal**: xem trước tác động, xác nhận lại danh tính, gõ "XÓA", xóa ở server theo 2 bước (chốt trong một transaction, hoàn tất idempotent) đúng FR-018–FR-022a.

**Independent Test**: `player1` có 2 đơn sắp tới, 1 lượt vãng lai, ảnh, yêu thích, 1 đánh giá → xóa; không đăng nhập lại được, 2 đơn `CANCELLED`, slot trống, đánh giá hiện "Người dùng đã xóa"; `owner1` xóa → cơ sở ẩn, khách được hoàn 100% và nhận thông báo (quickstart Q23–Q26).

**Phụ thuộc**: Phase 2B; helper của B, C, D theo bảng [Phụ thuộc nhóm khác](#phụ-thuộc-nhóm-khác-và-phương-án-tạm).

### Tests for User Story 6 – server (viết trước, phải fail)

- [ ] T072 [P] [US6] Bổ sung test `FN/_shared/auth.test.ts` (010): `users/{uid}.deletionRequestedAt != null` → `requireUser` trả `403 ACCOUNT_DELETING`; `requireUser(req, { allowDeleting: true })` → **không** trả `ACCOUNT_DELETING`, và khi `users/{uid}` đã bị xóa nhưng token hợp lệ thì trả `VerifiedToken` với `profile = null` thay vì lỗi; `VerifiedToken` có `authTime` (giây, lấy từ claim `auth_time`); các ca cũ của 010 vẫn xanh
- [ ] T073 [P] [US6] Bổ sung test `FN/_shared/identity.test.ts` (010): `deleteUser(uid)` gọi `accounts:delete` và người dùng không còn trên Auth Emulator; gọi lần 2 (`USER_NOT_FOUND`) → coi như thành công
- [ ] T074 [P] [US6] Tạo `FN/delete-account/test_fakes.ts`: bản giả **cùng chữ ký** của mọi helper B, C, D (`cancelBookingInTx`, `computeBookingRefund`, `anonymizeBookings`, `cancelRegistrationInTx`, `leaveWaitlistInTx`, `computeRefund`, `anonymizeReviews`, `anonymizeCommunity`, `hideVenueForOwnerDeletionInTx`, `cancelSessionInTx`, `deleteOwnerApplications`, `sendNotification` có khóa idempotent: cùng khóa gọi lại thì không gửi thêm), ghi lại lời gọi, có cờ "ném lỗi ở lần gọi thứ N" cho X7, X9; hàm seed dữ liệu (người chơi, đơn, slot, lượt vãng lai, cơ sở, buổi, đánh giá, favorites, ảnh)
- [ ] T075 [US6] Viết test `FN/delete-account/handler.test.ts` đủ **X1–X15** của [contracts/edge-functions-account.md §5](contracts/edge-functions-account.md#5-test-deno-test-firebase-emulator-auth--firestore) trên Auth/Firestore Emulator (Storage API giả bằng server `fetch` cục bộ):
  - X3 kiểm đủ: 2 đơn `CANCELLED`, `cancelledBy = SYSTEM`, slot bị xóa, `users/{uid}` và Auth không còn, `avatars/{uid}/` rỗng, favorites rỗng, `accountDeletions/{uid}.state = DONE`;
  - X7 (helper giả ném lỗi ở đơn thứ 2): `500`, **không** đơn nào bị hủy, `users/{uid}` nguyên vẹn, không có `accountDeletions/{uid}`, Auth vẫn bật (FR-021);
  - X8: cơ sở `HIDDEN` + `hiddenReason = OWNER_DELETED`, ví khách tăng đúng số đã trả, `notify` 4 phần tử, `sendNotification` giả nhận đúng 4 khóa khác nhau, **2 người đăng ký cùng một buổi đều được báo** (SC-008);
  - X10: `confirm` 2 lần liên tiếp và 2 lần song song (`Promise.all`) → lần sau **không** bị `requireUser` chặn bằng `ACCOUNT_DELETING`, trả `200 DELETED` hoặc `202` và dọn dẹp tiếp; gọi lại sau khi `users/{uid}` đã bị xóa vẫn trả `200 DELETED`; đơn chỉ bị hủy một lần, thông báo chỉ gửi một lần;
  - X14: seed 250 đơn sắp tới đã trả cọc (ước tính `2 × 250 + 2 + 3 = 505 > 450` theo công thức ở T079) → `422 DELETE_TOO_LARGE`, không có gì đổi; thêm ca biên 220 đơn (`445`) vẫn xóa được;
  - thêm script `deno task test:flaky` chạy X7 và X10 **10 lần liên tiếp**.
- [ ] T076 [P] [US6] Viết test `FN/delete-account-sweeper/index.test.ts`: **X16** (thiếu hoặc sai `CRON_SECRET` → `401`); bản ghi `COMMITTED` có bước `FAILED` → chạy lại tới `DONE` (nối tiếp X9); tối đa 5 bản ghi mỗi lần theo `requestedAt`; bản ghi `DONE` có `finalSweepAfter <= now` và chưa có `steps.finalSweep` → chạy lại `deleteSubcollections` một lần, ghi `steps.finalSweep = DONE`; bước `SKIPPED` không bị chạy lại; `attempts >= 20` → log mức error

### Implementation for User Story 6 – server

- [ ] T077 [US6] Sửa `FN/_shared/auth.ts` (010, [contract §4](contracts/edge-functions-account.md#4-thay-đổi-cho-code-dùng-chung-của-010)): `requireUser` đọc `users/{uid}` (đã đọc sẵn) và trả `403 ACCOUNT_DELETING` khi có `deletionRequestedAt`; thêm tham số **tùy chọn** `opts?: { allowDeleting?: boolean }` (chỉ `delete-account` truyền `true`: bỏ kiểm tra này và cho phép `users/{uid}` không còn); thêm `authTime` vào `VerifiedToken`; lời gọi cũ `requireUser(req)` giữ nguyên hành vi, không đổi `errorResponse`. Cho T072 xanh. Báo nhóm: mọi function dùng `requireUser` tự được bảo vệ
- [ ] T078 [US6] Sửa `FN/_shared/identity.ts` (010): thêm `deleteUser(uid)` (`accounts:delete`; Emulator dùng `Authorization: Bearer owner`; `USER_NOT_FOUND` coi như xong). Cho T073 xanh
- [ ] T079 [US6] Tạo `FN/_shared/account-deletion.ts` (A): `computeImpact(uid, now)` (đơn `HOLD`, `PENDING`, `CONFIRMED` chưa `checkedInAt` và `endAt > now`; lượt `REGISTERED` của buổi chưa bắt đầu; lượt `WAITLISTED`; cơ sở `ownerId == uid` cùng đơn và buổi sắp tới của khách), `impactVersion` = `"sha256:" + SHA-256(chuỗi nối ID đã sắp xếp)`, `estimateTransforms(impact)` = `2 × (đơn + lượt vãng lai + lượt chờ + buổi bị hủy + lượt của khách trong các buổi đó) + 2 × (số ví được hoàn) + số cơ sở + 3` (mỗi helper của B, C, D export `TRANSFORMS_PER_CALL`, thiếu thì dùng 2; ngưỡng **450**), `commitDeletion(tx, uid, impact)` (gọi helper trong **một** `runTransaction` của `_shared/firestore.ts`, ghi `users/{uid}.deletionRequestedAt = serverTime`, `fcmTokens = []`, tạo `accountDeletions/{uid}` với `state = COMMITTED`, `requestedAt`, `wasOwner`, `notify` (`{ kind: BOOKING | DROPIN, id, recipientUid }`), `steps = {}`, `attempts = 0`, `finalSweepAfter = requestedAt + 65 phút`), và `runCleanup(uid, deadline)` với **10 bước cố định** `disableAuth`, `notifyGuests` (khóa idempotent `{kind}_{id}_{recipientUid}_OWNER_DELETED`, để 2 khách cùng một buổi không trùng khóa), `anonymizeReviews`, `anonymizeCommunity`, `anonymizeBookings`, `deleteOwnerData`, `deleteSubcollections` (`favorites`, `searchHistory`, `aiUsage`, `notifications`, batch 400), `deleteAvatars` (liệt kê và xóa `avatars/{uid}/*` bằng Storage API + service role), `deleteUserDoc`, `deleteAuthUser` (**sau cùng**); bước `DONE` thì bỏ qua; lỗi → `FAILED` + `lastError` (chỉ mã lỗi, không có dữ liệu người dùng) và dừng; helper chưa có → `SKIPPED`; xong hết → `state = DONE`, `completedAt`
- [ ] T080 [US6] Tạo `FN/delete-account/handler.ts` + `FN/delete-account/index.ts` đúng [contract §1](contracts/edge-functions-account.md#1-delete-account): `preview` (chỉ đọc, `requireUser(req)`) và `confirm` theo 8 bước (`requireUser(req, { allowDeleting: true })`, `403 REAUTH_REQUIRED` khi `now − authTime > 300`, `403 ADMIN_SELF_DELETE`, `400 CONFIRM_TEXT_MISMATCH` khi `confirmText.trim().uppercase() != "XÓA"`, `200 DELETED` nếu đã `DONE`, bỏ qua chốt nếu đã `COMMITTED`, `409 IMPACT_CHANGED { preview }`, `422 DELETE_TOO_LARGE`, `500 INTERNAL` khi transaction lỗi, `runCleanup` với `deadline = start + 100 s`, `202 CLEANUP_SCHEDULED` khi chưa xong); `400 INVALID_ARGUMENT` cho `action` lạ. Cho T075 xanh, kể cả `deno task test:flaky`
- [ ] T081 [US6] Tạo `FN/delete-account-sweeper/index.ts` (kiểm header `CRON_SECRET`, tối đa 5 bản ghi `state == COMMITTED order by requestedAt`, tổng 120 giây, lượt quét cuối, cảnh báo `attempts >= 20`) và migration `supabase/migrations/<timestamp>_cron_delete_account_sweeper.sql` (`pg_cron` mỗi 5 phút + `net.http_post` tới `/functions/v1/delete-account-sweeper`, secret lấy từ Vault như `dropin-close` của 081; không ghi secret vào migration). Cho T076 xanh
- [ ] T082 [P] [US6] Tạo `scripts/admin/rerun-deletion-step.ts <step>`: chạy lại bước `<step>` cho mọi `accountDeletions` có `steps.<step> == "SKIPPED"` (dùng khi helper của B, C, D có sau), in số bản ghi đã xử lý
- [ ] T083 [US6] Khai báo `delete-account`, `delete-account-sweeper` trong `supabase/config.toml` (`verify_jwt = false` cho cả hai, như `on-signup` của 010: sweeper xác thực bằng `CRON_SECRET`, `delete-account` bằng `requireUser`); **không** deploy và không đẩy secret lên project thật (theo quy ước nhóm, PR #20/#21 đang chờ)

### Tests for User Story 6 – app (viết trước, phải fail)

- [ ] T084 [P] [US6] Unit test `TEST/feature/account/domain/validation/DeleteConfirmRuleTest.kt`: `"XÓA"`, `" XÓA "`, `"xóa"` → hợp lệ (so sau `trim` + chữ hoa); `"XOA"`, `"xoa"`, `""` → `account_error_delete_confirm`
- [ ] T085 [P] [US6] Unit test `TEST/feature/account/data/AccountFunctionsApiTest.kt`: ánh xạ body lỗi `REAUTH_REQUIRED` → `ReauthRequired`, `ADMIN_SELF_DELETE` → `AdminSelfDelete`, `IMPACT_CHANGED` (có `preview`) → `ImpactChanged(newImpact)`, `DELETE_TOO_LARGE` → `DeleteTooLarge`, `CONFIRM_TEXT_MISMATCH` → lỗi ô xác nhận, `500` → `Unknown`; `200 DELETED`, `202 CLEANUP_SCHEDULED` và `403 ACCOUNT_DELETING` (đã chốt ở lần gọi trước) đều là thành công; timeout `confirm` **120 giây**, `preview` **15 giây**
- [ ] T086 [P] [US6] Unit test `TEST/feature/account/ui/delete/DeleteAccountViewModelTest.kt` (Turbine): nút "Xóa vĩnh viễn tài khoản" chỉ bật khi **đủ 3 điều kiện** (đã xác nhận danh tính hoặc nhập mật khẩu, `DeleteConfirmRule` đúng, đã đánh dấu "Tôi hiểu…") (US6-2); `ImpactChanged` → cập nhật `impact`, **bỏ đánh dấu** ô "Tôi hiểu…"; `ReauthRequired` → sự kiện mở lại xác nhận danh tính; `500` → giữ nguyên màn hình và dữ liệu đã nhập, báo "Chưa xóa được tài khoản. Tài khoản vẫn dùng bình thường, vui lòng thử lại" (FR-021); **hết giờ hoặc mất mạng** khi gọi `confirm` → tự gọi `confirm` lại **một lần** với cùng `impactVersion` (idempotent) rồi mới báo lỗi, và lỗi lúc này là "Chưa rõ kết quả, vui lòng kiểm tra kết nối rồi thử lại" (không khẳng định tài khoản còn dùng được); thành công → `SessionManager.signOut(ACCOUNT_DELETED)`; bấm lặp khi `isDeleting` không gọi thêm

### Implementation for User Story 6 – app

- [ ] T087 [P] [US6] Tạo `ACC/domain/validation/DeleteConfirmRule.kt`. Cho T084 xanh
- [ ] T088 [US6] Tạo `ACC/data/AccountFunctionsApi.kt` (`@Serializable` request/response của `preview`, `confirm`; gọi `supabase.functions.invoke("delete-account")` qua `invokeWithAuthRetry` của 010). Cho T085 xanh
- [ ] T089 [US6] Tạo `ACC/domain/usecase/LoadDeletionImpactUseCase.kt` và `ACC/domain/usecase/DeleteAccountUseCase.kt` (xác nhận lại danh tính bằng `ReauthenticateUseCase` → `confirm` với `impactVersion` → hết giờ thì gọi lại một lần → `SessionManager.signOut(ACCOUNT_DELETED)`)
- [ ] T090 [US6] Tạo `ACC/ui/delete/DeleteAccountViewModel.kt` + `ACC/ui/delete/DeleteAccountFragment.kt` + `RES/layout/fragment_account_delete.xml` theo wireframe `ui/01_account_buoc5_xoa-tai-khoan.png` và [ui-screens §2](contracts/ui-screens.md#2-chi-tiết-theo-wireframe): thẻ vàng "N đơn sắp tới sẽ bị hủy" liệt kê từng đơn (cơ sở, giờ, trạng thái, tiền cọc, tiền hoàn), lượt vãng lai, lượt chờ; thẻ đỏ cho chủ sân; "Sẽ bị xóa vĩnh viễn" và "Được giữ lại ở dạng ẩn danh"; dòng "Số dư ví giả lập X đ và tiền hoàn sẽ bị xóa, không quy đổi được" (FR-022); xác nhận danh tính theo `ReauthMethod` (ô mật khẩu ngay trên màn hình, nút "Xác nhận bằng Google", hoặc "Gửi mã OTP" mở `ReauthDialog`); ô "Gõ XÓA để xác nhận"; ô đánh dấu; nút "Xóa vĩnh viễn tài khoản" và "Giữ tài khoản"; trạng thái đang tải bản xem trước, có dữ liệu, lỗi + "Thử lại". Cho T086 xanh
- [ ] T091 [US6] Nối mục "Xóa tài khoản" (chữ đỏ, ẩn với admin) trong `AccountFragment` (theo `accountEntries` T029)

**Checkpoint**: `deno test -A delete-account/ delete-account-sweeper/ _shared/` xanh; `deno task test:flaky` xanh 10/10; quickstart Q23–Q26 đạt (với helper giả thì ghi rõ bước `SKIPPED`).

---

## Phase 9: User Story 7 – Đổi email đăng nhập (Priority: P3)

**Goal**: đổi email qua link xác minh; luôn hiện cùng một thông báo (US7-2 đã sửa theo R16); `users.email` do server đồng bộ.

**Independent Test**: đổi email, bấm link, đăng nhập bằng email mới được, cũ không được; `users.email` = email mới; đổi sang email của tài khoản khác thì thấy cùng thông báo và không có thư (quickstart Q21–Q22).

**Phụ thuộc**: Phase 2B.

### Tests for User Story 7 (viết trước, phải fail)

- [ ] T092 [P] [US7] Viết test `FN/sync-account-email/index.test.ts` đủ **E1–E3** của [contract §5](contracts/edge-functions-account.md#5-test-deno-test-firebase-emulator-auth--firestore): token có email mới đã xác minh → `users.email` = email mới chữ thường, `updated: true`; gọi lại → `updated: false`; `email_verified = false` → không ghi, `updated: false`; body có trường `email` bị bỏ qua (chống giả)
- [ ] T093 [P] [US7] Unit test `TEST/feature/account/ui/email/ChangeEmailViewModelTest.kt` (fake `ChangeEmailUseCase`): email sai định dạng (`EmailRule` của 010) → `emailError`; email mới (sau `trim().lowercase()`) bằng email hiện tại → `account_error_same_email`, không gọi use case; `WrongCurrentPassword` → `passwordError`; thành công **và** trường hợp email đã thuộc tài khoản khác (Firebase không báo lỗi) đều cho `sent = true` với cùng một thông báo (US7-1, US7-2); `isSubmitting` khóa nút
- [ ] T094 [P] [US7] Unit test `TEST/feature/account/data/EmailSyncObserverTest.kt`: `SignedIn` mà email trong token khác `MyProfile.email` → gọi `sync-account-email` đúng 1 lần cho mỗi giá trị email; giống nhau → không gọi; lỗi → bỏ qua, không crash

### Implementation for User Story 7

- [ ] T095 [US7] Tạo `FN/sync-account-email/index.ts` đúng [contract §3](contracts/edge-functions-account.md#3-sync-account-email): `requireUser(req)`, lấy `email`, `email_verified` **từ token**, ghi `users.email = email.lowercase()`, `updatedAt = serverTime` khi đã xác minh và khác; trả `200 { updated }`; khai báo trong `supabase/config.toml` với `verify_jwt = false` (xác thực bằng `requireUser`). Cho T092 xanh
- [ ] T096 [US7] Thêm `verifyBeforeUpdateEmail(newEmail)` vào `ACC/data/FirebaseReauthDataSource.kt` và tạo `ACC/domain/usecase/ChangeEmailUseCase.kt` (`reauthWithPassword` → `verifyBeforeUpdateEmail`)
- [ ] T097 [US7] Tạo `ACC/ui/email/ChangeEmailViewModel.kt` + `ACC/ui/email/ChangeEmailFragment.kt` + `RES/layout/fragment_account_change_email.xml` (ô email mới, ô mật khẩu hiện tại, nút "Gửi email xác minh"; sau khi gửi hiện thông báo chung của R10); nối mục "Đổi email đăng nhập" trong `AccountFragment` (chỉ khi có provider `password`). Cho T093 xanh
- [ ] T098 [US7] Tạo `ACC/domain/usecase/SyncAccountEmailUseCase.kt` (gọi `sync-account-email` qua `AccountFunctionsApi`, timeout 15 s) và `ACC/data/EmailSyncObserver.kt` (`@Singleton`, thu thập `sessionState` + `observeMyProfile()`), khởi động từ `APP/SmashNowApp.kt` (tầng app được phụ thuộc `feature/`, `core/` thì không). Cho T094 xanh

**Checkpoint**: `deno test -A sync-account-email/` xanh; quickstart Q21, Q22 đạt; ghi kết quả thật của Q22 cho người dùng tự ghi vào log AI.

---

## Phase 10: Polish & Cross-Cutting

- [ ] T099 [P] Cập nhật `docs/design/database/README.md` theo **8 thay đổi** cuối [data-model.md](data-model.md#danh-sách-thay-đổi-cần-đưa-vào-docsdesigndatabasereadmemd):
  1. mục 4.1 `users`: `avatarPath` thành `{uid}/avatar_{epochMillis}.jpg`, thêm `deletionRequestedAt` 🔒, câu "Xóa tài khoản…" thay bằng tóm tắt 2 giai đoạn + link contract `delete-account`;
  2. mục 2: thêm `accountDeletions/{uid}` (A, chỉ server) và bảng trường `districts/{id}`;
  3. mục 4.3 `venues`: `hiddenReason` 🔒 (`OWNER_DELETED`), **chờ D đồng ý**;
  4. mục 4.5 `bookings`: `userDeleted` (Boolean), **chờ B đồng ý**;
  5. mục 6: index `districts` (`isActive`, `sortOrder`), `ownerApplications` (`uid`, `submittedAt desc`), `accountDeletions` (`state`, `requestedAt`);
  6. mục 7: `districts` "R (đã khởi tạo); admin ghi", `accountDeletions` "chỉ server";
  7. mục 8 (Storage): `avatars` object `{uid}/avatar_{epochMillis}.jpg`, không cho `UPDATE`, ghi cần `appRole`;
  8. mục 8a: mô tả lại `delete-account` (preview/confirm, 2 giai đoạn), thêm `delete-account-sweeper` (pg_cron 5 phút) và `sync-account-email` (A).
- [ ] T100 [P] Rà soát giao diện 6 màn hình (Tài khoản, Sửa hồ sơ, Cắt ảnh, Đổi mật khẩu, Đổi email, Xóa tài khoản), 2 bottom sheet (Chọn khu vực, Nguồn ảnh) và hộp thoại xác nhận danh tính ở chế độ tối, máy tính bảng `w600dp` (`RES/layout-w600dp/` nếu cần), TalkBack: `contentDescription` cho ảnh và icon, vùng chạm ≥ 48dp, mất mạng hiện `Snackbar` "Không có kết nối mạng" + "Thử lại" (FR-023, FR-025)
- [ ] T101 [P] (Nên có) Espresso `ATEST/feature/account/EditProfileTest.kt` trên Emulator: sửa tên, chọn trình độ, lưu → "Đã lưu"; sửa rồi Back → hộp thoại "Bỏ các thay đổi chưa lưu?"
- [ ] T102 Kiểm tra trước demo: không còn bước `SKIPPED` trong `accountDeletions` của dữ liệu demo; helper nào của B, C, D đã có thì bỏ bản giả trong `FN/_shared/account-deletion.ts` và chạy `scripts/admin/rerun-deletion-step.ts` (theo bảng phụ thuộc bên dưới)
- [ ] T103 Chạy toàn bộ kịch bản thủ công **Q1–Q28** của [quickstart.md §5](quickstart.md#5-kịch-bản-thủ-công-trên-máy) trên môi trường cục bộ của T005 (Android Emulator hoặc máy thật trỏ `10.0.2.2`, Firebase Emulator, Supabase local; **không** chạy trên project thật, constitution IV), tài khoản thử tạo theo quickstart §0 trên Emulator; Q5, Q8, Q9 chỉ chạy khi T005 xác nhận Supabase local nhận token Emulator hoặc nhóm đã chốt cách khác; lưu ảnh hoặc video Q1, Q5, Q13, Q15, Q23, Q25 vào `docs/features/01-account/screenshots/`; Q5 đo 20 lần (≥ 19 lần < 5 giây, SC-002); Q22 ghi kết quả thật
- [ ] T104 Thử với 5 người chưa dùng app (SC-009): mỗi người tự tìm và mở "Đăng ký làm chủ sân" từ màn hình chính, không được hướng dẫn; đạt khi ≥ 4/5 người làm được; ghi kết quả vào `docs/features/01-account/usability-sc009.md` (không ghi tên người thử)
- [ ] T105 Chạy `./gradlew lint testDebugUnitTest assembleDebug`, `npm test` trong `firestore-tests/`, `npx supabase test db`, `deno test -A delete-account/ delete-account-sweeper/ sync-account-email/ _shared/` (với Emulator) và `deno task test:flaky`; không còn cảnh báo Lint mức Error; kiểm tra `git status` không có `google-services.json`, `local.properties`, `supabase/.env`, service account

---

## Dependencies & Execution Order

### Phase

- **Setup (Phase 1)**: cần PR `chore(core)` của 010 đã merge.
- **2A** (PR `chore(core)` của 011): sau Phase 1. Chặn mọi story vì `MyProfile`, `SignOutReason`, `AppModeController`, `StorageUrls`, `MainActivity` mới.
- **2B** (xác nhận lại danh tính): sau 2A. Chặn US5, US6, US7; US1–US4 không cần. Xác nhận bằng OTP cần T025.
- **2C** (khung trang Tài khoản): sau 2A. Chặn mọi story.
- **US1, US2, US3**: sau 2C, độc lập nhau. US2 có T054 thuộc `core/` (có thể gộp vào PR 2A nếu muốn D dùng sớm).
- **US4**: sau US3 (khối chuyển chế độ nằm trong mục chủ sân, T061).
- **US5, US7**: sau 2B, độc lập nhau.
- **US6**: sau 2B; phần server (T072–T083) làm song song với phần app (T084–T091) được.
- **Polish**: sau các story cần demo.

### Trong mỗi story

- Test viết trước và phải fail → model/rule → data source/repository → use case → ViewModel → Fragment/layout.
- Rules (T039) và migration (T040) trước data source dùng chúng (T042, T044, T045).
- `_shared/auth.ts`, `_shared/identity.ts` (T077, T078) trước `account-deletion.ts` (T079); T079 trước `handler.ts` (T080) và sweeper (T081).

### Phụ thuộc nhóm khác và phương án tạm

| Task | Phụ thuộc | Phương án tạm nếu chưa có |
|---|---|---|
| T079, T080 | **B**: `_shared/firestore.ts` có `runTransaction` với `runQuery` trong transaction (cũng cần cho 080) | Dừng phần server US6, làm trước phần app (T084–T091) với API giả; báo B |
| T079 (chốt) | **B**: `cancelBookingInTx(tx, booking, { cancelledBy, reason, refund: "POLICY" \| "FULL" })`, `computeBookingRefund(booking, now)` trong `_shared/booking.ts`, xóa slot trong cùng transaction | Dùng bản giả trong `test_fakes.ts` cho test; trong code thật, bước hủy đơn **không được** ghi `SKIPPED` vì thuộc bước chốt: nếu chưa có helper thì `confirm` trả `500 INTERNAL` khi người dùng có đơn sắp tới (an toàn theo FR-021), người không có đơn vẫn xóa được |
| T079 (dọn dẹp) | **B**: `anonymizeBookings(uid)` + trường `bookings.userDeleted` | Bước `anonymizeBookings` ghi `SKIPPED` (R12) |
| T079 (chốt) | **C**: `cancelRegistrationInTx` (080), `leaveWaitlistInTx` (081) | Như dòng `cancelBookingInTx`: chưa có thì `confirm` trả `500` khi có lượt vãng lai/lượt chờ sắp tới |
| T079 (dọn dẹp) | **C**: `anonymizeReviews(uid)` (070 R9), `anonymizeCommunity(uid)` (090, 091) | Bước tương ứng ghi `SKIPPED` |
| T079 (chốt) | **D**: `hideVenueForOwnerDeletionInTx(tx, venueId)` + `venues.hiddenReason` 🔒, `cancelSessionInTx(tx, session, { reason, refund: "FULL" })` (112) | Chưa có thì `confirm` trả `500` với chủ sân có cơ sở; người chơi thường vẫn xóa được |
| T079 (dọn dẹp) | **D**: `send-notification` có khóa idempotent; `deleteOwnerApplications(uid)` (+ `owner-docs/{uid}/`) | `notifyGuests`, `deleteOwnerData` ghi `SKIPPED`; khi có helper chạy `scripts/admin/rerun-deletion-step.ts` (T082) |
| T029, T051, T061, T064 | **B, C, D**: deep link `smashnow://wallet/topup`, `bookings/mine`, `dropin/mine`, `favorites`, `chat/owner`, `notifications/settings`, `owner/apply`, `owner/application`, `owner/venues` | Mục chưa có destination bị ẩn (R14) |
| T061 | **D**: xác nhận màn hình "Hồ sơ chủ sân" (wireframe `buoc4`) thuộc spec 110 | Trang Tài khoản chỉ hiện trạng thái, không có nút xem chi tiết |
| Q14, Q16 | **D**: duyệt hồ sơ, thu hồi quyền ghi `ownerRevokedReason` cùng `ownerStatus = NONE`, `mergeClaims` trước | Dùng `scripts/admin/grant-role.ts` của 010 và ghi tay `ownerRevokedReason` trên Emulator |
| T099 mục 3, 4 | **D** đồng ý `venues.hiddenReason`; **B** đồng ý `bookings.userDeleted` | Ghi vào README với nhãn "chờ xác nhận" |

| T025, T026 | **010 (A)**: cách lấy `Activity` cho `verifyPhoneNumber` (analyze 010, H2) | Tạo `CurrentActivityProvider` ở 011 rồi để 010 dùng chung |
| T103 (Q5, Q8, Q9) | **Cả nhóm**: cách chạy kịch bản Storage cục bộ khi Supabase local không nhận token Auth Emulator (T005) | Tạm dựa vào pgTAP ST1–ST8 (T032); chưa chạy Q5, Q8, Q9 cho tới khi nhóm chốt |

Bước **chốt** không bao giờ ghi `SKIPPED` (bỏ qua hủy đơn mà vẫn xóa thì slot bị chiếm mãi, sai FR-022a); chỉ bước **dọn dẹp** mới được `SKIPPED` ([research R12](research.md#r12-ẩn-danh-và-các-bước-do-nhóm-khác-cung-cấp)). Các helper đã có chữ ký trong bảng phối hợp của [plan.md](plan.md#phối-hợp-với-nhóm); chốt ở họp 16/10.

---

## Parallel Example: Phase 2A

```text
Task: "T006 DefaultAppModeControllerTest trong TEST/core/session/"
Task: "T007 FirebaseSessionManagerSignOutTest trong TEST/core/session/"
Task: "T008 StorageUrlsTest trong TEST/core/media/"
Task: "T009 RootGraphTest trong TEST/core/session/"
Task: "T010 FunctionErrorsTest trong TEST/core/network/"
```

## Parallel Example: User Story 1

```text
Task: "T031 Rules P1–P18, D1–D4, A1 trong firestore-tests/test/"
Task: "T032 pgTAP ST1–ST8 trong supabase/tests/storage_avatars_test.sql"
Task: "T033 DistrictSelectionRuleTest"
Task: "T034 AvatarImageRuleTest"
Task: "T035 ChangeAvatarUseCaseTest"
Task: "T036 EditProfileViewModelTest"
```

## Parallel Example: User Story 6

```text
Task: "T072 _shared/auth.test.ts (ACCOUNT_DELETING, authTime)"
Task: "T073 _shared/identity.test.ts (deleteUser)"
Task: "T074 delete-account/test_fakes.ts"
Task: "T084 DeleteConfirmRuleTest"
Task: "T085 AccountFunctionsApiTest"
Task: "T086 DeleteAccountViewModelTest"
```

---

## Implementation Strategy

### MVP first (US1 + US2)

1. Phase 1 → **2A, mở PR `chore(core)` ngay** để nhóm dùng `StorageUrls`, `SignOutHook`, `AppModeController`.
2. 2C → US1 (sửa hồ sơ, ảnh đại diện, Rules, Storage) → US2 (đăng xuất).
3. **Dừng và kiểm tra**: quickstart Q1–Q12; demo được trang Tài khoản cơ bản.

### Tăng dần

1. US3 (mục chủ sân) → US4 (chuyển chế độ): cần cho nhóm D test luồng chủ sân.
2. 2B → US5 (đổi mật khẩu).
3. US6 (xóa tài khoản): làm phần server sớm sau họp 16/10 vì phụ thuộc helper B, C, D nhiều nhất; phần app song song.
4. US7 (đổi email).
5. Polish: README CSDL, rà soát giao diện, Q1–Q27.

### Làm song song (nếu có người hỗ trợ)

- Một người: 2A → US2 → US4 (phần `core/session`).
- Một người: US1 (Rules, Storage, màn hình sửa hồ sơ).
- Sau 2B: US6 server và US5/US7 app song song.
