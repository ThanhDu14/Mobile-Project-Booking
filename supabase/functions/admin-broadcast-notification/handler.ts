import { requireCurrentAdmin } from "../_shared/admin-authorization.ts";
import { HttpError, requireRole, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { createWrite, FirestoreError } from "../_shared/firestore.ts";

export interface AdminBroadcastDeps {
  firestore: Pick<typeof defaultFirestore, "list" | "commit" | "get">;
}

function json(status: number, data: unknown): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

export function validateBroadcastContent(title: unknown, body: unknown): { title: string; body: string } {
  if (typeof title !== "string" || !title.trim()) {
    throw new HttpError(400, "INVALID_TITLE", "Tiêu đề thông báo không được để trống");
  }
  if (typeof body !== "string" || !body.trim()) {
    throw new HttpError(400, "INVALID_BODY", "Nội dung thông báo không được để trống");
  }
  return {
    title: title.trim(),
    body: body.trim(),
  };
}

export async function handleAdminBroadcastNotification(
  req: Request,
  deps: AdminBroadcastDeps = { firestore: defaultFirestore },
): Promise<Response> {
  if (req.method !== "POST") {
    return json(405, { error: { code: "METHOD_NOT_ALLOWED", message: "Chỉ hỗ trợ POST" } });
  }

  try {
    const admin = await requireUser(req);
    requireRole(admin, "ADMIN");
    requireCurrentAdmin(admin);

    const reqBody = await req.json().catch(() => ({})) as Record<string, unknown>;
    const { title, body } = validateBroadcastContent(reqBody.title, reqBody.body);
    const requestId = reqBody.requestId;
    if (typeof requestId !== "string" || !/^[a-zA-Z0-9-]{16,80}$/.test(requestId)) {
      throw new HttpError(400, "INVALID_REQUEST_ID", "Thiếu mã yêu cầu ổn định để thử lại thông báo");
    }
    const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`${admin.uid}:${requestId}`));
    const notificationId = `sys_${Array.from(new Uint8Array(digest), (v) => v.toString(16).padStart(2, "0")).join("")}`;

    const allUsers = await deps.firestore.list("users");
    const activeUsers = allUsers.filter((u) => {
      const status = u.accountStatus as string | undefined;
      return status === "ACTIVE" && !u.deletionRequestedAt;
    });

    if (activeUsers.length === 0) {
      return json(200, { success: true, count: 0 });
    }

    const nowIso = new Date().toISOString();
    const batchSize = 300;
    let sentCount = 0;

    for (let i = 0; i < activeUsers.length; i += batchSize) {
      const chunk = activeUsers.slice(i, i + batchSize);
      for (let attempt = 0; attempt < 3; attempt++) {
        const writes = (await Promise.all(chunk.map(async (user) => {
          const uid = String(user.__documentId ?? "");
          if (!uid) throw new HttpError(503, "MISSING_RECIPIENT_ID", "Không thể xác định người nhận");
          const path = `users/${uid}/notifications/${notificationId}`;
          const existing = await deps.firestore.get(path);
          if (existing) {
            if (existing.title !== title || existing.body !== body) throw new HttpError(409, "REQUEST_CONFLICT", "Mã yêu cầu đã dùng cho nội dung khác");
            return null;
          }
          return createWrite(path, { type: "SYSTEM", title, body, data: {}, isRead: false, createdAt: nowIso });
        }))).filter((w): w is NonNullable<typeof w> => w !== null);
        try {
          if (writes.length) await deps.firestore.commit(writes);
          sentCount += chunk.length;
          break;
        } catch (error) {
          if (!(error instanceof FirestoreError) || error.code !== "ALREADY_EXISTS" || attempt === 2) throw error;
        }
      }
    }

    return json(200, { success: true, count: sentCount });
  } catch (err) {
    return toErrorResponse(err);
  }
}
