import { HttpError } from "./auth.ts";

/** Auth is outside Firestore transactions. Retry reconciles from current data, never an old decision. */
export async function reconcileAdminClaims(
  uid: string,
  get: (path: string) => Promise<Record<string, unknown> | null>,
  merge: (uid: string, claims: Record<string, unknown>) => Promise<void>,
): Promise<void> {
  try {
    for (let attempt = 0; attempt < 3; attempt++) {
      const before = await get(`users/${uid}`);
      if (!before || !["OWNER", "PLAYER", "ADMIN"].includes(String(before.role))) break;
      await merge(uid, { appRole: before.role });
      const after = await get(`users/${uid}`);
      if (after?.role === before.role && after?.ownerStatus === before.ownerStatus) return;
    }
  } catch { /* Return a recoverable, non-success result; do not expose Auth details. */ }
  throw new HttpError(503, "CLAIM_SYNC_PENDING", "Quyết định đã được lưu nhưng quyền chưa đồng bộ. Vui lòng thử lại để đồng bộ quyền.");
}
