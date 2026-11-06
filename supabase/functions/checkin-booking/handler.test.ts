import { assertEquals } from "jsr:@std/assert@1.0.19";
import { handleCheckinBooking } from "./handler.ts";

Deno.test("checkin-booking: rejects non-POST requests", async () => {
  const req = new Request("https://localhost/checkin-booking", { method: "GET" });
  const res = await handleCheckinBooking(req);
  assertEquals(res.status, 405);
});

Deno.test("checkin-booking: rejects unauthenticated requests", async () => {
  const req = new Request("https://localhost/checkin-booking", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      venueId: "v1",
      bookingId: "b1",
      confirmPayment: true,
    }),
  });
  const res = await handleCheckinBooking(req);
  assertEquals(res.status, 401);
});
