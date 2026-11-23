// Edge Function `booking-payment-confirm` (spec 050 US2). Logic ở handler.ts.
import { handleBookingPaymentConfirm } from "./handler.ts";

Deno.serve((req) => handleBookingPaymentConfirm(req));
