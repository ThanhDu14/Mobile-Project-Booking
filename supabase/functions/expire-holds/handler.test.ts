import { assertEquals } from "jsr:@std/assert@1.0.19";
import { handleExpireHolds } from "./handler.ts";

Deno.env.set("FIREBASE_PROJECT_ID", "demo-smashnow");

const now = new Date("2026-11-10T12:00:00.000Z");
const cronSecret = "test-cron-secret";

function request(method = "POST", secret = cronSecret) {
  return new Request("https://localhost/expire-holds", {
    method,
    headers: { "x-cron-secret": secret },
  });
}

Deno.test("expire-holds: requires POST and a configured cron secret", async () => {
  const firestore = {
    query: async () => [],
    runTransaction: async () => false,
  } as any;
  assertEquals(
    (await handleExpireHolds(request("GET"), {
      firestore,
      cronSecret,
      now: () => now,
    })).status,
    405,
  );
  assertEquals(
    (await handleExpireHolds(request("POST", "wrong"), {
      firestore,
      cronSecret,
      now: () => now,
    })).status,
    401,
  );
  assertEquals(
    (await handleExpireHolds(request(), {
      firestore,
      cronSecret: "",
      now: () => now,
    })).status,
    401,
  );
});

Deno.test("expire-holds: atomically expires stale single booking and only its held slots", async () => {
  const commits: any[][] = [];
  const firestore = {
    query: async (collection: string) =>
      collection === "bookings"
        ? [{
          id: "b1",
          path: "bookings/b1",
          data: { status: "HOLD", holdExpiresAt: new Date(now.getTime() - 1) },
        }]
        : [],
    runTransaction: async (operation: (tx: any) => Promise<any>) => {
      const out = await operation({
        get: async (path: string) => {
          if (path === "bookings/b1") {
            return {
              status: "HOLD",
              userId: "u1",
              holdExpiresAt: new Date(now.getTime() - 1),
              items: [{ slotId: "slot-a" }, { slotId: "slot-b" }],
            };
          }
          if (path === "slots/slot-a") {
            return {
              status: "HELD",
              bookingId: "b1",
              holdBy: "u1",
              holdExpiresAt: new Date(now.getTime() - 1),
            };
          }
          if (path === "slots/slot-b") {
            return { status: "BOOKED", bookingId: "other", holdBy: "u2" };
          }
          return null;
        },
      });
      if (out.writes.length) commits.push(out.writes);
      return out.result;
    },
  };
  const response = await handleExpireHolds(request(), {
    firestore,
    cronSecret,
    now: () => now,
  });
  const result = await response.json();
  assertEquals(response.status, 200);
  assertEquals(result.expired, 1);
  assertEquals(commits.length, 1);
  assertEquals(commits[0].length, 2);
  assertEquals(commits[0][0].update.fields.status.stringValue, "EXPIRED");
  assertEquals(commits[0].filter((w) => w.delete).length, 1);
});

Deno.test("expire-holds: does not update a booking confirmed after the stale scan", async () => {
  let writesCommitted = 0;
  const firestore = {
    query: async () => [{
      id: "b1",
      path: "bookings/b1",
      data: { status: "HOLD", holdExpiresAt: new Date(now.getTime() - 1) },
    }],
    runTransaction: async (operation: (tx: any) => Promise<any>) => {
      const out = await operation({
        get: async () => ({
          status: "PENDING",
          holdExpiresAt: new Date(now.getTime() - 1),
        }),
      });
      if (out.writes.length) writesCommitted++;
      return out.result;
    },
  };
  const response = await handleExpireHolds(request(), {
    firestore,
    cronSecret,
    now: () => now,
  });
  assertEquals(response.status, 200);
  assertEquals(writesCommitted, 0);
});

Deno.test("expire-holds: skips a slot renewed after the stale scan", async () => {
  let writesCommitted = 0;
  const firestore = {
    query: async (collection: string) =>
      collection === "slots"
        ? [{
          id: "slot-a",
          path: "slots/slot-a",
          data: { status: "HELD", holdExpiresAt: new Date(now.getTime() - 1) },
        }]
        : [],
    runTransaction: async (operation: (tx: any) => Promise<any>) => {
      const out = await operation({
        get: async () => ({
          status: "HELD",
          holdExpiresAt: new Date(now.getTime() + 60000),
        }),
      });
      if (out.writes.length) writesCommitted++;
      return out.result;
    },
  };
  const response = await handleExpireHolds(request(), {
    firestore,
    cronSecret,
    now: () => now,
  });
  assertEquals(response.status, 200);
  assertEquals(writesCommitted, 0);
});

