import { handleServerTime } from "./handler.ts";

Deno.serve((req) => handleServerTime(req));
