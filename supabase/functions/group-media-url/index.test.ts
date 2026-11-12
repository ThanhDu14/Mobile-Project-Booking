// Test `group-media-url` (spec 090 T040: M1, M2, discard) trên Firebase Emulator (Auth 9099, Firestore 8080).
// Tự bỏ qua khi thiếu biến môi trường Emulator. Chạy:
//   cd supabase/functions
//   FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 \
//   FIREBASE_PROJECT_ID=demo-smashnow deno test --allow-env --allow-net group-media-url/index.test.ts
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { assertGroupInvariant, ensureUsers, makeTestDeps, seedGroup, seedPost } from "../_shared/groups_fixtures.ts";
import { bearer, emulatorToken } from "../_shared/testing.ts";
import { handleGroupMediaUrl } from "./handler.ts";

Deno.env.set("FIREBASE_PROJECT_ID", Deno.env.get("FIREBASE_PROJECT_ID") ?? "demo-smashnow");

const ENABLED = !!Deno.env.get("FIRESTORE_EMULATOR_HOST") && !!Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST");
const RUN = `mu${crypto.randomUUID().slice(0, 8)}`;
const T = (name: string, fn: () => Promise<void>) => Deno.test({ name, ignore: !ENABLED, fn });
const BUCKET = "group-media";
const MB = 1024 * 1024;

const tokenOf = (uid: string) => emulatorToken({ sub: uid, user_id: uid, appRole: "PLAYER" });

async function call(deps: ReturnType<typeof makeTestDeps>["deps"], uid: string, body: unknown) {
  const r = await handleGroupMediaUrl(bearer(tokenOf(uid), { body: JSON.stringify(body) }), deps);
  return { status: r.status, body: await r.json() };
}

/** Nhóm mẫu: chủ `owner`, thành viên `member`; `outsider` không thuộc nhóm. */
async function setup(tag: string) {
  const owner = `${RUN}-${tag}-owner`;
  const member = `${RUN}-${tag}-member`;
  const outsider = `${RUN}-${tag}-outsider`;
  const groupId = await seedGroup({ owner, members: [member] });
  await ensureUsers([outsider]);
  return { owner, member, outsider, groupId, ...makeTestDeps() };
}

const file = (index: number, contentType = "image/jpeg", size = 100_000) => ({ index, contentType, size });

// ---------- M1: upload ----------

T("M1 upload: không phải thành viên → 403 NOT_MEMBER", async () => {
  const s = await setup("m1a");
  const r = await call(s.deps, s.outsider, {
    action: "upload",
    groupId: s.groupId,
    postId: crypto.randomUUID(),
    files: [file(0)],
  });
  assertEquals(r.status, 403);
  assertEquals(r.body.error.code, "NOT_MEMBER");
  assertEquals(s.storage.signedUploads.length, 0);
});

T("M1 upload: size 3 MB, image/gif, 5 file, index sai, postId không phải UUID → 400 INVALID_ARGUMENT", async () => {
  const s = await setup("m1b");
  const postId = crypto.randomUUID();
  const bad: Record<string, unknown>[] = [
    { postId, files: [file(0, "image/jpeg", 3 * MB)] },
    { postId, files: [file(0, "image/gif")] },
    { postId, files: [0, 1, 2, 3, 4].map((i) => file(i)) },
    { postId, files: [file(4)] },
    { postId, files: [file(-1)] },
    { postId, files: [file(0), file(0)] },
    { postId, files: [] },
    { postId, files: [file(0, "image/jpeg", 0)] },
    { postId: "khong-phai-uuid", files: [file(0)] },
    { files: [file(0)] },
  ];
  for (const extra of bad) {
    const r = await call(s.deps, s.member, { action: "upload", groupId: s.groupId, ...extra });
    assertEquals([r.status, r.body.error?.code], [400, "INVALID_ARGUMENT"], JSON.stringify(extra));
  }
  assertEquals(s.storage.signedUploads.length, 0);
});

T("M1 upload: hợp lệ → path {groupId}/{postId}/{index}.{ext} và signed URL tải lên được", async () => {
  const s = await setup("m1c");
  const postId = crypto.randomUUID();
  const r = await call(s.deps, s.member, {
    action: "upload",
    groupId: s.groupId,
    postId,
    files: [file(0, "image/jpeg", 2 * MB), file(1, "image/png"), file(3, "image/webp")],
  });
  assertEquals(r.status, 200);
  const uploads = r.body.uploads as { path: string; signedUrl: string; token: string }[];
  assertEquals(uploads.map((u) => u.path), [
    `${s.groupId}/${postId}/0.jpg`,
    `${s.groupId}/${postId}/1.png`,
    `${s.groupId}/${postId}/3.webp`,
  ]);
  for (const u of uploads) {
    assert(u.signedUrl.includes(u.path) && u.token.length > 0, "thiếu signedUrl/token");
    assert(s.storage.signedUploads.includes(`${BUCKET}/${u.path}`), "chưa xin signed upload ở bucket group-media");
  }
  await assertGroupInvariant(s.groupId);
});

