-- Bucket ảnh nhóm/CLB (spec 090, T012; contracts/security-rules.md mục 3).
--   group-media   (private): ảnh bài viết, object {groupId}/{postId}/{n}.{ext}; chỉ truy cập qua signed URL
--                 do Edge Function `group-media-url` cấp.
--   group-avatars (public) : ảnh đại diện nhóm, object {groupId}/avatar.{jpg,png,webp}; đọc công khai qua URL public,
--                 ghi qua signed upload URL của `group-manage`.
-- CỐ Ý không tạo policy select/insert/update/delete nào cho authenticated/anon trên storage.objects của hai bucket
-- này: RLS bật sẵn nên mọi truy cập trực tiếp bị chặn, chỉ service role (Edge Function) đọc/ghi.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
  ('group-media', 'group-media', false, 2097152, array['image/jpeg', 'image/png', 'image/webp']),
  ('group-avatars', 'group-avatars', true, 2097152, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update
  set public = excluded.public,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;
