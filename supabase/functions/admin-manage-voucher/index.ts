import { handleAdminManageVoucher } from "./handler.ts";

Deno.serve(async (req) => {
  return await handleAdminManageVoucher(req);
});
