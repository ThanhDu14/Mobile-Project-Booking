import { HttpError, requireRole, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { updateWrite } from "../_shared/firestore.ts";
import { setUserDisabled as defaultSetUserDisabled } from "../_shared/identity.ts";

export type AdminUserAction = "LOOKUP" | "SET_ACCOUNT_STATUS";
export type AccountStatus = "ACTIVE" | "LOCKED";

export interface AdminManageUserDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "runTransaction" | "list">;
  setUserDisabled: (uid: string, disabled: boolean) => Promise<void>;
}

function json(status: number, data: unknown): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

export function sanitizeUserRecord(uid: string, data: Record<string, unknown> | null): Record<string, unknown> | null {
  if (!data) return null;
  return {
    uid,
    fullName: data.displayName ?? data.fullName ?? "",
    phone: data.phone ?? "",
    role: data.role ?? "PLAYER",
    ownerStatus: data.ownerStatus ?? "NONE",
    accountStatus: data.accountStatus ?? "ACTIVE",
    ownerRevokedReason: data.ownerRevokedReason ?? null,
    createdAt: data.createdAt ?? null,
  };
}

export async function handleAdminManageUser(
  req: Request,
  deps: AdminManageUserDeps = { firestore: defaultFirestore, setUserDisabled: defaultSetUserDisabled },
): Promise<Response> {
  if (req.method !== "POST") {
    return json(405, { error: { code: "METHOD_NOT_ALLOWED", message: "Chỉ hỗ trợ POST" } });
  }

  try {
    const admin = await requireUser(req);
    requireRole(admin, "ADMIN");

    const body = await req.json().catch(() => ({})) as Record<string, unknown>;
    const action = typeof body.action === "string" ? body.action.trim().toUpperCase() as AdminUserAction : "LOOKUP";

    if (action === "LOOKUP") {
      const query = typeof body.query === "string" ? body.query.trim() : "";
      if (!query) {
        throw new HttpError(400, "INVALID_QUERY", "Từ khóa tra cứu không được để trống");
      }

      // 1. Thử lấy trực tiếp theo UID
      let userDoc = await deps.firestore.get(`users/${query}`);
      let targetUid = query;

      // 2. Nếu không tìm thấy bằng UID, tra cứu qua danh sách theo phone
      if (!userDoc) {
        const allUsers = await deps.firestore.list("users");
        const found = allUsers.find((u) => {
          const p = String(u.phone ?? "");
          return p === query || p.replace(/\s+/g, "") === query;
        });
        if (found) {
          userDoc = found;
          targetUid = String(found.uid ?? query);
        }
      }

      if (!userDoc) {
        return json(200, { success: true, user: null });
      }

      return json(200, { success: true, user: sanitizeUserRecord(targetUid, userDoc) });
    }

    if (action === "SET_ACCOUNT_STATUS") {
      const uid = typeof body.uid === "string" ? body.uid.trim() : "";
      const accountStatus = typeof body.accountStatus === "string" ? body.accountStatus.trim().toUpperCase() as AccountStatus : null;

      if (!uid) {
        throw new HttpError(400, "INVALID_UID", "Mã người dùng không hợp lệ");
      }
      if (accountStatus !== "ACTIVE" && accountStatus !== "LOCKED") {
        throw new HttpError(400, "INVALID_STATUS", "Trạng thái phải là ACTIVE hoặc LOCKED");
      }
      if (uid === admin.uid) {
        throw new HttpError(400, "CANNOT_LOCK_SELF", "Không thể tự thay đổi trạng thái tài khoản của chính mình");
      }

      const outcome = await deps.firestore.runTransaction(async (tx) => {
        const user = await tx.get(`users/${uid}`);
        if (!user) {
          throw new HttpError(404, "USER_NOT_FOUND", "Không tìm thấy người dùng");
        }

        const currentStatus = (user.accountStatus as string | undefined) ?? "ACTIVE";
        if (currentStatus === accountStatus) {
          return { alreadyCommitted: true };
        }

        const nowIso = new Date().toISOString();
        tx.write(updateWrite(`users/${uid}`, {
          accountStatus,
          updatedAt: nowIso,
        }));

        return { alreadyCommitted: false };
      });

      if (!outcome.alreadyCommitted) {
        await deps.setUserDisabled(uid, accountStatus === "LOCKED");
      }

      return json(200, {
        success: true,
        uid,
        accountStatus,
        alreadyCommitted: outcome.alreadyCommitted,
      });
    }

    throw new HttpError(400, "INVALID_ACTION", `Hành động ${action} không hỗ trợ`);
  } catch (err) {
    return toErrorResponse(err);
  }
}
