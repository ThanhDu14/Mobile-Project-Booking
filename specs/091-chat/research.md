# Research: Chat nhóm và chat với chủ sân (spec 091)

**Ngày**: 2026-10-16 · **Spec**: [spec.md](spec.md) · **Plan**: [plan.md](plan.md) · **Liên quan**: [research 090](../090-community-group/research.md)

Mỗi mục ghi: quyết định, lý do, phương án đã cân nhắc. Không còn mục `NEEDS CLARIFICATION`.

---

## R1. Gửi tin qua Edge Function `chat-send`, app giữ hàng chờ gửi (outbox)

**Quyết định:**
- App không ghi `messages` trực tiếp. Mỗi tin có `messageId` = UUID sinh khi bấm "Gửi" (cũng là `requestId`).
- App đưa tin vào `ChatOutbox` (hiện ngay "Đang gửi"), rồi gọi `chat-send`. Mất mạng thì chờ có mạng tự gửi lại cùng `messageId`. Quá 60 giây chưa gửi được thì hiện "Gửi lỗi" kèm "Gửi lại"/"Xóa".
- `chat-send` chạy transaction: kiểm tra quyền, tạo `messages/{messageId}` nếu chưa có (đã có thì trả tin cũ), cập nhật `lastMessage`, `updatedAt`, `unreadCounts`; sau commit gửi thông báo (R7).
- `ChatOutbox` lưu bằng DataStore (theo `chatId`) để tin chưa gửi còn sau khi app bị tắt.

**Lý do:**
- FR-008 cần "Gửi lại" và "Xóa" cho tin lỗi. Hàng đợi offline của Firestore SDK không hủy được một lần ghi đã vào hàng đợi, nên "Xóa" không làm được nếu app ghi trực tiếp.
- Supabase không có trigger Firestore. Số chưa đọc của từng người và thông báo `CHAT_MESSAGE` phải tính ở server ngay lúc gửi; gom vào một transaction là đơn giản nhất.
- Kiểm tra chặn (FR-016), nhóm bị khóa, chat chỉ đọc, ảnh đã tải lên đều làm ở server (constitution II); Rules chỉ cần `write: if false` cho `messages`.
- `messageId` tất định nên gửi lại bao nhiêu lần cũng chỉ một tin (SC-004).

**Đã cân nhắc:**
- *App ghi `messages` trực tiếp, Rules kiểm tra*: có hàng đợi offline sẵn, nhưng không "Xóa" được tin đã vào hàng đợi; không tăng `unreadCounts` cho người khác một cách tin cậy; không gửi được thông báo. Loại.
- *WorkManager để gửi nền*: thêm thư viện cho một nhu cầu nhỏ (constitution VII). Outbox gửi lại khi app mở và khi có mạng là đủ.

**Độ trễ:** gọi Edge Function cộng thêm khoảng 0,3–0,8 giây so với ghi thẳng; listener realtime phía người nhận vẫn đạt mục tiêu 3 giây (SC-001).

**Xóa tin lỗi:** trước khi bỏ khỏi outbox, app đọc `messages/{messageId}` từ server. Nếu tin đã có (lần gửi trước đã commit nhưng mất phản hồi) thì đánh dấu đã gửi thay vì xóa.

## R2. Dữ liệu: `chats`, `participants`, `messages`

**Quyết định:**
- `chats/{chatId}` (đặc tả CSDL mục 4.11) thêm `status` 🔒 (`ACTIVE`, `READ_ONLY`, `DELETED`), `unreadCounts` (Map uid → Int), `ownerId`, `playerId`, `memberSnapshots` 🔒 (chat riêng), `venueSnapshot` 🔒, `createdAt` **[MỚI]**.
- `chats/{chatId}/participants/{uid}` **[MỚI]**: `userSnapshot` 🔒, `lastReadAt`, `muted`, `blockedOther`, `viewingUntil`, `joinedAt` 🔒.
- `chats/{chatId}/messages/{messageId}`: thêm `senderSnapshot` 🔒, `senderDeleted` 🔒, `recalledAt` 🔒 **[MỚI]**; `status` (`NORMAL`, `RECALLED`, `HIDDEN`).

**Đặt tên `participants`, không dùng `members`:** spec 090 đã có collection group `members` (truy vấn "Nhóm của tôi" `where userId == uid`). Nếu chat cũng dùng `members` thì collection group sẽ lẫn cả document của chat, và Rules collection group phải xử lý hai loại.

## R3. ID cuộc trò chuyện tất định (FR-003, SC-005)

**Quyết định:**
- Chat riêng: `chatId = {uidNhỏ}_{uidLớn}` (đặc tả CSDL mục 5). Mở chat qua `chat-action` `openDirect`: transaction tạo nếu chưa có, luôn cập nhật `venueId`, `venueSnapshot` theo sân vừa xem.
- Chat nhóm: `chatId = group_{groupId}`, tạo trong cùng transaction tạo nhóm (R5).

