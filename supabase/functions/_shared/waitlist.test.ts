// T026, T043 (spec 081 US2, US4): `promoteFromWaitlist`, `leaveWaitlistInTx`, `closeWaitlistInTx` gọi trực tiếp trong
// `runTransaction` trên Firebase Emulator.
//
// Tự bỏ qua khi thiếu FIRESTORE_EMULATOR_HOST. Chạy (cần `firebase emulators:start --only auth,firestore
// --project demo-smashnow`):
//   cd supabase/functions
//   FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 \
//   FIREBASE_PROJECT_ID=demo-smashnow deno test --no-check --allow-env --allow-net _shared/waitlist.test.ts
import { assert, assertEquals, assertMatch, assertRejects } from "jsr:@std/assert@1.0.19";
import * as firestore from "./firestore.ts";
import { get, updateWrite } from "./firestore.ts";
import { registrationPath, type RegistrationDoc, type SessionDoc, sessionPath } from "./dropin.ts";
import { closeWaitlistInTx, leaveWaitlistInTx, promoteFromWaitlist } from "./waitlist.ts";
import { assertWaitlistInvariant, MIN, seedFullSession } from "./waitlist_fixtures.ts";

const ENABLED = !!Deno.env.get("FIRESTORE_EMULATOR_HOST");
const RUN = `t081w-${crypto.randomUUID().slice(0, 8)}`;

const T = (name: string, fn: () => Promise<void>) =>
  Deno.test({ name, ignore: !ENABLED, sanitizeOps: false, sanitizeResources: false, fn });

const regOf = async (sid: string, uid: string) => (await get(registrationPath(sid, uid)))! as RegistrationDoc;
const sessionOf = async (sid: string) => (await get(sessionPath(sid)))! as SessionDoc;

/**
 * Giả lập người đăng ký `uid` hủy: trả chỗ rồi gọi `promoteFromWaitlist` trong cùng transaction, ghi buổi theo
 * `sessionPatch` (đúng như `cancelRegistrationInTx` làm).
 */
async function cancelAndPromote(sid: string, uid: string, now: Date) {
  return await firestore.runTransaction(async (tx) => {
    const reg = (await tx.get(registrationPath(sid, uid)))!;
    const session = (await tx.get(sessionPath(sid)))! as SessionDoc;
    session.id = sid;
    session.registeredCount = Number(session.registeredCount) - Number(reg.partySize);
    if (session.status === "FULL") session.status = "OPEN";
    const out = await promoteFromWaitlist(tx, session, now);
    tx.write(updateWrite(registrationPath(sid, uid), { status: "CANCELLED", qrToken: null, ticketCode: null }));
    tx.write(updateWrite(sessionPath(sid), { ...out.sessionPatch, updatedAt: now }));
    return { promoted: out.promoted };
  });
}

T("Q4: chờ A(2), B(1); trả 1 chỗ → B lên, A giữ vị trí 1; trả tiếp 2 chỗ → A lên; registeredCount luôn 10", async () => {
  const now = new Date();
  const s = await seedFullSession({ id: `${RUN}-q4`, capacity: 10, registered: [1, 1, 2, 2, 2, 2], waitlist: [2, 1], startInMinutes: 120, now });
  const [a, b] = s.waitlistUids;
  await assertWaitlistInvariant(s.sessionId);

  const r1 = await cancelAndPromote(s.sessionId, s.registeredUids[0], now);
  assertEquals(r1.promoted.map((p) => p.uid), [b]);
  assertEquals((await regOf(s.sessionId, b)).status, "REGISTERED");
  const ra = await regOf(s.sessionId, a);
  assertEquals([ra.status, ra.waitlistTicket], ["WAITLISTED", 1]);
  let session = await sessionOf(s.sessionId);
  assertEquals([session.registeredCount, session.status, session.waitlistTickets, session.waitlistCount], [10, "FULL", [1], 1]);
  await assertWaitlistInvariant(s.sessionId);

  const r2 = await cancelAndPromote(s.sessionId, s.registeredUids[2], now);
  assertEquals(r2.promoted.map((p) => p.uid), [a]);
  session = await sessionOf(s.sessionId);
  assertEquals([session.registeredCount, session.status, session.waitlistTickets, session.waitlistCount], [10, "FULL", [], 0]);
  await assertWaitlistInvariant(s.sessionId);
});

