// Phần dùng chung của các function nhóm (`group-manage`, `group-post`, `group-media-url`, spec 090): lỗi tham số,
// transaction có xếp hàng theo nhóm và vài hàm kiểm tra/đọc giá trị.
import { HttpError } from "./auth.ts";
import { createKeyedSerializer, runWithBackoff, UUID } from "./dropin-tx.ts";
import type { FirestoreTransaction, Write } from "./firestore.ts";
import type { GroupDeps } from "./groups.ts";

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

/** Trường `key` phải là UUID (trả về chữ thường). */
export function requireUuid(body: Record<string, unknown>, key: string): string {
  const v = body[key];
  if (typeof v !== "string" || !UUID.test(v)) throw invalid(`${key} phải là UUID`);
  return v.toLowerCase();
}

export const requireRequestId = (body: Record<string, unknown>): string => requireUuid(body, "requestId");

export const asDate = (v: unknown): Date | null => (v instanceof Date && !Number.isNaN(v.getTime()) ? v : null);
export const asNumber = (v: unknown, fallback = 0): number =>
  typeof v === "number" && Number.isFinite(v) ? v : fallback;
