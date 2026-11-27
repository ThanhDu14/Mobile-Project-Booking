// Edge Function `dropin-close` (spec 081 US4). Logic ở handler.ts.
// verify_jwt tắt trong supabase/config.toml; mọi request được xác thực bằng CRON_SECRET trong handler.
import { handleDropInClose } from "./handler.ts";

Deno.serve((req) => handleDropInClose(req));
