// Test khung `group-manage` trên Firebase Emulator (Auth 9099, Firestore 8080): 401, 400, 501, 403 ACCOUNT_LOCKED.
// Tự bỏ qua khi thiếu biến môi trường Emulator. Chạy:
//   cd supabase/functions
//   FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 \
//   FIREBASE_PROJECT_ID=demo-smashnow deno test --allow-env --allow-net group-manage/index.test.ts
// Không xóa dữ liệu Emulator (có thể đang dùng chung): uid có tiền tố ngẫu nhiên.
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { commit, createWrite, get, query } from "../_shared/firestore.ts";
import { groupPath, joinRequestPath, memberPath, removalPath } from "../_shared/groups.ts";
import { assertGroupInvariant, ensureUsers, makeTestDeps, seedGroup } from "../_shared/groups_fixtures.ts";
import { nameKeywords } from "../_shared/keywords.ts";
import { bearer, emulatorToken } from "../_shared/testing.ts";
import { ACTIONS, handleGroupManage } from "./handler.ts";

Deno.env.set("FIREBASE_PROJECT_ID", Deno.env.get("FIREBASE_PROJECT_ID") ?? "demo-smashnow");

const ENABLED = !!Deno.env.get("FIRESTORE_EMULATOR_HOST") && !!Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST");
const RUN = `gm${crypto.randomUUID().slice(0, 8)}`;
const T = (name: string, fn: () => Promise<void>) => Deno.test({ name, ignore: !ENABLED, fn });

const tokenOf = (uid: string) => emulatorToken({ sub: uid, user_id: uid, appRole: "PLAYER" });
const post = (token: string | null, body: unknown) =>
  token === null
    ? new Request("http://localhost/group-manage", { method: "POST", body: JSON.stringify(body) })
    : bearer(token, { body: JSON.stringify(body) });

async function call(res: Promise<Response>) {
  const r = await res;
  return { status: r.status, body: await r.json() };
}

T("thiếu token → 401 UNAUTHENTICATED", async () => {
  const { deps } = makeTestDeps();
  const r = await call(handleGroupManage(post(null, { action: "join" }), deps));
  assertEquals(r.status, 401);
  assertEquals(r.body.error.code, "UNAUTHENTICATED");
});

T("action lạ hoặc thiếu → 400 INVALID_ARGUMENT", async () => {
  const uid = `${RUN}-p1`;
  await commit([createWrite(`users/${uid}`, { displayName: "P1", accountStatus: "ACTIVE" })]);
  const { deps } = makeTestDeps();
  for (const body of [{ action: "fly" }, {}, { action: 5 }]) {
    const r = await call(handleGroupManage(post(tokenOf(uid), body), deps));
    assertEquals(r.status, 400);
    assertEquals(r.body.error.code, "INVALID_ARGUMENT");
  }
});

T("action chưa cài (delete, setRole, transferOwner, remove) → 501 NOT_IMPLEMENTED", async () => {
  const uid = `${RUN}-p2`;
  await commit([createWrite(`users/${uid}`, { displayName: "P2", accountStatus: "ACTIVE" })]);
  const { deps } = makeTestDeps();
  for (const action of ACTIONS.filter((a) => ["delete", "setRole", "transferOwner", "remove"].includes(a))) {
    const r = await call(handleGroupManage(post(tokenOf(uid), { action }), deps));
    assertEquals([action, r.status, r.body.error.code], [action, 501, "NOT_IMPLEMENTED"]);
  }
});

T("tài khoản LOCKED → 403 ACCOUNT_LOCKED", async () => {
  const uid = `${RUN}-locked`;
  await commit([createWrite(`users/${uid}`, { displayName: "L", accountStatus: "LOCKED" })]);
  const { deps } = makeTestDeps();
  const r = await call(handleGroupManage(post(tokenOf(uid), { action: "join" }), deps));
  assertEquals(r.status, 403);
  assertEquals(r.body.error.code, "ACCOUNT_LOCKED");
});

T("GET → 405", async () => {
  const { deps } = makeTestDeps();
  const r = await handleGroupManage(new Request("http://localhost/group-manage", { method: "GET" }), deps);
  assertEquals(r.status, 405);
  await r.body?.cancel();
});

// ---------- Công cụ cho T021, T030 ----------

type Deps = ReturnType<typeof makeTestDeps>;

