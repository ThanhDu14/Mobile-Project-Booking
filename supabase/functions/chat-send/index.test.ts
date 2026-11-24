// Test `chat-send` (spec 091: C1–C8) trên Firebase Emulator (Auth 9099, Firestore 8080). Tự bỏ qua khi thiếu biến Emulator.
//   FIREBASE_PROJECT_ID=demo-smashnow FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 \
//   deno test -A --config supabase/functions/deno.json supabase/functions/chat-send/index.test.ts
// Không xóa dữ liệu Emulator: mọi ID có tiền tố ngẫu nhiên của lần chạy.
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { chatPath, participantPath } from "../_shared/chats.ts";
import {
  assertChatInvariant,
  ensureUsers,
  makeChatDeps,
  seedDirectChat,
  seedGroupChat,
} from "../_shared/chats_fixtures.ts";
import * as firestore from "../_shared/firestore.ts";
import { onGroupMembershipChanged } from "../_shared/groups.ts";
import { makeTestDeps, seedGroup } from "../_shared/groups_fixtures.ts";
import { handleGroupManage } from "../group-manage/handler.ts";
import { bearer, emulatorToken } from "../_shared/testing.ts";
import { handleChatSend } from "./handler.ts";

Deno.env.set("FIREBASE_PROJECT_ID", Deno.env.get("FIREBASE_PROJECT_ID") ?? "demo-smashnow");
const ENABLED = !!Deno.env.get("FIRESTORE_EMULATOR_HOST") && !!Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST");
const RUN = `cs${crypto.randomUUID().slice(0, 8)}`;
const T = (name: string, fn: () => Promise<void>) => Deno.test({ name, ignore: !ENABLED, fn });
const u = (tag: string, name: string) => `${RUN}-${tag}-${name}`;
const tokenOf = (uid: string) => emulatorToken({ sub: uid, user_id: uid, appRole: "PLAYER" });

type Deps = ReturnType<typeof makeChatDeps>["deps"];
async function send(deps: Deps, uid: string, body: Record<string, unknown>) {
  const r = await handleChatSend(bearer(tokenOf(uid), { body: JSON.stringify(body) }), deps);
  return { status: r.status, body: await r.json() };
}
const err = (r: { status: number; body: { error?: { code: string } } }) => [r.status, r.body.error?.code];

/** Chat riêng `p`–`o` (người chơi, chủ sân). */
async function direct(tag: string, messages = 0) {
  const p = u(tag, "p"), o = u(tag, "o");
  const chatId = await seedDirectChat(p, o, "v1", Array.from({ length: messages }, () => ({ senderId: p })));
  return { p, o, chatId, ...makeChatDeps() };
}

T("C1 tin chữ hợp lệ: NORMAL, createdAt server, lastMessage đúng, unreadCounts người nhận +1", async () => {
  const s = await direct("c1");
  const messageId = crypto.randomUUID();
  const r = await send(s.deps, s.p, { chatId: s.chatId, messageId, text: "  Tối thứ 7 còn sân không?  " });
  assertEquals(r.status, 201);
  assertEquals([r.body.message.id, r.body.message.status, r.body.message.text], [messageId, "NORMAL", "Tối thứ 7 còn sân không?"]);
  assert(r.body.message.createdAt);
  const msg = await firestore.get(`chats/${s.chatId}/messages/${messageId}`);
  assert(msg?.createdAt instanceof Date);
  assertEquals(msg?.senderId, s.p);
  assertEquals((msg?.senderSnapshot as { displayName: string }).displayName, `Người dùng ${s.p}`);
  const chat = await firestore.get(chatPath(s.chatId));
  const last = chat?.lastMessage as Record<string, unknown>;
  assertEquals([last.messageId, last.senderId, last.type, last.preview], [messageId, s.p, "TEXT", "Tối thứ 7 còn sân không?"]);
  assertEquals((last.createdAt as Date).getTime(), (msg?.createdAt as Date).getTime());
  assertEquals((chat?.updatedAt as Date).getTime(), (msg?.createdAt as Date).getTime());
  assertEquals(chat?.unreadCounts, { [s.p]: 0, [s.o]: 1 });
  await assertChatInvariant(s.chatId);
});

