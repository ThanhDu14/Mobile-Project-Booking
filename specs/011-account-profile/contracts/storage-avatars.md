# Contract: Supabase Storage bucket `avatars`

**Migration:** `supabase/migrations/<timestamp>_avatars_bucket.sql`. **Test:** `supabase/tests/storage_avatars_test.sql` (pgTAP, chạy bằng `npx supabase test db` trên Supabase local).

Supabase nhận Firebase ID token qua Third-party Auth (PR #23): `auth.jwt() ->> 'sub'` là Firebase `uid`, `auth.jwt() ->> 'appRole'` là vai trò. Không dùng `auth.uid()` vì `uid` của Firebase không phải UUID.

## 1. Bucket

```sql
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('avatars', 'avatars', true, 2097152, array['image/jpeg','image/png','image/webp'])
on conflict (id) do update
  set public = excluded.public,
      file_size_limit = excluded.file_size_limit,
      allowed_mime_types = excluded.allowed_mime_types;
```

## 2. Chính sách RLS trên `storage.objects`

| Tên policy | Lệnh | Điều kiện |
|---|---|---|
| `avatars_insert_own` | `INSERT` (`with check`) | `bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.jwt() ->> 'sub' and auth.jwt() ->> 'appRole' is not null` |
| `avatars_delete_own` | `DELETE` (`using`) | như trên |
| `avatars_select_own` | `SELECT` (`using`) | như trên. Cần để client `list`/`remove` object của mình; đọc ảnh vẫn đi qua URL public, không cần policy |
| — | `UPDATE` | Không tạo policy, nên mọi `UPDATE` (kể cả `upsert`) bị chặn |

## 3. Cách app dùng

| Thao tác | Lời gọi (supabase-kt `storage-kt`) | Ghi chú |
|---|---|---|
| Tải lên | `storage.from("avatars").upload("{uid}/avatar_{now}.jpg", bytes) { upsert = false; contentType = Jpeg }` | Token = Firebase ID token qua `SupabaseModule` (010) |
| Xóa object cũ | `storage.from("avatars").delete(oldPath)` | Bỏ qua lỗi |
| Hiển thị | `StorageUrls.publicUrl("avatars", avatarPath)` + Coil | Không gọi Storage API |

Thứ tự đổi ảnh và xử lý lỗi: [research R3](../research.md#r3-ảnh-đại-diện-trên-supabase-storage).

## 4. Test (pgTAP, giả lập JWT)

Mỗi test đặt `set local role authenticated;` và `select set_config('request.jwt.claims', '{"sub":"<uid>","appRole":"PLAYER","role":"authenticated"}', true);`, rồi thử `insert`/`delete` trên `storage.objects`.

| # | JWT | Thao tác | Kỳ vọng |
|---|---|---|---|
| ST1 | `sub = u1`, `appRole = PLAYER` | Insert `avatars/u1/avatar_1.jpg` | Được phép |
| ST2 | `sub = u1` | Insert `avatars/u2/avatar_1.jpg` | Bị chặn |
| ST3 | `sub = u1`, **không có** `appRole` | Insert `avatars/u1/avatar_1.jpg` | Bị chặn |
| ST4 | `sub = u1` | Update object `avatars/u1/avatar_1.jpg` | Bị chặn |
| ST5 | `sub = u1` | Delete `avatars/u1/avatar_1.jpg` | Được phép |
| ST6 | `sub = u2` | Delete `avatars/u1/avatar_1.jpg` | Bị chặn (0 dòng bị xóa) |
| ST7 | role `anon` | Insert `avatars/u1/x.jpg` | Bị chặn |
| ST8 | `sub = u1` | Insert vào bucket `owner-docs` với policy của 011 | Không bị policy `avatars_*` cho phép (kiểm tra policy không lan sang bucket khác) |

Giới hạn kích thước và MIME do Storage API kiểm tra (không phải RLS), kiểm bằng tay theo [quickstart](../quickstart.md) (Q8).
