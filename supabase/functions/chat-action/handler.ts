// Edge Function `chat-action` (spec 091, contracts/edge-functions-chat.md mục 2): openDirect, recall, report.
import { HttpError } from "../_shared/auth.ts";
import {
  type ChatContext,
  type ChatDeps,
  chatPath,
  defaultChatDeps,
  directChatId,
  handleChat,
  inChatTx,
  loadChatForMember,
  messagePath,
  newParticipantWrite,
  patchWrite,
  requestTime,
  routeActions,
} from "../_shared/chats.ts";
import { createWrite, FirestoreError } from "../_shared/firestore.ts";
import { invalid, requireId, requireUuid } from "../_shared/group-tx.ts";
import { userSnapshot } from "../_shared/groups.ts";
import { CHAT_MEDIA_BUCKET } from "../_shared/media.ts";

export const RECALL_WINDOW_MS = 10 * 60_000;
export const RECALLED_PREVIEW = "Tin nhắn đã được thu hồi";
export const REPORT_REASONS = ["SPAM", "OFFENSIVE", "SCAM", "INAPPROPRIATE", "OTHER"] as const;
export const MAX_REPORT_DETAIL = 500;

// ---------- openDirect (research R3, R4) ----------

async function openDirect({ uid, body, deps }: ChatContext): Promise<Response> {
  const venueId = requireId(body, "venueId");
  const venue = await deps.firestore.get(`venues/${venueId}`);
  const ownerId = typeof venue?.ownerId === "string" ? venue.ownerId : null;
  if (!venue || !ownerId) throw new HttpError(409, "VENUE_INACTIVE", "Không thể nhắn tin cho sân này lúc này");
  if (ownerId === uid) throw new HttpError(403, "OWN_VENUE", "Bạn không thể nhắn tin cho chính mình");
  const chatId = directChatId(uid, ownerId);

  // Hai bên tạo cùng lúc: bên thua có thể gặp ALREADY_EXISTS, thử lại sẽ thấy chat đã có.
  for (let attempt = 1;; attempt++) {
    try {
      const created = await inChatTx(deps, chatId, async (tx, writes) => {
        const v = await tx.get(`venues/${venueId}`);
        const photos = v?.photoPaths;
        const venueSnapshot = { name: v?.name ?? null, photoPath: Array.isArray(photos) ? photos[0] ?? null : null };
        const chat = await tx.get(chatPath(chatId));
        if (chat) {
          if (chat.status === "DELETED") throw new HttpError(404, "CHAT_NOT_FOUND", "Cuộc trò chuyện không còn");
          writes.push(patchWrite(chatPath(chatId), { venueId, venueSnapshot }, ["venueId", "venueSnapshot"]));
          return false;
        }
        if (!v || v.ownerId !== ownerId || v.status !== "ACTIVE") {
          throw new HttpError(409, "VENUE_INACTIVE", "Không thể nhắn tin cho sân này lúc này");
        }
        const owner = await tx.get(`users/${ownerId}`);
        if (owner?.ownerStatus !== "APPROVED") {
          throw new HttpError(409, "OWNER_NOT_APPROVED", "Không thể nhắn tin cho sân này lúc này");
        }
        const mine = await userSnapshot(tx, uid);
        const theirs = await userSnapshot(tx, ownerId);
        writes.push(createWrite(chatPath(chatId), {
          type: "DIRECT",
          memberIds: [uid, ownerId].sort(),
          groupId: null,
          ownerId,
          playerId: uid,
          venueId,
          venueSnapshot,
          memberSnapshots: { [uid]: mine, [ownerId]: theirs },
          lastMessage: null,
          unreadCounts: { [uid]: 0, [ownerId]: 0 },
          status: "ACTIVE",
        }, ["createdAt", "updatedAt"]));
        writes.push(newParticipantWrite(chatId, uid, mine), newParticipantWrite(chatId, ownerId, theirs));
        return true;
      });
      return Response.json({ chatId, created }, { status: 200 });
    } catch (e) {
      if (e instanceof FirestoreError && e.code === "ALREADY_EXISTS" && attempt < 3) continue;
      throw e;
    }
  }
}

// ---------- recall (research R9) ----------

