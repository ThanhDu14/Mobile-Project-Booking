import { errorResponse, HttpError, verifyIdToken } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import {
  deleteWrite,
  FirestoreError,
  updateWrite,
} from "../_shared/firestore.ts";
import {
  cancelRegistrationInTx,
  registrationPath,
} from "../_shared/dropin.ts";
import { closeWaitlistInTx } from "../_shared/waitlist.ts";
import { sendDropInNotification, type WaitlistNotification } from "../_shared/dropin-notify.ts";
import { decodeFields } from "../_shared/firestore.ts";
import { requireOwnerVenue } from "../_shared/owner-access.ts";

export interface OwnerCancelSessionDeps {
  firestore: Pick<typeof defaultFirestore, "get" | "runTransaction">;
  notify?: (notification: WaitlistNotification) => Promise<void>;
}

export async function handleOwnerCancelSession(
  req: Request,
  { firestore, notify = sendDropInNotification }: OwnerCancelSessionDeps = { firestore: defaultFirestore },
): Promise<Response> {
  if (req.method !== "POST") {
    return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
  }

  try {
    const token = await verifyIdToken(req);
    const body = await req.json().catch(() => ({}));

    const venueId = typeof body.venueId === "string" ? body.venueId.trim() : "";
    const sessionId = typeof body.sessionId === "string"
      ? body.sessionId.trim()
      : "";
    const reason = typeof body.reason === "string"
      ? body.reason.trim()
      : "Chủ sân hủy buổi chơi";

    if (!venueId || venueId.includes("/")) throw new HttpError(400, "INVALID_VENUE_ID", "Thiếu venueId");
    if (!sessionId || sessionId.includes("/")) {
      throw new HttpError(400, "INVALID_SESSION_ID", "Thiếu sessionId");
    }

    const now = new Date();
    const nowIso = now.toISOString();
    try {
      const closed = await firestore.runTransaction(async (tx) => {
        await requireOwnerVenue(tx, token.uid, venueId);
        // Lock session and registration query before collecting any writes.
        const session = await tx.get(`dropInSessions/${sessionId}`);
        if (!session || session.venueId !== venueId) {
          throw new HttpError(
            404,
            "SESSION_NOT_FOUND",
            "Không tìm thấy buổi chơi vãng lai",
          );
        }

        if (session.status === "CANCELLED") {
          throw new HttpError(
            409,
            "ALREADY_CANCELLED",
            "Buổi chơi vãng lai đã bị hủy trước đó",
          );
        }

        const slotIds = Array.isArray(session.slotIds) ? [...new Set(session.slotIds)] : [];
        for (const slotId of slotIds) {
          if (typeof slotId !== "string" || !slotId || slotId.includes("/")) {
            throw new HttpError(409, "SLOT_CONFLICT", "Dữ liệu khung giờ buổi chơi không hợp lệ");
          }
          const slot = await tx.get(`slots/${slotId}`);
          if (!slot || slot.venueId !== venueId || slot.sessionId !== sessionId ||
            slot.status !== "BLOCKED" || slot.blockReason !== "DROP_IN") {
            throw new HttpError(409, "SLOT_CONFLICT", "Khung giờ không còn thuộc buổi chơi này");
          }
        }
        const registrations = await tx.query("registrations", [], {
          parent: `dropInSessions/${sessionId}`,
        });
        // The helper reads before writing and must see the count from the previous cancellation.
        // Buffer its writes over a transaction snapshot so multiple registrations can be cancelled
        // without reading Firestore after a write or reopening/promoting a cancelled session.
        const sessionPath = `dropInSessions/${sessionId}`;
        const documents = new Map<string, Record<string, unknown>>([
          [sessionPath, { ...session, status: "CANCELLED" }],
          ...registrations.map((
            reg,
          ): [string, Record<string, unknown>] => [
            registrationPath(sessionId, reg.id),
            reg.data,
          ]),
        ]);
        const writes: defaultFirestore.Write[] = [];
        const bufferedTx = {
          get: (path: string) => Promise.resolve(documents.get(path) ?? null),
          write: (write: defaultFirestore.Write) => {
            if (!write.update) {
              throw new Error("Expected registration/session update");
            }
            const path = write.update.name.split("/documents/")[1];
            documents.set(path, {
              ...documents.get(path),
              ...decodeFields(write.update.fields),
            });
            if (path !== sessionPath) writes.push(write);
          },
        };
        for (const registration of registrations) {
          await cancelRegistrationInTx(
            bufferedTx,
            sessionId,
            registration.id,
            "OWNER",
            { forceFullRefund: true, now },
          );
        }
        const { closed } = await closeWaitlistInTx(
          tx, { ...session, id: sessionId }, "SESSION_CANCELLED", { now },
        );
        writes.push(
          updateWrite(sessionPath, {
            status: "CANCELLED",
            registeredCount: 0,
            waitlistTickets: [],
            waitlistCount: 0,
            cancelledAt: now,
            cancelReason: reason,
            updatedAt: now,
          }),
        );

        // Release all blocked slots
        for (const slotId of slotIds) {
          if (typeof slotId === "string" && slotId.trim()) {
            writes.push(deleteWrite(`slots/${slotId.trim()}`, true));
          }
        }

        for (const write of writes) tx.write(write);
        return closed;
      });
      for (const uid of closed) {
        try { await notify({ type: "DROPIN_WAITLIST_CLOSED", userId: uid, data: { sessionId, reason: "SESSION_CANCELLED" } }); }
        catch (error) { console.warn("[owner-cancel-session] notification delivery failed", error); }
      }
    } catch (e) {
      if (
        e instanceof FirestoreError &&
        (e.code === "ABORTED" || e.code === "NOT_FOUND")
      ) {
        throw new HttpError(
          409,
          "SESSION_CONFLICT",
          "Xung đột dữ liệu khi hủy buổi chơi vãng lai",
        );
      }
      throw e;
    }

    return new Response(
      JSON.stringify({
        success: true,
        sessionId,
        status: "CANCELLED",
        cancelledAt: nowIso,
      }),
      { status: 200, headers: { "Content-Type": "application/json" } },
    );
  } catch (err) {
    if (err instanceof HttpError) {
      return errorResponse(err.status, err.code, err.message);
    }
    const message = err instanceof Error
      ? err.message
      : "Lỗi máy chủ không xác định";
    return errorResponse(500, "INTERNAL_ERROR", message);
  }
}
