// T015 (spec 081 US1): vào hàng chờ qua `register-drop-in` với `joinWaitlist: true`, trên Firebase Emulator. Gọi thẳng
// handler với Firestore thật và `requireUser` thật (token không ký của Auth Emulator). Tách file khỏi index.test.ts
// của spec 080 để không đụng nhau.
//
// Tự bỏ qua khi thiếu FIRESTORE_EMULATOR_HOST hoặc FIREBASE_AUTH_EMULATOR_HOST. Chạy (cần
// `firebase emulators:start --only auth,firestore --project demo-smashnow`):
//   cd supabase/functions
//   FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 \
//   FIREBASE_PROJECT_ID=demo-smashnow deno test --no-check --allow-env --allow-net register-drop-in/waitlist.test.ts
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import * as firestore from "../_shared/firestore.ts";
import { commit, createWrite, get, updateWrite } from "../_shared/firestore.ts";
import { bearer, emulatorToken } from "../_shared/testing.ts";
import { assertWaitlistInvariant, seedFullSession } from "../_shared/waitlist_fixtures.ts";
import {
  createKeyedSerializer,
  defaultDeps,
  handleRegisterDropIn,
  type KeyedSerializer,
  type Notification,
  type RegisterDeps,
} from "./handler.ts";

const ENABLED = !!Deno.env.get("FIRESTORE_EMULATOR_HOST") && !!Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST");
const RUN = `t081r-${crypto.randomUUID().slice(0, 8)}`;
const JOINERS = Array.from({ length: 8 }, (_, i) => `${RUN}-j${i + 1}`);

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
  await commit(JOINERS.map((uid) => createWrite(`users/${uid}`, { role: "PLAYER", accountStatus: "ACTIVE" })));
}

interface Result {
  status: number;
  // deno-lint-ignore no-explicit-any
  body: any;
}

/** Như app: 503 BUSY thì chờ rồi gửi lại cùng `requestId`. */
async function call(
  uid: string,
  body: Record<string, unknown>,
  opts: { isolate?: KeyedSerializer; role?: string } = {},
): Promise<Result> {
  let r: Result = { status: 0, body: {} };
  for (let i = 0; i < 3; i++) {
    if (i > 0) await new Promise((resolve) => setTimeout(resolve, 500));
    const token = emulatorToken({ sub: uid, user_id: uid, appRole: opts.role ?? "PLAYER" });
    const res = await handleRegisterDropIn(bearer(token, { body: JSON.stringify(body) }), {
      ...deps,
      serialize: opts.isolate ?? createKeyedSerializer(),
    });
    r = { status: res.status, body: await res.json() };
    if (!(r.status === 503 && r.body.error?.code === "BUSY")) break;
  }
  return r;
}

const join = (
  uid: string,
  sessionId: string,
  extra: Record<string, unknown> = {},
  opts: { isolate?: KeyedSerializer; role?: string } = {},
) =>
  call(uid, {
    sessionId,
    partySize: 1,
    paymentMethod: "MOCK_PAY",
    requestId: crypto.randomUUID(),
    joinWaitlist: true,
    ...extra,
  }, opts);

const regOf = async (sid: string, uid: string) => (await get(`dropInSessions/${sid}/registrations/${uid}`))!;
const sessionOf = async (sid: string) => (await get(`dropInSessions/${sid}`))!;

const T = (name: string, fn: () => Promise<void>) =>
  Deno.test({ name, ignore: !ENABLED, sanitizeOps: false, sanitizeResources: false, fn });

T("Q1: 3 người lần lượt vào hàng chờ buổi FULL → vị trí 1, 2, 3; registeredCount không đổi; lượt chờ chưa có tiền/QR", async () => {
  await seedBase();
  const s = await seedFullSession({ id: `${RUN}-q1`, capacity: 4, registered: [2, 2], waitlist: [], startInMinutes: 24 * 60 });
  sent.length = 0;
  // partySize = 4 hợp lệ dù buổi không còn chỗ nào (FR-002).
  const sizes = [1, 2, 4];
  for (let i = 0; i < 3; i++) {
    const r = await join(JOINERS[i], s.sessionId, { partySize: sizes[i], paymentMethod: i === 1 ? "AT_VENUE" : "MOCK_PAY" });
    assertEquals(r.status, 201, JSON.stringify(r.body));
    assertEquals(r.body.waitlist, { position: i + 1, total: i + 1 });
    assertEquals(r.body.registration.status, "WAITLISTED");
    assertEquals(r.body.registration.amount, 60000 * sizes[i]);
    assertEquals(r.body.registration.pricePerPerson, 60000);
    assert(typeof r.body.registration.waitlistedAt === "string");
    assert(!JSON.stringify(r.body).includes("qrToken"));
    await assertWaitlistInvariant(s.sessionId);
  }
  const session = await sessionOf(s.sessionId);
  assertEquals([session.registeredCount, session.status, session.waitlistTickets, session.waitlistCount, session.waitlistNextTicket], [
    4,
    "FULL",
    [1, 2, 3],
    3,
    4,
  ]);
  const reg = await regOf(s.sessionId, JOINERS[1]);
  assertEquals(
    [reg.status, reg.paymentMethod, reg.paymentStatus, reg.qrToken, reg.ticketCode, reg.waitlistTicket, reg.promotedAt, reg.waitlistEndReason],
    ["WAITLISTED", "AT_VENUE", null, null, null, 2, null, null],
  );
  assert(reg.waitlistedAt instanceof Date);
  assertEquals(sent, [], "vào hàng chờ không gửi thông báo");
});

