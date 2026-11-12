// Test `getMembership`, `loadGroupForWrite`, `userSnapshot` trên Firebase Emulator (Firestore 8080).
// Tự bỏ qua khi thiếu FIRESTORE_EMULATOR_HOST. Chạy:
//   cd supabase/functions
//   FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 FIREBASE_PROJECT_ID=demo-smashnow deno test --allow-env --allow-net _shared/groups.test.ts
// Không gọi clearEmulators (Emulator có thể đang dùng chung): mọi ID có tiền tố ngẫu nhiên của lần chạy.
import { assertEquals, assertRejects } from "jsr:@std/assert@1.0.19";
import { HttpError } from "./auth.ts";
import * as firestore from "./firestore.ts";
import { getMembership, loadGroupForWrite, userSnapshot } from "./groups.ts";
import { assertGroupInvariant, seedGroup, seedPost } from "./groups_fixtures.ts";

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
