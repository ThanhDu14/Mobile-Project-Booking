// T074: bản giả cùng chữ ký của helper B, C, D (research R12) và hàm seed dữ liệu cho test delete-account,
// delete-account-sweeper. Chỉ dùng trong test; code thật lấy helper từ `_shared/account-deletion-helpers.ts`.
import type { DeletionHelpers, DeletionTx, StorageAdmin } from "../_shared/account-deletion.ts";
import { commit, createWrite, deleteWrite, query, type QueryDocument, updateWrite } from "../_shared/firestore.ts";

// ---------- Helper giả ----------

export interface SentNotification {
  recipientUid: string;
  type: string;
  idempotencyKey: string;
}

export interface FakeHelpers {
  helpers: Required<Omit<DeletionHelpers, "transformsPerCall">>;
  /** Tên helper đã gọi, theo thứ tự. */
  calls: string[];
  /** Thông báo đã gửi thật (khóa trùng thì không gửi lại). */
  sent: SentNotification[];
  /** `failAt.cancelBookingInTx = 2`: lời gọi thứ 2 ném lỗi. Đếm theo tên helper, cộng dồn qua các lần thử. */
  failAt: Partial<Record<string, number>>;
}

function paidAmount(doc: Record<string, unknown>): number {
  switch (doc.paymentStatus) {
    case "DEPOSITED":
      return Number(doc.depositAmount ?? 0);
    case "PAID":
      return Number(doc.total ?? doc.amount ?? 0);
    default:
      return 0;
  }
}

/** Chính sách giả: hủy trước giờ chơi từ 4 giờ trở lên thì hoàn 100% tiền đã trả, muộn hơn thì 50%. */
function policyRefund(doc: Record<string, unknown>, startAt: Date, now: Date): number {
  const paid = paidAmount(doc);
  return startAt.getTime() - now.getTime() >= 4 * 3600_000 ? paid : Math.floor(paid / 2);
}

