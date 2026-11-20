# Nhóm 09 (spec 090 nhóm/CLB + 091 chat) – việc cần thành viên khác làm

Server của C đã có: `group-manage`, `group-post`, `group-media-url` (090) và `chat-send`, `chat-action`, `chat-media-url` (091). Các việc dưới đây nằm trong file của người khác nên C không tự sửa. Mỗi mục có prompt dán thẳng cho AI của người nhận.

| # | Người nhận | Việc | Ảnh hưởng nếu chưa làm |
|---|---|---|---|
| 1 | A (011) | Cắm `anonymizeCommunity` vào `delete-account` | Xóa tài khoản không ẩn danh bài/chat, không chuyển quyền chủ nhóm (bước ghi `SKIPPED`) |
| 2 | D (100) | `send-notification` nhận `GROUP_ACTIVITY` và `CHAT_MESSAGE` | Không có thông báo nhóm/chat (server C bỏ qua lỗi, không chặn thao tác) |
| 3 | D (120) | Ẩn bài, ẩn tin nhắn, khóa nhóm theo đúng trường của 090/091 | App hiển thị sai trạng thái bị ẩn/bị khóa |

## 1. A – `delete-account` ẩn danh dữ liệu cộng đồng

`supabase/functions/_shared/community.ts` có `anonymizeCommunity(uid)`: gọi `anonymizeCommunityGroups` (090: ẩn danh bài với `authorSnapshot = null`, `authorDeleted = true`; xóa yêu cầu tham gia đang chờ; rời mọi nhóm, bỏ khỏi sự kiện chưa diễn ra; nhóm mình làm chủ thì chuyển quyền cho ADMIN rồi MEMBER vào sớm nhất, không còn ai thì xóa nhóm) rồi `anonymizeCommunityChats` (091: chat riêng thành `READ_ONLY`, tin của người đó `senderDeleted = true`). Idempotent. Chữ ký khớp `DeletionHelpers.anonymizeCommunity` (đã `deno check`):

```ts
import { anonymizeCommunity } from "./community.ts";
export const defaultHelpers: DeletionHelpers = { anonymizeCommunity /*, ...helper khác */ };
```

> **Prompt cho AI của A:** Trong `supabase/functions/_shared/account-deletion-helpers.ts`, import `anonymizeCommunity` từ `./community.ts` (nhóm 09 của C) và thêm vào `defaultHelpers`. Chữ ký đã khớp `DeletionHelpers.anonymizeCommunity`. Chạy lại test `supabase/functions/delete-account` trên Firebase Emulator (seed `scripts/seed/090-community.ts`, `scripts/seed/091-chat.ts`); kiểm bước `anonymizeCommunity` không còn `SKIPPED`, chạy lại lần hai không lỗi.

## 2. D – `send-notification` cho nhóm và chat

Server C gọi `send-notification` bằng service role với cùng envelope `{ type, recipientUid, data }` như `review` (070):

| type | data | Phát sinh |
|---|---|---|
| `GROUP_ACTIVITY` | `{ event, groupId, postId? }`; `event` ∈ yêu cầu tham gia, được duyệt/từ chối, bị mời ra, `EVENT_CREATED`, `EVENT_UPDATED`, `EVENT_DELETED` (xem `GroupEvent` trong `_shared/groups.ts`) | `group-manage`, `group-post` |
| `CHAT_MESSAGE` | `{ chatId, messageId }` | `chat-send`, chỉ gửi người không tắt thông báo và không đang mở cuộc trò chuyện |

Deep link đã chốt trong plan: `smashnow://community/group/{groupId}` (090) và `smashnow://chat/{chatId}` (091). Thông báo vãng lai (080/081) đang dùng `userId` thay cho `recipientUid` (`_shared/dropin-notify.ts`); khi chốt envelope chọn một tên và báo C sửa phía còn lại.

