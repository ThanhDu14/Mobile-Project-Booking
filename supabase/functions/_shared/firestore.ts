// TODO(B): thay bằng bản đầy đủ của _shared/firestore.ts. Bản này chỉ có đúng phần spec 010 cần
// (contracts/edge-function-on-signup.md): get, commit (có điều kiện exists), getAccessToken.
//
// Gọi Firestore REST bằng access token của service account (FIREBASE_SERVICE_ACCOUNT). Có
// FIRESTORE_EMULATOR_HOST thì gọi Emulator với `Authorization: Bearer owner` (bỏ qua Rules).
import { importPKCS8, SignJWT } from "npm:jose@6.2.12";

export const SCOPE_DATASTORE = "https://www.googleapis.com/auth/datastore";
export const SCOPE_CLOUD_PLATFORM = "https://www.googleapis.com/auth/cloud-platform";
export const SCOPE_IDENTITY_TOOLKIT = "https://www.googleapis.com/auth/identitytoolkit";

export function projectId(): string {
  const id = Deno.env.get("FIREBASE_PROJECT_ID");
  if (!id) throw new Error("Thiếu FIREBASE_PROJECT_ID");
  return id;
}

// ---------- Access token của service account (dùng chung với identity.ts) ----------

interface ServiceAccount {
  client_email: string;
  private_key: string;
  token_uri?: string;
}

const tokenCache = new Map<string, { token: string; expiresAt: number }>();

/**
 * Access token OAuth2 của service account cho các scope đã cho, cache tới 1 phút trước khi hết hạn.
 * Khi chạy với Emulator, gọi nơi khác truyền "owner" thay cho token này.
 */
export async function getAccessToken(scopes: string[]): Promise<string> {
  const key = [...scopes].sort().join(" ");
  const cached = tokenCache.get(key);
  if (cached && cached.expiresAt - 60_000 > Date.now()) return cached.token;

  const raw = Deno.env.get("FIREBASE_SERVICE_ACCOUNT");
  if (!raw) throw new Error("Thiếu FIREBASE_SERVICE_ACCOUNT");
  const sa = JSON.parse(raw) as ServiceAccount;
  const tokenUri = sa.token_uri ?? "https://oauth2.googleapis.com/token";
  const now = Math.floor(Date.now() / 1000);
  const assertion = await new SignJWT({ scope: key })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .setIssuer(sa.client_email)
    .setAudience(tokenUri)
    .setIssuedAt(now)
    .setExpirationTime(now + 3600)
    .sign(await importPKCS8(sa.private_key, "RS256"));

  const res = await fetch(tokenUri, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }),
  });
  if (!res.ok) throw new Error(`Không lấy được access token: ${res.status} ${await res.text()}`);
  const body = await res.json() as { access_token: string; expires_in: number };
  tokenCache.set(key, { token: body.access_token, expiresAt: Date.now() + body.expires_in * 1000 });
  return body.access_token;
}

// ---------- Firestore REST ----------

export type FirestoreErrorCode = "ALREADY_EXISTS" | "NOT_FOUND" | "FAILED_PRECONDITION" | "ABORTED" | "UNKNOWN";

export class FirestoreError extends Error {
  constructor(readonly code: FirestoreErrorCode, readonly status: number, message: string) {
    super(message);
    this.name = "FirestoreError";
  }
}

function emulatorHost(): string | undefined {
  return Deno.env.get("FIRESTORE_EMULATOR_HOST") || undefined;
}

function databaseRoot(): string {
  return `projects/${projectId()}/databases/(default)`;
}

function apiBase(): string {
  const host = emulatorHost();
  return host ? `http://${host}/v1` : "https://firestore.googleapis.com/v1";
}

/** Tên đầy đủ của document, `path` dạng `users/abc`. */
export function documentName(path: string): string {
  return `${databaseRoot()}/documents/${path}`;
}

async function authHeader(): Promise<string> {
  return emulatorHost() ? "Bearer owner" : `Bearer ${await getAccessToken([SCOPE_DATASTORE])}`;
}

async function request(url: string, init: RequestInit): Promise<Response> {
  return await fetch(url, {
    ...init,
    headers: { "Content-Type": "application/json", Authorization: await authHeader(), ...init.headers },
  });
}

async function toError(res: Response): Promise<FirestoreError> {
  const text = await res.text();
  let status = "";
  try {
    status = (JSON.parse(text) as { error?: { status?: string } }).error?.status ?? "";
  } catch { /* body không phải JSON */ }
  const known: FirestoreErrorCode[] = ["ALREADY_EXISTS", "NOT_FOUND", "FAILED_PRECONDITION", "ABORTED"];
  const code = known.find((c) => c === status) ?? "UNKNOWN";
  return new FirestoreError(code, res.status, text);
}

