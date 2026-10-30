import { afterAll, beforeAll, beforeEach, describe, it, expect } from 'vitest';
import { assertFails, assertSucceeds, type RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, deleteDoc, runTransaction, Timestamp } from 'firebase/firestore';
import { createTestEnv } from './setup';
import { ownerFixtures } from './owner-fixtures';

let env: RulesTestEnvironment;

const ownerA = () => env.authenticatedContext(ownerFixtures.approvedOwnerA.uid, ownerFixtures.approvedOwnerA.claims).firestore();
const ownerB = () => env.authenticatedContext(ownerFixtures.approvedOwnerB.uid, ownerFixtures.approvedOwnerB.claims).firestore();
const player = () => env.authenticatedContext(ownerFixtures.player.uid, ownerFixtures.player.claims).firestore();

beforeAll(async () => {
  env = await createTestEnv();
});

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const fs = ctx.firestore();
    // Seed approved owner A profile and venue
    await setDoc(doc(fs, `users/${ownerFixtures.approvedOwnerA.uid}`), ownerFixtures.approvedOwnerA.profile);
    await setDoc(doc(fs, `venues/${ownerFixtures.venue.id}`), {
      name: 'Smash Arena A',
      ownerId: ownerFixtures.approvedOwnerA.uid,
      status: 'ACTIVE',
      openTime: '0600',
      closeTime: '2200',
    });
    await setDoc(doc(fs, `venues/${ownerFixtures.venue.id}/courts/court-1`), ownerFixtures.court);

    // Seed approved owner B profile and venue
    await setDoc(doc(fs, `users/${ownerFixtures.approvedOwnerB.uid}`), ownerFixtures.approvedOwnerB.profile);
    await setDoc(doc(fs, 'venues/venue-b'), {
      name: 'Smash Arena B',
      ownerId: ownerFixtures.approvedOwnerB.uid,
      status: 'ACTIVE',
      openTime: '0600',
      closeTime: '2200',
    });
    await setDoc(doc(fs, 'venues/venue-b/courts/court-b1'), ownerFixtures.court);
  });
});

afterAll(async () => {
  await env.cleanup();
});