**Lý do:** hai người mở chat lần đầu cùng lúc tranh chấp đúng một document, transaction chỉ tạo một lần (SC-005).

**Vai trò cố định lúc tạo:** `ownerId` (chủ của sân được hỏi lần đầu) và `playerId` ghi lúc tạo, không đổi khi hỏi sân khác. Trường hợp hiếm hai chủ sân hỏi sân của nhau vẫn dùng chung một cuộc, nằm trong "Tin nhắn khách hàng" của người có `ownerId`.

## R4. Ai được mở chat riêng (FR-001, FR-003)

**Quyết định:** `openDirect` nhận `venueId`, kiểm tra:
- sân `status == ACTIVE` (`VENUE_INACTIVE`);
- người gọi không phải chủ sân (`OWN_VENUE`);
- chủ sân có `users.ownerStatus == APPROVED` (`OWNER_NOT_APPROVED`);
- người gọi `accountStatus == ACTIVE`.

Chat đã có thì mở lại được dù sân sau này bị ẩn/khóa hay chủ sân bị thu hồi quyền (Edge Cases); chỉ lần tạo mới mới cần các điều kiện trên.

## R5. Đồng bộ chat nhóm với danh sách thành viên (FR-002)

**Quyết định:** cài `syncGroupChat(tx, groupId, change)` trong `_shared/chats.ts`; hook `onGroupMembershipChanged` của spec 090 (`_shared/groups.ts`) gọi hàm này. Trong cùng transaction của 090:

| `change` | Việc |
|---|---|
| `created` | Tạo `chats/group_{groupId}` (`type = GROUP`, `groupId`, `memberIds = [owner]`, `status = ACTIVE`, `unreadCounts = {owner: 0}`) và `participants/{owner}` |
| `added` | `memberIds` thêm uid; tạo `participants/{uid}` với `lastReadAt = joinedAt = now` (tin cũ không tính là chưa đọc nhưng vẫn đọc được, đúng Clarifications); `unreadCounts[uid] = 0` |
| `removed` | `memberIds` bỏ uid; xóa `participants/{uid}` và `unreadCounts[uid]`. Rules dựa trên `memberIds` nên mất quyền đọc ngay |
| `deleted` | `memberIds = []`, `status = DELETED` |

Sau khi spec 090 xóa xong nhóm (090-T060), gọi `purgeChat(chatId)` để xóa `messages`, `participants` và thư mục ảnh của chat theo lô.

**Nhóm bị khóa** (`groups.status == LOCKED`): `chat-send` từ chối với `423 GROUP_LOCKED`, chat vẫn đọc được (AC 2.5). Không cần đổi gì trên chat.

## R6. Số chưa đọc và huy hiệu (FR-011, FR-012)

**Quyết định:**
- `chats.unreadCounts` (Map uid → Int): `chat-send` tăng 1 cho mọi người trong cuộc trừ người gửi, trong cùng transaction.
- Đọc tới tin mới nhất: app ghi một lần (batch) `participants/{mình}.lastReadAt = request.time` và `chats.unreadCounts.{mình} = 0`. Rules chỉ cho đổi đúng khóa của mình về 0.
- Danh sách "Tin nhắn" nghe realtime `chats where memberIds array-contains uid orderBy updatedAt desc`; số chưa đọc đọc thẳng từ `unreadCounts[uid]`, không cần truy vấn đếm.
- Huy hiệu = số cuộc có `unreadCounts[uid] > 0`. `MainActivity` (A) đọc qua interface `core/chat/UnreadChatsReader` do `feature/chat` cài, không import `feature/chat` (constitution I).

**Lý do:** realtime, không tốn thêm lượt đọc như `count()` cho từng cuộc.

**Đã cân nhắc:** *Chỉ lưu `lastReadAt`, đếm bằng `count()`*: `count()` không realtime, mỗi lần mở danh sách tốn một truy vấn cho mỗi cuộc. Loại.

## R7. Thông báo tin nhắn mới (FR-013, FR-018)

**Quyết định:** sau commit, `chat-send` gọi `send-notification` (D) với `type = CHAT_MESSAGE`, `data = { chatId, messageId }` cho người trong cuộc trừ:
- người gửi;
- người có `participants.muted == true`;
- người đang mở cuộc đó: `participants.viewingUntil > now`. App ghi `viewingUntil = now + 60 giây` khi mở cuộc trò chuyện, gia hạn mỗi 45 giây khi đang mở, xóa khi rời màn hình.

Lỗi gửi thông báo bị bỏ qua. Tắt thông báo không ảnh hưởng `unreadCounts`.

## R8. Ảnh trong chat (FR-006, US4)

**Quyết định:** bucket private `chat-media` (đặc tả CSDL mục 8), object `{chatId}/{messageId}.{ext}`. Edge Function `chat-media-url`:
- `upload`: người gọi trong `memberIds`, chat không `DELETED`; `contentType` JPEG/PNG/WebP, `size ≤ 2 MB`; trả signed upload URL.
- `download`: tối đa 30 path (một trang tin); với mỗi path kiểm tra người gọi trong cuộc và tin có `status == NORMAL` (tin thu hồi hoặc bị ẩn không cấp URL, FR-015); signed URL hết hạn 1 giờ.
- `discard`: xóa ảnh của tin chưa gửi thành công (chưa có `messages/{messageId}`).