/** Đọc một document. Không có thì trả `null`. */
export async function get(path: string): Promise<Record<string, unknown> | null> {
  const res = await request(`${apiBase()}/${documentName(path)}`, { method: "GET" });
  if (res.status === 404) {
    await res.body?.cancel();
    return null;
  }
  if (!res.ok) throw await toError(res);
  const doc = await res.json() as { fields?: Record<string, FirestoreValue> };
  return decodeFields(doc.fields ?? {});
}

/** Đọc danh sách document trong collection hoặc subcollection. */
export async function list(collectionPath: string): Promise<Record<string, unknown>[]> {
  const res = await request(`${apiBase()}/${documentName(collectionPath)}`, { method: "GET" });
  if (res.status === 404) {
    await res.body?.cancel();
    return [];
  }
  if (!res.ok) throw await toError(res);
  const data = await res.json() as { documents?: { fields?: Record<string, FirestoreValue> }[] };
  return (data.documents ?? []).map((d) => decodeFields(d.fields ?? {}));
}

/** Một thao tác ghi theo REST API (`Write`). Dùng `createWrite` / `updateWrite` để tạo. */
export interface Write {
  update?: { name: string; fields: Record<string, FirestoreValue> };
  delete?: string;
  updateMask?: { fieldPaths: string[] };
  updateTransforms?: FieldTransform[];
  currentDocument?: { exists: boolean };
}

/** Field transform của REST API: giờ server hoặc cộng dồn (`increment`, ví dụ hoàn tiền vào ví). */
export type FieldTransform =
  | { fieldPath: string; setToServerValue: "REQUEST_TIME" }
  | { fieldPath: string; increment: FirestoreValue };

/**
 * Ghi nguyên tử nhiều thao tác. Precondition `exists = false` không đạt thì ném
 * `FirestoreError` với `code == "ALREADY_EXISTS"`; tranh chấp ghi thì `code == "ABORTED"` (thử lại được).
 */
export async function commit(writes: Write[], transaction?: string): Promise<void> {
  const res = await request(`${apiBase()}/${databaseRoot()}/documents:commit`, {
    method: "POST",
    body: JSON.stringify({ writes, ...(transaction ? { transaction } : {}) }),
  });
  if (!res.ok) throw await toError(res);
  await res.body?.cancel();
}

// ---------- Truy vấn và transaction ----------
// Cùng chữ ký với bản của D (feature/110-owner-onboarding-implement) để merge không xung đột API; spec 011 thêm
// tham số tùy chọn `opts` (`parent` cho subcollection, `allDescendants` cho collection group).

export interface QueryFilter {
  field: string;
  op: "EQUAL" | "IN";
  value: unknown;
}

export interface QueryOptions {
  /** Đường dẫn document cha, ví dụ `users/abc` để đọc `users/abc/favorites`. Mặc định là gốc. */
  parent?: string;
  /** `true` để truy vấn collection group (mọi collection cùng tên ở mọi cấp). */
  allDescendants?: boolean;
  limit?: number;
}

export interface QueryDocument {
  id: string;
  /** Đường dẫn tương đối, ví dụ `dropInSessions/s1/registrations/u1`. */
  path: string;
  data: Record<string, unknown>;
}

function relativePath(name: string): string {
  const prefix = `${databaseRoot()}/documents/`;
  return name.startsWith(prefix) ? name.substring(prefix.length) : name;
}

function structuredQuery(collectionId: string, filters: QueryFilter[], opts: QueryOptions) {
  const clauses = filters.map((f) => ({
    fieldFilter: { field: { fieldPath: f.field }, op: f.op, value: encodeValue(f.value) },
  }));
  return {
    from: [{ collectionId, ...(opts.allDescendants ? { allDescendants: true } : {}) }],
    ...(clauses.length === 0 ? {} : {
      where: clauses.length === 1 ? clauses[0] : { compositeFilter: { op: "AND", filters: clauses } },
    }),
    ...(opts.limit ? { limit: opts.limit } : {}),
  };
}

