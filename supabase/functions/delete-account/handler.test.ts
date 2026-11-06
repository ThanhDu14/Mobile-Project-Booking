// T075: X1–X15 của contracts/edge-functions-account.md §5 trên Auth + Firestore Emulator; Storage là server giả.
// `deno task test:flaky` chạy X7 và X10 10 lần liên tiếp (biến FLAKY_REPEAT).
import { assert, assertEquals, assertExists } from "jsr:@std/assert@1.0.19";
import { requireUser } from "../_shared/auth.ts";
import { type DeletionDeps, defaultDeletionDeps, supabaseStorageAdmin } from "../_shared/account-deletion.ts";
import { commit, deleteWrite, get } from "../_shared/firestore.ts";
import { bearer, clearEmulators, emulatorToken, signInEmail, signUpEmail } from "../_shared/testing.ts";
import { handleSweeper } from "../delete-account-sweeper/handler.ts";
import { handleDeleteAccount } from "./handler.ts";
import {
  bookingWrites,
  docsIn,
  type FakeHelpers,
  fakeHelpers,
  type FakeStorage,
  fakeStorageServer,
  seedBooking,
  seedFavorites,
  seedRegistration,
  seedReview,
  seedSession,
  seedUser,
  seedVenue,
  storageFor,
} from "./test_fakes.ts";

const REPEAT = Number(Deno.env.get("FLAKY_REPEAT") ?? "1");
const CRON_SECRET = "cron-secret-test";

interface Ctx {
  uid: string;
  email: string;
  fakes: FakeHelpers;
  storage: FakeStorage;
  deps: DeletionDeps;
}

let counter = 0;

/** Xóa Emulator, tạo người dùng Auth thật + `users/{uid}`, Storage giả có 2 ảnh của người này và 1 ảnh người khác. */
async function setup(userData: Record<string, unknown> = {}): Promise<Ctx> {
  await clearEmulators();
  const email = `xoa${++counter}@example.com`;
  const { uid } = await signUpEmail(email);
  await seedUser(uid, { email, ...userData });
  const storage = fakeStorageServer();
  storage.objects.add(`${uid}/avatar_1700000000000.jpg`);
  storage.objects.add(`${uid}/avatar_1700000000001.jpg`);
  storage.objects.add("nguoi-khac/avatar_1700000000000.jpg");
  const fakes = fakeHelpers();
  const deps: DeletionDeps = {
    ...defaultDeletionDeps(),
    helpers: fakes.helpers,
    storage: supabaseStorageAdmin(storageFor(storage)),
  };
  return { uid, email, fakes, storage, deps };
}

function token(uid: string, claims: Record<string, unknown> = {}): string {
  return emulatorToken({ sub: uid, user_id: uid, appRole: "PLAYER", ...claims });
}

async function call(ctx: Ctx, body: Record<string, unknown>, claims: Record<string, unknown> = {}) {
  const res = await handleDeleteAccount(bearer(token(ctx.uid, claims), { body: JSON.stringify(body) }), ctx.deps);
  return { status: res.status, body: await res.json() };
}

async function preview(ctx: Ctx) {
  const r = await call(ctx, { action: "preview" });
  assertEquals(r.status, 200);
  return r.body;
}

async function confirm(ctx: Ctx, impactVersion?: string, claims: Record<string, unknown> = {}) {
  const version = impactVersion ?? (await preview(ctx)).impactVersion;
  return await call(ctx, { action: "confirm", impactVersion: version, confirmText: "XÓA" }, claims);
}

async function authState(email: string): Promise<"OK" | "USER_DISABLED" | "GONE"> {
  const res = await signInEmail(email);
  if (res.ok) {
    await res.body?.cancel();
    return "OK";
  }
  const message = (await res.json()).error.message as string;
  return message === "USER_DISABLED" ? "USER_DISABLED" : "GONE";
}

function test(name: string, fn: (ctx: Ctx) => Promise<void>, setupData?: Record<string, unknown>) {
  Deno.test(name, async () => {
    const ctx = await setup(setupData);
    try {
      await fn(ctx);
    } finally {
      await ctx.storage.close();
    }
  });
}

// ---------- preview ----------

