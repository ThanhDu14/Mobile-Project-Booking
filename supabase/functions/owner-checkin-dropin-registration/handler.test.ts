import { assertEquals } from "jsr:@std/assert@1.0.19";
import { handleOwnerCheckinDropIn } from "./handler.ts";

Deno.test("owner-checkin-dropin: rejects non-POST requests", async () => {
  const req = new Request("https://localhost/owner-checkin-dropin-registration", { method: "GET" });
  const res = await handleOwnerCheckinDropIn(req);
  assertEquals(res.status, 405);
});

Deno.test("owner-checkin-dropin: rejects unauthenticated requests", async () => {
  const req = new Request("https://localhost/owner-checkin-dropin-registration", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      venueId: "v1",
      sessionId: "s1",
      registrationId: "r1",
    }),
  });
  const res = await handleOwnerCheckinDropIn(req);
  assertEquals(res.status, 401);
});
