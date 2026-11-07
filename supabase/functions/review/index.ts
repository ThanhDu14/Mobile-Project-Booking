import { handleReview } from "./handler.ts";

Deno.serve((req) => handleReview(req));
