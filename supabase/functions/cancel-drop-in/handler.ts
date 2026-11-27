// Edge Function `cancel-drop-in` (C, spec 080 US4; contracts/edge-functions-dropin.md §2).
// Người chơi tự hủy lượt vãng lai trước giờ bắt đầu: kiểm tra theo bảng "Quy tắc kiểm tra của cancel-drop-in"
// (data-model.md), rồi gọi `cancelRegistrationInTx` trong MỘT Firestore transaction để trả chỗ và hoàn tiền giả lập.
// Idempotent theo `requestId` (lưu ở `cancelRequestId`, FR-018). Hủy lượt đã giữ chỗ không gửi thông báo cho người hủy
// (đặc tả CSDL mục 4.13).
//
// Spec 081: (1) lượt `WAITLISTED` thì hiểu là rời hàng chờ, không phát sinh tiền (US3); (2) hủy lượt đã giữ chỗ có thể
// đẩy người chờ lên trong cùng transaction, sau commit gửi `DROPIN_SPOT_OPENED` cho từng người (US2).
import { errorResponse, HttpError, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import type { FirestoreTransaction, Write } from "../_shared/firestore.ts";
import {
  cancelRegistrationInTx,
  computeRefund,
  type RefundReason,
  registrationPath,
  type RegistrationDoc,
  type SessionDoc,
  sessionPath,
} from "../_shared/dropin.ts";
import { sendDropInNotification } from "../_shared/dropin-notify.ts";
import { leaveWaitlistInTx } from "../_shared/waitlist.ts";
import {
  createKeyedSerializer,
  type KeyedSerializer,
  readJsonObject,
  runWithBackoff,
  UUID,
} from "../_shared/dropin-tx.ts";

/** Gửi sau khi commit cho người được đẩy lên từ hàng chờ (research R12). */
export interface Notification {
  type: "DROPIN_SPOT_OPENED";
  userId: string;
  data: { sessionId: string; autoLeftSessionIds: string[] };
}

export interface CancelDeps {
  firestore: Pick<typeof defaultFirestore, "runTransaction">;
  /** Xác thực người gọi; mặc định `requireUser` (401 UNAUTHENTICATED, 403 ACCOUNT_LOCKED, ...). */
  authenticate: (req: Request) => Promise<{ uid: string }>;
  /** Giờ server (`serverNow`). */
  now: () => Date;
  /** Gửi thông báo sau khi commit; lỗi chỉ ghi log. Thiếu thì không gửi (test của spec 080). */
  notify?: (n: Notification) => Promise<void>;
  /** Chờ giữa các lần thử lại; test thay để chạy nhanh. */
  sleep?: (ms: number) => Promise<void>;
  /** Xếp hàng request cùng `sessionId` trong isolate (xem `createKeyedSerializer`). */
  serialize?: KeyedSerializer;
}

const isolateSerializer = createKeyedSerializer();

interface CancelInput {
  sessionId: string;
  requestId: string;
  dryRun: boolean;
  /** Spec 081: app gửi khi bấm "Rời hàng chờ"; lượt đã được đẩy lên thì trả `409 ALREADY_PROMOTED`, không hủy. */
  expectWaitlisted: boolean;
}

const invalid = (message: string) => new HttpError(400, "INVALID_ARGUMENT", message);

function parseInput(body: Record<string, unknown>): CancelInput {
  const sessionId = typeof body.sessionId === "string" ? body.sessionId.trim() : "";
  if (!sessionId || sessionId.includes("/")) throw invalid("Thiếu hoặc sai sessionId");
  if (typeof body.requestId !== "string" || !UUID.test(body.requestId)) throw invalid("requestId phải là UUID");
  if (body.dryRun !== undefined && typeof body.dryRun !== "boolean") throw invalid("dryRun phải là boolean");
  if (body.expectWaitlisted !== undefined && typeof body.expectWaitlisted !== "boolean") {
    throw invalid("expectWaitlisted phải là boolean");
  }
  return {
    sessionId,
    requestId: body.requestId,
    dryRun: body.dryRun === true,
    expectWaitlisted: body.expectWaitlisted === true,
  };
}

const asDate = (v: unknown): Date | null => (v instanceof Date && !Number.isNaN(v.getTime()) ? v : null);
const asNumber = (v: unknown, fallback = 0): number => (typeof v === "number" && Number.isFinite(v) ? v : fallback);

interface CancelResult {
  registration: RegistrationDoc;
  quote: { refundAmount: number; reason: RefundReason };
  session: { registeredCount: number; capacity: number; status: string };
  /** Người được đẩy lên nhờ chỗ vừa trả (chỉ dùng để gửi thông báo, không đưa vào response). */
  promoted: { uid: string; autoLeftSessionIds: string[] }[];
}

function sessionSummary(s: SessionDoc) {
  return {
    registeredCount: asNumber(s.registeredCount),
    capacity: asNumber(s.capacity),
    status: String(s.status ?? ""),
  };
}

/** Lý do của lần hủy đã lưu (không có trường riêng, suy ra từ kết quả đã ghi). */
function storedQuote(reg: RegistrationDoc): { refundAmount: number; reason: RefundReason } {
  if (reg.waitlistEndReason) return { refundAmount: 0, reason: "WAITLIST_LEAVE" };
  const refundAmount = asNumber(reg.refundAmount);
  if (reg.paymentStatus === "UNPAID") return { refundAmount: 0, reason: "AT_VENUE" };
  if (refundAmount > 0) return { refundAmount, reason: reg.sessionChanged === true ? "SESSION_CHANGED" : "EARLY" };
  return { refundAmount: 0, reason: "LATE" };
}

async function cancel(
  tx: FirestoreTransaction,
  uid: string,
  input: CancelInput,
  now: Date,
): Promise<{ result: CancelResult; writes: Write[] }> {
  // Đường dẫn theo uid người gọi: không đọc được lượt của người khác, nên token khác → NOT_FOUND (E10).
  const reg = await tx.get(registrationPath(input.sessionId, uid)) as RegistrationDoc | null;
  if (!reg) throw new HttpError(404, "NOT_FOUND", "Không tìm thấy lượt đăng ký");
  const session = await tx.get(sessionPath(input.sessionId)) as SessionDoc | null;
  if (!session) throw new HttpError(404, "NOT_FOUND", "Không tìm thấy buổi chơi");

  if (!input.dryRun && reg.status === "CANCELLED" && reg.cancelRequestId === input.requestId) {
    // Gửi lại (mất mạng, bấm nhiều lần): trả kết quả đã lưu, không ghi gì.
    return {
      result: { registration: reg, quote: storedQuote(reg), session: sessionSummary(session), promoted: [] },
      writes: [],
    };
  }
  if (reg.status === "WAITLISTED") return leaveWaitlist(tx, uid, input, now, reg, session);
  if (input.expectWaitlisted && (reg.status === "REGISTERED" || reg.status === "CHECKED_IN")) {
    // Vừa được đẩy lên (hoặc không phải lượt chờ): không hủy nhầm một đăng ký có thể mất tiền (FR-013, research R10).
    throw reg.promotedAt != null
      ? new HttpError(409, "ALREADY_PROMOTED", "Bạn đã được nhận vào buổi")
      : new HttpError(409, "NOT_CANCELLABLE", "Lượt này không phải lượt chờ");
  }
  if (reg.status !== "REGISTERED") {
    throw new HttpError(409, "NOT_CANCELLABLE", reg.status === "CHECKED_IN"
      ? "Lượt đã check-in, không hủy được"
      : "Lượt này không còn hiệu lực để hủy");
  }
  const startAt = asDate(session.startAt) ?? asDate(reg.sessionStartAt);
  if (!startAt) throw new Error(`dropInSessions/${input.sessionId} thiếu startAt (Timestamp)`);
  if (now.getTime() >= startAt.getTime()) throw new HttpError(409, "SESSION_STARTED", "Buổi đã bắt đầu");

  const quote = computeRefund(reg, session, now);
  if (input.dryRun) {
    return { result: { registration: reg, quote, session: sessionSummary(session), promoted: [] }, writes: [] };
  }

  // `cancelRegistrationInTx` gom ghi bằng `tx.write`; ở đây gom lại để trả cho `runWithBackoff` commit một lần.
  // `query` để hook `afterSpotsReleased` đẩy người chờ lên trong cùng transaction (081).
  const writes: Write[] = [];
  const out = await cancelRegistrationInTx(
    { get: (path) => tx.get(path), query: (c, f, o) => tx.query(c, f, o), write: (w) => void writes.push(w) },
    input.sessionId,
    uid,
    "USER",
    { now, cancelRequestId: input.requestId },
  );
  const registration: RegistrationDoc = {
    ...reg,
    status: "CANCELLED",
    qrToken: null,
    ticketCode: null,
    cancelledAt: now,
    cancelledBy: "USER",
    cancelRequestId: input.requestId,
    refundAmount: out.refundAmount,
    paymentStatus: out.refundAmount > 0 ? "REFUNDED" : reg.paymentStatus,
    updatedAt: now,
  };
  return {
    result: {
      registration,
      quote: { refundAmount: out.refundAmount, reason: out.reason ?? quote.reason },
      session: sessionSummary(out.session ?? session),
      promoted: out.promoted ?? [],
    },
    writes,
  };
}

/** Lượt `WAITLISTED` → rời hàng chờ (081 US3): không phát sinh tiền, bỏ số vé khỏi buổi, người sau lên vị trí. */
async function leaveWaitlist(
  tx: FirestoreTransaction,
  uid: string,
  input: CancelInput,
  now: Date,
  reg: RegistrationDoc,
  session: SessionDoc,
): Promise<{ result: CancelResult; writes: Write[] }> {
  const quote = { refundAmount: 0, reason: "WAITLIST_LEAVE" as RefundReason };
  if (input.dryRun) {
    return { result: { registration: reg, quote, session: sessionSummary(session), promoted: [] }, writes: [] };
  }
  const writes: Write[] = [];
  // Đọc lại lượt trong transaction (R10): nếu vừa được đẩy lên thì ném ALREADY_PROMOTED và không ghi gì.
  // `cancelRequestId` được ghi cùng lần cập nhật lượt để gửi lại cùng requestId trả kết quả cũ.
  await leaveWaitlistInTx(
    { get: (path) => tx.get(path), write: (w) => void writes.push(w) },
    input.sessionId,
    uid,
    "LEFT",
    "USER",
    { now, extra: { cancelRequestId: input.requestId, refundAmount: 0 } },
  );
  const remaining = Array.isArray(session.waitlistTickets)
    ? session.waitlistTickets.filter((t) => t !== reg.waitlistTicket)
    : undefined;
  const registration: RegistrationDoc = {
    ...reg,
    status: "CANCELLED",
    cancelledAt: now,
    cancelledBy: "USER",
    waitlistEndReason: "LEFT",
    cancelRequestId: input.requestId,
    refundAmount: 0,
    updatedAt: now,
  };
  return {
    result: {
      registration,
      quote,
      session: sessionSummary(remaining ? { ...session, waitlistTickets: remaining } : session),
      promoted: [],
    },
    writes,
  };
}

/** Body trả về: không có `qrToken` (contract §2). */
function toResponseRegistration(r: RegistrationDoc) {
  return {
    sessionId: r.sessionId,
    status: r.status,
    partySize: r.partySize,
    paymentMethod: r.paymentMethod,
    paymentStatus: r.paymentStatus,
    amount: r.amount,
    refundAmount: r.refundAmount ?? null,
    cancelledAt: asDate(r.cancelledAt)?.toISOString() ?? null,
    // Chỉ lượt chờ đã rời mới có (không đổi dạng response của hủy thường).
    ...(r.waitlistEndReason ? { waitlistEndReason: r.waitlistEndReason } : {}),
  };
}

export async function handleCancelDropIn(req: Request, deps: CancelDeps = defaultDeps()): Promise<Response> {
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
  try {
    const { uid } = await deps.authenticate(req);
    const input = parseInput(await readJsonObject(req));
    const serialize = deps.serialize ?? isolateSerializer;
    // Lấy giờ sau khi tới lượt trong hàng đợi để kiểm tra SESSION_STARTED và mốc hoàn tiền không dùng giờ cũ.
    const result = await serialize(input.sessionId, () => {
      const now = deps.now();
      return runWithBackoff(deps, (tx) => cancel(tx, uid, input, now));
    });
    // Waitlist helper enqueued inbox + delivery in the source transaction (100 D2-W1).
    return Response.json({
      registration: toResponseRegistration(result.registration),
      quote: result.quote,
      session: result.session,
      // Chỉ số lượng, không có danh tính người được đẩy lên (contract §3).
      ...(result.registration.waitlistEndReason ? {} : { promotedCount: result.promoted.length }),
    });
  } catch (e) {
    return toErrorResponse(e);
  }
}

export function defaultDeps(): CancelDeps {
  return {
    firestore: defaultFirestore,
    authenticate: (req) => requireUser(req),
    now: () => new Date(),
    notify: sendDropInNotification,
  };
}
