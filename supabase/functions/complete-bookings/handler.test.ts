import { assertEquals, assert } from "jsr:@std/assert@1.0.19";
import { handleCompleteBookings } from "./handler.ts";

Deno.env.set("FIREBASE_PROJECT_ID", "demo-smashnow");

interface MockDoc {
  id: string;
  path: string;
  data: Record<string, any>;
}

function createMockFirestore(docs: MockDoc[]) {
  const store = new Map<string, Record<string, any>>();
  for (const d of docs) {
    store.set(d.id, { ...d.data });
  }

  return {
    get: async (path: string) => {
      const id = path.split("/").pop()!;
      return store.get(id) ?? null;
    },
    query: async (collectionId: string, filters: any[]) => {
      const statusFilter = filters.find((f) => f.field === "status");
      const results: MockDoc[] = [];
      for (const [id, data] of store.entries()) {
        if (statusFilter && data.status !== statusFilter.value) continue;
        results.push({ id, path: `${collectionId}/${id}`, data: { ...data } });
      }
      return results;
    },
    commit: async (writes: any[]) => {
      for (const w of writes) {
        if (w.update) {
          const path = w.update.name.replace(/^projects\/[^/]+\/databases\/\(default\)\/documents\//, "");
          const id = path.split("/").pop()!;
          const current = store.get(id) || {};
          const fields = w.update.fields || {};
          const updated: Record<string, any> = { ...current };
          for (const [k, v] of Object.entries(fields)) {
            if (v && typeof v === "object") {
              if ("stringValue" in v) updated[k] = (v as any).stringValue;
              else if ("timestampValue" in v) updated[k] = (v as any).timestampValue;
              else updated[k] = v;
            }
          }
          store.set(id, updated);
        }
      }
    },
    getDoc: (id: string) => store.get(id),
  };
}

Deno.test("complete-bookings: da check-in + qua endAt -> COMPLETED (+ completedAt)", async () => {
  const serverNow = new Date("2026-11-09T22:00:00Z");
  const pastEndAt = new Date("2026-11-09T21:30:00Z");

  const fs = createMockFirestore([
    {
      id: "b-checked-in",
      path: "bookings/b-checked-in",
      data: {
        status: "CONFIRMED",
        endAt: pastEndAt,
        checkedInAt: "2026-11-09T20:00:00Z",
      },
    },
  ]);

  const req = new Request("https://localhost/complete-bookings", { method: "POST" });
  const res = await handleCompleteBookings(req, {
    firestore: fs,
    now: () => serverNow,
  });

  assertEquals(res.status, 200);
  const json = await res.json();
  assertEquals(json.completed, 1);
  assertEquals(json.noShow, 0);

  const updated = fs.getDoc("b-checked-in")!;
  assertEquals(updated.status, "COMPLETED");
  assertEquals(updated.completedAt, serverNow.toISOString());
  assertEquals(updated.updatedAt, serverNow.toISOString());
});

Deno.test("complete-bookings: chua check-in + qua endAt -> NO_SHOW", async () => {
  const serverNow = new Date("2026-11-09T22:00:00Z");
  const pastEndAt = new Date("2026-11-09T21:30:00Z");

  const fs = createMockFirestore([
    {
      id: "b-no-checkin",
      path: "bookings/b-no-checkin",
      data: {
        status: "CONFIRMED",
        endAt: pastEndAt,
        checkedInAt: null,
      },
    },
  ]);

  const req = new Request("https://localhost/complete-bookings", { method: "POST" });
  const res = await handleCompleteBookings(req, {
    firestore: fs,
    now: () => serverNow,
  });

  assertEquals(res.status, 200);
  const json = await res.json();
  assertEquals(json.completed, 0);
  assertEquals(json.noShow, 1);

  const updated = fs.getDoc("b-no-checkin")!;
  assertEquals(updated.status, "NO_SHOW");
  assertEquals(updated.completedAt, undefined);
  assertEquals(updated.updatedAt, serverNow.toISOString());
});

Deno.test("complete-bookings: CONFIRMED chua toi endAt -> giu nguyen", async () => {
  const serverNow = new Date("2026-11-09T20:00:00Z");
  const futureEndAt = new Date("2026-11-09T21:30:00Z");

  const fs = createMockFirestore([
    {
      id: "b-future",
      path: "bookings/b-future",
      data: {
        status: "CONFIRMED",
        endAt: futureEndAt,
        checkedInAt: "2026-11-09T19:50:00Z",
      },
    },
  ]);

  const req = new Request("https://localhost/complete-bookings", { method: "POST" });
  const res = await handleCompleteBookings(req, {
    firestore: fs,
    now: () => serverNow,
  });

  assertEquals(res.status, 200);
  const json = await res.json();
  assertEquals(json.completed, 0);
  assertEquals(json.noShow, 0);
  assertEquals(json.processed, 0);

  const doc = fs.getDoc("b-future")!;
  assertEquals(doc.status, "CONFIRMED");
});

Deno.test("complete-bookings: PENDING va HOLD khong bi anh huong", async () => {
  const serverNow = new Date("2026-11-09T22:00:00Z");
  const pastEndAt = new Date("2026-11-09T21:30:00Z");

  const fs = createMockFirestore([
    {
      id: "b-pending",
      path: "bookings/b-pending",
      data: {
        status: "PENDING",
        endAt: pastEndAt,
      },
    },
    {
      id: "b-hold",
      path: "bookings/b-hold",
      data: {
        status: "HOLD",
        endAt: pastEndAt,
      },
    },
  ]);

  const req = new Request("https://localhost/complete-bookings", { method: "POST" });
  const res = await handleCompleteBookings(req, {
    firestore: fs,
    now: () => serverNow,
  });

  assertEquals(res.status, 200);
  const json = await res.json();
  assertEquals(json.completed, 0);
  assertEquals(json.noShow, 0);
  assertEquals(json.scanned, 0);

  assertEquals(fs.getDoc("b-pending")!.status, "PENDING");
  assertEquals(fs.getDoc("b-hold")!.status, "HOLD");
});

Deno.test("complete-bookings: chay lai lan 2 (idempotent) -> khong doi gi", async () => {
  const serverNow = new Date("2026-11-09T22:00:00Z");
  const pastEndAt = new Date("2026-11-09T21:30:00Z");

  const fs = createMockFirestore([
    {
      id: "b1",
      path: "bookings/b1",
      data: {
        status: "CONFIRMED",
        endAt: pastEndAt,
        checkedInAt: "2026-11-09T20:00:00Z",
      },
    },
    {
      id: "b2",
      path: "bookings/b2",
      data: {
        status: "CONFIRMED",
        endAt: pastEndAt,
        checkedInAt: null,
      },
    },
  ]);

  const req1 = new Request("https://localhost/complete-bookings", { method: "POST" });
  const res1 = await handleCompleteBookings(req1, {
    firestore: fs,
    now: () => serverNow,
  });
  assertEquals(res1.status, 200);
  const json1 = await res1.json();
  assertEquals(json1.completed, 1);
  assertEquals(json1.noShow, 1);

  // Run 2: idempotent
  const req2 = new Request("https://localhost/complete-bookings", { method: "POST" });
  const res2 = await handleCompleteBookings(req2, {
    firestore: fs,
    now: () => serverNow,
  });
  assertEquals(res2.status, 200);
  const json2 = await res2.json();
  assertEquals(json2.scanned, 0);
  assertEquals(json2.completed, 0);
  assertEquals(json2.noShow, 0);
  assertEquals(json2.processed, 0);
});
