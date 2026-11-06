import { handleOwnerCancelBooking } from "./handler.ts";

Deno.serve((req) => handleOwnerCancelBooking(req));