T("H1 DIRECT: atomic inbox/job survives muted/viewing, excludes sender; replay and callback failure cannot duplicate", async () => {
  const s = await direct("h1");
  await firestore.commit([firestore.updateWrite(participantPath(s.chatId, s.o), { muted: true, viewingUntil: new Date(Date.now() + 60000) })]);
  const messageId = crypto.randomUUID();
  const deps = { ...s.deps, notify: () => Promise.reject(new Error("must not call legacy callback")) };
  const body = { chatId: s.chatId, messageId, text: "Private text" };
  assertEquals((await send(deps, s.p, body)).status, 201);
  assertEquals((await send(deps, s.p, body)).status, 200);
  const inbox = await firestore.query("notifications", [], { parent: `users/${s.o}` });
  assertEquals(inbox.length, 1);
  assertEquals(inbox[0].data.type, "CHAT_MESSAGE");
  assertEquals(inbox[0].data.body, "Bạn có tin nhắn mới.");
  assertEquals(inbox[0].data.data, { chatId: s.chatId, messageId });
  assert(inbox[0].data.createdAt instanceof Date);
  assertEquals((await firestore.query("notifications", [], { parent: `users/${s.p}` })).length, 0);
  const jobs = await firestore.query("notificationDeliveries", [{ field: "recipientUid", op: "EQUAL", value: s.o }]);
  assertEquals(jobs.length, 1);
  assertEquals(jobs[0].data.notificationId, inbox[0].id);
  assertEquals(s.notifications.sent, []);
});

T("C2 cùng messageId 5 lần (song song và lặp lại): 1 tin, unreadCounts +1 đúng một lần", async () => {
  const s = await direct("c2");
  const messageId = crypto.randomUUID();
  const body = { chatId: s.chatId, messageId, text: "gửi lại" };
  const rs = await Promise.all(Array.from({ length: 5 }, () => send(s.deps, s.p, body)));
  rs.push(await send(s.deps, s.p, body));
  assertEquals(rs.map((r) => r.status).filter((c) => c === 201).length, 1);
  assert(rs.every((r) => r.status === 201 || r.status === 200), JSON.stringify(rs.map((r) => r.status)));
  assertEquals((await firestore.query("messages", [], { parent: chatPath(s.chatId) })).length, 1);
  assertEquals((await firestore.get(chatPath(s.chatId)))?.unreadCounts, { [s.p]: 0, [s.o]: 1 });
  await assertChatInvariant(s.chatId);
});

T("C3 20 thành viên gửi song song trong chat nhóm, lặp 10 lần", async () => {
  const members = Array.from({ length: 20 }, (_, i) => u("c3", `m${String(i).padStart(2, "0")}`));
  const groupId = await seedGroup({ owner: members[0], members: members.slice(1) });
  const chatId = await seedGroupChat(groupId, members);
  const { deps } = makeChatDeps();
  for (let round = 1; round <= 10; round++) {
    const rs = await Promise.all(members.map((m) =>
      send(deps, m, { chatId, messageId: crypto.randomUUID(), text: `vòng ${round}` })
    ));
    assertEquals(rs.map((r) => r.status), members.map(() => 201), JSON.stringify(rs.filter((r) => r.status !== 201)));
    await assertChatInvariant(chatId);
    const chat = await firestore.get(chatPath(chatId));
    const counts = chat?.unreadCounts as Record<string, number>;
    for (const m of members) assertEquals(counts[m], round * 19, `unreadCounts[${m}] vòng ${round}`);
  }
  assertEquals((await firestore.query("messages", [], { parent: chatPath(chatId) })).length, 200);
  console.log("C3 runs=10 messages=20 unreadOk=true lastOk=true");
});

