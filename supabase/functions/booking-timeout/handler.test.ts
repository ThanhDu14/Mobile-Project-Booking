import { assertEquals } from "jsr:@std/assert@1.0.19";
import { handleBookingTimeout, TIMEOUT_REASON } from "./handler.ts";

Deno.env.set("FIREBASE_PROJECT_ID", "demo-smashnow");

const now = new Date("2026-11-18T12:00:00.000Z");
const cronSecret = "test-cron-secret";
const past = new Date(now.getTime() - 60_000);
const future = new Date(now.getTime() + 60_000);

function request(method = "POST", secret = cronSecret) {
  return new Request("https://localhost/booking-timeout", { method, headers: { "x-cron-secret": secret } });
}

/** Firestore giả: `docs` theo đường dẫn, gom các lần commit để kiểm tra. */
function fakeFirestore(docs: Record<string, Record<string, unknown>>) {
  for (const [path, data] of Object.entries(docs)) if (path.startsWith("bookings/") && typeof data.userId === "string") {
    docs[`users/${data.userId}`] ??= { accountStatus: "ACTIVE" };
  }
  const commits: any[][] = [];
  return {
    commits,
    query: async () =>
      Object.entries(docs)
        .filter(([path, data]) => path.startsWith("bookings/") && data.status === "PENDING")
        .map(([path, data]) => ({ id: path.split("/")[1], path, data })),
    runTransaction: async (operation: (tx: any) => Promise<any>) => {
      const out = await operation({ get: async (path: string) => docs[path] ?? null });
      if (out.writes.length) commits.push(out.writes);
      return out.result;
    },
  };
}

const field = (write: any, name: string) => write.update.fields[name];
const named = (writes: any[], suffix: string) => writes.find((w) => (w.update?.name ?? w.delete ?? "").endsWith(suffix));

Deno.test("booking-timeout: requires POST and the cron secret", async () => {
  const firestore = fakeFirestore({}) as any;
  assertEquals((await handleBookingTimeout(request("GET"), { firestore, cronSecret, now: () => now })).status, 405);
  assertEquals((await handleBookingTimeout(request("POST", "wrong"), { firestore, cronSecret, now: () => now })).status, 401);
  assertEquals((await handleBookingTimeout(request(), { firestore, cronSecret: "", now: () => now })).status, 401);
});

Deno.test("booking-timeout: rejects an overdue unpaid booking and frees only its own slots", async () => {
  const firestore = fakeFirestore({
    "bookings/b1": {
      status: "PENDING", userId: "u1", venueId: "v1", approvalDeadline: past,
      paymentMethod: "AT_VENUE", paymentStatus: "UNPAID", paidAmount: 0,
      items: [{ slotId: "s-own" }, { slotId: "s-other" }],
    },
    "slots/s-own": { status: "BOOKED", bookingId: "b1" },
    "slots/s-other": { status: "BOOKED", bookingId: "someone-else" },
  });
  const res = await handleBookingTimeout(request(), { firestore: firestore as any, cronSecret, now: () => now });
  assertEquals(res.status, 200);
  assertEquals((await res.json()).rejected, 1);

  const writes = firestore.commits[0];
  const booking = named(writes, "/bookings/b1");
  assertEquals(field(booking, "status").stringValue, "REJECTED");
  assertEquals(field(booking, "rejectionReason").stringValue, TIMEOUT_REASON);
  assertEquals(field(booking, "updatedAt").timestampValue, now.toISOString());
  assertEquals(field(booking, "rejectedAt").timestampValue, now.toISOString());
  assertEquals(field(booking, "refundAmount").integerValue, "0");
  assertEquals(field(booking, "paymentStatus"), undefined);
  // Chỉ nhả slot của chính đơn.
  assertEquals(writes.filter((w) => w.delete).map((w) => w.delete.split("/").pop()), ["s-own"]);
  // Không hoàn tiền thì không đụng ví, nhưng vẫn báo cho người chơi.
  assertEquals(named(writes, "/users/u1"), undefined);
  assertEquals(writes.some((w) => w.update?.name?.includes("/users/u1/notifications/")), true);
});

Deno.test("booking-timeout: refunds a paid booking with an atomic wallet increment and a ledger", async () => {
  const firestore = fakeFirestore({
    "bookings/b2": {
      status: "PENDING", userId: "u2", approvalDeadline: past,
      paymentMethod: "MOCK_WALLET", paymentStatus: "PAID", paidAmount: 120000, items: [],
    },
  });
  await handleBookingTimeout(request(), { firestore: firestore as any, cronSecret, now: () => now });
  const writes = firestore.commits[0];
  assertEquals(field(named(writes, "/bookings/b2"), "paymentStatus").stringValue, "REFUNDED");
  assertEquals(field(named(writes, "/bookings/b2"), "refundAmount").integerValue, "120000");
  const wallet = named(writes, "/users/u2");
  assertEquals(wallet.update.fields.walletBalance, undefined);
  assertEquals(wallet.updateTransforms, [{ fieldPath: "walletBalance", increment: { integerValue: "120000" } }]);
  const ledger = named(writes, "/refunds/refund_b2");
  assertEquals(ledger.currentDocument, { exists: false });
  assertEquals(field(ledger, "createdAt").timestampValue, now.toISOString());
});

Deno.test("booking-timeout: does not refund twice when a refund ledger already exists", async () => {
  const firestore = fakeFirestore({
    "bookings/b3": {
      status: "PENDING", userId: "u3", approvalDeadline: past,
      paymentMethod: "DEPOSIT", paymentStatus: "DEPOSITED", paidAmount: 54000, items: [],
    },
    "refunds/refund_b3": { refundAmount: 54000 },
  });
  await handleBookingTimeout(request(), { firestore: firestore as any, cronSecret, now: () => now });
  const writes = firestore.commits[0];
  assertEquals(field(named(writes, "/bookings/b3"), "refundAmount").integerValue, "0");
  assertEquals(named(writes, "/users/u3"), undefined);
});

Deno.test("booking-timeout: leaves bookings that are still within the deadline", async () => {
  const firestore = fakeFirestore({
    "bookings/b4": { status: "PENDING", userId: "u4", approvalDeadline: future, items: [] },
    "bookings/b5": { status: "PENDING", userId: "u5", items: [] },
  });
  const body = await (await handleBookingTimeout(request(), { firestore: firestore as any, cronSecret, now: () => now })).json();
  assertEquals(body.rejected, 0);
  assertEquals(body.skipped, 2);
  assertEquals(firestore.commits.length, 0);
});
