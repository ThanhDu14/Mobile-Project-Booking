# Research: Hồ sơ và quản lý tài khoản (spec 011)

Phase 0 của `/speckit-plan`. Mỗi mục ghi **Quyết định**, **Lý do** và **Phương án đã cân nhắc**. Giới hạn của Firebase và Supabase được kiểm chứng ngày 15/10/2026 trên tài liệu chính thức: Email enumeration protection (Identity Platform), Manage user sessions (Firebase Auth), Firestore quotas, Edge Functions limits (Supabase).

Spec này **xây trên plan 010** ([plan](../010-account-auth/plan.md), [contracts](../010-account-auth/contracts/)). Những gì 010 đã có thì dùng lại, không viết lại: `SessionManager`, `IdTokenProvider`, `UserProfileReader`, `SessionPrefs`, `AppError`/`awaitWithTimeout`, Rules `users/{uid}` (`selfEditableFields()`, `isInitialized()`), `_shared/auth.ts`, `_shared/identity.ts`, `PasswordRule`, `NameRule`, `LegalDocumentFragment`, `VerifyEmailBannerView`.

---

## R1. Phần mở rộng `core/` so với 010

**Quyết định:** spec 011 mở rộng `core/session` ở 5 chỗ, giữ nguyên các interface 010 đã công bố cho nhóm (chỉ **thêm**, không đổi chữ ký cũ):

| Thành phần | Thay đổi | Dùng cho |
|---|---|---|
| `MyProfile` | Thêm `email`, `phone`, `walletBalance: Long`, `ownerStatus`, `ownerRevokedReason`, `deletionRequestedAt` | Trang tài khoản (FR-001, FR-001a, FR-008) |
| `AppModeController` (mới) | `mode: StateFlow<AppMode>` (`PLAYER`, `OWNER`), `switchTo(mode)` | Chuyển chế độ (R7) |
| `SignOutHook` (mới, Hilt multibinding `Set<SignOutHook>`) | `suspend fun beforeSignOut(uid)` | Xóa FCM token (R5); D, C gắn thêm nếu cần |
| `SignOutReason` | Thêm `SESSION_REVOKED` (Firebase thu hồi phiên sau khi đổi email/mật khẩu ở máy khác) và `ACCOUNT_DELETED` (sau khi xóa tài khoản, hoặc khi server trả `ACCOUNT_DELETING`) | R9, R10, R11 |
| `StorageUrls` (mới, `core/media`) | `publicUrl(bucket, path)` ghép `{SUPABASE_URL}/storage/v1/object/public/{bucket}/{path}` | Ảnh đại diện ở mọi màn hình (070, 091 dùng lại) |

`SessionManager.signOut()` của 010 gọi lần lượt các `SignOutHook` (mỗi hook tối đa 5 giây, lỗi bị bỏ qua) **trước** `FirebaseAuth.signOut()`, vì sau khi đăng xuất thì Rules không cho ghi `users/{uid}` nữa.

**Lý do:** các tính năng khác (070 ảnh người viết, 091 ảnh trong chat, 100 token FCM) cần cùng các thành phần này. Đặt ở `core/` để không import chéo `feature/*` (nguyên tắc I).

**Phương án đã cân nhắc:** để `feature/account` tự đọc `users/{uid}` bằng listener riêng: hai listener trên cùng document, tốn gấp đôi lượt đọc khi document đổi. Bị loại.

## R2. Rules kiểm tra từng trường của `users/{uid}`

**Quyết định:** giữ `selfEditableFields()` của 010, thêm kiểm tra cho từng trường khi trường đó bị đổi:

| Trường | Điều kiện |
|---|---|
| `displayName` | (010 đã có) chuỗi, 2–50 ký tự |
| `level` | `null` hoặc một trong `BEGINNER`, `INTERMEDIATE`, `ADVANCED` |
| `favoriteDistrictIds` | `list`, `size() <= 5`, không trùng (`toSet().size() == size()`), mỗi phần tử tồn tại trong `districts` và `isActive == true` |
| `avatarPath` | `null` hoặc chuỗi khớp `^{uid}/avatar_[0-9]{13}\.jpg$` (đúng `uid` của người ghi) |

Kiểm tra "có trong danh mục" dùng `get(/databases/$(database)/documents/districts/$(ids[i]))` cho từng vị trí `i = 0..4`, có điều kiện `ids.size() > i`. Tối đa 5 lượt `get()`, dưới giới hạn **10 lượt mỗi request** của Rules.

