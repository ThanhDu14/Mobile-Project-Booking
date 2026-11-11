import { assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import { handleAdminRevokeOwner, validateRevokeReason } from "./handler.ts";

Deno.test("admin-revoke-owner: rejects non-POST", async () => {
  const req = new Request("https://localhost/admin-revoke-owner", { method: "GET" });
  const res = await handleAdminRevokeOwner(req);
  assertEquals(res.status, 405);
});

Deno.test("admin-revoke-owner: rejects unauthenticated request", async () => {
  const req = new Request("https://localhost/admin-revoke-owner", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ uid: "user-1", reason: "Vi pham quy che" }),
  });
  const res = await handleAdminRevokeOwner(req);
  assertEquals(res.status, 401);
});

Deno.test("admin-revoke-owner: validateRevokeReason trims whitespace and rejects empty", () => {
  assertEquals(validateRevokeReason("  Ly do vi pham nghiem trong  "), "Ly do vi pham nghiem trong");
  assertThrows(() => validateRevokeReason("   "));
  assertThrows(() => validateRevokeReason(""));
  assertThrows(() => validateRevokeReason(null));
});
