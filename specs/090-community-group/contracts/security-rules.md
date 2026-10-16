# Contract: Firestore Rules và Storage cho spec 090

**Vị trí:** phần `groups` trong `firestore.rules`; test ở `firestore-tests/test/groups.test.ts` (Vitest, `createTestEnv()` từ `test/setup.ts`, project `demo-smashnow`, vai trò qua claim `appRole`). Chính sách Storage ở `supabase/migrations/<timestamp>_group_buckets.sql`, test pgTAP ở `supabase/tests/group_buckets.test.sql`.

Mọi ghi nghiệp vụ đi qua Edge Function (service account, bỏ qua Rules). Rules của spec này **chỉ mở quyền đọc**.

## 1. Bản phác Rules

```
match /groups/{groupId} {
  function isMember()  { return isSignedIn() && exists(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid)); }
  function myRole()    { return get(/databases/$(database)/documents/groups/$(groupId)/members/$(request.auth.uid)).data.role; }
  function isMod()     { return isMember() && myRole() in ['OWNER', 'ADMIN']; }

  allow get, list: if resource.data.status in ['ACTIVE', 'LOCKED'];   // trang giới thiệu, kể cả khách
  allow write: if false;

  match /members/{uid}      { allow read: if isMember() || isAdmin();                     allow write: if false; }
  match /joinRequests/{uid} { allow read: if isSelf(uid) || isMod() || isAdmin();         allow write: if false; }
  match /removals/{uid}     { allow read: if isMod() || isAdmin();                        allow write: if false; }
  match /posts/{postId} {
    allow read: if isAdmin() || isMod()
                || (isMember() && (resource.data.status == 'VISIBLE' || resource.data.authorId == request.auth.uid));
    allow write: if false;
    match /attendees/{uid}  { allow read: if isMember() || isAdmin();                     allow write: if false; }
  }
}
match /{path=**}/members/{uid} {           // "Nhóm của tôi"
  allow list: if isSignedIn() && resource.data.userId == request.auth.uid;
}
```

`list` trên `posts` của thành viên thường chỉ chạy khi query có `where status == VISIBLE` hoặc `where authorId == <uid mình>` (Rules không lọc kết quả, [research R6](../research.md#r6-phạm-vi-đọc-người-ngoài-chỉ-thấy-trang-giới-thiệu-fr-010-fr-014-fr-019)).

## 2. Bảng test Rules (mỗi dòng có cả ca được phép và ca bị chặn)

| # | Dòng Rules | `assertSucceeds` | `assertFails` |
|---|---|---|---|
| GR1 | `groups` get/list | Khách chưa đăng nhập đọc nhóm `ACTIVE` (công khai và riêng tư) | Đọc nhóm `DELETED` |
| GR2 | `groups` write | — (`write: if false`; ghi hợp lệ qua Edge Function) | Chủ nhóm tự ghi `memberCount`, `ownerId`, `name`; người chơi tạo `groups/x` |
| GR3 | `members` read | Thành viên đọc danh sách; admin (`appRole: ADMIN`) đọc | Người ngoài đọc `members` của nhóm công khai (FR-010) |
| GR4 | `members` write | — | Người chơi tự tạo `members/{mình}` (tự vào nhóm) |
| GR5 | collection group `members` list | `player1` list `where userId == player1` | `player1` list không lọc `userId` |
| GR6 | `joinRequests` read | Người xin đọc yêu cầu của mình; quản trị viên đọc danh sách | Thành viên thường đọc danh sách yêu cầu |
| GR7 | `removals` read | Chủ nhóm đọc | Thành viên thường đọc |
| GR8 | `posts` read (thành viên) | Thành viên list `where status == VISIBLE` | Thành viên list không lọc `status`; người ngoài get một bài `VISIBLE` (FR-014) |
| GR9 | `posts` read (bài ẩn) | Người đăng get bài `HIDDEN` của mình; quản trị viên get bài `HIDDEN`; admin get | Thành viên khác get bài `HIDDEN` (FR-019, SC-007) |
| GR10 | `posts` write | — | Người đăng tự sửa `content`, `status`, `attendeeCount` của bài mình |
| GR11 | `attendees` read | Thành viên đọc danh sách người tham gia | Người ngoài đọc |
| GR12 | `attendees` write | — | Thành viên tự tạo `attendees/{mình}` |

Các dòng `write: if false` chỉ có ca bị chặn: ghi hợp lệ đi qua Edge Function (service account), được kiểm tra ở test Deno.

## 3. Storage

| Bucket | Loại | Chính sách RLS | Cấu hình bucket |
|---|---|---|---|
| `group-media` | private | Không có policy `select`/`insert`/`update`/`delete` cho `authenticated`, `anon` → mọi truy cập trực tiếp bị chặn; chỉ signed URL do `group-media-url` cấp | ≤ 2 MB, `image/jpeg`, `image/png`, `image/webp` |
| `group-avatars` **[MỚI]** | public | Đọc công khai; không có policy ghi cho `authenticated` → chỉ ghi qua signed upload URL của `group-manage` | ≤ 2 MB, cùng MIME |

Test pgTAP:

| # | Ca | Kỳ vọng |
|---|---|---|
| S1 | `authenticated` (`sub = player1`) `select` object trong `group-media` | 0 dòng |
| S2 | `authenticated` `insert` object vào `group-media/{groupId}/...` | Bị chặn |
| S3 | `anon` đọc object trong `group-avatars` | Được (bucket public) |
| S4 | `authenticated` `insert` vào `group-avatars` | Bị chặn |
| S5 | Cấu hình bucket: `file_size_limit = 2097152`, `allowed_mime_types` đúng 3 loại | Đúng |
