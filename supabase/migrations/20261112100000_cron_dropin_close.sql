-- Every minute: close drop-in waitlists at T-30 minutes and mark ended sessions COMPLETED (spec 081 US4, dropin-close).
-- No production secret is stored in this migration; configure Vault before enabling the job (same secrets as expire-holds).
--   select vault.create_secret('https://<project-ref>.supabase.co', 'project_url');
--   select vault.create_secret('<CRON_SECRET>', 'cron_secret');
-- CRON_SECRET of the Edge Function itself: `npx supabase secrets set CRON_SECRET=...` (cloud) or supabase/.env (local).

create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net with schema extensions;

select cron.unschedule(jobid) from cron.job where jobname = 'dropin-close';

select cron.schedule(
  'dropin-close',
  '* * * * *',
  $$
  select net.http_post(
    url := coalesce((select decrypted_secret from vault.decrypted_secrets where name = 'project_url'), '')
      || '/functions/v1/dropin-close',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-cron-secret', coalesce((select decrypted_secret from vault.decrypted_secrets where name = 'cron_secret'), '')
    ),
    body := '{}'::jsonb,
    timeout_milliseconds := 60000
  );
  $$
);
