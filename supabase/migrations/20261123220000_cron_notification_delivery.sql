-- Feature 100 D1, explicitly approved 2026-11-23. Reviewable only: not applied by implementation.
-- Reuses existing project_url/cron_secret Vault secrets. Never embed service credentials.
create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net with schema extensions;

select cron.unschedule(jobid) from cron.job where jobname = 'notification-delivery';
select cron.schedule(
  'notification-delivery',
  '* * * * *',
  $$
  select net.http_post(
    url := coalesce((select decrypted_secret from vault.decrypted_secrets where name = 'project_url'), '')
      || '/functions/v1/notification-delivery',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-cron-secret', coalesce((select decrypted_secret from vault.decrypted_secrets where name = 'cron_secret'), '')
    ),
    body := '{}'::jsonb,
    timeout_milliseconds := 110000
  );
  $$
);
