# Research: Đăng ký và đăng nhập tài khoản (spec 010)

Phase 0 của `/speckit-plan`. Mỗi mục ghi **Quyết định**, **Lý do** và **Phương án đã cân nhắc**. Các giới hạn của Firebase được kiểm chứng ngày 15/10/2026 trên tài liệu chính thức (Firebase Auth Limits, Email enumeration protection, Verified email addresses, Phone Auth Android, Password policy).

---

## R1. Khởi tạo tài khoản: app gọi `on-signup`, không dùng trigger

**Quyết định:** Edge Function `on-signup` được **app gọi** ngay sau khi Firebase Auth tạo người dùng, kèm Firebase ID token ở header `Authorization`. Function:

1. Tự xác minh token (R3).
2. Tạo `users/{uid}` nếu chưa có.
3. Rồi mới gộp custom claim `role: "authenticated"`, `appRole` vào claim hiện có.

App gọi `getIdToken(true)` sau khi function trả `200`. Chi tiết ở [contracts/edge-function-on-signup.md](contracts/edge-function-on-signup.md).

**Lý do:**
- Gói Spark không có Cloud Functions, nên không có trigger `auth.user().onCreate`.
- Blocking functions (`beforeUserCreated`) cần Cloud Functions và Identity Platform.
- Supabase không nhận được sự kiện tạo tài khoản của Firebase.
- Nên chỉ còn cách client gọi.

**Phương án đã cân nhắc:**
- Client tự tạo `users/{uid}` bằng Rules: không gán được custom claim. Nếu cho client ghi `role`, `accountStatus` thì vi phạm nguyên tắc II. Bị loại.
- pg_cron quét tài khoản Firebase mới mỗi phút: người dùng phải chờ tới 1 phút, và vẫn phải xử lý đồng ý điều khoản ở client. Bị loại.

## R2. Thứ tự ghi và tính idempotent

**Quyết định:** `on-signup` ghi **document trước, claim sau**, và an toàn khi gọi lại nhiều lần.

- **Document chưa có:** tạo bằng REST `commit` với điều kiện `currentDocument.exists = false`. Nếu hai lần gọi chạy song song thì lần sau nhận `ALREADY_EXISTS` và được coi là thành công.
- **Document đã có:** không ghi đè trường nào.
- **Claim:**
  - Đọc claim hiện tại bằng `accounts:lookup`.
  - Gộp thêm `role = "authenticated"`.
  - `appRole` lấy theo thứ tự: claim hiện có, rồi `users.role`, rồi `"PLAYER"`. **Không bao giờ hạ quyền** (OWNER/ADMIN giữ nguyên).
  - Chỉ gọi `accounts:update` khi claim thực sự thay đổi.

**Lý do:**
- App coi "đã khởi tạo" là **token có claim `appRole`** (R4). Ghi claim sau cùng nên khi có claim thì document chắc chắn đã có.
- Nếu lỗi giữa hai bước thì lần gọi lại sẽ bỏ qua bước tạo document và chỉ gán claim. Đây là edge case "tạo tài khoản xong nhưng khởi tạo lỗi".

**Phương án đã cân nhắc:**
- Ghi claim trước: app có thể thấy `appRole` trong khi chưa có document, các màn hình đọc `users/{uid}` sẽ lỗi. Bị loại.
- Dùng transaction cho cả hai: claim nằm ở Identity Toolkit, không thuộc Firestore transaction, nên không có lợi gì.

## R3. Xác thực token trong Edge Function

