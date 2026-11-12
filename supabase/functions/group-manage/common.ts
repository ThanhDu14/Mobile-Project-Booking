// Phần dùng chung của các action `group-manage` (spec 090): kiểm tra tham số, chạy transaction có xếp hàng theo
// nhóm, và dạng JSON trả về của `group`.
import { HttpError } from "../_shared/auth.ts";
import { createKeyedSerializer, runWithBackoff, UUID } from "../_shared/dropin-tx.ts";
import type { FirestoreTransaction, Write } from "../_shared/firestore.ts";
import type { GroupDeps, GroupDoc, GroupVisibility } from "../_shared/groups.ts";

export const invalid = (message: string) => new HttpError(400, "INVALID_ARGUMENT", message);

/** Hàng đợi theo `groupId` trong một isolate: các request cùng nhóm chạy lần lượt để giảm tranh chấp (giống register-drop-in). */
const isolateSerializer = createKeyedSerializer();

/**
 * Chạy `fn` trong transaction (thử lại khi ABORTED, hết lượt thì 503 BUSY), xếp hàng theo `groupId`.
 * `tx` đưa cho `fn` chỉ ĐỌC thật; mọi `tx.write` (kể cả của hook `onMembershipChanged`, spec 091) được gom vào
 * `writes` rồi commit một lần ở cuối, nên nằm trong cùng lần commit.
 */
export function inGroupTx<T>(
  deps: GroupDeps,
  groupId: string,
  fn: (tx: FirestoreTransaction, writes: Write[]) => Promise<T>,
): Promise<T> {
  return isolateSerializer(
    groupId,
    () =>
      runWithBackoff(deps, async (raw) => {
        const writes: Write[] = [];
        const tx: FirestoreTransaction = {
          id: raw.id,
          get: (path) => raw.get(path),
          query: (collectionId, filters, opts) => raw.query(collectionId, filters, opts),
          write: (w) => {
            writes.push(w);
          },
        };
        const result = await fn(tx, writes);
        return { result, writes };
      }),
  );
}

// ---------- Kiểm tra tham số ----------

export function requireId(body: Record<string, unknown>, key: string): string {
  const v = body[key];
  const s = typeof v === "string" ? v.trim() : "";
  if (!s || s.includes("/")) throw invalid(`Thiếu hoặc sai ${key}`);
  return s;
}

export function requireRequestId(body: Record<string, unknown>): string {
  const v = body.requestId;
  if (typeof v !== "string" || !UUID.test(v)) throw invalid("requestId phải là UUID");
  return v.toLowerCase();
}

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

const asDate = (v: unknown): Date | null => (v instanceof Date && !Number.isNaN(v.getTime()) ? v : null);
export const asNumber = (v: unknown, fallback = 0): number =>
  typeof v === "number" && Number.isFinite(v) ? v : fallback;

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
