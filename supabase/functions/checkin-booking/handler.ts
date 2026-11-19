import { errorResponse, HttpError, verifyIdToken } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { updateWrite, FirestoreError } from "../_shared/firestore.ts";
import { ownerTransaction } from "../_shared/owner-transaction.ts";

export interface CheckinBookingDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "commit"> & Partial<Pick<typeof defaultFirestore, "query" | "runTransaction">>;
}

export async function handleCheckinBooking(
  req: Request,
  { firestore }: CheckinBookingDeps = { firestore: defaultFirestore },
): Promise<Response> {
  if (firestore.runTransaction) return ownerTransaction(req, { runTransaction: firestore.runTransaction },
    (request, source) => handleCheckinBooking(request, { firestore: source }));
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");

  try {
    const token = await verifyIdToken(req);
    const body = await req.json().catch(() => ({}));

    // venueId không bắt buộc: client chỉ có mã quét được (bookingId hoặc qrToken) và không còn
    // quyền query qrToken (Rules spec 112 T012). Server tra đơn rồi lấy venueId từ chính đơn.
    const requestedVenueId = typeof body.venueId === "string" ? body.venueId.trim() : "";
    const bookingId = typeof body.bookingId === "string" ? body.bookingId.trim() : "";
    if (body.confirmPayment != null && typeof body.confirmPayment !== "boolean") throw new HttpError(400,"INVALID_CONFIRMATION","Xác nhận thu tiền không hợp lệ");
    const confirmPayment = body.confirmPayment === true;

    if (!/^[A-Za-z0-9_-]{1,256}$/.test(bookingId) || requestedVenueId.includes("/")) throw new HttpError(400, "INVALID_BOOKING_ID", "Mã đơn không hợp lệ");

    // 1. Verify owner status
    const user = await firestore.get(`users/${token.uid}`);
    const admin = token.appRole === "ADMIN" && user?.role === "ADMIN";
    if (!user || user.accountStatus !== "ACTIVE" || user.deletionRequestedAt != null ||
        (!admin && (token.appRole !== "OWNER" || user.role !== "OWNER" || user.ownerStatus !== "APPROVED"))) {
        throw new HttpError(403, "FORBIDDEN", "Chủ sân chưa được phê duyệt");
    }

    // 2. Tìm đơn theo bookingId, không có thì tra theo qrToken
    let booking = await firestore.get(`bookings/${bookingId}`);
    let resolvedBookingId = bookingId;
    if (!booking && typeof firestore.query === "function") {
      const byToken = await firestore.query("bookings", [
        { field: "qrToken", op: "EQUAL", value: bookingId },
      ], { limit: 2 });
      if (byToken.length === 1) {
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
    if (!venue || (venue.ownerId !== token.uid && !admin)) {
      throw new HttpError(404, "BOOKING_NOT_FOUND", "Không tìm thấy đơn đặt sân");
    }
    if (venue.status !== "ACTIVE") throw new HttpError(403, "VENUE_UNAVAILABLE", "Cơ sở đang ẩn hoặc bị khóa");

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
    const timestamp = (value: unknown) => value instanceof Date ? value.getTime() : typeof value === "string" ? Date.parse(value) : NaN;
    const startTime=timestamp(booking.startAt),endTime=timestamp(booking.endAt);
    if (!Number.isFinite(startTime) || !Number.isFinite(endTime) || endTime<=startTime) throw new HttpError(409,"INVALID_BOOKING_TIME","Đơn chưa có thời gian hợp lệ");
    if (now < startTime-1800000) throw new HttpError(400,"TOO_EARLY","Chỉ check-in từ 30 phút trước giờ bắt đầu");
    if (now >= endTime) throw new HttpError(400,"TOO_LATE","Đơn đã hết thời gian check-in");

    const money=(value:unknown) => typeof value === "number" && Number.isSafeInteger(value) && value>=0 ? value : null;
    const total=money(booking.total);
    if(total==null) throw new HttpError(409,"INVALID_PAYMENT_DATA","Đơn thiếu số tiền hợp lệ");
    if(booking.paymentStatus === "REFUNDED") throw new HttpError(409,"BOOKING_REFUNDED","Đơn đã được hoàn tiền");
    const collected=booking.paymentStatus === "PAID" ? total : booking.paymentStatus === "DEPOSITED" ? money(booking.depositAmount) : 0;
    if(collected==null || collected>total) throw new HttpError(409,"INVALID_PAYMENT_DATA","Số tiền đặt cọc không hợp lệ");
    const remaining=Math.max(total-collected,0);
    if(remaining>0 && !["AT_VENUE","DEPOSIT"].includes(String(booking.paymentMethod))) throw new HttpError(409,"BOOKING_UNPAID","Đơn chưa được thanh toán");
    const items=Array.isArray(booking.items)?booking.items:[];
    const courtNames=Array.isArray(booking.courtNames)?booking.courtNames.filter(name=>typeof name === "string"):
      [...new Set(items.map(item=>item?.courtName).filter(name=>typeof name === "string"))];
    const projection={bookingId:resolvedBookingId,status:"CONFIRMED",courtNames,startAt:new Date(startTime).toISOString(),endAt:new Date(endTime).toISOString()};
    if(remaining>0 && !confirmPayment) return Response.json({...projection,success:true,requiresPaymentConfirmation:true,remainingAmount:remaining,paymentStatus:booking.paymentStatus??"UNPAID"});

    const nowIso = new Date().toISOString();
    const updateData: Record<string, unknown> = {
      checkedInAt: new Date(now),
      updatedAt: new Date(now),
    };

    if (confirmPayment && remaining>0) {
      updateData.paymentStatus = "PAID";
      updateData.paidAt = new Date(now);
      updateData.paidAmount = total;
      updateData.remainingAmount = 0;
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
        ...projection,
        bookingId: resolvedBookingId,
        status: "CONFIRMED",
        checkedInAt: nowIso,
        paymentStatus: confirmPayment && remaining>0 ? "PAID" : booking.paymentStatus,
        remainingAmount: 0,
        requiresPaymentConfirmation: false,
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
