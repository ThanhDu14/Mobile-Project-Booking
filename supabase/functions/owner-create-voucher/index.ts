import { handleOwnerCreateVoucher } from "./handler.ts";

Deno.serve((req) => handleOwnerCreateVoucher(req));
