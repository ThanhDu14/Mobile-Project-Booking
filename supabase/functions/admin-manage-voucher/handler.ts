import { HttpError, requireRole, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { createWrite, updateWrite, FirestoreError } from "../_shared/firestore.ts";

export interface AdminManageVoucherDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "commit" | "list">;
}

function json(status: number, data: unknown): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

export function validateVoucherInput(body: Record<string, unknown>): {
  code: string;
  type: "PERCENTAGE" | "FIXED";
  value: number;
  maxDiscount: number;
  minOrderTotal: number;
  validFrom: string;
  validTo: string;
  usageLimit: number;
  perUserLimit: number;
  isActive: boolean;
} {
  const rawCode = typeof body.code === "string" ? body.code.trim().toUpperCase() : "";
  if (!/^[A-Z0-9]{3,20}$/.test(rawCode)) {
    throw new HttpError(400, "INVALID_CODE", "Mã voucher phải từ 3 đến 20 ký tự chữ hoa hoặc số");
  }

  const type = typeof body.type === "string" ? body.type.trim().toUpperCase() : "";
  if (type !== "PERCENTAGE" && type !== "FIXED") {
    throw new HttpError(400, "INVALID_TYPE", "Loại voucher phải là PERCENTAGE hoặc FIXED");
  }

  const value = Number(body.value);
  if (isNaN(value) || value <= 0) {
    throw new HttpError(400, "INVALID_VALUE", "Giá trị voucher phải lớn hơn 0");
  }
  if (type === "PERCENTAGE" && value > 100) {
    throw new HttpError(400, "INVALID_PERCENTAGE", "Phần trăm giảm không được vượt quá 100%");
  }

  const maxDiscount = Number(body.maxDiscount ?? 0);
  const minOrderTotal = Number(body.minOrderTotal ?? 0);
  if (maxDiscount < 0 || minOrderTotal < 0) {
    throw new HttpError(400, "INVALID_AMOUNT", "Mức giảm tối đa và đơn tối thiểu không được âm");
  }

  const validFrom = typeof body.validFrom === "string" ? body.validFrom.trim() : "";
  const validTo = typeof body.validTo === "string" ? body.validTo.trim() : "";
  if (!validFrom || !validTo || new Date(validFrom) >= new Date(validTo)) {
    throw new HttpError(400, "INVALID_DATE_RANGE", "Thời gian hiệu lực không hợp lệ");
  }

  const usageLimit = Number(body.usageLimit);
  const perUserLimit = Number(body.perUserLimit);
  if (isNaN(usageLimit) || usageLimit <= 0 || isNaN(perUserLimit) || perUserLimit <= 0 || perUserLimit > usageLimit) {
    throw new HttpError(400, "INVALID_LIMIT", "Giới hạn sử dụng không hợp lệ");
  }

  const isActive = body.isActive !== false;

  return {
    code: rawCode,
    type,
    value,
    maxDiscount,
    minOrderTotal,
    validFrom,
    validTo,
    usageLimit,
    perUserLimit,
    isActive,
  };
}

export async function handleAdminManageVoucher(
  req: Request,
  deps: AdminManageVoucherDeps = { firestore: defaultFirestore },
): Promise<Response> {
  if (req.method !== "POST") {
    return json(405, { error: { code: "METHOD_NOT_ALLOWED", message: "Chỉ hỗ trợ POST" } });
  }

  try {
    const admin = await requireUser(req);
    requireRole(admin, "ADMIN");

    const body = await req.json().catch(() => ({})) as Record<string, unknown>;
    const action = typeof body.action === "string" ? body.action.trim().toUpperCase() : "CREATE";

    if (action === "CREATE") {
      const voucher = validateVoucherInput(body);

      const existing = await deps.firestore.get(`systemVouchers/${voucher.code}`);
      if (existing) {
        throw new HttpError(409, "VOUCHER_EXISTS", `Mã voucher hệ thống ${voucher.code} đã tồn tại`);
      }

      const nowIso = new Date().toISOString();
      const write = createWrite(`systemVouchers/${voucher.code}`, {
        code: voucher.code,
        type: voucher.type,
        value: voucher.value,
        maxDiscount: voucher.maxDiscount,
        minOrderTotal: voucher.minOrderTotal,
        validFrom: voucher.validFrom,
        validTo: voucher.validTo,
        usageLimit: voucher.usageLimit,
        perUserLimit: voucher.perUserLimit,
        usedCount: 0,
        isActive: voucher.isActive,
        createdAt: nowIso,
        updatedAt: nowIso,
      });

      try {
        await deps.firestore.commit([write]);
      } catch (e) {
        if (e instanceof FirestoreError && (e.code === "ALREADY_EXISTS" || e.code === "ABORTED")) {
          throw new HttpError(409, "VOUCHER_CONFLICT", "Xung đột dữ liệu khi tạo voucher hệ thống");
        }
        throw e;
      }

      return json(200, { success: true, code: voucher.code });
    }

    if (action === "TOGGLE_ACTIVE") {
      const code = typeof body.code === "string" ? body.code.trim().toUpperCase() : "";
      if (!code) throw new HttpError(400, "INVALID_CODE", "Mã voucher không hợp lệ");
      const isActive = body.isActive === true;

      const existing = await deps.firestore.get(`systemVouchers/${code}`);
      if (!existing) {
        throw new HttpError(404, "VOUCHER_NOT_FOUND", "Không tìm thấy voucher hệ thống");
      }

      const nowIso = new Date().toISOString();
      const write = updateWrite(`systemVouchers/${code}`, {
        isActive,
        updatedAt: nowIso,
      });

      await deps.firestore.commit([write]);
      return json(200, { success: true, code, isActive });
    }

    throw new HttpError(400, "INVALID_ACTION", `Hành động ${action} không hỗ trợ`);
  } catch (err) {
    return toErrorResponse(err);
  }
}
