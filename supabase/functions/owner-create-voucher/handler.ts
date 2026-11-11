import { errorResponse, HttpError, verifyIdToken } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { createWrite, FirestoreError } from "../_shared/firestore.ts";

export interface OwnerCreateVoucherDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "commit">;
}

export async function handleOwnerCreateVoucher(
  req: Request,
  { firestore }: OwnerCreateVoucherDeps = { firestore: defaultFirestore },
): Promise<Response> {
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");

  try {
    const token = await verifyIdToken(req);
    const body = await req.json().catch(() => ({}));

    const venueId = typeof body.venueId === "string" ? body.venueId.trim() : "";
    const rawCode = typeof body.code === "string" ? body.code.trim() : "";
    const code = rawCode.toUpperCase();
    const type = typeof body.type === "string" ? body.type.trim().toUpperCase() : "";
    const value = Number(body.value);
    const maxDiscount = Number(body.maxDiscount || 0);
    const minOrderTotal = Number(body.minOrderTotal || 0);
    const validFrom = typeof body.validFrom === "string" ? body.validFrom.trim() : "";
    const validTo = typeof body.validTo === "string" ? body.validTo.trim() : "";
    const usageLimit = Number(body.usageLimit);
    const perUserLimit = Number(body.perUserLimit);
    const isActive = body.isActive !== false;

    if (!venueId) throw new HttpError(400, "INVALID_VENUE_ID", "Thiếu venueId");
    if (!/^[A-Z0-9]{3,20}$/.test(code)) {
      throw new HttpError(400, "INVALID_CODE", "Mã voucher phải từ 3 đến 20 ký tự chữ hoa hoặc số");
    }
    if (type !== "PERCENTAGE" && type !== "FIXED") {
      throw new HttpError(400, "INVALID_TYPE", "Loại voucher phải là PERCENTAGE hoặc FIXED");
    }
    if (value <= 0) throw new HttpError(400, "INVALID_VALUE", "Giá trị voucher phải lớn hơn 0");
    if (type === "PERCENTAGE" && value > 100) {
      throw new HttpError(400, "INVALID_PERCENTAGE", "Phần trăm giảm không được vượt quá 100%");
    }
    if (maxDiscount < 0 || minOrderTotal < 0) {
      throw new HttpError(400, "INVALID_AMOUNT", "Mức giảm tối đa và đơn tối thiểu không được âm");
    }
    if (!validFrom || !validTo || new Date(validFrom) >= new Date(validTo)) {
      throw new HttpError(400, "INVALID_DATE_RANGE", "Thời gian hiệu lực không hợp lệ");
    }
    if (usageLimit <= 0 || perUserLimit <= 0 || perUserLimit > usageLimit) {
      throw new HttpError(400, "INVALID_LIMIT", "Giới hạn sử dụng không hợp lệ");
    }

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
    if (venue.status === "LOCKED") throw new HttpError(403, "VENUE_LOCKED", "Cơ sở đang bị khóa");
    if (venue.status === "HIDDEN") throw new HttpError(403, "VENUE_HIDDEN", "Cơ sở đang ở trạng thái ẩn");

    // 3. System-wide code check (T006 path context venues/{venueId}/vouchers/{code})
    const existing = await firestore.get(`venues/${venueId}/vouchers/${code}`);
    if (existing) {
      throw new HttpError(409, "VOUCHER_EXISTS", `Mã voucher ${code} đã tồn tại trong cơ sở`);
    }

    const nowIso = new Date().toISOString();
    const writes: defaultFirestore.Write[] = [
      createWrite(`venues/${venueId}/vouchers/${code}`, {
        code,
        type,
        value,
        maxDiscount,
        minOrderTotal,
        validFrom,
        validTo,
        usageLimit,
        perUserLimit,
        usedCount: 0, // Server manages counter, owner cannot override
        isActive,
        createdAt: nowIso,
        updatedAt: nowIso,
      }),
    ];

    try {
      await firestore.commit(writes);
    } catch (e) {
      if (e instanceof FirestoreError && (e.code === "ALREADY_EXISTS" || e.code === "ABORTED")) {
        throw new HttpError(409, "VOUCHER_CONFLICT", "Xung đột dữ liệu khi tạo voucher");
      }
      throw e;
    }

    return new Response(
      JSON.stringify({
        success: true,
        code,
        venueId,
        isActive,
        createdAt: nowIso,
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
