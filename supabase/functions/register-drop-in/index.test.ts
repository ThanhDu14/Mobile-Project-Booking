// T030 (spec 080 US2, SC-002, FR-007): đăng ký đồng thời trên Firebase Emulator, gọi thẳng handler với Firestore
// thật (Emulator) và `requireUser` thật (token không ký của Auth Emulator).
//
// Tự bỏ qua khi thiếu FIRESTORE_EMULATOR_HOST hoặc FIREBASE_AUTH_EMULATOR_HOST. Chạy (cần
// `firebase emulators:start --only auth,firestore --project demo-smashnow`):
//   cd supabase/functions
//   FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 \
//   FIREBASE_PROJECT_ID=demo-smashnow deno test --no-check --allow-env --allow-net register-drop-in/index.test.ts
//
// Không xóa dữ liệu Emulator (có thể đang dùng chung): mọi ID có tiền tố ngẫu nhiên của lần chạy.
import { assert, assertEquals, assertInstanceOf } from "jsr:@std/assert@1.0.19";
import * as firestore from "../_shared/firestore.ts";
import { commit, createWrite, get } from "../_shared/firestore.ts";
import { bearer, emulatorToken } from "../_shared/testing.ts";
import {
  createKeyedSerializer,
  defaultDeps,
  handleRegisterDropIn,
  type KeyedSerializer,
  type Notification,
  type RegisterDeps,
} from "./handler.ts";

const ENABLED = !!Deno.env.get("FIRESTORE_EMULATOR_HOST") && !!Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST");
const REPEAT = 10;
const HOUR = 3600_000;
const RUN = `t080-${crypto.randomUUID().slice(0, 8)}`;
const OWNER = `${RUN}-owner`;
const VENUE = `${RUN}-venue`;
const PLAYERS = Array.from({ length: 20 }, (_, i) => `${RUN}-p${String(i + 1).padStart(2, "0")}`);

const sent: Notification[] = [];
const deps: RegisterDeps = {
  firestore,
  authenticate: defaultDeps().authenticate, // requireUser thật
  notify: (n) => {
    sent.push(n);
    return Promise.resolve();
  },
  now: () => new Date(),
};

let seeded = false;
async function seedBase() {
  if (seeded) return;
  seeded = true;
  await commit([
    createWrite(`users/${OWNER}`, { role: "OWNER", accountStatus: "ACTIVE", ownerStatus: "APPROVED" }),
    createWrite(`users/${RUN}-locked`, { role: "PLAYER", accountStatus: "LOCKED" }),
    createWrite(`venues/${VENUE}`, { name: "Sân thử 080", ownerId: OWNER, status: "ACTIVE", districtId: "q1" }),
    ...PLAYERS.map((uid) => createWrite(`users/${uid}`, { role: "PLAYER", accountStatus: "ACTIVE" })),
  ]);
}

async function seedSession(id: string, capacity: number, registeredCount: number) {
  const startAt = new Date(Date.now() + 24 * HOUR);
  await commit([createWrite(`dropInSessions/${id}`, {
    venueId: VENUE,
    ownerId: OWNER,
    districtId: "q1",
    courtIds: ["court-01"],
    startAt,
    endAt: new Date(startAt.getTime() + 2 * HOUR),
    capacity,
    registeredCount,
    pricePerPerson: 60000,
    level: "INTERMEDIATE",
    status: registeredCount >= capacity ? "FULL" : "OPEN",
  })]);
}

interface Result {
  status: number;
  body: { error?: { code: string; remaining?: number }; registration?: Record<string, unknown> };
}

/**
 * Giả lập nhiều isolate của Edge Function: mỗi isolate có hàng đợi theo buổi riêng, nên request ở các isolate khác
 * nhau vẫn tranh chấp thật trong transaction Firestore (chỉ trong cùng isolate mới xếp hàng).
 */
const isolates = (n: number): KeyedSerializer[] => Array.from({ length: n }, () => createKeyedSerializer());

/** Giống app (research R2): nhận 503 BUSY thì chờ 1 giây rồi gửi lại cùng `requestId`, tối đa 2 lần. */
async function register(
  uid: string,
  sessionId: string,
  partySize: number,
  requestId = crypto.randomUUID(),
  isolate: KeyedSerializer = createKeyedSerializer(),
) {
  let r: Result = { status: 0, body: {} };
  for (let i = 0; i < 3; i++) {
    if (i > 0) await new Promise((resolve) => setTimeout(resolve, 1000));
    const token = emulatorToken({ sub: uid, user_id: uid, appRole: "PLAYER" });
    const res = await handleRegisterDropIn(
      bearer(token, { body: JSON.stringify({ sessionId, partySize, paymentMethod: "MOCK_PAY", requestId }) }),
      { ...deps, serialize: isolate },
    );
    r = { status: res.status, body: await res.json() };
    if (!(r.status === 503 && r.body.error?.code === "BUSY")) break;
  }
  return r;
}

async function registeredDocs(sessionId: string) {
  return await firestore.query("registrations", [{ field: "status", op: "EQUAL", value: "REGISTERED" }], {
    parent: `dropInSessions/${sessionId}`,
  });
}

