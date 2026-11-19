// `upload`, `download`, `discard` của `group-media-url` (spec 090 T045; research R8, contract mục 3).
import { HttpError } from "../_shared/auth.ts";
import { invalid, requireId, requireUuid } from "../_shared/group-tx.ts";
import { type ActionHandler, getMembership, type GroupDeps, postPath } from "../_shared/groups.ts";
import {
  deletePrefix,
  GROUP_MEDIA_BUCKET,
  groupIdOfPath,
  MAX_POST_PHOTOS,
  postMediaPath,
  postMediaPrefix,
  validateImage,
} from "../_shared/media.ts";

/** Một lần `download` xin tối đa 40 path (một trang bảng tin). */
export const MAX_DOWNLOAD_PATHS = 40;
/** Signed URL tải xuống hết hạn sau 1 giờ. */
export const DOWNLOAD_EXPIRES_SECONDS = 3600;

const requireMember = async (deps: GroupDeps, groupId: string, uid: string) => {
  if (await getMembership(deps.firestore, groupId, uid) === null) {
    throw new HttpError(403, "NOT_MEMBER", "Bạn không phải thành viên nhóm này");
  }
};

// ---------- upload ----------

export const uploadUrls: ActionHandler = async ({ uid, body, deps }) => {
  const groupId = requireId(body, "groupId");
  const postId = requireUuid(body, "postId");
  const files = body.files;
  if (!Array.isArray(files) || files.length < 1 || files.length > MAX_POST_PHOTOS) {
    throw invalid(`files phải có từ 1 đến ${MAX_POST_PHOTOS} phần tử`);
  }
  const seen = new Set<number>();
  const paths = files.map((f: unknown) => {
    const file = (typeof f === "object" && f !== null ? f : {}) as Record<string, unknown>;
    const index = file.index;
    if (typeof index !== "number" || !Number.isInteger(index) || index < 0 || index >= MAX_POST_PHOTOS) {
      throw invalid(`index phải từ 0 đến ${MAX_POST_PHOTOS - 1}`);
    }
    if (seen.has(index)) throw invalid("index bị trùng");
    seen.add(index);
    return postMediaPath(groupId, postId, index, validateImage(file.contentType, file.size));
  });
  await requireMember(deps, groupId, uid);
  // Ghi đè (upsert) chỉ an toàn khi bài chưa có hoặc là bài của chính người gọi: postId lộ trên bảng tin.
  const post = await deps.firestore.get(postPath(groupId, postId));
  if (post && post.authorId !== uid) throw new HttpError(403, "FORBIDDEN", "Bạn không thể tải ảnh cho bài của người khác");
  const uploads = await Promise.all(paths.map((p) => deps.storage.createSignedUploadUrl(GROUP_MEDIA_BUCKET, p, true)));
  return Response.json({ uploads });
};

// ---------- download ----------

export const downloadUrls: ActionHandler = async ({ uid, body, deps }) => {
  const paths = body.paths;
  if (!Array.isArray(paths) || paths.length > MAX_DOWNLOAD_PATHS || paths.some((p) => typeof p !== "string")) {
    throw invalid(`paths phải là mảng tối đa ${MAX_DOWNLOAD_PATHS} chuỗi`);
  }
  // Nhóm suy từ phần đầu path; chỉ ký path của nhóm mà người gọi là thành viên (mỗi nhóm kiểm một lần).
  const memberOf = new Map<string, Promise<boolean>>();
  const allowed: string[] = [];
  for (const path of new Set(paths as string[])) {
    const groupId = groupIdOfPath(path);
    if (groupId === null) continue;
    if (!memberOf.has(groupId)) {
      memberOf.set(groupId, getMembership(deps.firestore, groupId, uid).then((r) => r !== null));
    }
    if (await memberOf.get(groupId)) allowed.push(path);
  }
  const urls = await deps.storage.createSignedUrls(GROUP_MEDIA_BUCKET, allowed, DOWNLOAD_EXPIRES_SECONDS);
  const expiresAt = new Date(deps.now().getTime() + DOWNLOAD_EXPIRES_SECONDS * 1000).toISOString();
  return Response.json({ urls, expiresAt });
};

// ---------- discard ----------

export const discardUploads: ActionHandler = async ({ uid, body, deps }) => {
  const groupId = requireId(body, "groupId");
  const postId = requireUuid(body, "postId");
  await requireMember(deps, groupId, uid);
  // Bài đã được tạo thì ảnh thuộc về bài, không xóa.
  if (await deps.firestore.get(postPath(groupId, postId))) return Response.json({ deleted: 0 });
  const deleted = await deletePrefix(deps.storage, GROUP_MEDIA_BUCKET, postMediaPrefix(groupId, postId));
  return Response.json({ deleted });
};
