import { handleCheckinBooking } from "./handler.ts";

Deno.serve((req) => handleCheckinBooking(req));
