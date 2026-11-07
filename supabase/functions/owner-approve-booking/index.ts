import { handleOwnerApproveBooking } from "./handler.ts";

Deno.serve((req) => handleOwnerApproveBooking(req));
