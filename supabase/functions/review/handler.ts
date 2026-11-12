// Edge Function `review` (spec 070, C; contracts/edge-function-review.md). Mọi ghi đánh giá đi qua đây:
// kiểm tra điều kiện và cộng/trừ tổng điểm của cơ sở trong cùng một Firestore transaction (research R1).
//
// Action: `create` (US1, US2), `update`, `delete` (US5), `reply` (US4), `discardPhotos` (US2).
// Ảnh nằm trong bucket `review-photos` theo path `{uid}/{reviewId}/{file}` (research R4).
// reviewId = bookingId với đánh giá sân (`targetType = VENUE`), hoặc `{sessionId}_{uid}` với đánh giá buổi
// vãng lai (`targetType = DROP_IN`, spec 080 US5/R10): `create` nhận `bookingId` hoặc `sessionId` (đúng một).
import { type AppRole, errorResponse, HttpError, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import {
  createWrite,
  deleteWrite,
  FirestoreError,
  type FirestoreTransaction,
  updateWrite,
  type Write,
} from "../_shared/firestore.ts";
import { applyDelta, computeOverall, parseRatings, type Ratings, starBucket, type VenueRating } from "../_shared/reviews.ts";

export interface Notification {
  type: "REVIEW_NEW" | "REVIEW_REPLY";
  recipientUid: string;
  data: Record<string, string>;
}

/** Thao tác trên bucket `review-photos` bằng service role (client không được xóa, contracts/security-rules.md §2). */
export interface ReviewStorage {
  exists(path: string): Promise<boolean>;
  /** Mọi path bắt đầu bằng [prefix] (ví dụ `uid/bookingId/`). */
  list(prefix: string): Promise<string[]>;
  /** Xóa các path; trả số object thực sự bị xóa. */
  remove(paths: string[]): Promise<number>;
}

export interface ReviewDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "runTransaction">;
  storage: ReviewStorage;
  /** Gửi thông báo sau khi commit; lỗi chỉ ghi log (research R8). */
  notify: (n: Notification) => Promise<void>;
  now: () => Date;
}

const DAY_MS = 24 * 60 * 60 * 1000;
const REVIEW_WINDOW_MS = 7 * DAY_MS;
const MAX_COMMENT = 1000;
const MAX_PHOTOS = 3;
const MAX_REPLY = 500;
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
        return await update(user.uid, body, deps);
      case "delete":
        return await remove(user.uid, body, deps);
      case "reply":
        return await reply(user.uid, user.appRole, body, deps);
      case "discardPhotos":
        return await discardPhotos(user.uid, body, deps);
      default:
        throw invalid("action không hợp lệ");
    }
  } catch (e) {
    return toErrorResponse(e);
  }
}

// ---------- Dùng chung ----------

function parseId(value: unknown, name: string): string {
  const id = typeof value === "string" ? value.trim() : "";
  if (!id || id.includes("/")) throw invalid(`Thiếu hoặc sai ${name}`);
  return id;
}

/** Danh sách path ảnh; mỗi path phải nằm trong thư mục `{uid}/{reviewId}/` (FR-005, research R4). */
function parsePhotoPaths(value: unknown, uid: string, reviewId: string, max = MAX_PHOTOS): string[] {
  const raw = value ?? [];
  if (!Array.isArray(raw) || raw.some((p) => typeof p !== "string")) throw invalid("photoPaths không hợp lệ");
  const paths = raw as string[];
  if (paths.length > max) throw invalid(`Tối đa ${max} ảnh`);
  const prefix = `${uid}/${reviewId}/`;
  if (paths.some((p) => !p.startsWith(prefix) || p.length === prefix.length || p.includes(".."))) {
    throw invalid("Đường dẫn ảnh không thuộc đánh giá này");
  }
  return paths;
}

/** Ảnh phải tải lên xong trước khi gửi (FR-006); ảnh đã thuộc đánh giá thì bỏ qua. */
async function requirePhotosExist(storage: ReviewStorage, paths: string[], known: string[] = []): Promise<void> {
  const toCheck = paths.filter((p) => !known.includes(p));
  const found = await Promise.all(toCheck.map((p) => storage.exists(p)));
  if (found.some((ok) => !ok)) throw new HttpError(409, "PHOTO_MISSING", "Có ảnh chưa tải lên xong");
}

