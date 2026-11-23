/** Offline-only planner. Input is an array of Firestore REST document snapshots; never connects or commits. */
export interface Snapshot {
  name: string;
  updateTime?: string;
  fields?: Record<string, { stringValue?: string; timestampValue?: string }>;
}

function record(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function validUpdateTime(value: string): boolean {
  const match = /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?Z$/.exec(value);
  if (!match || Number(value.slice(0, 4)) < 1) return false;
  const millis = `${match[1]}.${(match[2] ?? "").padEnd(3, "0").slice(0, 3)}Z`;
  return Number.isFinite(Date.parse(millis)) && new Date(millis).toISOString() === millis;
}

export function planTimestampRepair(documents: Snapshot[], expectedProject?: string) {
  if (!Array.isArray(documents)) throw new Error("Expected a snapshot array");
  let projectId = expectedProject;
  const versions = new Map<string, string>();
  // Validate the whole input before planning any writes; reject conflicts instead of choosing a copy.
  for (const document of documents) {
    if (!record(document) || typeof document.name !== "string"
      || (document.fields !== undefined && !record(document.fields))
      || (document.updateTime !== undefined && typeof document.updateTime !== "string")) {
      throw new Error("Malformed REST snapshot");
    }
    const createdAt = document.fields?.createdAt;
    if (createdAt !== undefined && (!record(createdAt)
      || (createdAt.stringValue !== undefined && typeof createdAt.stringValue !== "string")
      || (createdAt.timestampValue !== undefined && typeof createdAt.timestampValue !== "string")
      || (createdAt.stringValue !== undefined && createdAt.timestampValue !== undefined))) {
      throw new Error("Malformed createdAt field");
    }
    const project = /^projects\/([^/]+)\//.exec(document.name)?.[1];
    if (project) {
      projectId ??= project;
      if (project !== projectId) throw new Error("Snapshots must belong to one expected project");
    }
    const version = JSON.stringify([document.updateTime, createdAt?.stringValue, createdAt?.timestampValue]);
    if (versions.has(document.name) && versions.get(document.name) !== version) {
      throw new Error("Conflicting duplicate snapshot");
    }
    versions.set(document.name, version);
  }
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
    if (!validUpdateTime(document.updateTime)) { skipped.push({ name, reason: "invalid-concurrency-precondition" }); continue; }
    writes.push({
      update: { name, fields: { createdAt: { timestampValue: original } } },
      updateMask: { fieldPaths: ["createdAt"] },
      currentDocument: { updateTime: document.updateTime },
    });
  }
  return { mode: "REVIEW_ONLY_NO_COMMIT", projectId: projectId ?? null, writes, skipped };
}

if (import.meta.main) {
  if (Deno.args.length !== 2 || !Deno.args[1]) throw new Error("Usage: deno run --allow-read=<snapshot.json> plan-timestamp-repair.ts <snapshot.json> <expected-project-id>");
  const input = JSON.parse(await Deno.readTextFile(Deno.args[0]));
  if (!Array.isArray(input)) throw new Error("Expected a JSON array of REST snapshots");
  console.log(JSON.stringify(planTimestampRepair(input, Deno.args[1]), null, 2));
}
