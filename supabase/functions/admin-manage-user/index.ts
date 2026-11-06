import { handleAdminManageUser } from "./handler.ts";

Deno.serve(async (req) => {
  return await handleAdminManageUser(req);
});
