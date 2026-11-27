import { handleBookingReminders } from "../../supabase/functions/booking-reminders/handler.ts";
import { handleChatSend } from "../../supabase/functions/chat-send/handler.ts";
import { handleGroupPost } from "../../supabase/functions/group-post/handler.ts";
import { handleGroupManage } from "../../supabase/functions/group-manage/handler.ts";
import { handleBookingPaymentConfirm } from "../../supabase/functions/booking-payment-confirm/handler.ts";
import { handleNotificationDelivery } from "../../supabase/functions/notification-delivery/handler.ts";
import { handleOwnerCreateVoucher } from "../../supabase/functions/owner-create-voucher/handler.ts";
import * as firestore from "../../supabase/functions/_shared/firestore.ts";

const project = Deno.env.get("FIREBASE_PROJECT_ID");
const secret = Deno.env.get("NOTIFICATION_LOCAL_CRON_SECRET");
for (const key of ["FIRESTORE_EMULATOR_HOST", "FIREBASE_AUTH_EMULATOR_HOST"]) {
  if (!/^127\.0\.0\.1:\d+$/.test(Deno.env.get(key) ?? "")) throw new Error(`Local ${key} required`);
}
if (!project?.startsWith("demo-") || !secret) throw new Error("Demo project and local cron secret required");

Deno.serve({ hostname: Deno.env.get("NOTIFICATION_LOCAL_BIND") ?? "127.0.0.1", port: 18765 }, async req => {
  if (req.headers.get("x-cron-secret") !== secret) return new Response(null, { status: 401 });
  const url = new URL(req.url);
  const path = url.pathname;
  const response = await (async () => {
    switch (path) {
      case "/functions/v1/chat-send": return await handleChatSend(req);
      case "/functions/v1/group-post": return await handleGroupPost(req);
      case "/functions/v1/group-manage": return await handleGroupManage(req);
      case "/functions/v1/owner-create-voucher": return await handleOwnerCreateVoucher(req);
      case "/functions/v1/booking-payment-confirm": return await handleBookingPaymentConfirm(req);
      case "/functions/v1/booking-reminders": return await handleBookingReminders(req, { cronSecret: secret });
      case "/functions/v1/notification-delivery": {
        const groupId = url.searchParams.get("group");
        const venueId = url.searchParams.get("venue");
        const filter = groupId ? { field: "groupId", value: groupId } : venueId ? { field: "venueId", value: venueId } : null;
        return await handleNotificationDelivery(req, {
          cronSecret: secret,
          send: () => Promise.resolve({ kind: "accepted" }),
          ...(filter ? { store: {
            ...firestore,
            query: (collection, filters, options) => collection === "notificationFanouts"
              ? firestore.query(collection, [...filters, { ...filter, op: "EQUAL" }], options)
              : Promise.resolve([]),
          } } : {}),
        });
      }
      default: return new Response(null, { status: 404 });
    }
  })();
  console.log(`${path} ${response.status}`);
  return response;
});