T("Q2: cùng requestId 5 lần song song → 1×201, 4×200, waitlistCount tăng 1 lần", async () => {
  await seedBase();
  const s = await seedFullSession({ id: `${RUN}-q2`, capacity: 2, registered: [2], waitlist: [], startInMinutes: 24 * 60 });
  const requestId = crypto.randomUUID();
  const iso = [createKeyedSerializer(), createKeyedSerializer()];
  const rs = await Promise.all(
    Array.from({ length: 5 }, (_, i) => join(JOINERS[0], s.sessionId, { requestId }, { isolate: iso[i % 2] })),
  );
  assertEquals(rs.map((r) => r.status).sort(), [200, 200, 200, 200, 201]);
  for (const r of rs) assertEquals(r.body.registration, rs[0].body.registration);
  for (const r of rs) assertEquals(r.body.waitlist, { position: 1, total: 1 });
  const session = await sessionOf(s.sessionId);
  assertEquals([session.waitlistTickets, session.waitlistCount, session.waitlistNextTicket], [[1], 1, 2]);
  await assertWaitlistInvariant(s.sessionId);
});

T("Q3: buổi OPEN → SESSION_HAS_SPOTS kèm remaining; còn đúng 30 phút → WAITLIST_CLOSED; còn 31 phút vào được", async () => {
  await seedBase();
  const open = await seedFullSession({ id: `${RUN}-q3a`, capacity: 6, registered: [2, 2], waitlist: [], startInMinutes: 24 * 60 });
  const a = await join(JOINERS[0], open.sessionId);
  assertEquals([a.status, a.body.error.code, a.body.error.remaining], [409, "SESSION_HAS_SPOTS", 2]);

  const at30 = await seedFullSession({ id: `${RUN}-q3b`, capacity: 2, registered: [2], waitlist: [], startInMinutes: 30 });
  const b = await join(JOINERS[0], at30.sessionId);
  assertEquals([b.status, b.body.error.code], [409, "WAITLIST_CLOSED"]);
  const at25 = await seedFullSession({ id: `${RUN}-q3b2`, capacity: 2, registered: [2], waitlist: [], startInMinutes: 25 });
  assertEquals((await join(JOINERS[0], at25.sessionId)).body.error.code, "WAITLIST_CLOSED");
  const at31 = await seedFullSession({ id: `${RUN}-q3b3`, capacity: 2, registered: [2], waitlist: [], startInMinutes: 31 });
  assertEquals((await join(JOINERS[0], at31.sessionId)).status, 201);
  await assertWaitlistInvariant(at30.sessionId);
});

T("Q3: chủ buổi → OWN_SESSION; buổi CANCELLED → SESSION_CANCELLED; cơ sở HIDDEN → VENUE_INACTIVE", async () => {
  await seedBase();
  const own = await seedFullSession({ id: `${RUN}-q3c`, capacity: 2, registered: [2], waitlist: [], startInMinutes: 24 * 60 });
  const c = await join(own.ownerId, own.sessionId, {}, { role: "OWNER" });
  assertEquals([c.status, c.body.error.code], [403, "OWN_SESSION"]);

  const cancelled = await seedFullSession({ id: `${RUN}-q3d`, capacity: 2, registered: [2], waitlist: [], startInMinutes: 24 * 60 });
  await commit([updateWrite(`dropInSessions/${cancelled.sessionId}`, { status: "CANCELLED" })]);
  const d = await join(JOINERS[0], cancelled.sessionId);
  assertEquals([d.status, d.body.error.code], [409, "SESSION_CANCELLED"]);

  const hidden = await seedFullSession({ id: `${RUN}-q3e`, capacity: 2, registered: [2], waitlist: [], startInMinutes: 24 * 60 });
  await commit([updateWrite(`venues/${hidden.venueId}`, { status: "HIDDEN" })]);
  const e = await join(JOINERS[0], hidden.sessionId);
  assertEquals([e.status, e.body.error.code], [409, "VENUE_INACTIVE"]);
  for (const x of [own, cancelled, hidden]) assertEquals((await sessionOf(x.sessionId)).waitlistTickets, []);
});

