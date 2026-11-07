import { handleOwnerCancelSession } from "./handler.ts";

Deno.serve((req) => handleOwnerCancelSession(req));
