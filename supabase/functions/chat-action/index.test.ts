// Test `chat-action` (spec 091: D1–D4 openDirect, R1 recall, R2 report) trên Firebase Emulator. Tự bỏ qua khi thiếu biến Emulator.
//   FIREBASE_PROJECT_ID=demo-smashnow FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 \
//   deno test -A --config supabase/functions/deno.json supabase/functions/chat-action/index.test.ts
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { chatPath, directChatId, participantPath } from "../_shared/chats.ts";
import { assertChatInvariant, makeChatDeps, seedDirectChat, seedVenue } from "../_shared/chats_fixtures.ts";
import * as firestore from "../_shared/firestore.ts";
import { ensureUsers } from "../_shared/groups_fixtures.ts";
import { bearer, emulatorToken } from "../_shared/testing.ts";
import { handleChatAction } from "./handler.ts";

Deno.env.set("FIREBASE_PROJECT_ID", Deno.env.get("FIREBASE_PROJECT_ID") ?? "demo-smashnow");
const ENABLED = !!Deno.env.get("FIRESTORE_EMULATOR_HOST") && !!Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST");
const RUN = `ca${crypto.randomUUID().slice(0, 8)}`;
const T = (name: string, fn: () => Promise<void>) => Deno.test({ name, ignore: !ENABLED, fn });
const u = (tag: string, name: string) => `${RUN}-${tag}-${name}`;
const tokenOf = (uid: string) => emulatorToken({ sub: uid, user_id: uid, appRole: "PLAYER" });

type Deps = ReturnType<typeof makeChatDeps>["deps"];
async function act(deps: Deps, uid: string, body: Record<string, unknown>) {
  const r = await handleChatAction(bearer(tokenOf(uid), { body: JSON.stringify(body) }), deps);
  return { status: r.status, body: await r.json() };
}
const err = (r: { status: number; body: { error?: { code: string } } }) => [r.status, r.body.error?.code];
const chatDocs = async (chatId: string) => (await firestore.query("participants", [], { parent: chatPath(chatId) })).length;

T("D1 hai bên mở chat riêng cùng lúc lần đầu, lặp 20 lần: đúng 1 document chat mỗi lần", async () => {
  const { deps } = makeChatDeps();
  for (let i = 0; i < 20; i++) {
    // X và Y đều là chủ sân đã duyệt và cũng là người chơi: mỗi bên mở chat qua sân của bên kia.
    const x = u("d1", `x${i}`), y = u("d1", `y${i}`);
    await seedVenue(`${x}-v`, x);
    await seedVenue(`${y}-v`, y);
    const chatId = directChatId(x, y);
    const rs = await Promise.all([
      act(deps, x, { action: "openDirect", venueId: `${y}-v` }),
      act(deps, y, { action: "openDirect", venueId: `${x}-v` }),
      act(deps, x, { action: "openDirect", venueId: `${y}-v` }),
    ]);
    assert(rs.every((r) => r.status === 200 && r.body.chatId === chatId), JSON.stringify(rs));
    assertEquals(rs.filter((r) => r.body.created).length, 1);
    assertEquals(await chatDocs(chatId), 2);
    await assertChatInvariant(chatId);
  }
  console.log("D1 runs=20 chats=1");
});

T("D1/D2 tạo mới đủ trường; mở lần 2 từ sân khác của cùng chủ: cùng chatId, venue đổi, created=false", async () => {
  const p = u("d2", "p"), o = u("d2", "o");
  const { deps } = makeChatDeps();
  await seedVenue(`${o}-v1`, o);
  await seedVenue(`${o}-v2`, o);
  await ensureUsers([p]);
  const r1 = await act(deps, p, { action: "openDirect", venueId: `${o}-v1` });
  assertEquals([r1.status, r1.body.created, r1.body.chatId], [200, true, directChatId(p, o)]);
  const chat = await firestore.get(chatPath(r1.body.chatId));
  assertEquals([chat?.type, chat?.status, chat?.ownerId, chat?.playerId, chat?.venueId], ["DIRECT", "ACTIVE", o, p, `${o}-v1`]);
  assertEquals((chat?.memberIds as string[]).sort(), [o, p].sort());
  assertEquals(chat?.unreadCounts, { [p]: 0, [o]: 0 });
  assertEquals(chat?.venueSnapshot, { name: `Sân ${o}-v1`, photoPath: `${o}-v1/cover.jpg` });
  assertEquals(Object.keys(chat?.memberSnapshots as object).sort(), [o, p].sort());
  assert(chat?.createdAt instanceof Date);
  const part = await firestore.get(participantPath(r1.body.chatId, p));
  assertEquals([part?.muted, part?.blockedOther, part?.viewingUntil], [false, false, null]);
  await assertChatInvariant(r1.body.chatId);

  const r2 = await act(deps, p, { action: "openDirect", venueId: `${o}-v2` });
  assertEquals([r2.status, r2.body.created, r2.body.chatId], [200, false, r1.body.chatId]);
  const after = await firestore.get(chatPath(r1.body.chatId));
  assertEquals([after?.venueId, (after?.venueSnapshot as { name: string }).name], [`${o}-v2`, `Sân ${o}-v2`]);
  assertEquals(after?.ownerId, o); // ownerId cố định
});

