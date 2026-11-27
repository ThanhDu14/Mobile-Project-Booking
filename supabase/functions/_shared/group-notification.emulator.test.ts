import { assert, assertEquals, assertRejects } from "jsr:@std/assert@1.0.19";
import { handleChatSend } from "../chat-send/handler.ts";
import { handleGroupManage } from "../group-manage/handler.ts";
import { handleGroupPost } from "../group-post/handler.ts";
import { handleNotificationDelivery } from "../notification-delivery/handler.ts";
import { participantPath } from "./chats.ts";
import { seedGroupChat, makeChatDeps } from "./chats_fixtures.ts";
import * as fs from "./firestore.ts";
import { processGroupFanouts } from "./group-notification.ts";
import { makeTestDeps, seedGroup, ensureUsers } from "./groups_fixtures.ts";
import { attendeePath, groupPath, memberPath, postPath } from "./groups.ts";
import type { DeliveryStore, Sender } from "./notification-worker.ts";
import { bearer, emulatorToken } from "./testing.ts";

const enabled = Deno.env.get("FIREBASE_PROJECT_ID")?.startsWith("demo-") &&
  !!Deno.env.get("FIRESTORE_EMULATOR_HOST") && !!Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST");
const test = (name: string, fn: () => Promise<void>) => Deno.test({ name, ignore: !enabled, fn });
const id = () => crypto.randomUUID();
const user = (suffix: string) => `f1-${id()}-${suffix}`;
const inbox = (uid: string) => fs.query("notifications", [], { parent: `users/${uid}` });
const fanouts = (groupId: string) => fs.query("notificationFanouts", [{ field: "groupId", op: "EQUAL", value: groupId }]);
const req = (uid: string, data: Record<string, unknown>) =>
  bearer(emulatorToken({ sub: uid, user_id: uid, appRole: "PLAYER" }), { body: JSON.stringify(data) });

function scoped(groupId: string, uids?: string[]): DeliveryStore {
  return {
    ...fs,
    query: (collection, filters, options) => {
      if (collection === "notificationFanouts") {
        return fs.query(collection, [...filters, { field: "groupId", op: "EQUAL", value: groupId }], options);
      }
      if (collection === "notificationDeliveries") {
        return uids ? fs.query(collection, [...filters, { field: "recipientUid", op: "IN", value: uids }], options) :
          Promise.resolve([]);
      }
      return fs.query(collection, filters, options);
    },
  };
}

async function drain(groupId: string) {
  const store = scoped(groupId);
  for (let tick = 0; tick < 12; tick++) {
    if (!await processGroupFanouts(store, new Date())) return;
  }
  throw new Error("Group fanout did not drain");
}

async function expect(res: Promise<Response>, status: number) {
  const response = await res;
  const text = await response.text();
  assertEquals(response.status, status, text);
}

