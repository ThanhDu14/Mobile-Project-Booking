// T031 (spec 080 US2): unit test `register-drop-in` với Firestore giả trong bộ nhớ. Không cần Emulator.
//   cd supabase/functions && deno test --allow-env register-drop-in/handler.test.ts
// Test đồng thời trên Emulator (T030) nằm ở index.test.ts.
import { assert, assertEquals, assertMatch } from "jsr:@std/assert@1.0.19";
import { HttpError } from "../_shared/auth.ts";
import {
  decodeFields,
  documentName,
  FirestoreError,
  type FirestoreTransaction,
  type Write,
} from "../_shared/firestore.ts";
import {
  createKeyedSerializer,
  handleRegisterDropIn,
  MAX_TX_ATTEMPTS,
  type Notification,
  type RegisterDeps,
} from "./handler.ts";

Deno.env.set("FIREBASE_PROJECT_ID", Deno.env.get("FIREBASE_PROJECT_ID") ?? "demo-smashnow");

const MIN = 60_000;
const NOW = new Date("2026-11-10T03:00:00Z");
const START = new Date(NOW.getTime() + 24 * 60 * MIN);
const END = new Date(START.getTime() + 120 * MIN);
const SID = "s1";
const SESSION = `dropInSessions/${SID}`;
const regPath = (uid: string) => `dropInSessions/${SID}/registrations/${uid}`;
const RID = "3b9e2c1a-1111-4222-8333-444455556666";
const RID2 = "7f1d0c2b-aaaa-4bbb-9ccc-ddddeeeeffff";

// ---------- Firestore giả ----------

type Doc = Record<string, unknown>;

/**
 * Lưu document trong bộ nhớ và chạy transaction lần lượt (khóa toàn cục), áp dụng `Write` giống REST:
 * precondition `exists`, `updateMask` (gộp) hoặc ghi đè cả document, transform `REQUEST_TIME`.
 */
class FakeFirestore {
  docs = new Map<string, Doc>();
  attempts = 0;
  /** Số lần đầu tiên ném ABORTED (giả tranh chấp). `Infinity` = luôn ABORTED. */
  abortFirst = 0;
  private lock: Promise<unknown> = Promise.resolve();

  seed(path: string, data: Doc) {
    this.docs.set(path, structuredClone(data));
  }

  read(path: string): Doc | null {
    const d = this.docs.get(path);
    return d ? structuredClone(d) : null;
  }

  private apply(writes: Write[]) {
    const prefix = documentName("");
    const next = new Map(this.docs);
    for (const w of writes) {
      const path = (w.update?.name ?? w.delete ?? "").substring(prefix.length);
      const existing = next.get(path);
      if (w.currentDocument?.exists === true && !existing) throw new FirestoreError("NOT_FOUND", 404, path);
      if (w.currentDocument?.exists === false && existing) throw new FirestoreError("ALREADY_EXISTS", 409, path);
      if (w.delete) {
        next.delete(path);
        continue;
      }
      const fields = decodeFields(w.update!.fields);
      const doc: Doc = w.updateMask ? { ...(existing ?? {}) } : {};
      for (const k of w.updateMask?.fieldPaths ?? Object.keys(fields)) doc[k] = fields[k];
      for (const t of w.updateTransforms ?? []) {
        if ("setToServerValue" in t) doc[t.fieldPath] = new Date(NOW);
      }
      next.set(path, doc);
    }
    this.docs = next;
  }

  runTransaction = <T>(
    operation: (tx: FirestoreTransaction) => Promise<T | { result: T; writes: Write[] }>,
    _maxAttempts?: number,
  ): Promise<T> => {
    const run = async (): Promise<T> => {
      this.attempts++;
      if (this.attempts <= this.abortFirst) throw new FirestoreError("ABORTED", 409, "fake contention");
      const writes: Write[] = [];
      const tx: FirestoreTransaction = {
        get: (path) => Promise.resolve(this.read(path)),
        query: () => Promise.resolve([]),
        write: (w) => void writes.push(w),
      };
      const out = await operation(tx);
      const wrapped = out !== null && typeof out === "object" && "writes" in (out as object);
      this.apply(wrapped ? (out as { writes: Write[] }).writes : writes);
      return wrapped ? (out as { result: T }).result : out as T;
    };
    const p = this.lock.then(run, run);
    this.lock = p.catch(() => undefined);
    return p;
  };
}

