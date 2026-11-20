import { errorResponse, toErrorResponse } from "../_shared/auth.ts";
import * as fs from "../_shared/firestore.ts";
import { closeWaitlistInTx } from "../_shared/waitlist.ts";

export async function handleDropinClose(req: Request, deps = {
  firestore: fs as Pick<typeof fs, "query" | "runTransaction">,
  cronSecret: Deno.env.get("CRON_SECRET") ?? "",
  now: () => new Date(),
}): Promise<Response> {
  if (req.method !== "POST") {
    return errorResponse(405, "METHOD_NOT_ALLOWED", "Chỉ hỗ trợ POST");
  }
  if (
    !deps.cronSecret || req.headers.get("x-cron-secret") !== deps.cronSecret
  ) {
    return errorResponse(
      401,
      "UNAUTHENTICATED",
      "Thiếu hoặc sai CRON_SECRET",
    );
  }
  try {
    const now = deps.now();
    const sessions = await deps.firestore.query("dropInSessions", [{
      field: "status",
      op: "IN",
      value: ["OPEN", "FULL"],
    }]);
    let closedSessions = 0, closedEntries = 0, completedSessions = 0;
    for (const row of sessions) {
      if (
        !(row.data.startAt instanceof Date) ||
        row.data.startAt.getTime() > now.getTime() + 30 * 60_000
      ) continue;
      const result = await deps.firestore.runTransaction(async (tx) => {
        const session = await tx.get(row.path);
        if (
          !session || !["OPEN", "FULL"].includes(String(session.status)) ||
          !(session.startAt instanceof Date) ||
          session.startAt.getTime() > now.getTime() + 30 * 60_000
        ) return { closed: 0, completed: false };
        const { closed } = await closeWaitlistInTx(
          tx,
          { ...session, id: row.id },
          "CLOSED",
          now,
        );
        const completed = session.endAt instanceof Date && session.endAt <= now;
        if (completed) {
          tx.write(
            fs.updateWrite(row.path, { status: "COMPLETED", updatedAt: now }),
          );
        }
        return { closed: closed.length, completed };
      });
      if (result.closed) closedSessions++;
      closedEntries += result.closed;
      if (result.completed) completedSessions++;
    }
    return Response.json({ closedSessions, closedEntries, completedSessions });
  } catch (err) {
    return toErrorResponse(err);
  }
}
