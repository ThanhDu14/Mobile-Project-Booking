// Firestore giả trong bộ nhớ cho unit test các Edge Function thanh toán (spec 050). Chỉ dùng trong test.
// Áp dụng `Write` giống REST: precondition `exists`, `updateMask` (gộp) hoặc ghi đè cả document,
// transform `REQUEST_TIME` và `increment`; `query` hỗ trợ `parent` và bộ lọc EQUAL/IN.
// Mặc định transaction chạy lần lượt. `optimistic: true` (test tranh chấp, spec 050 US5): transaction chạy xen nhau,
// lúc commit nếu document đã đọc bị transaction khác đổi thì báo ABORTED và thử lại tới `maxAttempts`, như Firestore.
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

export interface FakeFirestoreOptions {
  optimistic?: boolean;
}

export class FakeFirestore {
  docs = new Map<string, Doc>();
  /** Phiên bản của từng document, tăng mỗi lần bị ghi (chế độ optimistic dùng để phát hiện xung đột). */
  private versions = new Map<string, number>();
  /** Số lần commit bị từ chối vì xung đột (chế độ optimistic). */
  aborts = 0;
  /** Số lần `runTransaction` được gọi. */
  transactions = 0;
  /** Số lần commit có ghi ít nhất một thao tác. */
  commits = 0;
  private lock: Promise<unknown> = Promise.resolve();

  constructor(
    private readonly now: () => Date,
    private readonly options: FakeFirestoreOptions = {},
  ) {}

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
    for (const w of writes) {
      const path = (w.update?.name ?? w.delete ?? "").substring(prefix.length);
      this.versions.set(path, (this.versions.get(path) ?? 0) + 1);
    }
    this.docs = next;
    if (writes.length > 0) this.commits++;
  }

  runTransaction = <T>(
    operation: (
      tx: FirestoreTransaction,
    ) => Promise<T | { result: T; writes: Write[] }>,
    maxAttempts = 5,
  ): Promise<T> => {
    if (this.options.optimistic) return this.runOptimistic(operation, maxAttempts);
    const run = async (): Promise<T> => {
      this.transactions++;
      const writes: Write[] = [];
      const tx: FirestoreTransaction = {
        get: (path) => Promise.resolve(this.read(path)),
        query: (collectionId, filters, opts) => Promise.resolve(this.query(collectionId, filters, opts)),
        write: (w) => void writes.push(w),
      };
      const out = await operation(tx);
      return this.finish(out, writes);
    };
    const p = this.lock.then(run, run);
    this.lock = p.catch(() => undefined);
    return p;
  };

  private async runOptimistic<T>(
    operation: (tx: FirestoreTransaction) => Promise<T | { result: T; writes: Write[] }>,
    maxAttempts: number,
  ): Promise<T> {
    for (let attempt = 1;; attempt++) {
      this.transactions++;
      const seen = new Map<string, number>();
      const note = (path: string) => {
        if (!seen.has(path)) seen.set(path, this.versions.get(path) ?? 0);
      };
      // Nhường lượt trước mỗi lần đọc để các transaction đồng thời thật sự xen nhau.
      const yieldTurn = () => new Promise<void>((resolve) => setTimeout(resolve, 0));
      const writes: Write[] = [];
      const tx: FirestoreTransaction = {
        get: async (path) => {
          await yieldTurn();
          note(path);
          return this.read(path);
        },
        query: async (collectionId, filters, opts) => {
          await yieldTurn();
          const rows = this.query(collectionId, filters, opts);
          rows.forEach((r) => note(r.path));
          return rows;
        },
        write: (w) => void writes.push(w),
      };
      const out = await operation(tx);
      const conflict = [...seen].some(([path, version]) => (this.versions.get(path) ?? 0) !== version);
      if (!conflict) return this.finish(out, writes);
      this.aborts++;
      if (attempt >= maxAttempts) throw new FirestoreError("ABORTED", 409, "Transaction bị tranh chấp");
    }
  }

  private finish<T>(out: T | { result: T; writes: Write[] }, writes: Write[]): T {
    const wrapped = out !== null && typeof out === "object" && "writes" in (out as object);
    this.apply(wrapped ? (out as { writes: Write[] }).writes : writes);
    return wrapped ? (out as { result: T }).result : out as T;
  }
}
