import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import {
  decodeFields,
  type FirestoreTransaction,
  type QueryFilter,
  type QueryOptions,
  type Write,
} from "./firestore.ts";
import { closeWaitlistInTx, promoteFromWaitlist } from "./waitlist.ts";
import { handleDropinClose } from "../dropin-close/handler.ts";

const now = new Date("2026-11-20T01:00:00Z");
const startAt = new Date(now.getTime() + 2 * 3600_000);
const endAt = new Date(startAt.getTime() + 3600_000);
class Store {
  docs = new Map<string, Record<string, unknown>>();
  get = (path: string) =>
    Promise.resolve(structuredClone(this.docs.get(path) ?? null));
  query = (
    collection: string,
    filters: QueryFilter[],
    options: QueryOptions = {},
  ) =>
    Promise.resolve(
      [...this.docs].filter(([path, data]) => {
        const segments = path.split("/");
        return segments.at(-2) === collection &&
          (options.allDescendants || path.startsWith(
            options.parent
              ? `${options.parent}/${collection}/`
              : `${collection}/`,
          )) &&
          filters.every((f) =>
            f.op === "IN"
              ? (f.value as unknown[]).includes(data[f.field])
              : data[f.field] === f.value
          );
      }).map(([path, data]) => ({
        path,
        id: path.split("/").at(-1)!,
        data: structuredClone(data),
      })),
    );
  runTransaction = async <T>(
    fn: (
      tx: FirestoreTransaction,
    ) => Promise<T | { result: T; writes: Write[] }>,
  ): Promise<T> => {
    const writes: Write[] = [];
    const check = () => assertEquals(writes.length, 0, "No reads after writes");
    const result = await fn({
      get: (path) => {
        check();
        return this.get(path);
      },
      query: (...args) => {
        check();
        return this.query(...args);
      },
      write: (w) => {
        writes.push(w);
      },
    });
    for (const w of writes) {
      const path = w.update!.name.split("/documents/")[1];
      this.docs.set(path, {
        ...this.docs.get(path),
        ...decodeFields(w.update!.fields),
      });
    }
    return result as T;
  };
}
function fixture() {
  if (!Deno.env.get("FIREBASE_PROJECT_ID")) Deno.env.set("FIREBASE_PROJECT_ID", "demo-waitlist-unit");
  const store = new Store();
  const session = {
    id: "s",
    status: "OPEN",
    capacity: 10,
    registeredCount: 9,
    startAt,
    endAt,
    waitlistTickets: [1, 2],
    waitlistCount: 2,
  };
  store.docs.set("dropInSessions/s", session);
  for (const [uid, partySize, ticket] of [["a", 2, 1], ["b", 1, 2]] as const) {
    store.docs.set(`dropInSessions/s/registrations/${uid}`, {
      userId: uid,
      sessionId: "s",
      partySize,
      waitlistTicket: ticket,
      status: "WAITLISTED",
      paymentMethod: "MOCK_PAY",
      paymentStatus: null,
      pricePerPerson: 60000,
      amount: partySize * 60000,
      sessionStartAt: startAt,
      sessionEndAt: endAt,
    });
  }
  return { store, session };
}

