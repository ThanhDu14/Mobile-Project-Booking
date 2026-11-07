# Contract: Edge Functions của spec 091

**Vị trí mã:** `supabase/functions/chat-send/`, `supabase/functions/chat-action/`, `supabase/functions/chat-media-url/`, code dùng chung `supabase/functions/_shared/chats.ts`, `supabase/functions/_shared/media.ts`.

**Chung** (giống contract spec 090): xác thực bằng `_shared/auth.ts` (010-T021); `accountStatus == ACTIVE`; transaction qua `_shared/firestore.ts` (B), thử lại tối đa 8 lần, hết lượt trả `503 BUSY`; body lỗi `{ "error": { "code", "message" } }`.

---

## 1. `chat-send`

### Request

```json
{ "chatId": "uidA_uidB", "messageId": "uuid", "text": "Tối thứ 7 còn sân không?", "imagePath": null }
```

### Xử lý (một transaction)

1. Đọc `chats/{chatId}`: không có hoặc `DELETED` → `404 CHAT_NOT_FOUND`; người gọi không trong `memberIds` → `403 NOT_PARTICIPANT`; `READ_ONLY` → `409 CHAT_READ_ONLY`.
2. Đọc `messages/{messageId}`: đã có và cùng `senderId` → trả tin cũ (`200`), dừng.
3. Chat nhóm: `groups/{groupId}.status != ACTIVE` → `423 GROUP_LOCKED`. Chat riêng: `participants/{người nhận}.blockedOther == true` → `403 BLOCKED`.
4. Kiểm tra nội dung ([data-model mục 3](../data-model.md#3-chatschatidmessagesmessageid)); có `imagePath` thì object phải có trong `chat-media` (`PHOTO_MISSING`).
5. Ghi `messages/{messageId}` (`senderSnapshot` từ `participants/{người gửi}.userSnapshot`, `status = NORMAL`, `createdAt` = `REQUEST_TIME`); cập nhật `chats`: `lastMessage`, `updatedAt` = `REQUEST_TIME`, `unreadCounts.{mỗi người khác} += 1`.

Sau commit: gửi `CHAT_MESSAGE` (`data = { chatId, messageId }`) cho người trong cuộc trừ người gửi, người `muted`, người có `viewingUntil > now` ([research R7](../research.md#r7-thông-báo-tin-nhắn-mới-fr-013-fr-018)). Lỗi gửi bỏ qua.

### Response

`201 { message }` (tin mới) hoặc `200 { message }` (gửi lại).

## 2. `chat-action`

| `action` | Tham số | Kết quả |
|---|---|---|
| `openDirect` | `venueId` | `200 { chatId, created }`. Tạo nếu chưa có (ID `{uidNhỏ}_{uidLớn}`, `memberIds`, `ownerId`, `playerId`, `memberSnapshots`, `participants` của hai bên, `unreadCounts` = 0); luôn cập nhật `venueId`, `venueSnapshot` |
| `recall` | `chatId`, `messageId` | `200`. `status = RECALLED`, `text = ""`, `imagePath = null`, `recalledAt`; sửa `lastMessage.preview` nếu là tin cuối; sau commit xóa ảnh |
| `report` | `chatId`, `messageId`, `reason`, `detail?` | `201 { reportId }` với `reportId = MESSAGE_{chatId}_{messageId}_{uid}` |

Điều kiện từng action: [data-model mục 5](../data-model.md#5-quy-tắc-kiểm-tra-chính).

## 3. `chat-media-url`

| `action` | Tham số | Kết quả |
|---|---|---|
| `upload` | `chatId`, `messageId`, `contentType`, `size` | `200 { path, signedUrl, token }`; path `{chatId}/{messageId}.{ext}` |
| `download` | `paths` (≤ 30) | `200 { urls, expiresAt }`; chỉ path của chat mình trong cuộc và tin `status == NORMAL`; hết hạn 3600 giây |
| `discard` | `chatId`, `messageId` | `200`; xóa ảnh khi chưa có `messages/{messageId}` |

## 4. Hàm dùng chung (`_shared/chats.ts`)

```ts
// Hook onGroupMembershipChanged của spec 090 gọi hàm này trong cùng transaction (research R5)
export async function syncGroupChat(tx: Transaction, groupId: string,
  change: { added?: string[]; removed?: string[]; created?: boolean; deleted?: boolean }): Promise<void>

// 090-T060 gọi sau khi xóa xong nhóm: xóa messages, participants, ảnh của chat theo lô
export async function purgeChat(chatId: string): Promise<void>

// delete-account (A) gọi (research R16)
export async function anonymizeCommunityChats(uid: string): Promise<void>
```

## 5. Mã lỗi

| HTTP | `error.code` | Khi nào | App hiển thị |
|---|---|---|---|
| 400 | `INVALID_ARGUMENT` | Tin rỗng, chỉ khoảng trắng, > 1000 ký tự; ảnh sai định dạng hoặc > 2 MB | Lỗi tại ô nhập; không gửi |
| 400 | `PHOTO_MISSING` | Ảnh chưa có trong Storage | Tin hiện "Gửi lỗi" |
| 403 | `NOT_PARTICIPANT` | Không trong cuộc (kể cả sau khi rời nhóm) | Đóng cuộc trò chuyện, bỏ khỏi danh sách |
| 403 | `BLOCKED` | Bị bên kia chặn | "Bạn không thể gửi tin trong cuộc trò chuyện này" |
| 403 | `FORBIDDEN` | Thu hồi tin người khác | (không có nút) |
| 403 | `OWN_VENUE` | Chủ sân nhắn cho chính mình | (không có nút) |
| 403 | `OWN_MESSAGE` | Báo cáo tin của mình | (không có nút) |
| 404 | `CHAT_NOT_FOUND`, `MESSAGE_NOT_FOUND` | Không có hoặc đã xóa | "Cuộc trò chuyện không còn" |
| 409 | `CHAT_READ_ONLY` | Bên kia đã xóa tài khoản | Ẩn ô nhập, hiện "Cuộc trò chuyện chỉ đọc" |
| 409 | `VENUE_INACTIVE`, `OWNER_NOT_APPROVED` | Không mở được chat mới | "Không thể nhắn tin cho sân này lúc này" |
| 409 | `RECALL_WINDOW_PASSED` | Quá 10 phút | "Chỉ thu hồi được trong 10 phút" |
| 409 | `ALREADY_REPORTED` | Báo cáo lặp | "Bạn đã báo cáo tin này" |
| 423 | `GROUP_LOCKED` | Nhóm bị khóa | Ẩn ô nhập, hiện "Nhóm đã bị tạm khóa" |
| 503 | `BUSY` | Hết lượt thử transaction | Outbox tự gửi lại cùng `messageId` |

## 6. Test Edge Function (`deno test`, Firebase Emulator + Supabase local)

| # | Ca | Kỳ vọng | Liên quan |
|---|---|---|---|
| C1 | `chat-send` tin chữ hợp lệ trong chat riêng | Tin `NORMAL`, `createdAt` có; `lastMessage` đúng; `unreadCounts` người nhận +1, người gửi không đổi | US1, FR-007 |
| C2 | Gửi cùng `messageId` 5 lần (cả song song) | 1 tin; `unreadCounts` +1 đúng một lần | FR-008, SC-004 |
| C3 | **20 thành viên gửi song song trong chat nhóm, lặp 10 lần** | Đủ 20 tin; `unreadCounts[u]` = số tin của người khác; `lastMessage` là tin có `createdAt` lớn nhất | AC 2.4, R13 |
| C4 | Người không trong cuộc / đã rời nhóm gửi | `NOT_PARTICIPANT` | FR-004, SC-003 |
| C5 | Bị chặn gửi; người chặn gửi; bỏ chặn rồi gửi | `BLOCKED` / thành công / thành công | FR-016, SC-003 |
| C6 | Chat nhóm của nhóm `LOCKED`; chat `READ_ONLY` | `GROUP_LOCKED`; `CHAT_READ_ONLY` | AC 2.5, Edge Cases |
| C7 | Tin rỗng, chỉ khoảng trắng, 1001 ký tự; ảnh chưa tải | `INVALID_ARGUMENT`; `PHOTO_MISSING` | FR-006 |
| C8 | Thông báo: người `muted`, người `viewingUntil > now`, người còn lại | Chỉ người còn lại nhận `CHAT_MESSAGE` (mock đếm) | FR-013, FR-018 |
| D1 | **Hai bên gọi `openDirect` cùng lúc lần đầu, lặp 20 lần** | Đúng 1 document chat mỗi lần | SC-005 |
| D2 | `openDirect` lần 2 từ cơ sở khác của cùng chủ | Cùng `chatId`, `venueId`/`venueSnapshot` đổi | AC 1.2 |
| D3 | Chủ sân mở chat với sân của mình; sân `HIDDEN`; chủ sân chưa được duyệt | `OWN_VENUE`; `VENUE_INACTIVE`; `OWNER_NOT_APPROVED` | AC 1.5, R4 |
| D4 | Chat đã có, sân sau đó bị khóa, mở lại và gửi | Thành công | Edge Cases |
| R1 | `recall` sau 5 phút / 11 phút; tin của người khác | `RECALLED`, ảnh bị xóa, `lastMessage.preview` đổi / `RECALL_WINDOW_PASSED` / `FORBIDDEN` | FR-010, SC-003 |
| R2 | `report` lần 1 / lần 2 / tin của mình / tin đã thu hồi | `201` / `ALREADY_REPORTED` / `OWN_MESSAGE` / `INVALID_ARGUMENT` | FR-014 |
| H1 | `syncGroupChat` `created`, `added`, `removed`, `deleted` | `memberIds`, `participants`, `unreadCounts` đúng; người mới có `lastReadAt = joinedAt`; `deleted` → `memberIds = []`, `DELETED` | FR-002, R5 |
| H2 | `purgeChat` | Hết `messages`, `participants`, ảnh của chat | AC 2.5 |
| M1 | `upload` khi không trong cuộc / `image/gif` / 3 MB | `NOT_PARTICIPANT` / `INVALID_ARGUMENT` / `INVALID_ARGUMENT` | US4 |
| M2 | `download` path của tin `RECALLED`, `HIDDEN`, chat khác | Không trả URL | AC 4.4, FR-015 |
| A1 | `anonymizeCommunityChats` | Chat riêng `READ_ONLY`, `memberSnapshots.{uid} = null`; tin của uid `senderDeleted = true` | Edge Cases (R16) |