Luồng gửi ảnh: nén (`core/image/ImageCompressor` của 090-T020) → xin URL → tải lên → `chat-send` với `imagePath`. Server kiểm tra object có thật trước khi ghi tin, nên không ai thấy ảnh hỏng (AC 4.2).

Phần kiểm tra MIME/kích thước và tạo signed URL dùng chung với `group-media-url` qua `_shared/media.ts`.

## R9. Thu hồi tin (FR-010)

**Quyết định:** `chat-action` `recall` trong transaction: người gọi là `senderId`; `now < createdAt + 10 phút` theo giờ server (`RECALL_WINDOW_PASSED`); đặt `status = RECALLED`, `text = ""`, `imagePath = null`, `recalledAt`; nếu là `lastMessage` thì sửa bản xem trước thành "Tin nhắn đã được thu hồi". Sau commit xóa ảnh trong `chat-media`.

## R10. Tin bị admin ẩn (FR-015)

**Quyết định:** spec 120 (`HIDE_CONTENT` theo `targetPath`) ghi `messages.status = HIDDEN`. App hiện "Tin nhắn đã bị ẩn"; `chat-media-url download` không cấp URL cho tin `HIDDEN`.

**Giới hạn đã biết:** nội dung chữ vẫn nằm trong document và người trong cuộc đã nhận được trước đó; ẩn chỉ áp dụng ở giao diện và ảnh. Muốn xóa hẳn chữ thì spec 120 phải ghi `text = ""` khi ẩn; ghi vào bảng phối hợp với D.

## R11. Chặn trong chat riêng (FR-016)

**Quyết định:** `participants/{uid}.blockedOther` (Boolean), chỉ chính người đó ghi (Rules). `chat-send` trong chat `DIRECT` đọc `participants/{người nhận}.blockedOther`: `true` thì trả `403 BLOCKED`, app hiện "Bạn không thể gửi tin trong cuộc trò chuyện này". Người chặn vẫn gửi được (spec không cấm). Chat nhóm không có chặn (AC 5.5).

## R12. Báo cáo tin nhắn (FR-014)

**Quyết định:** giống spec 090 R14: `chat-action` `report` ghi `reports/MESSAGE_{chatId}_{messageId}_{uid}` (`targetType = MESSAGE`, `targetPath = chats/{chatId}/messages/{messageId}`), cùng danh sách `reason` (`SPAM`, `OFFENSIVE`, `SCAM`, `INAPPROPRIATE`, `OTHER`), `detail` ≤ 500 ký tự. Không báo cáo tin của mình hoặc tin đã thu hồi.

## R13. Thứ tự tin theo giờ server (FR-007, Edge Cases)

**Quyết định:** `createdAt` ghi bằng transform `REQUEST_TIME` trong lần commit của `chat-send`, nên thứ tự là thứ tự commit trên server, không phụ thuộc giờ điện thoại. `lastMessage.createdAt` cùng giá trị nên tin cuối luôn là tin commit sau cùng. App sắp `orderBy createdAt`; tin trong outbox hiện cuối danh sách cho tới khi có `createdAt`.

## R14. Phân trang (FR-009)

**Quyết định:** mở cuộc trò chuyện: listener `orderBy createdAt desc limit 30` (tin mới tới realtime); cuộn lên tải thêm 30 bằng `startAfter` (một lần đọc, không listener).

## R15. Chủ sân: "Tin nhắn khách hàng" (FR-005)

**Quyết định:**
- Chế độ quản lý sân: `chats where memberIds array-contains uid, ownerId == uid, type == DIRECT orderBy updatedAt desc`. Có `memberIds array-contains` để Rules chứng minh được người đọc nằm trong cuộc.
- Chế độ người chơi: `chats where memberIds array-contains uid orderBy updatedAt desc`, app bỏ các cuộc có `ownerId == uid`.

Mục "Tin nhắn khách hàng" nằm trong menu chế độ chủ sân (spec 011, 111/112); spec này cung cấp màn hình và deep link `smashnow://chat/owner`.

## R16. Người dùng xóa tài khoản (Edge Cases, spec 011)

**Quyết định:** `_shared/chats.ts` có `anonymizeCommunityChats(uid)` cho `delete-account` (A) gọi:
- chat riêng có uid: `status = READ_ONLY`, `memberSnapshots.{uid} = null`;
- tin của uid (collection group `messages where senderId == uid`): `senderSnapshot = null`, `senderDeleted = true`;
- chat nhóm: do spec 090 cho rời nhóm (`anonymizeCommunityGroups` → hook `removed`).

`chat-send` vào chat `READ_ONLY` trả `409 CHAT_READ_ONLY`.
