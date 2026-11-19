import { requireCurrentAdmin } from "../_shared/admin-authorization.ts";
import { HttpError, requireRole, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { updateWrite } from "../_shared/firestore.ts";
import { setUserDisabled as defaultSetUserDisabled } from "../_shared/identity.ts";

export type AdminUserAction = "LOOKUP" | "LIST" | "SET_ACCOUNT_STATUS";
export type AccountStatus = "ACTIVE" | "LOCKED";

export interface AdminManageUserDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "runTransaction" | "list" | "listPage" | "query">;
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
    email: data.email ?? "",
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
    requireCurrentAdmin(admin);

    const body = await req.json().catch(() => ({})) as Record<string, unknown>;
    const action = typeof body.action === "string" ? body.action.trim().toUpperCase() as AdminUserAction : "LOOKUP";

    if (action === "LIST") {
      const query = typeof body.query === "string" ? normalizeSearch(body.query) : "";
      const cursor = typeof body.pageToken === "string" ? body.pageToken : "";
      const filter = typeof body.filter === "string" ? body.filter : "ALL";
      if (!["ALL","OWNERS","LOCKED"].includes(filter)) throw new HttpError(400,"INVALID_FILTER","Bộ lọc không hợp lệ");
      const email = typeof body.query === "string" ? body.query.trim().toLowerCase() : "";
      if (!cursor && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        const direct = await deps.firestore.query("users",[{field:"email",op:"EQUAL",value:email}],{limit:30});
        if (direct.length) return json(200,{success:true,users:direct.filter(u=>matchesAdminFilter(u.data,filter)).map(u=>sanitizeUserRecord(u.id,u.data)),nextPageToken:""});
      }
      let next = cursor;
      const users: Record<string, unknown>[] = [];
      // Bounded scan of existing fields: no new search index/schema is assumed.
      for (let page = 0; page < 5; page++) {
        const result = await deps.firestore.listPage("users", next, 30);
        users.push(...result.documents.filter((u) => matchesSearch(u, query) && matchesAdminFilter(u,filter))
          .map((u) => sanitizeUserRecord(String(u.__documentId),u)!));
        next = result.nextPageToken;
        if (users.length || !next) break;
      }
      return json(200,{success:true,users,nextPageToken:next});
    }

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
          return matchesSearch(u, normalizeSearch(query));
        });
        if (found) {
          userDoc = found;
          targetUid = String(found.__documentId ?? "");
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

      // Retry also repairs Auth after Firestore has already committed.
      const current = await deps.firestore.get(`users/${uid}`);
      await deps.setUserDisabled(uid, current?.accountStatus === "LOCKED");

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

function normalizeSearch(value: string): string { return value.normalize("NFD").replace(/\p{Mn}/gu, "").replace(/[đĐ]/g,"d").toLowerCase().replace(/\s+/g,"").trim(); }
function matchesSearch(user: Record<string,unknown>, query: string): boolean {
  return [user.__documentId,user.displayName,user.fullName,user.email,user.phone].some((v) => normalizeSearch(String(v ?? "")).includes(query));
}
function matchesAdminFilter(user: Record<string,unknown>, filter: string): boolean {
  return (filter !== "OWNERS" || user.role === "OWNER" || user.ownerStatus === "APPROVED") && (filter !== "LOCKED" || user.accountStatus === "LOCKED");
}
