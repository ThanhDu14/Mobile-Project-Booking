import { handleAdminModerateReport } from "./handler.ts";

Deno.serve((req) => handleAdminModerateReport(req));
