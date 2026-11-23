// T042 (spec 081 US4): `dropin-close` (pg_cron mỗi phút) trên Firebase Emulator: đóng hàng chờ ở mốc T−30 phút và
// chuyển buổi đã qua `endAt` sang COMPLETED.
//
// Tự bỏ qua khi thiếu FIRESTORE_EMULATOR_HOST. Chạy (cần `firebase emulators:start --only auth,firestore
// --project demo-smashnow`):
//   cd supabase/functions
//   FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 \
//   FIREBASE_PROJECT_ID=demo-smashnow deno test --no-check --allow-env --allow-net dropin-close/index.test.ts
//
// Emulator có thể đang dùng chung (và còn buổi của các test khác): `firestore.query` được bọc để chỉ thấy buổi có
// tiền tố của lần chạy này, nên số đếm trong response chính xác.
import { assertEquals } from "jsr:@std/assert@1.0.19";
import * as firestore from "../_shared/firestore.ts";
import { get, updateWrite } from "../_shared/firestore.ts";
import { assertWaitlistInvariant, seedFullSession } from "../_shared/waitlist_fixtures.ts";
import type { WaitlistNotification } from "../_shared/dropin-notify.ts";
import { handleDropInClose } from "./handler.ts";

const ENABLED = !!Deno.env.get("FIRESTORE_EMULATOR_HOST");
const RUN = `t081d-${crypto.randomUUID().slice(0, 8)}`;
const SECRET = "test-cron-secret";

const T = (name: string, fn: () => Promise<void>) =>
  Deno.test({ name, ignore: !ENABLED, sanitizeOps: false, sanitizeResources: false, fn });

const scoped: Pick<typeof firestore, "query" | "runTransaction"> = {
  query: async (c, f, o) => (await firestore.query(c, f, o)).filter((d) => d.id.startsWith(RUN)),
  runTransaction: firestore.runTransaction,
};

const sent: WaitlistNotification[] = [];
let notifyFails = false;
const run = (now: Date, headers: Record<string, string> = { "x-cron-secret": SECRET }, method = "POST") =>
  handleDropInClose(new Request("http://localhost/functions/v1/dropin-close", { method, headers }), {
    firestore: scoped,
    now: () => now,
    cronSecret: SECRET,
    notify: (n) => {
      if (notifyFails) return Promise.reject(new Error("send-notification down"));
      sent.push(n);
      return Promise.resolve();
    },
  });

const regOf = async (sid: string, uid: string) => (await get(`dropInSessions/${sid}/registrations/${uid}`))!;
const sessionOf = async (sid: string) => (await get(`dropInSessions/${sid}`))!;

T("Q10: buổi tới mốc T−30 có 2 lượt chờ → cả 2 CLOSED, không tiền, 2 thông báo; buổi qua endAt → COMPLETED; chạy lại không đổi gì", async () => {
  const now = new Date();
  const a = await seedFullSession({ id: `${RUN}-a`, capacity: 4, registered: [2, 2], waitlist: [1, 2], startInMinutes: 20, now });
  const b = await seedFullSession({ id: `${RUN}-b`, capacity: 4, registered: [2, 2], waitlist: [1], startInMinutes: 120, now });
  const c = await seedFullSession({ id: `${RUN}-c`, capacity: 4, registered: [1], waitlist: [], startInMinutes: -200, durationMinutes: 120, now });
  const d = await seedFullSession({ id: `${RUN}-d`, capacity: 4, registered: [1], waitlist: [], startInMinutes: 60, now });
  sent.length = 0;

  const res = await run(now);
  assertEquals(res.status, 200);
  assertEquals(await res.json(), { closedSessions: 1, closedEntries: 2, completedSessions: 1 });

  for (const uid of a.waitlistUids) {
    const r = await regOf(a.sessionId, uid);
    assertEquals([r.status, r.cancelledBy, r.waitlistEndReason, r.paymentStatus, r.refundAmount], ["CANCELLED", "SYSTEM", "CLOSED", null, null]);
  }
  const sa = await sessionOf(a.sessionId);
  assertEquals([sa.waitlistTickets, sa.waitlistCount, sa.status], [[], 0, "FULL"]);
  assertEquals(sent, []); // No duplicate post-commit callback.
  for (const uid of a.waitlistUids) {
    const inbox = await firestore.query("notifications", [], { parent: `users/${uid}` });
    assertEquals(inbox.filter(n => n.data.type === "DROPIN_WAITLIST_CLOSED" && (n.data.data as any).sessionId === a.sessionId).length, 1);
    const jobs = await firestore.query("notificationDeliveries", [{ field: "recipientUid", op: "EQUAL", value: uid }]);
    assertEquals(jobs.length, 1);
  }
  // Buổi còn xa không bị đụng tới; buổi đã qua endAt thành COMPLETED.
  assertEquals((await sessionOf(b.sessionId)).waitlistTickets, [1]);
  assertEquals((await regOf(b.sessionId, b.waitlistUids[0])).status, "WAITLISTED");
  assertEquals((await sessionOf(c.sessionId)).status, "COMPLETED");
  assertEquals((await sessionOf(d.sessionId)).status, "OPEN");
  await assertWaitlistInvariant(a.sessionId);
  await assertWaitlistInvariant(b.sessionId);

  // Idempotent: chạy lại không đổi gì và không gửi thêm.
  const sentBefore = sent.length;
  const again = await run(now);
  assertEquals(await again.json(), { closedSessions: 0, closedEntries: 0, completedSessions: 0 });
  assertEquals(sent.length, sentBefore);
});

