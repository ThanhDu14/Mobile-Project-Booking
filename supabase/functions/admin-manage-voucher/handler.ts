import { requireCurrentAdmin } from "../_shared/admin-authorization.ts";
import { HttpError, requireRole, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { createWrite, updateWrite, FirestoreError } from "../_shared/firestore.ts";
import { createUniqueVoucher } from "../_shared/voucher-uniqueness.ts";

export interface AdminManageVoucherDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "commit" | "list" | "query" | "runTransaction">;
}

function json(status: number, data: unknown): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

export function validateVoucherInput(body: Record<string, unknown>): {
  code: string;
  type: "PERCENT" | "FIXED";
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

  const type = typeof body.type === "string" ? body.type.trim().toUpperCase().replace(/^PERCENTAGE$/, "PERCENT") : "";
  if (type !== "PERCENT" && type !== "FIXED") {
    throw new HttpError(400, "INVALID_TYPE", "Loại voucher phải là PERCENTAGE hoặc FIXED");
  }

  const value = body.value as number;
  if (!Number.isSafeInteger(value) || value <= 0) {
    throw new HttpError(400, "INVALID_VALUE", "Giá trị voucher phải lớn hơn 0");
  }
  if (type === "PERCENT" && value > 100) {
    throw new HttpError(400, "INVALID_PERCENTAGE", "Phần trăm giảm không được vượt quá 100%");
  }

  const maxDiscount = body.maxDiscount === undefined ? 0 : body.maxDiscount as number;
  const minOrderTotal = body.minOrderTotal === undefined ? 0 : body.minOrderTotal as number;
  if (!Number.isSafeInteger(maxDiscount) || !Number.isSafeInteger(minOrderTotal) || maxDiscount < 0 || minOrderTotal < 0) {
    throw new HttpError(400, "INVALID_AMOUNT", "Mức giảm tối đa và đơn tối thiểu không được âm");
  }

  const validFrom = typeof body.validFrom === "string" ? body.validFrom.trim() : "";
  const validTo = typeof body.validTo === "string" ? body.validTo.trim() : "";
  if (!isValidVoucherDate(validFrom) || !isValidVoucherDate(validTo) || Date.parse(validFrom) >= Date.parse(validTo)) {
    throw new HttpError(400, "INVALID_DATE_RANGE", "Thời gian hiệu lực không hợp lệ");
  }

  const usageLimit = body.usageLimit as number;
  const perUserLimit = body.perUserLimit as number;
  if (!Number.isSafeInteger(usageLimit) || usageLimit <= 0 || !Number.isSafeInteger(perUserLimit) || perUserLimit <= 0 || perUserLimit > usageLimit) {
    throw new HttpError(400, "INVALID_LIMIT", "Giới hạn sử dụng không hợp lệ");
  }

  if (body.isActive !== undefined && typeof body.isActive !== "boolean") throw new HttpError(400, "INVALID_ACTIVE", "Trạng thái voucher không hợp lệ");
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
    requireCurrentAdmin(admin);

    const body = await req.json().catch(() => ({})) as Record<string, unknown>;
    const action = typeof body.action === "string" ? body.action.trim().toUpperCase() : "CREATE";

    if (action === "CREATE") {
      const voucher = validateVoucherInput(body);

      const existing = await deps.firestore.get(`systemVouchers/${voucher.code}`);
      if (existing) {
        throw new HttpError(409, "VOUCHER_EXISTS", `Mã voucher hệ thống ${voucher.code} đã tồn tại`);
      }
      const venueDuplicates = await deps.firestore.query("vouchers", [{ field: "code", op: "EQUAL", value: voucher.code }], { allDescendants: true, limit: 1 });
      if (venueDuplicates.length) throw new HttpError(409, "VOUCHER_EXISTS", "Mã voucher đã tồn tại tại cơ sở");

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
        await createUniqueVoucher(deps.firestore.runTransaction, `systemVouchers/${voucher.code}`, voucher.code, defaultFirestore.decodeFields(write.update!.fields));
      } catch (e) {
        if (e instanceof FirestoreError && (e.code === "ALREADY_EXISTS" || e.code === "ABORTED")) {
          throw new HttpError(409, "VOUCHER_CONFLICT", "Xung đột dữ liệu khi tạo voucher hệ thống");
        }
        throw e;
      }

      return json(200, { success: true, code: voucher.code });
    }

    if (action === "UPDATE") {
      const input = validateVoucherInput(body);
      await deps.firestore.runTransaction(async tx => {
        const existing = await tx.get(`systemVouchers/${input.code}`);
        if (!existing) throw new HttpError(404,"VOUCHER_NOT_FOUND","Không tìm thấy voucher hệ thống");
        if (input.usageLimit < Number(existing.usedCount ?? 0)) throw new HttpError(400,"INVALID_LIMIT","Giới hạn không được thấp hơn số lượt đã dùng");
        tx.write(updateWrite(`systemVouchers/${input.code}`,{...input,updatedAt:new Date().toISOString()}));
      });
      return json(200,{success:true,code:input.code});
    }

    if (action === "TOGGLE_ACTIVE") {
      const code = typeof body.code === "string" ? body.code.trim().toUpperCase() : "";
      if (!code) throw new HttpError(400, "INVALID_CODE", "Mã voucher không hợp lệ");
      if (typeof body.isActive !== "boolean") throw new HttpError(400, "INVALID_ACTIVE", "Trạng thái voucher không hợp lệ");
      const isActive = body.isActive;

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

function isValidVoucherDate(value: string): boolean {
  const match = /^(\d{4})-(\d{2})-(\d{2})T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?(?:Z|[+-]\d{2}:\d{2})$/.exec(value);
  if (!match || !Number.isFinite(Date.parse(value))) return false;
  const year = Number(match[1]), month = Number(match[2]), day = Number(match[3]);
  return month >= 1 && month <= 12 && day >= 1 && day <= new Date(Date.UTC(year, month, 0)).getUTCDate();
}
