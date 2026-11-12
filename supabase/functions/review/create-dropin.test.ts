// T056 (spec 080 US5): `create`/`update`/`delete`/`discardPhotos` của Edge Function `review` cho buổi vãng lai.
// Chạy giống create.test.ts (cần Firebase Emulator).
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { commit, createWrite, get } from "../_shared/firestore.ts";
import { handleReview, type ReviewDeps } from "./handler.ts";
import { bearer } from "../_shared/testing.ts";
import { call, DAY, deps, NOW, RATINGS, seed, sent, storage, tokenOf } from "./testing.ts";

const HOUR = 60 * 60 * 1000;
const END_1D = new Date(NOW.getTime() - DAY); // buổi đã kết thúc 1 ngày trước

const session = (endAt: Date, extra: Record<string, unknown> = {}) => ({
  venueId: "venueA",
  ownerId: "owner1",
  startAt: new Date(endAt.getTime() - 2 * HOUR),
  endAt,
  status: "COMPLETED",
  ...extra,
});
const reg = (status: string, uid = "player1") => ({
  userId: uid,
  sessionId: "s1",
  venueId: "venueA",
  status,
  partySize: 1,
});

/** player1 CHECKED_IN buổi `s1` (venueA, kết thúc 1 ngày trước); thêm các document theo `extra`. */
async function seedDropIn(extra: ReturnType<typeof createWrite>[] = []) {
  await seed();
  await commit([
    createWrite("dropInSessions/s1", session(END_1D)),
    createWrite("dropInSessions/s1/registrations/player1", reg("CHECKED_IN")),
    ...extra,
  ]);
}

const create = (sessionId: string, extra: Record<string, unknown> = {}) => ({
  action: "create",
  sessionId,
  ratings: RATINGS,
  comment: "Buổi vui, đông người",
  photoPaths: [],
  ...extra,
});

async function callAt(now: Date, uid: string, body: unknown) {
  const d: ReviewDeps = { ...deps, now: () => now };
  const res = await handleReview(bearer(tokenOf(uid), { body: JSON.stringify(body) }), d);
  return { status: res.status, body: await res.json() };
}

Deno.test("create buổi vãng lai hợp lệ: reviews/{sessionId}_{uid}, DROP_IN, cộng điểm venue, thông báo chủ", async () => {
  await seedDropIn();
  const r = await call("player1", create("s1"));
  assertEquals(r.status, 201, JSON.stringify(r.body));
  assertEquals(r.body.review.id, "s1_player1");
  assertEquals(r.body.review.overall, 4.0);
  assertEquals(r.body.review.canEditUntil, new Date(END_1D.getTime() + 7 * DAY).toISOString());
  assertEquals(r.body.venue, { ratingAvg: 4.0, ratingCount: 1 });

  const review = await get("reviews/s1_player1");
  assert(review);
  assertEquals(review.targetType, "DROP_IN");
  assertEquals(review.sessionId, "s1");
  assertEquals(review.bookingId, undefined);
  assertEquals([review.venueId, review.userId, review.status], ["venueA", "player1", "VISIBLE"]);
  assertEquals((review.completedAt as Date).getTime(), END_1D.getTime());

  const venue = await get("venues/venueA");
  assertEquals(venue?.ratingSums, { court: 5, cleanliness: 4, service: 3, overall10: 40 });
  assertEquals([venue?.ratingCount, venue?.ratingAvg], [1, 4]);
  assertEquals(sent, [{ type: "REVIEW_NEW", recipientUid: "owner1", data: { reviewId: "s1_player1", venueId: "venueA" } }]);
});

