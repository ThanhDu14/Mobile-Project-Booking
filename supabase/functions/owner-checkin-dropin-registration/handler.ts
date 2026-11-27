import { errorResponse, HttpError, verifyIdToken } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { FirestoreError, updateWrite } from "../_shared/firestore.ts";
import { registrationPath } from "../_shared/dropin.ts";
import { requireOwnerVenue } from "../_shared/owner-access.ts";

export interface OwnerCheckinDropInDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "runTransaction">;
  clock?: () => Date;
}

export async function handleOwnerCheckinDropIn(
  req: Request,
  { firestore, clock = () => new Date() }: OwnerCheckinDropInDeps = { firestore: defaultFirestore },
): Promise<Response> {
  if (req.method !== "POST") {
    return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
  }

  try {
    const token = await verifyIdToken(req);
    const body = await req.json().catch(() => ({}));

    const venueId = typeof body.venueId === "string" ? body.venueId.trim() : "";
    const sessionId = typeof body.sessionId === "string"
      ? body.sessionId.trim()
      : "";
    const credential = typeof body.credential === "string"
      ? body.credential.trim()
      : "";

    if (!venueId || venueId.includes("/")) throw new HttpError(400, "INVALID_VENUE_ID", "Thiếu venueId");
    if (!sessionId || sessionId.includes("/")) {
      throw new HttpError(400, "INVALID_SESSION_ID", "Thiếu sessionId");
    }
    const ticketCode = credential.toUpperCase();
    const isTicketCode = /^DI-[A-HJ-NP-Z2-9]{8}$/.test(ticketCode);
    const isQrToken = /^[A-Za-z0-9_-]{43}$/.test(credential);
    if (!isTicketCode && !isQrToken) {
      throw new HttpError(
        400,
        "INVALID_CREDENTIAL",
        "Mã QR hoặc mã vé không hợp lệ",
      );
    }

    try {
      const result = await firestore.runTransaction(async (tx) => {
        const now = clock();
        const venue = await requireOwnerVenue(tx, token, venueId);
        if (venue.status !== "ACTIVE") throw new HttpError(403,"VENUE_UNAVAILABLE","Cơ sở đang ẩn hoặc bị khóa");
        // Read both documents in the transaction so concurrent check-ins cannot succeed twice.
        const session = await tx.get(`dropInSessions/${sessionId}`);
        if (!session || session.venueId !== venueId) {
          throw new HttpError(
            404,
            "SESSION_NOT_FOUND",
            "Không tìm thấy buổi chơi",
          );
        }

        const millis = (value: unknown) => value instanceof Date ? value.getTime() : typeof value === "string" ? Date.parse(value) : NaN;
        const start = millis(session.startAt), end = millis(session.endAt);
        if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) {
          throw new HttpError(409,"INVALID_SESSION_TIME","Buổi chơi chưa có thời gian hợp lệ");
        }
        if (now.getTime() < start - 30 * 60_000) throw new HttpError(400,"TOO_EARLY","Chỉ check-in từ 30 phút trước giờ bắt đầu buổi");
        if (now.getTime() >= end) throw new HttpError(400,"TOO_LATE","Buổi chơi đã kết thúc thời gian check-in");

        // C renders the raw qrToken; manual tickets use DI-XXXXXXXX. Resolve only
        // inside this authorized session, never treat an untrusted credential as a UID.
        const matches = await tx.query("registrations", [{
          field: isTicketCode ? "ticketCode" : "qrToken",
          op: "EQUAL",
          value: isTicketCode ? ticketCode : credential,
        }], { parent: `dropInSessions/${sessionId}`, limit: 2 });
        const match = matches[0];
        const registration = match?.data;
        if (
          matches.length !== 1 || !registration ||
          registration.sessionId !== sessionId ||
          registration.userId !== match.id
        ) {
          throw new HttpError(
            404,
            "REGISTRATION_NOT_FOUND",
            "Không tìm thấy đăng ký của người chơi",
          );
        }
        const path = registrationPath(sessionId, match.id);

        if (registration.status === "CHECKED_IN") {
          throw new HttpError(
            409,
            "ALREADY_CHECKED_IN",
            "Người chơi đã điểm danh trước đó",
          );
        }

        if (registration.status === "CANCELLED") {
          throw new HttpError(
            409,
            "REGISTRATION_CANCELLED",
            "Đăng ký này đã bị hủy",
          );
        }
        if (
          !["OPEN", "FULL"].includes(String(session.status)) ||
          registration.status !== "REGISTERED"
        ) {
          throw new HttpError(
            409,
            "NOT_CHECKIN_ALLOWED",
            "Buổi chơi hoặc đăng ký không cho phép điểm danh",
          );
        }

        tx.write(updateWrite(path, {
          status: "CHECKED_IN",
          checkedInAt: now,
          updatedAt: now,
        }));
        return {registrationId:match.id,checkedInAt:now.toISOString()};
      });
      return Response.json({
        success: true,
        registrationId:result.registrationId,
        sessionId,
        status: "CHECKED_IN",
        checkedInAt:result.checkedInAt,
      });
    } catch (e) {
      if (
        e instanceof FirestoreError &&
        (e.code === "ABORTED" || e.code === "NOT_FOUND")
      ) {
        throw new HttpError(
          409,
          "CHECKIN_CONFLICT",
          "Xung đột dữ liệu khi điểm danh",
        );
      }
      throw e;
    }
  } catch (err) {
    if (err instanceof HttpError) {
      return errorResponse(err.status, err.code, err.message);
    }
    const message = err instanceof Error
      ? err.message
      : "Lỗi máy chủ không xác định";
    return errorResponse(500, "INTERNAL_ERROR", message);
  }
}
