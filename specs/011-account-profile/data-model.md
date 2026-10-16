# Data Model: Hồ sơ và quản lý tài khoản (spec 011)

Spec 011 dùng lại dữ liệu của spec 010 ([data-model 010](../010-account-auth/data-model.md)) và chỉ thêm phần dưới đây.

| Nơi | Chứa gì | Mới ở 011 |
|---|---|---|
| Firestore `users/{uid}` | Hồ sơ | Kiểm tra từng trường người dùng sửa; trường `deletionRequestedAt` 🔒 |
| Firestore `districts/{id}` | Danh mục khu vực | Schema tối thiểu, Rules đọc |
| Firestore `accountDeletions/{uid}` | Tiến trình xóa tài khoản | Collection mới, chỉ server |
| Supabase Storage `avatars` | Ảnh đại diện | Đường dẫn có phiên bản, RLS |
| Firebase Auth | Email, mật khẩu, phiên | Đổi mật khẩu, đổi email, xóa người dùng |
| Bộ nhớ app | `AppMode` | Chỉ trong bộ nhớ, không lưu |

---

## 1. `users/{uid}` (A): phần 011 thêm hoặc làm rõ

| Trường | Kiểu | Ai ghi | Ràng buộc (Rules kiểm tra khi trường đổi) |
|---|---|---|---|
| `displayName` | String | Chủ tài khoản | (010) 2–50 ký tự sau `trim` |
| `level` | String? | Chủ tài khoản | `null` hoặc `BEGINNER` \| `INTERMEDIATE` \| `ADVANCED` (hiển thị: Mới chơi, Trung bình, Khá/Nâng cao) |
| `favoriteDistrictIds` | Array\<String> | Chủ tài khoản | 0–5 phần tử, không trùng, mỗi ID là `districts/{id}` có `isActive == true` |
| `avatarPath` | String? | Chủ tài khoản | `null` hoặc `{uid}/avatar_{epochMillis 13 chữ số}.jpg` (**đổi** so với `{uid}/avatar.jpg` của đặc tả CSDL) |
| `email` | String? | `on-signup`, `sync-account-email` | Chỉ server; lấy từ token đã xác minh, chữ thường |
| `fcmTokens` | Array\<String> | Chủ tài khoản | `arrayRemove` token của máy khi đăng xuất (011); thêm token do spec 100 |
| `deletionRequestedAt` 🔒 **[MỚI]** | Timestamp? | `delete-account` | Có giá trị thì `requireUser()` trả `403 ACCOUNT_DELETING` |
| `updatedAt` | Timestamp | Chủ tài khoản / server | `== request.time` khi chủ tài khoản ghi |

Các trường 🔒 (`role`, `ownerStatus`, `ownerRevokedReason`, `accountStatus`, `walletBalance`, `termsAcceptedAt`) giữ nguyên quy tắc của 010: chỉ hiển thị (FR-005), Rules chặn ghi.

## 2. Supabase Storage `avatars` (A)

| Thuộc tính | Giá trị |
|---|---|
| Loại | public |
| Giới hạn | `file_size_limit = 2 MB`, `allowed_mime_types = image/jpeg, image/png, image/webp` |
| Object | `{uid}/avatar_{epochMillis}.jpg`, JPEG tối đa 512 × 512 px |
| Ghi (`INSERT`) | `auth.jwt() ->> 'sub'` = thư mục đầu tiên, và `auth.jwt() ->> 'appRole'` khác `null` |
| Xóa (`DELETE`) | Như ghi |
| Sửa (`UPDATE`) | Không ai (mỗi lần đổi ảnh là object mới) |
| Đọc | URL công khai `{SUPABASE_URL}/storage/v1/object/public/avatars/{path}` |
| Server | `delete-account` xóa cả thư mục `{uid}/` bằng service role |

Chi tiết policy và test: [contracts/storage-avatars.md](contracts/storage-avatars.md).

## 3. Firestore `districts/{id}` (A)

Đặc tả CSDL mục 2 giao `districts` cho A nhưng chưa có bảng trường. Spec 011 là spec đầu tiên đọc nên định nghĩa bản tối thiểu; spec 020 (tìm sân) và 120 (admin quản lý danh mục) dùng chung.

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `name` | String | "Quận 10", "TP Thủ Đức" |
| `city` | String | `HCM` (phạm vi demo) |
| `sortOrder` | Int | Thứ tự hiển thị |
| `isActive` | Boolean | `false` thì không chọn mới được; người đã chọn vẫn giữ cho tới lần lưu sau |
| `createdAt`, `updatedAt` | Timestamp | |

- ID tất định, chữ thường không dấu: `q1`, `q3`, `q10`, `thu-duc`, `binh-thanh`…
- Rules: `isInitialized()` được `get`/`list`; chỉ admin ghi (spec 120).
- Truy vấn: `where isActive == true order by sortOrder`. Cần index ghép `isActive` + `sortOrder`.
- Dữ liệu mẫu: `scripts/seed/districts.ts` (22 quận/huyện TP.HCM).

