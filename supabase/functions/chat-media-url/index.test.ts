// Test `chat-media-url` (spec 091: M1, M2, discard) trên Firebase Emulator. Tự bỏ qua khi thiếu biến Emulator.
//   FIREBASE_PROJECT_ID=demo-smashnow FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 \
//   deno test -A --config supabase/functions/deno.json supabase/functions/chat-media-url/index.test.ts
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { chatPath, directChatId } from "../_shared/chats.ts";
import { makeChatDeps, seedDirectChat } from "../_shared/chats_fixtures.ts";
import * as firestore from "../_shared/firestore.ts";
import { ensureUsers } from "../_shared/groups_fixtures.ts";
import { bearer, emulatorToken } from "../_shared/testing.ts";
import { handleChatMediaUrl } from "./handler.ts";

Deno.env.set("FIREBASE_PROJECT_ID", Deno.env.get("FIREBASE_PROJECT_ID") ?? "demo-smashnow");
const ENABLED = !!Deno.env.get("FIRESTORE_EMULATOR_HOST") && !!Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST");
const RUN = `cm${crypto.randomUUID().slice(0, 8)}`;
const T = (name: string, fn: () => Promise<void>) => Deno.test({ name, ignore: !ENABLED, fn });
const BUCKET = "chat-media";
const MB = 1024 * 1024;
const tokenOf = (uid: string) => emulatorToken({ sub: uid, user_id: uid, appRole: "PLAYER" });

type Deps = ReturnType<typeof makeChatDeps>["deps"];
async function call(deps: Deps, uid: string, body: unknown) {
  const r = await handleChatMediaUrl(bearer(tokenOf(uid), { body: JSON.stringify(body) }), deps);
  return { status: r.status, body: await r.json() };
}

async function setup(tag: string) {
  const p = `${RUN}-${tag}-p`, o = `${RUN}-${tag}-o`, x = `${RUN}-${tag}-x`;
  const chatId = await seedDirectChat(p, o, "v1");
  await ensureUsers([x]);
  return { p, o, x, chatId, ...makeChatDeps() };
}

T("M1 upload: không trong cuộc → NOT_PARTICIPANT; gif, 3 MB, messageId sai → INVALID_ARGUMENT; hợp lệ → path", async () => {
  const s = await setup("m1");
  const messageId = crypto.randomUUID();
  const base = { action: "upload", chatId: s.chatId, messageId, contentType: "image/jpeg", size: 100_000 };
  let r = await call(s.deps, s.x, base);
  assertEquals([r.status, r.body.error.code], [403, "NOT_PARTICIPANT"]);
  for (const bad of [{ contentType: "image/gif" }, { size: 3 * MB }, { size: 0 }, { messageId: "khong-uuid" }]) {
    r = await call(s.deps, s.p, { ...base, ...bad });
    assertEquals([r.status, r.body.error.code], [400, "INVALID_ARGUMENT"], JSON.stringify(bad));
  }
  assertEquals(s.storage.signedUploads.length, 0);
  r = await call(s.deps, s.p, { ...base, contentType: "image/webp", size: 2 * MB });
  assertEquals(r.status, 200);
  assertEquals(r.body.path, `${s.chatId}/${messageId}.webp`);
  assert(r.body.signedUrl.includes(r.body.path) && r.body.token.length > 0);
  assert(s.storage.signedUploads.includes(`${BUCKET}/${r.body.path}`));
  r = await call(s.deps, s.o, { ...base, messageId: crypto.randomUUID() }); // người kia cũng tải lên được
  assertEquals(r.status, 200);
  const none = await call(s.deps, s.p, { ...base, chatId: `${RUN}-khong-co` });
  assertEquals([none.status, none.body.error.code], [404, "CHAT_NOT_FOUND"]);
});

