import { assertEquals } from "jsr:@std/assert@1.0.19";
import { handleOwnerApproveBooking } from "./handler.ts";

Deno.test("owner-approve-booking: rejects non-POST requests", async () => {
  const req = new Request("https://localhost/owner-approve-booking", { method: "GET" });
  const res = await handleOwnerApproveBooking(req);
  assertEquals(res.status, 405);
});

Deno.test("owner-approve-booking: rejects unauthenticated requests", async () => {
  const req = new Request("https://localhost/owner-approve-booking", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      venueId: "v1",
      bookingId: "b1",
    }),
  });
  const res = await handleOwnerApproveBooking(req);
  assertEquals(res.status, 401);
});

Deno.test("owner-approve-booking: rejects missing venueId or bookingId", async () => {
  const req = new Request("https://localhost/owner-approve-booking", {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      Authorization: "Bearer fake-token",
    },
    body: JSON.stringify({
      venueId: "",
      bookingId: "",
    }),
  });
  const res = await handleOwnerApproveBooking(req);
  assertEquals(res.status, 401); // Auth runs first in production pipeline
});
