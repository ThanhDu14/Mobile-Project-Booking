// Edge Function `booking-payment-transfer` (spec 050 US4; contracts/edge-functions.md §2).
// Bắt đầu chuyển khoản VietQR cho chủ sân: tính lại tiền, giữ tạm voucher (`RESERVED`), cấp mã thanh toán
// `SNP…`, gia hạn giữ chỗ 15 phút ở đơn/nhóm và mọi slot, trong MỘT transaction. Idempotent theo `requestId`:
// đơn đang chờ chuyển khoản thì trả lại đúng mã và hạn cũ, không gia hạn thêm.
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
import { amountDue, type PaymentBreakdown } from "../_shared/payment-calc.ts";
import { normalizeVoucherCode } from "../_shared/payment-voucher.ts";
import { generatePaymentCode, vietQrUrl } from "../_shared/sepay.ts";
import { parseSlotId } from "../_shared/slot-pricing.ts";
import {
  asDate,
  asString,
  type Doc,
  evaluateVoucher,
  forbidden,
  heldVoucher,
  holdExpired,
  itemsOf,
  loadOrder,
  type OrderContext,
  priceBreakdown,
} from "../_shared/payment-order.ts";

export interface TransferDeps {
  firestore: Pick<typeof defaultFirestore, "runTransaction">;
  authenticate: (req: Request) => Promise<{ uid: string }>;
  now: () => Date;
  sleep?: (ms: number) => Promise<void>;
  /** Sinh mã thanh toán; test thay bằng dãy cố định. */
  newCode?: () => string;
}

type TransferOption = "TRANSFER_DEPOSIT" | "TRANSFER_FULL";

interface Input {
  requestId: string;
  recurringGroupId: string | null;
  venueId: string;
  slotIds: string[];
  voucherCode: string | null;
  option: TransferOption;
  expectedTotal: number;
}

interface PayoutAccount {
  bankBin: string;
  bankShortName: string;
  accountNumber: string;
  accountHolderName: string;
}

export interface TransferResult {
  bookingId: string;
  recurringGroupId: string | null;
  paymentCode: string;
  amountDue: number;
  holdExpiresAt: string;
  qrUrl: string;
  payoutAccount: { bankShortName: string; accountNumber: string; accountHolderName: string };
  sandbox: true;
}

const TRANSFER_HOLD_MS = 15 * 60_000;
const MAX_SLOTS = 48;
const MAX_CODE_ATTEMPTS = 3;

const invalid = (message: string) => new HttpError(400, "INVALID_ARGUMENT", message);
const invalidState = (message: string) => new HttpError(409, "INVALID_STATE", message);

function parseInput(body: Doc): Input {
  if (typeof body.requestId !== "string" || !UUID.test(body.requestId)) throw invalid("requestId phải là UUID");
  const recurringGroupId = body.recurringGroupId == null ? null : body.recurringGroupId;
  if (recurringGroupId !== null && recurringGroupId !== body.requestId) {
    throw invalid("Đơn định kỳ: requestId phải bằng recurringGroupId");
  }
  const venueId = typeof body.venueId === "string" ? body.venueId.trim() : "";
  if (!venueId || venueId.includes("/")) throw invalid("Thiếu hoặc sai venueId");
  if (body.option !== "TRANSFER_DEPOSIT" && body.option !== "TRANSFER_FULL") {
    throw invalid("option phải là TRANSFER_DEPOSIT hoặc TRANSFER_FULL");
  }
  const rawSlots = Array.isArray(body.slotIds) ? body.slotIds : [];
  const slotIds = [...new Set(rawSlots.filter((s): s is string => typeof s === "string"))];
  if (recurringGroupId === null) {
    if (slotIds.length === 0 || slotIds.length > MAX_SLOTS || slotIds.length !== rawSlots.length) {
      throw invalid(`slotIds phải có từ 1 đến ${MAX_SLOTS} slot, không trùng`);
    }
    if (slotIds.some((s) => s.includes("/") || !parseSlotId(s))) throw invalid("slotId sai dạng");
  }
  const voucherCode = typeof body.voucherCode === "string" && body.voucherCode.trim()
    ? normalizeVoucherCode(body.voucherCode)
    : null;
  const expectedTotal = body.expectedTotal;
  if (typeof expectedTotal !== "number" || !Number.isInteger(expectedTotal) || expectedTotal < 0) {
    throw invalid("expectedTotal phải là số nguyên không âm");
  }
  return {
    requestId: body.requestId,
    recurringGroupId: recurringGroupId as string | null,
    venueId,
    slotIds,
    voucherCode,
    option: body.option,
    expectedTotal,
  };
}

/** Đủ 4 trường thì mới chuyển khoản được (FR-010); thiếu thì 422 `TRANSFER_NOT_SUPPORTED`. */
function payoutOf(venue: Doc): PayoutAccount {
  const p = venue.payoutAccount as Doc | null | undefined;
  const bankBin = asString(p?.bankBin), bankShortName = asString(p?.bankShortName);
  const accountNumber = asString(p?.accountNumber), accountHolderName = asString(p?.accountHolderName);
  if (!bankBin || !bankShortName || !accountNumber || !accountHolderName) {
    throw new HttpError(422, "TRANSFER_NOT_SUPPORTED", "Cơ sở chưa nhận chuyển khoản");
  }
  return { bankBin, bankShortName, accountNumber, accountHolderName };
}

