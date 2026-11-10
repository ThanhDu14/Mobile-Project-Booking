import { handlePlayerCancelBooking } from "./handler.ts";

Deno.serve((req) => handlePlayerCancelBooking(req));