**Quyết định:**
- `supabase/functions/_shared/auth.ts` (A viết, dùng chung cả nhóm; plan 070 T020 và 080 T007 cũng cần file này) xác minh Firebase ID token bằng thư viện `jose`.
- JWKS lấy từ `https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com`.
- Kiểm tra `iss = https://securetoken.google.com/<projectId>`, `aud = <projectId>`, `exp`, `sub`.
- Hàm trả về `{ uid, appRole, email, emailVerified, phone, signInProvider, name }`.
- Có hai hàm:
  - `requireUser(req)`: dùng cho mọi function khác. Đọc `users/{uid}.accountStatus` và trả `403 ACCOUNT_LOCKED` khi bị khóa. Token chưa có `appRole` thì trả `403 NOT_INITIALIZED`.
  - `verifyIdToken(req)`: chỉ dùng cho `on-signup`. Không yêu cầu `appRole`.
- `on-signup` đặt `verify_jwt = false` trong `supabase/config.toml` và tự xác minh token. Lý do: token lúc đó chưa có claim `role` nên gateway Supabase không coi là `authenticated`.
- Khi biến môi trường `FIREBASE_AUTH_EMULATOR_HOST` được đặt, `_shared/auth.ts` chấp nhận token không ký của Auth Emulator. **Chỉ dùng khi test.**

**Lý do:** Third-party Auth của Supabase chỉ tin token có `role = authenticated`. Token ngay sau khi đăng ký chưa có claim này.

**Phương án đã cân nhắc:** `npm:firebase-admin` trong Deno nặng, khởi động chậm và không khớp với cách gọi REST của `_shared/firestore.ts` (B). Bị loại.

## R4. App nhận biết "chưa khởi tạo" bằng claim `appRole`

**Quyết định:**
- Token có claim `appRole` thì tài khoản **đã khởi tạo**.
- Không có claim thì app vào nhánh khởi tạo:
  1. Có `PendingSignup` trong DataStore cho đúng `uid` (người đăng ký email đã đánh dấu đồng ý và nhập họ tên trên form): app tự gọi lại `on-signup` và hiện **màn hình chờ** (`AccountSetupFragment`) có nút "Thử lại" khi lỗi.
  2. Không có `PendingSignup` (đăng nhập Google/OTP lần đầu, hoặc đã cài lại app): app hiện màn hình **Hoàn tất tài khoản** (`TermsConsentFragment`), họ tên điền sẵn từ Firebase. Phải đồng ý thì mới gọi `on-signup`.
- Mỗi lần mở app, `SessionManager` kiểm tra lại bước này. Đóng app ở bước đồng ý thì lần sau vẫn quay về đúng bước đó và không vào được màn hình chính.
- Nút "Dùng tài khoản khác" gọi `signOut()`. Tài khoản Firebase vẫn còn nhưng không có `users/{uid}` và không truy cập được dữ liệu gì (R10).

**Lý do:**
- Claim có sẵn trong token nên không tốn lượt đọc Firestore.
- Rules và Edge Function cũng kiểm tra đúng claim này, nên app và server thống nhất một nguồn sự thật.
- Trường hợp "có document nhưng thiếu claim" (lỗi ở bước 2 của R2, lại mất `PendingSignup`): app hiện màn hình đồng ý thêm một lần. `on-signup` thấy document đã có nên chỉ gán claim, không có hại.

**Phương án đã cân nhắc:** Kiểm tra document `users/{uid}`: tốn một lượt đọc mỗi lần mở app, lúc mất mạng thì không biết document chưa có hay chỉ chưa tải được, và vẫn có thể thiếu claim. Bị loại.

## R5. Khóa tài khoản (FR-014): `accountStatus` kèm cờ `disabled` của Firebase Auth

**Quyết định:**
- Khóa và mở khóa chỉ qua Edge Function **`set-account-status`** (D, spec 120), dùng helper `setUserDisabled()` trong `_shared/identity.ts` (A). Trong cùng một lần gọi, function:
  1. ghi `users/{uid}.accountStatus = LOCKED | ACTIVE`;
  2. gọi Identity Toolkit `accounts:update` với `disableUser: true | false`.
- Admin **không** sửa `accountStatus` trực tiếp bằng Rules. Đây là thay đổi so với đặc tả CSDL mục 7.

**Hiệu lực:**

