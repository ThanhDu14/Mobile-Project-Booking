// `create`, `update`, `delete` cho bài `POST` của `group-post` (spec 090 T046; data-model mục 5, research R9).
// Nhánh EVENT (T053, data-model mục 5–6) nằm chung ở đây; attend/unattend ở attendance.ts; hide/unhide, report do US5–US6.
import { HttpError } from "../_shared/auth.ts";
import { createWrite, deleteWrite, updateWrite } from "../_shared/firestore.ts";
import { asDate, asNumber, inGroupTx, invalid, requireId, requireRequestId, requireUuid } from "../_shared/group-tx.ts";
import {
  type ActionHandler,
  getMembership,
  type GroupDeps,
  groupPath,
  loadGroupForWrite,
  notifyGroupActivity,
  postPath,
  userSnapshot,
} from "../_shared/groups.ts";
import { deletePrefix, GROUP_MEDIA_BUCKET, MAX_POST_PHOTOS, postMediaPrefix } from "../_shared/media.ts";

export const MAX_CONTENT_LENGTH = 2000;

const ok = () => Response.json({ ok: true });
const notMember = () => new HttpError(403, "NOT_MEMBER", "Bạn không phải thành viên nhóm này");
const forbidden = () => new HttpError(403, "FORBIDDEN", "Bạn không có quyền với bài này");
export const MAX_EVENT_TITLE = 100;
export const MAX_EVENT_LOCATION = 200;
export const MIN_EXPECTED = 2;
export const MAX_EXPECTED = 100;

// ---------- Kiểm tra tham số ----------

const len = (s: string) => Array.from(s).length;

/** Nội dung: bài POST 1–2000 ký tự; mô tả sự kiện (`min = 0`) 0–2000. */
function parseContent(v: unknown, min = 1): string {
  const s = typeof v === "string" ? v.trim() : "";
  if (len(s) < min || len(s) > MAX_CONTENT_LENGTH) {
    throw invalid(`Nội dung phải từ ${min} đến ${MAX_CONTENT_LENGTH} ký tự`);
  }
  return s;
}

function parseText(v: unknown, name: string, max: number): string {
  const s = typeof v === "string" ? v.trim() : "";
  if (len(s) < 1 || len(s) > max) throw invalid(`${name} phải từ 1 đến ${max} ký tự`);
  return s;
}

/** `eventAt`: chuỗi ISO 8601 hợp lệ và sau giờ server `now`. */
function parseEventAt(v: unknown, now: Date): Date {
  const t = typeof v === "string" ? Date.parse(v) : NaN;
  if (Number.isNaN(t)) throw invalid("eventAt phải là thời gian ISO 8601");
  if (t <= now.getTime()) throw invalid("eventAt phải sau thời điểm hiện tại");
  return new Date(t);
}

function parseExpectedCount(v: unknown): number {
  if (typeof v !== "number" || !Number.isInteger(v) || v < MIN_EXPECTED || v > MAX_EXPECTED) {
    throw invalid(`expectedCount phải là số nguyên từ ${MIN_EXPECTED} đến ${MAX_EXPECTED}`);
  }
  return v;
}

const sameTime = (a: unknown, b: Date) => asDate(a)?.getTime() === b.getTime();

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
  const isEvent = body.type === "EVENT";
  // Mô tả sự kiện được để trống (vắng = rỗng); bài thường bắt buộc 1–2000.
  const content = isEvent && body.content === undefined ? "" : parseContent(body.content, isEvent ? 0 : 1);
  const photoPaths = parsePhotoPaths(body.photoPaths ?? [], groupId, postId);

  const event = isEvent
    ? {
      title: parseText(body.eventTitle, "eventTitle", MAX_EVENT_TITLE),
      at: parseEventAt(body.eventAt, deps.now()),
      location: parseText(body.eventLocation, "eventLocation", MAX_EVENT_LOCATION),
      expected: parseExpectedCount(body.expectedCount),
    }
    : null;

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
      type: event ? "EVENT" : "POST",
      content,
      photoPaths,
      eventTitle: event?.title ?? null,
      eventAt: event?.at ?? null,
      eventLocation: event?.location ?? null,
      expectedCount: event?.expected ?? null,
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
  if (outcome.created && event) {
    // EVENT_CREATED cho mọi thành viên hiện tại trừ người đăng, gửi sau commit (lỗi chỉ ghi log).
    const members = await deps.firestore.query("members", [], { parent: groupPath(groupId) });
    await notifyGroupActivity(deps, "EVENT_CREATED", members.map((m) => m.id).filter((id) => id !== uid), {
      groupId,
      postId,
    });
  }
  return Response.json({ post: postJson(postId, outcome.post) }, { status: outcome.created ? 201 : 200 });
};

