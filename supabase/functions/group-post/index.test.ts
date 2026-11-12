// Test `group-post` create/update/delete cho bài POST (spec 090 T041: P1–P4) trên Firebase Emulator
// (Auth 9099, Firestore 8080). Tự bỏ qua khi thiếu biến môi trường Emulator. Chạy:
//   cd supabase/functions
//   FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 \
//   FIREBASE_PROJECT_ID=demo-smashnow deno test --allow-env --allow-net group-post/index.test.ts
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { get, query } from "../_shared/firestore.ts";
import { postPath } from "../_shared/groups.ts";
import { assertGroupInvariant, ensureUsers, makeTestDeps, seedGroup, seedPost } from "../_shared/groups_fixtures.ts";
import { bearer, emulatorToken } from "../_shared/testing.ts";
import { handleGroupPost } from "./handler.ts";

Deno.env.set("FIREBASE_PROJECT_ID", Deno.env.get("FIREBASE_PROJECT_ID") ?? "demo-smashnow");

const ENABLED = !!Deno.env.get("FIRESTORE_EMULATOR_HOST") && !!Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST");
const RUN = `gp${crypto.randomUUID().slice(0, 8)}`;
const T = (name: string, fn: () => Promise<void>) => Deno.test({ name, ignore: !ENABLED, fn });
const BUCKET = "group-media";

const tokenOf = (uid: string) => emulatorToken({ sub: uid, user_id: uid, appRole: "PLAYER" });

type Setup = Awaited<ReturnType<typeof setup>>;

async function call(s: Setup, uid: string, body: unknown) {
  const r = await handleGroupPost(bearer(tokenOf(uid), { body: JSON.stringify(body) }), s.deps);
  return { status: r.status, body: await r.json() };
}

/** Nhóm mẫu: chủ `owner`, quản trị viên `admin`, thành viên `member` và `member2`; `outsider` ngoài nhóm. */
async function setup(tag: string) {
  const owner = `${RUN}-${tag}-owner`;
  const admin = `${RUN}-${tag}-admin`;
  const member = `${RUN}-${tag}-member`;
  const member2 = `${RUN}-${tag}-member2`;
  const outsider = `${RUN}-${tag}-outsider`;
  const groupId = await seedGroup({ owner, admins: [admin], members: [member, member2] });
  await ensureUsers([outsider]);
  return { owner, admin, member, member2, outsider, groupId, ...makeTestDeps() };
}

/** Đặt sẵn ảnh trong group-media và trả path `{groupId}/{postId}/{i}.jpg`. */
function photos(s: Setup, postId: string, n: number): string[] {
  const paths = Array.from({ length: n }, (_, i) => `${s.groupId}/${postId}/${i}.jpg`);
  s.storage.put(BUCKET, ...paths);
  return paths;
}

const createBody = (s: Setup, postId: string, extra: Record<string, unknown> = {}) => ({
  action: "create",
  groupId: s.groupId,
  requestId: postId,
  type: "POST",
  content: "Chiều nay ai đánh cầu không?",
  photoPaths: [],
  ...extra,
});

const postCount = async (s: Setup) => (await query("posts", [], { parent: `groups/${s.groupId}` })).length;

// ---------- P1: create ----------

T("P1 create POST có 2 ảnh đã tải → 201 VISIBLE, photoPaths đúng thứ tự, authorSnapshot có", async () => {
  const s = await setup("p1a");
  const postId = crypto.randomUUID();
  const paths = photos(s, postId, 2);
  const r = await call(s, s.member, createBody(s, postId, { photoPaths: [paths[1], paths[0]] }));
  assertEquals(r.status, 201);
  const p = r.body.post;
  assertEquals(p.id, postId);
  assertEquals(p.groupId, s.groupId);
  assertEquals(p.authorId, s.member);
  assertEquals(p.type, "POST");
  assertEquals(p.status, "VISIBLE");
  assertEquals(p.hiddenBy, null);
  assertEquals(p.authorDeleted, false);
  assertEquals(p.photoPaths, [paths[1], paths[0]]);
  assertEquals(p.authorSnapshot.displayName, `Người dùng ${s.member}`);
  assertEquals(p.editedAt, null);
  assert(!Number.isNaN(Date.parse(p.createdAt)) && !Number.isNaN(Date.parse(p.updatedAt)), "createdAt/updatedAt");

  const doc = await get(postPath(s.groupId, postId));
  assertEquals(doc?.photoPaths, [paths[1], paths[0]]);
  assertEquals(doc?.status, "VISIBLE");
  assertEquals(doc?.editedAt, null);
  assert(doc?.createdAt instanceof Date);
  await assertGroupInvariant(s.groupId);
});

