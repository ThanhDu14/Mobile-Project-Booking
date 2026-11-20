-- S1–S5 của specs/090-community-group/contracts/security-rules.md mục 3 (spec 090, T011).
-- Chỉ chạy trên Supabase local: npx supabase db reset && npx supabase test db
begin;
create extension if not exists pgtap with schema extensions;
select plan(7);

-- Dữ liệu mẫu do service role (postgres) tạo, để S1 chứng minh "có object nhưng authenticated vẫn thấy 0 dòng".
insert into storage.objects (bucket_id, name)
values ('group-media', 'g1/p1/0.jpg'),
       ('group-avatars', 'g1/avatar.jpg');

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"player1","appRole":"PLAYER","role":"authenticated"}', true);

-- S1: không có policy select → 0 dòng dù object tồn tại
select results_eq(
  $$ select count(*)::int from storage.objects where bucket_id = 'group-media' $$,
  $$ values (0) $$,
  'S1: authenticated không đọc được object trong group-media');

-- S2: không có policy insert → bị RLS chặn
select throws_ok(
  $$ insert into storage.objects (bucket_id, name) values ('group-media', 'g1/p1/1.jpg') $$,
  '42501', null,
  'S2: authenticated không tải trực tiếp vào group-media');

-- S4: group-avatars cũng không có policy ghi cho authenticated
select throws_ok(
  $$ insert into storage.objects (bucket_id, name) values ('group-avatars', 'g1/avatar.png') $$,
  '42501', null,
  'S4: authenticated không tải trực tiếp vào group-avatars');

-- S3: anon. Bucket public nên URL public đọc được qua Storage API (API bỏ qua RLS của storage.objects);
-- SQL chỉ kiểm được cờ public của bucket. Truy vấn trực tiếp bảng objects bằng anon vẫn là 0 dòng (không có policy).
reset role;
set local role anon;
select set_config('request.jwt.claims', '{"role":"anon"}', true);
select results_eq(
  $$ select count(*)::int from storage.objects where bucket_id = 'group-media' $$,
  $$ values (0) $$,
  'S3b: anon không đọc được object trong group-media');
select results_eq(
  $$ select count(*)::int from storage.objects where bucket_id = 'group-avatars' $$,
  $$ values (0) $$,
  'S3c: anon không truy vấn trực tiếp được bảng objects (ảnh đại diện đọc qua URL public, không qua RLS)');

-- S3 + S5: cấu hình bucket (Storage API áp giới hạn kích thước/MIME theo cấu hình này)
reset role;
select results_eq(
  $$ select public from storage.buckets where id in ('group-avatars', 'group-media') order by id $$,
  $$ values (true), (false) $$,
  'S3: group-avatars public (anon đọc được qua URL public), group-media private');
select results_eq(
  $$ select id, file_size_limit, allowed_mime_types from storage.buckets
     where id in ('group-avatars', 'group-media') order by id $$,
  $$ values ('group-avatars'::text, 2097152::bigint, array['image/jpeg', 'image/png', 'image/webp']),
            ('group-media'::text, 2097152::bigint, array['image/jpeg', 'image/png', 'image/webp']) $$,
  'S5: cả hai bucket ≤ 2 MB, chỉ JPEG/PNG/WebP');

select * from finish();
rollback;
