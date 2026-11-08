import { assertEquals } from "jsr:@std/assert@1.0.19";
import { handlePlayerCancelBooking } from "./handler.ts";

Deno.test("player-cancel-booking: rejects non-POST requests", async () => {
  const res = await handlePlayerCancelBooking(new Request("https://localhost/player-cancel-booking", { method: "GET" }));
  assertEquals(res.status, 405);
});

Deno.test("player-cancel-booking: rejects unauthenticated requests", async () => {
  const req = new Request("https://localhost/player-cancel-booking", {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ bookingId: "b1" }),
  });
  assertEquals((await handlePlayerCancelBooking(req)).status, 401);
});

Deno.test("player-cancel-booking: rejects invalid token before reading", async () => {
  let reads = 0;
  const req = new Request("https://localhost/player-cancel-booking", {
    method: "POST",
    headers: { Authorization: "Bearer invalid", "Content-Type": "application/json" },
    body: JSON.stringify({ bookingId: "b1" }),
  });
  const response = await handlePlayerCancelBooking(req, { firestore: { get: async () => { reads++; return null; } } });
  assertEquals(response.status, 401);
  assertEquals(reads, 0);
});
