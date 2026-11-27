import { assertEquals, assert } from "jsr:@std/assert@1.0.19";
import * as fs from "../_shared/firestore.ts";
import { handleBookingReminders } from "./handler.ts";
import { prepareBookingNew } from "../_shared/booking-notification.ts";
import { runDeliveryWorker, cleanupDeliveries } from "../_shared/notification-worker.ts";
import { ownerBookingCommand } from "../_shared/owner-booking-command.ts";
import { handleBookingTimeout } from "../booking-timeout/handler.ts";
import { handlePlayerCancelBooking } from "../player-cancel-booking/handler.ts";
import { bearer, emulatorToken } from "../_shared/testing.ts";

const enabled = /^demo-/.test(Deno.env.get("FIREBASE_PROJECT_ID") ?? "") && /^(127\.0\.0\.1|localhost):\d+$/.test(Deno.env.get("FIRESTORE_EMULATOR_HOST") ?? "");
const prefix = crypto.randomUUID(), now = new Date(), future = (minutes: number) => new Date(now.getTime() + minutes * 60_000);
const req = (secret = "local") => new Request("http://localhost", { method: "POST", headers: { "x-cron-secret": secret } });
const T = (name: string, fn: () => Promise<void>) => Deno.test({ name, ignore: !enabled, fn });
const owner = `owner-${prefix}`, venue = `venue-${prefix}`;
async function seed(id: string, status = "CONFIRMED", starts = [future(100), future(110)]) {
  const uid = `user-${id}`;
  if (!await fs.get(`users/${owner}`)) await fs.commit([
    fs.createWrite(`users/${owner}`, { role: "OWNER", ownerStatus: "APPROVED", accountStatus: "ACTIVE" }),
    fs.createWrite(`venues/${venue}`, { ownerId: owner, status: "ACTIVE" }),
  ]);
  await fs.commit([
    fs.createWrite(`users/${uid}`, { role: "PLAYER", accountStatus: "ACTIVE", walletBalance: 5000, fcmTokens: [] }),
    fs.createWrite(`bookings/${id}`, { userId: uid, venueId: venue, status, startAt: new Date(Math.min(...starts.map(s => s.getTime()))),
      items: starts.map(startAt => ({ startAt, slotId: `slot-${id}` })), slotIds: [`slot-${id}`], paymentStatus: "PAID", paymentMethod: "WALLET", paidAmount: 100000, total: 100000, approvalDeadline: future(60) }),
    fs.createWrite(`slots/slot-${id}`, { venueId: venue, bookingId: id, status: "BOOKED" }),
  ]);
  return uid;
}
const inbox = (uid: string) => fs.query("notifications", [], { parent: `users/${uid}` });
// Small scoped fixtures must be filtered before limiting; the dedicated 205-row case uses the unwrapped query.
function sourceFor(ids: string[]) { return { ...fs, query: (c: string, f: fs.QueryFilter[], o?: fs.QueryOptions) => fs.query(c, f, { ...o, limit: undefined }).then(rows => rows.filter(r => ids.includes(r.id)).slice(0, o?.limit)) }; }
const schedule = (ids: string[], clock = () => now) => handleBookingReminders(req(), { firestore: sourceFor(ids), cronSecret: "local", now: clock });
const command = (id: string, decision: "APPROVE" | "REJECT" | "CANCEL") => ownerBookingCommand(bearer(emulatorToken({ sub: owner, appRole: "OWNER" }), {
  body: JSON.stringify({ venueId: venue, bookingId: id, reason: "Local audit" }),
}), decision);
Deno.test("reminder endpoint requires POST and cron authentication", async () => {
  assertEquals((await handleBookingReminders(new Request("http://localhost"), { cronSecret: "local" })).status, 405);
  assertEquals((await handleBookingReminders(req("wrong"), { cronSecret: "local" })).status, 401);
});
for (const decision of ["APPROVE", "REJECT", "CANCEL"] as const) T(`W1 ${decision}: actual source recipient/type, replay and FCM failure retain inbox`, async () => {
  const id = `${prefix}-${decision}`, uid = await seed(id, decision === "CANCEL" ? "CONFIRMED" : "PENDING");
  const response = await command(id, decision); assertEquals(response.status, 200); await response.body?.cancel();
  const rows = await inbox(uid); assertEquals(rows.length, 1);
  assertEquals(rows[0].data.type, decision === "APPROVE" ? "BOOKING_CONFIRMED" : decision === "REJECT" ? "BOOKING_REJECTED" : "BOOKING_CANCELLED");
  assertEquals((rows[0].data.data as any).bookingId, id);
  const replay = await command(id, decision); assert([200, 409].includes(replay.status)); await replay.body?.cancel();
  assertEquals((await inbox(uid)).length, 1);
  await fs.commit([fs.updateWrite(`users/${uid}`, { fcmTokens: [`fake-${uid}`] })]);
  await runDeliveryWorker(fs, async () => ({ kind: "retry" }));
  assertEquals((await inbox(uid)).length, 1);
  if (decision !== "APPROVE") assertEquals((await fs.get(`users/${uid}`))?.walletBalance, 105000);
});
T("W1 timeout refunds once, uses REJECTED and survives replay", async () => {
  const id = `${prefix}-timeout`, uid = await seed(id, "PENDING");
  await fs.commit([fs.updateWrite(`bookings/${id}`, { approvalDeadline: future(-1) })]);
  for (let i = 0; i < 2; i++) {
    const r = await handleBookingTimeout(req(), { firestore: sourceFor([id]), cronSecret: "local", now: () => now });
    assertEquals(r.status, 200); await r.body?.cancel();
  }
  const rows = await inbox(uid); assertEquals(rows.length, 1); assertEquals(rows[0].data.type, "BOOKING_REJECTED");
  assert(rows[0].data.body!.toString().includes("100000")); assertEquals((await fs.get(`users/${uid}`))?.walletBalance, 105000);
});
T("player self-cancel creates no own notification or delivery on replay", async () => {
  const id = `${prefix}-self`, uid = await seed(id, "PENDING");
  for (let i = 0; i < 2; i++) {
    const r = await handlePlayerCancelBooking(bearer(emulatorToken({ sub: uid, appRole: "PLAYER" }), { body: JSON.stringify({ bookingId: id, reason: "Local self cancellation", requestId: "local-self" }) }));
    assertEquals(r.status, 200); await r.body?.cancel();
  }
  assertEquals(await inbox(uid), []);
  assertEquals(await fs.query("notificationDeliveries", [{ field: "recipientUid", op: "EQUAL", value: uid }]), []);
});
T("B1 helper: venue owner recipient, caller-provided inbox ID and atomic simulated source rollback", async () => {
  const id = `${prefix}-new`, uid = await seed(id, "HOLD");
  const write = async (abort: boolean) => fs.runTransaction(async tx => {
    const b = (await tx.get(`bookings/${id}`))!;
    if (b.status !== "HOLD") return;
    const writes = await prepareBookingNew(tx, id, { ...b, status: "PENDING" }, now, `existing-${id}`);
    tx.write(fs.updateWrite(`bookings/${id}`, { status: "PENDING" }));
    for (const w of writes) tx.write(w);
    if (abort) throw new Error("SOURCE_ABORT");
  });
  try { await write(true); throw new Error("Expected rollback"); } catch (e) { assertEquals((e as Error).message, "SOURCE_ABORT"); }
  assertEquals(await fs.get(`users/${owner}/notifications/existing-${id}`), null);
  await write(false); await write(false);
  assertEquals((await fs.get(`users/${owner}/notifications/existing-${id}`))?.type, "BOOKING_NEW");
  assertEquals(await inbox(uid), []); // Payment producer itself is still missing; this is a helper harness.
});
T("B2 exact due boundary, earliest unordered items, late catchup and after-start exclusion", async () => {
  const ids = ["boundary", "late", "early", "past"].map(n => `${prefix}-${n}`);
  const uids = [];
  uids.push(await seed(ids[0], "CONFIRMED", [future(125), future(120)]));
  uids.push(await seed(ids[1], "CONFIRMED", [future(5)]));
  uids.push(await seed(ids[2], "CONFIRMED", [future(121)]));
  uids.push(await seed(ids[3], "CONFIRMED", [future(0)]));
  const r = await schedule(ids); assertEquals(r.status, 200); await r.body?.cancel();
  for (let i = 0; i < ids.length; i++) assertEquals((await inbox(uids[i])).length, i < 2 ? 1 : 0);
  assertEquals(((await inbox(uids[0]))[0].data.data as any).reminderStartAt.getTime(), future(120).getTime());
});
T("B2 all non-CONFIRMED states, invalid items, derived mismatch and inactive recipient excluded", async () => {
  const states = ["HOLD", "PENDING", "CANCELLED", "REJECTED", "COMPLETED", "NO_SHOW", "EXPIRED"];
  const ids = [];
  for (const state of states) { const id = `${prefix}-${state}`; ids.push(id); await seed(id, state); }
  for (const kind of ["invalid", "mismatch", "inactive"]) {
    const id = `${prefix}-${kind}`, uid = await seed(id); ids.push(id);
    await fs.commit([fs.updateWrite(kind === "inactive" ? `users/${uid}` : `bookings/${id}`,
      kind === "inactive" ? { accountStatus: "LOCKED" } : kind === "invalid" ? { items: [{ startAt: "invalid" }] } : { items: [{ startAt: future(90) }] })]);
  }
  const r = await schedule(ids); assertEquals(r.status, 200); await r.body?.cancel();
  for (const id of ids) assertEquals(await inbox(`user-${id}`), []);
});
T("B2 concurrent schedulers/replay dedup, reschedule before/after inbox and stale delivery", async () => {
  const id = `${prefix}-move`, uid = await seed(id, "CONFIRMED", [future(180)]);
  let r = await schedule([id]); await r.body?.cancel(); assertEquals(await inbox(uid), []);
  await fs.commit([fs.updateWrite(`bookings/${id}`, { startAt: future(60), items: [{ startAt: future(60) }] })]);
  for (const r of await Promise.all([schedule([id]), schedule([id])])) { assertEquals(r.status, 200); await r.body?.cancel(); }
  assertEquals((await inbox(uid)).length, 1);
  await fs.commit([fs.updateWrite(`bookings/${id}`, { startAt: future(70), items: [{ startAt: future(70) }] }), fs.updateWrite(`users/${uid}`, { fcmTokens: [`fake-${uid}`] })]);
  r = await schedule([id]); assertEquals(r.status, 200); await r.body?.cancel();
  let sent = 0;
  // Isolate delivery candidates to this user while keeping real transactions and receipt paths.
  await runDeliveryWorker({ ...fs, query: async (c, f, o) => fs.query(c, [...f, { field: "recipientUid", op: "EQUAL", value: uid }], o) }, async () => { sent++; return { kind: "accepted" }; }, () => now);
  assertEquals(sent, 0); assertEquals((await inbox(uid)).length, 1);
});
T("B2 real REST cursor pages 205 tied timestamps without missing recipients", async () => {
  const ids = Array.from({ length: 205 }, (_, i) => `${prefix}-page-${i.toString().padStart(3,"0")}`);
  for (const id of ids) await seed(id);
  const timestampValue = future(100).toISOString().replace(/\.\d{3}Z$/, ".123456789Z");
  await fs.commit(ids.map(id => {
    const w = fs.updateWrite(`bookings/${id}`, {});
    w.updateMask = { fieldPaths: ["startAt", "items"] };
    w.update!.fields = { startAt: { timestampValue }, items: { arrayValue: { values: [{ mapValue: { fields: { startAt: { timestampValue } } } }] } } };
    return w;
  }));
  // Query window contains earlier fixtures too. Never filter a paginated page before choosing its cursor.
  const r = await handleBookingReminders(req(), { cronSecret: "local", now: () => now });
  assertEquals(r.status, 200); await r.body?.cancel();
  for (const id of ids) assertEquals((await inbox(`user-${id}`)).length, 1);
});
T("B2 FCM failure preserves inbox, retained-away job is not recreated, cancelled/started push is suppressed", async () => {
  const id = `${prefix}-retention`, uid = await seed(id);
  await fs.commit([fs.updateWrite(`users/${uid}`, { fcmTokens: ["fake-retention"] })]);
  let r = await schedule([id]); assertEquals(r.status, 200); await r.body?.cancel();
  const store = { ...fs, query: async (c: string, f: fs.QueryFilter[], o?: fs.QueryOptions) => fs.query(c, [...f, { field: "recipientUid", op: "EQUAL", value: uid }], o) };
  let sent = 0;
  await runDeliveryWorker(store, async () => { sent++; return { kind: "retry" }; }, () => now);
  assertEquals(sent, 1); assertEquals((await inbox(uid)).length, 1);
  await fs.commit([fs.updateWrite(`bookings/${id}`, { status: "CANCELLED" })]);
  await runDeliveryWorker(store, async () => { sent++; return { kind: "accepted" }; }, () => future(1));
  assertEquals(sent, 1);
  await cleanupDeliveries(fs, now, uid);
  await fs.commit([fs.updateWrite(`bookings/${id}`, { status: "CONFIRMED" })]);
  r = await schedule([id]); assertEquals(r.status, 200); await r.body?.cancel();
  assertEquals(await fs.query("notificationDeliveries", [{ field: "recipientUid", op: "EQUAL", value: uid }]), []);
  const started = `${prefix}-started-push`, startedUid = await seed(started, "CONFIRMED", [future(2)]);
  r = await schedule([started]); await r.body?.cancel();
  await fs.commit([fs.updateWrite(`users/${startedUid}`, { fcmTokens: ["fake-started"] })]);
  await runDeliveryWorker({ ...fs, query: async (c, f, o) => fs.query(c, [...f, { field: "recipientUid", op: "EQUAL", value: startedUid }], o) }, async () => { sent++; return { kind: "accepted" }; }, () => future(3));
  assertEquals(sent, 1);
});
T("B2 rechecks cancellation/deletion race and source rollback leaves no reminder", async () => {
  const id = `${prefix}-race-cancel`, uid = await seed(id);
  const source = sourceFor([id]);
  const r = await handleBookingReminders(req(), { cronSecret: "local", now: () => now, firestore: {
    ...source, runTransaction: async fn => {
      await fs.commit([fs.updateWrite(`bookings/${id}`, { status: "CANCELLED" }), fs.updateWrite(`users/${uid}`, { deletionRequestedAt: now })]);
      return await fs.runTransaction(fn);
    },
  } });
  assertEquals(r.status, 200); await r.body?.cancel(); assertEquals(await inbox(uid), []);
  const abortId = `${prefix}-abort`, abortUid = await seed(abortId);
  const aborted = await handleBookingReminders(req(), { cronSecret: "local", now: () => now, firestore: {
    ...sourceFor([abortId]), runTransaction: async fn => await fs.runTransaction(async tx => { await fn(tx); throw new Error("SOURCE_ABORT"); }),
  } });
  assertEquals(aborted.status, 503); await aborted.body?.cancel(); assertEquals(await inbox(abortUid), []);
});
T("B1 rejects unapproved/locked/deleting owners and B2 reports exhausted processing budget", async () => {
  const id = `${prefix}-bad-owner`; await seed(id, "HOLD");
  const b = (await fs.get(`bookings/${id}`))!;
  try {
    for (const patch of [{ ownerStatus: "NONE", accountStatus: "ACTIVE", deletionRequestedAt: null }, { ownerStatus: "APPROVED", accountStatus: "LOCKED", deletionRequestedAt: null }, { ownerStatus: "APPROVED", accountStatus: "ACTIVE", deletionRequestedAt: now }]) {
      await fs.commit([fs.updateWrite(`users/${owner}`, patch)]);
      const writes = await fs.runTransaction(tx => prepareBookingNew(tx, id, { ...b, status: "PENDING", ownerUid: "forged" }, now));
      assertEquals(writes, []);
    }
  } finally { await fs.commit([fs.updateWrite(`users/${owner}`, { ownerStatus: "APPROVED", accountStatus: "ACTIVE", deletionRequestedAt: null })]); }
  const late = `${prefix}-budget`; await seed(late);
  let calls = 0;
  const r = await schedule([late], () => calls++ === 0 ? now : future(2));
  assertEquals((await r.json()).budgetExhausted, true);
  assertEquals(await inbox(`user-${late}`), []);
});
T("B2 valid reminder reaches fake FCM on both devices once and leaves its unread inbox", async () => {
  const id = `${prefix}-send`, uid = await seed(id, "CONFIRMED", [future(20)]);
  await fs.commit([fs.updateWrite(`users/${uid}`, { fcmTokens: ["fake-send-a", "fake-send-b"] })]);
  const r = await schedule([id]); assertEquals(r.status, 200); await r.body?.cancel();
  const store = { ...fs, query: (c: string, f: fs.QueryFilter[], o?: fs.QueryOptions) => fs.query(c, [...f, { field: "recipientUid", op: "EQUAL", value: uid }], o) };
  let count = 0;
  for (let i = 0; i < 2; i++) await runDeliveryWorker(store, async (_token, data) => {
    count++; assertEquals(data.recipientUid, uid); return { kind: "accepted" };
  }, () => now);
  assertEquals(count, 2);
  const rows = await inbox(uid); assertEquals(rows.length, 1); assertEquals(rows[0].data.isRead, false);
  const jobs = await fs.query("notificationDeliveries", [{ field: "recipientUid", op: "EQUAL", value: uid }]);
  assertEquals(jobs[0].data.state, "SENT");
});
