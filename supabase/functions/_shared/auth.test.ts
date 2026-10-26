// T020: chạy trên Auth + Firestore Emulator (quickstart §2).
import { assert, assertEquals, assertRejects } from "jsr:@std/assert@1.0.19";
import { createLocalJWKSet, exportJWK, generateKeyPair, SignJWT } from "npm:jose@6.2.12";
import {
  __setJwksForTesting,
  errorResponse,
  HttpError,
  requireRole,
  requireUser,
  type VerifiedToken,
  verifyIdToken,
} from "./auth.ts";
import { commit, createWrite, projectId } from "./firestore.ts";
import { bearer, clearEmulators, emulatorToken, signUpEmail } from "./testing.ts";

async function assertHttpError(p: Promise<unknown>, status: number, code: string) {
  const e = await assertRejects(() => p, HttpError);
  assertEquals([e.status, e.code], [status, code]);
}

// ---------- Chế độ Emulator ----------

Deno.test("Emulator: token của Auth Emulator được chấp nhận, trả đủ trường, có log cảnh báo", async () => {
  await clearEmulators();
  const { uid, idToken } = await signUpEmail("nam.le@example.com", "abc12345", "Lê Hoàng Nam");
  const warnings: unknown[] = [];
  const original = console.warn;
  console.warn = (...args: unknown[]) => warnings.push(args);
  try {
    const t = await verifyIdToken(bearer(idToken));
    assertEquals(t, {
      uid,
      appRole: null,
      email: "nam.le@example.com",
      emailVerified: false,
      phone: null,
      name: "Lê Hoàng Nam",
      signInProvider: "password",
    } satisfies VerifiedToken);
  } finally {
    console.warn = original;
  }
  assert(warnings.length > 0, "phải ghi log cảnh báo khi bỏ qua chữ ký");
});

Deno.test("Emulator: token số điện thoại có phone, không có email", async () => {
  const t = await verifyIdToken(bearer(emulatorToken({
    sub: "phone-user",
    user_id: "phone-user",
    phone_number: "+84912345678",
    firebase: { sign_in_provider: "phone", identities: {} },
  })));
  assertEquals([t.uid, t.email, t.phone, t.signInProvider], ["phone-user", null, "+84912345678", "phone"]);
});

Deno.test("thiếu header Authorization → 401 UNAUTHENTICATED", async () => {
  await assertHttpError(verifyIdToken(new Request("http://localhost/", { method: "POST" })), 401, "UNAUTHENTICATED");
});

// ---------- Chế độ thật với JWKS giả ----------

async function withRealMode(fn: (sign: (claims: Record<string, unknown>, opts?: SignOpts) => Promise<string>) => Promise<void>) {
  const emulator = Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST");
  Deno.env.delete("FIREBASE_AUTH_EMULATOR_HOST");
  const good = await generateKeyPair("RS256");
  const bad = await generateKeyPair("RS256");
  const jwk = { ...(await exportJWK(good.publicKey)), kid: "k1", alg: "RS256" };
  __setJwksForTesting(createLocalJWKSet({ keys: [jwk] }));
  const pid = projectId();
  const sign = (claims: Record<string, unknown>, opts: SignOpts = {}) => {
    const now = Math.floor(Date.now() / 1000);
    return new SignJWT({ firebase: { sign_in_provider: "password" }, ...claims })
      .setProtectedHeader({ alg: "RS256", kid: "k1" })
      .setIssuer(opts.iss ?? `https://securetoken.google.com/${pid}`)
      .setAudience(opts.aud ?? pid)
      .setSubject("real-user")
      .setIssuedAt(now - 10)
      .setExpirationTime(opts.exp ?? now + 3600)
      .sign(opts.wrongKey ? bad.privateKey : good.privateKey);
  };
  try {
    await fn(sign);
  } finally {
    __setJwksForTesting(null);
    if (emulator) Deno.env.set("FIREBASE_AUTH_EMULATOR_HOST", emulator);
  }
}

interface SignOpts {
  iss?: string;
  aud?: string;
  exp?: number;
  wrongKey?: boolean;
}

