import { assertEquals } from "jsr:@std/assert@1.0.19";
import { handleAdminModerateReport, validateActionTarget } from "./handler.ts";
import { adminFixture } from "../_shared/admin-testing.ts";
import { handleAdminManageGroup } from "../admin-manage-group/handler.ts";

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
  assertEquals(validateActionTarget("LOCK_GROUP", "GROUP"), null);
  assertEquals(validateActionTarget("LOCK_GROUP", "POST"), null);
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

Deno.test("moderation persists system post hiding, clears message text, locks group atomically and retries safely", async () => {
  await adminFixture(async ({ records, request }) => {
    for (const [type, path, action] of [
      ["POST", "groups/g/posts/p", "HIDE_CONTENT"],
      ["MESSAGE", "chats/c/messages/m", "HIDE_CONTENT"],
      ["REVIEW", "reviews/r", "HIDE_CONTENT"],
      ["GROUP", "groups/g", "LOCK_GROUP"],
    ]) {
      records.set(path, { status: "ACTIVE", text: "reported", content: "reported", hiddenByUid: "previous" });
      records.set(`reports/${type}`, { status: "OPEN", targetType: type, targetPath: path });
      const deps = { firestore: await import("../_shared/firestore.ts"), setUserDisabled: async () => {} };
      assertEquals((await handleAdminModerateReport(request({ reportId: type, action }), deps)).status, 200);
      const target = records.get(path)!;
      assertEquals(target.status, type === "GROUP" ? "LOCKED" : "HIDDEN");
      if (type === "POST") assertEquals([target.hiddenBy, target.hiddenByUid], ["SYSTEM", null]);
      if (type === "MESSAGE") assertEquals(target.text, "");
      assertEquals(records.get(`reports/${type}`)!.status, "RESOLVED");
      const retry = await handleAdminModerateReport(request({ reportId: type, action }), deps);
      assertEquals((await retry.json()).alreadyCommitted, true);
    }
    assertEquals((await handleAdminManageGroup(request({ groupId: "g", status: "ACTIVE" }))).status, 200);
    assertEquals(records.get("groups/g")!.status, "ACTIVE");
    records.set("groups/g", { status: "DELETED" });
    assertEquals((await handleAdminManageGroup(request({ groupId: "g", status: "ACTIVE" }))).status, 409);
  });
});

Deno.test("moderation rejects a target path belonging to another collection without resolving report", async () => {
  await adminFixture(async ({ records, request }) => {
    records.set("groups/g", { status: "ACTIVE" });
    records.set("reports/bad", { status: "OPEN", targetType: "POST", targetPath: "groups/g" });
    const res = await handleAdminModerateReport(request({ reportId: "bad", action: "HIDE_CONTENT" }));
    assertEquals(res.status, 400);
    assertEquals(records.get("groups/g")!.status, "ACTIVE");
    assertEquals(records.get("reports/bad")!.status, "OPEN");
  });
});

Deno.test("LOCK_GROUP on a post report locks the parent group and preserves the post", async () => {
  await adminFixture(async ({ records, request }) => {
    records.set("groups/g", { status: "ACTIVE" });
    records.set("groups/g/posts/p", { status: "VISIBLE" });
    records.set("reports/post", { status: "OPEN", targetType: "POST", targetPath: "groups/g/posts/p" });
    assertEquals((await handleAdminModerateReport(request({ reportId: "post", action: "LOCK_GROUP" }))).status, 200);
    assertEquals(records.get("groups/g")!.status, "LOCKED");
    assertEquals(records.get("groups/g/posts/p")!.status, "VISIBLE");
    assertEquals(records.get("reports/post")!.action, "LOCK_GROUP");
  });
});
