# Setup Supabase

Project dùng chung: **`smashnow`** (ref `dbjzrpmtslncogipwpai`, region Singapore, gói Free). Dùng Storage (ảnh), Edge Functions và pg_cron; không dùng database của Supabase cho dữ liệu nghiệp vụ, xem [constitution](../../.specify/memory/constitution.md).

Đăng nhập dùng Firebase Auth qua **Third-party Auth**: Supabase tin Firebase ID token của project `mobile-22e6c`. Đã bật trên project cloud và trong `supabase/config.toml` cho bản local.

## Chạy Supabase local

Cần Docker. Lần đầu tải khoảng 8 GB image; khi chạy dùng khoảng 1,7 GB RAM.

```bash
npx supabase start     # bật Storage, Edge Functions, Postgres... trên máy
npx supabase status    # xem URL và key của bản local
npx supabase stop      # tắt khi không dùng
```

Test Rules/RLS và Edge Functions chạy trên bản local, không chạy trên project thật (constitution, nguyên tắc IV).

## Truy cập project cloud

Chỉ người cần deploy Edge Function, đẩy migration hoặc xem log mới cần tài khoản. Nhờ người quản lý mời vào organization với role **Developer** (không chọn Owner/Administrator: sẽ tính vào giới hạn 2 project Free của người được mời).

```bash
npx supabase login
npx supabase link --project-ref dbjzrpmtslncogipwpai
```

**Không chạy `supabase config push`** lên project này: `config.toml` là cấu hình cho máy local (tắt xác nhận email, `site_url` là `127.0.0.1`...), push lên sẽ ghi đè cấu hình của project thật.

## Khóa

- URL và publishable key của project cloud xin trong nhóm chat. Key này được phép nằm trong app nhưng vẫn không commit: đặt trong `local.properties`.
- Khóa `service_role` / secret key chỉ dùng trong secrets của Edge Function (`npx supabase secrets set`) hoặc `supabase/.env` (đã nằm trong `.gitignore`). Tuyệt đối không đưa vào app hay repo.

## Claim `role`

Firebase ID token phải có custom claim `role: "authenticated"` thì Supabase mới coi là người dùng đã đăng nhập; thiếu claim thì request chạy với quyền `anon`. Edge Function `on-signup` (spec 010) gán claim này, và app gọi `getIdToken(true)` sau khi đăng ký để lấy token mới.

## Cron đóng hàng chờ vãng lai

Migration `20261120100000_cron_dropin_close.sql` bật job `dropin-close` mỗi phút. Function xác thực header `x-cron-secret`; `verify_jwt = false` để pg_cron không cần Firebase token.

Trước khi áp dụng migration, deploy function `dropin-close`, cấu hình Edge secret `CRON_SECRET` và hai Vault secret `project_url`, `cron_secret`. `cron_secret` phải trùng `CRON_SECRET`; không ghi giá trị secret vào SQL được commit. Job đóng hàng chờ từ T−30 phút và chuyển buổi hết giờ sang `COMPLETED`. Chạy lại migration chỉ giữ một job cùng tên; chạy lại function không gửi trùng thông báo.

`owner-cancel-session` gọi `_shared/waitlist.ts::closeWaitlistInTx` trong transaction để kết thúc các lượt chờ. `owner-update-session` chỉ gọi `promoteFromWaitlist` khi tăng sức chứa; giảm sức chứa giữ nguyên hàng chờ. Thông báo inbox được ghi cùng transaction để chỉ xuất hiện khi thay đổi đã commit.
