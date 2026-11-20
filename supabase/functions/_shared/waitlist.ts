import {
  createWrite,
  type FirestoreTransaction,
  updateWrite,
  type Write,
} from "./firestore.ts";
import {
  newQrToken,
  newTicketCode,
  type SessionDoc,
  sessionPath,
} from "./dropin.ts";

export type WaitlistTx = Pick<FirestoreTransaction, "get" | "query" | "write">;
const tickets = (session: SessionDoc): number[] =>
  Array.isArray(session.waitlistTickets)
    ? [...session.waitlistTickets] as number[]
    : [];

function notification(
  uid: string,
  sessionId: string,
  type: string,
  data: Record<string, unknown>,
  now: Date,
): Write {
  return createWrite(`users/${uid}/notifications/${crypto.randomUUID()}`, {
    type,
    title: type === "DROPIN_SPOT_OPENED"
      ? "Bạn đã được nhận vào buổi"
      : "Hàng chờ đã đóng",
    body: type === "DROPIN_SPOT_OPENED"
      ? "Mở vé để xem thông tin buổi chơi."
      : "Lượt chờ của bạn đã kết thúc.",
    data: { sessionId, ...data },
    isRead: false,
    createdAt: now,
  });
}

/** All reads complete before writes. The caller supplies a session read in this transaction. */
export async function closeWaitlistInTx(
  tx: WaitlistTx,
  session: SessionDoc,
  reason: "CLOSED" | "SESSION_CANCELLED",
  now = new Date(),
): Promise<{ closed: string[] }> {
  if (!session.id) throw new Error("Missing session.id");
  const rows = await tx.query("registrations", [{
    field: "status",
    op: "EQUAL",
    value: "WAITLISTED",
  }], { parent: sessionPath(session.id) });
  for (const row of rows) {
    tx.write(updateWrite(row.path, {
      status: "CANCELLED",
      cancelledBy: reason === "CLOSED" ? "SYSTEM" : "OWNER",
      waitlistEndReason: reason,
      cancelledAt: now,
      refundAmount: 0,
      qrToken: null,
      ticketCode: null,
      updatedAt: now,
    }));
    tx.write(
      notification(
        row.id,
        session.id,
        "DROPIN_WAITLIST_CLOSED",
        { reason },
        now,
      ),
    );
  }
  const needsUpdate = rows.length > 0 || tickets(session).length > 0 ||
    session.waitlistCount !== 0;
  session.waitlistTickets = [];
  session.waitlistCount = 0;
  if (needsUpdate) {
    tx.write(
      updateWrite(sessionPath(session.id), {
        waitlistTickets: [],
        waitlistCount: 0,
        updatedAt: now,
      }),
    );
  }
  return { closed: rows.map((row) => row.id) };
}

/** FIFO, skipping parties that do not fit; overlap removals share this transaction. */
export async function promoteFromWaitlist(
  tx: WaitlistTx,
  session: SessionDoc,
  now: Date,
) {
  const promoted: { uid: string; autoLeftSessionIds: string[] }[] = [];
  if (
    !session.id || !["OPEN", "FULL"].includes(String(session.status)) ||
    !(session.startAt instanceof Date) ||
    now.getTime() >= session.startAt.getTime() - 30 * 60_000
  ) return { promoted };
  const rows = await tx.query("registrations", [{
    field: "status",
    op: "EQUAL",
    value: "WAITLISTED",
  }], { parent: sessionPath(session.id) });
  rows.sort((a, b) =>
    Number(a.data.waitlistTicket) - Number(b.data.waitlistTicket)
  );
  const writes: Write[] = [];
  const otherSessions = new Map<string, SessionDoc>();
  let remainingTickets = tickets(session);
  let count = Number(session.registeredCount ?? 0);
  for (const row of rows) {
    const size = Number(row.data.partySize);
    if (
      !Number.isInteger(size) || size < 1 ||
      size > Number(session.capacity) - count
    ) continue;
    const overlaps = await tx.query("registrations", [
      { field: "userId", op: "EQUAL", value: row.id },
      { field: "status", op: "EQUAL", value: "WAITLISTED" },
    ], { allDescendants: true });
    const autoLeftSessionIds: string[] = [];
    for (const other of overlaps) {
      const otherId = other.path.split("/")[1];
      if (
        otherId === session.id || !(session.endAt instanceof Date) ||
        !(other.data.sessionStartAt instanceof Date) ||
        !(other.data.sessionEndAt instanceof Date) ||
        other.data.sessionStartAt >= session.endAt ||
        other.data.sessionEndAt <= session.startAt
      ) continue;
      let otherSession = otherSessions.get(otherId);
      if (!otherSession) {
        otherSession = await tx.get(sessionPath(otherId)) ?? undefined;
        if (!otherSession) {
          throw new Error(`Missing overlapping session ${otherId}`);
        }
        otherSessions.set(otherId, otherSession);
      }
      otherSession.waitlistTickets = tickets(otherSession).filter((ticket) =>
        ticket !== other.data.waitlistTicket
      );
      otherSession.waitlistCount = tickets(otherSession).length;
      writes.push(
        updateWrite(other.path, {
          status: "CANCELLED",
          cancelledBy: "SYSTEM",
          waitlistEndReason: "AUTO_LEFT_OVERLAP",
          cancelledAt: now,
          updatedAt: now,
        }),
      );
      autoLeftSessionIds.push(otherId);
    }
    writes.push(updateWrite(row.path, {
      status: "REGISTERED",
      promotedAt: now,
      registeredAt: now,
      sessionStartAt: session.startAt,
      sessionEndAt: session.endAt,
      paymentStatus: row.data.paymentMethod === "MOCK_PAY" ? "PAID" : "UNPAID",
      qrToken: newQrToken(),
      ticketCode: newTicketCode(),
      waitlistEndReason: null,
      updatedAt: now,
    }));
    writes.push(
      notification(row.id, session.id, "DROPIN_SPOT_OPENED", {
        autoLeftSessionIds,
      }, now),
    );
    remainingTickets = remainingTickets.filter((ticket) =>
      ticket !== row.data.waitlistTicket
    );
    count += size;
    promoted.push({ uid: row.id, autoLeftSessionIds });
  }
  session.registeredCount = count;
  session.status = count >= Number(session.capacity) ? "FULL" : "OPEN";
  session.waitlistTickets = remainingTickets;
  session.waitlistCount = remainingTickets.length;
  for (const [id, other] of otherSessions) {
    writes.push(updateWrite(sessionPath(id), {
      waitlistTickets: other.waitlistTickets,
      waitlistCount: other.waitlistCount,
      updatedAt: now,
    }));
  }
  writes.push(updateWrite(sessionPath(session.id), {
    registeredCount: count,
    status: session.status,
    waitlistTickets: remainingTickets,
    waitlistCount: remainingTickets.length,
    updatedAt: now,
  }));
  for (const write of writes) tx.write(write);
  return { promoted };
}
