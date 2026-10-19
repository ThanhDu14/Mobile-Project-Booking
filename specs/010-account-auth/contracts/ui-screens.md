# Contract: Màn hình, điều hướng và giao diện `core/session` cho nhóm

Wireframe: [ui/README.md](../ui/README.md) (7 bước, có chế độ tối).

## 1. Nav graph

`MainActivity` (single Activity) dùng `nav_main.xml` với start destination là `LauncherFragment`. Màn hình này chỉ có logo và vòng xoay, tự quyết định đi đâu theo `SessionState`. Các graph lồng:

| Graph | Chủ | Nội dung |
|---|---|---|
| `nav_auth` | A (010) | Các màn hình đăng ký/đăng nhập ở mục 2 |
| `nav_player` | Màn hình chính người chơi (spec 020/060…) | Spec 010 tạo `PlayerHomePlaceholderFragment` có banner xác minh email. Spec sở hữu màn hình chính thay bằng màn hình thật |
| `nav_owner` | D (111/112) | Spec 010 tạo placeholder |
| `nav_admin` | D (120) | Spec 010 tạo placeholder |

**Quy tắc điều hướng** (cập nhật 2026-10-19 cho chế độ khách, [research R15](../research.md#r15-chế-độ-khách-và-quay-lại-đúng-chỗ-sau-khi-đăng-nhập-fr-025-fr-026)):
- `LauncherFragment` chọn đích bằng `SessionRouter` (hàm thuần, có unit test):
  - `SignedOut` chưa xem màn hình chào (`welcome_seen = false`) → `nav_auth` / `WelcomeFragment`;
  - `SignedOut` đã xem → `nav_player` ở **chế độ khách**;
  - `SignedOut(LOCKED)` → `nav_player` chế độ khách, rồi mở `LoginFragment(lockedNotice = true)` chồng lên;
  - `SignedIn` → graph theo `appRole`.
  
  Mọi lần chuyển từ `LauncherFragment` dùng `popUpTo(nav_main, inclusive = true)`.
- **Khách mở đăng nhập/đăng ký** từ bất kỳ màn hình nào: gọi `AuthNavigator` (§4), mở `nav_auth` **chồng lên** back stack, không `popUpTo`. Back trong `nav_auth` thì về màn hình bên dưới ở chế độ khách.
- **Đăng nhập xong** (`SignedOut` → `SignedIn`):
  - `PLAYER` và dưới `nav_auth` có `nav_player` → `popBackStack(R.id.nav_auth, inclusive = true)`, màn hình cũ và ViewModel theo nav graph còn nguyên (FR-026). Có `returnTo` thì `navigate(Uri)` sau đó;
  - `OWNER`, `ADMIN`, hoặc `nav_auth` là gốc (đi từ màn hình chào) → `popUpTo(nav_main, inclusive = true)` sang graph của vai trò. Bấm Back sau đó thì thoát app, không quay lại màn hình đăng nhập (Edge Case của spec).
- `MainActivity` thu thập `sessionState`:
  - Chuyển sang `SignedOut` khi đang đăng nhập (đăng xuất ở spec 011, bị khóa) → `nav_player` chế độ khách, `popUpTo(nav_main, inclusive = true)`; bị khóa thì mở thêm `LoginFragment(lockedNotice = true)`.
  - `SignedIn` mà `appRole` đổi thì điều hướng sang graph tương ứng.
- **Deep link `returnTo`** (plan 080 yêu cầu): `LoginFragment` nhận argument `returnTo: String?` (URI deep link). Đăng nhập xong mà có `returnTo` thì `navigate(Uri)` theo quy tắc trên.
- **Deep link mở `nav_auth`:** `smashnow://auth/login?returnTo={returnTo}`, `smashnow://auth/register?returnTo={returnTo}`, khai báo trong `nav_auth.xml`.

## 2. Màn hình của `nav_auth`

| Fragment | Layout | Wireframe | Story / FR | Safe Args |
|---|---|---|---|---|
| `WelcomeFragment` | `fragment_auth_welcome.xml` | `buoc1_man-chao` | US1 | — |
| `LoginFragment` | `fragment_auth_login.xml` | `buoc2_dang-nhap` (+ chế độ tối) | US2, US4, FR-011, FR-014 | `returnTo: String?`, `lockedNotice: Boolean = false` |
| `RegisterFragment` | `fragment_auth_register.xml` | `buoc3_dang-ky-email` | US1, FR-003, FR-004 | — |
| `PhoneLoginFragment` | `fragment_auth_phone.xml` | `buoc4_dang-nhap-so-dien-thoai` | US5, FR-017 | — |
| `OtpVerifyFragment` | `fragment_auth_otp.xml` | `buoc5_nhap-otp-sai-ma` | US5, FR-016 | `phoneE164: String` |
| `TermsConsentFragment` | `fragment_auth_terms_consent.xml` | `buoc6_hoan-tat-tai-khoan` | US4-1, US5-2, FR-003 | — (đọc `ConsentPrefill` từ `SessionManager`) |
| `AccountSetupFragment` | `fragment_auth_account_setup.xml` | — (mới) | Edge Case "khởi tạo lỗi", FR-006 | — |
| `ForgotPasswordFragment` | `fragment_auth_forgot_password.xml` | `buoc7_quen-mat-khau` | US3, FR-018 | `email: String?` (điền sẵn từ màn hình đăng nhập hoặc đăng ký) |
| `LegalDocumentFragment` | `fragment_auth_legal.xml` | — | FR-003 | `doc: TERMS \| PRIVACY` |
| `LinkAccountDialog` | `dialog_auth_link_account.xml` | — (mới) | FR-012 ([R7](../research.md#r7-liên-kết-google-với-emailmật-khẩu-cùng-email-fr-012-analyze-f3)) | `email: String` |

**Chi tiết theo wireframe:**
- **Đăng ký:**
  - Dòng mô tả "Mọi tài khoản bắt đầu là người chơi…" (FR-002, FR-007).
  - Hai dấu tích "Ít nhất 8 ký tự" và "Có chữ và số" cập nhật khi gõ.
  - Lỗi "Email đã được sử dụng" có hai link "Đăng nhập" và "quên mật khẩu" (US1-3).
  - Nút "Đăng ký" bị vô hiệu khi chưa đánh dấu đồng ý (US1-2).
- **Hoàn tất tài khoản:**
  - Thẻ "Đã xác thực bằng Google / số điện thoại" kèm email hoặc số.
  - Họ tên điền sẵn và sửa được.
  - Có tóm tắt điều khoản.
  - Nút "Bắt đầu dùng SmashNow" bị vô hiệu cho tới khi đánh dấu đồng ý.
  - "Dùng tài khoản khác" gọi `signOut()` rồi về màn hình đăng nhập.
- **OTP:**
  - 6 ô số, số điện thoại hiện dạng che bớt (`+84 9•• ••• 123`).
  - Đếm ngược 60 giây cho "Gửi lại mã".
  - Lỗi "Mã không đúng" kèm số lần còn lại.
- **Điều khoản** (`LegalDocumentFragment`): nội dung tĩnh trong `res/raw/terms_vi.html`, `privacy_vi.html`, hiển thị bằng `TextView` với `HtmlCompat`. Có dòng "Phiên bản học tập" (FR-003).
- **Màn hình chờ khởi tạo:** "Đang chuẩn bị tài khoản…". Lỗi thì hiện thông báo kèm "Thử lại" và "Đăng xuất". Không vào được màn hình chính cho tới khi xong.
- **Thông báo khóa** (`lockedNotice = true` hoặc lỗi `AccountLocked`): `MaterialAlertDialog` với nội dung "Tài khoản của bạn đã bị khóa" và email hỗ trợ trong `strings.xml` (`support_email`).

**Chung cho mọi màn hình:**
- Chuỗi đặt trong `strings_auth.xml` với tiền tố `auth_`.
- Ô nhập dùng `TextInputLayout`, lỗi hiện bằng `setError` ngay dưới ô (FR-020). Mỗi ô có `hint` / `contentDescription` (FR-023).
- Nút gửi hiện `CircularProgressIndicator` và bị vô hiệu khi `isSubmitting` (FR-022).
- Mất mạng hoặc hết 15 giây: `Snackbar` "Không có kết nối mạng" kèm action "Thử lại" (FR-021).
- Layout `w600dp` giới hạn chiều rộng form 480dp và căn giữa (FR-023).
- Theme `Theme.Material3.DayNight`.

## 3. ViewModel → `StateFlow<UiState>`

| ViewModel | UiState | Sự kiện một lần (`Channel`) |
|---|---|---|
| `LoginViewModel` | `email`, `emailError`, `isSubmitting`, `formError: AuthError?` | điều hướng theo `SessionState`, mở Google, mở `LinkAccountDialog`, hiện khóa |
| `RegisterViewModel` | `name`, `email`, các lỗi từng ô, `passwordChecks(len8, letterDigit)`, `termsChecked`, `canSubmit`, `isSubmitting` | điều hướng, snackbar mạng |
| `PhoneLoginViewModel` | `phone`, `phoneError`, `isSending` | sang OTP với `phoneE164`, cần Activity để gọi `verifyPhoneNumber` |
| `OtpViewModel` | `code`, `remainingAttempts`, `resendInSeconds`, `state ∈ {Editing, Verifying, WrongCode, Expired, Locked5}` | đăng nhập xong |
| `TermsConsentViewModel` | `prefill`, `name`, `nameError`, `termsChecked`, `isSubmitting` | xong |
| `AccountSetupViewModel` | `Loading`, `Failed(AppError)` | xong |
| `ForgotPasswordViewModel` | `email`, `emailError`, `sent: Boolean`, `resendInSeconds` | — |
| `VerifyEmailBannerViewModel` (`core/ui`) | `visible`, `resendInSeconds`, `isSending` | snackbar "Đã gửi lại email" |

## 4. Giao diện `core/session` cho các nhóm khác

```kotlin
interface SessionManager {
    val sessionState: StateFlow<SessionState>
    suspend fun refresh(forceTokenRefresh: Boolean = true)   // gọi sau khi D đổi vai trò, sau on-signup
    suspend fun signOut()                                     // spec 011 gọi
    fun currentUid(): String?                                 // null khi chưa SignedIn
    fun currentAppRole(): AppRole?
}

interface IdTokenProvider {
    suspend fun getToken(forceRefresh: Boolean = false): String   // ném AppError.NotSignedIn
}

interface UserProfileReader {                                     // plan 080 yêu cầu
    fun observeMyProfile(): Flow<MyProfile?>                      // displayName, level, favoriteDistrictIds, avatarPath
}

// Chế độ khách (research R15)
// SessionManager có thêm:
//     val isGuest: StateFlow<Boolean>                            // true khi SignedOut

interface AuthNavigator {                                         // core/session, cài đặt bằng deep link
    fun openLogin(navController: NavController, returnTo: Uri? = null)
    fun openRegister(navController: NavController, returnTo: Uri? = null)
}
```

`LoginPromptView` (`core/ui`, layout `view_login_prompt.xml`): biểu tượng, câu mời (tham số chuỗi), nút "Đăng nhập", "Đăng ký" gọi `AuthNavigator`. Màn hình cần tài khoản hiện view này thay cho nội dung khi `isGuest`.

**Quy ước cho mọi tính năng:**
- Không gọi `FirebaseAuth.getInstance()` trong `feature/*`.
- `uid` lấy từ `SessionManager.currentUid()`.
- Token cho Edge Function lấy từ `IdTokenProvider`. `SupabaseClient` trong `core/di` đã gắn sẵn.
- Edge Function trả `403 ACCOUNT_LOCKED` thì repository ném `AppError.Locked`. ViewModel không cần xử lý riêng: `core/network` báo cho `SessionManager` để đăng xuất; app về chế độ khách và mở màn hình đăng nhập kèm thông báo khóa (research R15).
