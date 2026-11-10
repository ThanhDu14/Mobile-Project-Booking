# Contract: Màn hình, điều hướng và phần mở rộng `core/session`

Wireframe: [ui/README.md](../ui/README.md) (5 artboard, có chế độ tối).

## 1. Nav graph `nav_account` (A)

`nav_account.xml` là graph lồng, được `include` vào `nav_player` và `nav_owner`. Bộ khung `nav_main`, `nav_player`, `nav_owner` do spec 010 tạo.

*(2026-11-06)* Không còn tab "Tài khoản" (`tab_account`). Cả hai graph mở `nav_account` bằng **global action** `action_global_account` (khai báo trong `nav_player` và `nav_owner`), gọi từ avatar của header dùng chung (§5). `AccountFragment` được đẩy lên back stack của tab đang đứng.

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
- *(2026-11-06)* Màn gốc của tab chính dùng header dùng chung ở §5, không tự vẽ avatar.

## 5. Header dùng chung và avatar (2026-11-06, US8, FR-026 → FR-031)

Đặt trong `core/ui/header/` (không ở `feature/account`) để mọi tính năng dùng được mà không import `ui/` của A (constitution I).

| Thành phần | Vị trí | Vai trò |
|---|---|---|
| `view_main_tab_header.xml` | `RES/layout/` | Dùng bằng `<include>`. Trái: `tabTitle` (`textAppearanceHeadlineSmall`, `accessibilityHeading`) và `tabSubtitle` tùy chọn (ví dụ ngày ở màn Hôm nay). Giữa: `tabActions` (`LinearLayout` rỗng để tab tự thêm nút như sắp xếp, lọc). Phải: `accountAvatar` (`AccountAvatarView` 48dp). Id có tiền tố để không trùng id của màn include header. Bố cục lấy theo header của `fragment_home_placeholder_owner.xml` hiện có. |
| `AccountAvatarView` | `core/ui/header/` | `FrameLayout` 48dp gồm ảnh tròn (Coil, `ShapeAppearance.SmashNow.Circle`) và chữ cái đầu trên nền `bg_account_avatar_initials`. `render(AccountAvatarState)`. Ảnh tải lỗi thì về chữ cái đầu. |
| `AccountAvatarState` | `core/session/` | `Guest` (icon `ic_person_outline_24`, nhãn "Đăng nhập") · `Pending` (vòng tròn trống, nhãn "Tài khoản") · `SignedIn(initials, avatarPath?)` (có ảnh thì hiện ảnh, ảnh lỗi về chữ cái đầu; `initials` rỗng thì icon người). |
| `AccountAvatarSource` | `core/session/` | `val state: Flow<AccountAvatarState>` gộp `SessionState` và `UserProfileReader.observeMyProfile()`. `AccountAvatarView` đổi `avatarPath` thành URL bằng `StorageUrls.publicUrl("avatars", path)`. Implementation `DefaultAccountAvatarSource` của A. |
| `initialsOf(displayName)` | chuyển từ `feature/account/ui/account/AccountFormat.kt` sang `core/session/Initials.kt` | Dùng chung cho avatar header, `AccountFragment`, `EditProfileFragment`. Rỗng thì avatar hiện icon người nhưng vẫn bấm mở trang Tài khoản (Edge Case họ tên rỗng). |
| `AccountEntryNavigator` | `core/session/` | `fun open(navController: NavController)`: đã đăng nhập → `navigate(R.id.action_global_account)` với `launchSingleTop`; khách → `AuthNavigator.openLogin(navController, returnTo = null)` (về đúng tab, FR-030). |
| `MainTabHeader.bind(...)` | `core/ui/header/` | `bind(fragment: Fragment, header: ViewMainTabHeaderBinding, avatarSource: AccountAvatarSource, accountEntryNavigator: AccountEntryNavigator)`: thu `state` theo `repeatOnLifecycle(STARTED)`, gắn click có chống bấm liên tiếp (Edge Case). Tab chỉ gọi hàm này, không xử lý avatar. |

**Quy ước cho tab của spec khác (020, 060, 080, 090, 112):**
- Màn gốc của tab `<include android:id="@+id/header" layout="@layout/view_main_tab_header">`, đặt `header.tabTitle`, gọi `MainTabHeader.bind` trong `onViewCreated`; inject `AccountAvatarSource`, `AccountEntryNavigator` bằng Hilt.
- Không vẽ avatar riêng, không navigate tới `nav_account` hay `accountFragment` trực tiếp.
- Tab có `TabLayout` riêng (060: 5 tab trạng thái) đặt header ở trên `TabLayout`.
- Màn con (chi tiết sân, chi tiết đơn…) dùng `MaterialToolbar` có nút quay lại như cũ, không cần header này.

**`MainActivity` (BottomNav người chơi):** ở `accountFragment` thì giữ sáng tab của entry ngay dưới trong back stack (giống nhánh chủ sân đang có), BottomNav vẫn hiện; `setOnItemReselectedListener` ở `accountFragment` thì `popBackStack` về màn gốc của tab đó.

**Chuỗi** (`strings_account.xml`): `account_avatar_cd_signed_in` = "Tài khoản", `account_avatar_cd_guest` = "Đăng nhập".

**Test:** `DefaultAccountAvatarSourceTest` (các trạng thái; đổi ảnh, tên, đăng xuất phát trạng thái mới), `AccountEntryNavigatorTest` (khách → `AuthNavigator.openLogin`, đã đăng nhập → action, không mở chồng). Dự án chưa có Robolectric nên `AccountAvatarView` và `MainActivity` (giữ sáng `tab_home` khi ở trang Tài khoản) kiểm trên máy ảo.

