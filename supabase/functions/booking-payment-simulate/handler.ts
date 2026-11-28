// Edge Function `booking-payment-simulate`: giả lập "đã chuyển khoản" cho đơn đang chờ chuyển khoản, dùng để thử
// luồng thanh toán khi chưa có tài khoản SePay. Chạy đúng lõi chốt đơn của webhook SePay
// (`processSepayTransaction`), số tiền đúng bằng mã thanh toán, sổ giao dịch ghi `simulated: true`.
//
// - Chỉ chạy khi env `PAYMENT_SIMULATOR_ENABLED=true`; không thì 404 `SIMULATOR_DISABLED`.
// - Chỉ người chơi tạo mã mới giả lập được (403 FORBIDDEN).
// - Mã `id` giao dịch giả là số âm suy ra từ mã thanh toán (không trùng id thật của SePay), nên bấm lại → DUPLICATE.
// - Đơn đã hết giữ chỗ hoặc lệch tiền → 409 `HOLD_EXPIRED`, không sinh khoản hoàn cho tiền giả.
import { errorResponse, HttpError, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { readJsonObject } from "../_shared/dropin-tx.ts";
import { asNumber, asString, forbidden, holdExpired } from "../_shared/payment-order.ts";
import { type SepayTransaction } from "../_shared/sepay.ts";
import { processSepayTransaction, type WebhookOutcome } from "../sepay-webhook/handler.ts";

export interface SimulateDeps {
  firestore: Pick<typeof defaultFirestore, "runTransaction">;
  authenticate: (req: Request) => Promise<{ uid: string }>;
  now: () => Date;
  enabled: () => boolean;
}

const CODE_PATTERN = /^SNP[A-Z0-9]{8}$/;

/** Id âm, cố định theo mã: 48 bit đầu của SHA-256(mã). */
export async function simulatedTransactionId(paymentCode: string): Promise<number> {
  const digest = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(paymentCode)));
  let n = 0;
  for (let i = 0; i < 6; i++) n = n * 256 + digest[i];
  return -(n + 1);
}

export async function handleBookingPaymentSimulate(
  req: Request,
  deps: SimulateDeps = defaultDeps(),
): Promise<Response> {
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
  if (!deps.enabled()) return errorResponse(404, "SIMULATOR_DISABLED", "Chưa bật thanh toán giả lập");
  try {
    const { uid } = await deps.authenticate(req);
    const body = await readJsonObject(req);
    const paymentCode = typeof body.paymentCode === "string" ? body.paymentCode.trim().toUpperCase() : "";
    if (!CODE_PATTERN.test(paymentCode)) throw new HttpError(400, "INVALID_ARGUMENT", "paymentCode sai dạng");

    const id = await simulatedTransactionId(paymentCode);
    const now = deps.now();
    const outcome = await deps.firestore.runTransaction<WebhookOutcome>(async (tx) => {
      const code = await tx.get(`paymentCodes/${paymentCode}`);
      if (!code) throw new HttpError(404, "CODE_NOT_FOUND", "Không tìm thấy mã thanh toán");
      if (asString(code.userId) !== uid) throw forbidden();
      const sepay: SepayTransaction = {
        id,
        transferType: "in",
        transferAmount: asNumber(code.amount),
        code: paymentCode,
        content: `${paymentCode} GIA LAP`,
        referenceCode: "SIMULATED",
        transactionDate: now.toISOString(),
      };
      const processed = await processSepayTransaction(tx, sepay, now, { simulated: true, simulatedBy: uid });
      // Tiền giả không sinh khoản hoàn: chỉ ghi khi chốt được đơn.
      return processed.result === "CONFIRMED" ? processed : { result: processed.result, writes: [] };
    });
    if (outcome === "LATE_TRANSFER" || outcome === "AMOUNT_MISMATCH") throw holdExpired();
    console.log(`[booking-payment-simulate] ${paymentCode} (${uid}): ${outcome}`);
    return Response.json({ outcome });
  } catch (e) {
    return toErrorResponse(e);
  }
}

export function defaultDeps(): SimulateDeps {
  return {
    firestore: defaultFirestore,
    authenticate: (req) => requireUser(req),
    now: () => new Date(),
    enabled: () => Deno.env.get("PAYMENT_SIMULATOR_ENABLED") === "true",
  };
}
