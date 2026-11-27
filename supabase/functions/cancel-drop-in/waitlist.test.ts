// T027, T036 (spec 081 US2, US3): hàng chờ qua `cancel-drop-in` trên Firebase Emulator. Gọi thẳng handler với Firestore
// thật và `requireUser` thật (token không ký của Auth Emulator).
//   - Q6: 5 lượt hủy đồng thời với 10 người chờ, lặp 20 lần (SC-003, FR-008); ưu tiên người chờ (FR-009).
//   - Q7: rời hàng chờ cùng lúc bị đẩy lên, lặp 20 lần (FR-013).
//   - Rời hàng chờ, dryRun, idempotent, thông báo.
//
// Tự bỏ qua khi thiếu FIRESTORE_EMULATOR_HOST hoặc FIREBASE_AUTH_EMULATOR_HOST. Chạy (cần
// `firebase emulators:start --only auth,firestore --project demo-smashnow`):
//   cd supabase/functions
//   FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 \
//   FIREBASE_PROJECT_ID=demo-smashnow deno test --no-check --allow-env --allow-net cancel-drop-in/waitlist.test.ts
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import * as firestore from "../_shared/firestore.ts";
import { commit, createWrite, get } from "../_shared/firestore.ts";
import { createKeyedSerializer, type KeyedSerializer } from "../_shared/dropin-tx.ts";
import { bearer, emulatorToken } from "../_shared/testing.ts";
import { assertWaitlistInvariant, seedFullSession } from "../_shared/waitlist_fixtures.ts";
import { type CancelDeps, defaultDeps, handleCancelDropIn, type Notification } from "./handler.ts";
import { handleRegisterDropIn, type RegisterDeps } from "../register-drop-in/handler.ts";

const ENABLED = !!Deno.env.get("FIRESTORE_EMULATOR_HOST") && !!Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST");
const RUNS = 20;
const RUN = `t081c-${crypto.randomUUID().slice(0, 8)}`;
const EXTRA = Array.from({ length: 4 }, (_, i) => `${RUN}-x${i + 1}`);

const sent: Notification[] = [];
let notifyFails = false;
const deps: CancelDeps = {
  firestore,
  authenticate: defaultDeps().authenticate, // requireUser thật
  now: () => new Date(),
  notify: (n) => {
    if (notifyFails) return Promise.reject(new Error("send-notification down"));
    sent.push(n);
    return Promise.resolve();
  },
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
  await commit(EXTRA.map((uid) => createWrite(`users/${uid}`, { role: "PLAYER", accountStatus: "ACTIVE" })));
}

interface Result {
  status: number;
  // deno-lint-ignore no-explicit-any
  body: any;
}

/** Như app (R2): 503 BUSY thì chờ rồi gửi lại cùng `requestId` (idempotent). */
async function cancel(
  uid: string,
  sessionId: string,
  opts: { requestId?: string; dryRun?: boolean; expectWaitlisted?: boolean; isolate?: KeyedSerializer } = {},
): Promise<Result> {
  const requestId = opts.requestId ?? crypto.randomUUID();
  let r: Result = { status: 0, body: {} };
  for (let i = 0; i < 4; i++) {
    if (i > 0) await new Promise((resolve) => setTimeout(resolve, 500));
    const token = emulatorToken({ sub: uid, user_id: uid, appRole: "PLAYER" });
    const res = await handleCancelDropIn(
      bearer(token, {
        body: JSON.stringify({
          sessionId,
          requestId,
          dryRun: opts.dryRun ?? false,
          ...(opts.expectWaitlisted !== undefined ? { expectWaitlisted: opts.expectWaitlisted } : {}),
        }),
      }),
      { ...deps, serialize: opts.isolate ?? createKeyedSerializer() },
    );
    r = { status: res.status, body: await res.json() };
    if (!(r.status === 503 && r.body.error?.code === "BUSY")) break;
  }
  return r;
}

