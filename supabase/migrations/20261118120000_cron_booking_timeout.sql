-- Reject PENDING bookings whose approval deadline has passed (spec 060 FR-005).
-- Owners cannot approve or reject after approvalDeadline, so without this job such bookings stay
-- PENDING and keep their courts booked. Uses the same Vault secrets as expire-holds:
--   select vault.create_secret('https://<project-ref>.supabase.co', 'project_url');
--   select vault.create_secret('<CRON_SECRET>', 'cron_secret');

create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net with schema extensions;

select cron.unschedule(jobid) from cron.job where jobname = 'booking-timeout';

select cron.schedule(
  'booking-timeout',
  '*/2 * * * *',
  $$
  select net.http_post(
    url := coalesce((select decrypted_secret from vault.decrypted_secrets where name = 'project_url'), '')
      || '/functions/v1/booking-timeout',
    headers := jsonb_build_object(
      'Content-Type', 'application/json',
      'x-cron-secret', coalesce((select decrypted_secret from vault.decrypted_secrets where name = 'cron_secret'), '')
    ),
    body := '{}'::jsonb,
    timeout_milliseconds := 60000
  );
  $$
);