Deno.test("thật: token hợp lệ được chấp nhận", async () => {
  await withRealMode(async (sign) => {
    const t = await verifyIdToken(bearer(await sign({ appRole: "PLAYER", email: "a@b.vn", email_verified: true })));
    assertEquals([t.uid, t.appRole, t.email, t.emailVerified], ["real-user", "PLAYER", "a@b.vn", true]);
  });
});

Deno.test("thật: sai chữ ký → 401", async () => {
  await withRealMode(async (sign) => {
    await assertHttpError(verifyIdToken(bearer(await sign({}, { wrongKey: true }))), 401, "UNAUTHENTICATED");
  });
});

Deno.test("thật: hết hạn → 401", async () => {
  await withRealMode(async (sign) => {
    const exp = Math.floor(Date.now() / 1000) - 60;
    await assertHttpError(verifyIdToken(bearer(await sign({}, { exp }))), 401, "UNAUTHENTICATED");
  });
});

Deno.test("thật: sai aud → 401", async () => {
  await withRealMode(async (sign) => {
    await assertHttpError(verifyIdToken(bearer(await sign({}, { aud: "other-project" }))), 401, "UNAUTHENTICATED");
  });
});

Deno.test("thật: sai iss → 401", async () => {
  await withRealMode(async (sign) => {
    const iss = "https://securetoken.google.com/other-project";
    await assertHttpError(verifyIdToken(bearer(await sign({}, { iss }))), 401, "UNAUTHENTICATED");
  });
});

Deno.test("thật: token không ký của Emulator bị từ chối khi không bật Emulator", async () => {
  await withRealMode(async () => {
    await assertHttpError(verifyIdToken(bearer(emulatorToken({ sub: "x" }))), 401, "UNAUTHENTICATED");
  });
});

// ---------- requireUser, requireRole ----------

Deno.test("requireUser: thiếu appRole → 403 NOT_INITIALIZED", async () => {
  await assertHttpError(requireUser(bearer(emulatorToken({ sub: "u-new", email: "n@b.vn" }))), 403, "NOT_INITIALIZED");
});

Deno.test("requireUser: accountStatus LOCKED → 403 ACCOUNT_LOCKED", async () => {
  await clearEmulators();
  await commit([createWrite("users/u-locked", { accountStatus: "LOCKED", role: "PLAYER" })]);
  await assertHttpError(
    requireUser(bearer(emulatorToken({ sub: "u-locked", appRole: "PLAYER" }))),
    403,
    "ACCOUNT_LOCKED",
  );
});

Deno.test("requireUser: ACTIVE → trả người dùng có appRole", async () => {
  await clearEmulators();
  await commit([createWrite("users/u-ok", { accountStatus: "ACTIVE", role: "OWNER" })]);
  const u = await requireUser(bearer(emulatorToken({ sub: "u-ok", appRole: "OWNER" })));
  assertEquals([u.uid, u.appRole], ["u-ok", "OWNER"]);
});

const player: VerifiedToken = {
  uid: "p1",
  appRole: "PLAYER",
  email: null,
  emailVerified: false,
  phone: null,
  name: null,
  signInProvider: "password",
};

Deno.test("requireRole: PLAYER gọi chức năng ADMIN → 403 FORBIDDEN", () => {
  const e = (() => {
    try {
      requireRole(player, "ADMIN");
    } catch (e) {
      return e;
    }
  })();
  assert(e instanceof HttpError);
  assertEquals([e.status, e.code], [403, "FORBIDDEN"]);
});

Deno.test("requireRole: đúng một trong các vai trò thì không ném", () => {
  requireRole({ ...player, appRole: "OWNER" }, "OWNER", "ADMIN");
});

Deno.test("errorResponse: đúng dạng { error: { code, message, ...extra } }", async () => {
  const res = errorResponse(429, "RATE_LIMITED", "Thử lại sau", { retryAfter: 30 });
  assertEquals(res.status, 429);
  assertEquals(res.headers.get("Content-Type"), "application/json");
  assertEquals(await res.json(), { error: { code: "RATE_LIMITED", message: "Thử lại sau", retryAfter: 30 } });
});
