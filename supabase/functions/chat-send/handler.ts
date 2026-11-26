// Edge Function `chat-send` (spec 091, contracts/edge-functions-chat.md mục 1; research R1, R7, R11, R13).
import { HttpError } from "../_shared/auth.ts";
import {
  type ChatContext,
  type ChatDeps,
  chatPath,
  defaultChatDeps,
  handleChat,
  incrementBy1,
  inChatTx,
  loadChatForMember,
  messagePath,
  participantPath,
  patchWrite,
  requestTime,
  fieldPath,
} from "../_shared/chats.ts";
import { createWrite } from "../_shared/firestore.ts";
import { invalid, requireId, requireUuid } from "../_shared/group-tx.ts";
import { groupPath } from "../_shared/groups.ts";
import { CHAT_MEDIA_BUCKET } from "../_shared/media.ts";
import { notificationInboxId, prepareNotification } from "../_shared/notification-enqueue.ts";
import { enqueueGroupFanout } from "../_shared/group-notification.ts";

export const MAX_TEXT = 1000;
const PREVIEW_LEN = 100;
const MAX_CHAT_TRANSFORMS = 500;
const IMAGE_EXTS = ["jpg", "png", "webp"];
const len = (s: string) => Array.from(s).length;

/** Dạng JSON của một tin trong response (`Timestamp` thành ISO 8601). */
export function messageJson(id: string, m: Record<string, unknown>) {
  const iso = (v: unknown) => v instanceof Date ? v.toISOString() : null;
  return {
    id,
    senderId: m.senderId ?? "",
    senderSnapshot: m.senderSnapshot ?? null,
    senderDeleted: m.senderDeleted === true,
    text: m.text ?? "",
    imagePath: m.imagePath ?? null,
    status: m.status ?? "NORMAL",
    createdAt: iso(m.createdAt),
    recalledAt: iso(m.recalledAt),
  };
}

async function send({ uid, body, deps }: ChatContext): Promise<Response> {
  const chatId = requireId(body, "chatId");
  const messageId = requireUuid(body, "messageId");
  const text = typeof body.text === "string" ? body.text.trim() : body.text === undefined || body.text === null ? "" : null;
  if (text === null) throw invalid("text phải là chuỗi");
  if (len(text) > MAX_TEXT) throw invalid(`text tối đa ${MAX_TEXT} ký tự`);
  const imagePath = body.imagePath ?? null;
  if (imagePath !== null && typeof imagePath !== "string") throw invalid("imagePath phải là chuỗi hoặc null");
  if (imagePath === null && text === "") throw invalid("Tin nhắn không được để trống");
  if (imagePath !== null && !IMAGE_EXTS.some((e) => imagePath === `${chatId}/${messageId}.${e}`)) {
    throw invalid("imagePath phải có dạng {chatId}/{messageId}.{jpg,png,webp}");
  }

  const out = await inChatTx(deps, chatId, async (tx, writes) => {
    const chat = await loadChatForMember(tx, chatId, uid);
    if (chat.status === "READ_ONLY") throw new HttpError(409, "CHAT_READ_ONLY", "Cuộc trò chuyện chỉ đọc");
    const existing = await tx.get(messagePath(chatId, messageId));
    if (existing) {
      if (existing.senderId !== uid) throw invalid("messageId đã được dùng");
      return { created: false as const, message: messageJson(messageId, existing) };
    }
    const memberIds = chat.memberIds ?? [];
    if (chat.type === "GROUP") {
      const group = chat.groupId ? await tx.get(groupPath(chat.groupId)) : null;
      if (group?.status !== "ACTIVE") throw new HttpError(423, "GROUP_LOCKED", "Nhóm đã bị tạm khóa");
    } else {
      const other = memberIds.find((m) => m !== uid);
      const theirs = other ? await tx.get(participantPath(chatId, other)) : null;
      if (theirs?.blockedOther === true) {
        throw new HttpError(403, "BLOCKED", "Bạn không thể gửi tin trong cuộc trò chuyện này");
      }
    }
    if (imagePath !== null && !await deps.storage.exists(CHAT_MEDIA_BUCKET, imagePath)) {
      throw new HttpError(400, "PHOTO_MISSING", "Ảnh chưa được tải lên");
    }
    const mine = await tx.get(participantPath(chatId, uid));
    const others = memberIds.filter((m) => m !== uid);
    const now = deps.now();
    const content = {
      type: "CHAT_MESSAGE", title: "Tin nhắn mới", body: "Bạn có tin nhắn mới.", data: { chatId, messageId },
    };
    const notificationWrites = chat.type === "DIRECT" ? (await Promise.all(others.map(async (recipientUid) => {
      const identity = JSON.stringify([chatId, messageId, recipientUid, "CHAT_MESSAGE"]);
      return await prepareNotification(tx, "chat", identity, recipientUid,
        await notificationInboxId("chat", identity, recipientUid, "CHAT_MESSAGE"), content, now);
    }))).flat() : [];
    writes.push(createWrite(messagePath(chatId, messageId), {
      senderId: uid,
      senderSnapshot: mine?.userSnapshot ?? null,
      senderDeleted: false,
      text,
      imagePath,
      status: "NORMAL",
      recalledAt: null,
    }, ["createdAt"]));
    const type = imagePath ? "IMAGE" : "TEXT";
    const preview = text === "" ? "[Hình ảnh]" : Array.from(text).slice(0, PREVIEW_LEN).join("");
    const fields = ["lastMessage.messageId", "lastMessage.senderId", "lastMessage.type", "lastMessage.preview"];
    const patch: Record<string, unknown> = { lastMessage: { messageId, senderId: uid, type, preview } };
    const transforms = [requestTime("lastMessage.createdAt"), requestTime("updatedAt")];
    if (transforms.length + others.length <= MAX_CHAT_TRANSFORMS) {
      transforms.push(...others.map(recipient => incrementBy1(fieldPath("unreadCounts", recipient))));
    } else {
      // This transaction already reads the chat. A replacement map preserves increments
      // on conflict retries and avoids Firestore's per-document field transform limit.
      const counts = { ...chat.unreadCounts as Record<string, unknown> };
      const increments = Object.fromEntries(others.map(recipient => {
        const previous = Object.hasOwn(counts, recipient) ? counts[recipient] : null;
        return [recipient, (typeof previous === "number" ? previous : 0) + 1];
      }));
      fields.push("unreadCounts");
      patch.unreadCounts = { ...counts, ...increments };
    }
    writes.push(patchWrite(
      chatPath(chatId),
      patch,
      fields,
      transforms,
    ));
    writes.push(...notificationWrites);
    if (chat.type === "GROUP" && chat.groupId && others.length) {
      await enqueueGroupFanout(tx, writes, {
        groupId: chat.groupId, actorUid: uid, sourceId: messageId, audience: "MEMBERS", content, now,
        recipients: others,
      });
    }
    return { created: true as const };
  });

  if (!out.created) return Response.json({ message: out.message }, { status: 200 });
  const saved = await deps.firestore.get(messagePath(chatId, messageId));
  return Response.json({ message: messageJson(messageId, saved ?? {}) }, { status: 201 });
}

export function handleChatSend(req: Request, deps: ChatDeps = defaultChatDeps()): Promise<Response> {
  return handleChat(req, deps, send);
}
