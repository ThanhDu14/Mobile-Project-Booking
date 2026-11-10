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
