// Edge Function `group-post` (spec 090). Logic ở handler.ts.
import { handleGroupPost } from "./handler.ts";

Deno.serve((req) => handleGroupPost(req));
