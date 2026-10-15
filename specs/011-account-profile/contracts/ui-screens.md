# Contract: Màn hình, điều hướng và phần mở rộng `core/session`

Wireframe: [ui/README.md](../ui/README.md) (5 artboard, có chế độ tối).

## 1. Nav graph `nav_account` (A)

`nav_account.xml` là graph lồng, được `include` vào `nav_player` (tab "Tài khoản") và `nav_owner` (tab "Cơ sở", mục tài khoản). Bộ khung `nav_main`, `nav_player`, `nav_owner` do spec 010 tạo.

| Fragment | Layout | Wireframe | Story / FR | Safe Args |
|---|---|---|---|---|
| `AccountFragment` (start) | `fragment_account.xml` | `buoc1_tai-khoan`, `buoc1_…bi-thu-hoi-quyen`, `buoc3_chu-san-kiem-nguoi-choi` | US1–US4, FR-001, FR-001a, FR-008–FR-013 | — |
| `EditProfileFragment` | `fragment_account_edit_profile.xml` | `buoc2_sua-ho-so` | US1, FR-002, FR-003, FR-006 | — |
| `DistrictPickerBottomSheet` | `sheet_account_district_picker.xml` | (trong `buoc2`, nút "Thêm khu vực") | FR-002 | `selectedIds: String[]` |
| `AvatarSourceBottomSheet` | `sheet_account_avatar_source.xml` | — | FR-003, FR-024 | — |
| `AvatarCropFragment` | `fragment_account_avatar_crop.xml` | — | FR-003 | `sourceUri: String` |
| `ChangePasswordFragment` | `fragment_account_change_password.xml` | — | US5, FR-014 | — |
| `ChangeEmailFragment` | `fragment_account_change_email.xml` | — | US7, FR-016 | — |
| `DeleteAccountFragment` | `fragment_account_delete.xml` | `buoc5_xoa-tai-khoan` | US6, FR-018–FR-022 | — |
| `ReauthDialog` | `dialog_account_reauth.xml` | — | FR-017 | `purpose: DELETE` |

Màn hình "Hồ sơ chủ sân" (`buoc4_ho-so-chu-san-bi-tu-choi`) thuộc spec 110 (D), mở bằng deep link (research R6).