Deno.test("expire-holds: leaves legacy holds without expiry untouched", async () => {
  let writesCommitted = 0;
  const firestore = {
    query: async (collection: string) =>
      collection === "bookings"
        ? [{
          id: "legacy",
          path: "bookings/legacy",
          data: { status: "HOLD", holdExpiresAt: null },
        }]
        : [],
    runTransaction: async () => {
      writesCommitted++;
      return true;
    },
  } as any;
  const response = await handleExpireHolds(request(), {
    firestore,
    cronSecret,
    now: () => now,
  });
  const result = await response.json();
  assertEquals(response.status, 200);
  assertEquals(result.skipped, 1);
  assertEquals(writesCommitted, 0);
});

Deno.test("expire-holds: atomically expires recurring group, sessions, and matching held slots", async () => {
  const commits: any[][] = [];
  const firestore = {
    query: async (collection: string, filters: any[] = []) => {
      if (collection === "bookings" && filters[0]?.field === "status") {
        return [];
      }
      if (collection === "recurringGroups") {
        return [{
          id: "g1",
          path: "recurringGroups/g1",
          data: {
            status: "HOLD",
            holdExpiresAt: new Date(now.getTime() - 1000),
          },
        }];
      }
      return [];
    },
    runTransaction: async (operation: (tx: any) => Promise<any>) => {
      const out = await operation({
        get: async (path: string) => {
          if (path === "recurringGroups/g1") {
            return {
              status: "HOLD",
              userId: "u1",
              holdExpiresAt: new Date(now.getTime() - 1000),
            };
          }
          if (path === "slots/slot-a") {
            return {
              status: "HELD",
              bookingId: "g1",
              holdBy: "u1",
              holdExpiresAt: new Date(now.getTime() - 1000),
            };
          }
          return null;
        },
        query: async () => [{
          id: "s1",
          path: "bookings/s1",
          data: { status: "HOLD", slotIds: ["slot-a"] },
        }],
      });
      if (out.writes.length) commits.push(out.writes);
      return out.result;
    },
  };
  const response = await handleExpireHolds(request(), {
    firestore,
    cronSecret,
    now: () => now,
  });
  const result = await response.json();
  assertEquals(response.status, 200);
  assertEquals(result.expired, 1);
  assertEquals(commits.length, 1);
  assertEquals(commits[0].length, 3);
});

// ---------- T050 (spec 050 US3): đơn chờ chuyển khoản hết hạn ----------
import { type Doc, FakeFirestore } from "../_shared/fake-firestore.ts";

function paymentSetup(seed: Record<string, Doc>) {
  const fs = new FakeFirestore(() => now);
  for (const [p, d] of Object.entries(seed)) fs.seed(p, d);
  const firestore = {
    query: (...a: Parameters<FakeFirestore["query"]>) => Promise.resolve(fs.query(...a)),
    runTransaction: fs.runTransaction,
  } as any;
  return { fs, run: () => handleExpireHolds(request(), { firestore, cronSecret, now: () => now }) };
}

const stale = new Date(now.getTime() - 60_000);
const fresh = new Date(now.getTime() + 60_000);

