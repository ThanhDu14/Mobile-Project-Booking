import { handleOwnerApplications } from "./handler.ts";

Deno.serve((req) => handleOwnerApplications(req));
