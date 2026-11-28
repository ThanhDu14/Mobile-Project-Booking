import * as defaultFirestore from "../_shared/firestore.ts";
import { createWrite, type FirestoreTransaction, updateWrite, type Write } from "../_shared/firestore.ts";
import { approvalDeadline, asDate, asNumber, asString, type Doc } from "../_shared/payment-order.ts";
import { prepareBookingNew } from "../_shared/booking-notification.ts";
import { commitWrites, loadRedemption, type VoucherRef } from "../_shared/payment-voucher.ts";
import { refundCreateWrite, refundIdFor } from "../_shared/refunds.ts";
import { parseSepayTransaction, type SepayTransaction, verifySepaySignature } from "../_shared/sepay.ts";

/**
 * Webhook "Tiền vào" của SePay Test mode: chốt đơn đang chờ chuyển khoản theo mã `SNP…` (spec 050 US4, research R4).
 *
 * - Xác thực bằng HMAC-SHA256 (`X-SePay-Signature`, `X-SePay-Timestamp`); sai chữ ký → 401.
 * - Chống trùng theo `id` của SePay: `sepayTransactions/{id}` tạo trong cùng transaction với việc chốt đơn,
 *   nên SePay gửi lại (retry, Replay) không chốt hai lần.
 * - Giao dịch hợp lệ về chữ ký luôn trả 200 `{success: true, outcome}` để SePay ngừng gửi lại; tiền về muộn hoặc
 *   sai số tiền sinh khoản hoàn thủ công `refunds/refund_sepay_{id}`. Lỗi tạm thời trả 500 để SePay thử lại.
 */
export interface SepayWebhookDeps {
  firestore?: Pick<typeof defaultFirestore, "runTransaction">;
  secret?: string;
  nowSeconds?: () => number;
  now?: () => Date;
}

export type WebhookOutcome =
  | "CONFIRMED"
  | "DUPLICATE"
  | "NOT_INCOMING"
  | "NO_CODE"
  | "CODE_NOT_FOUND"
  | "LATE_TRANSFER"
  | "AMOUNT_MISMATCH";

function defaults(): Required<SepayWebhookDeps> {
  return {
    firestore: defaultFirestore,
    secret: Deno.env.get("SEPAY_WEBHOOK_SECRET") ?? "",
    nowSeconds: () => Math.floor(Date.now() / 1000),
    now: () => new Date(),
  };
}

