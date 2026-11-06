// T018 (spec 070): tính tổng điểm của cơ sở theo bảng "Ảnh hưởng tới tổng điểm" trong data-model.md.
// Hàm thuần, không cần Emulator: `deno test supabase/functions/_shared/reviews.test.ts`.
import { assertEquals } from "jsr:@std/assert@1.0.19";
import { applyDelta, computeOverall, EMPTY_VENUE_RATING, parseRatings, starBucket, type VenueRating } from "./reviews.ts";

const r543 = { court: 5, cleanliness: 4, service: 3 }; // overall 4.0
const r554 = { court: 5, cleanliness: 5, service: 4 }; // overall 4.7
const r111 = { court: 1, cleanliness: 1, service: 1 }; // overall 1.0

const visible = (ratings: typeof r543) => ({ ratings, status: "VISIBLE" });
const hidden = (ratings: typeof r543) => ({ ratings, status: "HIDDEN" });

Deno.test("computeOverall: trung bình 3 tiêu chí, làm tròn 1 chữ số", () => {
  assertEquals(computeOverall(r543), 4.0);
  assertEquals(computeOverall(r554), 4.7);
  assertEquals(computeOverall({ court: 4, cleanliness: 4, service: 5 }), 4.3);
  assertEquals(computeOverall(r111), 1.0);
});

Deno.test("starBucket: round(overall), nằm trong 1–5", () => {
  assertEquals(starBucket(4.0), 4);
  assertEquals(starBucket(4.7), 5);
  assertEquals(starBucket(4.3), 4);
  assertEquals(starBucket(4.5), 5);
  assertEquals(starBucket(1.0), 1);
});

Deno.test("parseRatings: đủ 3 khóa, số nguyên 1–5; sai thì null", () => {
  assertEquals(parseRatings(r543), r543);
  assertEquals(parseRatings({ court: 5, cleanliness: 4 }), null);
  assertEquals(parseRatings({ court: 6, cleanliness: 4, service: 3 }), null);
  assertEquals(parseRatings({ court: 0, cleanliness: 4, service: 3 }), null);
  assertEquals(parseRatings({ court: 4.5, cleanliness: 4, service: 3 }), null);
  assertEquals(parseRatings({ court: "5", cleanliness: 4, service: 3 }), null);
  assertEquals(parseRatings(null), null);
});

Deno.test("create (VISIBLE): ratingCount +1, ratingSums + điểm mới", () => {
  const v = applyDelta(EMPTY_VENUE_RATING, null, visible(r543));
  assertEquals(v, {
    ratingSums: { court: 5, cleanliness: 4, service: 3, overall10: 40 },
    ratingCount: 1,
    ratingAvg: 4.0,
  });
  const v2 = applyDelta(v, null, visible(r554));
  assertEquals(v2, {
    ratingSums: { court: 10, cleanliness: 9, service: 7, overall10: 87 },
    ratingCount: 2,
    ratingAvg: 4.4, // 87 / 10 / 2 = 4.35 → 4.4
  });
});

Deno.test("update (VISIBLE): count giữ nguyên, − điểm cũ + điểm mới", () => {
  const start = applyDelta(applyDelta(EMPTY_VENUE_RATING, null, visible(r543)), null, visible(r111));
  const v = applyDelta(start, visible(r111), visible(r554));
  assertEquals(v.ratingCount, 2);
  assertEquals(v.ratingSums, { court: 10, cleanliness: 9, service: 7, overall10: 87 });
});

Deno.test("delete (VISIBLE): count −1, − điểm cũ; còn 0 đánh giá thì ratingAvg = 0", () => {
  const one = applyDelta(EMPTY_VENUE_RATING, null, visible(r543));
  assertEquals(applyDelta(one, visible(r543), null), EMPTY_VENUE_RATING);
});

Deno.test("delete (HIDDEN) và sửa đánh giá HIDDEN: tổng điểm không đổi", () => {
  const one = applyDelta(EMPTY_VENUE_RATING, null, visible(r543));
  assertEquals(applyDelta(one, hidden(r554), null), one);
  assertEquals(applyDelta(one, hidden(r554), hidden(r111)), one);
});

Deno.test("ẩn (VISIBLE → HIDDEN) −1; hiện lại (HIDDEN → VISIBLE) +1", () => {
  const two = applyDelta(applyDelta(EMPTY_VENUE_RATING, null, visible(r543)), null, visible(r554));
  const hiddenOne = applyDelta(two, visible(r554), hidden(r554));
  assertEquals(hiddenOne, applyDelta(EMPTY_VENUE_RATING, null, visible(r543)));
  assertEquals(applyDelta(hiddenOne, hidden(r554), visible(r554)), two);
});

Deno.test("venue chưa có ratingSums (dữ liệu seed cũ): suy ra từ ratingAvg × ratingCount", () => {
  const legacy = { ratingAvg: 4.5, ratingCount: 2 };
  const v = applyDelta(legacy, null, visible(r543));
  assertEquals(v.ratingCount, 3);
  assertEquals(v.ratingSums.overall10, 90 + 40);
  assertEquals(v.ratingAvg, 4.3); // 130 / 10 / 3 = 4.33
});

Deno.test("không bao giờ ra số âm khi dữ liệu lệch", () => {
  const v: VenueRating = applyDelta(EMPTY_VENUE_RATING, visible(r543), null);
  assertEquals(v, EMPTY_VENUE_RATING);
});
