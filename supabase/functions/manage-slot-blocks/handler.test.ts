import { assertEquals } from "jsr:@std/assert@1.0.19";
import { handleManageSlotBlocks } from "./handler.ts";

Deno.test("manage-slot-blocks: rejects non-POST requests", async () => {
  const req = new Request("https://localhost/manage-slot-blocks", { method: "GET" });
  const res = await handleManageSlotBlocks(req);
  assertEquals(res.status, 405);
});

Deno.test("manage-slot-blocks: rejects invalid action", async () => {
  const req = new Request("https://localhost/manage-slot-blocks", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      venueId: "v1",
      courtId: "c1",
      action: "INVALID",
    }),
  });
  const res = await handleManageSlotBlocks(req);
  assertEquals(res.status, 401); // No auth token provided
});
