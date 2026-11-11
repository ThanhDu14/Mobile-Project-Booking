import { errorResponse, toErrorResponse } from "../_shared/auth.ts";
import * as defaultFirestore from "../_shared/firestore.ts";
import { updateWrite } from "../_shared/firestore.ts";

export interface CompleteBookingsDeps {
  firestore?: Pick<typeof defaultFirestore, "query" | "commit" | "get">;
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
    const writes: defaultFirestore.Write[] = [];

    for (const doc of confirmedDocs) {
      const data = doc.data;
      const endAtMs = getEpochMillis(data.endAt);
      if (endAtMs > 0 && endAtMs <= serverNowMs) {
        const isCheckedIn = data.checkedInAt !== null && data.checkedInAt !== undefined && data.checkedInAt !== "";
        if (isCheckedIn) {
          writes.push(
            updateWrite(`bookings/${doc.id}`, {
              status: "COMPLETED",
              completedAt: nowIso,
              updatedAt: nowIso,
            })
          );
          completed++;
        } else {
          writes.push(
            updateWrite(`bookings/${doc.id}`, {
              status: "NO_SHOW",
              updatedAt: nowIso,
            })
          );
          noShow++;
        }
      }
    }

    if (writes.length > 0) {
      await deps.firestore.commit(writes);
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
