import { errorResponse, HttpError, verifyIdToken } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { deleteWrite, updateWrite, FirestoreError } from "../_shared/firestore.ts";
import { closeWaitlistInTx } from "../_shared/dropin.ts";

export interface OwnerCancelSessionDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "commit">;
}

export async function handleOwnerCancelSession(
  req: Request,
  { firestore }: OwnerCancelSessionDeps = { firestore: defaultFirestore },
): Promise<Response> {
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");

  try {
    const token = await verifyIdToken(req);
    const body = await req.json().catch(() => ({}));

    const venueId = typeof body.venueId === "string" ? body.venueId.trim() : "";
    const sessionId = typeof body.sessionId === "string" ? body.sessionId.trim() : "";
    const reason = typeof body.reason === "string" ? body.reason.trim() : "Chủ sân hủy buổi chơi";

    if (!venueId) throw new HttpError(400, "INVALID_VENUE_ID", "Thiếu venueId");
    if (!sessionId) throw new HttpError(400, "INVALID_SESSION_ID", "Thiếu sessionId");

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
      throw new HttpError(404, "SESSION_NOT_FOUND", "Không tìm thấy buổi chơi vãng lai");
    }

    if (session.status === "CANCELLED") {
      throw new HttpError(409, "ALREADY_CANCELLED", "Buổi chơi vãng lai đã bị hủy trước đó");
    }

    const nowIso = new Date().toISOString();
    const writes: defaultFirestore.Write[] = [
      updateWrite(`dropInSessions/${sessionId}`, {
        status: "CANCELLED",
        cancelledAt: nowIso,
        cancelReason: reason,
        updatedAt: nowIso,
      }),
      closeWaitlistInTx(sessionId, "SESSION_CANCELLED"),
    ];

    // Release all blocked slots
    const slotIds = Array.isArray(session.slotIds) ? session.slotIds : [];
    for (const slotId of slotIds) {
      if (typeof slotId === "string" && slotId.trim()) {
        writes.push(deleteWrite(`slots/${slotId.trim()}`, true));
      }
    }

    try {
      await firestore.commit(writes);
    } catch (e) {
      if (e instanceof FirestoreError && (e.code === "ABORTED" || e.code === "NOT_FOUND")) {
        throw new HttpError(409, "SESSION_CONFLICT", "Xung đột dữ liệu khi hủy buổi chơi vãng lai");
      }
      throw e;
    }

    return new Response(
      JSON.stringify({
        success: true,
        sessionId,
        status: "CANCELLED",
        cancelledAt: nowIso,
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
