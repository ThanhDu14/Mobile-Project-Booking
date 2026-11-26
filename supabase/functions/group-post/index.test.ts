// Test `group-post` create/update/delete cho bài POST (spec 090 T041: P1–P4) trên Firebase Emulator
// (Auth 9099, Firestore 8080). Tự bỏ qua khi thiếu biến môi trường Emulator. Chạy:
//   cd supabase/functions
//   FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 \
//   FIREBASE_PROJECT_ID=demo-smashnow deno test --allow-env --allow-net group-post/index.test.ts
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { get, query } from "../_shared/firestore.ts";
import { attendeePath, postPath } from "../_shared/groups.ts";
import { assertGroupInvariant, ensureUsers, makeTestDeps, seedGroup, seedPost } from "../_shared/groups_fixtures.ts";
import { bearer, emulatorToken } from "../_shared/testing.ts";
import { handleGroupManage } from "../group-manage/handler.ts";
import { handleGroupPost } from "./handler.ts";

Deno.env.set("FIREBASE_PROJECT_ID", Deno.env.get("FIREBASE_PROJECT_ID") ?? "demo-smashnow");

const ENABLED = !!Deno.env.get("FIRESTORE_EMULATOR_HOST") && !!Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST");
const RUN = `gp${crypto.randomUUID().slice(0, 8)}`;
const T = (name: string, fn: () => Promise<void>) => Deno.test({ name, ignore: !ENABLED, fn });
const BUCKET = "group-media";

const tokenOf = (uid: string) => emulatorToken({ sub: uid, user_id: uid, appRole: "PLAYER" });

type Setup = Awaited<ReturnType<typeof setup>>;

