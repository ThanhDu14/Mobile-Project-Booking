// verify_jwt tắt trong config.toml: function tự kiểm Firebase ID token (requireUser).
import { handleBookingPaymentTransfer } from "./handler.ts";

Deno.serve((req) => handleBookingPaymentTransfer(req));
