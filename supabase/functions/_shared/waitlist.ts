// Hàng chờ buổi vãng lai (C, spec 081; contracts/edge-functions-waitlist.md mục 5, research R3–R5, R7, R10).
// Người dùng: cancel-drop-in và dropin-close (081), delete-account (A, 011), tăng sức chứa/hủy buổi (D, spec 112).
//
// Các hàm `...InTx` chạy BÊN TRONG transaction của người gọi: chỉ đọc bằng `tx.get`/`tx.query` và gom ghi bằng
// `tx.write`, không tự commit. Mọi thao tác đọc nằm trước mọi thao tác ghi: transaction của `_shared/firestore.ts`
// không cho đọc sau khi đã ghi, nên mỗi hàm đọc hết rồi mới ghi một lượt ở cuối.
import { HttpError } from "./auth.ts";
import { activeRecipient, prepareNotification } from "./notification-enqueue.ts";
import { createWrite, updateWrite, type QueryDocument, type QueryFilter, type QueryOptions, type Write } from "./firestore.ts";
import {
  type CancelledBy,
  type DropInTx,
  newQrToken,
  newTicketCode,
  registrationPath,
  type RegistrationDoc,
  type SessionDoc,
  sessionPath,
} from "./dropin.ts";

/** Transaction có truy vấn trong transaction (`FirestoreTransaction`, `DeletionTx` đều khớp). */
export interface WaitlistTx extends DropInTx {
  query(collectionId: string, filters: QueryFilter[], opts?: QueryOptions): Promise<QueryDocument[]>;
}

/** Hàng chờ đóng khi còn không quá bấy nhiêu phút nữa tới giờ bắt đầu (FR-014). */
export const WAITLIST_CLOSE_MINUTES = 30;
const WAITLIST_CLOSE_MS = WAITLIST_CLOSE_MINUTES * 60_000;

export interface PromoteResult {
  promoted: { uid: string; autoLeftSessionIds: string[] }[];
  /**
   * Các trường buổi đã đổi, để NGƯỜI GỌI ghi vào document buổi (`updateWrite(sessionPath(id), { ...sessionPatch,
   * updatedAt })`): `registeredCount`, `status` luôn có; `waitlistTickets`, `waitlistCount` chỉ có khi có người được
   * đẩy lên. `promoteFromWaitlist` KHÔNG ghi document buổi để không trùng với lần ghi của người gọi
   * (`cancelRegistrationInTx` ghi buổi đúng một lần; D ghi `capacity` khi tăng sức chứa).
   */
  sessionPatch: Record<string, unknown>;
}

export type WaitlistLeaveReason = "LEFT" | "AUTO_LEFT_OVERLAP";
export type WaitlistCloseReason = "CLOSED" | "SESSION_CANCELLED";

const asDate = (v: unknown): Date | null => (v instanceof Date && !Number.isNaN(v.getTime()) ? v : null);
const asNumber = (v: unknown, fallback = 0): number => (typeof v === "number" && Number.isFinite(v) ? v : fallback);
const ticketsOf = (s: SessionDoc): number[] =>
  Array.isArray(s.waitlistTickets) ? s.waitlistTickets.filter((n): n is number => typeof n === "number") : [];

function sessionIdOf(session: SessionDoc): string {
  if (typeof session.id !== "string" || !session.id) throw new Error("session.id là bắt buộc (hàng chờ tìm lượt theo buổi)");
  return session.id;
}

/** Hàng chờ còn mở tại `serverNow`: còn hơn 30 phút nữa mới tới giờ bắt đầu. */
export function isWaitlistOpen(session: SessionDoc, serverNow: Date): boolean {
  const startAt = asDate(session.startAt);
  return startAt != null && serverNow.getTime() < startAt.getTime() - WAITLIST_CLOSE_MS;
}

const byTicket = (a: QueryDocument, b: QueryDocument) =>
  asNumber(a.data.waitlistTicket, Number.MAX_SAFE_INTEGER) - asNumber(b.data.waitlistTicket, Number.MAX_SAFE_INTEGER);