T("Q5: MOCK_PAY → PAID, AT_VENUE → UNPAID; có qrToken, ticketCode, promotedAt = registeredAt = serverNow; amount theo giá lúc vào", async () => {
  const now = new Date();
  const s = await seedFullSession({
    id: `${RUN}-q5`,
    capacity: 4,
    registered: [2, 2],
    pricePerPerson: 90000, // giá buổi đã đổi so với lúc vào hàng chờ
    waitlist: [{ partySize: 2, paymentMethod: "MOCK_PAY", pricePerPerson: 50000 }, { partySize: 2, paymentMethod: "AT_VENUE", pricePerPerson: 50000 }],
    startInMinutes: 90,
    now,
  });
  const serverNow = new Date(now.getTime() + 5 * MIN);
  await cancelAndPromote(s.sessionId, s.registeredUids[0], serverNow);
  await cancelAndPromote(s.sessionId, s.registeredUids[1], serverNow);
  const [paid, unpaid] = await Promise.all(s.waitlistUids.map((u) => regOf(s.sessionId, u)));
  assertEquals([paid.status, paid.paymentStatus, paid.amount, paid.pricePerPerson], ["REGISTERED", "PAID", 100000, 50000]);
  assertEquals([unpaid.status, unpaid.paymentStatus, unpaid.amount], ["REGISTERED", "UNPAID", 100000]);
  for (const r of [paid, unpaid]) {
    assertEquals(String(r.qrToken).length, 43);
    assertMatch(String(r.ticketCode), /^DI-[A-HJ-NP-Z2-9]{8}$/);
    assertEquals((r.promotedAt as Date).getTime(), serverNow.getTime());
    assertEquals((r.registeredAt as Date).getTime(), serverNow.getTime());
  }
  await assertWaitlistInvariant(s.sessionId);
});

T("Q8: người chờ cả W1 và W2 (trùng giờ), được đẩy lên W1 → lượt W2 AUTO_LEFT_OVERLAP, vé bị bỏ khỏi W2", async () => {
  const now = new Date();
  const u = `${RUN}-q8-u`;
  const w1 = await seedFullSession({ id: `${RUN}-q8a`, capacity: 2, registered: [2], waitlist: [{ partySize: 1, uid: u }], startInMinutes: 300, now });
  const w2 = await seedFullSession({
    id: `${RUN}-q8b`,
    capacity: 2,
    registered: [2],
    waitlist: [1, { partySize: 1, uid: u }],
    startInMinutes: 300,
    now,
  });
  // Buổi thứ ba lệch giờ (bắt đầu sau khi W1 kết thúc) thì không bị tự rời.
  const w3 = await seedFullSession({ id: `${RUN}-q8c`, capacity: 2, registered: [2], waitlist: [{ partySize: 1, uid: u }], startInMinutes: 300 + 130, now });

  const out = await cancelAndPromote(w1.sessionId, w1.registeredUids[0], now);
  assertEquals(out.promoted, [{ uid: u, autoLeftSessionIds: [w2.sessionId] }]);
  const left = await regOf(w2.sessionId, u);
  assertEquals([left.status, left.cancelledBy, left.waitlistEndReason], ["CANCELLED", "SYSTEM", "AUTO_LEFT_OVERLAP"]);
  assertEquals(left.paymentStatus, null);
  const s2 = await sessionOf(w2.sessionId);
  assertEquals([s2.waitlistTickets, s2.waitlistCount], [[1], 1]);
  assertEquals((await regOf(w3.sessionId, u)).status, "WAITLISTED");
  for (const id of [w1.sessionId, w2.sessionId, w3.sessionId]) await assertWaitlistInvariant(id);
});

T("Q8b: hai người được đẩy lên cùng tự rời một buổi X → một lần ghi buổi X, vé bỏ đúng cả hai", async () => {
  const now = new Date();
  const [u1, u2] = [`${RUN}-q8b-u1`, `${RUN}-q8b-u2`];
  const w1 = await seedFullSession({
    id: `${RUN}-q8d`,
    capacity: 2,
    registered: [2],
    waitlist: [{ partySize: 1, uid: u1 }, { partySize: 1, uid: u2 }],
    startInMinutes: 300,
    now,
  });
  const x = await seedFullSession({
    id: `${RUN}-q8e`,
    capacity: 2,
    registered: [2],
    waitlist: [{ partySize: 1, uid: u1 }, 1, { partySize: 1, uid: u2 }],
    startInMinutes: 300,
    now,
  });
  const out = await cancelAndPromote(w1.sessionId, w1.registeredUids[0], now);
  assertEquals(out.promoted.map((p) => p.uid), [u1, u2]);
  const sx = await sessionOf(x.sessionId);
  assertEquals([sx.waitlistTickets, sx.waitlistCount], [[2], 1]);
  await assertWaitlistInvariant(x.sessionId);
  await assertWaitlistInvariant(w1.sessionId);
});

