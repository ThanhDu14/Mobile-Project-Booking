import { afterAll, beforeAll, beforeEach, describe, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, setDoc, updateDoc } from 'firebase/firestore';
import { createTestEnv } from './setup';

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

beforeAll(async () => {
  env = await createTestEnv();
});

beforeEach(async () => {
  await env.clearFirestore();
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

    it('owner can update status to CANCELLED', async () => {
      const fs = player('player1');
      await assertSucceeds(
        updateDoc(doc(fs, 'bookings/booking-01'), { status: 'CANCELLED' })
      );
    });

    it('owner can update status to CONFIRMED (open for spec 050)', async () => {
      const fs = player('player1');
      await assertSucceeds(
        updateDoc(doc(fs, 'bookings/booking-01'), { status: 'CONFIRMED' })
      );
    });

    it('owner can update status to EXPIRED', async () => {
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