> **Prompt cho AI của D:** Khi cài `send-notification` (spec 100), nhận envelope `{ type, recipientUid, data }` với `type = GROUP_ACTIVITY` (data `{ event, groupId, postId? }`, danh sách `event` ở `GroupEvent` trong `supabase/functions/_shared/groups.ts`) và `type = CHAT_MESSAGE` (data `{ chatId, messageId }`). Ghi `users/{uid}/notifications` và gửi FCM; bấm thông báo mở `smashnow://community/group/{groupId}` hoặc `smashnow://chat/{chatId}`. Nếu đổi envelope, báo C để sửa `sendGroupNotification` (`_shared/groups.ts`) và `sendChatNotification` (`_shared/chats.ts`).

## 3. D – kiểm duyệt (spec 120)

| Hành động | Ghi gì | Nguồn |
|---|---|---|
| Ẩn bài nhóm theo báo cáo | `groups/{gid}/posts/{pid}`: `status = HIDDEN`, `hiddenBy = SYSTEM`, `hiddenByUid = null` (nhóm không bỏ ẩn được) | 090 plan, R7 |
| Khóa nhóm (action mới, hiện chưa có) | `groups/{gid}.status = LOCKED` (mọi action ghi của `group-manage`, `group-post` trừ rời nhóm trả `423`; chat nhóm không gửi được, `423 GROUP_LOCKED`) | 090 plan, R17 |
| Ẩn tin nhắn theo báo cáo | `chats/{cid}/messages/{mid}`: `status = HIDDEN`, nên ghi thêm `text = ""` | 091 plan, R10 |
| `reportId` | Bài: `POST_{groupId}_{postId}_{uid}`; tin: `MESSAGE_{chatId}_{messageId}_{uid}`; `reason` chung `SPAM`, `OFFENSIVE`, `SCAM`, `INAPPROPRIATE`, `OTHER` | 090 R14 |

Hiện `admin-moderate-report/handler.ts` nhánh `HIDE_CONTENT` chỉ ghi `status = HIDDEN` lên `targetPath`, nên bài bị admin ẩn thiếu `hiddenBy = SYSTEM` (nhóm vẫn bỏ ẩn được, trái R7) và tin nhắn bị ẩn vẫn còn `text`.

> **Prompt cho AI của D:** Trong `supabase/functions/admin-moderate-report/handler.ts`, nhánh `HIDE_CONTENT`: nếu `targetPath` dạng `groups/{g}/posts/{p}` thì ghi thêm `hiddenBy: "SYSTEM"`, `hiddenByUid: null`; nếu dạng `chats/{c}/messages/{m}` thì ghi thêm `text: ""`. Thêm action mới khóa nhóm (ví dụ `LOCK_GROUP`): cập nhật kiểu `ReportActionType`, mảng `validActions` và `validateActionTarget` (hiện `POST`/`MESSAGE` chỉ cho `HIDE_CONTENT`, chưa có `targetType` cho nhóm — chọn cho phép `LOCK_GROUP` trên báo cáo `POST` hoặc thêm `targetType = GROUP`). Đường ghi khóa nhóm KHÔNG phải `targetPath` (đó là đường bài) mà là `groups/{gid}` với `gid = targetPath.split("/")[1]`, ghi `status = LOCKED`. `reportId` và danh sách `reason` theo bảng trong `specs/091-chat/handoffs/README.md`. Mọi ghi qua Edge Function bằng service role (Rules chặn client).

## 4. C – tạo bù chat nhóm cho nhóm cũ sau khi deploy 091

Nhóm tạo trước khi deploy 091 chưa có `chats/group_{groupId}` (chat nhóm chỉ tạo trong transaction `create` của `group-manage`), nên nút "Chat nhóm" báo "Bạn không còn trong cuộc trò chuyện". C chạy một lần ngay sau khi deploy function + Rules 091:

```bash
deno run -A --config supabase/functions/deno.json scripts/admin/backfill-group-chats.ts --dry-run --service-account <file.json>
deno run -A --config supabase/functions/deno.json scripts/admin/backfill-group-chats.ts --service-account <file.json>
```

Chạy lại an toàn (nhóm đã có chat bị bỏ qua); bỏ qua nhóm `DELETED`; người đang trong nhóm có `lastReadAt` = lúc tạo nên không bị tính chưa đọc. Test: `scripts/admin/backfill-group-chats.test.ts` (Emulator).
