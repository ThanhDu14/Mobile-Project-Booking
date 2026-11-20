// Dữ liệu demo cho spec 081 (hàng chờ buổi vãng lai), CHỈ trên Firebase Emulator (tasks.md T004, quickstart.md mục 1).
//
//   firebase emulators:start --only auth,firestore --project demo-smashnow     # terminal khác
//   deno run -A --config supabase/functions/deno.json scripts/seed/080-dropin.ts   # dữ liệu nền của 080, chạy TRƯỚC
//   deno run -A --config supabase/functions/deno.json scripts/seed/081-waitlist.ts # từ thư mục gốc repo (--config để Deno nạp npm:jose, vì scripts/seed có package.json)
//
// Chạy bằng Deno như 080-dropin.ts (dùng chung `deriveSessionFields` của `supabase/functions/_shared/dropin.ts`).
// Mặc định project `demo-smashnow`, Auth 127.0.0.1:9099, Firestore 127.0.0.1:8080; đổi bằng FIREBASE_PROJECT_ID /
// FIREBASE_AUTH_EMULATOR_HOST / FIRESTORE_EMULATOR_HOST. Script từ chối chạy nếu host không phải máy này, và KHÔNG
// được chạy lên project thật.
//
// Tạo (ID cố định, chạy lại nhiều lần được; mỗi lần chạy xóa và tạo lại đăng ký/lượt chờ của 3 buổi demo):
// - Buổi `demo081-w1`: 10 chỗ, đã đủ người (FULL), bắt đầu sau 2 ngày.
// - Buổi `demo081-w2`: trùng giờ với W1 (cùng giờ bắt đầu, cùng cơ sở khác sân), 10 chỗ, FULL. Dùng thử tự rời hàng chờ
//   trùng giờ (quickstart M4).
// - Buổi `demo081-w3`: 8 chỗ, FULL, bắt đầu sau 35 phút. Dùng thử đóng hàng chờ ở mốc T−30 (quickstart M5).
// - Tài khoản `player3`…`player12@test.local` (mật khẩu `Test@1234`; id `demo080-playerN` cùng quy ước với 080).
// - Mỗi buổi có `waitlistNextTicket = 1`, `waitlistTickets = []`, `waitlistCount = 0`.
// - `registeredCount` khớp tổng `partySize` của các đăng ký REGISTERED (người chơi giả `demo081-guest-*`).
import { setCustomClaims } from "../../supabase/functions/_shared/identity.ts";
import {
  commit,
  deleteWrite,
  documentName,
  encodeFields,
  get,
  projectId,
  query,
  type Write,
} from "../../supabase/functions/_shared/firestore.ts";
import {
  deriveSessionFields,
  newQrToken,
  newTicketCode,
  type VenueDoc,
} from "../../supabase/functions/_shared/dropin.ts";

// ---------- Môi trường: chỉ Emulator ----------

const defaults: Record<string, string> = {
  FIREBASE_PROJECT_ID: "demo-smashnow",
  FIREBASE_AUTH_EMULATOR_HOST: "127.0.0.1:9099",
  FIRESTORE_EMULATOR_HOST: "127.0.0.1:8080",
};
for (const [k, v] of Object.entries(defaults)) if (!Deno.env.get(k)) Deno.env.set(k, v);
for (const k of ["FIREBASE_AUTH_EMULATOR_HOST", "FIRESTORE_EMULATOR_HOST"]) {
  const host = Deno.env.get(k)!;
  if (!/^(127\.0\.0\.1|localhost|0\.0\.0\.0|10\.0\.2\.2):\d+$/.test(host)) {
    console.error(`${k}=${host} không phải Emulator trên máy này. Dừng.`);
    Deno.exit(1);
  }
}

const BASE = "demo080"; // tài khoản, cơ sở, chủ sân do 080-dropin.ts tạo
const PREFIX = "demo081";
const PASSWORD = "Test@1234";
const MIN = 60_000;
const HOUR = 3600_000;
const now = new Date();

/** Ghi đè cả document, không điều kiện. */
const setWrite = (path: string, data: Record<string, unknown>): Write => ({
  update: { name: documentName(path), fields: encodeFields(data) },
});

async function ensureAuthUser(uid: string, email: string, displayName: string) {
  const host = Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST");
  const res = await fetch(`http://${host}/identitytoolkit.googleapis.com/v1/projects/${projectId()}/accounts`, {
    method: "POST",
    headers: { Authorization: "Bearer owner", "Content-Type": "application/json" },
    body: JSON.stringify({ localId: uid, email, password: PASSWORD, displayName, emailVerified: true }),
  });
  const text = await res.text();
  if (!res.ok && !/DUPLICATE_LOCAL_ID|EMAIL_EXISTS/.test(text)) {
    throw new Error(`Không tạo được ${email}: ${res.status} ${text}`);
  }
  await setCustomClaims(uid, { role: "authenticated", appRole: "PLAYER" });
}

// ---------- Dữ liệu nền (của 080) ----------

const VENUE_ID = `${BASE}-v2`; // SmashNow Ba Tháng Hai, Quận 10
const OWNER_ID = `${BASE}-owner1`;
const venue = await get(`venues/${VENUE_ID}`);
if (!venue) {
  console.error(`Chưa có venues/${VENUE_ID}: chạy "deno run -A scripts/seed/080-dropin.ts" trước.`);
  Deno.exit(1);
}

// ---------- Tài khoản player3…player12 ----------

