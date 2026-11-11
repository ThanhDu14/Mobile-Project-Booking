-- Bucket ảnh đánh giá (spec 070, C; contracts/security-rules.md §2, research R4).
-- Object: {uid}/{reviewId}/{uuid}.jpg (reviewId = bookingId). auth.jwt() ->> 'sub' là uid Firebase.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('review-photos', 'review-photos', true, 2097152, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update
  set public = excluded.public,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

-- Ghi: chỉ vào thư mục {uid}/{reviewId}/ của chính mình (đúng 2 cấp thư mục).
create policy review_photos_insert_own_folder on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'review-photos'
    and (storage.foldername(name))[1] = auth.jwt() ->> 'sub'
    and array_length(storage.foldername(name), 1) = 2
  );

-- Upload của Storage API dùng INSERT ... RETURNING nên cần quyền SELECT trên dòng vừa ghi.
-- Đọc ảnh đi qua URL public, policy này chỉ cho thấy object của chính mình.
create policy review_photos_select_own on storage.objects
  for select to authenticated
  using (
    bucket_id = 'review-photos'
    and (storage.foldername(name))[1] = auth.jwt() ->> 'sub'
  );

-- Sửa/xóa: không có policy cho authenticated → chỉ service role (Edge Function `review`).
