// `create`, `update`, `delete` cho bài `POST` của `group-post` (spec 090 T046; data-model mục 5, research R9).
// EVENT, hide/unhide, attend/unattend và report do các task US4–US6 cài.
import { HttpError } from "../_shared/auth.ts";
import { createWrite, deleteWrite, updateWrite } from "../_shared/firestore.ts";
import { asDate, asNumber, inGroupTx, invalid, requireId, requireRequestId, requireUuid } from "../_shared/group-tx.ts";
import {
  type ActionHandler,
  getMembership,
  type GroupDeps,
  loadGroupForWrite,
  postPath,
  userSnapshot,
} from "../_shared/groups.ts";
import { deletePrefix, GROUP_MEDIA_BUCKET, MAX_POST_PHOTOS, postMediaPrefix } from "../_shared/media.ts";

export const MAX_CONTENT_LENGTH = 2000;

const ok = () => Response.json({ ok: true });
const notMember = () => new HttpError(403, "NOT_MEMBER", "Bạn không phải thành viên nhóm này");
const forbidden = () => new HttpError(403, "FORBIDDEN", "Bạn không có quyền với bài này");
const eventNotReady = () => new HttpError(501, "NOT_IMPLEMENTED", "Chưa hỗ trợ sự kiện");

// ---------- Kiểm tra tham số ----------

const len = (s: string) => Array.from(s).length;

function parseContent(v: unknown): string {
  const s = typeof v === "string" ? v.trim() : "";
  if (len(s) < 1 || len(s) > MAX_CONTENT_LENGTH) throw invalid(`Nội dung phải từ 1 đến ${MAX_CONTENT_LENGTH} ký tự`);
  return s;
}

/** `photoPaths`: mảng ≤ 4 chuỗi không trùng, mỗi path là `{groupId}/{postId}/{tên file}` (chỉ một cấp dưới tiền tố). */
function parsePhotoPaths(v: unknown, groupId: string, postId: string): string[] {
  if (!Array.isArray(v) || v.length > MAX_POST_PHOTOS) {
    throw invalid(`photoPaths phải là mảng tối đa ${MAX_POST_PHOTOS} ảnh`);
  }
  const prefix = postMediaPrefix(groupId, postId);
  const seen = new Set<string>();
  for (const p of v) {
    if (typeof p !== "string" || !p.startsWith(prefix)) throw invalid(`Path ảnh phải bắt đầu bằng ${prefix}`);
    const name = p.substring(prefix.length);
    if (name === "" || name.includes("/") || name === "." || name === "..") throw invalid("Path ảnh không hợp lệ");
    if (seen.has(p)) throw invalid("Path ảnh bị trùng");
    seen.add(p);
  }
  return v as string[];
}

/** Mỗi path phải có object thật trong `group-media`, nếu không `400 PHOTO_MISSING` (R9). */
async function assertPhotosExist(deps: GroupDeps, groupId: string, postId: string, paths: string[]): Promise<void> {
  if (paths.length === 0) return;
  const present = new Set(await deps.storage.list(GROUP_MEDIA_BUCKET, postMediaPrefix(groupId, postId)));
  const missing = paths.filter((p) => !present.has(p));
  if (missing.length > 0) throw new HttpError(400, "PHOTO_MISSING", "Có ảnh chưa được tải lên", { missing });
}

// ---------- Dạng JSON trả về ----------

/** `post` trong response: `id` + trường của bài; `Timestamp` thành chuỗi ISO 8601 (UTC), thiếu thì `null`. */
export function postJson(id: string, p: Record<string, unknown>) {
  const iso = (v: unknown) => asDate(v)?.toISOString() ?? null;
  return {
    id,
    groupId: p.groupId ?? "",
    authorId: p.authorId ?? "",
    authorSnapshot: p.authorSnapshot ?? { displayName: null, avatarPath: null },
    authorDeleted: p.authorDeleted === true,
    type: p.type ?? "POST",
    content: p.content ?? "",
    photoPaths: Array.isArray(p.photoPaths) ? p.photoPaths : [],
    eventTitle: p.eventTitle ?? null,
    eventAt: iso(p.eventAt),
    eventLocation: p.eventLocation ?? null,
    expectedCount: p.expectedCount ?? null,
    attendeeCount: asNumber(p.attendeeCount),
    status: p.status ?? "VISIBLE",
    hiddenBy: p.hiddenBy ?? null,
    editedAt: iso(p.editedAt),
    createdAt: iso(p.createdAt),
    updatedAt: iso(p.updatedAt),
  };
}

/** Chạy việc dọn ảnh sau commit; lỗi Storage chỉ ghi log (ảnh mồ côi chấp nhận được, R8). */
async function cleanQuietly(what: string, run: () => Promise<unknown>): Promise<void> {
  try {
    await run();
  } catch (e) {
    console.warn(`[group-post] dọn ảnh (${what}) thất bại`, e);
  }
}

// ---------- create ----------

