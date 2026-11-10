import { assertEquals } from "jsr:@std/assert@1.0.19";
import { handleAdminModerateReport, validateActionTarget } from "./handler.ts";

Deno.test("admin-moderate-report: rejects non-POST", async () => {
  const req = new Request("https://localhost/admin-moderate-report", { method: "GET" });
  const res = await handleAdminModerateReport(req);
  assertEquals(res.status, 405);
});

Deno.test("admin-moderate-report: rejects unauthenticated requests", async () => {
  const req = new Request("https://localhost/admin-moderate-report", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      reportId: "rep-123",
      action: "LOCK_VENUE",
    }),
  });
  const res = await handleAdminModerateReport(req);
  assertEquals(res.status, 401);
});

Deno.test("admin-moderate-report: validateActionTarget correctly maps target types", () => {
  assertEquals(validateActionTarget("LOCK_VENUE", "VENUE"), null);
  assertEquals(validateActionTarget("LOCK_USER", "USER"), null);
  assertEquals(validateActionTarget("HIDE_CONTENT", "POST"), null);
  assertEquals(validateActionTarget("HIDE_CONTENT", "MESSAGE"), null);
  assertEquals(validateActionTarget("HIDE_CONTENT", "REVIEW"), null);
  assertEquals(validateActionTarget("NONE", "VENUE"), null);

  // Mismatched actions
  assertEquals(validateActionTarget("HIDE_CONTENT", "VENUE") !== null, true);
  assertEquals(validateActionTarget("LOCK_USER", "VENUE") !== null, true);
  assertEquals(validateActionTarget("LOCK_VENUE", "USER") !== null, true);
  assertEquals(validateActionTarget("HIDE_CONTENT", "DROP_IN") !== null, true);
});
