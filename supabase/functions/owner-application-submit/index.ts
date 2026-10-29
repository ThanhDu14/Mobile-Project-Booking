import { handleOwnerApplicationSubmit } from "./handler.ts";

Deno.serve((req) => handleOwnerApplicationSubmit(req));
