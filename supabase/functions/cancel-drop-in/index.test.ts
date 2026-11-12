// T050, T051 (spec 080 US4): `cancel-drop-in` trên Firebase Emulator, gọi thẳng handler với Firestore thật (Emulator)
// và `requireUser` thật (token không ký của Auth Emulator).
//
// Tự bỏ qua khi thiếu FIRESTORE_EMULATOR_HOST hoặc FIREBASE_AUTH_EMULATOR_HOST. Chạy (cần
// `firebase emulators:start --only auth,firestore --project demo-smashnow`):
//   cd supabase/functions
//   FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 \
//   FIREBASE_PROJECT_ID=demo-smashnow deno test --no-check --allow-env --allow-net cancel-drop-in/index.test.ts
//
// Không xóa dữ liệu Emulator (có thể đang dùng chung): mọi ID có tiền tố ngẫu nhiên của lần chạy.
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import * as firestore from "../_shared/firestore.ts";
import { commit, createWrite, get } from "../_shared/firestore.ts";
import { createKeyedSerializer, type KeyedSerializer } from "../_shared/dropin-tx.ts";
import { bearer, emulatorToken } from "../_shared/testing.ts";
import { type CancelDeps, defaultDeps, handleCancelDropIn } from "./handler.ts";
import { handleRegisterDropIn, type RegisterDeps } from "../register-drop-in/handler.ts";

const ENABLED = !!Deno.env.get("FIRESTORE_EMULATOR_HOST") && !!Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST");
const REPEAT = 10;
const MIN = 60_000;
const HOUR = 60 * MIN;
const RUN = `t080c-${crypto.randomUUID().slice(0, 8)}`;
const OWNER = `${RUN}-owner`;
const VENUE = `${RUN}-venue`;
const PLAYERS = Array.from({ length: 6 }, (_, i) => `${RUN}-p${i + 1}`);
const PRICE = 60000;

/** Giờ server cố định của mỗi test để mốc 239/240/241 phút không lệch theo thời gian chạy. */
let now = new Date();
const deps: CancelDeps = {
  firestore,
  authenticate: defaultDeps().authenticate, // requireUser thật
  now: () => now,
};
const registerDeps: RegisterDeps = {
  firestore,
  authenticate: defaultDeps().authenticate,
  notify: () => Promise.resolve(),
  now: () => new Date(),
};

let seeded = false;
async function seedBase() {
  if (seeded) return;
  seeded = true;
  await commit([
    createWrite(`users/${OWNER}`, { role: "OWNER", accountStatus: "ACTIVE", ownerStatus: "APPROVED" }),
    createWrite(`venues/${VENUE}`, { name: "Sân thử 080 hủy", ownerId: OWNER, status: "ACTIVE", districtId: "q1" }),
    ...PLAYERS.map((uid) => createWrite(`users/${uid}`, { role: "PLAYER", accountStatus: "ACTIVE" })),
  ]);
}

interface RegSeed {
  uid: string;
  partySize?: number;
  status?: string;
  paymentMethod?: "MOCK_PAY" | "AT_VENUE";
  sessionChanged?: boolean;
}

/** Buổi bắt đầu sau `startInMin` phút tính từ `now`, kèm các lượt đăng ký; `registeredCount` = tổng lượt giữ chỗ. */
async function seedSession(id: string, opts: { capacity: number; startInMin: number; regs: RegSeed[]; extra?: number }) {
  const startAt = new Date(now.getTime() + opts.startInMin * MIN);
  const endAt = new Date(startAt.getTime() + 2 * HOUR);
  const held = opts.regs
    .filter((r) => (r.status ?? "REGISTERED") !== "CANCELLED")
    .reduce((s, r) => s + (r.partySize ?? 1), 0) + (opts.extra ?? 0);
  await commit([
    createWrite(`dropInSessions/${id}`, {
      venueId: VENUE,
      ownerId: OWNER,
      districtId: "q1",
      courtIds: ["court-01"],
      startAt,
      endAt,
      capacity: opts.capacity,
      registeredCount: held,
      pricePerPerson: PRICE,
      level: "INTERMEDIATE",
      status: held >= opts.capacity ? "FULL" : "OPEN",
    }),
    ...opts.regs.map((r) => {
      const partySize = r.partySize ?? 1;
      const method = r.paymentMethod ?? "MOCK_PAY";
      return createWrite(`dropInSessions/${id}/registrations/${r.uid}`, {
        userId: r.uid,
        sessionId: id,
        venueId: VENUE,
        sessionStartAt: startAt,
        sessionEndAt: endAt,
        partySize,
        status: r.status ?? "REGISTERED",
        paymentMethod: method,
        paymentStatus: method === "MOCK_PAY" ? "PAID" : "UNPAID",
        pricePerPerson: PRICE,
        amount: PRICE * partySize,
        refundAmount: null,
        qrToken: `qr-${r.uid}`,
        ticketCode: "DI-ABCDEFGH",
        requestId: crypto.randomUUID(),
        cancelRequestId: null,
        sessionChanged: r.sessionChanged ?? false,
        registeredAt: now,
        cancelledAt: null,
        cancelledBy: null,
        checkedInAt: r.status === "CHECKED_IN" ? now : null,
        waitlistedAt: null,
        createdAt: now,
        updatedAt: now,
      });
    }),
  ]);
}

