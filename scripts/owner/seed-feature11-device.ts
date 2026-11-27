// Fixed local test namespace. Never seed a hosted project or a user's real account.
import { assert } from "jsr:@std/assert@1.0.19";
import * as fs from "../../supabase/functions/_shared/firestore.ts";
import { createUser } from "../../supabase/functions/_shared/testing.ts";
import { mergeClaims } from "../../supabase/functions/_shared/identity.ts";
import { deriveSessionFields } from "../../supabase/functions/_shared/dropin.ts";
assert(fs.projectId() === "demo-admin-repair");
assert(Deno.env.get("FIRESTORE_EMULATOR_HOST") === "127.0.0.1:18080");
assert(Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST") === "127.0.0.1:19099");
const docs: Record<string, Record<string, unknown>> = {};
for (
  const [uid, role] of [["feature11-owner", "OWNER"], [
    "feature11-player",
    "PLAYER",
  ]]
) {
  try {
    await createUser({
      localId: uid,
      email: `${uid}@example.test`,
      password: "Test123456",
      emailVerified: true,
    });
  } catch (error) {
    if (
      !/EMAIL_EXISTS|LOCAL_ID_EXISTS|DUPLICATE_LOCAL_ID/.test(String(error))
    ) throw error;
  }
  await mergeClaims(uid, { appRole: role, role: "authenticated" });
  docs[`users/${uid}`] = {
    role,
    ownerStatus: role === "OWNER" ? "APPROVED" : "NONE",
    accountStatus: "ACTIVE",
    displayName: uid,
    walletBalance: 0,
  };
}
const now = new Date(),
  day = (offset: number) =>
    new Date(now.getTime() + offset * 86400000 + 7 * 3600000).toISOString()
      .slice(0, 10).replaceAll("-", "");
const at = (date: string, time: string) =>
  new Date(
    `${date.slice(0, 4)}-${date.slice(4, 6)}-${date.slice(6, 8)}T${
      time.slice(0, 2)
    }:${time.slice(2)}:00+07:00`,
  );
const venue = {
  ownerId: "feature11-owner",
  address: "TEST local only",
  districtId: "device-district",
  status: "ACTIVE",
  openTime: "0600",
  closeTime: "2300",
  phone: "0900000000",
  location: new fs.FirestoreGeoPoint(10.7, 106.6),
  amenityIds: [],
  photoPaths: ["https://example.test/TEST.jpg"],
  courtCount: 2,
  acceptsRecurring: true,
};
for (
  const [id, name] of [["feature11-a", "TEST Facility A"], [
    "feature11-b",
    "TEST Facility B",
  ], ["feature11-stats", "TEST Statistics"]]
) {
  docs[`venues/${id}`] = {
    ...venue,
    name,
    ...(id.endsWith("stats") ? { openTime: "0600", closeTime: "1000" } : {}),
    minPricePerHour: id.endsWith("a") ? null : 60000,
  };
  for (const index of [1, 2]) {
    docs[`venues/${id}/courts/${id}-c${index}`] = {
      name: `TEST Court ${index}`,
      surfaceType: "PU",
      sortOrder: index - 1,
      isActive: true,
    };
  }
  if (!id.endsWith("a")) {
    docs[`venues/${id}/priceRules/fixture`] = {
      daysOfWeek: [1, 2, 3, 4, 5, 6, 7],
      startTime: "0600",
      endTime: id.endsWith("stats") ? "1000" : "2300",
      pricePerHour: 60000,
      label: "NORMAL",
    };
  }
}
for (
  const [id, time, status, paymentStatus] of [
    ["approve", "1000", "PENDING", "DEPOSITED"],
    ["reject", "1100", "PENDING", "DEPOSITED"],
    ["cancel", "1200", "CONFIRMED", "PAID"],
  ]
) {
  const date = day(1),
    endTime = `${Number(time.slice(0, 2)) + 1}00`,
    courtId = "feature11-a-c1";
  const slots = [time, time.slice(0, 2) + "30"].map((t) =>
    `${courtId}_${date}_${t}`
  );
  docs[`bookings/feature11-${id}`] = {
    venueId: "feature11-a",
    userId: "feature11-player",
    customerDisplayName: `TEST ${id}`,
    courtNames: ["TEST Court 1"],
    status,
    paymentStatus,
    paymentMethod: "MOCK_WALLET",
    total: 100000,
    depositAmount: 30000,
    subtotal: 100000,
    discount: 0,
    startAt: at(date, time),
    endAt: at(date, endTime),
    date,
    approvalDeadline: new Date(now.getTime() + 6 * 3600000),
    slotIds: slots,
    items: [{
      courtId,
      startAt: at(date, time),
      endAt: at(date, endTime),
      price: 100000,
    }],
    createdAt: now,
  };
  for (const t of [time, time.slice(0, 2) + "30"]) {
    docs[`slots/${courtId}_${date}_${t}`] = {
      venueId: "feature11-a",
      courtId,
      date,
      startTime: t,
      endTime: t.endsWith("00") ? t.slice(0, 2) + "30" : endTime,
      status: "BOOKED",
      bookingId: `feature11-${id}`,
    };
  }
}
const start = new Date(Math.floor(now.getTime() / 1800000) * 1800000),
  end = new Date(start.getTime() + 3600000);
docs["bookings/feature11-checkin"] = {
  venueId: "feature11-b",
  userId: "feature11-player",
  status: "CONFIRMED",
  paymentStatus: "UNPAID",
  paymentMethod: "AT_VENUE",
  total: 100000,
  depositAmount: 0,
  startAt: start,
  endAt: end,
  slotIds: [],
  courtNames: ["TEST Court 1"],
  qrToken: "FEATURE11_BOOKING_QR_TEST_ONLY",
};
const sessionId = "feature11-checkin-session",
  derived = deriveSessionFields({
    startAt: start,
    venue,
    districtName: "Device District",
  });
docs[`dropInSessions/${sessionId}`] = {
  ...derived,
  venueId: "feature11-b",
  ownerId: "feature11-owner",
  courtIds: ["feature11-b-c1"],
  startAt: start,
  endAt: end,
  capacity: 8,
  registeredCount: 1,
  pricePerPerson: 50000,
  status: "OPEN",
  level: "BEGINNER",
  note: "TEST current check-in",
  slotIds: [],
  createdAt: now,
  updatedAt: now,
};
docs[`dropInSessions/${sessionId}/registrations/feature11-player`] = {
  userId: "feature11-player",
  sessionId,
  venueId: "feature11-b",
  sessionStartAt: start,
  sessionEndAt: end,
  partySize: 1,
  status: "REGISTERED",
  paymentStatus: "UNPAID",
  paymentMethod: "AT_VENUE",
  pricePerPerson: 50000,
  amount: 50000,
  qrToken: "x".repeat(43),
  ticketCode: "DI-F22TEST2",
  requestId: "feature11-registration",
  createdAt: now,
  registeredAt: now,
};
const reportDate = day(-1), reportVenue = "feature11-stats";
for (
  const [id, status, paymentStatus, total, depositAmount, refundAmount] of [
    ["paid", "COMPLETED", "PAID", 100000, 0, 0],
    ["deposit", "CONFIRMED", "DEPOSITED", 100000, 30000, 0],
    ["refund", "CANCELLED", "REFUNDED", 100000, 0, 20000],
  ] as const
) {
  docs[`bookings/feature11-stats-${id}`] = {
    venueId: reportVenue,
    userId: "feature11-player",
    status,
    paymentStatus,
    paymentMethod: "MOCK_WALLET",
    total,
    depositAmount,
    refundAmount,
    date: reportDate,
    startAt: at(reportDate, "0800"),
    endAt: at(reportDate, "0900"),
    qrToken: "PRIVATE_TEST_QR",
  };
}
for (const time of ["0800", "0830"]) {
  docs[`slots/feature11-stats-c1_${reportDate}_${time}`] = {
    venueId: reportVenue,
    courtId: "feature11-stats-c1",
    date: reportDate,
    startTime: time,
    endTime: time === "0800" ? "0830" : "0900",
    status: "BOOKED",
    bookingId: "feature11-stats-paid",
  };
}
docs[`slots/feature11-stats-c2_${reportDate}_0900`] = {
  venueId: reportVenue,
  courtId: "feature11-stats-c2",
  date: reportDate,
  startTime: "0900",
  endTime: "0930",
  status: "BLOCKED",
  blockReason: "MAINTENANCE",
};
await fs.commit(
  Object.entries(docs).map(([path, data]) => fs.createWrite(path, data)),
);
console.log(JSON.stringify({
  project: fs.projectId(),
  documents: Object.keys(docs).length,
  reportDate,
  expected: {
    paid: 100000,
    deposited: 30000,
    refunded: 20000,
    occupancy: 2 / 15 * 100,
    peakHour: 8,
    peakMinutes: 60,
  },
}));