export function fakeHelpers(): FakeHelpers {
  const calls: string[] = [];
  const sent: SentNotification[] = [];
  const sentKeys = new Set<string>();
  const failAt: Partial<Record<string, number>> = {};
  const counts = new Map<string, number>();

  const track = (name: string) => {
    calls.push(name);
    const n = (counts.get(name) ?? 0) + 1;
    counts.set(name, n);
    if (failAt[name] === n) throw new Error(`FAKE_${name}_FAILED`);
  };

  const cancelRegistrationInTx = async (
    tx: DeletionTx,
    sessionId: string,
    uid: string,
    cancelledBy: "USER" | "OWNER" | "SYSTEM",
    opts: { forceFullRefund?: boolean } = {},
  ) => {
    track("cancelRegistrationInTx");
    const path = `dropInSessions/${sessionId}/registrations/${uid}`;
    const reg = await tx.get(path);
    const session = await tx.get(`dropInSessions/${sessionId}`);
    if (!reg || !session) return { refundAmount: 0 };
    const refundAmount = opts.forceFullRefund
      ? paidAmount(reg)
      : policyRefund(reg, session.startAt as Date, new Date());
    tx.write(updateWrite(path, { status: "CANCELLED", cancelledBy, refundAmount }, ["cancelledAt"]));
    if (refundAmount > 0) tx.increment(`users/${uid}`, "walletBalance", refundAmount);
    return { refundAmount };
  };

  const helpers: FakeHelpers["helpers"] = {
    // B
    async cancelBookingInTx(tx, booking, opts) {
      track("cancelBookingInTx");
      const refundAmount = opts.refund === "FULL"
        ? paidAmount(booking.data)
        : policyRefund(booking.data, booking.data.startAt as Date, new Date());
      tx.write(updateWrite(`bookings/${booking.id}`, {
        status: "CANCELLED",
        cancelledBy: opts.cancelledBy,
        cancelReason: opts.reason,
        refundAmount,
      }, ["cancelledAt"]));
      for (const item of (booking.data.items ?? []) as { slotId: string }[]) {
        tx.write(deleteWrite(`slots/${item.slotId}`, false));
      }
      if (refundAmount > 0) tx.increment(`users/${booking.data.userId}`, "walletBalance", refundAmount);
      return { refundAmount };
    },
    computeBookingRefund(booking, now) {
      return policyRefund(booking, booking.startAt as Date, now);
    },
    async anonymizeBookings(uid) {
      track("anonymizeBookings");
      const docs = await query("bookings", [{ field: "userId", op: "EQUAL", value: uid }]);
      if (docs.length > 0) await commit(docs.map((d) => updateWrite(d.path, { userDeleted: true })));
    },
    // C
    cancelRegistrationInTx,
    computeRefund(reg, session, now) {
      return { refundAmount: policyRefund(reg, session.startAt as Date, now) };
    },
    async leaveWaitlistInTx(tx, sessionId, uid, _reason, cancelledBy) {
      track("leaveWaitlistInTx");
      tx.write(updateWrite(`dropInSessions/${sessionId}/registrations/${uid}`, { status: "CANCELLED", cancelledBy }));
    },
    async anonymizeReviews(uid) {
      track("anonymizeReviews");
      const docs = await query("reviews", [{ field: "userId", op: "EQUAL", value: uid }]);
      if (docs.length > 0) {
        await commit(docs.map((d) =>
          updateWrite(d.path, {
            userId: null,
            authorDeleted: true,
            userSnapshot: { displayName: null, avatarPath: null },
          })
        ));
      }
    },
    async anonymizeCommunity() {
      track("anonymizeCommunity");
    },
    // D
    async hideVenueForOwnerDeletionInTx(tx, venueId) {
      track("hideVenueForOwnerDeletionInTx");
      tx.write(updateWrite(`venues/${venueId}`, { status: "HIDDEN", hiddenReason: "OWNER_DELETED" }));
    },
    async cancelSessionInTx(tx, session, opts) {
      track("cancelSessionInTx");
      tx.write(updateWrite(`dropInSessions/${session.id}`, { status: "CANCELLED", cancelReason: opts.reason }));
      const regs = await tx.query("registrations", [
        { field: "status", op: "IN", value: ["REGISTERED", "WAITLISTED"] },
      ], { parent: `dropInSessions/${session.id}` });
      for (const reg of regs) {
        await cancelRegistrationInTx(tx, session.id, reg.id, "OWNER", { forceFullRefund: true });
      }
    },
    async deleteOwnerApplications() {
      track("deleteOwnerApplications");
    },
    async sendNotification(n) {
      track("sendNotification");
      if (sentKeys.has(n.idempotencyKey)) return;
      sentKeys.add(n.idempotencyKey);
      sent.push({ recipientUid: n.recipientUid, type: n.type, idempotencyKey: n.idempotencyKey });
    },
  };
  return { helpers, calls, sent, failAt };
}

// ---------- Storage giả (Supabase Storage API của bucket avatars) ----------

export interface FakeStorage {
  url: string;
  objects: Set<string>;
  close(): Promise<void>;
}

/** Server `fetch` cục bộ trả lời `object/list/avatars` và `DELETE object/avatars` như Supabase Storage. */
export function fakeStorageServer(serviceKey = "service-role-test"): FakeStorage {
  const objects = new Set<string>();
  const server = Deno.serve({ port: 0, hostname: "127.0.0.1", onListen() {} }, async (req) => {
    if (req.headers.get("Authorization") !== `Bearer ${serviceKey}`) return new Response("{}", { status: 401 });
    const url = new URL(req.url);
    if (req.method === "POST" && url.pathname === "/storage/v1/object/list/avatars") {
      const { prefix } = await req.json() as { prefix: string };
      const dir = prefix.endsWith("/") ? prefix : `${prefix}/`;
      const items = [...objects].filter((p) => p.startsWith(dir)).map((p) => ({ name: p.substring(dir.length) }));
      return Response.json(items);
    }
    if (req.method === "DELETE" && url.pathname === "/storage/v1/object/avatars") {
      const { prefixes } = await req.json() as { prefixes: string[] };
      const removed = prefixes.filter((p) => objects.delete(p)).map((name) => ({ name }));
      return Response.json(removed);
    }
    return new Response("{}", { status: 404 });
  });
  const addr = server.addr as Deno.NetAddr;
  return { url: `http://127.0.0.1:${addr.port}`, objects, close: () => server.shutdown() };
}

