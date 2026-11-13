import { assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import { handleAdminBroadcastNotification, validateBroadcastContent } from "./handler.ts";
import { adminFixture } from "../_shared/admin-testing.ts";

Deno.test("R120-07: retry after partial batch sends once to all explicitly active document IDs", () => adminFixture(async ({records, request, failCommit}) => {
  const fs = await import("../_shared/firestore.ts");
  const users = Array.from({length: 601}, (_, i) => ({ __documentId: `u${i}`, accountStatus: "ACTIVE" }));
  const deps = { firestore: { ...fs, list: async () => [...users, {__documentId:"locked",accountStatus:"LOCKED"},{__documentId:"unknown"}] } };
  const body = {title:"Maintenance",body:"Details",requestId:"12345678-abcd-abcd-abcd-123456789012"};
  failCommit(2);
  const first = await handleAdminBroadcastNotification(request(body), deps);
  assertEquals(first.status >= 500, true);
  await first.body?.cancel();
  const existing = [...records.keys()].find((p) => p.includes("/notifications/"))!;
  records.set(existing, {...records.get(existing), isRead:true});
  const retry = await handleAdminBroadcastNotification(request(body), deps);
  assertEquals(retry.status, 200);
  assertEquals((await retry.json()).count, 601);
  assertEquals([...records.keys()].filter((p) => p.includes("/notifications/")).length, 601);
  assertEquals(records.get(existing)?.isRead, true);
}));

Deno.test("admin-broadcast-notification: rejects non-POST", async () => {
  const req = new Request("https://localhost/admin-broadcast-notification", { method: "GET" });
  const res = await handleAdminBroadcastNotification(req);
  assertEquals(res.status, 405);
});

Deno.test("admin-broadcast-notification: rejects unauthenticated request", async () => {
  const req = new Request("https://localhost/admin-broadcast-notification", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ title: "Bảo trì", body: "Hệ thống bảo trì lúc 0h" }),
  });
  const res = await handleAdminBroadcastNotification(req);
  assertEquals(res.status, 401);
});

Deno.test("admin-broadcast-notification: validateBroadcastContent checks non-empty title and body", () => {
  const valid = validateBroadcastContent("  Thông báo bảo trì  ", "  Chi tiết nội dung  ");
  assertEquals(valid.title, "Thông báo bảo trì");
  assertEquals(valid.body, "Chi tiết nội dung");

  assertThrows(() => validateBroadcastContent("", "Nội dung"));
  assertThrows(() => validateBroadcastContent("   ", "Nội dung"));
  assertThrows(() => validateBroadcastContent("Tiêu đề", ""));
  assertThrows(() => validateBroadcastContent("Tiêu đề", "   "));
});
