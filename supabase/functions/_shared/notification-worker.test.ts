import { assertEquals, assertRejects } from "jsr:@std/assert@1.0.19";
import * as fs from "./firestore.ts";
import { NOTIFICATION_TYPES, prepareNotification, sha256 } from "./notification-enqueue.ts";
import { runDeliveryWorker, cleanupDeliveries, type DeliveryStore, type Sender } from "./notification-worker.ts";
import { httpV1Sender } from "./notification-fcm.ts";
import { handleNotificationDelivery } from "../notification-delivery/handler.ts";
Deno.env.set("FIREBASE_PROJECT_ID", "demo-notification-delivery");
type Doc = Record<string, unknown>;
const at = new Date("2026-11-23T10:00:00Z");
const content = { type: "BOOKING_CONFIRMED", title: "Title", body: "Body", data: { bookingId: "b" } };
function memory() {
  const docs = new Map<string, Doc>();
  const apply = (writes: fs.Write[]) => {
    for (const w of writes) {
      const path = (w.update?.name ?? w.delete!).split("/documents/")[1];
      if (w.currentDocument?.exists === false && docs.has(path) || w.currentDocument?.exists === true && !docs.has(path)) throw new Error("PRECONDITION");
    }
    for (const w of writes) {
      const path = (w.update?.name ?? w.delete!).split("/documents/")[1];
      if (w.delete) docs.delete(path);
      else docs.set(path, { ...(w.updateMask ? docs.get(path) : {}), ...fs.decodeFields(w.update!.fields) });
    }
  };
  const query = async (collection: string, filters: fs.QueryFilter[], opts: fs.QueryOptions = {}) => {
    const prefix = `${opts.parent ? opts.parent + "/" : ""}${collection}/`;
    return [...docs].filter(([p, d]) => p.startsWith(prefix) && !p.slice(prefix.length).includes("/") && filters.every(f => {
      const a = d[f.field] instanceof Date ? (d[f.field] as Date).getTime() : d[f.field] as any;
      const b = f.value instanceof Date ? f.value.getTime() : f.value as any;
      return f.op === "EQUAL" ? a === b : f.op === "IN" ? b.includes(a) : f.op === "GREATER_THAN" ? a > b : a <= b;
    })).slice(0, opts.limit).map(([path, data]) => ({ id: path.split("/").pop()!, path, data }));
  };
  const store: DeliveryStore = {
    query, commit: async w => apply(w),
    listPage: async path => ({ documents: [...docs].filter(([p]) => p.startsWith(path + "/")).slice(0, 100).map(([p, d]) => ({ ...d, __documentId: p.split("/").pop() })), nextPageToken: "" }),
    runTransaction: async <T>(fn: (tx: fs.FirestoreTransaction) => Promise<T | { result: T; writes: fs.Write[] }>) => {
      const writes: fs.Write[] = []; let wrote = false;
      const value = await fn({ get: async p => { if (wrote) throw new Error("READ_AFTER_WRITE"); return docs.get(p) ?? null; },
        query: async (...args) => { if (wrote) throw new Error("READ_AFTER_WRITE"); return await query(...args); },
        write: w => { wrote = true; writes.push(w); } });
      const wrapped = value && typeof value === "object" && "writes" in value;
      apply(wrapped ? (value as { writes: fs.Write[] }).writes : writes);
      return (wrapped ? (value as { result: T }).result : value) as T;
    },
  };
  async function enqueue(type = content.type, id = "inbox") {
    docs.set("users/u", { accountStatus: "ACTIVE", fcmTokens: ["a", "b"] });
    await store.runTransaction(async tx => { for (const w of await prepareNotification(tx, "booking", "event", "u", id, { ...content, type }, at)) tx.write(w); });
    return [...docs.keys()].find(p => p.startsWith("notificationDeliveries/"))!;
  }
  return { store, docs, enqueue };
}
Deno.test("T043: all 19 types retain inbox; defaults, saved preferences and mandatory BOOKING_NEW govern push", async () => {
  for (const type of NOTIFICATION_TYPES) {
    for (const setting of [undefined, false, true]) {
      const m = memory();
      await m.enqueue(type);
      const user = m.docs.get("users/u")!;
      user.notificationPrefs = setting === undefined ? {} : { [type]: setting };
      const inbox = m.docs.get("users/u/notifications/inbox")!;
      const start = new Date(at.getTime() + 3600_000);
      inbox.data = { bookingId: "b", reminderStartAt: start, chatId: "c" };
      m.docs.set("bookings/b", { userId: "u", status: "CONFIRMED", startAt: start, items: [{ startAt: start }] });
      m.docs.set("chats/c", { memberIds: ["u", "sender"] });
      m.docs.set("chats/c/participants/u", { muted: false });
      const sent: string[] = [];
      await runDeliveryWorker(m.store, async token => { sent.push(token); return { kind: "accepted" }; }, () => at);
      const enabled = type === "BOOKING_NEW" || (setting ?? type !== "PROMOTION");
      assertEquals(sent.length, enabled ? 2 : 0, `${type}/${setting}`);
      assertEquals(m.docs.get("users/u/notifications/inbox"), inbox);
      assertEquals(inbox.isRead, false);
    }
  }
});
Deno.test("enqueue: canonical identity dedups UUID replay and never backfills retained-away job", async () => {
  const m = memory(), job = await m.enqueue();
  const original = m.docs.get("users/u/notifications/inbox")!; original.isRead = true;
  assertEquals(await prepareNotification({ get: async p => m.docs.get(p) ?? null }, "booking", "event", "u", "other", content, at), []);
  m.docs.delete(job);
  assertEquals(await prepareNotification({ get: async p => m.docs.get(p) ?? null }, "booking", "event", "u", "inbox", content, at), []);
  assertEquals(original.isRead, true);
  await assertRejects(() => prepareNotification({ get: async p => m.docs.get(p) ?? null }, "booking", "event", "u", "inbox", { ...content, body: "different" }, at));
});
Deno.test("enqueue: inactive/deleting recipient and invalid type never create inbox/job", async () => {
  const m = memory(); m.docs.set("users/u", { accountStatus: "ACTIVE", deletionRequestedAt: at });
  assertEquals(await prepareNotification({ get: async p => m.docs.get(p) ?? null }, "f", "id", "u", "n", content, at), []);
  await assertRejects(() => prepareNotification({ get: async () => null }, "f", "id", "u", "n", { ...content, type: "REVIEW_REPLY" }, at));
});
Deno.test("enqueue: persists exactly six inbox fields and rejects non-map data", async () => {
  const m = memory(); m.docs.set("users/u", { accountStatus: "ACTIVE" });
  const tx = { get: async (p: string) => m.docs.get(p) ?? null };
  const writes = await prepareNotification(tx, "f", "event", "u", "n", { ...content, extra: "must not persist" } as typeof content, at);
  assertEquals(Object.keys(fs.decodeFields(writes[0].update!.fields)).sort(), ["body", "createdAt", "data", "isRead", "title", "type"]);
  await assertRejects(() => prepareNotification(tx, "f", "event", "u", "n", { ...content, data: "not a map" as any }, at));
});
Deno.test("worker: partial success retries only failed token and honors Retry-After", async () => {
  const m = memory(), job = await m.enqueue(); let clock = at;
  const sent: string[] = [];
  const sender: Sender = async (token, data) => { sent.push(token); assertEquals(data, { recipientUid: "u", notificationId: "inbox" }); return token === "b" && sent.length === 2 ? { kind: "retry", retryAfterMs: 120_000 } : { kind: "accepted" }; };
  await runDeliveryWorker(m.store, sender, () => clock);
  assertEquals(m.docs.get(job)?.state, "PENDING");
  clock = new Date(at.getTime() + 60_000); await runDeliveryWorker(m.store, sender, () => clock); assertEquals(sent, ["a", "b"]);
  clock = new Date(at.getTime() + 120_000); await runDeliveryWorker(m.store, sender, () => clock);
  assertEquals(sent, ["a", "b", "b"]); assertEquals(m.docs.get(job)?.state, "SENT");
});
Deno.test("worker: expired SENDING consumes reservation, caps six attempts, does not resend SENT", async () => {
  const m = memory(), job = await m.enqueue();
  await runDeliveryWorker(m.store, async () => ({ kind: "accepted" }), () => at);
  Object.assign(m.docs.get(job)!, { state: "LEASED", leaseOwner: "dead", leaseUntil: at });
  Object.assign(m.docs.get(`${job}/tokens/${await sha256("b")}`)!, { state: "SENDING", attemptCount: 6, nextAttemptAt: at });
  let calls = 0; await runDeliveryWorker(m.store, async () => { calls++; return { kind: "accepted" }; }, () => at);
  assertEquals(calls, 0); assertEquals(m.docs.get(job)?.state, "FAILED");
});
Deno.test("worker: recovers unknown SENDING below budget with a fresh attempt", async () => {
  const m = memory(), job = await m.enqueue();
  await runDeliveryWorker(m.store, async () => ({ kind: "accepted" }), () => at);
  Object.assign(m.docs.get(job)!, { state: "LEASED", leaseOwner: "dead", leaseUntil: at });
  const receipt = m.docs.get(`${job}/tokens/${await sha256("b")}`)!;
  Object.assign(receipt, { state: "SENDING", attemptCount: 1, attemptId: "old", nextAttemptAt: at });
  let calls = 0; await runDeliveryWorker(m.store, async () => { calls++; return { kind: "accepted" }; }, () => at);
  assertEquals(calls, 1); assertEquals(m.docs.get(`${job}/tokens/${await sha256("b")}`)?.attemptCount, 2);
});
Deno.test("worker: promotion default off, forced booking new, inbox remains regardless preference", async () => {
  const m = memory(), job = await m.enqueue("PROMOTION"); let calls = 0;
  await runDeliveryWorker(m.store, async () => { calls++; return { kind: "accepted" }; }, () => at);
  assertEquals(calls, 0); assertEquals(m.docs.get(job)?.state, "SKIPPED"); assertEquals(m.docs.has("users/u/notifications/inbox"), true);
  const n = memory(); await n.enqueue("BOOKING_NEW"); n.docs.get("users/u")!.notificationPrefs = { BOOKING_NEW: false };
  await runDeliveryWorker(n.store, async () => { calls++; return { kind: "accepted" }; }, () => at); assertEquals(calls, 2);
});
Deno.test("worker: unregistered removes only exact token; permanent provider failures preserve token", async () => {
  const m = memory(), job = await m.enqueue();
  await runDeliveryWorker(m.store, async token => ({ kind: token === "a" ? "unregistered" : "failed" }), () => at);
  assertEquals(m.docs.get("users/u")?.fcmTokens, ["b"]); assertEquals(m.docs.get(job)?.state, "FAILED");
});
Deno.test("worker: target initialization resumes beyond 100 and never adds tokens registered later", async () => {
  const m = memory(), job = await m.enqueue(); m.docs.get("users/u")!.fcmTokens = Array.from({ length: 101 }, (_, i) => `t${i}`);
  const sender: Sender = async () => ({ kind: "accepted" });
  assertEquals((await runDeliveryWorker(m.store, sender, () => at)).requests, 0);
  assertEquals(m.docs.get(job)?.initializedThrough, 100);
  (m.docs.get("users/u")!.fcmTokens as string[]).push("new-later");
  for (let i = 0; i < 3; i++) await runDeliveryWorker(m.store, sender, () => at);
  assertEquals(m.docs.get(job)?.state, "SENT"); assertEquals(m.docs.has(`${job}/tokens/${await sha256("new-later")}`), false);
});
Deno.test("worker: deletion racing send cannot resurrect job/receipt; child-first account cleanup", async () => {
  const m = memory(), job = await m.enqueue();
  await runDeliveryWorker(m.store, async () => {
    m.docs.get("users/u")!.deletionRequestedAt = at;
    await cleanupDeliveries(m.store, at, "u"); return { kind: "accepted" };
  }, () => at);
  assertEquals([...m.docs.keys()].some(p => p.startsWith(job)), false);
});
Deno.test("worker: terminal retention keeps pending jobs and removes children before parent", async () => {
  const m = memory(), job = await m.enqueue();
  await runDeliveryWorker(m.store, async () => ({ kind: "accepted" }), () => at);
  assertEquals(await cleanupDeliveries(m.store, new Date(at.getTime() + 7 * 86400_000 - 1)), 0);
  assertEquals(await cleanupDeliveries(m.store, new Date(at.getTime() + 7 * 86400_000)), 1);
  assertEquals([...m.docs.keys()].some(p => p.startsWith(job)), false);
  assertEquals(m.docs.has("users/u/notifications/inbox"), true);
});
Deno.test("worker: initialization observes deletion gate before creating receipt children", async () => {
  const m = memory(), job = await m.enqueue();
  const original = m.store.runTransaction; let transactions = 0;
  m.store.runTransaction = async fn => {
    transactions++;
    if (transactions === 3) m.docs.get("users/u")!.deletionRequestedAt = at;
    return await original(fn);
  };
  let sends = 0; await runDeliveryWorker(m.store, async () => { sends++; return { kind: "accepted" }; }, () => at);
  assertEquals(sends, 0); assertEquals(m.docs.get(job)?.state, "SKIPPED");
  assertEquals([...m.docs.keys()].filter(p => p.startsWith(job + "/tokens/")), []);
});
Deno.test("worker: chat suppression is account-wide across installations without dropping inbox", async () => {
  const m = memory(), job = await m.enqueue("CHAT_MESSAGE");
  m.docs.get("users/u/notifications/inbox")!.data = { chatId: "c" };
  m.docs.set("chats/c", { memberIds: ["u", "sender"] });
  m.docs.set("chats/c/participants/u", { muted: false });
  m.docs.set("chats/c/participants/u/presence/one", { viewingUntil: new Date(at.getTime() - 1) });
  m.docs.set("chats/c/participants/u/presence/two", { viewingUntil: new Date(at.getTime() + 60_000) });
  let sends = 0; await runDeliveryWorker(m.store, async () => { sends++; return { kind: "accepted" }; }, () => at);
  assertEquals(sends, 0); assertEquals(m.docs.get(job)?.state, "SKIPPED");
  assertEquals(m.docs.has("users/u/notifications/inbox"), true);
});
Deno.test("worker: target budget failure is explicit, never truncated into partial delivery", async () => {
  const m = memory(), job = await m.enqueue();
  m.docs.get("users/u")!.fcmTokens = Array.from({ length: 12_000 }, (_, i) => `token-${i}`);
  let sends = 0; await runDeliveryWorker(m.store, async () => { sends++; return { kind: "accepted" }; }, () => at);
  assertEquals(sends, 0); assertEquals(m.docs.get(job)?.state, "FAILED");
  assertEquals(m.docs.get(job)?.lastErrorCode, "TARGET_BUDGET"); assertEquals(m.docs.has("users/u/notifications/inbox"), true);
});
Deno.test("retention cleanup resumes at 100 child receipts per job without deleting parent early", async () => {
  const m = memory(), job = await m.enqueue();
  Object.assign(m.docs.get(job)!, { state: "SENT", updatedAt: at });
  for (let i = 0; i < 205; i++) m.docs.set(`${job}/tokens/${i}`, { state: "SENT" });
  const due = new Date(at.getTime() + 7 * 86400_000);
  assertEquals(await cleanupDeliveries(m.store, due), 0);
  assertEquals([...m.docs.keys()].filter(p => p.startsWith(job + "/tokens/")).length, 105);
  assertEquals(m.docs.has(job), true);
  assertEquals(await cleanupDeliveries(m.store, due), 0);
  assertEquals(await cleanupDeliveries(m.store, due), 1);
  assertEquals(m.docs.has(job), false);
});
Deno.test("FCM adapter: data-only payload and typed UNREGISTERED vs invalid arguments and Retry-After", async () => {
  let payload: any;
  const sender = httpV1Sender(async (_url, init) => { payload = JSON.parse(String(init?.body)); return Response.json({ name: "accepted" }); }, async () => "fake", () => "demo");
  assertEquals(await sender("raw", { recipientUid: "u", notificationId: "n" }), { kind: "accepted" });
  assertEquals(payload.message, { token: "raw", data: { recipientUid: "u", notificationId: "n" }, android: { priority: "high" } });
  for (const [status, details, expected] of [
    [404, [{ "@type": "type.googleapis.com/google.firebase.fcm.v1.FcmError", errorCode: "UNREGISTERED" }], "unregistered"],
    [400, [], "failed"], [429, [], "retry"],
  ] as const) {
    const send = httpV1Sender(async () => Response.json({ error: { details } }, { status, headers: { "Retry-After": "120" } }), async () => "fake", () => "demo");
    assertEquals((await send("raw", { recipientUid: "u", notificationId: "n" })).kind, expected);
  }
});
Deno.test("worker HTTP entrypoint: POST and cron auth required; emulator cannot use real FCM", async () => {
  assertEquals((await handleNotificationDelivery(new Request("http://localhost", { method: "GET" }))).status, 405);
  assertEquals((await handleNotificationDelivery(new Request("http://localhost", { method: "POST" }), { cronSecret: "x" })).status, 401);
});
Deno.test("worker HTTP authorized harness uses fake FCM and refuses emulator calls without it", async () => {
  const previous = Deno.env.get("FIRESTORE_EMULATOR_HOST");
  Deno.env.set("FIRESTORE_EMULATOR_HOST", "127.0.0.1:8080");
  try {
    const request = () => new Request("http://localhost", { method: "POST", headers: { "x-cron-secret": "secret" } });
    const m = memory();
    const response = await handleNotificationDelivery(request(), { store: m.store, cronSecret: "secret", send: async () => ({ kind: "accepted" }) });
    assertEquals(response.status, 200); assertEquals(await response.json(), { claims: 0, requests: 0, cleaned: 0 });
    assertEquals((await handleNotificationDelivery(request(), { store: m.store, cronSecret: "secret" })).status, 503);
  } finally { if (previous === undefined) Deno.env.delete("FIRESTORE_EMULATOR_HOST"); else Deno.env.set("FIRESTORE_EMULATOR_HOST", previous); }
});
