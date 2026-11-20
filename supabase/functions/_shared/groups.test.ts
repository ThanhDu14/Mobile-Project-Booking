// Test `getMembership`, `loadGroupForWrite`, `userSnapshot`, `anonymizeCommunityGroups` (M3) trên Firebase Emulator (Firestore 8080).
// Tự bỏ qua khi thiếu FIRESTORE_EMULATOR_HOST. Chạy:
//   cd supabase/functions
//   FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 FIREBASE_PROJECT_ID=demo-smashnow deno test --allow-env --allow-net _shared/groups.test.ts
// Không gọi clearEmulators (Emulator có thể đang dùng chung): mọi ID có tiền tố ngẫu nhiên của lần chạy.
import { assertEquals, assertRejects } from "jsr:@std/assert@1.0.19";
import { HttpError } from "./auth.ts";
import * as firestore from "./firestore.ts";
import { anonymizeCommunityGroups, attendeePath, getMembership, loadGroupForWrite, userSnapshot } from "./groups.ts";
import { assertGroupInvariant, makeTestDeps, seedGroup, seedPost } from "./groups_fixtures.ts";

Deno.env.set("FIREBASE_PROJECT_ID", Deno.env.get("FIREBASE_PROJECT_ID") ?? "demo-smashnow");

const ENABLED = !!Deno.env.get("FIRESTORE_EMULATOR_HOST");
const RUN = `g${crypto.randomUUID().slice(0, 8)}`;
const u = (name: string) => `${RUN}-${name}`;
const T = (name: string, fn: () => Promise<void>) => Deno.test({ name, ignore: !ENABLED, fn });

/** Chạy `fn` trong một transaction chỉ đọc (không ghi nên rollback). */
const inTx = <R>(fn: (tx: firestore.FirestoreTransaction) => Promise<R>): Promise<R> =>
  firestore.runTransaction(async (tx) => ({ result: await fn(tx), writes: [] }));

async function expectHttp(p: Promise<unknown>, status: number, code: string) {
  const e = await assertRejects(() => p, HttpError);
  assertEquals([e.status, e.code], [status, code]);
}

T("getMembership trả đúng vai trò hoặc null", async () => {
  const id = await seedGroup({
    owner: u("owner"),
    admins: [u("admin")],
    members: [u("member")],
    pending: [u("pending")],
  });
  await assertGroupInvariant(id);
  assertEquals(await inTx((tx) => getMembership(tx, id, u("owner"))), "OWNER");
  assertEquals(await inTx((tx) => getMembership(tx, id, u("admin"))), "ADMIN");
  assertEquals(await inTx((tx) => getMembership(tx, id, u("member"))), "MEMBER");
  assertEquals(await inTx((tx) => getMembership(tx, id, u("pending"))), null);
  assertEquals(await inTx((tx) => getMembership(tx, id, u("stranger"))), null);
});

T("loadGroupForWrite: ACTIVE trả dữ liệu; không có hoặc DELETED → 404", async () => {
  const id = await seedGroup({ owner: u("o1"), name: "CLB A" });
  const group = await inTx((tx) => loadGroupForWrite(tx, id));
  assertEquals(group.name, "CLB A");
  assertEquals(group.ownerId, u("o1"));
  await expectHttp(inTx((tx) => loadGroupForWrite(tx, `${RUN}-none`)), 404, "GROUP_NOT_FOUND");
  const deleted = await seedGroup({ owner: u("o2"), status: "DELETED" });
  await expectHttp(inTx((tx) => loadGroupForWrite(tx, deleted)), 404, "GROUP_NOT_FOUND");
  await expectHttp(inTx((tx) => loadGroupForWrite(tx, deleted, { allowLocked: true })), 404, "GROUP_NOT_FOUND");
});

T("loadGroupForWrite: LOCKED → 423 trừ khi allowLocked", async () => {
  const id = await seedGroup({ owner: u("o3"), status: "LOCKED" });
  await expectHttp(inTx((tx) => loadGroupForWrite(tx, id)), 423, "GROUP_LOCKED");
  await expectHttp(inTx((tx) => loadGroupForWrite(tx, id, { allowLocked: false })), 423, "GROUP_LOCKED");
  const group = await inTx((tx) => loadGroupForWrite(tx, id, { allowLocked: true }));
  assertEquals(group.status, "LOCKED");
});

T("userSnapshot đọc users/{uid}, thiếu thì null", async () => {
  await firestore.commit([
    firestore.createWrite(`users/${u("named")}`, {
      displayName: "Nam",
      avatarPath: "x/a.jpg",
      accountStatus: "ACTIVE",
    }),
  ]);
  assertEquals(await userSnapshot(firestore, u("named")), { displayName: "Nam", avatarPath: "x/a.jpg" });
  assertEquals(await inTx((tx) => userSnapshot(tx, u("ghost"))), { displayName: null, avatarPath: null });
});

T("seedPost: attendeeCount khớp attendees (assertGroupInvariant)", async () => {
  const id = await seedGroup({ owner: u("o4"), members: [u("m1"), u("m2")] });
  await seedPost(id, { type: "EVENT", authorId: u("o4"), attendees: [u("m1"), u("m2")], expectedCount: 2 });
  await seedPost(id, { type: "POST", authorId: u("m1"), hiddenBy: "GROUP" });
  await assertGroupInvariant(id);
});

// ---------- M3: anonymizeCommunityGroups ----------

