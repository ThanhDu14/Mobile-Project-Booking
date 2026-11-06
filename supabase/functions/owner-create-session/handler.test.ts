import { assertEquals } from "jsr:@std/assert@1.0.19";
import { handleOwnerCreateSession } from "./handler.ts";

Deno.test("owner-create-session: rejects non-POST requests", async () => {
  const req = new Request("https://localhost/owner-create-session", { method: "GET" });
  const res = await handleOwnerCreateSession(req);
  assertEquals(res.status, 405);
});

Deno.test("owner-create-session: rejects unauthenticated requests", async () => {
  const req = new Request("https://localhost/owner-create-session", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      venueId: "v1",
      courtId: "c1",
      date: "20261110",
      startTime: "0800",
      endTime: "1000",
      capacity: 8,
      pricePerPerson: 50000,
    }),
  });
  const res = await handleOwnerCreateSession(req);
  assertEquals(res.status, 401);
});
