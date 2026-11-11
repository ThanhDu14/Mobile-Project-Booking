// T072 (spec 070, dùng cho spec 120): `applyReviewVisibility` ẩn/hiện đánh giá và cộng/trừ tổng điểm cơ sở
// trong transaction của người gọi. Cần Firebase Emulator (xem review/create.test.ts).
import { assertEquals } from "jsr:@std/assert@1.0.19";
import { commit, createWrite, get, runTransaction } from "./firestore.ts";
import { applyReviewVisibility } from "./reviews.ts";
import { clearEmulators } from "./testing.ts";

const review = (ratings: Record<string, number>, overall: number, status = "VISIBLE") => ({
  venueId: "venueA",
  userId: "player1",
  ratings,
  overall,
  status,
});

/** venueA có 2 đánh giá VISIBLE: r1 = 5/4/3 (4.0), r2 = 1/1/1 (1.0). */
async function seed() {
  await clearEmulators();
  await commit([
    createWrite("venues/venueA", {
      name: "Sân A",
      ratingSums: { court: 6, cleanliness: 5, service: 4, overall10: 50 },
      ratingCount: 2,
      ratingAvg: 2.5,
    }),
    createWrite("reviews/r1", review({ court: 5, cleanliness: 4, service: 3 }, 4.0)),
    createWrite("reviews/r2", review({ court: 1, cleanliness: 1, service: 1 }, 1.0)),
  ]);
}

const setVisible = (id: string, visible: boolean) =>
  runTransaction(async (tx) => {
    const writes = await applyReviewVisibility(tx, id, visible);
    return { result: writes.length, writes };
  });

Deno.test("ẩn đánh giá VISIBLE: status = HIDDEN, ratingCount −1, ratingSums − điểm", async () => {
  await seed();
  assertEquals(await setVisible("r1", false), 2);
  assertEquals((await get("reviews/r1"))?.status, "HIDDEN");
  const venue = await get("venues/venueA");
  assertEquals(venue?.ratingSums, { court: 1, cleanliness: 1, service: 1, overall10: 10 });
  assertEquals([venue?.ratingCount, venue?.ratingAvg], [1, 1]);
});

Deno.test("hiện lại đánh giá HIDDEN: ratingCount +1, ratingSums + điểm", async () => {
  await seed();
  await setVisible("r1", false);
  assertEquals(await setVisible("r1", true), 2);
  assertEquals((await get("reviews/r1"))?.status, "VISIBLE");
  const venue = await get("venues/venueA");
  assertEquals(venue?.ratingSums, { court: 6, cleanliness: 5, service: 4, overall10: 50 });
  assertEquals([venue?.ratingCount, venue?.ratingAvg], [2, 2.5]);
});

Deno.test("trạng thái đã đúng hoặc đánh giá không tồn tại → không ghi gì", async () => {
  await seed();
  assertEquals(await setVisible("r1", true), 0);
  assertEquals(await setVisible("missing", false), 0);
  assertEquals((await get("venues/venueA"))?.ratingCount, 2);
});

Deno.test("ẩn đánh giá cuối cùng → ratingCount 0, ratingAvg 0", async () => {
  await seed();
  await setVisible("r1", false);
  await setVisible("r2", false);
  const venue = await get("venues/venueA");
  assertEquals([venue?.ratingCount, venue?.ratingAvg], [0, 0]);
  assertEquals(venue?.ratingSums, { court: 0, cleanliness: 0, service: 0, overall10: 0 });
});
