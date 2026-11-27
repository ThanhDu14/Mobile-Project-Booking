import { assertEquals } from "jsr:@std/assert@1.0.19";
import { handlePlayerCancelBooking } from "./handler.ts";

Deno.env.set("FIREBASE_PROJECT_ID", "demo-smashnow");

Deno.test("player-cancel-booking: rejects non-POST requests", async () => {
  const res = await handlePlayerCancelBooking(
    new Request("https://localhost/player-cancel-booking", { method: "GET" }),
  );
  assertEquals(res.status, 405);
});

Deno.test("player-cancel-booking: rejects unauthenticated requests", async () => {
  const req = new Request("https://localhost/player-cancel-booking", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ bookingId: "b1" }),
  });
  assertEquals((await handlePlayerCancelBooking(req)).status, 401);
});

Deno.test("player-cancel-booking: rejects invalid token before reading", async () => {
  let reads = 0;
  const req = new Request("https://localhost/player-cancel-booking", {
    method: "POST",
    headers: { Authorization: "Bearer invalid", "Content-Type": "application/json" },
    body: JSON.stringify({ bookingId: "b1" }),
  });
  const response = await handlePlayerCancelBooking(req, {
    firestore: {
      get: async () => {
        reads++;
        return null;
      },
      commit: async () => {},
    },
  });
  assertEquals(response.status, 401);
  assertEquals(reads, 0);
});

Deno.test("player-cancel-booking: successfully cancels PENDING booking and releases slots", async () => {
  const committedWrites: any[] = [];
  const fakeToken = {
    uid: "user-1",
    appRole: "PLAYER" as const,
    email: "test@smashnow.vn",
    emailVerified: true,
    phone: null,
    name: "Tester",
    signInProvider: "password",
  };

  const req = new Request("https://localhost/player-cancel-booking", {
    method: "POST",
    headers: { Authorization: "Bearer valid", "Content-Type": "application/json" },
    body: JSON.stringify({
      bookingId: "b-pending",
      reason: "Bận việc đột xuất",
      requestId: "req-123",
    }),
  });

  const res = await handlePlayerCancelBooking(req, {
    verifyToken: async () => fakeToken,
    firestore: {
      get: async (path: string) => {
        if (path === "bookings/b-pending") {
          return {
            userId: "user-1",
            venueId: "venue-1",
            status: "PENDING",
            paidAmount: 57000,
            paymentMethod: "DEPOSIT",
            startAt: new Date(Date.now() + 3600000 * 5).toISOString(),
            items: [{ slotId: "court-1_20261110_0500" }, { slotId: "court-1_20261110_0530" }],
          };
        }
        return null;
      },
      commit: async (writes: any[]) => {
        committedWrites.push(...writes);
      },
    },
  });

  assertEquals(res.status, 200);
  const data = await res.json();
  assertEquals(data.success, true);
  assertEquals(data.refundAmount, 57000);
  assertEquals(data.penaltyAmount, 0);

  // Check committed writes: booking update, 2 slot deletes, refund doc create (spec 050: không còn cộng ví)
  const slotDeletes = committedWrites.filter((w) => w.delete && w.delete.includes("slots/"));
  assertEquals(slotDeletes.length, 2);

  // Mốc thời gian phải lưu thành Timestamp, không phải chuỗi (tab "Đã hủy" sắp xếp theo updatedAt).
  const bookingWrite = committedWrites.find((w) => w.update?.name?.endsWith("/bookings/b-pending"));
  assertEquals(typeof bookingWrite.update.fields.updatedAt.timestampValue, "string");
  assertEquals(typeof bookingWrite.update.fields.cancelledAt.timestampValue, "string");
  assertEquals(bookingWrite.update.fields.paymentStatus.stringValue, "REFUND_PENDING");
  const refundWrite = committedWrites.find((w) => w.update?.name?.endsWith("/refunds/refund_b-pending"));
  assertEquals(refundWrite.update.fields.status.stringValue, "PENDING");

  // Spec 050 (T058): không còn ví, không ghi gì vào users/{uid}.
  assertEquals(committedWrites.find((w) => w.update?.name?.endsWith("/users/user-1")), undefined);
});

