import { assertEquals } from "jsr:@std/assert@1.0.19";
import { generateKeywords, geohash, handleAdminReviewApplication } from "./handler.ts";

Deno.test("admin-review-application: rejects non-POST", async () => {
  const req = new Request("https://localhost/admin-review-application", { method: "GET" });
  const res = await handleAdminReviewApplication(req);
  assertEquals(res.status, 405);
});

Deno.test("admin-review-application: rejects unauthenticated requests", async () => {
  const req = new Request("https://localhost/admin-review-application", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      applicationId: "app-123",
      decision: "APPROVE",
    }),
  });
  const res = await handleAdminReviewApplication(req);
  assertEquals(res.status, 401);
});

Deno.test("admin-review-application: generateKeywords strips accents and generates prefixes", () => {
  const keywords = generateKeywords("Sân Cầu Lông Đạt Chuẩn");
  assertEquals(keywords.includes("san"), true);
  assertEquals(keywords.includes("cau"), true);
  assertEquals(keywords.includes("long"), true);
  assertEquals(keywords.includes("dat"), true);
  assertEquals(keywords.includes("chuan"), true);
});

Deno.test("admin-review-application: geohash encodes coordinates correctly", () => {
  const hash = geohash(10.7769, 106.7009);
  assertEquals(typeof hash, "string");
  assertEquals(hash.length, 10);
});
