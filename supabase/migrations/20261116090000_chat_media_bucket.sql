-- Bucket ảnh chat (spec 091, T013; contracts/security-rules.md mục 3).
--   chat-media (private): object {chatId}/{messageId}.{jpg,png,webp}; chỉ truy cập qua signed URL do Edge Function
--   `chat-media-url` cấp.
-- CỐ Ý không tạo policy nào cho authenticated/anon trên storage.objects của bucket này: RLS bật sẵn nên mọi truy cập
-- trực tiếp bị chặn, chỉ service role (Edge Function) đọc/ghi.

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('chat-media', 'chat-media', false, 2097152, array['image/jpeg', 'image/png', 'image/webp'])
on conflict (id) do update
  set public = excluded.public,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;
