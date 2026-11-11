import { afterAll, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, getDoc, runTransaction, setDoc, Timestamp } from 'firebase/firestore';
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
      status: 'ACTIVE',
      acceptsRecurring: true,
    });
  });
});

afterAll(async () => {
  await env.cleanup();
});

describe('T042: Recurring Hold Transaction End-to-End on Firestore Emulator', () => {
  it('player executes 041 hold transaction: recurringGroup + bookings + slots succeed in 1 transaction', async () => {
    const fs = player('player1');
    const holdExpiresAt = Timestamp.fromMillis(Date.now() + 4 * 60 * 1000);

    await assertSucceeds(
      runTransaction(fs, async (transaction) => {
        // 1. Write recurringGroups/{groupId}
        transaction.set(doc(fs, 'recurringGroups/group-01'), {
          userId: 'player1',
          venueId: 'demo-venue-01',
          status: 'HOLD',
          holdExpiresAt,
          requestId: 'req-01',
          createdAt: Timestamp.now(),
        });

        // 2. Write bookings/{bookingId}
        transaction.set(doc(fs, 'bookings/booking-01'), {
          userId: 'player1',
          recurringGroupId: 'group-01',
          venueId: 'demo-venue-01',
          courtId: 'court-01',
          status: 'HOLD',
          price: 160000,
          depositAmount: 48000,
        });

        // 3. Write slots/{slotId}
        transaction.set(doc(fs, 'slots/court-01_20261110_1800'), {
          venueId: 'demo-venue-01',
          courtId: 'court-01',
          date: '20261110',
          startTime: '1800',
          endTime: '1830',
          status: 'HELD',
          holdExpiresAt,
          holdBy: 'player1',
          bookingId: 'group-01',
        });
      })
    );

    // Verify written data
    const groupSnap = await getDoc(doc(fs, 'recurringGroups/group-01'));
    const bookingSnap = await getDoc(doc(fs, 'bookings/booking-01'));
    const slotSnap = await getDoc(doc(fs, 'slots/court-01_20261110_1800'));

    expect(groupSnap.data()?.status).toBe('HOLD');
    expect(bookingSnap.data()?.status).toBe('HOLD');
    expect(slotSnap.data()?.status).toBe('HELD');
    expect(slotSnap.data()?.holdBy).toBe('player1');
  });

  it('unauthenticated user cannot execute 041 hold transaction', async () => {
    const fs = unauth();
    const holdExpiresAt = Timestamp.fromMillis(Date.now() + 4 * 60 * 1000);

    await assertFails(
      runTransaction(fs, async (transaction) => {
        transaction.set(doc(fs, 'recurringGroups/group-02'), {
          userId: 'player1',
          venueId: 'demo-venue-01',
          status: 'HOLD',
          holdExpiresAt,
        });
        transaction.set(doc(fs, 'bookings/booking-02'), {
          userId: 'player1',
          recurringGroupId: 'group-02',
          status: 'HOLD',
        });
        transaction.set(doc(fs, 'slots/court-01_20261110_1830'), {
          status: 'HELD',
          holdExpiresAt,
          holdBy: 'player1',
        });
      })
    );
  });

  // Từ commit 885b165 (spec 060) client không được tự đổi status của recurringGroups
  // (xem recurringGroups.test.ts). Transaction nhả giữ chỗ bị chặn cả khối, slot vẫn HELD.
  it('player cannot release hold by cancelling the recurringGroup directly (contract pending)', async () => {
    const fs = player('player1');
    const holdExpiresAt = Timestamp.fromMillis(Date.now() + 4 * 60 * 1000);

    // Seed held transaction
    await runTransaction(fs, async (transaction) => {
      transaction.set(doc(fs, 'recurringGroups/group-03'), {
        userId: 'player1',
        venueId: 'demo-venue-01',
        status: 'HOLD',
        holdExpiresAt,
      });
      transaction.set(doc(fs, 'slots/court-01_20261110_1900'), {
        status: 'HELD',
        holdExpiresAt,
        holdBy: 'player1',
      });
    });

    // Release transaction
    await assertFails(
      runTransaction(fs, async (transaction) => {
        transaction.update(doc(fs, 'slots/court-01_20261110_1900'), {
          status: 'AVAILABLE',
          holdBy: 'player1',
        });
        transaction.update(doc(fs, 'recurringGroups/group-03'), {
          status: 'CANCELLED',
        });
      })
    );

    const groupSnap = await getDoc(doc(fs, 'recurringGroups/group-03'));
    const slotSnap = await getDoc(doc(fs, 'slots/court-01_20261110_1900'));
    expect(groupSnap.data()?.status).toBe('HOLD');
    expect(slotSnap.data()?.status).toBe('HELD');
  });

  it('foreign player cannot cancel another player recurringGroup', async () => {
    const fs1 = player('player1');
    const fs2 = otherPlayer('player2');
    const holdExpiresAt = Timestamp.fromMillis(Date.now() + 4 * 60 * 1000);

    await runTransaction(fs1, async (transaction) => {
      transaction.set(doc(fs1, 'recurringGroups/group-04'), {
        userId: 'player1',
        venueId: 'demo-venue-01',
        status: 'HOLD',
        holdExpiresAt,
      });
    });

    await assertFails(
      runTransaction(fs2, async (transaction) => {
        transaction.update(doc(fs2, 'recurringGroups/group-04'), {
          status: 'CANCELLED',
        });
      })
    );
  });
});
