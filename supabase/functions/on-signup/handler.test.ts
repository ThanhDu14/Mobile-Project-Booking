// T024: S1–S12 của contracts/edge-function-on-signup.md §6, chạy trên Auth + Firestore Emulator.
import { assert, assertEquals, assertInstanceOf } from "jsr:@std/assert@1.0.19";
import { createLocalJWKSet, exportJWK, generateKeyPair } from "npm:jose@6.2.12";
import { __setJwksForTesting } from "../_shared/auth.ts";
import * as firestore from "../_shared/firestore.ts";
import { commit, createWrite, get } from "../_shared/firestore.ts";
import * as identity from "../_shared/identity.ts";
import { getCustomClaims, setCustomClaims } from "../_shared/identity.ts";
import { bearer, clearEmulators, createUser, emulatorToken, signUpEmail } from "../_shared/testing.ts";
import { handleOnSignup, type OnSignupDeps } from "./handler.ts";

const deps: OnSignupDeps = { firestore, identity };

async function call(token: string, body: unknown, d: OnSignupDeps = deps) {
  const res = await handleOnSignup(bearer(token, { body: JSON.stringify(body) }), d);
  return { status: res.status, body: await res.json() };
}

const ACCEPT = { displayName: "Lê Hoàng Nam", termsAccepted: true };

Deno.test("S1: người dùng email mới → 200 CREATED, document đủ trường, claim đủ", async () => {
  await clearEmulators();
  const { uid, idToken } = await signUpEmail("nam.le@example.com");
  const before = Date.now();

  assertEquals(await call(idToken, ACCEPT), { status: 200, body: { status: "CREATED", appRole: "PLAYER" } });

  const doc = await get(`users/${uid}`);
  assert(doc);
  const { termsAcceptedAt, createdAt, updatedAt, ...rest } = doc;
  assertEquals(rest, {
    displayName: "Lê Hoàng Nam",
    email: "nam.le@example.com",
    phone: null,
    avatarPath: null,
    level: null,
    favoriteDistrictIds: [],
    role: "PLAYER",
    ownerStatus: "NONE",
    accountStatus: "ACTIVE",
    fcmTokens: [],
    notificationPrefs: {},
    walletBalance: 0,
  });
  for (const t of [termsAcceptedAt, createdAt, updatedAt]) {
    assertInstanceOf(t, Date);
    assert(t.getTime() >= before - 5_000 && t.getTime() <= Date.now() + 5_000, "giờ server");
  }
  assertEquals(await getCustomClaims(uid), { role: "authenticated", appRole: "PLAYER" });
});

Deno.test("S2 + gọi liên tiếp: 5 lần → CREATED rồi 4 lần ALREADY_INITIALIZED, createdAt giữ nguyên", async () => {
  await clearEmulators();
  const { uid, idToken } = await signUpEmail("lien.tiep@example.com");
  const statuses: string[] = [];
  let firstCreatedAt: Date | undefined;
  for (let i = 0; i < 5; i++) {
    const r = await call(idToken, ACCEPT);
    assertEquals(r.status, 200);
    statuses.push(r.body.status);
    const createdAt = (await get(`users/${uid}`))!.createdAt as Date;
    firstCreatedAt ??= createdAt;
    assertEquals(createdAt.getTime(), firstCreatedAt.getTime());
  }
  assertEquals(statuses, ["CREATED", ...Array(4).fill("ALREADY_INITIALIZED")]);
});

Deno.test("S3 + gọi đồng thời: 10 lời gọi song song, chạy 10 vòng, mỗi vòng đúng 1 document và claim đúng", async () => {
  for (let round = 0; round < 10; round++) {
    await clearEmulators();
    const { uid, idToken } = await signUpEmail(`song.song${round}@example.com`);
    const results = await Promise.all(Array.from({ length: 10 }, () => call(idToken, ACCEPT)));
    assert(results.every((r) => r.status === 200), JSON.stringify(results));
    assertEquals(results.filter((r) => r.body.status === "CREATED").length, 1, `vòng ${round}`);
    assert(await get(`users/${uid}`));
    assertEquals(await getCustomClaims(uid), { role: "authenticated", appRole: "PLAYER" });
  }
});

