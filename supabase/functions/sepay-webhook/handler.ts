import * as defaultFirestore from "../_shared/firestore.ts";
import { createWrite, updateWrite, type Write } from "../_shared/firestore.ts";
import { parseSepayTransaction, verifySepaySignature } from "../_shared/sepay.ts";

/**
 * Webhook "Tiền vào" của SePay Test mode: cộng ví giả lập cho yêu cầu nạp `walletTopups/{code}` (spec 050).
 *
 * - Xác thực bằng HMAC-SHA256 (`X-SePay-Signature`, `X-SePay-Timestamp`); sai chữ ký → 401.
 * - Chống trùng theo `id` của SePay: `sepayTransactions/{id}` tạo trong cùng transaction với việc cộng ví,
 *   nên SePay gửi lại (retry, Replay) không cộng hai lần.
 * - Giao dịch hợp lệ nhưng không khớp yêu cầu nạp nào vẫn trả 200 `{"success": true}` (đã ghi nhận), để SePay
 *   không thử lại mãi; lý do nằm ở `sepayTransactions/{id}.outcome`.
 */
export interface SepayWebhookDeps {
  firestore?: Pick<typeof defaultFirestore, "runTransaction">;
  secret?: string;
  nowSeconds?: () => number;
}

export type TopupOutcome =
  | "CREDITED"
  | "DUPLICATE"
  | "NOT_INCOMING"
  | "NO_CODE"
  | "TOPUP_NOT_FOUND"
  | "TOPUP_NOT_PENDING"
  | "AMOUNT_MISMATCH"
  | "USER_NOT_FOUND";

function defaults(): Required<SepayWebhookDeps> {
  return {
    firestore: defaultFirestore,
    secret: Deno.env.get("SEPAY_WEBHOOK_SECRET") ?? "",
    nowSeconds: () => Math.floor(Date.now() / 1000),
  };
}

function json(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
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
  const tx = parseSepayTransaction(body);
  if (!tx) return json(400, { success: false, error: "INVALID_PAYLOAD" });

  try {
    const outcome = await deps.firestore.runTransaction<TopupOutcome>(async (t) => {
      const ledgerPath = `sepayTransactions/${tx.id}`;
      if (await t.get(ledgerPath)) return { result: "DUPLICATE", writes: [] };

      const ledger = (outcome: TopupOutcome, extra: Record<string, unknown> = {}): Write =>
        createWrite(ledgerPath, {
          sepayId: tx.id,
          transferType: tx.transferType,
          transferAmount: tx.transferAmount,
          code: tx.code,
          content: tx.content,
          referenceCode: tx.referenceCode,
          transactionDate: tx.transactionDate,
          outcome,
          ...extra,
        }, ["receivedAt"]);
      const reject = (outcome: TopupOutcome, extra?: Record<string, unknown>) => ({
        result: outcome,
        writes: [ledger(outcome, extra)],
      });

      if (tx.transferType !== "in") return reject("NOT_INCOMING");
      if (!tx.code) return reject("NO_CODE");

      const topupPath = `walletTopups/${tx.code}`;
      const topup = await t.get(topupPath);
      if (!topup) return reject("TOPUP_NOT_FOUND");
      if (topup.status !== "PENDING") return reject("TOPUP_NOT_PENDING");
      if (Number(topup.amount) !== tx.transferAmount) {
        return reject("AMOUNT_MISMATCH", { expectedAmount: Number(topup.amount) });
      }

      const userId = String(topup.userId ?? "");
      const user = userId ? await t.get(`users/${userId}`) : null;
      if (!user) return reject("USER_NOT_FOUND");

      const balanceBefore = Number(user.walletBalance ?? 0);
      const balanceAfter = (Number.isSafeInteger(balanceBefore) ? balanceBefore : 0) + tx.transferAmount;
      const walletTxId = `sepay-${tx.id}`;
      return {
        result: "CREDITED",
        writes: [
          ledger("CREDITED", { userId, topupCode: tx.code }),
          updateWrite(`users/${userId}`, { walletBalance: balanceAfter }, ["updatedAt"]),
          createWrite(`users/${userId}/walletTransactions/${walletTxId}`, {
            txId: walletTxId,
            userId,
            type: "TOP_UP",
            amount: tx.transferAmount,
            balanceAfter,
            bookingId: null,
            description: `Nạp ví giả lập qua SePay sandbox (${tx.code})`,
          }, ["createdAt"]),
          updateWrite(topupPath, { status: "SUCCESS", sepayId: tx.id, balanceAfter }, ["paidAt"]),
        ],
      };
    });
    console.log(`[sepay-webhook] giao dịch ${tx.id} (${tx.code ?? "không mã"}): ${outcome}`);
    return json(200, { success: true, outcome });
  } catch (e) {
    // Lỗi tạm thời (Firestore, mạng): trả 500 để SePay tự thử lại; ledger chống trùng nên thử lại an toàn.
    console.error("[sepay-webhook]", e);
    return json(500, { success: false, error: "INTERNAL" });
  }
}
