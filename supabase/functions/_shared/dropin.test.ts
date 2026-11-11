// T005 (spec 080): unit test cho `_shared/dropin.ts`. Không cần Emulator.
//   cd supabase/functions && deno test --allow-env _shared/dropin.test.ts
import { assert, assertEquals, assertMatch } from "jsr:@std/assert@1.0.19";
import type { DeletionHelpers } from "./account-deletion.ts";
import {
  cancelRegistrationInTx,
  closeWaitlistInTx,
  computeRefund,
  deriveSessionFields,
  type DropInTx,
  newQrToken,
  newTicketCode,
} from "./dropin.ts";
import { decodeFields, documentName, type Write } from "./firestore.ts";

Deno.env.set("FIREBASE_PROJECT_ID", Deno.env.get("FIREBASE_PROJECT_ID") ?? "demo-smashnow");

const MIN = 60_000;
const NOW = new Date("2026-11-10T03:00:00Z"); // 10:00 giờ Việt Nam

const paidReg = (extra: Record<string, unknown> = {}) => ({
  status: "REGISTERED",
  partySize: 2,
  paymentMethod: "MOCK_PAY",
  paymentStatus: "PAID",
  pricePerPerson: 60000,
  amount: 120000,
  sessionChanged: false,
  ...extra,
});

const sessionAt = (minutesFromNow: number, extra: Record<string, unknown> = {}) => ({
  startAt: new Date(NOW.getTime() + minutesFromNow * MIN),
  capacity: 12,
  registeredCount: 12,
  status: "FULL",
  ...extra,
});

// ---------- computeRefund ----------

Deno.test("computeRefund: 239 phút → LATE 0; 240 và 241 phút → EARLY hoàn 100%", () => {
  assertEquals(computeRefund(paidReg(), sessionAt(239), NOW), { refundAmount: 0, reason: "LATE" });
  assertEquals(computeRefund(paidReg(), sessionAt(240), NOW), { refundAmount: 120000, reason: "EARLY" });
  assertEquals(computeRefund(paidReg(), sessionAt(241), NOW), { refundAmount: 120000, reason: "EARLY" });
});

Deno.test("computeRefund: sessionChanged còn 30 phút → SESSION_CHANGED hoàn 100%", () => {
  assertEquals(
    computeRefund(paidReg({ sessionChanged: true }), sessionAt(30), NOW),
    { refundAmount: 120000, reason: "SESSION_CHANGED" },
  );
});

Deno.test("computeRefund: AT_VENUE → refundAmount 0, reason AT_VENUE (sớm hay muộn đều vậy)", () => {
  const reg = paidReg({ paymentMethod: "AT_VENUE", paymentStatus: "UNPAID" });
  assertEquals(computeRefund(reg, sessionAt(600), NOW), { refundAmount: 0, reason: "AT_VENUE" });
  assertEquals(computeRefund(reg, sessionAt(10), NOW), { refundAmount: 0, reason: "AT_VENUE" });
});

Deno.test("computeRefund: thiếu amount thì tính pricePerPerson × partySize; thiếu startAt của buổi thì dùng sessionStartAt", () => {
  const reg = paidReg({ amount: undefined, sessionStartAt: new Date(NOW.getTime() + 300 * MIN) });
  assertEquals(computeRefund(reg, { status: "OPEN" }, NOW), { refundAmount: 120000, reason: "EARLY" });
});

// ---------- deriveSessionFields ----------

/** Giờ Việt Nam (UTC+7) → Date. */
const vn = (iso: string) => new Date(`${iso}+07:00`);
const venue = { name: "Sân A", address: "1 Lê Lợi, Quận 1", districtId: "q1", status: "ACTIVE" };

Deno.test("deriveSessionFields: timeSlot đúng biên 05:00/11:00/13:00/18:00 theo giờ Việt Nam", () => {
  const slot = (t: string) => deriveSessionFields({ startAt: vn(`2026-11-11T${t}:00`), venue, districtName: "Quận 1" })
    .timeSlot;
  assertEquals(slot("05:00"), "MORNING");
  assertEquals(slot("10:30"), "MORNING");
  assertEquals(slot("11:00"), "NOON");
  assertEquals(slot("12:30"), "NOON");
  assertEquals(slot("13:00"), "AFTERNOON");
  assertEquals(slot("17:30"), "AFTERNOON");
  assertEquals(slot("18:00"), "EVENING");
  assertEquals(slot("22:30"), "EVENING");
});