T("M2 download: chỉ URL của tin NORMAL trong chat mình; RECALLED, HIDDEN, chat khác, path lạ không có URL", async () => {
  const s = await setup("m2");
  const id = () => crypto.randomUUID();
  const [okId, recalledId, hiddenId] = [id(), id(), id()];
  const path = (m: string) => `${s.chatId}/${m}.jpg`;
  const paths = [okId, recalledId, hiddenId].map(path);
  // chat khác mà người gọi không ở trong đó
  const [p2, o2] = [`${RUN}-m2-p2`, `${RUN}-m2-o2`];
  const other = directChatId(p2, o2);
  const otherPath = `${other}/${okId}.jpg`;
  await seedDirectChat(p2, o2, "v1", [{ id: okId, senderId: p2, imagePath: otherPath }]);
  // dựng thẳng tin trong chat của s
  await firestore.commit([
    firestore.createWrite(`chats/${s.chatId}/messages/${okId}`, { senderId: s.p, text: "", imagePath: path(okId), status: "NORMAL", createdAt: new Date() }),
    firestore.createWrite(`chats/${s.chatId}/messages/${recalledId}`, { senderId: s.p, text: "", imagePath: null, status: "RECALLED", createdAt: new Date() }),
    firestore.createWrite(`chats/${s.chatId}/messages/${hiddenId}`, { senderId: s.p, text: "", imagePath: path(hiddenId), status: "HIDDEN", createdAt: new Date() }),
  ]);
  s.storage.put(BUCKET, ...paths, otherPath);

  const before = Date.now();
  const r = await call(s.deps, s.o, { action: "download", paths: [...paths, otherPath, "khong-co-gach", "../x/y.jpg"] });
  assertEquals(r.status, 200);
  assertEquals(Object.keys(r.body.urls), [path(okId)]);
  assert(Math.abs(new Date(r.body.expiresAt).getTime() - (before + 3600_000)) < 60_000);

  const outsider = await call(s.deps, s.x, { action: "download", paths: [path(okId)] });
  assertEquals([outsider.status, outsider.body.urls], [200, {}]);

  const mk = (n: number) => Array.from({ length: n }, () => path(id()));
  let bad = await call(s.deps, s.p, { action: "download", paths: mk(31) });
  assertEquals([bad.status, bad.body.error.code], [400, "INVALID_ARGUMENT"]);
  assertEquals((await call(s.deps, s.p, { action: "download", paths: mk(30) })).status, 200);
  for (const paths2 of ["x", [1], undefined]) {
    bad = await call(s.deps, s.p, { action: "download", paths: paths2 });
    assertEquals([bad.status, bad.body.error.code], [400, "INVALID_ARGUMENT"]);
  }
});

T("discard: chưa có tin → xóa ảnh của tin đó (không xóa ảnh tin khác); tin đã có → không xóa", async () => {
  const s = await setup("d");
  const m1 = crypto.randomUUID(), m2 = crypto.randomUUID(), m3 = crypto.randomUUID();
  const [a, b, c] = [m1, m2, m3].map((m) => `${s.chatId}/${m}.png`);
  s.storage.put(BUCKET, a, b, c);
  await firestore.commit([
    firestore.createWrite(`chats/${s.chatId}/messages/${m3}`, { senderId: s.p, text: "", imagePath: c, status: "NORMAL", createdAt: new Date() }),
  ]);
  let r = await call(s.deps, s.p, { action: "discard", chatId: s.chatId, messageId: m1 });
  assertEquals([r.status, r.body.deleted], [200, 1]);
  assert(!s.storage.has(BUCKET, a));
  assert(s.storage.has(BUCKET, b), "ảnh của tin khác bị xóa nhầm");
  r = await call(s.deps, s.p, { action: "discard", chatId: s.chatId, messageId: m3 });
  assertEquals([r.status, r.body.deleted], [200, 0]);
  assert(s.storage.has(BUCKET, c));
  r = await call(s.deps, s.x, { action: "discard", chatId: s.chatId, messageId: m2 });
  assertEquals([r.status, r.body.error.code], [403, "NOT_PARTICIPANT"]);
  assert(s.storage.has(BUCKET, b));
  assertEquals((await firestore.get(chatPath(s.chatId)))?.status, "ACTIVE");
});