Thêm `match /districts/{id}` (A sở hữu, theo đặc tả CSDL mục 2): người đã khởi tạo (`isInitialized()`) được đọc, chỉ admin ghi. Spec 120 (D) làm màn hình quản lý danh mục; spec 011 chỉ định nghĩa schema tối thiểu và dữ liệu mẫu ([data-model §3](data-model.md#3-firestore-districtsid-a)).

**Lý do:** constitution II, mọi kiểm tra phải ở server; Rules là chỗ duy nhất chặn người dùng gọi thẳng Firestore. Mỗi `get()` tính thêm 1 lượt đọc, nhưng chỉ khi lưu hồ sơ, không đáng kể.

**Phương án đã cân nhắc:**
- Lưu hồ sơ qua Edge Function: thêm độ trễ và mã server cho một thao tác Rules làm được. Bị loại.
- Không kiểm tra `districtId` tồn tại: người dùng gọi thẳng API ghi được ID rác, các tính năng gợi ý (020, 080) phải tự lọc. Bị loại.

## R3. Ảnh đại diện trên Supabase Storage

**Quyết định:**
- Bucket `avatars` **public**, giới hạn `2 MB`, MIME `image/jpeg`, `image/png`, `image/webp` (đúng đặc tả CSDL mục 8). App luôn tải lên JPEG (R4).
- Đường dẫn **có phiên bản**: `{uid}/avatar_{epochMillis}.jpg` thay cho `{uid}/avatar.jpg` của đặc tả CSDL. Ghi lại vào danh sách sửa đặc tả CSDL.
- RLS trên `storage.objects` (migration trong `supabase/migrations/`):
  - `INSERT`, `DELETE`: `bucket_id = 'avatars'` và `(storage.foldername(name))[1] = auth.jwt() ->> 'sub'` và `auth.jwt() ->> 'appRole' is not null` (đã khởi tạo).
  - Không cho `UPDATE` (mỗi lần đổi ảnh là object mới).
  - `SELECT` không cần policy vì bucket public đọc qua URL công khai.
  - Dùng `auth.jwt() ->> 'sub'` chứ không dùng `auth.uid()`, vì `uid` của Firebase không phải UUID.
- **Thứ tự đổi ảnh** (không để trạng thái nửa vời, Edge Case mất mạng):
  1. Tải object mới lên.
  2. Ghi `users/{uid}.avatarPath = path mới` (Rules kiểm tra tiền tố `uid`).
  3. Xóa object cũ (bỏ qua lỗi).

  Lỗi ở bước 1 thì ảnh cũ giữ nguyên. Lỗi ở bước 2 thì app xóa object mới vừa tải (bỏ qua lỗi). Object mồ côi (nếu có) bị xóa khi xóa tài khoản (R11) vì nằm trong thư mục `{uid}/`.
- **Xóa ảnh** (FR-003): ghi `avatarPath = null` rồi xóa object.
- **Test:** pgTAP qua `supabase test db` (`supabase/tests/storage_avatars_test.sql`), giả lập JWT bằng `set_config('request.jwt.claims', ...)` với role `authenticated`. Đủ các ca: ghi đúng thư mục của mình; ghi thư mục người khác; chưa có `appRole`; `UPDATE` bị chặn; xóa của người khác bị chặn.

**Lý do:**
- Đường dẫn cố định khiến Coil và CDN của Supabase trả ảnh cũ trong bộ nhớ đệm, không đạt SC-002 (ảnh mới hiện trong 5 giây). Đổi tên object là cách đơn giản nhất để bỏ bộ nhớ đệm.
- Bucket public để 070 (người viết đánh giá), 091 (chat) hiện ảnh bằng URL, không phải xin signed URL cho từng ảnh trong danh sách.

**Lưu ý về FR-004:** bucket public nghĩa là **ai có URL** cũng xem được, kể cả người chưa đăng nhập, rộng hơn chữ "mọi người dùng đã đăng nhập" của FR-004. URL chứa `uid` và mốc thời gian khó đoán, nhưng không phải bí mật. Đề xuất sửa FR-004 ở [R16](#r16-những-chỗ-cần-sửa-spec).

**Phương án đã cân nhắc:**
- Bucket private + `sign-download`: mỗi ảnh trong danh sách đánh giá hay chat cần một lời gọi Edge Function, tốn hạn mức và chậm. Bị loại.
- Thêm `?v=` vào URL: CDN của Supabase có thể bỏ qua query string khi cache; không chắc chắn. Bị loại.

## R4. Chọn, cắt và nén ảnh

**Quyết định:**
- **Thư viện ảnh:** `ActivityResultContracts.PickVisualMedia` (Photo Picker, **không cần quyền** đọc ảnh, có bản backport cho Android 11 trở xuống qua Google Play services). Camera: `ActivityResultContracts.TakePicture` với `FileProvider`, chỉ xin quyền `CAMERA` lúc bấm "Chụp ảnh" (FR-024).
- **Từ chối quyền camera:** hiện giải thích bằng `MaterialAlertDialog` và vẫn cho chọn từ thư viện (US1-4). Từ chối hẳn ("Don't ask again") thì có nút mở Cài đặt ứng dụng.
- **Kiểm tra định dạng:** đọc MIME bằng `ContentResolver.getType`; khác `image/jpeg`, `image/png`, `image/webp` thì báo "Chỉ hỗ trợ ảnh JPG, PNG, WebP" (Edge Case).
- **Cắt vuông:** thư viện `com.vanniktech:android-image-cropper` (bản tiếp nối của CanHub Android Image Cropper), khung cố định tỉ lệ 1:1.
- **Nén:** `ImageDecoder` → thu về tối đa 512 × 512 px → `Bitmap.compress(JPEG, 85)`. Nếu vẫn > 2 MB (gần như không xảy ra ở 512 px) thì giảm chất lượng mỗi lần 10, tối thiểu 50; vẫn quá thì báo lỗi. Xử lý trên `Dispatchers.Default`.
- Ảnh tạm của camera nằm trong `cacheDir/avatar/` và bị xóa sau khi tải lên xong hoặc khi thoát màn hình.

**Lý do:** 512 px đủ cho ảnh đại diện lớn nhất (96 dp ở màn hình xxxhdpi là 384 px). File thường dưới 150 KB, tải lên nhanh trên 4G (SC-002).

**Phương án đã cân nhắc:**
- Tự viết màn hình cắt ảnh: nhiều mã cử chỉ (kéo, phóng to) mà không phải trọng tâm đồ án. Bị loại.
- Cắt giữa tự động, không cho người dùng chỉnh: không đúng US1-3 ("cắt ảnh vuông và xác nhận"). Bị loại.
- uCrop: cần Activity riêng có theme riêng, khó đồng bộ Material 3 DayNight. Bị loại.

## R5. Đăng xuất (FR-007)

**Quyết định:** `AccountViewModel.signOut()` gọi `SessionManager.signOut()` (010). Phần 011 thêm:

1. `FcmSignOutHook` (một `SignOutHook`, R1):
   - lấy token hiện tại bằng `FirebaseMessaging.getInstance().token`;
   - `users/{uid}.fcmTokens` ← `arrayRemove(token)` và `updatedAt` (Rules cho phép vì `fcmTokens` thuộc `selfEditableFields`);
   - `FirebaseMessaging.getInstance().deleteToken()`.

   Bước `deleteToken()` vẫn chạy khi bước ghi Firestore lỗi (mất mạng). Token đã xóa trên máy thì FCM trả `UNREGISTERED`; `send-notification` (D) gỡ token chết khỏi `fcmTokens` khi gặp lỗi này.
2. `GoogleSignOutHook`: `CredentialManager.clearCredentialState()` để lần sau bottom sheet chọn tài khoản Google hiện lại.
3. Sau các hook, 010 xóa `session_prefs`, gọi `FirebaseAuth.signOut()`, phát `SignedOut(null)`; `MainActivity` chuyển về `nav_auth` và xóa back stack (010 contract §1).

**Lý do:** US2-3 yêu cầu máy đã đăng xuất không nhận thông báo nữa. Chỉ gỡ token khỏi Firestore là không đủ khi mất mạng; xóa token trên máy luôn làm được.

**Phương án đã cân nhắc:**
- Ghi `arrayRemove` sau `signOut()`: Rules chặn vì không còn đăng nhập. Bị loại.
- Xóa bộ nhớ đệm offline của Firestore (`clearPersistence()`): phải `terminate()` instance đang được Hilt giữ, phức tạp. Dữ liệu cũ chỉ đọc được qua truy vấn theo `uid` của người trước, mà các màn hình luôn truy vấn theo `uid` hiện tại. Không làm, ghi rõ để nhóm biết.

## R6. Mục chủ sân trên trang tài khoản (FR-008, FR-009, FR-013)

**Quyết định:**
- Trạng thái lấy từ `MyProfile` (listener `users/{uid}` của 010, nên tự cập nhật khi admin đổi, FR-009) cộng hồ sơ gần nhất `ownerApplications where uid == me order by submittedAt desc limit 1` (listener, chỉ khi `ownerStatus` là `PENDING` hoặc `REJECTED`).
- Bảng suy ra:

| `appRole` (claim) | `ownerStatus` | Điều kiện thêm | Hiển thị |
|---|---|---|---|
| `ADMIN` | bất kỳ | — | Ẩn cả mục (FR-013) |
| `PLAYER` | `NONE` | `ownerRevokedReason == null` | "Đăng ký làm chủ sân" |
| `PLAYER` | `NONE` | `ownerRevokedReason != null` | "Quyền chủ sân đã bị thu hồi" + lý do, nút "Đăng ký làm chủ sân" (form trống) |
| `PLAYER` | `PENDING` | — | "Đang chờ duyệt" + `submittedAt` |
| `PLAYER` | `REJECTED` | — | "Bị từ chối" + `rejectReason`, nút "Sửa và nộp lại" |
| `OWNER` | `APPROVED` | — | "Đã là chủ sân" + khối chuyển chế độ (R7) |

- Không cần đọc hồ sơ để biết đã bị thu hồi: 120 ghi `ownerRevokedReason` khi thu hồi và xóa khi duyệt lại.
- **Màn hình chi tiết "Hồ sơ chủ sân"** (wireframe `buoc4`, có timeline và "Thông tin đã nộp" kèm ảnh giấy tờ) **thuộc spec 110 (D)**, vì nó đọc `venueDraft`, `documentPaths` trong bucket private `owner-docs`. Trang tài khoản chỉ mở màn hình đó qua deep link (R14). Cần D xác nhận ở họp 16/10.

**Lý do:** `appRole` trong claim là nguồn quyền thật; `ownerStatus` là bản sao để hiển thị. Hai giá trị lệch nhau trong vài giây khi D ghi claim trước rồi ghi document (010 R6) thì bảng trên vẫn cho kết quả hợp lý.

**Phương án đã cân nhắc:** luôn đọc hồ sơ gần nhất để suy ra mọi trạng thái: thêm một listener cho mọi người dùng, kể cả 95% người chưa từng nộp. Bị loại.

## R7. Chuyển chế độ "Người chơi" ↔ "Quản lý sân" (FR-010–FR-012)

**Quyết định:**
- `AppModeController` (`core/session`, `@Singleton`) giữ `mode` **trong bộ nhớ**. Khi `sessionState` chuyển `SignedIn(appRole = OWNER)`, `mode` mặc định là `OWNER`; các vai trò khác luôn `PLAYER`.
- `MainActivity` (010 T017/T053) điều hướng theo cặp `(appRole, mode)`: `OWNER + OWNER` → `nav_owner`, `OWNER + PLAYER` → `nav_player`. Cần sửa nhỏ code của 010.
- `switchTo(PLAYER)`: đổi ngay, không gọi mạng (SC-003 ≤ 1 giây).
- `switchTo(OWNER)` (FR-012):
  1. `SessionManager.refresh(forceTokenRefresh = true)` với timeout **3 giây**.
  2. `appRole == OWNER` thì đổi chế độ.
  3. Không còn `OWNER` thì ở lại `PLAYER`, trả lỗi `OwnerRevoked(reason)` và trang tài khoản hiện "Quyền chủ sân đã bị thu hồi" kèm `ownerRevokedReason`.
  4. Mất mạng hoặc hết 3 giây: dùng claim của token đang có (do server cấp, tối đa 1 giờ tuổi). Rules và Edge Function của 111/112 vẫn chặn nếu quyền đã bị thu hồi, và listener `users/{uid}` (010 R6) sẽ đưa người dùng về `nav_player` khi có mạng lại.
- Chủ sân ở `nav_owner` cũng mở được trang tài khoản: `AccountFragment` nằm trong `nav_account.xml`, được `include` vào cả `nav_player` và `nav_owner` (R14).
- Process bị hệ điều hành dừng thì `mode` về mặc định `OWNER`, khớp FR-011 ("chỉ có hiệu lực trong phiên dùng hiện tại").

**Lý do:** spec chốt chế độ chỉ sống trong phiên, nên không lưu DataStore. Bảng công nghệ của constitution liệt kê DataStore cho "chế độ chủ sân" như một lựa chọn lưu trữ, không bắt buộc phải lưu.

**Phương án đã cân nhắc:**
- Lưu chế độ vào DataStore: trái FR-011. Bị loại.
- Chặn hẳn khi mất mạng: chủ sân ở sân có sóng yếu không chuyển được dù quyền còn; bảo mật thật nằm ở server. Bị loại.

## R8. Xác nhận lại danh tính (FR-017, FR-018)

**Quyết định:**
- Ba thao tác nhạy cảm **luôn** xác nhận lại danh tính ngay trước khi gọi, không chờ Firebase báo `ERROR_REQUIRES_RECENT_LOGIN`:

| Phương thức của tài khoản | Cách xác nhận lại |
|---|---|
| Có `password` | `EmailAuthProvider.getCredential(email, currentPassword)` + `reauthenticate` |
| Chỉ Google | Credential Manager (`GetGoogleIdOption`, `setFilterByAuthorizedAccounts(true)`), rồi `reauthenticate(GoogleAuthProvider.getCredential(idToken, null))` |
| Chỉ số điện thoại | Gửi OTP tới số hiện tại (dùng lại `SendOtpUseCase` của 010), rồi `reauthenticate(PhoneAuthProvider.getCredential(...))` |

  Tài khoản có nhiều phương thức thì ưu tiên `password` → Google → số điện thoại.
- Đổi mật khẩu và đổi email luôn có ô "Mật khẩu hiện tại" (US5, US7), nên việc xác nhận lại không thêm bước.
- **Phía server cho xóa tài khoản:** `delete-account` (action `confirm`) kiểm tra claim `auth_time` của ID token: `now − auth_time ≤ 300 giây`. Không đạt thì trả `403 REAUTH_REQUIRED`. App lấy token bằng `getIdToken(true)` ngay sau khi `reauthenticate`.

**Lý do:**
- "Quá lâu" của FR-017 do Firebase quyết định (khoảng 5 phút) và không cấu hình được. Luôn xác nhận lại thì hành vi đoán trước được và thỏa cả FR-017 lẫn FR-018.
- Kiểm tra ở client thôi thì gọi thẳng API là bỏ qua được. `auth_time` là thời điểm người dùng thật sự nhập thông tin đăng nhập, nên server dựa vào nó được (constitution II).

**Phương án đã cân nhắc:** chỉ xác nhận lại khi gặp `ERROR_REQUIRES_RECENT_LOGIN`: không áp dụng được cho `delete-account` (chạy ở server, không qua SDK). Bị loại.

## R9. Đổi mật khẩu (US5, FR-014, FR-015)

**Quyết định:**
- Chỉ hiện mục khi `user.providerData` có `password` (FR-015).
- Kiểm tra ở app: dùng lại `PasswordRule`, `ConfirmPasswordRule` của 010; thêm `newPassword != currentPassword` → "Mật khẩu mới phải khác mật khẩu hiện tại" (US5-3).
- Trình tự: `reauthenticate` (R8) → `updatePassword(new)`.
  - Sai mật khẩu hiện tại (`INVALID_LOGIN_CREDENTIALS` / `ERROR_WRONG_PASSWORD`) → "Mật khẩu hiện tại không đúng" (US5-2).
  - Password policy của server từ chối (`PASSWORD_DOES_NOT_MEET_REQUIREMENTS`) → lỗi dưới ô mật khẩu mới.
  - `TOO_MANY_ATTEMPTS_TRY_LATER` → thông báo chung của 010.
- **Hệ quả:** Firebase thu hồi refresh token của mọi phiên khi đổi mật khẩu. Máy đang đổi nhận token mới nên vẫn đăng nhập (US5-1). **Các máy khác bị đăng xuất** ở lần làm mới token kế tiếp. `SessionManager` (010) cần coi lỗi `ERROR_USER_TOKEN_EXPIRED` là `SignedOut(SESSION_REVOKED)` với thông báo "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại" (khác thông báo khóa tài khoản).

**Lý do:** tài liệu "Manage user sessions" của Firebase: refresh token hết hiệu lực khi "a major account change is detected for the user. This includes events like password or email address updates."

## R10. Đổi email đăng nhập (US7, FR-016)

**Quyết định:**
- Chỉ hiện với tài khoản có `password` (FR-015).
- Trình tự: `reauthenticate` (R8) → `verifyBeforeUpdateEmail(newEmail)`. Email chuẩn hóa `trim().lowercase()`, dùng `EmailRule` của 010. Email mới trùng email hiện tại thì báo ngay ở app.
- **Email enumeration protection đang bật (010 R7):** Firebase **không báo lỗi** khi email mới đã thuộc tài khoản khác; thư xác minh chỉ không được gửi ("there are no specific error messages indicating when emails aren't sent"). Vì vậy app **không thể** làm đúng US7-2 ("app báo email đã được sử dụng"). App luôn hiện: "Nếu email này chưa được dùng cho tài khoản khác, bạn sẽ nhận được thư xác minh. Email đăng nhập chỉ đổi sau khi bấm link." Đề xuất sửa US7-2 ở [R16](#r16-những-chỗ-cần-sửa-spec).
- Sau khi người dùng bấm link: Firebase đổi email và **thu hồi mọi phiên** (R9). Máy của người dùng nhận `SignedOut(SESSION_REVOKED)` và người dùng đăng nhập lại bằng email mới.
- **Đồng bộ `users.email`:** `email` không thuộc `selfEditableFields`, nên client không tự ghi. Edge Function mới **`sync-account-email`** (A):
  - `requireUser(req)` (010), lấy `email` và `email_verified` **từ token**;
  - `email_verified == true` và khác `users.email` thì ghi `users.email = email.lowercase()`, `updatedAt`;
  - idempotent, không nhận tham số nào từ body.
- App gọi `sync-account-email` khi `SessionManager` thấy `SignedIn` mà email trong token khác `MyProfile.email`. Thường xảy ra ngay sau lần đăng nhập lại bằng email mới. Lỗi được bỏ qua và thử lại ở lần mở app sau.

**Lý do:** email là định danh liên lạc; để server ghi theo token đã xác minh thì không giả được (constitution II).

**Phương án đã cân nhắc:**
- Cho client ghi `email` qua Rules, so `request.resource.data.email == request.auth.token.email`: làm được, nhưng Rules không kiểm tra được `email_verified` cho đúng thời điểm và không chuẩn hóa chữ thường. Bị loại.
- Gộp vào `on-signup`: `on-signup` dành cho khởi tạo, `verify_jwt = false` và không yêu cầu `appRole`; trộn hai việc làm khó test. Bị loại.

## R11. Xóa tài khoản: `delete-account` (US6, FR-018–FR-022a)

**Kiểm chứng giới hạn:**
- Firestore (quotas): transaction tối đa **270 giây**, hết hạn sau **60 giây** không hoạt động, request tối đa **10 MiB**, tối đa **500 field transform** (`serverTimestamp`, `increment`…) trong một commit. Tài liệu hiện tại **không còn** giới hạn 500 document mỗi transaction như prompt giả định; giới hạn thực tế với spec này là **500 field transform**, vì mỗi đơn bị hủy dùng `serverTimestamp()` cho `cancelledAt`, `updatedAt` và `increment()` cho ví.
- Supabase Edge Functions gói Free: wall clock **150 giây**, **2 giây CPU** mỗi request (không tính thời gian chờ I/O), bộ nhớ 256 MB.
- Không có transaction chung cho Firestore, Supabase Storage và Firebase Auth.

**Quyết định:** chia làm 3 giai đoạn trong một Edge Function, cộng một tác vụ quét định kỳ:

1. **Xem trước** (`action: "preview"`, FR-022): chỉ đọc. Trả danh sách đơn và lượt vãng lai sắp tới kèm tiền hoàn theo chính sách, số lượt đang chờ, và với chủ sân thì số cơ sở, số đơn và buổi vãng lai của khách sẽ bị hủy. Trả `impactVersion` = SHA-256 của danh sách ID bị ảnh hưởng, đã sắp xếp.
2. **Chốt** (`action: "confirm"`): **một Firestore transaction** chứa mọi thay đổi nghiệp vụ:
   - hủy đơn sắp tới của người dùng (helper của B), xóa slot trong cùng transaction (FR-022a(3));
   - hủy lượt vãng lai (helper của C, `cancelledBy = SYSTEM`) và rời hàng chờ (helper 081);
   - nếu có cơ sở (`venues where ownerId == uid`): ẩn cơ sở, hủy đơn và buổi vãng lai sắp tới của khách với hoàn 100% vào ví khách (helper của B, D);
   - `users/{uid}`: `deletionRequestedAt = serverTime` 🔒, `fcmTokens = []`;
   - tạo `accountDeletions/{uid}` (`state = COMMITTED`, danh sách ID đơn và buổi cần gửi thông báo, không chứa thông tin cá nhân).

   Trước khi mở transaction, function tính lại `impactVersion`; khác giá trị client gửi thì trả `409 IMPACT_CHANGED` kèm bản xem trước mới. Ước tính số field transform; vượt **450** thì trả `422 DELETE_TOO_LARGE` và **không đổi gì**.

   Transaction lỗi → không có gì bị đổi, tài khoản dùng bình thường (FR-021).
3. **Dọn dẹp** (sau khi chốt, idempotent, mỗi bước ghi cờ xong vào `accountDeletions/{uid}.steps`):
   10 bước cố định, đánh số giống [contract §1.3](contracts/edge-functions-account.md#13-các-bước-dọn-dẹp-runcleanup-idempotent):
   1. `disableAuth`: `setUserDisabled(uid, true)`, chặn đăng nhập ngay;
   2. `notifyGuests`: gửi thông báo hủy cho khách (`send-notification` của D, khóa idempotent `{kind}_{id}_{recipientUid}_OWNER_DELETED` để hai khách cùng một buổi không trùng khóa) (SC-008);
   3. `anonymizeReviews`, 4. `anonymizeCommunity`, 5. `anonymizeBookings`: ẩn danh dữ liệu liên quan người khác ([R12](#r12-ẩn-danh-và-các-bước-do-nhóm-khác-cung-cấp));
   6. `deleteOwnerData`: xóa hồ sơ chủ sân và ảnh giấy tờ (D);
   7. `deleteSubcollections`: xóa `favorites`, `searchHistory`, `aiUsage`, `notifications` của `users/{uid}`;
   8. `deleteAvatars`: xóa thư mục `avatars/{uid}/`;
   9. `deleteUserDoc`: xóa `users/{uid}`;
   10. `deleteAuthUser`: `deleteUser(uid)` (Identity Toolkit `accounts:delete`, thêm vào `_shared/identity.ts`) **sau cùng**;

   rồi ghi `state = DONE`, `completedAt`.

   Hết ~100 giây mà chưa xong thì trả `202 CLEANUP_SCHEDULED`. App coi `200` và `202` đều là thành công.
4. **`delete-account-sweeper`** (pg_cron mỗi 5 phút, header `CRON_SECRET` như `dropin-close` của 081): làm tiếp các `accountDeletions` có `state = COMMITTED`. Ghi `attempts`, `lastError`. Lượt quét cuối chạy lại bước 7 (`deleteSubcollections`) sau 65 phút kể từ khi chốt, để dọn dữ liệu do ID token cũ (còn hạn tối đa 1 giờ) ghi thẳng vào Firestore.

**Chặn thao tác sau khi chốt:** `requireUser()` của `_shared/auth.ts` (010) đã đọc `users/{uid}`; thêm: có `deletionRequestedAt` thì trả `403 ACCOUNT_DELETING`. Mọi Edge Function của nhóm tự động được bảo vệ. Riêng `delete-account` (`confirm`) gọi `requireUser(req, { allowDeleting: true })` (tham số tùy chọn, lời gọi cũ không đổi) để lần gọi lặp sau khi chốt, hoặc sau khi `users/{uid}` đã bị xóa, vẫn trả kết quả idempotent thay vì bị chặn. App coi `ACCOUNT_DELETING` là "tài khoản đã được xóa" (`SignOutReason.ACCOUNT_DELETED`), không hiện thông báo khóa.

**Cách hiểu FR-021 ("toàn bộ hoặc không làm gì"):**
- Ranh giới là bước **chốt**. Trước bước chốt: lỗi ở đâu cũng không có gì đổi.
- Sau bước chốt: hệ thống **bảo đảm hoàn tất** bằng cách thử lại tới khi xong, chứ không quay lui. Dữ liệu ở 3 dịch vụ khác nhau không thể quay lui nguyên tử.

Người dùng không bao giờ thấy trạng thái "xóa một nửa mà vẫn dùng được": sau bước chốt, tài khoản bị tắt và mọi Edge Function trả `ACCOUNT_DELETING`. Đề xuất ghi rõ cách hiểu này vào spec ([R16](#r16-những-chỗ-cần-sửa-spec)).

**Lý do:**
- Đặt mọi thay đổi ảnh hưởng người khác (hủy đơn, nhả slot, hoàn tiền khách, ẩn cơ sở) vào **một** transaction thì FR-022a được bảo đảm nguyên tử, đúng constitution III (thao tác với slot phải trong transaction).
- Phần còn lại (ẩn danh hàng trăm tin nhắn, xóa file) không thể nằm trong một transaction, nhưng chỉ ảnh hưởng dữ liệu của chính người xóa, nên thử lại tới khi xong là an toàn.
- Xóa Auth sau cùng để nếu dọn dẹp dừng giữa chừng, `uid` vẫn còn và sweeper xác định được dữ liệu cần dọn.

**Phương án đã cân nhắc:**
- Một transaction cho tất cả, kể cả ẩn danh: vượt 500 field transform với người dùng hoạt động nhiều, và Storage/Auth vẫn nằm ngoài. Bị loại.
- Tắt tài khoản trước, chốt sau, lỗi thì mở lại: phải viết bước bù (bật lại), mà bật lại cũng có thể lỗi. Bị loại.
- Xóa mềm (`accountStatus = DELETED`) rồi để admin xóa tay: trái FR-019 ("xóa vĩnh viễn") và Nghị định 13. Bị loại.

**Tiền hoàn cho đơn của chính người xóa:** helper của B cộng tiền hoàn vào `users/{uid}.walletBalance`, nhưng ví bị xóa ngay sau đó (FR-019). Số tiền vẫn ghi vào `bookings.refundAmount` để chủ sân đối soát. Màn hình xác nhận ghi rõ "Tiền hoàn vào ví giả lập và bị xóa cùng tài khoản". Đề xuất bổ sung spec ở [R16](#r16-những-chỗ-cần-sửa-spec).

## R12. Ẩn danh và các bước do nhóm khác cung cấp

**Quyết định:** `supabase/functions/_shared/account-deletion.ts` (A) định nghĩa danh sách bước. Mỗi bước là một hàm do nhóm sở hữu dữ liệu viết, A chỉ điều phối:

| Bước | Hàm | Chủ | Nội dung |
|---|---|---|---|
| Hủy đơn của người dùng | `cancelBookingInTx(tx, booking, { cancelledBy: "SYSTEM", reason, refund: "POLICY" })` | **B** (`_shared/booking.ts`) | Theo chính sách hủy của 060; xóa slot |
| Hủy đơn của khách tại cơ sở | như trên với `refund: "FULL"`, `reason = "Cơ sở ngừng hoạt động"` | **B** | Hoàn 100% vào ví khách |
| Ẩn cơ sở | `hideVenueForOwnerDeletionInTx(tx, venueId)` | **D** (`_shared/venue.ts`) | `status = HIDDEN`, `hiddenReason = OWNER_DELETED` 🔒 để không ai mở lại |
| Hủy buổi vãng lai của cơ sở | `cancelSessionInTx(tx, session, { reason, refund: "FULL" })` | **D** (spec 112) | Gồm đóng hàng chờ (`closeWaitlistInTx` của 081) |
| Hủy lượt vãng lai của người dùng | `cancelRegistrationInTx(tx, sessionId, uid, "SYSTEM")` | **C** (đã có ở contract 080) | |
| Rời hàng chờ | `leaveWaitlistInTx(tx, sessionId, uid, "LEFT", "SYSTEM")` | **C** (đã có ở contract 081) | |
| Ẩn danh đánh giá | `anonymizeReviews(uid)` | **C** (070 R9 đã chốt) | `userId = null`, `userSnapshot = { displayName: null, avatarPath: null }`, `authorDeleted = true` |
| Ẩn danh tin nhắn, bài đăng, nhóm | `anonymizeCommunity(uid)` | **C** (090, 091) | Snapshot tên/ảnh về `null` + cờ `authorDeleted`; chuyển quyền chủ nhóm (090 Edge Case); chat riêng thành chỉ đọc (091) |
| Ẩn danh đơn đã qua | `anonymizeBookings(uid)` | **B** | Bỏ thông tin liên hệ của người đặt nếu đơn có lưu, đặt `userDeleted = true` |
| Thông báo cho khách | `send-notification` (`BOOKING_CANCELLED`, `DROPIN_CHANGED`) | **D** | Cần hỗ trợ khóa idempotent; `delete-account` dùng khóa `{kind}_{id}_{recipientUid}_OWNER_DELETED` |
| Xóa hồ sơ chủ sân | `deleteOwnerApplications(uid)` | **D** | Xóa `ownerApplications where uid == me` và `owner-docs/{uid}/` |

Các hàm ẩn danh chạy theo lô (tối đa 400 thao tác ghi mỗi batch), idempotent (ghi đè cùng giá trị) và tự phân trang.

**Phương án tạm** nếu nhóm khác chưa xong lúc A viết `delete-account`:
- **Bước dọn dẹp** (ẩn danh, thông báo, xóa hồ sơ chủ sân): hàm rỗng có `// TODO(B|C|D)`, đánh dấu `steps.<tên> = "SKIPPED"` thay vì `DONE`, bị chặn ở checklist trước demo (không chặn merge). Khi helper có thì chạy `scripts/admin/rerun-deletion-step.ts`.
- **Bước chốt** (hủy đơn, hủy lượt vãng lai, rời hàng chờ, ẩn cơ sở, hủy buổi) **không bao giờ** `SKIPPED`: bỏ qua hủy đơn mà vẫn xóa tài khoản thì slot bị chiếm mãi, sai FR-022a. Helper chưa có thì `confirm` trả `500 INTERNAL` khi người dùng có dữ liệu cần xử lý ở bước đó (an toàn theo FR-021); người không có đơn, lượt hay cơ sở vẫn xóa được.

**Lý do:** mỗi nhóm biết rõ schema và quy tắc của dữ liệu mình (constitution I áp dụng tương tự cho server: không tự sửa collection của người khác). 080 và 081 đã chuẩn bị đúng các hàm này cho `delete-account`.

## R13. Ví giả lập (FR-001a)

**Quyết định:**
- Hiển thị `MyProfile.walletBalance` (realtime theo listener 010), định dạng `NumberFormat` VND (`50.000 đ`), nhãn "Ví giả lập · không phải tiền thật".
- Nút "Nạp ví" mở chức năng nạp ví giả lập của **B** (spec 050) qua deep link `smashnow://wallet/topup` (bottom sheet chọn mệnh giá 100.000 / 500.000 / 1.000.000 đ). Spec 011 không ghi `walletBalance`.
- Nếu B chưa có destination: nút bị ẩn (kiểm tra `navController.graph.hasDeepLink`, R14).

## R14. Trang tài khoản trong điều hướng và lối sang tính năng khác

**Quyết định:**
- `RES/navigation/nav_account.xml` (graph lồng, A) gồm: `AccountFragment` (start), `EditProfileFragment`, `DistrictPickerBottomSheet`, `AvatarCropFragment`, `ChangePasswordFragment`, `ChangeEmailFragment`, `DeleteAccountFragment`, `ReauthDialog`. `nav_player` và `nav_owner` đều `include` graph này, ở tab "Tài khoản" / "Cơ sở" theo wireframe.
- Mục dẫn sang tính năng khác dùng **deep link** để không import `ui/` của tính năng khác (constitution I):

| Mục | Deep link | Chủ |
|---|---|---|
| Lịch đặt của tôi | `smashnow://bookings/mine` | B (060) |
| Lượt vãng lai của tôi | `smashnow://dropin/mine` | C (080) |
| Sân yêu thích | `smashnow://favorites` | C (070) |
| Cài đặt thông báo | `smashnow://notifications/settings` | D (100) |
| Đăng ký / Sửa và nộp lại / Hồ sơ chủ sân | `smashnow://owner/apply?mode=new|resubmit`, `smashnow://owner/application` | D (110) |
| Cơ sở của tôi, Tin nhắn khách hàng | `smashnow://owner/venues`, `smashnow://chat/owner` | D (111), C (091) |
| Nạp ví | `smashnow://wallet/topup` | B (050) |
| Điều khoản và quyền riêng tư | `smashnow://legal/{TERMS\|PRIVACY}` (destination `legal` của `nav_auth`, 010). Không import `LegalDocumentFragment` vì nó thuộc `feature/auth/ui` (constitution I) | A |

- Mục nào chưa có destination trong graph (nhóm chưa làm) thì **ẩn**, không crash.

**Lý do:** mỗi nhóm làm màn hình của mình theo tiến độ riêng; trang tài khoản vẫn chạy được khi chỉ có một phần.

## R15. Thư viện

**Quyết định:**
- Dùng lại PR `chore(core)` của 010: Hilt, Firebase (Auth, Firestore, Messaging đã có trong `app/build.gradle.kts`), supabase-kt, Coil, Credentials, DataStore.
- Thêm `storage-kt` của supabase-kt nếu PR core chưa có (plan 070 đã liệt kê).
- **Mới:** `com.vanniktech:android-image-cropper` (R4). Ghi vào Complexity Tracking của plan vì constitution VII yêu cầu nêu lý do khi thêm thư viện ngoài bảng công nghệ.
- Server: không thêm thư viện; `delete-account` dùng `_shared/firestore.ts` (B), `_shared/identity.ts`, `_shared/auth.ts` (A), và gọi Storage API của Supabase bằng `fetch` với service role key (đã có trong secrets của Edge Functions, không bao giờ vào app).

## R16. Những chỗ cần sửa spec

| Chỗ | Thực tế | Đề xuất sửa spec |
|---|---|---|
| US7-2 | Email enumeration protection không cho biết email mới đã được dùng (R10) | Đổi thành: "app hiện cùng một thông báo; nếu email đã thuộc tài khoản khác thì không có thư xác minh và email đăng nhập không đổi" |
| FR-004 | Bucket `avatars` public: ai có URL đều xem được (R3) | "Ảnh đại diện công khai qua đường dẫn khó đoán; chỉ chủ tài khoản thay hoặc xóa được" |
| FR-021 | Không có transaction chung cho Firestore, Storage, Auth (R11) | Ghi rõ: "các thay đổi ảnh hưởng người khác (hủy đơn, nhả giờ, hoàn tiền, ẩn cơ sở) xảy ra cùng lúc hoặc không xảy ra; sau đó việc xóa dữ liệu cá nhân được hệ thống tự hoàn tất, tài khoản không dùng được nữa trong lúc chờ" |
| FR-022 | Tiền hoàn cho đơn của chính người xóa vào ví rồi bị xóa cùng ví (R11) | Thêm câu: màn hình xác nhận ghi rõ tiền hoàn vào ví giả lập bị xóa cùng tài khoản |
| FR-017 | App luôn xác nhận lại danh tính cho 3 thao tác (R8) | Đổi "nếu lần đăng nhập gần nhất đã quá lâu" thành "luôn" (chặt hơn, đơn giản hơn) |
| Edge Cases | Đổi mật khẩu/email làm Firebase thu hồi phiên ở máy khác (R9) | Thêm: "đổi mật khẩu hoặc email thì các máy khác phải đăng nhập lại" |
| Edge Cases (admin) | Spec không nói admin có tự xóa tài khoản được không | Đề xuất: admin không có nút "Xóa tài khoản"; xóa admin do nhóm làm thủ công bằng script (giống cấp admin, constitution II). `delete-account` trả `403 ADMIN_SELF_DELETE` |

**Đã áp dụng** vào `spec.md` ngày 2026-10-15 (Clarifications, Session 2026-10-15), kèm 3 chỗ sửa theo cho khớp: Edge Case "xác nhận lại danh tính", câu cuối FR-022a (thông báo gửi ở bước hoàn tất), Key Entities "Ảnh đại diện".