/** Hai khu vực dùng trong test (không ghi đè nếu đã có). */
async function ensureDistricts() {
  for (const id of ["d1", "d2"]) {
    if (!await get(`districts/${id}`)) await commit([createWrite(`districts/${id}`, { name: id, isActive: true })]);
  }
}

let seq = 0;
/** Tạo người dùng ACTIVE với uid riêng của lần chạy. */
async function newUser(tag: string): Promise<string> {
  const uid = `${RUN}-${tag}-${seq++}`;
  await ensureUsers([uid]);
  return uid;
}

const act = (t: Deps, uid: string, body: Record<string, unknown>) =>
  call(handleGroupManage(post(tokenOf(uid), body), t.deps));

const createBody = (over: Record<string, unknown> = {}) => ({
  action: "create",
  requestId: crypto.randomUUID(),
  name: "CLB Cầu Lông Q7",
  description: "Chơi cuối tuần",
  districtId: "d1",
  visibility: "PUBLIC",
  hasAvatar: false,
  ...over,
});

// ---------- T021: G1–G4, ảnh đại diện, update ----------

T(
  "G1: create hợp lệ → OWNER, memberCount 1, nameKeywords đúng, hook created gọi 1 lần; trùng tên vẫn được",
  async () => {
    await ensureDistricts();
    const owner = await newUser("g1");
    const t = makeTestDeps();
    const body = createBody({ name: "  CLB Cầu Lông Q7 " });
    const r = await act(t, owner, body);
    assertEquals(r.status, 201);
    const id = body.requestId as string;
    assertEquals(r.body.group.id, id);
    assertEquals(r.body.group.name, "CLB Cầu Lông Q7");
    assertEquals(r.body.group.ownerId, owner);
    assertEquals(r.body.avatarUpload, undefined);

    const group = await get(groupPath(id));
    assertEquals(group?.memberCount, 1);
    assertEquals(group?.pendingRequestCount, 0);
    assertEquals(group?.status, "ACTIVE");
    assertEquals(group?.avatarPath, null);
    assertEquals(group?.nameKeywords, nameKeywords("CLB Cầu Lông Q7"));
    const member = await get(memberPath(id, owner));
    assertEquals(member?.role, "OWNER");
    assertEquals(member?.userId, owner);
    assertEquals(t.hooks.calls.length, 1);
    assertEquals(t.hooks.calls[0].groupId, id);
    assertEquals(t.hooks.calls[0].change, { created: true, added: [owner] });
    await assertGroupInvariant(id);

    // Tên trùng với nhóm của người khác vẫn tạo được.
    const other = await newUser("g1b");
    const r2 = await act(t, other, createBody({ name: "CLB Cầu Lông Q7" }));
    assertEquals(r2.status, 201);
    await assertGroupInvariant(r2.body.group.id);
  },
);

T("G2: create cùng requestId 5 lần (tuần tự và song song) → 1 nhóm, các lần sau 200", async () => {
  await ensureDistricts();
  const owner = await newUser("g2");
  const t = makeTestDeps();
  const body = createBody();
  const seqRes = [];
  for (let i = 0; i < 5; i++) seqRes.push(await act(t, owner, body));
  assertEquals(seqRes.map((r) => r.status), [201, 200, 200, 200, 200]);

  const body2 = createBody();
  const par = await Promise.all(Array.from({ length: 5 }, () => act(t, owner, body2)));
  assertEquals(par.map((r) => r.status).sort(), [200, 200, 200, 200, 201]);

  const groups = await query("groups", [{ field: "ownerId", op: "EQUAL", value: owner }]);
  assertEquals(groups.length, 2);
  assertEquals(t.hooks.calls.length, 2, "hook chỉ gọi ở lần tạo thật");
  await assertGroupInvariant(body.requestId);
  await assertGroupInvariant(body2.requestId);

  // requestId của nhóm người khác → 409 (quyết định: REQUEST_ID_CONFLICT).
  const other = await newUser("g2b");
  const r = await act(t, other, body);
  assertEquals(r.status, 409);
  assertEquals(r.body.error.code, "REQUEST_ID_CONFLICT");
});

