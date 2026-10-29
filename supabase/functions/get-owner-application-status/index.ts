import { handleGetOwnerApplicationStatus } from "./handler.ts";

Deno.serve((req) => handleGetOwnerApplicationStatus(req));