T("D3 chủ sân mở sân của mình / sân HIDDEN / chủ sân chưa duyệt", async () => {
  const p = u("d3", "p"), o = u("d3", "o"), o2 = u("d3", "o2");
  const { deps } = makeChatDeps();
  await seedVenue(`${o}-v`, o);
  await seedVenue(`${o}-hidden`, o, { status: "HIDDEN" });
  await seedVenue(`${o2}-v`, o2, { ownerStatus: "NONE" });
  await ensureUsers([p]);
  assertEquals(err(await act(deps, o, { action: "openDirect", venueId: `${o}-v` })), [403, "OWN_VENUE"]);
  assertEquals(err(await act(deps, p, { action: "openDirect", venueId: `${o}-hidden` })), [409, "VENUE_INACTIVE"]);
  assertEquals(err(await act(deps, p, { action: "openDirect", venueId: `${o2}-v` })), [409, "OWNER_NOT_APPROVED"]);
  assertEquals(err(await act(deps, p, { action: "openDirect", venueId: `${RUN}-khong-co` })), [409, "VENUE_INACTIVE"]);
  assertEquals(err(await act(deps, p, { action: "nope" })), [400, "INVALID_ARGUMENT"]);
  assertEquals(await firestore.get(chatPath(directChatId(p, o))), null);
  assertEquals(await firestore.get(chatPath(directChatId(p, o2))), null);
});

T("D4 chat đã có: sân bị khóa / chủ sân hết quyền sau đó vẫn mở lại được", async () => {
  const p = u("d4", "p"), o = u("d4", "o");
  const { deps } = makeChatDeps();
  await seedVenue(`${o}-v`, o);
  await ensureUsers([p]);
  const r1 = await act(deps, p, { action: "openDirect", venueId: `${o}-v` });
  assertEquals(r1.status, 200);
  await firestore.commit([
    firestore.updateWrite(`venues/${o}-v`, { status: "LOCKED" }),
    firestore.updateWrite(`users/${o}`, { ownerStatus: "NONE" }),
  ]);
  const r2 = await act(deps, p, { action: "openDirect", venueId: `${o}-v` });
  assertEquals([r2.status, r2.body.chatId, r2.body.created], [200, r1.body.chatId, false]);
});

// ---------- R1 recall ----------

const MIN = 60_000;
async function chatWithMessage(tag: string, opts: { image?: boolean } = {}) {
  const p = u(tag, "p"), o = u(tag, "o");
  const createdAt = new Date(Date.now() - 20 * MIN);
  const messageId = crypto.randomUUID();
  const chatId = directChatId(p, o);
  const imagePath = opts.image ? `${chatId}/${messageId}.jpg` : null;
  await seedDirectChat(p, o, "v1", [
    { id: crypto.randomUUID(), senderId: o, createdAt: new Date(createdAt.getTime() - MIN) },
    { id: messageId, senderId: p, createdAt, imagePath },
  ]);
  return { p, o, chatId, messageId, createdAt, imagePath };
}

T("R1 recall sau 5 phút: RECALLED, xóa ảnh, lastMessage.preview đổi", async () => {
  const s = await chatWithMessage("r1a", { image: true });
  const { deps, storage } = makeChatDeps({ now: () => new Date(s.createdAt.getTime() + 5 * MIN) });
  storage.put("chat-media", s.imagePath!);
  const r = await act(deps, s.p, { action: "recall", chatId: s.chatId, messageId: s.messageId });
  assertEquals(r.status, 200);
  const msg = await firestore.get(`chats/${s.chatId}/messages/${s.messageId}`);
  assertEquals([msg?.status, msg?.text, msg?.imagePath], ["RECALLED", "", null]);
  assert(msg?.recalledAt instanceof Date);
  assert(!storage.has("chat-media", s.imagePath!));
  const last = (await firestore.get(chatPath(s.chatId)))?.lastMessage as Record<string, unknown>;
  assertEquals(last.preview, "Tin nhắn đã được thu hồi");
  assertEquals(last.messageId, s.messageId);
  assertEquals((await act(deps, s.p, { action: "recall", chatId: s.chatId, messageId: s.messageId })).status, 200); // lặp
  await assertChatInvariant(s.chatId);
});

