// Edge Function `chat-media-url` (spec 091). Logic ở handler.ts.
import { handleChatMediaUrl } from "./handler.ts";

Deno.serve((req) => handleChatMediaUrl(req));
