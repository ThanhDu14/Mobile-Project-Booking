// T026 (spec 070 US1): action `create` của Edge Function `review` trên Firestore Emulator.
// Chạy (cần `firebase emulators:start --only auth,firestore`):
//   cd supabase/functions
//   FIREBASE_PROJECT_ID=demo-smashnow FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 \
//   FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 deno test -A review/
import { assert, assertEquals, assertInstanceOf } from "jsr:@std/assert@1.0.19";
import * as firestore from "../_shared/firestore.ts";
import { commit, createWrite, get } from "../_shared/firestore.ts";
import { bearer, clearEmulators, emulatorToken } from "../_shared/testing.ts";
import { handleReview, type Notification, type ReviewDeps } from "./handler.ts";

const DAY = 24 * 60 * 60 * 1000;
const NOW = new Date("2026-11-06T10:00:00Z");
const RATINGS = { court: 5, cleanliness: 4, service: 3 }; // overall 4.0

const sent: Notification[] = [];
const deps: ReviewDeps = {
  firestore,
  now: () => NOW,
  notify: (n) => {
    sent.push(n);
    return Promise.resolve();
  },
};

const tokenOf = (uid: string, appRole = "PLAYER") => emulatorToken({ sub: uid, user_id: uid, appRole });

async function call(uid: string, body: unknown, appRole = "PLAYER", d: ReviewDeps = deps) {
  const res = await handleReview(bearer(tokenOf(uid, appRole), { body: JSON.stringify(body) }), d);
  return { status: res.status, body: await res.json() };
}

const create = (bookingId: string, extra: Record<string, unknown> = {}) => ({
  action: "create",
  bookingId,
  ratings: RATINGS,
  comment: "Sân sạch, đèn sáng",
  photoPaths: [],
  ...extra,
});

/** player1, owner1 (sở hữu venueA), venueA chưa có đánh giá; đơn của player1 theo `bookings`. */
async function seed(bookings: Record<string, Record<string, unknown>> = {}) {
  await clearEmulators();
  sent.length = 0;
  await commit([
    createWrite("users/player1", { displayName: "Nam", avatarPath: "player1/a.jpg", accountStatus: "ACTIVE" }),
    createWrite("users/owner1", { displayName: "Chủ A", avatarPath: null, accountStatus: "ACTIVE" }),
    createWrite("users/locked1", { displayName: "Khóa", accountStatus: "LOCKED" }),
    createWrite("venues/venueA", { name: "Sân A", ownerId: "owner1", status: "ACTIVE", ratingAvg: 0, ratingCount: 0 }),
    ...Object.entries(bookings).map(([id, data]) =>
      createWrite(`bookings/${id}`, { venueId: "venueA", userId: "player1", status: "COMPLETED", ...data })
    ),
  ]);
}

const done = (daysAgo = 1) => ({ completedAt: new Date(NOW.getTime() - daysAgo * DAY) });

Deno.test("create thành công: 201, review đủ trường, tổng điểm +1, gửi REVIEW_NEW cho chủ sân", async () => {
  await seed({ "b-done": done(1) });
  const r = await call("player1", create("b-done", { comment: "  Sân sạch, đèn sáng  " }));

  assertEquals(r.status, 201, JSON.stringify(r.body));
  assertEquals(r.body.review.id, "b-done");
  assertEquals(r.body.review.overall, 4.0);
  assertEquals(r.body.review.canEditUntil, new Date(NOW.getTime() - DAY + 7 * DAY).toISOString());
  assertEquals(r.body.venue, { ratingAvg: 4.0, ratingCount: 1 });

  const review = await get("reviews/b-done");
  assert(review);
  const { createdAt, updatedAt, completedAt, ...rest } = review;
  assertEquals(rest, {
    targetType: "VENUE",
    bookingId: "b-done",
    venueId: "venueA",
    userId: "player1",
    userSnapshot: { displayName: "Nam", avatarPath: "player1/a.jpg" },
    authorDeleted: false,
    ratings: RATINGS,
    overall: 4.0,
    starBucket: 4,
    comment: "Sân sạch, đèn sáng",
    photoPaths: [],
    hasPhotos: false,
    ownerReply: null,
    hasOwnerReply: false,
    status: "VISIBLE",
    editedAt: null,
  });
  assertInstanceOf(createdAt, Date);
  assertInstanceOf(updatedAt, Date);
  assertEquals((completedAt as Date).getTime(), NOW.getTime() - DAY);

  const venue = await get("venues/venueA");
  assertEquals(venue?.ratingSums, { court: 5, cleanliness: 4, service: 3, overall10: 40 });
  assertEquals([venue?.ratingCount, venue?.ratingAvg], [1, 4]);

  assertEquals(sent, [{ type: "REVIEW_NEW", recipientUid: "owner1", data: { reviewId: "b-done", venueId: "venueA" } }]);
});