test("X1: người chơi không có đơn, preview → myBookings rỗng, ownerImpact null, có impactVersion", async (ctx) => {
  const p = await preview(ctx);
  assertEquals(p.myBookings, []);
  assertEquals(p.myDropIns, []);
  assertEquals(p.waitlistCount, 0);
  assertEquals(p.ownerImpact, null);
  assertEquals(p.walletBalance, 50000);
  assert(String(p.impactVersion).startsWith("sha256:"));
}, { walletBalance: 50000 });

test("X2: 2 đơn sắp tới → refundAmount 0 (chưa trả) và 60.000 (cọc, còn > 4 giờ); đơn đã qua, đã hủy không tính", async (ctx) => {
  await seedBooking("b-pending", { userId: ctx.uid, status: "PENDING", startInHours: 30 });
  await seedBooking("b-confirmed", {
    userId: ctx.uid,
    status: "CONFIRMED",
    startInHours: 48,
    paymentStatus: "DEPOSITED",
    depositAmount: 60000,
  });
  await seedBooking("b-past", { userId: ctx.uid, status: "COMPLETED", startInHours: -24 });
  await seedBooking("b-cancelled", { userId: ctx.uid, status: "CANCELLED", startInHours: 24 });
  const p = await preview(ctx);
  const byId = Object.fromEntries(p.myBookings.map((b: { bookingId: string }) => [b.bookingId, b]));
  assertEquals(Object.keys(byId).sort(), ["b-confirmed", "b-pending"]);
  assertEquals([byId["b-pending"].paidAmount, byId["b-pending"].refundAmount], [0, 0]);
  assertEquals([byId["b-confirmed"].paidAmount, byId["b-confirmed"].refundAmount], [60000, 60000]);
  assertEquals(byId["b-confirmed"].venueName, "Sân cầu lông Hòa Bình");
  assertEquals(byId["b-confirmed"].status, "CONFIRMED");
});

test("preview liệt kê lượt vãng lai sắp tới và số lượt chờ", async (ctx) => {
  await seedSession("s1", "v-other");
  await seedRegistration("s1", ctx.uid, { amount: 50000, paymentStatus: "PAID" });
  await seedSession("s2", "v-other");
  await seedRegistration("s2", ctx.uid, { status: "WAITLISTED", paymentStatus: "UNPAID", amount: 0 });
  const p = await preview(ctx);
  assertEquals(p.myDropIns.length, 1);
  assertEquals([p.myDropIns[0].sessionId, p.myDropIns[0].paidAmount, p.myDropIns[0].refundAmount], ["s1", 50000, 50000]);
  assertEquals(p.waitlistCount, 1);
});

// ---------- confirm ----------

test("X3: confirm đúng → 200 DELETED, đơn hủy, slot xóa, users/Auth/ảnh/favorites không còn, state DONE", async (ctx) => {
  await seedBooking("b1", { userId: ctx.uid, status: "PENDING" });
  await seedBooking("b2", { userId: ctx.uid, status: "CONFIRMED", paymentStatus: "DEPOSITED", depositAmount: 60000 });
  await seedFavorites(ctx.uid, ["v1", "v2"]);
  const r = await confirm(ctx);
  assertEquals([r.status, r.body], [200, { status: "DELETED" }]);
  for (const id of ["b1", "b2"]) {
    const b = await get(`bookings/${id}`);
    assertEquals([b?.status, b?.cancelledBy], ["CANCELLED", "SYSTEM"]);
    assertEquals(await get(`slots/slot_${id}`), null);
  }
  assertEquals(await get(`users/${ctx.uid}`), null);
  assertEquals(await authState(ctx.email), "GONE");
  assertEquals([...ctx.storage.objects], ["nguoi-khac/avatar_1700000000000.jpg"]);
  assertEquals(await docsIn("favorites", `users/${ctx.uid}`), []);
  const del = await get(`accountDeletions/${ctx.uid}`);
  assertEquals(del?.state, "DONE");
  assert(del?.completedAt instanceof Date);
});

test("X4: auth_time cũ hơn 5 phút → 403 REAUTH_REQUIRED, không có gì đổi", async (ctx) => {
  await seedBooking("b1", { userId: ctx.uid });
  const version = (await preview(ctx)).impactVersion;
  const r = await confirm(ctx, version, { auth_time: Math.floor(Date.now() / 1000) - 301 });
  assertEquals([r.status, r.body.error.code], [403, "REAUTH_REQUIRED"]);
  assertEquals((await get("bookings/b1"))?.status, "CONFIRMED");
  assertEquals(await get(`accountDeletions/${ctx.uid}`), null);
});