function json(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

interface Target {
  /** `bookings/{id}` hoặc `recurringGroups/{id}`. */
  path: string;
  key: string;
  recurring: boolean;
  doc: Doc | null;
  /** Các buổi của nhóm (rỗng với đơn lẻ). */
  sessions: { id: string; data: Doc }[];
  slotIds: string[];
}

async function loadTarget(tx: FirestoreTransaction, code: Doc): Promise<Target | null> {
  const groupId = asString(code.recurringGroupId);
  const bookingId = asString(code.bookingId);
  if (groupId) {
    const doc = await tx.get(`recurringGroups/${groupId}`);
    const sessions = (await tx.query("bookings", [{ field: "recurringGroupId", op: "EQUAL", value: groupId }]))
      .map((d) => ({ id: d.id, data: d.data }))
      .sort((a, b) => (asDate(a.data.startAt)?.getTime() ?? 0) - (asDate(b.data.startAt)?.getTime() ?? 0) ||
        a.id.localeCompare(b.id));
    const slotIds = sessions.flatMap((s) => ((s.data.slotIds as unknown[] | undefined) ?? []).map(String));
    return { path: `recurringGroups/${groupId}`, key: groupId, recurring: true, doc, sessions, slotIds };
  }
  if (bookingId) {
    const doc = await tx.get(`bookings/${bookingId}`);
    const slotIds = ((doc?.items as Doc[] | undefined) ?? []).map((i) => String(i.slotId));
    return { path: `bookings/${bookingId}`, key: bookingId, recurring: false, doc, sessions: [], slotIds };
  }
  return null;
}

/** Đơn còn chờ đúng mã này và mọi slot vẫn đang giữ cho đơn (research R4 bước 5). */
async function stillWaiting(tx: FirestoreTransaction, t: Target, code: Doc, paymentCode: string): Promise<boolean> {
  if (code.active !== true || !t.doc) return false;
  if (t.doc.status !== "HOLD" || t.doc.paymentStatus !== "AWAITING_TRANSFER") return false;
  if (asString(t.doc.paymentCode) !== paymentCode || t.slotIds.length === 0) return false;
  const slots = await Promise.all(t.slotIds.map((id) => tx.get(`slots/${id}`)));
  return slots.every((s) => s?.status === "HELD" && s.bookingId === t.key);
}

function heldVoucherRef(doc: Doc, venueId: string): VoucherRef | null {
  const code = asString(doc.voucherCode);
  const scope = doc.voucherScope === "VENUE" || doc.voucherScope === "SYSTEM" ? doc.voucherScope : null;
  return code && scope ? { scope, venueId, code } : null;
}

async function confirmWrites(
  tx: FirestoreTransaction,
  t: Target,
  sepay: SepayTransaction,
  paymentCode: string,
  venueId: string,
  now: Date,
): Promise<Write[]> {
  const doc = t.doc!;
  const total = asNumber(doc.total);
  const paid = asNumber(doc.amountDue);
  const paymentStatus = paid >= total ? "PAID" : "DEPOSITED";
  const ref = heldVoucherRef(doc, venueId);
  const voucher = ref ? commitWrites(ref, t.key, await loadRedemption(tx, ref, t.key)) : [];
  const paidFields = { paymentStatus, paidAt: now, sepayTransactionId: sepay.id, holdExpiresAt: null, updatedAt: now };
  const first = t.recurring ? t.sessions[0] : { id: t.key, data: doc };
  const notifications = first ? await prepareBookingNew(tx, first.id, {
    venueId, status: "PENDING", approvalDeadline: approvalDeadline(now, asDate(first.data.startAt) ?? now),
  }, now, `booking_new_${t.key}`) : [];

  const docWrites: Write[] = [];
  if (t.recurring) {
    docWrites.push(updateWrite(t.path, {
      ...paidFields,
      status: "DONE",
      paidAmount: paid,
      remainingAmount: total - paid,
      pendingAt: now,
    }));
    // Chia số đã trả theo tỷ lệ tiền từng buổi, phần lẻ vào buổi cuối.
    let left = paid;
    t.sessions.forEach((s, i) => {
      const sTotal = asNumber(s.data.total);
      const sPaid = i === t.sessions.length - 1 ? left : Math.floor(total > 0 ? paid * sTotal / total : 0);
      left -= sPaid;
      const startAt = asDate(s.data.startAt) ?? now;
      docWrites.push(updateWrite(`bookings/${s.id}`, {
        ...paidFields,
        status: "PENDING",
        paidAmount: sPaid,
        depositAmount: paymentStatus === "DEPOSITED" ? sPaid : 0,
        remainingAmount: sTotal - sPaid,
        pendingAt: now,
        approvalDeadline: approvalDeadline(now, startAt),
      }));
    });
  } else {
    const startAt = asDate(doc.startAt) ?? now;
    docWrites.push(updateWrite(t.path, {
      ...paidFields,
      status: "PENDING",
      paidAmount: paid,
      remainingAmount: total - paid,
      pendingAt: now,
      approvalDeadline: approvalDeadline(now, startAt),
    }));
  }
  return [
    ...docWrites,
    ...t.slotIds.map((id) => updateWrite(`slots/${id}`, { status: "BOOKED", holdExpiresAt: null })),
    updateWrite(`paymentCodes/${paymentCode}`, { active: false }),
    ...voucher,
    ...notifications,
  ];
}

/**
 * Lõi xử lý một giao dịch "Tiền vào" trong transaction: chống trùng theo `sepayTransactions/{id}`, tìm đơn theo mã,
 * chốt đơn hoặc sinh khoản hoàn. Dùng chung cho webhook SePay và function giả lập `booking-payment-simulate`;
 * `ledgerExtra` ghi thêm vào sổ giao dịch (vd. `simulated: true`).
 */
export async function processSepayTransaction(
  tx: FirestoreTransaction,
  sepay: SepayTransaction,
  now: Date,
  ledgerExtra: Doc = {},
): Promise<{ result: WebhookOutcome; writes: Write[] }> {
  const ledgerPath = `sepayTransactions/${sepay.id}`;
  if (await tx.get(ledgerPath)) return { result: "DUPLICATE", writes: [] };

  const ledger = (outcome: WebhookOutcome, extra: Doc = {}): Write =>
    createWrite(ledgerPath, {
      sepayId: sepay.id,
      transferType: sepay.transferType,
      transferAmount: sepay.transferAmount,
      content: sepay.content,
      referenceCode: sepay.referenceCode,
      transactionDate: sepay.transactionDate,
      paymentCode: sepay.code,
      bookingId: null,
      recurringGroupId: null,
      refundId: null,
      outcome,
      ...ledgerExtra,
      ...extra,
    }, ["receivedAt"]);
  const only = (outcome: WebhookOutcome) => ({ result: outcome, writes: [ledger(outcome)] });

  if (sepay.transferType !== "in") return only("NOT_INCOMING");
  if (!sepay.code) return only("NO_CODE");
  const code = await tx.get(`paymentCodes/${sepay.code}`);
  if (!code) return only("CODE_NOT_FOUND");

  const target = await loadTarget(tx, code);
  const venueId = asString(code.venueId) ?? asString(target?.doc?.venueId) ?? "";
  const userId = asString(code.userId) ?? asString(target?.doc?.userId) ?? "";
  const links = {
    bookingId: target && !target.recurring ? target.key : null,
    recurringGroupId: target?.recurring ? target.key : null,
  };
  const refund = (reason: "LATE_TRANSFER" | "AMOUNT_MISMATCH") => {
    const refundId = refundIdFor({ sepayTransactionId: sepay.id });
    return {
      result: reason,
      writes: [
        ledger(reason, { ...links, refundId }),
        refundCreateWrite({
          id: refundId,
          ...links,
          venueId,
          userId,
          amount: sepay.transferAmount,
          penaltyAmount: 0,
          reason,
          sepayTransactionId: sepay.id,
        }),
      ],
    };
  };

  if (!target || !(await stillWaiting(tx, target, code, sepay.code))) return refund("LATE_TRANSFER");
  if (sepay.transferAmount !== asNumber(target.doc!.amountDue)) return refund("AMOUNT_MISMATCH");
  return {
    result: "CONFIRMED",
    writes: [
      ledger("CONFIRMED", links),
      ...await confirmWrites(tx, target, sepay, sepay.code, venueId, now),
    ],
  };
}

export async function handleSepayWebhook(req: Request, injected: SepayWebhookDeps = {}): Promise<Response> {
  const deps = { ...defaults(), ...injected };
  if (req.method !== "POST") return json(405, { success: false, error: "METHOD_NOT_ALLOWED" });
  if (!deps.secret) {
    console.error("[sepay-webhook] SEPAY_WEBHOOK_SECRET chưa được đặt");
    return json(500, { success: false, error: "NOT_CONFIGURED" });
  }

  const rawBody = await req.text();
  const check = await verifySepaySignature(
    deps.secret,
    rawBody,
    req.headers.get("x-sepay-signature"),
    req.headers.get("x-sepay-timestamp"),
    deps.nowSeconds(),
  );
  if (check !== "OK") {
    console.warn(`[sepay-webhook] từ chối chữ ký: ${check}`);
    return json(401, { success: false, error: `SIGNATURE_${check}` });
  }

  let body: unknown;
  try {
    body = JSON.parse(rawBody);
  } catch {
    return json(400, { success: false, error: "INVALID_JSON" });
  }
  const sepay = parseSepayTransaction(body);
  if (!sepay) return json(400, { success: false, error: "INVALID_PAYLOAD" });

  try {
    const now = deps.now();
    const outcome = await deps.firestore.runTransaction<WebhookOutcome>((tx) => processSepayTransaction(tx, sepay, now));
    console.log(`[sepay-webhook] giao dịch ${sepay.id} (${sepay.code ?? "không mã"}): ${outcome}`);
    return json(200, { success: true, outcome });
  } catch (e) {
    // Lỗi tạm thời (Firestore, mạng): trả 500 để SePay tự thử lại; sổ chống trùng nên thử lại an toàn.
    console.error("[sepay-webhook]", e);
    return json(500, { success: false, error: "INTERNAL" });
  }
}
