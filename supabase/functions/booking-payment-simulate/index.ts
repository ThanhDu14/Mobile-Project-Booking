// verify_jwt tắt trong config.toml: function tự kiểm Firebase ID token (requireUser).
import { handleBookingPaymentSimulate } from "./handler.ts";

Deno.serve((req) => handleBookingPaymentSimulate(req));