async function queryRequest(
  collectionId: string,
  filters: QueryFilter[],
  opts: QueryOptions,
  transaction?: string,
): Promise<QueryDocument[]> {
  const parent = opts.parent ? `${databaseRoot()}/documents/${opts.parent}` : `${databaseRoot()}/documents`;
  const res = await request(`${apiBase()}/${parent}:runQuery`, {
    method: "POST",
    body: JSON.stringify({
      structuredQuery: structuredQuery(collectionId, filters, opts),
      ...(transaction ? { transaction } : {}),
    }),
  });
  if (!res.ok) throw await toError(res);
  const rows = await res.json() as { document?: { name: string; fields?: Record<string, FirestoreValue> } }[];
  return rows.flatMap((row) => {
    if (!row.document) return [];
    const path = relativePath(row.document.name);
    return [{ id: path.substring(path.lastIndexOf("/") + 1), path, data: decodeFields(row.document.fields ?? {}) }];
  });
}

/** Truy vấn có điều kiện bằng (`EQUAL`, `IN`). */
export function query(collectionId: string, filters: QueryFilter[], opts: QueryOptions = {}): Promise<QueryDocument[]> {
  return queryRequest(collectionId, filters, opts);
}

export interface FirestoreTransaction {
  readonly id?: string;
  get(path: string): Promise<Record<string, unknown> | null>;
  query(collectionId: string, filters: QueryFilter[], opts?: QueryOptions): Promise<QueryDocument[]>;
  write(write: Write): void;
  commit?(writes: Write[]): Promise<void>;
}

async function rollback(transaction: string): Promise<void> {
  const res = await request(`${apiBase()}/${databaseRoot()}/documents:rollback`, {
    method: "POST",
    body: JSON.stringify({ transaction }),
  }).catch(() => undefined);
  await res?.body?.cancel();
}

/**
 * Chạy đọc và ghi trong một transaction đọc-ghi của Firestore REST. `operation` trả `writes` để commit một lần;
 * bị `ABORTED` (tranh chấp) thì chạy lại `operation`, nên `operation` không được có tác dụng phụ bên ngoài.
 */
export async function runTransaction<T>(
  operation: (transaction: FirestoreTransaction) => Promise<T | { result: T; writes: Write[] }>,
  maxAttempts = 5,
): Promise<T> {
  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    const begin = await request(`${apiBase()}/${databaseRoot()}/documents:beginTransaction`, {
      method: "POST",
      body: JSON.stringify({ options: { readWrite: {} } }),
    });
    if (!begin.ok) throw await toError(begin);
    const { transaction } = await begin.json() as { transaction: string };
    const writes: Write[] = [];
    let writeStarted = false;
    const tx: FirestoreTransaction = {
      id: transaction,
      get: async (path) => {
        if (writeStarted) throw new Error("Firestore transaction cannot read after a write");
        const res = await request(`${apiBase()}/${databaseRoot()}/documents:batchGet`, {
          method: "POST",
          body: JSON.stringify({ documents: [documentName(path)], transaction }),
        });
        if (!res.ok) throw await toError(res);
        const rows = await res.json() as { found?: { fields?: Record<string, FirestoreValue> }; missing?: unknown }[];
        const found = rows.find((row) => row.found)?.found;
        return found ? decodeFields(found.fields ?? {}) : null;
      },
      query: (collectionId, filters, opts = {}) => queryRequest(collectionId, filters, opts, transaction),
      write: (write) => {
        writeStarted = true;
        writes.push(write);
      },
      async commit(customWrites) {
        const res = await request(`${apiBase()}/${databaseRoot()}/documents:commit`, {
          method: "POST",
          body: JSON.stringify({ writes: customWrites, transaction }),
        });
        if (!res.ok) throw await toError(res);
        await res.body?.cancel();
      },
    };

    try {
      const outcome = await operation(tx);
      const isWrapped = outcome !== null && typeof outcome === "object" && "writes" in outcome && Array.isArray((outcome as Record<string, unknown>).writes);
      const writesToCommit = isWrapped ? (outcome as { writes: Write[] }).writes : writes;
      if (writesToCommit.length > 0) {
        await commit(writesToCommit, transaction);
      } else {
        await rollback(transaction);
      }
      return isWrapped && "result" in (outcome as Record<string, unknown>)
        ? (outcome as { result: T }).result
        : outcome as T;
    } catch (error) {
      await rollback(transaction);
      if (error instanceof FirestoreError && error.code === "ABORTED" && attempt < maxAttempts) continue;
      throw error;
    }
  }
  throw new FirestoreError("ABORTED", 409, "Transaction bị tranh chấp sau nhiều lần thử");
}

async function rollbackTransaction(transaction: string): Promise<void> {
  const res = await request(`${apiBase()}/${databaseRoot()}/documents:rollback`, {
    method: "POST",
    body: JSON.stringify({ transaction }),
  });
  if (!res.ok) throw await toError(res);
  await res.body?.cancel();
}

