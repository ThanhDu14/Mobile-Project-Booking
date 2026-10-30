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
  update: { name: string; fields: Record<string, FirestoreValue> };
  updateMask?: { fieldPaths: string[] };
  updateTransforms?: { fieldPath: string; setToServerValue: "REQUEST_TIME" }[];
  currentDocument?: { exists: boolean };
}

/**
 * Ghi nguyên tử nhiều thao tác. Precondition `exists = false` không đạt thì ném
 * `FirestoreError` với `code == "ALREADY_EXISTS"`; tranh chấp ghi thì `code == "ABORTED"` (thử lại được).
 */
export async function commit(writes: Write[]): Promise<void> {
  const res = await request(`${apiBase()}/${databaseRoot()}/documents:commit`, {
    method: "POST",
    body: JSON.stringify({ writes }),
  });
  if (!res.ok) throw await toError(res);
  await res.body?.cancel();
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
