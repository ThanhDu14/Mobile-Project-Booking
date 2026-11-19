// Test H1 (syncGroupChat), H2 (purgeChat), A1 (anonymizeCommunityChats) của spec 091 trên Firebase Emulator.
// Tự bỏ qua khi thiếu FIRESTORE_EMULATOR_HOST. Chạy:
//   FIREBASE_PROJECT_ID=demo-smashnow FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 \
//   deno test -A --config supabase/functions/deno.json supabase/functions/_shared/chats.test.ts
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import {
  anonymizeCommunityChats,
  chatPath,
  directChatId,
  groupChatId,
  inChatTx,
  messagePath,
  participantPath,
  purgeChat,
  syncGroupChat,
} from "./chats.ts";
import { assertChatInvariant, ensureUsers, makeChatDeps, seedDirectChat, seedGroupChat } from "./chats_fixtures.ts";
import * as firestore from "./firestore.ts";
import type { MembershipChange } from "./groups.ts";

Deno.env.set("FIREBASE_PROJECT_ID", Deno.env.get("FIREBASE_PROJECT_ID") ?? "demo-smashnow");
const ENABLED = !!Deno.env.get("FIRESTORE_EMULATOR_HOST");
const RUN = `c${crypto.randomUUID().slice(0, 8)}`;
const u = (name: string) => `${RUN}-${name}`;
const T = (name: string, fn: () => Promise<void>) => Deno.test({ name, ignore: !ENABLED, fn });
const { deps } = makeChatDeps();

/** Chạy hook như `inGroupTx` (ghi gom lại commit một lần). */
const sync = (groupId: string, change: MembershipChange) =>
  inChatTx(deps, groupChatId(groupId), (tx) => syncGroupChat(tx, groupId, change));

T("H1 created: tạo chat nhóm + participants của chủ nhóm", async () => {
  const g = u("g1");
  await ensureUsers([u("owner")]);
  await sync(g, { created: true, added: [u("owner")] });
  const chat = await firestore.get(chatPath(groupChatId(g)));
  assertEquals([chat?.type, chat?.groupId, chat?.status, chat?.memberIds], ["GROUP", g, "ACTIVE", [u("owner")]]);
  assert(chat?.createdAt instanceof Date && chat?.updatedAt instanceof Date);
  const p = await firestore.get(participantPath(groupChatId(g), u("owner")));
  assertEquals(p?.userId, u("owner"));
  assertEquals(p?.muted, false);
  assertEquals((p?.joinedAt as Date).getTime(), (p?.lastReadAt as Date).getTime());
  await assertChatInvariant(groupChatId(g));
});

T("H1 added / removed: memberIds, participants, unreadCounts khớp; người mới có lastReadAt = joinedAt", async () => {
  const g = u("g2");
  const chatId = await seedGroupChat(g, [u("o2"), u("m1")], [{ senderId: u("o2") }]);
  await ensureUsers([u("m2"), u("m3")]);
  await sync(g, { added: [u("m2"), u("m3")] });
  await assertChatInvariant(chatId);
  let chat = await firestore.get(chatPath(chatId));
  assertEquals((chat?.memberIds as string[]).sort(), [u("m1"), u("m2"), u("m3"), u("o2")].sort());
  assertEquals((chat?.unreadCounts as Record<string, number>)[u("m2")], 0);
  const p = await firestore.get(participantPath(chatId, u("m2")));
  assertEquals((p?.joinedAt as Date).getTime(), (p?.lastReadAt as Date).getTime());

  await sync(g, { added: [u("m2")] }); // thêm lại người đã có: không đổi
  await sync(g, { removed: [u("m1"), u("m3")] });
  await assertChatInvariant(chatId);
  chat = await firestore.get(chatPath(chatId));
  assertEquals((chat?.memberIds as string[]).sort(), [u("m2"), u("o2")].sort());
  assertEquals(await firestore.get(participantPath(chatId, u("m1"))), null);
});

T("H1 deleted: memberIds rỗng, status DELETED; chat chưa tồn tại thì bỏ qua", async () => {
  const g = u("g3");
  const chatId = await seedGroupChat(g, [u("o3"), u("m4")]);
  await sync(g, { deleted: true });
  const chat = await firestore.get(chatPath(chatId));
  assertEquals([chat?.memberIds, chat?.status], [[], "DELETED"]);
  await sync(u("khong-co"), { removed: [u("m4")] });
  assertEquals(await firestore.get(chatPath(groupChatId(u("khong-co")))), null);
});

T("H2 purgeChat: hết messages, participants, ảnh của chat; ảnh chat khác còn", async () => {
  const g = u("g4");
  const chatId = groupChatId(g);
  const img = `${chatId}/${crypto.randomUUID()}.jpg`;
  const { deps: d, storage } = makeChatDeps();
  await seedGroupChat(g, [u("o4"), u("m5")], [{ senderId: u("o4") }, { senderId: u("m5"), imagePath: img }]);
  const other = `${groupChatId(u("g5"))}/x.jpg`;
  storage.put("chat-media", img, other);
  await purgeChat(chatId, d);
  assertEquals((await firestore.query("messages", [], { parent: chatPath(chatId) })).length, 0);
  assertEquals((await firestore.query("participants", [], { parent: chatPath(chatId) })).length, 0);
  assert(!storage.has("chat-media", img));
  assert(storage.has("chat-media", other));
  await purgeChat(chatId, d); // idempotent
});

T("A1 anonymizeCommunityChats: chat riêng READ_ONLY, memberSnapshots null; tin của uid senderDeleted", async () => {
  const p = u("p"), o = u("o");
  const direct = await seedDirectChat(p, o, "v1", [{ senderId: p }, { senderId: o }, { senderId: p }]);
  const group = await seedGroupChat(u("g6"), [u("o6"), p], [{ senderId: p }, { senderId: u("o6") }]);
  await anonymizeCommunityChats(p, deps);
  const chat = await firestore.get(chatPath(direct));
  assertEquals(chat?.status, "READ_ONLY");
  const snaps = chat?.memberSnapshots as Record<string, unknown>;
  assertEquals(snaps[p], null);
  assert(snaps[o] !== null);
  for (const c of [direct, group]) {
    for (const m of await firestore.query("messages", [], { parent: chatPath(c) })) {
      assertEquals(m.data.senderDeleted, m.data.senderId === p, m.path);
      assertEquals(m.data.senderSnapshot === null, m.data.senderId === p, m.path);
    }
  }
  assertEquals((await firestore.get(chatPath(group)))?.status, "ACTIVE"); // chat nhóm do 090 xử lý
  await anonymizeCommunityChats(p, deps); // idempotent
  assertEquals(directChatId(p, o), direct);
  assert(await firestore.get(messagePath(direct, (await firestore.query("messages", [], { parent: chatPath(direct) }))[0].id)));
});
