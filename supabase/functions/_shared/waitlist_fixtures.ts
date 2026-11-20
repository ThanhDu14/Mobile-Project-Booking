// Fixture dùng chung cho test Deno của spec 081 (hàng chờ buổi vãng lai), chạy trên Firebase Emulator.
// Không dùng trong function. Không xóa dữ liệu Emulator: mọi ID do người gọi đặt (nên có tiền tố ngẫu nhiên).
import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import { commit, createWrite, get, query } from "./firestore.ts";

const MIN = 60_000;
const HOUR = 60 * MIN;

/** Một lượt chờ: số người, hoặc object khi cần chỉ định thêm. */
export type WaitlistSeed = number | {
  partySize: number;
  uid?: string;
  paymentMethod?: "MOCK_PAY" | "AT_VENUE";
  /** Giá chốt lúc vào hàng chờ; mặc định bằng `pricePerPerson` của buổi. */
  pricePerPerson?: number;
};

export interface SeedFullSessionOptions {
  /** ID buổi; cũng là tiền tố của uid (`<id>-r1`, `<id>-w1`, ...), cơ sở (`<id>-venue`) và chủ (`<id>-owner`). */
  id: string;
  capacity: number;
  /** `partySize` của từng đăng ký `REGISTERED`. */
  registered: number[];
  /** Các lượt chờ theo thứ tự vào hàng (vé 1, 2, ...). */
  waitlist: WaitlistSeed[];
  /** Buổi bắt đầu sau bấy nhiêu phút tính từ `now`. */
  startInMinutes: number;
  /** Mặc định giờ hiện tại. */
  now?: Date;
  pricePerPerson?: number;
  /** Dùng cơ sở/chủ đã có (không tạo thêm); mặc định tạo `<id>-venue`, `<id>-owner`. */
  venueId?: string;
  ownerId?: string;
  /** Mặc định 2 giờ. */
  durationMinutes?: number;
  /** Mặc định `PAID`/`MOCK_PAY` cho đăng ký đã giữ chỗ. */
  registeredPaymentMethod?: "MOCK_PAY" | "AT_VENUE";
}

export interface SeededSession {
  sessionId: string;
  venueId: string;
  ownerId: string;
  startAt: Date;
  endAt: Date;
  registeredUids: string[];
  /** uid của các lượt chờ theo thứ tự vé tăng dần. */
  waitlistUids: string[];
  /** Số vé tương ứng `waitlistUids` (1, 2, ...). */
  waitlistTickets: number[];
}

/**
 * Ghi buổi, các đăng ký `REGISTERED` và các lượt `WAITLISTED` lên Emulator, với đúng `waitlistTicket`,
 * `waitlistTickets`, `waitlistCount`, `waitlistNextTicket`. Buổi `FULL` khi `registeredCount >= capacity`.
 */