// ---------- M2: download ----------

T("M2 download: 3 path, 1 path của nhóm không là thành viên → đúng 2 URL; có expiresAt ~ 1 giờ", async () => {
  const s = await setup("m2a");
  const other = await seedGroup({ owner: `${RUN}-m2a-other` });
  const p1 = `${s.groupId}/${crypto.randomUUID()}/0.jpg`;
  const p2 = `${s.groupId}/${crypto.randomUUID()}/1.png`;
  const p3 = `${other}/${crypto.randomUUID()}/0.jpg`;
  s.storage.put(BUCKET, p1, p2, p3);
  const before = Date.now();
  const r = await call(s.deps, s.member, { action: "download", paths: [p1, p2, p3] });
  assertEquals(r.status, 200);
  assertEquals(Object.keys(r.body.urls).sort(), [p1, p2].sort());
  const exp = new Date(r.body.expiresAt).getTime();
  assert(Math.abs(exp - (before + 3600_000)) < 60_000, `expiresAt lệch: ${r.body.expiresAt}`);
});

T("M2 download: 41 path → 400; 40 path (kể cả path lạ) → 200; paths sai kiểu → 400", async () => {
  const s = await setup("m2b");
  const mk = (n: number) => Array.from({ length: n }, (_, i) => `${s.groupId}/${crypto.randomUUID()}/${i % 4}.jpg`);
  let r = await call(s.deps, s.member, { action: "download", paths: mk(41) });
  assertEquals([r.status, r.body.error.code], [400, "INVALID_ARGUMENT"]);
  r = await call(s.deps, s.member, { action: "download", paths: mk(40).concat([]) });
  assertEquals(r.status, 200);
  assertEquals(r.body.urls, {}); // object chưa tồn tại nên không có URL
  for (const paths of ["x", [1], undefined]) {
    r = await call(s.deps, s.member, { action: "download", paths });
    assertEquals([r.status, r.body.error.code], [400, "INVALID_ARGUMENT"]);
  }
  r = await call(s.deps, s.member, { action: "download", paths: ["khong-co-gach", "../x/y.jpg", ""] });
  assertEquals([r.status, r.body.urls], [200, {}]); // path lạ bị bỏ qua
});

// ---------- discard ----------

T("discard: chưa có posts/{postId} → xóa mọi ảnh dưới {groupId}/{postId}/", async () => {
  const s = await setup("d1");
  const postId = crypto.randomUUID();
  const mine = [`${s.groupId}/${postId}/0.jpg`, `${s.groupId}/${postId}/2.png`];
  const another = `${s.groupId}/${crypto.randomUUID()}/0.jpg`;
  s.storage.put(BUCKET, ...mine, another);
  const r = await call(s.deps, s.member, { action: "discard", groupId: s.groupId, postId });
  assertEquals(r.status, 200);
  for (const p of mine) assert(!s.storage.has(BUCKET, p), `${p} chưa bị xóa`);
  assert(s.storage.has(BUCKET, another), "ảnh của bài khác bị xóa nhầm");
});

T("discard: bài đã tạo → không xóa gì; không phải thành viên → NOT_MEMBER", async () => {
  const s = await setup("d2");
  const postId = crypto.randomUUID();
  const path = `${s.groupId}/${postId}/0.jpg`;
  await seedPost(s.groupId, { id: postId, authorId: s.member, photoPaths: [path] });
  s.storage.put(BUCKET, path);
  let r = await call(s.deps, s.member, { action: "discard", groupId: s.groupId, postId });
  assertEquals(r.status, 200);
  assert(s.storage.has(BUCKET, path), "ảnh của bài đã tạo bị xóa");
  assertEquals(s.storage.removed.length, 0);

  r = await call(s.deps, s.outsider, { action: "discard", groupId: s.groupId, postId: crypto.randomUUID() });
  assertEquals([r.status, r.body.error.code], [403, "NOT_MEMBER"]);
  await assertGroupInvariant(s.groupId);
});
