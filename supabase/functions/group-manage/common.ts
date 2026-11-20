// Phần riêng của `group-manage` (spec 090): kiểm tra tham số của nhóm, ảnh đại diện và dạng JSON trả về của `group`.
// Phần dùng chung với `group-post` (inGroupTx, requireId, ...) nằm ở `_shared/group-tx.ts`.
import type { FirestoreTransaction } from "../_shared/firestore.ts";
import { asDate, asNumber, invalid } from "../_shared/group-tx.ts";
import type { GroupDoc, GroupVisibility } from "../_shared/groups.ts";

// ---------- Kiểm tra tham số ----------

const len = (s: string) => Array.from(s).length;

export function parseName(v: unknown): string {
  const s = typeof v === "string" ? v.trim() : "";
  if (len(s) < 3 || len(s) > 50) throw invalid("Tên nhóm phải từ 3 đến 50 ký tự");
  return s;
}

export function parseDescription(v: unknown): string {
  if (typeof v !== "string") throw invalid("Mô tả phải là chuỗi");
  const s = v.trim();
  if (len(s) > 500) throw invalid("Mô tả tối đa 500 ký tự");
  return s;
}

/** Chỉ kiểm định dạng; việc `districts/{id}` có tồn tại do transaction kiểm tra (`assertDistrictExists`). */
export function parseDistrictId(v: unknown): string {
  const s = typeof v === "string" ? v.trim() : "";
  if (!s || s.includes("/")) throw invalid("Thiếu hoặc sai districtId");
  return s;
}

export function parseVisibility(v: unknown): GroupVisibility {
  if (v !== "PUBLIC" && v !== "PRIVATE") throw invalid("visibility phải là PUBLIC hoặc PRIVATE");
  return v;
}

export async function assertDistrictExists(tx: FirestoreTransaction, districtId: string): Promise<void> {
  if (!await tx.get(`districts/${districtId}`)) throw invalid("Khu vực không tồn tại");
}

// ---------- Ảnh đại diện ----------

export const AVATAR_BUCKET = "group-avatars";
export const AVATAR_EXTS = ["jpg", "png", "webp"] as const;
const CONTENT_EXT: Record<string, string> = { "image/jpeg": "jpg", "image/png": "png", "image/webp": "webp" };

/** Path trong bucket `group-avatars` (cũng là giá trị `groups.avatarPath`). */
export const avatarObjectPath = (groupId: string, ext: string) => `${groupId}/avatar.${ext}`;

/**
 * Đuôi file ảnh đại diện từ `avatarContentType` (`image/jpeg|png|webp`). Contract chưa nêu tham số này nên thiếu
 * thì mặc định `jpg` (app nén ảnh sang JPEG).
 */
export function parseAvatarExt(body: Record<string, unknown>): string {
  const ct = body.avatarContentType;
  if (ct === undefined || ct === null) return "jpg";
  const ext = typeof ct === "string" ? CONTENT_EXT[ct] : undefined;
  if (!ext) throw invalid("avatarContentType phải là image/jpeg, image/png hoặc image/webp");
  return ext;
}

// ---------- Dạng JSON trả về ----------

/**
 * `group` trong response: `id` + các trường công khai của nhóm; `Timestamp` thành chuỗi ISO 8601 (UTC).
 * Không trả `nameKeywords` (chỉ phục vụ truy vấn).
 */
export function groupJson(id: string, g: GroupDoc) {
  return {
    id,
    name: g.name ?? "",
    description: g.description ?? "",
    avatarPath: g.avatarPath ?? null,
    districtId: g.districtId ?? "",
    ownerId: g.ownerId ?? "",
    visibility: g.visibility ?? "PUBLIC",
    memberCount: asNumber(g.memberCount),
    pendingRequestCount: asNumber(g.pendingRequestCount),
    status: g.status ?? "ACTIVE",
    createdAt: asDate(g.createdAt)?.toISOString() ?? null,
    updatedAt: asDate(g.updatedAt)?.toISOString() ?? null,
  };
}
