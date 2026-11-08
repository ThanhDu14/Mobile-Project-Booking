import { errorResponse, requireUser, toErrorResponse } from "../_shared/auth.ts";

export async function handleServerTime(req: Request): Promise<Response> {
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
  try {
    await requireUser(req);
    return new Response(JSON.stringify({ epochMillis: Date.now() }), {
      headers: { "Content-Type": "application/json", "Cache-Control": "no-store" },
    });
  } catch (e) {
    return toErrorResponse(e);
  }
}