/** Xóa ảnh sau commit; lỗi chỉ ghi log (đánh giá đã lưu đúng, ảnh thừa không ảnh hưởng người dùng). */
async function removePhotosQuietly(storage: ReviewStorage, paths: string[]): Promise<void> {
  if (paths.length === 0) return;
  try {
    await storage.remove(paths);
  } catch (e) {
    console.error("[review] xóa ảnh thất bại", paths, e);
  }
}

async function notifyQuietly(deps: ReviewDeps, n: Notification): Promise<void> {
  try {
    await deps.notify(n);
  } catch (e) {
    console.error(`[review] gửi ${n.type} thất bại`, e);
  }
}

function requireActive(user: Record<string, unknown> | null): void {
  if (user?.accountStatus === "LOCKED") throw new HttpError(403, "ACCOUNT_LOCKED", "Tài khoản của bạn đã bị khóa");
}

function parseComment(value: unknown): string {
  if (value != null && typeof value !== "string") throw invalid("Bình luận không hợp lệ");
  const comment = ((value as string | undefined) ?? "").trim();
  if ([...comment].length > MAX_COMMENT) throw invalid(`Bình luận tối đa ${MAX_COMMENT} ký tự`);
  return comment;
}

const stringArray = (v: unknown): string[] =>
  Array.isArray(v) ? v.filter((p): p is string => typeof p === "string") : [];

// ---------- create ----------

interface CreateInput {
  /** `bookingId` (đánh giá sân) hoặc `{sessionId}_{uid}` (đánh giá buổi vãng lai). */
  reviewId: string;
  /** Có khi đánh giá buổi vãng lai. */
  sessionId: string | null;
  ratings: Ratings;
  comment: string;
  photoPaths: string[];
}

