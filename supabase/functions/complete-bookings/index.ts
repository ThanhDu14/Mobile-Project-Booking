// Edge Function complete-bookings: quét đơn CONFIRMED qua giờ kết thúc để chuyển sang COMPLETED hoặc NO_SHOW.
// verify_jwt = false: xác thực bằng header x-cron-secret hoặc token.
import { handleCompleteBookings } from "./handler.ts";

Deno.serve((req) => handleCompleteBookings(req));
