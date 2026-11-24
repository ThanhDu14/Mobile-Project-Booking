import * as fs from "../_shared/firestore.ts";
import { httpV1Sender } from "../_shared/notification-fcm.ts";
import { cleanupDeliveries, runDeliveryWorker, type DeliveryStore, type Sender } from "../_shared/notification-worker.ts";
import { processDropInChangeFanouts } from "../_shared/dropin-change-notification.ts";

export async function handleNotificationDelivery(req: Request, deps: {
  store?: DeliveryStore; send?: Sender; cronSecret?: string; now?: () => Date;
} = {}): Promise<Response> {
  if (req.method !== "POST") return new Response(null, { status: 405 });
  const secret = deps.cronSecret ?? Deno.env.get("CRON_SECRET");
  if (!secret || req.headers.get("x-cron-secret") !== secret) return new Response(null, { status: 401 });
  // Emulator invocations must explicitly inject a fake: never send real FCM from a local harness.
  if (Deno.env.get("FIRESTORE_EMULATOR_HOST") && !deps.send) return Response.json({ error: "FAKE_SENDER_REQUIRED" }, { status: 503 });
  try {
    const store = deps.store ?? fs, clock = deps.now ?? (() => new Date());
    const result = await runDeliveryWorker(store, deps.send ?? httpV1Sender(), clock);
    const dropInPages = await processDropInChangeFanouts(store, clock());
    const cleaned = await cleanupDeliveries(store, clock());
    return Response.json({ ...result, cleaned, dropInPages });
  } catch { return Response.json({ error: "DELIVERY_UNAVAILABLE" }, { status: 503 }); }
}
