-- Spec 060: gọi Edge Function complete-bookings mỗi 5 phút để chuyển đơn CONFIRMED qua endAt
-- sang COMPLETED (nếu đã check-in) hoặc NO_SHOW (nếu chưa check-in).
-- Giống job delete-account-sweeper và dropin-close.
--
-- Không ghi secret trong file này. Trước khi job chạy được, người quản lý project tạo 2 secret trong Vault
-- (Dashboard → Project Settings → Vault, hoặc SQL editor):
--   select vault.create_secret('https://<project-ref>.supabase.co', 'project_url');
--   select vault.create_secret('<giá trị CRON_SECRET>', 'cron_secret');

create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net with schema extensions;

-- Chạy lại migration không tạo job trùng.
select cron.unschedule(jobid) from cron.job where jobname = 'complete-bookings';

select cron.schedule(
  'complete-bookings',
  '*/5 * * * *',
  $$
  select net.http_post(
    url := coalesce((select decrypted_secret from vault.decrypted_secrets where name = 'project_url'), '')
      || '/functions/v1/complete-bookings',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-cron-secret', coalesce((select decrypted_secret from vault.decrypted_secrets where name = 'cron_secret'), '')
    ),
    body := '{}'::jsonb,
    timeout_milliseconds := 60000
  );
  $$
);
