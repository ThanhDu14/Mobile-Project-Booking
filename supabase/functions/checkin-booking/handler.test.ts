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


Deno.env.set("FIREBASE_PROJECT_ID", "demo-owner-checkin");
Deno.env.set("FIREBASE_AUTH_EMULATOR_HOST", Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST") ?? "127.0.0.1:9099");

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
    "users/owner-a": { ownerStatus: "APPROVED", role:"OWNER", accountStatus:"ACTIVE" },
    "users/owner-b": { ownerStatus: "APPROVED", role:"OWNER", accountStatus:"ACTIVE" },
    "venues/venue-a": { ownerId: "owner-a", status: "ACTIVE" },
    "bookings/booking-1": { venueId: "venue-a", status: "CONFIRMED", qrToken: "qr-abc", startAt,
      endAt:new Date(Date.now()+3600000).toISOString(),total:100000,paymentStatus:"PAID",paymentMethod:"MOCK_WALLET" },
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

Deno.test("checkin-booking: preview cash still owed does not check in; explicit confirmation updates amounts",async()=>{
  const store=seed();Object.assign(store["bookings/booking-1"],{paymentMethod:"AT_VENUE",paymentStatus:"UNPAID"});
  const {firestore,commits}=fakeFirestore(store);
  const preview=await handleCheckinBooking(checkinAs("owner-a",{bookingId:"booking-1",confirmPayment:false}),{firestore});
  assertEquals(preview.status,200);
  const data=await preview.json();assertEquals(data.requiresPaymentConfirmation,true);assertEquals(data.remainingAmount,100000);
  assertEquals(commits.length,0);
  const checked=await handleCheckinBooking(checkinAs("owner-a",{bookingId:"booking-1",confirmPayment:true}),{firestore});
  assertEquals(checked.status,200);await checked.body?.cancel();
  const {decodeFields}=await import("../_shared/firestore.ts");
  const fields=decodeFields(commits[0].update!.fields);
  assertEquals(fields.paymentStatus,"PAID");assertEquals(fields.paidAmount,100000);assertEquals(fields.remainingAmount,0);
});

Deno.test("checkin-booking: ended booking and blocked current owner cannot mutate",async()=>{
  for(const blocked of [false,true]){
    const store=seed();
    if(blocked)store["users/owner-a"].accountStatus="BLOCKED";
    else Object.assign(store["bookings/booking-1"],{startAt:new Date(Date.now()-3600000),endAt:new Date(Date.now()-1000)});
    const {firestore,commits}=fakeFirestore(store);
    const response=await handleCheckinBooking(checkinAs("owner-a",{bookingId:"booking-1"}),{firestore});
    assertEquals(response.status,blocked?403:400);await response.body?.cancel();assertEquals(commits.length,0);
  }
});