Deno.test("waitlist promotion skips oversized party, preserves price, creates ticket, removes overlaps atomically", async () => {
  const { store, session } = fixture();
  store.docs.set("dropInSessions/other", {
    waitlistTickets: [4],
    waitlistCount: 1,
  });
  store.docs.set("dropInSessions/other/registrations/b", {
    userId: "b",
    status: "WAITLISTED",
    waitlistTicket: 4,
    sessionStartAt: startAt,
    sessionEndAt: endAt,
  });
  const result = await store.runTransaction((tx) =>
    promoteFromWaitlist(tx, session, now)
  );
  assertEquals(result.promoted, [{ uid: "b", autoLeftSessionIds: ["other"] }]);
  const reg = store.docs.get("dropInSessions/s/registrations/b")!;
  assertEquals([reg.status, reg.amount, reg.paymentStatus], [
    "REGISTERED",
    60000,
    "PAID",
  ]);
  assert(typeof reg.qrToken === "string" && reg.qrToken.length === 43);
  assertEquals(
    store.docs.get("dropInSessions/s/registrations/a")!.status,
    "WAITLISTED",
  );
  assertEquals([
    session.registeredCount,
    session.status,
    session.waitlistTickets,
  ], [10, "FULL", [1]]);
  assertEquals(
    store.docs.get("dropInSessions/other/registrations/b")!.waitlistEndReason,
    "AUTO_LEFT_OVERLAP",
  );
  assertEquals(store.docs.get("dropInSessions/other")!.waitlistCount, 0);
  const snapshot = structuredClone(store.docs);
  await store.runTransaction((tx) => promoteFromWaitlist(tx, session, now));
  assertEquals(store.docs, snapshot);
});

Deno.test("waitlist closes all entries without charging and sends exactly one inbox notification per entry", async () => {
  const { store, session } = fixture();
  await store.runTransaction((tx) =>
    closeWaitlistInTx(tx, session, "SESSION_CANCELLED", now)
  );
  for (const uid of ["a", "b"]) {
    const reg = store.docs.get(`dropInSessions/s/registrations/${uid}`)!;
    assertEquals([
      reg.status,
      reg.cancelledBy,
      reg.waitlistEndReason,
      reg.paymentStatus,
    ], ["CANCELLED", "OWNER", "SESSION_CANCELLED", null]);
  }
  assertEquals([session.waitlistCount, session.waitlistTickets], [0, []]);
  const snapshot = structuredClone(store.docs);
  await store.runTransaction((tx) =>
    closeWaitlistInTx(tx, session, "SESSION_CANCELLED", now)
  );
  assertEquals(store.docs, snapshot);
});

Deno.test("promotion respects exact T-30 cutoff and cancelled sessions", async () => {
  for (const state of ["OPEN", "CANCELLED", "COMPLETED"]) {
    const { store, session } = fixture();
    session.status = state;
    session.startAt = new Date(now.getTime() + 30 * 60_000);
    const snapshot = structuredClone(store.docs);
    assertEquals(
      (await store.runTransaction((tx) =>
        promoteFromWaitlist(tx, session, now)
      )).promoted,
      [],
    );
    assertEquals(store.docs, snapshot);
  }
});

Deno.test("dropin-close authenticates, closes at T-30, completes ended sessions and is idempotent", async () => {
  const { store, session } = fixture();
  session.startAt = new Date(now.getTime() + 30 * 60_000);
  store.docs.set("dropInSessions/ended", {
    startAt: new Date(0),
    endAt: new Date(1),
    status: "FULL",
  });
  const deps = { firestore: store, cronSecret: "test", now: () => now };
  const request = (secret: string) =>
    new Request("https://localhost/dropin-close", {
      method: "POST",
      headers: { "x-cron-secret": secret },
    });
  assertEquals((await handleDropinClose(request("wrong"), deps)).status, 401);
  assertEquals(
    (await handleDropinClose(request("test"), { ...deps, cronSecret: "" }))
      .status,
    401,
  );
  assertEquals(await (await handleDropinClose(request("test"), deps)).json(), {
    closedSessions: 1,
    closedEntries: 2,
    completedSessions: 1,
  });
  assertEquals(store.docs.get("dropInSessions/ended")!.status, "COMPLETED");
  assertEquals(
    store.docs.get("dropInSessions/s/registrations/a")!.waitlistEndReason,
    "CLOSED",
  );
  const snapshot = structuredClone(store.docs);
  assertEquals(await (await handleDropinClose(request("test"), deps)).json(), {
    closedSessions: 0,
    closedEntries: 0,
    completedSessions: 0,
  });
  assertEquals(store.docs, snapshot);
});