Deno.test("đơn không phải của mình → 403 NOT_OWNER; đơn không tồn tại → 404 NOT_FOUND", async () => {
  await seed({ "b-other": { ...done(1), userId: "player2" } });
  const r = await call("player1", create("b-other"));
  assertEquals([r.status, r.body.error.code], [403, "NOT_OWNER"]);
  const missing = await call("player1", create("b-missing"));
  assertEquals([missing.status, missing.body.error.code], [404, "NOT_FOUND"]);
  assertEquals(await get("reviews/b-other"), null);
});

Deno.test("đơn CONFIRMED → 409 BOOKING_NOT_COMPLETED", async () => {
  await seed({ "b-confirmed": { status: "CONFIRMED" } });
  const r = await call("player1", create("b-confirmed"));
  assertEquals([r.status, r.body.error.code], [409, "BOOKING_NOT_COMPLETED"]);
});

Deno.test("completedAt 8 ngày trước → 409 REVIEW_WINDOW_CLOSED; đúng 7 ngày vẫn được", async () => {
  await seed({ "b-old": done(8), "b-edge": done(7) });
  const r = await call("player1", create("b-old"));
  assertEquals([r.status, r.body.error.code], [409, "REVIEW_WINDOW_CLOSED"]);
  assertEquals((await call("player1", create("b-edge"))).status, 201);
});

Deno.test("chủ sân đánh giá cơ sở của mình → 403 OWN_VENUE", async () => {
  await seed({ "b-own": { ...done(1), userId: "owner1" } });
  const r = await call("owner1", create("b-own"), "OWNER");
  assertEquals([r.status, r.body.error.code], [403, "OWN_VENUE"]);
  assertEquals(await get("reviews/b-own"), null);
});

Deno.test("tài khoản bị khóa → 403 ACCOUNT_LOCKED", async () => {
  await seed({ "b-locked": { ...done(1), userId: "locked1" } });
  const r = await call("locked1", create("b-locked"));
  assertEquals([r.status, r.body.error.code], [403, "ACCOUNT_LOCKED"]);
});

Deno.test("dữ liệu sai → 400 INVALID_ARGUMENT, không ghi gì", async () => {
  await seed({ "b-done": done(1) });
  const bad = [
    create("b-done", { ratings: { court: 5, cleanliness: 4 } }),
    create("b-done", { ratings: { court: 6, cleanliness: 4, service: 3 } }),
    create("b-done", { ratings: { court: 0, cleanliness: 4, service: 3 } }),
    create("b-done", { comment: "x".repeat(1001) }),
    create("b-done", { photoPaths: ["a", "b", "c", "d"].map((n) => `player1/b-done/${n}.jpg`) }),
    create("b-done", { photoPaths: ["player2/b-done/a.jpg"] }),
    create(""),
    { action: "create" },
  ];
  for (const body of bad) {
    const r = await call("player1", body);
    assertEquals([r.status, r.body.error.code], [400, "INVALID_ARGUMENT"], JSON.stringify(body).slice(0, 80));
  }
  // Bình luận đúng 1000 ký tự sau khi trim vẫn hợp lệ.
  assertEquals((await call("player1", create("b-done", { comment: ` ${"x".repeat(1000)} ` }))).status, 201);
});