// ---------- update ----------

export const updatePost: ActionHandler = async ({ uid, body, deps }) => {
  const groupId = requireId(body, "groupId");
  const postId = requireUuid(body, "postId");
  const newPaths = body.photoPaths === undefined ? undefined : parsePhotoPaths(body.photoPaths, groupId, postId);

  let obsolete: string[] = [];
  let toNotify: string[] = [];
  const post = await inGroupTx(deps, groupId, async (tx, writes) => {
    await loadGroupForWrite(tx, groupId);
    if (await getMembership(tx, groupId, uid) === null) throw notMember();
    const current = await tx.get(postPath(groupId, postId));
    if (!current) throw new HttpError(404, "POST_NOT_FOUND", "Bài viết không còn tồn tại");
    // Chỉ người đăng được sửa (chủ nhóm cũng không sửa bài người khác, FR-013); bài đang ẩn người đăng vẫn sửa được.
    if (current.authorId !== uid) throw forbidden();
    const isEvent = current.type === "EVENT";
    const now = deps.now();
    // Kiểm tra trường theo loại bài (cần `current.type`); lỗi ở đây ném trước khi ghi gì.
    const content = body.content === undefined ? undefined : parseContent(body.content, isEvent ? 0 : 1);

    const oldPaths = Array.isArray(current.photoPaths) ? current.photoPaths as string[] : [];
    const changes: Record<string, unknown> = {};
    if (content !== undefined) changes.content = content;
    if (newPaths !== undefined) {
      // Ảnh giữ lại đã được kiểm khi tạo; chỉ kiểm ảnh mới thêm.
      await assertPhotosExist(deps, groupId, postId, newPaths.filter((p) => !oldPaths.includes(p)));
      changes.photoPaths = newPaths;
      obsolete = oldPaths.filter((p) => !newPaths.includes(p));
    }
    if (isEvent) {
      if (body.eventTitle !== undefined) changes.eventTitle = parseText(body.eventTitle, "eventTitle", MAX_EVENT_TITLE);
      if (body.expectedCount !== undefined) changes.expectedCount = parseExpectedCount(body.expectedCount);
      // Đổi giờ/địa điểm (giá trị khác cũ) thì báo người tham gia; giờ mới cũng phải sau `now`.
      let notable = false;
      if (body.eventLocation !== undefined) {
        const location = parseText(body.eventLocation, "eventLocation", MAX_EVENT_LOCATION);
        changes.eventLocation = location;
        if (location !== current.eventLocation) notable = true;
      }
      const attendees = await tx.query("attendees", [], { parent: postPath(groupId, postId) });
      if (body.eventAt !== undefined) {
        const at = parseEventAt(body.eventAt, now);
        changes.eventAt = at;
        if (!sameTime(current.eventAt, at)) {
          notable = true;
          // Cùng transaction: mọi `attendees.eventAt` theo giờ mới (truy vấn "sự kiện sắp tới của tôi" dựa vào trường này).
          for (const a of attendees) writes.push(updateWrite(a.path, { eventAt: at }));
        }
      }
      if (notable) toNotify = attendees.map((a) => a.id).filter((id) => id !== uid);
    }
    changes.editedAt = now;
    changes.updatedAt = now;
    writes.push(updateWrite(postPath(groupId, postId), changes));
    return { ...current, ...changes };
  });
  await notifyGroupActivity(deps, "EVENT_UPDATED", toNotify, { groupId, postId });

  if (obsolete.length > 0) await cleanQuietly("ảnh bị bỏ", () => deps.storage.remove(GROUP_MEDIA_BUCKET, obsolete));
  return Response.json({ post: postJson(postId, post) });
};

// ---------- delete ----------

export const deletePost: ActionHandler = async ({ uid, body, deps }) => {
  const groupId = requireId(body, "groupId");
  const postId = requireUuid(body, "postId");

  let toNotify: string[] = [];
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
    // Chỉ báo khi là sự kiện chưa diễn ra; danh sách lấy trước khi xóa.
    const at = asDate(current.eventAt);
    if (current.type === "EVENT" && at && at.getTime() > deps.now().getTime()) {
      toNotify = attendees.map((a) => a.id).filter((id) => id !== uid);
    }
  });
  await notifyGroupActivity(deps, "EVENT_DELETED", toNotify, { groupId, postId });

  await cleanQuietly(
    "xóa bài",
    () => deletePrefix(deps.storage, GROUP_MEDIA_BUCKET, postMediaPrefix(groupId, postId)),
  );
  return ok();
};
