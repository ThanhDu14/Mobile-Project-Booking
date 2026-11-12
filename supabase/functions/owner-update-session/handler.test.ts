import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { generateKeyPair, SignJWT } from "npm:jose@6.2.12";
import { __setJwksForTesting } from "../_shared/auth.ts";
import {
  decodeFields,
  documentName,
  FirestoreError,
  type FirestoreTransaction,
  type Write,
} from "../_shared/firestore.ts";
import { handleOwnerCreateSession } from "../owner-create-session/handler.ts";
import { handleOwnerCancelSession } from "../owner-cancel-session/handler.ts";
import { handleOwnerCheckinDropIn } from "../owner-checkin-dropin-registration/handler.ts";
import { handleOwnerUpdateSession } from "./handler.ts";

Deno.env.set("FIREBASE_PROJECT_ID", "demo-owner-dropin");
const { privateKey, publicKey } = await generateKeyPair("RS256");
__setJwksForTesting(() => Promise.resolve(publicKey));
const token = await new SignJWT({ appRole: "OWNER" }).setProtectedHeader({
  alg: "RS256",
})
  .setSubject("owner").setAudience("demo-owner-dropin").setIssuer(
    "https://securetoken.google.com/demo-owner-dropin",
  )
  .setExpirationTime("1h").sign(privateKey);