| Tình huống | Cơ chế |
|---|---|
| Đăng nhập bằng email, Google hoặc OTP | Firebase từ chối với `ERROR_USER_DISABLED` (`FirebaseAuthInvalidUserException`). App hiện "Tài khoản của bạn đã bị khóa" kèm cách liên hệ hỗ trợ |
| Đang đăng nhập sẵn, mở lại app | `SessionManager` gọi `user.reload()` và `getIdToken(true)`. Firebase trả `USER_DISABLED` vì tài khoản bị tắt thì refresh token hết hiệu lực. App `signOut()` và hiện thông báo khóa |
| Đang dùng app, có mạng | Listener `users/{uid}` thấy `accountStatus == LOCKED` thì đăng xuất ngay (vượt yêu cầu "lần mở app sau" của spec) |
| Edge Function | `requireUser()` (R3) đọc `accountStatus` nên trả `403 ACCOUNT_LOCKED` |
| Ghi Firestore trực tiếp bằng ID token còn hạn | Còn hiệu lực tối đa 1 giờ. Chấp nhận rủi ro này: mọi thao tác nhạy cảm đều qua Edge Function, còn Rules của dữ liệu tự sửa được chỉ cho chủ tài khoản ghi dữ liệu của chính mình |

**Lý do:**
- Chỉ dùng `accountStatus`: app phải đọc document sau khi đăng nhập thành công rồi tự đăng xuất. Nếu người dùng gọi thẳng API (không qua app) thì vẫn lấy được token, nên bị loại.
- Dùng thêm cờ `disabled` thì Firebase chặn được mọi phương thức đăng nhập ở phía server (FR-014, SC-005), không phụ thuộc app.
- Identity Toolkit REST dùng được trên gói Spark (cùng API mà Admin SDK dùng).

**Phương án đã cân nhắc:**
- Kiểm tra `get(/users/$(uid)).data.accountStatus` trong mọi Rule: thêm một lượt đọc cho mỗi request, tốn hạn mức gói miễn phí. Bị loại.
- `revokeRefreshTokens` riêng: không cần, vì tắt tài khoản đã làm refresh token hết hiệu lực.

## R6. Điều hướng theo vai trò (FR-015) và cập nhật khi vai trò đổi

**Quyết định:**
- Điều hướng chỉ dựa trên claim `appRole` trong ID token:
  - `OWNER` vào `nav_owner`;
  - `ADMIN` vào `nav_admin`;
  - `PLAYER` vào `nav_player`.
- `users.role` chỉ là bản sao để hiển thị.
- Khi mở app (có mạng), `SessionManager` gọi `getIdToken(true)` nên claim luôn mới.
- Khi đang mở app, listener `users/{uid}` thấy `role` khác `appRole` của token thì gọi `getIdToken(true)` rồi điều hướng lại.
- Vì vậy Edge Function của D (`review-owner-application`, thu hồi quyền chủ sân ở spec 120) PHẢI **ghi claim trước, ghi document sau**, để lúc app làm mới token thì claim đã đổi.

**Lý do:**
- Claim là thứ Rules và Edge Function dùng để cấp quyền. Điều hướng theo document có thể đưa người bị thu hồi quyền vào giao diện chủ sân trong khi Rules đã chặn.
- Listener trên chính document của mình chỉ tốn 1 lượt đọc khi mở app, cộng thêm khi document thay đổi.

**Phương án đã cân nhắc:** Thêm trường `claimsUpdatedAt`: không cần, vì so sánh `role` với `appRole` là đủ.

## R7. Liên kết Google với email/mật khẩu cùng email (FR-012, analyze F3)

**Kiểm chứng:**
- Project tạo sau 15/09/2023 bật sẵn **Email enumeration protection** và cài đặt **"Link accounts that use the same email"** (một tài khoản cho mỗi email).
- Google chỉ là *trusted provider* với địa chỉ `@gmail.com`.
- Email/mật khẩu **chưa xác minh** là *untrusted*.

