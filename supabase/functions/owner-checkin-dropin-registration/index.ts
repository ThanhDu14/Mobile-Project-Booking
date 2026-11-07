import { handleOwnerCheckinDropIn } from "./handler.ts";

Deno.serve((req) => handleOwnerCheckinDropIn(req));
