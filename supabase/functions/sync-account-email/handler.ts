// Logic của sync-account-email (spec 011 US7, contracts/edge-functions-account.md §3).
// Email luôn lấy từ token đã xác minh; body bị bỏ qua để người dùng không tự đặt email (constitution II).
import { errorResponse, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { updateWrite } from "../_shared/firestore.ts";

export interface SyncAccountEmailDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "commit">;
}

export async function handleSyncAccountEmail(
  req: Request,
  { firestore }: SyncAccountEmailDeps = { firestore: defaultFirestore },
): Promise<Response> {
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
  try {
    const user = await requireUser(req);
    await req.body?.cancel();
    const email = user.email?.trim().toLowerCase();
    let updated = false;
    if (user.emailVerified && email) {
      const profile = await firestore.get(`users/${user.uid}`);
      if (profile && profile.email !== email) {
        await firestore.commit([updateWrite(`users/${user.uid}`, { email }, ["updatedAt"])]);
        updated = true;
      }
    }
    return Response.json({ updated });
  } catch (e) {
    return toErrorResponse(e);
  }
}
