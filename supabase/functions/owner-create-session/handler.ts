import { errorResponse, HttpError, verifyIdToken } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { createWrite, FirestoreError } from "../_shared/firestore.ts";
import { deriveSessionFields } from "../_shared/dropin.ts";
import { parseOwnerSessionInput } from "../_shared/owner-dropin.ts";

export interface OwnerCreateSessionDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "commit">;
}

export async function handleOwnerCreateSession(
  req: Request,
  { firestore }: OwnerCreateSessionDeps = { firestore: defaultFirestore },
): Promise<Response> {
  if (req.method !== "POST") {
    return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
  }

  try {
    const token = await verifyIdToken(req);
    const body = await req.json().catch(() => ({}));

    const venueId = typeof body.venueId === "string" ? body.venueId.trim() : "";
    if (!venueId) throw new HttpError(400, "INVALID_VENUE_ID", "Thiếu venueId");
    const input = parseOwnerSessionInput(body);
    const { courtIds, date, startTime, slotIds } = input;

    // 1. Verify owner status
    if (token.appRole !== "ADMIN") {
      const user = await firestore.get(`users/${token.uid}`);
      if (user?.ownerStatus !== "APPROVED") {
        throw new HttpError(403, "FORBIDDEN", "Chủ sân chưa được phê duyệt");
      }
    }

    // 2. Verify venue & court
    const venue = await firestore.get(`venues/${venueId}`);
    if (!venue || (venue.ownerId !== token.uid && token.appRole !== "ADMIN")) {
      throw new HttpError(
        404,
        "NOT_FOUND",
        "Không tìm thấy cơ sở hoặc bạn không có quyền",
      );
    }
    if (venue.status === "LOCKED") {
      throw new HttpError(403, "VENUE_LOCKED", "Cơ sở đang bị khóa");
    }
    if (venue.status === "HIDDEN") {
      throw new HttpError(403, "VENUE_HIDDEN", "Cơ sở đang ở trạng thái ẩn");
    }

    for (const courtId of courtIds) {
      const court = await firestore.get(`venues/${venueId}/courts/${courtId}`);
      if (!court) {
        throw new HttpError(404, "COURT_NOT_FOUND", "Không tìm thấy sân con");
      }
      if (court.isActive !== true) {
        throw new HttpError(
          400,
          "COURT_INACTIVE",
          "Sân con đang ngừng hoạt động",
        );
      }
    }
    const districtId = typeof venue.districtId === "string"
      ? venue.districtId
      : "";
    const district = districtId
      ? await firestore.get(`districts/${districtId}`)
      : null;

    // 4. Verify all slots are available
    for (const slotId of slotIds) {
      const existingSlot = await firestore.get(`slots/${slotId}`);
      if (existingSlot) {
        throw new HttpError(
          409,
          "SLOT_CONFLICT",
          `Khung giờ ${slotId} đã có đơn hoặc bị khóa`,
        );
      }
    }

    // 5. Create session document and block slots atomically
    const sessionId = `dropin_${courtIds[0]}_${date}_${startTime}`;
    const nowIso = new Date().toISOString();

    const sessionData = {
      sessionId,
      venueId,
      ...input,
      ownerId: venue.ownerId,
      districtId,
      ...deriveSessionFields({
        startAt: input.startAt,
        venue,
        districtName: typeof district?.name === "string" ? district.name : "",
      }),
      registeredCount: 0,
      status: "OPEN",
      createdAt: nowIso,
      updatedAt: nowIso,
    };

    const writes: defaultFirestore.Write[] = [
      createWrite(`dropInSessions/${sessionId}`, sessionData),
    ];

    for (const slotId of slotIds) {
      const courtId = courtIds.find((id) =>
        slotId.startsWith(`${id}_${date}_`)
      )!;
      const timePart = slotId.split("_").pop() || "";
      const sMin = parseInt(timePart.slice(0, 2), 10) * 60 +
        parseInt(timePart.slice(2, 4), 10);
      const eMin = sMin + 30;
      const endT = `${String(Math.floor(eMin / 60)).padStart(2, "0")}${
        String(eMin % 60).padStart(2, "0")
      }`;

      writes.push(
        createWrite(`slots/${slotId}`, {
          venueId,
          courtId,
          date,
          startTime: timePart,
          endTime: endT,
          status: "BLOCKED",
          blockReason: "DROP_IN",
          sessionId,
        }),
      );
    }

    try {
      await firestore.commit(writes);
    } catch (e) {
      if (
        e instanceof FirestoreError &&
        (e.code === "ALREADY_EXISTS" || e.code === "ABORTED")
      ) {
        throw new HttpError(
          409,
          "SESSION_CONFLICT",
          "Xung đột dữ liệu khi mở buổi chơi vãng lai",
        );
      }
      throw e;
    }

    return new Response(
      JSON.stringify({
        success: true,
        sessionId,
        status: "OPEN",
        slotCount: slotIds.length,
      }),
      { status: 200, headers: { "Content-Type": "application/json" } },
    );
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
