import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, setDoc, updateDoc } from 'firebase/firestore';
import { createTestEnv, seedCurrentAdmin } from './setup';

let env: RulesTestEnvironment;

const unauth = () => env.unauthenticatedContext().firestore();
const player = (uid = 'player1') => env.authenticatedContext(uid, { appRole: 'PLAYER' }).firestore();
const otherPlayer = (uid = 'player2') => env.authenticatedContext(uid, { appRole: 'PLAYER' }).firestore();
const admin = () => env.authenticatedContext('admin1', { appRole: 'ADMIN' }).firestore();
const owner = (uid = 'owner1') => env.authenticatedContext(uid, { appRole: 'OWNER' }).firestore();

function validBookingData(userId = 'player1', overrides: Record<string, unknown> = {}) {
  return {
    userId,
    recurringGroupId: 'group-01',
    venueId: 'demo-venue-01',
    courtId: 'court-01',
    status: 'HOLD',
    price: 150000,
    depositAmount: 45000,
    ...overrides,
  };
}

function validPendingBookingData(userId = 'player1', overrides: Record<string, unknown> = {}) {
  return {
    userId,
    venueId: 'demo-venue-01',
    status: 'PENDING',
    subtotal: 180000,
    discount: 0,
    total: 180000,
    depositAmount: 0,
    paidAmount: 0,
    remainingAmount: 180000,
    paymentMethod: 'AT_VENUE',
    paymentStatus: 'UNPAID',
    items: [
      {
        slotId: 'court-01_20261109_2000',
        courtId: 'court-01',
        courtName: 'Sân 4 · PU',
        startAt: new Date(1731157200000),
        endAt: new Date(1731159000000),
        price: 30000,
      },
    ],
    startAt: new Date(1731157200000),
    endAt: new Date(1731159000000),
    venueSnapshot: {
      name: 'CLB Cầu Lông Gia Hân',
      address: '123 Đào Duy Anh',
      phone: '0901234567',
    },
    ...overrides,
  };
}

beforeAll(async () => {
  env = await createTestEnv();
});

beforeEach(async () => {
  await env.clearFirestore();
  await seedCurrentAdmin(env, 'admin1');
});

afterAll(async () => {
  await env.cleanup();
});

