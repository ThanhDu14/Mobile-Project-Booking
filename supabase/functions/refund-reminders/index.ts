// Logic ở handler.ts (spec 050 US4).
import { handleRefundReminders } from "./handler.ts";

Deno.serve((req) => handleRefundReminders(req));
