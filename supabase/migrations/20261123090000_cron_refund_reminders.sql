-- Spec 050 US4: nhắc chủ sân hoàn tiền mỗi giờ (refund PENDING quá 24 giờ, contracts/edge-functions.md §6).
-- Dùng lại secret Vault `project_url` và `cron_secret` của job expire-holds; không lưu secret trong migration.

create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net with schema extensions;

select cron.unschedule(jobid) from cron.job where jobname = 'refund-reminders';

select cron.schedule(
  'refund-reminders',
  '0 * * * *',
  $$
  select net.http_post(
    url := coalesce((select decrypted_secret from vault.decrypted_secrets where name = 'project_url'), '')
      || '/functions/v1/refund-reminders',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-cron-secret', coalesce((select decrypted_secret from vault.decrypted_secrets where name = 'cron_secret'), '')
    ),
    body := '{}'::jsonb,
    timeout_milliseconds := 60000
  );
  $$
);