Deno.test("player-cancel-booking: works when invoked by Deno.serve with info as 2nd arg", async () => {
  const req = new Request("https://localhost/player-cancel-booking", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ bookingId: "b-1" }),
  });
  // Deno.serve passes (req, info) where info has remoteAddr
  const fakeInfo = { remoteAddr: { hostname: "127.0.0.1", port: 8080, transport: "tcp" } } as any;
  const res = await handlePlayerCancelBooking(req, fakeInfo);
  // Should not throw TypeError, returns 401 unauthenticated
  assertEquals(res.status, 401);
});

// ---------- Spec 050 (T058): bỏ ví, hoàn tiền thủ công qua refunds PENDING ----------

import { decodeFields, documentName, type Write } from "../_shared/firestore.ts";

const PLAYER = {
  uid: "user-1",
  appRole: "PLAYER" as const,
  email: "p@smashnow.vn",
  emailVerified: true,
  phone: null,
  name: "P",
  signInProvider: "password",
};

function cancelRequest(bookingId = "b1") {
  return new Request("https://localhost/player-cancel-booking", {
    method: "POST",
    headers: { Authorization: "Bearer valid", "Content-Type": "application/json" },
    body: JSON.stringify({ bookingId, reason: "Bận việc", requestId: "req-1" }),
  });
}

/** Chạy hủy với một đơn giả, trả về status, body và các write đã commit (theo đường dẫn). */
async function runCancel(booking: Record<string, unknown>) {
  const writes: Write[] = [];
  let commits = 0;
  const res = await handlePlayerCancelBooking(cancelRequest(), {
    verifyToken: () => Promise.resolve(PLAYER),
    firestore: {
      get: (path: string) => Promise.resolve(path === "bookings/b1" ? booking : null),
      commit: (ws: Write[]) => {
        commits++;
        writes.push(...ws);
        return Promise.resolve();
      },
    },
  });
  const prefix = documentName("");
  const byPath = new Map<string, Write>();
  for (const w of writes) byPath.set((w.update?.name ?? w.delete ?? "").substring(prefix.length), w);
  const fields = (path: string) => {
    const w = byPath.get(path);
    return w?.update ? decodeFields(w.update.fields) : undefined;
  };
  return { status: res.status, body: await res.json(), byPath, fields, commits };
}

const HOUR = 3_600_000;
const baseBooking = (over: Record<string, unknown>) => ({
  userId: "user-1",
  venueId: "v1",
  status: "CONFIRMED",
  startAt: new Date(Date.now() + 10 * HOUR).toISOString(),
  slotIds: ["c1_20261124_0700"],
  ...over,
});

Deno.test("T058: đơn PENDING đã trả đủ → hoàn 100%, refund PENDING, paymentStatus REFUND_PENDING, không đụng ví", async () => {
  const r = await runCancel(baseBooking({
    status: "PENDING",
    paymentMethod: "TRANSFER_FULL",
    paymentStatus: "PAID",
    paidAmount: 57000,
  }));
  assertEquals(r.status, 200);
  assertEquals([r.body.refundAmount, r.body.penaltyAmount], [57000, 0]);
  const booking = r.fields("bookings/b1")!;
  assertEquals([booking.status, booking.paymentStatus, booking.cancelledBy], ["CANCELLED", "REFUND_PENDING", "USER"]);
  assertEquals("refundStatus" in booking, false);
  const refund = r.fields("refunds/refund_b1")!;
  assertEquals(
    [
      refund.bookingId,
      refund.venueId,
      refund.userId,
      refund.amount,
      refund.penaltyAmount,
      refund.reason,
      refund.status,
    ],
    ["b1", "v1", "user-1", 57000, 0, "CANCELLED", "PENDING"],
  );
  assertEquals([...r.byPath.keys()].some((p) => p.startsWith("users/")), false);
  assertEquals(r.byPath.has("slots/c1_20261124_0700"), true);
});