Deno.test("S4: document đã có nhưng gán claim lỗi → lần sau bổ sung claim, không cần termsAccepted", async () => {
  await clearEmulators();
  const { uid, idToken } = await signUpEmail("loi.claim@example.com");
  const failing: OnSignupDeps = {
    firestore,
    identity: {
      ...identity,
      setCustomClaims: () => Promise.reject(new Error("Identity Toolkit lỗi")),
    },
  };
  const first = await call(idToken, ACCEPT, failing);
  assertEquals([first.status, first.body.error.code], [500, "INTERNAL"]);
  assert(await get(`users/${uid}`), "document đã tạo trước khi gán claim");
  assertEquals(await getCustomClaims(uid), {});

  assertEquals(await call(idToken, {}), { status: 200, body: { status: "ALREADY_INITIALIZED", appRole: "PLAYER" } });
  assertEquals(await getCustomClaims(uid), { role: "authenticated", appRole: "PLAYER" });
});

Deno.test("S5: người dùng mới, termsAccepted false → 400 TERMS_REQUIRED, không document, không claim", async () => {
  await clearEmulators();
  const { uid, idToken } = await signUpEmail("chua.dong.y@example.com");
  for (const body of [{ ...ACCEPT, termsAccepted: false }, { displayName: "Nam" }, { ...ACCEPT, termsAccepted: "true" }]) {
    const r = await call(idToken, body);
    assertEquals([r.status, r.body.error.code], [400, "TERMS_REQUIRED"]);
  }
  assertEquals(await get(`users/${uid}`), null);
  assertEquals(await getCustomClaims(uid), {});
});

Deno.test("S6: displayName 1 hoặc 51 ký tự → 400 INVALID_DISPLAY_NAME", async () => {
  await clearEmulators();
  const { uid, idToken } = await signUpEmail("ten.sai@example.com");
  for (const displayName of ["A", "x".repeat(51), "   B   "]) {
    const r = await call(idToken, { displayName, termsAccepted: true });
    assertEquals([r.status, r.body.error.code], [400, "INVALID_DISPLAY_NAME"], displayName);
  }
  assertEquals(await get(`users/${uid}`), null);
});

Deno.test("displayName được trim; bỏ trống thì dùng tên trong token", async () => {
  await clearEmulators();
  const a = await signUpEmail("trim@example.com");
  await call(a.idToken, { displayName: "  Lê Nam  ", termsAccepted: true });
  assertEquals((await get(`users/${a.uid}`))!.displayName, "Lê Nam");

  const b = await signUpEmail("google@example.com", "abc12345", "Tên Từ Google");
  assertEquals((await call(b.idToken, { displayName: "  ", termsAccepted: true })).status, 200);
  assertEquals((await get(`users/${b.uid}`))!.displayName, "Tên Từ Google");
});

Deno.test("S7: đã có appRole OWNER hoặc ADMIN → giữ nguyên, chỉ thêm role", async () => {
  for (const appRole of ["OWNER", "ADMIN"]) {
    await clearEmulators();
    const { uid, idToken } = await signUpEmail(`${appRole.toLowerCase()}@example.com`);
    await commit([createWrite(`users/${uid}`, { role: appRole, accountStatus: "ACTIVE" })]);
    await setCustomClaims(uid, { appRole });
    assertEquals(await call(idToken, {}), { status: 200, body: { status: "ALREADY_INITIALIZED", appRole } });
    assertEquals(await getCustomClaims(uid), { appRole, role: "authenticated" });
    assertEquals((await get(`users/${uid}`))!.role, appRole);
  }
});

