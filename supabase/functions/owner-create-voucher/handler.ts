import { errorResponse, HttpError, verifyIdToken } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { createWrite, updateWrite } from "../_shared/firestore.ts";

export interface OwnerCreateVoucherDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "commit" | "runTransaction">;
}

export function validateOwnerVoucher(body: Record<string, unknown>): Record<string, unknown> {
  const type = body.type;
  if (type !== "PERCENT" && type !== "FIXED") throw new HttpError(400, "INVALID_TYPE", "Loại voucher không hợp lệ");
  const amount = (key: string, minimum: number): number => {
    const value = body[key];
    if (typeof value !== "number" || !Number.isSafeInteger(value) || value < minimum) {
      throw new HttpError(400, "INVALID_AMOUNT", `${key} không hợp lệ`);
    }
    return value;
  };
  const value = amount("value", 1);
  if (type === "PERCENT" && value > 100) throw new HttpError(400, "INVALID_PERCENTAGE", "Phần trăm giảm không được vượt quá 100%");
  const maxDiscount = amount("maxDiscount", 0);
  const minOrderTotal = amount("minOrderTotal", 0);
  const usageLimit = amount("usageLimit", 1);
  const perUserLimit = amount("perUserLimit", 1);
  if (perUserLimit > usageLimit) throw new HttpError(400, "INVALID_LIMIT", "Giới hạn mỗi người vượt tổng số lượt");
  const validFrom = typeof body.validFrom === "string" ? new Date(body.validFrom) : new Date(NaN);
  const validTo = typeof body.validTo === "string" ? new Date(body.validTo) : new Date(NaN);
  if (!Number.isFinite(validFrom.getTime()) || !Number.isFinite(validTo.getTime()) || validFrom >= validTo) {
    throw new HttpError(400, "INVALID_DATE_RANGE", "Thời gian hiệu lực không hợp lệ");
  }
  const isActive = body.isActive === undefined ? true : body.isActive;
  if (typeof isActive !== "boolean") throw new HttpError(400, "INVALID_ACTIVE", "Trạng thái voucher không hợp lệ");
  return { type, value, maxDiscount, minOrderTotal, usageLimit, perUserLimit, validFrom, validTo, isActive };
}

/** All venue voucher mutations use this endpoint; usage counters remain server-owned. */
export async function handleOwnerCreateVoucher(
  req: Request,
  { firestore }: OwnerCreateVoucherDeps = { firestore: defaultFirestore },
): Promise<Response> {
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
  try {
    const token = await verifyIdToken(req);
    const body = await req.json().catch(() => ({})) as Record<string, unknown>;
    const venueId = typeof body.venueId === "string" ? body.venueId.trim() : "";
    const code = typeof body.code === "string" ? body.code.trim().toUpperCase() : "";
    const action = body.action ?? "CREATE";
    if (!venueId || venueId.includes("/")) throw new HttpError(400, "INVALID_VENUE_ID", "Cơ sở không hợp lệ");
    if (!/^[A-Z0-9]{3,20}$/.test(code)) throw new HttpError(400, "INVALID_CODE", "Mã voucher cần 3–20 chữ hoa hoặc chữ số");
    if (!["CREATE", "UPDATE", "SET_ACTIVE"].includes(String(action))) throw new HttpError(400, "INVALID_ACTION", "Thao tác không hợp lệ");
    const fields = action === "SET_ACTIVE" ? { isActive: body.isActive === undefined ? true : body.isActive } : validateOwnerVoucher(body);
    if (typeof fields.isActive !== "boolean") throw new HttpError(400, "INVALID_ACTIVE", "Trạng thái không hợp lệ");
    const path = `venues/${venueId}/vouchers/${code}`;
    await firestore.runTransaction(async tx => {
      const user = await tx.get(`users/${token.uid}`);
      const venue = await tx.get(`venues/${venueId}`);
      if (token.appRole !== "OWNER" || user?.role !== "OWNER" || user.ownerStatus !== "APPROVED" ||
          user.accountStatus !== "ACTIVE" || user.deletionRequestedAt != null || !venue || venue.ownerId !== token.uid) {
        throw new HttpError(403, "FORBIDDEN", "Bạn không có quyền quản lý cơ sở này");
      }
      if (venue.status !== "ACTIVE") throw new HttpError(403, "VENUE_UNAVAILABLE", "Cơ sở đang ẩn hoặc bị khóa");
      const existing = await tx.get(path);
      const now = new Date();
      if (action === "CREATE") {
        const system = await tx.get(`systemVouchers/${code}`);
        const venues = await tx.query("vouchers", [{ field: "code", op: "EQUAL", value: code }], { allDescendants: true, limit: 1 });
        if (existing || system || venues.length) throw new HttpError(409, "VOUCHER_EXISTS", "Mã voucher đã tồn tại trên hệ thống");
        tx.write(createWrite(path, { ...fields, code, usedCount: 0, createdAt: now, updatedAt: now }));
      } else {
        if (!existing) throw new HttpError(404, "NOT_FOUND", "Không tìm thấy voucher");
        if (action === "UPDATE" && Number(fields.usageLimit) < Number(existing.usedCount ?? 0)) {
          throw new HttpError(409, "LIMIT_BELOW_USAGE", "Tổng số lượt không được thấp hơn số lượt đã dùng");
        }
        tx.write(updateWrite(path, { ...fields, updatedAt: now }));
      }
    });
    return new Response(JSON.stringify({ success: true, code, venueId }), { headers: { "Content-Type": "application/json" } });
  } catch (err) {
    if (err instanceof HttpError) return errorResponse(err.status, err.code, err.message);
    console.error("owner voucher failed", err instanceof Error ? err.name : "unknown");
    return errorResponse(500, "INTERNAL_ERROR", "Không thể lưu voucher. Vui lòng tải lại để kiểm tra kết quả.");
  }
}
