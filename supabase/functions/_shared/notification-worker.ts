import * as fs from "./firestore.ts";
import { activeRecipient, sha256 } from "./notification-enqueue.ts";
import { earliestBookingStart } from "./booking-notification.ts";

type Doc = Record<string, unknown>;
export type DeliveryStore = Pick<typeof fs, "runTransaction" | "query" | "listPage" | "commit">;
export type SendResult = { kind: "accepted" | "unregistered" | "retry" | "failed"; retryAfterMs?: number };
export type Sender = (token: string, data: { recipientUid: string; notificationId: string }) => Promise<SendResult>;
const delays = [60_000, 300_000, 900_000, 3_600_000, 10_800_000];
const ms = (v: unknown) => v instanceof Date ? v.getTime() : 0;
const terminal = (s: unknown) => ["SENT", "SKIPPED", "FAILED"].includes(String(s));
const owns = (job: Doc | null, owner: string, now: Date) => job?.state === "LEASED" && job.leaseOwner === owner && ms(job.leaseUntil) > now.getTime();
const tokens = (u: Doc | null): string[] => Array.isArray(u?.fcmTokens) ? [...new Set(u.fcmTokens.filter((t): t is string => typeof t === "string" && !!t))] : [];
const end = (state: string, now: Date, code: string | null = null) => ({ state, updatedAt: now, leaseOwner: null, leaseUntil: null, lastErrorCode: code });

/** Bounded invocation. Every external send follows a fenced, committed per-token reservation.
 * Unknown provider outcomes may duplicate a push, never the persisted inbox. */
