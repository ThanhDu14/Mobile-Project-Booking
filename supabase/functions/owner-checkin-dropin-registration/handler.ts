import { errorResponse, HttpError, verifyIdToken } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { updateWrite, FirestoreError } from "../_shared/firestore.ts";

export interface OwnerCheckinDropInDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "commit">;
}

export async function handleOwnerCheckinDropIn(
  req: Request,
  { firestore }: OwnerCheckinDropInDeps = { firestore: defaultFirestore },
): Promise<Response> {
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");

  try {
    const token = await verifyIdToken(req);
    const body = await req.json().catch(() => ({}));

    const venueId = typeof body.venueId === "string" ? body.venueId.trim() : "";
    const sessionId = typeof body.sessionId === "string" ? body.sessionId.trim() : "";
    const registrationId = typeof body.registrationId === "string" ? body.registrationId.trim() : "";

    if (!venueId) throw new HttpError(400, "INVALID_VENUE_ID", "Thiếu venueId");
    if (!sessionId) throw new HttpError(400, "INVALID_SESSION_ID", "Thiếu sessionId");
    if (!registrationId) throw new HttpError(400, "INVALID_REGISTRATION_ID", "Thiếu registrationId");

    // 1. Verify owner
    if (token.appRole !== "ADMIN") {
      const user = await firestore.get(`users/${token.uid}`);
      if (user?.ownerStatus !== "APPROVED") {
        throw new HttpError(403, "FORBIDDEN", "Chủ sân chưa được phê duyệt");
      }
    }

    // 2. Verify venue
    const venue = await firestore.get(`venues/${venueId}`);
    if (!venue || (venue.ownerId !== token.uid && token.appRole !== "ADMIN")) {
      throw new HttpError(404, "NOT_FOUND", "Không tìm thấy cơ sở hoặc bạn không có quyền");
    }

    // 3. Verify session
    const session = await firestore.get(`dropInSessions/${sessionId}`);
    if (!session || session.venueId !== venueId) {
      throw new HttpError(404, "SESSION_NOT_FOUND", "Không tìm thấy buổi chơi");
    }

    // 4. Verify registration
    const registration = await firestore.get(`registrations/${registrationId}`);
    if (!registration || registration.sessionId !== sessionId) {
      throw new HttpError(404, "REGISTRATION_NOT_FOUND", "Không tìm thấy đăng ký của người chơi");
    }

    if (registration.status === "CHECKED_IN") {
      throw new HttpError(409, "ALREADY_CHECKED_IN", "Người chơi đã điểm danh trước đó");
    }

    if (registration.status === "CANCELLED") {
      throw new HttpError(409, "REGISTRATION_CANCELLED", "Đăng ký này đã bị hủy");
    }

    const nowIso = new Date().toISOString();
    const writes: defaultFirestore.Write[] = [
      updateWrite(`registrations/${registrationId}`, {
        status: "CHECKED_IN",
        checkedInAt: nowIso,
        updatedAt: nowIso,
      }),
    ];

    try {
      await firestore.commit(writes);
    } catch (e) {
      if (e instanceof FirestoreError && (e.code === "ABORTED" || e.code === "NOT_FOUND")) {
        throw new HttpError(409, "CHECKIN_CONFLICT", "Xung đột dữ liệu khi điểm danh");
      }
      throw e;
    }

    return new Response(
      JSON.stringify({
        success: true,
        registrationId,
        sessionId,
        status: "CHECKED_IN",
        checkedInAt: nowIso,
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