T("Mốc đúng 30 phút: còn 30 phút → đóng; còn 31 phút → chưa; buổi OPEN có người chờ cũng được đóng", async () => {
  const now = new Date();
  const at30 = await seedFullSession({ id: `${RUN}-m30`, capacity: 4, registered: [2, 1], waitlist: [2], startInMinutes: 30, now }); // OPEN
  const at31 = await seedFullSession({ id: `${RUN}-m31`, capacity: 2, registered: [2], waitlist: [1], startInMinutes: 31, now });
  assertEquals((await sessionOf(at30.sessionId)).status, "OPEN");
  const res = await run(now);
  assertEquals(await res.json(), { closedSessions: 1, closedEntries: 1, completedSessions: 0 });
  assertEquals((await regOf(at30.sessionId, at30.waitlistUids[0])).waitlistEndReason, "CLOSED");
  assertEquals((await regOf(at31.sessionId, at31.waitlistUids[0])).status, "WAITLISTED");
});

T("Buổi CANCELLED/COMPLETED không bị đụng tới; lỗi gửi thông báo không hoàn tác việc đóng", async () => {
  const now = new Date();
  const x = await seedFullSession({ id: `${RUN}-x`, capacity: 2, registered: [2], waitlist: [1], startInMinutes: 10, now });
  await firestore.commit([updateWrite(`dropInSessions/${x.sessionId}`, { status: "CANCELLED" })]);
  const y = await seedFullSession({ id: `${RUN}-y`, capacity: 2, registered: [2], waitlist: [1], startInMinutes: 10, now });
  notifyFails = true;
  try {
    const res = await run(now);
    assertEquals(await res.json(), { closedSessions: 1, closedEntries: 1, completedSessions: 0 });
  } finally {
    notifyFails = false;
  }
  assertEquals((await regOf(y.sessionId, y.waitlistUids[0])).waitlistEndReason, "CLOSED");
  assertEquals((await regOf(x.sessionId, x.waitlistUids[0])).status, "WAITLISTED");
});

T("Q14: thiếu hoặc sai CRON_SECRET → 401, không ghi gì; GET → 405", async () => {
  const now = new Date();
  const z = await seedFullSession({ id: `${RUN}-z`, capacity: 2, registered: [2], waitlist: [1], startInMinutes: 10, now });
  sent.length = 0;
  for (const headers of [{}, { "x-cron-secret": "sai" }, { "x-cron-secret": "" }] as Record<string, string>[]) {
    const res = await run(now, headers);
    assertEquals(res.status, 401);
    assertEquals((await res.json()).error.code, "UNAUTHENTICATED");
  }
  assertEquals((await regOf(z.sessionId, z.waitlistUids[0])).status, "WAITLISTED");
  assertEquals((await sessionOf(z.sessionId)).waitlistTickets, [1]);
  assertEquals(sent, []);
  assertEquals((await run(now, { "x-cron-secret": SECRET }, "GET")).status, 405);
});
