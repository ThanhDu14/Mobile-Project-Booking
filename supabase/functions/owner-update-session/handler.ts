import { errorResponse, HttpError, verifyIdToken } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import {
  createWrite,
  deleteWrite,
  FirestoreError,
  updateWrite,
} from "../_shared/firestore.ts";
import { ACTIVE_STATUSES, deriveSessionFields } from "../_shared/dropin.ts";
import { parseOwnerSessionInput } from "../_shared/owner-dropin.ts";

export interface OwnerUpdateSessionDeps {
  firestore: Pick<typeof defaultFirestore, "runTransaction">;
}

export async function handleOwnerUpdateSession(
  req: Request,
  { firestore }: OwnerUpdateSessionDeps = { firestore: defaultFirestore },
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
    if (!venueId) throw new HttpError(400, "INVALID_VENUE_ID", "Thiếu venueId");
    if (!sessionId) {
      throw new HttpError(400, "INVALID_SESSION_ID", "Thiếu sessionId");
    }
    const input = parseOwnerSessionInput(body);
    await firestore.runTransaction(async (tx) => {
      if (token.appRole !== "ADMIN") {
        const user = await tx.get(`users/${token.uid}`);
        if (user?.ownerStatus !== "APPROVED") {
          throw new HttpError(403, "FORBIDDEN", "Chủ sân chưa được phê duyệt");
        }
      }
      const venue = await tx.get(`venues/${venueId}`);
      if (
        !venue || (venue.ownerId !== token.uid && token.appRole !== "ADMIN")
      ) {
        throw new HttpError(
          404,
          "NOT_FOUND",
          "Không tìm thấy cơ sở hoặc bạn không có quyền",
        );
      }
      if (venue.status === "LOCKED" || venue.status === "HIDDEN") {
        throw new HttpError(
          403,
          `VENUE_${venue.status}`,
          "Cơ sở không cho phép vận hành",
        );
      }
      const path = `dropInSessions/${sessionId}`;
      const session = await tx.get(path);
      if (!session || session.venueId !== venueId) {
        throw new HttpError(
          404,
          "SESSION_NOT_FOUND",
          "Không tìm thấy buổi chơi",
        );
      }
      if (!["OPEN", "FULL"].includes(String(session.status))) {
        throw new HttpError(
          409,
          "NOT_EDITABLE",
          "Buổi chơi không còn cho phép sửa",
        );
      }
      const registeredCount = typeof session.registeredCount === "number"
        ? session.registeredCount
        : 0;
      if (input.capacity < registeredCount) {
        throw new HttpError(
          409,
          "CAPACITY_TOO_LOW",
          "Sức chứa không được nhỏ hơn số chỗ đã đăng ký",
        );
      }
      for (const courtId of input.courtIds) {
        const court = await tx.get(`venues/${venueId}/courts/${courtId}`);
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
        ? await tx.get(`districts/${districtId}`)
        : null;
      const oldSlotIds = new Set<string>(
        Array.isArray(session.slotIds)
          ? session.slotIds.filter((id): id is string => typeof id === "string")
          : [],
      );
      const slots = new Map<string, Record<string, unknown> | null>();
      for (const slotId of new Set([...oldSlotIds, ...input.slotIds])) {
        const slot = await tx.get(`slots/${slotId}`);
        if (
          slot &&
          (slot.sessionId !== sessionId || slot.status !== "BLOCKED" ||
            slot.blockReason !== "DROP_IN")
        ) {
          throw new HttpError(
            409,
            "SLOT_CONFLICT",
            "Khung giờ đã có đơn hoặc bị khóa",
          );
        }
        slots.set(slotId, slot);
      }
      const changed = !(session.startAt instanceof Date) ||
        session.startAt.getTime() !== input.startAt.getTime() ||
        !(session.endAt instanceof Date) ||
        session.endAt.getTime() !== input.endAt.getTime() ||
        session.pricePerPerson !== input.pricePerPerson ||
        JSON.stringify(
            [...(Array.isArray(session.courtIds) ? session.courtIds : [])]
              .sort(),
          ) !== JSON.stringify([...input.courtIds].sort());
      const registrations = changed
        ? await tx.query("registrations", [], { parent: path })
        : [];
      const now = new Date();
      tx.write(updateWrite(path, {
        ...input,
        ownerId: venue.ownerId,
        districtId,
        ...deriveSessionFields({
          startAt: input.startAt,
          venue,
          districtName: typeof district?.name === "string" ? district.name : "",
        }),
        status: registeredCount >= input.capacity ? "FULL" : "OPEN",
        updatedAt: now,
      }));
      for (const reg of registrations) {
        if (ACTIVE_STATUSES.includes(String(reg.data.status))) {
          tx.write(
            updateWrite(reg.path, {
              sessionChanged: true,
              sessionStartAt: input.startAt,
              sessionEndAt: input.endAt,
              updatedAt: now,
            }),
          );
        }
      }
      const nextSlots = new Set(input.slotIds);
      for (const slotId of oldSlotIds) {
        if (!nextSlots.has(slotId) && slots.get(slotId)) {
          tx.write(deleteWrite(`slots/${slotId}`));
        }
      }
      for (const slotId of input.slotIds) {
        if (slots.get(slotId)) continue;
        const courtId = input.courtIds.find((id) =>
          slotId.startsWith(`${id}_${input.date}_`)
        )!;
        const time = slotId.slice(-4);
        const startAt = new Date(
          `${input.date.slice(0, 4)}-${input.date.slice(4, 6)}-${
            input.date.slice(6, 8)
          }T${time.slice(0, 2)}:${time.slice(2)}:00+07:00`,
        );
        tx.write(createWrite(`slots/${slotId}`, {
          venueId,
          courtId,
          date: input.date,
          startTime: time,
          endTime: new Date(startAt.getTime() + 30 * 60_000 + 7 * 3600_000)
            .toISOString().slice(11, 16).replace(":", ""),
          startAt,
          endAt: new Date(startAt.getTime() + 30 * 60_000),
          status: "BLOCKED",
          blockReason: "DROP_IN",
          sessionId,
        }));
      }
    });
    return Response.json({ success: true, sessionId });
  } catch (err) {
    if (err instanceof HttpError) {
      return errorResponse(err.status, err.code, err.message);
    }
    if (
      err instanceof FirestoreError &&
      ["ABORTED", "ALREADY_EXISTS", "NOT_FOUND"].includes(err.code)
    ) {
      return errorResponse(
        409,
        "SESSION_CONFLICT",
        "Xung đột dữ liệu khi sửa buổi chơi",
      );
    }
    return errorResponse(
      500,
      "INTERNAL_ERROR",
      err instanceof Error ? err.message : "Lỗi máy chủ không xác định",
    );
  }
}
