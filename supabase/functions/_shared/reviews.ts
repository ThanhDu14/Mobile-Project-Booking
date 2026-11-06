// Tính điểm đánh giá và tổng điểm của cơ sở (spec 070, C; research R2, data-model.md mục 1).
// Dùng trong Edge Function `review` và `applyReviewVisibility()` cho spec 120 (D, T072).
//
// `venues.ratingSums` lưu tổng từng tiêu chí và `overall10` (= tổng điểm tổng × 10, số nguyên) để
// cộng/trừ O(1) mà không bị sai số Double. Chỉ đánh giá `VISIBLE` được tính.

export interface Ratings {
  court: number;
  cleanliness: number;
  service: number;
}

export interface RatingSums {
  court: number;
  cleanliness: number;
  service: number;
  overall10: number;
}

export interface VenueRating {
  ratingSums: RatingSums;
  ratingCount: number;
  ratingAvg: number;
}

/** Phần của một đánh giá ảnh hưởng tới tổng điểm. `status` khác `VISIBLE` thì không được tính. */
export interface ScoredReview {
  ratings: Ratings;
  status: string;
}

export const EMPTY_VENUE_RATING: VenueRating = Object.freeze({
  ratingSums: Object.freeze({ court: 0, cleanliness: 0, service: 0, overall10: 0 }),
  ratingCount: 0,
  ratingAvg: 0,
}) as VenueRating;

const CRITERIA = ["court", "cleanliness", "service"] as const;

const round1 = (x: number): number => Math.round(x * 10) / 10;

/** Đủ 3 tiêu chí, mỗi tiêu chí là số nguyên 1–5; sai thì `null` (FR-003). */
export function parseRatings(value: unknown): Ratings | null {
  if (!value || typeof value !== "object") return null;
  const v = value as Record<string, unknown>;
  for (const key of CRITERIA) {
    const n = v[key];
    if (typeof n !== "number" || !Number.isInteger(n) || n < 1 || n > 5) return null;
  }
  return { court: v.court as number, cleanliness: v.cleanliness as number, service: v.service as number };
}

/** Trung bình 3 tiêu chí, làm tròn 1 chữ số (FR-003). */
export function computeOverall(r: Ratings): number {
  return round1((r.court + r.cleanliness + r.service) / 3);
}

/** Số sao dùng để lọc (FR-013): `round(overall)` trong 1–5. */
export function starBucket(overall: number): number {
  return Math.min(5, Math.max(1, Math.round(overall)));
}

function overall10(r: Ratings): number {
  return Math.round(computeOverall(r) * 10);
}

const num = (v: unknown): number => (typeof v === "number" && Number.isFinite(v) ? v : 0);

/**
 * Đọc tổng điểm từ document `venues`. Cơ sở trong dữ liệu seed cũ chưa có `ratingSums` thì suy ra từ
 * `ratingAvg × ratingCount` để trung bình không bị đặt lại khi có đánh giá đầu tiên qua function.
 */
export function readVenueRating(venue: Record<string, unknown> | VenueRating | null | undefined): VenueRating {
  if (!venue) return EMPTY_VENUE_RATING;
  const count = Math.max(0, Math.round(num(venue.ratingCount)));
  const avg = num(venue.ratingAvg);
  const sums = venue.ratingSums as Record<string, unknown> | undefined;
  if (sums && typeof sums === "object") {
    return {
      ratingSums: {
        court: num(sums.court),
        cleanliness: num(sums.cleanliness),
        service: num(sums.service),
        overall10: num(sums.overall10),
      },
      ratingCount: count,
      ratingAvg: avg,
    };
  }
  const perCriterion = Math.round(avg * count);
  return {
    ratingSums: {
      court: perCriterion,
      cleanliness: perCriterion,
      service: perCriterion,
      overall10: Math.round(avg * 10 * count),
    },
    ratingCount: count,
    ratingAvg: avg,
  };
}

const counts = (review: ScoredReview | null | undefined): review is ScoredReview =>
  !!review && review.status === "VISIBLE";

/**
 * Tổng điểm mới sau khi đánh giá đổi từ `oldReview` sang `newReview` (`null` = chưa có/đã xóa).
 * Bao trọn bảng "Ảnh hưởng tới tổng điểm": tạo, sửa, xóa, ẩn, hiện lại.
 */
export function applyDelta(
  venue: Record<string, unknown> | VenueRating | null | undefined,
  oldReview?: ScoredReview | null,
  newReview?: ScoredReview | null,
): VenueRating {
  const current = readVenueRating(venue);
  const sums = { ...current.ratingSums };
  let count = current.ratingCount;

  if (counts(oldReview)) {
    for (const key of CRITERIA) sums[key] -= oldReview.ratings[key];
    sums.overall10 -= overall10(oldReview.ratings);
    count -= 1;
  }
  if (counts(newReview)) {
    for (const key of CRITERIA) sums[key] += newReview.ratings[key];
    sums.overall10 += overall10(newReview.ratings);
    count += 1;
  }

  if (count <= 0) return { ...EMPTY_VENUE_RATING, ratingSums: { ...EMPTY_VENUE_RATING.ratingSums } };
  for (const key of [...CRITERIA, "overall10"] as const) sums[key] = Math.max(0, sums[key]);
  return { ratingSums: sums, ratingCount: count, ratingAvg: round1(sums.overall10 / 10 / count) };
}