function parseCreate(uid: string, body: Record<string, unknown>): CreateInput {
  if ((body.bookingId != null) === (body.sessionId != null)) throw invalid("Chỉ gửi một trong bookingId hoặc sessionId");
  const sessionId = body.sessionId != null ? parseId(body.sessionId, "sessionId") : null;
  const reviewId = sessionId ? `${sessionId}_${uid}` : parseId(body.bookingId, "bookingId");

  const ratings = parseRatings(body.ratings);
  if (!ratings) throw invalid("Cần chấm đủ 3 tiêu chí, mỗi tiêu chí 1–5 sao");

  const comment = parseComment(body.comment);
  const photoPaths = parsePhotoPaths(body.photoPaths, uid, reviewId);
  return { reviewId, sessionId, ratings, comment, photoPaths };
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
  await requirePhotosExist(deps.storage, input.photoPaths);
  const now = deps.now();

  const result = await runWithBackoff<CreateResult>(deps.firestore, async (tx: FirestoreTransaction) => {
    // Nguồn đánh giá: đơn đặt sân (đã COMPLETED) hoặc buổi vãng lai (đã CHECKED_IN, đã kết thúc).
    let venueId: string;
    let session: Record<string, unknown> | null = null;
    let booking: Record<string, unknown> | null = null;
    if (input.sessionId) {
      session = await tx.get(`dropInSessions/${input.sessionId}`);
      if (!session) throw new HttpError(404, "NOT_FOUND", "Không tìm thấy buổi vãng lai");
      venueId = typeof session.venueId === "string" ? session.venueId : "";
    } else {
      booking = await tx.get(`bookings/${input.reviewId}`);
      if (!booking) throw new HttpError(404, "NOT_FOUND", "Không tìm thấy đơn đặt sân");
      if (booking.userId !== uid) throw new HttpError(403, "NOT_OWNER", "Đơn này không phải của bạn");
      venueId = typeof booking.venueId === "string" ? booking.venueId : "";
    }

    const [existing, venue, user, registration] = await Promise.all([
      tx.get(`reviews/${input.reviewId}`),
      venueId ? tx.get(`venues/${venueId}`) : Promise.resolve(null),
      tx.get(`users/${uid}`),
      input.sessionId ? tx.get(`dropInSessions/${input.sessionId}/registrations/${uid}`) : Promise.resolve(null),
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

    requireActive(user);
    const ownerId = typeof venue.ownerId === "string" ? venue.ownerId : null;
    if (ownerId === uid || (session && session.ownerId === uid)) {
      throw new HttpError(403, "OWN_VENUE", "Bạn không thể đánh giá sân của chính mình");
    }

    let completedAt: Date | null;
    if (session) {
      const endAt = asDate(session.endAt);
      if (registration?.status !== "CHECKED_IN" || !endAt || now.getTime() < endAt.getTime()) {
        throw new HttpError(409, "NOT_CHECKED_IN", "Bạn chỉ đánh giá được buổi đã tham gia");
      }
      completedAt = endAt;
    } else {
      completedAt = asDate(booking?.completedAt);
      if (booking?.status !== "COMPLETED" || !completedAt) {
        throw new HttpError(409, "BOOKING_NOT_COMPLETED", "Bạn chỉ đánh giá được sau khi đã chơi");
      }
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
        createWrite(`reviews/${input.reviewId}`, {
          ...(input.sessionId
            ? { targetType: "DROP_IN", sessionId: input.sessionId }
            : { targetType: "VENUE", bookingId: input.reviewId }),
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
    await notifyQuietly(deps, {
      type: "REVIEW_NEW",
      recipientUid: result.ownerId,
      data: { reviewId: input.reviewId, venueId: result.venueId },
    });
  }

  return Response.json({
    review: {
      id: input.reviewId,
      overall: result.overall,
      createdAt: result.createdAt.toISOString(),
      canEditUntil: result.canEditUntil.toISOString(),
    },
    venue: result.venue,
  }, { status: result.created ? 201 : 200 });
}

// ---------- update (US5) ----------

interface WriteResult {
  overall: number;
  createdAt: Date;
  canEditUntil: Date;
  venue: { ratingAvg: number; ratingCount: number };
  removedPhotos: string[];
}

const venueRatingOf = (venue: Record<string, unknown> | null) => ({
  ratingAvg: Number(venue?.ratingAvg ?? 0),
  ratingCount: Number(venue?.ratingCount ?? 0),
});

const ratingFields = (r: VenueRating) => ({ ratingSums: r.ratingSums, ratingCount: r.ratingCount, ratingAvg: r.ratingAvg });

async function update(uid: string, body: Record<string, unknown>, deps: ReviewDeps): Promise<Response> {
  const reviewId = parseId(body.reviewId, "reviewId");
  const ratings = parseRatings(body.ratings);
  if (!ratings) throw invalid("Cần chấm đủ 3 tiêu chí, mỗi tiêu chí 1–5 sao");
  const comment = parseComment(body.comment);
  const photoPaths = parsePhotoPaths(body.photoPaths, uid, reviewId);

  // Ảnh mới phải có trong Storage; ảnh đã thuộc đánh giá thì không cần kiểm tra lại.
  const current = await deps.firestore.get(`reviews/${reviewId}`);
  await requirePhotosExist(deps.storage, photoPaths, current?.userId === uid ? stringArray(current.photoPaths) : []);
  const now = deps.now();

  const result = await runWithBackoff<WriteResult>(deps.firestore, async (tx) => {
    const [review, user] = await Promise.all([tx.get(`reviews/${reviewId}`), tx.get(`users/${uid}`)]);
    if (!review) throw new HttpError(404, "NOT_FOUND", "Không tìm thấy đánh giá");
    if (review.userId !== uid) throw new HttpError(403, "NOT_OWNER", "Đánh giá này không phải của bạn");
    requireActive(user);

    const completedAt = asDate(review.completedAt) ?? asDate(review.createdAt) ?? now;
    const canEditUntil = new Date(completedAt.getTime() + REVIEW_WINDOW_MS);
    if (now.getTime() > canEditUntil.getTime()) {
      throw new HttpError(409, "REVIEW_WINDOW_CLOSED", "Đã hết hạn sửa đánh giá");
    }

    const venueId = typeof review.venueId === "string" ? review.venueId : "";
    const venue = venueId ? await tx.get(`venues/${venueId}`) : null;
    const status = typeof review.status === "string" ? review.status : "VISIBLE";
    const oldRatings = parseRatings(review.ratings);
    const overall = computeOverall(ratings);
    const writes: Write[] = [
      updateWrite(`reviews/${reviewId}`, {
        ratings,
        overall,
        starBucket: starBucket(overall),
        comment,
        photoPaths,
        hasPhotos: photoPaths.length > 0,
        editedAt: now,
      }, ["updatedAt"]),
    ];
    let venueRating = venueRatingOf(venue);
    if (venue && status === "VISIBLE") {
      const rating = applyDelta(venue, oldRatings ? { ratings: oldRatings, status } : null, { ratings, status });
      writes.push(updateWrite(`venues/${venueId}`, ratingFields(rating)));
      venueRating = { ratingAvg: rating.ratingAvg, ratingCount: rating.ratingCount };
    }
    return {
      result: {
        overall,
        createdAt: asDate(review.createdAt) ?? now,
        canEditUntil,
        venue: venueRating,
        removedPhotos: stringArray(review.photoPaths).filter((p) => !photoPaths.includes(p)),
      },
      writes,
    };
  });

  await removePhotosQuietly(deps.storage, result.removedPhotos);
  return Response.json({
    review: {
      id: reviewId,
      overall: result.overall,
      createdAt: result.createdAt.toISOString(),
      canEditUntil: result.canEditUntil.toISOString(),
    },
    venue: result.venue,
  });
}

// ---------- delete (US5) ----------

async function remove(uid: string, body: Record<string, unknown>, deps: ReviewDeps): Promise<Response> {
  const reviewId = parseId(body.reviewId, "reviewId");

  const result = await runWithBackoff(deps.firestore, async (tx) => {
    const [review, user] = await Promise.all([tx.get(`reviews/${reviewId}`), tx.get(`users/${uid}`)]);
    if (!review) throw new HttpError(404, "NOT_FOUND", "Không tìm thấy đánh giá");
    if (review.userId !== uid) throw new HttpError(403, "NOT_OWNER", "Đánh giá này không phải của bạn");
    requireActive(user);

    const venueId = typeof review.venueId === "string" ? review.venueId : "";
    const venue = venueId ? await tx.get(`venues/${venueId}`) : null;
    const status = typeof review.status === "string" ? review.status : "VISIBLE";
    const oldRatings = parseRatings(review.ratings);
    const writes: Write[] = [deleteWrite(`reviews/${reviewId}`)];
    let venueRating = venueRatingOf(venue);
    if (venue && status === "VISIBLE" && oldRatings) {
      const rating = applyDelta(venue, { ratings: oldRatings, status }, null);
      writes.push(updateWrite(`venues/${venueId}`, ratingFields(rating)));
      venueRating = { ratingAvg: rating.ratingAvg, ratingCount: rating.ratingCount };
    }
    return { result: { venue: venueRating, photoPaths: stringArray(review.photoPaths) }, writes };
  });

  // Xóa mọi ảnh trong thư mục của đánh giá, kể cả ảnh đã tải mà chưa gắn vào đánh giá.
  let paths = result.photoPaths;
  try {
    paths = [...new Set([...paths, ...await deps.storage.list(`${uid}/${reviewId}/`)])];
  } catch (e) {
    console.error("[review] không liệt kê được ảnh", e);
  }
  await removePhotosQuietly(deps.storage, paths);
  return Response.json({ venue: result.venue });
}

// ---------- reply (US4) ----------

interface ReplyResult {
  text: string;
  repliedAt: Date;
  editedAt: Date | null;
  first: boolean;
  recipientUid: string | null;
  venueId: string;
}

async function reply(uid: string, appRole: AppRole, body: Record<string, unknown>, deps: ReviewDeps): Promise<Response> {
  const reviewId = parseId(body.reviewId, "reviewId");
  if (typeof body.text !== "string") throw invalid("Thiếu nội dung phản hồi");
  const text = body.text.trim();
  if (text.length === 0 || [...text].length > MAX_REPLY) throw invalid(`Phản hồi 1–${MAX_REPLY} ký tự`);
  const notOwner = () => new HttpError(403, "NOT_OWNER", "Bạn không có quyền phản hồi đánh giá này");
  if (appRole !== "OWNER") throw notOwner();
  const now = deps.now();

  const result = await runWithBackoff<ReplyResult>(deps.firestore, async (tx) => {
    const [review, user] = await Promise.all([tx.get(`reviews/${reviewId}`), tx.get(`users/${uid}`)]);
    if (!review) throw new HttpError(404, "NOT_FOUND", "Không tìm thấy đánh giá");
    requireActive(user);
    if (user?.ownerStatus !== "APPROVED") throw notOwner();
    const venueId = typeof review.venueId === "string" ? review.venueId : "";
    const venue = venueId ? await tx.get(`venues/${venueId}`) : null;
    if (!venue || venue.ownerId !== uid) throw notOwner();

    const existing = review.ownerReply && typeof review.ownerReply === "object"
      ? review.ownerReply as Record<string, unknown>
      : null;
    const repliedAt = (existing && asDate(existing.repliedAt)) || now;
    const editedAt = existing ? now : null;
    return {
      result: {
        text,
        repliedAt,
        editedAt,
        first: !existing,
        recipientUid: typeof review.userId === "string" ? review.userId : null,
        venueId,
      },
      writes: [
        updateWrite(`reviews/${reviewId}`, {
          ownerReply: { text, repliedAt, editedAt },
          hasOwnerReply: true,
        }, ["updatedAt"]),
      ],
    };
  });

  if (result.first && result.recipientUid) {
    await notifyQuietly(deps, {
      type: "REVIEW_REPLY",
      recipientUid: result.recipientUid,
      data: { reviewId, venueId: result.venueId },
    });
  }
  return Response.json({
    ownerReply: {
      text: result.text,
      repliedAt: result.repliedAt.toISOString(),
      editedAt: result.editedAt?.toISOString() ?? null,
    },
  });
}

// ---------- discardPhotos (US2) ----------

/** Số path tối đa một lần gọi: 3 ảnh đang viết + 3 ảnh đã bỏ khi sửa, để dư. */
const MAX_DISCARD = 10;

async function discardPhotos(uid: string, body: Record<string, unknown>, deps: ReviewDeps): Promise<Response> {
  // Đánh giá sân gửi `bookingId`; đánh giá buổi vãng lai gửi `sessionId` (reviewId = `{sessionId}_{uid}`).
  if ((body.bookingId != null) === (body.sessionId != null)) throw invalid("Chỉ gửi một trong bookingId hoặc sessionId");
  const reviewId = body.sessionId != null ? `${parseId(body.sessionId, "sessionId")}_${uid}` : parseId(body.bookingId, "bookingId");
  const paths = parsePhotoPaths(body.photoPaths, uid, reviewId, MAX_DISCARD);
  const saved = await deps.firestore.get(`reviews/${reviewId}`);
  const keep = new Set(stringArray(saved?.photoPaths));
  const toDelete = [...new Set(paths)].filter((p) => !keep.has(p));
  const deleted = toDelete.length > 0 ? await deps.storage.remove(toDelete) : 0;
  return Response.json({ deleted });
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

/**
 * Supabase Storage REST với service role (bỏ qua RLS). Bucket `review-photos` (migration
 * `*_review_photos.sql`).
 */
export function supabaseStorage(
  url = Deno.env.get("SUPABASE_URL") ?? "",
  key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
  bucket = "review-photos",
): ReviewStorage {
  const headers = { Authorization: `Bearer ${key}`, apikey: key, "Content-Type": "application/json" };
  const base = `${url.replace(/\/+$/, "")}/storage/v1/object`;

  async function listFolder(folder: string): Promise<string[]> {
    const res = await fetch(`${base}/list/${bucket}`, {
      method: "POST",
      headers,
      body: JSON.stringify({ prefix: folder, limit: 100, offset: 0 }),
    });
    if (!res.ok) throw new Error(`storage list ${res.status}: ${await res.text()}`);
    const rows = await res.json() as { name: string; id: string | null }[];
    // `id == null` là thư mục con; chỉ lấy file.
    return rows.filter((r) => r.id !== null).map((r) => `${folder}/${r.name}`);
  }

  return {
    async exists(path) {
      const slash = path.lastIndexOf("/");
      return (await listFolder(path.substring(0, slash))).includes(path);
    },
    list: (prefix) => listFolder(prefix.replace(/\/+$/, "")),
    async remove(paths) {
      if (paths.length === 0) return 0;
      const res = await fetch(`${base}/${bucket}`, {
        method: "DELETE",
        headers,
        body: JSON.stringify({ prefixes: paths }),
      });
      if (!res.ok) throw new Error(`storage remove ${res.status}: ${await res.text()}`);
      return (await res.json() as unknown[]).length;
    },
  };
}

function defaultDeps(): ReviewDeps {
  return { firestore: defaultFirestore, storage: supabaseStorage(), notify: sendNotification, now: () => new Date() };
}

