-- Bucket ảnh đại diện (spec 011, contracts/storage-avatars.md §1–§2).
-- Firebase ID token qua Third-party Auth: auth.jwt() ->> 'sub' là uid Firebase (không phải UUID nên không dùng auth.uid()).

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', true, 2097152, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update
  set public = excluded.public,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;

-- Chỉ ghi/xóa trong thư mục {uid}/ của chính mình, và phải qua on-signup (có claim appRole).
-- Không có policy UPDATE: mọi update/upsert bị chặn, app luôn tải object tên mới (research R3).
create policy avatars_insert_own on storage.objects
  for insert to authenticated
  with check (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = auth.jwt() ->> 'sub'
    and auth.jwt() ->> 'appRole' is not null
  );

create policy avatars_delete_own on storage.objects
  for delete to authenticated
  using (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = auth.jwt() ->> 'sub'
    and auth.jwt() ->> 'appRole' is not null
  );

-- Cần cho list/remove object của mình; đọc ảnh đi qua URL public, không cần policy.
create policy avatars_select_own on storage.objects
  for select to authenticated
  using (
    bucket_id = 'avatars'
    and (storage.foldername(name))[1] = auth.jwt() ->> 'sub'
    and auth.jwt() ->> 'appRole' is not null
  );
