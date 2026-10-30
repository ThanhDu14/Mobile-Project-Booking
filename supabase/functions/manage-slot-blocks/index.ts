import { handleManageSlotBlocks } from "./handler.ts";

Deno.serve((req) => handleManageSlotBlocks(req));
