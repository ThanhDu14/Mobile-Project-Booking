import { handleBookingReminders } from "./handler.ts";
Deno.serve(req => handleBookingReminders(req));