T("C4 người ngoài cuộc, người đã rời nhóm, chat không có → NOT_PARTICIPANT / CHAT_NOT_FOUND", async () => {
  const s = await direct("c4");
  const stranger = u("c4", "x");
  await ensureUsers([stranger]);
  assertEquals(err(await send(s.deps, stranger, { chatId: s.chatId, messageId: crypto.randomUUID(), text: "hi" })), [403, "NOT_PARTICIPANT"]);
  assertEquals(err(await send(s.deps, s.p, { chatId: `${RUN}-none`, messageId: crypto.randomUUID(), text: "hi" })), [404, "CHAT_NOT_FOUND"]);
  assertEquals((await firestore.query("messages", [], { parent: chatPath(s.chatId) })).length, 0);
  // đã rời nhóm: memberIds không còn uid
  const gid = await seedGroup({ owner: u("c4", "go"), members: [u("c4", "gm")] });
  const gChat = await seedGroupChat(gid, [u("c4", "go")]);
  assertEquals(err(await send(s.deps, u("c4", "gm"), { chatId: gChat, messageId: crypto.randomUUID(), text: "hi" })), [403, "NOT_PARTICIPANT"]);
});

T("C5 bị chặn / người chặn gửi / bỏ chặn rồi gửi", async () => {
  const s = await direct("c5");
  const mk = (text: string) => ({ chatId: s.chatId, messageId: crypto.randomUUID(), text });
  await firestore.commit([firestore.updateWrite(participantPath(s.chatId, s.o), { blockedOther: true })]);
  assertEquals(err(await send(s.deps, s.p, mk("a"))), [403, "BLOCKED"]);
  assertEquals((await send(s.deps, s.o, mk("b"))).status, 201); // người chặn vẫn gửi được
  await firestore.commit([firestore.updateWrite(participantPath(s.chatId, s.o), { blockedOther: false })]);
  assertEquals((await send(s.deps, s.p, mk("c"))).status, 201);
  await assertChatInvariant(s.chatId);
});

T("C6 nhóm LOCKED → GROUP_LOCKED; chat READ_ONLY → CHAT_READ_ONLY", async () => {
  const owner = u("c6", "o"), mem = u("c6", "m");
  const gid = await seedGroup({ owner, members: [mem], status: "LOCKED" });
  const chatId = await seedGroupChat(gid, [owner, mem]);
  const { deps } = makeChatDeps();
  assertEquals(err(await send(deps, mem, { chatId, messageId: crypto.randomUUID(), text: "hi" })), [423, "GROUP_LOCKED"]);
  const ro = await seedDirectChat(u("c6", "p"), u("c6", "q"), "v1", [], "READ_ONLY");
  assertEquals(err(await send(deps, u("c6", "p"), { chatId: ro, messageId: crypto.randomUUID(), text: "hi" })), [409, "CHAT_READ_ONLY"]);
  await assertChatInvariant(chatId);
});

T("C7 chữ: rỗng, chỉ khoảng trắng, 1001 ký tự → INVALID_ARGUMENT; 1000 ký tự được", async () => {
  const s = await direct("c7");
  const mk = (text: unknown) => ({ chatId: s.chatId, messageId: crypto.randomUUID(), text });
  for (const text of ["", "   \n ", "a".repeat(1001), 5]) {
    assertEquals(err(await send(s.deps, s.p, mk(text))), [400, "INVALID_ARGUMENT"], String(text).slice(0, 10));
  }
  assertEquals(err(await send(s.deps, s.p, { chatId: s.chatId, messageId: "khong-uuid", text: "x" })), [400, "INVALID_ARGUMENT"]);
  assertEquals((await send(s.deps, s.p, mk("a".repeat(1000)))).status, 201);
  assertEquals((await firestore.query("messages", [], { parent: chatPath(s.chatId) })).length, 1);
});

T("C7 ảnh: chưa tải → PHOTO_MISSING; path sai → INVALID_ARGUMENT; ảnh hợp lệ không chú thích → IMAGE", async () => {
  const s = await direct("c7i");
  const messageId = crypto.randomUUID();
  const path = `${s.chatId}/${messageId}.jpg`;
  assertEquals(err(await send(s.deps, s.p, { chatId: s.chatId, messageId, imagePath: path })), [400, "PHOTO_MISSING"]);
  assertEquals(err(await send(s.deps, s.p, { chatId: s.chatId, messageId, imagePath: `khac/${messageId}.jpg` })), [400, "INVALID_ARGUMENT"]);
  assertEquals(err(await send(s.deps, s.p, { chatId: s.chatId, messageId, imagePath: `${s.chatId}/${messageId}.gif` })), [400, "INVALID_ARGUMENT"]);
  assertEquals((await firestore.query("messages", [], { parent: chatPath(s.chatId) })).length, 0);
  s.storage.put("chat-media", path);
  const r = await send(s.deps, s.p, { chatId: s.chatId, messageId, text: "", imagePath: path });
  assertEquals([r.status, r.body.message.imagePath], [201, path]);
  const last = (await firestore.get(chatPath(s.chatId)))?.lastMessage as Record<string, unknown>;
  assertEquals([last.type, last.preview], ["IMAGE", "[Hình ảnh]"]);
  await assertChatInvariant(s.chatId);
});