export const createPost: ActionHandler = async ({ uid, body, deps }) => {
  const groupId = requireId(body, "groupId");
  const postId = requireRequestId(body);
  if (body.type !== "POST" && body.type !== "EVENT") throw invalid("type phải là POST hoặc EVENT");
  if (body.type === "EVENT") throw eventNotReady();
  const content = parseContent(body.content);
  const photoPaths = parsePhotoPaths(body.photoPaths ?? [], groupId, postId);

  const outcome = await inGroupTx(deps, groupId, async (tx, writes) => {
    await loadGroupForWrite(tx, groupId);
    if (await getMembership(tx, groupId, uid) === null) throw notMember();

    const existing = await tx.get(postPath(groupId, postId));
    if (existing) {
      // Gửi lại cùng requestId: trả bài cũ, không ghi gì.
      if (existing.authorId !== uid) {
        throw new HttpError(409, "REQUEST_ID_CONFLICT", "requestId đã được dùng cho bài khác");
      }
      return { created: false, post: existing };
    }

    await assertPhotosExist(deps, groupId, postId, photoPaths);
    const now = deps.now();
    const post = {
      groupId,
      authorId: uid,
      authorSnapshot: await userSnapshot(tx, uid),
      authorDeleted: false,
      type: "POST",
      content,
      photoPaths,
      eventTitle: null,
      eventAt: null,
      eventLocation: null,
      expectedCount: null,
      attendeeCount: 0,
      status: "VISIBLE",
      hiddenBy: null,
      hiddenByUid: null,
      editedAt: null,
      createdAt: now,
      updatedAt: now,
    };
    writes.push(createWrite(postPath(groupId, postId), post));
    return { created: true, post };
  });
  return Response.json({ post: postJson(postId, outcome.post) }, { status: outcome.created ? 201 : 200 });
};

// ---------- update ----------

export const updatePost: ActionHandler = async ({ uid, body, deps }) => {
  const groupId = requireId(body, "groupId");
  const postId = requireUuid(body, "postId");
  const content = body.content === undefined ? undefined : parseContent(body.content);
  const newPaths = body.photoPaths === undefined ? undefined : parsePhotoPaths(body.photoPaths, groupId, postId);

  let obsolete: string[] = [];
  const post = await inGroupTx(deps, groupId, async (tx, writes) => {
    await loadGroupForWrite(tx, groupId);
    if (await getMembership(tx, groupId, uid) === null) throw notMember();
    const current = await tx.get(postPath(groupId, postId));
    if (!current) throw new HttpError(404, "POST_NOT_FOUND", "Bài viết không còn tồn tại");
    // Chỉ người đăng được sửa (chủ nhóm cũng không sửa bài người khác, FR-013); bài đang ẩn người đăng vẫn sửa được.
    if (current.authorId !== uid) throw forbidden();
    if (current.type !== "POST") throw eventNotReady();

    const oldPaths = Array.isArray(current.photoPaths) ? current.photoPaths as string[] : [];
    const changes: Record<string, unknown> = {};
    if (content !== undefined) changes.content = content;
    if (newPaths !== undefined) {
      // Ảnh giữ lại đã được kiểm khi tạo; chỉ kiểm ảnh mới thêm.
      await assertPhotosExist(deps, groupId, postId, newPaths.filter((p) => !oldPaths.includes(p)));
      changes.photoPaths = newPaths;
      obsolete = oldPaths.filter((p) => !newPaths.includes(p));
    }
    const now = deps.now();
    changes.editedAt = now;
    changes.updatedAt = now;
    writes.push(updateWrite(postPath(groupId, postId), changes));
    return { ...current, ...changes };
  });

  if (obsolete.length > 0) await cleanQuietly("ảnh bị bỏ", () => deps.storage.remove(GROUP_MEDIA_BUCKET, obsolete));
  return Response.json({ post: postJson(postId, post) });
};

// ---------- delete ----------

export const deletePost: ActionHandler = async ({ uid, body, deps }) => {
  const groupId = requireId(body, "groupId");
  const postId = requireUuid(body, "postId");

  await inGroupTx(deps, groupId, async (tx, writes) => {
    await loadGroupForWrite(tx, groupId);
    if (await getMembership(tx, groupId, uid) === null) throw notMember();
    const current = await tx.get(postPath(groupId, postId));
    if (!current) throw new HttpError(404, "POST_NOT_FOUND", "Bài viết không còn tồn tại");
    if (current.authorId !== uid) throw forbidden();
    // Xóa cả `attendees` của sự kiện (nếu có) cùng lần commit với document bài.
    const attendees = await tx.query("attendees", [], { parent: postPath(groupId, postId) });
    for (const a of attendees) writes.push(deleteWrite(a.path));
    writes.push(deleteWrite(postPath(groupId, postId)));
  });

  await cleanQuietly(
    "xóa bài",
    () => deletePrefix(deps.storage, GROUP_MEDIA_BUCKET, postMediaPrefix(groupId, postId)),
  );
  return ok();
};
