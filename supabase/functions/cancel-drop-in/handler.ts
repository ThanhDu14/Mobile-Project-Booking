// Edge Function `cancel-drop-in` (C, spec 080 US4; contracts/edge-functions-dropin.md §2).
// Người chơi tự hủy lượt vãng lai trước giờ bắt đầu: kiểm tra theo bảng "Quy tắc kiểm tra của cancel-drop-in"
// (data-model.md), rồi gọi `cancelRegistrationInTx` trong MỘT Firestore transaction để trả chỗ và hoàn tiền giả lập.
// Idempotent theo `requestId` (lưu ở `cancelRequestId`, FR-018). Không gửi thông báo (đặc tả CSDL mục 4.13).
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
import {
  createKeyedSerializer,
  type KeyedSerializer,
  readJsonObject,
  runWithBackoff,
  UUID,
} from "../_shared/dropin-tx.ts";

export interface CancelDeps {
  firestore: Pick<typeof defaultFirestore, "runTransaction">;
  /** Xác thực người gọi; mặc định `requireUser` (401 UNAUTHENTICATED, 403 ACCOUNT_LOCKED, ...). */
  authenticate: (req: Request) => Promise<{ uid: string }>;
  /** Giờ server (`serverNow`). */
  now: () => Date;
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
}

const invalid = (message: string) => new HttpError(400, "INVALID_ARGUMENT", message);

function parseInput(body: Record<string, unknown>): CancelInput {
  const sessionId = typeof body.sessionId === "string" ? body.sessionId.trim() : "";
  if (!sessionId || sessionId.includes("/")) throw invalid("Thiếu hoặc sai sessionId");
  if (typeof body.requestId !== "string" || !UUID.test(body.requestId)) throw invalid("requestId phải là UUID");
  if (body.dryRun !== undefined && typeof body.dryRun !== "boolean") throw invalid("dryRun phải là boolean");
  return { sessionId, requestId: body.requestId, dryRun: body.dryRun === true };
}

const asDate = (v: unknown): Date | null => (v instanceof Date && !Number.isNaN(v.getTime()) ? v : null);
const asNumber = (v: unknown, fallback = 0): number => (typeof v === "number" && Number.isFinite(v) ? v : fallback);

interface CancelResult {
  registration: RegistrationDoc;
  quote: { refundAmount: number; reason: RefundReason };
  session: { registeredCount: number; capacity: number; status: string };
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
    return { result: { registration: reg, quote: storedQuote(reg), session: sessionSummary(session) }, writes: [] };
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
    return { result: { registration: reg, quote, session: sessionSummary(session) }, writes: [] };
  }

  // `cancelRegistrationInTx` gom ghi bằng `tx.write`; ở đây gom lại để trả cho `runWithBackoff` commit một lần.
  const writes: Write[] = [];
  const out = await cancelRegistrationInTx(
    { get: (path) => tx.get(path), write: (w) => void writes.push(w) },
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
    return Response.json({
      registration: toResponseRegistration(result.registration),
      quote: result.quote,
      session: result.session,
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
  };
}
