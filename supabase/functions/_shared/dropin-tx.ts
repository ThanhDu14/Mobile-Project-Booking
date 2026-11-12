// Chạy transaction cho các Edge Function vãng lai (C, spec 080: register-drop-in, cancel-drop-in; research R2).
import { HttpError } from "./auth.ts";
import type * as defaultFirestore from "./firestore.ts";
import { FirestoreError, type FirestoreTransaction, type Write } from "./firestore.ts";

export type KeyedSerializer = <T>(key: string, task: () => Promise<T>) => Promise<T>;

/**
 * Hàng đợi theo khóa: task cùng khóa chạy lần lượt, khác khóa chạy song song.
 *
 * Dùng để xếp hàng các request cùng `sessionId` trong một isolate trước khi mở transaction, để chúng không cùng giữ
 * khóa đọc trên document buổi (Emulator khi đó chờ tới "Transaction lock timeout" ~2 giây rồi abort tất cả).
 * Chỉ giảm tranh chấp: giữa các isolate (và giữa các function khác nhau) vẫn dựa vào transaction.
 */
export function createKeyedSerializer(): KeyedSerializer {
  const tails = new Map<string, Promise<unknown>>();
  return <T>(key: string, task: () => Promise<T>): Promise<T> => {
    const prev = tails.get(key) ?? Promise.resolve();
    const run = prev.then(task, task);
    const tail = run.catch(() => undefined);
    tails.set(key, tail);
    tail.then(() => {
      if (tails.get(key) === tail) tails.delete(key);
    });
    return run;
  };
}

/** research R2: thử lại tối đa 8 lần khi `ABORTED`, backoff 50–400 ms có ngẫu nhiên. */
export const MAX_TX_ATTEMPTS = 8;
const BACKOFF_MIN_MS = 50;
const BACKOFF_MAX_MS = 400;

export interface TxDeps {
  firestore: Pick<typeof defaultFirestore, "runTransaction">;
  /** Chờ giữa các lần thử lại; test thay để chạy nhanh. */
  sleep?: (ms: number) => Promise<void>;
}

/** Chạy `operation` trong transaction, mỗi lần 1 lượt, ABORTED thì chờ ngẫu nhiên rồi thử lại; hết lượt → 503. */
export async function runWithBackoff<T>(
  deps: TxDeps,
  operation: (tx: FirestoreTransaction) => Promise<{ result: T; writes: Write[] }>,
): Promise<T> {
  const sleep = deps.sleep ?? ((ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms)));
  for (let attempt = 1;; attempt++) {
    try {
      return await deps.firestore.runTransaction(operation, 1);
    } catch (e) {
      if (!(e instanceof FirestoreError && e.code === "ABORTED")) throw e;
      if (attempt >= MAX_TX_ATTEMPTS) {
        throw new HttpError(503, "BUSY", "Hệ thống đang bận, vui lòng thử lại");
      }
      const cap = Math.min(BACKOFF_MAX_MS, BACKOFF_MIN_MS * 2 ** attempt);
      await sleep(BACKOFF_MIN_MS + Math.random() * (cap - BACKOFF_MIN_MS));
    }
  }
}

export const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Đọc body JSON object; sai thì `400 INVALID_ARGUMENT`. */
export async function readJsonObject(req: Request): Promise<Record<string, unknown>> {
  try {
    const body = await req.json();
    if (body && typeof body === "object" && !Array.isArray(body)) return body;
  } catch { /* không phải JSON */ }
  throw new HttpError(400, "INVALID_ARGUMENT", "Body phải là JSON object");
}
