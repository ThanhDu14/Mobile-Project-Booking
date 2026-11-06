import { HttpError, requireRole, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { createWrite } from "../_shared/firestore.ts";

export interface AdminBroadcastDeps {
  firestore: Pick<typeof defaultFirestore, "list" | "commit">;
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

    const reqBody = await req.json().catch(() => ({})) as Record<string, unknown>;
    const { title, body } = validateBroadcastContent(reqBody.title, reqBody.body);

    const allUsers = await deps.firestore.list("users");
    const activeUsers = allUsers.filter((u) => {
      const status = u.accountStatus as string | undefined;
      return !status || status === "ACTIVE";
    });

    if (activeUsers.length === 0) {
      return json(200, { success: true, count: 0 });
    }

    const nowIso = new Date().toISOString();
    const batchSize = 300;
    let sentCount = 0;

    for (let i = 0; i < activeUsers.length; i += batchSize) {
      const chunk = activeUsers.slice(i, i + batchSize);
      const writes = chunk.map((user) => {
        const uid = String(user.uid ?? user.__documentId ?? "");
        if (!uid) return null;
        const notificationId = `sys_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`;
        return createWrite(`users/${uid}/notifications/${notificationId}`, {
          type: "SYSTEM",
          title,
          body,
          data: {},
          isRead: false,
          createdAt: nowIso,
        });
      }).filter((w): w is NonNullable<typeof w> => w !== null);

      if (writes.length > 0) {
        await deps.firestore.commit(writes);
        sentCount += writes.length;
      }
    }

    return json(200, { success: true, count: sentCount });
  } catch (err) {
    return toErrorResponse(err);
  }
}
