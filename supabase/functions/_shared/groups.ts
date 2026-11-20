// Phần server dùng chung của spec 090 (Nhóm/CLB): kiểu dữ liệu, kiểm tra quyền, hook thành viên cho spec 091,
// storage, thông báo và khung điều phối action của 3 function `group-manage`, `group-post`, `group-media-url`
// (contracts/edge-functions-community.md mục 4).
import { errorResponse, HttpError, requireUser, toErrorResponse } from "./auth.ts";
import * as defaultFirestore from "./firestore.ts";
import type { FirestoreTransaction } from "./firestore.ts";
import { readJsonObject } from "./dropin-tx.ts";

// ---------- Kiểu dữ liệu và đường dẫn (data-model.md mục 1–6) ----------

export type GroupRole = "OWNER" | "ADMIN" | "MEMBER";
export type GroupStatus = "ACTIVE" | "LOCKED" | "DELETED";
export type GroupVisibility = "PUBLIC" | "PRIVATE";

export interface UserSnapshot {
  displayName: string | null;
  avatarPath: string | null;
}

export interface GroupDoc extends Record<string, unknown> {
  name?: string;
  nameKeywords?: string[];
  description?: string;
  avatarPath?: string | null;
  districtId?: string;
  ownerId?: string;
  visibility?: GroupVisibility;
  memberCount?: number;
  pendingRequestCount?: number;
  status?: GroupStatus;
}

export const groupPath = (groupId: string) => `groups/${groupId}`;
export const memberPath = (groupId: string, uid: string) => `groups/${groupId}/members/${uid}`;
export const joinRequestPath = (groupId: string, uid: string) => `groups/${groupId}/joinRequests/${uid}`;
export const removalPath = (groupId: string, uid: string) => `groups/${groupId}/removals/${uid}`;
export const postPath = (groupId: string, postId: string) => `groups/${groupId}/posts/${postId}`;
export const attendeePath = (groupId: string, postId: string, uid: string) =>
  `groups/${groupId}/posts/${postId}/attendees/${uid}`;

/** Đối tượng đọc được document: transaction hoặc chính module `firestore.ts`. */
export interface DocReader {
  get(path: string): Promise<Record<string, unknown> | null>;
}

// ---------- Kiểm tra quyền và nạp nhóm ----------

/** Vai trò của `uid` trong nhóm (đọc `members/{uid}`); không phải thành viên thì `null`. */
export async function getMembership(tx: DocReader, groupId: string, uid: string): Promise<GroupRole | null> {
  const member = await tx.get(memberPath(groupId, uid));
  const role = member?.role;
  return role === "OWNER" || role === "ADMIN" || role === "MEMBER" ? role : null;
}

/**
 * Đọc `groups/{groupId}` cho thao tác ghi. Không có hoặc `DELETED` → `404 GROUP_NOT_FOUND`; `LOCKED` mà
 * `allowLocked` không bật (chỉ `leave` bật) → `423 GROUP_LOCKED`.
 */
export async function loadGroupForWrite(
  tx: DocReader,
  groupId: string,
  opts: { allowLocked?: boolean } = {},
): Promise<GroupDoc> {
  const group = await tx.get(groupPath(groupId)) as GroupDoc | null;
  if (!group || group.status === "DELETED") throw new HttpError(404, "GROUP_NOT_FOUND", "Nhóm không còn tồn tại");
  if (group.status === "LOCKED" && !opts.allowLocked) {
    throw new HttpError(423, "GROUP_LOCKED", "Nhóm đã bị tạm khóa");
  }
  return group;
}

/** Ảnh chụp tên và ảnh đại diện từ `users/{uid}` để chép vào thành viên/yêu cầu/bài; thiếu dữ liệu thì `null`. */
export async function userSnapshot(reader: DocReader, uid: string): Promise<UserSnapshot> {
  const user = await reader.get(`users/${uid}`);
  return {
    displayName: typeof user?.displayName === "string" ? user.displayName : null,
    avatarPath: typeof user?.avatarPath === "string" ? user.avatarPath : null,
  };
}

// ---------- Hook cho spec 091 và A ----------

export interface MembershipChange {
  added?: string[];
  removed?: string[];
  created?: boolean;
  deleted?: boolean;
}

/**
 * Hook đồng bộ thành viên chat nhóm (research R10, spec 091).
 *
 * PHẢI được gọi trong CÙNG transaction với thao tác đổi danh sách thành viên (`create`, `join` khi `JOINED`,
 * `approve`, `update` có tự duyệt, `leave`, `remove`, `delete`), để `chats/{chatId}.memberIds` không lệch với
 * `members`. Spec 090 để thân trống; spec 091 cài (cập nhật `memberIds`, tạo chat khi `created`, khóa/xóa chat
 * khi `deleted`) bằng `tx.get`/`tx.write`.
 */
