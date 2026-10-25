-- ST1–ST8 của specs/011-account-profile/contracts/storage-avatars.md §4 (spec 011, A).
-- Chỉ chạy trên Supabase local: npx supabase db reset && npx supabase test db
begin;
create extension if not exists pgtap with schema extensions;
select plan(8);

-- Storage API đặt cờ này trước khi xóa object; test xóa thẳng bằng SQL nên tự đặt để kiểm tra RLS của DELETE.
select set_config('storage.allow_delete_query', 'true', true);

-- Bucket khác để kiểm tra policy avatars_* không lan sang (ST8). Tạo bằng postgres, rollback khi xong.
insert into storage.buckets (id, name, public) values ('owner-docs', 'owner-docs', false)
on conflict (id) do nothing;

-- ST1: chủ thư mục, có appRole
set local role authenticated;
select set_config('request.jwt.claims', '{"sub":"u1","appRole":"PLAYER","role":"authenticated"}', true);
select lives_ok(
  $$ insert into storage.objects (bucket_id, name) values ('avatars', 'u1/avatar_1.jpg') $$,
  'ST1: u1 tải ảnh vào thư mục của mình');

-- ST2: thư mục người khác
select throws_ok(
  $$ insert into storage.objects (bucket_id, name) values ('avatars', 'u2/avatar_1.jpg') $$,
  '42501', null,
  'ST2: u1 không tải được vào thư mục của u2');

-- ST4: không có policy UPDATE nên không dòng nào bị sửa
select results_eq(
  $$ with u as (update storage.objects set metadata = '{"x":1}'::jsonb
                where bucket_id = 'avatars' and name = 'u1/avatar_1.jpg' returning 1)
     select count(*)::int from u $$,
  $$ values (0) $$,
  'ST4: không sửa (update/upsert) được object, kể cả của mình');

-- ST8: bucket khác
select throws_ok(
  $$ insert into storage.objects (bucket_id, name) values ('owner-docs', 'u1/giay-to.jpg') $$,
  '42501', null,
  'ST8: policy avatars_* không cho ghi vào bucket owner-docs');

-- ST3: chưa có appRole (chưa qua on-signup)
select set_config('request.jwt.claims', '{"sub":"u1","role":"authenticated"}', true);
select throws_ok(
  $$ insert into storage.objects (bucket_id, name) values ('avatars', 'u1/avatar_2.jpg') $$,
  '42501', null,
  'ST3: thiếu claim appRole thì không tải được');

-- ST6: người khác xóa
select set_config('request.jwt.claims', '{"sub":"u2","appRole":"PLAYER","role":"authenticated"}', true);
select results_eq(
  $$ with d as (delete from storage.objects
                where bucket_id = 'avatars' and name = 'u1/avatar_1.jpg' returning 1)
     select count(*)::int from d $$,
  $$ values (0) $$,
  'ST6: u2 không xóa được ảnh của u1');

-- ST5: chủ thư mục xóa
select set_config('request.jwt.claims', '{"sub":"u1","appRole":"PLAYER","role":"authenticated"}', true);
select results_eq(
  $$ with d as (delete from storage.objects
                where bucket_id = 'avatars' and name = 'u1/avatar_1.jpg' returning 1)
     select count(*)::int from d $$,
  $$ values (1) $$,
  'ST5: u1 xóa được ảnh của mình');

-- ST7: chưa đăng nhập
reset role;
set local role anon;
select set_config('request.jwt.claims', '{"role":"anon"}', true);
select throws_ok(
  $$ insert into storage.objects (bucket_id, name) values ('avatars', 'u1/x.jpg') $$,
  '42501', null,
  'ST7: anon không tải được');

select * from finish();
rollback;
