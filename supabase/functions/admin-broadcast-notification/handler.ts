import { requireCurrentAdmin } from "../_shared/admin-authorization.ts";
import { HttpError, requireRole, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { activeRecipient, prepareNotification } from "../_shared/notification-enqueue.ts";

export interface AdminBroadcastDeps {
  firestore: Pick<typeof defaultFirestore, "list" | "runTransaction">;
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
    const batchSize = 100;
    let sentCount = 0;

    for (let i = 0; i < activeUsers.length; i += batchSize) {
      const chunk = activeUsers.slice(i, i + batchSize);
      const count = await deps.firestore.runTransaction(async tx => {
        const groups = await Promise.all(chunk.map(async (user) => {
          const uid = String(user.__documentId ?? "");
          if (!uid) throw new HttpError(503, "MISSING_RECIPIENT_ID", "Không thể xác định người nhận");
          if (!activeRecipient(await tx.get(`users/${uid}`))) return null;
          return await prepareNotification(tx, "system", JSON.stringify([admin.uid, requestId]), uid, notificationId,
            { type: "SYSTEM", title, body, data: {} }, new Date(nowIso));
        }));
        const eligible = groups.filter((g): g is NonNullable<typeof g> => g !== null);
        for (const write of eligible.flat()) tx.write(write);
        return eligible.length;
      });
      sentCount += count;
    }

    return json(200, { success: true, count: sentCount });
  } catch (err) {
    return toErrorResponse(err);
  }
}
