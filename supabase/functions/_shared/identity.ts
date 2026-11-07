// Custom claim và trạng thái tài khoản Firebase Auth qua Identity Toolkit REST (A viết, A và D dùng;
// contracts/edge-function-on-signup.md §5).
//
// D dùng: review-owner-application gọi `mergeClaims(uid, { appRole: "OWNER" })` TRƯỚC khi ghi
// users.role; set-account-status gọi `setUserDisabled` cùng với ghi accountStatus.
import { getAccessToken, projectId, SCOPE_CLOUD_PLATFORM, SCOPE_IDENTITY_TOOLKIT } from "./firestore.ts";

/** Firebase giới hạn claim JSON dưới 1000 byte. */
const MAX_CLAIMS_BYTES = 1000;

function emulatorHost(): string | undefined {
  return Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST") || undefined;
}

function accountsUrl(method: "lookup" | "update"): string {
  const host = emulatorHost();
  const base = host ? `http://${host}/identitytoolkit.googleapis.com` : "https://identitytoolkit.googleapis.com";
  return `${base}/v1/projects/${projectId()}/accounts:${method}`;
}

async function call<T>(method: "lookup" | "update", body: Record<string, unknown>): Promise<T> {
  const token = emulatorHost() ? "owner" : await getAccessToken([SCOPE_IDENTITY_TOOLKIT, SCOPE_CLOUD_PLATFORM]);
  const res = await fetch(accountsUrl(method), {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(`Identity Toolkit accounts:${method} lỗi ${res.status}: ${text}`);
  return JSON.parse(text) as T;
}

export async function getCustomClaims(uid: string): Promise<Record<string, unknown>> {
  const r = await call<{ users?: { customAttributes?: string }[] }>("lookup", { localId: [uid] });
  const user = r.users?.[0];
  if (!user) throw new Error(`Không tìm thấy người dùng ${uid}`);
  return user.customAttributes ? JSON.parse(user.customAttributes) : {};
}

/** Ghi đè toàn bộ claim. Nên gọi qua `mergeClaims` để không làm mất claim khác. */
export async function setCustomClaims(uid: string, claims: Record<string, unknown>): Promise<void> {
  const json = JSON.stringify(claims);
  if (new TextEncoder().encode(json).length >= MAX_CLAIMS_BYTES) {
    throw new RangeError(`Claim của ${uid} phải nhỏ hơn ${MAX_CLAIMS_BYTES} byte`);
  }
  await call("update", { localId: uid, customAttributes: json });
}

/** Đọc claim hiện có, gộp `patch` vào rồi ghi lại. */
export async function mergeClaims(uid: string, patch: Record<string, unknown>): Promise<void> {
  await setCustomClaims(uid, { ...(await getCustomClaims(uid)), ...patch });
}

/** Bật/tắt cờ disabled: tài khoản bị tắt không đăng nhập hay làm mới token được. */
export async function setUserDisabled(uid: string, disabled: boolean): Promise<void> {
  await call("update", { localId: uid, disableUser: disabled });
}