// ---------- Dựng môi trường ----------

interface Env {
  fs: FakeFirestore;
  sent: Notification[];
  deps: RegisterDeps;
}

function setup(
  session: Doc = {},
  opts: { venue?: Doc | null; registrations?: Record<string, Doc>; notifyFails?: boolean } = {},
): Env {
  const fs = new FakeFirestore();
  fs.seed(SESSION, {
    venueId: "v1",
    ownerId: "owner1",
    districtId: "q1",
    courtIds: ["c1"],
    startAt: START,
    endAt: END,
    capacity: 12,
    registeredCount: 8,
    pricePerPerson: 60000,
    status: "OPEN",
    ...session,
  });
  if (opts.venue !== null) fs.seed("venues/v1", { ownerId: "owner1", status: "ACTIVE", ...(opts.venue ?? {}) });
  for (const [uid, reg] of Object.entries(opts.registrations ?? {})) fs.seed(regPath(uid), reg);
  const sent: Notification[] = [];
  const deps: RegisterDeps = {
    firestore: fs,
    now: () => NOW,
    // Token giả: header `Authorization: Bearer <uid>`.
    authenticate: (req) => {
      const uid = /^Bearer (\S+)$/.exec(req.headers.get("Authorization") ?? "")?.[1];
      if (!uid) return Promise.reject(new HttpError(401, "UNAUTHENTICATED", "no token"));
      return Promise.resolve({ uid });
    },
    notify: (n) => {
      sent.push(n);
      return opts.notifyFails ? Promise.reject(new Error("send-notification 500")) : Promise.resolve();
    },
    sleep: () => Promise.resolve(),
  };
  return { fs, sent, deps };
}

const body = (extra: Record<string, unknown> = {}) => ({
  sessionId: SID,
  partySize: 2,
  paymentMethod: "MOCK_PAY",
  requestId: RID,
  ...extra,
});

async function call(env: Env, uid: string | null, payload: unknown, method = "POST") {
  const res = await handleRegisterDropIn(
    new Request("http://localhost/functions/v1/register-drop-in", {
      method,
      headers: { ...(uid ? { Authorization: `Bearer ${uid}` } : {}), "Content-Type": "application/json" },
      ...(method === "POST" ? { body: typeof payload === "string" ? payload : JSON.stringify(payload) } : {}),
    }),
    env.deps,
  );
  const text = await res.text();
  return { status: res.status, text, body: JSON.parse(text) };
}

const errorOf = (r: { status: number; body: { error?: { code: string } } }) => [r.status, r.body.error?.code];

// ---------- Đăng ký thành công ----------

Deno.test("201 MOCK_PAY: response đúng contract, không có qrToken; đăng ký đủ trường 🔒; buổi +partySize", async () => {
  const env = setup();
  const r = await call(env, "u1", body());
  assertEquals(r.status, 201, r.text);
  const { ticketCode, ...registration } = r.body.registration;
  assertEquals(registration, {
    sessionId: SID,
    status: "REGISTERED",
    partySize: 2,
    paymentMethod: "MOCK_PAY",
    paymentStatus: "PAID",
    pricePerPerson: 60000,
    amount: 120000,
    registeredAt: NOW.toISOString(),
  });
  assertMatch(ticketCode, /^DI-[A-HJ-NP-Z2-9]{8}$/);
  assertEquals(r.body.session, { registeredCount: 10, capacity: 12, status: "OPEN" });
  assert(!r.text.includes("qrToken"), "response không được chứa qrToken");

  const reg = env.fs.read(regPath("u1"))!;
  assertMatch(String(reg.qrToken), /^[A-Za-z0-9_-]{43}$/);
  assert(!r.text.includes(String(reg.qrToken)));
  assertEquals(reg.ticketCode, ticketCode);
  const { qrToken: _q, ticketCode: _t, ...rest } = reg;
  assertEquals(rest, {
    userId: "u1",
    sessionId: SID,
    venueId: "v1",
    sessionStartAt: START,
    sessionEndAt: END,
    partySize: 2,
    status: "REGISTERED",
    paymentMethod: "MOCK_PAY",
    paymentStatus: "PAID",
    pricePerPerson: 60000,
    amount: 120000,
    refundAmount: null,
    requestId: RID,
    cancelRequestId: null,
    sessionChanged: false,
    registeredAt: NOW,
    cancelledAt: null,
    cancelledBy: null,
    checkedInAt: null,
    waitlistedAt: null,
    createdAt: NOW,
    updatedAt: NOW,
  });
  // Timestamp (Date), không phải chuỗi ISO
  for (const k of ["sessionStartAt", "sessionEndAt", "registeredAt", "createdAt", "updatedAt"]) {
    assert(reg[k] instanceof Date, `${k} phải là Timestamp`);
  }
  const session = env.fs.read(SESSION)!;
  assertEquals([session.registeredCount, session.status], [10, "OPEN"]);
  assert(session.updatedAt instanceof Date);

  assertEquals(env.sent, [{ type: "DROPIN_REGISTERED", userId: "u1", data: { sessionId: SID } }]);
});

