// Dữ liệu demo cho spec 080 (đặt lịch vãng lai), CHỈ trên Firebase Emulator (tasks.md T016, quickstart.md mục 1).
//
//   firebase emulators:start --only auth,firestore --project demo-smashnow     # terminal khác
//   deno run -A scripts/seed/080-dropin.ts                                     # từ thư mục gốc repo
//
// Chạy bằng Deno (không phải `npx tsx`) để dùng chung `deriveSessionFields` của `supabase/functions/_shared/dropin.ts`
// (file đó import `npm:` specifier nên Node không nạp được). Mặc định project `demo-smashnow`, Auth 127.0.0.1:9099,
// Firestore 127.0.0.1:8080; đổi bằng FIREBASE_PROJECT_ID / FIREBASE_AUTH_EMULATOR_HOST / FIRESTORE_EMULATOR_HOST.
// Script từ chối chạy nếu host không phải máy này.
//
// Tạo (ID cố định `demo080-*`, chạy lại nhiều lần được; mỗi lần chạy xóa và tạo lại đăng ký của các buổi demo):
// - 3 quận (gộp vào `districts` có sẵn), 4 cơ sở (1 cơ sở HIDDEN), mỗi cơ sở 2 sân con.
// - Tài khoản (mật khẩu `Test@1234`): player1@test.local, player2@test.local, owner1@test.local (chủ cả 4 cơ sở).
// - 20 buổi: 3 trình độ, đủ 4 khung giờ, giá 40.000–120.000 đ. Trong đó: s01 FULL, s02 CANCELLED, s03 đã qua
//   (COMPLETED, player1 đã CHECKED_IN — dùng cho đánh giá), s04 còn đúng 3 chỗ, s05 bắt đầu sau khoảng 3 giờ,
//   s20 thuộc cơ sở HIDDEN (thử VENUE_INACTIVE).
// - Khớp kịch bản quickstart mục 3: player1 có khu vực yêu thích Quận 10, và các buổi s01 (FULL), s02 (CANCELLED),
//   s03 (đã qua), s04 (còn 3 chỗ, 60.000 đ/người → Q8 hoàn 120.000 đ), s15 (thứ Bảy tới, tối, INTERMEDIATE,
//   75.000 đ → Q2) cùng cơ sở HIDDEN đều ở Quận 10.
// - `registeredCount` khớp tổng `partySize` của các đăng ký REGISTERED/CHECKED_IN (người chơi giả `demo080-guest-*`).
import { setCustomClaims } from "../../supabase/functions/_shared/identity.ts";
import {
  commit,
  deleteWrite,
  documentName,
  encodeFields,
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

const PREFIX = "demo080";
const PASSWORD = "Test@1234";
const HOUR = 3600_000;
const MIN = 60_000;
const VN_OFFSET = 7 * HOUR;

/** Ghi đè cả document, không điều kiện. */
const setWrite = (path: string, data: Record<string, unknown>): Write => ({
  update: { name: documentName(path), fields: encodeFields(data) },
});
/** Gộp đúng các trường trong `data`, document chưa có thì tạo. */
const mergeWrite = (path: string, data: Record<string, unknown>): Write => ({
  update: { name: documentName(path), fields: encodeFields(data) },
  updateMask: { fieldPaths: Object.keys(data) },
});

async function commitChunks(writes: Write[]) {
  for (let i = 0; i < writes.length; i += 400) await commit(writes.slice(i, i + 400));
}

// ---------- Tài khoản (Auth Emulator) ----------

async function ensureAuthUser(uid: string, email: string, displayName: string, appRole: string) {
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
  await setCustomClaims(uid, { role: "authenticated", appRole });
}

const now = new Date();

const ACCOUNTS = [
  { uid: `${PREFIX}-player1`, email: "player1@test.local", name: "Người chơi 1", role: "PLAYER", level: "INTERMEDIATE", fav: ["q10"] },
  { uid: `${PREFIX}-player2`, email: "player2@test.local", name: "Người chơi 2", role: "PLAYER", level: "BEGINNER", fav: ["binh-thanh"] },
  { uid: `${PREFIX}-owner1`, email: "owner1@test.local", name: "Chủ sân 1", role: "OWNER", level: null, fav: [] },
];
const OWNER_ID = `${PREFIX}-owner1`;
const PLAYER1 = `${PREFIX}-player1`;

function userDoc(a: (typeof ACCOUNTS)[number]) {
  return {
    displayName: a.name,
    email: a.email,
    phone: null,
    avatarPath: null,
    level: a.level,
    favoriteDistrictIds: a.fav,
    role: a.role,
    ownerStatus: a.role === "OWNER" ? "APPROVED" : "NONE",
    accountStatus: "ACTIVE",
    fcmTokens: [],
    notificationPrefs: {},
    walletBalance: 0,
    termsAcceptedAt: now,
    createdAt: now,
    updatedAt: now,
  };
}

// ---------- Quận, cơ sở ----------

const DISTRICTS = [
  { id: "q1", name: "Quận 1", sortOrder: 1 },
  { id: "q10", name: "Quận 10", sortOrder: 8 },
  { id: "binh-thanh", name: "Bình Thạnh", sortOrder: 12 },
];
const districtName = (id: string) => DISTRICTS.find((d) => d.id === id)!.name;

const VENUES: { id: string; data: VenueDoc & { districtId: string } }[] = [
  { id: `${PREFIX}-v1`, data: { name: "SmashNow Lê Lợi", address: "12 Lê Lợi, Phường Bến Nghé, Quận 1", districtId: "q1", status: "ACTIVE" } },
  { id: `${PREFIX}-v2`, data: { name: "SmashNow Ba Tháng Hai", address: "300 Ba Tháng Hai, Phường 12, Quận 10", districtId: "q10", status: "ACTIVE" } },
  { id: `${PREFIX}-v3`, data: { name: "SmashNow Điện Biên Phủ", address: "200 Điện Biên Phủ, Phường 15, Bình Thạnh", districtId: "binh-thanh", status: "ACTIVE" } },
  { id: `${PREFIX}-v4`, data: { name: "SmashNow Tạm Ẩn", address: "5 Thành Thái, Phường 14, Quận 10", districtId: "q10", status: "HIDDEN" } },
];

// ---------- Buổi ----------

/** Giờ Việt Nam `hh:mm` của ngày (hôm nay + dayOffset). */
function vnTime(dayOffset: number, hhmm: string): Date {
  const local = new Date(now.getTime() + VN_OFFSET);
  const [h, m] = hhmm.split(":").map(Number);
  return new Date(Date.UTC(local.getUTCFullYear(), local.getUTCMonth(), local.getUTCDate() + dayOffset, h, m) - VN_OFFSET);
}

/** Số ngày tới thứ Bảy gần nhất (1–7, không tính hôm nay) theo giờ Việt Nam: quickstart Q2 lọc thứ Bảy + Tối. */
const nextSaturday = ((6 - new Date(now.getTime() + VN_OFFSET).getUTCDay() + 7) % 7) || 7;

/** Mốc 30 phút đầu tiên sau `now + hours`. */
function roundedFromNow(hours: number): Date {
  const t = now.getTime() + hours * HOUR;
  return new Date(Math.ceil(t / (30 * MIN)) * 30 * MIN);
}

interface SessionSpec {
  n: number;
  venue: number; // chỉ số trong VENUES
  start: Date;
  durationMin: number;
  level: "BEGINNER" | "INTERMEDIATE" | "ADVANCED";
  capacity: number;
  registered: number;
  price: number;
  status?: "OPEN" | "FULL" | "CANCELLED" | "COMPLETED";
  note?: string;
  /** player1 đã check-in (buổi đã qua). */
  player1CheckedIn?: boolean;
}

const SESSIONS: SessionSpec[] = [
  { n: 1, venue: 1, start: vnTime(1, "18:00"), durationMin: 120, level: "INTERMEDIATE", capacity: 8, registered: 8, price: 80000, status: "FULL", note: "Buổi đã đủ người" },
  { n: 2, venue: 1, start: vnTime(1, "19:00"), durationMin: 120, level: "BEGINNER", capacity: 12, registered: 0, price: 50000, status: "CANCELLED", note: "Chủ sân đã hủy buổi" },
  { n: 3, venue: 1, start: vnTime(-1, "18:00"), durationMin: 120, level: "INTERMEDIATE", capacity: 12, registered: 6, price: 70000, status: "COMPLETED", note: "Buổi đã qua", player1CheckedIn: true },
  { n: 4, venue: 1, start: vnTime(2, "06:00"), durationMin: 90, level: "BEGINNER", capacity: 10, registered: 7, price: 60000, note: "Còn đúng 3 chỗ" },
  { n: 5, venue: 0, start: roundedFromNow(3), durationMin: 120, level: "ADVANCED", capacity: 12, registered: 4, price: 100000, note: "Bắt đầu sau khoảng 3 giờ (hủy sẽ không được hoàn)" },
  { n: 6, venue: 1, start: vnTime(1, "05:30"), durationMin: 90, level: "INTERMEDIATE", capacity: 12, registered: 2, price: 45000 },
  { n: 7, venue: 2, start: vnTime(1, "11:30"), durationMin: 90, level: "BEGINNER", capacity: 8, registered: 1, price: 40000 },
  { n: 8, venue: 0, start: vnTime(1, "14:00"), durationMin: 120, level: "ADVANCED", capacity: 12, registered: 5, price: 90000 },
  { n: 9, venue: 1, start: vnTime(2, "12:00"), durationMin: 60, level: "INTERMEDIATE", capacity: 8, registered: 0, price: 60000 },
  { n: 10, venue: 2, start: vnTime(2, "16:00"), durationMin: 120, level: "ADVANCED", capacity: 12, registered: 9, price: 110000 },
  { n: 11, venue: 0, start: vnTime(2, "20:00"), durationMin: 120, level: "BEGINNER", capacity: 16, registered: 3, price: 55000 },
  { n: 12, venue: 1, start: vnTime(3, "07:00"), durationMin: 120, level: "ADVANCED", capacity: 12, registered: 6, price: 95000 },
  { n: 13, venue: 2, start: vnTime(3, "11:00"), durationMin: 120, level: "INTERMEDIATE", capacity: 10, registered: 2, price: 65000 },
  { n: 14, venue: 0, start: vnTime(3, "13:00"), durationMin: 120, level: "BEGINNER", capacity: 12, registered: 0, price: 50000 },
  { n: 15, venue: 1, start: vnTime(nextSaturday, "18:30"), durationMin: 120, level: "INTERMEDIATE", capacity: 12, registered: 10, price: 75000, note: "Thứ Bảy, buổi tối (Q2)" },
  { n: 16, venue: 2, start: vnTime(4, "09:00"), durationMin: 120, level: "ADVANCED", capacity: 8, registered: 4, price: 120000 },
  { n: 17, venue: 0, start: vnTime(4, "12:30"), durationMin: 60, level: "INTERMEDIATE", capacity: 8, registered: 1, price: 60000 },
  { n: 18, venue: 1, start: vnTime(5, "17:00"), durationMin: 120, level: "BEGINNER", capacity: 12, registered: 5, price: 45000 },
  { n: 19, venue: 2, start: vnTime(5, "21:00"), durationMin: 90, level: "ADVANCED", capacity: 12, registered: 3, price: 85000 },
  { n: 20, venue: 3, start: vnTime(2, "18:00"), durationMin: 120, level: "INTERMEDIATE", capacity: 12, registered: 2, price: 70000, note: "Cơ sở đang ẩn (VENUE_INACTIVE)" },
];

const sessionId = (n: number) => `${PREFIX}-s${String(n).padStart(2, "0")}`;

/** Chia `count` chỗ thành các nhóm ≤ 4 người. */
function parties(count: number): number[] {
  const out: number[] = [];
  for (let left = count; left > 0; left -= Math.min(4, left)) out.push(Math.min(4, left));
  return out;
}

// ---------- Chạy ----------

console.log(`Project ${projectId()}, Auth ${Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST")}, Firestore ${Deno.env.get("FIRESTORE_EMULATOR_HOST")}`);

for (const a of ACCOUNTS) await ensureAuthUser(a.uid, a.email, a.name, a.role);

const writes: Write[] = [];
for (const a of ACCOUNTS) writes.push(setWrite(`users/${a.uid}`, userDoc(a)));
for (const d of DISTRICTS) writes.push(mergeWrite(`districts/${d.id}`, { name: d.name, sortOrder: d.sortOrder }));
for (const v of VENUES) {
  writes.push(setWrite(`venues/${v.id}`, {
    ...v.data,
    ownerId: OWNER_ID,
    phone: "0900000000",
    openTime: "0500",
    closeTime: "2300",
    ratingAvg: 0,
    ratingCount: 0,
    createdAt: now,
    updatedAt: now,
  }));
  for (const c of [1, 2]) {
    writes.push(setWrite(`venues/${v.id}/courts/court-0${c}`, { name: `Sân ${c}`, isActive: true, createdAt: now }));
  }
}

// Xóa đăng ký cũ của các buổi demo (kể cả đăng ký tạo từ app ở lần demo trước) để số đếm khớp. Commit riêng
// trước để một commit không ghi hai lần vào cùng document.
const stale: Write[] = [];
for (const s of SESSIONS) {
  const old = await query("registrations", [], { parent: `dropInSessions/${sessionId(s.n)}` });
  stale.push(...old.map((d) => deleteWrite(d.path, false)));
}
await commitChunks(stale);

let guestNo = 0;
for (const s of SESSIONS) {
  const id = sessionId(s.n);
  const venue = VENUES[s.venue];
  const derived = deriveSessionFields({ startAt: s.start, venue: venue.data, districtName: districtName(venue.data.districtId) });
  const endAt = new Date(s.start.getTime() + s.durationMin * MIN);
  const status = s.status ?? (s.registered >= s.capacity ? "FULL" : "OPEN");
  writes.push(setWrite(`dropInSessions/${id}`, {
    venueId: venue.id,
    ownerId: OWNER_ID,
    districtId: venue.data.districtId,
    courtIds: s.n % 3 === 0 ? ["court-01", "court-02"] : ["court-01"],
    date: derived.date,
    startAt: s.start,
    endAt,
    timeSlot: derived.timeSlot,
    venueSnapshot: derived.venueSnapshot,
    level: s.level,
    capacity: s.capacity,
    registeredCount: s.registered,
    pricePerPerson: s.price,
    status,
    note: s.note ?? null,
    createdAt: new Date(now.getTime() - 3 * 24 * HOUR),
    updatedAt: now,
  }));

  // Đăng ký giữ chỗ: player1 (nếu có) + người chơi giả, tổng partySize = registered.
  const sizes = s.player1CheckedIn ? [2, ...parties(s.registered - 2)] : parties(s.registered);
  sizes.forEach((partySize, i) => {
    const uid = s.player1CheckedIn && i === 0 ? PLAYER1 : `${PREFIX}-guest-${String(++guestNo).padStart(3, "0")}`;
    const registeredAt = new Date(Math.min(now.getTime(), s.start.getTime()) - (24 + i) * HOUR);
    const mockPay = i % 2 === 0;
    const checkedIn = s.player1CheckedIn && i === 0;
    writes.push(setWrite(`dropInSessions/${id}/registrations/${uid}`, {
      userId: uid,
      sessionId: id,
      venueId: venue.id,
      sessionStartAt: s.start,
      sessionEndAt: endAt,
      partySize,
      status: checkedIn ? "CHECKED_IN" : "REGISTERED",
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
      checkedInAt: checkedIn ? new Date(s.start.getTime() + 5 * MIN) : null,
      waitlistedAt: null,
      createdAt: registeredAt,
      updatedAt: registeredAt,
    }));
  });
  console.log(
    `  ${id}  ${derived.date} ${derived.timeSlot.padEnd(9)} ${s.level.padEnd(12)} ` +
      `${String(s.registered).padStart(2)}/${String(s.capacity).padEnd(2)} ${status.padEnd(9)} ` +
      `${s.price.toLocaleString("vi-VN")} đ  ${venue.data.name}`,
  );
}

await commitChunks(writes);
console.log(
  `Xong: ${ACCOUNTS.length} tài khoản (mật khẩu ${PASSWORD}), ${DISTRICTS.length} quận, ${VENUES.length} cơ sở, ` +
    `${SESSIONS.length} buổi, ${guestNo} người chơi giả.`,
);