export async function runDeliveryWorker(store: DeliveryStore, send: Sender, clock: () => Date = () => new Date()) {
  const now = clock();
  const deadline = now.getTime() + 90_000;
  const pending = await store.query("notificationDeliveries", [
    { field: "state", op: "EQUAL", value: "PENDING" }, { field: "nextAttemptAt", op: "LESS_THAN_OR_EQUAL", value: now },
  ], { limit: 50, orderBy: { field: "nextAttemptAt" } });
  const expired = await store.query("notificationDeliveries", [
    { field: "state", op: "EQUAL", value: "LEASED" }, { field: "leaseUntil", op: "LESS_THAN_OR_EQUAL", value: now },
  ], { limit: 50, orderBy: { field: "leaseUntil" } });
  // Alternate queues so a constant backlog cannot starve crash recovery.
  const candidates = Array.from({ length: Math.max(pending.length, expired.length) }, (_, i) => [expired[i], pending[i]])
    .flat().filter(Boolean).slice(0, 50);
  let requests = 0, claims = 0;
  for (const candidate of candidates) {
    if (requests >= 50 || clock().getTime() >= deadline) break;
    const path = candidate.path, owner = crypto.randomUUID();
    const claimed = await store.runTransaction(async tx => {
      const job = await tx.get(path), at = clock();
      if (!job || terminal(job.state) || (job.state === "LEASED" ? ms(job.leaseUntil) > at.getTime() : ms(job.nextAttemptAt) > at.getTime())) return false;
      const user = await tx.get(`users/${job.recipientUid}`);
      if (!activeRecipient(user)) { tx.write(fs.updateWrite(path, end("SKIPPED", at, "RECIPIENT_UNAVAILABLE"))); return false; }
      tx.write(fs.updateWrite(path, { state: "LEASED", leaseOwner: owner, leaseUntil: new Date(at.getTime() + 120_000),
        attemptCount: Number(job.attemptCount) + 1, updatedAt: at }));
      return true;
    });
    if (!claimed) continue;
    claims++;
    // Freeze hashes once. Raw tokens are re-resolved only from the current profile before send.
    await store.runTransaction(async tx => {
      const job = await tx.get(path), at = clock();
      if (!owns(job, owner, at) || job!.targetHashes !== null) return;
      const user = await tx.get(`users/${job!.recipientUid}`);
      const inbox = await tx.get(`users/${job!.recipientUid}/notifications/${job!.notificationId}`);
      const prefs = user?.notificationPrefs as Doc | undefined;
      const enabled = inbox?.type === "BOOKING_NEW" || (prefs?.[String(inbox?.type)] ?? inbox?.type !== "PROMOTION") === true;
      if (!activeRecipient(user) || !inbox || !enabled) { tx.write(fs.updateWrite(path, end("SKIPPED", at, "INELIGIBLE"))); return; }
      const hashes = await Promise.all(tokens(user).map(sha256));
      if (!hashes.length) { tx.write(fs.updateWrite(path, end("SKIPPED", at, "NO_TOKEN"))); return; }
      // Conservative headroom below Firestore's 1MiB document limit, without truncating devices.
      if (JSON.stringify(hashes).length > 800_000) { tx.write(fs.updateWrite(path, end("FAILED", at, "TARGET_BUDGET"))); return; }
      tx.write(fs.updateWrite(path, { targetHashes: hashes, updatedAt: at }));
    });
    // One initialization page per invocation; resume with the same frozen set.
    const ready = await store.runTransaction(async tx => {
      const job = await tx.get(path), at = clock();
      if (!owns(job, owner, at)) return false;
      // This read fences child creation against the account-deletion profile gate.
      // Without it, initialization could create orphans between the cleanup's final child scan and parent delete.
      const user = await tx.get(`users/${job!.recipientUid}`);
      if (!activeRecipient(user)) { tx.write(fs.updateWrite(path, end("SKIPPED", at, "RECIPIENT_UNAVAILABLE"))); return false; }
      const hashes = job!.targetHashes as string[], start = Number(job!.initializedThrough), slice = hashes.slice(start, start + 100);
      for (const hash of slice) tx.write(fs.createWrite(`${path}/tokens/${hash}`, {
        state: "PENDING", attemptCount: 0, nextAttemptAt: at, attemptId: null, acceptedAt: null, lastErrorCode: null, updatedAt: at,
      }));
      const through = start + slice.length, complete = through === hashes.length;
      tx.write(fs.updateWrite(path, { initializedThrough: through, targetsInitializedAt: complete ? job!.targetsInitializedAt ?? at : null,
        ...(complete ? {} : { state: "PENDING", nextAttemptAt: at, leaseOwner: null, leaseUntil: null }), updatedAt: at }));
      return complete;
    });
    if (!ready) continue;
    // Each reservation reads its own receipt, profile and inbox under the parent lease.
    const jobSnapshot = await store.runTransaction(tx => tx.get(path));
    for (const hash of (jobSnapshot?.targetHashes as string[] ?? [])) {
      if (requests >= 50 || clock().getTime() >= deadline) break;
      const receiptPath = `${path}/tokens/${hash}`, attemptId = crypto.randomUUID();
      const reserved = await store.runTransaction(async tx => {
        const job = await tx.get(path), receipt = await tx.get(receiptPath), at = clock();
        if (!owns(job, owner, at) || !receipt || terminal(receipt.state) || ms(receipt.nextAttemptAt) > at.getTime()) return null;
        if (Number(receipt.attemptCount) >= 6) { tx.write(fs.updateWrite(receiptPath, { state: "FAILED", lastErrorCode: "ATTEMPTS_EXHAUSTED", updatedAt: at })); return null; }
        const uid = String(job!.recipientUid), user = await tx.get(`users/${uid}`);
        const inbox = await tx.get(`users/${uid}/notifications/${job!.notificationId}`);
        const prefs = user?.notificationPrefs as Doc | undefined;
        const enabled = inbox?.type === "BOOKING_NEW" || (prefs?.[String(inbox?.type)] ?? inbox?.type !== "PROMOTION") === true;
        let raw: string | undefined;
        for (const token of tokens(user)) if (await sha256(token) === hash) { raw = token; break; }
        if (!activeRecipient(user) || !inbox || !enabled || !raw) {
          tx.write(fs.updateWrite(receiptPath, { state: "SKIPPED", lastErrorCode: "INELIGIBLE", updatedAt: at })); return null;
        }
        if (inbox.type === "BOOKING_REMINDER") {
          const data = inbox.data as Doc, id = data?.bookingId;
          const booking = typeof id === "string" && id && !id.includes("/") ? await tx.get(`bookings/${id}`) : null;
          const start = booking && earliestBookingStart(booking);
          if (!booking || booking.userId !== uid || booking.status !== "CONFIRMED" || !start || start <= at ||
            !(data.reminderStartAt instanceof Date) || start.getTime() !== data.reminderStartAt.getTime() ||
            !(booking.startAt instanceof Date) || booking.startAt.getTime() !== start.getTime()) {
            tx.write(fs.updateWrite(receiptPath, { state: "SKIPPED", lastErrorCode: "BOOKING_CHANGED", updatedAt: at })); return null;
          }
        }
        if (inbox.type === "CHAT_MESSAGE") {
          const chatId = (inbox.data as Doc)?.chatId;
          if (typeof chatId !== "string" || chatId.includes("/")) {
            tx.write(fs.updateWrite(receiptPath, { state: "SKIPPED", lastErrorCode: "CHAT_UNAVAILABLE", updatedAt: at })); return null;
          }
          const chat = await tx.get(`chats/${chatId}`), participant = await tx.get(`chats/${chatId}/participants/${uid}`);
          const presence = await tx.query("presence", [{ field: "viewingUntil", op: "GREATER_THAN", value: at }], { parent: `chats/${chatId}/participants/${uid}`, limit: 1 });
          if (!Array.isArray(chat?.memberIds) || !chat.memberIds.includes(uid) || participant?.muted === true || presence.length) {
            tx.write(fs.updateWrite(receiptPath, { state: "SKIPPED", lastErrorCode: "CHAT_SUPPRESSED", updatedAt: at })); return null;
          }
        }
        tx.write(fs.updateWrite(path, { leaseUntil: new Date(at.getTime() + 120_000), updatedAt: at }));
        tx.write(fs.updateWrite(receiptPath, { state: "SENDING", attemptCount: Number(receipt.attemptCount) + 1, attemptId, updatedAt: at }));
        return { raw, uid, notificationId: String(job!.notificationId), count: Number(receipt.attemptCount) + 1 };
      });
      if (!reserved) continue;
      requests++;
      let result: SendResult;
      try { result = await send(reserved.raw, { recipientUid: reserved.uid, notificationId: reserved.notificationId }); }
      catch { result = { kind: "retry" }; }
      await store.runTransaction(async tx => {
        const job = await tx.get(path), receipt = await tx.get(receiptPath), at = clock();
        if (!owns(job, owner, at) || receipt?.attemptId !== attemptId || receipt.state !== "SENDING") return;
        const user = result.kind === "unregistered" ? await tx.get(`users/${reserved.uid}`) : null;
        const retry = result.kind === "retry" && reserved.count < 6;
        const state = result.kind === "accepted" ? "SENT" : result.kind === "unregistered" ? "SKIPPED" : retry ? "PENDING" : "FAILED";
        tx.write(fs.updateWrite(receiptPath, { state, acceptedAt: state === "SENT" ? at : null,
          nextAttemptAt: retry ? new Date(at.getTime() + Math.max(delays[reserved.count - 1], result.retryAfterMs ?? 0)) : null,
          lastErrorCode: result.kind === "accepted" ? null : result.kind === "unregistered" ? "UNREGISTERED" : result.kind === "failed" ? "PROVIDER_REJECTED" : "TRANSIENT", updatedAt: at }));
        if (user && tokens(user).includes(reserved.raw)) tx.write(fs.updateWrite(`users/${reserved.uid}`, {
          fcmTokens: (user.fcmTokens as unknown[]).filter(t => t !== reserved.raw), updatedAt: at,
        }));
      });
    }
    await store.runTransaction(async tx => {
      const job = await tx.get(path), at = clock();
      if (!owns(job, owner, at)) return;
      const receipts = await Promise.all((job!.targetHashes as string[]).map(h => tx.get(`${path}/tokens/${h}`)));
      const unfinished = receipts.filter(r => r && !terminal(r.state));
      if (unfinished.length) {
        const due = Math.min(...unfinished.map(r => ms(r!.nextAttemptAt) || at.getTime()));
        tx.write(fs.updateWrite(path, { ...end("PENDING", at), nextAttemptAt: new Date(due) }));
      } else tx.write(fs.updateWrite(path, end(receipts.some(r => r?.state === "FAILED") ? "FAILED" : receipts.some(r => r?.state === "SENT") ? "SENT" : "SKIPPED", at)));
    });
  }
  return { claims, requests };
}

/** Child-first cleanup; profile deletion gate prevents new enqueue. No worker can recreate a parent. */
export async function deleteDelivery(store: Pick<DeliveryStore, "listPage" | "commit">, path: string) {
  // One page per cron visit: large retained jobs cannot monopolize an invocation.
  const page = await store.listPage(`${path}/tokens`, "", 100);
  if (page.documents.length) await store.commit(page.documents.map(d => fs.deleteWrite(`${path}/tokens/${d.__documentId}`, false)));
  if (page.documents.length === 100) return false;
  await store.commit([fs.deleteWrite(path, false)]);
  return true;
}

export async function cleanupDeliveries(store: DeliveryStore, now = new Date(), uid?: string) {
  const rows = await store.query("notificationDeliveries", uid ? [{ field: "recipientUid", op: "EQUAL", value: uid }] : [
    { field: "state", op: "IN", value: ["SENT", "SKIPPED", "FAILED"] },
    { field: "updatedAt", op: "LESS_THAN_OR_EQUAL", value: new Date(now.getTime() - 7 * 86400_000) },
  ], { limit: 50 });
  let completed = 0;
  for (const row of rows) if (await deleteDelivery(store, row.path)) completed++;
  return completed;
}