| Tình huống | Hành vi của Firebase | App xử lý |
|---|---|---|
| Đã đăng ký email/mật khẩu (`@gmail.com`) **đã xác minh**, sau đó đăng nhập Google | Tự liên kết, cùng `uid` | Không cần làm gì |
| Đã đăng ký email/mật khẩu (`@gmail.com`) **chưa xác minh**, sau đó đăng nhập Google | Google **ghi đè**: giữ `uid`, **gỡ mật khẩu** | Vẫn cùng tài khoản SmashNow. Muốn dùng lại mật khẩu thì người dùng "Quên mật khẩu" để đặt lại. Ghi chú vào spec (R14) |
| Email không phải `@gmail.com` (Google Workspace) trùng tài khoản mật khẩu | Báo lỗi `FirebaseAuthUserCollisionException` (`ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL`) | Hộp thoại **Liên kết tài khoản**: nhập mật khẩu của email đó, rồi `signInWithEmailAndPassword` và `linkWithCredential(googleCredential)` |

**Quyết định:**
- Giữ nguyên Email enumeration protection (bật).
- Không dùng `fetchSignInMethodsForEmail`, vì khi bật bảo vệ thì hàm này luôn trả danh sách rỗng.
- Thêm hộp thoại liên kết như bảng trên. Đây là hộp thoại nhỏ, không có trong wireframe; ghi ở [contracts/ui-screens.md](contracts/ui-screens.md).

**Lý do:** đáp ứng FR-012 (một tài khoản) mà không mở lỗ hổng dò email.

## R8. OTP số điện thoại (FR-016, FR-017, analyze F4)

**Kiểm chứng:**
- Gói Spark **không gửi được SMS thật**: "Verification code SMS messages: Pay as you go (Blaze) plan only".
- Chỉ dùng được **số thử nghiệm**, tối đa **10 số**, mỗi số có mã 6 chữ số cố định khai báo trên console.
- Firebase **không cho cấu hình** thời hạn mã hay số lần nhập sai.
- Có giới hạn 150 yêu cầu xác minh / IP / giờ.

**Quyết định:**
- `verifyPhoneNumber` với `PhoneAuthOptions` (timeout 60 giây, gắn với Activity).
- Số được chuẩn hóa về E.164 `+84xxxxxxxxx` trước khi gửi (FR-017). `0xxxxxxxxx` được đổi thành `+84xxxxxxxxx`. Đầu số di động hợp lệ: `3`, `5`, `7`, `8`, `9`.
- **5 phút** và **5 lần sai** (FR-016) do **app** thực thi trên phiên OTP (`OtpSession`: `verificationId`, `sentAt` (elapsedRealtime), `wrongCount`), lưu trong `SavedStateHandle`.
  - Hết 5 phút hoặc sai đủ 5 lần thì app bỏ `verificationId`, bắt buộc "Gửi lại mã".
  - Lỗi `ERROR_SESSION_EXPIRED` của Firebase cũng hiện là "Mã đã hết hạn".
- Bảng đổi lỗi sang thông báo:

| Lỗi | Thông báo |
|---|---|
| `ERROR_INVALID_VERIFICATION_CODE` | "Mã không đúng" (tăng `wrongCount`) |
| `FirebaseTooManyRequestsException` | "Bạn đã thử quá nhiều lần, vui lòng thử lại sau" |
| Lỗi do số không phải số thử nghiệm trên Spark (`ERROR_QUOTA_EXCEEDED` / `BILLING_NOT_ENABLED`) | "Không gửi được mã tới số này. Hãy dùng email hoặc Google" |

**Giới hạn cần sửa spec (R14):** với số thử nghiệm, mã là cố định và không hết hạn ở phía Firebase. "Vô hiệu mã" ở FR-016 vì vậy là hành vi của app, không phải đảm bảo của server.

## R9. Đăng ký, đăng nhập email và quên mật khẩu (FR-004, FR-011, FR-018, FR-019, analyze F8)

