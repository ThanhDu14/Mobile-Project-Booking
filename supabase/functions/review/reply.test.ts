// T055 (spec 070 US4): action `reply` — chủ sân phản hồi đánh giá (tạo một lần, sửa được, không xóa).
// Chạy giống create.test.ts (cần Firebase Emulator).
import { assert, assertEquals, assertInstanceOf } from "jsr:@std/assert@1.0.19";
import { get } from "../_shared/firestore.ts";
import { call, createBody, done, NOW, seed, sent } from "./testing.ts";

const reply = (reviewId: string, text: unknown) => ({ action: "reply", reviewId, text });

/** venueA có một đánh giá `b-done` của player1. */
async function seedWithReview() {
  await seed({ "b-done": done(1) });
  assertEquals((await call("player1", createBody("b-done"))).status, 201);
  sent.length = 0;
}

Deno.test("chủ đúng cơ sở phản hồi lần đầu: hasOwnerReply = true, gửi REVIEW_REPLY, tổng điểm không đổi", async () => {
  await seedWithReview();
  const before = await get("venues/venueA");

  const r = await call("owner1", reply("b-done", "  Cảm ơn bạn, sân sẽ thay đèn tuần sau.  "), "OWNER");
  assertEquals(r.status, 200, JSON.stringify(r.body));
  assertEquals(r.body.ownerReply, {
    text: "Cảm ơn bạn, sân sẽ thay đèn tuần sau.",
    repliedAt: NOW.toISOString(),
    editedAt: null,
  });

  const review = await get("reviews/b-done");
  assertEquals(review?.hasOwnerReply, true);
  const ownerReply = review?.ownerReply as Record<string, unknown>;
  assertEquals(ownerReply.text, "Cảm ơn bạn, sân sẽ thay đèn tuần sau.");
  assertInstanceOf(ownerReply.repliedAt, Date);
  assertEquals(ownerReply.editedAt, null);

  const after = await get("venues/venueA");
  assertEquals([after?.ratingSums, after?.ratingCount, after?.ratingAvg], [before?.ratingSums, before?.ratingCount, before?.ratingAvg]);
  assertEquals(sent, [{ type: "REVIEW_REPLY", recipientUid: "player1", data: { reviewId: "b-done", venueId: "venueA" } }]);
});

Deno.test("sửa phản hồi lần 2: editedAt có giá trị, repliedAt giữ nguyên, không gửi thông báo lại", async () => {
  await seedWithReview();
  assertEquals((await call("owner1", reply("b-done", "Lần 1"), "OWNER")).status, 200);
  const first = (await get("reviews/b-done"))?.ownerReply as Record<string, unknown>;
  sent.length = 0;

  const r = await call("owner1", reply("b-done", "Lần 2"), "OWNER");
  assertEquals(r.status, 200);
  assertEquals(r.body.ownerReply.text, "Lần 2");
  assert(r.body.ownerReply.editedAt);

  const second = (await get("reviews/b-done"))?.ownerReply as Record<string, unknown>;
  assertEquals(second.text, "Lần 2");
  assertEquals((second.repliedAt as Date).getTime(), (first.repliedAt as Date).getTime());
  assertInstanceOf(second.editedAt, Date);
  assertEquals(sent, []);
});

Deno.test("chủ cơ sở khác, người chơi, chủ chưa duyệt → 403 NOT_OWNER", async () => {
  await seedWithReview();
  for (const [uid, role] of [["owner2", "OWNER"], ["player2", "PLAYER"], ["player1", "PLAYER"], ["owner3", "OWNER"]]) {
    const r = await call(uid, reply("b-done", "Xin chào"), role);
    assertEquals([r.status, r.body.error.code], [403, "NOT_OWNER"], uid);
  }
  assertEquals((await get("reviews/b-done"))?.hasOwnerReply, false);
});

Deno.test("text rỗng hoặc > 500 ký tự → 400; đánh giá không tồn tại → 404", async () => {
  await seedWithReview();
  for (const text of ["", "   ", "x".repeat(501), 42, null]) {
    const r = await call("owner1", reply("b-done", text), "OWNER");
    assertEquals([r.status, r.body.error.code], [400, "INVALID_ARGUMENT"], String(text).slice(0, 10));
  }
  assertEquals((await call("owner1", reply("b-done", "x".repeat(500)), "OWNER")).status, 200);
  const missing = await call("owner1", reply("b-missing", "Xin chào"), "OWNER");
  assertEquals([missing.status, missing.body.error.code], [404, "NOT_FOUND"]);
});

Deno.test("người viết đã xóa tài khoản (userId = null): phản hồi được, không gửi thông báo", async () => {
  await seedWithReview();
  const { commit, updateWrite } = await import("../_shared/firestore.ts");
  await commit([updateWrite("reviews/b-done", { userId: null, authorDeleted: true })]);
  const r = await call("owner1", reply("b-done", "Cảm ơn"), "OWNER");
  assertEquals(r.status, 200);
  assertEquals(sent, []);
});