T("P1 create: 1 ảnh chưa tải → 400 PHOTO_MISSING, không có document", async () => {
  const s = await setup("p1b");
  const postId = crypto.randomUUID();
  const [a] = photos(s, postId, 1);
  const missing = `${s.groupId}/${postId}/1.jpg`;
  const r = await call(s, s.member, createBody(s, postId, { photoPaths: [a, missing] }));
  assertEquals([r.status, r.body.error.code], [400, "PHOTO_MISSING"]);
  assertEquals(await get(postPath(s.groupId, postId)), null);
  assertEquals(await postCount(s), 0);
  await assertGroupInvariant(s.groupId);
});

T("P1 create: path sai tiền tố, content 0 / 2001 ký tự, 5 ảnh, type lạ → 400 INVALID_ARGUMENT", async () => {
  const s = await setup("p1c");
  const postId = crypto.randomUUID();
  const own = photos(s, postId, 5);
  const otherPost = `${s.groupId}/${crypto.randomUUID()}/0.jpg`;
  const otherGroup = `${crypto.randomUUID()}/${postId}/0.jpg`;
  s.storage.put(BUCKET, otherPost, otherGroup);
  const cases: Record<string, unknown>[] = [
    { photoPaths: [otherPost] },
    { photoPaths: [otherGroup] },
    { photoPaths: [`${s.groupId}/${postId}/../x/0.jpg`] },
    { photoPaths: [own[0], own[0]] },
    { photoPaths: "a.jpg" },
    { content: "" },
    { content: "   " },
    { content: "a".repeat(2001) },
    { content: undefined },
    { photoPaths: own.slice(0, 5) },
    { type: "KHAC" },
  ];
  for (const extra of cases) {
    const r = await call(s, s.member, createBody(s, postId, extra));
    assertEquals([r.status, r.body.error?.code], [400, "INVALID_ARGUMENT"], JSON.stringify(extra).slice(0, 80));
  }
  const bad = await call(s, s.member, createBody(s, "không-phải-uuid"));
  assertEquals([bad.status, bad.body.error.code], [400, "INVALID_ARGUMENT"]);
  assertEquals(await postCount(s), 0);

  // Biên: 1 và 2000 ký tự, đủ 4 ảnh đều hợp lệ.
  const ok1 = await call(s, s.member, createBody(s, crypto.randomUUID(), { content: "a" }));
  assertEquals(ok1.status, 201);
  const ok2 = await call(
    s,
    s.member,
    createBody(s, postId, { content: "b".repeat(2000), photoPaths: own.slice(0, 4) }),
  );
  assertEquals(ok2.status, 201);
  assertEquals(ok2.body.post.photoPaths.length, 4);
  await assertGroupInvariant(s.groupId);
});

T("P1 create: type EVENT chưa cài → 501; nhóm LOCKED → 423; nhóm không có → 404", async () => {
  const s = await setup("p1d");
  let r = await call(s, s.member, createBody(s, crypto.randomUUID(), { type: "EVENT" }));
  assertEquals([r.status, r.body.error.code], [501, "NOT_IMPLEMENTED"]);

  const locked = await seedGroup({ owner: s.owner, members: [s.member], status: "LOCKED" });
  r = await call(s, s.member, { ...createBody(s, crypto.randomUUID()), groupId: locked });
  assertEquals([r.status, r.body.error.code], [423, "GROUP_LOCKED"]);

  r = await call(s, s.member, { ...createBody(s, crypto.randomUUID()), groupId: crypto.randomUUID() });
  assertEquals([r.status, r.body.error.code], [404, "GROUP_NOT_FOUND"]);
});

