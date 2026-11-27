// Edge Function `group-manage` (spec 090). Logic ở handler.ts.
import { handleGroupManage } from "./handler.ts";

Deno.serve((req) => handleGroupManage(req));
