// Edge Function `owner-mark-refunded` (spec 050 US4; contracts/edge-functions.md §4).
// Chủ sân xác nhận đã tự chuyển trả tiền: `refunds/{id}` PENDING → DONE; đơn đang `REFUND_PENDING` → `REFUNDED`.
// Gọi lại khi đã DONE trả lại kết quả cũ, không ghi thêm.
import { errorResponse, HttpError, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { type FirestoreTransaction, updateWrite, type Write } from "../_shared/firestore.ts";
import { readJsonObject, runWithBackoff } from "../_shared/dropin-tx.ts";
import { asString, type Doc } from "../_shared/payment-order.ts";

export interface MarkRefundedDeps {
  firestore: Pick<typeof defaultFirestore, "runTransaction">;
  authenticate: (req: Request) => Promise<{ uid: string }>;
  now: () => Date;
  sleep?: (ms: number) => Promise<void>;
}

interface MarkResult {
  refundId: string;
  status: "DONE";
  bookingPaymentStatus: "REFUNDED" | null;
}

/** Đơn (hoặc nhóm) gắn với khoản hoàn. */
function relatedPath(refund: Doc): string | null {
  const bookingId = asString(refund.bookingId);
  if (bookingId) return `bookings/${bookingId}`;
  const groupId = asString(refund.recurringGroupId);
  return groupId ? `recurringGroups/${groupId}` : null;
}

async function mark(
  tx: FirestoreTransaction,
  uid: string,
  refundId: string,
  now: Date,
): Promise<{ result: MarkResult; writes: Write[] }> {
  const refund = await tx.get(`refunds/${refundId}`);
  if (!refund) throw new HttpError(404, "NOT_FOUND", "Không tìm thấy khoản hoàn tiền");
  const venue = await tx.get(`venues/${asString(refund.venueId) ?? "-"}`);
  if (!venue || venue.ownerId !== uid) {
    throw new HttpError(403, "FORBIDDEN", "Khoản hoàn này không thuộc cơ sở của bạn");
  }

  const path = relatedPath(refund);
  const booking = path ? await tx.get(path) : null;
  if (refund.status === "DONE") {
    const refunded = booking?.paymentStatus === "REFUNDED" ? "REFUNDED" : null;
    return { result: { refundId, status: "DONE", bookingPaymentStatus: refunded }, writes: [] };
  }
  if (refund.status !== "PENDING") throw new HttpError(409, "INVALID_STATE", "Khoản hoàn không ở trạng thái chờ");

  const writes: Write[] = [updateWrite(`refunds/${refundId}`, { status: "DONE", doneAt: now })];
  // Tiền về muộn/sai số tiền không đổi trạng thái thanh toán của đơn, chỉ khoản đơn đang chờ hoàn mới sang REFUNDED.
  const flip = path != null && booking?.paymentStatus === "REFUND_PENDING";
  if (flip) writes.push(updateWrite(path!, { paymentStatus: "REFUNDED", updatedAt: now }));
  return { result: { refundId, status: "DONE", bookingPaymentStatus: flip ? "REFUNDED" : null }, writes };
}

export async function handleOwnerMarkRefunded(req: Request, deps: MarkRefundedDeps = defaultDeps()): Promise<Response> {
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
  try {
    const { uid } = await deps.authenticate(req);
    const body = await readJsonObject(req);
    const refundId = typeof body.refundId === "string" ? body.refundId.trim() : "";
    if (!refundId || refundId.includes("/")) throw new HttpError(400, "INVALID_ARGUMENT", "Thiếu refundId");
    const now = deps.now();
    return Response.json(await runWithBackoff(deps, (tx) => mark(tx, uid, refundId, now)));
  } catch (e) {
    return toErrorResponse(e);
  }
}

export function defaultDeps(): MarkRefundedDeps {
  return { firestore: defaultFirestore, authenticate: (req) => requireUser(req), now: () => new Date() };
}