// ---------- P2: quyền ----------

T("P2 create: không phải thành viên → 403 NOT_MEMBER", async () => {
  const s = await setup("p2a");
  const r = await call(s, s.outsider, createBody(s, crypto.randomUUID()));
  assertEquals([r.status, r.body.error.code], [403, "NOT_MEMBER"]);
  assertEquals(await postCount(s), 0);
});

T(
  "P2 update/delete bài người khác → FORBIDDEN, kể cả chủ nhóm và quản trị viên; người ngoài → NOT_MEMBER",
  async () => {
    const s = await setup("p2b");
    const postId = crypto.randomUUID();
    const [path] = photos(s, postId, 1);
    await seedPost(s.groupId, { id: postId, authorId: s.member, photoPaths: [path] });
    for (const uid of [s.member2, s.admin, s.owner]) {
      for (const body of [{ action: "update", content: "sửa trộm" }, { action: "delete" }]) {
        const r = await call(s, uid, { ...body, groupId: s.groupId, postId });
        assertEquals([r.status, r.body.error.code], [403, "FORBIDDEN"], `${body.action} bởi ${uid}`);
      }
    }
    const r = await call(s, s.outsider, { action: "delete", groupId: s.groupId, postId });
    assertEquals([r.status, r.body.error.code], [403, "NOT_MEMBER"]);
    const doc = await get(postPath(s.groupId, postId));
    assertEquals(doc?.content, "Nội dung bài viết");
    assert(s.storage.has(BUCKET, path), "ảnh bị xóa dù bị từ chối");
    await assertGroupInvariant(s.groupId);
  },
);

T("P2 update/delete bài không có → 404 POST_NOT_FOUND", async () => {
  const s = await setup("p2c");
  for (const action of ["update", "delete"]) {
    const r = await call(s, s.member, { action, groupId: s.groupId, postId: crypto.randomUUID(), content: "x" });
    assertEquals([r.status, r.body.error.code], [404, "POST_NOT_FOUND"]);
  }
});

// ---------- P3: idempotent ----------

T("P3 create cùng requestId 3 lần (tuần tự và song song) → 1 bài; lần sau 200 trả bài cũ", async () => {
  const s = await setup("p3a");
  const postId = crypto.randomUUID();
  const first = await call(s, s.member, createBody(s, postId));
  assertEquals(first.status, 201);
  const again = await call(s, s.member, createBody(s, postId, { content: "nội dung khác" }));
  assertEquals(again.status, 200);
  assertEquals(again.body.post, first.body.post);
  const par = await Promise.all([1, 2, 3].map(() => call(s, s.member, createBody(s, postId))));
  assertEquals(par.map((r) => r.status), [200, 200, 200]);
  assertEquals(await postCount(s), 1);

  // Song song ngay từ đầu: đúng một lần 201.
  const id2 = crypto.randomUUID();
  const race = await Promise.all([1, 2, 3].map(() => call(s, s.member2, createBody(s, id2))));
  assertEquals(race.map((r) => r.status).sort(), [200, 200, 201]);
  assertEquals(await postCount(s), 2);
  await assertGroupInvariant(s.groupId);
});

T("P3 create: requestId đã là bài của người khác → 409 REQUEST_ID_CONFLICT", async () => {
  const s = await setup("p3b");
  const postId = crypto.randomUUID();
  await call(s, s.member, createBody(s, postId));
  const r = await call(s, s.member2, createBody(s, postId));
  assertEquals([r.status, r.body.error.code], [409, "REQUEST_ID_CONFLICT"]);
  assertEquals(await postCount(s), 1);
});

// ---------- P4: update, delete ----------

