# Contract: Edge Functions của spec 090

**Vị trí mã:** `supabase/functions/group-manage/`, `supabase/functions/group-post/`, `supabase/functions/group-media-url/`, code dùng chung `supabase/functions/_shared/groups.ts`.

**Chung cho cả ba function** (giống contract của spec 070, 080):
- Header `Authorization: Bearer <Firebase ID token>`; xác thực bằng `_shared/auth.ts` (010-T021), đọc `users/{uid}.accountStatus` (`403 ACCOUNT_LOCKED`).
- Transaction qua `_shared/firestore.ts` (B), thử lại tối đa 8 lần khi `ABORTED`; hết lượt trả `503 BUSY`.
- Body lỗi: `{ "error": { "code": "...", "message": "...", ...extra } }`.
- Mọi request ghi có `requestId` (UUID). Gửi lại cùng `requestId` thì trả kết quả cũ (`200`).
- Thông báo `GROUP_ACTIVITY` gửi **sau khi commit**, lỗi bỏ qua ([research R15](../research.md#r15-thông-báo-fr-021)).

---

## 1. `group-manage`

Request: `{ "action": "...", "requestId": "uuid", ...tham số }`. Quy tắc kiểm tra từng action: [data-model mục 8](../data-model.md#8-quy-tắc-kiểm-tra-chính).

| `action` | Tham số | Kết quả | Thông báo |
|---|---|---|---|
| `create` | `name`, `description`, `districtId`, `visibility`, `hasAvatar` | `201 { group, avatarUpload? }`; `avatarUpload = { path, signedUrl, token }` khi `hasAvatar` | — |
| `update` | `groupId`, các trường đổi (`name`, `description`, `districtId`, `visibility`), `avatar` (`KEEP`, `REPLACE`, `CONFIRM`, `REMOVE`) | `200 { group, avatarUpload?, autoApproved }` | `JOIN_APPROVED` cho người được duyệt tự động (R12) |
| `delete` | `groupId`, `confirmName` | `200 { deleted: true }` | — |
| `join` | `groupId` | `200 { result }` với `result` là `JOINED` hoặc `REQUESTED` | `JOIN_REQUESTED` khi `REQUESTED` |
| `cancelRequest` | `groupId` | `200` | — |
| `leave` | `groupId` | `200` | — |
| `approve`, `reject` | `groupId`, `userId` | `200` | `JOIN_APPROVED` / `JOIN_REJECTED` |
| `setRole` | `groupId`, `userId`, `role` (`ADMIN` hoặc `MEMBER`) | `200` | — |
| `transferOwner` | `groupId`, `userId` | `200` | — |
| `remove` | `groupId`, `userId` | `200` | `REMOVED` |

**Ảnh đại diện:**
1. `create` (hoặc `update` với `avatar = REPLACE`) trả signed upload URL vào `group-avatars/{groupId}/avatar.{ext}`.
2. App tải ảnh lên, rồi gọi `update` với `avatar = CONFIRM`. Server kiểm tra object có thật rồi mới ghi `avatarPath`.
3. Tải lỗi: nhóm vẫn có, dùng ảnh mặc định; app cho thử lại hoặc bỏ ảnh (AC 1.3).

**Mọi action đổi danh sách thành viên** (`create`, `join` khi `JOINED`, `approve`, `update` có tự duyệt, `leave`, `remove`, `delete`) gọi `onGroupMembershipChanged` trong cùng transaction (mục 4).

## 2. `group-post`

| `action` | Tham số | Kết quả | Thông báo |
|---|---|---|---|
| `create` | `groupId`, `requestId` (= `postId`), `type`, `content`, `photoPaths`, `eventTitle?`, `eventAt?`, `eventLocation?`, `expectedCount?` | `201 { post }` | `EVENT_CREATED` khi `type = EVENT` |
| `update` | `groupId`, `postId`, các trường đổi, `photoPaths` (bộ mới) | `200 { post }`; xóa ảnh không còn dùng | `EVENT_UPDATED` khi đổi `eventAt` hoặc `eventLocation` |
| `delete` | `groupId`, `postId` | `200`; xóa document, `attendees`, ảnh | `EVENT_DELETED` khi là sự kiện chưa diễn ra |
| `hide`, `unhide` | `groupId`, `postId` | `200` | — |
| `attend`, `unattend` | `groupId`, `postId` | `200 { attendeeCount, attending }` | — |
| `report` | `groupId`, `postId`, `reason`, `detail?` | `201 { reportId }` | — |

## 3. `group-media-url`

| `action` | Tham số | Kết quả |
|---|---|---|
| `upload` | `groupId`, `postId` (bài đang soạn), `files: [{ index, contentType, size }]` (≤ 4) | `200 { uploads: [{ path, signedUrl, token }] }`; path `{groupId}/{postId}/{index}.{ext}` |
| `download` | `paths` (≤ 40) | `200 { urls: { [path]: signedUrl }, expiresAt }`; bỏ qua path của nhóm mình không là thành viên |
| `discard` | `groupId`, `postId` | `200`; xóa ảnh của bài đang soạn khi **chưa** có document `posts/{postId}` |

Kiểm tra: người gọi là thành viên nhóm; `contentType` thuộc `image/jpeg`, `image/png`, `image/webp`; `size ≤ 2 MB` (bucket cũng giới hạn).

## 4. Hàm dùng chung (`_shared/groups.ts`)

```ts
// Gọi trong cùng transaction mỗi khi danh sách thành viên nhóm đổi. Spec 090 để thân trống, spec 091 cài
// (cập nhật chats/{chatId}.memberIds; tạo chat khi created; khóa/xóa chat khi deleted).
export async function onGroupMembershipChanged(tx: Transaction, groupId: string,
  change: { added?: string[]; removed?: string[]; created?: boolean; deleted?: boolean }): Promise<void>

// delete-account (A) gọi để ẩn danh bài, rời nhóm, chuyển quyền chủ nhóm (research R16)
export async function anonymizeCommunityGroups(uid: string): Promise<{ transferredGroups: string[]; deletedGroups: string[] }>

// Kiểm tra quyền dùng lại ở cả 3 function
export async function getMembership(tx: Transaction, groupId: string, uid: string): Promise<"OWNER" | "ADMIN" | "MEMBER" | null>

// Tiền tố tìm kiếm, cùng cách venues.nameKeywords của A; đặt ở _shared/keywords.ts nếu A chưa có
export function nameKeywords(name: string): string[]
```

## 5. Mã lỗi

| HTTP | `error.code` | Khi nào | App hiển thị |
|---|---|---|---|
| 400 | `INVALID_ARGUMENT` | Sai độ dài, thiếu trường, sai khu vực, quá 4 ảnh | Lỗi tại ô tương ứng |
| 400 | `CONFIRM_NAME_MISMATCH` | Gõ sai tên khi xóa nhóm | "Tên nhóm không khớp" |
| 400 | `PHOTO_MISSING` | Ảnh trong `photoPaths` chưa có trong Storage | Đánh dấu ảnh lỗi, cho thử lại |
| 401 | `UNAUTHENTICATED` | Thiếu hoặc sai token | Mời đăng nhập |
| 403 | `ACCOUNT_LOCKED` | Tài khoản bị khóa | "Tài khoản đã bị khóa" |
| 403 | `NOT_MEMBER` | Không phải thành viên | Về trang giới thiệu |
| 403 | `FORBIDDEN` | Sai vai trò | "Bạn không có quyền" |
| 403 | `HIDDEN_BY_SYSTEM` | `unhide` bài admin ẩn | "Bài đã bị quản trị hệ thống ẩn" |
| 403 | `OWN_POST` | Báo cáo bài của mình | (nút đã ẩn) |
| 404 | `GROUP_NOT_FOUND`, `POST_NOT_FOUND`, `REQUEST_NOT_FOUND` | Không có hoặc đã xóa | "Không còn tồn tại" |
| 409 | `OWNER_LIMIT_REACHED` | Đã làm chủ 5 nhóm | "Bạn đã làm chủ tối đa 5 nhóm" |
| 409 | `ALREADY_MEMBER`, `ALREADY_REQUESTED` | Tham gia lặp | Làm mới trạng thái |
| 409 | `OWNER_MUST_TRANSFER` | Chủ nhóm rời (`soleMember` khi chỉ còn một mình) | Mở chọn người nhận quyền, hoặc gợi ý xóa nhóm |
| 409 | `EVENT_PAST` | Tham gia sự kiện đã diễn ra | "Sự kiện đã diễn ra" |
| 409 | `ALREADY_REPORTED` | Báo cáo lặp | "Bạn đã báo cáo bài này" |
| 409 | `TOO_MANY_PENDING` | Đổi sang công khai khi > 200 yêu cầu chờ | "Hãy duyệt bớt yêu cầu trước" |
| 423 | `GROUP_LOCKED` | Nhóm bị admin khóa | "Nhóm đã bị tạm khóa" |
| 503 | `BUSY` | Hết lượt thử transaction | Tự gửi lại cùng `requestId` tối đa 2 lần |

## 6. Test Edge Function (`deno test`, Firebase Emulator + Supabase local)

| # | Ca | Kỳ vọng | Liên quan |
|---|---|---|---|
| G1 | `create` hợp lệ | `members/{uid}.role = OWNER`, `memberCount = 1`, `nameKeywords` đúng, hook `created` gọi 1 lần | US1, FR-001 |
| G2 | `create` cùng `requestId` 5 lần (cả song song) | 1 nhóm; 4 lần sau `200` | FR-004 |
| G3 | Tạo nhóm thứ 6 | `OWNER_LIMIT_REACHED` | AC 1.4 |
| G4 | Tên 2 / 51 ký tự, mô tả 501, khu vực không có | `INVALID_ARGUMENT` | AC 1.2 |
| G5 | **20 người `join` nhóm công khai song song, lặp 10 lần** | `memberCount` = trước + 20; đúng 20 document `members` mới; hook `added` đủ 20 uid | SC-003, FR-003 |
| G6 | `join` nhóm riêng tư → `approve`; `join` → `reject`; `cancelRequest` | `REQUESTED` rồi thành viên / yêu cầu bị xóa; `pendingRequestCount` đúng | US2, FR-006 |
| G7 | `approve` cùng một người 2 lần song song | `memberCount` tăng đúng 1 | FR-003 |
| G8 | Người bị `remove` gọi `join` nhóm công khai | `REQUESTED`, `wasRemoved = true` | FR-008 |
| G9 | Chủ nhóm `leave` (còn người / một mình) | `OWNER_MUST_TRANSFER` (`soleMember` false / true) | FR-007 |
| G10 | Quản trị viên `remove` chủ nhóm / quản trị viên khác / thành viên | `FORBIDDEN` / `FORBIDDEN` / thành công + `removals` | FR-009, SC-004 |
| G11 | Thành viên thường `setRole`, `update`, `delete`, `approve` | `FORBIDDEN` | FR-002, FR-009 |
| G12 | `transferOwner` rồi chủ cũ `leave` | `ownerId` đổi, chủ cũ thành `ADMIN` rồi rời được | AC 5.5 |
| G13 | Đổi `PRIVATE → PUBLIC` với 3 yêu cầu (1 người từng bị mời ra) | 2 người thành thành viên, 1 vẫn chờ; `memberCount +2` | Edge Cases |
| G14 | `delete` gõ sai tên / đúng tên; gọi lại `delete` khi đang xóa dở | `CONFIRM_NAME_MISMATCH` / `DELETED` rồi hết dữ liệu và ảnh; lần gọi lại chạy tiếp | FR-002 |
| G15 | `leave` khi đang tham gia 1 sự kiện sắp tới và 1 sự kiện đã qua | Bỏ khỏi sự kiện sắp tới (`attendeeCount` giảm 1), giữ sự kiện đã qua | Edge Cases |
| P1 | `group-post create` có 2 ảnh đã tải / 1 ảnh chưa tải | `VISIBLE` với 2 path đúng thứ tự / `PHOTO_MISSING`, không tạo bài | US3, FR-011 |
| P2 | Không phải thành viên `create`; thành viên `update`/`delete` bài người khác | `NOT_MEMBER`; `FORBIDDEN` | AC 3.5, SC-004 |
| P3 | `create` cùng `requestId` 3 lần | 1 bài | Edge Cases |
| P4 | `delete` bài có ảnh | Document và ảnh trong `group-media` đều mất | AC 3.4 |
| P5 | Sự kiện: `eventAt` quá khứ, thiếu tiêu đề hoặc địa điểm, `expectedCount` 1 / 101 | `INVALID_ARGUMENT` | AC 4.2 |
| P6 | **20 người `attend` song song, lặp 10 lần**; 1 người bấm 5 lần | `attendeeCount` = 20; người bấm lặp chỉ tính 1 | SC-003, FR-016 |
| P7 | `attend` khi `attendeeCount == expectedCount` / sự kiện đã qua | Thành công / `EVENT_PAST` | AC 4.4, 4.5 |
| P8 | `update` đổi giờ sự kiện | `editedAt` có giá trị; `attendees.eventAt` cập nhật; `EVENT_UPDATED` gửi cho người tham gia trừ người sửa | AC 4.6 |
| P9 | Quản trị viên `hide` → `unhide`; bài `hiddenBy = SYSTEM` → `unhide` | Thành công / `HIDDEN_BY_SYSTEM` | FR-019, AC 6.4 |
| P10 | `report` lần 1 / lần 2 / bài của mình / không phải thành viên | `201` với `reportId` tất định / `ALREADY_REPORTED` / `OWN_POST` / `NOT_MEMBER` | FR-018 |
| P11 | Nhóm `LOCKED`: `create` bài, `join`; `leave` | `GROUP_LOCKED`; `leave` thành công | Edge Cases |
| M1 | `group-media-url upload` khi không phải thành viên / file 3 MB / `image/gif` | `NOT_MEMBER` / `INVALID_ARGUMENT` / `INVALID_ARGUMENT` | FR-011, FR-014 |
| M2 | `download` 3 path, 1 path thuộc nhóm không là thành viên | Trả 2 URL | FR-014, SC-004 |
| M3 | `anonymizeCommunityGroups` với chủ nhóm có 1 quản trị viên và 2 thành viên | Quyền chủ nhóm sang quản trị viên; bài `authorDeleted = true` | Edge Cases (R16) |