async function recall({ uid, body, deps }: ChatContext): Promise<Response> {
  const chatId = requireId(body, "chatId");
  const messageId = requireUuid(body, "messageId");
  const imagePath = await inChatTx(deps, chatId, async (tx, writes) => {
    const chat = await loadChatForMember(tx, chatId, uid);
    const msg = await tx.get(messagePath(chatId, messageId));
    if (!msg) throw new HttpError(404, "MESSAGE_NOT_FOUND", "Tin nhắn không còn");
    if (msg.senderId !== uid) throw new HttpError(403, "FORBIDDEN", "Chỉ người gửi được thu hồi tin");
    if (msg.status === "RECALLED") return null; // đã thu hồi: lặp lại vẫn thành công
    if (msg.status !== "NORMAL") throw invalid("Tin nhắn không thu hồi được");
    const createdAt = msg.createdAt instanceof Date ? msg.createdAt.getTime() : 0;
    if (deps.now().getTime() >= createdAt + RECALL_WINDOW_MS) {
      throw new HttpError(409, "RECALL_WINDOW_PASSED", "Chỉ thu hồi được trong 10 phút");
    }
    writes.push(patchWrite(
      messagePath(chatId, messageId),
      { status: "RECALLED", text: "", imagePath: null },
      ["status", "text", "imagePath"],
      [requestTime("recalledAt")],
    ));
    if ((chat.lastMessage as { messageId?: string } | null | undefined)?.messageId === messageId) {
      writes.push(patchWrite(
        chatPath(chatId),
        { lastMessage: { type: "TEXT", preview: RECALLED_PREVIEW } },
        ["lastMessage.type", "lastMessage.preview"],
      ));
    }
    return typeof msg.imagePath === "string" ? msg.imagePath : null;
  });
  if (imagePath) await deps.storage.remove(CHAT_MEDIA_BUCKET, [imagePath]);
  return Response.json({ recalled: true });
}

// ---------- report (research R12) ----------

export const reportId = (chatId: string, messageId: string, uid: string) => `MESSAGE_${chatId}_${messageId}_${uid}`;
const alreadyReported = () => new HttpError(409, "ALREADY_REPORTED", "Bạn đã báo cáo tin này");

async function report({ uid, body, deps }: ChatContext): Promise<Response> {
  const chatId = requireId(body, "chatId");
  const messageId = requireUuid(body, "messageId");
  const reason = body.reason;
  if (typeof reason !== "string" || !(REPORT_REASONS as readonly string[]).includes(reason)) {
    throw invalid(`reason phải là một trong: ${REPORT_REASONS.join(", ")}`);
  }
  if (body.detail !== undefined && body.detail !== null && typeof body.detail !== "string") {
    throw invalid("detail phải là chuỗi");
  }
  const detail = typeof body.detail === "string" ? body.detail.trim() : "";
  if (Array.from(detail).length > MAX_REPORT_DETAIL) throw invalid(`detail tối đa ${MAX_REPORT_DETAIL} ký tự`);
  if (reason === "OTHER" && detail === "") throw invalid("Cần mô tả lý do khi chọn Khác");
  const id = reportId(chatId, messageId, uid);

  try {
    await inChatTx(deps, chatId, async (tx, writes) => {
      await loadChatForMember(tx, chatId, uid);
      const msg = await tx.get(messagePath(chatId, messageId));
      if (!msg) throw new HttpError(404, "MESSAGE_NOT_FOUND", "Tin nhắn không còn");
      if (msg.senderId === uid) throw new HttpError(403, "OWN_MESSAGE", "Bạn không thể báo cáo tin của mình");
      if (msg.status !== "NORMAL") throw invalid("Tin nhắn không báo cáo được");
      if (await tx.get(`reports/${id}`)) throw alreadyReported();
      // createWrite có precondition "chưa tồn tại": chốt chặn cuối khi hai request đua nhau.
      writes.push(createWrite(`reports/${id}`, {
        reporterId: uid,
        targetType: "MESSAGE",
        targetPath: messagePath(chatId, messageId),
        reason,
        detail: detail === "" ? null : detail,
        status: "OPEN",
        action: "NONE",
      }, ["createdAt"]));
    });
  } catch (e) {
    if (e instanceof FirestoreError && e.code === "ALREADY_EXISTS") throw alreadyReported();
    throw e;
  }
  return Response.json({ reportId: id }, { status: 201 });
}

export const ACTIONS = ["openDirect", "recall", "report"] as const;

export function handleChatAction(req: Request, deps: ChatDeps = defaultChatDeps()): Promise<Response> {
  return handleChat(req, deps, routeActions({ openDirect, recall, report }));
}
