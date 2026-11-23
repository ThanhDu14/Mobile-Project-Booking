import * as fs from "./firestore.ts";
import { HttpError } from "./auth.ts";

export const NOTIFICATION_TYPES = new Set([
  "BOOKING_NEW", "BOOKING_CONFIRMED", "BOOKING_REJECTED", "BOOKING_CANCELLED", "BOOKING_REMINDER",
  "DROPIN_REGISTERED", "DROPIN_SPOT_OPENED", "DROPIN_WAITLIST_CLOSED", "DROPIN_CHANGED", "PROMOTION",
  "OWNER_APPROVED", "OWNER_REJECTED", "OWNER_REVOKED", "REVIEW_REPLIED", "REVIEW_NEW", "REVIEW_REMINDER",
  "GROUP_ACTIVITY", "CHAT_MESSAGE", "SYSTEM",
]);
export async function sha256(value: string): Promise<string> {
  const bytes = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return Array.from(new Uint8Array(bytes), b => b.toString(16).padStart(2, "0")).join("");
}
export const activeRecipient = (u: Record<string, unknown> | null) => !!u && u.accountStatus === "ACTIVE" && !u.deletionRequestedAt;
export interface InboxContent { type: string; title: string; body: string; data: Record<string, unknown> }

/** Read phase only. Caller appends returned writes after ALL source reads, in the SAME transaction.
 * Existing inbox is authoritative: never backfill a missing/retained-away job. */
export async function prepareNotification(
  tx: Pick<fs.FirestoreTransaction, "get">,
  family: string, identity: string, uid: string, notificationId: string,
  content: InboxContent, now: Date,
): Promise<fs.Write[]> {
  if (!family || !identity || !uid || uid.includes("/") || !notificationId || notificationId.includes("/") ||
    !NOTIFICATION_TYPES.has(content.type) || typeof content.title !== "string" || typeof content.body !== "string" ||
    !content.data || typeof content.data !== "object" || Array.isArray(content.data) || content.data instanceof Date ||
    !Number.isFinite(now.getTime())) throw new Error("INVALID_NOTIFICATION_EVENT");
  const path = `users/${uid}/notifications/${notificationId}`;
  const jobId = await sha256(JSON.stringify([family, identity, uid, content.type]));
  const [user, inbox, job] = await Promise.all([tx.get(`users/${uid}`), tx.get(path), tx.get(`notificationDeliveries/${jobId}`)]);
  if (!activeRecipient(user)) return [];
  if (inbox) {
    if (inbox.type !== content.type || inbox.title !== content.title || inbox.body !== content.body) throw new HttpError(409, "REQUEST_CONFLICT", "Mã yêu cầu đã dùng cho nội dung khác");
    return [];
  }
  // A different UUID replay for an already-enqueued source must not create another inbox.
  if (job) return [];
  return [fs.createWrite(path, { type: content.type, title: content.title, body: content.body, data: content.data, isRead: false, createdAt: now }),
    fs.createWrite(`notificationDeliveries/${jobId}`, {
      recipientUid: uid, notificationId, state: "PENDING", attemptCount: 0,
      nextAttemptAt: now, createdAt: now, updatedAt: now, leaseOwner: null, leaseUntil: null,
      lastErrorCode: null, targetHashes: null, initializedThrough: 0, targetsInitializedAt: null,
    })];
}