/** Fetches the latest application for a UID without requiring a composite index. */
export async function latestOwnerApplication(uid: string): Promise<Record<string, unknown> | null> {
  const res = await request(`${apiBase()}/${databaseRoot()}/documents:runQuery`, {
    method: "POST",
    body: JSON.stringify({
      structuredQuery: {
        from: [{ collectionId: "ownerApplications" }],
        where: { fieldFilter: { field: { fieldPath: "uid" }, op: "EQUAL", value: { stringValue: uid } } },
      },
    }),
  });
  if (!res.ok) throw await toError(res);
  const rows = await res.json() as { document?: { name: string; fields?: Record<string, FirestoreValue> } }[];
  const docs: Record<string, unknown>[] = rows
    .map((row) => row.document)
    .filter((doc): doc is NonNullable<typeof doc> => !!doc)
    .map((doc) => ({ ...decodeFields(doc.fields ?? {}), __documentId: doc.name.split("/").pop() }));
  if (docs.length === 0) return null;
  docs.sort((a, b) => String(b.submittedAt ?? "").localeCompare(String(a.submittedAt ?? "")));
  return docs[0];
}

/** Tạo document mới; đã có thì `commit` ném `ALREADY_EXISTS`. */
export function createWrite(
  path: string,
  data: Record<string, unknown>,
  serverTimestampFields: string[] = [],
): Write {
  return {
    update: { name: documentName(path), fields: encodeFields(data) },
    updateTransforms: serverTimestampFields.map((fieldPath) => ({ fieldPath, setToServerValue: "REQUEST_TIME" })),
    currentDocument: { exists: false },
  };
}

/** Sửa đúng các trường trong `data` của document đã có. */
export function updateWrite(
  path: string,
  data: Record<string, unknown>,
  serverTimestampFields: string[] = [],
): Write {
  return {
    update: { name: documentName(path), fields: encodeFields(data) },
    updateMask: { fieldPaths: Object.keys(data) },
    updateTransforms: serverTimestampFields.map((fieldPath) => ({ fieldPath, setToServerValue: "REQUEST_TIME" })),
    currentDocument: { exists: true },
  };
}

/** Xóa document; precondition exists = true để báo lỗi nếu document không tồn tại. */
export function deleteWrite(path: string, preconditionExists = true): Write {
  return {
    delete: documentName(path),
    currentDocument: preconditionExists ? { exists: true } : undefined,
  };
}

// ---------- Chuyển đổi giá trị ----------

export type FirestoreValue =
  | { nullValue: null }
  | { booleanValue: boolean }
  | { integerValue: string }
  | { doubleValue: number }
  | { stringValue: string }
  | { timestampValue: string }
  | { arrayValue: { values?: FirestoreValue[] } }
  | { mapValue: { fields?: Record<string, FirestoreValue> } };

export function encodeValue(value: unknown): FirestoreValue {
  if (value === null || value === undefined) return { nullValue: null };
  if (typeof value === "boolean") return { booleanValue: value };
  if (typeof value === "number") {
    return Number.isInteger(value) ? { integerValue: String(value) } : { doubleValue: value };
  }
  if (typeof value === "string") return { stringValue: value };
  if (value instanceof Date) return { timestampValue: value.toISOString() };
  if (Array.isArray(value)) return { arrayValue: { values: value.map(encodeValue) } };
  if (typeof value === "object") return { mapValue: { fields: encodeFields(value as Record<string, unknown>) } };
  throw new Error(`Kiểu giá trị không hỗ trợ: ${typeof value}`);
}

export function encodeFields(data: Record<string, unknown>): Record<string, FirestoreValue> {
  return Object.fromEntries(Object.entries(data).map(([k, v]) => [k, encodeValue(v)]));
}

export function decodeValue(value: FirestoreValue): unknown {
  if ("nullValue" in value) return null;
  if ("booleanValue" in value) return value.booleanValue;
  if ("integerValue" in value) return Number(value.integerValue);
  if ("doubleValue" in value) return value.doubleValue;
  if ("stringValue" in value) return value.stringValue;
  if ("timestampValue" in value) return new Date(value.timestampValue);
  if ("arrayValue" in value) return (value.arrayValue.values ?? []).map(decodeValue);
  if ("mapValue" in value) return decodeFields(value.mapValue.fields ?? {});
  return undefined;
}

export function decodeFields(fields: Record<string, FirestoreValue>): Record<string, unknown> {
  return Object.fromEntries(Object.entries(fields).map(([k, v]) => [k, decodeValue(v)]));
}
