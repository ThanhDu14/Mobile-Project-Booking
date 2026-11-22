/** Offline-only planner. Input is an array of Firestore REST document snapshots; never connects or commits. */
export interface Snapshot {
  name: string;
  updateTime?: string;
  fields?: Record<string, { stringValue?: string; timestampValue?: string }>;
}

export function planTimestampRepair(documents: Snapshot[]) {
  const writes: unknown[] = [];
  const skipped: { name: string; reason: string }[] = [];
  const seen = new Set<string>();
  for (const document of documents) {
    const name = document.name;
    if (!/^projects\/[^/]+\/databases\/\(default\)\/documents\/users\/[^/]+\/notifications\/[^/]+$/.test(name)) {
      skipped.push({ name, reason: "outside-inbox" }); continue;
    }
    if (seen.has(name)) { skipped.push({ name, reason: "duplicate-input" }); continue; }
    seen.add(name);
    const value = document.fields?.createdAt;
    if (value?.timestampValue) { skipped.push({ name, reason: "already-timestamp" }); continue; }
    const original = value?.stringValue;
    // All affected producers used Date.toISOString(). Reject ambiguous zones and calendar rollovers.
    if (!original || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/.test(original)
      || !Number.isFinite(Date.parse(original)) || new Date(original).toISOString() !== original) {
      skipped.push({ name, reason: "manual-review-invalid-date" }); continue;
    }
    if (!document.updateTime) { skipped.push({ name, reason: "missing-concurrency-precondition" }); continue; }
    writes.push({
      update: { name, fields: { createdAt: { timestampValue: original } } },
      updateMask: { fieldPaths: ["createdAt"] },
      currentDocument: { updateTime: document.updateTime },
    });
  }
  return { mode: "REVIEW_ONLY_NO_COMMIT", writes, skipped };
}

if (import.meta.main) {
  if (Deno.args.length !== 1) throw new Error("Usage: deno run --allow-read=<snapshot.json> plan-timestamp-repair.ts <snapshot.json>");
  const input = JSON.parse(await Deno.readTextFile(Deno.args[0]));
  if (!Array.isArray(input)) throw new Error("Expected a JSON array of REST snapshots");
  console.log(JSON.stringify(planTimestampRepair(input), null, 2));
}
