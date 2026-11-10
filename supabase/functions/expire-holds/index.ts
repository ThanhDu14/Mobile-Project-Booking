// verify_jwt is disabled in config; every request is authenticated by CRON_SECRET in the handler.
import { handleExpireHolds } from "./handler.ts";

Deno.serve((req) => handleExpireHolds(req));
