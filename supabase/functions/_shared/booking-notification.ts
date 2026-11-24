import * as fs from "./firestore.ts";
import { prepareNotification, sha256 } from "./notification-enqueue.ts";

export const bookingEventIdentity = (id: string, type: string) => JSON.stringify([id, type]);
export async function bookingInboxId(family: string, identity: string, uid: string, type: string) {
  return `n_${await sha256(JSON.stringify([family, identity, uid, type]))}`;
}
/** B1 read-phase helper only. The actual payment transaction is not in this checkout. */
export async function prepareBookingNew(tx: Pick<fs.FirestoreTransaction, "get">, id: string, booking: Record<string, unknown>, now: Date, existingInboxId?: string) {
  if (!id || id.includes("/") || booking.status !== "PENDING" || typeof booking.venueId !== "string" || booking.venueId.includes("/")) throw new Error("INVALID_PENDING_BOOKING");
  const venue = await tx.get(`venues/${booking.venueId}`);
  const uid = venue?.ownerId;
  if (venue?.status !== "ACTIVE" || typeof uid !== "string" || !uid || uid.includes("/")) return [];
  const owner = await tx.get(`users/${uid}`);
  if (owner?.role !== "OWNER" || owner.ownerStatus !== "APPROVED") return [];
  const deadline = booking.approvalDeadline;
  if (!(deadline instanceof Date) || !Number.isFinite(deadline.getTime()) || deadline <= now) throw new Error("INVALID_APPROVAL_DEADLINE");
  const identity = bookingEventIdentity(id, "BOOKING_NEW");
  return await prepareNotification(tx, "booking", identity, uid,
    existingInboxId ?? await bookingInboxId("booking", identity, uid, "BOOKING_NEW"), {
      type: "BOOKING_NEW", title: "Có đơn đặt sân mới", body: `Bạn có đơn đặt sân đang chờ duyệt. Hạn duyệt: ${deadline.toISOString()}.`,
      data: { bookingId: id, venueId: booking.venueId },
    }, now);
}

export function earliestBookingStart(booking: Record<string, unknown>): Date | null {
  if (!Array.isArray(booking.items) || !booking.items.length) return null;
  const values = booking.items.map(i => i?.startAt);
  if (values.some(v => !(v instanceof Date) || !Number.isFinite(v.getTime()))) return null;
  return new Date(Math.min(...values.map(v => v.getTime())));
}
