import { errorResponse, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { updateWrite } from "../_shared/firestore.ts";
import { notificationInboxId, prepareNotification } from "../_shared/notification-enqueue.ts";

export interface CompleteBookingsDeps {
  firestore?: Pick<typeof defaultFirestore, "query" | "runTransaction">;
  now?: () => Date;
  cronSecret?: string;
}

function defaultDeps(): Required<CompleteBookingsDeps> {
  return {
    firestore: defaultFirestore,
    now: () => new Date(),
    cronSecret: Deno.env.get("CRON_SECRET") ?? "",
  };
}

function getEpochMillis(val: unknown): number {
  if (val instanceof Date) return val.getTime();
  if (typeof val === "string") {
    const parsed = new Date(val).getTime();
    return isNaN(parsed) ? 0 : parsed;
  }
  if (typeof val === "number") return val;
  if (val && typeof val === "object" && "_seconds" in (val as Record<string, unknown>)) {
    return (val as { _seconds: number })._seconds * 1000;
  }
  return 0;
}

export async function handleCompleteBookings(
  req: Request,
  injectedDeps: CompleteBookingsDeps = {},
): Promise<Response> {
  const deps: Required<CompleteBookingsDeps> = {
    ...defaultDeps(),
    ...injectedDeps,
  };

  const secretHeader = req.headers.get("x-cron-secret");
  const hasAuth = req.headers.has("Authorization");

  if (deps.cronSecret && secretHeader !== deps.cronSecret && !hasAuth) {
    return errorResponse(401, "UNAUTHENTICATED", "Thiếu hoặc sai CRON_SECRET");
  }

  try {
    const serverNow = deps.now();
    const serverNowMs = serverNow.getTime();
    const nowIso = serverNow.toISOString();

    // Query bookings with status == "CONFIRMED"
    const confirmedDocs = await deps.firestore.query("bookings", [
      { field: "status", op: "EQUAL", value: "CONFIRMED" },
    ]);

    let completed = 0;
    let noShow = 0;
    for (const doc of confirmedDocs) {
      const scannedEndAt = getEpochMillis(doc.data.endAt);
      if (!(scannedEndAt > 0 && scannedEndAt <= serverNowMs)) continue;
      const status = await deps.firestore.runTransaction(async tx => {
        const path = `bookings/${doc.id}`;
        const current = await tx.get(path);
        if (current?.status !== "CONFIRMED") return null;
        const endAt = getEpochMillis(current.endAt);
        if (!(endAt > 0 && endAt <= serverNowMs)) return null;
        const checkedIn = current.checkedInAt != null && current.checkedInAt !== "";
        if (!checkedIn) {
          tx.write(updateWrite(path, { status: "NO_SHOW", updatedAt: serverNow }));
          return "NO_SHOW";
        }

        const uid = current.userId;
        const venueId = current.venueId;
        const validId = (value: unknown): value is string =>
          typeof value === "string" && value.length > 0 && !value.includes("/");
        if (validId(uid) && validId(venueId)) {
          const [venue, review] = await Promise.all([tx.get(`venues/${venueId}`), tx.get(`reviews/${doc.id}`)]);
          if (venue && venue.ownerId !== uid && !review) {
            const type = "REVIEW_REMINDER";
            const family = "review-reminder";
            const identity = JSON.stringify([doc.id, type]);
            const id = await notificationInboxId(family, identity, uid, type);
            const notification = await prepareNotification(tx, family, identity, uid, id, {
              type,
              title: "Đánh giá buổi chơi của bạn",
              body: "Bạn có thể đánh giá sân sau buổi chơi.",
              data: { bookingId: doc.id },
            }, serverNow);
            for (const write of notification) tx.write(write);
          }
        }
        tx.write(updateWrite(path, { status: "COMPLETED", completedAt: serverNow, updatedAt: serverNow }));
        return "COMPLETED";
      });
      if (status === "COMPLETED") completed++;
      if (status === "NO_SHOW") noShow++;
    }

    return Response.json({
      success: true,
      scanned: confirmedDocs.length,
      processed: completed + noShow,
      completed,
      noShow,
      timestamp: nowIso,
    });
  } catch (error) {
    return toErrorResponse(error);
  }
}
