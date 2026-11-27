-- B2 approved 2026-11-24. Local/review artifact; not applied to a hosted database.
create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net with schema extensions;
select cron.unschedule(jobid) from cron.job where jobname = 'booking-reminders';
select cron.schedule('booking-reminders', '* * * * *', $$
  select net.http_post(
    url := coalesce((select decrypted_secret from vault.decrypted_secrets where name = 'project_url'), '') || '/functions/v1/booking-reminders',
    headers := jsonb_build_object('Content-Type', 'application/json', 'x-cron-secret', coalesce((select decrypted_secret from vault.decrypted_secrets where name = 'cron_secret'), '')),
    body := '{}'::jsonb, timeout_milliseconds := 110000
  );
$$);