Deno.test("AT_VENUE → paymentStatus UNPAID; amount = pricePerPerson × partySize", async () => {
  const env = setup({ pricePerPerson: 45000 });
  const r = await call(env, "u1", body({ paymentMethod: "AT_VENUE", partySize: 3 }));
  assertEquals(r.status, 201, r.text);
  assertEquals([r.body.registration.paymentStatus, r.body.registration.amount], ["UNPAID", 135000]);
  assertEquals(env.fs.read(regPath("u1"))!.paymentStatus, "UNPAID");
});

Deno.test("registeredCount == capacity sau khi đăng ký → status FULL", async () => {
  const env = setup({ registeredCount: 10 });
  const r = await call(env, "u1", body({ partySize: 2 }));
  assertEquals(r.status, 201, r.text);
  assertEquals(r.body.session, { registeredCount: 12, capacity: 12, status: "FULL" });
  assertEquals(env.fs.read(SESSION)!.status, "FULL");
});

// ---------- Idempotent (E3, FR-008) ----------

Deno.test("E3: cùng requestId gửi 5 lần song song → 1×201, 4×200 cùng vé, đếm tăng 1 lần, thông báo 1 lần", async () => {
  const env = setup();
  const rs = await Promise.all(Array.from({ length: 5 }, () => call(env, "u1", body())));
  assertEquals(rs.map((r) => r.status).sort(), [200, 200, 200, 200, 201]);
  const first = rs.find((r) => r.status === 201)!;
  for (const r of rs) {
    assertEquals(r.body.registration, first.body.registration);
    assert(!r.text.includes("qrToken"));
  }
  assertEquals(env.fs.read(SESSION)!.registeredCount, 10);
  assertEquals(env.sent.length, 1);
});

Deno.test("cùng requestId khi đã CHECKED_IN hoặc buổi đã FULL → 200 trả bản cũ", async () => {
  const env = setup({ registeredCount: 12, status: "FULL" }, {
    registrations: {
      u1: {
        status: "CHECKED_IN",
        requestId: RID,
        sessionId: SID,
        partySize: 2,
        paymentMethod: "MOCK_PAY",
        paymentStatus: "PAID",
        pricePerPerson: 50000,
        amount: 100000,
        ticketCode: "DI-ABCDEFGH",
        registeredAt: new Date(NOW.getTime() - 60 * MIN),
      },
    },
  });
  const r = await call(env, "u1", body());
  assertEquals(r.status, 200, r.text);
  assertEquals(r.body.registration.status, "CHECKED_IN");
  assertEquals(r.body.registration.amount, 100000); // giá chốt lúc đăng ký, không lấy giá mới
  assertEquals(r.body.registration.registeredAt, new Date(NOW.getTime() - 60 * MIN).toISOString());
  assertEquals(r.body.session, { registeredCount: 12, capacity: 12, status: "FULL" });
  assertEquals(env.sent, []);
});

Deno.test("đã REGISTERED, requestId khác → 409 ALREADY_REGISTERED; WAITLISTED → 409 ALREADY_WAITLISTED", async () => {
  const env = setup({}, {
    registrations: {
      u1: { status: "REGISTERED", requestId: RID2, partySize: 1 },
      u2: { status: "WAITLISTED", requestId: RID2, partySize: 1 },
    },
  });
  assertEquals(errorOf(await call(env, "u1", body())), [409, "ALREADY_REGISTERED"]);
  assertEquals(errorOf(await call(env, "u2", body())), [409, "ALREADY_WAITLISTED"]);
  assertEquals(errorOf(await call(env, "u2", body({ requestId: RID2 }))), [409, "ALREADY_WAITLISTED"]);
  assertEquals(env.fs.read(SESSION)!.registeredCount, 8);
  assertEquals(env.sent, []);
});

