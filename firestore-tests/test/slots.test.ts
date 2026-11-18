import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, deleteDoc, getDoc, setDoc, Timestamp, updateDoc, writeBatch } from 'firebase/firestore';
import { createTestEnv } from './setup';

let env: RulesTestEnvironment;

const unauth = () => env.unauthenticatedContext().firestore();
const player = (uid = 'player1') => env.authenticatedContext(uid, { appRole: 'PLAYER' }).firestore();
const otherPlayer = (uid = 'player2') => env.authenticatedContext(uid, { appRole: 'PLAYER' }).firestore();

beforeAll(async () => {
  env = await createTestEnv();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const fs = ctx.firestore();
    await setDoc(doc(fs, 'venues/demo-venue-01'), {
      name: 'CLB Cầu Lông SmashNow',
      ownerId: 'owner1',
      openTime: '0600',
      closeTime: '2200',
      status: 'ACTIVE',
    });
    await setDoc(doc(fs, 'venues/demo-venue-01/courts/court-01'), {
      name: 'Sân 1',
      isActive: true,
      sortOrder: 1,
    });
    await setDoc(doc(fs, 'venues/demo-venue-01/priceRules/rule-01'), {
      startTime: '0600',
      endTime: '1700',
      pricePerHour: 80000,
    });
  });
});

afterAll(async () => {
  await env.cleanup();
});

describe('venues and subcollections (Spec 040)', () => {
  it('anyone can read venues, courts, and priceRules', async () => {
    const fs = unauth();
    await assertSucceeds(getDoc(doc(fs, 'venues/demo-venue-01')));
    await assertSucceeds(getDoc(doc(fs, 'venues/demo-venue-01/courts/court-01')));
    await assertSucceeds(getDoc(doc(fs, 'venues/demo-venue-01/priceRules/rule-01')));
  });
});

describe('slots collection (T022)', () => {
  it('anyone can read slots', async () => {
    const fs = unauth();
    await assertSucceeds(getDoc(doc(fs, 'slots/court-01_20261027_0600')));
  });

  it('unauthenticated users cannot hold slots', async () => {
    const fs = unauth();
    const futureExpiry = Timestamp.fromMillis(Date.now() + 4 * 60 * 1000);
    await assertFails(
      setDoc(doc(fs, 'slots/court-01_20261027_0600'), {
        venueId: 'demo-venue-01',
        courtId: 'court-01',
        date: '20261027',
        startTime: '0600',
        endTime: '0630',
        status: 'HELD',
        holdExpiresAt: futureExpiry,
        holdBy: 'player1',
        bookingId: 'req-01',
      })
    );
  });

  it('authenticated player can hold an available slot within 5 minutes', async () => {
    const fs = player('player1');
    const futureExpiry = Timestamp.fromMillis(Date.now() + 4 * 60 * 1000);
    await assertSucceeds(
      setDoc(doc(fs, 'slots/court-01_20261027_0600'), {
        venueId: 'demo-venue-01',
        courtId: 'court-01',
        date: '20261027',
        startTime: '0600',
        endTime: '0630',
        status: 'HELD',
        holdExpiresAt: futureExpiry,
        holdBy: 'player1',
        bookingId: 'req-01',
      })
    );
  });

  it('holding with expiry exceeding 5 minutes is blocked', async () => {
    const fs = player('player1');
    const tooFarFutureExpiry = Timestamp.fromMillis(Date.now() + 10 * 60 * 1000); // 10 minutes (cheat)
    await assertFails(
      setDoc(doc(fs, 'slots/court-01_20261027_0600'), {
        venueId: 'demo-venue-01',
        courtId: 'court-01',
        date: '20261027',
        startTime: '0600',
        endTime: '0630',
        status: 'HELD',
        holdExpiresAt: tooFarFutureExpiry,
        holdBy: 'player1',
        bookingId: 'req-01',
      })
    );
  });

  it('cannot overwrite an active hold by another player', async () => {
    // Seed active hold by player1
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'slots/court-01_20261027_0600'), {
        venueId: 'demo-venue-01',
        courtId: 'court-01',
        date: '20261027',
        startTime: '0600',
        endTime: '0630',
        status: 'HELD',
        holdExpiresAt: Timestamp.fromMillis(Date.now() + 4 * 60 * 1000),
        holdBy: 'player1',
        bookingId: 'req-01',
      });
    });

    const fs = otherPlayer('player2');
    const futureExpiry = Timestamp.fromMillis(Date.now() + 4 * 60 * 1000);
    await assertFails(
      updateDoc(doc(fs, 'slots/court-01_20261027_0600'), {
        status: 'HELD',
        holdExpiresAt: futureExpiry,
        holdBy: 'player2',
        bookingId: 'req-02',
      })
    );
  });

  it('holding player can delete their held slot, but others cannot', async () => {
    // Seed active hold by player1
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'slots/court-01_20261027_0600'), {
        venueId: 'demo-venue-01',
        courtId: 'court-01',
        date: '20261027',
        startTime: '0600',
        endTime: '0630',
        status: 'HELD',
        holdExpiresAt: Timestamp.fromMillis(Date.now() + 4 * 60 * 1000),
        holdBy: 'player1',
        bookingId: 'req-01',
      });
    });

    const fsOther = otherPlayer('player2');
    await assertFails(deleteDoc(doc(fsOther, 'slots/court-01_20261027_0600')));

    const fsHolder = player('player1');
    await assertSucceeds(deleteDoc(doc(fsHolder, 'slots/court-01_20261027_0600')));
  });
});