T("R1 recall sau 11 phút → RECALL_WINDOW_PASSED; tin người khác → FORBIDDEN; người ngoài → NOT_PARTICIPANT", async () => {
  const s = await chatWithMessage("r1b");
  const late = makeChatDeps({ now: () => new Date(s.createdAt.getTime() + 11 * MIN) }).deps;
  assertEquals(err(await act(late, s.p, { action: "recall", chatId: s.chatId, messageId: s.messageId })), [409, "RECALL_WINDOW_PASSED"]);
  const ok = makeChatDeps({ now: () => new Date(s.createdAt.getTime() + 1 * MIN) }).deps;
  assertEquals(err(await act(ok, s.o, { action: "recall", chatId: s.chatId, messageId: s.messageId })), [403, "FORBIDDEN"]);
  await ensureUsers([u("r1b", "x")]);
  assertEquals(err(await act(ok, u("r1b", "x"), { action: "recall", chatId: s.chatId, messageId: s.messageId })), [403, "NOT_PARTICIPANT"]);
  assertEquals(err(await act(ok, s.p, { action: "recall", chatId: s.chatId, messageId: crypto.randomUUID() })), [404, "MESSAGE_NOT_FOUND"]);
  const msg = await firestore.get(`chats/${s.chatId}/messages/${s.messageId}`);
  assertEquals(msg?.status, "NORMAL");
});

// ---------- R2 report ----------

T("R2 report: lần 1 → 201; lần 2 → ALREADY_REPORTED; tin của mình → OWN_MESSAGE; tin đã thu hồi → INVALID_ARGUMENT", async () => {
  const s = await chatWithMessage("r2");
  const { deps } = makeChatDeps();
  const body = { action: "report", chatId: s.chatId, messageId: s.messageId, reason: "SCAM" };
  const id = `MESSAGE_${s.chatId}_${s.messageId}_${s.o}`;
  const r1 = await act(deps, s.o, body);
  assertEquals([r1.status, r1.body.reportId], [201, id]);
  const rep = await firestore.get(`reports/${id}`);
  assertEquals(
    [rep?.targetType, rep?.targetPath, rep?.reason, rep?.status, rep?.action, rep?.reporterId, rep?.detail],
    ["MESSAGE", `chats/${s.chatId}/messages/${s.messageId}`, "SCAM", "OPEN", "NONE", s.o, null],
  );
  assert(rep?.createdAt instanceof Date);
  assertEquals(err(await act(deps, s.o, body)), [409, "ALREADY_REPORTED"]);
  assertEquals(err(await act(deps, s.p, body)), [403, "OWN_MESSAGE"]);

  assertEquals(err(await act(deps, s.o, { ...body, reason: "OTHER" })), [400, "INVALID_ARGUMENT"]); // OTHER cần detail
  assertEquals(err(await act(deps, s.o, { ...body, reason: "SPAM", detail: "x".repeat(501) })), [400, "INVALID_ARGUMENT"]);
  assertEquals(err(await act(deps, s.o, { ...body, reason: "KHAC" })), [400, "INVALID_ARGUMENT"]);

  const recalled = await seedDirectChat(u("r2", "p2"), u("r2", "o2"), "v1", [{ id: crypto.randomUUID(), senderId: u("r2", "p2"), status: "RECALLED" }]);
  const [m] = await firestore.query("messages", [], { parent: chatPath(recalled) });
  assertEquals(
    err(await act(deps, u("r2", "o2"), { action: "report", chatId: recalled, messageId: m.id, reason: "SPAM" })),
    [400, "INVALID_ARGUMENT"],
  );
  const ok = await act(deps, s.o, { ...body, messageId: (await firestore.query("messages", [], { parent: chatPath(s.chatId) })).find((x) => x.data.senderId === s.o)!.id });
  assertEquals(err(ok), [403, "OWN_MESSAGE"]);
});