// Persist the inbox entry with the state change; callers may deliver push after commit.
async function inboxWrites(tx: WaitlistTx, uid: string, sessionId: string, ticket: unknown, type: "DROPIN_SPOT_OPENED" | "DROPIN_WAITLIST_CLOSED", data: Record<string, unknown>, now: Date): Promise<Write[]> {
  const content = {
    type, title: type === "DROPIN_SPOT_OPENED" ? "Bạn đã được nhận vào buổi" : "Hàng chờ đã đóng",
    body: type === "DROPIN_SPOT_OPENED" ? "Mở vé để xem thông tin buổi chơi." : "Lượt chờ của bạn đã kết thúc.",
    data: { sessionId, ...data },
  };
  const id = crypto.randomUUID();
  if (typeof ticket === "number" && Number.isSafeInteger(ticket) && ticket >= 0) return await prepareNotification(
    tx, "waitlist", JSON.stringify([sessionId, uid, ticket, type]), uid, id, content, now);
  // Legacy registration without a ticket: preserve inbox, never guess a delivery generation.
  return activeRecipient(await tx.get(`users/${uid}`))
    ? [createWrite(`users/${uid}/notifications/${id}`, { ...content, isRead: false, createdAt: now })] : [];
}

/**
 * Đẩy người chờ lên khi buổi có chỗ trống (081 FR-007..FR-010a; research R3–R5), trong transaction của người gọi.
 *
 * Gọi SAU khi người gọi đã trừ `session.registeredCount` (và/hoặc tăng `session.capacity`) trên object `session`
 * (cần `session.id`), TRƯỚC khi người gọi ghi bất cứ thứ gì. Hàm sửa tại chỗ `session.registeredCount`,
 * `session.status`, `session.waitlistTickets`, `session.waitlistCount` và trả cùng các giá trị trong `sessionPatch`.
 *
 * - Hàng chờ đã đóng (`serverNow ≥ startAt − 30 phút`) hoặc buổi không còn `OPEN`/`FULL`: không đẩy ai.
 * - Duyệt lượt chờ theo `waitlistTicket` tăng dần: lượt có `partySize` lớn hơn số chỗ còn lại bị bỏ qua nhưng giữ
 *   vị trí; lượt vừa chỗ được chuyển `REGISTERED` kèm vé QR (R4).
 * - Người được đẩy lên tự rời hàng chờ các buổi trùng giờ khác của chính họ (R5). Nhiều người cùng rời một buổi X
 *   thì gộp thành MỘT lần ghi document buổi X.
 */
