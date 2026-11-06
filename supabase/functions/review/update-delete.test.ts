// T062 (spec 070 US5): action `update` và `delete` — người viết sửa trong hạn 7 ngày, xóa bất cứ lúc nào.
// Chạy giống create.test.ts (cần Firebase Emulator).
import { assert, assertEquals, assertInstanceOf } from "jsr:@std/assert@1.0.19";
import { commit, get, updateWrite } from "../_shared/firestore.ts";
import { handleReview, type ReviewDeps } from "./handler.ts";
import { call, createBody, DAY, deps, done, NOW, seed, storage } from "./testing.ts";
import { bearer } from "../_shared/testing.ts";
import { tokenOf } from "./testing.ts";

const update = (reviewId: string, extra: Record<string, unknown> = {}) => ({
  action: "update",
  reviewId,
  ratings: { court: 5, cleanliness: 4, service: 5 }, // overall 4.7
  comment: "Đã sửa",
  photoPaths: [],
  ...extra,
});
const del = (reviewId: string) => ({ action: "delete", reviewId });

/** Gọi với `now` lùi/tiến so với NOW (kiểm tra hạn sửa). */
async function callAt(now: Date, uid: string, body: unknown) {
  const d: ReviewDeps = { ...deps, now: () => now };
  const res = await handleReview(bearer(tokenOf(uid), { body: JSON.stringify(body) }), d);
  return { status: res.status, body: await res.json() };
}

Deno.test("sửa trong hạn: editedAt đặt, tổng điểm = − cũ + mới, ownerReply giữ nguyên", async () => {
  await seed({ "b-done": done(1), "b-2": done(1) });
  assertEquals((await call("player1", createBody("b-done"))).status, 201); // 5/4/3 = 4.0
  assertEquals((await call("player1", createBody("b-2", { ratings: { court: 1, cleanliness: 1, service: 1 } }))).status, 201);
  assertEquals((await call("owner1", { action: "reply", reviewId: "b-done", text: "Cảm ơn" }, "OWNER")).status, 200);

  const r = await call("player1", update("b-done"));
  assertEquals(r.status, 200, JSON.stringify(r.body));
  assertEquals(r.body.review.id, "b-done");
  assertEquals(r.body.review.overall, 4.7);
  assertEquals(r.body.review.canEditUntil, new Date(NOW.getTime() - DAY + 7 * DAY).toISOString());

  const review = await get("reviews/b-done");
  assertEquals([review?.comment, review?.overall, review?.starBucket], ["Đã sửa", 4.7, 5]);
  assertEquals(review?.ratings, { court: 5, cleanliness: 4, service: 5 });
  assertInstanceOf(review?.editedAt, Date);
  assertEquals((review?.ownerReply as Record<string, unknown>).text, "Cảm ơn");
  assertEquals(review?.hasOwnerReply, true);

  const venue = await get("venues/venueA");
  assertEquals(venue?.ratingSums, { court: 6, cleanliness: 5, service: 6, overall10: 57 });
  assertEquals([venue?.ratingCount, venue?.ratingAvg], [2, 2.9]);
  assertEquals(r.body.venue, { ratingAvg: 2.9, ratingCount: 2 });
});

Deno.test("sửa quá hạn → 409 REVIEW_WINDOW_CLOSED; người khác sửa → 403 NOT_OWNER; không tồn tại → 404", async () => {
  await seed({ "b-done": done(1) });
  assertEquals((await call("player1", createBody("b-done"))).status, 201);

  const late = await callAt(new Date(NOW.getTime() + 7 * DAY), "player1", update("b-done"));
  assertEquals([late.status, late.body.error.code], [409, "REVIEW_WINDOW_CLOSED"]);
  const other = await call("player2", update("b-done"));
  assertEquals([other.status, other.body.error.code], [403, "NOT_OWNER"]);
  const missing = await call("player1", update("b-missing"));
  assertEquals([missing.status, missing.body.error.code], [404, "NOT_FOUND"]);
  assertEquals((await get("reviews/b-done"))?.editedAt, null);
});

Deno.test("sửa: dữ liệu sai → 400; ảnh mới chưa có trong Storage → 409 PHOTO_MISSING", async () => {
  await seed({ "b-done": done(1) });
  assertEquals((await call("player1", createBody("b-done"))).status, 201);
  for (const extra of [{ ratings: { court: 5 } }, { comment: "x".repeat(1001) }, { photoPaths: ["player2/b-done/a.jpg"] }]) {
    const r = await call("player1", update("b-done", extra));
    assertEquals([r.status, r.body.error.code], [400, "INVALID_ARGUMENT"], JSON.stringify(extra));
  }
  const r = await call("player1", update("b-done", { photoPaths: ["player1/b-done/new.jpg"] }));
  assertEquals([r.status, r.body.error.code], [409, "PHOTO_MISSING"]);
});

