// Edge Function `booking-payment-confirm` (spec 050 US2; contracts/edge-functions.md §1).
// Chốt đơn trả tại sân hoặc miễn phí (tổng 0đ), và đổi đơn đang chờ chuyển khoản sang trả tại sân.
// Đọc slot/nhóm, cơ sở, bảng giá, voucher rồi ghi đơn + slot + voucher + thông báo trong MỘT transaction;
// idempotent theo `requestId` (= bookingId, hoặc = recurringGroupId với đơn định kỳ).
import { errorResponse, HttpError, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import {
  createWrite,
  documentName,
  encodeFields,
  type FirestoreTransaction,
  updateWrite,
  type Write,
} from "../_shared/firestore.ts";
import { readJsonObject, runWithBackoff, UUID } from "../_shared/dropin-tx.ts";
import { type PaymentBreakdown, paymentBreakdown } from "../_shared/payment-calc.ts";
import {
  checkVoucher,
  commitWrites,
  type LoadedVoucher,
  loadRedemption,
  loadUserUsageCount,
  loadVoucher,
  normalizeVoucherCode,
  releaseWrites,
  reserveWrites,
  type VoucherRef,
  type VoucherScope,
} from "../_shared/payment-voucher.ts";
import { type ParsedSlot, parseSlotId, type PriceRule, slotPrice, toPriceRule } from "../_shared/slot-pricing.ts";

export interface ConfirmDeps {
  firestore: Pick<typeof defaultFirestore, "runTransaction">;
  /** Xác thực người gọi; mặc định `requireUser` (401 UNAUTHENTICATED, 403 ACCOUNT_LOCKED, ...). */
  authenticate: (req: Request) => Promise<{ uid: string }>;
  now: () => Date;
  sleep?: (ms: number) => Promise<void>;
}

/** Hóa đơn trả cho app (data-model §2 `PaymentReceipt`). */
export interface Receipt {
  bookingId: string;
  recurringGroupId: string | null;
  venueName: string;
  items: string[];
  option: string;
  status: string;
  subtotal: number;
  discount: number;
  total: number;
  paidAmount: number;
  remainingAmount: number;
  voucherCode: string | null;
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

type Doc = Record<string, unknown>;

const MAX_SLOTS = 48;
const APPROVAL_WINDOW_MS = 60 * 60_000;

const invalid = (message: string) => new HttpError(400, "INVALID_ARGUMENT", message);
const holdExpired = () => new HttpError(409, "HOLD_EXPIRED", "Đã hết thời gian giữ chỗ");
const forbidden = () => new HttpError(403, "FORBIDDEN", "Đơn này không thuộc về bạn");

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

// ---------- Đọc chung ----------

const asDate = (
  v: unknown,
): Date | null => (v instanceof Date && !Number.isNaN(v.getTime()) ? v : null);
const asNumber = (
  v: unknown,
): number => (typeof v === "number" && Number.isFinite(v) ? v : 0);
const asString = (
  v: unknown,
): string | null => (typeof v === "string" && v ? v : null);

interface PricedSlot extends ParsedSlot {
  courtName: string;
  price: number;
}

interface OrderContext {
  venue: Doc;
  ownerId: string;
  venueSnapshot: Doc;
  slots: PricedSlot[];
}

/**
 * Cơ sở còn hoạt động, mọi slot đang `HELD` cho đúng người và đúng đơn (`holderKey`), chưa hết hạn; kèm giá
 * tính lại từ bảng giá và tên sân.
 */
async function loadOrder(
  tx: FirestoreTransaction,
  uid: string,
  venueId: string,
  holderKey: string,
  slotIds: string[],
  now: Date,
): Promise<OrderContext> {
  const venue = await tx.get(`venues/${venueId}`);
  if (!venue) {
    throw new HttpError(404, "VENUE_NOT_FOUND", "Không tìm thấy cơ sở");
  }
  if (venue.status != null && venue.status !== "ACTIVE") {
    throw new HttpError(409, "VENUE_INACTIVE", "Cơ sở tạm ngừng hoạt động");
  }
  const parsed = slotIds.map((id) => {
    const p = parseSlotId(id);
    if (!p) throw holdExpired();
    return p;
  });
  const slotDocs = await Promise.all(
    parsed.map((p) => tx.get(`slots/${p.slotId}`)),
  );
  slotDocs.forEach((s) => {
    const expiresAt = asDate(s?.holdExpiresAt);
    const bookingId = asString(s?.bookingId);
    const ok = s != null && s.status === "HELD" && s.holdBy === uid &&
      (s.venueId == null || s.venueId === venueId) &&
      (bookingId == null || bookingId === holderKey) && expiresAt != null &&
      expiresAt.getTime() > now.getTime();
    if (!ok) throw holdExpired();
  });

  const rules = (await tx.query("priceRules", [], { parent: `venues/${venueId}` }))
    .map((d) => toPriceRule(d.data))
    .filter((r): r is PriceRule => r != null);
  const courtIds = [...new Set(parsed.map((p) => p.courtId))];
  const courts = await Promise.all(
    courtIds.map((c) => tx.get(`venues/${venueId}/courts/${c}`)),
  );
  const courtNames = new Map(courtIds.map((c, i) => {
    const doc = courts[i];
    const name = asString(doc?.name) ?? `Sân ${c}`;
    const surface = asString(doc?.surfaceType);
    return [c, surface ? `${name} · ${surface}` : name];
  }));

  const slots = parsed
    .map((p) => {
      const price = slotPrice(rules, p);
      if (price == null) {
        throw new HttpError(
          409,
          "NO_PRICE",
          "Khung giờ không có trong bảng giá của cơ sở",
        );
      }
      return { ...p, courtName: courtNames.get(p.courtId)!, price };
    })
    .sort((a, b) =>
      a.startAt.getTime() - b.startAt.getTime() ||
      a.courtId.localeCompare(b.courtId)
    );

  return {
    venue,
    ownerId: asString(venue.ownerId) ?? "",
    venueSnapshot: {
      name: asString(venue.name) ?? `Cơ sở ${venueId}`,
      address: asString(venue.address) ?? "",
      phone: asString(venue.phone),
      photoUrl: asString(venue.photoUrl) ??
        asString((venue.photoUrls as unknown[] | undefined)?.[0]),
    },
    slots,
  };
}

interface VoucherOutcome {
  voucher: LoadedVoucher | null;
  writes: Write[];
}

/**
 * Kiểm voucher cho đơn có khóa `key`. Lượt đang giữ tạm cho đơn này (`RESERVED`, khi đổi từ chuyển khoản) được
 * chốt thành `USED` mà không đếm lại; mã khác mã đang giữ thì nhả lượt cũ. Mã mới thì kiểm đủ điều kiện và giữ
 * luôn ở `USED` (đơn chốt ngay).
 */
async function evaluateVoucher(
  tx: FirestoreTransaction,
  uid: string,
  venueId: string,
  key: string,
  code: string | null,
  held: VoucherRef | null,
  subtotal: number,
  now: Date,
): Promise<VoucherOutcome> {
  const writes: Write[] = [];
  if (held && held.code !== code) {
    writes.push(
      ...releaseWrites(held, key, await loadRedemption(tx, held, key)),
    );
  }
  if (!code) return { voucher: null, writes };

  const voucher = await loadVoucher(tx, venueId, code);
  const redemption = voucher ? await loadRedemption(tx, voucher, key) : null;
  if (voucher && redemption?.userId === uid) {
    writes.push(...commitWrites(voucher, key, redemption));
    return { voucher, writes };
  }
  const usage = voucher ? await loadUserUsageCount(tx, voucher, uid) : 0;
  const check = checkVoucher(voucher, usage, subtotal, now);
  if (!check.ok) {
    const extra: Doc = { reason: check.reason };
    if (check.reason === "MIN_ORDER") extra.minOrderTotal = check.minOrderTotal;
    throw new HttpError(
      422,
      "VOUCHER_INVALID",
      "Mã khuyến mãi không dùng được",
      extra,
    );
  }
  writes.push(...reserveWrites(voucher!, uid, key, "USED"));
  return { voucher, writes };
}

function priceBreakdown(
  subtotal: number,
  voucher: LoadedVoucher | null,
  expectedTotal: number,
): PaymentBreakdown {
  const breakdown = paymentBreakdown(subtotal, voucher);
  if (breakdown.total !== expectedTotal) {
    throw new HttpError(409, "PRICE_CHANGED", "Giá đơn đã thay đổi", {
      breakdown,
    });
  }
  return breakdown;
}

function heldVoucher(doc: Doc | null, venueId: string): VoucherRef | null {
  const code = asString(doc?.voucherCode);
  const scope = doc?.voucherScope === "VENUE" || doc?.voucherScope === "SYSTEM"
    ? doc.voucherScope as VoucherScope
    : null;
  return code && scope ? { scope, venueId, code } : null;
}

function itemsOf(slots: PricedSlot[]): Doc[] {
  return slots.map((s) => ({
    slotId: s.slotId,
    courtId: s.courtId,
    courtName: s.courtName,
    startAt: s.startAt,
    endAt: s.endAt,
    price: s.price,
  }));
}

function paymentFields(
  subtotal: number,
  discount: number,
  total: number,
  voucher: LoadedVoucher | null,
): Doc {
  return {
    subtotal,
    discount,
    total,
    voucherCode: voucher?.code ?? null,
    voucherScope: voucher?.scope ?? null,
    paymentMethod: "AT_VENUE",
    paymentStatus: total === 0 ? "PAID" : "UNPAID",
    amountDue: 0,
    depositAmount: 0,
    paidAmount: 0,
    remainingAmount: total,
  };
}

function approvalDeadline(now: Date, startAt: Date): Date {
  return new Date(
    Math.min(now.getTime() + APPROVAL_WINDOW_MS, startAt.getTime()),
  );
}

function notificationWrite(
  ownerId: string,
  key: string,
  venueName: string,
  recurring: boolean,
  now: Date,
): Write[] {
  if (!ownerId) return [];
  return [createWrite(`users/${ownerId}/notifications/booking_new_${key}`, {
    type: "BOOKING_NEW",
    title: "Có đơn đặt sân mới",
    body: recurring
      ? `Đơn đặt cố định mới tại ${venueName}, cần duyệt trong 60 phút`
      : `Đơn đặt sân mới tại ${venueName}, cần duyệt trong 60 phút`,
    data: recurring ? { recurringGroupId: key } : { bookingId: key },
    isRead: false,
    createdAt: now,
  })];
}

function deactivateCode(doc: Doc | null): Write[] {
  const code = asString(doc?.paymentCode);
  return code ? [updateWrite(`paymentCodes/${code}`, { active: false })] : [];
}

// ---------- Hóa đơn ----------

const VN_OFFSET_MS = 7 * 60 * 60_000;
const pad = (n: number) => String(n).padStart(2, "0");

function vnParts(d: Date) {
  const t = new Date(d.getTime() + VN_OFFSET_MS);
  return {
    date: `${pad(t.getUTCDate())}/${pad(t.getUTCMonth() + 1)}/${t.getUTCFullYear()}`,
    time: `${pad(t.getUTCHours())}:${pad(t.getUTCMinutes())}`,
  };
}

/** Gộp các slot liền nhau cùng sân thành một dòng: "Sân 1 · Thảm PVC · 20/11/2026 18:00–19:00". */
export function receiptLines(items: Doc[]): string[] {
  const sorted = items
    .map((i) => ({
      name: String(i.courtName ?? "Sân"),
      start: asDate(i.startAt),
      end: asDate(i.endAt),
    }))
    .filter((i): i is { name: string; start: Date; end: Date } => i.start != null && i.end != null)
    .sort((a, b) => a.name.localeCompare(b.name) || a.start.getTime() - b.start.getTime());
  const groups: { name: string; start: Date; end: Date }[] = [];
  for (const i of sorted) {
    const last = groups[groups.length - 1];
    if (
      last && last.name === i.name && last.end.getTime() === i.start.getTime()
    ) last.end = i.end;
    else groups.push({ ...i });
  }
  return groups
    .sort((a, b) => a.start.getTime() - b.start.getTime())
    .map((g) => `${g.name} · ${vnParts(g.start).date} ${vnParts(g.start).time}–${vnParts(g.end).time}`);
}

function receiptOf(
  id: string,
  groupId: string | null,
  payment: Doc,
  venueName: string,
  items: Doc[],
): Receipt {
  return {
    bookingId: id,
    recurringGroupId: groupId,
    venueName,
    items: receiptLines(items),
    option: String(payment.paymentMethod ?? "AT_VENUE"),
    status: String(payment.paymentStatus ?? "UNPAID"),
    subtotal: asNumber(payment.subtotal),
    discount: asNumber(payment.discount),
    total: asNumber(payment.total),
    paidAmount: asNumber(payment.paidAmount),
    remainingAmount: asNumber(payment.remainingAmount),
    voucherCode: asString(payment.voucherCode),
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
