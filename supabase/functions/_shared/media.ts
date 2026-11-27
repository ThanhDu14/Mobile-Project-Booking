// Ảnh bài đăng trong bucket private `group-media` (spec 090 research R8, R9; dùng chung với spec 091 cho ảnh chat):
// kiểm tra ảnh, đặt tên object và dọn theo tiền tố.
import { invalid } from "./group-tx.ts";
import type { GroupStorage } from "./groups.ts";

export const GROUP_MEDIA_BUCKET = "group-media";
export const MAX_IMAGE_BYTES = 2 * 1024 * 1024;
/** Số ảnh tối đa của một bài đăng (FR-011). */
export const MAX_POST_PHOTOS = 4;

const IMAGE_EXT: Record<string, string> = { "image/jpeg": "jpg", "image/png": "png", "image/webp": "webp" };

/** Kiểm tra `contentType` (jpeg/png/webp) và `size` (1..2 MB); trả đuôi file (`jpg`, `png`, `webp`). */
export function validateImage(contentType: unknown, size: unknown): string {
  const ext = typeof contentType === "string" ? IMAGE_EXT[contentType] : undefined;
  if (!ext) throw invalid("contentType phải là image/jpeg, image/png hoặc image/webp");
  if (typeof size !== "number" || !Number.isInteger(size) || size <= 0 || size > MAX_IMAGE_BYTES) {
    throw invalid("size phải từ 1 byte đến 2 MB");
  }
  return ext;
}

/** Thư mục chứa ảnh của một bài (có dấu `/` cuối): `{groupId}/{postId}/`. */
export const postMediaPrefix = (groupId: string, postId: string) => `${groupId}/${postId}/`;

export const postMediaPath = (groupId: string, postId: string, index: number, ext: string) =>
  `${postMediaPrefix(groupId, postId)}${index}.${ext}`;

/** `groupId` là phần đầu của path `{groupId}/...`; path không có dạng `a/b` hoặc có `..` thì `null`. */
export function groupIdOfPath(path: string): string | null {
  const parts = path.split("/");
  if (parts.length < 2 || parts.some((p) => p === "" || p === "." || p === "..")) return null;
  return parts[0];
}

/** Xóa mọi file ngay dưới `prefix` trong `bucket`; trả số file đã xóa. */
export async function deletePrefix(storage: GroupStorage, bucket: string, prefix: string): Promise<number> {
  return await storage.remove(bucket, await storage.list(bucket, prefix));
}

// ---------- Ảnh chat (spec 091): bucket private `chat-media`, object `{chatId}/{messageId}.{ext}` ----------

export const CHAT_MEDIA_BUCKET = "chat-media";

export const chatMediaPath = (chatId: string, messageId: string, ext: string) => `${chatId}/${messageId}.${ext}`;

/** `{chatId}/{messageId}.{jpg,png,webp}` → `{ chatId, messageId }`; sai dạng thì `null`. */
export function parseChatMediaPath(path: string): { chatId: string; messageId: string } | null {
  const m = /^([^/.][^/]*)\/([^/.]+)\.(jpg|png|webp)$/.exec(path);
  return m ? { chatId: m[1], messageId: m[2] } : null;
}