const draft = {
  venueId: "v1",
  courtIds: ["c1"],
  date: "20261115",
  startTime: "1800",
  endTime: "1900",
  capacity: 8,
  pricePerPerson: 50000,
  level: "BEGINNER",
  note: "",
};
const request = (body: Record<string, unknown>) =>
  new Request("https://localhost/owner-dropin", {
    method: "POST",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
type Doc = Record<string, unknown>;
class Store {
  docs = new Map<string, Doc>([
    ["users/owner", { ownerStatus: "APPROVED" }],
    ["venues/v1", {
      ownerId: "owner",
      districtId: "d1",
      status: "ACTIVE",
      name: "Sân A",
      address: "Địa chỉ A",
    }],
    ["venues/v1/courts/c1", { isActive: true }],
    ["venues/v1/courts/c2", { isActive: true }],
    ["districts/d1", { name: "Quận 1" }],
  ]);
  writes: Write[] = [];
  get = (path: string) =>
    Promise.resolve(structuredClone(this.docs.get(path) ?? null));
  commit = (writes: Write[]): Promise<void> => {
    const next = new Map(this.docs);
    for (const write of writes) {
      const path = (write.update?.name ?? write.delete!).slice(
        documentName("").length,
      );
      if (write.currentDocument?.exists === false && next.has(path)) {
        throw new FirestoreError("ALREADY_EXISTS", 409, path);
      }
      if (write.currentDocument?.exists === true && !next.has(path)) {
        throw new FirestoreError("NOT_FOUND", 404, path);
      }
      if (write.delete) next.delete(path);
      else {next.set(path, {
          ...(write.updateMask ? next.get(path) : {}),
          ...decodeFields(write.update!.fields),
        });}
    }
    this.docs = next;
    this.writes.push(...writes);
    return Promise.resolve();
  };
  runTransaction = async <T>(
    operation: (
      tx: FirestoreTransaction,
    ) => Promise<T | { result: T; writes: Write[] }>,
  ): Promise<T> => {
    const writes: Write[] = [];
    const checkRead = () =>
      assertEquals(writes.length, 0, "No transaction read after a write");
    const result = await operation({
      get: (path) => {
        checkRead();
        return this.get(path);
      },
      query: (collection, filters, options) => {
        checkRead();
        assertEquals(collection, "registrations");
        assert(options?.parent?.startsWith("dropInSessions/"));
        const prefix = `${options!.parent}/${collection}/`;
        return Promise.resolve(
          [...this.docs].filter(([path, data]) =>
            path.startsWith(prefix) && filters.every((filter) => {
              assertEquals(filter.op, "EQUAL");
              return data[filter.field] === filter.value;
            })
          ).slice(0, options?.limit ?? Infinity).map((
            [path, data],
          ) => ({
            path,
            id: path.slice(prefix.length),
            data: structuredClone(data),
          })),
        );
      },
      write: (write) => {
        writes.push(write);
      },
    });
    await this.commit(writes);
    return result as T;
  };
}
async function created() {
  const store = new Store();
  const response = await handleOwnerCreateSession(request(draft), {
    firestore: store,
  });
  assertEquals(response.status, 200);
  const { sessionId } = await response.json();
  return {
    store,
    sessionId: sessionId as string,
    path: `dropInSessions/${sessionId}`,
  };
}

Deno.test("owner create: timestamps, array courts, owner/district and derived snapshot match spec 080", async () => {
  const { store, path } = await created();
  const session = store.docs.get(path)!;
  assertEquals(session.startAt, new Date("2026-11-15T11:00:00Z"));
  assertEquals(session.endAt, new Date("2026-11-15T12:00:00Z"));
  assertEquals(session.courtIds, ["c1"]);
  assertEquals([
    session.ownerId,
    session.districtId,
    session.timeSlot,
    session.date,
  ], ["owner", "d1", "EVENING", "20261115"]);
  assertEquals(session.venueSnapshot, {
    name: "Sân A",
    address: "Địa chỉ A",
    districtName: "Quận 1",
  });
  const fields = store.writes[0].update!.fields;
  assert(
    "timestampValue" in fields.startAt && "timestampValue" in fields.endAt,
  );
});

Deno.test("owner create rejects unsupported levels, impossible dates and off-grid times without writes", async () => {
  for (
    const invalid of [{ level: "ALL" }, { date: "20260230" }, {
      startTime: "1860",
    }, { endTime: "1800" }]
  ) {
    const store = new Store();
    assertEquals(
      (await handleOwnerCreateSession(request({ ...draft, ...invalid }), {
        firestore: store,
      })).status,
      400,
    );
    assertEquals(store.writes, []);
  }
});

Deno.test("owner cancel: multiple paid/check-in registrations refund fully, unpaid stays unpaid, waitlist closes", async () => {
  const { store, sessionId, path } = await created();
  store.docs.set(path, {
    ...store.docs.get(path),
    registeredCount: 4,
    status: "FULL",
    startAt: new Date(0),
  });
  for (
    const [uid, status, paymentStatus, partySize] of [
      ["u1", "REGISTERED", "PAID", 2],
      ["u2", "CHECKED_IN", "PAID", 1],
      ["u3", "REGISTERED", "UNPAID", 1],
    ] as const
  ) {
    store.docs.set(`${path}/registrations/${uid}`, {
      sessionId,
      userId: uid,
      status,
      paymentStatus,
      partySize,
      amount: partySize * 50000,
      qrToken: "secret",
      ticketCode: "DI-ABCDEFGH",
    });
  }
  const response = await handleOwnerCancelSession(
    request({ venueId: "v1", sessionId }),
    { firestore: store },
  );
  assertEquals(response.status, 200);
  for (const uid of ["u1", "u2", "u3"]) {
    const reg = store.docs.get(`${path}/registrations/${uid}`)!;
    assertEquals([reg.status, reg.qrToken, reg.ticketCode, reg.cancelledBy], [
      "CANCELLED",
      null,
      null,
      "OWNER",
    ]);
    assertEquals(
      reg.refundAmount,
      uid === "u1" ? 100000 : uid === "u2" ? 50000 : 0,
    );
    assertEquals(reg.paymentStatus, uid === "u3" ? "UNPAID" : "REFUNDED");
  }
  assertEquals([
    store.docs.get(path)!.status,
    store.docs.get(path)!.registeredCount,
    store.docs.get(path)!.waitlistStatus,
  ], ["CANCELLED", 0, "CLOSED"]);
  assert(![...store.docs.keys()].some((key) => key.startsWith("slots/")));
});

Deno.test("owner check-in reads nested uid registration and rejects repeated/waitlisted check-in", async () => {
  const { store, sessionId, path } = await created();
  store.docs.set(`${path}/registrations/u1`, {
    sessionId,
    userId: "u1",
    status: "REGISTERED",
    qrToken: "A".repeat(43),
  });
  const body = { venueId: "v1", sessionId, credential: "A".repeat(43) };
  assertEquals(
    (await handleOwnerCheckinDropIn(request(body), { firestore: store }))
      .status,
    200,
  );
  assert(
    store.docs.get(`${path}/registrations/u1`)!.checkedInAt instanceof Date,
  );
  assertEquals(
    (await handleOwnerCheckinDropIn(request(body), { firestore: store }))
      .status,
    409,
  );
  store.docs.set(`${path}/registrations/u2`, {
    sessionId,
    userId: "u2",
    status: "WAITLISTED",
    ticketCode: "DI-ABCDEFGH",
  });
  assertEquals(
    (await handleOwnerCheckinDropIn(
      request({ ...body, credential: "DI-ABCDEFGH" }),
      { firestore: store },
    )).status,
    409,
  );
});

Deno.test("owner check-in resolves a normalized manual ticket to its UID without exposing credentials", async () => {
  const { store, sessionId, path } = await created();
  store.docs.set(`${path}/registrations/u1`, {
    sessionId,
    userId: "u1",
    status: "REGISTERED",
    ticketCode: "DI-ABCDEFGH",
  });
  store.docs.set(`${path}/registrations/u2`, {
    sessionId,
    userId: "u2",
    status: "REGISTERED",
    ticketCode: "DI-23456789",
  });
  const response = await handleOwnerCheckinDropIn(
    request({ venueId: "v1", sessionId, credential: "  di-23456789  " }),
    { firestore: store },
  );
  assertEquals(response.status, 200);
  const result = await response.json();
  assertEquals(result.registrationId, "u2");
  assertEquals(result.status, "CHECKED_IN");
  assert(!JSON.stringify(result).includes("DI-23456789"));
  assertEquals(
    store.docs.get(`${path}/registrations/u1`)!.status,
    "REGISTERED",
  );
});

Deno.test("owner check-in rejects UID/malformed credentials and never resolves another session or root collection", async () => {
  const { store, sessionId, path } = await created();
  const reg = {
    sessionId: "other",
    userId: "u1",
    status: "REGISTERED",
    qrToken: "A".repeat(43),
    ticketCode: "DI-ABCDEFGH",
  };
  store.docs.set("dropInSessions/other/registrations/u1", reg);
  store.docs.set("registrations/u1", { ...reg, sessionId });
  const before = structuredClone(store.docs);
  for (
    const [credential, status] of [
      ["u1", 400],
      ["", 400],
      ["dropInSessions/x/registrations/u1", 400],
      ["DI-ABCDEFGH", 404],
      ["A".repeat(43), 404],
      ["a".repeat(43), 404],
    ] as const
  ) {
    const response = await handleOwnerCheckinDropIn(
      request({ venueId: "v1", sessionId, credential }),
      { firestore: store },
    );
    assertEquals(response.status, status);
    assertEquals(store.docs, before);
  }
  // A QR token's case is significant even when a similar token exists in this session.
  store.docs.set(`${path}/registrations/u1`, { ...reg, sessionId });
  assertEquals(
    (await handleOwnerCheckinDropIn(
      request({ venueId: "v1", sessionId, credential: "a".repeat(43) }),
      { firestore: store },
    )).status,
    404,
  );
});

Deno.test("owner check-in denies foreign/unapproved owners and mismatched venue context without writes", async () => {
  for (const scenario of ["foreign", "unapproved", "wrong-session-venue"]) {
    const { store, sessionId, path } = await created();
    store.docs.set(`${path}/registrations/u1`, {
      sessionId,
      userId: "u1",
      status: "REGISTERED",
      ticketCode: "DI-ABCDEFGH",
    });
    if (scenario === "foreign") {
      store.docs.set("venues/v1", { ownerId: "another-owner" });
    }
    if (scenario === "unapproved") {
      store.docs.set("users/owner", { ownerStatus: "REVOKED" });
    }
    if (scenario === "wrong-session-venue") {
      store.docs.set(path, { ...store.docs.get(path), venueId: "v2" });
    }
    const before = structuredClone(store.docs);
    const response = await handleOwnerCheckinDropIn(
      request({ venueId: "v1", sessionId, credential: "DI-ABCDEFGH" }),
      { firestore: store },
    );
    assertEquals(response.status, scenario === "unapproved" ? 403 : 404);
    assertEquals(store.docs, before);
  }
});

Deno.test("owner check-in rejects used/cancelled/waitlisted registrations and closed sessions", async () => {
  for (
    const status of [
      "CHECKED_IN",
      "CANCELLED",
      "WAITLISTED",
      "SESSION_CANCELLED",
      "SESSION_COMPLETED",
    ]
  ) {
    const { store, sessionId, path } = await created();
    store.docs.set(`${path}/registrations/u1`, {
      sessionId,
      userId: "u1",
      status: status.startsWith("SESSION_") ? "REGISTERED" : status,
      ticketCode: "DI-ABCDEFGH",
    });
    if (status.startsWith("SESSION_")) {
      store.docs.set(path, {
        ...store.docs.get(path),
        status: status.slice(8),
      });
    }
    const before = structuredClone(store.docs);
    assertEquals(
      (await handleOwnerCheckinDropIn(
        request({ venueId: "v1", sessionId, credential: "DI-ABCDEFGH" }),
        { firestore: store },
      )).status,
      409,
    );
    assertEquals(store.docs, before);
  }
});

Deno.test("owner check-in fails closed for duplicate credentials or mismatched registration identity", async () => {
  for (const scenario of ["duplicate", "wrong-user", "wrong-session"]) {
    const { store, sessionId, path } = await created();
    const reg = {
      sessionId,
      userId: "u1",
      status: "REGISTERED",
      ticketCode: "DI-ABCDEFGH",
    };
    store.docs.set(`${path}/registrations/u1`, {
      ...reg,
      ...(scenario === "wrong-user" ? { userId: "u2" } : {}),
      ...(scenario === "wrong-session" ? { sessionId: "other" } : {}),
    });
    if (scenario === "duplicate") {
      store.docs.set(`${path}/registrations/u2`, { ...reg, userId: "u2" });
    }
    const before = structuredClone(store.docs);
    assertEquals(
      (await handleOwnerCheckinDropIn(
        request({ venueId: "v1", sessionId, credential: "DI-ABCDEFGH" }),
        { firestore: store },
      )).status,
      404,
    );
    assertEquals(store.docs, before);
  }
});

Deno.test("owner update moves courts/time atomically, recomputes fields, marks registrations and preserves paid amount", async () => {
  const { store, sessionId, path } = await created();
  store.docs.set(path, { ...store.docs.get(path), registeredCount: 2 });
  store.docs.set(`${path}/registrations/u1`, {
    status: "REGISTERED",
    amount: 100000,
  });
  const response = await handleOwnerUpdateSession(
    request({
      ...draft,
      sessionId,
      courtIds: ["c2"],
      startTime: "0800",
      endTime: "0900",
      pricePerPerson: 60000,
    }),
    { firestore: store },
  );
  assertEquals(response.status, 200);
  const session = store.docs.get(path)!;
  assertEquals([session.courtIds, session.timeSlot, session.registeredCount], [
    ["c2"],
    "MORNING",
    2,
  ]);
  assertEquals(session.startAt, new Date("2026-11-15T01:00:00Z"));
  assertEquals(store.docs.get(`${path}/registrations/u1`)!.amount, 100000);
  assertEquals(
    store.docs.get(`${path}/registrations/u1`)!.sessionChanged,
    true,
  );
  assertEquals(
    [...store.docs.keys()].filter((key) => key.startsWith("slots/")).sort(),
    ["slots/c2_20261115_0800", "slots/c2_20261115_0830"],
  );
});

Deno.test("owner update rejects slot conflict, low capacity, wrong owner, closed session with no partial writes", async () => {
  for (const scenario of ["conflict", "capacity", "owner", "closed"]) {
    const { store, sessionId, path } = await created();
    if (scenario === "conflict") {
      store.docs.set("slots/c2_20261115_1800", { status: "BOOKED" });
    }
    if (scenario === "capacity") {
      store.docs.set(path, { ...store.docs.get(path), registeredCount: 10 });
    }
    if (scenario === "owner") {
      store.docs.set("venues/v1", { ownerId: "someone-else" });
    }
    if (scenario === "closed") {
      store.docs.set(path, { ...store.docs.get(path), status: "CANCELLED" });
    }
    const before = structuredClone(store.docs);
    const response = await handleOwnerUpdateSession(
      request({ ...draft, sessionId, courtIds: ["c2"] }),
      { firestore: store },
    );
    assertEquals(response.status, scenario === "owner" ? 404 : 409);
    assertEquals(store.docs, before);
  }
});

Deno.test("owner update keeps own slots when only capacity changes and transitions FULL to OPEN", async () => {
  const { store, sessionId, path } = await created();
  store.docs.set(path, {
    ...store.docs.get(path),
    registeredCount: 8,
    status: "FULL",
  });
  assertEquals(
    (await handleOwnerUpdateSession(
      request({ ...draft, sessionId, capacity: 10 }),
      { firestore: store },
    )).status,
    200,
  );
  assertEquals(store.docs.get(path)!.status, "OPEN");
  assertEquals(
    [...store.docs.keys()].filter((key) => key.startsWith("slots/")).length,
    2,
  );
});