**Điều hướng ra ngoài:** deep link theo bảng ở [research R14](../research.md#r14-trang-tài-khoản-trong-điều-hướng-và-lối-sang-tính-năng-khác). Hàng nào chưa có destination thì ẩn.

## 2. Chi tiết theo wireframe

**Tài khoản (`AccountFragment`):**
- Đầu trang: ảnh đại diện (Coil, `StorageUrls`), không có ảnh thì vòng tròn chữ cái đầu tên; họ tên; email hoặc số điện thoại; chip trình độ và khu vực (tên khu vực đọc từ `districts`).
- Nút "Sửa hồ sơ".
- Thẻ ví: "Ví giả lập · không phải tiền thật", số dư `50.000 đ`, nút "Nạp ví" (FR-001a).
- Banner xác minh email: dùng lại `VerifyEmailBannerView` của 010 (Edge Case "Chưa xác minh email").
- Mục chủ sân theo `OwnerSection` (research R6). Chủ sân đã duyệt: khối "Chế độ đang dùng" với `MaterialButtonToggleGroup` "Người chơi" / "Quản lý sân" và dòng "Mỗi lần mở app, bạn vào giao diện quản lý sân" (FR-011); nhóm "CHỦ SÂN" (Cơ sở của tôi, Tin nhắn khách hàng, Hồ sơ chủ sân).
- Danh sách: Lịch đặt của tôi, Lượt vãng lai của tôi, Sân yêu thích, Cài đặt thông báo, **Đổi mật khẩu** và **Đổi email đăng nhập** (chỉ khi có provider `password`, FR-015), Điều khoản và quyền riêng tư (deep link `smashnow://legal/TERMS`, không import `LegalDocumentFragment` của `feature/auth/ui`).
- Nút "Đăng xuất" mở `MaterialAlertDialog` xác nhận (FR-007). "Xóa tài khoản" chữ đỏ, ẩn với admin.
- Dòng cuối "SmashNow · Phiên bản học tập".
- Trạng thái: đang tải (skeleton), có dữ liệu, lỗi kèm "Thử lại" (FR-025). Không có trạng thái rỗng vì người đã đăng nhập luôn có hồ sơ.

**Sửa hồ sơ (`EditProfileFragment`):**
- Ảnh tròn lớn, nút "Đổi ảnh" (mở `AvatarSourceBottomSheet`: "Chụp ảnh", "Chọn từ thư viện") và "Xóa ảnh"; dòng "Ảnh được cắt vuông, tối đa 2 MB".
- Ô họ tên có bộ đếm `12/50`. Ô email chỉ đọc, kèm chip "Chưa xác minh" và dòng hướng dẫn đổi email.
- Trình độ: 3 nút chọn một (`MaterialButtonToggleGroup`, `singleSelection`, cho bỏ chọn).
- Khu vực: chip có nút xóa, bộ đếm `2/5`, nút "+ Thêm khu vực" mở `DistrictPickerBottomSheet`; đủ 5 thì nút bị tắt. Danh mục tải lỗi: hiện lỗi và "Thử lại" ngay trong mục khu vực, các ô khác vẫn sửa được (Edge Case).
- Thanh dưới: "Có thay đổi chưa lưu" (khi `isDirty`) và nút "Lưu thay đổi" (khóa khi đang lưu, FR-023).
- Back khi `isDirty`: hỏi "Bỏ các thay đổi chưa lưu?" (FR-006), dùng `OnBackPressedCallback`.
- Ảnh đại diện được lưu ngay khi xác nhận cắt (không chờ nút "Lưu thay đổi"), có vòng tiến trình trên ảnh.

**Đổi mật khẩu:** 3 ô (hiện tại, mới, nhập lại), hai dấu tích như màn hình đăng ký của 010, nút "Đổi mật khẩu".

**Đổi email:** ô email mới, ô mật khẩu hiện tại, nút "Gửi email xác minh"; sau khi gửi hiện thông báo chung (research R10).

**Xóa tài khoản (`DeleteAccountFragment`):**
- Câu cảnh báo, thẻ vàng "N đơn sắp tới sẽ bị hủy" liệt kê từng đơn (cơ sở, giờ, trạng thái, tiền cọc và tiền hoàn), cùng lượt vãng lai, lượt chờ.
- Chủ sân: thẻ đỏ "N cơ sở sẽ bị ẩn, M đơn và K buổi vãng lai của khách sẽ bị hủy, khách được hoàn tiền toàn bộ và nhận thông báo".
- "Sẽ bị xóa vĩnh viễn" (5 dòng theo wireframe) và "Được giữ lại ở dạng ẩn danh".
- Dòng "Số dư ví giả lập X đ và tiền hoàn sẽ bị xóa, không quy đổi được".
- Xác nhận danh tính: tài khoản có mật khẩu → ô "Mật khẩu hiện tại" ngay trên màn hình (như wireframe); Google → nút "Xác nhận bằng Google"; số điện thoại → nút "Gửi mã OTP" mở `ReauthDialog`.
- Ô "Gõ XÓA để xác nhận" (FR-018) và ô đánh dấu "Tôi hiểu tài khoản và dữ liệu trên sẽ bị xóa, N đơn sắp tới sẽ bị hủy".
- Nút "Xóa vĩnh viễn tài khoản" chỉ bật khi đã xác nhận danh tính (hoặc nhập mật khẩu), gõ đúng "XÓA" và đánh dấu (US6-2). Nút "Giữ tài khoản" quay lại.
- Trạng thái: đang tải bản xem trước, có dữ liệu, lỗi kèm "Thử lại".

**Chung:** chuỗi trong `strings_account.xml` tiền tố `account_`; `TextInputLayout.setError`; nút khóa và hiện `CircularProgressIndicator` khi đang xử lý; mất mạng hiện `Snackbar` "Không có kết nối mạng" + "Thử lại", giữ dữ liệu đã nhập trừ mật khẩu (FR-023); `w600dp` giới hạn chiều rộng nội dung 480dp và căn giữa (dùng lại `auth_form_max_width` của 010); `contentDescription` cho ảnh đại diện và các icon; vùng chạm ≥ 48dp (FR-025).

## 3. ViewModel → `StateFlow<UiState>`

| ViewModel | UiState | Sự kiện một lần (`Channel`) |
|---|---|---|
| `AccountViewModel` | `Loading` · `Error(AppError)` · `Content(profile, districtNames, ownerSection, mode, canChangePassword, canDelete, showVerifyBanner, entries: Set<AccountEntry>)` | điều hướng deep link, xác nhận đăng xuất, snackbar "Quyền chủ sân đã bị thu hồi" |
| `EditProfileViewModel` | `draft: ProfileDraft`, `saved: ProfileDraft`, `nameError`, `districtsState: Loading \| Error \| Content(List<District>)`, `avatar: Idle \| Processing \| Uploading(progress) \| Error`, `isSaving`, `isDirty` | "Đã lưu", hỏi bỏ thay đổi, xin quyền camera, mở cắt ảnh |
| `ChangePasswordViewModel` | các ô, lỗi từng ô, `checks(len8, letterDigit)`, `isSubmitting` | thành công → quay lại + snackbar |
| `ChangeEmailViewModel` | `newEmail`, `emailError`, `passwordError`, `isSubmitting`, `sent: Boolean` | — |
| `DeleteAccountViewModel` | `impact: Loading \| Error \| Content(DeletionImpact)`, `reauth: Required(method) \| Done`, `password`, `confirmText`, `acknowledged`, `canDelete`, `isDeleting` | xác nhận lại danh tính, đã xóa → điều hướng `WelcomeFragment` |
| `ReauthViewModel` | `method`, `otp state` (dùng lại logic `OtpSession` của 010), `isSubmitting` | xong / hủy |

## 4. Mở rộng `core/session` cho cả nhóm

```kotlin
// 010 đã có; 011 chỉ thêm trường (không đổi chữ ký cũ)
data class MyProfile(
    val displayName: String, val level: String?, val favoriteDistrictIds: List<String>,
    val avatarPath: String?, val role: AppRole, val accountStatus: String,
    // [011]
    val email: String?, val phone: String?, val walletBalance: Long,
    val ownerStatus: String, val ownerRevokedReason: String?, val deletionRequestedAt: Instant?,
)

enum class AppMode { PLAYER, OWNER }

interface AppModeController {                       // [011] core/session
    val mode: StateFlow<AppMode>
    suspend fun switchTo(target: AppMode): Result<AppMode>   // OWNER: làm mới token, kiểm appRole (R7)
}

interface SessionManager {                           // 010; 011 chỉ thêm hàm có thân mặc định
    // ... các hàm cũ giữ nguyên
    suspend fun signOut(reason: SignOutReason?) { signOut() }   // [011] FirebaseSessionManager override
}

fun interface SignOutHook {                          // [011] Hilt @IntoSet
    suspend fun beforeSignOut(uid: String)
}

object StorageUrls {                                 // [011] core/media
    fun publicUrl(bucket: String, path: String): String
}
```

**Quy ước cho mọi tính năng:**
- Hiển thị ảnh đại diện bằng `StorageUrls.publicUrl("avatars", path)`; `path == null` thì vẽ chữ cái đầu.
- Spec 100 (D) đăng ký token FCM; khi đăng xuất, `FcmSignOutHook` của A gỡ token. D không cần viết thêm trừ khi có dữ liệu khác cần dọn khi đăng xuất (thêm `SignOutHook` riêng).
- `MainActivity` (010) điều hướng theo `(appRole, mode)`.
