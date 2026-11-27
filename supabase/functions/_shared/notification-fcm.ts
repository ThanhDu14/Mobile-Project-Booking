import { getAccessToken, projectId } from "./firestore.ts";
import type { Sender } from "./notification-worker.ts";

/** No provider response/token is logged or persisted. Only UNREGISTERED invalidates a token. */
export function httpV1Sender(
  request: typeof fetch = fetch,
  accessToken: () => Promise<string> = () => getAccessToken(["https://www.googleapis.com/auth/firebase.messaging"]),
  project: () => string = projectId,
): Sender {
  return async (token, data) => {
    const response = await request(`https://fcm.googleapis.com/v1/projects/${project()}/messages:send`, {
      method: "POST", signal: AbortSignal.timeout(15_000),
      headers: { Authorization: `Bearer ${await accessToken()}`, "Content-Type": "application/json" },
      body: JSON.stringify({ message: { token, data, android: { priority: "high" } } }),
    });
    if (response.ok) { await response.body?.cancel(); return { kind: "accepted" }; }
    const error = await response.json().catch(() => ({}));
    if (error?.error?.details?.some((d: Record<string, unknown>) =>
      d["@type"] === "type.googleapis.com/google.firebase.fcm.v1.FcmError" && d.errorCode === "UNREGISTERED")) return { kind: "unregistered" };
    if (response.status === 429 || response.status >= 500) {
      const header = response.headers.get("Retry-After") ?? "";
      const retryAfterMs = /^\d+$/.test(header) ? Number(header) * 1000 : Math.max(0, Date.parse(header) - Date.now()) || 0;
      return { kind: "retry", retryAfterMs };
    }
    return { kind: "failed" };
  };
}
