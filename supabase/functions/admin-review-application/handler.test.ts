import { assertEquals } from "jsr:@std/assert@1.0.19";
import { generateKeywords, geohash, handleAdminReviewApplication } from "./handler.ts";
import { adminFixture } from "../_shared/admin-testing.ts";

Deno.test("R120-05: another current admin can recover claims after reopening a decided application", () => adminFixture(async ({records,request}) => {
  records.set("users/player",{role:"OWNER",ownerStatus:"APPROVED"});
  records.set("ownerApplications/a",{uid:"player",status:"APPROVED",reviewedBy:"previous-admin"});
  const fs=await import("../_shared/firestore.ts");
  let repaired:unknown;
  const result=await handleAdminReviewApplication(request({applicationId:"a",action:"RECOVER_CLAIMS"}),{firestore:fs,mergeClaims:async(_,claims)=>{repaired=claims.appRole;}});
  assertEquals(result.status,200);await result.body?.cancel();
  assertEquals(repaired,"OWNER");
  assertEquals(records.size,3);
}));

Deno.test("R120-05: revoked admin claim cannot mutate or recover another account", () => adminFixture(async ({records,request}) => {
  records.set("users/admin",{role:"PLAYER",accountStatus:"ACTIVE"});
  const result=await handleAdminReviewApplication(request({applicationId:"a",decision:"APPROVE"}));
  assertEquals(result.status,403);
  await result.body?.cancel();
}));

Deno.test("R120-04/05: committed approve retry repairs failed claim without duplicate effects", () => adminFixture(async ({records, request}) => {
  records.set("users/player", { role: "PLAYER", ownerStatus: "PENDING" });
  records.set("ownerApplications/a", { uid: "player", status: "PENDING", submittedAt: "2026-11-01T00:00:00Z", venueDraft: { name: "Test", location: { latitude: 10, longitude: 106 } } });
  let fail = true;
  const fs = await import("../_shared/firestore.ts");
  const deps = { firestore: fs, mergeClaims: async () => { if (fail) throw new Error("Auth unavailable"); } };
  const body = { applicationId: "a", decision: "APPROVE", expectedSubmittedAt: "2026-11-01T00:00:00Z" };
  const first = await handleAdminReviewApplication(request(body), deps);
  assertEquals(first.status, 503);
  await first.body?.cancel();
  const count = records.size;
  fail = false;
  const retry = await handleAdminReviewApplication(request(body), deps);
  assertEquals(retry.status, 200);
  assertEquals((await retry.json()).status, "APPROVED");
  assertEquals(records.size, count);
}));

Deno.test("R120-04: stale submitted version cannot approve a resubmission", () => adminFixture(async ({records, request}) => {
  records.set("users/player", { role: "PLAYER", ownerStatus: "PENDING" });
  records.set("ownerApplications/a", {uid:"player",status:"PENDING",submittedAt:"2026-11-02T00:00:00Z"});
  const fs = await import("../_shared/firestore.ts");
  let claims = 0;
  const result = await handleAdminReviewApplication(request({applicationId:"a",decision:"APPROVE",expectedSubmittedAt:"2026-11-01T00:00:00Z"}), {firestore:fs,mergeClaims:async()=>{claims++;}});
  assertEquals(result.status,409);
  await result.body?.cancel();
  assertEquals(claims,0);
  assertEquals(records.get("ownerApplications/a")?.status,"PENDING");
}));

Deno.test("R120-04: missing snapshot version cannot decide a pending application", () => adminFixture(async ({records,request}) => {
  records.set("users/player",{role:"PLAYER",ownerStatus:"PENDING"});
  records.set("ownerApplications/a",{uid:"player",status:"PENDING",submittedAt:"2026-11-01T00:00:00Z"});
  const fs=await import("../_shared/firestore.ts");
  const result=await handleAdminReviewApplication(request({applicationId:"a",decision:"REJECT",rejectReason:"Missing"}),{firestore:fs,mergeClaims:async()=>{}});
  assertEquals(result.status,400);
  await result.body?.cancel();
  assertEquals(records.get("ownerApplications/a")?.status,"PENDING");
}));

Deno.test("R120-05: retry after owner revocation reconciles PLAYER, never old OWNER", () => adminFixture(async ({records,request}) => {
  records.set("users/player", {role:"PLAYER",ownerStatus:"NONE"});
  records.set("ownerApplications/a", {uid:"player",status:"APPROVED",reviewedBy:"admin"});
  const fs=await import("../_shared/firestore.ts");
  let role: unknown;
  const result=await handleAdminReviewApplication(request({applicationId:"a",decision:"APPROVE"}),{firestore:fs,mergeClaims:async(_,claims)=>{role=claims.appRole;}});
  assertEquals(result.status,200);
  await result.body?.cancel();
  assertEquals(role,"PLAYER");
}));

Deno.test("R120-04: REJECT retry maps REJECTED, opposing decision conflicts", () => adminFixture(async ({records, request}) => {
  records.set("users/player", { role: "PLAYER", ownerStatus: "REJECTED" });
  records.set("ownerApplications/a", { uid: "player", status: "REJECTED", rejectReason: "Missing", reviewedBy: "admin" });
  const fs = await import("../_shared/firestore.ts");
  const deps = { firestore: fs, mergeClaims: async () => {} };
  const retry = await handleAdminReviewApplication(request({applicationId:"a",decision:"REJECT",rejectReason:"Missing"}), deps);
  assertEquals(retry.status, 200);
  assertEquals((await retry.json()).status, "REJECTED");
  const stale = await handleAdminReviewApplication(request({applicationId:"a",decision:"APPROVE"}), deps);
  assertEquals(stale.status, 409);
  await stale.body?.cancel();
}));

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