async function register(uid: string, sessionId: string, extra: Record<string, unknown> = {}): Promise<Result> {
  const requestId = crypto.randomUUID();
  let r: Result = { status: 0, body: {} };
  for (let i = 0; i < 4; i++) {
    if (i > 0) await new Promise((resolve) => setTimeout(resolve, 500));
    const token = emulatorToken({ sub: uid, user_id: uid, appRole: "PLAYER" });
    const res = await handleRegisterDropIn(
      bearer(token, { body: JSON.stringify({ sessionId, partySize: 1, paymentMethod: "MOCK_PAY", requestId, ...extra }) }),
      { ...registerDeps, serialize: createKeyedSerializer() },
    );
    r = { status: res.status, body: await res.json() };
    if (!(r.status === 503 && r.body.error?.code === "BUSY")) break;
  }
  return r;
}

const regOf = async (sid: string, uid: string) => (await get(`dropInSessions/${sid}/registrations/${uid}`))!;
const sessionOf = async (sid: string) => (await get(`dropInSessions/${sid}`))!;
const T = (name: string, fn: () => Promise<void>) =>
  Deno.test({ name, ignore: !ENABLED, sanitizeOps: false, sanitizeResources: false, fn });

// ---------- Q6 ----------

/** Mô phỏng tuần tự: mỗi lượt hủy trả 1 chỗ rồi duyệt hàng chờ theo vé, lượt không vừa bị bỏ qua giữ vị trí. */
function simulate(parties: number[], cancels: number): number[] {
  const queue = parties.map((partySize, i) => ({ ticket: i + 1, partySize }));
  const promoted: number[] = [];
  let free = 0;
  for (let k = 0; k < cancels; k++) {
    free += 1;
    for (const c of [...queue]) {
      if (free <= 0) break;
      if (c.partySize <= free) {
        free -= c.partySize;
        promoted.push(c.ticket);
        queue.splice(queue.indexOf(c), 1);
      }
    }
  }
  return promoted.sort((a, b) => a - b);
}

T(`Q6: 5 lượt hủy đồng thời, 10 người chờ (partySize 1–3) → không vượt sức chứa, không đẩy trùng, đúng thứ tự (×${RUNS})`, async () => {
  await seedBase();
  let overCapacity = 0;
  let doublePromoted = 0;
  let orderViolations = 0;
  for (let round = 1; round <= RUNS; round++) {
    // 10 người chờ với partySize ngẫu nhiên 1–3 (ghi lại để mô phỏng).
    const parties = Array.from({ length: 10 }, () => 1 + Math.floor(Math.random() * 3));
    const s = await seedFullSession({
      id: `${RUN}-q6-${round}`,
      capacity: 10,
      registered: [1, 1, 1, 1, 1, 5],
      waitlist: parties,
      startInMinutes: 24 * 60,
    });
    sent.length = 0;
    const cancellers = s.registeredUids.slice(0, 5);
    // 5 lời gọi song song trên 2 isolate giả lập (như E1/E2 của spec 080): cùng isolate xếp hàng, khác isolate tranh
    // chấp thật trên document buổi. Không dùng 5 isolate riêng vì Firestore Emulator khóa bi quan: nhiều transaction
    // cùng đọc buổi rồi cùng chờ commit sẽ kẹt tới "Transaction lock timeout" (~2 giây) và bị abort cả loạt, nên có
    // vòng hết 8 lần thử (503 BUSY) dù logic đúng (xem research 080 phần "Bổ sung khi cài").
    const iso = [createKeyedSerializer(), createKeyedSerializer()];
    const rs = await Promise.all(cancellers.map((uid, i) => cancel(uid, s.sessionId, { isolate: iso[i % 2] })));
    assertEquals(rs.map((r) => r.status), [200, 200, 200, 200, 200], JSON.stringify(rs.map((r) => r.body.error)));

    const session = await sessionOf(s.sessionId);
    const regs = await firestore.query("registrations", [], { parent: `dropInSessions/${s.sessionId}` });
    const held = regs
      .filter((d) => d.data.status === "REGISTERED")
      .reduce((n, d) => n + Number(d.data.partySize), 0);
    if (Number(session.registeredCount) > 10 || held > 10) overCapacity++;
    // Không ai được thông báo "được nhận" hai lần (thông báo chỉ gửi sau commit thành công).
    const perUid = new Map<string, number>();
    for (const uid of s.waitlistUids) {
      const inbox = await firestore.query("notifications", [], { parent: `users/${uid}` });
      for (const n of inbox.filter(n => n.data.type === "DROPIN_SPOT_OPENED" && (n.data.data as any).sessionId === s.sessionId)) {
        perUid.set(uid, (perUid.get(uid) ?? 0) + 1);
      }
    }
    doublePromoted += [...perUid.values()].filter((c) => c > 1).length;
    const promoted = regs
      .filter((d) => d.data.promotedAt instanceof Date)
      .map((d) => Number(d.data.waitlistTicket))
      .sort((a, b) => a - b);
    const expected = simulate(parties, 5);
    if (JSON.stringify(promoted) !== JSON.stringify(expected)) orderViolations++;
    assertEquals([...perUid.values()].reduce((a, b) => a + b, 0), expected.length, "mỗi người được đẩy lên nhận đúng một inbox");
    assertEquals(sent, [], "helper owns delivery; no duplicate callback");
    assertEquals(rs.reduce((n, r) => n + Number(r.body.promotedCount), 0), expected.length, "promotedCount cộng lại");
    await assertWaitlistInvariant(s.sessionId);
  }
  console.log(`runs=${RUNS} overCapacity=${overCapacity} doublePromoted=${doublePromoted} orderViolations=${orderViolations}`);
  assertEquals([overCapacity, doublePromoted, orderViolations], [0, 0, 0]);
});