**Kiểm chứng:**
- Khi bật Email enumeration protection:
  - đăng nhập sai trả `INVALID_LOGIN_CREDENTIALS`, không phân biệt email sai hay mật khẩu sai (đúng FR-011);
  - `sendPasswordResetEmail` không báo lỗi khi email chưa đăng ký (đúng FR-018);
  - đăng ký vẫn trả `EMAIL_EXISTS` (cần cho US1-3).
- Hạn mức gói Spark mỗi ngày: **150 email đặt lại mật khẩu**, **1000 email xác minh**.
- Password policy hỗ trợ độ dài tối thiểu 6–30 và yêu cầu chữ số, chữ thường, chữ hoa. **Không có lựa chọn "có ít nhất một chữ cái bất kỳ".**

**Quyết định:**
- **Password policy** trên console: `minLength = 8`, bắt buộc có chữ số, chế độ **Require**. Policy này áp dụng cho cả đặt lại mật khẩu trên trang của Firebase (FR-019).
- Yêu cầu "có chữ" do app kiểm tra (`PasswordRule`). Trang đặt lại mật khẩu do Firebase lưu trữ chỉ bảo đảm ≥ 8 ký tự và có số (ghi vào R14).
- Email được chuẩn hóa: `trim().lowercase()` trước khi đăng ký, đăng nhập, quên mật khẩu.
- Đặt lại mật khẩu dùng trang mặc định của Firebase, không làm deep link trong app (FR-019 không yêu cầu). Firebase làm mật khẩu cũ hết hiệu lực.
- Email xác minh: `sendEmailVerification()` ngay sau khi tạo tài khoản. Nếu gửi lỗi thì vẫn cho dùng app, banner có nút gửi lại.
  - Mỗi lần `onResume` của màn hình có banner, app gọi `user.reload()`. Thấy `isEmailVerified` thì gọi `getIdToken(true)` rồi ẩn banner.
  - Mốc chờ 60 giây giữa hai lần gửi lại lưu trong DataStore (`lastVerificationEmailAt`) để không mất khi xoay màn hình hay đóng app.
- Lỗi `TOO_MANY_ATTEMPTS_TRY_LATER` (`FirebaseTooManyRequestsException`) hiện "Bạn đã thử quá nhiều lần...".

## R10. Security Rules cho `users/{uid}`

**Quyết định** (chi tiết ở [contracts/security-rules.md](contracts/security-rules.md)):

| Quyền | Ai được |
|---|---|
| `read` | Chủ tài khoản hoặc admin. **Không cho người dùng khác đọc**, vì document chứa `email`, `phone`, `walletBalance`, `fcmTokens` (nguyên tắc VI). Tính năng khác cần tên hoặc ảnh người khác thì dùng snapshot (như `userSnapshot` của 070) |
| `create` | Không ai (chỉ `on-signup` tạo) |
| `delete` | Không ai (chỉ `delete-account` của spec 011 xóa) |
| `update` | Chủ tài khoản, chỉ các trường `displayName`, `avatarPath`, `level`, `favoriteDistrictIds`, `fcmTokens`, `notificationPrefs`, `updatedAt`. `updatedAt == request.time`. `displayName` dài 2–50 ký tự. Spec 011 thêm kiểm tra riêng cho từng trường |

Rules nền có hai thay đổi:
- `appRole()` không mặc định `'PLAYER'` nữa mà trả `null` khi thiếu claim.
- Thêm `isInitialized()`: tài khoản đã xác thực Firebase nhưng chưa qua `on-signup` thì không được coi là người chơi.

**Lý do:** đặc tả CSDL mục 7 ghi "R mọi người (trường công khai)". Nhưng Firestore không lọc được theo trường khi đọc, nên mở quyền đọc là lộ email, số điện thoại và số dư ví.