test("F1 GROUP_ACTIVITY: all seven subtypes, current permissions, durable replay and retention", async () => {
  const [owner, admin, member, applicant, rejected, automatic] = ["owner", "admin", "member", "applicant", "rejected", "auto"].map(user);
  const groupId = await seedGroup({ owner, admins: [admin], members: [member], visibility: "PRIVATE" });
  await ensureUsers([applicant, rejected, automatic]);
  const fixture = makeTestDeps();
  const manage = (uid: string, action: string, data = {}) =>
    handleGroupManage(req(uid, { groupId, action, requestId: id(), ...data }), fixture.deps);
  const post = (uid: string, action: string, data = {}) =>
    handleGroupPost(req(uid, { groupId, action, requestId: id(), ...data }), fixture.deps);

  const joinId = id(), reviewId = id(), removalId = id();
  await expect(manage(applicant, "join", { requestId: joinId }), 200);
  await expect(manage(applicant, "join", { requestId: joinId }), 200);
  await expect(manage(member, "approve", { userId: applicant }), 403);
  await expect(manage(owner, "approve", { requestId: reviewId, userId: applicant }), 200);
  await expect(manage(owner, "approve", { requestId: reviewId, userId: applicant }), 200);
  await expect(manage(owner, "reject", { requestId: reviewId, userId: applicant }), 409);
  await expect(manage(rejected, "join"), 200);
  await expect(manage(admin, "reject", { userId: rejected }), 200);
  await expect(manage(owner, "remove", { requestId: removalId, userId: applicant }), 200);
  await expect(manage(owner, "remove", { requestId: removalId, userId: applicant }), 200);
  await expect(manage(automatic, "join"), 200);
  const exposeId = id();
  await expect(manage(owner, "update", { visibility: "PUBLIC", requestId: exposeId }), 200);
  await expect(manage(owner, "update", { visibility: "PRIVATE" }), 200);
  await expect(manage(owner, "update", { visibility: "PUBLIC", requestId: exposeId }), 200);
  assertEquals((await fs.get(groupPath(groupId)))?.visibility, "PRIVATE");
  // A former manager remains a member but cannot receive queued JOIN_REQUESTED.
  await expect(manage(owner, "setRole", { userId: admin, role: "MEMBER" }), 200);

  const postId = id(), eventAt = new Date(Date.now() + 3_600_000);
  const event = { requestId: postId, type: "EVENT", eventTitle: "F1", eventAt: eventAt.toISOString(), eventLocation: "A", expectedCount: 4 };
  await expect(post(owner, "create", event), 201);
  await expect(post(owner, "create", event), 200);
  await fs.commit([fs.createWrite(attendeePath(groupId, postId, member), { userId: member, groupId, postId, eventAt })]);
  await expect(post(member, "update", { postId, eventLocation: "B" }), 403);
  const change = { postId, eventLocation: "B", requestId: id() };
  await expect(post(owner, "update", change), 200);
  await expect(post(owner, "update", change), 200);
  await expect(post(owner, "update", { ...change, eventLocation: "C" }), 409);
  await expect(post(owner, "update", { postId, eventTitle: "Title-only edit" }), 200);
  const deletion = { postId, requestId: id() };
  await expect(post(owner, "delete", deletion), 200);
  await expect(post(owner, "delete", deletion), 200);
  await expect(post(owner, "create", event), 200);
  assertEquals(await fs.get(postPath(groupId, postId)), null);
  assertEquals(fixture.notifications.sent, []);
  assertEquals((await inbox(member)).length, 0);
  await drain(groupId);

  const events = async (uid: string) => (await inbox(uid)).map(row => row.data.data).sort((a, b) =>
    JSON.stringify(a).localeCompare(JSON.stringify(b)));
  const subtypes = async (uid: string) => (await events(uid)).map(data => (data as { event: string }).event).sort();
  assertEquals(await subtypes(owner), ["JOIN_REQUESTED", "JOIN_REQUESTED", "JOIN_REQUESTED"]);
  assertEquals(await subtypes(admin), ["EVENT_CREATED"]);
  assertEquals(await subtypes(rejected), ["JOIN_REJECTED"]);
  assertEquals(await subtypes(applicant), ["JOIN_APPROVED", "REMOVED"]);
  assertEquals(await subtypes(automatic), ["EVENT_CREATED", "JOIN_APPROVED"]);
  assertEquals(await subtypes(member), ["EVENT_CREATED", "EVENT_DELETED", "EVENT_UPDATED"]);
  for (const data of await events(member)) assertEquals(data, { event: (data as { event: string }).event, groupId, postId });
  await fs.commit([fs.updateWrite((await inbox(member))[0].path, { isRead: true })]);
  await drain(groupId);
  assertEquals((await inbox(member)).filter(row => row.data.isRead).length, 1);
  const later = new Date(Date.now() + 8 * 24 * 3600_000);
  for (let tick = 0; tick < 3; tick++) await processGroupFanouts(scoped(groupId), later);
  for (const job of await fanouts(groupId)) {
    assertEquals(job.data.state, "DONE");
    assertEquals(await fs.query("recipients", [], { parent: job.path }), []);
  }
});

