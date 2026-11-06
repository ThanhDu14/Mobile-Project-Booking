import { assertEquals, assertThrows } from "jsr:@std/assert@1.0.19";
import { handleAdminBroadcastNotification, validateBroadcastContent } from "./handler.ts";

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
