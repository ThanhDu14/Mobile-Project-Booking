// Edge Function `review` (spec 070, C; contracts/edge-function-review.md). Mọi ghi đánh giá đi qua đây:
// kiểm tra điều kiện và cộng/trừ tổng điểm của cơ sở trong cùng một Firestore transaction (research R1).
//
// Đã có: `create` (US1). `update`, `delete` (US5), `reply` (US4), `discardPhotos` (US2) trả
// `501 NOT_IMPLEMENTED` cho tới khi làm các story đó.
import { errorResponse, HttpError, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { createWrite, FirestoreError, type FirestoreTransaction, updateWrite } from "../_shared/firestore.ts";
import { applyDelta, computeOverall, parseRatings, type Ratings, starBucket } from "../_shared/reviews.ts";

export interface Notification {
  type: "REVIEW_NEW" | "REVIEW_REPLY";
  recipientUid: string;
  data: Record<string, string>;
}

export interface ReviewDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "runTransaction">;
  /** Gửi thông báo sau khi commit; lỗi chỉ ghi log (research R8). */
  notify: (n: Notification) => Promise<void>;
  now: () => Date;
}

const DAY_MS = 24 * 60 * 60 * 1000;
const REVIEW_WINDOW_MS = 7 * DAY_MS;
const MAX_COMMENT = 1000;
const MAX_PHOTOS = 3;
/** Nhiều người đánh giá cùng một cơ sở cùng lúc tranh chấp `venues/{id}`; cho thử lại nhiều hơn mặc định. */
const MAX_TX_ATTEMPTS = 8;
const BACKOFF_BASE_MS = 50;

/**
 * `runTransaction` của `_shared` thử lại ngay khi `ABORTED`. Các transaction cùng đọc `venues/{id}` vì vậy
 * thử lại đồng loạt và lại chờ khóa của nhau (Emulator: "Transaction lock timeout" sau ~2 s, lặp mãi).
 * Ở đây mỗi lần chỉ chạy 1 lượt rồi chờ ngẫu nhiên tăng dần để các transaction tách nhau ra.
 * TODO: chuyển backoff này vào `_shared/firestore.ts` khi B/A thống nhất.
 */
async function runWithBackoff<T>(
  firestore: ReviewDeps["firestore"],
  operation: Parameters<typeof defaultFirestore.runTransaction<T>>[0],
): Promise<T> {
  for (let attempt = 1; ; attempt++) {
    try {
      return await firestore.runTransaction(operation, 1);
    } catch (e) {
      if (!(e instanceof FirestoreError && e.code === "ABORTED") || attempt >= MAX_TX_ATTEMPTS) throw e;
      const cap = BACKOFF_BASE_MS * 2 ** attempt;
      await new Promise((resolve) => setTimeout(resolve, cap / 2 + Math.random() * cap));
    }
  }
}

const invalid = (message: string) => new HttpError(400, "INVALID_ARGUMENT", message);

async function readBody(req: Request): Promise<Record<string, unknown>> {
  try {
    const body = await req.json();
    return body && typeof body === "object" && !Array.isArray(body) ? body : {};
  } catch {
    return {};
  }
}

export async function handleReview(req: Request, deps: ReviewDeps = defaultDeps()): Promise<Response> {
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
  try {
    const user = await requireUser(req);
    const body = await readBody(req);
    switch (body.action) {
      case "create":
        return await create(user.uid, body, deps);
      case "update":
      case "delete":
      case "reply":
      case "discardPhotos":
        throw new HttpError(501, "NOT_IMPLEMENTED", `Action ${body.action} chưa được hỗ trợ`);
      default:
        throw invalid("action không hợp lệ");
    }
  } catch (e) {
    return toErrorResponse(e);
  }
}

// ---------- create ----------

interface CreateInput {
  bookingId: string;
  ratings: Ratings;
  comment: string;
  photoPaths: string[];
}