export async function promoteFromWaitlist(tx: WaitlistTx, session: SessionDoc, serverNow: Date): Promise<PromoteResult> {
  const sessionId = sessionIdOf(session);
  const capacity = asNumber(session.capacity);
  const live = session.status === "OPEN" || session.status === "FULL";
  const tickets = ticketsOf(session);
  const startAt = asDate(session.startAt);
  const endAt = asDate(session.endAt);
  let registeredCount = asNumber(session.registeredCount);
  let free = capacity - registeredCount;

  const chosen: QueryDocument[] = [];
  if (live && isWaitlistOpen(session, serverNow) && free > 0 && tickets.length > 0) {
    const candidates = await tx.query("registrations", [{ field: "status", op: "EQUAL", value: "WAITLISTED" }], {
      parent: sessionPath(sessionId),
      orderBy: [{ field: "waitlistTicket" }],
    });
    for (const c of [...candidates].sort(byTicket)) {
      if (free <= 0) break;
      const partySize = asNumber(c.data.partySize, 1);
      if (!Number.isInteger(partySize) || partySize < 1) continue;
      if (partySize > free) continue; // bỏ qua nhưng giữ vị trí
      free -= partySize;
      registeredCount += partySize;
      chosen.push(c);
    }
  }

  // ---- Đọc hết lượt chờ trùng giờ và buổi tương ứng trước khi ghi ----
  const overlaps = new Map<string, QueryDocument[]>(); // uid được đẩy lên → lượt chờ trùng giờ ở buổi khác
  const leavingBySession = new Map<string, QueryDocument[]>(); // buổi X → các lượt chờ tự rời
  if (endAt && startAt) {
    for (const c of chosen) {
      const found = await tx.query("registrations", [
        { field: "userId", op: "EQUAL", value: c.id },
        { field: "status", op: "EQUAL", value: "WAITLISTED" },
        { field: "sessionStartAt", op: "LESS_THAN", value: endAt },
      ], { allDescendants: true });
      const mine = found.filter((d) => {
        const end = asDate(d.data.sessionEndAt);
        return d.path !== c.path && d.data.sessionId !== sessionId && end != null && end.getTime() > startAt.getTime();
      });
      overlaps.set(c.id, mine);
      for (const d of mine) {
        const x = String(d.data.sessionId ?? d.path.split("/")[1]);
        leavingBySession.set(x, [...(leavingBySession.get(x) ?? []), d]);
      }
    }
  }
  const otherSessions = new Map<string, SessionDoc | null>();
  for (const x of leavingBySession.keys()) otherSessions.set(x, await tx.get(sessionPath(x)) as SessionDoc | null);

  const notifications = new Map<string, Write[]>();
  for (const c of chosen) notifications.set(c.id, await inboxWrites(tx, c.id, sessionId, c.data.waitlistTicket,
    "DROPIN_SPOT_OPENED", { autoLeftSessionIds: (overlaps.get(c.id) ?? []).map(d => String(d.data.sessionId ?? d.path.split("/")[1])) }, serverNow));
  // ---- Ghi ----
  const promoted: PromoteResult["promoted"] = [];
  for (const c of chosen) {
    const method = c.data.paymentMethod;
    tx.write(updateWrite(c.path, {
      status: "REGISTERED",
      promotedAt: serverNow,
      registeredAt: serverNow,
      paymentStatus: method === "MOCK_PAY" ? "PAID" : "UNPAID",
      qrToken: newQrToken(),
      ticketCode: newTicketCode(),
      ...(startAt ? { sessionStartAt: startAt } : {}),
      ...(endAt ? { sessionEndAt: endAt } : {}),
      waitlistEndReason: null,
      updatedAt: serverNow,
    }));
    const left = overlaps.get(c.id) ?? [];
    for (const d of left) {
      tx.write(updateWrite(d.path, {
        status: "CANCELLED",
        cancelledAt: serverNow,
        cancelledBy: "SYSTEM",
        waitlistEndReason: "AUTO_LEFT_OVERLAP",
        updatedAt: serverNow,
      }));
    }
    const autoLeftSessionIds = left.map((d) => String(d.data.sessionId ?? d.path.split("/")[1]));
    for (const write of notifications.get(c.id) ?? []) tx.write(write);
    promoted.push({ uid: c.id, autoLeftSessionIds });
  }
  for (const [x, docs] of leavingBySession) {
    const doc = otherSessions.get(x);
    if (!doc || !Array.isArray(doc.waitlistTickets)) continue;
    const gone = new Set(docs.map((d) => asNumber(d.data.waitlistTicket, -1)));
    const remaining = ticketsOf(doc).filter((t) => !gone.has(t));
    tx.write(updateWrite(sessionPath(x), { waitlistTickets: remaining, waitlistCount: remaining.length, updatedAt: serverNow }));
  }

  session.registeredCount = registeredCount;
  if (live) session.status = registeredCount >= capacity ? "FULL" : "OPEN";
  const sessionPatch: Record<string, unknown> = {
    registeredCount,
    ...(session.status !== undefined ? { status: session.status } : {}),
  };
  if (chosen.length > 0) {
    const gone = new Set(chosen.map((c) => asNumber(c.data.waitlistTicket, -1)));
    const remaining = tickets.filter((t) => !gone.has(t));
    session.waitlistTickets = remaining;
    session.waitlistCount = remaining.length;
    sessionPatch.waitlistTickets = remaining;
    sessionPatch.waitlistCount = remaining.length;
  }
  return { promoted, sessionPatch };
}

