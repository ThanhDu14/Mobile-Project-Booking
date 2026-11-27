import { HttpError } from "./auth.ts";
import * as fs from "./firestore.ts";
import { requireRequestId } from "./group-tx.ts";
import { groupPath, memberPath, type GroupEvent } from "./groups.ts";
import { notificationInboxId, prepareNotification, sha256, type InboxContent } from "./notification-enqueue.ts";

const family = "group";
const pageSize = 100;
const snapshotLimit = 400;
const sourceBudget = 8 * 1024 * 1024;
const retentionMs = 7 * 24 * 60 * 60 * 1000;
type Members = "MEMBERS" | "MANAGERS";
type Audience = Members | "ATTENDEES" | "TARGET";

/** Older clients omitted the ID. Their accepted source mutations still get durable fanout. */
export function groupCommandId(body: Record<string, unknown>): string {
  return body.requestId === undefined ? crypto.randomUUID() : requireRequestId(body);
}

export async function groupNotificationCommand<T>(
  tx: fs.FirestoreTransaction, groupId: string, requestId: string, actorUid: string,
  payload: unknown, now: Date,
) {
  const path = `${groupPath(groupId)}/notificationCommands/${requestId}`;
  const fingerprint = await sha256(JSON.stringify(payload));
  const record = await tx.get(path);
  if (record && (record.actorUid !== actorUid || record.fingerprint !== fingerprint)) {
    throw new HttpError(409, "REQUEST_ID_CONFLICT", "requestId đã được dùng cho thao tác khác");
  }
  return {
    previous: record?.result as T | undefined,
    write: (result: T) => fs.createWrite(path, { actorUid, fingerprint, result, createdAt: now }),
  };
}

const groupContent: Record<GroupEvent, [string, string]> = {
  JOIN_REQUESTED: ["Yêu cầu tham gia nhóm", "Có người gửi yêu cầu tham gia nhóm."],
  JOIN_APPROVED: ["Yêu cầu tham gia nhóm đã được duyệt", "Bạn đã trở thành thành viên của nhóm."],
  JOIN_REJECTED: ["Yêu cầu tham gia nhóm bị từ chối", "Yêu cầu tham gia nhóm của bạn chưa được chấp nhận."],
  REMOVED: ["Bạn đã bị mời ra khỏi nhóm", "Quyền thành viên của bạn trong nhóm đã kết thúc."],
  EVENT_CREATED: ["Sự kiện nhóm mới", "Một sự kiện mới đã được đăng trong nhóm."],
  EVENT_UPDATED: ["Sự kiện nhóm đã thay đổi", "Giờ hoặc địa điểm của sự kiện bạn tham gia đã thay đổi."],
  EVENT_DELETED: ["Sự kiện nhóm đã bị hủy", "Sự kiện bạn tham gia đã bị xóa."],
};

export function groupActivity(event: GroupEvent, groupId: string, postId?: string): InboxContent {
  const [title, body] = groupContent[event];
  return { type: "GROUP_ACTIVITY", title, body, data: { event, groupId, ...(postId ? { postId } : {}) } };
}

function queryMembers(tx: fs.FirestoreTransaction, groupId: string, audience: Members, limit: number, after?: string) {
  const parent = groupPath(groupId);
  return tx.query("members", audience === "MANAGERS"
    ? [{ field: "role", op: "IN", value: ["OWNER", "ADMIN"] }] : [], {
    parent, limit, orderBy: { field: "__name__" },
    ...(after ? { startAfter: [{ referenceValue: fs.documentName(memberPath(groupId, after)) }] } : {}),
  });
}

interface GroupFanout {
  groupId: string;
  actorUid: string;
  sourceId: string;
  audience: Audience;
  content: InboxContent;
  now: Date;
  recipients?: string[];
}

/** Captures bounded audiences; large live sets are paged by document ID at delivery time. */
export async function enqueueGroupFanout(tx: fs.FirestoreTransaction, sourceWrites: fs.Write[], event: GroupFanout) {
  const { groupId, actorUid, sourceId, audience, content, now } = event;
  const fixedAudience = audience === "TARGET" || audience === "ATTENDEES";
  let recipients = event.recipients;
  if (!recipients && !fixedAudience) {
    const rows = await queryMembers(tx, groupId, audience, snapshotLimit + 1);
    if (rows.length <= snapshotLimit) recipients = rows.map(row => row.id);
  }
  if (!fixedAudience && recipients && recipients.length > snapshotLimit) recipients = undefined;
  const identity = JSON.stringify([groupId, content.type, content.data.event ?? content.data.chatId, sourceId]);
  const path = `notificationFanouts/${await sha256(JSON.stringify([family, identity]))}`;
  const head = { family, identity, groupId, actorUid, audience, content, createdAt: now, state: "PENDING", cursor: 0 };
  const uids = recipients && [...new Set(recipients)].filter(uid => uid !== actorUid).sort();
  if (uids?.length === 0) return;
  if (uids) {
    const pageCount = Math.ceil(uids.length / pageSize);
    const writes = [fs.createWrite(path, { ...head, mode: "SNAPSHOT", pageCount })];
    for (let page = 0; page < pageCount; page++) {
      writes.push(fs.createWrite(`${path}/recipients/${page}`, { uids: uids.slice(page * pageSize, (page + 1) * pageSize) }));
    }
    // Attendance cannot be reconstructed after post deletion. Its pages fit within the
    // same transaction that already writes one source document per attendee.
    if (fixedAudience || (sourceWrites.length + writes.length <= 400 &&
      new TextEncoder().encode(JSON.stringify([...sourceWrites, ...writes])).length <= sourceBudget)) {
      sourceWrites.push(...writes);
      return;
    }
  }
  if (audience !== "MEMBERS" && audience !== "MANAGERS") throw new Error("Missing group recipient snapshot");
  sourceWrites.push(fs.createWrite(path, { ...head, mode: "SCAN", after: null }));
}