T("Q9: còn 25 phút trước giờ bắt đầu, có người chờ → không đẩy ai; chỗ trống giữ cho đăng ký thường (OPEN)", async () => {
  const now = new Date();
  const s = await seedFullSession({ id: `${RUN}-q9`, capacity: 4, registered: [2, 2], waitlist: [1, 1], startInMinutes: 25, now });
  const out = await cancelAndPromote(s.sessionId, s.registeredUids[0], now);
  assertEquals(out.promoted, []);
  const session = await sessionOf(s.sessionId);
  assertEquals([session.registeredCount, session.status, session.waitlistTickets], [2, "OPEN", [1, 2]]);
  assertEquals((await regOf(s.sessionId, s.waitlistUids[0])).status, "WAITLISTED");
});

T("Q9b: còn đúng 30 phút → hàng chờ đã đóng; còn 31 phút → vẫn đẩy lên", async () => {
  const now = new Date();
  const at30 = await seedFullSession({ id: `${RUN}-q9b30`, capacity: 2, registered: [1, 1], waitlist: [1], startInMinutes: 30, now });
  assertEquals((await cancelAndPromote(at30.sessionId, at30.registeredUids[0], now)).promoted, []);
  const at31 = await seedFullSession({ id: `${RUN}-q9b31`, capacity: 2, registered: [1, 1], waitlist: [1], startInMinutes: 31, now });
  assertEquals((await cancelAndPromote(at31.sessionId, at31.registeredUids[0], now)).promoted.length, 1);
});

T("Q13: tăng capacity thêm 3 rồi gọi promoteFromWaitlist → đẩy lên tối đa 3 chỗ theo thứ tự vé", async () => {
  const now = new Date();
  const s = await seedFullSession({ id: `${RUN}-q13`, capacity: 10, registered: [4, 3, 3], waitlist: [1, 1, 1, 1, 1], startInMinutes: 120, now });
  const out = await firestore.runTransaction(async (tx) => {
    const session = (await tx.get(sessionPath(s.sessionId)))! as SessionDoc;
    session.id = s.sessionId;
    session.capacity = 13;
    const r = await promoteFromWaitlist(tx, session, now);
    tx.write(updateWrite(sessionPath(s.sessionId), { capacity: 13, ...r.sessionPatch, updatedAt: now }));
    return { promoted: r.promoted };
  });
  assertEquals(out.promoted.map((p) => p.uid), s.waitlistUids.slice(0, 3));
  const session = await sessionOf(s.sessionId);
  assertEquals([session.registeredCount, session.status, session.waitlistTickets], [13, "FULL", [4, 5]]);
  await assertWaitlistInvariant(s.sessionId);
});

T("Hàng chờ có người nhưng không ai vừa → promoted = [], buổi OPEN, vị trí giữ nguyên (AC 2.8)", async () => {
  const now = new Date();
  const s = await seedFullSession({ id: `${RUN}-nofit`, capacity: 4, registered: [1, 1, 1, 1], waitlist: [3, 2], startInMinutes: 120, now });
  const out = await cancelAndPromote(s.sessionId, s.registeredUids[0], now);
  assertEquals(out.promoted, []);
  const session = await sessionOf(s.sessionId);
  assertEquals([session.registeredCount, session.status, session.waitlistTickets], [3, "OPEN", [1, 2]]);
  await assertWaitlistInvariant(s.sessionId);
});

T("Giảm capacity (không gọi promoteFromWaitlist) → waitlistTickets và vị trí giữ nguyên", async () => {
  const now = new Date();
  const s = await seedFullSession({ id: `${RUN}-shrink`, capacity: 4, registered: [2, 2], waitlist: [1, 1], startInMinutes: 120, now });
  await firestore.commit([updateWrite(sessionPath(s.sessionId), { capacity: 2 })]);
  const session = await sessionOf(s.sessionId);
  assertEquals([session.waitlistTickets, session.waitlistCount], [[1, 2], 2]);
  assertEquals((await regOf(s.sessionId, s.waitlistUids[0])).waitlistTicket, 1);
});