**Phương án đã cân nhắc:** Tách collection `publicProfiles/{uid}`. Hiện chưa có spec nào cần đọc hồ sơ người khác (070 và 080 dùng snapshot). Theo YAGNI thì để khi có spec cần mới làm.

## R11. Phần `core/` dùng chung cho cả nhóm

**Quyết định:** spec 010 đặt các thành phần sau ở `core/`. Các tính năng khác chỉ dùng qua interface (nguyên tắc I).

| Thành phần | Package | Việc |
|---|---|---|
| `SessionManager` (interface) + `FirebaseSessionManager` | `core/session` | `sessionState: StateFlow<SessionState>`, `refresh()`, `signOut()`, `currentUid()`, `requireAppRole()` |
| `SessionState` | `core/session` | `Loading`, `SignedOut`, `NeedsConsent(prefill)`, `Initializing`, `InitFailed(error)`, `Locked`, `SignedIn(uid, appRole, emailVerified)` |
| `AppRole` | `core/session` | enum `PLAYER`, `OWNER`, `ADMIN` |
| `IdTokenProvider` | `core/network` | `getToken(forceRefresh)`. Dùng chung cho supabase-kt và mọi lời gọi Edge Function |
| `SupabaseModule` | `core/di` | Tạo `SupabaseClient` với `accessToken = { idTokenProvider.getToken(false) }` (Third-party Auth) |
| `FirebaseModule` | `core/di` | `FirebaseAuth`, `FirebaseFirestore`. Bản debug trỏ tới Emulator khi `BuildConfig.USE_EMULATOR` |
| `NetworkResult` / `AppError` | `core/network` | `Network`, `Timeout`, `RateLimited`, `Locked`, `Unknown`. Hàm `awaitWithTimeout(15s)` cho Task của Firebase (FR-021) |
| `VerifyEmailBannerView` | `core/ui` | Banner xác minh email, đặt ở màn hình chính của người chơi (do spec của màn hình chính nhúng vào) |
| `UserProfileReader` (interface) | `core/session` | Đọc `level`, `favoriteDistrictIds` của chính mình. Plan 080 đã yêu cầu A cung cấp; spec 011 mở rộng |

**Lý do:**
- Mọi tính năng đều cần biết người dùng hiện tại và token.
- Constitution cấm import chéo `feature/*/data`.
- Plan 070 R12 đã thống nhất: phần cấu hình chung làm **một lần** trong PR `chore(core)`.

## R12. Google Sign-In bằng Credential Manager

**Quyết định:**
- Dùng `androidx.credentials:credentials`, `credentials-play-services-auth` và `com.google.android.libraries.identity.googleid:googleid`.
- `GetGoogleIdOption` với `setFilterByAuthorizedAccounts(false)`, `serverClientId = default_web_client_id`. Giá trị này do plugin google-services sinh từ `google-services.json`, không commit.
- Nhận `GoogleIdTokenCredential`, rồi `GoogleAuthProvider.getCredential(idToken, null)` và `signInWithCredential`.
- Người dùng hủy (`GetCredentialCancellationException`) thì ở lại màn hình, **không báo lỗi** (US4-4).
- Không có tài khoản Google trên máy (`NoCredentialException`): hiện "Không tìm thấy tài khoản Google trên máy".
- Cần thêm SHA-1 của keystore debug của từng thành viên vào Firebase (chạy `firebase_create_android_sha` hoặc thêm qua console).

**Lý do:** Google Sign-In cũ (`play-services-auth` `GoogleSignInClient`) đã bị deprecate. Credential Manager là cách Android khuyến nghị. SC-002 (tối đa 3 lần chạm) đạt được với bottom sheet chọn tài khoản.

## R13. Thư viện, cấu hình chung, lỗi mạng và timeout

**Thư viện:** dùng danh sách đã thống nhất ở plan 070 R12 (Hilt + KSP, lifecycle, coroutines, Safe Args, supabase-kt `functions-kt` + Ktor OkHttp, kotlinx-serialization, MockK, Turbine, coroutines-test). Spec 010 thêm:
- `androidx.datastore:datastore-preferences`: có trong bảng công nghệ của constitution, dùng cho `PendingSignup`, `lastVerificationEmailAt`.
- `androidx.credentials` và `googleid`: R12.

