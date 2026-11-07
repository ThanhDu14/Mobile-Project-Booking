# Contract: Quyền truy cập (Firestore Rules + Supabase Storage RLS)

Phần của spec 070 trong `firestore.rules` và `supabase/migrations/`. Mỗi dòng bảng cần ít nhất một test "được phép" và một test "bị chặn" (đặc tả CSDL mục 7, constitution nguyên tắc II, IV).

## 1. Firestore Rules

```
match /reviews/{reviewId} {
  // Đọc: đánh giá đang hiển thị ai đăng nhập cũng đọc được;
  // người viết đọc được đánh giá của mình kể cả khi bị ẩn; admin đọc tất cả.
  allow get, list: if isSignedIn() && (
      resource.data.status == 'VISIBLE'
      || resource.data.userId == request.auth.uid
      || request.auth.token.appRole == 'ADMIN');
  // Mọi ghi đi qua Edge Function `review` (service account bỏ qua Rules).
  allow write: if false;
}

match /users/{uid}/favorites/{venueId} {
  allow read, delete: if isSignedIn() && request.auth.uid == uid;
  allow create, update: if isSignedIn() && request.auth.uid == uid
      && request.resource.data.keys().hasOnly(['venueId', 'venueSnapshot', 'createdAt'])
      && request.resource.data.venueId == venueId
      && request.resource.data.createdAt == request.time
      && request.resource.data.venueSnapshot.keys().hasOnly(['name', 'photoPath', 'address']);
}
// Không có rule cho collection group `favorites` → client không truy vấn được.
```

Truy vấn `list` phải có điều kiện khớp Rules: danh sách của sân dùng `where('status', '==', 'VISIBLE')`; "Đánh giá của tôi" dùng `where('userId', '==', uid)`.

Truy vấn của chủ sân ("Đánh giá của khách") chỉ đọc đánh giá `VISIBLE` nên dùng chung rule trên.

### Test Emulator (`firestore-tests/reviews.test.ts`, `favorites.test.ts`)

| # | Tình huống | Kết quả mong đợi |
|---|---|---|
| R1 | Người chơi đọc đánh giá `VISIBLE` của sân bất kỳ | Được phép |
| R2 | Người chơi đọc đánh giá `HIDDEN` của người khác | Bị chặn |
| R3 | Người viết đọc đánh giá `HIDDEN` của chính mình | Được phép |
| R4 | Admin đọc đánh giá `HIDDEN` | Được phép |
| R5 | Người chơi tạo `reviews/{id}` trực tiếp (kể cả đơn `COMPLETED`) | Bị chặn |
| R6 | Người viết sửa `ratings` hoặc xóa đánh giá của mình trực tiếp | Bị chặn |
| R7 | Chủ sân ghi `ownerReply` trực tiếp | Bị chặn |
| R8 | Người chơi ghi `venues/{id}.ratingAvg` | Bị chặn (Rules của D, test chung) |
| R9 | Chưa đăng nhập đọc `reviews` | Bị chặn |
| F1 | Chủ tài khoản tạo/xóa `users/{uid}/favorites/{venueId}` đúng schema | Được phép |
| F2 | Người khác đọc/ghi favorites của `uid` | Bị chặn |
| F3 | Tạo favorite có `venueId` khác ID document hoặc thêm trường lạ | Bị chặn |
| F4 | Truy vấn collection group `favorites` từ client | Bị chặn |

## 2. Supabase Storage — bucket `review-photos`

```sql
-- supabase/migrations/<timestamp>_review_photos.sql
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('review-photos', 'review-photos', true, 2097152,
        array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do nothing;

-- Ghi: chỉ vào thư mục của chính mình: {uid}/{reviewId}/{file}
create policy "review_photos_insert_own_folder" on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'review-photos'
    and (storage.foldername(name))[1] = auth.jwt() ->> 'sub'
    and array_length(storage.foldername(name), 1) = 2
  );

-- Đọc: bucket public, không cần policy SELECT cho URL công khai.
-- Sửa/xóa: không có policy cho authenticated → chỉ service role (Edge Function `review`).
```

### Test Supabase local (`supabase/tests/review_photos.test.sql` hoặc test Deno)

| # | Tình huống | Kết quả mong đợi |
|---|---|---|
| S1 | Người dùng `u1` tải ảnh lên `u1/b1/x.jpg` | Được phép |
| S2 | `u1` tải ảnh lên `u2/b1/x.jpg` | Bị chặn |
| S3 | `u1` tải ảnh lên `u1/x.jpg` (thiếu thư mục reviewId) | Bị chặn |
| S4 | `u1` xóa `u1/b1/x.jpg` trực tiếp | Bị chặn |
| S5 | Tải file 3 MB hoặc `image/gif` | Bị chặn (giới hạn bucket) |
| S6 | Chưa đăng nhập đọc URL công khai của ảnh | Được phép |
