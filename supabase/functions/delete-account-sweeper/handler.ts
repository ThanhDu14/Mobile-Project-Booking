// Logic của delete-account-sweeper (spec 011 US6, contracts/edge-functions-account.md §2). pg_cron gọi mỗi 5 phút
// với header `x-cron-secret` (giống dropin-close của 081).
import { errorResponse, toErrorResponse } from "../_shared/auth.ts";
import {
  type DeletionDeps,
  defaultDeletionDeps,
  markStep,
  runCleanup,
  runStep,
} from "../_shared/account-deletion.ts";

const MAX_RECORDS = 5;
const BUDGET_MS = 120_000;

export type SweeperDeps = DeletionDeps & { cronSecret: string };

function defaultSweeperDeps(): SweeperDeps {
  return { ...defaultDeletionDeps(), cronSecret: Deno.env.get("CRON_SECRET") ?? "" };
}

const time = (v: unknown) => (v instanceof Date ? v.getTime() : 0);

export async function handleSweeper(req: Request, deps: SweeperDeps = defaultSweeperDeps()): Promise<Response> {
  const secret = req.headers.get("x-cron-secret");
  if (!deps.cronSecret || secret !== deps.cronSecret) {
    return errorResponse(401, "UNAUTHENTICATED", "Thiếu hoặc sai CRON_SECRET");
  }
  try {
    const start = Date.now();
    const deadline = start + BUDGET_MS;
    const committed = (await deps.firestore.query("accountDeletions", [
      { field: "state", op: "EQUAL", value: "COMMITTED" },
    ])).sort((a, b) => time(a.data.requestedAt) - time(b.data.requestedAt)).slice(0, MAX_RECORDS);

    let done = 0;
    for (const record of committed) {
      if (Date.now() > deadline) break;
      const outcome = await runCleanup(record.id, deadline, deps);
      if (outcome === "DONE") done++;
    }

    // Lượt quét cuối: dọn dữ liệu do ID token cũ (còn hạn tối đa 1 giờ) ghi thêm sau khi xóa.
    const now = deps.now().getTime();
    const due = (await deps.firestore.query("accountDeletions", [{ field: "state", op: "EQUAL", value: "DONE" }]))
      .filter((d) => time(d.data.finalSweepAfter) <= now && !(d.data.steps as Record<string, string>)?.finalSweep);
    let finalSweeps = 0;
    for (const record of due) {
      if (Date.now() > deadline) break;
      await markStep(record.id, "finalSweep", await runStep("finalSweep", record.id, record.data, deps), deps);
      finalSweeps++;
    }
    return Response.json({ processed: committed.length, done, finalSweeps });
  } catch (e) {
    return toErrorResponse(e);
  }
}
