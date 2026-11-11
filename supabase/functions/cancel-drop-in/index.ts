// Edge Function `cancel-drop-in` (spec 080 US4). Logic ở handler.ts.
import { handleCancelDropIn } from "./handler.ts";

Deno.serve((req) => handleCancelDropIn(req));