export function storageFor(fake: FakeStorage): { url: string; serviceKey: string } {
  return { url: fake.url, serviceKey: "service-role-test" };
}

// ---------- Seed dữ liệu ----------

const HOUR = 3600_000;

export function hoursFromNow(h: number): Date {
  return new Date(Date.now() + h * HOUR);
}

export async function seedUser(uid: string, data: Record<string, unknown> = {}): Promise<void> {
  await commit([createWrite(`users/${uid}`, {
    displayName: "Người thử",
    role: "PLAYER",
    accountStatus: "ACTIVE",
    walletBalance: 0,
    fcmTokens: ["token-1"],
    ...data,
  })]);
}

export interface SeedBooking {
  userId: string;
  venueId?: string;
  status?: string;
  startInHours?: number;
  paymentStatus?: string;
  depositAmount?: number;
  total?: number;
  checkedInAt?: Date | null;
}

/** Đơn 1 giờ, 1 slot `slot_{id}` ở trạng thái `BOOKED`. */
export function bookingWrites(id: string, b: SeedBooking) {
  const startAt = hoursFromNow(b.startInHours ?? 24);
  const endAt = new Date(startAt.getTime() + HOUR);
  const slotId = `slot_${id}`;
  return [
    createWrite(`bookings/${id}`, {
      userId: b.userId,
      venueId: b.venueId ?? "v-other",
      venueSnapshot: { name: "Sân cầu lông Hòa Bình" },
      items: [{ courtId: "c1", slotId, startAt, endAt, price: b.total ?? 120000 }],
      startAt,
      endAt,
      status: b.status ?? "CONFIRMED",
      paymentStatus: b.paymentStatus ?? "UNPAID",
      depositAmount: b.depositAmount ?? 0,
      total: b.total ?? 120000,
      checkedInAt: b.checkedInAt ?? null,
    }),
    createWrite(`slots/${slotId}`, { venueId: b.venueId ?? "v-other", status: "BOOKED", bookingId: id }),
  ];
}

export async function seedBooking(id: string, b: SeedBooking): Promise<void> {
  await commit(bookingWrites(id, b));
}

export async function seedVenue(id: string, ownerId: string): Promise<void> {
  await commit([createWrite(`venues/${id}`, { ownerId, name: `Cơ sở ${id}`, status: "ACTIVE" })]);
}

export async function seedSession(id: string, venueId: string, startInHours = 24): Promise<void> {
  const startAt = hoursFromNow(startInHours);
  await commit([createWrite(`dropInSessions/${id}`, {
    venueId,
    venueSnapshot: { name: `Cơ sở ${venueId}` },
    startAt,
    endAt: new Date(startAt.getTime() + 2 * HOUR),
    status: "OPEN",
  })]);
}

export async function seedRegistration(
  sessionId: string,
  uid: string,
  data: { status?: string; amount?: number; paymentStatus?: string; startInHours?: number } = {},
): Promise<void> {
  const startAt = hoursFromNow(data.startInHours ?? 24);
  await commit([createWrite(`dropInSessions/${sessionId}/registrations/${uid}`, {
    userId: uid,
    sessionId,
    sessionStartAt: startAt,
    sessionEndAt: new Date(startAt.getTime() + 2 * HOUR),
    status: data.status ?? "REGISTERED",
    amount: data.amount ?? 50000,
    paymentStatus: data.paymentStatus ?? "PAID",
  })]);
}

export async function seedReview(id: string, uid: string): Promise<void> {
  await commit([createWrite(`reviews/${id}`, {
    userId: uid,
    venueId: "v-other",
    rating: 5,
    userSnapshot: { displayName: "Người thử", avatarPath: `${uid}/avatar_1700000000000.jpg` },
  })]);
}

export async function seedFavorites(uid: string, venueIds: string[]): Promise<void> {
  await commit(venueIds.map((v) => createWrite(`users/${uid}/favorites/${v}`, { venueId: v })));
}

export async function docsIn(collectionId: string, parent?: string): Promise<QueryDocument[]> {
  return await query(collectionId, [], parent ? { parent } : {});
}

export type { StorageAdmin };