T("G3: đã làm chủ 5 nhóm ACTIVE/LOCKED → 409 OWNER_LIMIT_REACHED (DELETED không tính)", async () => {
  await ensureDistricts();
  const owner = await newUser("g3");
  const t = makeTestDeps();
  const o = { owner };
  // 4 nhóm được tính (3 ACTIVE + 1 LOCKED) + 1 DELETED không tính → lần tạo `fifth` là nhóm thứ 5.
  const ids = [
    await seedGroup(o),
    await seedGroup(o),
    await seedGroup(o),
    await seedGroup({ ...o, status: "LOCKED" }),
    await seedGroup({ ...o, status: "DELETED" }),
  ];
  const fifth = createBody();
  assertEquals((await act(t, owner, fifth)).status, 201);
  const sixth = createBody();
  const r = await act(t, owner, sixth);
  assertEquals(r.status, 409);
  assertEquals(r.body.error.code, "OWNER_LIMIT_REACHED");
  assertEquals(await get(groupPath(sixth.requestId)), null);
  // Gửi lại nhóm thứ 5 vẫn 200 dù đã đủ giới hạn.
  assertEquals((await act(t, owner, fifth)).status, 200);
  for (const id of [...ids.slice(0, 4), fifth.requestId]) await assertGroupInvariant(id);
});

T("G4: tên 2/51 ký tự, mô tả 501, khu vực không có, visibility lạ → 400 INVALID_ARGUMENT", async () => {
  await ensureDistricts();
  const owner = await newUser("g4");
  const t = makeTestDeps();
  const cases: Record<string, unknown>[] = [
    { name: "ab" },
    { name: "  ab  " },
    { name: "x".repeat(51) },
    { name: undefined },
    { description: "d".repeat(501) },
    { districtId: `${RUN}-khong-co` },
    { districtId: "" },
    { visibility: "SECRET" },
    { hasAvatar: "yes" },
    { avatarContentType: "image/gif" },
    { requestId: "khong-phai-uuid" },
  ];
  for (const over of cases) {
    const body = createBody(over);
    const r = await act(t, owner, body);
    assertEquals([JSON.stringify(over), r.status, r.body.error.code], [JSON.stringify(over), 400, "INVALID_ARGUMENT"]);
    if (typeof body.requestId === "string" && body.requestId.length === 36) {
      assertEquals(await get(groupPath(body.requestId)), null);
    }
  }
  // Biên hợp lệ: 3 và 50 ký tự (sau trim), mô tả 500.
  const okBody = createBody({ name: ` ${"a".repeat(50)} `, description: "d".repeat(500) });
  assertEquals((await act(t, owner, okBody)).status, 201);
  assertEquals((await act(t, owner, createBody({ name: "abc", description: "" }))).status, 201);
  await assertGroupInvariant(okBody.requestId);
});

T(
  "avatar: create hasAvatar → avatarUpload; update CONFIRM thiếu object → PHOTO_MISSING, có → ghi avatarPath; REMOVE xóa",
  async () => {
    await ensureDistricts();
    const owner = await newUser("av");
    const t = makeTestDeps();
    const body = createBody({ hasAvatar: true, avatarContentType: "image/png" });
    const id = body.requestId as string;
    const r = await act(t, owner, body);
    assertEquals(r.status, 201);
    assertEquals(r.body.avatarUpload.path, `${id}/avatar.png`);
    assert(t.storage.signedUploads.includes(`group-avatars/${id}/avatar.png`));
    assertEquals((await get(groupPath(id)))?.avatarPath, null);

    const confirm = { action: "update", groupId: id, avatar: "CONFIRM", avatarContentType: "image/png" };
    const missing = await act(t, owner, confirm);
    assertEquals(missing.status, 400);
    assertEquals(missing.body.error.code, "PHOTO_MISSING");
    assertEquals((await get(groupPath(id)))?.avatarPath, null);

    t.storage.put("group-avatars", `${id}/avatar.png`);
    const done = await act(t, owner, confirm);
    assertEquals(done.status, 200);
    assertEquals(done.body.group.avatarPath, `${id}/avatar.png`);
    assertEquals((await get(groupPath(id)))?.avatarPath, `${id}/avatar.png`);

    // REPLACE chỉ cấp URL mới, chưa đổi avatarPath.
    const rep = await act(t, owner, { action: "update", groupId: id, avatar: "REPLACE" });
    assertEquals(rep.body.avatarUpload.path, `${id}/avatar.jpg`);
    assertEquals((await get(groupPath(id)))?.avatarPath, `${id}/avatar.png`);

    const rm = await act(t, owner, { action: "update", groupId: id, avatar: "REMOVE" });
    assertEquals(rm.status, 200);
    assertEquals((await get(groupPath(id)))?.avatarPath, null);
    assertEquals(t.storage.has("group-avatars", `${id}/avatar.png`), false);
    assert(t.storage.removed.includes(`group-avatars/${id}/avatar.png`));
    await assertGroupInvariant(id);

    // hasAvatar không nêu content type → jpg.
    const r2 = await act(t, owner, createBody({ hasAvatar: true }));
    assertEquals(r2.body.avatarUpload.path, `${r2.body.group.id}/avatar.jpg`);
  },
);

