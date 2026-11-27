import * as fs from "../_shared/firestore.ts";
import { prepareNotification } from "../_shared/notification-enqueue.ts";
import { bookingEventIdentity, bookingInboxId, earliestBookingStart } from "../_shared/booking-notification.ts";

export async function handleBookingReminders(req: Request, deps: {
  firestore?: Pick<typeof fs, "query" | "runTransaction">; now?: () => Date; cronSecret?: string;
} = {}) {
  if (req.method !== "POST") return new Response(null, { status: 405 });
  const secret = deps.cronSecret ?? Deno.env.get("CRON_SECRET");
  if (!secret || req.headers.get("x-cron-secret") !== secret) return new Response(null, { status: 401 });
  const source = deps.firestore ?? fs, clock = deps.now ?? (() => new Date()), captured = clock();
  const upper = new Date(captured.getTime() + 7200_000);
  let cursor: fs.QueryCursorValue[] | undefined, scanned = 0, created = 0, skipped = 0;
  try {
    do {
      const rows = await source.query("bookings", [
        { field: "status", op: "EQUAL", value: "CONFIRMED" },
        { field: "startAt", op: "GREATER_THAN", value: captured },
        { field: "startAt", op: "LESS_THAN_OR_EQUAL", value: upper },
      ], { limit: 100, orderBy: [{ field: "startAt" }, { field: "__name__" }], startAfter: cursor, withCursor: true });
      for (const row of rows) {
        if (clock().getTime() - captured.getTime() >= 90_000) return Response.json({ scanned, created, skipped, budgetExhausted: true });
        scanned++;
        const count = await source.runTransaction(async tx => {
          const booking = await tx.get(row.path), now = clock();
          if (!booking || booking.status !== "CONFIRMED") return 0;
          const start = earliestBookingStart(booking), uid = booking.userId;
          if (!start || !(booking.startAt instanceof Date) || booking.startAt.getTime() !== start.getTime() ||
            start <= now || start.getTime() > now.getTime() + 7200_000 || typeof uid !== "string" || !uid || uid.includes("/")) return 0;
          const identity = bookingEventIdentity(row.id, "BOOKING_REMINDER");
          const inboxId = await bookingInboxId("booking-reminder", identity, uid, "BOOKING_REMINDER");
          if (await tx.get(`users/${uid}/notifications/${inboxId}`)) return 0;
          const writes = await prepareNotification(tx, "booking-reminder", identity, uid,
            inboxId, {
              type: "BOOKING_REMINDER", title: "Sắp tới giờ chơi", body: `Lịch đặt sân bắt đầu lúc ${start.toISOString()}.`,
              data: { bookingId: row.id, venueId: booking.venueId ?? null, reminderStartAt: start },
            }, now);
          for (const write of writes) tx.write(write);
          return writes.length ? 1 : 0;
        });
        created += count; skipped += count ? 0 : 1;
      }
      const last = rows.at(-1);
      if (last && rows.length === 100 && !last.cursorValues) throw new Error("MISSING_QUERY_CURSOR");
      cursor = last && rows.length === 100 ? last.cursorValues : undefined;
    } while (cursor);
    return Response.json({ scanned, created, skipped, budgetExhausted: false });
  } catch { return Response.json({ error: "REMINDERS_UNAVAILABLE" }, { status: 503 }); }
}