interface Body {
  error?: { code: string };
  registration?: Record<string, unknown>;
  quote?: { refundAmount: number; reason: string };
  session?: { registeredCount: number; capacity: number; status: string };
}

async function cancel(
  uid: string,
  sessionId: string,
  opts: { requestId?: string; dryRun?: boolean; isolate?: KeyedSerializer } = {},
): Promise<{ status: number; body: Body }> {
  const token = emulatorToken({ sub: uid, user_id: uid, appRole: "PLAYER" });
  const res = await handleCancelDropIn(
    bearer(token, {
      body: JSON.stringify({ sessionId, requestId: opts.requestId ?? crypto.randomUUID(), dryRun: opts.dryRun ?? false }),
    }),
    { ...deps, serialize: opts.isolate ?? createKeyedSerializer() },
  );
  return { status: res.status, body: await res.json() };
}

const regOf = async (sid: string, uid: string) => (await get(`dropInSessions/${sid}/registrations/${uid}`))!;
const sessionOf = async (sid: string) => (await get(`dropInSessions/${sid}`))!;

const T = (name: string, fn: () => Promise<void>) =>
  Deno.test({ name, ignore: !ENABLED, sanitizeOps: false, sanitizeResources: false, fn });

T("E4: cùng requestId gọi 5 lần song song → trả chỗ 1 lần, hoàn tiền 1 lần, kết quả giống nhau", async () => {
  await seedBase();
  now = new Date();
  const sid = `${RUN}-e4`;
  await seedSession(sid, { capacity: 12, startInMin: 24 * 60, regs: [{ uid: PLAYERS[0], partySize: 2 }], extra: 5 });
  const requestId = crypto.randomUUID();
  const iso = [createKeyedSerializer(), createKeyedSerializer()];
  const rs = await Promise.all(
    Array.from({ length: 5 }, (_, i) => cancel(PLAYERS[0], sid, { requestId, isolate: iso[i % 2] })),
  );
  assertEquals(rs.map((r) => r.status), [200, 200, 200, 200, 200]);
  for (const r of rs) {
    assertEquals(r.body.registration, rs[0].body.registration);
    assertEquals(r.body.quote, { refundAmount: 120000, reason: "EARLY" });
  }
  const session = await sessionOf(sid);
  assertEquals(session.registeredCount, 5);
  const reg = await regOf(sid, PLAYERS[0]);
  assertEquals(
    [reg.status, reg.paymentStatus, reg.refundAmount, reg.cancelledBy, reg.cancelRequestId],
    ["CANCELLED", "REFUNDED", 120000, "USER", requestId],
  );
  // Gửi lại sau khi đã xong vẫn trả kết quả cũ, không trừ thêm.
  const again = await cancel(PLAYERS[0], sid, { requestId });
  assertEquals([again.status, again.body.quote], [200, { refundAmount: 120000, reason: "EARLY" }]);
  assertEquals((await sessionOf(sid)).registeredCount, 5);
});

T("E5: còn 239 / 240 / 241 phút → hoàn 0 / 100% / 100%", async () => {
  await seedBase();
  now = new Date();
  for (const [minutes, refund, reason] of [[239, 0, "LATE"], [240, 60000, "EARLY"], [241, 60000, "EARLY"]] as const) {
    const sid = `${RUN}-e5-${minutes}`;
    await seedSession(sid, { capacity: 12, startInMin: minutes, regs: [{ uid: PLAYERS[1] }] });
    const r = await cancel(PLAYERS[1], sid);
    assertEquals([r.status, r.body.quote], [200, { refundAmount: refund, reason }], `${minutes} phút`);
    const reg = await regOf(sid, PLAYERS[1]);
    assertEquals([reg.refundAmount, reg.paymentStatus], [refund, refund > 0 ? "REFUNDED" : "PAID"], `${minutes} phút`);
  }
});

