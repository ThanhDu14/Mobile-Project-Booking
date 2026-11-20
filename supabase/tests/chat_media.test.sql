-- S1–S4 của specs/091-chat/contracts/security-rules.md mục 3 (spec 091, T012).
-- Chỉ chạy trên Supabase local: npx supabase db reset && npx supabase test db
begin;
create extension if not exists pgtap with schema extensions;
select plan(4);

-- Dữ liệu mẫu do service role (postgres) tạo, để S1 chứng minh "có object nhưng authenticated vẫn thấy 0 dòng".
insert into storage.objects (bucket_id, name) values ('chat-media', 'c1/m1.jpg');

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"player1","appRole":"PLAYER","role":"authenticated"}', true);

-- S1: không có policy select → 0 dòng dù object tồn tại
select results_eq(
  $$ select count(*)::int from storage.objects where bucket_id = 'chat-media' $$,
  $$ values (0) $$,
  'S1: authenticated không đọc được object trong chat-media');

-- S2: không có policy insert → bị RLS chặn
select throws_ok(
  $$ insert into storage.objects (bucket_id, name) values ('chat-media', 'c1/m2.jpg') $$,
  '42501', null,
  'S2: authenticated không tải trực tiếp vào chat-media');

-- S3: anon
reset role;
set local role anon;
select set_config('request.jwt.claims', '{"role":"anon"}', true);
select results_eq(
  $$ select count(*)::int from storage.objects where bucket_id = 'chat-media' $$,
  $$ values (0) $$,
  'S3: anon không đọc được object trong chat-media');

-- S4: cấu hình bucket
reset role;
select results_eq(
  $$ select public, file_size_limit, allowed_mime_types from storage.buckets where id = 'chat-media' $$,
  $$ values (false, 2097152::bigint, array['image/jpeg', 'image/png', 'image/webp']) $$,
  'S4: chat-media private, ≤ 2 MB, đúng 3 MIME');

select * from finish();
rollback;
