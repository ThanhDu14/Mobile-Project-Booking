// Edge Function `booking-payment-confirm` (spec 050 US2; contracts/edge-functions.md §1).
// Chốt đơn trả tại sân hoặc miễn phí (tổng 0đ), và đổi đơn đang chờ chuyển khoản sang trả tại sân.
// Đọc slot/nhóm, cơ sở, bảng giá, voucher rồi ghi đơn + slot + voucher + thông báo trong MỘT transaction;
// idempotent theo `requestId` (= bookingId, hoặc = recurringGroupId với đơn định kỳ).
import { errorResponse, HttpError, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import {
  documentName,
  encodeFields,
  type FirestoreTransaction,
  updateWrite,
  type Write,
} from "../_shared/firestore.ts";
import { readJsonObject, runWithBackoff, UUID } from "../_shared/dropin-tx.ts";
import { normalizeVoucherCode } from "../_shared/payment-voucher.ts";
import { parseSlotId } from "../_shared/slot-pricing.ts";
import {
  approvalDeadline,
  asDate,
  asString,
  deactivateCode,
  type Doc,
  evaluateVoucher,
  forbidden,
  heldVoucher,
  holdExpired,
  itemsOf,
  loadOrder,
  notificationWrite,
  paymentFields,
  priceBreakdown,
  type Receipt,
  receiptOf,
} from "../_shared/payment-order.ts";

export { receiptLines } from "../_shared/payment-order.ts";

export interface ConfirmDeps {
  firestore: Pick<typeof defaultFirestore, "runTransaction">;
  /** Xác thực người gọi; mặc định `requireUser` (401 UNAUTHENTICATED, 403 ACCOUNT_LOCKED, ...). */
  authenticate: (req: Request) => Promise<{ uid: string }>;
  now: () => Date;
  sleep?: (ms: number) => Promise<void>;
}

interface ConfirmResult {
  bookingId: string;
  recurringGroupId: string | null;
  status: string;
  paymentStatus: string;
  receipt: Receipt;
}

interface Input {
  requestId: string;
  recurringGroupId: string | null;
  venueId: string;
  slotIds: string[];
  voucherCode: string | null;
  expectedTotal: number;
}

const MAX_SLOTS = 48;

const invalid = (message: string) => new HttpError(400, "INVALID_ARGUMENT", message);

function parseInput(body: Doc): Input {
  if (typeof body.requestId !== "string" || !UUID.test(body.requestId)) {
    throw invalid("requestId phải là UUID");
  }
  const recurringGroupId = body.recurringGroupId == null ? null : body.recurringGroupId;
  if (recurringGroupId !== null) {
    if (
      typeof recurringGroupId !== "string" ||
      recurringGroupId !== body.requestId
    ) {
      throw invalid("Đơn định kỳ: requestId phải bằng recurringGroupId");
    }
  }
  const venueId = typeof body.venueId === "string" ? body.venueId.trim() : "";
  if (!venueId || venueId.includes("/")) {
    throw invalid("Thiếu hoặc sai venueId");
  }
  const rawSlots = Array.isArray(body.slotIds) ? body.slotIds : [];
  const slotIds = [
    ...new Set(rawSlots.filter((s): s is string => typeof s === "string")),
  ];
  if (recurringGroupId === null) {
    if (
      slotIds.length === 0 || slotIds.length > MAX_SLOTS ||
      slotIds.length !== rawSlots.length
    ) {
      throw invalid(`slotIds phải có từ 1 đến ${MAX_SLOTS} slot, không trùng`);
    }
    if (slotIds.some((s) => s.includes("/") || !parseSlotId(s))) {
      throw invalid("slotId sai dạng");
    }
  }
  const voucherCode = typeof body.voucherCode === "string" && body.voucherCode.trim()
    ? normalizeVoucherCode(body.voucherCode)
    : null;
  const expectedTotal = body.expectedTotal;
  if (
    typeof expectedTotal !== "number" || !Number.isInteger(expectedTotal) ||
    expectedTotal < 0
  ) {
    throw invalid("expectedTotal phải là số nguyên không âm");
  }
  return {
    requestId: body.requestId,
    recurringGroupId,
    venueId,
    slotIds,
    voucherCode,
    expectedTotal,
  };
}

function bookingResult(id: string, doc: Doc): ConfirmResult {
  const venueName = asString((doc.venueSnapshot as Doc | undefined)?.name) ??
    "";
  return {
    bookingId: id,
    recurringGroupId: null,
    status: String(doc.status),
    paymentStatus: String(doc.paymentStatus),
    receipt: receiptOf(
      id,
      null,
      doc,
      venueName,
      (doc.items as Doc[] | undefined) ?? [],
    ),
  };
}

function groupResult(id: string, group: Doc, sessions: Doc[]): ConfirmResult {
  const venueName = asString((sessions[0]?.venueSnapshot as Doc | undefined)?.name) ??
    asString(group.venueName) ?? "";
  // Mỗi buổi một dòng (gộp slot liền nhau).
  const items = sessions.flatMap((s) => (s.items as Doc[] | undefined) ?? []);
  return {
    bookingId: id,
    recurringGroupId: id,
    status: "PENDING",
    paymentStatus: String(group.paymentStatus),
    receipt: receiptOf(id, id, group, venueName, items),
  };
}

// ---------- Đơn lẻ ----------

async function confirmSingle(
  tx: FirestoreTransaction,
  uid: string,
  input: Input,
  now: Date,
): Promise<{ result: ConfirmResult; writes: Write[] }> {
  const id = input.requestId;
  const path = `bookings/${id}`;
  const existing = await tx.get(path);
  if (existing) {
    if (existing.userId !== uid) throw forbidden();
    if (existing.status === "EXPIRED") throw holdExpired();
    if (existing.status !== "HOLD") {
      return { result: bookingResult(id, existing), writes: [] };
    }
  }
  const venueId = asString(existing?.venueId) ?? input.venueId;
  const slotIds = existing ? ((existing.items as Doc[] | undefined) ?? []).map((i) => String(i.slotId)) : input.slotIds;
  if (slotIds.length === 0) throw holdExpired();

  const order = await loadOrder(tx, uid, venueId, id, slotIds, now);
  const subtotal = order.slots.reduce((sum, s) => sum + s.price, 0);
  const voucher = await evaluateVoucher(
    tx,
    uid,
    venueId,
    id,
    input.voucherCode,
    heldVoucher(existing, venueId),
    subtotal,
    now,
  );
  const breakdown = priceBreakdown(
    subtotal,
    voucher.voucher,
    input.expectedTotal,
  );

  const items = itemsOf(order.slots);
  const startAt = order.slots[0].startAt;
  const endAt = new Date(
    Math.max(...order.slots.map((s) => s.endAt.getTime())),
  );
  const doc: Doc = {
    userId: uid,
    venueId,
    venueSnapshot: order.venueSnapshot,
    items,
    startAt,
    endAt,
    status: "PENDING",
    ...paymentFields(
      subtotal,
      breakdown.discount,
      breakdown.total,
      voucher.voucher,
    ),
    paymentCode: asString(existing?.paymentCode),
    recurringGroupId: null,
    requestId: id,
    qrToken: null,
    checkedInAt: null,
    holdExpiresAt: null,
    pendingAt: now,
    approvalDeadline: approvalDeadline(now, startAt),
    cancelReason: null,
    cancelledBy: null,
    cancelledAt: null,
    completedAt: null,
    createdAt: asDate(existing?.createdAt) ?? now,
    updatedAt: now,
  };

  return {
    result: bookingResult(id, doc),
    writes: [
      // Ghi cả document; precondition theo bản đã đọc để hai request đua nhau không cùng tạo đơn.
      {
        update: { name: documentName(path), fields: encodeFields(doc) },
        currentDocument: { exists: existing != null },
      },
      ...order.slots.map((s) =>
        updateWrite(`slots/${s.slotId}`, {
          status: "BOOKED",
          holdExpiresAt: null,
          bookingId: id,
        })
      ),
      ...voucher.writes,
      ...deactivateCode(existing),
      ...notificationWrite(
        order.ownerId,
        id,
        String(order.venueSnapshot.name),
        false,
        now,
      ),
    ],
  };
}

// ---------- Đơn định kỳ ----------

async function confirmRecurring(
  tx: FirestoreTransaction,
  uid: string,
  input: Input,
  now: Date,
): Promise<{ result: ConfirmResult; writes: Write[] }> {
  const id = input.requestId;
  const groupPath = `recurringGroups/${id}`;
  const group = await tx.get(groupPath);
  if (!group) {
    throw new HttpError(404, "NOT_FOUND", "Không tìm thấy nhóm đặt cố định");
  }
  if (group.userId !== uid) throw forbidden();
  const sessionDocs = (await tx.query("bookings", [{
    field: "recurringGroupId",
    op: "EQUAL",
    value: id,
  }]))
    .filter((d) => d.data.userId === uid)
    .sort((a, b) =>
      `${a.data.date}${a.data.startTime}`.localeCompare(
        `${b.data.date}${b.data.startTime}`,
      )
    );
  if (group.status === "DONE") {
    return {
      result: groupResult(id, group, sessionDocs.map((d) => d.data)),
      writes: [],
    };
  }
  if (group.status === "EXPIRED") throw holdExpired();
  if (group.status !== "HOLD") {
    throw new HttpError(
      409,
      "INVALID_STATE",
      "Nhóm đặt cố định không ở trạng thái giữ chỗ",
    );
  }
  const expiresAt = asDate(group.holdExpiresAt);
  if (!expiresAt || expiresAt.getTime() <= now.getTime()) throw holdExpired();
  if (sessionDocs.length === 0) {
    throw new HttpError(
      409,
      "INVALID_STATE",
      "Nhóm đặt cố định không có buổi nào",
    );
  }

  const venueId = asString(group.venueId) ?? input.venueId;
  if (venueId !== input.venueId) {
    throw invalid("venueId không khớp nhóm đặt cố định");
  }
  const sessionSlots = sessionDocs.map((d) => ((d.data.slotIds as unknown[] | undefined) ?? []).map(String));
  const order = await loadOrder(tx, uid, venueId, id, sessionSlots.flat(), now);
  const priced = new Map(order.slots.map((s) => [s.slotId, s]));
  const subtotal = order.slots.reduce((sum, s) => sum + s.price, 0);
  const voucher = await evaluateVoucher(
    tx,
    uid,
    venueId,
    id,
    input.voucherCode,
    heldVoucher(group, venueId),
    subtotal,
    now,
  );
  const breakdown = priceBreakdown(
    subtotal,
    voucher.voucher,
    input.expectedTotal,
  );

  // Chia giảm giá theo tỷ lệ tiền từng buổi; phần lẻ vào buổi cuối để tổng các buổi = tổng nhóm.
  let discountLeft = breakdown.discount;
  const sessions = sessionDocs.map((d, idx) => {
    const slots = sessionSlots[idx].map((s) => priced.get(s)!).sort((a, b) =>
      a.startAt.getTime() - b.startAt.getTime()
    );
    const price = slots.reduce((sum, s) => sum + s.price, 0);
    const discount = idx === sessionDocs.length - 1
      ? discountLeft
      : Math.floor(subtotal > 0 ? breakdown.discount * price / subtotal : 0);
    discountLeft -= discount;
    const startAt = slots[0].startAt;
    const patch: Doc = {
      venueSnapshot: order.venueSnapshot,
      items: itemsOf(slots),
      startAt,
      endAt: slots[slots.length - 1].endAt,
      status: "PENDING",
      ...paymentFields(price, discount, price - discount, voucher.voucher),
      // Trạng thái thanh toán theo nhóm (nhóm 0đ thì mọi buổi PAID).
      paymentStatus: breakdown.total === 0 ? "PAID" : "UNPAID",
      holdExpiresAt: null,
      pendingAt: now,
      approvalDeadline: approvalDeadline(now, startAt),
      updatedAt: now,
    };
    return { id: d.id, data: { ...d.data, ...patch }, patch };
  });

  const groupPatch: Doc = {
    status: "DONE",
    ...paymentFields(
      subtotal,
      breakdown.discount,
      breakdown.total,
      voucher.voucher,
    ),
    holdExpiresAt: null,
    pendingAt: now,
    updatedAt: now,
  };
  const venueName = String(order.venueSnapshot.name);
  return {
    result: groupResult(
      id,
      { ...group, ...groupPatch },
      sessions.map((s) => s.data),
    ),
    writes: [
      updateWrite(groupPath, groupPatch),
      ...sessions.map((s) => updateWrite(`bookings/${s.id}`, s.patch)),
      ...order.slots.map((s) =>
        updateWrite(`slots/${s.slotId}`, {
          status: "BOOKED",
          holdExpiresAt: null,
        })
      ),
      ...voucher.writes,
      ...deactivateCode(group),
      ...notificationWrite(order.ownerId, id, venueName, true, now),
    ],
  };
}

export async function handleBookingPaymentConfirm(
  req: Request,
  deps: ConfirmDeps = defaultDeps(),
): Promise<Response> {
  if (req.method !== "POST") {
    return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
  }
  try {
    const { uid } = await deps.authenticate(req);
    const input = parseInput(await readJsonObject(req));
    const now = deps.now();
    const result = await runWithBackoff(
      deps,
      (tx) => input.recurringGroupId ? confirmRecurring(tx, uid, input, now) : confirmSingle(tx, uid, input, now),
    );
    return Response.json(result);
  } catch (e) {
    return toErrorResponse(e);
  }
}

export function defaultDeps(): ConfirmDeps {
  return {
    firestore: defaultFirestore,
    authenticate: (req) => requireUser(req),
    now: () => new Date(),
  };
}