Deno.test("action lạ hoặc thiếu → 400 INVALID_ARGUMENT; không có token → 401", async () => {
  await seed();
  for (const body of [{ action: "nope" }, {}]) {
    const r = await call("player1", body);
    assertEquals([r.status, r.body.error.code], [400, "INVALID_ARGUMENT"]);
  }
  const res = await handleReview(new Request("http://localhost/functions/v1/review", { method: "POST", body: "{}" }), deps);
  assertEquals(res.status, 401);
  await res.body?.cancel();
});

Deno.test("gửi lại cùng nội dung → 200, không cộng điểm lần 2; khác nội dung → 409 ALREADY_REVIEWED", async () => {
  await seed({ "b-done": done(1) });
  assertEquals((await call("player1", create("b-done"))).status, 201);

  const again = await call("player1", create("b-done"));
  assertEquals(again.status, 200);
  assertEquals(again.body.review.id, "b-done");
  assertEquals(again.body.venue, { ratingAvg: 4.0, ratingCount: 1 });
  assertEquals((await get("venues/venueA"))?.ratingCount, 1);
  assertEquals(sent.length, 1, "không gửi thông báo lần 2");

  const different = await call("player1", create("b-done", { ratings: { court: 1, cleanliness: 1, service: 1 } }));
  assertEquals([different.status, different.body.error.code], [409, "ALREADY_REVIEWED"]);
});

Deno.test("gửi trùng đồng thời (bấm Gửi 3 lần): chỉ 1 đánh giá, ratingCount = 1", async () => {
  await seed({ "b-done": done(1) });
  const results = await Promise.all([1, 2, 3].map(() => call("player1", create("b-done"))));
  assert(results.every((r) => r.status === 200 || r.status === 201), JSON.stringify(results));
  assertEquals(results.filter((r) => r.status === 201).length, 1);
  assertEquals((await get("venues/venueA"))?.ratingCount, 1);
});

Deno.test("SC-005: 10 lời gọi song song cho 10 đơn của cùng venue → ratingCount +10, ratingSums đúng tổng", async () => {
  const ids = Array.from({ length: 10 }, (_, i) => `b-${i}`);
  await seed(Object.fromEntries(ids.map((id) => [id, done(1)])));
  const ratingsOf = (i: number) => ({ court: (i % 5) + 1, cleanliness: 5, service: ((i + 2) % 5) + 1 });

  const results = await Promise.all(ids.map((id, i) => call("player1", create(id, { ratings: ratingsOf(i) }))));
  assert(results.every((r) => r.status === 201), JSON.stringify(results.map((r) => r.body)));

  const expected = { court: 0, cleanliness: 0, service: 0, overall10: 0 };
  ids.forEach((_, i) => {
    const r = ratingsOf(i);
    expected.court += r.court;
    expected.cleanliness += r.cleanliness;
    expected.service += r.service;
    expected.overall10 += Math.round(Math.round((r.court + r.cleanliness + r.service) / 3 * 10));
  });
  const venue = await get("venues/venueA");
  assertEquals(venue?.ratingCount, 10);
  assertEquals(venue?.ratingSums, expected);
  assertEquals(venue?.ratingAvg, Math.round(expected.overall10 / 10 / 10 * 10) / 10);
});

Deno.test("gửi thông báo lỗi → đánh giá vẫn được lưu, trả 201", async () => {
  await seed({ "b-done": done(1) });
  const failing: ReviewDeps = { ...deps, notify: () => Promise.reject(new Error("send-notification 500")) };
  const r = await call("player1", create("b-done"), "PLAYER", failing);
  assertEquals(r.status, 201);
  assert(await get("reviews/b-done"));
});