T("P4 update: bỏ 1 ảnh → ảnh bị xóa khỏi Storage, editedAt có giá trị; thêm ảnh chưa tải → PHOTO_MISSING", async () => {
  const s = await setup("p4a");
  const postId = crypto.randomUUID();
  const [a, b] = photos(s, postId, 2);
  assertEquals((await call(s, s.member, createBody(s, postId, { photoPaths: [a, b] }))).status, 201);

  const bad = await call(s, s.member, {
    action: "update",
    groupId: s.groupId,
    postId,
    photoPaths: [a, `${s.groupId}/${postId}/3.jpg`],
  });
  assertEquals([bad.status, bad.body.error.code], [400, "PHOTO_MISSING"]);
  assert(s.storage.has(BUCKET, b), "ảnh bị xóa khi update lỗi");

  const r = await call(s, s.member, {
    action: "update",
    groupId: s.groupId,
    postId,
    content: "Đã sửa",
    photoPaths: [a],
  });
  assertEquals(r.status, 200);
  assertEquals(r.body.post.content, "Đã sửa");
  assertEquals(r.body.post.photoPaths, [a]);
  assert(r.body.post.editedAt && !Number.isNaN(Date.parse(r.body.post.editedAt)), "editedAt");
  assert(!s.storage.has(BUCKET, b), "ảnh bỏ đi vẫn còn trong Storage");
  assert(s.storage.has(BUCKET, a), "ảnh giữ lại bị xóa");
  const doc = await get(postPath(s.groupId, postId));
  assert(doc?.editedAt instanceof Date);
  assertEquals(doc?.photoPaths, [a]);

  // Nội dung rỗng / quá dài → INVALID_ARGUMENT.
  for (const content of ["", "x".repeat(2001)]) {
    const e = await call(s, s.member, { action: "update", groupId: s.groupId, postId, content });
    assertEquals([e.status, e.body.error.code], [400, "INVALID_ARGUMENT"]);
  }
  await assertGroupInvariant(s.groupId);
});

T("P4 delete: bài có ảnh → document và ảnh trong group-media đều mất; ảnh bài khác còn", async () => {
  const s = await setup("p4b");
  const postId = crypto.randomUUID();
  const mine = photos(s, postId, 2);
  const otherId = crypto.randomUUID();
  const [keep] = photos(s, otherId, 1);
  assertEquals((await call(s, s.member, createBody(s, postId, { photoPaths: mine }))).status, 201);
  assertEquals((await call(s, s.member, createBody(s, otherId, { photoPaths: [keep] }))).status, 201);

  const r = await call(s, s.member, { action: "delete", groupId: s.groupId, postId });
  assertEquals(r.status, 200);
  assertEquals(await get(postPath(s.groupId, postId)), null);
  for (const p of mine) assert(!s.storage.has(BUCKET, p), `${p} vẫn còn`);
  assert(s.storage.has(BUCKET, keep));
  assertEquals(await postCount(s), 1);
  await assertGroupInvariant(s.groupId);
});

T("P4 bài HIDDEN: người đăng vẫn sửa và xóa được (quyết định: cho phép)", async () => {
  const s = await setup("p4c");
  const postId = crypto.randomUUID();
  await seedPost(s.groupId, { id: postId, authorId: s.member, hiddenBy: "GROUP" });
  let r = await call(s, s.member, { action: "update", groupId: s.groupId, postId, content: "sửa khi ẩn" });
  assertEquals(r.status, 200);
  assertEquals(r.body.post.status, "HIDDEN");
  assertEquals(r.body.post.hiddenBy, "GROUP");
  r = await call(s, s.member, { action: "delete", groupId: s.groupId, postId });
  assertEquals(r.status, 200);
  assertEquals(await get(postPath(s.groupId, postId)), null);
});

T("delete bài EVENT: xóa luôn attendees (chuẩn bị cho US4)", async () => {
  const s = await setup("p4d");
  const postId = await seedPost(s.groupId, { type: "EVENT", authorId: s.member, attendees: [s.member2, s.admin] });
  const r = await call(s, s.member, { action: "delete", groupId: s.groupId, postId });
  assertEquals(r.status, 200);
  assertEquals(await get(postPath(s.groupId, postId)), null);
  assertEquals((await query("attendees", [], { parent: postPath(s.groupId, postId) })).length, 0);
  await assertGroupInvariant(s.groupId);
});