T("E6: sessionChanged = true, còn 30 phút → hoàn 100% SESSION_CHANGED", async () => {
  await seedBase();
  now = new Date();
  const sid = `${RUN}-e6`;
  await seedSession(sid, { capacity: 12, startInMin: 30, regs: [{ uid: PLAYERS[1], partySize: 3, sessionChanged: true }] });
  const r = await cancel(PLAYERS[1], sid);
  assertEquals([r.status, r.body.quote], [200, { refundAmount: 180000, reason: "SESSION_CHANGED" }]);
  assertEquals((await regOf(sid, PLAYERS[1])).paymentStatus, "REFUNDED");
});

T("E7: lượt AT_VENUE → refundAmount 0, reason AT_VENUE, paymentStatus giữ UNPAID", async () => {
  await seedBase();
  now = new Date();
  const sid = `${RUN}-e7`;
  await seedSession(sid, { capacity: 12, startInMin: 24 * 60, regs: [{ uid: PLAYERS[1], paymentMethod: "AT_VENUE" }] });
  const r = await cancel(PLAYERS[1], sid);
  assertEquals([r.status, r.body.quote], [200, { refundAmount: 0, reason: "AT_VENUE" }]);
  const reg = await regOf(sid, PLAYERS[1]);
  assertEquals([reg.status, reg.paymentStatus, reg.refundAmount], ["CANCELLED", "UNPAID", 0]);
});

T("E10: token người khác → 404 NOT_FOUND, đăng ký không đổi", async () => {
  await seedBase();
  now = new Date();
  const sid = `${RUN}-e10`;
  await seedSession(sid, { capacity: 12, startInMin: 24 * 60, regs: [{ uid: PLAYERS[1] }] });
  const r = await cancel(PLAYERS[2], sid);
  assertEquals([r.status, r.body.error?.code], [404, "NOT_FOUND"]);
  assertEquals((await regOf(sid, PLAYERS[1])).status, "REGISTERED");
  assertEquals((await sessionOf(sid)).registeredCount, 1);
});

T("E12 + E13: buổi FULL, một người hủy → OPEN; qrToken và ticketCode bằng null", async () => {
  await seedBase();
  now = new Date();
  const sid = `${RUN}-e12`;
  await seedSession(sid, { capacity: 4, startInMin: 24 * 60, regs: [{ uid: PLAYERS[1], partySize: 2 }], extra: 2 });
  assertEquals((await sessionOf(sid)).status, "FULL");
  const r = await cancel(PLAYERS[1], sid);
  assertEquals([r.status, r.body.session], [200, { registeredCount: 2, capacity: 4, status: "OPEN" }]);
  const session = await sessionOf(sid);
  assertEquals([session.registeredCount, session.status], [2, "OPEN"]);
  const reg = await regOf(sid, PLAYERS[1]);
  assertEquals([reg.qrToken, reg.ticketCode], [null, null]);
  assert(!JSON.stringify(r.body).includes("qr-"), "không trả qrToken");
});

T("CHECKED_IN → 409 NOT_CANCELLABLE; buổi đã bắt đầu → 409 SESSION_STARTED", async () => {
  await seedBase();
  now = new Date();
  const sid = `${RUN}-ci`;
  await seedSession(sid, { capacity: 12, startInMin: 60, regs: [{ uid: PLAYERS[1], status: "CHECKED_IN" }] });
  const r = await cancel(PLAYERS[1], sid);
  assertEquals([r.status, r.body.error?.code], [409, "NOT_CANCELLABLE"]);
  assertEquals((await regOf(sid, PLAYERS[1])).status, "CHECKED_IN");

  const started = `${RUN}-started`;
  await seedSession(started, { capacity: 12, startInMin: -5, regs: [{ uid: PLAYERS[1] }] });
  const s = await cancel(PLAYERS[1], started);
  assertEquals([s.status, s.body.error?.code], [409, "SESSION_STARTED"]);
  assertEquals((await regOf(started, PLAYERS[1])).status, "REGISTERED");
});

T("dryRun: true chỉ trả quote, không ghi gì", async () => {
  await seedBase();
  now = new Date();
  const sid = `${RUN}-dry`;
  await seedSession(sid, { capacity: 12, startInMin: 24 * 60, regs: [{ uid: PLAYERS[1], partySize: 2 }] });
  const before = { reg: await regOf(sid, PLAYERS[1]), session: await sessionOf(sid) };
  const r = await cancel(PLAYERS[1], sid, { dryRun: true });
  assertEquals([r.status, r.body.quote], [200, { refundAmount: 120000, reason: "EARLY" }]);
  assertEquals(r.body.registration?.status, "REGISTERED");
  assertEquals(await regOf(sid, PLAYERS[1]), before.reg);
  assertEquals(await sessionOf(sid), before.session);
});