Deno.test("S8: chế độ thật — token giả hoặc thiếu token → 401", async () => {
  const emulator = Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST")!;
  Deno.env.delete("FIREBASE_AUTH_EMULATOR_HOST");
  const { publicKey } = await generateKeyPair("RS256");
  __setJwksForTesting(createLocalJWKSet({ keys: [{ ...(await exportJWK(publicKey)), kid: "k1", alg: "RS256" }] }));
  try {
    const fake = await call(emulatorToken({ sub: "x", email: "x@example.com" }), ACCEPT);
    assertEquals([fake.status, fake.body.error.code], [401, "UNAUTHENTICATED"]);
    const none = await handleOnSignup(new Request("http://localhost/", { method: "POST", body: "{}" }), deps);
    assertEquals(none.status, 401);
    await none.body?.cancel();
  } finally {
    __setJwksForTesting(null);
    Deno.env.set("FIREBASE_AUTH_EMULATOR_HOST", emulator);
  }
});

Deno.test("S9: accountStatus LOCKED → 403 ACCOUNT_LOCKED, claim không đổi", async () => {
  await clearEmulators();
  const { uid, idToken } = await signUpEmail("bi.khoa@example.com");
  await commit([createWrite(`users/${uid}`, { role: "PLAYER", accountStatus: "LOCKED" })]);
  await setCustomClaims(uid, { appRole: "PLAYER" });
  const r = await call(idToken, ACCEPT);
  assertEquals([r.status, r.body.error.code], [403, "ACCOUNT_LOCKED"]);
  assertEquals(await getCustomClaims(uid), { appRole: "PLAYER" });
});

Deno.test("S10: body có role ADMIN, walletBalance 1e9 → bị bỏ qua", async () => {
  await clearEmulators();
  const { uid, idToken } = await signUpEmail("gian.lan@example.com");
  const r = await call(idToken, { ...ACCEPT, role: "ADMIN", appRole: "ADMIN", walletBalance: 1e9, accountStatus: "X" });
  assertEquals(r.body, { status: "CREATED", appRole: "PLAYER" });
  const doc = (await get(`users/${uid}`))!;
  assertEquals([doc.role, doc.walletBalance, doc.accountStatus], ["PLAYER", 0, "ACTIVE"]);
  assertEquals(await getCustomClaims(uid), { role: "authenticated", appRole: "PLAYER" });
});

Deno.test("S11: đăng nhập số điện thoại → email null, phone +84…", async () => {
  await clearEmulators();
  const uid = await createUser({ localId: "phone-user", phoneNumber: "+84912345678" });
  const token = emulatorToken({
    sub: uid,
    user_id: uid,
    phone_number: "+84912345678",
    firebase: { sign_in_provider: "phone", identities: {} },
  });
  assertEquals((await call(token, ACCEPT)).status, 200);
  const doc = (await get(`users/${uid}`))!;
  assertEquals([doc.email, doc.phone], [null, "+84912345678"]);
});

Deno.test("S12: email trong body khác token → dùng email của token, đã lowercase", async () => {
  await clearEmulators();
  const uid = await createUser({ localId: "mixed-case", email: "nam.le@example.com" });
  const token = emulatorToken({ sub: uid, user_id: uid, email: "Nam.Le@Example.COM", email_verified: true });
  assertEquals((await call(token, { ...ACCEPT, email: "hacker@evil.com" })).status, 200);
  assertEquals((await get(`users/${uid}`))!.email, "nam.le@example.com");
});

Deno.test("token không có email lẫn phone_number → 400 MISSING_CONTACT", async () => {
  await clearEmulators();
  const uid = await createUser({ localId: "no-contact" });
  const r = await call(emulatorToken({ sub: uid, user_id: uid }), ACCEPT);
  assertEquals([r.status, r.body.error.code], [400, "MISSING_CONTACT"]);
  assertEquals(await get(`users/${uid}`), null);
});

Deno.test("GET → 405 METHOD_NOT_ALLOWED", async () => {
  const res = await handleOnSignup(new Request("http://localhost/", { method: "GET" }), deps);
  assertEquals(res.status, 405);
  assertEquals((await res.json()).error.code, "METHOD_NOT_ALLOWED");
});