Deno.test("E11: ghi đè bản CANCELLED cũ → 201, qrToken/ticketCode mới, xóa trường hủy", async () => {
  const env = setup({}, {
    registrations: {
      u1: {
        status: "CANCELLED",
        requestId: RID,
        partySize: 1,
        qrToken: null,
        ticketCode: null,
        cancelledAt: new Date(NOW.getTime() - 10 * MIN),
        cancelledBy: "USER",
        refundAmount: 60000,
        paymentStatus: "REFUNDED",
        cancelRequestId: RID2,
        createdAt: new Date("2026-11-01T00:00:00Z"),
        legacyField: "x",
      },
    },
  });
  const r = await call(env, "u1", body({ partySize: 1 }));
  assertEquals(r.status, 201, r.text);
  const reg = env.fs.read(regPath("u1"))!;
  assertEquals([reg.status, reg.paymentStatus, reg.amount], ["REGISTERED", "PAID", 60000]);
  assertMatch(String(reg.qrToken), /^[A-Za-z0-9_-]{43}$/);
  assertMatch(String(reg.ticketCode), /^DI-[A-HJ-NP-Z2-9]{8}$/);
  assertEquals([reg.cancelledAt, reg.cancelledBy, reg.refundAmount, reg.cancelRequestId], [null, null, null, null]);
  assertEquals(reg.legacyField, undefined, "ghi đè cả document");
  assertEquals((reg.createdAt as Date).toISOString(), "2026-11-01T00:00:00.000Z", "giữ createdAt cũ");
  assertEquals(env.fs.read(SESSION)!.registeredCount, 9);
  assertEquals(env.sent.length, 1);
});

// ---------- Điều kiện buổi (E8) ----------

Deno.test("E8: buổi đã bắt đầu → 409 SESSION_STARTED (kể cả đúng startAt)", async () => {
  assertEquals(errorOf(await call(setup({ startAt: NOW }), "u1", body())), [409, "SESSION_STARTED"]);
  assertEquals(
    errorOf(await call(setup({ startAt: new Date(NOW.getTime() - MIN) }), "u1", body())),
    [409, "SESSION_STARTED"],
  );
});

Deno.test("E8: buổi CANCELLED/COMPLETED → 409 SESSION_CANCELLED", async () => {
  for (const status of ["CANCELLED", "COMPLETED"]) {
    assertEquals(errorOf(await call(setup({ status }), "u1", body())), [409, "SESSION_CANCELLED"]);
  }
});

Deno.test("E8: cơ sở HIDDEN/LOCKED/không còn → 409 VENUE_INACTIVE", async () => {
  for (const status of ["HIDDEN", "LOCKED"]) {
    assertEquals(errorOf(await call(setup({}, { venue: { status } }), "u1", body())), [409, "VENUE_INACTIVE"]);
  }
  assertEquals(errorOf(await call(setup({}, { venue: null }), "u1", body())), [409, "VENUE_INACTIVE"]);
});

Deno.test("E8: chủ sân tự đăng ký buổi của mình → 403 OWN_SESSION", async () => {
  const env = setup();
  assertEquals(errorOf(await call(env, "owner1", body())), [403, "OWN_SESSION"]);
  assertEquals(env.fs.read(regPath("owner1")), null);
});

Deno.test("không có buổi → 404 SESSION_NOT_FOUND", async () => {
  assertEquals(errorOf(await call(setup(), "u1", body({ sessionId: "khong-co" }))), [404, "SESSION_NOT_FOUND"]);
});

Deno.test("không đủ chỗ → 409 SESSION_FULL kèm remaining, không ghi gì", async () => {
  const env = setup({ registeredCount: 11 });
  const r = await call(env, "u1", body({ partySize: 2 }));
  assertEquals(errorOf(r), [409, "SESSION_FULL"]);
  assertEquals(r.body.error.remaining, 1);
  assertEquals(env.fs.read(SESSION)!.registeredCount, 11);
  assertEquals(env.fs.read(regPath("u1")), null);
  assertEquals(env.sent, []);

  const full = await call(setup({ registeredCount: 12, status: "FULL" }), "u1", body({ partySize: 1 }));
  assertEquals([...errorOf(full), full.body.error.remaining], [409, "SESSION_FULL", 0]);
});