type FanoutStore = Pick<typeof fs, "query" | "runTransaction">;

async function materializePage(tx: fs.FirestoreTransaction, path: string, now: Date): Promise<boolean> {
  const job = await tx.get(path);
  if (job?.state !== "PENDING") return false;
  const { groupId, actorUid, audience, content, createdAt, identity } = job as unknown as
    Omit<GroupFanout, "now" | "sourceId"> & { createdAt: Date; identity: string };
  const cursor = Number(job.cursor);
  const scan = job.mode === "SCAN";
  let uids: string[], done: boolean, after: string | null = null;
  if (scan) {
    if (audience !== "MEMBERS" && audience !== "MANAGERS") throw new Error("Invalid group scan audience");
    const rows = await queryMembers(tx, groupId, audience, pageSize, typeof job.after === "string" ? job.after : undefined);
    uids = rows.filter(({ data }) => data.joinedAt instanceof Date && data.joinedAt <= createdAt).map(row => row.id);
    done = rows.length < pageSize;
    after = rows.at(-1)?.id ?? null;
  } else {
    const snapshot = await tx.get(`${path}/recipients/${cursor}`);
    if (!Array.isArray(snapshot?.uids) || snapshot.uids.length > pageSize ||
      !snapshot.uids.every(uid => typeof uid === "string")) throw new Error("Invalid group recipient page");
    uids = snapshot.uids;
    done = cursor + 1 === job.pageCount;
  }

  const group = audience === "TARGET" ? null : await tx.get(groupPath(groupId));
  const chatId = content.type === "CHAT_MESSAGE" ? String(content.data.chatId) : null;
  const chat = chatId ? await tx.get(`chats/${chatId}`) : null;
  const writes: fs.Write[] = [];
  if (audience === "TARGET" || (group && group.status !== "DELETED")) {
    for (let i = 0; i < uids.length; i += 8) {
      const batch = await Promise.all(uids.slice(i, i + 8).map(async uid => {
        if (uid === actorUid || (chatId && (!Array.isArray(chat?.memberIds) || !chat.memberIds.includes(uid)))) return [];
        if (audience !== "TARGET" && !scan) {
          const member = await tx.get(memberPath(groupId, uid));
          if (!member || (member.joinedAt instanceof Date && member.joinedAt > createdAt) ||
            (audience === "MANAGERS" && member.role !== "OWNER" && member.role !== "ADMIN")) return [];
        }
        const id = await notificationInboxId(family, identity, uid, content.type);
        return await prepareNotification(tx, family, identity, uid, id, content, createdAt);
      }));
      writes.push(...batch.flat());
    }
  }
  for (const write of writes) tx.write(write);
  tx.write(fs.updateWrite(path, {
    cursor: cursor + 1, state: done ? "DONE" : "PENDING",
    ...(scan ? { after } : {}),
    ...(done && !scan ? { cleanupAfter: new Date(now.getTime() + retentionMs) } : {}),
  }));
  return true;
}

export async function processGroupFanouts(store: FanoutStore, now: Date): Promise<number> {
  const jobs = await store.query("notificationFanouts", [
    { field: "family", op: "EQUAL", value: family }, { field: "state", op: "EQUAL", value: "PENDING" },
  ], { limit: 5 });
  let processed = 0, failure: unknown;
  for (const job of jobs) {
    try {
      if (await store.runTransaction(tx => materializePage(tx, job.path, now))) processed++;
    } catch (error) {
      failure = error;
    }
  }
  const expired = await store.query("notificationFanouts", [
    { field: "family", op: "EQUAL", value: family }, { field: "cleanupAfter", op: "LESS_THAN_OR_EQUAL", value: now },
  ], { limit: 5 });
  for (const job of expired) {
    await store.runTransaction(async tx => {
      const current = await tx.get(job.path);
      if (current?.state !== "DONE" || !(current.cleanupAfter instanceof Date) || current.cleanupAfter > now) return;
      const pages = await tx.query("recipients", [], { parent: job.path, limit: 100 });
      for (const page of pages) tx.write(fs.deleteWrite(page.path, false));
      if (pages.length < 100) tx.write(fs.updateWrite(job.path, { cleanupAfter: null }));
    });
  }
  if (failure) throw failure;
  return processed;
}
