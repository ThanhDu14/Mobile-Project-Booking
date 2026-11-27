import { HttpError, type VerifiedToken } from "./auth.ts";
import type { FirestoreTransaction } from "./firestore.ts";

/** Read current authority inside the same transaction as the operation. */
export async function requireOwnerAuthority(tx: Pick<FirestoreTransaction, "get">, token: VerifiedToken) {
  const user = await tx.get(`users/${token.uid}`);
  const admin = token.appRole === "ADMIN" && user?.role === "ADMIN";
  if (!user || user.accountStatus !== "ACTIVE" || user.deletionRequestedAt != null ||
    (!admin && (token.appRole !== "OWNER" || user.role !== "OWNER" || user.ownerStatus !== "APPROVED"))) {
    throw new HttpError(403, "FORBIDDEN", "Tài khoản không có quyền quản lý cơ sở");
  }
  return admin;
}

export async function requireOwnerVenue(tx: FirestoreTransaction, token: VerifiedToken, venueId: string) {
  const admin = await requireOwnerAuthority(tx, token);
  const venue = await tx.get(`venues/${venueId}`);
  if (!venue || (!admin && venue.ownerId !== token.uid)) {
    throw new HttpError(404, "NOT_FOUND", "Không tìm thấy cơ sở hoặc bạn không có quyền");
  }
  return venue;
}