Spec 010 là spec đầu tiên làm code, nên A tạo PR `chore(core)` này trước.

**Timeout và mạng (FR-021, FR-022):**
- Mọi Task của Firebase được bọc `withTimeout(15_000)`.
- `FirebaseNetworkException` hoặc hết thời gian thì hiện `AppError.Network`: thông báo "Không có kết nối mạng" kèm "Thử lại".
- Ô nhập giữ trong `SavedStateHandle`, trừ mật khẩu và OTP. Ô mật khẩu đặt `android:saveEnabled="false"`.
- ViewModel bỏ qua lần bấm khi `isSubmitting = true`.
- **Trường hợp đặc biệt:**
  - Vấn đề: `createUserWithEmailAndPassword` có thể vẫn thành công sau khi app đã báo hết thời gian. Bấm "Thử lại" lúc đó sẽ nhận `EMAIL_EXISTS`.
  - Cách xử lý: trước khi thử lại, app kiểm tra `auth.currentUser?.email == email`. Nếu đúng thì đi tiếp bước khởi tạo (R4), không tạo lại.

## R14. Những chỗ cần sửa spec sau khi kiểm chứng

| FR | Thực tế của Firebase (Spark) | Đề xuất sửa spec |
|---|---|---|
| FR-016 | Không cấu hình được thời hạn mã hay số lần sai. Số thử nghiệm có mã cố định | Ghi rõ: 5 phút và 5 lần sai do app thực thi trên phiên nhập mã; server không bảo đảm |
| FR-012 | Email/mật khẩu `@gmail.com` chưa xác minh bị Google ghi đè (mất mật khẩu, giữ `uid`) | Thêm vào Edge Cases: vẫn cùng tài khoản; muốn dùng mật khẩu thì đặt lại qua "Quên mật khẩu" |
| FR-019 | Password policy không có "có chữ cái bất kỳ" | Server bảo đảm ≥ 8 ký tự và có số; "có chữ" do app kiểm tra (trang đặt lại của Firebase chỉ áp dụng phần server) |
| FR-018 | Hạn mức 150 email đặt lại mật khẩu / ngày | Thêm vào Assumptions |
| Edge Cases "Số không phải số thử nghiệm" | Spark không gửi SMS thật | Giữ nguyên, đã đúng |

Nên chạy `/speckit-clarify` hoặc sửa tay `spec.md` trước `/speckit-tasks`.

## R15. Chế độ khách và quay lại đúng chỗ sau khi đăng nhập (FR-025, FR-026)

Bổ sung ngày 2026-10-19, sau khi `/speckit-analyze` spec 020 phát hiện luồng cũ (`SignedOut` → `nav_auth`, xóa back stack) làm khách không vào được tìm kiếm (FR-026 của 020) và làm mất bộ lọc sau khi đăng nhập.

**Quyết định:**
- **Khách = `SessionState.SignedOut`.** Không thêm trạng thái mới. Thêm `SessionManager.isGuest: StateFlow<Boolean>` cho tiện dùng.
- **Điểm vào khi mở app** (`SessionRouter`, hàm thuần trong `core/session`, có unit test):

| `SessionState` | `welcome_seen` (DataStore) | Đích |
|---|---|---|
| `SignedOut(reason = null)` | `false` | `nav_auth` / `WelcomeFragment` (có nút "Xem sân trước") |
| `SignedOut(reason = null)` | `true` | `nav_player` chế độ khách |
| `SignedOut(LOCKED)` | bất kỳ | `nav_player` chế độ khách, rồi mở `LoginFragment(lockedNotice = true)` chồng lên |
| `SignedIn` | bất kỳ | Theo `appRole` như R6 |
| `NeedsConsent`, `Initializing`, `InitFailed` | bất kỳ | Như cũ (bước đồng ý điều khoản, màn hình chờ) |

  Bấm "Xem sân trước", "Đăng ký" hoặc "Đăng nhập" ở màn hình chào đều ghi `welcome_seen = true`.
