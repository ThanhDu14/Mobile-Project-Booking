# Contract: màn hình spec 091

Package `com.example.booking_app.feature.chat` (`ui/`, `data/`, `domain/`). Nav graph lồng `nav_chat.xml`, được `include` vào graph chính. Wireframe: [ui/README.md](../ui/README.md).

## 1. Màn hình

| Màn hình | Layout | Story | Safe Args | Ghi chú |
|---|---|---|---|---|
| `ChatListFragment` | `fragment_chat_list.xml`, `item_chat.xml` | US3 | `mode: PLAYER / OWNER` | `PLAYER`: mục "Tin nhắn" (chat nhóm và chat với chủ sân), bỏ các cuộc có `ownerId == mình`. `OWNER`: "Tin nhắn khách hàng". Mỗi dòng: ảnh, tên, cơ sở được hỏi (chat riêng), tin cuối ("[Hình ảnh]" với tin ảnh), thời gian, số chưa đọc (99+). Wireframe `09_community-chat_buoc1_tin-nhan.png`, `buoc4_chu-san-tin-nhan-khach.png` |
| `ChatFragment` | `fragment_chat.xml`, `item_message_mine.xml`, `item_message_other.xml` | US1, US2, US4, US5 | `chatId` | Đầu màn hình: tên nhóm, hoặc tên và ảnh cơ sở được hỏi (chat riêng). Danh sách tin (30 mới nhất, cuộn lên tải thêm), tin của mình bên phải; trạng thái "Đang gửi"/"Gửi lỗi" + "Gửi lại"/"Xóa"; ô nhập đếm 1000 ký tự; nút ảnh (camera hoặc Photo Picker). Menu: "Tắt/Bật thông báo", "Chặn/Bỏ chặn" (chat riêng). Giữ vào tin: "Thu hồi" (tin của mình ≤ 10 phút), "Báo cáo" (tin người khác). Wireframe `buoc2_chat-nhom.png`, `buoc3_chat-voi-chu-san.png`, `buoc5_chu-san-tro-chuyen-voi-khach.png` |
| `ReportMessageBottomSheet` | `bottom_sheet_report_message.xml` | US5 | `chatId`, `messageId` | 5 lý do, mô tả khi "Khác" |
| `PhotoViewerFragment` | — | US4 | `urls`, `startIndex` | Dùng lại bản ở `core/ui` (070/090) |

Deep link:
- `smashnow://chat/{chatId}` (thông báo `CHAT_MESSAGE`);
- `smashnow://chat/group/{groupId}` (nút "Chat nhóm" của spec 090, mở `group_{groupId}`);
- `smashnow://chat/direct?venueId={venueId}` (nút "Nhắn tin cho chủ sân" của spec 030: gọi `openDirect` rồi mở `ChatFragment`);
- `smashnow://chat/owner` (mục "Tin nhắn khách hàng" trong chế độ chủ sân).

Mọi màn hình có đủ 4 trạng thái; mất mạng giữ nội dung đang soạn (FR-017). Chuỗi trong `strings_chat.xml`, tiền tố `chat_`. Rỗng: "Tham gia một nhóm" hoặc "Tìm sân để hỏi chủ sân" (AC 3.4).

## 2. ViewModel

| ViewModel | Trạng thái và sự kiện |
|---|---|
| `ChatListViewModel` | `UiState<List<ChatSummary>>` realtime; với chat nhóm đọc tên/ảnh nhóm theo lô ≤ 10 (`whereIn(documentId)` **kèm** `status in [ACTIVE, LOCKED]`, theo bài học F1 của spec 090) |
| `ChatViewModel` | `header`, `messages: UiState<List<ChatMessage>>` (gộp tin server và outbox), `composer` (text, ảnh đang chọn), `canSend` (không `READ_ONLY`, không `GROUP_LOCKED`, không `BLOCKED`); khi tin mới nhất hiển thị thì đánh dấu đã đọc (batch `lastReadAt` + `unreadCounts.{mình} = 0`); gia hạn `viewingUntil` mỗi 45 giây khi đang mở; sự kiện `OpenPhoto`, `ShowError` |
| `ReportMessageViewModel` | Giống `ReportPostViewModel` của 090 |

## 3. Thành phần dùng chung

| Thành phần | Vị trí | Dùng bởi |
|---|---|---|
| `UnreadChatsReader` (interface: `observeUnreadChatCount(): Flow<Int>`) | `APP/core/chat/` | `MainActivity` (A) gắn huy hiệu cho mục "Tin nhắn"; `feature/chat` cài và bind bằng Hilt |
| `ChatOutbox` | `feature/chat/data/` | `ChatRepository`; lưu DataStore `chat_outbox` |
| `ImageCompressor` | `APP/core/image/` (090-T020) | Gửi ảnh |

## 4. Điểm nối cho nhóm khác

| Nhóm | Việc của họ | Spec 091 cung cấp |
|---|---|---|
| A, spec 030 | Nút "Nhắn tin cho chủ sân" trên chi tiết sân đang hoạt động (ẩn với chủ sân xem sân của mình) | Deep link `smashnow://chat/direct?venueId=` |
| A, `MainActivity` | Huy hiệu mục "Tin nhắn" | `core/chat/UnreadChatsReader` |
| A, `delete-account` | Xóa tài khoản | `anonymizeCommunityChats(uid)` |
| D, spec 011/111/112 | Mục "Tin nhắn khách hàng" trong menu chế độ chủ sân | Deep link `smashnow://chat/owner` |
| D, spec 100 | Gửi `CHAT_MESSAGE` | `data = { chatId, messageId }`, deep link `smashnow://chat/{chatId}` |
| D, spec 120 | Ẩn tin theo báo cáo | `targetPath`; ghi `status = HIDDEN` (và nên ghi `text = ""`, research R10) |
| C, spec 090 | Đồng bộ thành viên chat nhóm, xóa chat khi xóa nhóm | `syncGroupChat`, `purgeChat` trong `_shared/chats.ts` |
