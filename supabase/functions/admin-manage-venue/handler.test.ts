import { assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import { handleAdminManageVenue, validateVenueStatus } from "./handler.ts";

Deno.test("admin-manage-venue: rejects non-POST", async () => {
  const req = new Request("https://localhost/admin-manage-venue", { method: "GET" });
  const res = await handleAdminManageVenue(req);
  assertEquals(res.status, 405);
});

Deno.test("admin-manage-venue: rejects unauthenticated request", async () => {
  const req = new Request("https://localhost/admin-manage-venue", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ venueId: "venue-1", status: "LOCKED" }),
  });
  const res = await handleAdminManageVenue(req);
  assertEquals(res.status, 401);
});

Deno.test("admin-manage-venue: validateVenueStatus accepts only LOCKED, ACTIVE, HIDDEN", () => {
  assertEquals(validateVenueStatus("LOCKED"), "LOCKED");
  assertEquals(validateVenueStatus("ACTIVE"), "ACTIVE");
  assertEquals(validateVenueStatus("HIDDEN"), "HIDDEN");
  assertEquals(validateVenueStatus("  locked  "), "LOCKED");
  assertThrows(() => validateVenueStatus("DELETED"));
  assertThrows(() => validateVenueStatus("UNKNOWN"));
  assertThrows(() => validateVenueStatus(""));
});
