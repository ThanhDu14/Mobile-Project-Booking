import { errorResponse, HttpError, verifyIdToken } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { createWrite, deleteWrite, updateWrite, FirestoreError } from "../_shared/firestore.ts";

export interface ManageSlotBlocksDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "commit">;
}

const ALLOWED_OWNER_REASONS = new Set(["MAINTENANCE", "EVENT", "WALK_IN"]);

export async function handleManageSlotBlocks(
  req: Request,
  { firestore }: ManageSlotBlocksDeps = { firestore: defaultFirestore },
): Promise<Response> {
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");

  try {
    const token = await verifyIdToken(req);
    const body = await req.json().catch(() => ({}));

    const venueId = typeof body.venueId === "string" ? body.venueId.trim() : "";
    const courtId = typeof body.courtId === "string" ? body.courtId.trim() : "";
    const action = typeof body.action === "string" ? body.action.trim().toUpperCase() : "";

    if (!venueId) throw new HttpError(400, "INVALID_VENUE_ID", "Thiếu hoặc sai venueId");
    if (!courtId) throw new HttpError(400, "INVALID_COURT_ID", "Thiếu hoặc sai courtId");
    if (action !== "LOCK" && action !== "UNLOCK") {
      throw new HttpError(400, "INVALID_ACTION", "Hành động phải là LOCK hoặc UNLOCK");
    }

    // 1. Verify approved owner status
    if (token.appRole !== "ADMIN") {
      const user = await firestore.get(`users/${token.uid}`);
      if (user?.ownerStatus !== "APPROVED") {
        throw new HttpError(403, "FORBIDDEN", "Chủ sân chưa được phê duyệt không thể khóa hoặc mở khóa sân");
      }
    }

    // 2. Verify venue ownership & status
    const venue = await firestore.get(`venues/${venueId}`);
    if (!venue) throw new HttpError(404, "VENUE_NOT_FOUND", "Không tìm thấy cơ sở");
    if (venue.ownerId !== token.uid && token.appRole !== "ADMIN") {
      throw new HttpError(403, "FORBIDDEN", "Bạn không có quyền quản lý cơ sở này");
    }
    if (venue.status === "LOCKED") {
      throw new HttpError(403, "VENUE_LOCKED", "Cơ sở đang bị quản trị viên khóa");
    }

    // 3. Verify court exists & belongs to venue
    const court = await firestore.get(`venues/${venueId}/courts/${courtId}`);
    if (!court) throw new HttpError(404, "COURT_NOT_FOUND", "Không tìm thấy sân con");
    if (court.isActive === false) {
      throw new HttpError(400, "COURT_INACTIVE", "Sân con đang ngừng hoạt động");
    }

    // 4. Handle LOCK
    if (action === "LOCK") {
      const date = typeof body.date === "string" ? body.date.trim() : "";
      if (!/^\d{8}$/.test(date)) {
        throw new HttpError(400, "INVALID_DATE", "Ngày phải có định dạng yyyyMMdd");
      }

      const blockReason = typeof body.blockReason === "string" ? body.blockReason.trim() : "";
      if (!ALLOWED_OWNER_REASONS.has(blockReason)) {
        throw new HttpError(
          400,
          "INVALID_BLOCK_REASON",
          "Lý do khóa phải là MAINTENANCE, EVENT hoặc WALK_IN",
        );
      }

      const slotIds = Array.isArray(body.slotIds)
        ? body.slotIds.map((s: unknown) => String(s).trim()).filter(Boolean)
        : [];
      if (slotIds.length === 0 || slotIds.length > 48) {
        throw new HttpError(400, "INVALID_SLOT_IDS", "Danh sách khung giờ phải từ 1 đến 48 slot");
      }

      // Check slot IDs prefix
      const prefix = `${courtId}_${date}_`;
      for (const slotId of slotIds) {
        if (!slotId.startsWith(prefix)) {
          throw new HttpError(
            400,
            "INVALID_SLOT_ID_PREFIX",
            `Slot ID ${slotId} không khớp với sân ${courtId} và ngày ${date}`,
          );
        }
      }

      // Read current state of all requested slots
      const writes: defaultFirestore.Write[] = [];
      const now = Date.now();

      for (const slotId of slotIds) {
        const slot = await firestore.get(`slots/${slotId}`);
        let isExpiredHold = false;

        if (slot) {
          const status = slot.status as string | undefined;
          if (status === "BOOKED") {
            throw new HttpError(409, "SLOT_BOOKED", `Khung giờ ${slotId} đã có đơn đặt sân`);
          }
          if (status === "BLOCKED") {
            throw new HttpError(409, "SLOT_ALREADY_BLOCKED", `Khung giờ ${slotId} đã bị khóa trước đó`);
          }
          if (status === "HELD") {
            const holdExpiry = slot.holdExpiresAt instanceof Date
              ? slot.holdExpiresAt.getTime()
              : 0;
            if (holdExpiry > now) {
              throw new HttpError(409, "SLOT_HELD", `Khung giờ ${slotId} đang được giữ chỗ`);
            }
            isExpiredHold = true;
          }
        }

        const timePart = slotId.substring(prefix.length);
        const hour = parseInt(timePart.slice(0, 2), 10);
        const min = parseInt(timePart.slice(2, 4), 10);
        const endMinutes = hour * 60 + min + 30;
        const endTime = `${String(Math.floor(endMinutes / 60)).padStart(2, "0")}${String(endMinutes % 60).padStart(2, "0")}`;

        const slotData = {
          venueId,
          courtId,
          date,
          startTime: timePart,
          endTime,
          status: "BLOCKED",
          blockReason,
        };

        if (isExpiredHold) {
          writes.push(updateWrite(`slots/${slotId}`, slotData));
        } else {
          writes.push(createWrite(`slots/${slotId}`, slotData));
        }
      }

      try {
        await firestore.commit(writes);
      } catch (e) {
        if (e instanceof FirestoreError && (e.code === "ALREADY_EXISTS" || e.code === "ABORTED")) {
          throw new HttpError(
            409,
            "SLOT_CONFLICT",
            "Xung đột dữ liệu khi khóa sân (slot vừa bị đặt hoặc thay đổi bởi người khác)",
          );
        }
        throw e;
      }

      return new Response(
        JSON.stringify({
          success: true,
          count: slotIds.length,
          lockedSlotIds: slotIds,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      );
    }

    // 5. Handle UNLOCK
    const slotIds = Array.isArray(body.slotIds)
      ? body.slotIds.map((s: unknown) => String(s).trim()).filter(Boolean)
      : [];
    if (slotIds.length === 0 || slotIds.length > 48) {
      throw new HttpError(400, "INVALID_SLOT_IDS", "Danh sách khung giờ mở khóa phải từ 1 đến 48 slot");
    }

    const writes: defaultFirestore.Write[] = [];

    for (const slotId of slotIds) {
      const slot = await firestore.get(`slots/${slotId}`);
      if (!slot) {
        throw new HttpError(409, "SLOT_NOT_FOUND", `Khung giờ ${slotId} không tồn tại hoặc đã được mở`);
      }

      const status = slot.status as string | undefined;
      if (status !== "BLOCKED") {
        throw new HttpError(
          403,
          "CANNOT_UNLOCK_NON_BLOCKED",
          `Không thể mở khóa khung giờ ${slotId} vì trạng thái hiện tại là ${status}`,
        );
      }

      const reason = slot.blockReason as string | undefined;
      if (!reason || !ALLOWED_OWNER_REASONS.has(reason)) {
        if (reason === "DROP_IN") {
          throw new HttpError(
            403,
            "PROTECTED_DROP_IN",
            `Không thể mở khóa khung giờ ${slotId} do đây là ca Drop-in (thuộc Feature 112/080)`,
          );
        }
        throw new HttpError(
          403,
          "UNAUTHORIZED_BLOCK_REASON",
          `Không có quyền mở khóa khung giờ ${slotId} với lý do ${reason}`,
        );
      }

      writes.push(deleteWrite(`slots/${slotId}`, true));
    }

    try {
      await firestore.commit(writes);
    } catch (e) {
      if (e instanceof FirestoreError && (e.code === "NOT_FOUND" || e.code === "ABORTED")) {
        throw new HttpError(409, "SLOT_CONFLICT", "Xung đột dữ liệu khi mở khóa sân");
      }
      throw e;
    }

    return new Response(
      JSON.stringify({
        success: true,
        count: slotIds.length,
        unlockedSlotIds: slotIds,
      }),
      { status: 200, headers: { "Content-Type": "application/json" } },
    );
  } catch (e) {
    if (e instanceof HttpError) {
      return errorResponse(e.status, e.code, e.message, e.extra);
    }
    console.error(e);
    return errorResponse(500, "INTERNAL", "Lỗi máy chủ khi xử lý khóa/mở khóa sân");
  }
}