T("Đã WAITLISTED với requestId khác → ALREADY_WAITLISTED; đã REGISTERED → ALREADY_REGISTERED", async () => {
  await seedBase();
  const s = await seedFullSession({
    id: `${RUN}-dup`,
    capacity: 2,
    registered: [2],
    waitlist: [{ partySize: 1, uid: JOINERS[0] }],
    startInMinutes: 24 * 60,
  });
  const a = await join(JOINERS[0], s.sessionId);
  assertEquals([a.status, a.body.error.code], [409, "ALREADY_WAITLISTED"]);
  const b = await join(s.registeredUids[0], s.sessionId);
  assertEquals([b.status, b.body.error.code], [409, "ALREADY_REGISTERED"]);
  await assertWaitlistInvariant(s.sessionId);
});

T("Bản CANCELLED cũ → ghi đè và nhận số vé mới ở cuối hàng (AC 3.3)", async () => {
  await seedBase();
  const s = await seedFullSession({ id: `${RUN}-again`, capacity: 2, registered: [2], waitlist: [1], startInMinutes: 24 * 60 });
  const created = new Date(Date.now() - 3600_000);
  await commit([createWrite(`dropInSessions/${s.sessionId}/registrations/${JOINERS[3]}`, {
    userId: JOINERS[3],
    sessionId: s.sessionId,
    venueId: s.venueId,
    partySize: 1,
    status: "CANCELLED",
    paymentMethod: "MOCK_PAY",
    paymentStatus: null,
    cancelledBy: "USER",
    cancelledAt: created,
    waitlistTicket: 5,
    waitlistEndReason: "LEFT",
    requestId: crypto.randomUUID(),
    createdAt: created,
    updatedAt: created,
  })]);
  const r = await join(JOINERS[3], s.sessionId);
  assertEquals([r.status, r.body.waitlist], [201, { position: 2, total: 2 }]);
  const reg = await regOf(s.sessionId, JOINERS[3]);
  assertEquals([reg.status, reg.waitlistTicket, reg.waitlistEndReason, reg.cancelledAt, reg.cancelledBy], ["WAITLISTED", 2, null, null, null]);
  assertEquals((reg.createdAt as Date).getTime(), created.getTime());
  await assertWaitlistInvariant(s.sessionId);
});

T("partySize 0 hoặc 5 → INVALID_ARGUMENT; thiếu requestId → INVALID_ARGUMENT", async () => {
  await seedBase();
  const s = await seedFullSession({ id: `${RUN}-party`, capacity: 2, registered: [2], waitlist: [], startInMinutes: 24 * 60 });
  for (const partySize of [0, 5]) {
    const r = await join(JOINERS[0], s.sessionId, { partySize });
    assertEquals([r.status, r.body.error.code], [400, "INVALID_ARGUMENT"], `partySize ${partySize}`);
  }
  const bad = await call(JOINERS[0], { sessionId: s.sessionId, partySize: 1, paymentMethod: "MOCK_PAY", joinWaitlist: true });
  assertEquals([bad.status, bad.body.error.code], [400, "INVALID_ARGUMENT"]);
  const notBool = await join(JOINERS[0], s.sessionId, { joinWaitlist: "yes" });
  assertEquals([notBool.status, notBool.body.error.code], [400, "INVALID_ARGUMENT"]);
  await assertWaitlistInvariant(s.sessionId);
});

T("Đăng ký thường vào buổi FULL → SESSION_FULL có waitlistOpen true; còn ≤ 30 phút → false", async () => {
  await seedBase();
  const far = await seedFullSession({ id: `${RUN}-wo1`, capacity: 2, registered: [2], waitlist: [], startInMinutes: 24 * 60 });
  const a = await join(JOINERS[0], far.sessionId, { joinWaitlist: undefined });
  assertEquals([a.status, a.body.error.code, a.body.error.waitlistOpen], [409, "SESSION_FULL", true]);
  const near = await seedFullSession({ id: `${RUN}-wo2`, capacity: 2, registered: [2], waitlist: [], startInMinutes: 30 });
  const b = await join(JOINERS[0], near.sessionId, { joinWaitlist: false });
  assertEquals([b.status, b.body.error.code, b.body.error.waitlistOpen], [409, "SESSION_FULL", false]);
  assertEquals((await sessionOf(far.sessionId)).waitlistTickets, []);
});
