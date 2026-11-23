// Firestore giả trong bộ nhớ cho unit test các Edge Function thanh toán (spec 050). Chỉ dùng trong test.
// Áp dụng `Write` giống REST: precondition `exists`, `updateMask` (gộp) hoặc ghi đè cả document,
// transform `REQUEST_TIME` và `increment`; transaction chạy lần lượt; `query` hỗ trợ `parent` và bộ lọc EQUAL/IN.
import {
  decodeFields,
  decodeValue,
  documentName,
  FirestoreError,
  type FirestoreTransaction,
  type QueryDocument,
  type QueryFilter,
  type QueryOptions,
  type Write,
} from "./firestore.ts";

export type Doc = Record<string, unknown>;

export class FakeFirestore {
  docs = new Map<string, Doc>();
  /** Số lần `runTransaction` được gọi. */
  transactions = 0;
  /** Số lần commit có ghi ít nhất một thao tác. */
  commits = 0;
  private lock: Promise<unknown> = Promise.resolve();

  constructor(private readonly now: () => Date) {}

  seed(path: string, data: Doc): void {
    this.docs.set(path, structuredClone(data));
  }

  read(path: string): Doc | null {
    const d = this.docs.get(path);
    return d ? structuredClone(d) : null;
  }

  /** Các document con trực tiếp của `collectionPath`. */
  children(collectionPath: string): { id: string; data: Doc }[] {
    const prefix = `${collectionPath}/`;
    return [...this.docs.entries()]
      .filter(([p]) => p.startsWith(prefix) && !p.substring(prefix.length).includes("/"))
      .map(([p, data]) => ({
        id: p.substring(prefix.length),
        data: structuredClone(data),
      }));
  }

  query(
    collectionId: string,
    filters: QueryFilter[],
    opts: QueryOptions = {},
  ): QueryDocument[] {
    const base = opts.parent ? `${opts.parent}/${collectionId}` : collectionId;
    const rows = this.children(base).filter(({ data }) =>
      filters.every((f) => f.op === "IN" ? (f.value as unknown[]).includes(data[f.field]) : data[f.field] === f.value)
    );
    return rows.slice(0, opts.limit ?? rows.length).map(({ id, data }) => ({
      id,
      path: `${base}/${id}`,
      data,
    }));
  }

  apply(writes: Write[]): void {
    const prefix = documentName("");
    const next = new Map(this.docs);
    for (const w of writes) {
      const path = (w.update?.name ?? w.delete ?? "").substring(prefix.length);
      const existing = next.get(path);
      if (w.currentDocument?.exists === true && !existing) {
        throw new FirestoreError("NOT_FOUND", 404, path);
      }
      if (w.currentDocument?.exists === false && existing) {
        throw new FirestoreError("ALREADY_EXISTS", 409, path);
      }
      if (w.delete) {
        next.delete(path);
        continue;
      }
      const fields = decodeFields(w.update!.fields);
      const doc: Doc = w.updateMask ? { ...(existing ?? {}) } : {};
      for (const k of w.updateMask?.fieldPaths ?? Object.keys(fields)) {
        if (k in fields) doc[k] = fields[k];
        else delete doc[k];
      }
      for (const t of w.updateTransforms ?? []) {
        if ("setToServerValue" in t) doc[t.fieldPath] = new Date(this.now());
        else {doc[t.fieldPath] = Number(doc[t.fieldPath] ?? 0) +
            Number(decodeValue(t.increment));}
      }
      next.set(path, doc);
    }
    this.docs = next;
    if (writes.length > 0) this.commits++;
  }

  runTransaction = <T>(
    operation: (
      tx: FirestoreTransaction,
    ) => Promise<T | { result: T; writes: Write[] }>,
    _maxAttempts?: number,
  ): Promise<T> => {
    const run = async (): Promise<T> => {
      this.transactions++;
      const writes: Write[] = [];
      const tx: FirestoreTransaction = {
        get: (path) => Promise.resolve(this.read(path)),
        query: (collectionId, filters, opts) => Promise.resolve(this.query(collectionId, filters, opts)),
        write: (w) => void writes.push(w),
      };
      const out = await operation(tx);
      const wrapped = out !== null && typeof out === "object" &&
        "writes" in (out as object);
      this.apply(wrapped ? (out as { writes: Write[] }).writes : writes);
      return wrapped ? (out as { result: T }).result : out as T;
    };
    const p = this.lock.then(run, run);
    this.lock = p.catch(() => undefined);
    return p;
  };
}
