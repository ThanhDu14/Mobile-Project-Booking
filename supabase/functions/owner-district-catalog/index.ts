import { handleOwnerDistrictCatalog } from "./handler.ts";

Deno.serve((req) => handleOwnerDistrictCatalog(req));

