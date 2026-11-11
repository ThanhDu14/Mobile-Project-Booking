import { assertEquals } from "jsr:@std/assert@1.0.19";
import { handleCheckinBooking } from "./handler.ts";
import { bearer, emulatorToken } from "../_shared/testing.ts";
import type { Write } from "../_shared/firestore.ts";

Deno.test("checkin-booking: rejects non-POST requests", async () => {
  const req = new Request("https://localhost/checkin-booking", { method: "GET" });
  const res = await handleCheckinBooking(req);
  assertEquals(res.status, 405);
});

Deno.test("checkin-booking: rejects unauthenticated requests", async () => {
  const req = new Request("https://localhost/checkin-booking", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      venueId: "v1",
      bookingId: "b1",
      confirmPayment: true,
    }),
  });
  const res = await handleCheckinBooking(req);
  assertEquals(res.status, 401);
});

// ---- Tra đơn ở server (Rules spec 112 T012: client không query qrToken được) ----


Deno.env.set("FIREBASE_PROJECT_ID", Deno.env.get("FIREBASE_PROJECT_ID") ?? "demo-smashnow");

function fakeFirestore(store: Record<string, Record<string, unknown>>) {
  const commits: Write[] = [];
  return {
    commits,
    firestore: {
      get: async (path: string) => store[path] ?? null,
      commit: async (writes: Write[]) => { commits.push(...writes); },
      query: async (collection: string, filters: { field: string; value: unknown }[]) =>
        Object.entries(store)
          .filter(([path, data]) =>
            path.startsWith(`${collection}/`) && filters.every((f) => data[f.field] === f.value))
          .map(([path, data]) => ({ id: path.split("/").pop()!, data })),
    } as never,
  };
}

function seed(): Record<string, Record<string, unknown>> {
  const startAt = new Date(Date.now() + 10 * 60 * 1000).toISOString();
  return {
    "users/owner-a": { ownerStatus: "APPROVED" },
    "users/owner-b": { ownerStatus: "APPROVED" },
    "venues/venue-a": { ownerId: "owner-a", status: "ACTIVE" },
    "bookings/booking-1": { venueId: "venue-a", status: "CONFIRMED", qrToken: "qr-abc", startAt },
  };
}

function checkinAs(uid: string, body: Record<string, unknown>): Request {
  return bearer(emulatorToken({ sub: uid, user_id: uid, appRole: "OWNER" }), {
    body: JSON.stringify(body),
  });
}

Deno.test("checkin-booking: tra theo qrToken không cần venueId, lấy venueId từ đơn", async () => {
  const { firestore, commits } = fakeFirestore(seed());
  const res = await handleCheckinBooking(checkinAs("owner-a", { bookingId: "qr-abc" }), { firestore });
  assertEquals(res.status, 200);
  assertEquals((await res.json()).bookingId, "booking-1");
  assertEquals(commits.length, 1);
});

Deno.test("checkin-booking: check-in bằng mã đơn không cần venueId", async () => {
  const { firestore } = fakeFirestore(seed());
  const res = await handleCheckinBooking(checkinAs("owner-a", { bookingId: "booking-1" }), { firestore });
  assertEquals(res.status, 200);
});

Deno.test("checkin-booking: đơn của cơ sở người khác trả BOOKING_NOT_FOUND, không ghi gì", async () => {
  const { firestore, commits } = fakeFirestore(seed());
  const res = await handleCheckinBooking(checkinAs("owner-b", { bookingId: "qr-abc" }), { firestore });
  assertEquals(res.status, 404);
  assertEquals((await res.json()).error.code, "BOOKING_NOT_FOUND");
  assertEquals(commits.length, 0);
});

Deno.test("checkin-booking: venueId gửi lên khác cơ sở của đơn → BOOKING_NOT_FOUND", async () => {
  const { firestore } = fakeFirestore(seed());
  const res = await handleCheckinBooking(
    checkinAs("owner-a", { venueId: "venue-x", bookingId: "booking-1" }),
    { firestore },
  );
  assertEquals(res.status, 404);
});

Deno.test("checkin-booking: mã không tồn tại → BOOKING_NOT_FOUND", async () => {
  const { firestore } = fakeFirestore(seed());
  const res = await handleCheckinBooking(checkinAs("owner-a", { bookingId: "khong-co" }), { firestore });
  assertEquals(res.status, 404);
});