T(`FR-009: 1 lượt hủy cùng lúc 3 người chưa chờ đăng ký thường → người chờ A luôn được đẩy lên, cả 3 nhận SESSION_FULL (×${RUNS})`, async () => {
  await seedBase();
  for (let round = 1; round <= RUNS; round++) {
    const s = await seedFullSession({
      id: `${RUN}-fr9-${round}`,
      capacity: 4,
      registered: [1, 1, 1, 1],
      waitlist: [1],
      startInMinutes: 24 * 60,
    });
    const cancelling = cancel(s.registeredUids[0], s.sessionId);
    const joiners = await Promise.all(
      EXTRA.slice(0, 3).map(async (uid) => {
        await new Promise((r) => setTimeout(r, Math.random() * 300));
        return register(uid, s.sessionId);
      }),
    );
    assertEquals((await cancelling).status, 200);
    assertEquals(joiners.map((r) => [r.status, r.body.error?.code]), [
      [409, "SESSION_FULL"],
      [409, "SESSION_FULL"],
      [409, "SESSION_FULL"],
    ], `vòng ${round}`);
    assertEquals((await regOf(s.sessionId, s.waitlistUids[0])).status, "REGISTERED", `vòng ${round}`);
    const session = await sessionOf(s.sessionId);
    assertEquals([session.registeredCount, session.status, session.waitlistTickets], [4, "FULL", []]);
    await assertWaitlistInvariant(s.sessionId);
  }
});

