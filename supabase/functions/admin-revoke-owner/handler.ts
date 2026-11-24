import { requireCurrentAdmin } from "../_shared/admin-authorization.ts";
import { HttpError, requireRole, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { createWrite, updateWrite } from "../_shared/firestore.ts";
import { mergeClaims as defaultMergeClaims } from "../_shared/identity.ts";
import { reconcileAdminClaims } from "../_shared/admin-claims.ts";
import { notificationInboxId, prepareNotification, sha256 } from "../_shared/notification-enqueue.ts";
import { UUID } from "../_shared/dropin-tx.ts";

export interface AdminRevokeOwnerDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "runTransaction">;
  mergeClaims: (uid: string, claims: Record<string, unknown>) => Promise<void>;
}

function json(status: number, data: unknown): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

export function validateRevokeReason(reason: unknown): string {
  if (typeof reason !== "string") {
    throw new HttpError(400, "INVALID_REASON", "Lý do thu hồi không được để trống");
  }
  const trimmed = reason.trim();
  if (!trimmed) {
    throw new HttpError(400, "INVALID_REASON", "Lý do thu hồi không được để trống");
  }
  return trimmed;
}

export async function handleAdminRevokeOwner(
  req: Request,
  deps: AdminRevokeOwnerDeps = { firestore: defaultFirestore, mergeClaims: defaultMergeClaims },
): Promise<Response> {
  if (req.method !== "POST") {
    return json(405, { error: { code: "METHOD_NOT_ALLOWED", message: "Chỉ hỗ trợ POST" } });
  }

  try {
    const admin = await requireUser(req);
    requireRole(admin, "ADMIN");
    requireCurrentAdmin(admin);

    const body = await req.json().catch(() => ({})) as Record<string, unknown>;
    const uid = typeof body.uid === "string" ? body.uid.trim() : "";
    if (!uid || uid.includes("/")) {
      throw new HttpError(400, "INVALID_UID", "Mã người dùng không hợp lệ");
    }
    if (uid === admin.uid) {
      throw new HttpError(400, "CANNOT_REVOKE_SELF", "Không thể tự thu hồi quyền của chính mình");
    }

    const trimmedReason = validateRevokeReason(body.reason);
    if (typeof body.requestId !== "string" || !UUID.test(body.requestId)) {
      throw new HttpError(400, "INVALID_REQUEST_ID", "requestId phải là UUID");
    }
    const requestId = body.requestId.toLowerCase();
    const journalPath = `users/${uid}/ownerRevocationCommands/${requestId}`;
    const reasonHash = await sha256(trimmedReason);

    const outcome = await deps.firestore.runTransaction(async (tx) => {
      const [profile, user, previous] = await Promise.all([
        tx.get(`users/${admin.uid}`), tx.get(`users/${uid}`), tx.get(journalPath),
      ]);
      requireCurrentAdmin({ profile });
      if (!user) {
        throw new HttpError(404, "USER_NOT_FOUND", "Không tìm thấy người dùng");
      }

      if (user.deletionRequestedAt != null) {
        throw new HttpError(409, "ACCOUNT_DELETING", "Tài khoản đang được xóa");
      }
      if (previous) {
        if (previous.actorUid !== admin.uid || previous.reasonHash !== reasonHash) {
          throw new HttpError(409, "REQUEST_CONFLICT", "Mã yêu cầu đã được sử dụng");
        }
        return { alreadyCommitted: true };
      }

      const now = new Date();
      const journal = createWrite(journalPath, { actorUid: admin.uid, reasonHash, createdAt: now });
      if (user.role === "PLAYER" && user.ownerStatus === "NONE") {
        tx.write(journal);
        return { alreadyCommitted: true };
      }
      const type = "OWNER_REVOKED";
      const family = "owner-revoke";
      const identity = JSON.stringify([uid, requestId, type]);
      const id = await notificationInboxId(family, identity, uid, type);
      const notification = await prepareNotification(tx, family, identity, uid, id, {
        type,
        title: "Quyền chủ sân đã bị thu hồi",
        body: `Quyền chủ sân của bạn đã bị thu hồi bởi quản trị viên. Lý do: ${trimmedReason}`,
        data: { reason: trimmedReason },
      }, now);
      tx.write(updateWrite(`users/${uid}`, {
        role: "PLAYER",
        ownerStatus: "NONE",
        ownerRevokedReason: trimmedReason,
        updatedAt: now.toISOString(),
      }));
      tx.write(journal);
      for (const write of notification) tx.write(write);

      // NOTE: Per FR-009 / US3 AC5: ownerApplications và venues được giữ nguyên, không thay đổi tự động
      return { alreadyCommitted: false };
    });

    await reconcileAdminClaims(uid, deps.firestore.get, deps.mergeClaims);

    return json(200, {
      success: true,
      uid,
      alreadyCommitted: outcome.alreadyCommitted,
    });
  } catch (err) {
    return toErrorResponse(err);
  }
}