Deno.test("sửa: ảnh bị bỏ khỏi photoPaths bị xóa khỏi Storage sau commit", async () => {
  const a = "player1/b-done/a.jpg", b = "player1/b-done/b.jpg", c = "player1/b-done/c.jpg";
  await seed({ "b-done": done(1) }, [a, b, c]);
  assertEquals((await call("player1", createBody("b-done", { photoPaths: [a, b] }))).status, 201);

  const r = await call("player1", update("b-done", { photoPaths: [b, c] }));
  assertEquals(r.status, 200, JSON.stringify(r.body));
  assertEquals(storage.removed, [a]);
  const review = await get("reviews/b-done");
  assertEquals([review?.photoPaths, review?.hasPhotos], [[b, c], true]);
});

Deno.test("sửa đánh giá HIDDEN: tổng điểm không đổi", async () => {
  await seed({ "b-done": done(1) });
  assertEquals((await call("player1", createBody("b-done"))).status, 201);
  await commit([
    updateWrite("reviews/b-done", { status: "HIDDEN" }),
    updateWrite("venues/venueA", {
      ratingSums: { court: 0, cleanliness: 0, service: 0, overall10: 0 },
      ratingCount: 0,
      ratingAvg: 0,
    }),
  ]);
  assertEquals((await call("player1", update("b-done"))).status, 200);
  const venue = await get("venues/venueA");
  assertEquals([venue?.ratingCount, venue?.ratingAvg], [0, 0]);
});

Deno.test("xóa VISIBLE: ratingCount −1, ảnh trong {uid}/{reviewId}/ bị xóa", async () => {
  const a = "player1/b-done/a.jpg", draft = "player1/b-done/draft.jpg", other = "player1/b-2/x.jpg";
  await seed({ "b-done": done(1), "b-2": done(1) }, [a, draft, other]);
  assertEquals((await call("player1", createBody("b-done", { photoPaths: [a] }))).status, 201);
  assertEquals((await call("player1", createBody("b-2", { ratings: { court: 1, cleanliness: 1, service: 1 } }))).status, 201);

  const r = await call("player1", del("b-done"));
  assertEquals(r.status, 200, JSON.stringify(r.body));
  assertEquals(r.body, { venue: { ratingAvg: 1, ratingCount: 1 } });
  assertEquals(await get("reviews/b-done"), null);
  assertEquals(storage.removed.sort(), [a, draft].sort());
  assert(storage.objects.has(other));

  const venue = await get("venues/venueA");
  assertEquals(venue?.ratingSums, { court: 1, cleanliness: 1, service: 1, overall10: 10 });
});

Deno.test("xóa HIDDEN: tổng điểm không đổi", async () => {
  await seed({ "b-done": done(1) });
  assertEquals((await call("player1", createBody("b-done"))).status, 201);
  await commit([updateWrite("reviews/b-done", { status: "HIDDEN" })]);
  const before = await get("venues/venueA");
  assertEquals((await call("player1", del("b-done"))).status, 200);
  const after = await get("venues/venueA");
  assertEquals([after?.ratingCount, after?.ratingSums], [before?.ratingCount, before?.ratingSums]);
});

Deno.test("xóa quá hạn vẫn được; người khác xóa → 403; sau khi xóa, tạo lại trong hạn được", async () => {
  await seed({ "b-done": done(1) });
  assertEquals((await call("player1", createBody("b-done"))).status, 201);

  const other = await call("player2", del("b-done"));
  assertEquals([other.status, other.body.error.code], [403, "NOT_OWNER"]);

  assertEquals((await call("player1", del("b-done"))).status, 200);
  assertEquals((await call("player1", createBody("b-done", { comment: "Viết lại" }))).status, 201);
  assertEquals((await get("venues/venueA"))?.ratingCount, 1);

  const late = await callAt(new Date(NOW.getTime() + 30 * DAY), "player1", del("b-done"));
  assertEquals(late.status, 200);
  assertEquals((await get("venues/venueA"))?.ratingCount, 0);

  const missing = await call("player1", del("b-done"));
  assertEquals([missing.status, missing.body.error.code], [404, "NOT_FOUND"]);
});
