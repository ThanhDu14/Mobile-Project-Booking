import { assertEquals } from "jsr:@std/assert@1.0.19";
import { handleOwnerRejectBooking } from "./handler.ts";

Deno.test("owner-reject-booking: rejects non-POST requests", async () => {
  const req = new Request("https://localhost/owner-reject-booking", { method: "GET" });
  const res = await handleOwnerRejectBooking(req);
  assertEquals(res.status, 405);
});

Deno.test("owner-reject-booking: rejects unauthenticated requests", async () => {
  const req = new Request("https://localhost/owner-reject-booking", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      venueId: "v1",
      bookingId: "b1",
      reason: "Hết sân đột xuất",
    }),
  });
  const res = await handleOwnerRejectBooking(req);
  assertEquals(res.status, 401);
});

Deno.test("owner-reject-booking: rejects empty reason", async () => {
  const req = new Request("https://localhost/owner-reject-booking", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: "Bearer fake-token",
    },
    body: JSON.stringify({
      venueId: "v1",
      bookingId: "b1",
      reason: "",
    }),
  });
  const res = await handleOwnerRejectBooking(req);
  assertEquals(res.status, 401);
});