T(
  "update: chủ nhóm đổi tên → nameKeywords đổi; thành viên thường → 403 FORBIDDEN; người ngoài → NOT_MEMBER",
  async () => {
    await ensureDistricts();
    const owner = await newUser("up-o");
    const member = await newUser("up-m");
    const outsider = await newUser("up-x");
    const t = makeTestDeps();
    const id = await seedGroup({ owner, members: [member], name: "Nhóm Cũ" });

    const r = await act(t, owner, {
      action: "update",
      groupId: id,
      name: "Đồng Đội Mới",
      description: "Mô tả mới",
      districtId: "d2",
      visibility: "PRIVATE",
    });
    assertEquals(r.status, 200);
    assertEquals(r.body.autoApproved, 0);
    assertEquals(r.body.group.name, "Đồng Đội Mới");
    const group = await get(groupPath(id));
    assertEquals(group?.nameKeywords, nameKeywords("Đồng Đội Mới"));
    assertEquals(group?.description, "Mô tả mới");
    assertEquals(group?.districtId, "d2");
    assertEquals(group?.visibility, "PRIVATE");

    for (const [who, code] of [[member, "FORBIDDEN"], [outsider, "NOT_MEMBER"]]) {
      const denied = await act(t, who, { action: "update", groupId: id, name: "Tên khác" });
      assertEquals([denied.status, denied.body.error.code], [403, code]);
    }
    assertEquals((await get(groupPath(id)))?.name, "Đồng Đội Mới");
    const missing = await act(t, owner, { action: "update", groupId: crypto.randomUUID(), name: "Tên khác" });
    assertEquals([missing.status, missing.body.error.code], [404, "GROUP_NOT_FOUND"]);
    await assertGroupInvariant(id);
  },
);

// ---------- T030: G5–G9, G13 ----------

T("G5: 20 người join nhóm công khai song song, lặp 10 lần → đủ 20, không trùng", async () => {
  const RUNS = 10;
  const N = 20;
  const users = await Promise.all(Array.from({ length: N }, (_, i) => newUser(`g5-${i}`)));
  let actual = 0;
  let duplicates = 0;
  for (let run = 0; run < RUNS; run++) {
    const owner = await newUser("g5o");
    const id = await seedGroup({ owner, visibility: "PUBLIC" });
    const before = (await get(groupPath(id)))?.memberCount as number;
    const t = makeTestDeps();
    const results = await Promise.all(
      users.map((u) => act(t, u, { action: "join", groupId: id, requestId: crypto.randomUUID() })),
    );
    assertEquals(results.map((r) => [r.status, r.body.result]), users.map(() => [200, "JOINED"]), `lần ${run + 1}`);

    const members = (await query("members", [], { parent: groupPath(id) })).filter((m) => m.data.role === "MEMBER");
    duplicates += members.length - new Set(members.map((m) => m.id)).size;
    actual = members.length;
    assertEquals(actual, N, `lần ${run + 1}: số members mới`);
    assertEquals((await get(groupPath(id)))?.memberCount, before + N, `lần ${run + 1}: memberCount`);
    assertEquals(t.hooks.added(id).sort(), [...users].sort(), `lần ${run + 1}: hook added`);
    await assertGroupInvariant(id);
  }
  console.log(`runs=${RUNS} expected=${N} actual=${actual} duplicates=${duplicates}`);
  assertEquals(duplicates, 0);
});

