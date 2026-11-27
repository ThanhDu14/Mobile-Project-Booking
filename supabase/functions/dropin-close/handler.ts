// Edge Function `dropin-close` (C, spec 081 US4; contracts/edge-functions-waitlist.md mục 4, research R7).
// pg_cron gọi mỗi phút (migration `dropin_close_cron`):
//   1. đóng hàng chờ của các buổi đã tới mốc T−30 phút (`closeWaitlistInTx(..., "CLOSED")`, mỗi buổi một transaction),
//      sau commit gửi `DROPIN_WAITLIST_CLOSED` cho từng người;
//   2. chuyển buổi đã qua `endAt` sang `COMPLETED` (việc spec 080 R14 giao cho cron này).
// Idempotent: chạy lại thì không đổi gì vì `waitlistTickets` đã rỗng và `status` đã là `COMPLETED`.
import { errorResponse, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { updateWrite } from "../_shared/firestore.ts";
import { type SessionDoc, sessionPath } from "../_shared/dropin.ts";
import { sendDropInNotification, type WaitlistNotification } from "../_shared/dropin-notify.ts";
import { closeWaitlistInTx, WAITLIST_CLOSE_MINUTES } from "../_shared/waitlist.ts";

export interface DropInCloseDeps {
  firestore: Pick<typeof defaultFirestore, "query" | "runTransaction">;
  /** Giờ server. */
  now: () => Date;
  /** Giá trị `CRON_SECRET`; rỗng thì mọi request bị từ chối. */
  cronSecret: string;
  /** Gửi thông báo sau khi commit; lỗi chỉ ghi log. */
  notify: (n: WaitlistNotification) => Promise<void>;
}

function defaults(): DropInCloseDeps {
  return {
    firestore: defaultFirestore,
    now: () => new Date(),
    cronSecret: Deno.env.get("CRON_SECRET") ?? "",
    notify: sendDropInNotification,
  };
}

const LIVE = ["OPEN", "FULL"];
const asDate = (v: unknown): Date | null => (v instanceof Date && !Number.isNaN(v.getTime()) ? v : null);
const hasWaitlist = (s: Record<string, unknown>) =>
  (typeof s.waitlistCount === "number" && s.waitlistCount > 0) ||
  (Array.isArray(s.waitlistTickets) && s.waitlistTickets.length > 0);

export async function handleDropInClose(req: Request, injected: Partial<DropInCloseDeps> = {}): Promise<Response> {
  const deps = { ...defaults(), ...injected };
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
  const supplied = req.headers.get("x-cron-secret") ?? "";
  if (!deps.cronSecret || supplied !== deps.cronSecret) {
    return errorResponse(401, "UNAUTHENTICATED", "Thiếu hoặc sai CRON_SECRET");
  }

  try {
    const now = deps.now();
    const cutoff = new Date(now.getTime() + WAITLIST_CLOSE_MINUTES * 60_000);
    let closedSessions = 0;
    let closedEntries = 0;
    let completedSessions = 0;
    let failed = 0;

    // 1. Đóng hàng chờ của buổi đã tới mốc T−30 phút.
    const toClose = await deps.firestore.query("dropInSessions", [
      { field: "status", op: "IN", value: LIVE },
      { field: "startAt", op: "LESS_THAN_OR_EQUAL", value: cutoff },
    ]);
    for (const doc of toClose) {
      if (!hasWaitlist(doc.data)) continue;
      try {
        const closed = await deps.firestore.runTransaction(async (tx) => {
          // Đọc lại trong transaction: buổi có thể vừa được đẩy lên/hủy/đóng bởi request khác.
          const session = await tx.get(sessionPath(doc.id)) as SessionDoc | null;
          const startAt = asDate(session?.startAt);
          if (!session || !LIVE.includes(String(session.status)) || !startAt || startAt.getTime() > cutoff.getTime()) {
            return { closed: [] as string[] };
          }
          session.id = doc.id;
          return await closeWaitlistInTx(tx, session, "CLOSED", { now });
        });
        if (closed.closed.length > 0) {
          closedSessions++;
          closedEntries += closed.closed.length;
        }
        // closeWaitlistInTx owns the inbox/job. Do not replay through the legacy callback.
      } catch (e) {
        failed++;
        console.error(`[dropin-close] đóng hàng chờ ${doc.id} thất bại`, e);
      }
    }

    // 2. Buổi đã qua endAt → COMPLETED.
    const ended = await deps.firestore.query("dropInSessions", [
      { field: "status", op: "IN", value: LIVE },
      { field: "endAt", op: "LESS_THAN", value: now },
    ]);
    for (const doc of ended) {
      try {
        const done = await deps.firestore.runTransaction(async (tx) => {
          const session = await tx.get(sessionPath(doc.id)) as SessionDoc | null;
          const endAt = asDate(session?.endAt);
          if (!session || !LIVE.includes(String(session.status)) || !endAt || endAt.getTime() >= now.getTime()) {
            return { done: false };
          }
           // If closing failed above, leave the session live so the next cron
           // run can retry instead of stranding WAITLISTED registrations.
           if (hasWaitlist(session)) return { done: false };
          tx.write(updateWrite(sessionPath(doc.id), { status: "COMPLETED", updatedAt: now }));
          return { done: true };
        });
        if (done.done) completedSessions++;
      } catch (e) {
        failed++;
        console.error(`[dropin-close] chuyển ${doc.id} sang COMPLETED thất bại`, e);
      }
    }

    return Response.json({ closedSessions, closedEntries, completedSessions, ...(failed > 0 ? { failed } : {}) });
  } catch (e) {
    return toErrorResponse(e);
  }
}

// Existing owner/device callers use this spelling.
export const handleDropinClose = handleDropInClose;
