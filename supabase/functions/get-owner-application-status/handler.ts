import { HttpError, toErrorResponse, type VerifiedToken } from "../_shared/auth.ts";
import * as defaultAuth from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";

type OwnerStatus = "NONE" | "PENDING" | "APPROVED" | "REJECTED";
type ApplicationStatus = Exclude<OwnerStatus, "NONE">;

export interface OwnerStatusDeps {
  auth: Pick<typeof defaultAuth, "requireUser">;
  firestore: Pick<typeof defaultFirestore, "get" | "query">;
}

function error(status: number, code: string, message: string): HttpError {
  return new HttpError(status, code, message);
}

export async function handleGetOwnerApplicationStatus(
  req: Request,
  deps: OwnerStatusDeps = { auth: defaultAuth, firestore: defaultFirestore },
): Promise<Response> {
  if (req.method !== "GET") {
    return Response.json({ error: { code: "METHOD_NOT_ALLOWED", message: "Chỉ hỗ trợ GET" } }, { status: 405 });
  }
  try {
    const user: VerifiedToken & { appRole: "PLAYER" | "OWNER" | "ADMIN" } = await deps.auth.requireUser(req);
    const account = await deps.firestore.get(`users/${user.uid}`);
    if (!account) throw error(403, "NOT_AUTHORIZED", "Không tìm thấy trạng thái tài khoản authoritative");
    const status = account.ownerStatus;
    const role = account.role;
    if (status !== "NONE" && status !== "PENDING" && status !== "APPROVED" && status !== "REJECTED") {
      throw error(409, "AMBIGUOUS_RESULT", "Trạng thái hồ sơ chưa xác định; hãy tải lại sau");
    }
    const expectedRole = status === "APPROVED" ? "OWNER" : "PLAYER";
    if (role === "ADMIN") throw error(403, "FORBIDDEN", "Thao tác này chỉ dành cho người nộp hồ sơ");
    if (role !== expectedRole) {
      throw error(409, "AMBIGUOUS_RESULT", "Vai trò và trạng thái hồ sơ chưa đồng bộ; hãy tải lại sau");
    }
    if (status === "NONE") return Response.json({ ownerStatus: status, role, application: null });

    const applications = await deps.firestore.query("ownerApplications", [
      { field: "uid", op: "EQUAL", value: user.uid },
      { field: "status", op: "EQUAL", value: status satisfies ApplicationStatus },
    ]);
    if (applications.length !== 1) {
      throw error(409, "AMBIGUOUS_RESULT", "Không thể xác định duy nhất hồ sơ hiện hành; hãy liên hệ hỗ trợ");
    }
    const [application] = applications;
    if (application.data.uid !== user.uid || application.data.status !== status) {
      throw error(409, "AMBIGUOUS_RESULT", "Dữ liệu hồ sơ không nhất quán; hãy tải lại sau");
    }
    return Response.json({
      ownerStatus: status,
      role,
      application: {
        id: application.id,
        status,
        submittedAt: application.data.submittedAt instanceof Date
          ? application.data.submittedAt.toISOString()
          : null,
        rejectReason: status === "REJECTED" && typeof application.data.rejectReason === "string"
          ? application.data.rejectReason
          : null,
        documentPaths: Array.isArray(application.data.documentPaths)
          ? application.data.documentPaths.filter((path): path is string => typeof path === "string")
          : [],
        ...(status === "REJECTED" ? {
          venueDraft: application.data.venueDraft,
          representativeName: application.data.representativeName,
          representativePhone: application.data.representativePhone,
        } : {}),
      },
    });
  } catch (e) {
    return toErrorResponse(e);
  }
}
