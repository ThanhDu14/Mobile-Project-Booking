// Xác thực giả cho test các function thanh toán spec 050: token `Bearer <uid>`, `locked` → 403. Chỉ dùng trong test.
import { HttpError } from "./auth.ts";

export function fakeAuthenticate(req: Request): Promise<{ uid: string }> {
  const token = /^Bearer (\S+)$/.exec(req.headers.get("Authorization") ?? "")?.[1];
  if (!token) return Promise.reject(new HttpError(401, "UNAUTHENTICATED", "no token"));
  if (token === "locked") return Promise.reject(new HttpError(403, "ACCOUNT_LOCKED", "locked"));
  return Promise.resolve({ uid: token });
}

export function authedRequest(uid: string | null, body: unknown, headers: Record<string, string> = {}): Request {
  return new Request("http://localhost/fn", {
    method: "POST",
    headers: { "Content-Type": "application/json", ...(uid ? { Authorization: `Bearer ${uid}` } : {}), ...headers },
    body: JSON.stringify(body),
  });
}
