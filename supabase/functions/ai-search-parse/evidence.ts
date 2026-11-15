import type {
  query as firestoreQuery,
  QueryDocument,
} from "../_shared/firestore.ts";
import { normalize, type Preference, preferences } from "./criteria.ts";

type ReviewData = Record<string, unknown>;
type Source = { reviewId: string; version: string };
export type Observation = { source: Source; phrase: string; supports: boolean };

const labels: Record<Preference, [string, string]> = {
  GRIP: ["sàn ít trơn hoặc có độ bám tốt", "sàn trơn hoặc dễ trượt"],
  HIGH_CEILING: ["trần cao", "trần thấp hoặc vướng trần"],
  QUIET: ["không gian yên tĩnh hoặc ít đông", "sân đông hoặc ồn"],
  CLEAN: ["sân sạch sẽ", "sân bẩn hoặc thiếu vệ sinh"],
  GOOD_LIGHT: ["ánh sáng tốt, đủ sáng", "thiếu sáng hoặc chói mắt"],
  VENTILATED: ["không gian thoáng mát", "không gian bí hoặc nóng"],
  GOOD_SERVICE: ["phục vụ tốt hoặc nhân viên nhiệt tình", "phục vụ chưa tốt"],
};

export function isPublicReview(
  data: ReviewData | null,
  venueId: string,
): data is ReviewData & { comment: string } {
  return data !== null && data.status === "VISIBLE" &&
    data.targetType === "VENUE" && data.venueId === venueId &&
    typeof data.comment === "string" && data.comment.trim().length > 0;
}

export async function digest(value: string): Promise<string> {
  const bytes = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(value),
  );
  return Array.from(
    new Uint8Array(bytes),
    (byte) => byte.toString(16).padStart(2, "0"),
  ).join("");
}

/**
 * Version of the evidence a review provides. Only fields that change what the review says count:
 * an owner reply or rating-only edit bumps `updatedAt` but must not invalidate the source.
 */
export function reviewVersion(data: ReviewData): Promise<string> {
  return digest(
    JSON.stringify([
      data.venueId,
      data.targetType,
      data.status,
      data.createdAt,
      data.comment,
    ]),
  );
}

function mentions(text: string, phrase: string): boolean {
  return ` ${text} `.includes(` ${phrase} `);
}

/** Return predefined feature observations. Author fields and free-form review text never enter the model prompt. */
export function reviewObservations(
  comment: string,
  preference: Preference,
): Array<{ phrase: string; supports: boolean }> {
  const dictionary = preferences[preference];
  const clauses = comment.split(/[.!?;\n]|\bnhưng\b/iu).map(normalize);
  let supports = false;
  let disagrees = false;
  for (const clause of clauses) {
    let remaining = clause;
    for (const phrase of dictionary.match) {
      if (!mentions(remaining, phrase)) continue;
      if (
        mentions(remaining, "khong " + phrase) ||
        mentions(remaining, "khong thay " + phrase)
      ) disagrees = true;
      else supports = true;
      remaining = remaining.replaceAll(phrase, " ").replace(/\s+/g, " ").trim();
    }
    if (
      dictionary.counter.some((phrase) =>
        mentions(remaining, phrase) && !mentions(remaining, "khong " + phrase)
      )
    ) {
      disagrees = true;
    }
  }
  const [positive, negative] = labels[preference];
  return [
    ...(supports ? [{ phrase: positive, supports: true }] : []),
    ...(disagrees ? [{ phrase: negative, supports: false }] : []),
  ];
}

export async function observeReview(
  reviewId: string,
  data: ReviewData | null,
  venueId: string,
  preference: Preference,
): Promise<Observation[]> {
  if (!isPublicReview(data, venueId)) return [];
  const observations = reviewObservations(data.comment, preference);
  if (observations.length === 0) return [];
  const source = { reviewId, version: await reviewVersion(data) };
  return observations.map((observation) => ({ ...observation, source }));
}

export async function rankReviews(
  venueIds: string[],
  preference: Preference,
  query: typeof firestoreQuery,
) {
  const candidates: Array<
    { venueId: string; score: number; sources: Source[] }
  > = [];
  // Keep individual reads bounded and use the existing venueId/status/createdAt review index.
  for (let offset = 0; offset < venueIds.length; offset += 6) {
    const group = await Promise.all(
      venueIds.slice(offset, offset + 6).map(async (venueId) => {
        const reviews: QueryDocument[] = await query("reviews", [
          { field: "venueId", op: "EQUAL", value: venueId },
          // Drop-in reviews share the collection and venueId; without this filter they can fill the limit.
          { field: "targetType", op: "EQUAL", value: "VENUE" },
          { field: "status", op: "EQUAL", value: "VISIBLE" },
        ], {
          limit: 10,
          orderBy: { field: "createdAt", direction: "DESCENDING" },
        });
        const observations = (await Promise.all(reviews.map(({ id, data }) =>
          observeReview(id, data, venueId, preference)
        ))).flat();
        const positive = observations.filter((item) =>
          item.supports
        );
        if (positive.length === 0) {
          return null;
        }
        const negative = observations.filter((item) =>
          !item.supports
        );
        const sources = [
          ...new Map([...positive.slice(0, 2), ...negative.slice(0, 1)]
            .map(({ source }) => [source.reviewId, source])).values(),
        ];
        return {
          venueId,
          score: positive.length * 2 - negative.length,
          sources,
        };
      }),
    );
    for (const candidate of group) if (candidate) candidates.push(candidate);
  }
  return candidates.sort((a, b) => b.score - a.score).slice(0, 3)
    .map(({ venueId, sources }) => ({ venueId, sources }));
}

/**
 * Deterministic explanation (no LLM): one supporting observation and, when the sources disagree,
 * one opposing observation so conflicting reviews are shown as different opinions (FR-029).
 */
export function buildExplanation(observations: Observation[]) {
  const support = observations.find((item) => item.supports);
  if (!support) return null;
  const counter = observations.find((item) => !item.supports);
  return [support, ...(counter ? [counter] : [])].map(
    ({ source, phrase, supports }) => ({ source, excerpt: phrase, supports }),
  );
}
