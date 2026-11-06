-- Spec 011 US6 (T081): gọi Edge Function delete-account-sweeper mỗi 5 phút để làm tiếp các lần xóa tài khoản chưa xong
-- (research R11, contracts/edge-functions-account.md §2). Giống job dropin-close của 081.
--
-- Không ghi secret trong file này. Trước khi job chạy được, người quản lý project tạo 2 secret trong Vault
-- (Dashboard → Project Settings → Vault, hoặc SQL editor), giá trị CRON_SECRET trùng secret của Edge Function:
--   select vault.create_secret('https://<project-ref>.supabase.co', 'project_url');
--   select vault.create_secret('<giá trị CRON_SECRET>', 'cron_secret');
-- Chưa có secret thì job vẫn được tạo, net.http_post gửi tới URL rỗng và thất bại, không ảnh hưởng gì khác.

create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net with schema extensions;

-- Chạy lại migration không tạo job trùng.
select cron.unschedule(jobid) from cron.job where jobname = 'delete-account-sweeper';

select cron.schedule(
  'delete-account-sweeper',
  '*/5 * * * *',
  $$
  select net.http_post(
    url := coalesce((select decrypted_secret from vault.decrypted_secrets where name = 'project_url'), '')
      || '/functions/v1/delete-account-sweeper',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-cron-secret', coalesce((select decrypted_secret from vault.decrypted_secrets where name = 'cron_secret'), '')
    ),
    body := '{}'::jsonb,
    timeout_milliseconds := 150000
  );
  $$
);
