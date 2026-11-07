# Data Model: Đăng ký và đăng nhập tài khoản (spec 010)

Dữ liệu nằm ở ba nơi:

| Nơi | Chứa gì |
|---|---|
| **Firebase Auth** | Phương thức đăng nhập, mật khẩu, trạng thái xác minh email, cờ `disabled`, custom claims |
| **Firestore** `users/{uid}` | Theo [đặc tả CSDL mục 4.1](../../docs/design/database/README.md#41-usersuid--a) |
| **DataStore trên máy** | Trạng thái tạm của luồng đăng ký |

Supabase không lưu dữ liệu của spec này.

---

## 1. Firebase Auth: custom claims

| Claim | Giá trị | Ai ghi | Dùng cho |
|---|---|---|---|
| `role` | `"authenticated"` | `on-signup` | Supabase Third-party Auth. Thiếu claim này thì request chạy với quyền `anon` |
| `appRole` | `PLAYER` \| `OWNER` \| `ADMIN` | `on-signup` (ghi `PLAYER`), `review-owner-application` và thu hồi quyền (D) ghi `OWNER` / `PLAYER`, script cấp admin ghi `ADMIN` | Điều hướng (FR-015), Rules `appRole()`, `requireUser()` |

**Quy tắc:**
- Mọi lần ghi claim PHẢI **gộp** vào claim hiện có, không được ghi đè cả object. Nếu ghi đè thì mất `role`.
- Không có `appRole` nghĩa là tài khoản **chưa khởi tạo** ([research R4](research.md#r4-app-nhận-biết-chưa-khởi-tạo-bằng-claim-appRole)).
- Cờ `disabled` của tài khoản Firebase luôn trùng với `accountStatus == LOCKED` ([R5](research.md#r5-khóa-tài-khoản-fr-014-accountstatus-kèm-cờ-disabled-của-firebase-auth)).

## 2. Firestore `users/{uid}` (A)

Các trường `on-signup` ghi khi tạo document:

| Trường | Kiểu | Giá trị khởi tạo | Ghi chú |
|---|---|---|---|
| `displayName` | String | Họ tên trên form hoặc ở bước Hoàn tất tài khoản | 2–50 ký tự sau khi `trim` (FR-004) |
| `email` | String? | `token.email` đã `lowercase` | `null` khi đăng ký bằng số điện thoại |
| `phone` | String? | `token.phone_number` (E.164 `+84…`) | `null` khi đăng ký bằng email hoặc Google |
| `avatarPath` | String? | `null` | Spec 011 |
| `level` | String? | `null` | **Đổi thành nullable** (trước ghi `String`). Spec 011 cho chọn; 080 coi `null` là "chưa chọn" |
| `favoriteDistrictIds` | Array\<String> | `[]` | |
| `role` 🔒 | String | `PLAYER` | Bản sao của `appRole` để hiển thị |
| `ownerStatus` 🔒 | String | `NONE` | |
| `ownerRevokedReason` 🔒 | String? | không ghi | Spec 120 |
| `accountStatus` 🔒 | String | `ACTIVE` | Chỉ `set-account-status` (D) đổi |
| `fcmTokens` | Array\<String> | `[]` | Spec 100 ghi |
| `notificationPrefs` | Map\<String, Boolean> | `{}` | Rỗng nghĩa là bật tất cả (spec 100) |
| `walletBalance` 🔒 | Long | `0` | |
| `termsAcceptedAt` 🔒 | Timestamp | Giờ server lúc gọi `on-signup` | **Đổi thành 🔒**: chỉ server ghi, người dùng không sửa được thời điểm đồng ý (SC-004) |
| `createdAt`, `updatedAt` | Timestamp | Giờ server | |

**Kiểm tra trong `on-signup`:**
- Document chưa có thì bắt buộc `termsAccepted == true` (FR-003, SC-004).
- `email` và `phone` lấy **từ token**, không lấy từ body, để không giả được.
- Phải có ít nhất một trong hai: `email` hoặc `phone`.

**Không lưu:**
- Trạng thái xác minh email: đọc từ Firebase Auth (`isEmailVerified`, claim `email_verified`).
- Phương thức đăng nhập: do Firebase Auth quản lý (Key Entities của spec).

### Chuyển trạng thái

```text
accountStatus:  ACTIVE ──(admin khóa, D)──▶ LOCKED ──(admin mở khóa, D)──▶ ACTIVE
ownerStatus:    NONE ─(nộp hồ sơ, 110)─▶ PENDING ─(duyệt, 120)─▶ APPROVED ─(thu hồi, 120)─▶ NONE
                                          └─(từ chối)─▶ REJECTED ─(nộp lại)─▶ PENDING
appRole:        (chưa có) ─(on-signup)─▶ PLAYER ◀──▶ OWNER (D)      ADMIN: cấp bằng script
```

Spec 010 chỉ tạo trạng thái ban đầu và đọc các trạng thái này để điều hướng hoặc chặn. Mọi chuyển trạng thái khác do spec 110 và 120 làm.

## 3. Trên máy (DataStore `session_prefs`)

| Key | Kiểu | Ý nghĩa | Xóa khi |
|---|---|---|---|
| `pending_signup_uid` | String | `uid` vừa đăng ký email, chưa khởi tạo xong | `on-signup` trả `200`, hoặc đăng xuất |
| `pending_signup_name` | String | Họ tên đã nhập trên form | như trên |
| `pending_signup_terms_at` | Long | Thời điểm bấm "Đăng ký" (chỉ để biết người dùng đã đồng ý; server vẫn ghi giờ của server) | như trên |
| `last_verification_email_at` | Long | Mốc để tính 60 giây chờ gửi lại email xác minh | đăng xuất |
| `last_password_reset_at` | Long | Mốc để tính 60 giây chờ gửi lại email đặt lại mật khẩu | sau 60 giây |

Không lưu mật khẩu, OTP hay token (FR-024). Firebase SDK tự giữ phiên đăng nhập trong bộ nhớ riêng của app.

## 4. Mô hình phía app (`core/session`, `feature/auth/domain`)

| Lớp | Trường hoặc giá trị | Ghi chú |
|---|---|---|
| `AppRole` | `PLAYER`, `OWNER`, `ADMIN` | Đọc từ claim. Giá trị lạ thì coi là `PLAYER` |
| `SessionState` | `Loading` · `SignedOut(reason: SignOutReason?)` · `NeedsConsent(prefill: ConsentPrefill)` · `Initializing` · `InitFailed(error: AppError)` · `SignedIn(uid, appRole, emailVerified)` | `SignOutReason.LOCKED` để màn hình đăng nhập hiện thông báo khóa |
| `ConsentPrefill` | `provider: GOOGLE \| PHONE \| PASSWORD`, `email: String?`, `phone: String?`, `displayName: String?` | Hiện "Đã xác thực bằng Google / số điện thoại" |
| `OtpSession` | `phoneE164`, `verificationId`, `resendToken`, `sentAtElapsed`, `wrongCount` | Hết 5 phút (`sentAtElapsed + 300_000`) hoặc `wrongCount >= 5` thì `isUsable = false` |
| `AuthError` | `InvalidCredentials`, `EmailInUse`, `AccountLocked`, `InvalidCode`, `CodeExpired`, `TooManyAttempts`, `PhoneNotSupported`, `GoogleCancelled`, `NoGoogleAccount`, `NeedsLinking(email, pendingCredential)`, `Network`, `Unknown` | Mỗi lỗi có chuỗi tiếng Việt trong `strings_auth.xml` |

### Kiểm tra dữ liệu nhập (thuần Kotlin, có unit test)

| Rule | Điều kiện | Chuỗi lỗi |
|---|---|---|
| `NameRule` | `trim` dài 2–50 | `auth_error_name_length` |
| `EmailRule` | `trim().lowercase()` khớp `Patterns.EMAIL_ADDRESS` (unit test dùng regex tương đương) | `auth_error_email_format` |
| `PasswordRule` | ≥ 8 ký tự, có ít nhất một chữ cái (`Char.isLetter`) và một chữ số | `auth_error_password_rule` (hai dấu tích "Ít nhất 8 ký tự", "Có chữ và số" cập nhật khi gõ) |
| `ConfirmPasswordRule` | bằng mật khẩu | `auth_error_password_mismatch` |
| `PhoneRule` | `^(0\|\+84)(3\|5\|7\|8\|9)\d{8}$` sau khi bỏ khoảng trắng, chuẩn hóa thành `+84…` | `auth_error_phone_format` |
| `OtpRule` | đúng 6 chữ số | `auth_error_otp_format` |

## 5. Index

Spec 010 không cần index mới (chỉ `get` theo `uid`).

---

## Danh sách thay đổi cần đưa vào `docs/design/database/README.md`

1. **Mục 4.1:**
   - `level` đổi thành `String?` (khởi tạo `null`).
   - `termsAcceptedAt` thêm 🔒 (chỉ `on-signup` ghi).
   - Thêm câu: "`email`, `phone` lấy từ Firebase ID token khi tạo; đổi email hoặc số điện thoại theo spec 011".
2. **Mục 4.1:** thêm đoạn "Custom claims": `role = "authenticated"` và `appRole`. Mọi Edge Function ghi claim phải gộp vào claim cũ và ghi claim **trước** khi ghi `users.role`.
3. **Mục 7, dòng `users`:**
   - Người chơi: "R **của mình**, U của mình trừ 🔒; không C/D".
   - Admin: "R; khóa/mở khóa qua Edge Function `set-account-status`" (bỏ "U `accountStatus`" qua Rules).
4. **Mục 8a:**
   - `on-signup`: đổi mô tả thành "App gọi sau khi tạo tài khoản và đồng ý điều khoản (Spark không có trigger Auth). Idempotent; tạo `users/{uid}` rồi gộp claim".
   - Thêm dòng `set-account-status` (D): "Khóa/mở khóa: ghi `accountStatus` và `disabled` của Firebase Auth".
   - Thêm `_shared/auth.ts`, `_shared/identity.ts` (A) vào phần code dùng chung (mục 1).
5. **Mục 1 (Quy ước chung):** thêm câu "Trường chứa dữ liệu cá nhân (`users`) chỉ chủ tài khoản và admin đọc; tính năng khác cần tên/ảnh dùng snapshot".