Deno.test("deriveSessionFields: date yyyyMMdd theo Asia/Ho_Chi_Minh (khác ngày UTC), venueSnapshot", () => {
  // 2026-11-10T23:00Z = 06:00 ngày 11/11 ở Việt Nam
  const r = deriveSessionFields({ startAt: new Date("2026-11-10T23:00:00Z"), venue, districtName: "Quận 1" });
  assertEquals(r, {
    timeSlot: "MORNING",
    date: "20261111",
    venueSnapshot: { name: "Sân A", address: "1 Lê Lợi, Quận 1", districtName: "Quận 1" },
  });
});

// ---------- Sinh mã ----------

Deno.test("newQrToken: 43 ký tự base64url, không trùng sau 10.000 lần", () => {
  const seen = new Set<string>();
  for (let i = 0; i < 10_000; i++) {
    const t = newQrToken();
    assertMatch(t, /^[A-Za-z0-9_-]{43}$/);
    seen.add(t);
  }
  assertEquals(seen.size, 10_000);
});

Deno.test("newTicketCode: đúng ^DI-[A-HJ-NP-Z2-9]{8}$", () => {
  for (let i = 0; i < 2_000; i++) assertMatch(newTicketCode(), /^DI-[A-HJ-NP-Z2-9]{8}$/);
});

// ---------- cancelRegistrationInTx ----------

const SID = "s1";
const REG_PATH = `dropInSessions/${SID}/registrations/u1`;
const SESSION_PATH = `dropInSessions/${SID}`;

function fakeTx(docs: Record<string, Record<string, unknown>>) {
  const writes: Write[] = [];
  const tx: DropInTx = {
    get: (path) => Promise.resolve(docs[path] ? structuredClone(docs[path]) : null),
    write: (w) => void writes.push(w),
  };
  /** Trường đã ghi vào `path` (gộp các lần ghi). */
  const written = (path: string) => {
    const ws = writes.filter((w) => w.update?.name === documentName(path));
    if (ws.length === 0) return null;
    return Object.assign({}, ...ws.map((w) => decodeFields(w.update!.fields))) as Record<string, unknown>;
  };
  return { tx, writes, written };
}

Deno.test("cancelRegistrationInTx: hủy sớm → CANCELLED, xóa mã, hoàn 100%, REFUNDED, trả chỗ, FULL → OPEN", async () => {
  const f = fakeTx({
    [REG_PATH]: paidReg({ qrToken: "tok", ticketCode: "DI-AAAAAAAA" }),
    [SESSION_PATH]: sessionAt(600),
  });
  const r = await cancelRegistrationInTx(f.tx, SID, "u1", "USER", { now: NOW, cancelRequestId: "c-1" });
  assertEquals(r.refundAmount, 120000);

  const reg = f.written(REG_PATH)!;
  assertEquals(reg.status, "CANCELLED");
  assertEquals([reg.qrToken, reg.ticketCode], [null, null]);
  assertEquals([reg.cancelledBy, reg.refundAmount, reg.paymentStatus], ["USER", 120000, "REFUNDED"]);
  assertEquals(reg.cancelRequestId, "c-1");
  assertEquals((reg.cancelledAt as Date).getTime(), NOW.getTime());
  assertEquals((reg.updatedAt as Date).getTime(), NOW.getTime());

  const session = f.written(SESSION_PATH)!;
  assertEquals([session.registeredCount, session.status], [10, "OPEN"]);
  // Precondition: chỉ sửa document đã có
  assert(f.writes.every((w) => w.currentDocument?.exists === true));
});