test("F1 large GROUP chat: paged scan, source rollback, cursor rollback, two workers and membership changes", async () => {
  const prefix = `f1-${id()}`;
  const owner = `${prefix}-000`, first = `${prefix}-001`, last = `${prefix}-504`;
  const removed = `${prefix}-003`, deleting = `${prefix}-004`, late = `${prefix}-005`;
  const groupId = await seedGroup({ owner });
  const roster = Array.from({ length: 505 }, (_, i) => `${prefix}-${String(i).padStart(3, "0")}`);
  let writes = roster.slice(1).map(uid => fs.createWrite(memberPath(groupId, uid), { role: "MEMBER", joinedAt: new Date(0) }));
  await fs.commit(writes.slice(0, 250));
  await fs.commit(writes.slice(250));
  await ensureUsers([first, last, removed, deleting, late]);
  const chatId = await seedGroupChat(groupId, [owner, first, last, removed, deleting]);
  await fs.commit([fs.updateWrite(`chats/${chatId}`, { memberIds: roster.filter(uid => uid !== late) })]);
  const fixture = makeChatDeps();
  const messageId = id();
  const send = (deps = fixture.deps) => handleChatSend(req(owner, { chatId, messageId, text: "F1 large group" }), deps);
  const brokenProducer: typeof fs.runTransaction = (operation, attempts) => fs.runTransaction(async tx => {
    await operation(tx);
    throw new Error("injected producer transaction failure");
  }, attempts);
  await expect(send({ ...fixture.deps, firestore: { ...fs, runTransaction: brokenProducer } }), 500);
  assertEquals(await fs.get(`chats/${chatId}/messages/${messageId}`), null);
  assertEquals(await fanouts(groupId), []);
  await expect(send(), 201);
  await expect(send(), 200);
  const counts = (await fs.get(`chats/${chatId}`))?.unreadCounts as Record<string, unknown>;
  assertEquals([counts[first], counts[last], counts[owner]], [1, 1, 0]);
  const [job] = await fanouts(groupId);
  assertEquals(job.data.mode, "SCAN");
  assertEquals(await fs.query("recipients", [], { parent: job.path }), []);
  assertEquals(fixture.notifications.sent, []);
  const now = new Date();
  writes = [
    fs.deleteWrite(memberPath(groupId, removed)),
    fs.updateWrite(`users/${deleting}`, { deletionRequestedAt: now }),
    fs.updateWrite(memberPath(groupId, late), { joinedAt: new Date(Date.now() + 60_000) }),
    fs.updateWrite(`chats/${chatId}`, { memberIds: roster }),
  ];
  await fs.commit(writes);
  const store = scoped(groupId);
  const failPage: typeof fs.runTransaction = (operation, attempts) => fs.runTransaction(async tx => {
    await operation(tx);
    throw new Error("injected page transaction failure");
  }, attempts);
  await assertRejects(() => processGroupFanouts({ ...store, runTransaction: failPage }, now), Error, "injected page");
  assertEquals((await fs.get(job.path))?.cursor, 0);
  assertEquals(await inbox(first), []);
  await Promise.all([processGroupFanouts(store, now), processGroupFanouts(store, now)]);
  await drain(groupId);
  assertEquals((await fs.get(job.path))?.cursor, 6);
  assertEquals((await fs.get(job.path))?.state, "DONE");
  for (const uid of [first, last]) {
    assertEquals((await inbox(uid)).map(row => row.data.data), [{ chatId, messageId }]);
    assertEquals((await fs.query("notificationDeliveries", [{ field: "recipientUid", op: "EQUAL", value: uid }])).length, 1);
  }
  for (const uid of [owner, removed, deleting, late]) assertEquals(await inbox(uid), []);
});

