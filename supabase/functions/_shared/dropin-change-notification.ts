import { HttpError } from "./auth.ts";
import { UUID } from "./dropin-tx.ts";
import * as fs from "./firestore.ts";
import { notificationInboxId, prepareNotification, sha256 } from "./notification-enqueue.ts";

export function requireDropInCommandId(value: unknown): string {
  if (typeof value !== "string" || !UUID.test(value)) {
    throw new HttpError(400, "INVALID_REQUEST_ID", "requestId phải là UUID");
  }
  return value.toLowerCase();
}

export async function readDropInCommand(
  tx: fs.FirestoreTransaction, sessionId: string, requestId: string,
  actorUid: string, payload: unknown, now: Date,
) {
  const path = `dropInSessions/${sessionId}/notificationCommands/${requestId}`;
  const fingerprint = await sha256(JSON.stringify(payload));
  const previous = await tx.get(path);
  if (previous && (previous.actorUid !== actorUid || previous.fingerprint !== fingerprint)) {
    throw new HttpError(409, "REQUEST_CONFLICT", "Mã yêu cầu đã được sử dụng");
  }
  return {
    previous: previous?.result as Record<string, unknown> | undefined,
    journal: (result: Record<string, unknown>) => fs.createWrite(path, { actorUid, fingerprint, result, createdAt: now }),
  };
}

const type = "DROPIN_CHANGED";
const family = "dropin-change";
const pageSize = 100;
const retentionMs = 7 * 24 * 60 * 60 * 1000;
const wireSize = (writes: fs.Write[]) => new TextEncoder().encode(JSON.stringify(writes)).length;

export async function prepareDropInChanged(
  tx: fs.FirestoreTransaction, sourceWrites: fs.Write[],
  registrations: fs.QueryDocument[],
  event: { sessionId: string; requestId: string; actorUid: string; cancelled: boolean; now: Date },
): Promise<fs.Write[]> {
  const { sessionId, requestId, actorUid, cancelled, now } = event;
  const uids = [...new Set(registrations.filter(row => row.data.status === "REGISTERED" && row.id !== actorUid)
    .map(row => row.id))].sort();
  if (!uids.length) return [];
  const identity = JSON.stringify([sessionId, requestId, type]);
  const content = {
    type,
    title: cancelled ? "Buổi chơi vãng lai đã bị hủy" : "Buổi chơi vãng lai đã đổi giờ",
    body: cancelled ? "Chủ sân đã hủy buổi chơi bạn đăng ký." : "Mở buổi chơi để xem giờ mới.",
    data: { sessionId },
  };
  if (sourceWrites.length + 2 * uids.length <= 400) {
    const rows = await writeRecipients(tx, identity, uids, content, now);
    if (wireSize([...sourceWrites, ...rows]) <= 8 * 1024 * 1024) return rows;
  }
  const id = await sha256(JSON.stringify([family, identity]));
  const path = `notificationFanouts/${id}`;
  const pageCount = Math.ceil(uids.length / pageSize);
  const writes = [fs.createWrite(path, { family, identity, actorUid, content, createdAt: now, state: "PENDING", cursor: 0, pageCount })];
  for (let page = 0; page < pageCount; page++) {
    writes.push(fs.createWrite(`${path}/recipients/${page}`, { uids: uids.slice(page * pageSize, (page + 1) * pageSize) }));
  }
  return writes;
}

async function writeRecipient(tx: fs.FirestoreTransaction, identity: string, uid: string,
  content: { type: string; title: string; body: string; data: Record<string, unknown> }, now: Date) {
  const id = await notificationInboxId(family, identity, uid, type);
  return await prepareNotification(tx, family, identity, uid, id, content, now);
}

async function writeRecipients(tx: fs.FirestoreTransaction, identity: string, uids: string[],
  content: Parameters<typeof writeRecipient>[3], now: Date): Promise<fs.Write[]> {
  const writes: fs.Write[] = [];
  for (let i = 0; i < uids.length; i += 8) {
    const batch = await Promise.all(uids.slice(i, i + 8).map(uid => writeRecipient(tx, identity, uid, content, now)));
    writes.push(...batch.flat());
  }
  return writes;
}

type FanoutStore = Pick<typeof fs, "query" | "runTransaction">;

/** Each page writes inbox/jobs and advances its cursor atomically. FCM runs in the D1 worker. */
export async function processDropInChangeFanouts(store: FanoutStore, now: Date): Promise<number> {
  const jobs = await store.query("notificationFanouts", [{ field: "state", op: "EQUAL", value: "PENDING" }], { limit: 5 });
  let processed = 0;
  for (const job of jobs) {
    if (job.data.family !== family) continue;
    const advanced = await store.runTransaction(async tx => {
      const current = await tx.get(job.path);
      if (current?.state !== "PENDING") return false;
      const cursor = Number(current.cursor);
      const page = await tx.get(`${job.path}/recipients/${cursor}`);
      if (!Array.isArray(page?.uids) || page.uids.length > pageSize) throw new Error("Missing drop-in recipient page");
      const uids = page.uids;
      if (!uids.every((uid): uid is string => typeof uid === "string")) throw new Error("Invalid drop-in recipient");
      const writes = await writeRecipients(tx, String(current.identity), uids,
        current.content as Parameters<typeof writeRecipient>[3], current.createdAt as Date);
      for (const write of writes) tx.write(write);
      const done = cursor + 1 === current.pageCount;
      tx.write(fs.updateWrite(job.path, {
        cursor: cursor + 1, state: done ? "DONE" : "PENDING",
        ...(done ? { cleanupAfter: new Date(now.getTime() + retentionMs) } : {}),
      }));
      return true;
    });
    if (advanced) processed++;
  }
  const expired = await store.query("notificationFanouts", [
    { field: "cleanupAfter", op: "LESS_THAN_OR_EQUAL", value: now },
  ], { limit: 5 });
  for (const job of expired) {
    if (job.data.family !== family || job.data.state !== "DONE") continue;
    await store.runTransaction(async tx => {
      const current = await tx.get(job.path);
      if (current?.state !== "DONE") return;
      const pages = await tx.query("recipients", [], { parent: job.path, limit: 100 });
      for (const page of pages) tx.write(fs.deleteWrite(page.path, false));
      if (pages.length < 100) tx.write(fs.deleteWrite(job.path, false));
    });
  }
  return processed;
}
