# Data Model: Nhóm/CLB và bảng tin cộng đồng (spec 090)

**Ngày**: 2026-10-16 · **Spec**: [spec.md](spec.md) · **Research**: [research.md](research.md) · **Đặc tả CSDL**: [mục 4.10, 4.12, 8](../../docs/design/database/README.md#410-groupsgroupid--c)

- 🔒 nghĩa là chỉ server (Edge Function) ghi. Với spec này, **mọi trường của `groups` và collection con đều do server ghi** (research R1); 🔒 đánh dấu thêm những trường người dùng không bao giờ gửi lên trực tiếp.
- **[MỚI]** là trường hoặc collection mới so với đặc tả CSDL, cần nhóm đồng ý (danh sách cuối file).

---

## 1. `groups/{groupId}`

`groupId` = `requestId` của lần bấm "Tạo nhóm" (research R4).

| Trường | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| `name` | String | 3–50 ký tự sau khi bỏ khoảng trắng đầu/cuối | FR-001; cho phép trùng giữa các nhóm |
| `nameKeywords` 🔒 **[MỚI]** | Array\<String> | — | Tiền tố từng từ, chữ thường, bỏ dấu (R5) |
| `description` | String | 0–500 ký tự | |
| `avatarPath` **[MỚI]** | String? | Bucket `group-avatars`, `{groupId}/avatar.{jpg,png,webp}` | Thay `avatarUrl` của đặc tả CSDL (R8); `null` thì dùng ảnh mặc định |
| `districtId` | String | Có trong `districts` | |
| `ownerId` 🔒 | String | Đúng một chủ nhóm | Đổi khi `transferOwner` |
| `visibility` | String | `PUBLIC`, `PRIVATE` | |
| `memberCount` 🔒 | Int | ≥ 1 khi `ACTIVE` | Chỉ đổi trong transaction (R3) |
| `pendingRequestCount` 🔒 **[MỚI]** | Int | ≥ 0 | Hiện số trên nút "Yêu cầu tham gia" cho chủ nhóm/quản trị viên |
| `status` 🔒 **[MỚI]** | String | `ACTIVE`, `LOCKED`, `DELETED` | `LOCKED` do spec 120 ghi (R17); `DELETED` trong lúc xóa dở (R13) |
| `createdAt`, `updatedAt` | Timestamp | server | |

**Máy trạng thái nhóm:**

```
(chưa có) ──create──▶ ACTIVE ──delete (chủ nhóm, gõ đúng tên)──▶ DELETED ──(xóa xong dữ liệu)──▶ (không còn)
                        ▲  │
          admin mở khóa └──┴── admin khóa (spec 120) ──▶ LOCKED ──delete──▶ DELETED
```

## 2. `groups/{groupId}/members/{uid}`

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `userId` **[MỚI]** | String | Bằng ID document; dùng cho truy vấn collection group "Nhóm của tôi" |
| `groupId` **[MỚI]** | String | Biết document thuộc nhóm nào khi đọc collection group |
| `role` | String | `OWNER`, `ADMIN`, `MEMBER`. Đúng một `OWNER` mỗi nhóm |
| `userSnapshot` 🔒 **[MỚI]** | Map | `displayName`, `avatarPath` (chép từ `users` lúc vào nhóm, R18) |
| `joinedAt` | Timestamp | Thứ tự chuyển quyền khi chủ nhóm xóa tài khoản (R16) |
| `createdAt`, `updatedAt` | Timestamp | |

## 3. `groups/{groupId}/joinRequests/{uid}` **[MỚI]**

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `userId` | String | |
| `userSnapshot` 🔒 | Map | `displayName`, `avatarPath` |
| `wasRemoved` | Boolean | `true` nếu người này từng bị mời ra (FR-008); hiện nhãn cho người duyệt |
| `requestId` | String | Gửi lại cùng `requestId` thì trả yêu cầu cũ |
| `createdAt` | Timestamp | Sắp danh sách yêu cầu, cũ nhất trước |

Duyệt → xóa yêu cầu, tạo `members/{uid}`. Từ chối hoặc người xin tự hủy → xóa yêu cầu.

## 4. `groups/{groupId}/removals/{uid}` **[MỚI]**

`userId`, `removedBy` (uid), `removedAt`. Có document này thì `join` vào nhóm công khai thành yêu cầu chờ duyệt (FR-008). Được duyệt vào lại thì xóa.

## 5. `groups/{groupId}/posts/{postId}`

`postId` = `requestId` của lần bấm "Đăng" (research R9).

| Trường | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| `groupId` **[MỚI]** | String | | Phục vụ collection group và `targetPath` báo cáo |
| `authorId` | String | Là thành viên lúc đăng | |
| `authorSnapshot` 🔒 **[MỚI]** | Map? | `displayName`, `avatarPath` | `null` khi người đăng xóa tài khoản |
| `authorDeleted` 🔒 **[MỚI]** | Boolean | | Hiện "Người dùng đã xóa" (R16) |
| `type` | String | `POST`, `EVENT` | |
| `content` | String | `POST`: 1–2000 ký tự. `EVENT`: mô tả 0–2000 | FR-011, FR-015 |
| `photoPaths` | Array\<String> | 0–4 phần tử, mỗi path bắt đầu bằng `{groupId}/{postId}/` | FR-011; object phải tồn tại khi ghi (R9) |
| `eventTitle` **[MỚI]** | String? | `EVENT`: bắt buộc, 1–100 ký tự | FR-015 |
| `eventAt` | Timestamp? | `EVENT`: bắt buộc, > thời điểm server lúc tạo/sửa | Đã có trong đặc tả CSDL |
| `eventLocation` **[MỚI]** | String? | `EVENT`: bắt buộc, 1–200 ký tự, chữ tự do | |
| `expectedCount` **[MỚI]** | Int? | `EVENT`: 2–100 | Chỉ để tham khảo (FR-016) |
| `attendeeCount` 🔒 **[MỚI]** | Int | ≥ 0, chỉ `EVENT` | Chỉ đổi trong transaction (R3) |
| `status` 🔒 | String | `VISIBLE`, `HIDDEN` | |
| `hiddenBy` 🔒 **[MỚI]** | String? | `GROUP`, `SYSTEM` | R7 |
| `hiddenByUid` 🔒 **[MỚI]** | String? | | |
| `editedAt` 🔒 **[MỚI]** | Timestamp? | | Có giá trị thì hiện "Đã chỉnh sửa" |
| `createdAt`, `updatedAt` | Timestamp | | Bảng tin sắp `createdAt desc` |

**Máy trạng thái bài:**

```
create ──▶ VISIBLE ──hide (chủ nhóm/QTV)──▶ HIDDEN(GROUP) ──unhide (chủ nhóm/QTV)──▶ VISIBLE
              │                                   │
              └──admin ẩn (spec 120)──▶ HIDDEN(SYSTEM) ◀──admin ẩn──┘   (nhóm không unhide được)
VISIBLE / HIDDEN ──delete (người đăng)──▶ (xóa document và ảnh)
```

Trạng thái hiển thị của sự kiện suy ra ở app, không lưu: "Đã diễn ra" khi `eventAt ≤ now`; "Vượt số dự kiến" khi `attendeeCount > expectedCount`.

## 6. `groups/{groupId}/posts/{postId}/attendees/{uid}` **[MỚI]**

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `userId` | String | |
| `groupId`, `postId` | String | Cho collection group khi bỏ tham gia lúc rời nhóm (R11) |
| `eventAt` | Timestamp | Chép từ bài; cập nhật khi sửa giờ sự kiện |
| `userSnapshot` 🔒 | Map | `displayName`, `avatarPath` |
| `joinedAt` | Timestamp | Danh sách người tham gia sắp theo mốc này |

## 7. `reports/{reportId}` (của D, spec 120)

Spec 090 chỉ tạo báo cáo loại `POST`, theo [research R14](research.md#r14-báo-cáo-bài-đăng-fr-018):
- `reportId = POST_{groupId}_{postId}_{reporterId}` (ID tất định, mỗi người một lần);
- `reporterId`, `targetType = POST`, `targetPath = groups/{groupId}/posts/{postId}`;
- `reason` ∈ `SPAM`, `OFFENSIVE`, `SCAM`, `INAPPROPRIATE`, `OTHER`; `detail` ≤ 500 ký tự (bắt buộc khi `OTHER`);
- `status = OPEN`, `action = NONE`, `createdAt`.

## 8. Quy tắc kiểm tra chính

| Thao tác | Quy tắc | Lỗi | FR |
|---|---|---|---|
| Mọi thao tác ghi | Đã đăng nhập, `accountStatus == ACTIVE` | `UNAUTHENTICATED`, `ACCOUNT_LOCKED` | — |
| Mọi thao tác ghi trừ `leave` | Nhóm `ACTIVE` | `GROUP_NOT_FOUND` (không có hoặc `DELETED`), `GROUP_LOCKED` | Edge Cases |
| `create` | Tên, mô tả, khu vực hợp lệ; chủ < 5 nhóm `ACTIVE`/`LOCKED` | `INVALID_ARGUMENT`, `OWNER_LIMIT_REACHED` | FR-001 |
| `create` lặp | `groups/{requestId}` đã có, cùng `ownerId` | Trả nhóm cũ `200` | FR-004 |
| `join` | Chưa là thành viên, chưa có yêu cầu | `ALREADY_MEMBER`, `ALREADY_REQUESTED` | FR-006 |
| `join` | `PUBLIC` và không có `removals/{uid}` → vào ngay; còn lại → tạo `joinRequests` | — | FR-006, FR-008 |
| `leave` | Là thành viên, không phải `OWNER` | `NOT_MEMBER`, `OWNER_MUST_TRANSFER` | FR-007 |
| `setRole`, `transferOwner` | Người gọi là `OWNER` | `FORBIDDEN` | FR-009 |
| `remove` | `OWNER` mời bất kỳ ai trừ mình; `ADMIN` chỉ mời `MEMBER` | `FORBIDDEN` | FR-009 |
| `approve`, `reject` | Người gọi là `OWNER`/`ADMIN`, có yêu cầu | `FORBIDDEN`, `REQUEST_NOT_FOUND` | FR-006 |
| `update` nhóm | Người gọi là `OWNER` | `FORBIDDEN` | FR-002 |
| `delete` nhóm | Người gọi là `OWNER`, `confirmName == name` | `FORBIDDEN`, `CONFIRM_NAME_MISMATCH` | FR-002 |
| `group-post create` | Là thành viên; nội dung, ảnh, trường sự kiện hợp lệ; ảnh tồn tại | `NOT_MEMBER`, `INVALID_ARGUMENT`, `PHOTO_MISSING` | FR-011, FR-015 |
| `update`, `delete` bài | Người gọi là `authorId` | `FORBIDDEN` | FR-013 |
| `hide`, `unhide` | `OWNER`/`ADMIN`; `unhide` khi `hiddenBy == GROUP` | `FORBIDDEN`, `HIDDEN_BY_SYSTEM` | FR-019 |
| `attend`, `unattend` | Là thành viên; bài `EVENT`, `VISIBLE`, `eventAt > now` | `NOT_MEMBER`, `EVENT_PAST` | FR-016, FR-017 |
| `report` | Đọc được bài (thành viên, bài `VISIBLE`), không phải người đăng | `NOT_MEMBER`, `OWN_POST`, `ALREADY_REPORTED` | FR-018 |

## 9. Index cần thêm vào `firestore.indexes.json`

| Collection | Trường | Dùng cho |
|---|---|---|
| `groups` | `status`, `memberCount desc` | Danh sách nhóm không lọc |
| `groups` | `status`, `districtId`, `memberCount desc` | Lọc khu vực |
| `groups` | `status`, `nameKeywords` (array-contains), `memberCount desc` | Tìm theo tên |
| `groups` | `status`, `nameKeywords` (array-contains), `districtId`, `memberCount desc` | Tên + khu vực |
| `groups` | `ownerId`, `status` | Đếm giới hạn 5 nhóm (R4) |
| `members` (collection group) | `userId`, `joinedAt desc` | "Nhóm của tôi" |
| `members` (collection) | `role`, `joinedAt` | Danh sách thành viên, chuyển quyền (R16) |
| `posts` (collection) | `status`, `createdAt desc` | Bảng tin thành viên |
| `posts` (collection) | `authorId`, `status`, `createdAt desc` | Bài bị ẩn của chính mình |
| `posts` (collection group) | `authorId` | Ẩn danh khi xóa tài khoản (R16) |
| `attendees` (collection group) | `userId`, `groupId`, `eventAt` | Bỏ tham gia khi rời nhóm (R11) |
| `joinRequests` (collection) | `createdAt` | Danh sách yêu cầu |

## 10. Mô hình phía app (Kotlin, `feature/community/domain`)

| Lớp | Nội dung |
|---|---|
| `Group` | `id`, `name`, `description`, `avatarPath`, `districtId`, `ownerId`, `visibility`, `memberCount`, `pendingRequestCount`, `status`; `isLocked` |
| `GroupMembership` | `role: GroupRole?` (`null` = chưa là thành viên), `pending: Boolean`; quyết định nút "Tham gia" / "Xin tham gia" / "Đang chờ duyệt" |
| `GroupMember` | `userId`, `role`, `displayName`, `avatarPath`, `joinedAt` |
| `JoinRequest` | `userId`, `displayName`, `avatarPath`, `wasRemoved`, `createdAt` |
| `Post` | `id`, `type`, `content`, `photoPaths`, `author` (`isDeleted`), `status`, `hiddenBy`, `isEdited`, `createdAt`, `event: EventInfo?` |
| `EventInfo` | `title`, `at: Instant`, `location`, `expectedCount`, `attendeeCount`, `iAmAttending`; `isPast(now)`, `isOverExpected` |
| `PostDraft` | `requestId`, `type`, `content`, `photos: List<DraftPhoto>`, trường sự kiện; giữ qua `SavedStateHandle` |
| `GroupPermissions` | Hàm thuần `canEdit`, `canManageRoles`, `canRemove(target)`, `canModerate`, `canReport(post)` theo bảng mục 8 (chỉ để ẩn/hiện nút; server vẫn kiểm tra) |
| Enum | `GroupVisibility` (`PUBLIC`, `PRIVATE`), `GroupRole` (`OWNER`, `ADMIN`, `MEMBER`), `GroupStatus`, `PostType`, `PostStatus`, `HiddenBy`, `ReportReason` |

Thời gian là `Instant`, hiển thị theo `Asia/Ho_Chi_Minh` bằng `core/format/TimeFormat` (spec 080 T014).

---

## Danh sách thay đổi cần đưa vào `docs/design/database/README.md`

1. **Mục 4.10 `groups`:**
   - đổi `avatarUrl` → `avatarPath` (bucket `group-avatars`);
   - thêm `nameKeywords` 🔒, `pendingRequestCount` 🔒, `status` 🔒 (`ACTIVE`, `LOCKED`, `DELETED`);
   - `members`: thêm `userId`, `groupId`, `userSnapshot` 🔒;
   - thêm collection con `joinRequests/{uid}`, `removals/{uid}`;
   - `posts`: thêm `groupId`, `authorSnapshot` 🔒, `authorDeleted` 🔒, `eventTitle`, `eventLocation`, `expectedCount`, `attendeeCount` 🔒, `hiddenBy` 🔒, `hiddenByUid` 🔒, `editedAt` 🔒; `status` là `VISIBLE`, `HIDDEN`;
   - thêm `posts/{postId}/attendees/{uid}`.
2. **Mục 4.12 `reports`:** quy ước `reportId = POST_{groupId}_{postId}_{uid}` và danh sách `reason` dùng chung (R14).
3. **Mục 5:** thêm `groups` (`requestId`), `posts` (`requestId`), `members`/`joinRequests`/`removals`/`attendees` (`uid`).
4. **Mục 6:** thêm index ở mục 9.
5. **Mục 7:** sửa dòng `groups` thành "giới thiệu nhóm ai cũng R; bảng tin, thành viên chỉ thành viên R; mọi ghi qua Edge Function" (R6).
6. **Mục 8:** thêm bucket public `group-avatars` (`{groupId}/avatar.*`, ghi qua signed URL của `group-manage`); `group-media` object là `{groupId}/{postId}/{n}.{ext}`, đọc/ghi qua `group-media-url`.
7. **Mục 8a:** thêm `group-manage`, `group-post`, `group-media-url` (C).
