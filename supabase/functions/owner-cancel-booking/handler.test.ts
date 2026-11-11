import { assertEquals } from "jsr:@std/assert@1.0.19";
import { handleOwnerCancelBooking } from "./handler.ts";

Deno.test("owner-cancel-booking: rejects non-POST requests", async () => {
  const req = new Request("https://localhost/owner-cancel-booking", { method: "GET" });
  const res = await handleOwnerCancelBooking(req);
  assertEquals(res.status, 405);
});

Deno.test("owner-cancel-booking: rejects unauthenticated requests", async () => {
  const req = new Request("https://localhost/owner-cancel-booking", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      venueId: "v1",
      bookingId: "b1",
      reason: "Bảo trì sân",
    }),
  });
  const res = await handleOwnerCancelBooking(req);
  assertEquals(res.status, 401);
});

Deno.test("owner-cancel-booking: rejects empty reason", async () => {
  const req = new Request("https://localhost/owner-cancel-booking", {
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
  const res = await handleOwnerCancelBooking(req);
  assertEquals(res.status, 401);
});