test("X5: confirmText = \"xoa\" → 400 CONFIRM_TEXT_MISMATCH; \" xóa \" thì hợp lệ", async (ctx) => {
  const version = (await preview(ctx)).impactVersion;
  const bad = await call(ctx, { action: "confirm", impactVersion: version, confirmText: "xoa" });
  assertEquals([bad.status, bad.body.error.code], [400, "CONFIRM_TEXT_MISMATCH"]);
  const ok = await call(ctx, { action: "confirm", impactVersion: version, confirmText: " xóa " });
  assertEquals(ok.status, 200);
});

test("X6: thêm đơn sau preview rồi confirm với impactVersion cũ → 409 IMPACT_CHANGED kèm preview mới", async (ctx) => {
  await seedBooking("b1", { userId: ctx.uid });
  const version = (await preview(ctx)).impactVersion;
  await seedBooking("b-new", { userId: ctx.uid });
  const r = await confirm(ctx, version);
  assertEquals([r.status, r.body.error.code], [409, "IMPACT_CHANGED"]);
  assertEquals(r.body.error.preview.myBookings.length, 2);
  assert(r.body.error.preview.impactVersion !== version);
  assertEquals((await get("bookings/b1"))?.status, "CONFIRMED");
  assertEquals(await get(`accountDeletions/${ctx.uid}`), null);
});

for (let i = 1; i <= REPEAT; i++) {
  const suffix = REPEAT > 1 ? ` (lần ${i})` : "";
  test(`X7: helper ném lỗi ở đơn thứ 2 → 500, không đơn nào bị hủy, tài khoản nguyên vẹn${suffix}`, async (ctx) => {
    await seedBooking("b1", { userId: ctx.uid, paymentStatus: "PAID", total: 100000 });
    await seedBooking("b2", { userId: ctx.uid, paymentStatus: "PAID", total: 100000 });
    ctx.fakes.failAt.cancelBookingInTx = 2;
    const r = await confirm(ctx);
    assertEquals([r.status, r.body.error.code], [500, "INTERNAL"]);
    for (const id of ["b1", "b2"]) {
      assertEquals((await get(`bookings/${id}`))?.status, "CONFIRMED");
      assertExists(await get(`slots/slot_${id}`));
    }
    const user = await get(`users/${ctx.uid}`);
    assertEquals([user?.deletionRequestedAt, user?.walletBalance, user?.fcmTokens], [undefined, 0, ["token-1"]]);
    assertEquals(await get(`accountDeletions/${ctx.uid}`), null);
    assertEquals(await authState(ctx.email), "OK");
  });
}

test("X8: chủ sân → cơ sở ẩn, đơn và buổi của khách hủy hoàn 100%, 4 thông báo khác khóa (SC-008)", async (ctx) => {
  for (const g of ["g1", "g2", "g3", "g4"]) await seedUser(g);
  await seedVenue("v1", ctx.uid);
  await seedBooking("gb1", { userId: "g1", venueId: "v1", paymentStatus: "PAID", total: 100000, startInHours: 2 });
  await seedBooking("gb2", { userId: "g2", venueId: "v1", paymentStatus: "DEPOSITED", depositAmount: 30000 });
  await seedSession("s1", "v1");
  await seedRegistration("s1", "g3", { amount: 50000 });
  await seedRegistration("s1", "g4", { amount: 50000 });
  const p = await preview(ctx);
  assertEquals(p.ownerImpact, { venueCount: 1, guestBookingCount: 2, guestDropInCount: 2, guestCount: 4 });

  const r = await confirm(ctx, p.impactVersion);
  assertEquals(r.status, 200);
  const venue = await get("venues/v1");
  assertEquals([venue?.status, venue?.hiddenReason], ["HIDDEN", "OWNER_DELETED"]);
  assertEquals((await get("bookings/gb1"))?.status, "CANCELLED");
  assertEquals((await get("users/g1"))?.walletBalance, 100000);
  assertEquals((await get("users/g2"))?.walletBalance, 30000);
  assertEquals((await get("dropInSessions/s1"))?.status, "CANCELLED");
  assertEquals((await get("users/g3"))?.walletBalance, 50000);
  assertEquals((await get("users/g4"))?.walletBalance, 50000);

  const del = await get(`accountDeletions/${ctx.uid}`);
  assertEquals(del?.wasOwner, true);
  assertEquals((del?.notify as unknown[]).length, 4);
  const keys = ctx.fakes.sent.map((s) => s.idempotencyKey).sort();
  assertEquals(keys, [
    "BOOKING_gb1_g1_OWNER_DELETED",
    "BOOKING_gb2_g2_OWNER_DELETED",
    "DROPIN_s1_g3_OWNER_DELETED",
    "DROPIN_s1_g4_OWNER_DELETED",
  ]);
  assertEquals(ctx.fakes.sent.map((s) => s.recipientUid).sort(), ["g1", "g2", "g3", "g4"]);
});

