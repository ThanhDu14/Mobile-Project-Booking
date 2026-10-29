import { handleOwnerDocumentDownload } from "./handler.ts";

Deno.serve((req) => handleOwnerDocumentDownload(req));

