// `hide`, `unhide`, `report` của `group-post` (spec 090 T066; data-model mục 5 và 7, research R7, R14).
// Cả ba đều là thao tác ghi nên đọc nhóm bằng `loadGroupForWrite` (nhóm `LOCKED` → `423 GROUP_LOCKED`, R17).
import { HttpError } from "../_shared/auth.ts";
import { createWrite, FirestoreError, updateWrite } from "../_shared/firestore.ts";
import { inGroupTx, invalid, requireId, requireUuid } from "../_shared/group-tx.ts";
import { type ActionHandler, getMembership, loadGroupForWrite, postPath } from "../_shared/groups.ts";

const ok = () => Response.json({ ok: true });

/** Lý do báo cáo dùng chung với spec 070/091 (R14). */
export const REPORT_REASONS = ["SPAM", "OFFENSIVE", "SCAM", "INAPPROPRIATE", "OTHER"] as const;
export const MAX_REPORT_DETAIL = 500;

/** ID báo cáo tất định: mỗi người báo cáo một bài đúng một lần. */
export const reportId = (groupId: string, postId: string, uid: string) => `POST_${groupId}_${postId}_${uid}`;

const postNotFound = () => new HttpError(404, "POST_NOT_FOUND", "Bài viết không còn tồn tại");

/** `hide`/`unhide`: chỉ OWNER/ADMIN (thành viên thường `FORBIDDEN`, người ngoài `NOT_MEMBER`). */
function moderation(hide: boolean): ActionHandler {
  return async ({ uid, body, deps }) => {
    const groupId = requireId(body, "groupId");
    const postId = requireUuid(body, "postId");

    await inGroupTx(deps, groupId, async (tx, writes) => {
      await loadGroupForWrite(tx, groupId);
      const role = await getMembership(tx, groupId, uid);
      if (!role) throw new HttpError(403, "NOT_MEMBER", "Bạn không phải thành viên nhóm này");
      if (role === "MEMBER") {
        throw new HttpError(403, "FORBIDDEN", "Chỉ chủ nhóm hoặc quản trị viên được ẩn/hiện bài");
      }
      const post = await tx.get(postPath(groupId, postId));
      if (!post) throw postNotFound();

      if (hide) {
        // Đã ẩn (bởi nhóm hay hệ thống): giữ nguyên hiện trạng, không ghi đè người ẩn.
        if (post.status === "HIDDEN") return;
        writes.push(updateWrite(postPath(groupId, postId), {
          status: "HIDDEN",
          hiddenBy: "GROUP",
          hiddenByUid: uid,
          updatedAt: deps.now(),
        }));
        return;
      }
      // Bài admin hệ thống ẩn (R7) thì nhóm không gỡ được.
      if (post.hiddenBy === "SYSTEM") {
        throw new HttpError(403, "HIDDEN_BY_SYSTEM", "Bài đã bị quản trị hệ thống ẩn");
      }
      if (post.status !== "HIDDEN") return; // đã hiện: trả hiện trạng
      writes.push(updateWrite(postPath(groupId, postId), {
        status: "VISIBLE",
        hiddenBy: null,
        hiddenByUid: null,
        updatedAt: deps.now(),
      }));
    });
    return ok();
  };
}

export const hidePost = moderation(true);
export const unhidePost = moderation(false);

/** `detail`: chuỗi ≤ 500 ký tự (sau trim); bắt buộc không rỗng khi `reason = OTHER`; vắng/rỗng → `null`. */
function parseDetail(v: unknown, reason: string): string | null {
  if (v !== undefined && v !== null && typeof v !== "string") throw invalid("detail phải là chuỗi");
  const s = typeof v === "string" ? v.trim() : "";
  if (Array.from(s).length > MAX_REPORT_DETAIL) throw invalid(`detail tối đa ${MAX_REPORT_DETAIL} ký tự`);
  if (reason === "OTHER" && s === "") throw invalid("Cần mô tả lý do khi chọn Khác");
  return s === "" ? null : s;
}

const alreadyReported = () => new HttpError(409, "ALREADY_REPORTED", "Bạn đã báo cáo bài này");

export const reportPost: ActionHandler = async ({ uid, body, deps }) => {
  const groupId = requireId(body, "groupId");
  const postId = requireUuid(body, "postId");
  const reason = body.reason;
  if (typeof reason !== "string" || !(REPORT_REASONS as readonly string[]).includes(reason)) {
    throw invalid(`reason phải là một trong: ${REPORT_REASONS.join(", ")}`);
  }
  const detail = parseDetail(body.detail, reason);
  const id = reportId(groupId, postId, uid);

  try {
    await inGroupTx(deps, groupId, async (tx, writes) => {
      await loadGroupForWrite(tx, groupId);
      if (await getMembership(tx, groupId, uid) === null) {
        throw new HttpError(403, "NOT_MEMBER", "Bạn không phải thành viên nhóm này");
      }
      const post = await tx.get(postPath(groupId, postId));
      // Bài đang ẩn người thường không thấy nên coi như không tồn tại.
      if (!post || post.status !== "VISIBLE") throw postNotFound();
      if (post.authorId === uid) throw new HttpError(403, "OWN_POST", "Bạn không thể báo cáo bài của chính mình");
      if (await tx.get(`reports/${id}`)) throw alreadyReported();
      // `createWrite` có precondition "chưa tồn tại": chốt chặn cuối khi hai request đua nhau.
      writes.push(createWrite(`reports/${id}`, {
        reporterId: uid,
        targetType: "POST",
        targetPath: postPath(groupId, postId),
        reason,
        detail,
        status: "OPEN",
        action: "NONE",
        createdAt: deps.now(),
      }));
    });
  } catch (e) {
    if (e instanceof FirestoreError && e.code === "ALREADY_EXISTS") throw alreadyReported();
    throw e;
  }
  return Response.json({ reportId: id }, { status: 201 });
};