T("C8 notification inbox callback includes muted/viewing recipients; push suppression happens at delivery", async () => {
  const tag = "c8";
  const [a, b, c, d] = ["a", "b", "c", "d"].map((n) => u(tag, n));
  const gid = await seedGroup({ owner: a, members: [b, c, d] });
  const chatId = await seedGroupChat(gid, [a, b, c, d]);
  const { deps, notifications } = makeChatDeps();
  const now = Date.now();
  await firestore.commit([
    firestore.updateWrite(participantPath(chatId, b), { muted: true }),
    firestore.updateWrite(participantPath(chatId, c), { viewingUntil: new Date(now + 60_000) }),
    firestore.updateWrite(participantPath(chatId, d), { viewingUntil: new Date(now - 60_000) }), // đã hết hạn
  ]);
  const messageId = crypto.randomUUID();
  assertEquals((await send(deps, a, { chatId, messageId, text: "hello" })).status, 201);
  assertEquals(notifications.recipients().sort(), [b, c, d].sort());
  assertEquals(notifications.sent.every(n => n.type === "CHAT_MESSAGE" && n.data.chatId === chatId && n.data.messageId === messageId), true);
  // gửi lại (200) không thông báo lần nữa; unreadCounts không phụ thuộc muted
  await send(deps, a, { chatId, messageId, text: "hello" });
  assertEquals(notifications.sent.length, 3);
  assertEquals((await firestore.get(chatPath(chatId)))?.unreadCounts, { [a]: 0, [b]: 1, [c]: 1, [d]: 1 });
  // lỗi gửi thông báo bị bỏ qua
  const failing = makeChatDeps({ notify: () => Promise.reject(new Error("boom")) }).deps;
  assertEquals((await send(failing, a, { chatId, messageId: crypto.randomUUID(), text: "x" })).status, 201);
  await assertChatInvariant(chatId);
});

T("T031 tích hợp 090: group-manage join rồi chat-send được; remove rồi chat-send → NOT_PARTICIPANT", async () => {
  const owner = u("int", "o"), joiner = u("int", "j");
  const gid = await seedGroup({ owner, visibility: "PUBLIC" });
  const chatId = await seedGroupChat(gid, [owner]);
  await ensureUsers([joiner]);
  // Hook thật của 090 (syncGroupChat) thay cho bản ghi lại của makeTestDeps.
  const g = makeTestDeps({ onMembershipChanged: onGroupMembershipChanged });
  const manage = async (uid: string, body: Record<string, unknown>) => {
    const r = await handleGroupManage(bearer(tokenOf(uid), { body: JSON.stringify(body) }), g.deps);
    return { status: r.status, body: await r.json() };
  };
  const { deps } = makeChatDeps();
  const mk = () => ({ chatId, messageId: crypto.randomUUID(), text: "xin chào" });

  assertEquals(err(await send(deps, joiner, mk())), [403, "NOT_PARTICIPANT"]);
  assertEquals((await manage(joiner, { action: "join", groupId: gid })).body.result, "JOINED");
  assertEquals((await send(deps, joiner, mk())).status, 201);
  await assertChatInvariant(chatId);

  assertEquals((await manage(owner, { action: "remove", groupId: gid, userId: joiner })).status, 200);
  assertEquals(err(await send(deps, joiner, mk())), [403, "NOT_PARTICIPANT"]);
  assertEquals((await send(deps, owner, mk())).status, 201);
  await assertChatInvariant(chatId);
});
