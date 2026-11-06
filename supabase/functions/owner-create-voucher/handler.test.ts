import { assertEquals } from "jsr:@std/assert@1.0.19";
import { handleOwnerCreateVoucher } from "./handler.ts";

Deno.test("owner-create-voucher: rejects non-POST requests", async () => {
  const req = new Request("https://localhost/owner-create-voucher", { method: "GET" });
  const res = await handleOwnerCreateVoucher(req);
  assertEquals(res.status, 405);
});

Deno.test("owner-create-voucher: rejects unauthenticated requests", async () => {
  const req = new Request("https://localhost/owner-create-voucher", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      venueId: "v1",
      code: "SUMMER20",
      type: "PERCENTAGE",
      value: 20,
    }),
  });
  const res = await handleOwnerCreateVoucher(req);
  assertEquals(res.status, 401);
});
