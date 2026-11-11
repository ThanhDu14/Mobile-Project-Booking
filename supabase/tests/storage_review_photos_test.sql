-- S1–S6 của specs/070-review-favorite/contracts/security-rules.md §2 (spec 070, C; T038).
-- Chỉ chạy trên Supabase local: npx supabase db reset && npx supabase test db
begin;
create extension if not exists pgtap with schema extensions;
select plan(8);

-- Storage API đặt cờ này trước khi xóa object; test xóa thẳng bằng SQL nên tự đặt để kiểm tra RLS của DELETE.
select set_config('storage.allow_delete_query', 'true', true);

-- S5: giới hạn kích thước/MIME do Storage API áp theo cấu hình bucket (SQL không kiểm được upload thật).
select results_eq(
  $$ select public, file_size_limit, allowed_mime_types from storage.buckets where id = 'review-photos' $$,
  $$ values (true, 2097152::bigint, array['image/jpeg', 'image/png', 'image/webp']) $$,
  'S5/S6: bucket public, ≤ 2 MB, chỉ JPEG/PNG/WebP (3 MB hoặc image/gif bị Storage API chặn)');

set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"u1","appRole":"PLAYER","role":"authenticated"}', true);

-- S1
select lives_ok(
  $$ insert into storage.objects (bucket_id, name) values ('review-photos', 'u1/b1/x.jpg') $$,
  'S1: u1 tải ảnh lên u1/b1/x.jpg');

-- S2
select throws_ok(
  $$ insert into storage.objects (bucket_id, name) values ('review-photos', 'u2/b1/x.jpg') $$,
  '42501', null,
  'S2: u1 không tải được vào thư mục của u2');

-- S3: thiếu thư mục reviewId, hoặc lồng quá sâu
select throws_ok(
  $$ insert into storage.objects (bucket_id, name) values ('review-photos', 'u1/x.jpg') $$,
  '42501', null,
  'S3: u1/x.jpg (thiếu thư mục reviewId) bị chặn');
select throws_ok(
  $$ insert into storage.objects (bucket_id, name) values ('review-photos', 'u1/b1/sub/x.jpg') $$,
  '42501', null,
  'S3b: u1/b1/sub/x.jpg (lồng quá sâu) bị chặn');

-- S4: không có policy DELETE/UPDATE cho authenticated → chỉ service role (Edge Function `review`) xóa được
select results_eq(
  $$ with d as (delete from storage.objects
                where bucket_id = 'review-photos' and name = 'u1/b1/x.jpg' returning 1)
     select count(*)::int from d $$,
  $$ values (0) $$,
  'S4: u1 không tự xóa được ảnh của mình');
select results_eq(
  $$ with u as (update storage.objects set metadata = '{"x":1}'::jsonb
                where bucket_id = 'review-photos' and name = 'u1/b1/x.jpg' returning 1)
     select count(*)::int from u $$,
  $$ values (0) $$,
  'S4b: u1 không sửa (upsert) được ảnh');

-- S6 (ghi): chưa đăng nhập không tải được; đọc đi qua URL public của bucket (đã kiểm ở trên)
reset role;
set local role anon;
select set_config('request.jwt.claims', '{"role":"anon"}', true);
select throws_ok(
  $$ insert into storage.objects (bucket_id, name) values ('review-photos', 'u1/b1/y.jpg') $$,
  '42501', null,
  'S6b: anon không tải được');

select * from finish();
rollback;
