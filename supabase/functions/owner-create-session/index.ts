import { handleOwnerCreateSession } from "./handler.ts";

Deno.serve((req) => handleOwnerCreateSession(req));