// ---------- Kiểm tra đầu vào (E9) ----------

Deno.test("E9: partySize 0/5/1.5/\"2\", paymentMethod sai, requestId không phải UUID, thiếu sessionId → 400 INVALID_ARGUMENT", async () => {
  const env = setup();
  const bad = [
    body({ partySize: 0 }),
    body({ partySize: 5 }),
    body({ partySize: 1.5 }),
    body({ partySize: "2" }),
    body({ paymentMethod: "CASH" }),
    body({ requestId: "abc" }),
    body({ requestId: undefined }),
    body({ sessionId: "" }),
    body({ sessionId: "a/b" }),
  ];
  for (const b of bad) assertEquals(errorOf(await call(env, "u1", b)), [400, "INVALID_ARGUMENT"], JSON.stringify(b));
  assertEquals(errorOf(await call(env, "u1", "{not json")), [400, "INVALID_ARGUMENT"]);
  assertEquals(env.fs.attempts, 0, "không mở transaction khi đầu vào sai");
});

Deno.test("partySize 1 và 4 hợp lệ", async () => {
  assertEquals((await call(setup(), "u1", body({ partySize: 1 }))).status, 201);
  assertEquals((await call(setup(), "u1", body({ partySize: 4 }))).status, 201);
});

// ---------- Xác thực, phương thức ----------

Deno.test("không có token → 401 UNAUTHENTICATED; GET → 405", async () => {
  const env = setup();
  assertEquals(errorOf(await call(env, null, body())), [401, "UNAUTHENTICATED"]);
  assertEquals((await call(env, "u1", null, "GET")).status, 405);
});

Deno.test("ACCOUNT_LOCKED từ authenticate được trả nguyên", async () => {
  const env = setup();
  env.deps.authenticate = () => Promise.reject(new HttpError(403, "ACCOUNT_LOCKED", "khóa"));
  assertEquals(errorOf(await call(env, "u1", body())), [403, "ACCOUNT_LOCKED"]);
});

// ---------- Thông báo, thử lại ----------

Deno.test("gửi thông báo lỗi → vẫn 201", async () => {
  const env = setup({}, { notifyFails: true });
  assertEquals((await call(env, "u1", body())).status, 201);
  assertEquals(env.sent.length, 1);
});

Deno.test("ABORTED 2 lần rồi thành công → 201, đếm chỉ tăng 1 lần", async () => {
  const env = setup();
  env.fs.abortFirst = 2;
  assertEquals((await call(env, "u1", body())).status, 201);
  assertEquals(env.fs.attempts, 3);
  assertEquals(env.fs.read(SESSION)!.registeredCount, 10);
});

Deno.test(`hết ${MAX_TX_ATTEMPTS} lần thử → 503 BUSY, không gửi thông báo`, async () => {
  const env = setup();
  env.fs.abortFirst = Infinity;
  const slept: number[] = [];
  env.deps.sleep = (ms) => {
    slept.push(ms);
    return Promise.resolve();
  };
  assertEquals(errorOf(await call(env, "u1", body())), [503, "BUSY"]);
  assertEquals(env.fs.attempts, 8);
  assertEquals(slept.length, 7);
  assert(slept.every((ms) => ms >= 50 && ms <= 400), `backoff 50–400 ms: ${slept}`);
  assertEquals(env.sent, []);
});

Deno.test("createKeyedSerializer: cùng khóa chạy lần lượt (kể cả task trước lỗi), khác khóa chạy song song", async () => {
  const serialize = createKeyedSerializer();
  const log: string[] = [];
  const gate = Promise.withResolvers<void>();
  const a1 = serialize("s1", async () => {
    log.push("a1 start");
    await gate.promise;
    log.push("a1 end");
    throw new Error("a1 lỗi");
  });
  const a2 = serialize("s1", () => {
    log.push("a2");
    return Promise.resolve(2);
  });
  const b1 = serialize("s2", () => {
    log.push("b1");
    return Promise.resolve(1);
  });
  assertEquals(await b1, 1);
  assertEquals(log, ["a1 start", "b1"]);
  gate.resolve();
  const err = await a1.catch((e: Error) => e.message);
  assertEquals(err, "a1 lỗi");
  assertEquals(await a2, 2);
  assertEquals(log, ["a1 start", "b1", "a1 end", "a2"]);
});