test("F1 group CHAT_MESSAGE HTTP worker: muted/viewing retain inbox, eligible token retries once", async () => {
  const [owner, muted, viewing, eligible, lost] = ["owner", "muted", "viewing", "eligible", "lost"].map(user);
  const recipients = [muted, viewing, eligible, lost];
  const groupId = await seedGroup({ owner, members: recipients });
  const chatId = await seedGroupChat(groupId, [owner, ...recipients]);
  let now = new Date();
  await fs.commit([
    ...recipients.map(uid => fs.updateWrite(`users/${uid}`, { fcmTokens: [`demo-${uid}`] })),
    fs.updateWrite(participantPath(chatId, muted), { muted: true }),
    fs.createWrite(`${participantPath(chatId, viewing)}/presence/phone`, { viewingUntil: new Date(now.getTime() + 600_000) }),
    fs.createWrite(`${participantPath(chatId, eligible)}/presence/expired`, { viewingUntil: new Date(now.getTime() - 1) }),
  ]);
  const fixture = makeChatDeps({ now: () => now });
  await expect(handleChatSend(req(owner, { chatId, messageId: id(), text: "demo" }), fixture.deps), 201);
  const sent: string[] = [];
  const fake: Sender = (_token, { recipientUid }) => {
    sent.push(recipientUid);
    return Promise.resolve({ kind: sent.length === 1 ? "retry" : "accepted" });
  };
  const store = scoped(groupId, recipients);
  const tick = (key: string) => handleNotificationDelivery(new Request("http://local/notification-delivery", {
    method: "POST", headers: { "x-cron-secret": key },
  }), { store, send: fake, now: () => now, cronSecret: "test-f1" });
  await expect(tick("wrong"), 401);
  await expect(tick("test-f1"), 200); // atomically materialize all four inbox rows
  for (const uid of recipients) assertEquals((await inbox(uid)).length, 1);
  await fs.commit([fs.updateWrite(`chats/${chatId}`, { memberIds: [owner, muted, viewing, eligible] })]);
  await expect(tick("test-f1"), 200);
  assertEquals(sent, [eligible]);
  await expect(tick("test-f1"), 200);
  assertEquals(sent, [eligible]);
  now = new Date(now.getTime() + 120_000);
  await expect(tick("test-f1"), 200);
  assertEquals(sent, [eligible, eligible]);
  for (const uid of recipients) assertEquals((await inbox(uid)).length, 1);
  assertEquals(await inbox(owner), []);
  assertEquals(fixture.notifications.sent, []);
  const [job] = await fanouts(groupId);
  assert(job.data.state === "DONE");
});

test("F1 large GROUP_ACTIVITY: member scan and deletion snapshot with more than 500 attendees", async () => {
  const prefix = `f1-${id()}`;
  const owner = `${prefix}-owner`;
  const members = Array.from({ length: 501 }, (_, i) => `${prefix}-${String(i).padStart(3, "0")}`);
  const [first, ...rest] = members;
  const last = rest.at(-1)!;
  await ensureUsers([first, last]);
  const groupId = await seedGroup({ owner });
  const memberWrites = members.map(uid =>
    fs.createWrite(memberPath(groupId, uid), { role: "MEMBER", joinedAt: new Date(0) }));
  for (let i = 0; i < memberWrites.length; i += 250) await fs.commit(memberWrites.slice(i, i + 250));
  const postId = id(), eventAt = new Date(Date.now() + 3_600_000);
  const fixture = makeTestDeps();
  const call = (action: string, data: Record<string, unknown>) =>
    handleGroupPost(req(owner, { action, groupId, requestId: id(), ...data }), fixture.deps);
  await expect(call("create", {
    requestId: postId, type: "EVENT", eventTitle: "F1 large event", eventAt: eventAt.toISOString(),
    eventLocation: "Demo", expectedCount: 100,
  }), 201);
  assertEquals((await fanouts(groupId))[0].data.mode, "SCAN");
  const attendeeWrites = members.map(uid => fs.createWrite(attendeePath(groupId, postId, uid), { userId: uid, eventAt }));
  for (let i = 0; i < attendeeWrites.length; i += 250) await fs.commit(attendeeWrites.slice(i, i + 250));
  await expect(call("update", { postId, eventLocation: "Demo B" }), 200);
  await expect(call("delete", { postId }), 200);
  assertEquals(await fs.query("attendees", [], { parent: postPath(groupId, postId) }), []);
  const modes = (await fanouts(groupId)).map(row => row.data.mode).sort();
  assertEquals(modes, ["SCAN", "SNAPSHOT", "SNAPSHOT"]);
  await drain(groupId);
  for (const uid of [first, last]) {
    const delivered = (await inbox(uid)).map(row => (row.data.data as { event: string }).event).sort();
    assertEquals(delivered, ["EVENT_CREATED", "EVENT_DELETED", "EVENT_UPDATED"]);
  }
  assertEquals(await inbox(owner), []);
});