Deno.test("expire-holds: đơn HOLD chờ chuyển khoản hết hạn → EXPIRED, nhả voucher RESERVED, tắt mã", async () => {
  const { fs, run } = paymentSetup({
    "bookings/b1": {
      userId: "p1", venueId: "v1", status: "HOLD", paymentStatus: "AWAITING_TRANSFER", paymentCode: "SNPAAAA2222",
      voucherCode: "SALE20", voucherScope: "SYSTEM", holdExpiresAt: stale, items: [{ slotId: "c1_20261110_1800" }],
    },
    "slots/c1_20261110_1800": { status: "HELD", holdBy: "p1", bookingId: "b1", holdExpiresAt: stale },
    "paymentCodes/SNPAAAA2222": { bookingId: "b1", active: true },
    "systemVouchers/SALE20": { usedCount: 3 },
    "systemVouchers/SALE20/userUsage/p1": { count: 1 },
    "systemVouchers/SALE20/redemptions/b1": { userId: "p1", status: "RESERVED" },
  });
  assertEquals((await run()).status, 200);
  assertEquals(fs.read("bookings/b1")!.status, "EXPIRED");
  assertEquals(fs.read("slots/c1_20261110_1800"), null);
  assertEquals(fs.read("paymentCodes/SNPAAAA2222")!.active, false);
  assertEquals(fs.read("systemVouchers/SALE20")!.usedCount, 2);
  assertEquals(fs.read("systemVouchers/SALE20/userUsage/p1")!.count, 0);
  assertEquals(fs.read("systemVouchers/SALE20/redemptions/b1"), null);
});

Deno.test("expire-holds: voucher đã USED thì giữ nguyên lượt", async () => {
  const { fs, run } = paymentSetup({
    "bookings/b1": { userId: "p1", status: "HOLD", voucherCode: "SALE20", voucherScope: "SYSTEM", holdExpiresAt: stale },
    "systemVouchers/SALE20": { usedCount: 3 },
    "systemVouchers/SALE20/redemptions/b1": { userId: "p1", status: "USED" },
  });
  await run();
  assertEquals(fs.read("systemVouchers/SALE20")!.usedCount, 3);
  assertEquals(fs.read("systemVouchers/SALE20/redemptions/b1")!.status, "USED");
});

Deno.test("expire-holds: nhóm định kỳ chờ chuyển khoản hết hạn → nhả voucher cơ sở, tắt mã", async () => {
  const { fs, run } = paymentSetup({
    "recurringGroups/g1": {
      userId: "p1", venueId: "v1", status: "HOLD", paymentStatus: "AWAITING_TRANSFER", paymentCode: "SNPBBBB3333",
      voucherCode: "VIP", voucherScope: "VENUE", holdExpiresAt: stale,
    },
    "bookings/s1": { userId: "p1", recurringGroupId: "g1", status: "HOLD", slotIds: ["c1_20261117_1800"], holdExpiresAt: stale },
    "slots/c1_20261117_1800": { status: "HELD", holdBy: "p1", bookingId: "g1", holdExpiresAt: stale },
    "paymentCodes/SNPBBBB3333": { recurringGroupId: "g1", active: true },
    "venues/v1/vouchers/VIP": { usedCount: 1 },
    "venues/v1/vouchers/VIP/userUsage/p1": { count: 1 },
    "venues/v1/vouchers/VIP/redemptions/g1": { userId: "p1", status: "RESERVED" },
  });
  await run();
  assertEquals(fs.read("recurringGroups/g1")!.status, "EXPIRED");
  assertEquals(fs.read("bookings/s1")!.status, "EXPIRED");
  assertEquals(fs.read("slots/c1_20261117_1800"), null);
  assertEquals(fs.read("paymentCodes/SNPBBBB3333")!.active, false);
  assertEquals(fs.read("venues/v1/vouchers/VIP")!.usedCount, 0);
  assertEquals(fs.read("venues/v1/vouchers/VIP/redemptions/g1"), null);
});

Deno.test("expire-holds: đơn chờ chuyển khoản còn hạn thì không đổi", async () => {
  const { fs, run } = paymentSetup({
    "bookings/b1": { userId: "p1", status: "HOLD", paymentCode: "SNPAAAA2222", voucherCode: "SALE20", voucherScope: "SYSTEM", holdExpiresAt: fresh },
    "paymentCodes/SNPAAAA2222": { bookingId: "b1", active: true },
    "systemVouchers/SALE20/redemptions/b1": { userId: "p1", status: "RESERVED" },
  });
  await run();
  assertEquals(fs.read("bookings/b1")!.status, "HOLD");
  assertEquals(fs.read("paymentCodes/SNPAAAA2222")!.active, true);
  assertEquals(fs.read("systemVouchers/SALE20/redemptions/b1")!.status, "RESERVED");
});