T("Hủy có đẩy lên: response có promotedCount (không lộ danh tính), gửi DROPIN_SPOT_OPENED sau commit; lỗi gửi bị bỏ qua", async () => {
  await seedBase();
  const u = `${RUN}-ov-u`;
  const a = await seedFullSession({ id: `${RUN}-not-a`, capacity: 2, registered: [1, 1], waitlist: [{ partySize: 1, uid: u }], startInMinutes: 300 });
  const b = await seedFullSession({ id: `${RUN}-not-b`, capacity: 2, registered: [1, 1], waitlist: [1, { partySize: 1, uid: u }], startInMinutes: 300 });
  sent.length = 0;
  const r = await cancel(a.registeredUids[0], a.sessionId);
  assertEquals(r.status, 200);
  assertEquals(r.body.promotedCount, 1);
  assert(!JSON.stringify(r.body).includes(u), "không lộ danh tính người được đẩy lên");
  assertEquals(r.body.session, { registeredCount: 2, capacity: 2, status: "FULL" });
  assertEquals(sent, []);
  const inbox = await firestore.query("notifications", [], { parent: `users/${u}` });
  const notification = inbox.find(n => n.data.type === "DROPIN_SPOT_OPENED" && (n.data.data as any).sessionId === a.sessionId)!;
  assertEquals(notification.data.data, { sessionId: a.sessionId, autoLeftSessionIds: [b.sessionId] });
  const jobs = await firestore.query("notificationDeliveries", [{ field: "recipientUid", op: "EQUAL", value: u }]);
  assertEquals(jobs.length, 1);
  assertEquals((await regOf(b.sessionId, u)).waitlistEndReason, "AUTO_LEFT_OVERLAP");

  // Gửi thông báo lỗi: hủy và đẩy lên vẫn thành công.
  const c = await seedFullSession({ id: `${RUN}-not-c`, capacity: 2, registered: [1, 1], waitlist: [1], startInMinutes: 300 });
  notifyFails = true;
  try {
    const r2 = await cancel(c.registeredUids[0], c.sessionId);
    assertEquals([r2.status, r2.body.promotedCount], [200, 1]);
  } finally {
    notifyFails = false;
  }
  assertEquals((await regOf(c.sessionId, c.waitlistUids[0])).status, "REGISTERED");
  // Hủy khi không có ai chờ: promotedCount 0, không thông báo.
  const d = await seedFullSession({ id: `${RUN}-not-d`, capacity: 2, registered: [1, 1], waitlist: [], startInMinutes: 300 });
  sent.length = 0;
  const r3 = await cancel(d.registeredUids[0], d.sessionId);
  assertEquals([r3.status, r3.body.promotedCount, sent], [200, 0, []]);
  for (const x of [a, b, c, d]) await assertWaitlistInvariant(x.sessionId);
});

// ---------- US3: rời hàng chờ ----------

T("Rời hàng chờ (expectWaitlisted) → CANCELLED, USER, LEFT; 0 đ, paymentStatus vẫn null; vị trí người sau tăng; không thông báo", async () => {
  await seedBase();
  const s = await seedFullSession({ id: `${RUN}-leave`, capacity: 2, registered: [2], waitlist: [1, 2, 1], startInMinutes: 24 * 60 });
  sent.length = 0;
  const r = await cancel(s.waitlistUids[1], s.sessionId, { expectWaitlisted: true });
  assertEquals(r.status, 200, JSON.stringify(r.body));
  assertEquals(r.body.quote, { refundAmount: 0, reason: "WAITLIST_LEAVE" });
  assertEquals([r.body.registration.status, r.body.registration.waitlistEndReason], ["CANCELLED", "LEFT"]);
  const reg = await regOf(s.sessionId, s.waitlistUids[1]);
  assertEquals(
    [reg.status, reg.cancelledBy, reg.waitlistEndReason, reg.paymentStatus, reg.refundAmount],
    ["CANCELLED", "USER", "LEFT", null, 0],
  );
  const session = await sessionOf(s.sessionId);
  assertEquals([session.registeredCount, session.waitlistTickets, session.waitlistCount], [2, [1, 3], 2]);
  // Người đứng sau (vé 3) từ vị trí 3 lên vị trí 2.
  assertEquals((session.waitlistTickets as number[]).indexOf(3) + 1, 2);
  assertEquals(sent, []);
  assertEquals((await firestore.query("notifications", [], { parent: `users/${s.waitlistUids[1]}` })).length, 0);
  await assertWaitlistInvariant(s.sessionId);
});

