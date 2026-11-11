import { HttpError, requireRole, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { createWrite, updateWrite } from "../_shared/firestore.ts";
import { mergeClaims as defaultMergeClaims } from "../_shared/identity.ts";

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

    const body = await req.json().catch(() => ({})) as Record<string, unknown>;
    const uid = typeof body.uid === "string" ? body.uid.trim() : "";
    if (!uid) {
      throw new HttpError(400, "INVALID_UID", "Mã người dùng không hợp lệ");
    }
    if (uid === admin.uid) {
      throw new HttpError(400, "CANNOT_REVOKE_SELF", "Không thể tự thu hồi quyền của chính mình");
    }

    const trimmedReason = validateRevokeReason(body.reason);

    const outcome = await deps.firestore.runTransaction(async (tx) => {
      const user = await tx.get(`users/${uid}`);
      if (!user) {
        throw new HttpError(404, "USER_NOT_FOUND", "Không tìm thấy người dùng");
      }

      const role = user.role as string | undefined;
      const ownerStatus = user.ownerStatus as string | undefined;

      // Idempotency: Nếu đã là PLAYER và NONE thì không làm lại
      if (role === "PLAYER" && ownerStatus === "NONE") {
        return { alreadyCommitted: true };
      }

      const nowIso = new Date().toISOString();
      const notificationId = `revoked_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;

      // Cập nhật users document
      tx.write(updateWrite(`users/${uid}`, {
        role: "PLAYER",
        ownerStatus: "NONE",
        ownerRevokedReason: trimmedReason,
        updatedAt: nowIso,
      }));

      // Gửi thông báo OWNER_REVOKED
      tx.write(createWrite(`users/${uid}/notifications/${notificationId}`, {
        type: "OWNER_REVOKED",
        title: "Quyền chủ sân đã bị thu hồi",
        body: `Quyền chủ sân của bạn đã bị thu hồi bởi quản trị viên. Lý do: ${trimmedReason}`,
        data: { reason: trimmedReason },
        isRead: false,
        createdAt: nowIso,
      }));

      // NOTE: Per FR-009 / US3 AC5: ownerApplications và venues được giữ nguyên, không thay đổi tự động
      return { alreadyCommitted: false };
    });

    if (!outcome.alreadyCommitted) {
      await deps.mergeClaims(uid, { appRole: "PLAYER" }).catch((err) => {
        console.error("Không thể cập nhật claim appRole=PLAYER:", err);
      });
    }

    return json(200, {
      success: true,
      uid,
      alreadyCommitted: outcome.alreadyCommitted,
    });
  } catch (err) {
    return toErrorResponse(err);
  }
}