test("X9: lỗi ở anonymizeReviews lần đầu → 202, bước FAILED, Auth đã tắt; sweeper chạy tiếp tới DONE", async (ctx) => {
  ctx.fakes.failAt.anonymizeReviews = 1;
  const r = await confirm(ctx);
  assertEquals([r.status, r.body], [202, { status: "CLEANUP_SCHEDULED" }]);
  const del = await get(`accountDeletions/${ctx.uid}`);
  const steps = del?.steps as Record<string, string>;
  assertEquals([del?.state, steps.disableAuth, steps.anonymizeReviews], ["COMMITTED", "DONE", "FAILED"]);
  assertEquals(del?.lastError, "anonymizeReviews:Error");
  assertEquals(await authState(ctx.email), "USER_DISABLED");

  const sweep = await handleSweeper(
    new Request("http://localhost/", { method: "POST", headers: { "x-cron-secret": CRON_SECRET } }),
    { ...ctx.deps, cronSecret: CRON_SECRET },
  );
  assertEquals(sweep.status, 200);
  assertEquals((await get(`accountDeletions/${ctx.uid}`))?.state, "DONE");
  assertEquals(await authState(ctx.email), "GONE");
});

for (let i = 1; i <= REPEAT; i++) {
  const suffix = REPEAT > 1 ? ` (lần ${i})` : "";
  test(`X10: confirm 2 lần liên tiếp, 2 lần song song, và sau khi users đã xóa → idempotent${suffix}`, async (ctx) => {
    await seedUser("g1");
    await seedVenue("v1", ctx.uid);
    await seedBooking("gb1", { userId: "g1", venueId: "v1", paymentStatus: "PAID", total: 100000 });
    const version = (await preview(ctx)).impactVersion;

    const [a, b] = await Promise.all([confirm(ctx, version), confirm(ctx, version)]);
    for (const r of [a, b]) assert([200, 202].includes(r.status), `song song: ${r.status} ${JSON.stringify(r.body)}`);
    const again = await confirm(ctx, version);
    assertEquals(again.status, 200);

    assertEquals(await get(`users/${ctx.uid}`), null);
    const afterDelete = await confirm(ctx, version);
    assertEquals([afterDelete.status, afterDelete.body], [200, { status: "DELETED" }]);

    // Hủy đúng một lần: ví khách chỉ được cộng một lần; thông báo chỉ gửi một lần.
    assertEquals((await get("users/g1"))?.walletBalance, 100000);
    assertEquals(ctx.fakes.sent.length, 1);
  });
}

test("X11: appRole = ADMIN → 403 ADMIN_SELF_DELETE", async (ctx) => {
  const version = (await preview(ctx)).impactVersion;
  const r = await confirm(ctx, version, { appRole: "ADMIN" });
  assertEquals([r.status, r.body.error.code], [403, "ADMIN_SELF_DELETE"]);
});

test("X12: sau khi chốt (và sau khi xóa xong), function khác dùng requireUser với token cũ → 403 ACCOUNT_DELETING", async (ctx) => {
  ctx.fakes.failAt.anonymizeReviews = 1; // dừng ở COMMITTED, users/{uid} còn
  await confirm(ctx);
  const req = () => bearer(token(ctx.uid));
  const e1 = await requireUser(req()).catch((e) => e);
  assertEquals([e1.status, e1.code], [403, "ACCOUNT_DELETING"]);

  await commit([deleteWrite(`users/${ctx.uid}`, false)]); // như sau bước deleteUserDoc
  const e2 = await requireUser(req()).catch((e) => e);
  assertEquals([e2.status, e2.code], [403, "ACCOUNT_DELETING"]);
});