T("G6: join nhóm riêng tư → approve; join → reject; cancelRequest", async () => {
  const owner = await newUser("g6o");
  const admin = await newUser("g6a");
  const [a, b, c] = [await newUser("g6-a"), await newUser("g6-b"), await newUser("g6-c")];
  const t = makeTestDeps();
  const id = await seedGroup({ owner, admins: [admin], visibility: "PRIVATE" });

  const j = await act(t, a, { action: "join", groupId: id });
  assertEquals([j.status, j.body.result], [200, "REQUESTED"]);
  assertEquals((await get(groupPath(id)))?.pendingRequestCount, 1);
  const req = await get(joinRequestPath(id, a));
  assertEquals(req?.wasRemoved, false);
  assertEquals(req?.userId, a);
  assertEquals(t.notifications.recipients("JOIN_REQUESTED").sort(), [owner, admin].sort());
  assertEquals(t.hooks.calls.length, 0, "REQUESTED không đổi danh sách thành viên");
  await assertGroupInvariant(id);

  const ap = await act(t, owner, { action: "approve", groupId: id, userId: a });
  assertEquals(ap.status, 200);
  assertEquals(await get(joinRequestPath(id, a)), null);
  assertEquals((await get(memberPath(id, a)))?.role, "MEMBER");
  const g1 = await get(groupPath(id));
  assertEquals([g1?.memberCount, g1?.pendingRequestCount], [3, 0]);
  assertEquals(t.notifications.recipients("JOIN_APPROVED"), [a]);
  assertEquals(t.hooks.added(id), [a]);
  await assertGroupInvariant(id);

  t.notifications.reset();
  await act(t, b, { action: "join", groupId: id });
  const rj = await act(t, admin, { action: "reject", groupId: id, userId: b });
  assertEquals(rj.status, 200);
  assertEquals(await get(joinRequestPath(id, b)), null);
  assertEquals(await get(memberPath(id, b)), null);
  assertEquals(t.notifications.recipients("JOIN_REJECTED"), [b]);
  await assertGroupInvariant(id);

  await act(t, c, { action: "join", groupId: id });
  t.notifications.reset();
  const cn = await act(t, c, { action: "cancelRequest", groupId: id });
  assertEquals(cn.status, 200);
  assertEquals(await get(joinRequestPath(id, c)), null);
  assertEquals(t.notifications.sent.length, 0);
  assertEquals((await get(groupPath(id)))?.pendingRequestCount, 0);
  const again = await act(t, c, { action: "cancelRequest", groupId: id });
  assertEquals([again.status, again.body.error.code], [404, "REQUEST_NOT_FOUND"]);
  await assertGroupInvariant(id);
});

T("G7: approve cùng một người 2 lần song song → memberCount tăng đúng 1", async () => {
  const owner = await newUser("g7o");
  const a = await newUser("g7a");
  const t = makeTestDeps();
  const id = await seedGroup({ owner, pending: [a], visibility: "PRIVATE" });
  const rs = await Promise.all([
    act(t, owner, { action: "approve", groupId: id, userId: a }),
    act(t, owner, { action: "approve", groupId: id, userId: a }),
  ]);
  assertEquals(rs.map((r) => r.status).sort(), [200, 404]);
  assertEquals(rs.find((r) => r.status === 404)?.body.error.code, "REQUEST_NOT_FOUND");
  assertEquals((await get(groupPath(id)))?.memberCount, 2);
  assertEquals(t.hooks.added(id), [a]);
  await assertGroupInvariant(id);
});

T("G8: người có removals join nhóm công khai → REQUESTED, wasRemoved = true; approve xóa removals", async () => {
  const owner = await newUser("g8o");
  const u = await newUser("g8u");
  const t = makeTestDeps();
  const id = await seedGroup({ owner, visibility: "PUBLIC", removed: [u] });
  const j = await act(t, u, { action: "join", groupId: id });
  assertEquals([j.status, j.body.result], [200, "REQUESTED"]);
  assertEquals((await get(joinRequestPath(id, u)))?.wasRemoved, true);
  assertEquals(await get(memberPath(id, u)), null);
  assertEquals(t.notifications.recipients("JOIN_REQUESTED"), [owner]);
  await assertGroupInvariant(id);

  await act(t, owner, { action: "approve", groupId: id, userId: u });
  assertEquals(await get(removalPath(id, u)), null);
  assertEquals((await get(memberPath(id, u)))?.role, "MEMBER");
  await assertGroupInvariant(id);
});

