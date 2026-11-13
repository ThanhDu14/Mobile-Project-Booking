import { requireCurrentAdmin } from "../_shared/admin-authorization.ts";
import { HttpError, requireRole, requireUser, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { updateWrite } from "../_shared/firestore.ts";

export type AdminVenueStatus = "LOCKED" | "ACTIVE" | "HIDDEN";

export interface AdminManageVenueDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "runTransaction">;
}

function json(status: number, data: unknown): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

export function validateVenueStatus(status: unknown): AdminVenueStatus {
  if (typeof status !== "string") {
    throw new HttpError(400, "INVALID_STATUS", "Trạng thái cơ sở không hợp lệ");
  }
  const upper = status.trim().toUpperCase();
  if (upper !== "LOCKED" && upper !== "ACTIVE" && upper !== "HIDDEN") {
    throw new HttpError(400, "INVALID_STATUS", "Trạng thái chỉ có thể là LOCKED, ACTIVE hoặc HIDDEN");
  }
  return upper as AdminVenueStatus;
}

export async function handleAdminManageVenue(
  req: Request,
  deps: AdminManageVenueDeps = { firestore: defaultFirestore },
): Promise<Response> {
  if (req.method !== "POST") {
    return json(405, { error: { code: "METHOD_NOT_ALLOWED", message: "Chỉ hỗ trợ POST" } });
  }

  try {
    const admin = await requireUser(req);
    requireRole(admin, "ADMIN");
    requireCurrentAdmin(admin);

    const body = await req.json().catch(() => ({})) as Record<string, unknown>;
    const venueId = typeof body.venueId === "string" ? body.venueId.trim() : "";
    if (!venueId) {
      throw new HttpError(400, "INVALID_VENUE_ID", "Mã cơ sở không hợp lệ");
    }

    const targetStatus = validateVenueStatus(body.status);

    const outcome = await deps.firestore.runTransaction(async (tx) => {
      const venue = await tx.get(`venues/${venueId}`);
      if (!venue) {
        throw new HttpError(404, "VENUE_NOT_FOUND", "Không tìm thấy cơ sở");
      }

      const currentStatus = (venue.status as string | undefined) ?? "ACTIVE";
      if (currentStatus === targetStatus) {
        return { alreadyCommitted: true };
      }

      const nowIso = new Date().toISOString();
      tx.write(updateWrite(`venues/${venueId}`, {
        status: targetStatus,
        updatedAt: nowIso,
      }));

      // NOTE: Per FR-009 / FR-012 / T036: courts/price rules/bookings giữ nguyên
      return { alreadyCommitted: false };
    });

    return json(200, {
      success: true,
      venueId,
      status: targetStatus,
      alreadyCommitted: outcome.alreadyCommitted,
    });
  } catch (err) {
    return toErrorResponse(err);
  }
}