- **Mở đăng nhập từ chế độ khách:** dùng deep link `smashnow://auth/login?returnTo=<uri>` (hoặc `smashnow://auth/register`), mở `nav_auth` **chồng lên** back stack hiện tại, không `popUpTo`. Tính năng khác gọi qua `AuthNavigator` ở `core/session` nên không import `feature/auth` (constitution I).
- **Đăng nhập xong** (`SignedOut` → `SignedIn`), `MainActivity` quyết định:
  - `appRole == PLAYER` và đang có `nav_player` dưới `nav_auth` → `popBackStack(R.id.nav_auth, inclusive = true)`. Màn hình cũ hiện lại, ViewModel scope theo nav graph (ví dụ `nav_search` của 020) **còn nguyên**, nên bộ lọc được giữ (FR-026). Có `returnTo` thì `navigate(returnTo)` sau khi pop.
  - `OWNER` / `ADMIN`, hoặc không có `nav_player` bên dưới (mở app lần đầu từ màn hình chào) → `popUpTo(nav_main, inclusive = true)` sang graph của vai trò, như R6.
  - Đi qua `NeedsConsent` / `Initializing` (Google/OTP lần đầu, đăng ký mới) vẫn nằm trong `nav_auth`, nên quy tắc trên áp dụng khi tới `SignedIn`.
- **Bấm Back trong `nav_auth`** khi mở từ chế độ khách: về màn hình bên dưới (khách). Khi `nav_auth` là gốc (màn hình chào lần đầu): thoát app như cũ.
- **Đăng xuất (011) và bị khóa:** `navigate(nav_player)` với `popUpTo(nav_main, inclusive = true)` ở chế độ khách (bị khóa thì mở thêm `LoginFragment(lockedNotice = true)`). Không đưa về màn hình chào.
- **Màn hình cần tài khoản:** `LoginPromptView` dùng chung ở `core/ui` (biểu tượng, câu mời, nút "Đăng nhập", "Đăng ký"), gọi `AuthNavigator`. Trong spec 010 chỉ `PlayerHomePlaceholderFragment` và các tab placeholder dùng tới; mỗi spec tự quyết màn hình nào của mình khách xem được (mặc định cần đăng nhập).
- **Rules:** không đổi gì ở 010. Quyền đọc của khách do từng collection quy định (ví dụ spec 020 R13). Khách không có ID token nên Edge Function cần đăng nhập vẫn trả `401`; app không gọi các function đó khi `isGuest`.

**Lý do:**
- Tìm sân trước khi đăng ký là luồng phổ biến của app đặt chỗ; bắt đăng nhập ngay làm mất người dùng mới (SC-001 của 020 về thời gian tìm sân).
- `popBackStack` thay vì `popUpTo(nav_main)` là cách duy nhất giữ ViewModel của graph bên dưới mà không phải lưu trạng thái ra ngoài.
- Dùng `SignedOut` làm khách giữ nguyên máy trạng thái phiên đã test ở T007.

**Phương án đã loại:**
- **Đăng nhập ẩn danh của Firebase (Anonymous Auth):** khách có `uid` và token, nhưng phải dọn tài khoản ẩn danh, Rules phải phân biệt ẩn danh với tài khoản thật, `on-signup` phải xử lý liên kết tài khoản ẩn danh. Phức tạp hơn nhiều mà spec không cần dữ liệu riêng của khách.
- **Lưu `SearchCriteria` ra DataStore rồi khôi phục sau khi đăng nhập:** chỉ giải quyết cho tìm kiếm, mỗi spec khác phải tự làm lại; mất vị trí cuộn và trang đã tải.