T(
  "G9: chủ nhóm leave → OWNER_MUST_TRANSFER (soleMember false/true); thành viên leave → memberCount -1, hook removed",
  async () => {
    const owner = await newUser("g9o");
    const m = await newUser("g9m");
    const t = makeTestDeps();
    const id = await seedGroup({ owner, members: [m] });

    const r = await act(t, owner, { action: "leave", groupId: id });
    assertEquals([r.status, r.body.error.code, r.body.error.soleMember], [409, "OWNER_MUST_TRANSFER", false]);

    const lv = await act(t, m, { action: "leave", groupId: id });
    assertEquals(lv.status, 200);
    assertEquals(await get(memberPath(id, m)), null);
    assertEquals((await get(groupPath(id)))?.memberCount, 1);
    assertEquals(t.hooks.calls.at(-1)?.change, { removed: [m] });
    await assertGroupInvariant(id);

    const solo = await act(t, owner, { action: "leave", groupId: id });
    assertEquals([solo.status, solo.body.error.code, solo.body.error.soleMember], [409, "OWNER_MUST_TRANSFER", true]);

    const notMember = await act(t, m, { action: "leave", groupId: id });
    assertEquals([notMember.status, notMember.body.error.code], [403, "NOT_MEMBER"]);

    // Nhóm LOCKED: leave vẫn được, join thì 423.
    const m2 = await newUser("g9m2");
    const locked = await seedGroup({ owner, members: [m2], status: "LOCKED" });
    assertEquals((await act(t, m2, { action: "leave", groupId: locked })).status, 200);
    const joinLocked = await act(t, m2, { action: "join", groupId: locked });
    assertEquals([joinLocked.status, joinLocked.body.error.code], [423, "GROUP_LOCKED"]);
    await assertGroupInvariant(locked);
  },
);

T("G13: update PRIVATE → PUBLIC với 3 yêu cầu (1 có removals) → 2 người vào nhóm, 1 vẫn chờ", async () => {
  const owner = await newUser("g13o");
  const [a, b, c] = [await newUser("g13a"), await newUser("g13b"), await newUser("g13c")];
  const t = makeTestDeps();
  const id = await seedGroup({ owner, visibility: "PRIVATE", pending: [a, b, c], removed: [c] });

  const r = await act(t, owner, { action: "update", groupId: id, visibility: "PUBLIC" });
  assertEquals(r.status, 200);
  assertEquals(r.body.autoApproved, 2);
  assertEquals(r.body.group.visibility, "PUBLIC");
  assertEquals(r.body.group.memberCount, 3);
  const g = await get(groupPath(id));
  assertEquals([g?.visibility, g?.memberCount, g?.pendingRequestCount], ["PUBLIC", 3, 1]);
  for (const u of [a, b]) {
    assertEquals((await get(memberPath(id, u)))?.role, "MEMBER");
    assertEquals(await get(joinRequestPath(id, u)), null);
  }
  assertEquals(await get(memberPath(id, c)), null);
  assert(await get(joinRequestPath(id, c)));
  assertEquals(t.notifications.recipients("JOIN_APPROVED").sort(), [a, b].sort());
  assertEquals(t.hooks.added(id).sort(), [a, b].sort());
  await assertGroupInvariant(id);

  // PUBLIC → PRIVATE không tự duyệt gì.
  const back = await act(t, owner, { action: "update", groupId: id, visibility: "PRIVATE" });
  assertEquals(back.body.autoApproved, 0);
  await assertGroupInvariant(id);
});

T("join lặp → ALREADY_MEMBER / ALREADY_REQUESTED; approve bởi MEMBER → FORBIDDEN; nhóm không có → 404", async () => {
  const owner = await newUser("jd-o");
  const m = await newUser("jd-m");
  const p = await newUser("jd-p");
  const x = await newUser("jd-x");
  const t = makeTestDeps();
  const id = await seedGroup({ owner, members: [m], pending: [p], visibility: "PRIVATE" });

  const dupMember = await act(t, m, { action: "join", groupId: id });
  assertEquals([dupMember.status, dupMember.body.error.code], [409, "ALREADY_MEMBER"]);
  const dupReq = await act(t, p, { action: "join", groupId: id });
  assertEquals([dupReq.status, dupReq.body.error.code], [409, "ALREADY_REQUESTED"]);

  for (const action of ["approve", "reject"]) {
    const r = await act(t, m, { action, groupId: id, userId: p });
    assertEquals([r.status, r.body.error.code], [403, "FORBIDDEN"]);
    const outsider = await act(t, x, { action, groupId: id, userId: p });
    assertEquals([outsider.status, outsider.body.error.code], [403, "NOT_MEMBER"]);
  }
  assert(await get(joinRequestPath(id, p)), "yêu cầu vẫn còn");

  const noGroup = await act(t, x, { action: "join", groupId: crypto.randomUUID() });
  assertEquals([noGroup.status, noGroup.body.error.code], [404, "GROUP_NOT_FOUND"]);
  assertEquals(t.notifications.sent.length, 0);
  await assertGroupInvariant(id);
});
