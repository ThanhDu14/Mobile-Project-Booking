// Logic của delete-account (spec 011 US6, contracts/edge-functions-account.md §1). Nhận deps để test thay được.
import { errorResponse, HttpError, requireUser, toErrorResponse } from "../_shared/auth.ts";
import {
  commitDeletion,
  computeImpact,
  type DeletionDeps,
  defaultDeletionDeps,
  estimateTransforms,
  HelperMissingError,
  ImpactChangedError,
  MAX_TRANSFORMS,
  runCleanup,
  toPreview,
} from "../_shared/account-deletion.ts";
import { FirestoreError } from "../_shared/firestore.ts";

/** Xác nhận lại danh tính phải trong vòng 5 phút (research R8). */
const REAUTH_WINDOW_SECONDS = 300;
/** Dọn dẹp tối đa ~100 giây, còn lại để sweeper làm (giới hạn 150 giây của Edge Function). */
const CLEANUP_BUDGET_MS = 100_000;
const CONFIRM_WORD = "XÓA";

async function readBody(req: Request): Promise<Record<string, unknown>> {
  try {
    const body = await req.json();
    return body && typeof body === "object" && !Array.isArray(body) ? body : {};
  } catch {
    return {};
  }
}

const invalid = () => new HttpError(400, "INVALID_ARGUMENT", "Yêu cầu không hợp lệ");
const internal = () => new HttpError(500, "INTERNAL", "Chưa xóa được tài khoản. Tài khoản vẫn dùng bình thường");

async function preview(req: Request, deps: DeletionDeps): Promise<Response> {
  const user = await requireUser(req);
  const now = deps.now();
  const impact = await computeImpact(user.uid, now, deps.firestore);
  return Response.json(toPreview(impact, now, deps.helpers));
}

async function confirm(req: Request, body: Record<string, unknown>, deps: DeletionDeps): Promise<Response> {
  const start = Date.now();
  const user = await requireUser(req, { allowDeleting: true });
  const nowSeconds = Math.floor(deps.now().getTime() / 1000);
  if (nowSeconds - (user.authTime ?? 0) > REAUTH_WINDOW_SECONDS) {
    throw new HttpError(403, "REAUTH_REQUIRED", "Vui lòng xác nhận lại danh tính");
  }
  if (user.appRole === "ADMIN") {
    throw new HttpError(403, "ADMIN_SELF_DELETE", "Tài khoản quản trị không tự xóa được, liên hệ nhóm phát triển");
  }
  const confirmText = typeof body.confirmText === "string" ? body.confirmText : "";
  if (confirmText.trim().toUpperCase() !== CONFIRM_WORD) {
    throw new HttpError(400, "CONFIRM_TEXT_MISMATCH", "Gõ đúng chữ XÓA để xác nhận");
  }
  const impactVersion = body.impactVersion;
  if (typeof impactVersion !== "string" || impactVersion.length === 0) throw invalid();

  const existing = await deps.firestore.get(`accountDeletions/${user.uid}`);
  if (existing?.state === "DONE") return Response.json({ status: "DELETED" });

  if (!existing) {
    const now = deps.now();
    const impact = await computeImpact(user.uid, now, deps.firestore);
    if (impact.version !== impactVersion) throw impactChanged(impact, now, deps);
    if (estimateTransforms(impact, deps.helpers) > MAX_TRANSFORMS) {
      throw new HttpError(
        422,
        "DELETE_TOO_LARGE",
        "Tài khoản có quá nhiều đơn sắp tới để xóa tự động. Vui lòng hủy bớt hoặc liên hệ hỗ trợ",
      );
    }
    try {
      await commitDeletion(user.uid, impactVersion, deps);
    } catch (e) {
      if (e instanceof ImpactChangedError) throw impactChanged(e.impact, now, deps);
      // Lần gọi song song đã chốt trước: đi tiếp phần dọn dẹp.
      if (!(e instanceof FirestoreError && e.code === "ALREADY_EXISTS")) {
        if (e instanceof HelperMissingError) console.error(`[delete-account] thiếu helper ${e.helper}`);
        else console.error("[delete-account] transaction chốt lỗi", e instanceof Error ? e.name : e);
        throw internal();
      }
    }
  }

  const outcome = await runCleanup(user.uid, start + CLEANUP_BUDGET_MS, deps);
  return outcome === "DONE"
    ? Response.json({ status: "DELETED" })
    : Response.json({ status: "CLEANUP_SCHEDULED" }, { status: 202 });
}

function impactChanged(impact: Awaited<ReturnType<typeof computeImpact>>, now: Date, deps: DeletionDeps): HttpError {
  return new HttpError(409, "IMPACT_CHANGED", "Có thay đổi từ lúc xem trước", {
    preview: toPreview(impact, now, deps.helpers),
  });
}

export async function handleDeleteAccount(req: Request, deps: DeletionDeps = defaultDeletionDeps()): Promise<Response> {
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
  try {
    const body = await readBody(req);
    switch (body.action) {
      case "preview":
        return await preview(req, deps);
      case "confirm":
        return await confirm(req, body, deps);
      default:
        throw invalid();
    }
  } catch (e) {
    return toErrorResponse(e);
  }
}