export function onGroupMembershipChanged(
  _tx: FirestoreTransaction,
  _groupId: string,
  _change: MembershipChange,
): Promise<void> {
  return Promise.resolve();
}

// `anonymizeCommunityGroups(uid)` cho `delete-account` (A) gọi (T069, research R16) nằm ở `group-anonymize.ts`.
export { anonymizeCommunityGroups, type AnonymizeResult } from "./group-anonymize.ts";

// ---------- Storage (Supabase Storage, 2 bucket: group-media, group-avatars) ----------

export type GroupBucket = "group-media" | "group-avatars";

export interface SignedUpload {
  path: string;
  signedUrl: string;
  token: string;
}

export interface GroupStorage {
  exists(bucket: string, path: string): Promise<boolean>;
  /** Các file (không tính thư mục con) ngay dưới thư mục `prefix`, trả path đầy đủ. */
  list(bucket: string, prefix: string): Promise<string[]>;
  /** Trả số object đã xóa. */
  remove(bucket: string, paths: string[]): Promise<number>;
  /** `upsert` cho phép ghi đè object cũ cùng path (ảnh đại diện, ảnh bài của chính tác giả); mặc định không. */
  createSignedUploadUrl(bucket: string, path: string, upsert?: boolean): Promise<SignedUpload>;
  /** Path không có trong kết quả nghĩa là object không tồn tại hoặc ký lỗi. */
  createSignedUrls(bucket: string, paths: string[], expiresInSeconds: number): Promise<Record<string, string>>;
}

/** Supabase Storage REST bằng service role (bỏ qua RLS). */
export function supabaseGroupStorage(
  url = Deno.env.get("SUPABASE_URL") ?? "",
  key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? "",
): GroupStorage {
  const headers = { Authorization: `Bearer ${key}`, apikey: key, "Content-Type": "application/json" };
  const root = `${url.replace(/\/+$/, "")}/storage/v1`;
  const base = `${root}/object`;
  const absolute = (rel: string) => rel.startsWith("http") ? rel : `${root}${rel}`;

  async function listFolder(bucket: string, folder: string): Promise<string[]> {
    const res = await fetch(`${base}/list/${bucket}`, {
      method: "POST",
      headers,
      body: JSON.stringify({ prefix: folder, limit: 1000, offset: 0 }),
    });
    if (!res.ok) throw new Error(`storage list ${res.status}: ${await res.text()}`);
    const rows = await res.json() as { name: string; id: string | null }[];
    // `id == null` là thư mục con; chỉ lấy file.
    return rows.filter((r) => r.id !== null).map((r) => `${folder}/${r.name}`);
  }

  return {
    async exists(bucket, path) {
      return (await listFolder(bucket, path.substring(0, path.lastIndexOf("/")))).includes(path);
    },
    list: (bucket, prefix) => listFolder(bucket, prefix.replace(/\/+$/, "")),
    async remove(bucket, paths) {
      if (paths.length === 0) return 0;
      const res = await fetch(`${base}/${bucket}`, {
        method: "DELETE",
        headers,
        body: JSON.stringify({ prefixes: paths }),
      });
      if (!res.ok) throw new Error(`storage remove ${res.status}: ${await res.text()}`);
      return (await res.json() as unknown[]).length;
    },
    async createSignedUploadUrl(bucket, path, upsert = false) {
      const res = await fetch(`${base}/upload/sign/${bucket}/${path}`, {
        method: "POST",
        headers: upsert ? { ...headers, "x-upsert": "true" } : headers,
        body: "{}",
      });
      if (!res.ok) throw new Error(`storage upload sign ${res.status}: ${await res.text()}`);
      const { url: rel } = await res.json() as { url: string };
      const signedUrl = absolute(rel);
      return { path, signedUrl, token: new URL(signedUrl).searchParams.get("token") ?? "" };
    },
    async createSignedUrls(bucket, paths, expiresInSeconds) {
      if (paths.length === 0) return {};
      const res = await fetch(`${base}/sign/${bucket}`, {
        method: "POST",
        headers,
        body: JSON.stringify({ expiresIn: expiresInSeconds, paths }),
      });
      if (!res.ok) throw new Error(`storage sign ${res.status}: ${await res.text()}`);
      const rows = await res.json() as { path: string; signedURL: string | null }[];
      const out: Record<string, string> = {};
      for (const r of rows) if (r.signedURL) out[r.path] = absolute(r.signedURL);
      return out;
    },
  };
}

// ---------- Thông báo (research R15) ----------

export type GroupEvent =
  | "JOIN_REQUESTED"
  | "JOIN_APPROVED"
  | "JOIN_REJECTED"
  | "REMOVED"
  | "EVENT_CREATED"
  | "EVENT_UPDATED"
  | "EVENT_DELETED";

