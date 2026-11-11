// Edge Function `register-drop-in` (spec 080). Logic ở handler.ts.
import { handleRegisterDropIn } from "./handler.ts";

Deno.serve((req) => handleRegisterDropIn(req));
