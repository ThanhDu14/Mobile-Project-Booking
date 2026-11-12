import { handleOwnerUpdateSession } from "./handler.ts";

Deno.serve((req) => handleOwnerUpdateSession(req));