T("Không gọi send-notification; requestId sai → 400 INVALID_ARGUMENT", async () => {
  await seedBase();
  now = new Date();
  const sid = `${RUN}-notify`;
  await seedSession(sid, { capacity: 12, startInMin: 24 * 60, regs: [{ uid: PLAYERS[1] }] });
  const realFetch = globalThis.fetch;
  const urls: string[] = [];
  globalThis.fetch = (input: string | URL | Request, init?: RequestInit) => {
    urls.push(input instanceof Request ? input.url : String(input));
    return realFetch(input, init);
  };
  try {
    assertEquals((await cancel(PLAYERS[1], sid)).status, 200);
  } finally {
    globalThis.fetch = realFetch;
  }
  assertEquals(urls.filter((u) => u.includes("send-notification")), []);

  const token = emulatorToken({ sub: PLAYERS[1], user_id: PLAYERS[1], appRole: "PLAYER" });
  const bad = await handleCancelDropIn(bearer(token, { body: JSON.stringify({ sessionId: sid, requestId: "x" }) }), deps);
  assertEquals([bad.status, (await bad.json()).error.code], [400, "INVALID_ARGUMENT"]);
});

T(`T051: buổi FULL, 1 người hủy (2 chỗ) cùng lúc 3 người đăng ký → đúng 2 người vào, không vượt sức chứa (×${REPEAT})`, async () => {
  await seedBase();
  const [canceller, ...others] = PLAYERS;
  const joiners = others.slice(0, 3);
  for (let round = 1; round <= REPEAT; round++) {
    now = new Date();
    const sid = `${RUN}-t051-${round}`;
    await seedSession(sid, { capacity: 6, startInMin: 24 * 60, regs: [{ uid: canceller, partySize: 2 }], extra: 4 });

    const register = async (uid: string, requestId = crypto.randomUUID()) => {
      // Như app (R2): BUSY thì người dùng bấm "Thử lại" với cùng requestId.
      for (let i = 0;; i++) {
        const token = emulatorToken({ sub: uid, user_id: uid, appRole: "PLAYER" });
        const res = await handleRegisterDropIn(
          bearer(token, { body: JSON.stringify({ sessionId: sid, partySize: 1, paymentMethod: "MOCK_PAY", requestId }) }),
          { ...registerDeps, serialize: createKeyedSerializer() },
        );
        const body = await res.json();
        if (!(res.status === 503 && body.error?.code === "BUSY") || i >= 2) return { status: res.status, code: body.error?.code };
        await new Promise((r) => setTimeout(r, 1000));
      }
    };

    // Mỗi request ở một isolate riêng (cancel và register là hai function khác nhau): tranh chấp thật.
    // Người đăng ký tới rải rác 0–600 ms sau lệnh hủy: có người tới trước, trong lúc và sau khi chỗ được trả.
    const cancelling = cancel(canceller, sid);
    const first = await Promise.all(
      joiners.map(async (uid) => {
        await new Promise((r) => setTimeout(r, Math.random() * 600));
        return register(uid);
      }),
    );
    const c = await cancelling;
    assertEquals(c.status, 200, "hủy thành công");
    // Ai bị SESSION_FULL vì tới trước khi chỗ được trả thì thử lại một lần sau khi hủy xong (người dùng bấm lại).
    const final = await Promise.all(
      first.map((r, i) => r.status === 409 && r.code === "SESSION_FULL" ? register(joiners[i]) : Promise.resolve(r)),
    );
    const ok = final.filter((r) => r.status === 201).length;
    const full = final.filter((r) => r.status === 409 && r.code === "SESSION_FULL").length;
    const session = await sessionOf(sid);
    const active = await firestore.query("registrations", [{ field: "status", op: "EQUAL", value: "REGISTERED" }], {
      parent: `dropInSessions/${sid}`,
    });
    const held = active.reduce((s, d) => s + Number(d.data.partySize ?? 1), 0) + 4;
    console.log(
      `[T051 #${round}] lượt đầu ok=${first.filter((r) => r.status === 201).length} cuối ok=${ok} full=${full} ` +
        `registeredCount=${session.registeredCount}/6 ${session.status}`,
    );
    assertEquals([ok, full], [2, 1]);
    assertEquals([session.registeredCount, session.status], [6, "FULL"]);
    assertEquals(held, 6, "registeredCount khớp tổng partySize các lượt còn giữ chỗ");
  }
});