/**
 * Kết thúc mọi lượt chờ của buổi (`CLOSED` tới mốc T−30 phút, `SESSION_CANCELLED` khi chủ sân hủy buổi) và trả danh
 * sách uid để gửi thông báo sau commit. Ghi các lượt chờ và HAI trường `waitlistTickets`, `waitlistCount` của buổi
 * (các trường khác của buổi, ví dụ `status`, do người gọi ghi). Sửa tại chỗ `session.waitlistTickets/waitlistCount`.
 * Chạy lại khi hàng chờ đã trống thì không ghi gì.
 */
export async function closeWaitlistInTx(
  tx: WaitlistTx,
  session: SessionDoc,
  reason: WaitlistCloseReason,
  opts: { now?: Date } = {},
): Promise<{ closed: string[] }> {
  const sessionId = sessionIdOf(session);
  const now = opts.now ?? new Date();
  const docs = await tx.query("registrations", [{ field: "status", op: "EQUAL", value: "WAITLISTED" }], {
    parent: sessionPath(sessionId),
  });
  const stale = ticketsOf(session).length > 0 || asNumber(session.waitlistCount) !== 0;
  if (docs.length === 0 && !stale) return { closed: [] };
  const cancelledBy: CancelledBy = reason === "CLOSED" ? "SYSTEM" : "OWNER";
  const notifications = new Map<string, Write[]>();
  for (const d of docs) notifications.set(d.id, await inboxWrites(tx, d.id, sessionId, d.data.waitlistTicket,
    "DROPIN_WAITLIST_CLOSED", { reason }, now));
  for (const d of docs) {
    tx.write(updateWrite(d.path, {
      status: "CANCELLED",
      cancelledAt: now,
      cancelledBy,
      waitlistEndReason: reason,
      updatedAt: now,
    }));
    for (const write of notifications.get(d.id) ?? []) tx.write(write);
  }
  tx.write(updateWrite(sessionPath(sessionId), { waitlistTickets: [], waitlistCount: 0, updatedAt: now }));
  session.waitlistTickets = [];
  session.waitlistCount = 0;
  return { closed: docs.map((d) => d.id) };
}

/**
 * Rời hàng chờ của `uid` ở buổi `sessionId` (người dùng bấm "Rời hàng chờ", tự rời do trùng giờ, hoặc xóa tài khoản):
 * lượt → `CANCELLED` kèm `waitlistEndReason = reason`, buổi bỏ số vé khỏi `waitlistTickets`. Không phát sinh tiền.
 *
 * Đọc lại đăng ký trong transaction (research R10): lượt đã `REGISTERED`/`CHECKED_IN` (vừa được đẩy lên) thì ném
 * `409 ALREADY_PROMOTED` và không ghi gì; không có lượt hoặc đã kết thúc thì bỏ qua.
 */
export async function leaveWaitlistInTx(
  tx: DropInTx,
  sessionId: string,
  uid: string,
  reason: WaitlistLeaveReason,
  cancelledBy: "USER" | "SYSTEM",
  opts: { now?: Date; extra?: Record<string, unknown> } = {},
): Promise<void> {
  const now = opts.now ?? new Date();
  const regPath = registrationPath(sessionId, uid);
  const reg = await tx.get(regPath) as RegistrationDoc | null;
  if (!reg) return;
  if (reg.status === "REGISTERED" || reg.status === "CHECKED_IN") {
    throw new HttpError(409, "ALREADY_PROMOTED", "Bạn đã được nhận vào buổi");
  }
  if (reg.status !== "WAITLISTED") return;
  const session = await tx.get(sessionPath(sessionId)) as SessionDoc | null;

  const writes: Write[] = [updateWrite(regPath, {
    status: "CANCELLED",
    cancelledAt: now,
    cancelledBy,
    waitlistEndReason: reason,
    ...opts.extra, // trường bổ sung của người gọi (ví dụ `cancelRequestId`), cùng một lần ghi lượt
    updatedAt: now,
  })];
  if (session && Array.isArray(session.waitlistTickets)) {
    const remaining = ticketsOf(session).filter((t) => t !== reg.waitlistTicket);
    writes.push(updateWrite(sessionPath(sessionId), { waitlistTickets: remaining, waitlistCount: remaining.length, updatedAt: now }));
  }
  for (const w of writes) tx.write(w);
}