async function call(s: Setup, uid: string, body: Record<string, unknown>) {
  const r = await handleGroupPost(bearer(tokenOf(uid), { body: JSON.stringify({ requestId: crypto.randomUUID(), ...body }) }), s.deps);
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

T("P1 create: nhóm LOCKED → 423; nhóm không có → 404", async () => {
  const s = await setup("p1d");
  let r: Awaited<ReturnType<typeof call>>;

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

// ---------- US4: sự kiện giao lưu (T051: P5–P8) ----------

const DAY = 24 * 3600_000;
const inFuture = (ms = DAY) => new Date(Date.now() + ms).toISOString();

const eventBody = (s: Setup, postId: string, extra: Record<string, unknown> = {}) => ({
  action: "create",
  groupId: s.groupId,
  requestId: postId,
  type: "EVENT",
  content: "Mang vợt và nước uống",
  photoPaths: [],
  eventTitle: "Giao lưu cuối tuần",
  eventAt: inFuture(),
  eventLocation: "Sân A, Quận 7",
  expectedCount: 8,
  ...extra,
});

const attendeeDocs = (s: { groupId: string }, postId: string) =>
  query("attendees", [], { parent: postPath(s.groupId, postId) });
const attend = (s: { groupId: string } & Pick<Setup, "deps">, uid: string, postId: string, action = "attend") =>
  call(s as Setup, uid, { action, groupId: s.groupId, postId });

T("P5 create EVENT không hợp lệ → INVALID_ARGUMENT (thiếu/quá dài/quá khứ/expectedCount ngoài 2–100)", async () => {
  const s = await setup("p5a");
  const cases: Record<string, unknown>[] = [
    { eventTitle: undefined },
    { eventTitle: "   " },
    { eventTitle: "t".repeat(101) },
    { eventLocation: undefined },
    { eventLocation: "l".repeat(201) },
    { eventAt: undefined },
    { eventAt: "không-phải-ngày" },
    { eventAt: inFuture(-60_000) },
    { expectedCount: 1 },
    { expectedCount: 101 },
    { expectedCount: 2.5 },
    { expectedCount: "8" },
    { expectedCount: undefined },
    { content: "c".repeat(2001) },
  ];
  for (const extra of cases) {
    const r = await call(s, s.member, eventBody(s, crypto.randomUUID(), extra));
    assertEquals([r.status, r.body.error?.code], [400, "INVALID_ARGUMENT"], JSON.stringify(extra));
  }
  assertEquals(await postCount(s), 0);

  // Biên hợp lệ: tiêu đề 100, địa điểm 200, expectedCount 2 và 100, mô tả rỗng hoặc vắng.
  for (
    const extra of [
      { eventTitle: "t".repeat(100), eventLocation: "l".repeat(200), expectedCount: 2, content: "" },
      { expectedCount: 100, content: undefined },
    ]
  ) {
    const r = await call(s, s.member, eventBody(s, crypto.randomUUID(), extra));
    assertEquals(r.status, 201, JSON.stringify(extra));
  }
  assertEquals(await postCount(s), 2);
  await assertGroupInvariant(s.groupId);
});

T("P5 create EVENT hợp lệ → 201, attendeeCount 0, EVENT_CREATED gửi mọi thành viên trừ người đăng", async () => {
  const s = await setup("p5b");
  const postId = crypto.randomUUID();
  const eventAt = inFuture(2 * DAY);
  const r = await call(s, s.admin, eventBody(s, postId, { eventTitle: "  Giải nội bộ  ", eventAt }));
  assertEquals(r.status, 201);
  const p = r.body.post;
  assertEquals([p.type, p.eventTitle, p.eventLocation, p.expectedCount], ["EVENT", "Giải nội bộ", "Sân A, Quận 7", 8]);
  assertEquals(p.attendeeCount, 0);
  assertEquals(p.eventAt, eventAt);
  assertEquals(p.status, "VISIBLE");
  const doc = await get(postPath(s.groupId, postId));
  assert(doc?.eventAt instanceof Date);
  assertEquals(doc?.attendeeCount, 0);
  await s.notifications.capture(s.groupId);
assertEquals(s.notifications.recipients("EVENT_CREATED").sort(), [s.owner, s.member, s.member2].sort());
  await s.notifications.capture(s.groupId);
assert(s.notifications.sent.every((n) => n.data.groupId === s.groupId && n.data.postId === postId));

  // Gửi lại cùng requestId: trả bài cũ, không gửi thêm thông báo.
  await s.notifications.capture(s.groupId);
s.notifications.reset();
  assertEquals((await call(s, s.admin, eventBody(s, postId))).status, 200);
  await s.notifications.capture(s.groupId);
assertEquals(s.notifications.sent.length, 0);
  await assertGroupInvariant(s.groupId);
});

T("P5 create POST thường không gửi EVENT_CREATED và bỏ qua trường sự kiện", async () => {
  const s = await setup("p5c");
  const r = await call(s, s.member, createBody(s, crypto.randomUUID(), { eventTitle: "x", expectedCount: 5 }));
  assertEquals(r.status, 201);
  assertEquals(r.body.post.eventTitle, null);
  await s.notifications.capture(s.groupId);
assertEquals(s.notifications.sent.length, 0);
});

T("P6 20 thành viên attend song song, lặp 10 lần → attendeeCount = 20, đúng 20 attendees, không trùng", async () => {
  const members = Array.from({ length: 20 }, (_, i) => `${RUN}-p6-m${i}`);
  const owner = `${RUN}-p6-owner`;
  const groupId = await seedGroup({ owner, members });
  const s = { groupId, ...makeTestDeps() };
  const RUNS = 10;
  let expected = 0, actual = 0, duplicates = 0;
  for (let run = 0; run < RUNS; run++) {
    const postId = await seedPost(groupId, { type: "EVENT", authorId: owner, expectedCount: 5 });
    const rs = await Promise.all(members.map((m) => attend(s, m, postId)));
    assertEquals(rs.map((r) => r.status), members.map(() => 200), JSON.stringify(rs.find((r) => r.status !== 200)));
    const post = await get(postPath(groupId, postId));
    const docs = await attendeeDocs(s, postId);
    expected += members.length;
    actual += docs.length;
    duplicates += docs.length - new Set(docs.map((d) => d.id)).size;
    assertEquals(post?.attendeeCount, 20, `lần ${run + 1}: attendeeCount`);
    assertEquals(docs.length, 20, `lần ${run + 1}: số attendees`);
    await assertGroupInvariant(groupId);
  }
  console.log(`runs=${RUNS} expected=${expected / RUNS} actual=${actual / RUNS} duplicates=${duplicates}`);
  assertEquals([expected, actual, duplicates], [RUNS * 20, RUNS * 20, 0]);
});

T("P6 một người bấm attend 5 lần (tuần tự và song song) → tính 1; unattend 2 lần → giảm 1", async () => {
  const s = await setup("p6b");
  const postId = await seedPost(s.groupId, { type: "EVENT", authorId: s.owner });
  for (let i = 0; i < 5; i++) {
    const r = await attend(s, s.member, postId);
    assertEquals([r.status, r.body], [200, { attendeeCount: 1, attending: true }]);
  }
  const par = await Promise.all([1, 2, 3, 4, 5].map(() => attend(s, s.member, postId)));
  assert(par.every((r) => r.status === 200 && r.body.attendeeCount === 1 && r.body.attending === true));
  const other = await attend(s, s.member2, postId);
  assertEquals(other.body, { attendeeCount: 2, attending: true });
  const att = await get(attendeePath(s.groupId, postId, s.member));
  assertEquals([att?.userId, att?.groupId, att?.postId], [s.member, s.groupId, postId]);
  assert(att?.eventAt instanceof Date && att?.joinedAt instanceof Date);
  assertEquals((att?.userSnapshot as { displayName: string }).displayName, `Người dùng ${s.member}`);
  assertEquals((await attendeeDocs(s, postId)).length, 2);

  for (let i = 0; i < 2; i++) {
    const r = await attend(s, s.member, postId, "unattend");
    assertEquals([r.status, r.body], [200, { attendeeCount: 1, attending: false }]);
  }
  assertEquals(await get(attendeePath(s.groupId, postId, s.member)), null);
  // Chưa từng tham gia mà unattend: trả kết quả hiện tại.
  const never = await attend(s, s.owner, postId, "unattend");
  assertEquals(never.body, { attendeeCount: 1, attending: false });
  await assertGroupInvariant(s.groupId);
});

T("P7 attend khi attendeeCount == expectedCount → thành công, vượt số dự kiến", async () => {
  const s = await setup("p7a");
  const postId = await seedPost(s.groupId, {
    type: "EVENT",
    authorId: s.owner,
    expectedCount: 2,
    attendees: [s.admin, s.member],
  });
  const r = await attend(s, s.member2, postId);
  assertEquals([r.status, r.body], [200, { attendeeCount: 3, attending: true }]);
  assertEquals((await get(postPath(s.groupId, postId)))?.attendeeCount, 3);
  await assertGroupInvariant(s.groupId);
});

T("P7 attend/unattend sự kiện đã qua → 409 EVENT_PAST, không đổi dữ liệu", async () => {
  const s = await setup("p7b");
  const postId = await seedPost(s.groupId, {
    type: "EVENT",
    authorId: s.owner,
    eventAt: new Date(Date.now() - 3600_000),
    attendees: [s.member],
  });
  for (const [uid, action] of [[s.member2, "attend"], [s.member, "unattend"]]) {
    const r = await attend(s, uid, postId, action);
    assertEquals([r.status, r.body.error.code], [409, "EVENT_PAST"], action);
  }
  assertEquals((await get(postPath(s.groupId, postId)))?.attendeeCount, 1);
  assertEquals((await attendeeDocs(s, postId)).length, 1);
  await assertGroupInvariant(s.groupId);
});

T("P7 attend: không phải thành viên → NOT_MEMBER; bài không có/POST/HIDDEN → 404; LOCKED → 423", async () => {
  const s = await setup("p7c");
  const ev = await seedPost(s.groupId, { type: "EVENT", authorId: s.owner });
  let r = await attend(s, s.outsider, ev);
  assertEquals([r.status, r.body.error.code], [403, "NOT_MEMBER"]);
  assertEquals((await attendeeDocs(s, ev)).length, 0);

  const plain = await seedPost(s.groupId, { authorId: s.owner });
  const hidden = await seedPost(s.groupId, { type: "EVENT", authorId: s.owner, hiddenBy: "GROUP" });
  for (const postId of [crypto.randomUUID(), plain, hidden]) {
    for (const action of ["attend", "unattend"]) {
      r = await attend(s, s.member, postId, action);
      assertEquals([r.status, r.body.error.code], [404, "POST_NOT_FOUND"], `${action} ${postId}`);
    }
  }
  r = await call(s, s.member, { action: "attend", groupId: s.groupId, postId: "xyz" });
  assertEquals([r.status, r.body.error.code], [400, "INVALID_ARGUMENT"]);

  const locked = await seedGroup({ owner: s.owner, members: [s.member], status: "LOCKED" });
  const lockedEv = await seedPost(locked, { type: "EVENT", authorId: s.owner });
  r = await call(s, s.member, { action: "attend", groupId: locked, postId: lockedEv });
  assertEquals([r.status, r.body.error.code], [423, "GROUP_LOCKED"]);
  await assertGroupInvariant(s.groupId);
});

T(
  "P8 update đổi eventAt → editedAt, mọi attendees.eventAt đổi, EVENT_UPDATED cho người tham gia trừ người sửa",
  async () => {
    const s = await setup("p8a");
    const postId = await seedPost(s.groupId, {
      type: "EVENT",
      authorId: s.member,
      attendees: [s.member, s.member2, s.admin],
    });
    const eventAt = inFuture(3 * DAY);
    const r = await call(s, s.member, { action: "update", groupId: s.groupId, postId, eventAt });
    assertEquals(r.status, 200);
    assertEquals(r.body.post.eventAt, eventAt);
    assertEquals(r.body.post.attendeeCount, 3);
    assert(r.body.post.editedAt && !Number.isNaN(Date.parse(r.body.post.editedAt)), "editedAt");
    const docs = await attendeeDocs(s, postId);
    assertEquals(docs.length, 3);
    for (const d of docs) assertEquals((d.data.eventAt as Date).toISOString(), eventAt);
    await s.notifications.capture(s.groupId);
assertEquals(s.notifications.recipients("EVENT_UPDATED").sort(), [s.member2, s.admin].sort());

    // Đổi địa điểm cũng gửi; tiêu đề và expectedCount cập nhật được.
    await s.notifications.capture(s.groupId);
s.notifications.reset();
    const r2 = await call(s, s.member, {
      action: "update",
      groupId: s.groupId,
      postId,
      eventLocation: "Sân B",
      eventTitle: "Tên mới",
      expectedCount: 12,
    });
    assertEquals([r2.status, r2.body.post.eventLocation, r2.body.post.eventTitle, r2.body.post.expectedCount], [
      200,
      "Sân B",
      "Tên mới",
      12,
    ]);
    await s.notifications.capture(s.groupId);
assertEquals(s.notifications.recipients("EVENT_UPDATED").sort(), [s.member2, s.admin].sort());
    await assertGroupInvariant(s.groupId);
  },
);

T("P8 update chỉ đổi mô tả → không gửi; eventAt quá khứ/sai → INVALID_ARGUMENT; người khác → FORBIDDEN", async () => {
  const s = await setup("p8b");
  const postId = await seedPost(s.groupId, { type: "EVENT", authorId: s.member, attendees: [s.member2] });
  const r = await call(s, s.member, { action: "update", groupId: s.groupId, postId, content: "Mô tả mới" });
  assertEquals([r.status, r.body.post.content], [200, "Mô tả mới"]);
  await s.notifications.capture(s.groupId);
assertEquals(s.notifications.sent.length, 0);
  // Mô tả rỗng được phép với sự kiện.
  assertEquals((await call(s, s.member, { action: "update", groupId: s.groupId, postId, content: "" })).status, 200);

  for (
    const extra of [
      { eventAt: inFuture(-60_000) },
      { eventAt: "x" },
      { eventTitle: "" },
      { eventLocation: "l".repeat(201) },
      { expectedCount: 1 },
    ]
  ) {
    const e = await call(s, s.member, { action: "update", groupId: s.groupId, postId, ...extra });
    assertEquals([e.status, e.body.error?.code], [400, "INVALID_ARGUMENT"], JSON.stringify(extra));
  }
  const f = await call(s, s.owner, { action: "update", groupId: s.groupId, postId, content: "x" });
  assertEquals([f.status, f.body.error.code], [403, "FORBIDDEN"]);
  await s.notifications.capture(s.groupId);
assertEquals(s.notifications.sent.length, 0);
  await assertGroupInvariant(s.groupId);
});

T("P8 delete sự kiện chưa diễn ra → xóa attendees, EVENT_DELETED cho người tham gia; đã qua → không gửi", async () => {
  const s = await setup("p8c");
  const upcoming = await seedPost(s.groupId, {
    type: "EVENT",
    authorId: s.member,
    attendees: [s.member, s.member2, s.admin],
  });
  let r = await call(s, s.member, { action: "delete", groupId: s.groupId, postId: upcoming });
  assertEquals(r.status, 200);
  assertEquals(await get(postPath(s.groupId, upcoming)), null);
  assertEquals((await attendeeDocs(s, upcoming)).length, 0);
  await s.notifications.capture(s.groupId);
assertEquals(s.notifications.recipients("EVENT_DELETED").sort(), [s.member2, s.admin].sort());

  await s.notifications.capture(s.groupId);
s.notifications.reset();
  const past = await seedPost(s.groupId, {
    type: "EVENT",
    authorId: s.member,
    eventAt: new Date(Date.now() - 3600_000),
    attendees: [s.member2],
  });
  r = await call(s, s.member, { action: "delete", groupId: s.groupId, postId: past });
  assertEquals(r.status, 200);
  assertEquals((await attendeeDocs(s, past)).length, 0);
  await s.notifications.capture(s.groupId);
assertEquals(s.notifications.sent.length, 0);
  await assertGroupInvariant(s.groupId);
});

// ---------- P9: hide / unhide ----------

const postDoc = async (s: Setup, postId: string) => (await get(postPath(s.groupId, postId)))!;

T("P9 admin hide → HIDDEN/GROUP + hiddenByUid; hide lần 2 → 200 giữ nguyên; unhide → VISIBLE, xóa dấu", async () => {
  const s = await setup("p9a");
  const postId = await seedPost(s.groupId, { authorId: s.member });
  let r = await call(s, s.admin, { action: "hide", groupId: s.groupId, postId });
  assertEquals(r.status, 200);
  let doc = await postDoc(s, postId);
  assertEquals([doc.status, doc.hiddenBy, doc.hiddenByUid], ["HIDDEN", "GROUP", s.admin]);
  assert(doc.updatedAt instanceof Date && doc.updatedAt.getTime() > Date.now() - 60_000, "updatedAt");

  // Đã ẩn: trả hiện trạng, không ghi đè người ẩn.
  r = await call(s, s.owner, { action: "hide", groupId: s.groupId, postId });
  assertEquals(r.status, 200);
  assertEquals((await postDoc(s, postId)).hiddenByUid, s.admin);

  r = await call(s, s.owner, { action: "unhide", groupId: s.groupId, postId });
  assertEquals(r.status, 200);
  doc = await postDoc(s, postId);
  assertEquals([doc.status, doc.hiddenBy, doc.hiddenByUid], ["VISIBLE", null, null]);
  await assertGroupInvariant(s.groupId);
});

T("P9 unhide bài SYSTEM → 403 HIDDEN_BY_SYSTEM; thành viên thường / ngoài nhóm → FORBIDDEN / NOT_MEMBER", async () => {
  const s = await setup("p9b");
  const sys = await seedPost(s.groupId, { authorId: s.member, hiddenBy: "SYSTEM" });
  let r = await call(s, s.admin, { action: "unhide", groupId: s.groupId, postId: sys });
  assertEquals([r.status, r.body.error.code], [403, "HIDDEN_BY_SYSTEM"]);
  assertEquals((await postDoc(s, sys)).status, "HIDDEN");

  const visible = await seedPost(s.groupId, { authorId: s.member });
  for (const action of ["hide", "unhide"]) {
    r = await call(s, s.member, { action, groupId: s.groupId, postId: visible });
    assertEquals([r.status, r.body.error.code], [403, "FORBIDDEN"], action);
    r = await call(s, s.outsider, { action, groupId: s.groupId, postId: visible });
    assertEquals([r.status, r.body.error.code], [403, "NOT_MEMBER"], action);
  }
  assertEquals((await postDoc(s, visible)).status, "VISIBLE");
  r = await call(s, s.admin, { action: "hide", groupId: s.groupId, postId: crypto.randomUUID() });
  assertEquals([r.status, r.body.error.code], [404, "POST_NOT_FOUND"]);
});

// ---------- P10: report ----------

T("P10 report lần 1 → 201 reportId tất định + document đủ trường; lần 2 → 409 ALREADY_REPORTED", async () => {
  const s = await setup("p10a");
  const postId = await seedPost(s.groupId, { authorId: s.member });
  const body = { action: "report", groupId: s.groupId, postId, reason: "SPAM" };
  const reportId = `POST_${s.groupId}_${postId}_${s.member2}`;
  const r = await call(s, s.member2, body);
  assertEquals([r.status, r.body.reportId], [201, reportId]);
  const doc = await get(`reports/${reportId}`);
  assertEquals(doc?.reporterId, s.member2);
  assertEquals(doc?.targetType, "POST");
  assertEquals(doc?.targetPath, `groups/${s.groupId}/posts/${postId}`);
  assertEquals(doc?.reason, "SPAM");
  assertEquals(doc?.status, "OPEN");
  assertEquals(doc?.action, "NONE");
  assert(doc?.createdAt instanceof Date, "createdAt");

  const again = await call(s, s.member2, { ...body, reason: "SCAM" });
  assertEquals([again.status, again.body.error.code], [409, "ALREADY_REPORTED"]);
  assertEquals((await get(`reports/${reportId}`))?.reason, "SPAM");
  // Người khác vẫn báo cáo được cùng bài.
  assertEquals((await call(s, s.admin, body)).status, 201);
});

T("P10 report: bài của mình → OWN_POST; ngoài nhóm → NOT_MEMBER; bài ẩn/không có → POST_NOT_FOUND", async () => {
  const s = await setup("p10b");
  const postId = await seedPost(s.groupId, { authorId: s.member });
  const body = { action: "report", groupId: s.groupId, postId, reason: "OFFENSIVE" };
  let r = await call(s, s.member, body);
  assertEquals([r.status, r.body.error.code], [403, "OWN_POST"]);
  r = await call(s, s.outsider, body);
  assertEquals([r.status, r.body.error.code], [403, "NOT_MEMBER"]);
  const hidden = await seedPost(s.groupId, { authorId: s.member, hiddenBy: "GROUP" });
  r = await call(s, s.member2, { ...body, postId: hidden });
  assertEquals([r.status, r.body.error.code], [404, "POST_NOT_FOUND"]);
  r = await call(s, s.member2, { ...body, postId: crypto.randomUUID() });
  assertEquals([r.status, r.body.error.code], [404, "POST_NOT_FOUND"]);
  assertEquals(await get(`reports/POST_${s.groupId}_${postId}_${s.member}`), null);
});

T("P10 report: reason lạ, OTHER thiếu detail, detail 501 ký tự → 400; OTHER có detail 500 → 201", async () => {
  const s = await setup("p10c");
  const postId = await seedPost(s.groupId, { authorId: s.member });
  const body = { action: "report", groupId: s.groupId, postId };
  for (
    const extra of [
      { reason: "KHAC" },
      { reason: undefined },
      { reason: "OTHER" },
      { reason: "OTHER", detail: "   " },
      { reason: "OTHER", detail: "d".repeat(501) },
      { reason: "SPAM", detail: "d".repeat(501) },
      { reason: "SPAM", detail: 5 },
    ]
  ) {
    const r = await call(s, s.member2, { ...body, ...extra });
    assertEquals([r.status, r.body.error?.code], [400, "INVALID_ARGUMENT"], JSON.stringify(extra));
  }
  assertEquals(await get(`reports/POST_${s.groupId}_${postId}_${s.member2}`), null);
  const ok = await call(s, s.member2, { ...body, reason: "OTHER", detail: "d".repeat(500) });
  assertEquals(ok.status, 201);
  assertEquals((await get(`reports/${ok.body.reportId}`))?.detail, "d".repeat(500));
});

// ---------- P11: nhóm LOCKED ----------

T("P11 nhóm LOCKED: mọi action ghi của group-post và join → 423 GROUP_LOCKED; leave vẫn thành công", async () => {
  const owner = `${RUN}-p11-owner`;
  const admin = `${RUN}-p11-admin`;
  const member = `${RUN}-p11-member`;
  const outsider = `${RUN}-p11-outsider`;
  const groupId = await seedGroup({ owner, admins: [admin], members: [member], status: "LOCKED" });
  await ensureUsers([outsider]);
  const s = { owner, admin, member, outsider, groupId, ...makeTestDeps() } as unknown as Setup;
  const postId = await seedPost(groupId, { authorId: member });
  const event = await seedPost(groupId, { type: "EVENT", authorId: member });

  const locked = async (uid: string, body: Record<string, unknown>) => {
    const r = await call(s, uid, { groupId, ...body });
    assertEquals([r.status, r.body.error?.code], [423, "GROUP_LOCKED"], String(body.action));
  };
  await locked(member, createBody(s, crypto.randomUUID()));
  await locked(member, { action: "update", postId, content: "x" });
  await locked(member, { action: "delete", postId });
  await locked(admin, { action: "hide", postId });
  await locked(admin, { action: "unhide", postId });
  await locked(owner, { action: "report", postId, reason: "SPAM" });
  await locked(owner, { action: "attend", postId: event });
  await locked(owner, { action: "unattend", postId: event });
  assertEquals((await postDoc(s, postId)).status, "VISIBLE");

  const manage = async (uid: string, body: Record<string, unknown>) => {
    const r = await handleGroupManage(bearer(tokenOf(uid), { body: JSON.stringify({ requestId: crypto.randomUUID(), ...body }) }), s.deps);
    return { status: r.status, body: await r.json() };
  };
  const join = await manage(outsider, { action: "join", groupId });
  assertEquals([join.status, join.body.error.code], [423, "GROUP_LOCKED"]);

  const leave = await manage(member, { action: "leave", groupId });
  assertEquals([leave.status, leave.body.ok], [200, true]);
  assertEquals(await get(`groups/${groupId}/members/${member}`), null);
  await assertGroupInvariant(groupId);
});
