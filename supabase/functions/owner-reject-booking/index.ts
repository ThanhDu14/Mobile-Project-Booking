import { handleOwnerRejectBooking } from "./handler.ts";

Deno.serve((req) => handleOwnerRejectBooking(req));
