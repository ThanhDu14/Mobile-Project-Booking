// Edge Function `group-media-url` (spec 090). Logic ở handler.ts.
import { handleGroupMediaUrl } from "./handler.ts";

Deno.serve((req) => handleGroupMediaUrl(req));
