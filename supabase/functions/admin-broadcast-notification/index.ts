import { handleAdminBroadcastNotification } from "./handler.ts";

Deno.serve(async (req) => {
  return await handleAdminBroadcastNotification(req);
});