T("T021 self-cancellation never creates inbox/job for the cancelling player", async () => {
  await seedBase();
  const s = await seedFullSession({ id: `${RUN}-t021-self`, capacity: 2, registered: [2], waitlist: [], startInMinutes: 24 * 60 });
  const uid = s.registeredUids[0];
  assertEquals((await cancel(uid, s.sessionId)).status, 200);
  assertEquals((await firestore.query("notifications", [], { parent: `users/${uid}` })).length, 0);
  assertEquals((await firestore.query("notificationDeliveries", [{ field: "recipientUid", op: "EQUAL", value: uid }])).length, 0);
});

T("Rời hàng chờ: dryRun → quote 0 đ WAITLIST_LEAVE và không ghi gì", async () => {
  await seedBase();
  const s = await seedFullSession({ id: `${RUN}-dry`, capacity: 2, registered: [2], waitlist: [1, 1], startInMinutes: 24 * 60 });
  const before = { reg: await regOf(s.sessionId, s.waitlistUids[0]), session: await sessionOf(s.sessionId) };
  const r = await cancel(s.waitlistUids[0], s.sessionId, { dryRun: true, expectWaitlisted: true });
  assertEquals([r.status, r.body.quote], [200, { refundAmount: 0, reason: "WAITLIST_LEAVE" }]);
  assertEquals(r.body.registration.status, "WAITLISTED");
  assertEquals(await regOf(s.sessionId, s.waitlistUids[0]), before.reg);
  assertEquals(await sessionOf(s.sessionId), before.session);
});

T("Rời hàng chờ: token người khác (không có lượt) → 404 NOT_FOUND, hàng chờ không đổi (AC 3.4)", async () => {
  await seedBase();
  const s = await seedFullSession({ id: `${RUN}-other`, capacity: 2, registered: [2], waitlist: [1], startInMinutes: 24 * 60 });
  const r = await cancel(EXTRA[0], s.sessionId, { expectWaitlisted: true });
  assertEquals([r.status, r.body.error.code], [404, "NOT_FOUND"]);
  assertEquals((await regOf(s.sessionId, s.waitlistUids[0])).status, "WAITLISTED");
  await assertWaitlistInvariant(s.sessionId);
});

T("Rời hàng chờ: cùng cancelRequestId gửi lại 3 lần song song → kết quả cũ, vé bỏ đúng một lần", async () => {
  await seedBase();
  const s = await seedFullSession({ id: `${RUN}-idem`, capacity: 2, registered: [2], waitlist: [1, 1, 1], startInMinutes: 24 * 60 });
  const requestId = crypto.randomUUID();
  const iso = [createKeyedSerializer(), createKeyedSerializer()];
  const rs = await Promise.all([0, 1, 2].map((i) =>
    cancel(s.waitlistUids[0], s.sessionId, { requestId, expectWaitlisted: true, isolate: iso[i % 2] })
  ));
  assertEquals(rs.map((r) => r.status), [200, 200, 200]);
  for (const r of rs) assertEquals(r.body.quote, { refundAmount: 0, reason: "WAITLIST_LEAVE" });
  assertEquals((await sessionOf(s.sessionId)).waitlistTickets, [2, 3]);
  // Gửi lại sau khi đã xong.
  const again = await cancel(s.waitlistUids[0], s.sessionId, { requestId, expectWaitlisted: true });
  assertEquals([again.status, again.body.quote.reason], [200, "WAITLIST_LEAVE"]);
  assertEquals((await sessionOf(s.sessionId)).waitlistTickets, [2, 3]);
  await assertWaitlistInvariant(s.sessionId);
});

