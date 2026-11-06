import { handleAdminRevokeOwner } from "./handler.ts";

Deno.serve(async (req) => {
  return await handleAdminRevokeOwner(req);
});