T(
  "M3 chủ nhóm có ADMIN + 2 MEMBER: quyền sang ADMIN, bài ẩn danh, rời nhóm, bỏ sự kiện, xóa yêu cầu ở nhóm khác",
  async () => {
    const [owner, admin, m1, m2, other] = ["m3-owner", "m3-admin", "m3-m1", "m3-m2", "m3-other"].map(u);
    // Thứ tự joinedAt của seedGroup: owner, admin, m1, m2 → ADMIN được chọn dù m1 cũng tham gia sớm.
    const groupId = await seedGroup({ owner, admins: [admin], members: [m1, m2] });
    const mine = await seedPost(groupId, { authorId: owner });
    const theirs = await seedPost(groupId, { authorId: m1 });
    const upcoming = await seedPost(groupId, { type: "EVENT", authorId: m1, attendees: [owner, m2] });
    const past = await seedPost(groupId, {
      type: "EVENT",
      authorId: m1,
      eventAt: new Date(Date.now() - 3600_000),
      attendees: [owner],
    });
    const otherGroup = await seedGroup({ owner: other, visibility: "PRIVATE", pending: [owner, m2] });
    const { deps, hooks } = makeTestDeps();

    const result = await anonymizeCommunityGroups(owner, deps);
    assertEquals(result, { transferredGroups: [groupId], deletedGroups: [] });

    const group = await firestore.get(`groups/${groupId}`);
    assertEquals([group?.ownerId, group?.memberCount, group?.status], [admin, 3, "ACTIVE"]);
    assertEquals((await firestore.get(`groups/${groupId}/members/${admin}`))?.role, "OWNER");
    assertEquals(await firestore.get(`groups/${groupId}/members/${owner}`), null);
    const post = await firestore.get(`groups/${groupId}/posts/${mine}`);
    assertEquals([post?.authorSnapshot, post?.authorDeleted], [null, true]);
    assertEquals((await firestore.get(`groups/${groupId}/posts/${theirs}`))?.authorDeleted, false);
    // Sự kiện sắp tới: bỏ owner, giữ m2; sự kiện đã qua giữ nguyên.
    assertEquals(await firestore.get(attendeePath(groupId, upcoming, owner)), null);
    assertEquals((await firestore.get(attendeePath(groupId, upcoming, m2)))?.userId, m2);
    assertEquals((await firestore.get(`groups/${groupId}/posts/${upcoming}`))?.attendeeCount, 1);
    assertEquals((await firestore.get(attendeePath(groupId, past, owner)))?.userId, owner);
    // Yêu cầu của owner ở nhóm khác bị xóa, của m2 giữ lại.
    assertEquals(await firestore.get(`groups/${otherGroup}/joinRequests/${owner}`), null);
    assertEquals((await firestore.get(`groups/${otherGroup}/joinRequests/${m2}`))?.userId, m2);
    assertEquals((await firestore.get(`groups/${otherGroup}`))?.pendingRequestCount, 1);
    assertEquals(hooks.calls.filter((c) => c.groupId === groupId).map((c) => c.change), [{ removed: [owner] }]);
    await assertGroupInvariant(groupId);
    await assertGroupInvariant(otherGroup);

    // Idempotent: gọi lại không lỗi, không đổi gì.
    assertEquals(await anonymizeCommunityGroups(owner, deps), { transferredGroups: [], deletedGroups: [] });
    assertEquals((await firestore.get(`groups/${groupId}`))?.memberCount, 3);
    await assertGroupInvariant(groupId);
  },
);

T("M3 chủ nhóm không có ADMIN: quyền sang MEMBER tham gia sớm nhất; thành viên thường chỉ rời nhóm", async () => {
  const [owner, m1, m2] = ["m3b-owner", "m3b-m1", "m3b-m2"].map(u);
  const groupId = await seedGroup({ owner, members: [m1, m2], status: "LOCKED" });
  const { deps } = makeTestDeps();
  // Thành viên thường (m2) xóa tài khoản trước: chỉ rời nhóm (nhóm LOCKED vẫn rời được).
  assertEquals(await anonymizeCommunityGroups(m2, deps), { transferredGroups: [], deletedGroups: [] });
  assertEquals((await firestore.get(`groups/${groupId}`))?.memberCount, 2);
  assertEquals(await anonymizeCommunityGroups(owner, deps), { transferredGroups: [groupId], deletedGroups: [] });
  assertEquals((await firestore.get(`groups/${groupId}`))?.ownerId, m1);
  assertEquals((await firestore.get(`groups/${groupId}/members/${m1}`))?.role, "OWNER");
  await assertGroupInvariant(groupId);
});

T("M3 chủ nhóm một mình → nhóm bị xóa sạch (bài, ảnh, yêu cầu), hook deleted", async () => {
  const [owner, pending] = ["m3c-owner", "m3c-pending"].map(u);
  const groupId = await seedGroup({ owner, visibility: "PRIVATE", pending: [pending] });
  const postId = await seedPost(groupId, { type: "EVENT", authorId: owner, attendees: [owner] });
  const { deps, storage, hooks } = makeTestDeps();
  storage.put("group-media", `${groupId}/${postId}/0.jpg`);

  assertEquals(await anonymizeCommunityGroups(owner, deps), { transferredGroups: [], deletedGroups: [groupId] });
  assertEquals(await firestore.get(`groups/${groupId}`), null);
  assertEquals(await firestore.get(`groups/${groupId}/posts/${postId}`), null);
  assertEquals((await firestore.query("members", [], { parent: `groups/${groupId}` })).length, 0);
  assertEquals((await firestore.query("joinRequests", [], { parent: `groups/${groupId}` })).length, 0);
  assertEquals(storage.has("group-media", `${groupId}/${postId}/0.jpg`), false);
  assertEquals(hooks.calls.filter((c) => c.groupId === groupId).map((c) => c.change), [{ deleted: true }]);
  assertEquals(await anonymizeCommunityGroups(owner, deps), { transferredGroups: [], deletedGroups: [] });
});