T("Rời rồi vào lại → số vé mới ở cuối hàng (AC 3.3)", async () => {
  await seedBase();
  const s = await seedFullSession({ id: `${RUN}-rejoin`, capacity: 2, registered: [2], waitlist: [1, 1], startInMinutes: 24 * 60 });
  assertEquals((await cancel(s.waitlistUids[0], s.sessionId, { expectWaitlisted: true })).status, 200);
  const j = await register(s.waitlistUids[0], s.sessionId, { joinWaitlist: true });
  assertEquals([j.status, j.body.waitlist], [201, { position: 2, total: 2 }]);
  const session = await sessionOf(s.sessionId);
  assertEquals([session.waitlistTickets, session.waitlistNextTicket], [[2, 3], 4]);
  assertEquals((await regOf(s.sessionId, s.waitlistUids[0])).waitlistTicket, 3);
  await assertWaitlistInvariant(s.sessionId);
});

T("expectWaitlisted nhưng lượt đã REGISTERED (đã được đẩy lên) → 409 ALREADY_PROMOTED, không hủy nhầm", async () => {
  await seedBase();
  const s = await seedFullSession({ id: `${RUN}-promoted`, capacity: 2, registered: [1, 1], waitlist: [1], startInMinutes: 24 * 60 });
  assertEquals((await cancel(s.registeredUids[0], s.sessionId)).body.promotedCount, 1);
  const r = await cancel(s.waitlistUids[0], s.sessionId, { expectWaitlisted: true });
  assertEquals([r.status, r.body.error.code], [409, "ALREADY_PROMOTED"]);
  const reg = await regOf(s.sessionId, s.waitlistUids[0]);
  assertEquals([reg.status, reg.cancelledAt, reg.paymentStatus], ["REGISTERED", null, "PAID"]);
  assertEquals((await sessionOf(s.sessionId)).registeredCount, 2);
  await assertWaitlistInvariant(s.sessionId);
});

T(`Q7: rời hàng chờ cùng lúc bị đẩy lên (song song ×${RUNS}) → đúng một kết quả: LEFT hoặc REGISTERED + ALREADY_PROMOTED`, async () => {
  await seedBase();
  let left = 0;
  let promoted = 0;
  let overCapacity = 0;
  let doublePromoted = 0; // vừa rời thành công vừa được đẩy lên (hai kết quả cùng lúc)
  for (let round = 1; round <= RUNS; round++) {
    const s = await seedFullSession({ id: `${RUN}-q7-${round}`, capacity: 2, registered: [1, 1], waitlist: [1], startInMinutes: 24 * 60 });
    const [leaving, cancelling] = await Promise.all([
      cancel(s.waitlistUids[0], s.sessionId, { expectWaitlisted: true }),
      cancel(s.registeredUids[0], s.sessionId),
    ]);
    assertEquals(cancelling.status, 200, `vòng ${round}`);
    const reg = await regOf(s.sessionId, s.waitlistUids[0]);
    const session = await sessionOf(s.sessionId);
    if (Number(session.registeredCount) > 2) overCapacity++;
    if (leaving.status === 200 && (reg.status !== "CANCELLED" || reg.promotedAt != null)) doublePromoted++;
    if (leaving.status !== 200 && reg.status !== "REGISTERED") doublePromoted++;
    if (leaving.status === 200) {
      left++;
      assertEquals([reg.status, reg.waitlistEndReason, reg.paymentStatus], ["CANCELLED", "LEFT", null], `vòng ${round}`);
      assertEquals([session.registeredCount, session.status], [1, "OPEN"], `vòng ${round}`);
    } else {
      promoted++;
      assertEquals([leaving.status, leaving.body.error?.code], [409, "ALREADY_PROMOTED"], `vòng ${round}`);
      assertEquals([reg.status, reg.cancelledAt], ["REGISTERED", null], `vòng ${round}: lượt được đẩy lên không bị hủy nhầm`);
      assertEquals([session.registeredCount, session.status], [2, "FULL"], `vòng ${round}`);
    }
    await assertWaitlistInvariant(s.sessionId);
  }
  console.log(`runs=${RUNS} overCapacity=${overCapacity} doublePromoted=${doublePromoted} orderViolations=0 (Q7: left=${left} promoted=${promoted})`);
  assertEquals([left + promoted, overCapacity, doublePromoted], [RUNS, 0, 0]);
});