Deno.test("T058: CONFIRMED trả đủ, hủy muộn (< 4 giờ) → phạt 30% làm tròn nghìn, hoàn phần còn lại", async () => {
  const r = await runCancel(baseBooking({
    paymentMethod: "TRANSFER_FULL",
    paymentStatus: "PAID",
    paidAmount: 105000,
    startAt: new Date(Date.now() + 2 * HOUR).toISOString(),
  }));
  assertEquals([r.body.refundAmount, r.body.penaltyAmount], [74000, 31000]);
  assertEquals(r.fields("refunds/refund_b1")?.amount, 74000);
  assertEquals(r.fields("refunds/refund_b1")?.penaltyAmount, 31000);
});

Deno.test("T058: CONFIRMED cọc 30%, hủy muộn → mất cọc, không tạo refund, giữ paymentStatus", async () => {
  const r = await runCancel(baseBooking({
    paymentMethod: "TRANSFER_DEPOSIT",
    paymentStatus: "DEPOSITED",
    paidAmount: 30000,
    startAt: new Date(Date.now() + 1 * HOUR).toISOString(),
  }));
  assertEquals([r.body.refundAmount, r.body.penaltyAmount], [0, 30000]);
  assertEquals(r.byPath.has("refunds/refund_b1"), false);
  assertEquals("paymentStatus" in r.fields("bookings/b1")!, false);
});

Deno.test("T058: CONFIRMED cọc 30%, hủy sớm (≥ 4 giờ) → hoàn đủ cọc", async () => {
  const r = await runCancel(
    baseBooking({ paymentMethod: "TRANSFER_DEPOSIT", paymentStatus: "DEPOSITED", paidAmount: 30000 }),
  );
  assertEquals([r.body.refundAmount, r.body.penaltyAmount], [30000, 0]);
  assertEquals(r.fields("refunds/refund_b1")?.reason, "CANCELLED");
});

Deno.test("T058: trả tại sân → không refund, không đổi paymentStatus", async () => {
  const r = await runCancel(baseBooking({ paymentMethod: "AT_VENUE", paymentStatus: "UNPAID", paidAmount: 0 }));
  assertEquals([r.status, r.body.refundAmount, r.body.penaltyAmount], [200, 0, 0]);
  assertEquals(r.byPath.has("refunds/refund_b1"), false);
  assertEquals("paymentStatus" in r.fields("bookings/b1")!, false);
});

Deno.test("T058: dữ liệu cũ MOCK_WALLET/DEPOSIT tính như TRANSFER_FULL/TRANSFER_DEPOSIT", async () => {
  const late = { startAt: new Date(Date.now() + 2 * HOUR).toISOString() };
  const wallet = await runCancel(baseBooking({ paymentMethod: "MOCK_WALLET", paidAmount: 100000, ...late }));
  assertEquals([wallet.body.refundAmount, wallet.body.penaltyAmount], [70000, 30000]);
  const deposit = await runCancel(baseBooking({ paymentMethod: "DEPOSIT", paidAmount: 30000, ...late }));
  assertEquals([deposit.body.refundAmount, deposit.body.penaltyAmount], [0, 30000]);
});

Deno.test("T058: gọi lại khi đơn đã do người chơi hủy → 200 cùng số tiền, không commit thêm", async () => {
  const r = await runCancel(baseBooking({
    status: "CANCELLED",
    cancelledBy: "USER",
    cancelledAt: "2026-11-23T04:00:00.000Z",
    refundAmount: 57000,
    penaltyAmount: 0,
  }));
  assertEquals(r.status, 200);
  assertEquals([r.body.refundAmount, r.body.penaltyAmount, r.body.cancelledAt], [57000, 0, "2026-11-23T04:00:00.000Z"]);
  assertEquals(r.commits, 0);
});
