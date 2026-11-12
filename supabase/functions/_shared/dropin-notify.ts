// Gửi thông báo của hàng chờ (C, spec 081; research R12) qua Edge Function `send-notification` của D.
// Luôn gọi SAU khi commit; người gọi bắt lỗi và chỉ ghi log (không hoàn tác việc đã làm).

export interface WaitlistNotification {
  type: "DROPIN_SPOT_OPENED" | "DROPIN_WAITLIST_CLOSED";
  userId: string;
  data: Record<string, unknown>;
}

/**
 * Gọi `send-notification` bằng service role với `{ type, userId, data }` (cùng envelope với `register-drop-in`).
 * TODO(D, spec 100): envelope chưa chốt; chưa có function thì 404 và người gọi chỉ ghi log.
 */
export async function sendDropInNotification(n: WaitlistNotification): Promise<void> {
  const url = Deno.env.get("SUPABASE_URL");
  const key = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
  if (!url || !key) {
    console.warn("[dropin] thiếu SUPABASE_URL/SUPABASE_SERVICE_ROLE_KEY, bỏ qua thông báo", n.type);
    return;
  }
  const res = await fetch(`${url}/functions/v1/send-notification`, {
    method: "POST",
    headers: { Authorization: `Bearer ${key}`, "Content-Type": "application/json" },
    body: JSON.stringify(n),
  });
  await res.body?.cancel();
  if (!res.ok) throw new Error(`send-notification ${res.status}`);
}