## 6. Ảnh hưởng tới code và spec khác (2026-11-06)

### 6.1. Code cần sửa (A làm)

| File | Hiện tại | Cần sửa |
|---|---|---|
| `RES/menu/menu_player_bottom_nav.xml` | Item `tab_account` cuối menu | Xóa item `tab_account`. (`tab_search` do spec 020 thêm theo FR-028.) |
| `RES/values/strings_nav.xml` | `nav_tab_account` | Xóa khi không còn chỗ dùng. |
| `MainActivity.kt` | `setOnItemSelectedListener` có nhánh `R.id.tab_account` (mở `nav_account` hoặc `LOGIN` cho khách); `updateBottomNav` map `R.id.accountFragment -> R.id.tab_account`; reselect chỉ xử lý tab chủ sân; KDoc `setUpBottomNav` ghi "Người chơi mới nối Trang chủ và Tài khoản" | Bỏ nhánh `tab_account` (khách đi qua `AccountEntryNavigator`). Trong nhánh người chơi của `updateBottomNav`, `accountFragment` → tab của `previousBackStackEntry` (mặc định `tab_home`). Mở rộng reselect cho `tab_home` (và các tab người chơi khi có). Sửa KDoc. |
| `RES/navigation/nav_player.xml` | `include nav_account`, comment "Trang Tài khoản của spec 011" | Thêm `<action android:id="@+id/action_global_account" app:destination="@id/nav_account" />`. |
| `RES/navigation/nav_owner.xml` | `action_ownerHome_to_account` trong `ownerHomePlaceholderFragment` | Đổi thành global action `action_global_account` (cùng id với `nav_player`) để `AccountEntryNavigator` dùng chung. |
| `RES/layout/fragment_home_placeholder_owner.xml` | Header tự vẽ (`date`, tiêu đề, `TextView avatar`) | Thay bằng `<include layout="@layout/view_main_tab_header">` (`subtitle` = ngày). |
| `feature/auth/ui/home/OwnerHomePlaceholderFragment.kt`, `OwnerHomePlaceholderViewModel.kt` | Tự gắn click `action_ownerHome_to_account`; ViewModel import `feature.account.ui.account.initialsOf` (import chéo `feature/auth` → `feature/account/ui`, trái constitution I) | Dùng `MainTabHeader.bind`; xóa `OwnerHomePlaceholderViewModel` nếu chỉ còn phục vụ avatar. |
| `RES/layout/fragment_home_placeholder.xml`, `PlayerHomePlaceholderFragment.kt` | Màn tạm Trang chủ, không có avatar | Nếu spec 020 chưa thay bằng `HomeFragment` thật: thêm header dùng chung để người chơi vẫn vào được trang Tài khoản sau khi bỏ tab. |
| `feature/account/ui/account/AccountFormat.kt` | `initialsOf` | Chuyển sang `core` (§5); sửa import ở `AccountFragment`, `EditProfileFragment`, `OwnerHomePlaceholderViewModel`. |
| Mới | — | `core/ui/header/{AccountAvatarView, MainTabHeader}.kt`, `RES/layout/view_main_tab_header.xml`, `core/session/{AccountAvatarState, AccountAvatarSource, AccountEntryNavigator}.kt` + implementation và Hilt binding. |
| Tài liệu của chính 011 | `research.md` R14 ("ở tab Tài khoản / Cơ sở"), `tasks.md` T028 và Checkpoint 2 ("vào tab Tài khoản"), `ui/README.md` và ảnh `01_account_*` | Cập nhật khi chạy `/speckit-plan` và `/speckit-tasks` cho thay đổi này; vẽ lại lối vào trên canvas. |

### 6.2. Spec của người khác cần báo (không sửa ở đây)

| Spec | Phụ trách | Chỗ nhắc | Cần báo |
|---|---|---|---|
| 060-my-bookings | B | `spec.md` US1 dòng 31: mở "Lịch đặt của tôi" "từ thanh điều hướng chính"; `contracts/ui-contracts.md`, `plan.md`, `tasks.md` T025–T029 chưa có header; wireframe `06_booking-management_buoc1_lich-dat-cua-toi.png` vẽ theo thanh dưới cũ | Màn gốc tab "Lịch đặt" đặt header dùng chung (§5) trên `TabLayout` 5 trạng thái; không tự vẽ avatar. Lối "Lịch đặt của tôi" trên trang Tài khoản (`smashnow://bookings/mine`, R14) giữ nguyên. |
| 080-drop-in-session | C | `contracts/ui-screens.md` dòng 17 (`MyDropInsFragment`: "Tab "Lịch của tôi" / trang Tài khoản"); `tasks.md` T049 ("từ tab "Lịch của tôi" hoặc trang Tài khoản, thống nhất với A/B") | Màn gốc tab "Vãng lai" dùng header dùng chung. Lối vào "Lượt vãng lai của tôi": vẫn có mục trên trang Tài khoản (`smashnow://dropin/mine`), trang Tài khoản giờ mở từ avatar, không còn là tab. |
| 090-community-group | C | Chưa nhắc header hay tab | Màn gốc tab "Cộng đồng" dùng header dùng chung. |
| 112-owner-operations | D | Màn "Hôm nay" thay `fragment_home_placeholder_owner.xml` và "giữ header" | Header của màn Hôm nay giờ là `view_main_tab_header` (§5), `subtitle` = ngày; không vẽ avatar riêng. |
| 020-court-search | A | Đã cập nhật 2026-11-06 (FR-028, `contracts/ui-screens.md` dòng 9 và 32) | Không cần báo; khi làm `HomeFragment` và tab Tìm kiếm thì dùng §5. |