T("leaveWaitlistInTx(LEFT, SYSTEM) (delete-account): lượt CANCELLED, vé bị bỏ, người sau lên vị trí, không phát sinh tiền", async () => {
  const now = new Date();
  const s = await seedFullSession({ id: `${RUN}-leave`, capacity: 2, registered: [2], waitlist: [1, 1, 1], startInMinutes: 120, now });
  await firestore.runTransaction(async (tx) => {
    await leaveWaitlistInTx(tx, s.sessionId, s.waitlistUids[1], "LEFT", "SYSTEM");
    return null;
  });
  const reg = await regOf(s.sessionId, s.waitlistUids[1]);
  assertEquals([reg.status, reg.cancelledBy, reg.waitlistEndReason], ["CANCELLED", "SYSTEM", "LEFT"]);
  assertEquals([reg.paymentStatus, reg.refundAmount], [null, null]);
  assert(reg.cancelledAt instanceof Date);
  const session = await sessionOf(s.sessionId);
  assertEquals([session.waitlistTickets, session.waitlistCount], [[1, 3], 2]);
  await assertWaitlistInvariant(s.sessionId);
});

T("leaveWaitlistInTx trên lượt đã REGISTERED → ALREADY_PROMOTED, không ghi gì; lượt không có → bỏ qua", async () => {
  const now = new Date();
  const s = await seedFullSession({ id: `${RUN}-leave2`, capacity: 2, registered: [2], waitlist: [1], startInMinutes: 120, now });
  const err = await assertRejects(() =>
    firestore.runTransaction(async (tx) => {
      await leaveWaitlistInTx(tx, s.sessionId, s.registeredUids[0], "LEFT", "USER");
      return null;
    })
  );
  assertEquals((err as { code?: string }).code, "ALREADY_PROMOTED");
  assertEquals((await regOf(s.sessionId, s.registeredUids[0])).status, "REGISTERED");
  await firestore.runTransaction(async (tx) => {
    await leaveWaitlistInTx(tx, s.sessionId, `${RUN}-nobody`, "LEFT", "USER");
    return null;
  });
  await assertWaitlistInvariant(s.sessionId);
});

T("Q11: closeWaitlistInTx(SESSION_CANCELLED) → lượt chờ CANCELLED, cancelledBy OWNER; không có tiền (SC-005)", async () => {
  const now = new Date();
  const s = await seedFullSession({ id: `${RUN}-q11`, capacity: 2, registered: [2], waitlist: [1, 2, 1], startInMinutes: 120, now });
  const out = await firestore.runTransaction(async (tx) => {
    const session = (await tx.get(sessionPath(s.sessionId)))! as SessionDoc;
    session.id = s.sessionId;
    return await closeWaitlistInTx(tx, session, "SESSION_CANCELLED");
  });
  assertEquals([...out.closed].sort(), [...s.waitlistUids].sort());
  for (const uid of s.waitlistUids) {
    const r = await regOf(s.sessionId, uid);
    assertEquals([r.status, r.cancelledBy, r.waitlistEndReason, r.paymentStatus], ["CANCELLED", "OWNER", "SESSION_CANCELLED", null]);
  }
  const session = await sessionOf(s.sessionId);
  assertEquals([session.waitlistTickets, session.waitlistCount], [[], 0]);
  assertEquals((await regOf(s.sessionId, s.registeredUids[0])).status, "REGISTERED");
  await assertWaitlistInvariant(s.sessionId);
});

T("closeWaitlistInTx(CLOSED) → cancelledBy SYSTEM; hàng chờ trống thì closed = []", async () => {
  const now = new Date();
  const s = await seedFullSession({ id: `${RUN}-close`, capacity: 2, registered: [2], waitlist: [1], startInMinutes: 20, now });
  const close = () =>
    firestore.runTransaction(async (tx) => {
      const session = (await tx.get(sessionPath(s.sessionId)))! as SessionDoc;
      session.id = s.sessionId;
      return await closeWaitlistInTx(tx, session, "CLOSED");
    });
  assertEquals((await close()).closed, s.waitlistUids);
  const r = await regOf(s.sessionId, s.waitlistUids[0]);
  assertEquals([r.cancelledBy, r.waitlistEndReason], ["SYSTEM", "CLOSED"]);
  assertEquals((await close()).closed, []);
});