// Rà soát 040 ngày 2026-11-18: người giữ chỗ không được trả slot đã đặt về trống hoặc tự đánh BOOKED khi không có đơn.
describe('slot integrity after booking (review 2026-11-18)', () => {
  const SLOT = 'slots/court-01_20261027_0600';

  const seedSlot = (status: string, extra: Record<string, unknown> = {}) =>
    env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), SLOT), {
        venueId: 'demo-venue-01',
        courtId: 'court-01',
        date: '20261027',
        startTime: '0600',
        endTime: '0630',
        status,
        holdExpiresAt: status === 'HELD' ? Timestamp.fromMillis(Date.now() + 4 * 60 * 1000) : null,
        holdBy: 'player1',
        bookingId: 'req-01',
        ...extra,
      });
    });

  const pendingBooking = (userId = 'player1') => ({
    userId,
    venueId: 'demo-venue-01',
    status: 'PENDING',
    subtotal: 40000,
    discount: 0,
    total: 40000,
    depositAmount: 0,
    paidAmount: 0,
    remainingAmount: 40000,
    paymentMethod: 'AT_VENUE',
    paymentStatus: 'UNPAID',
  });

  const confirmBatch = (fs: ReturnType<typeof player>, bookingId: string, slotBookingId = bookingId) => {
    const batch = writeBatch(fs);
    batch.set(doc(fs, `bookings/${bookingId}`), pendingBooking());
    batch.update(doc(fs, SLOT), { status: 'BOOKED', holdExpiresAt: null, bookingId: slotBookingId });
    return batch.commit();
  };

  it('holder cannot release a BOOKED slot back to AVAILABLE', async () => {
    await seedSlot('BOOKED', { holdExpiresAt: null });
    await assertFails(
      updateDoc(doc(player('player1'), SLOT), { status: 'AVAILABLE', holdExpiresAt: null, holdBy: null, bookingId: null })
    );
  });

  it('holder cannot delete a BOOKED slot', async () => {
    await seedSlot('BOOKED', { holdExpiresAt: null });
    await assertFails(deleteDoc(doc(player('player1'), SLOT)));
  });

  it('holder can still release their own HELD slot', async () => {
    await seedSlot('HELD');
    await assertSucceeds(
      updateDoc(doc(player('player1'), SLOT), { status: 'AVAILABLE', holdExpiresAt: null, holdBy: null, bookingId: null })
    );
  });

  it('holder cannot mark a HELD slot BOOKED without a booking', async () => {
    await seedSlot('HELD');
    await assertFails(updateDoc(doc(player('player1'), SLOT), { status: 'BOOKED', holdExpiresAt: null }));
  });

  it('holder can mark a HELD slot BOOKED together with their PENDING booking', async () => {
    await seedSlot('HELD');
    await assertSucceeds(confirmBatch(player('player1'), 'req-01'));
  });

  it('booking id must match the id given when the slot was held', async () => {
    await seedSlot('HELD');
    await assertFails(confirmBatch(player('player1'), 'req-other'));
  });

  it('an expired hold cannot be confirmed', async () => {
    await seedSlot('HELD', { holdExpiresAt: Timestamp.fromMillis(Date.now() - 60 * 1000) });
    await assertFails(confirmBatch(player('player1'), 'req-01'));
  });
});
