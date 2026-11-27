import { errorResponse, HttpError, verifyIdToken } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { deleteWrite, FirestoreError, updateWrite } from "../_shared/firestore.ts";
import { refundCreateWrite, refundIdFor } from "../_shared/refunds.ts";

// Spec 050 (T058): không còn ví. Đơn đã trả bị hủy → `paymentStatus = REFUND_PENDING` và `refunds/refund_{bookingId}`
// `PENDING` để chủ sân hoàn thủ công. Số tiền hoàn/phạt vẫn theo `RefundPolicy` của 060.

/** Tên cũ (`DEPOSIT`, `MOCK_WALLET`) của đơn tạo trước spec 050 quy về cách trả mới. */
function normalizeMethod(raw: unknown): "AT_VENUE" | "TRANSFER_DEPOSIT" | "TRANSFER_FULL" {
  switch (raw) {
    case "TRANSFER_DEPOSIT":
    case "DEPOSIT":
      return "TRANSFER_DEPOSIT";
    case "TRANSFER_FULL":
    case "MOCK_WALLET":
      return "TRANSFER_FULL";
    default:
      return "AT_VENUE";
  }
}

/** 30% làm tròn xuống hàng nghìn. */
function latePenaltyFull(paid: number): number {
  return Math.floor((paid * 0.3) / 1000) * 1000;
}

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

    const booking = await firestore.get(`bookings/${bookingId}`);
    if (!booking || (booking.userId !== token.uid && token.appRole !== "ADMIN")) {
      throw new HttpError(404, "BOOKING_NOT_FOUND", "Không tìm thấy đơn đặt sân");
    }

    // Gọi lại sau khi đã hủy (mất mạng, bấm lại): trả kết quả đã lưu trên đơn.
    if (booking.status === "CANCELLED" && booking.cancelledBy === "USER") {
      return new Response(
        JSON.stringify({
          success: true,
          bookingId,
          status: "CANCELLED",
          refundAmount: Number(booking.refundAmount || 0),
          penaltyAmount: Number(booking.penaltyAmount || 0),
          cancelledAt: booking.cancelledAt instanceof Date ? booking.cancelledAt.toISOString() : booking.cancelledAt,
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
    const paymentMethod = normalizeMethod(booking.paymentMethod);

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
        if (paymentMethod === "TRANSFER_DEPOSIT") {
          refundAmount = 0;
          penaltyAmount = paidAmount;
        } else if (paymentMethod === "TRANSFER_FULL") {
          penaltyAmount = latePenaltyFull(paidAmount);
          refundAmount = paidAmount - penaltyAmount;
        } else {
          refundAmount = 0;
          penaltyAmount = 0;
        }
      }
    }

    // Ghi mốc thời gian bằng Date để REST lưu thành Timestamp. Chuỗi ISO bị lưu thành string, làm app
    // không sắp xếp và phân trang được tab "Đã hủy" (rà soát 060 ngày 2026-11-18).
    const cancelledAt = new Date();
    const nowIso = cancelledAt.toISOString();
    const writes: defaultFirestore.Write[] = [
      updateWrite(`bookings/${bookingId}`, {
        status: "CANCELLED",
        cancelledAt,
        cancelReason: reason,
        cancelledBy: "USER",
        refundAmount,
        penaltyAmount,
        ...(refundAmount > 0 ? { paymentStatus: "REFUND_PENDING" } : {}),
        updatedAt: cancelledAt,
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

    // Khoản chủ sân phải chuyển trả (hoàn thủ công, spec 050). Đã có thì commit báo ALREADY_EXISTS → 409.
    if (refundAmount > 0) {
      writes.push(refundCreateWrite({
        id: refundIdFor({ bookingId }),
        bookingId,
        venueId: String(booking.venueId ?? ""),
        userId: String(booking.userId),
        amount: refundAmount,
        penaltyAmount,
        reason: "CANCELLED",
      }));
    }

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