function resultOf(
  id: string,
  groupId: string | null,
  code: string,
  due: number,
  expiresAt: Date,
  payout: PayoutAccount,
): TransferResult {
  return {
    bookingId: id,
    recurringGroupId: groupId,
    paymentCode: code,
    amountDue: due,
    holdExpiresAt: expiresAt.toISOString(),
    qrUrl: vietQrUrl(payout.bankBin, payout.accountNumber, due, code),
    payoutAccount: {
      bankShortName: payout.bankShortName,
      accountNumber: payout.accountNumber,
      accountHolderName: payout.accountHolderName,
    },
    sandbox: true,
  };
}

/** Đơn/nhóm đang chờ chuyển khoản: trả lại mã + hạn cũ (không gia hạn); hết hạn thì `HOLD_EXPIRED`. */
async function replay(
  tx: FirestoreTransaction,
  id: string,
  groupId: string | null,
  doc: Doc,
  venueId: string,
  now: Date,
): Promise<TransferResult | null> {
  if (doc.paymentStatus !== "AWAITING_TRANSFER") return null;
  const code = asString(doc.paymentCode);
  const expiresAt = asDate(doc.holdExpiresAt);
  if (!code || !expiresAt) return null;
  if (expiresAt.getTime() <= now.getTime()) throw holdExpired();
  const venue = await tx.get(`venues/${venueId}`);
  return resultOf(id, groupId, code, Number(doc.amountDue ?? 0), expiresAt, payoutOf(venue ?? {}));
}

/** Mã chưa dùng; trùng thì sinh lại, tối đa 3 lần. Tạo bằng precondition `exists = false` để chặn đua. */
async function freshCode(tx: FirestoreTransaction, newCode: () => string): Promise<string> {
  for (let i = 0; i < MAX_CODE_ATTEMPTS; i++) {
    const code = newCode();
    if (!(await tx.get(`paymentCodes/${code}`))) return code;
  }
  throw new Error("Không sinh được mã thanh toán chưa dùng sau 3 lần");
}

interface Priced {
  order: OrderContext;
  subtotal: number;
  breakdown: PaymentBreakdown;
  voucherWrites: Write[];
  voucherFields: Doc;
  payout: PayoutAccount;
  due: number;
}

async function price(
  tx: FirestoreTransaction,
  uid: string,
  input: Input,
  venueId: string,
  key: string,
  slotIds: string[],
  held: Doc | null,
  now: Date,
): Promise<Priced> {
  const order = await loadOrder(tx, uid, venueId, key, slotIds, now);
  const payout = payoutOf(order.venue);
  const subtotal = order.slots.reduce((sum, s) => sum + s.price, 0);
  const voucher = await evaluateVoucher(
    tx,
    uid,
    venueId,
    key,
    input.voucherCode,
    heldVoucher(held, venueId),
    subtotal,
    now,
    "RESERVED",
  );
  const breakdown = priceBreakdown(subtotal, voucher.voucher, input.expectedTotal);
  if (breakdown.isFree) invalidStateThrow();
  return {
    order,
    subtotal,
    breakdown,
    voucherWrites: voucher.writes,
    voucherFields: { voucherCode: voucher.voucher?.code ?? null, voucherScope: voucher.voucher?.scope ?? null },
    payout,
    due: amountDue(breakdown, input.option),
  };
}

function invalidStateThrow(): never {
  throw invalidState("Đơn 0đ không cần chuyển khoản, hãy xác nhận trực tiếp");
}

function paymentFieldsOf(p: Priced, option: TransferOption, code: string, expiresAt: Date): Doc {
  return {
    subtotal: p.subtotal,
    discount: p.breakdown.discount,
    total: p.breakdown.total,
    ...p.voucherFields,
    paymentMethod: option,
    paymentStatus: "AWAITING_TRANSFER",
    amountDue: p.due,
    depositAmount: option === "TRANSFER_DEPOSIT" ? p.due : 0,
    paidAmount: 0,
    remainingAmount: p.breakdown.total,
    paymentCode: code,
    holdExpiresAt: expiresAt,
  };
}

function codeWrite(code: string, input: Input, uid: string, venueId: string, due: number): Write {
  return createWrite(`paymentCodes/${code}`, {
    code,
    bookingId: input.recurringGroupId ? null : input.requestId,
    recurringGroupId: input.recurringGroupId,
    userId: uid,
    venueId,
    amount: due,
    active: true,
  }, ["createdAt"]);
}