Deno.test("cancelRegistrationInTx: hủy muộn → refund 0, paymentStatus giữ PAID; buổi OPEN giữ OPEN", async () => {
  const f = fakeTx({
    [REG_PATH]: paidReg(),
    [SESSION_PATH]: sessionAt(60, { registeredCount: 5, status: "OPEN" }),
  });
  assertEquals((await cancelRegistrationInTx(f.tx, SID, "u1", "USER", { now: NOW })).refundAmount, 0);
  const reg = f.written(REG_PATH)!;
  assertEquals(reg.refundAmount, 0);
  assert(!("paymentStatus" in reg), "không đổi paymentStatus");
  assertEquals([f.written(SESSION_PATH)!.registeredCount, f.written(SESSION_PATH)!.status], [3, "OPEN"]);
});

Deno.test("cancelRegistrationInTx: forceFullRefund (chủ sân hủy buổi) → hoàn 100% dù muộn; buổi CANCELLED giữ CANCELLED", async () => {
  const f = fakeTx({
    [REG_PATH]: paidReg(),
    [SESSION_PATH]: sessionAt(10, { status: "CANCELLED", registeredCount: 2 }),
  });
  const r = await cancelRegistrationInTx(f.tx, SID, "u1", "OWNER", { forceFullRefund: true, now: NOW });
  assertEquals(r.refundAmount, 120000);
  assertEquals(f.written(REG_PATH)!.cancelledBy, "OWNER");
  assertEquals([f.written(SESSION_PATH)!.registeredCount, f.written(SESSION_PATH)!.status], [0, "CANCELLED"]);
});

Deno.test("cancelRegistrationInTx: AT_VENUE → refund 0, paymentStatus giữ UNPAID", async () => {
  const f = fakeTx({
    [REG_PATH]: paidReg({ paymentMethod: "AT_VENUE", paymentStatus: "UNPAID" }),
    [SESSION_PATH]: sessionAt(600),
  });
  assertEquals((await cancelRegistrationInTx(f.tx, SID, "u1", "SYSTEM", { now: NOW })).refundAmount, 0);
  assert(!("paymentStatus" in f.written(REG_PATH)!), "không đổi paymentStatus");
});

Deno.test("cancelRegistrationInTx: không có đăng ký / đã CANCELLED / WAITLISTED → không ghi gì, refund 0", async () => {
  for (const reg of [undefined, paidReg({ status: "CANCELLED" }), paidReg({ status: "WAITLISTED" })]) {
    const f = fakeTx({ ...(reg ? { [REG_PATH]: reg } : {}), [SESSION_PATH]: sessionAt(600) });
    assertEquals((await cancelRegistrationInTx(f.tx, SID, "u1", "SYSTEM", { now: NOW })).refundAmount, 0);
    assertEquals(f.writes, []);
  }
});

Deno.test("cancelRegistrationInTx khớp chữ ký helper của delete-account (A)", () => {
  const helper: DeletionHelpers["cancelRegistrationInTx"] = cancelRegistrationInTx;
  const refund: DeletionHelpers["computeRefund"] = computeRefund;
  assertEquals([typeof helper, typeof refund], ["function", "function"]);
});

Deno.test("cancelRegistrationInTx: buổi không còn document → vẫn hủy đăng ký, không ghi buổi", async () => {
  const f = fakeTx({ [REG_PATH]: paidReg() });
  await cancelRegistrationInTx(f.tx, SID, "u1", "SYSTEM", { now: NOW });
  assertEquals(f.written(REG_PATH)!.status, "CANCELLED");
  assertEquals(f.written(SESSION_PATH), null);
});

// ---------- closeWaitlistInTx (D dùng, giữ nguyên hành vi) ----------

Deno.test("closeWaitlistInTx: giữ nguyên hành vi cũ", () => {
  const w = closeWaitlistInTx(SID, "SESSION_CANCELLED");
  assertEquals(w.update?.name, documentName(SESSION_PATH));
  const fields = decodeFields(w.update!.fields);
  assertEquals([fields.waitlistStatus, fields.closeReason], ["CLOSED", "SESSION_CANCELLED"]);
  assertEquals(w.updateMask?.fieldPaths, ["waitlistStatus", "closeReason", "updatedAt"]);
});
