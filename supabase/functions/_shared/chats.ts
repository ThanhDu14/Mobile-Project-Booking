// Phần server dùng chung của spec 091 (Chat): kiểu, đường dẫn, deps, khung xử lý request, đồng bộ chat nhóm
// (`syncGroupChat`, hook của 090), dọn chat (`purgeChat`) và ẩn danh khi xóa tài khoản (`anonymizeCommunityChats`).
// Contract: specs/091-chat/contracts/edge-functions-chat.md.
import { errorResponse, HttpError, requireUser, toErrorResponse } from "./auth.ts";
import { createKeyedSerializer, readJsonObject, runWithBackoff } from "./dropin-tx.ts";
import * as defaultFirestore from "./firestore.ts";
import {
  createWrite,
  deleteWrite,
  documentName,
  encodeFields,
  type FieldTransform,
  type FirestoreTransaction,
  updateWrite,
  type Write,
} from "./firestore.ts";
import { type DocReader, type GroupStorage, type MembershipChange, supabaseGroupStorage, userSnapshot } from "./groups.ts";
import { CHAT_MEDIA_BUCKET, deletePrefix } from "./media.ts";

export const chatPath = (chatId: string) => `chats/${chatId}`;
export const participantPath = (chatId: string, uid: string) => `chats/${chatId}/participants/${uid}`;
export const messagePath = (chatId: string, messageId: string) => `chats/${chatId}/messages/${messageId}`;
export const groupChatId = (groupId: string) => `group_${groupId}`;
/** `{uidNhỏ}_{uidLớn}` (research R3). */
export const directChatId = (a: string, b: string) => [a, b].sort().join("_");

export type ChatStatus = "ACTIVE" | "READ_ONLY" | "DELETED";

export interface ChatDoc extends Record<string, unknown> {
  type?: "GROUP" | "DIRECT";
  memberIds?: string[];
  groupId?: string | null;
  ownerId?: string | null;
  playerId?: string | null;
  status?: ChatStatus;
  lastMessage?: Record<string, unknown> | null;
}

// ---------- Deps ----------

export interface ChatNotification {
  type: "CHAT_MESSAGE";
  recipientUid: string;
  data: { chatId: string; messageId: string };
}

export interface ChatDeps {
  firestore: Pick<typeof defaultFirestore, "runTransaction" | "get" | "query" | "commit">;
  storage: GroupStorage;
  authenticate: (req: Request) => Promise<{ uid: string }>;
  /** Gửi thông báo sau commit; lỗi bị bỏ qua ở caller. */
  notify: (n: ChatNotification) => Promise<void>;
  now: () => Date;
  sleep?: (ms: number) => Promise<void>;
}