describe('bookings collection (Spec 041)', () => {
  describe('create', () => {
    it('player can create booking with HOLD status', async () => {
      const fs = player('player1');
      await assertSucceeds(setDoc(doc(fs, 'bookings/booking-01'), validBookingData('player1')));
    });

    it('create fails when userId does not match auth uid', async () => {
      const fs = player('player1');
      await assertFails(setDoc(doc(fs, 'bookings/booking-01'), validBookingData('player2')));
    });

    it('create fails when status is not HOLD', async () => {
      const fs = player('player1');
      await assertFails(
        setDoc(doc(fs, 'bookings/booking-01'), validBookingData('player1', { status: 'CONFIRMED' }))
      );
    });

    it('unauthenticated user cannot create booking', async () => {
      const fs = unauth();
      await assertFails(setDoc(doc(fs, 'bookings/booking-01'), validBookingData('player1')));
    });

    it('non-player cannot create booking', async () => {
      const fs = owner('owner1');
      await assertFails(setDoc(doc(fs, 'bookings/booking-01'), validBookingData('owner1')));
    });

    it('player can create booking with PENDING status and valid financial fields', async () => {
      const fs = player('player1');
      await assertSucceeds(setDoc(doc(fs, 'bookings/booking-pending-01'), validPendingBookingData('player1')));
    });

    it('create PENDING fails when userId does not match auth uid', async () => {
      const fs = player('player1');
      await assertFails(setDoc(doc(fs, 'bookings/booking-pending-01'), validPendingBookingData('player2')));
    });

    it('create PENDING fails when status is CONFIRMED', async () => {
      const fs = player('player1');
      await assertFails(
        setDoc(doc(fs, 'bookings/booking-pending-01'), validPendingBookingData('player1', { status: 'CONFIRMED' }))
      );
    });

    it('create PENDING fails when total != subtotal - discount', async () => {
      const fs = player('player1');
      await assertFails(
        setDoc(doc(fs, 'bookings/booking-pending-01'), validPendingBookingData('player1', { total: 150000, subtotal: 180000, discount: 0 }))
      );
    });

    it('create PENDING fails when negative amounts', async () => {
      const fs = player('player1');
      await assertFails(
        setDoc(doc(fs, 'bookings/booking-pending-01'), validPendingBookingData('player1', { remainingAmount: -1 }))
      );
    });

    it('non-player cannot create booking with PENDING status', async () => {
      const fs = owner('owner1');
      await assertFails(setDoc(doc(fs, 'bookings/booking-pending-01'), validPendingBookingData('owner1')));
    });

    it('unauthenticated user cannot create booking with PENDING status', async () => {
      const fs = unauth();
      await assertFails(setDoc(doc(fs, 'bookings/booking-pending-01'), validPendingBookingData('player1')));
    });
  });

  describe('read', () => {
    beforeEach(async () => {
      await env.withSecurityRulesDisabled(async (ctx) => {
        await setDoc(doc(ctx.firestore(), 'bookings/booking-01'), validBookingData('player1'));
      });
    });

    it('owner of booking can read their booking', async () => {
      const fs = player('player1');
      await assertSucceeds(getDoc(doc(fs, 'bookings/booking-01')));
    });

    it('another player cannot read the booking', async () => {
      const fs = otherPlayer('player2');
      await assertFails(getDoc(doc(fs, 'bookings/booking-01')));
    });

    it('admin can read any booking', async () => {
      const fs = admin();
      await assertSucceeds(getDoc(doc(fs, 'bookings/booking-01')));
    });

    it('signed in player can read non-existent booking (returns snapshot with exists == false)', async () => {
      const fs = player('player1');
      const snap = await getDoc(doc(fs, 'bookings/non-existent-booking-123'));
      expect(snap.exists()).toBe(false);
    });

    it('unauthenticated user cannot read booking', async () => {
      const fs = unauth();
      await assertFails(getDoc(doc(fs, 'bookings/booking-01')));
    });
  });

  describe('update', () => {
    beforeEach(async () => {
      await env.withSecurityRulesDisabled(async (ctx) => {
        await setDoc(doc(ctx.firestore(), 'bookings/booking-01'), validBookingData('player1'));
      });
    });

    it('owner cannot directly cancel a booking', async () => {
      const fs = player('player1');
      await assertFails(updateDoc(doc(fs, 'bookings/booking-01'), { status: 'CANCELLED' }));
    });

    it('owner cannot directly confirm a booking', async () => {
      const fs = player('player1');
      await assertFails(updateDoc(doc(fs, 'bookings/booking-01'), { status: 'CONFIRMED' }));
    });

    it('owner can expire their HOLD booking', async () => {
      const fs = player('player1');
      await assertSucceeds(
        updateDoc(doc(fs, 'bookings/booking-01'), { status: 'EXPIRED' })
      );
    });

    it('another player cannot update booking', async () => {
      const fs = otherPlayer('player2');
      await assertFails(
        updateDoc(doc(fs, 'bookings/booking-01'), { status: 'CANCELLED' })
      );
    });

    it('owner cannot modify fields other than status (e.g. price)', async () => {
      const fs = player('player1');
      await assertFails(
        updateDoc(doc(fs, 'bookings/booking-01'), { price: 200000 })
      );
      await assertFails(
        updateDoc(doc(fs, 'bookings/booking-01'), { status: 'CONFIRMED', price: 200000 })
      );
    });
  });

  describe('delete', () => {
    beforeEach(async () => {
      await env.withSecurityRulesDisabled(async (ctx) => {
        await setDoc(doc(ctx.firestore(), 'bookings/booking-01'), validBookingData('player1'));
      });
    });

    it('player cannot delete booking', async () => {
      const fs = player('player1');
      await assertFails(deleteDoc(doc(fs, 'bookings/booking-01')));
    });

    it('admin cannot delete booking (allow delete: if false)', async () => {
      const fs = admin();
      await assertFails(deleteDoc(doc(fs, 'bookings/booking-01')));
    });
  });
});