/** Cùng envelope `{ type, recipientUid, data }` với `review` (TODO(D, spec 100): chưa chốt). */
export interface GroupNotification {
  type: "GROUP_ACTIVITY";
  recipientUid: string;
  data: { event: GroupEvent; groupId: string; postId?: string };
}

/** Gọi Edge Function `send-notification` của D bằng service role; chưa có function thì ném lỗi (bị bỏ qua ở caller). */
export async function sendGroupNotification(n: GroupNotification): Promise<void> {
  const url = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !key) {
    console.warn("[group] thiếu SUPABASE_URL/SUPABASE_SERVICE_ROLE_KEY, bỏ qua thông báo", n.data.event);
    return;
  }
  const res = await fetch(`${url}/functions/v1/send-notification`, {
    method: "POST",
    headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
    body: JSON.stringify(n),
  });
  await res.body?.cancel();
  if (!res.ok) throw new Error(`send-notification ${res.status}`);
}

/**
 * Gửi `GROUP_ACTIVITY` cho từng người nhận SAU KHI commit; mỗi người một lời gọi, lỗi chỉ `console.warn`
 * (không hoàn tác thao tác). Người nhận trùng chỉ nhận một lần.
 */
export async function notifyGroupActivity(
  deps: Pick<GroupDeps, "notify">,
  event: GroupEvent,
  recipients: Iterable<string>,
  data: { groupId: string; postId?: string },
): Promise<void> {
  await Promise.all([...new Set(recipients)].map(async (recipientUid) => {
    try {
      await deps.notify({ type: "GROUP_ACTIVITY", recipientUid, data: { event, ...data } });
    } catch (e) {
      console.warn(`[group] gửi GROUP_ACTIVITY/${event} cho ${recipientUid} thất bại`, e);
    }
  }));
}

// ---------- Deps và khung điều phối ----------

export interface GroupDeps {
  firestore: Pick<typeof defaultFirestore, "runTransaction" | "get" | "query" | "commit">;
  storage: GroupStorage;
  /** Xác thực người gọi; mặc định `requireUser` (401, 403 ACCOUNT_LOCKED, ...). */
  authenticate: (req: Request) => Promise<{ uid: string }>;
  /** Gửi một thông báo sau khi commit (gọi qua `notifyGroupActivity`, lỗi chỉ ghi log). */
  notify: (n: GroupNotification) => Promise<void>;
  /** Hook thành viên; mặc định là bản rỗng của spec 090, test thay bằng bản ghi lại. */
  onMembershipChanged: typeof onGroupMembershipChanged;
  now: () => Date;
  /** Chờ giữa các lần thử lại transaction (`runWithBackoff`); test thay để chạy nhanh. */
  sleep?: (ms: number) => Promise<void>;
}

export function defaultGroupDeps(): GroupDeps {
  return {
    firestore: defaultFirestore,
    storage: supabaseGroupStorage(),
    authenticate: (req) => requireUser(req),
    notify: sendGroupNotification,
    onMembershipChanged: onGroupMembershipChanged,
    now: () => new Date(),
  };
}

export interface ActionContext {
  uid: string;
  body: Record<string, unknown>;
  deps: GroupDeps;
}

/** Trả `Response` (200/201...) hoặc ném `HttpError`. */
export type ActionHandler = (ctx: ActionContext) => Promise<Response>;

/** Action đã định nghĩa trong contract nhưng chưa cài. */
export const notImplemented: ActionHandler = () =>
  Promise.resolve(errorResponse(501, "NOT_IMPLEMENTED", "Chưa cài đặt"));

/**
 * Khung chung của 3 function: chỉ POST, xác thực (gồm kiểm `accountStatus`), đọc JSON, parse `action` theo
 * `actions` (lạ → 400 INVALID_ARGUMENT), chạy handler tương ứng (mặc định 501). Lỗi thành
 * `{ error: { code, message, ...extra } }` (`HttpError.extra` đã có sẵn trong auth.ts).
 */
export async function dispatchGroupAction(
  req: Request,
  deps: GroupDeps,
  actions: readonly string[],
  handlers: Record<string, ActionHandler>,
): Promise<Response> {
  try {
    if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
    const { uid } = await deps.authenticate(req);
    const body = await readJsonObject(req);
    const action = body.action;
    if (typeof action !== "string" || !actions.includes(action)) {
      throw new HttpError(400, "INVALID_ARGUMENT", `action phải là một trong: ${actions.join(", ")}`);
    }
    return await (handlers[action] ?? notImplemented)({ uid, body, deps });
  } catch (e) {
    return toErrorResponse(e);
  }
}
