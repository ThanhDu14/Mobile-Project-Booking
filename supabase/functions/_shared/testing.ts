// Hàm hỗ trợ test trên Firebase Emulator (Auth 9099, Firestore 8080). Không dùng trong function.
import { projectId } from "./firestore.ts";

export const AUTH_HOST = () => Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST") ?? "127.0.0.1:9099";
export const FIRESTORE_HOST = () => Deno.env.get("FIRESTORE_EMULATOR_HOST") ?? "127.0.0.1:8080";

const owner = { Authorization: "Bearer owner", "Content-Type": "application/json" };

function authUrl(path: string): string {
  return `http://${AUTH_HOST()}/identitytoolkit.googleapis.com/v1/${path}`;
}

async function json<T>(res: Response): Promise<T> {
  const body = await res.text();
  if (!res.ok) throw new Error(`${res.status} ${body}`);
  return JSON.parse(body) as T;
}

/** Xóa toàn bộ người dùng trong Auth Emulator và document trong Firestore Emulator. */
export async function clearEmulators(): Promise<void> {
  const pid = projectId();
  await (await fetch(`http://${AUTH_HOST()}/emulator/v1/projects/${pid}/accounts`, { method: "DELETE" })).body
    ?.cancel();
  await (await fetch(`http://${FIRESTORE_HOST()}/emulator/v1/projects/${pid}/databases/(default)/documents`, {
    method: "DELETE",
  })).body?.cancel();
}

export interface TestUser {
  uid: string;
  idToken: string;
}

/** Đăng ký email/mật khẩu qua REST của Emulator, trả ID token thật của Emulator. */
export async function signUpEmail(email: string, password = "abc12345", displayName?: string): Promise<TestUser> {
  const r = await json<{ localId: string; idToken: string }>(
    await fetch(authUrl("accounts:signUp?key=fake-api-key"), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ email, password, displayName, returnSecureToken: true }),
    }),
  );
  return { uid: r.localId, idToken: r.idToken };
}

/** Đăng nhập lại để lấy token mới (có claim mới nhất). */
export async function signInEmail(email: string, password = "abc12345"): Promise<Response> {
  return await fetch(authUrl("accounts:signInWithPassword?key=fake-api-key"), {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password, returnSecureToken: true }),
  });
}

/** Tạo người dùng bằng quyền admin của Emulator (dùng cho tài khoản số điện thoại). */
export async function createUser(fields: Record<string, unknown>): Promise<string> {
  const r = await json<{ localId: string }>(
    await fetch(authUrl(`projects/${projectId()}/accounts`), {
      method: "POST",
      headers: owner,
      body: JSON.stringify(fields),
    }),
  );
  return r.localId;
}

/**
 * Token không ký theo dạng của Auth Emulator (`alg: none`). Dùng để giả các tình huống khó tạo bằng
 * REST, ví dụ token số điện thoại hoặc token không có email lẫn số.
 */
export function emulatorToken(claims: Record<string, unknown>): string {
  const pid = projectId();
  const now = Math.floor(Date.now() / 1000);
  const payload = {
    iss: `https://securetoken.google.com/${pid}`,
    aud: pid,
    auth_time: now,
    iat: now,
    exp: now + 3600,
    firebase: { sign_in_provider: "password", identities: {} },
    ...claims,
  };
  const b64 = (o: unknown) =>
    btoa(JSON.stringify(o)).replace(/=+$/, "").replace(/\+/g, "-").replace(/\//g, "_");
  return `${b64({ alg: "none", typ: "JWT" })}.${b64(payload)}.`;
}

export function bearer(token: string, init: RequestInit = {}): Request {
  return new Request("http://localhost/functions/v1/on-signup", {
    method: "POST",
    ...init,
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json", ...init.headers },
  });
}
