import { errorResponse, HttpError, verifyIdToken } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { updateWrite } from "../_shared/firestore.ts";

export interface SyncVenuePriceDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "list" | "commit">;
}

export async function handleSyncVenuePrice(
  req: Request,
  { firestore }: SyncVenuePriceDeps = { firestore: defaultFirestore },
): Promise<Response> {
  if (req.method !== "POST") return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
  try {
    const token = await verifyIdToken(req);
    const body = await req.json().catch(() => ({}));
    const venueId = typeof body.venueId === "string" ? body.venueId.trim() : "";
    if (!venueId) throw new HttpError(400, "INVALID_VENUE_ID", "Thiếu hoặc sai venueId");

    const venue = await firestore.get(`venues/${venueId}`);
    if (!venue) throw new HttpError(404, "VENUE_NOT_FOUND", "Không tìm thấy cơ sở");

    if (venue.ownerId !== token.uid && token.appRole !== "ADMIN") {
      throw new HttpError(403, "FORBIDDEN", "Bạn không có quyền quản lý cơ sở này");
    }

    const rules = await firestore.list(`venues/${venueId}/priceRules`);
    const prices = rules
      .map((r) => r.pricePerHour)
      .filter((p): p is number => typeof p === "number" && p > 0);

    const minPricePerHour = prices.length > 0 ? Math.min(...prices) : null;

    await firestore.commit([
      updateWrite(`venues/${venueId}`, { minPricePerHour }, ["updatedAt"]),
    ]);

    return Response.json({ venueId, minPricePerHour });
  } catch (e) {
    if (e instanceof HttpError) return errorResponse(e.status, e.code, e.message);
    const msg = e instanceof Error ? e.message : String(e);
    return errorResponse(500, "INTERNAL_ERROR", msg);
  }
}
