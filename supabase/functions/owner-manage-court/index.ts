import { handleOwnerManageCourt } from "./handler.ts";

Deno.serve((req) => handleOwnerManageCourt(req));