/** Gọi `send-notification` của D (spec 100) bằng service role. */
export async function sendChatNotification(n: ChatNotification): Promise<void> {
  const url = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !key) {
    console.warn("[chat] thiếu SUPABASE_URL/SUPABASE_SERVICE_ROLE_KEY, bỏ qua thông báo");
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

export function defaultChatDeps(): ChatDeps {
  return {
    firestore: defaultFirestore,
    storage: supabaseGroupStorage(),
    authenticate: (req) => requireUser(req),
    notify: sendChatNotification,
    now: () => new Date(),
  };
}

// ---------- Khung request và transaction ----------

export interface ChatContext {
  uid: string;
  body: Record<string, unknown>;
  deps: ChatDeps;
}
export type ChatHandler = (ctx: ChatContext) => Promise<Response>;

/** Chỉ POST, xác thực, đọc JSON, chạy handler; `HttpError` thành `{ error: { code, message } }`. */
export async function handleChat(req: Request, deps: ChatDeps, handler: ChatHandler): Promise<Response> {
  try {
    if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
    const { uid } = await deps.authenticate(req);
    return await handler({ uid, body: await readJsonObject(req), deps });
  } catch (e) {
    return toErrorResponse(e);
  }
}

/** Router theo `body.action` (action lạ → 400). */
export function routeActions(handlers: Record<string, ChatHandler>): ChatHandler {
  return (ctx) => {
    const h = typeof ctx.body.action === "string" ? handlers[ctx.body.action] : undefined;
    if (!h) throw new HttpError(400, "INVALID_ARGUMENT", `action phải là một trong: ${Object.keys(handlers).join(", ")}`);
    return h(ctx);
  };
}

const serializer = createKeyedSerializer();

/**
 * Transaction xếp hàng theo `chatId` trong một isolate (giảm tranh chấp, giống `inGroupTx`); thử lại khi ABORTED,
 * hết lượt thì 503 BUSY. `tx` chỉ đọc thật, mọi `write` gom lại commit một lần.
 */
export function inChatTx<T>(
  deps: Pick<ChatDeps, "firestore" | "sleep">,
  chatId: string,
  fn: (tx: FirestoreTransaction, writes: Write[]) => Promise<T>,
): Promise<T> {
  return serializer(chatId, () =>
    runWithBackoff(deps, async (raw) => {
      const writes: Write[] = [];
      const tx: FirestoreTransaction = {
        id: raw.id,
        get: (p) => raw.get(p),
        query: (c, f, o) => raw.query(c, f, o),
        write: (w) => void writes.push(w),
      };
      return { result: await fn(tx, writes), writes };
    }));
}

/** Đọc `chats/{chatId}`: không có hoặc `DELETED` → 404; người gọi không trong `memberIds` → 403. */
export async function loadChatForMember(tx: DocReader, chatId: string, uid: string): Promise<ChatDoc> {
  const chat = await tx.get(chatPath(chatId)) as ChatDoc | null;
  if (!chat || chat.status === "DELETED") throw new HttpError(404, "CHAT_NOT_FOUND", "Cuộc trò chuyện không còn");
  if (!Array.isArray(chat.memberIds) || !chat.memberIds.includes(uid)) {
    throw new HttpError(403, "NOT_PARTICIPANT", "Bạn không ở trong cuộc trò chuyện này");
  }
  return chat;
}

// ---------- Ghi một phần document (field path lồng nhau) ----------

const fp = (...segs: string[]) =>
  segs.map((s) => /^[A-Za-z_][A-Za-z0-9_]*$/.test(s) ? s : "`" + s.replace(/[\\`]/g, "\\$&") + "`").join(".");
export { fp as fieldPath };

/**
 * Sửa document đã có: `fields` là dữ liệu lồng nhau, `mask` là các field path được ghi (path không có trong `fields`
 * thì bị xóa), `transforms` là giờ server / cộng dồn.
 */
export function patchWrite(path: string, fields: Record<string, unknown>, mask: string[], transforms: FieldTransform[] = []): Write {
  return {
    update: { name: documentName(path), fields: encodeFields(fields) },
    updateMask: { fieldPaths: mask },
    ...(transforms.length ? { updateTransforms: transforms } : {}),
    currentDocument: { exists: true },
  };
}

export const requestTime = (fieldPath: string): FieldTransform => ({ fieldPath, setToServerValue: "REQUEST_TIME" });
export const incrementBy1 = (fieldPath: string): FieldTransform => ({ fieldPath, increment: { integerValue: "1" } });

// ---------- Hook đồng bộ chat nhóm (research R5) ----------

const participantData = (chatId: string, uid: string, snap: unknown) => ({
  userId: uid,
  chatId,
  userSnapshot: snap,
  muted: false,
  blockedOther: false,
  viewingUntil: null,
});

/** `participants/{uid}` mới: `joinedAt = lastReadAt = REQUEST_TIME` (tin cũ không tính là chưa đọc). */
export const newParticipantWrite = (chatId: string, uid: string, snap: unknown): Write =>
  createWrite(participantPath(chatId, uid), participantData(chatId, uid, snap), ["joinedAt", "lastReadAt"]);

/**
 * Hook `onGroupMembershipChanged` của 090 gọi trong CÙNG transaction. Chat chưa tồn tại (nhóm có từ trước 091) thì
 * `added`/`removed`/`deleted` bỏ qua, chỉ `created` tạo mới.
 */
export async function syncGroupChat(tx: FirestoreTransaction, groupId: string, change: MembershipChange): Promise<void> {
  const chatId = groupChatId(groupId);
  const added = change.added ?? [];
  if (change.created) {
    const snaps = await Promise.all(added.map((u) => userSnapshot(tx, u))); // đọc hết trước khi ghi
    const unreadCounts = Object.fromEntries(added.map((u) => [u, 0]));
    tx.write(createWrite(chatPath(chatId), {
      type: "GROUP",
      memberIds: added,
      groupId,
      ownerId: null,
      playerId: null,
      lastMessage: null,
      unreadCounts,
      status: "ACTIVE",
    }, ["createdAt", "updatedAt"]));
    added.forEach((uid, i) => tx.write(newParticipantWrite(chatId, uid, snaps[i])));
    return;
  }
  const chat = await tx.get(chatPath(chatId)) as ChatDoc | null;
  if (!chat || chat.status === "DELETED") return;
  const members = chat.memberIds ?? [];
  if (change.deleted) {
    tx.write(patchWrite(chatPath(chatId), { memberIds: [], status: "DELETED" }, ["memberIds", "status"]));
    return;
  }
  const removed = (change.removed ?? []).filter((u) => members.includes(u));
  const joined = added.filter((u) => !members.includes(u));
  if (removed.length === 0 && joined.length === 0) return;
  const snaps = await Promise.all(joined.map((u) => userSnapshot(tx, u)));
  const memberIds = [...members.filter((u) => !removed.includes(u)), ...joined];
  // Khóa unreadCounts của người rời có trong mask nhưng không có trong fields nên bị xóa.
  tx.write(patchWrite(
    chatPath(chatId),
    { memberIds, unreadCounts: Object.fromEntries(joined.map((u) => [u, 0])) },
    ["memberIds", ...[...joined, ...removed].map((u) => fp("unreadCounts", u))],
  ));
  for (const uid of removed) tx.write(deleteWrite(participantPath(chatId, uid), false));
  joined.forEach((uid, i) => tx.write(newParticipantWrite(chatId, uid, snaps[i])));
}

// ---------- purgeChat (090-T060, H2) ----------

const BATCH = 500;

async function drain(deps: Pick<ChatDeps, "firestore">, collectionId: string, parent: string): Promise<void> {
  for (;;) {
    const rows = await deps.firestore.query(collectionId, [], { parent, limit: BATCH });
    if (rows.length === 0) return;
    await deps.firestore.commit(rows.map((r) => deleteWrite(r.path, false)));
    if (rows.length < BATCH) return;
  }
}

/** Xóa `messages`, `participants` và ảnh của chat theo lô (idempotent); giữ document chat (`DELETED`). */
export async function purgeChat(
  chatId: string,
  deps: Pick<ChatDeps, "firestore" | "storage"> = defaultChatDeps(),
): Promise<void> {
  await drain(deps, "messages", chatPath(chatId));
  await drain(deps, "participants", chatPath(chatId));
  await deletePrefix(deps.storage, CHAT_MEDIA_BUCKET, chatId);
}

// ---------- anonymizeCommunityChats (A1, research R16) ----------

/**
 * Cho `delete-account` (A) gọi sau `anonymizeCommunityGroups`: chat riêng có `uid` thành `READ_ONLY` với
 * `memberSnapshots.{uid} = null`; mọi tin của `uid` có `senderSnapshot = null`, `senderDeleted = true`. Idempotent.
 */
export async function anonymizeCommunityChats(
  uid: string,
  deps: Pick<ChatDeps, "firestore" | "sleep"> = defaultChatDeps(),
): Promise<void> {
  const parts = await deps.firestore.query("participants", [{ field: "userId", op: "EQUAL", value: uid }], {
    allDescendants: true,
  });
  for (const chatId of new Set(parts.map((p) => p.path.split("/")[1]))) {
    await inChatTx(deps, chatId, async (tx, writes) => {
      const chat = await tx.get(chatPath(chatId));
      if (!chat || chat.type !== "DIRECT" || chat.status === "READ_ONLY") return;
      writes.push(patchWrite(
        chatPath(chatId),
        { status: "READ_ONLY", memberSnapshots: { [uid]: null } },
        ["status", fp("memberSnapshots", uid)],
      ));
    });
  }
  const messages = await deps.firestore.query("messages", [{ field: "senderId", op: "EQUAL", value: uid }], {
    allDescendants: true,
  });
  const todo = messages.filter((m) => m.data.senderDeleted !== true);
  for (let i = 0; i < todo.length; i += 400) {
    await deps.firestore.commit(
      todo.slice(i, i + 400).map((m) => updateWrite(m.path, { senderSnapshot: null, senderDeleted: true })),
    );
  }
}
