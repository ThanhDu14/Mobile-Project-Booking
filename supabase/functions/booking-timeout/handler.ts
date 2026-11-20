import { errorResponse, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { createWrite, deleteWrite, encodeValue, updateWrite, type Write } from "../_shared/firestore.ts";

/**
 * Chuyển đơn PENDING quá hạn duyệt sang REJECTED (spec 060 FR-005, Clarifications 2026-10-12).
 *
 * Chủ sân không duyệt hay từ chối được sau approvalDeadline (owner-booking-command trả DEADLINE_EXPIRED),
 * nên thiếu job này thì đơn kẹt PENDING vĩnh viễn và giữ chỗ sân. Mỗi đơn chạy trong một transaction:
 * nhả slot BOOKED của chính đơn, hoàn tiền đã trả (nếu có) vào ví, ghi bản ghi hoàn tiền và thông báo.
 */
export interface BookingTimeoutDeps {
  firestore?: Pick<typeof defaultFirestore, "query" | "runTransaction">;
  now?: () => Date;
  cronSecret?: string;
}

export const TIMEOUT_REASON = "Chủ sân chưa duyệt đơn trong thời hạn";

function defaults(): Required<BookingTimeoutDeps> {
  return {
    firestore: defaultFirestore,
    now: () => new Date(),
    cronSecret: Deno.env.get("CRON_SECRET") ?? "",
  };
}

function millis(value: unknown): number | null {
  if (value instanceof Date) return value.getTime();
  if (typeof value === "number" && Number.isFinite(value)) return value;
  if (typeof value === "string") {
    const parsed = Date.parse(value);
    return Number.isFinite(parsed) ? parsed : null;
  }
  return null;
}

const money = (value: unknown) => typeof value === "number" && Number.isSafeInteger(value) && value >= 0 ? value : 0;

/** Số tiền người chơi thực đã trả, cùng quy tắc với owner-booking-command khi từ chối. */
function paidByPlayer(booking: Record<string, unknown>): number {
  if (booking.paymentMethod === "AT_VENUE" && booking.paymentStatus !== "PAID") return 0;
  if (booking.paymentStatus === "PAID") return money(booking.paidAmount ?? booking.total);
  if (booking.paymentStatus === "DEPOSITED") return money(booking.paidAmount ?? booking.depositAmount);
  return 0;
}

function slotIdsOf(booking: Record<string, unknown>): string[] {
  const ids = new Set<string>();
  if (Array.isArray(booking.slotIds)) {
    for (const id of booking.slotIds) if (typeof id === "string" && id.trim()) ids.add(id.trim());
  }
  if (Array.isArray(booking.items)) {
    for (const item of booking.items) {
      const id = item && typeof item === "object" ? (item as Record<string, unknown>).slotId : null;
      if (typeof id === "string" && id.trim()) ids.add(id.trim());
    }
  }
  return [...ids].filter((id) => !id.includes("/"));
}

export async function handleBookingTimeout(req: Request, injected: BookingTimeoutDeps = {}): Promise<Response> {
  const deps = { ...defaults(), ...injected };
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
  const suppliedSecret = req.headers.get("x-cron-secret") ?? "";
  if (!deps.cronSecret || suppliedSecret !== deps.cronSecret) {
    return errorResponse(401, "UNAUTHENTICATED", "Thiếu hoặc sai CRON_SECRET");
  }

  try {
    const now = deps.now();
    const candidates = await deps.firestore.query("bookings", [
      { field: "status", op: "EQUAL", value: "PENDING" },
    ], { limit: 200 });
    let rejected = 0;
    let skipped = 0;

    for (const doc of candidates) {
      const deadline = millis(doc.data.approvalDeadline);
      if (deadline === null || deadline > now.getTime() || doc.id.includes("/")) {
        skipped++;
        continue;
      }
      const done = await deps.firestore.runTransaction(async (tx) => {
        const bookingPath = `bookings/${doc.id}`;
        const current = await tx.get(bookingPath);
        const currentDeadline = millis(current?.approvalDeadline);
        const userId = typeof current?.userId === "string" ? current.userId : "";
        if (
          !current || current.status !== "PENDING" || currentDeadline === null ||
          currentDeadline > now.getTime() || !userId || userId.includes("/")
        ) {
          return { result: false, writes: [] };
        }

        const ledgerPath = `refunds/refund_${doc.id}`;
        const ledger = await tx.get(ledgerPath);
        const ownedSlots: string[] = [];
        for (const id of slotIdsOf(current)) {
          const slot = await tx.get(`slots/${id}`);
          if (slot?.status === "BOOKED" && slot.bookingId === doc.id) ownedSlots.push(id);
        }

        const refundAmount = ledger ? 0 : paidByPlayer(current);
        const bookingUpdate: Record<string, unknown> = {
          status: "REJECTED",
          rejectedAt: now,
          rejectionReason: TIMEOUT_REASON,
          refundAmount,
          refundStatus: "COMPLETED",
          updatedAt: now,
        };
        if (refundAmount > 0) bookingUpdate.paymentStatus = "REFUNDED";

        const writes: Write[] = [
          updateWrite(bookingPath, bookingUpdate),
          ...ownedSlots.map((id) => deleteWrite(`slots/${id}`, false)),
        ];
        if (refundAmount > 0) {
          const wallet = updateWrite(`users/${userId}`, { updatedAt: now });
          wallet.updateTransforms = [{ fieldPath: "walletBalance", increment: encodeValue(refundAmount) }];
          writes.push(
            wallet,
            createWrite(ledgerPath, {
              bookingId: doc.id,
              userId,
              requestId: `timeout_${doc.id}`,
              refundAmount,
              penaltyAmount: 0,
              createdAt: now,
            }),
          );
        }
        writes.push(createWrite(`users/${userId}/notifications/${crypto.randomUUID()}`, {
          type: "BOOKING_REJECTED",
          title: "Đơn đặt chưa được duyệt",
          body: refundAmount > 0
            ? `${TIMEOUT_REASON}. Đã hoàn ${refundAmount} đ vào ví.`
            : `${TIMEOUT_REASON}. Khung giờ đã được mở lại, bạn có thể đặt sân khác.`,
          data: { bookingId: doc.id, venueId: typeof current.venueId === "string" ? current.venueId : null },
          isRead: false,
          createdAt: now,
        }));
        return { result: true, writes };
      });
      if (done) rejected++;
      else skipped++;
    }

    return Response.json({
      success: true,
      scanned: candidates.length,
      rejected,
      skipped,
      timestamp: now.toISOString(),
    });
  } catch (error) {
    return toErrorResponse(error);
  }
}