async function startSingle(
  tx: FirestoreTransaction,
  uid: string,
  input: Input,
  now: Date,
  newCode: () => string,
): Promise<{ result: TransferResult; writes: Write[] }> {
  const id = input.requestId;
  const path = `bookings/${id}`;
  const existing = await tx.get(path);
  if (existing) {
    if (existing.userId !== uid) throw forbidden();
    if (existing.status === "EXPIRED") throw holdExpired();
    if (existing.status !== "HOLD") throw invalidState("Đơn đã được chốt");
    const again = await replay(tx, id, null, existing, asString(existing.venueId) ?? input.venueId, now);
    if (again) return { result: again, writes: [] };
  }
  const venueId = asString(existing?.venueId) ?? input.venueId;
  const p = await price(tx, uid, input, venueId, id, input.slotIds, existing, now);
  const code = await freshCode(tx, newCode);
  const expiresAt = new Date(now.getTime() + TRANSFER_HOLD_MS);
  const slots = p.order.slots;
  const doc: Doc = {
    userId: uid,
    venueId,
    venueSnapshot: p.order.venueSnapshot,
    items: itemsOf(slots),
    startAt: slots[0].startAt,
    endAt: new Date(Math.max(...slots.map((s) => s.endAt.getTime()))),
    status: "HOLD",
    ...paymentFieldsOf(p, input.option, code, expiresAt),
    recurringGroupId: null,
    requestId: id,
    qrToken: null,
    createdAt: asDate(existing?.createdAt) ?? now,
    updatedAt: now,
  };
  return {
    result: resultOf(id, null, code, p.due, expiresAt, p.payout),
    writes: [
      {
        update: { name: documentName(path), fields: encodeFields(doc) },
        currentDocument: { exists: existing != null },
      },
      ...slots.map((s) => updateWrite(`slots/${s.slotId}`, { holdExpiresAt: expiresAt, bookingId: id })),
      codeWrite(code, input, uid, venueId, p.due),
      ...p.voucherWrites,
    ],
  };
}

async function startRecurring(
  tx: FirestoreTransaction,
  uid: string,
  input: Input,
  now: Date,
  newCode: () => string,
): Promise<{ result: TransferResult; writes: Write[] }> {
  const id = input.requestId;
  const groupPath = `recurringGroups/${id}`;
  const group = await tx.get(groupPath);
  if (!group) throw new HttpError(404, "NOT_FOUND", "Không tìm thấy nhóm đặt cố định");
  if (group.userId !== uid) throw forbidden();
  if (group.status === "EXPIRED") throw holdExpired();
  if (group.status !== "HOLD") throw invalidState("Nhóm đặt cố định đã được chốt");
  const venueId = asString(group.venueId) ?? input.venueId;
  if (venueId !== input.venueId) throw invalid("venueId không khớp nhóm đặt cố định");
  const again = await replay(tx, id, id, group, venueId, now);
  if (again) return { result: again, writes: [] };
  const expiry = asDate(group.holdExpiresAt);
  if (!expiry || expiry.getTime() <= now.getTime()) throw holdExpired();

  const sessions = (await tx.query("bookings", [{ field: "recurringGroupId", op: "EQUAL", value: id }]))
    .filter((d) => d.data.userId === uid && d.data.status === "HOLD");
  if (sessions.length === 0) throw invalidState("Nhóm đặt cố định không có buổi nào");
  const slotIds = sessions.flatMap((d) => ((d.data.slotIds as unknown[] | undefined) ?? []).map(String));
  const p = await price(tx, uid, input, venueId, id, slotIds, group, now);
  const code = await freshCode(tx, newCode);
  const expiresAt = new Date(now.getTime() + TRANSFER_HOLD_MS);
  return {
    result: resultOf(id, id, code, p.due, expiresAt, p.payout),
    writes: [
      updateWrite(groupPath, { ...paymentFieldsOf(p, input.option, code, expiresAt), updatedAt: now }),
      ...sessions.map((d) =>
        updateWrite(`bookings/${d.id}`, {
          paymentStatus: "AWAITING_TRANSFER",
          paymentMethod: input.option,
          paymentCode: code,
          holdExpiresAt: expiresAt,
          updatedAt: now,
        })
      ),
      ...p.order.slots.map((s) => updateWrite(`slots/${s.slotId}`, { holdExpiresAt: expiresAt })),
      codeWrite(code, input, uid, venueId, p.due),
      ...p.voucherWrites,
    ],
  };
}

export async function handleBookingPaymentTransfer(
  req: Request,
  deps: TransferDeps = defaultDeps(),
): Promise<Response> {
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
  try {
    const { uid } = await deps.authenticate(req);
    const input = parseInput(await readJsonObject(req));
    const now = deps.now();
    const newCode = deps.newCode ?? (() => generatePaymentCode());
    const result = await runWithBackoff(
      deps,
      (tx) =>
        input.recurringGroupId
          ? startRecurring(tx, uid, input, now, newCode)
          : startSingle(tx, uid, input, now, newCode),
    );
    return Response.json(result);
  } catch (e) {
    return toErrorResponse(e);
  }
}

export function defaultDeps(): TransferDeps {
  return { firestore: defaultFirestore, authenticate: (req) => requireUser(req), now: () => new Date() };
}
