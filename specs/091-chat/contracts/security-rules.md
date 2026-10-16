# Contract: Firestore Rules và Storage cho spec 091

**Vị trí:** phần `chats` trong `firestore.rules`; test ở `firestore-tests/test/chats.test.ts` (Vitest, `createTestEnv()`, project `demo-smashnow`, vai trò qua claim `appRole`). Chính sách bucket `chat-media` ở `supabase/migrations/<timestamp>_chat_media_bucket.sql`, test pgTAP ở `supabase/tests/chat_media.test.sql`.

Tin nhắn ghi qua Edge Function. Client chỉ được ghi đúng hai chỗ: `chats.unreadCounts.{mình} = 0` và `participants/{mình}` (`lastReadAt`, `muted`, `blockedOther`, `viewingUntil`).

## 1. Bản phác Rules

```
match /chats/{chatId} {
  function inChat() { return isSignedIn() && request.auth.uid in resource.data.memberIds; }
  function inChatById() {
    return isSignedIn() && request.auth.uid in get(/databases/$(database)/documents/chats/$(chatId)).data.memberIds;
  }

  allow get, list: if inChat() || isAdmin();
  // Chỉ đặt số chưa đọc của chính mình về 0
  allow update: if inChat()
    && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['unreadCounts'])
    && request.resource.data.unreadCounts.diff(resource.data.unreadCounts).affectedKeys().hasOnly([request.auth.uid])
    && request.resource.data.unreadCounts[request.auth.uid] == 0;
  allow create, delete: if false;

  match /participants/{uid} {
    allow get: if isSelf(uid) || isAdmin();
    allow update: if isSelf(uid) && inChatById()
      && request.resource.data.diff(resource.data).affectedKeys().hasOnly(['lastReadAt', 'muted', 'blockedOther', 'viewingUntil'])
      && (!('lastReadAt' in request.resource.data.diff(resource.data).affectedKeys()) || request.resource.data.lastReadAt == request.time)
      && (!('viewingUntil' in request.resource.data.diff(resource.data).affectedKeys())
          || request.resource.data.viewingUntil == null
          || request.resource.data.viewingUntil <= request.time + duration.value(2, 'm'));
    allow create, delete: if false;
  }
  match /messages/{messageId} {
    allow read: if inChatById() || isAdmin();
    allow write: if false;
  }
}
match /{path=**}/participants/{uid} {
  allow list: if isSignedIn() && resource.data.userId == request.auth.uid;
}
```

## 2. Bảng test Rules (mỗi dòng có cả ca được phép và ca bị chặn)

| # | Dòng Rules | `assertSucceeds` | `assertFails` |
|---|---|---|---|
| CR1 | `chats` get/list | Người trong cuộc get; list `where memberIds array-contains <mình>`; admin get | Người ngoài get; list không có `array-contains <mình>` |
| CR2 | `chats` update `unreadCounts` | Người trong cuộc đặt `unreadCounts.{mình} = 0` | Đặt `unreadCounts.{người khác} = 0`; đặt của mình = 5; sửa `lastMessage`, `memberIds` |
| CR3 | `chats` create/delete | — (`if false`; tạo qua Edge Function) | Người chơi tự tạo `chats/{a}_{b}`; xóa chat |
| CR4 | `participants` get | Đọc document của mình | Đọc document của người kia (xem họ có chặn/tắt thông báo không) |
| CR5 | `participants` update | Ghi `lastReadAt = request.time`, `muted`, `blockedOther`, `viewingUntil = now + 60s` của mình | Ghi của người khác; ghi `userSnapshot`; `lastReadAt` là giờ client; `viewingUntil = now + 1 giờ`; ghi sau khi đã rời nhóm |
| CR6 | `messages` read | Người trong cuộc list 30 tin; admin get | Người ngoài list; người vừa bị mời ra khỏi nhóm list (SC-003) |
| CR7 | `messages` write | — (`if false`) | Người trong cuộc tự tạo tin; tự sửa `status` về `NORMAL`; xóa tin |
| CR8 | collection group `participants` list | `where userId == <mình>` | Không lọc `userId` |

## 3. Storage `chat-media`

| Bucket | Loại | Chính sách RLS | Cấu hình |
|---|---|---|---|
| `chat-media` | private | Không có policy cho `authenticated`, `anon` trên `storage.objects` của bucket → chỉ signed URL do `chat-media-url` cấp | ≤ 2 MB, `image/jpeg`, `image/png`, `image/webp` |

| # | Ca pgTAP | Kỳ vọng |
|---|---|---|
| S1 | `authenticated` `select` object trong `chat-media` | 0 dòng |
| S2 | `authenticated` `insert` vào `chat-media/{chatId}/...` | Bị chặn |
| S3 | `anon` `select` | 0 dòng |
| S4 | Cấu hình bucket `file_size_limit = 2097152`, đúng 3 MIME | Đúng |
