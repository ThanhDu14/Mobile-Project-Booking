import { requireCurrentAdmin } from "../_shared/admin-authorization.ts";
import { HttpError, requireRole, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { updateWrite, FirestoreError } from "../_shared/firestore.ts";
import { setUserDisabled as defaultSetUserDisabled } from "../_shared/identity.ts";

export type ReportActionType = "NONE" | "HIDE_CONTENT" | "LOCK_USER" | "LOCK_VENUE";

export interface AdminModerateDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "runTransaction">;
  setUserDisabled: (uid: string, disabled: boolean) => Promise<void>;
}

function json(status: number, data: unknown): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

export function validateActionTarget(action: ReportActionType, targetType: string): string | null {
  if (action === "NONE") return null;

  switch (targetType) {
    case "VENUE":
      if (action !== "LOCK_VENUE") return "Báo cáo cơ sở chỉ có thể áp dụng Khóa cơ sở (LOCK_VENUE)";
      return null;
    case "USER":
      if (action !== "LOCK_USER") return "Báo cáo người dùng chỉ có thể áp dụng Khóa tài khoản (LOCK_USER)";
      return null;
    case "POST":
    case "MESSAGE":
    case "REVIEW":
      if (action !== "HIDE_CONTENT") return `Báo cáo ${targetType} chỉ có thể áp dụng Ẩn nội dung (HIDE_CONTENT)`;
      return null;
    case "DROP_IN":
      return "Đối tượng DROP_IN chưa hỗ trợ thao tác ẩn nội dung theo quy định";
    default:
      return `Loại đối tượng ${targetType} không hỗ trợ kiểm duyệt`;
  }
}

export async function handleAdminModerateReport(
  req: Request,
  deps: AdminModerateDeps = { firestore: defaultFirestore, setUserDisabled: defaultSetUserDisabled },
): Promise<Response> {
  if (req.method !== "POST") {
    return json(405, { error: { code: "METHOD_NOT_ALLOWED", message: "Chỉ hỗ trợ POST" } });
  }

  try {
    const user = await requireUser(req);
    requireRole(user, "ADMIN");
    requireCurrentAdmin(user);

    const body = await req.json().catch(() => ({})) as Record<string, unknown>;
    const reportId = typeof body.reportId === "string" ? body.reportId.trim() : "";
    const action = typeof body.action === "string" ? body.action.trim().toUpperCase() as ReportActionType : "NONE";

    if (!reportId) {
      throw new HttpError(400, "INVALID_REPORT_ID", "Mã báo cáo không hợp lệ");
    }

    const validActions: ReportActionType[] = ["NONE", "HIDE_CONTENT", "LOCK_USER", "LOCK_VENUE"];
    if (!validActions.includes(action)) {
      throw new HttpError(400, "INVALID_ACTION", `Hành động không hợp lệ: ${action}`);
    }

    const nowIso = new Date().toISOString();
    let lockedUserId: string | null = null;

    const outcome = await deps.firestore.runTransaction(async (tx) => {
      const report = await tx.get(`reports/${reportId}`);
      if (!report) {
        throw new HttpError(404, "REPORT_NOT_FOUND", "Không tìm thấy báo cáo");
      }

      const targetType = String(report.targetType ?? "").toUpperCase();
      const targetPath = String(report.targetPath ?? "").trim();

      const validationError = validateActionTarget(action, targetType);
      if (validationError) {
        throw new HttpError(400, "ACTION_MISMATCH", validationError);
      }

      if (report.status !== "OPEN") {
        const expectedStatus = action === "NONE" ? "DISMISSED" : "RESOLVED";
        if (report.status === expectedStatus && report.action === action) {
          return { success: true, status: report.status, action, alreadyCommitted: true, reportId };
        }
        throw new HttpError(409, "REPORT_CONFLICT", "Báo cáo đã được xử lý bởi quản trị viên khác");
      }

      if (action === "NONE") {
        // Dismiss report
        tx.write(updateWrite(`reports/${reportId}`, {
          status: "DISMISSED",
          action: "NONE",
          handledBy: user.uid,
          handledAt: nowIso,
        }));
        return { success: true, status: "DISMISSED", action: "NONE", reportId };
      }

      // Action applied on target
      if (!targetPath) {
        throw new HttpError(400, "INVALID_TARGET_PATH", "Báo cáo thiếu đường dẫn đối tượng vi phạm");
      }

      const target = await tx.get(targetPath);
      if (!target) {
        throw new HttpError(404, "TARGET_NOT_FOUND", "Đối tượng báo cáo không còn tồn tại");
      }

      if (action === "LOCK_VENUE") {
        tx.write(updateWrite(targetPath, {
          status: "LOCKED",
          updatedAt: nowIso,
        }));
      } else if (action === "LOCK_USER") {
        tx.write(updateWrite(targetPath, {
          accountStatus: "LOCKED",
          updatedAt: nowIso,
        }));
        lockedUserId = targetPath.split("/")[1] ?? null;
      } else if (action === "HIDE_CONTENT") {
        tx.write(updateWrite(targetPath, {
          status: "HIDDEN",
          updatedAt: nowIso,
        }));
      }

      // Mark report resolved
      tx.write(updateWrite(`reports/${reportId}`, {
        status: "RESOLVED",
        action,
        handledBy: user.uid,
        handledAt: nowIso,
      }));

      return { success: true, status: "RESOLVED", action, reportId };
    });

    if (action === "LOCK_USER" && lockedUserId && !outcome.alreadyCommitted) {
      await deps.setUserDisabled(lockedUserId, true).catch((err) => {
        console.warn("Failed to set user disabled in Firebase Auth:", err);
      });
    }

    return json(200, outcome);
  } catch (error) {
    if (error instanceof FirestoreError && error.code === "ABORTED") {
      return json(409, { error: { code: "REPORT_CONFLICT", message: "Báo cáo vừa thay đổi bởi quản trị viên khác. Vui lòng làm mới." } });
    }
    return toErrorResponse(error);
  }
}