const PLAYERS = Array.from({ length: 10 }, (_, i) => {
  const n = i + 3;
  return { uid: `${BASE}-player${n}`, email: `player${n}@test.local`, name: `Người chơi ${n}` };
});

// ---------- Buổi ----------

/** Mốc 30 phút đầu tiên sau `now + hours`. */
function roundedFromNow(hours: number): Date {
  const t = now.getTime() + hours * HOUR;
  return new Date(Math.ceil(t / (30 * MIN)) * 30 * MIN);
}

const W1_START = roundedFromNow(48);
const SESSIONS = [
  { id: `${PREFIX}-w1`, start: W1_START, durationMin: 120, capacity: 10, parties: [4, 4, 2], price: 60000, court: "court-01", note: "Buổi đã đủ người, thử vào hàng chờ (M1)" },
  { id: `${PREFIX}-w2`, start: W1_START, durationMin: 120, capacity: 10, parties: [4, 3, 3], price: 70000, court: "court-02", note: "Trùng giờ với W1 (M4)" },
  { id: `${PREFIX}-w3`, start: new Date(now.getTime() + 35 * MIN), durationMin: 90, capacity: 8, parties: [4, 4], price: 50000, court: "court-01", note: "Bắt đầu sau 35 phút, thử đóng hàng chờ (M5)" },
];

// ---------- Chạy ----------

console.log(`Project ${projectId()}, Auth ${Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST")}, Firestore ${Deno.env.get("FIRESTORE_EMULATOR_HOST")}`);

for (const p of PLAYERS) await ensureAuthUser(p.uid, p.email, p.name);

const writes: Write[] = [];
for (const p of PLAYERS) {
  writes.push(setWrite(`users/${p.uid}`, {
    displayName: p.name,
    email: p.email,
    phone: null,
    avatarPath: null,
    level: "INTERMEDIATE",
    favoriteDistrictIds: ["q10"],
    role: "PLAYER",
    ownerStatus: "NONE",
    accountStatus: "ACTIVE",
    fcmTokens: [],
    notificationPrefs: {},
    walletBalance: 0,
    termsAcceptedAt: now,
    createdAt: now,
    updatedAt: now,
  }));
}

// Xóa đăng ký và lượt chờ cũ của 3 buổi demo (kể cả lượt tạo từ app ở lần demo trước) để số đếm khớp.
const stale: Write[] = [];
for (const s of SESSIONS) {
  const old = await query("registrations", [], { parent: `dropInSessions/${s.id}` });
  stale.push(...old.map((d) => deleteWrite(d.path, false)));
}
if (stale.length > 0) await commit(stale);

let guestNo = 0;
for (const s of SESSIONS) {
  const derived = deriveSessionFields({ startAt: s.start, venue: venue as VenueDoc, districtName: "Quận 10" });
  const endAt = new Date(s.start.getTime() + s.durationMin * MIN);
  const registered = s.parties.reduce((a, b) => a + b, 0);
  writes.push(setWrite(`dropInSessions/${s.id}`, {
    venueId: VENUE_ID,
    ownerId: OWNER_ID,
    districtId: "q10",
    courtIds: [s.court],
    date: derived.date,
    startAt: s.start,
    endAt,
    timeSlot: derived.timeSlot,
    venueSnapshot: derived.venueSnapshot,
    level: "INTERMEDIATE",
    capacity: s.capacity,
    registeredCount: registered,
    pricePerPerson: s.price,
    status: registered >= s.capacity ? "FULL" : "OPEN",
    note: s.note,
    waitlistNextTicket: 1,
    waitlistTickets: [],
    waitlistCount: 0,
    createdAt: new Date(now.getTime() - 3 * 24 * HOUR),
    updatedAt: now,
  }));
  s.parties.forEach((partySize, i) => {
    const uid = `${PREFIX}-guest-${String(++guestNo).padStart(3, "0")}`;
    const registeredAt = new Date(now.getTime() - (24 + i) * HOUR);
    const mockPay = i % 2 === 0;
    writes.push(setWrite(`dropInSessions/${s.id}/registrations/${uid}`, {
      userId: uid,
      sessionId: s.id,
      venueId: VENUE_ID,
      sessionStartAt: s.start,
      sessionEndAt: endAt,
      partySize,
      status: "REGISTERED",
      paymentMethod: mockPay ? "MOCK_PAY" : "AT_VENUE",
      paymentStatus: mockPay ? "PAID" : "UNPAID",
      pricePerPerson: s.price,
      amount: s.price * partySize,
      refundAmount: null,
      qrToken: newQrToken(),
      ticketCode: newTicketCode(),
      requestId: crypto.randomUUID(),
      cancelRequestId: null,
      sessionChanged: false,
      registeredAt,
      cancelledAt: null,
      cancelledBy: null,
      checkedInAt: null,
      waitlistedAt: null,
      createdAt: registeredAt,
      updatedAt: registeredAt,
    }));
  });
  console.log(
    `  ${s.id}  ${derived.date} ${derived.timeSlot.padEnd(9)} ${registered}/${s.capacity} FULL  ` +
      `${s.price.toLocaleString("vi-VN")} đ  bắt đầu ${s.start.toISOString()}`,
  );
}

for (let i = 0; i < writes.length; i += 400) await commit(writes.slice(i, i + 400));
console.log(
  `Xong: ${PLAYERS.length} tài khoản player3…player12 (mật khẩu ${PASSWORD}), ${SESSIONS.length} buổi, ` +
    `${guestNo} người chơi giả. Hàng chờ đang trống, hãy vào hàng chờ từ app (quickstart M1).`,
);
