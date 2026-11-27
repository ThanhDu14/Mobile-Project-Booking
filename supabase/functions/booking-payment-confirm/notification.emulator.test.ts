import { assert, assertEquals } from "jsr:@std/assert@1.0.19";
import * as fs from "../_shared/firestore.ts";
import { bearer, emulatorToken } from "../_shared/testing.ts";
import { defaultDeps, handleBookingPaymentConfirm } from "./handler.ts";

const enabled = Deno.env.get("FIREBASE_PROJECT_ID")?.startsWith("demo-") &&
  !!Deno.env.get("FIRESTORE_EMULATOR_HOST") && !!Deno.env.get("FIREBASE_AUTH_EMULATOR_HOST");

Deno.test({
  name: "B1 checkout HOLD to PENDING atomically with voucher, current owner inbox/job and concurrent replay",
  ignore: !enabled,
  async fn() {
    const [uid, owner, other, venue, booking, court] = Array.from({ length: 6 }, () => crypto.randomUUID());
    const slot = `${court}_20300102_1800`;
    const now = new Date("2030-01-02T06:00:00Z");
    const bookingPath = `bookings/${booking}`, slotPath = `slots/${slot}`;
    const voucherPath = `venues/${venue}/vouchers/B1DEMO`;
    const countPath = `${voucherPath}/userUsage/${uid}`;
    const inboxPath = `users/${owner}/notifications/booking_new_${booking}`;
    await fs.commit([
      fs.createWrite(`users/${uid}`, { role: "PLAYER", accountStatus: "ACTIVE" }),
      fs.createWrite(`users/${owner}`, { role: "OWNER", ownerStatus: "APPROVED", accountStatus: "ACTIVE" }),
      fs.createWrite(`users/${other}`, { role: "OWNER", ownerStatus: "APPROVED", accountStatus: "ACTIVE" }),
      fs.createWrite(`venues/${venue}`, { ownerId: owner, name: "Demo", status: "ACTIVE" }),
      fs.createWrite(`venues/${venue}/courts/${court}`, { name: "Demo" }),
      fs.createWrite(`venues/${venue}/priceRules/base`, {
        daysOfWeek: [], startTime: "0600", endTime: "2200", pricePerHour: 120_000,
      }),
      fs.createWrite(voucherPath, {
        type: "FIXED", value: 10_000, minOrderTotal: 0, isActive: true, usageLimit: 1, usedCount: 0, perUserLimit: 1,
      }),
      fs.createWrite(bookingPath, { venueId: venue, userId: uid, status: "HOLD", items: [{ slotId: slot }] }),
      fs.createWrite(slotPath, {
        venueId: venue, courtId: court, status: "HELD", holdBy: uid, bookingId: booking,
        holdExpiresAt: new Date(now.getTime() + 120_000),
      }),
    ]);
    const call = (deps = fs) => handleBookingPaymentConfirm(
      bearer(emulatorToken({ sub: uid, user_id: uid, appRole: "PLAYER" }), { body: JSON.stringify({
        requestId: booking, venueId: venue, slotIds: [slot], voucherCode: "B1DEMO", expectedTotal: 50_000,
        ownerUid: other,
      }) }),
      { ...defaultDeps(), now: () => now, firestore: deps },
    );

    const rollback: typeof fs.runTransaction = (operation, attempts) => fs.runTransaction(async tx => {
      await operation(tx);
      throw new Error("injected checkout rollback");
    }, attempts);
    const failed = await call({ ...fs, runTransaction: rollback });
    assertEquals(failed.status, 500);
    await failed.body?.cancel();
    assertEquals((await fs.get(bookingPath))?.status, "HOLD");
    assertEquals(await fs.get(countPath), null);
    assertEquals(await fs.get(inboxPath), null);

    const responses = await Promise.all([call(), call()]);
    for (const response of responses) assertEquals(response.status, 200, await response.text());
    const pending = await fs.get(bookingPath);
    assertEquals(pending?.status, "PENDING");
    assertEquals((await fs.get(slotPath))?.status, "BOOKED");
    assertEquals((await fs.get(countPath))?.count, 1);
    assertEquals((await fs.get(voucherPath))?.usedCount, 1);
    assertEquals((await fs.get(`${voucherPath}/redemptions/${booking}`))?.status, "USED");
    const saved = await fs.get(inboxPath);
    assertEquals(saved?.data, { bookingId: booking, venueId: venue });
    assert(typeof saved?.body === "string" && saved.body.includes((pending?.approvalDeadline as Date).toISOString()));
    assertEquals(await fs.get(`users/${other}/notifications/booking_new_${booking}`), null);
    const jobs = await fs.query("notificationDeliveries", [{ field: "recipientUid", op: "EQUAL", value: owner }]);
    assertEquals(jobs.map(j => j.data.notificationId), [`booking_new_${booking}`]);
  },
});