## 4. Firestore `accountDeletions/{uid}` (A) **[MỚI]**

Tiến trình xóa tài khoản. Chỉ `delete-account` và `delete-account-sweeper` đọc/ghi; Rules chặn mọi client. **Không chứa thông tin cá nhân** (chỉ `uid` và ID document).

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `state` | String | `COMMITTED` → `DONE` |
| `requestedAt` | Timestamp | Thời điểm chốt (giờ server) |
| `wasOwner` | Boolean | Có cơ sở lúc chốt |
| `notify` | Array\<Map> | `{ kind: BOOKING \| DROPIN, id, recipientUid }` của khách cần báo hủy |
| `steps` | Map\<String, String> | Tên bước → `DONE` \| `SKIPPED` \| `FAILED` |
| `attempts` | Int | Số lần chạy dọn dẹp |
| `lastError` | String? | Mã lỗi gần nhất (không chứa dữ liệu người dùng) |
| `finalSweepAfter` | Timestamp | `requestedAt + 65 phút` |
| `completedAt` | Timestamp? | Khi `state = DONE` |

Giữ lại sau khi `DONE` để `delete-account` gọi lại vẫn trả kết quả idempotent. `recipientUid` là `uid` của khách (người còn tài khoản), không phải dữ liệu của người xóa.

### Chuyển trạng thái xóa tài khoản

```text
(không có) ──preview──▶ (không có)                     // chỉ đọc
(không có) ──confirm: transaction OK──▶ COMMITTED ──dọn dẹp xong──▶ DONE
(không có) ──confirm: transaction lỗi──▶ (không có)    // tài khoản dùng bình thường (FR-021)
COMMITTED ──sweeper mỗi 5 phút──▶ COMMITTED | DONE
```

## 5. Ảnh hưởng tới collection của nhóm khác khi xóa tài khoản

