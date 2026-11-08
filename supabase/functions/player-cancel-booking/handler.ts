import { errorResponse, HttpError, verifyIdToken } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";

export interface PlayerCancelBookingDeps {
  firestore: Pick<typeof defaultFirestore, "get">;
}

/**
 * Cancellation remains deliberately fail-closed: the approved data model does
 * not define enough booking/payment/redemption/slot state to safely refund and
 * release resources atomically.
 */
export async function handlePlayerCancelBooking(
  req: Request,
  { firestore }: PlayerCancelBookingDeps = { firestore: defaultFirestore },
): Promise<Response> {
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");

  try {
    const token = await verifyIdToken(req);
    if (token.appRole !== "PLAYER") {
      throw new HttpError(403, "FORBIDDEN", "Chỉ người chơi mới có thể yêu cầu hủy đơn");
    }

    const body = await req.json().catch(() => ({}));
    const bookingId = typeof body.bookingId === "string" ? body.bookingId.trim() : "";
    if (!bookingId) throw new HttpError(400, "INVALID_BOOKING_ID", "Thiếu hoặc sai bookingId");

    const booking = await firestore.get(`bookings/${bookingId}`);
    if (!booking || booking.userId !== token.uid) {
      throw new HttpError(404, "BOOKING_NOT_FOUND", "Không tìm thấy đơn đặt sân");
    }

    // Do not mutate only the booking: doing so could expose a refunded booking
    // while leaving wallet history, voucher redemption, or occupied slots intact.
    throw new HttpError(
      409,
      "CANCELLATION_UNAVAILABLE",
      "Chưa thể hủy đơn an toàn cho đến khi hợp đồng thanh toán, voucher và slot được xác nhận",
    );
  } catch (err) {
    if (err instanceof HttpError) return errorResponse(err.status, err.code, err.message);
    const message = err instanceof Error ? err.message : "Lỗi máy chủ không xác định";
    return errorResponse(500, "INTERNAL_ERROR", message);
  }
}
