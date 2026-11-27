// Edge Function `chat-media-url` (spec 091, contracts/edge-functions-chat.md mục 3; research R8): upload, download, discard.
import {
  type ChatContext,
  type ChatDeps,
  defaultChatDeps,
  handleChat,
  loadChatForMember,
  messagePath,
  routeActions,
} from "../_shared/chats.ts";
import { invalid, requireId, requireUuid } from "../_shared/group-tx.ts";
import { CHAT_MEDIA_BUCKET, chatMediaPath, parseChatMediaPath, validateImage } from "../_shared/media.ts";

/** Một lần `download` xin tối đa 30 path (một trang tin). */
export const MAX_DOWNLOAD_PATHS = 30;
export const DOWNLOAD_EXPIRES_SECONDS = 3600;

async function upload({ uid, body, deps }: ChatContext): Promise<Response> {
  const chatId = requireId(body, "chatId");
  const messageId = requireUuid(body, "messageId");
  const ext = validateImage(body.contentType, body.size);
  await loadChatForMember(deps.firestore, chatId, uid);
  const signed = await deps.storage.createSignedUploadUrl(CHAT_MEDIA_BUCKET, chatMediaPath(chatId, messageId, ext));
  return Response.json({ path: signed.path, signedUrl: signed.signedUrl, token: signed.token });
}

async function download({ uid, body, deps }: ChatContext): Promise<Response> {
  const paths = body.paths;
  if (!Array.isArray(paths) || paths.length > MAX_DOWNLOAD_PATHS || paths.some((p) => typeof p !== "string")) {
    throw invalid(`paths phải là mảng tối đa ${MAX_DOWNLOAD_PATHS} chuỗi`);
  }
  // Chỉ ký path của tin NORMAL trong chat mà người gọi đang ở trong cuộc (FR-015).
  const memberOf = new Map<string, Promise<boolean>>();
  const allowed: string[] = [];
  for (const path of new Set(paths as string[])) {
    const parsed = parseChatMediaPath(path);
    if (!parsed) continue;
    if (!memberOf.has(parsed.chatId)) {
      memberOf.set(parsed.chatId, loadChatForMember(deps.firestore, parsed.chatId, uid).then(() => true, () => false));
    }
    if (!await memberOf.get(parsed.chatId)) continue;
    const msg = await deps.firestore.get(messagePath(parsed.chatId, parsed.messageId));
    if (msg?.status === "NORMAL" && msg.imagePath === path) allowed.push(path);
  }
  const urls = await deps.storage.createSignedUrls(CHAT_MEDIA_BUCKET, allowed, DOWNLOAD_EXPIRES_SECONDS);
  const expiresAt = new Date(deps.now().getTime() + DOWNLOAD_EXPIRES_SECONDS * 1000).toISOString();
  return Response.json({ urls, expiresAt });
}

async function discard({ uid, body, deps }: ChatContext): Promise<Response> {
  const chatId = requireId(body, "chatId");
  const messageId = requireUuid(body, "messageId");
  await loadChatForMember(deps.firestore, chatId, uid);
  // Tin đã được tạo thì ảnh thuộc về tin, không xóa. Chỉ xóa đúng object của tin này (không xóa ảnh tin khác).
  if (await deps.firestore.get(messagePath(chatId, messageId))) return Response.json({ deleted: 0 });
  const deleted = await deps.storage.remove(
    CHAT_MEDIA_BUCKET,
    ["jpg", "png", "webp"].map((e) => chatMediaPath(chatId, messageId, e)),
  );
  return Response.json({ deleted });
}

export const ACTIONS = ["upload", "download", "discard"] as const;

export function handleChatMediaUrl(req: Request, deps: ChatDeps = defaultChatDeps()): Promise<Response> {
  return handleChat(req, deps, routeActions({ upload, download, discard }));
}
