import { errorResponse, HttpError, verifyIdToken } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { createWrite, FirestoreError } from "../_shared/firestore.ts";

export interface OwnerCreateSessionDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "commit">;
}

export async function handleOwnerCreateSession(
  req: Request,
  { firestore }: OwnerCreateSessionDeps = { firestore: defaultFirestore },
): Promise<Response> {
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");

  try {
    const token = await verifyIdToken(req);
    const body = await req.json().catch(() => ({}));

    const venueId = typeof body.venueId === "string" ? body.venueId.trim() : "";
    const courtId = typeof body.courtId === "string" ? body.courtId.trim() : "";
    const date = typeof body.date === "string" ? body.date.trim() : "";
    const startTime = typeof body.startTime === "string" ? body.startTime.trim() : "";
    const endTime = typeof body.endTime === "string" ? body.endTime.trim() : "";
    const capacity = Number(body.capacity);
    const pricePerPerson = Number(body.pricePerPerson);
    const level = typeof body.level === "string" ? body.level.trim() : "ALL";
    const note = typeof body.note === "string" ? body.note.trim() : "";

    if (!venueId) throw new HttpError(400, "INVALID_VENUE_ID", "Thiếu venueId");
    if (!courtId) throw new HttpError(400, "INVALID_COURT_ID", "Thiếu courtId");
    if (!/^\d{8}$/.test(date)) throw new HttpError(400, "INVALID_DATE", "Ngày phải có định dạng yyyyMMdd");
    if (!startTime || !endTime) throw new HttpError(400, "INVALID_TIME", "Giờ bắt đầu và kết thúc không được để trống");
    if (!Number.isInteger(capacity) || capacity <= 0) throw new HttpError(400, "INVALID_CAPACITY", "Sức chứa phải là số nguyên dương");
    if (!Number.isInteger(pricePerPerson) || pricePerPerson < 0) throw new HttpError(400, "INVALID_PRICE", "Giá vé không được âm");

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
      throw new HttpError(404, "NOT_FOUND", "Không tìm thấy cơ sở hoặc bạn không có quyền");
    }
    if (venue.status === "LOCKED") throw new HttpError(403, "VENUE_LOCKED", "Cơ sở đang bị khóa");
    if (venue.status === "HIDDEN") throw new HttpError(403, "VENUE_HIDDEN", "Cơ sở đang ở trạng thái ẩn");

    const court = await firestore.get(`venues/${venueId}/courts/${courtId}`);
    if (!court) throw new HttpError(404, "COURT_NOT_FOUND", "Không tìm thấy sân con");
    if (court.isActive === false) throw new HttpError(400, "COURT_INACTIVE", "Sân con đang ngừng hoạt động");

    // 3. Generate slot IDs between startTime and endTime (e.g. 0800 to 1000 -> 0800, 0830, 0900, 0930)
    const startHour = parseInt(startTime.slice(0, 2), 10);
    const startMin = parseInt(startTime.slice(2, 4), 10);
    const endHour = parseInt(endTime.slice(0, 2), 10);
    const endMin = parseInt(endTime.slice(2, 4), 10);

    const startTotal = startHour * 60 + startMin;
    const endTotal = endHour * 60 + endMin;
    if (endTotal <= startTotal) {
      throw new HttpError(400, "INVALID_TIME_RANGE", "Giờ kết thúc phải sau giờ bắt đầu");
    }

    const slotIds: string[] = [];
    for (let m = startTotal; m < endTotal; m += 30) {
      const hh = String(Math.floor(m / 60)).padStart(2, "0");
      const mm = String(m % 60).padStart(2, "0");
      slotIds.push(`${courtId}_${date}_${hh}${mm}`);
    }

    // 4. Verify all slots are available
    for (const slotId of slotIds) {
      const existingSlot = await firestore.get(`slots/${slotId}`);
      if (existingSlot) {
        throw new HttpError(409, "SLOT_CONFLICT", `Khung giờ ${slotId} đã có đơn hoặc bị khóa`);
      }
    }

    // 5. Create session document and block slots atomically
    const sessionId = `dropin_${courtId}_${date}_${startTime}`;
    const nowIso = new Date().toISOString();

    const sessionData = {
      sessionId,
      venueId,
      courtId,
      date,
      startTime,
      endTime,
      capacity,
      registeredCount: 0,
      pricePerPerson,
      level,
      note,
      status: "OPEN",
      slotIds,
      createdAt: nowIso,
      updatedAt: nowIso,
    };

    const writes: defaultFirestore.Write[] = [
      createWrite(`dropInSessions/${sessionId}`, sessionData),
    ];

    for (const slotId of slotIds) {
      const timePart = slotId.split("_").pop() || "";
      const sMin = parseInt(timePart.slice(0, 2), 10) * 60 + parseInt(timePart.slice(2, 4), 10);
      const eMin = sMin + 30;
      const endT = `${String(Math.floor(eMin / 60)).padStart(2, "0")}${String(eMin % 60).padStart(2, "0")}`;

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
      if (e instanceof FirestoreError && (e.code === "ALREADY_EXISTS" || e.code === "ABORTED")) {
        throw new HttpError(409, "SESSION_CONFLICT", "Xung đột dữ liệu khi mở buổi chơi vãng lai");
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
    const message = err instanceof Error ? err.message : "Lỗi máy chủ không xác định";
    return errorResponse(500, "INTERNAL_ERROR", message);
  }
}
