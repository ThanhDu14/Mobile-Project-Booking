import { handleOwnerStatisticsSource } from "./handler.ts";
Deno.serve(req => handleOwnerStatisticsSource(req));
