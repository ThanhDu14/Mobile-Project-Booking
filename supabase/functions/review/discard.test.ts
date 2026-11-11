// T039 (spec 070 US2): action `discardPhotos` — xóa ảnh đã tải lên khi bỏ ảnh hoặc bỏ đánh giá đang viết.
// Chạy giống create.test.ts (cần Firebase Emulator).
import { assertEquals } from "jsr:@std/assert@1.0.19";
import { call, createBody, done, seed, storage } from "./testing.ts";

const discard = (bookingId: string, photoPaths: unknown) => ({ action: "discardPhotos", bookingId, photoPaths });

Deno.test("discardPhotos xóa ảnh của mình chưa thuộc đánh giá nào", async () => {
  const a = "player1/b-done/a.jpg", b = "player1/b-done/b.jpg";
  await seed({ "b-done": done(1) }, [a, b]);
  const r = await call("player1", discard("b-done", [a]));
  assertEquals([r.status, r.body], [200, { deleted: 1 }]);
  assertEquals([...storage.objects], [b]);
});

Deno.test("discardPhotos không xóa ảnh đã thuộc đánh giá đã lưu", async () => {
  const kept = "player1/b-done/kept.jpg", draft = "player1/b-done/draft.jpg";
  await seed({ "b-done": done(1) }, [kept, draft]);
  assertEquals((await call("player1", createBody("b-done", { photoPaths: [kept] }))).status, 201);

  const r = await call("player1", discard("b-done", [kept, draft]));
  assertEquals([r.status, r.body], [200, { deleted: 1 }]);
  assertEquals([...storage.objects], [kept]);
});

Deno.test("discardPhotos: path của người khác hoặc đơn khác → 400, không xóa gì", async () => {
  const other = "player2/b-done/a.jpg", otherBooking = "player1/b-x/a.jpg";
  await seed({ "b-done": done(1) }, [other, otherBooking]);
  for (const paths of [[other], [otherBooking], "player1/b-done/a.jpg", [1]]) {
    const r = await call("player1", discard("b-done", paths));
    assertEquals([r.status, r.body.error.code], [400, "INVALID_ARGUMENT"], JSON.stringify(paths));
  }
  assertEquals(storage.removed, []);
});

Deno.test("discardPhotos với path không còn trong Storage → deleted = 0", async () => {
  await seed({ "b-done": done(1) });
  const r = await call("player1", discard("b-done", ["player1/b-done/gone.jpg"]));
  assertEquals([r.status, r.body], [200, { deleted: 0 }]);
});
