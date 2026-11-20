import { handleAdminManageGroup } from "./handler.ts";
Deno.serve((req) => handleAdminManageGroup(req));