Deno.test("E14: đăng ký REGISTERED / CANCELLED / NO_SHOW / không có đăng ký → 409 NOT_CHECKED_IN", async () => {
  for (const status of ["REGISTERED", "CANCELLED", "NO_SHOW"]) {
    await seed();
    await commit([
      createWrite("dropInSessions/s1", session(END_1D)),
      createWrite("dropInSessions/s1/registrations/player1", reg(status)),
    ]);
    const r = await call("player1", create("s1"));
    assertEquals([r.status, r.body.error.code], [409, "NOT_CHECKED_IN"], status);
    assertEquals(await get("reviews/s1_player1"), null);
  }
  await seed();
  await commit([createWrite("dropInSessions/s1", session(END_1D))]);
  const none = await call("player1", create("s1"));
  assertEquals([none.status, none.body.error.code], [409, "NOT_CHECKED_IN"]);
  assertEquals((await get("venues/venueA"))?.ratingCount, 0);
});

Deno.test("buổi chưa kết thúc (now < endAt) → 409 NOT_CHECKED_IN", async () => {
  await seedDropIn([
    createWrite("dropInSessions/s-live", session(new Date(NOW.getTime() + HOUR), { status: "OPEN" })),
    createWrite("dropInSessions/s-live/registrations/player1", reg("CHECKED_IN")),
  ]);
  const r = await call("player1", create("s-live"));
  assertEquals([r.status, r.body.error.code], [409, "NOT_CHECKED_IN"]);
});

Deno.test("quá 7 ngày sau endAt → 409 REVIEW_WINDOW_CLOSED; đúng 7 ngày vẫn được", async () => {
  await seedDropIn([
    createWrite("dropInSessions/s-old", session(new Date(NOW.getTime() - 8 * DAY))),
    createWrite("dropInSessions/s-old/registrations/player1", reg("CHECKED_IN")),
    createWrite("dropInSessions/s-edge", session(new Date(NOW.getTime() - 7 * DAY))),
    createWrite("dropInSessions/s-edge/registrations/player1", reg("CHECKED_IN")),
  ]);
  const r = await call("player1", create("s-old"));
  assertEquals([r.status, r.body.error.code], [409, "REVIEW_WINDOW_CLOSED"]);
  assertEquals((await call("player1", create("s-edge"))).status, 201);
});

Deno.test("buổi không tồn tại → 404 NOT_FOUND", async () => {
  await seedDropIn();
  const r = await call("player1", create("s-missing"));
  assertEquals([r.status, r.body.error.code], [404, "NOT_FOUND"]);
});

Deno.test("chủ cơ sở tự đánh giá buổi của mình → 403 OWN_VENUE", async () => {
  await seedDropIn([createWrite("dropInSessions/s1/registrations/owner1", reg("CHECKED_IN", "owner1"))]);
  const r = await call("owner1", create("s1"), "OWNER");
  assertEquals([r.status, r.body.error.code], [403, "OWN_VENUE"]);
  assertEquals(await get("reviews/s1_owner1"), null);
});

Deno.test("E15: lần 2 khác nội dung → 409 ALREADY_REVIEWED; cùng nội dung → 200, không cộng điểm lần 2", async () => {
  await seedDropIn();
  assertEquals((await call("player1", create("s1"))).status, 201);

  const again = await call("player1", create("s1"));
  assertEquals([again.status, again.body.review.id], [200, "s1_player1"]);
  assertEquals((await get("venues/venueA"))?.ratingCount, 1);
  assertEquals(sent.length, 1);

  const different = await call("player1", create("s1", { ratings: { court: 1, cleanliness: 1, service: 1 } }));
  assertEquals([different.status, different.body.error.code], [409, "ALREADY_REVIEWED"]);
  assertEquals((await get("venues/venueA"))?.ratingCount, 1);
});

Deno.test("ảnh phải có tiền tố {uid}/{sessionId}_{uid}/", async () => {
  const ok = "player1/s1_player1/a.jpg";
  await seedDropIn();
  storage.reset([ok]);
  for (const bad of ["player1/s1/a.jpg", "player2/s1_player1/a.jpg", "player1/s1_player2/a.jpg"]) {
    const r = await call("player1", create("s1", { photoPaths: [bad] }));
    assertEquals([r.status, r.body.error.code], [400, "INVALID_ARGUMENT"], bad);
  }
  assertEquals((await call("player1", create("s1", { photoPaths: [ok] }))).status, 201);
  assertEquals((await get("reviews/s1_player1"))?.photoPaths, [ok]);
});

