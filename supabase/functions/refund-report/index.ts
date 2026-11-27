// Logic ở handler.ts (spec 050 US4).
import { handleRefundReport } from "./handler.ts";

Deno.serve((req) => handleRefundReport(req));
