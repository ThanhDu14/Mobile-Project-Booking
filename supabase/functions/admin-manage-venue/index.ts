import { handleAdminManageVenue } from "./handler.ts";

Deno.serve(async (req) => {
  return await handleAdminManageVenue(req);
});