Do helper của nhóm sở hữu thực hiện ([research R12](research.md#r12-ẩn-danh-và-các-bước-do-nhóm-khác-cung-cấp)). Bảng này để B, C, D đối chiếu:

| Collection | Thay đổi | Giai đoạn | Chủ helper |
|---|---|---|---|
| `bookings` của người dùng (sắp tới: `HOLD`, `PENDING`, `CONFIRMED` chưa check-in) | `status = CANCELLED`, `cancelledBy = SYSTEM`, `cancelReason = "Tài khoản đã bị xóa"`, `refundAmount` theo chính sách; xóa `slots` | Chốt (transaction) | B |
| `bookings` của khách tại cơ sở của người dùng | `CANCELLED`, `SYSTEM`, `"Cơ sở ngừng hoạt động"`, hoàn 100% vào `users/{khách}.walletBalance`; xóa `slots` | Chốt | B |
| `venues` của người dùng | `status = HIDDEN`, `hiddenReason = OWNER_DELETED` 🔒 **[MỚI, D]** | Chốt | D |
| `dropInSessions` của cơ sở (sắp tới) | Hủy buổi, hoàn 100%, đóng hàng chờ | Chốt | D (112) |
| `registrations/{uid}` của người dùng (sắp tới) | `CANCELLED` hoặc rời hàng chờ, `cancelledBy = SYSTEM` | Chốt | C (080, 081) |
| `bookings` đã qua của người dùng | `userDeleted = true` **[MỚI, B]**, bỏ thông tin liên hệ nếu có | Dọn dẹp | B |
| `reviews` của người dùng | `userId = null`, `userSnapshot` rỗng, `authorDeleted = true` | Dọn dẹp | C (070 R9) |
| `groups`, `posts`, `chats`, `messages` | Snapshot tên/ảnh về `null`, `authorDeleted = true`; chuyển quyền chủ nhóm; chat riêng chỉ đọc | Dọn dẹp | C (090, 091) |
| `ownerApplications` + `owner-docs/{uid}/` | Xóa | Dọn dẹp | D (110) |
| `users/{uid}/favorites`, `searchHistory`, `aiUsage`, `notifications` | Xóa | Dọn dẹp | A |
| `users/{uid}`, `avatars/{uid}/`, tài khoản Firebase Auth | Xóa (Auth sau cùng) | Dọn dẹp | A |

## 6. Mô hình phía app

### `core/session` (mở rộng 010)

| Lớp | Trường hoặc giá trị | Ghi chú |
|---|---|---|
| `MyProfile` | (010) `displayName`, `level`, `favoriteDistrictIds`, `avatarPath`, `role`, `accountStatus` + **[MỚI]** `email`, `phone`, `walletBalance: Long`, `ownerStatus`, `ownerRevokedReason`, `deletionRequestedAt` | Đọc từ listener `users/{uid}` |
| `AppMode` **[MỚI]** | `PLAYER`, `OWNER` | Trong bộ nhớ (research R7) |
| `SignOutReason` | (010) `LOCKED` + **[MỚI]** `SESSION_REVOKED`, `ACCOUNT_DELETED` | Thông báo khác nhau trên màn hình đăng nhập/chào |
| `SignOutHook` **[MỚI]** | `suspend fun beforeSignOut(uid: String)` | `FcmSignOutHook`, `GoogleSignOutHook` |

### `feature/account/domain`

| Lớp | Trường hoặc giá trị | Ghi chú |
|---|---|---|
| `PlayerLevel` | `BEGINNER`, `INTERMEDIATE`, `ADVANCED` | Chuỗi hiển thị trong `strings_account.xml` |
| `District` | `id`, `name`, `sortOrder` | |
| `ProfileDraft` | `displayName`, `level: PlayerLevel?`, `districtIds: List<String>` | `isDirty` = khác bản đã lưu (FR-006) |
| `OwnerSection` | `Hidden` · `CanApply` · `Revoked(reason)` · `Pending(submittedAt)` · `Rejected(reason)` · `Approved` | Suy ra theo bảng ở [research R6](research.md#r6-mục-chủ-sân-trên-trang-tài-khoản-fr-008-fr-009-fr-013) |
| `ReauthMethod` | `Password`, `Google`, `Phone(phoneE164)` | Chọn theo `providerData` (R8) |
| `DeletionImpact` | `impactVersion`, `myBookings: List<ImpactBooking>`, `myDropIns: List<ImpactDropIn>`, `waitlistCount`, `ownerImpact: OwnerImpact?`, `walletBalance` | Từ `delete-account` preview |
| `ImpactBooking` | `bookingId`, `venueName`, `startAt`, `status`, `paidAmount`, `refundAmount` | |
| `OwnerImpact` | `venueCount`, `guestBookingCount`, `guestDropInCount`, `guestCount` | |
| `AccountError` | `WrongCurrentPassword`, `SamePassword`, `WeakPassword`, `SameEmail`, `ReauthCancelled`, `ReauthWrongAccount`, `ReauthRequired`, `ImpactChanged(newImpact)`, `DeleteTooLarge`, `AdminSelfDelete`, `ImageTooLarge`, `UnsupportedImage`, `PermissionDenied`, `OwnerRevoked(reason)`, `Network`, `Unknown` | Chuỗi tiếng Việt trong `strings_account.xml` |

### Kiểm tra dữ liệu nhập (thuần Kotlin, có unit test)

| Rule | Điều kiện | Chuỗi lỗi |
|---|---|---|
| `NameRule` (010) | `trim` dài 2–50 | `auth_error_name_length` |
| `DistrictSelectionRule` | 0–5 phần tử, không trùng | `account_error_district_max` |
| `PasswordRule`, `ConfirmPasswordRule` (010) | ≥ 8 ký tự, có chữ và số; nhập lại khớp | (010) |
| `DifferentPasswordRule` | mật khẩu mới ≠ mật khẩu hiện tại | `account_error_same_password` |
| `EmailRule` (010) | định dạng; email mới ≠ email hiện tại | (010), `account_error_same_email` |
| `DeleteConfirmRule` | đúng chuỗi `XÓA` sau `trim` (so chữ hoa, chấp nhận `xóa`) | `account_error_delete_confirm` |
| `AvatarImageRule` | MIME thuộc `image/jpeg`, `image/png`, `image/webp`; sau nén ≤ 2 MB | `account_error_image_type`, `account_error_image_size` |

---

## Danh sách thay đổi cần đưa vào `docs/design/database/README.md`

1. **Mục 4.1 `users`:**
   - `avatarPath`: object `{uid}/avatar_{epochMillis}.jpg` (có phiên bản, tránh cache).
   - Thêm `deletionRequestedAt` 🔒.
   - Câu "Xóa tài khoản…" thay bằng tóm tắt 2 giai đoạn (chốt trong transaction, dọn dẹp idempotent) và link tới contract `delete-account`.
2. **Mục 2:** thêm `accountDeletions/{uid}` (A, chỉ server). Thêm bảng trường cho `districts/{id}`.
3. **Mục 4.3 `venues`:** thêm `hiddenReason` 🔒 (`OWNER_DELETED`) — cần D đồng ý.
4. **Mục 4.5 `bookings`:** thêm `userDeleted` (Boolean) — cần B đồng ý.
5. **Mục 6 (index):** `districts` (`isActive`, `sortOrder`); `ownerApplications` (`uid`, `submittedAt desc`); `bookings` (`userId`, `status`, `startAt`) đã có; `venues` (`ownerId`) — đơn trường, tự có.
6. **Mục 7:** dòng `districts`: "R (đã khởi tạo); admin ghi". Dòng `accountDeletions`: "chỉ server".
7. **Mục 8 (Storage):** dòng `avatars`: object `{uid}/avatar_{epochMillis}.jpg`; không cho `UPDATE`; ghi cần `appRole`.
8. **Mục 8a:** `delete-account` mô tả lại (preview/confirm, 2 giai đoạn); thêm `delete-account-sweeper` (pg_cron 5 phút) và `sync-account-email` (A).