test("X13: đánh giá sau khi xóa → userId null, authorDeleted true, tên null", async (ctx) => {
  await seedReview("r1", ctx.uid);
  await confirm(ctx);
  const review = await get("reviews/r1");
  assertEquals(review?.userId, null);
  assertEquals(review?.authorDeleted, true);
  assertEquals((review?.userSnapshot as Record<string, unknown>).displayName, null);
});

async function seedManyBookings(uid: string, n: number) {
  const writes = Array.from({ length: n }, (_, i) =>
    bookingWrites(`m${i}`, { userId: uid, paymentStatus: "DEPOSITED", depositAmount: 10000 })).flat();
  for (let i = 0; i < writes.length; i += 400) await commit(writes.slice(i, i + 400));
}

test("X14: 250 đơn đã cọc (ước tính 505 > 450) → 422 DELETE_TOO_LARGE, không có gì đổi", async (ctx) => {
  await seedManyBookings(ctx.uid, 250);
  const r = await confirm(ctx);
  assertEquals([r.status, r.body.error.code], [422, "DELETE_TOO_LARGE"]);
  assertEquals((await get("bookings/m0"))?.status, "CONFIRMED");
  assertEquals(await get(`accountDeletions/${ctx.uid}`), null);
});

test("X14 biên: 220 đơn đã cọc (ước tính 445) → xóa được", async (ctx) => {
  await seedManyBookings(ctx.uid, 220);
  const r = await confirm(ctx);
  assert([200, 202].includes(r.status), `${r.status} ${JSON.stringify(r.body)}`);
  assertEquals((await get("bookings/m219"))?.status, "CANCELLED");
});

test("X15: đơn đã check-in hoặc đã qua không bị hủy; đơn đã qua được ẩn danh", async (ctx) => {
  await seedBooking("b-checked", { userId: ctx.uid, startInHours: -0.5, checkedInAt: new Date() });
  await seedBooking("b-past", { userId: ctx.uid, status: "COMPLETED", startInHours: -48 });
  const p = await preview(ctx);
  assertEquals(p.myBookings, []);
  await confirm(ctx, p.impactVersion);
  const checked = await get("bookings/b-checked");
  const past = await get("bookings/b-past");
  assertEquals([checked?.status, past?.status], ["CONFIRMED", "COMPLETED"]);
  assertEquals(past?.userDeleted, true);
});

// ---------- Không có helper (code thật khi nhóm khác chưa xong, research R12) ----------

test("chưa có helper của B: người có đơn sắp tới → 500, không có gì đổi (FR-021)", async (ctx) => {
  await seedBooking("b1", { userId: ctx.uid });
  ctx.deps = { ...ctx.deps, helpers: {} };
  const r = await confirm(ctx);
  assertEquals([r.status, r.body.error.code], [500, "INTERNAL"]);
  assertEquals((await get("bookings/b1"))?.status, "CONFIRMED");
  assertEquals(await get(`accountDeletions/${ctx.uid}`), null);
});

test("chưa có helper: người không có đơn vẫn xóa được, bước của nhóm khác ghi SKIPPED", async (ctx) => {
  ctx.deps = { ...ctx.deps, helpers: {} };
  const r = await confirm(ctx);
  assertEquals(r.status, 200);
  const steps = (await get(`accountDeletions/${ctx.uid}`))?.steps as Record<string, string>;
  assertEquals(steps, {
    disableAuth: "DONE",
    notifyGuests: "DONE",
    anonymizeReviews: "SKIPPED",
    anonymizeCommunity: "SKIPPED",
    anonymizeBookings: "SKIPPED",
    deleteOwnerData: "SKIPPED",
    deleteSubcollections: "DONE",
    deleteAvatars: "DONE",
    deleteUserDoc: "DONE",
    deleteAuthUser: "DONE",
  });
  assertEquals(await authState(ctx.email), "GONE");
});

test("action lạ → 400 INVALID_ARGUMENT; confirm thiếu impactVersion → 400", async (ctx) => {
  const r = await call(ctx, { action: "xoa-luon" });
  assertEquals([r.status, r.body.error.code], [400, "INVALID_ARGUMENT"]);
  const r2 = await call(ctx, { action: "confirm", confirmText: "XÓA" });
  assertEquals([r2.status, r2.body.error.code], [400, "INVALID_ARGUMENT"]);
});
