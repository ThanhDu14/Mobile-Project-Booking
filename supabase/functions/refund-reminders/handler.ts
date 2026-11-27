// Edge Function `refund-reminders` (spec 050 US4; contracts/edge-functions.md §6), pg_cron gọi mỗi giờ.
// Nhắc chủ sân các khoản hoàn PENDING quá 24 giờ chưa nhắc: ghi `users/{ownerId}/notifications` loại
// REFUND_REMINDER và đặt `remindedAt`, mỗi khoản một transaction (chỉ nhắc một lần).
import { errorResponse, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { createWrite, updateWrite } from "../_shared/firestore.ts";
import { asDate, asNumber, asString } from "../_shared/payment-order.ts";

export interface RefundRemindersDeps {
  firestore?: Pick<typeof defaultFirestore, "query" | "runTransaction">;
  now?: () => Date;
  cronSecret?: string;
}

const REMIND_AFTER_MS = 24 * 60 * 60_000;

function defaults(): Required<RefundRemindersDeps> {
  return { firestore: defaultFirestore, now: () => new Date(), cronSecret: Deno.env.get("CRON_SECRET") ?? "" };
}

const vnd = (n: number) => `${n.toLocaleString("de-DE")}đ`;

export async function handleRefundReminders(req: Request, injected: RefundRemindersDeps = {}): Promise<Response> {
  const deps = { ...defaults(), ...injected };
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
  const supplied = req.headers.get("x-cron-secret") ?? "";
  if (!deps.cronSecret || supplied !== deps.cronSecret) {
    return errorResponse(401, "UNAUTHENTICATED", "Thiếu hoặc sai CRON_SECRET");
  }
  try {
    const now = deps.now();
    const cutoff = now.getTime() - REMIND_AFTER_MS;
    const candidates = (await deps.firestore.query("refunds", [{ field: "status", op: "EQUAL", value: "PENDING" }]))
      .filter((d) => d.data.remindedAt == null && (asDate(d.data.createdAt)?.getTime() ?? Infinity) <= cutoff);
    let reminded = 0;
    for (const c of candidates) {
      const done = await deps.firestore.runTransaction<boolean>(async (tx) => {
        const refund = await tx.get(`refunds/${c.id}`);
        if (!refund || refund.status !== "PENDING" || refund.remindedAt != null) return { result: false, writes: [] };
        const venue = await tx.get(`venues/${asString(refund.venueId) ?? "-"}`);
        const ownerId = asString(venue?.ownerId);
        if (!ownerId) return { result: false, writes: [] };
        return {
          result: true,
          writes: [
            createWrite(`users/${ownerId}/notifications/refund_reminder_${c.id}`, {
              type: "REFUND_REMINDER",
              title: "Nhắc hoàn tiền cho người chơi",
              body: `Khoản hoàn ${vnd(asNumber(refund.amount))} tại ${
                asString(venue?.name) ?? "cơ sở của bạn"
              } đã chờ hơn 24 giờ`,
              data: { refundId: c.id, bookingId: asString(refund.bookingId) },
              isRead: false,
              createdAt: now,
            }),
            updateWrite(`refunds/${c.id}`, { remindedAt: now }),
          ],
        };
      });
      if (done) reminded++;
    }
    return Response.json({ reminded });
  } catch (e) {
    return toErrorResponse(e);
  }
}
