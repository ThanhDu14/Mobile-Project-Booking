import { assertEquals } from "jsr:@std/assert@1.0.19";
import { handleAdminManageUser, sanitizeUserRecord } from "./handler.ts";

Deno.test("admin-manage-user: rejects non-POST", async () => {
  const req = new Request("https://localhost/admin-manage-user", { method: "GET" });
  const res = await handleAdminManageUser(req);
  assertEquals(res.status, 405);
});

Deno.test("admin-manage-user: rejects unauthenticated request", async () => {
  const req = new Request("https://localhost/admin-manage-user", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ action: "LOOKUP", query: "0901234567" }),
  });
  const res = await handleAdminManageUser(req);
  assertEquals(res.status, 401);
});

Deno.test("admin-manage-user: sanitizeUserRecord filters sensitive fields", () => {
  const raw = {
    displayName: "Nguyen Van A",
    phone: "0901234567",
    role: "PLAYER",
    ownerStatus: "NONE",
    accountStatus: "ACTIVE",
    favoriteDistrictIds: ["d1", "d2"],
    fcmTokens: ["token1"],
    internalSecret: "do-not-leak",
  };
  const sanitized = sanitizeUserRecord("uid123", raw);
  assertEquals(sanitized?.uid, "uid123");
  assertEquals(sanitized?.fullName, "Nguyen Van A");
  assertEquals(sanitized?.phone, "0901234567");
  assertEquals(sanitized?.role, "PLAYER");
  assertEquals((sanitized as Record<string, unknown>)?.internalSecret, undefined);
  assertEquals((sanitized as Record<string, unknown>)?.fcmTokens, undefined);
});
