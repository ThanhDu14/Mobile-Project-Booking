// Edge Function `refund-report` (spec 050 US4; contracts/edge-functions.md §5).
// Người chơi báo chủ sân chưa hoàn tiền: refund PENDING quá 3 ngày, hoặc DONE mà chưa nhận. Tạo `reports`
// (targetType VENUE, reason REFUND_NOT_RECEIVED) cho admin 120 xử lý; mỗi khoản hoàn chỉ báo một lần.
import { errorResponse, HttpError, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { createWrite, type FirestoreTransaction, updateWrite, type Write } from "../_shared/firestore.ts";
import { readJsonObject, runWithBackoff } from "../_shared/dropin-tx.ts";
import { asDate, asNumber, asString } from "../_shared/payment-order.ts";

export interface RefundReportDeps {
  firestore: Pick<typeof defaultFirestore, "runTransaction">;
  authenticate: (req: Request) => Promise<{ uid: string }>;
  now: () => Date;
  sleep?: (ms: number) => Promise<void>;
  newId?: () => string;
}

const WAIT_MS = 3 * 24 * 60 * 60_000;
const MAX_NOTE = 500;

async function report(
  tx: FirestoreTransaction,
  uid: string,
  refundId: string,
  note: string,
  reportId: string,
  now: Date,
): Promise<{ result: { reportId: string }; writes: Write[] }> {
  const refund = await tx.get(`refunds/${refundId}`);
  if (!refund) throw new HttpError(404, "NOT_FOUND", "Không tìm thấy khoản hoàn tiền");
  if (refund.userId !== uid) throw new HttpError(403, "FORBIDDEN", "Khoản hoàn này không phải của bạn");
  if (refund.reportedAt != null) throw new HttpError(409, "INVALID_STATE", "Bạn đã báo cáo khoản hoàn này");
  const createdAt = asDate(refund.createdAt);
  const overdue = refund.status === "PENDING" && createdAt != null && now.getTime() - createdAt.getTime() >= WAIT_MS;
  if (!overdue && refund.status !== "DONE") {
    throw new HttpError(409, "INVALID_STATE", "Chỉ báo cáo được sau 3 ngày chờ hoàn tiền");
  }
  const venueId = asString(refund.venueId) ?? "";
  return {
    result: { reportId },
    writes: [
      createWrite(`reports/${reportId}`, {
        reporterId: uid,
        targetType: "VENUE",
        targetPath: `venues/${venueId}`,
        reason: "REFUND_NOT_RECEIVED",
        detail: note,
        refundId,
        bookingId: asString(refund.bookingId) ?? asString(refund.recurringGroupId),
        amount: asNumber(refund.amount),
        status: "OPEN",
        action: "NONE",
        createdAt: now,
      }),
      updateWrite(`refunds/${refundId}`, { reportedAt: now }),
    ],
  };
}

export async function handleRefundReport(req: Request, deps: RefundReportDeps = defaultDeps()): Promise<Response> {
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
  try {
    const { uid } = await deps.authenticate(req);
    const body = await readJsonObject(req);
    const refundId = typeof body.refundId === "string" ? body.refundId.trim() : "";
    if (!refundId || refundId.includes("/")) throw new HttpError(400, "INVALID_ARGUMENT", "Thiếu refundId");
    const note = typeof body.note === "string" ? body.note.trim().slice(0, MAX_NOTE) : "";
    const reportId = (deps.newId ?? (() => crypto.randomUUID()))();
    const now = deps.now();
    const result = await runWithBackoff(deps, (tx) => report(tx, uid, refundId, note, reportId, now));
    return Response.json(result, { status: 201 });
  } catch (e) {
    return toErrorResponse(e);
  }
}

export function defaultDeps(): RefundReportDeps {
  return { firestore: defaultFirestore, authenticate: (req) => requireUser(req), now: () => new Date() };
}
