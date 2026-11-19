// Edge Function `chat-send` (spec 091). Logic ở handler.ts.
import { handleChatSend } from "./handler.ts";

Deno.serve((req) => handleChatSend(req));