Deno.test("gửi cả bookingId và sessionId, hoặc không gửi cái nào → 400 INVALID_ARGUMENT", async () => {
  await seedDropIn();
  const noId = { action: "create", ratings: RATINGS, comment: "", photoPaths: [] };
  for (const body of [create("s1", { bookingId: "b1" }), noId, create("a/b")]) {
    const r = await call("player1", body);
    assertEquals([r.status, r.body.error.code], [400, "INVALID_ARGUMENT"], JSON.stringify(body));
  }
  assertEquals(await get("reviews/s1_player1"), null);
});

Deno.test("update review DROP_IN: tổng điểm venue = − cũ + mới, hạn sửa tính từ endAt", async () => {
  await seedDropIn();
  assertEquals((await call("player1", create("s1"))).status, 201); // 4.0
  const upd = (extra: Record<string, unknown> = {}) => ({
    action: "update",
    reviewId: "s1_player1",
    ratings: { court: 5, cleanliness: 4, service: 5 }, // 4.7
    comment: "Đã sửa",
    photoPaths: [],
    ...extra,
  });
  const r = await call("player1", upd());
  assertEquals(r.status, 200, JSON.stringify(r.body));
  assertEquals(r.body.review.canEditUntil, new Date(END_1D.getTime() + 7 * DAY).toISOString());
  assertEquals(r.body.venue, { ratingAvg: 4.7, ratingCount: 1 });
  assertEquals((await get("reviews/s1_player1"))?.targetType, "DROP_IN");

  const late = await callAt(new Date(END_1D.getTime() + 7 * DAY + 1), "player1", upd());
  assertEquals([late.status, late.body.error.code], [409, "REVIEW_WINDOW_CLOSED"]);
});

Deno.test("delete review DROP_IN: trừ điểm venue và xóa ảnh trong {uid}/{sessionId}_{uid}/", async () => {
  const photo = "player1/s1_player1/a.jpg";
  await seedDropIn();
  storage.reset([photo]);
  assertEquals((await call("player1", create("s1", { photoPaths: [photo] }))).status, 201);
  const r = await call("player1", { action: "delete", reviewId: "s1_player1" });
  assertEquals([r.status, r.body.venue], [200, { ratingAvg: 0, ratingCount: 0 }]);
  assertEquals(await get("reviews/s1_player1"), null);
  assertEquals((await get("venues/venueA"))?.ratingCount, 0);
  assertEquals(storage.removed, [photo]);
});

Deno.test("discardPhotos nhận sessionId: xóa ảnh nháp, giữ ảnh đã thuộc review; sai tiền tố → 400", async () => {
  const kept = "player1/s1_player1/kept.jpg", draft = "player1/s1_player1/draft.jpg";
  await seedDropIn();
  storage.reset([kept, draft]);
  assertEquals((await call("player1", create("s1", { photoPaths: [kept] }))).status, 201);

  const r = await call("player1", { action: "discardPhotos", sessionId: "s1", photoPaths: [kept, draft] });
  assertEquals([r.status, r.body], [200, { deleted: 1 }]);
  assertEquals([...storage.objects], [kept]);

  const bad = await call("player1", { action: "discardPhotos", sessionId: "s1", photoPaths: ["player1/s1/x.jpg"] });
  assertEquals([bad.status, bad.body.error.code], [400, "INVALID_ARGUMENT"]);
  const both = await call("player1", { action: "discardPhotos", sessionId: "s1", bookingId: "b", photoPaths: [] });
  assertEquals([both.status, both.body.error.code], [400, "INVALID_ARGUMENT"]);
});
