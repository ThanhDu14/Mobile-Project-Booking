import { handleAdminReviewApplication } from "./handler.ts";

Deno.serve((req) => handleAdminReviewApplication(req));
