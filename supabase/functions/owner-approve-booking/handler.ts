import { errorResponse, HttpError, VerifiedToken, verifyIdToken } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { updateWrite, FirestoreError } from "../_shared/firestore.ts";

export interface OwnerApproveBookingDeps {
  firestore?: Pick<typeof defaultFirestore, "get" | "commit">;
  verifyToken?: (req: Request) => Promise<VerifiedToken>;
}

export async function handleOwnerApproveBooking(
  req: Request,
  deps: OwnerApproveBookingDeps = {},
): Promise<Response> {
  const firestore = deps.firestore ?? defaultFirestore;
  const verifyToken = deps.verifyToken ?? verifyIdToken;

  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");

  try {
    const token = await verifyToken(req);
    const body = await req.json().catch(() => ({}));

    const venueId = typeof body.venueId === "string" ? body.venueId.trim() : "";
    const bookingId = typeof body.bookingId === "string" ? body.bookingId.trim() : "";

    if (!venueId) throw new HttpError(400, "INVALID_VENUE_ID", "Thiếu hoặc sai venueId");
    if (!bookingId) throw new HttpError(400, "INVALID_BOOKING_ID", "Thiếu hoặc sai bookingId");

    // 1. Verify approved owner status
    if (token.appRole !== "ADMIN") {
      const user = await firestore.get(`users/${token.uid}`);
      if (user?.ownerStatus !== "APPROVED") {
        throw new HttpError(403, "FORBIDDEN", "Chủ sân chưa được phê duyệt không thể thực hiện thao tác");
      }
    }

    // 2. Verify venue ownership & status (T004: generic error, block LOCKED and HIDDEN)
    const venue = await firestore.get(`venues/${venueId}`);
    if (!venue || (venue.ownerId !== token.uid && token.appRole !== "ADMIN")) {
      throw new HttpError(404, "NOT_FOUND", "Không tìm thấy cơ sở hoặc bạn không có quyền");
    }
    if (venue.status === "LOCKED") {
      throw new HttpError(403, "VENUE_LOCKED", "Cơ sở đang bị quản trị viên khóa");
    }
    if (venue.status === "HIDDEN") {
      throw new HttpError(403, "VENUE_HIDDEN", "Cơ sở đang ở trạng thái ẩn, không thể vận hành");
    }

    // 3. Verify booking exists, belongs to venue, and is PENDING
    const booking = await firestore.get(`bookings/${bookingId}`);
    if (!booking || booking.venueId !== venueId) {
      throw new HttpError(404, "BOOKING_NOT_FOUND", "Không tìm thấy đơn đặt sân");
    }

    if (booking.status !== "PENDING") {
      throw new HttpError(
        409,
        "INVALID_BOOKING_STATUS",
        `Đơn đặt sân đang ở trạng thái ${booking.status}, chỉ có thể duyệt đơn PENDING`,
      );
    }

    // 4. Verify deadline
    const now = Date.now();
    if (booking.approvalDeadline) {
      const deadline = booking.approvalDeadline instanceof Date
        ? booking.approvalDeadline.getTime()
        : (typeof booking.approvalDeadline === "string" ? new Date(booking.approvalDeadline).getTime() : 0);
      if (deadline > 0 && deadline < now) {
        throw new HttpError(409, "DEADLINE_EXPIRED", "Đơn đặt sân đã quá hạn duyệt và không thể phê duyệt");
      }
    }

    // 5. Atomic update: PENDING -> CONFIRMED, generate qrToken
    const nowIso = new Date().toISOString();
    const qrToken = crypto.randomUUID();
    const writes: defaultFirestore.Write[] = [
      updateWrite(`bookings/${bookingId}`, {
        status: "CONFIRMED",
        qrToken,
        confirmedAt: nowIso,
        updatedAt: nowIso,
      }),
    ];

    try {
      await firestore.commit(writes);
    } catch (e) {
      if (e instanceof FirestoreError && (e.code === "ABORTED" || e.code === "NOT_FOUND")) {
        throw new HttpError(409, "BOOKING_CONFLICT", "Xung đột dữ liệu khi duyệt đơn đặt sân");
      }
      throw e;
    }

    return new Response(
      JSON.stringify({
        success: true,
        bookingId,
        status: "CONFIRMED",
        qrToken,
        confirmedAt: nowIso,
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
