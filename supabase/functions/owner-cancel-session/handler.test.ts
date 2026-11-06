import { assertEquals } from "jsr:@std/assert@1.0.19";
import { handleOwnerCancelSession } from "./handler.ts";

Deno.test("owner-cancel-session: rejects non-POST requests", async () => {
  const req = new Request("https://localhost/owner-cancel-session", { method: "GET" });
  const res = await handleOwnerCancelSession(req);
  assertEquals(res.status, 405);
});

Deno.test("owner-cancel-session: rejects unauthenticated requests", async () => {
  const req = new Request("https://localhost/owner-cancel-session", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      venueId: "v1",
      sessionId: "s1",
    }),
  });
  const res = await handleOwnerCancelSession(req);
  assertEquals(res.status, 401);
});
