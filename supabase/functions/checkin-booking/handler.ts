import { errorResponse, HttpError, verifyIdToken } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { updateWrite, FirestoreError } from "../_shared/firestore.ts";

export interface CheckinBookingDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "commit"> & Partial<Pick<typeof defaultFirestore, "query">>;
}

export async function handleCheckinBooking(
  req: Request,
  { firestore }: CheckinBookingDeps = { firestore: defaultFirestore },
): Promise<Response> {
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");

  try {
    const token = await verifyIdToken(req);
    const body = await req.json().catch(() => ({}));

    // venueId không bắt buộc: client chỉ có mã quét được (bookingId hoặc qrToken) và không còn
    // quyền query qrToken (Rules spec 112 T012). Server tra đơn rồi lấy venueId từ chính đơn.
    const requestedVenueId = typeof body.venueId === "string" ? body.venueId.trim() : "";
    const bookingId = typeof body.bookingId === "string" ? body.bookingId.trim() : "";
    const confirmPayment = Boolean(body.confirmPayment);

    if (!bookingId) throw new HttpError(400, "INVALID_BOOKING_ID", "Thiếu bookingId");

    // 1. Verify owner status
    if (token.appRole !== "ADMIN") {
      const user = await firestore.get(`users/${token.uid}`);
      if (user?.ownerStatus !== "APPROVED") {
        throw new HttpError(403, "FORBIDDEN", "Chủ sân chưa được phê duyệt");
      }
    }

    // 2. Tìm đơn theo bookingId, không có thì tra theo qrToken
    let booking = await firestore.get(`bookings/${bookingId}`);
    let resolvedBookingId = bookingId;
    if (!booking && typeof firestore.query === "function") {
      const byToken = await firestore.query("bookings", [
        { field: "qrToken", op: "EQUAL", value: bookingId },
      ]);
      if (byToken.length > 0) {
        booking = byToken[0].data;
        resolvedBookingId = byToken[0].id;
      }
    }
    const venueId = typeof booking?.venueId === "string" ? booking.venueId : "";
    if (!booking || !venueId || (requestedVenueId && requestedVenueId !== venueId)) {
      throw new HttpError(404, "BOOKING_NOT_FOUND", "Không tìm thấy đơn đặt sân");
    }

    // 3. Verify venue ownership & status. Đơn của cơ sở người khác trả cùng mã BOOKING_NOT_FOUND
    // để không lộ việc mã đó có tồn tại.
    const venue = await firestore.get(`venues/${venueId}`);
    if (!venue || (venue.ownerId !== token.uid && token.appRole !== "ADMIN")) {
      throw new HttpError(404, "BOOKING_NOT_FOUND", "Không tìm thấy đơn đặt sân");
    }
    if (venue.status === "LOCKED") throw new HttpError(403, "VENUE_LOCKED", "Cơ sở đang bị khóa");
    if (venue.status === "HIDDEN") throw new HttpError(403, "VENUE_HIDDEN", "Cơ sở đang ở trạng thái ẩn");

    if (booking.status !== "CONFIRMED") {
      throw new HttpError(
        409,
        "INVALID_BOOKING_STATUS",
        `Đơn đặt sân đang ở trạng thái ${booking.status}, chỉ có thể check-in đơn CONFIRMED`,
      );
    }

    if (booking.checkedInAt) {
      throw new HttpError(409, "ALREADY_CHECKED_IN", "Đơn đặt sân đã được check-in trước đó");
    }

    // 4. Time window check (cửa sổ 30 phút trước startAt đến endAt muộn nhất)
    const now = Date.now();
    if (booking.startAt) {
      const startTime = booking.startAt instanceof Date
        ? booking.startAt.getTime()
        : (typeof booking.startAt === "string" ? new Date(booking.startAt).getTime() : 0);
      const windowStart = startTime - 30 * 60 * 1000;
      if (startTime > 0 && now < windowStart) {
        throw new HttpError(400, "TOO_EARLY", "Chưa đến thời gian check-in (chỉ được check-in trước giờ bắt đầu tối đa 30 phút)");
      }
    }

    const nowIso = new Date().toISOString();
    const updateData: Record<string, unknown> = {
      checkedInAt: nowIso,
      updatedAt: nowIso,
    };

    if (confirmPayment) {
      updateData.paymentStatus = "PAID";
      updateData.paidAt = nowIso;
    }

    const writes: defaultFirestore.Write[] = [
      updateWrite(`bookings/${resolvedBookingId}`, updateData),
    ];

    try {
      await firestore.commit(writes);
    } catch (e) {
      if (e instanceof FirestoreError && (e.code === "ABORTED" || e.code === "NOT_FOUND")) {
        throw new HttpError(409, "CHECKIN_CONFLICT", "Xung đột dữ liệu khi check-in");
      }
      throw e;
    }

    return new Response(
      JSON.stringify({
        success: true,
        bookingId: resolvedBookingId,
        status: "CONFIRMED",
        checkedInAt: nowIso,
        paymentStatus: confirmPayment ? "PAID" : (booking.paymentStatus || "DEPOSITED"),
      }),
      { status: 200, headers: { "Content-Type": "application/json" } },
    );
  } catch (err) {
    if (err instanceof HttpError) {
      return errorResponse(err.status, err.code, err.message);
    }
    const message = err instanceof Error ? err.message : "Lỗi máy chủ không xác định";
    return errorResponse(500, "INTERNAL_ERROR", message);
  }
}
