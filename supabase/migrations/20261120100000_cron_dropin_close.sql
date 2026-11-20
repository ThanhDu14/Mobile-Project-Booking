-- Vault project_url and cron_secret must match the Edge Function CRON_SECRET.
do $migration$
begin
create extension if not exists pg_cron with schema pg_catalog;
create extension if not exists pg_net with schema extensions;

perform cron.unschedule(jobid) from cron.job where jobname = 'dropin-close';
perform cron.schedule('dropin-close', '* * * * *', $$
  select net.http_post(
    url := (select decrypted_secret from vault.decrypted_secrets where name = 'project_url') || '/functions/v1/dropin-close',
    headers := jsonb_build_object('Content-Type', 'application/json',
      'x-cron-secret', (select decrypted_secret from vault.decrypted_secrets where name = 'cron_secret')),
    body := '{}'::jsonb, timeout_milliseconds := 60000
  );
$$);
end;
$migration$;
