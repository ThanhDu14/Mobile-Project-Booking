import { errorResponse, HttpError, verifyIdToken } from "../_shared/auth.ts";
import * as fs from "../_shared/firestore.ts";
import { requireOwnerAuthority } from "../_shared/owner-access.ts";
import { ownerTransaction } from "../_shared/owner-transaction.ts";

function id(value: unknown): string {
  if (typeof value !== "string" || !value.trim() || value.includes("/")) {
    throw new HttpError(400, "INVALID_ID", "Mã cơ sở hoặc sân không hợp lệ");
  }
  return value;
}

function fields(value: unknown) {
  const data = (value ?? {}) as Record<string, unknown>;
  if (
    typeof data.name !== "string" || !data.name.trim() ||
    !["WOOD", "PU", "MAT"].includes(String(data.surfaceType)) ||
    typeof data.isActive !== "boolean" ||
    !Number.isSafeInteger(data.sortOrder) ||
    Number(data.sortOrder) < 0
  ) {
    throw new HttpError(
      400,
      "INVALID_COURT",
      "Tên, mặt sân, trạng thái hoặc thứ tự sân không hợp lệ",
    );
  }
  return {
    name: data.name,
    surfaceType: String(data.surfaceType),
    isActive: data.isActive,
    sortOrder: Number(data.sortOrder),
  };
}

/** Every court mutation serializes on the parent and recomputes its active count. */
export async function handleOwnerManageCourt(
  req: Request,
  { firestore }: { firestore: Pick<typeof fs, "runTransaction"> } = {
    firestore: fs,
  },
): Promise<Response> {
  return await ownerTransaction(req, firestore, async (request, source) => {
    try {
      const token = await verifyIdToken(request), body = await request.json();
      const venueId = id(body.venueId), courtId = id(body.courtId);
      const admin = await requireOwnerAuthority(source, token);
      const venue = await source.get(`venues/${venueId}`);
      if (!venue || (!admin && venue.ownerId !== token.uid)) {
        throw new HttpError(
          404,
          "NOT_FOUND",
          "Không tìm thấy cơ sở hoặc bạn không có quyền",
        );
      }
      if (!admin && venue.status === "LOCKED") {
        throw new HttpError(403, "VENUE_LOCKED", "Cơ sở đang bị khóa");
      }
      const rows = await source.query("courts", [], {
        parent: `venues/${venueId}`,
      });
      const existing = rows.find((row) => row.id === courtId);
      const writes: fs.Write[] = [];
      const path = `venues/${venueId}/courts/${courtId}`;
      let activeCount = rows.filter((row) => row.data.isActive === true).length;
      if (body.action === "CREATE" || body.action === "UPDATE") {
        const desired = fields(body.court);
        if (
          body.action === "CREATE" && existing &&
          JSON.stringify(fields(existing.data)) !== JSON.stringify(desired)
        ) {
          throw new HttpError(
            409,
            "STALE_CREATE",
            "Sân đã được tạo và thay đổi. Hãy tải lại trước khi thử lại.",
          );
        }
        if (body.action === "UPDATE" && !existing) {
          throw new HttpError(404, "NOT_FOUND", "Sân không còn tồn tại");
        }
        activeCount += Number(desired.isActive) -
          Number(existing?.data.isActive === true);
        writes.push(
          existing
            ? fs.updateWrite(path, desired)
            : fs.createWrite(path, desired),
        );
      } else if (body.action === "REORDER") {
        const secondId = id(body.secondCourtId),
          second = rows.find((row) => row.id === secondId);
        if (
          !existing || !second || secondId === courtId ||
          !Number.isSafeInteger(body.firstOrder) ||
          !Number.isSafeInteger(body.secondOrder) ||
          existing.data.sortOrder !== body.firstOrder ||
          second.data.sortOrder !== body.secondOrder
        ) {
          throw new HttpError(
            409,
            "STALE_ORDER",
            "Thứ tự sân vừa thay đổi. Hãy tải lại.",
          );
        }
        writes.push(fs.updateWrite(path, { sortOrder: body.secondOrder }));
        writes.push(
          fs.updateWrite(`venues/${venueId}/courts/${secondId}`, {
            sortOrder: body.firstOrder,
          }),
        );
      } else {throw new HttpError(
          400,
          "INVALID_ACTION",
          "Thao tác không hợp lệ",
        );}
      // Count is derived from authoritative courts, including legacy venues with a declared count.
      writes.push(
        fs.updateWrite(`venues/${venueId}`, { courtCount: activeCount }, [
          "updatedAt",
        ]),
      );
      await source.commit(writes);
      return Response.json({ venueId, courtId, courtCount: activeCount });
    } catch (error) {
      if (error instanceof fs.FirestoreError && error.code === "ABORTED") {
        throw error;
      }
      if (error instanceof HttpError) {
        return errorResponse(error.status, error.code, error.message);
      }
      return errorResponse(
        500,
        "COURT_UNAVAILABLE",
        "Chưa xác nhận được kết quả. Hãy tải lại hoặc thử lại cùng sân.",
      );
    }
  });
}
