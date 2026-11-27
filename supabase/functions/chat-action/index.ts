// Edge Function `chat-action` (spec 091). Logic ở handler.ts.
import { handleChatAction } from "./handler.ts";

Deno.serve((req) => handleChatAction(req));
