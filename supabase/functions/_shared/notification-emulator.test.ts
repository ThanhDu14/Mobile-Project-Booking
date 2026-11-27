import { assertEquals, assertRejects } from "jsr:@std/assert@1.0.19";
import * as fs from "./firestore.ts";
import { prepareNotification } from "./notification-enqueue.ts";
import { cleanupDeliveries, runDeliveryWorker } from "./notification-worker.ts";
import { handleAdminBroadcastNotification } from "../admin-broadcast-notification/handler.ts";
import { bearer, emulatorToken } from "./testing.ts";
const enabled = !!Deno.env.get("FIRESTORE_EMULATOR_HOST");
const run = crypto.randomUUID();
const test = (name: string, fn: () => Promise<void>) => Deno.test({ name, ignore: !enabled, fn });
test("D1 emulator: source rollback, concurrent replay, competing claims and per-token partial recovery", async () => {
  const uid = `n-${run}`, id = "inbox", now = new Date();
  await fs.commit([fs.createWrite(`users/${uid}`, { accountStatus: "ACTIVE", fcmTokens: ["one", "two"] })]);
  const enqueue = async (rollback = false) => await fs.runTransaction(async tx => {
    const writes = await prepareNotification(tx, "test", run, uid, id,
      { type: "BOOKING_CONFIRMED", title: "Saved", body: "Details", data: {} }, now);
    for (const w of writes) tx.write(w);
    if (rollback) throw new Error("SOURCE_ABORT");
  });
  await assertRejects(() => enqueue(true));
  assertEquals(await fs.get(`users/${uid}/notifications/${id}`), null);
  await Promise.all([enqueue(), enqueue()]);
  const jobs = await fs.query("notificationDeliveries", [{ field: "recipientUid", op: "EQUAL", value: uid }]);
  assertEquals(jobs.length, 1);
  let clock = now; const sent: string[] = [];
  const send = async (token: string) => { sent.push(token); return { kind: token === "two" && sent.filter(t => t === token).length === 1 ? "retry" as const : "accepted" as const }; };
  await Promise.all([runDeliveryWorker(fs, send, () => clock), runDeliveryWorker(fs, send, () => clock)]);
  assertEquals(sent.sort(), ["one", "two"]);
  clock = new Date(now.getTime() + 60_000);
  await runDeliveryWorker(fs, send, () => clock);
  assertEquals(sent.filter(t => t === "one").length, 1);
  assertEquals(sent.filter(t => t === "two").length, 2);
  assertEquals((await fs.get(jobs[0].path))?.state, "SENT");
  await cleanupDeliveries(fs, clock, uid);
  await enqueue(); // retained-away job is never backfilled, read state/inbox survive.
  assertEquals(await fs.query("notificationDeliveries", [{ field: "recipientUid", op: "EQUAL", value: uid }]), []);
});
test("SYSTEM emulator: current ADMIN, all active roles, atomic job/inbox, replay and fake FCM failure", async () => {
  const admin = `admin-${run}`, active = ["PLAYER", "OWNER", "ADMIN"].map(role => `${role}-${run}`);
  await fs.commit([fs.createWrite(`users/${admin}`, { accountStatus: "ACTIVE", role: "ADMIN" }),
    ...active.map((uid, i) => fs.createWrite(`users/${uid}`, { accountStatus: "ACTIVE", role: ["PLAYER", "OWNER", "ADMIN"][i], fcmTokens: [`fake-${uid}`] })),
    fs.createWrite(`users/locked-${run}`, { accountStatus: "LOCKED" }),
    fs.createWrite(`users/deleting-${run}`, { accountStatus: "ACTIVE", deletionRequestedAt: new Date() })]);
  const request = () => bearer(emulatorToken({ sub: admin, appRole: "ADMIN" }), { body: JSON.stringify({ title: "System", body: "Persisted", requestId: `system-${run}` }) });
  const first = await handleAdminBroadcastNotification(request(), { firestore: fs });
  assertEquals(first.status, 200); await first.body?.cancel();
  const inboxes = await Promise.all(active.map(uid => fs.query("notifications", [], { parent: `users/${uid}` })));
  assertEquals(inboxes.map(rows => rows.length), [1, 1, 1]);
  await fs.commit([fs.updateWrite(inboxes[0][0].path, { isRead: true })]);
  const retry = await handleAdminBroadcastNotification(request(), { firestore: fs });
  assertEquals(retry.status, 200); await retry.body?.cancel();
  assertEquals((await fs.get(inboxes[0][0].path))?.isRead, true);
  assertEquals(await fs.query("notifications", [], { parent: `users/locked-${run}` }), []);
  assertEquals(await fs.query("notifications", [], { parent: `users/deleting-${run}` }), []);
  let requests = 0;
  await runDeliveryWorker(fs, async (_token, data) => {
    requests++; assertEquals(Object.keys(data).sort(), ["notificationId", "recipientUid"]);
    return { kind: "retry" };
  });
  assertEquals(requests >= 3, true);
  for (const uid of active) {
    const rows = await fs.query("notifications", [], { parent: `users/${uid}` });
    const jobs = await fs.query("notificationDeliveries", [{ field: "recipientUid", op: "EQUAL", value: uid }]);
    assertEquals(rows.length, 1); assertEquals(rows[0].data.body, "Persisted");
    assertEquals(jobs.length, 1); assertEquals(jobs[0].data.state, "PENDING");
  }
});
