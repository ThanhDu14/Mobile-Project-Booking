import { HttpError } from "./auth.ts";
import type { FirestoreTransaction } from "./firestore.ts";

/** Read current authority inside the same transaction as the operation. */
export async function requireOwnerVenue(tx: FirestoreTransaction, uid: string, venueId: string) {
  const user = await tx.get(`users/${uid}`);
  const admin = user?.role === "ADMIN";
  if (!user || user.accountStatus !== "ACTIVE" ||
    (!admin && (user.role !== "OWNER" || user.ownerStatus !== "APPROVED"))) {
    throw new HttpError(403, "FORBIDDEN", "Tài khoản không có quyền quản lý cơ sở");
  }
  const venue = await tx.get(`venues/${venueId}`);
  if (!venue || (!admin && venue.ownerId !== uid)) {
    throw new HttpError(404, "NOT_FOUND", "Không tìm thấy cơ sở hoặc bạn không có quyền");
  }
  return venue;
}
