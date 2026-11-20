// Device-test fixtures only. Refuse hosted Firebase and touch device-* documents only.
import { assert } from "jsr:@std/assert@1.0.19";
import * as fs from "../../supabase/functions/_shared/firestore.ts";
import { createUser } from "../../supabase/functions/_shared/testing.ts";
import { mergeClaims } from "../../supabase/functions/_shared/identity.ts";
import { deriveSessionFields } from "../../supabase/functions/_shared/dropin.ts";

assert(fs.projectId() === "demo-admin-repair");
assert(Deno.env.get("FIRESTORE_EMULATOR_HOST") === "127.0.0.1:18080");
assert(Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST") === "127.0.0.1:19099");
const now = new Date();
const docs: Record<string, Record<string, unknown>> = {};
for (
  const [uid, role] of [
    ["device-admin", "ADMIN"],
    ["device-owner", "OWNER"],
    ["device-existing", "PLAYER"],
    ["device-party-a", "PLAYER"],
    ["device-party-b", "PLAYER"],
  ]
) {
  try {
    await createUser({
      localId: uid,
      email: `${uid}@example.test`,
      password: "Test123456",
      emailVerified: true,
    });
  } catch (err) {
    if (!/EMAIL_EXISTS|LOCAL_ID_EXISTS|DUPLICATE_LOCAL_ID/.test(String(err))) {
      throw err;
    }
  }
  await mergeClaims(uid, { appRole: role, role: "authenticated" });
  docs[`users/${uid}`] = {
    role,
    accountStatus: "ACTIVE",
    ownerStatus: role === "OWNER" ? "APPROVED" : "NONE",
    displayName: uid,
    email: `${uid}@example.test`,
  };
}
const venue = {
  ownerId: "device-owner",
  name: "Device Test Venue",
  address: "Local test address",
  districtId: "device-district",
  status: "ACTIVE",
  openTime: "0600",
  closeTime: "2300",
  phone: "0900000000",
  location: { latitude: 10.7, longitude: 106.6 },
  amenityIds: [],
};
docs["venues/device-venue"] = venue;
docs["districts/device-district"] = {
  name: "Device District",
  sortOrder: 1,
  isActive: true,
};
docs["venues/device-venue/priceRules/device-price"] = {
  daysOfWeek: [1, 2, 3, 4, 5, 6, 7],
  startTime: "0600",
  endTime: "2300",
  pricePerHour: 100000,
  isActive: true,
};
for (
  const [id, capacity, count, size, ticket] of [
    ["device-increase", 3, 3, 2, 1],
    ["device-decrease", 6, 4, 3, 11],
  ] as const
) {
  const courtId = `${id}-court`;
  docs[`venues/device-venue/courts/${courtId}`] = {
    name: courtId,
    isActive: true,
    surface: "WOOD",
    createdAt: now,
    updatedAt: now,
  };
  const day = new Date(now.getTime() + 2 * 86400000).toISOString().slice(0, 10);
  const startAt = new Date(`${day}T18:00:00+07:00`),
    endAt = new Date(`${day}T19:00:00+07:00`);
  const derived = deriveSessionFields({
    startAt,
    venue,
    districtName: "Device District",
  });
  const slotIds = ["1800", "1830"].map((time) =>
    `${courtId}_${derived.date}_${time}`
  );
  docs[`dropInSessions/${id}`] = {
    ...derived,
    venueId: "device-venue",
    ownerId: "device-owner",
    districtId: "device-district",
    courtIds: [courtId],
    slotIds,
    startAt,
    endAt,
    capacity,
    registeredCount: count,
    level: "BEGINNER",
    pricePerPerson: 50000,
    status: count === capacity ? "FULL" : "OPEN",
    note: "Physical device local test",
    waitlistTickets: id === "device-increase" ? [1, 2] : [11],
    waitlistCount: id === "device-increase" ? 2 : 1,
    waitlistNextTicket: 20,
    createdAt: now,
    updatedAt: now,
  };
  for (const [index, slotId] of slotIds.entries()) {
    docs[`slots/${slotId}`] = {
      venueId: "device-venue",
      courtId,
      date: derived.date,
      status: "BLOCKED",
      blockReason: "DROP_IN",
      sessionId: id,
      startAt: new Date(startAt.getTime() + index * 1800000),
      endAt: new Date(startAt.getTime() + (index + 1) * 1800000),
    };
  }
  docs[`dropInSessions/${id}/registrations/device-existing`] = {
    userId: "device-existing",
    sessionId: id,
    venueId: "device-venue",
    status: "REGISTERED",
    partySize: count,
    paymentMethod: "MOCK_PAY",
    paymentStatus: "PAID",
    amount: count * 50000,
    pricePerPerson: 50000,
    sessionStartAt: startAt,
    sessionEndAt: endAt,
    qrToken: "test-only",
    ticketCode: "DI-ABCDEFGH",
    registeredAt: now,
  };
  for (
    const [uid, partySize, waitlistTicket] of [
      ["device-party-a", size, ticket],
      ...(id === "device-increase" ? [["device-party-b", 1, 2]] : []),
    ]
  ) {
    docs[`dropInSessions/${id}/registrations/${uid}`] = {
      userId: uid,
      sessionId: id,
      venueId: "device-venue",
      status: "WAITLISTED",
      partySize,
      waitlistTicket,
      paymentMethod: "AT_VENUE",
      paymentStatus: null,
      amount: Number(partySize) * 50000,
      pricePerPerson: 50000,
      sessionStartAt: startAt,
      sessionEndAt: endAt,
      waitlistedAt: now,
      qrToken: null,
      ticketCode: null,
    };
  }
}
docs["groups/device-group"] = {
  name: "Device Test Group",
  ownerId: "device-owner",
  status: "ACTIVE",
};
for (const id of ["device-hide", "device-lock"]) {
  docs[`groups/device-group/posts/${id}`] = {
    content: "Device reported content",
    authorSnapshot: { displayName: "Device Post Author", avatarPath: null },
    authorId: "device-party-a",
    status: "VISIBLE",
    hiddenByUid: "device-owner",
  };
}
docs["chats/device-chat/messages/device-message"] = {
  text: "Device reported message",
  senderSnapshot: { displayName: "Device Message Sender", avatarPath: null },
  senderId: "device-party-a",
  status: "VISIBLE",
};
for (
  const [id, type, path] of [
    ["device-hide", "POST", "groups/device-group/posts/device-hide"],
    ["device-lock", "POST", "groups/device-group/posts/device-lock"],
    ["device-message", "MESSAGE", "chats/device-chat/messages/device-message"],
  ]
) {
  docs[`reports/${id}`] = {
    reporterId: "device-owner",
    targetType: type,
    targetPath: path,
    reason: id,
    detail: "Physical device local validation",
    status: "OPEN",
    action: "NONE",
    createdAt: now,
  };
}
await fs.commit(
  Object.entries(docs).map(([path, data]) => ({
    update: { name: fs.documentName(path), fields: fs.encodeFields(data) },
  })),
);
console.log("Device fixtures ready: demo-admin-repair, device-* only");
