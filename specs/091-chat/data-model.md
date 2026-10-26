# Data Model: Chat nhóm và chat với chủ sân (spec 091)

**Ngày**: 2026-10-16 · **Spec**: [spec.md](spec.md) · **Research**: [research.md](research.md) · **Đặc tả CSDL**: [mục 4.11, 5, 8](../../docs/design/database/README.md#411-chatschatid--c)

- 🔒 nghĩa là chỉ server (Edge Function) ghi.
- **[MỚI]** là trường hoặc collection mới so với đặc tả CSDL, cần nhóm đồng ý (danh sách cuối file).

---

## 1. `chats/{chatId}`

`chatId` = `{uidNhỏ}_{uidLớn}` (chat riêng) hoặc `group_{groupId}` (chat nhóm) (research R3).

| Trường | Kiểu | Ghi chú |
|---|---|---|
| `type` 🔒 | String | `GROUP`, `DIRECT` |
| `memberIds` 🔒 | Array\<String> | Người trong cuộc. Rules và truy vấn `array-contains` dựa trên trường này. Chat nhóm khớp đúng `groups/{groupId}/members` (R5) |
| `groupId` 🔒 | String? | Chat nhóm |
| `ownerId` 🔒 **[MỚI]** | String? | Chat riêng: chủ sân, cố định lúc tạo (R3) |
| `playerId` 🔒 **[MỚI]** | String? | Chat riêng: người chơi mở chat |
| `venueId` 🔒 | String? | Chat riêng: cơ sở được hỏi gần nhất |
| `venueSnapshot` 🔒 **[MỚI]** | Map? | `name`, `photoPath` của cơ sở được hỏi gần nhất |
| `memberSnapshots` 🔒 **[MỚI]** | Map? | Chat riêng: uid → `{ displayName, avatarPath }` hoặc `null` khi người đó xóa tài khoản |
| `lastMessage` 🔒 | Map? | `messageId`, `senderId`, `type` (`TEXT`, `IMAGE`), `preview` (≤ 100 ký tự đầu, hoặc "Tin nhắn đã được thu hồi"), `createdAt` |
| `unreadCounts` **[MỚI]** | Map\<String, Int> | Số tin chưa đọc của từng người. `chat-send` tăng; chính người đó chỉ được ghi khóa của mình về 0 (R6) |
| `status` 🔒 **[MỚI]** | String | `ACTIVE`, `READ_ONLY` (bên kia đã xóa tài khoản), `DELETED` (nhóm đã xóa) |
| `createdAt` **[MỚI]**, `updatedAt` | Timestamp | `updatedAt` = `createdAt` của tin cuối; danh sách sắp `updatedAt desc` |

## 2. `chats/{chatId}/participants/{uid}` **[MỚI]**

| Trường | Kiểu | Ai ghi | Ghi chú |
|---|---|---|---|
| `userId` | String | server | Bằng ID document |
| `chatId` | String | server | |
| `userSnapshot` 🔒 | Map | server | `displayName`, `avatarPath`; nguồn của `senderSnapshot` |
| `joinedAt` 🔒 | Timestamp | server | |
| `lastReadAt` | Timestamp | chính người đó | Phải bằng `request.time` |
| `muted` | Boolean | chính người đó | Tắt thông báo cuộc này (FR-013) |
| `blockedOther` | Boolean | chính người đó | Chỉ chat riêng (FR-016) |
| `viewingUntil` | Timestamp? | chính người đó | Đang mở cuộc này thì không gửi thông báo (R7); ≤ `request.time + 10 phút`; app tính theo giờ server ước lượng |

## 3. `chats/{chatId}/messages/{messageId}`

`messageId` = `requestId` (UUID) sinh khi bấm "Gửi" (R1).

| Trường | Kiểu | Ràng buộc | Ghi chú |
|---|---|---|---|
| `senderId` 🔒 | String | Trong `memberIds` lúc gửi | |
| `senderSnapshot` 🔒 **[MỚI]** | Map? | Chép từ `participants/{senderId}.userSnapshot` | Tên, ảnh trong chat nhóm; `null` khi người gửi xóa tài khoản |
| `senderDeleted` 🔒 **[MỚI]** | Boolean | | Hiện "Người dùng đã xóa" (R16) |
| `text` 🔒 | String | Tin chữ: 1–1000 ký tự, không chỉ khoảng trắng. Tin ảnh: chú thích 0–1000 | Đã bỏ khoảng trắng đầu/cuối |
| `imagePath` 🔒 | String? | `{chatId}/{messageId}.{jpg,png,webp}` trong `chat-media`; object phải có thật khi ghi | Một ảnh mỗi tin |
| `createdAt` 🔒 | Timestamp | Transform `REQUEST_TIME` lúc commit | Thứ tự tin (R13) |
| `status` 🔒 | String | `NORMAL`, `RECALLED`, `HIDDEN` | |
| `recalledAt` 🔒 **[MỚI]** | Timestamp? | | |

**Máy trạng thái tin:**

```
(chat-send) ──▶ NORMAL ──recall (người gửi, ≤ 10 phút từ createdAt)──▶ RECALLED (text = "", imagePath = null)
                  │
                  └──admin ẩn (spec 120)──▶ HIDDEN
```

**Trạng thái tin phía app** (không lưu Firestore): `PENDING` (trong outbox), `FAILED` (quá 60 giây hoặc lỗi không thử lại được), rồi tin thật khi có trên server.

## 4. `reports/{reportId}` (của D, spec 120)

Giống spec 090 ([data-model 090 mục 7](../090-community-group/data-model.md#7-reportsreportid-của-d-spec-120)):
- `reportId = MESSAGE_{chatId}_{messageId}_{reporterId}`;
- `targetType = MESSAGE`, `targetPath = chats/{chatId}/messages/{messageId}`;
- `reason` ∈ `SPAM`, `OFFENSIVE`, `SCAM`, `INAPPROPRIATE`, `OTHER`; `detail` ≤ 500 ký tự (bắt buộc khi `OTHER`).

## 5. Quy tắc kiểm tra chính

| Thao tác | Quy tắc | Lỗi | FR |
|---|---|---|---|
| Mọi Edge Function | Đã đăng nhập, `accountStatus == ACTIVE` | `UNAUTHENTICATED`, `ACCOUNT_LOCKED` | — |
| `openDirect` | Sân `ACTIVE`, người gọi không phải chủ sân, chủ sân `ownerStatus == APPROVED` (chỉ khi tạo mới) | `VENUE_INACTIVE`, `OWN_VENUE`, `OWNER_NOT_APPROVED` | FR-003, R4 |
| `chat-send` | Người gọi trong `memberIds`; chat `ACTIVE` | `NOT_PARTICIPANT`, `CHAT_READ_ONLY`, `CHAT_NOT_FOUND` | FR-004 |
| `chat-send` chat nhóm | `groups/{groupId}.status == ACTIVE` | `GROUP_LOCKED` | AC 2.5 |
| `chat-send` chat riêng | `participants/{người nhận}.blockedOther != true` | `BLOCKED` | FR-016 |
| `chat-send` nội dung | `text` 1–1000 (hoặc có ảnh); ảnh đúng path và có thật | `INVALID_ARGUMENT`, `PHOTO_MISSING` | FR-006 |
| `chat-send` lặp | `messages/{messageId}` đã có, cùng `senderId` | Trả tin cũ `200` | FR-008 |
| `recall` | Người gọi là `senderId`, `status == NORMAL`, `now < createdAt + 10 phút` | `FORBIDDEN`, `RECALL_WINDOW_PASSED` | FR-010 |
| `report` | Người gọi trong cuộc, không phải người gửi, tin `NORMAL`, chưa báo cáo | `NOT_PARTICIPANT`, `OWN_MESSAGE`, `ALREADY_REPORTED` | FR-014 |

## 6. Index cần thêm vào `firestore.indexes.json`

| Collection | Trường | Dùng cho |
|---|---|---|
| `chats` | `memberIds` (array-contains), `updatedAt desc` | Danh sách "Tin nhắn" (đã có trong đặc tả CSDL mục 6) |
| `chats` | `memberIds` (array-contains), `ownerId`, `type`, `updatedAt desc` | "Tin nhắn khách hàng" (R15) |
| `messages` (collection) | `createdAt desc` | Tải 30 tin (tự có, chỉ một trường) |
| `messages` (collection group) | `senderId` | Ẩn danh khi xóa tài khoản (R16) |
| `participants` (collection group) | `userId` | Ẩn danh, kiểm tra tắt thông báo |

## 7. Mô hình phía app (Kotlin, `feature/chat/domain`)

| Lớp | Nội dung |
|---|---|
| `ChatSummary` | `id`, `type`, `title`, `avatarPath`, `venueName?`, `lastMessagePreview`, `lastMessageType`, `updatedAt`, `unreadCount`, `isReadOnly` |
| `ChatDetail` | `id`, `type`, `memberIds`, `groupId?`, `venue?`, `otherParty?` (chat riêng), `status`, `myParticipant: Participant` |
| `Participant` | `muted`, `blockedOther`, `lastReadAt` |
| `ChatMessage` | `id`, `senderId`, `senderName?`, `senderAvatarPath?`, `senderDeleted`, `text`, `imagePath?`, `createdAt?`, `status` (`NORMAL`, `RECALLED`, `HIDDEN`), `delivery` (`PENDING`, `FAILED`, `SENT`); `isMine`, `canRecall(now)` (≤ 10 phút) |
| `OutboxEntry` | `messageId`, `chatId`, `text`, `localImageUri?`, `imagePath?`, `createdAtLocal`, `attempts` |
| Enum | `ChatType`, `ChatStatus`, `MessageStatus`, `Delivery`, `ReportReason` (dùng chung danh sách với 090) |

Thời gian là `Instant`, hiển thị theo `Asia/Ho_Chi_Minh`.

---

## Danh sách thay đổi cần đưa vào `docs/design/database/README.md`

1. **Mục 4.11 `chats`:**
   - thêm `status` 🔒, `unreadCounts`, `ownerId` 🔒, `playerId` 🔒, `venueSnapshot` 🔒, `memberSnapshots` 🔒, `createdAt`; ghi rõ `lastMessage` gồm `messageId`, `senderId`, `type`, `preview`, `createdAt`;
   - chat nhóm dùng ID `group_{groupId}`;
   - thêm collection con `participants/{uid}` (`userSnapshot` 🔒, `joinedAt` 🔒, `lastReadAt`, `muted`, `blockedOther`, `viewingUntil`);
   - `messages`: ID là `requestId`; thêm `senderSnapshot` 🔒, `senderDeleted` 🔒, `recalledAt` 🔒; `status` là `NORMAL`, `RECALLED`, `HIDDEN`; mọi trường do server ghi.
2. **Mục 4.12 `reports`:** quy ước `reportId = MESSAGE_{chatId}_{messageId}_{uid}`.
3. **Mục 5:** thêm `chats` (nhóm) `group_{groupId}`, `messages` `requestId`, `participants` `uid`.
4. **Mục 6:** thêm index ở mục 6.
5. **Mục 7:** sửa dòng `chats`: người trong cuộc R; client chỉ ghi `unreadCounts.{mình} = 0` và `participants/{mình}` (`lastReadAt`, `muted`, `blockedOther`, `viewingUntil`); tin nhắn ghi qua Edge Function.
6. **Mục 8:** `chat-media` object là `{chatId}/{messageId}.{ext}`, đọc/ghi qua `chat-media-url`.
7. **Mục 8a:** thêm `chat-send`, `chat-action`, `chat-media-url` (C).