Deno.test({
  name: `E1: capacity 12, đã có 7, 20 người song song → 5×201, 15×409 SESSION_FULL, 12/12 FULL (×${REPEAT})`,
  ignore: !ENABLED,
  sanitizeOps: false,
  sanitizeResources: false,
  async fn() {
    await seedBase();
    for (let round = 1; round <= REPEAT; round++) {
      const sid = `${RUN}-e1-${round}`;
      await seedSession(sid, 12, 7);
      // 20 người rải đều trên 4 isolate: mỗi lúc tối đa 4 transaction cùng tranh document buổi.
      const iso = isolates(4);
      const rs = await Promise.all(PLAYERS.map((uid, i) => register(uid, sid, 1, undefined, iso[i % 4])));
      const success = rs.filter((r) => r.status === 201).length;
      const full = rs.filter((r) => r.status === 409 && r.body.error?.code === "SESSION_FULL").length;
      const session = (await get(`dropInSessions/${sid}`))!;
      console.log(`[E1 #${round}] success=${success} full=${full} registeredCount=${session.registeredCount}/${session.capacity}`);
      assertEquals(rs.filter((r) => r.status !== 201 && r.status !== 409), [], "chỉ có 201 hoặc 409");
      assertEquals([success, full], [5, 15]);
      assertEquals([session.registeredCount, session.status], [12, "FULL"]);
      assertEquals((await registeredDocs(sid)).length, 5);
    }
  },
});

Deno.test({
  name: `E2: còn 3 chỗ, 2 nhóm 2 người song song → 1 thành công, người kia SESSION_FULL remaining=1 (×${REPEAT})`,
  ignore: !ENABLED,
  sanitizeOps: false,
  sanitizeResources: false,
  async fn() {
    await seedBase();
    for (let round = 1; round <= REPEAT; round++) {
      const sid = `${RUN}-e2-${round}`;
      await seedSession(sid, 12, 9);
      // Hai isolate khác nhau: hai transaction tranh chấp thật.
      const [a, b] = isolates(2);
      const rs = await Promise.all([register(PLAYERS[0], sid, 2, undefined, a), register(PLAYERS[1], sid, 2, undefined, b)]);
      const ok = rs.filter((r) => r.status === 201);
      const lost = rs.filter((r) => r.status === 409);
      const session = (await get(`dropInSessions/${sid}`))!;
      console.log(`[E2 #${round}] success=${ok.length} full=${lost.length} registeredCount=${session.registeredCount}/12`);
      assertEquals([ok.length, lost.length], [1, 1]);
      assertEquals([lost[0].body.error?.code, lost[0].body.error?.remaining], ["SESSION_FULL", 1]);
      assertEquals([session.registeredCount, session.status], [11, "OPEN"]);
      assert(Number(session.registeredCount) <= Number(session.capacity));
    }
  },
});

Deno.test({
  name: "E3: cùng requestId 5 lần song song → 1×201, 4×200, đếm +1, thông báo 1 lần; trường thời gian là Timestamp",
  ignore: !ENABLED,
  sanitizeOps: false,
  sanitizeResources: false,
  async fn() {
    await seedBase();
    const sid = `${RUN}-e3`;
    await seedSession(sid, 12, 2);
    sent.length = 0;
    const requestId = crypto.randomUUID();
    const iso = isolates(2);
    const rs = await Promise.all(Array.from({ length: 5 }, (_, i) => register(PLAYERS[2], sid, 2, requestId, iso[i % 2])));
    assertEquals(rs.map((r) => r.status).sort(), [200, 200, 200, 200, 201]);
    for (const r of rs) assertEquals(r.body.registration, rs[0].body.registration);
    assert(!JSON.stringify(rs).includes("qrToken"));
    assertEquals((await get(`dropInSessions/${sid}`))!.registeredCount, 4);
    assertEquals(sent, [{ type: "DROPIN_REGISTERED", userId: PLAYERS[2], data: { sessionId: sid } }]);

    const reg = (await get(`dropInSessions/${sid}/registrations/${PLAYERS[2]}`))!;
    for (const k of ["sessionStartAt", "sessionEndAt", "registeredAt", "createdAt", "updatedAt"]) {
      assertInstanceOf(reg[k], Date, k);
    }
    assertEquals([reg.status, reg.paymentStatus, reg.amount, reg.requestId], ["REGISTERED", "PAID", 120000, requestId]);
  },
});

Deno.test({
  name: "requireUser thật: tài khoản LOCKED → 403 ACCOUNT_LOCKED; không token → 401",
  ignore: !ENABLED,
  sanitizeOps: false,
  sanitizeResources: false,
  async fn() {
    await seedBase();
    const sid = `${RUN}-auth`;
    await seedSession(sid, 12, 0);
    const locked = `${RUN}-locked`;
    const res = await handleRegisterDropIn(
      bearer(emulatorToken({ sub: locked, user_id: locked, appRole: "PLAYER" }), {
        body: JSON.stringify({ sessionId: sid, partySize: 1, paymentMethod: "MOCK_PAY", requestId: crypto.randomUUID() }),
      }),
      deps,
    );
    assertEquals([res.status, (await res.json()).error.code], [403, "ACCOUNT_LOCKED"]);
    const anon = await handleRegisterDropIn(
      new Request("http://localhost/functions/v1/register-drop-in", { method: "POST", body: "{}" }),
      deps,
    );
    assertEquals([anon.status, (await anon.json()).error.code], [401, "UNAUTHENTICATED"]);
  },
});
