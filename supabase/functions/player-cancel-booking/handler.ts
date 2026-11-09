import { errorResponse, HttpError, verifyIdToken } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { createWrite, deleteWrite, FirestoreError, updateWrite } from "../_shared/firestore.ts";

export interface PlayerCancelBookingDeps {
  firestore?: Pick<typeof defaultFirestore, "get" | "commit">;
  verifyToken?: (req: Request) => Promise<import("../_shared/auth.ts").VerifiedToken>;
}

export async function handlePlayerCancelBooking(
  req: Request,
  deps: PlayerCancelBookingDeps = {},
): Promise<Response> {
  const firestore = deps?.firestore ?? defaultFirestore;
  const verifyToken = deps?.verifyToken ?? verifyIdToken;
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");

  try {
    const token = await verifyToken(req);
    if (token.appRole !== "PLAYER" && token.appRole !== "ADMIN") {
      throw new HttpError(403, "FORBIDDEN", "Chỉ người chơi mới có thể yêu cầu hủy đơn");
    }

    const body = await req.json().catch(() => ({}));
    const bookingId = typeof body.bookingId === "string" ? body.bookingId.trim() : "";
    const reason = typeof body.reason === "string" ? body.reason.trim() : "";
    const requestId = typeof body.requestId === "string" ? body.requestId.trim() : "";

    if (!bookingId) throw new HttpError(400, "INVALID_BOOKING_ID", "Thiếu hoặc sai bookingId");
    if (!reason) throw new HttpError(400, "INVALID_REASON", "Lý do hủy bắt buộc không được để trống");

    // These independent reads avoid two sequential Firestore network round trips.
    const [booking, existingRefund] = await Promise.all([
      firestore.get(`bookings/${bookingId}`),
      firestore.get(`refunds/refund_${bookingId}`),
    ]);
    if (!booking || (booking.userId !== token.uid && token.appRole !== "ADMIN")) {
      throw new HttpError(404, "BOOKING_NOT_FOUND", "Không tìm thấy đơn đặt sân");
    }

    // Idempotency: nếu đơn đã có bản ghi hoàn tiền với requestId tương ứng.
    if (existingRefund) {
      return new Response(
        JSON.stringify({
          success: true,
          bookingId,
          status: "CANCELLED",
          refundAmount: Number(existingRefund.refundAmount || 0),
          penaltyAmount: Number(existingRefund.penaltyAmount || 0),
          cancelledAt: existingRefund.createdAt ?? new Date().toISOString(),
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      );
    }

    if (booking.status !== "PENDING" && booking.status !== "CONFIRMED") {
      throw new HttpError(
        409,
        "INVALID_BOOKING_STATUS",
        `Đơn đặt sân đang ở trạng thái ${booking.status}, chỉ có thể hủy đơn PENDING hoặc CONFIRMED`,
      );
    }

    if (booking.checkedInAt) {
      throw new HttpError(409, "ALREADY_CHECKED_IN", "Đơn đặt sân đã check-in không thể hủy");
    }

    const now = Date.now();
    const startAt = booking.startAt instanceof Date
      ? booking.startAt.getTime()
      : (typeof booking.startAt === "string" ? new Date(booking.startAt).getTime() : Number(booking.startAt || 0));

    if (startAt > 0 && startAt <= now) {
      throw new HttpError(409, "BOOKING_ALREADY_STARTED", "Đơn đặt sân đã bắt đầu không thể hủy");
    }

    // Tính toán hoàn / phạt
    let refundAmount = 0;
    let penaltyAmount = 0;
    const paidAmount = Number(booking.paidAmount || booking.depositAmount || 0);
    const paymentMethod = String(booking.paymentMethod || "AT_VENUE");

    if (booking.status === "PENDING") {
      refundAmount = paidAmount;
      penaltyAmount = 0;
    } else {
      const minutesUntilStart = Math.floor((startAt - now) / 60000);
      if (minutesUntilStart >= 240) {
        // Hủy sớm >= 240 phút: hoàn 100%
        refundAmount = paidAmount;
        penaltyAmount = 0;
      } else {
        // Hủy trễ < 240 phút
        if (paymentMethod === "AT_VENUE") {
          refundAmount = 0;
          penaltyAmount = 0;
        } else if (paymentMethod === "DEPOSIT") {
          refundAmount = 0;
          penaltyAmount = paidAmount;
        } else if (paymentMethod === "MOCK_WALLET") {
          penaltyAmount = Math.floor((paidAmount * 0.3) / 1000) * 1000;
          refundAmount = paidAmount - penaltyAmount;
        }
      }
    }

    const nowIso = new Date().toISOString();
    const writes: defaultFirestore.Write[] = [
      updateWrite(`bookings/${bookingId}`, {
        status: "CANCELLED",
        cancelledAt: nowIso,
        cancelReason: reason,
        cancelledBy: "USER",
        refundAmount,
        penaltyAmount,
        refundStatus: "COMPLETED",
        updatedAt: nowIso,
      }),
    ];

    // Nhả slot (deduplicate để tránh Firestore commit lỗi "multiple writes to same document")
    const slotIdSet = new Set<string>();
    const items = Array.isArray(booking.items) ? booking.items : [];
    for (const item of items) {
      if (item && typeof item === "object" && typeof item.slotId === "string" && item.slotId.trim()) {
        slotIdSet.add(item.slotId.trim());
      }
    }
    const slotIds = Array.isArray(booking.slotIds) ? booking.slotIds : [];
    for (const sId of slotIds) {
      if (typeof sId === "string" && sId.trim()) {
        slotIdSet.add(sId.trim());
      }
    }
    for (const slotId of slotIdSet) {
      writes.push(deleteWrite(`slots/${slotId}`, false));
    }

    // Hoàn tiền ví giả lập nếu có
    if (refundAmount > 0) {
      const user = await firestore.get(`users/${booking.userId}`);
      const currentBalance = Number(user?.walletBalance || 0);
      writes.push(
        updateWrite(`users/${booking.userId}`, {
          walletBalance: currentBalance + refundAmount,
          updatedAt: nowIso,
        }),
      );
    }

    // Ghi nhận idempotency refund record
    writes.push(
      createWrite(`refunds/refund_${bookingId}`, {
        bookingId,
        userId: booking.userId,
        requestId,
        refundAmount,
        penaltyAmount,
        createdAt: nowIso,
      }),
    );

    try {
      await firestore.commit(writes);
    } catch (e) {
      if (e instanceof FirestoreError && (e.code === "ABORTED" || e.code === "ALREADY_EXISTS")) {
        throw new HttpError(409, "BOOKING_CONFLICT", "Xung đột dữ liệu hoặc đơn đã được hủy trước đó");
      }
      throw e;
    }

    return new Response(
      JSON.stringify({
        success: true,
        bookingId,
        status: "CANCELLED",
        refundAmount,
        penaltyAmount,
        cancelledAt: nowIso,
      }),
      { status: 200, headers: { "Content-Type": "application/json" } },
    );
  } catch (err) {
    if (err instanceof HttpError) return errorResponse(err.status, err.code, err.message);
    console.error("player-cancel-booking unexpected error:", err);
    const message = err instanceof Error ? err.message : "Lỗi máy chủ không xác định";
    return errorResponse(500, "INTERNAL_ERROR", message);
  }
}