function parseCreate(uid: string, body: Record<string, unknown>): CreateInput {
  const bookingId = typeof body.bookingId === "string" ? body.bookingId.trim() : "";
  if (!bookingId || bookingId.includes("/")) throw invalid("Thiếu hoặc sai bookingId");

  const ratings = parseRatings(body.ratings);
  if (!ratings) throw invalid("Cần chấm đủ 3 tiêu chí, mỗi tiêu chí 1–5 sao");

  if (body.comment != null && typeof body.comment !== "string") throw invalid("Bình luận không hợp lệ");
  const comment = ((body.comment as string | undefined) ?? "").trim();
  if ([...comment].length > MAX_COMMENT) throw invalid(`Bình luận tối đa ${MAX_COMMENT} ký tự`);

  const rawPaths = body.photoPaths ?? [];
  if (!Array.isArray(rawPaths) || rawPaths.some((p) => typeof p !== "string")) throw invalid("photoPaths không hợp lệ");
  const photoPaths = rawPaths as string[];
  if (photoPaths.length > MAX_PHOTOS) throw invalid(`Tối đa ${MAX_PHOTOS} ảnh`);
  const prefix = `${uid}/${bookingId}/`;
  if (photoPaths.some((p) => !p.startsWith(prefix) || p.length === prefix.length || p.includes(".."))) {
    throw invalid("Đường dẫn ảnh không thuộc đánh giá này");
  }
  // TODO(T042, US2): kiểm tra object tồn tại trong bucket `review-photos` → 409 PHOTO_MISSING.
  return { bookingId, ratings, comment, photoPaths };
}

const sameContent = (review: Record<string, unknown>, input: CreateInput): boolean => {
  const r = parseRatings(review.ratings);
  const paths = Array.isArray(review.photoPaths) ? review.photoPaths : [];
  return !!r && r.court === input.ratings.court && r.cleanliness === input.ratings.cleanliness &&
    r.service === input.ratings.service && review.comment === input.comment &&
    paths.length === input.photoPaths.length && paths.every((p, i) => p === input.photoPaths[i]);
};

const asDate = (v: unknown): Date | null => (v instanceof Date && !Number.isNaN(v.getTime()) ? v : null);

interface CreateResult {
  created: boolean;
  overall: number;
  createdAt: Date;
  canEditUntil: Date;
  venue: { ratingAvg: number; ratingCount: number };
  venueId: string;
  ownerId: string | null;
}

