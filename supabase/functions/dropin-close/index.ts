import { handleDropinClose } from "./handler.ts";
Deno.serve((req) => handleDropinClose(req));