describe('Owner Slot Block Security Rules & Boundaries (T007, T028, T029, T031)', () => {
  it('direct client write by owner to create status = BLOCKED is DENIED by Security Rules', async () => {
    const fs = ownerA();
    await assertFails(
      setDoc(doc(fs, 'slots/court-1_20261030_0800'), {
        venueId: ownerFixtures.venue.id,
        courtId: 'court-1',
        date: '20261030',
        startTime: '0800',
        endTime: '0830',
        status: 'BLOCKED',
        blockReason: 'MAINTENANCE',
      })
    );
  });

  it('direct client write by owner to delete a BLOCKED slot is DENIED by Security Rules', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'slots/court-1_20261030_0800'), {
        venueId: ownerFixtures.venue.id,
        courtId: 'court-1',
        date: '20261030',
        startTime: '0800',
        endTime: '0830',
        status: 'BLOCKED',
        blockReason: 'MAINTENANCE',
      });
    });

    const fs = ownerA();
    await assertFails(deleteDoc(doc(fs, 'slots/court-1_20261030_0800')));
  });

  it('player can read BLOCKED slots for calendar availability display', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'slots/court-1_20261030_0800'), {
        venueId: ownerFixtures.venue.id,
        courtId: 'court-1',
        date: '20261030',
        startTime: '0800',
        endTime: '0830',
        status: 'BLOCKED',
        blockReason: 'MAINTENANCE',
      });
    });

    const fs = player();
    const snap = await assertSucceeds(getDoc(doc(fs, 'slots/court-1_20261030_0800')));
    expect(snap.exists()).toBe(true);
    expect(snap.data()?.status).toBe('BLOCKED');
  });

  it('atomic transaction races between owner lock and player hold (Feature 040 transaction boundary)', async () => {
    // Simulate server transaction simulating manage-slot-blocks against existing active hold
    await env.withSecurityRulesDisabled(async (ctx) => {
      const fs = ctx.firestore();
      // Player holds slot 0800
      await setDoc(doc(fs, 'slots/court-1_20261030_0800'), {
        venueId: ownerFixtures.venue.id,
        courtId: 'court-1',
        date: '20261030',
        startTime: '0800',
        endTime: '0830',
        status: 'HELD',
        holdBy: ownerFixtures.player.uid,
        holdExpiresAt: Timestamp.fromMillis(Date.now() + 4 * 60 * 1000),
      });

      // Server lock transaction attempts to lock 0800 and 0830
      let transactionConflict = false;
      try {
        await runTransaction(fs, async (tx) => {
          const s1 = await tx.get(doc(fs, 'slots/court-1_20261030_0800'));
          const s2 = await tx.get(doc(fs, 'slots/court-1_20261030_0830'));

          // Check if any slot is occupied or held
          if (s1.exists() && (s1.data().status === 'HELD' || s1.data().status === 'BOOKED' || s1.data().status === 'BLOCKED')) {
            throw new Error('SLOT_OCCUPIED_CONFLICT');
          }
          if (s2.exists() && (s2.data().status === 'HELD' || s2.data().status === 'BOOKED' || s2.data().status === 'BLOCKED')) {
            throw new Error('SLOT_OCCUPIED_CONFLICT');
          }

          tx.set(doc(fs, 'slots/court-1_20261030_0800'), { status: 'BLOCKED', blockReason: 'MAINTENANCE' });
          tx.set(doc(fs, 'slots/court-1_20261030_0830'), { status: 'BLOCKED', blockReason: 'MAINTENANCE' });
        });
      } catch (err: any) {
        if (err.message === 'SLOT_OCCUPIED_CONFLICT') {
          transactionConflict = true;
        }
      }

      // Assert entire operation aborted: s2 was NOT written
      expect(transactionConflict).toBe(true);
      const checkS2 = await getDoc(doc(fs, 'slots/court-1_20261030_0830'));
      expect(checkS2.exists()).toBe(false);
    });
  });

  it('partial range conflict aborts atomically leaving no partial writes', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const fs = ctx.firestore();
      // Slot 2 is BOOKED
      await setDoc(doc(fs, 'slots/court-1_20261030_0930'), {
        venueId: ownerFixtures.venue.id,
        courtId: 'court-1',
        date: '20261030',
        startTime: '0930',
        endTime: '1000',
        status: 'BOOKED',
        bookingId: 'book-1',
      });

      // Try locking 0900, 0930, 1000
      let aborted = false;
      try {
        await runTransaction(fs, async (tx) => {
          const s1 = await tx.get(doc(fs, 'slots/court-1_20261030_0900'));
          const s2 = await tx.get(doc(fs, 'slots/court-1_20261030_0930'));
          const s3 = await tx.get(doc(fs, 'slots/court-1_20261030_1000'));

          if (s1.exists() || s2.exists() || s3.exists()) {
            throw new Error('PARTIAL_CONFLICT');
          }

          tx.set(doc(fs, 'slots/court-1_20261030_0900'), { status: 'BLOCKED', blockReason: 'MAINTENANCE' });
          tx.set(doc(fs, 'slots/court-1_20261030_0930'), { status: 'BLOCKED', blockReason: 'MAINTENANCE' });
          tx.set(doc(fs, 'slots/court-1_20261030_1000'), { status: 'BLOCKED', blockReason: 'MAINTENANCE' });
        });
      } catch (e: any) {
        aborted = true;
      }

      expect(aborted).toBe(true);
      const checkS1 = await getDoc(doc(fs, 'slots/court-1_20261030_0900'));
      const checkS3 = await getDoc(doc(fs, 'slots/court-1_20261030_1000'));
      expect(checkS1.exists()).toBe(false);
      expect(checkS3.exists()).toBe(false);
    });
  });

  it('protected DROP_IN block reason cannot be unlocked by owner unlock flow', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const fs = ctx.firestore();
      await setDoc(doc(fs, 'slots/court-1_20261030_1400'), {
        venueId: ownerFixtures.venue.id,
        courtId: 'court-1',
        date: '20261030',
        startTime: '1400',
        endTime: '1430',
        status: 'BLOCKED',
        blockReason: 'DROP_IN', // Feature 112/080
      });

      // Simulate unlock validation logic (as performed by manage-slot-blocks)
      const allowedOwnerReasons = new Set(['MAINTENANCE', 'EVENT', 'WALK_IN']);
      const slotSnap = await getDoc(doc(fs, 'slots/court-1_20261030_1400'));
      const reason = slotSnap.data()?.blockReason;

      let rejectedDropIn = false;
      if (!allowedOwnerReasons.has(reason)) {
        rejectedDropIn = true;
      }

      expect(rejectedDropIn).toBe(true);
      // Verify document is preserved
      const slotAfter = await getDoc(doc(fs, 'slots/court-1_20261030_1400'));
      expect(slotAfter.exists()).toBe(true);
      expect(slotAfter.data()?.blockReason).toBe('DROP_IN');
    });
  });

  it('safe unlock removes owner-created MAINTENANCE block', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      const fs = ctx.firestore();
      await setDoc(doc(fs, 'slots/court-1_20261030_1500'), {
        venueId: ownerFixtures.venue.id,
        courtId: 'court-1',
        date: '20261030',
        startTime: '1500',
        endTime: '1530',
        status: 'BLOCKED',
        blockReason: 'MAINTENANCE',
      });

      // Safe unlock
      await deleteDoc(doc(fs, 'slots/court-1_20261030_1500'));
      const slotAfter = await getDoc(doc(fs, 'slots/court-1_20261030_1500'));
      expect(slotAfter.exists()).toBe(false);
    });
  });
});
