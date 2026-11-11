import { assertEquals, assert } from "jsr:@std/assert@1.0.19";
import { handleOwnerApproveBooking } from "./handler.ts";
import type { VerifiedToken } from "../_shared/auth.ts";

Deno.env.set("FIREBASE_PROJECT_ID", "demo-smashnow");

Deno.test("owner-approve-booking: rejects non-POST requests", async () => {
  const req = new Request("https://localhost/owner-approve-booking", { method: "GET" });
  const res = await handleOwnerApproveBooking(req);
  assertEquals(res.status, 405);
});

Deno.test("owner-approve-booking: rejects unauthenticated requests", async () => {
  const req = new Request("https://localhost/owner-approve-booking", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      venueId: "v1",
      bookingId: "b1",
    }),
  });
  const res = await handleOwnerApproveBooking(req);
  assertEquals(res.status, 401);
});

function mockAuth(uid = "owner-1", appRole: "OWNER" | "ADMIN" = "OWNER"): (req: Request) => Promise<VerifiedToken> {
  return async () => ({
    uid,
    appRole,
    email: "owner@test.com",
    emailVerified: true,
    phone: null,
    name: "Owner Test",
    signInProvider: "password",
    authTime: Date.now() / 1000,
  });
}

function mockFirestore(store: Record<string, any>) {
  const commits: any[] = [];
  return {
    get: async (path: string) => store[path] ?? null,
    commit: async (writes: any[]) => {
      commits.push(...writes);
      for (const w of writes) {
        if (w.update) {
          const path = w.update.name.replace(/^projects\/[^/]+\/databases\/\(default\)\/documents\//, "");
          const current = store[path] || {};
          const fields = w.update.fields || {};
          const updated: Record<string, any> = { ...current };
          for (const [k, v] of Object.entries(fields)) {
            if (v && typeof v === "object") {
              if ("stringValue" in v) updated[k] = (v as any).stringValue;
              else if ("timestampValue" in v) updated[k] = (v as any).timestampValue;
              else updated[k] = v;
            }
          }
          store[path] = updated;
        }
      }
    },
    commits,
  };
}

Deno.test("owner-approve-booking: PENDING hop le -> CONFIRMED + qrToken non-empty + confirmedAt set", async () => {
  const store: Record<string, any> = {
    "users/owner-1": { ownerStatus: "APPROVED" },
    "venues/v1": { ownerId: "owner-1", status: "ACTIVE" },
    "bookings/b1": {
      venueId: "v1",
      userId: "player-1",
      status: "PENDING",
      qrToken: null,
      approvalDeadline: new Date(Date.now() + 60000).toISOString(),
    },
  };
  const fs = mockFirestore(store);
  const req = new Request("https://localhost/owner-approve-booking", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ venueId: "v1", bookingId: "b1" }),
  });

  const res = await handleOwnerApproveBooking(req, {
    firestore: fs,
    verifyToken: mockAuth("owner-1"),
  });

  assertEquals(res.status, 200);
  const json = await res.json();
  assertEquals(json.success, true);
  assertEquals(json.status, "CONFIRMED");
  assert(typeof json.qrToken === "string" && json.qrToken.length > 0, "qrToken must be non-empty string");
  assert(typeof json.confirmedAt === "string" && json.confirmedAt.length > 0, "confirmedAt must be set");

  // Verify write occurred
  assertEquals(store["bookings/b1"].status, "CONFIRMED");
  assertEquals(store["bookings/b1"].qrToken, json.qrToken);
});

Deno.test("owner-approve-booking: approve don da CONFIRMED -> 409 va khong doi token", async () => {
  const originalToken = "existing-token-uuid-123";
  const store: Record<string, any> = {
    "users/owner-1": { ownerStatus: "APPROVED" },
    "venues/v1": { ownerId: "owner-1", status: "ACTIVE" },
    "bookings/b1": {
      venueId: "v1",
      userId: "player-1",
      status: "CONFIRMED",
      qrToken: originalToken,
    },
  };
  const fs = mockFirestore(store);
  const req = new Request("https://localhost/owner-approve-booking", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ venueId: "v1", bookingId: "b1" }),
  });

  const res = await handleOwnerApproveBooking(req, {
    firestore: fs,
    verifyToken: mockAuth("owner-1"),
  });

  assertEquals(res.status, 409);
  const json = await res.json();
  assertEquals(json.error.code, "INVALID_BOOKING_STATUS");
  assertEquals(store["bookings/b1"].qrToken, originalToken);
  assertEquals(fs.commits.length, 0);
});

Deno.test("owner-approve-booking: approve qua approvalDeadline -> 409 va khong set token", async () => {
  const store: Record<string, any> = {
    "users/owner-1": { ownerStatus: "APPROVED" },
    "venues/v1": { ownerId: "owner-1", status: "ACTIVE" },
    "bookings/b1": {
      venueId: "v1",
      userId: "player-1",
      status: "PENDING",
      qrToken: null,
      approvalDeadline: new Date(Date.now() - 60000).toISOString(), // expired
    },
  };
  const fs = mockFirestore(store);
  const req = new Request("https://localhost/owner-approve-booking", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ venueId: "v1", bookingId: "b1" }),
  });

  const res = await handleOwnerApproveBooking(req, {
    firestore: fs,
    verifyToken: mockAuth("owner-1"),
  });

  assertEquals(res.status, 409);
  const json = await res.json();
  assertEquals(json.error.code, "DEADLINE_EXPIRED");
  assertEquals(store["bookings/b1"].qrToken, null);
  assertEquals(fs.commits.length, 0);
});