export async function seedFullSession(opts: SeedFullSessionOptions): Promise<SeededSession> {
  const now = opts.now ?? new Date();
  const price = opts.pricePerPerson ?? 60000;
  const startAt = new Date(now.getTime() + opts.startInMinutes * MIN);
  const endAt = new Date(startAt.getTime() + (opts.durationMinutes ?? 120) * MIN);
  const ownerId = opts.ownerId ?? `${opts.id}-owner`;
  const venueId = opts.venueId ?? `${opts.id}-venue`;
  const registeredUids = opts.registered.map((_, i) => `${opts.id}-r${i + 1}`);
  const waitSeeds = opts.waitlist.map((w) => typeof w === "number" ? { partySize: w } : w);
  const waitlistUids = waitSeeds.map((w, i) => ("uid" in w && w.uid) || `${opts.id}-w${i + 1}`);
  const waitlistTickets = waitSeeds.map((_, i) => i + 1);
  const held = opts.registered.reduce((s, n) => s + n, 0);

  const writes = [];
  if (!opts.ownerId) {
    writes.push(createWrite(`users/${ownerId}`, { role: "OWNER", accountStatus: "ACTIVE", ownerStatus: "APPROVED" }));
  }
  if (!opts.venueId) {
    writes.push(createWrite(`venues/${venueId}`, { name: "Sân thử 081", ownerId, status: "ACTIVE", districtId: "q1" }));
  }
  for (const uid of [...registeredUids, ...waitlistUids]) {
    // Người chơi dùng chung giữa nhiều buổi (uid chỉ định) có thể đã có hồ sơ.
    if (!(await get(`users/${uid}`))) writes.push(createWrite(`users/${uid}`, { role: "PLAYER", accountStatus: "ACTIVE" }));
  }
  writes.push(createWrite(`dropInSessions/${opts.id}`, {
    venueId,
    ownerId,
    districtId: "q1",
    courtIds: ["court-01"],
    startAt,
    endAt,
    capacity: opts.capacity,
    registeredCount: held,
    pricePerPerson: price,
    level: "INTERMEDIATE",
    status: held >= opts.capacity ? "FULL" : "OPEN",
    waitlistNextTicket: waitSeeds.length + 1,
    waitlistTickets,
    waitlistCount: waitSeeds.length,
  }));
  const base = (uid: string, partySize: number, pricePerPerson: number) => ({
    userId: uid,
    sessionId: opts.id,
    venueId,
    sessionStartAt: startAt,
    sessionEndAt: endAt,
    partySize,
    pricePerPerson,
    amount: pricePerPerson * partySize,
    refundAmount: null,
    requestId: crypto.randomUUID(),
    cancelRequestId: null,
    sessionChanged: false,
    cancelledAt: null,
    cancelledBy: null,
    checkedInAt: null,
    createdAt: now,
    updatedAt: now,
  });
  opts.registered.forEach((partySize, i) => {
    const uid = registeredUids[i];
    const method = opts.registeredPaymentMethod ?? "MOCK_PAY";
    writes.push(createWrite(`dropInSessions/${opts.id}/registrations/${uid}`, {
      ...base(uid, partySize, price),
      status: "REGISTERED",
      paymentMethod: method,
      paymentStatus: method === "MOCK_PAY" ? "PAID" : "UNPAID",
      qrToken: `qr-${uid}`,
      ticketCode: "DI-ABCDEFGH",
      registeredAt: now,
      waitlistedAt: null,
    }));
  });
  waitSeeds.forEach((w, i) => {
    const uid = waitlistUids[i];
    writes.push(createWrite(`dropInSessions/${opts.id}/registrations/${uid}`, {
      ...base(uid, w.partySize, w.pricePerPerson ?? price),
      status: "WAITLISTED",
      paymentMethod: w.paymentMethod ?? "MOCK_PAY",
      paymentStatus: null,
      qrToken: null,
      ticketCode: null,
      registeredAt: null,
      waitlistedAt: new Date(now.getTime() - (waitSeeds.length - i) * MIN),
      waitlistTicket: i + 1,
      promotedAt: null,
      waitlistEndReason: null,
    }));
  });
  await commit(writes);
  return { sessionId: opts.id, venueId, ownerId, startAt, endAt, registeredUids, waitlistUids, waitlistTickets };
}

/**
 * Kiểm tra các bất biến của hàng chờ (data-model mục 1):
 * - `waitlistTickets` khớp đúng tập số vé của các lượt `WAITLISTED`, tăng dần;
 * - `waitlistCount == waitlistTickets.length`;
 * - `registeredCount == Σ partySize` của các lượt `REGISTERED`/`CHECKED_IN` và `registeredCount ≤ capacity`.
 */
export async function assertWaitlistInvariant(sessionId: string): Promise<void> {
  const session = (await get(`dropInSessions/${sessionId}`))!;
  assert(session, `thiếu buổi ${sessionId}`);
  const regs = await query("registrations", [], { parent: `dropInSessions/${sessionId}` });
  const waiting = regs
    .filter((d) => d.data.status === "WAITLISTED")
    .map((d) => Number(d.data.waitlistTicket))
    .sort((a, b) => a - b);
  const tickets = (session.waitlistTickets ?? []) as number[];
  assertEquals(tickets, waiting, `${sessionId}: waitlistTickets khớp các lượt WAITLISTED`);
  assertEquals(tickets, [...tickets].sort((a, b) => a - b), `${sessionId}: waitlistTickets tăng dần`);
  assertEquals(new Set(tickets).size, tickets.length, `${sessionId}: waitlistTickets không trùng`);
  assertEquals(session.waitlistCount, tickets.length, `${sessionId}: waitlistCount`);
  const held = regs
    .filter((d) => d.data.status === "REGISTERED" || d.data.status === "CHECKED_IN")
    .reduce((s, d) => s + Number(d.data.partySize ?? 1), 0);
  assertEquals(session.registeredCount, held, `${sessionId}: registeredCount khớp tổng partySize`);
  assert(Number(session.registeredCount) <= Number(session.capacity), `${sessionId}: registeredCount ≤ capacity`);
}

export { HOUR, MIN };
