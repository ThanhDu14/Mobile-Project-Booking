import { handleSyncVenuePrice } from "./handler.ts";

Deno.serve((req) => handleSyncVenuePrice(req));
