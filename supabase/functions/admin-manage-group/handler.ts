import { requireCurrentAdmin } from "../_shared/admin-authorization.ts";
import {
  HttpError,
  requireRole,
  requireUser,
  toErrorResponse,
} from "../_shared/auth.ts";
import * as fs from "../_shared/firestore.ts";

/** Admin-only lock/unlock. Owners cannot bypass moderation by deleting a locked group. */
export async function handleAdminManageGroup(
  req: Request,
  deps = { firestore: fs as Pick<typeof fs, "runTransaction"> },
): Promise<Response> {
  if (req.method !== "POST") {
    return Response.json({ error: { code: "METHOD_NOT_ALLOWED" } }, {
      status: 405,
    });
  }
  try {
    const admin = await requireUser(req);
    requireRole(admin, "ADMIN");
    requireCurrentAdmin(admin);
    const body = await req.json().catch(() => ({}));
    const groupId = typeof body.groupId === "string" ? body.groupId.trim() : "";
    if (!groupId || groupId.includes("/")) {
      throw new HttpError(400, "INVALID_GROUP_ID", "Mã nhóm không hợp lệ");
    }
    if (!["ACTIVE", "LOCKED"].includes(body.status)) {
      throw new HttpError(
        400,
        "INVALID_STATUS",
        "Chỉ hỗ trợ ACTIVE hoặc LOCKED",
      );
    }
    await deps.firestore.runTransaction(async (tx) => {
      const group = await tx.get(`groups/${groupId}`);
      if (!group) {
        throw new HttpError(404, "GROUP_NOT_FOUND", "Không tìm thấy nhóm");
      }
      if (group.status === "DELETED") {
        throw new HttpError(409, "GROUP_DELETED", "Nhóm đã bị xóa");
      }
      if (group.status !== body.status) {
        tx.write(
          fs.updateWrite(`groups/${groupId}`, {
            status: body.status,
            updatedAt: new Date(),
          }),
        );
      }
    });
    return Response.json({ success: true, groupId, status: body.status });
  } catch (err) {
    return toErrorResponse(err);
  }
}
