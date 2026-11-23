import { handleNotificationDelivery } from "./handler.ts";
Deno.serve(req => handleNotificationDelivery(req));
