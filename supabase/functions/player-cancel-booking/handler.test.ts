import { assertEquals } from "jsr:@std/assert@1.0.19";
import { handlePlayerCancelBooking } from "./handler.ts";

Deno.env.set("FIREBASE_PROJECT_ID", "demo-smashnow");

Deno.test("player-cancel-booking: rejects non-POST requests", async () => {
  const res = await handlePlayerCancelBooking(new Request("https://localhost/player-cancel-booking", { method: "GET" }));
  assertEquals(res.status, 405);
});

Deno.test("player-cancel-booking: rejects unauthenticated requests", async () => {
  const req = new Request("https://localhost/player-cancel-booking", {
    method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ bookingId: "b1" }),
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
      get: async () => { reads++; return null; },
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
            status: "PENDING",
            paidAmount: 57000,
            paymentMethod: "DEPOSIT",
            startAt: new Date(Date.now() + 3600000 * 5).toISOString(),
            items: [{ slotId: "court-1_20261110_0500" }, { slotId: "court-1_20261110_0530" }],
          };
        }
        if (path === "refunds/refund_b-pending") return null;
        if (path === "users/user-1") return { walletBalance: 10000 };
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

  // Check committed writes: booking update, 2 slot deletes, user balance update, refund doc create
  const slotDeletes = committedWrites.filter((w) => w.delete && w.delete.includes("slots/"));
  assertEquals(slotDeletes.length, 2);
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

