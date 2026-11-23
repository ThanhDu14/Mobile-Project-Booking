import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { adminFixture } from "./_shared/admin-testing.ts";
import * as fs from "./_shared/firestore.ts";
import { handleAdminReviewApplication } from "./admin-review-application/handler.ts";
import { handleAdminRevokeOwner } from "./admin-revoke-owner/handler.ts";
import { handleAdminBroadcastNotification } from "./admin-broadcast-notification/handler.ts";

// Exercise actual handlers and the REST encoder/decoder. An ISO string would remain a string here.
for (const decision of ["APPROVE", "REJECT"] as const) {
  Deno.test(`notification Timestamp: application ${decision} persists native date and retry preserves it`, () => adminFixture(async ({ records, request }) => {
    records.set("users/player", { role: "PLAYER", ownerStatus: "PENDING", accountStatus: "ACTIVE" });
    records.set("ownerApplications/a", { uid: "player", status: "PENDING", submittedAt: "2026-11-20T01:00:00Z", venueDraft: { name: "Venue", photoPaths: [] } });
    const deps = { firestore: fs, mergeClaims: async () => {} };
    const body = { applicationId: "a", decision, rejectReason: "Missing evidence", expectedSubmittedAt: "2026-11-20T01:00:00Z" };
    const before = Date.now();
    const response = await handleAdminReviewApplication(request(body), deps);
    assertEquals(response.status, 200); await response.body?.cancel();
    const notifications = [...records.entries()].filter(([path]) => path.includes("/notifications/"));
    assertEquals(notifications.length, 1);
    const [path, notification] = notifications[0];
    assert(notification.createdAt instanceof Date);
    assert(notification.createdAt.getTime() >= before && notification.createdAt.getTime() <= Date.now());
    assertEquals(notification.type, decision === "APPROVE" ? "OWNER_APPROVED" : "OWNER_REJECTED");
    const retry = await handleAdminReviewApplication(request(body), deps);
    assertEquals(retry.status, 200); await retry.body?.cancel();
    assertEquals(records.get(path)?.createdAt, notification.createdAt);
    assertEquals([...records.keys()].filter(p => p.includes("/notifications/")).length, 1);
    const jobs = [...records.entries()].filter(([p]) => p.startsWith("notificationDeliveries/"));
    assertEquals(jobs.length, 1);
    assertEquals(jobs[0][1].notificationId, path.split("/").pop());
  }));
}

Deno.test("notification Timestamp: revoke creates one dated inbox entry across retry", () => adminFixture(async ({ records, request }) => {
  records.set("users/player", { role: "OWNER", ownerStatus: "APPROVED", accountStatus: "ACTIVE" });
  const deps = { firestore: fs, mergeClaims: async () => {} };
  for (let attempt = 0; attempt < 2; attempt++) {
    const response = await handleAdminRevokeOwner(request({ uid: "player", reason: "Policy" }), deps);
    assertEquals(response.status, 200); await response.body?.cancel();
  }
  const notifications = [...records.entries()].filter(([path]) => path.includes("/notifications/"));
  assertEquals(notifications.length, 1);
  assertEquals(notifications[0][1].type, "OWNER_REVOKED");
  assert(notifications[0][1].createdAt instanceof Date);
}));

Deno.test("notification Timestamp: SYSTEM fanout dates every recipient and preserves read state on retry", () => adminFixture(async ({ records, request }) => {
  for (const uid of ["a", "b"]) records.set(`users/${uid}`, { accountStatus: "ACTIVE" });
  const deps = { firestore: { ...fs, list: async () => [
    ...["a", "b"].map(__documentId => ({ __documentId, accountStatus: "ACTIVE" })),
    { __documentId: "deleting", accountStatus: "ACTIVE", deletionRequestedAt: new Date() },
    { __documentId: "locked", accountStatus: "LOCKED" },
    { __documentId: "unspecified" },
  ] } };
  const body = { title: "Maintenance", body: "Details", requestId: "notification-timestamp-regression" };
  const first = await handleAdminBroadcastNotification(request(body), deps);
  assertEquals(first.status, 200); await first.body?.cancel();
  const entries = [...records.entries()].filter(([path]) => path.includes("/notifications/"));
  assertEquals(entries.length, 2);
  for (const [, doc] of entries) assert(doc.createdAt instanceof Date);
  entries[0][1].isRead = true;
  const retry = await handleAdminBroadcastNotification(request(body), deps);
  assertEquals(retry.status, 200); await retry.body?.cancel();
  assertEquals(records.get(entries[0][0])?.isRead, true);
  assertEquals(records.get(entries[0][0])?.createdAt, entries[0][1].createdAt);
}));