async function create(uid: string, body: Record<string, unknown>, deps: ReviewDeps): Promise<Response> {
  const input = parseCreate(uid, body);
  const now = deps.now();

  const result = await runWithBackoff<CreateResult>(deps.firestore, async (tx: FirestoreTransaction) => {
    const booking = await tx.get(`bookings/${input.bookingId}`);
    if (!booking) throw new HttpError(404, "NOT_FOUND", "Không tìm thấy đơn đặt sân");
    if (booking.userId !== uid) throw new HttpError(403, "NOT_OWNER", "Đơn này không phải của bạn");

    const venueId = typeof booking.venueId === "string" ? booking.venueId : "";
    const [existing, venue, user] = await Promise.all([
      tx.get(`reviews/${input.bookingId}`),
      venueId ? tx.get(`venues/${venueId}`) : Promise.resolve(null),
      tx.get(`users/${uid}`),
    ]);
    if (!venue) throw new HttpError(404, "NOT_FOUND", "Không tìm thấy cơ sở của đơn này");

    // Gửi lại (mất mạng, bấm nhiều lần): cùng người, cùng nội dung → trả bản đã lưu (FR-002).
    if (existing) {
      if (existing.userId !== uid || !sameContent(existing, input)) {
        throw new HttpError(409, "ALREADY_REVIEWED", "Bạn đã đánh giá lượt chơi này");
      }
      const completedAt = asDate(existing.completedAt) ?? now;
      return {
        result: {
          created: false,
          overall: Number(existing.overall),
          createdAt: asDate(existing.createdAt) ?? now,
          canEditUntil: new Date(completedAt.getTime() + REVIEW_WINDOW_MS),
          venue: { ratingAvg: Number(venue.ratingAvg ?? 0), ratingCount: Number(venue.ratingCount ?? 0) },
          venueId,
          ownerId: null,
        },
        writes: [],
      };
    }

    if (user?.accountStatus === "LOCKED") throw new HttpError(403, "ACCOUNT_LOCKED", "Tài khoản của bạn đã bị khóa");
    const ownerId = typeof venue.ownerId === "string" ? venue.ownerId : null;
    if (ownerId === uid) throw new HttpError(403, "OWN_VENUE", "Bạn không thể đánh giá sân của chính mình");

    const completedAt = asDate(booking.completedAt);
    if (booking.status !== "COMPLETED" || !completedAt) {
      throw new HttpError(409, "BOOKING_NOT_COMPLETED", "Bạn chỉ đánh giá được sau khi đã chơi");
    }
    const canEditUntil = new Date(completedAt.getTime() + REVIEW_WINDOW_MS);
    if (now.getTime() > canEditUntil.getTime()) {
      throw new HttpError(409, "REVIEW_WINDOW_CLOSED", "Đã hết hạn đánh giá");
    }

    const overall = computeOverall(input.ratings);
    const rating = applyDelta(venue, null, { ratings: input.ratings, status: "VISIBLE" });
    return {
      result: {
        created: true,
        overall,
        createdAt: now,
        canEditUntil,
        venue: { ratingAvg: rating.ratingAvg, ratingCount: rating.ratingCount },
        venueId,
        ownerId,
      },
      writes: [
        createWrite(`reviews/${input.bookingId}`, {
          targetType: "VENUE",
          bookingId: input.bookingId,
          venueId,
          userId: uid,
          userSnapshot: {
            displayName: typeof user?.displayName === "string" ? user.displayName : null,
            avatarPath: typeof user?.avatarPath === "string" ? user.avatarPath : null,
          },
          authorDeleted: false,
          ratings: input.ratings,
          overall,
          starBucket: starBucket(overall),
          comment: input.comment,
          photoPaths: input.photoPaths,
          hasPhotos: input.photoPaths.length > 0,
          ownerReply: null,
          hasOwnerReply: false,
          status: "VISIBLE",
          completedAt,
          editedAt: null,
        }, ["createdAt", "updatedAt"]),
        updateWrite(`venues/${venueId}`, {
          ratingSums: rating.ratingSums,
          ratingCount: rating.ratingCount,
          ratingAvg: rating.ratingAvg,
        }),
      ],
    };
  });

  if (result.created && result.ownerId) {
    try {
      await deps.notify({
        type: "REVIEW_NEW",
        recipientUid: result.ownerId,
        data: { reviewId: input.bookingId, venueId: result.venueId },
      });
    } catch (e) {
      console.error("[review] gửi REVIEW_NEW thất bại", e);
    }
  }

  return Response.json({
    review: {
      id: input.bookingId,
      overall: result.overall,
      createdAt: result.createdAt.toISOString(),
      canEditUntil: result.canEditUntil.toISOString(),
    },
    venue: result.venue,
  }, { status: result.created ? 201 : 200 });
}

// ---------- Mặc định khi chạy thật ----------

/**
 * Gọi Edge Function `send-notification` của D bằng service role. TODO(D, spec 100): envelope chưa chốt
 * (contracts/event-integration.md của 100); đây là đề xuất `{ type, recipientUid, data }`. Chưa có
 * function thì lời gọi trả 404 và chỉ ghi log.
 */
async function sendNotification(n: Notification): Promise<void> {
  const url = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !key) {
    console.warn("[review] thiếu SUPABASE_URL/SUPABASE_SERVICE_ROLE_KEY, bỏ qua thông báo", n.type);
    return;
  }
  const res = await fetch(`${url}/functions/v1/send-notification`, {
    method: "POST",
    headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
    body: JSON.stringify(n),
  });
  await res.body?.cancel();
  if (!res.ok) throw new Error(`send-notification ${res.status}`);
}

function defaultDeps(): ReviewDeps {
  return { firestore: defaultFirestore, notify: sendNotification, now: () => new Date() };
}
